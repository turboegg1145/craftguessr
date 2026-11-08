package com.craftguessr.server;

import com.craftguessr.CraftGuessr;
import com.craftguessr.ai.AiClient;
import com.craftguessr.ai.AiException;
import com.craftguessr.ai.HostContext;
import com.craftguessr.ai.ScanVerdict;
import com.craftguessr.ai.TokenLedger;
import com.craftguessr.game.GameSettings;
import com.craftguessr.game.Spot;
import com.craftguessr.game.SpotRepository;
import com.craftguessr.map.ImageCollage;
import com.craftguessr.map.SurfaceView;
import com.craftguessr.network.Payloads;
import com.craftguessr.scanner.FilterStrength;
import com.craftguessr.scanner.LocalHeuristic;
import com.craftguessr.scanner.WorldSampler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The AI director: a spiral pre-filter over loaded terrain, then one panorama collage + one model
 * call per surviving candidate. Runs off-thread and streams progress to the requesting player.
 */
public final class ScanService {

    private static final int MAX_CANDIDATES = 500;
    private static final int MAX_JUDGED = 16;
    private static final int VIEW_SIZE = 64;
    private static final int COLLAGE_SIZE = 256;
    private static final float[] VIEW_YAW = {0.0F, 90.0F, 180.0F, 270.0F};

