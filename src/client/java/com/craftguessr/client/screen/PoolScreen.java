package com.craftguessr.client.screen;

import com.craftguessr.ai.ScanVerdict;
import com.craftguessr.client.ClientImages;
import com.craftguessr.client.ClientNetwork;
import com.craftguessr.client.ClientState;
import com.craftguessr.game.Spot;
import com.craftguessr.network.Payloads;
import com.craftguessr.scanner.FilterStrength;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Spot pool album: inspect, retitle, retag, delete, scan and submit spots. */
public final class PoolScreen extends Screen {

    private static final int PAD = 8;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_GAP = 2;
    private static final int THUMB = 64;

    private String selectedId;
    private FilterStrength strength = FilterStrength.BALANCED;
    private EditBox titleBox;
    private EditBox radiusBox;
    private ScanVerdict.Difficulty draftDifficulty = ScanVerdict.Difficulty.MEDIUM;
    private int shownPoolSize = -1;

    private int listX;
    private int listWidth;
    private int rightX;
    private int rightWidth;

    public PoolScreen() {
        super(Component.translatable("gui.craftguessr.pool.title"));
    }

    @Override
    protected void init() {
        ClientPlayNetworking.send(new Payloads.PoolRequestC2S());
        repositionElements();
    }

    private int listRows() {
        return Math.max(1, (height - 26 - PAD) / (ROW_HEIGHT + ROW_GAP));
    }

