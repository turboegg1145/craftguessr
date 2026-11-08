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

    private static final int LIST_WIDTH = 220;
    private static final int MAX_ROWS = 9;

    private String selectedId;
    private FilterStrength strength = FilterStrength.BALANCED;
    private EditBox titleBox;
    private EditBox radiusBox;
    private ScanVerdict.Difficulty draftDifficulty = ScanVerdict.Difficulty.MEDIUM;
    private int shownPoolSize = -1;

    public PoolScreen() {
        super(Component.translatable("gui.craftguessr.pool.title"));
    }

    @Override
    protected void init() {
        ClientPlayNetworking.send(new Payloads.PoolRequestC2S());
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        clearWidgets();
        List<Spot> pool = ClientState.get().pool();
        shownPoolSize = pool.size();
        int listX = 8;
        int rows = Math.min(pool.size(), MAX_ROWS);
        Spot selected = selectedId == null ? null : ClientState.get().poolSpot(selectedId);
        if (selectedId != null && selected != null) {
            draftDifficulty = selected.difficulty();
        }
        for (int i = 0; i < rows; i++) {
            Spot spot = pool.get(i);
            boolean active = spot.id().equals(selectedId);
            Button button = addRenderableWidget(Button.builder(Component.literal(
                                    (active ? "> " : "") + (spot.title() == null || spot.title().isBlank()
                                            ? spot.id() : spot.title()) + " [" + spot.difficulty().name() + "]"),
                            b -> {
                                selectedId = spot.id();
                                draftDifficulty = spot.difficulty();
                                rebuildWidgets();
                            })
                    .bounds(listX, 30 + i * 20, LIST_WIDTH, 18).build());
            button.active = true;
        }

        int rightX = 8 + LIST_WIDTH + 12;
        int rightWidth = width - rightX - 8;
        int controls = rightWidth - 140;
        titleBox = new EditBox(font, rightX, 80, controls, 18,
                Component.translatable("gui.craftguessr.pool.title_label"));
        if (selected != null) {
            titleBox.setValue(selected.title() == null ? "" : selected.title());
        }
        addRenderableWidget(titleBox);

        addRenderableWidget(CycleButton.builder((ScanVerdict.Difficulty d) ->
                                Component.translatable(d.translationKey()), draftDifficulty)
                .withValues(ScanVerdict.Difficulty.values())
                .create(rightX, 104, controls, 18, Component.translatable("gui.craftguessr.pool.difficulty"),
                        (button, value) -> draftDifficulty = value));

        addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.pool.save"), b -> {
                    if (selectedId != null) {
                        ClientPlayNetworking.send(new Payloads.PoolEditC2S(selectedId,
                                titleBox.getValue().trim(), draftDifficulty.name()));
                    }
                })
                .bounds(rightX, 128, Math.max(60, controls / 2 - 3), 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.pool.delete"), b -> {
                    if (selectedId != null) {
                        ClientPlayNetworking.send(new Payloads.PoolDeleteC2S(selectedId));
                        selectedId = null;
                    }
                })
                .bounds(rightX + Math.max(60, controls / 2 - 3) + 6, 128,
                        Math.max(60, controls / 2 - 3), 18).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.pool.submit"), b -> submit())
                .bounds(rightX, 152, controls, 18).build());

        radiusBox = new EditBox(font, rightX, 176, 56, 18, Component.translatable("gui.craftguessr.pool.radius"));
        radiusBox.setValue("256");
        addRenderableWidget(radiusBox);
        addRenderableWidget(CycleButton.builder((FilterStrength s) -> Component.translatable(s.translationKey()),
                        strength)
                .withValues(FilterStrength.values())
                .create(rightX + 62, 176, Math.max(90, controls - 62), 18,
                        Component.translatable("gui.craftguessr.pool.strength"),
                        (button, value) -> strength = value));
        addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.pool.scan"), b -> {
                    if (ClientState.get().scanning()) {
                        return;
                    }
                    ClientPlayNetworking.send(new Payloads.ScanRequestC2S(parseInt(radiusBox.getValue(), 256),
                            strength.name(), ClientState.get().dimension()));
                })
                .bounds(rightX, 200, 120, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.map.cancel"),
                        b -> minecraft.gui.setScreen(null))
                .bounds(width - 70, 4, 62, 18).build());

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
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.text(font, title.getString(), 8, 8, 0xFFFFFFFF);
        List<Spot> pool = ClientState.get().pool();
        if (pool.isEmpty()) {
            g.centeredText(font, Component.translatable("gui.craftguessr.pool.no_spots").getString(),
                    width / 2, height / 2, 0xFF90A4AE);
        }
        Spot selected = selectedId == null ? null : ClientState.get().poolSpot(selectedId);
        int rightX = 8 + LIST_WIDTH + 12;
        if (selected != null) {
            g.text(font, selected.id(), rightX, 32, 0xFFB0BEC5);
            g.text(font, selected.coordinates() + "  " + ClientNetwork.dimensionName(selected.dimension()),
                    rightX, 44, 0xFF90A4AE);
            g.text(font, Component.translatable("gui.craftguessr.pool.title_label").getString(),
                    rightX, 70, 0xFFB0BEC5);
            int rightWidth = width - rightX - 8;
            int thumbX = rightX + Math.max(0, rightWidth - 132);
            Identifier thumb = ClientImages.get(ClientImages.thumbKey(selected.id()));
            if (thumb != null) {
                g.pose().pushMatrix();
                g.pose().translate(thumbX, 32);
                g.pose().scale(0.5F, 0.5F);
                g.blit(RenderPipelines.GUI_TEXTURED, thumb, 0, 0, 0.0F, 0.0F, 256, 256, 256, 256);
                g.pose().popMatrix();
            } else {
                g.fill(thumbX, 32, thumbX + 128, 32 + 128, 0xFF14202B);
                if (ClientState.get().shouldRequestThumb(selected.id())) {
                    ClientPlayNetworking.send(new Payloads.ThumbRequestC2S(selected.id()));
                }
            }
        }
        Payloads.ScanProgressS2C scan = ClientState.get().scan();
        if (scan != null) {
            String text = Component.translatable(scan.state(), scan.percent(), scan.kept(), scan.rejected()).getString()
                    + "  [" + scan.promptTokens() + "/" + scan.completionTokens() + "]";
            g.text(font, text, rightX, 244, scan.finished() ? 0xFF9CCC65 : 0xFFFFD54F);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