    private final ServerGameManager manager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "craftguessr-scan");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<UUID, ScanJob> jobs = new ConcurrentHashMap<>();

    public ScanService(ServerGameManager manager) {
        this.manager = manager;
    }

    public boolean isScanning(UUID player) {
        return jobs.containsKey(player);
    }

    public void start(ServerPlayer player, int radius, String strengthId, String dimension) {
        if (jobs.containsKey(player.getUUID())) {
            manager.notice(player, "craftguessr.message.scan_busy", true);
            return;
        }
        if (!Perms.isAdmin(player)
                && (manager.roomOf(player.getUUID()) == null
                || !manager.roomOf(player.getUUID()).host.equals(player.getUUID()))) {
            manager.notice(player, "craftguessr.message.scan_denied", true);
            return;
        }
        String dim = dimension == null || dimension.isBlank()
                ? player.level().dimension().identifier().toString() : dimension.trim();
        FilterStrength filter;
        try {
            filter = FilterStrength.valueOf(strengthId.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            filter = FilterStrength.BALANCED;
        }
        ScanJob job = new ScanJob(player.getUUID(), player.getName().getString(),
                (int) Math.floor(player.getX()), (int) Math.floor(player.getZ()),
                Math.max(32, Math.min(1024, radius)), dim, filter);
        jobs.put(player.getUUID(), job);
        executor.submit(() -> run(job));
    }

    public void cancel(UUID player) {
        ScanJob job = jobs.get(player);
        if (job != null) {
            job.cancelled = true;
        }
    }

    public void cancelOwner(UUID ignored) {
        // Scans are started by a host; the room no longer matters once the task is queued.
    }

    public void tick() {
        jobs.entrySet().removeIf(entry -> entry.getValue().finished && entry.getValue().finishedAt + 30_000L < System.currentTimeMillis());
    }

    // ================================================================ worker

    private void run(ScanJob job) {
        long started = System.currentTimeMillis();
        int kept = 0;
        int rejected = 0;
        int judged = 0;
        try {
            ServerPlayer player = manager.player(job.owner);
            if (player == null) {
                return;
            }
            ServerLevel level = manager.level(job.dimension);
            GameSettings settings = manager.settings();
            SpotRepository repository = manager.repository();
            TokenLedger ledger = manager.ledger();
            long promptBefore = ledger.promptTokens();
            long completionBefore = ledger.completionTokens();

            progress(job, "craftguessr.scan.filtering", 2, kept, rejected, 0, 0, "scan", false);
            List<Candidate> candidates = collect(level, job);
            int filtered = candidates.size();
            progress(job, "craftguessr.scan.judging", 35, kept, rejected, 0, 0,
                    filtered + " candidates", false);

            if (candidates.isEmpty()) {
                finish(job, "craftguessr.scan.empty", kept, 0, 0, 0);
                return;
            }

            AiClient ai = new AiClient(settings.aiConnection(), settings.maxConcurrency, ledger, 3);
            int limit = Math.min(MAX_JUDGED, candidates.size());
            for (int i = 0; i < limit; i++) {
                if (job.cancelled) {
                    break;
                }
                Candidate candidate = candidates.get(i);
                int percent = 35 + (int) Math.round(60.0 * i / limit);
                progress(job, "craftguessr.scan.judging", percent, kept, rejected,
                        ledger.promptTokens() - promptBefore, ledger.completionTokens() - completionBefore,
                        candidate.sample().stats().artificialRatio() > 0.08 ? "built" : "wild", false);
                try {
                    BufferedImage collage = collage(level, candidate.sample());
                    byte[] jpeg = ImageCollage.toJpeg(collage, 0.82F);
                    ScanVerdict verdict = ai.judge(jpeg, candidate.sample().context(), settings.aiCommentary);
                    judged++;
                    if (!verdict.keep()) {
                        rejected++;
                        continue;
                    }
                    Spot spot = Spot.from(candidate.sample().x(), candidate.sample().y(),
                            candidate.sample().z(), verdict, job.dimension, "", Spot.Source.AI);
                    if (repository.containsPosition(spot)) {
                        rejected++;
                        continue;
                    }
                    String thumbnail = repository.saveThumbnail(spot.id(), jpeg);
                    repository.add(spot.withThumbnail(thumbnail));
                    kept++;
                } catch (AiException e) {
                    rejected++;
                    progress(job, "craftguessr.scan.error", percent, kept, rejected, 0, 0,
                            e.getMessage(), false);
                    if (e.httpStatus() == 401 || e.httpStatus() == 403) {
                        break;
                    }
                } catch (IOException | RuntimeException e) {
                    rejected++;
                }
            }
            try {
                repository.trimTo(settings.maxPoolSize);
            } catch (IOException ignored) {
            }
            long promptUsed = ledger.promptTokens() - promptBefore;
            long completionUsed = ledger.completionTokens() - completionBefore;
            if (job.cancelled) {
                finish(job, "craftguessr.scan.cancelled", kept, judged, promptUsed, completionUsed);
            } else {
                finish(job, "craftguessr.scan.done", kept, judged, promptUsed, completionUsed);
            }
            CraftGuessr.LOGGER.info("Scan for {} finished: kept={} judged={} in {} ms (prompt {} / completion {} tokens)",
                    job.name, kept, judged, System.currentTimeMillis() - started, promptUsed, completionUsed);
        } catch (RuntimeException e) {
            CraftGuessr.LOGGER.error("Scan failed", e);
            finish(job, "craftguessr.scan.failed", kept, judged, 0, 0);
        }
    }

    private List<Candidate> collect(ServerLevel level, ScanJob job) {
        int radius = job.radius;
        int step = Math.max(24, radius / 12);
        List<Candidate> candidates = new ArrayList<>();
        int sampled = 0;
        outer:
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                if (job.cancelled || sampled >= MAX_CANDIDATES) {
                    break outer;
                }
                if (Math.hypot(dx, dz) > radius) {
                    continue;
                }
                int x = job.centerX + dx + ThreadLocalRandom.current().nextInt(-step / 3, step / 3 + 1);
                int z = job.centerZ + dz + ThreadLocalRandom.current().nextInt(-step / 3, step / 3 + 1);
                sampled++;
                WorldSampler.Sample sample;
                try {
                    sample = WorldSampler.sample(level, x, z);
                } catch (RuntimeException e) {
                    continue;
                }
                if (sample == null) {
                    continue;
                }
                LocalHeuristic.Verdict verdict;
                try {
                    verdict = LocalHeuristic.evaluate(sample.stats(), job.filter);
                } catch (RuntimeException e) {
                    continue;
                }
                if (verdict.pass()) {
                    candidates.add(new Candidate(sample, verdict.score()));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        return candidates;
    }

    private BufferedImage collage(ServerLevel level, WorldSampler.Sample sample) {
        List<BufferedImage> views = new ArrayList<>(4);
        for (float yaw : VIEW_YAW) {
            views.add(SurfaceView.render(level, sample.x(), sample.y(), sample.z(), yaw, VIEW_SIZE));
        }
        return ImageCollage.collage(views, COLLAGE_SIZE);
    }

    private void finish(ScanJob job, String messageKey, int kept, int judged, long prompt, long completion) {
        job.finished = true;
        job.finishedAt = System.currentTimeMillis();
        progress(job, messageKey, 100, kept, judged - kept, prompt, completion, messageKey, true);
    }

    private void progress(ScanJob job, String state, int percent, int kept, int rejected,
                          long promptTokens, long completionTokens, String message, boolean finished) {
        ServerPlayer player = manager.player(job.owner);
        if (player == null) {
            return;
        }
        ServerPlayNetworking.send(player, new Payloads.ScanProgressS2C(state, percent, kept, rejected,
                promptTokens, completionTokens, message == null ? "" : message, finished));
    }

    private record Candidate(WorldSampler.Sample sample, double score) {
    }

    private static final class ScanJob {
        final UUID owner;
        final String name;
        final int centerX;
        final int centerZ;
        final int radius;
        final String dimension;
        final FilterStrength filter;
        volatile boolean cancelled;
        volatile boolean finished;
        volatile long finishedAt;

        ScanJob(UUID owner, String name, int centerX, int centerZ, int radius, String dimension,
                FilterStrength filter) {
            this.owner = owner;
            this.name = name;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.dimension = dimension;
            this.filter = filter;
        }
    }
}