    @Override
    protected void repositionElements() {
        clearWidgets();
        List<Spot> pool = ClientState.get().pool();
        shownPoolSize = pool.size();

        listX = PAD;
        listWidth = Math.max(120, Math.min(150, width / 3));
        rightX = listX + listWidth + PAD;
        rightWidth = Math.max(120, width - rightX - PAD);

        Spot selected = selectedId == null ? null : ClientState.get().poolSpot(selectedId);
        if (selectedId != null && selected == null) {
            selectedId = null;
        }
        if (selectedId == null && !pool.isEmpty()) {
            selectedId = pool.get(0).id();
            selected = pool.get(0);
            draftDifficulty = selected.difficulty();
        }
        if (selected != null) {
            draftDifficulty = selected.difficulty();
        }

        int rows = Math.min(pool.size(), listRows());
        for (int i = 0; i < rows; i++) {
            Spot spot = pool.get(i);
            boolean active = spot.id().equals(selectedId);
            String name = spot.title() == null || spot.title().isBlank()
                    ? spot.x() + "," + spot.z() : spot.title();
            if (name.length() > 14) {
                name = name.substring(0, 13) + "…";
            }
            String label = (active ? "> " : "") + name + " [" + spot.difficulty().name().charAt(0) + "]";
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                        selectedId = spot.id();
                        draftDifficulty = spot.difficulty();
                        rebuildWidgets();
                    })
                    .bounds(listX, 26 + i * (ROW_HEIGHT + ROW_GAP), listWidth, ROW_HEIGHT).build());
        }

        int fieldWidth = Math.max(80, rightWidth - THUMB - 6);
        int y = 56;

        titleBox = new EditBox(font, rightX, y, fieldWidth, ROW_HEIGHT,
                Component.translatable("gui.craftguessr.pool.title_label"));
        if (selected != null) {
            titleBox.setValue(selected.title() == null ? "" : selected.title());
        }
        titleBox.setEditable(selected != null);
        addRenderableWidget(titleBox);

        y += ROW_HEIGHT + ROW_GAP;
        addRenderableWidget(CycleButton.builder((ScanVerdict.Difficulty d) ->
                                Component.translatable(d.translationKey()), draftDifficulty)
                .withValues(ScanVerdict.Difficulty.values())
                .create(rightX, y, fieldWidth, ROW_HEIGHT,
                        Component.translatable("gui.craftguessr.pool.difficulty"),
                        (button, value) -> draftDifficulty = value));

        y += ROW_HEIGHT + ROW_GAP;
        int half = Math.max(50, (fieldWidth - 4) / 2);
        addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.pool.save"), b -> {
                    if (selectedId != null) {
                        ClientPlayNetworking.send(new Payloads.PoolEditC2S(selectedId,
                                titleBox.getValue().trim(), draftDifficulty.name()));
                    }
                })
                .bounds(rightX, y, half, ROW_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.pool.delete"), b -> {
                    if (selectedId != null) {
                        ClientPlayNetworking.send(new Payloads.PoolDeleteC2S(selectedId));
                        selectedId = null;
                        rebuildWidgets();
                    }
                })
                .bounds(rightX + half + 4, y, half, ROW_HEIGHT).build());

        y += ROW_HEIGHT + ROW_GAP;
        addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.pool.submit"), b -> submit())
                .bounds(rightX, y, fieldWidth, ROW_HEIGHT).build());

        y += ROW_HEIGHT + ROW_GAP;
        radiusBox = new EditBox(font, rightX, y, 44, ROW_HEIGHT,
                Component.translatable("gui.craftguessr.pool.radius"));
        radiusBox.setValue("256");
        addRenderableWidget(radiusBox);
        addRenderableWidget(CycleButton.builder((FilterStrength s) -> Component.translatable(s.translationKey()),
                        strength)
                .withValues(FilterStrength.values())
                .create(rightX + 48, y, Math.max(60, fieldWidth - 48), ROW_HEIGHT,
                        Component.translatable("gui.craftguessr.pool.strength"),
                        (button, value) -> strength = value));

        y += ROW_HEIGHT + ROW_GAP;
        addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.pool.scan"), b -> {
                    if (ClientState.get().scanning()) {
                        return;
                    }
                    ClientPlayNetworking.send(new Payloads.ScanRequestC2S(parseInt(radiusBox.getValue(), 256),
                            strength.name(), ClientState.get().dimension()));
                })
                .bounds(rightX, y, fieldWidth, ROW_HEIGHT).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.map.cancel"),
                        b -> minecraft.gui.setScreen(null))
                .bounds(width - 70, 4, 62, ROW_HEIGHT).build());

        if (selected != null && ClientState.get().shouldRequestThumb(selected.id())) {
            ClientPlayNetworking.send(new Payloads.ThumbRequestC2S(selected.id()));
        }
    }

    private void submit() {
        var player = minecraft.player;
        if (player == null) {
            return;
        }
        var pos = player.position();
        ClientPlayNetworking.send(new Payloads.SubmitSpotC2S(ClientState.get().dimension(),
                (int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z),
                player.getYRot(), player.getXRot(), new byte[0]));
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public void tick() {
        if (ClientState.get().pool().size() != shownPoolSize) {
            rebuildWidgets();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        ClientScreens.dimBackground(g);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.text(font, title.getString(), PAD, 8, 0xFFFFFFFF);

        List<Spot> pool = ClientState.get().pool();
        if (pool.isEmpty()) {
            g.text(font, Component.translatable("gui.craftguessr.pool.no_spots").getString(),
                    listX, 30, 0xFF90A4AE);
        }

        Spot selected = selectedId == null ? null : ClientState.get().poolSpot(selectedId);
        if (selected != null) {
            g.text(font, selected.coordinates(), rightX, 28, 0xFF90A4AE);
            g.text(font, ClientNetwork.dimensionName(selected.dimension()) + "  " + selected.id(),
                    rightX, 40, 0xFF78858C);

            int thumbX = rightX + Math.max(0, rightWidth - THUMB);
            int thumbY = 26;
            g.fill(thumbX - 1, thumbY - 1, thumbX + THUMB + 1, thumbY + THUMB + 1, 0xFF2A3742);
            Identifier thumb = ClientImages.get(ClientImages.thumbKey(selected.id()));
            if (thumb != null) {
                g.pose().pushMatrix();
                g.pose().translate(thumbX, thumbY);
                g.pose().scale(THUMB / 256.0F, THUMB / 256.0F);
                g.blit(RenderPipelines.GUI_TEXTURED, thumb, 0, 0, 0.0F, 0.0F, 256, 256, 256, 256);
                g.pose().popMatrix();
            } else {
                g.centeredText(font, "…", thumbX + THUMB / 2, thumbY + THUMB / 2 - 4, 0xFF78909C);
                if (ClientState.get().shouldRequestThumb(selected.id())) {
                    ClientPlayNetworking.send(new Payloads.ThumbRequestC2S(selected.id()));
                }
            }
            g.text(font, Component.translatable("gui.craftguessr.pool.title_label").getString(),
                    rightX, 48, 0xFFB0BEC5);
        }

        Payloads.ScanProgressS2C scan = ClientState.get().scan();
        if (scan != null) {
            String text = Component.translatable(scan.state(), scan.percent(), scan.kept(), scan.rejected())
                    .getString() + "  [" + scan.promptTokens() + "/" + scan.completionTokens() + "]";
            g.text(font, text, rightX, height - 14, scan.finished() ? 0xFF9CCC65 : 0xFFFFD54F);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
