package com.craftguessr.client.screen;

import com.craftguessr.game.GameMode;
import com.craftguessr.game.MovementMode;
import com.craftguessr.game.RoomSettings;
import com.craftguessr.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/** Create-room form; every field folds straight into a {@link RoomSettings} draft. */
public final class CreateRoomScreen extends Screen {

    private static final int PANEL_WIDTH = 420;

    private RoomSettings draft = RoomSettings.defaults();

    public CreateRoomScreen() {
        super(Component.translatable("gui.craftguessr.create.title"));
    }

    @Override
    protected void init() {
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        clearWidgets();
        int left = (width - PANEL_WIDTH) / 2;
        int column = PANEL_WIDTH / 2 - 4;
        int row = 44;

        addRenderableWidget(CycleButton.builder((GameMode mode) -> Component.translatable(mode.translationKey()),
                        draft.mode())
                .withValues(GameMode.values())
                .create(left, row, column, 20, Component.translatable("gui.craftguessr.create.mode"),
                        (button, mode) -> draft = draft.withMode(mode)));
        addRenderableWidget(CycleButton.builder((MovementMode mode) -> Component.translatable(mode.translationKey()),
                        draft.movement())
                .withValues(MovementMode.values())
                .create(left + column + 8, row, column, 20, Component.translatable("gui.craftguessr.create.movement"),
                        (button, mode) -> draft = draft.withMovement(mode)));
        row += 28;

        EditBox rounds = numberField(left, row, column, "gui.craftguessr.create.rounds", draft.rounds(),
                value -> draft = draft.withRounds(value));
        addRenderableWidget(rounds);
        EditBox seconds = numberField(left + column + 8, row, column, "gui.craftguessr.create.seconds",
                draft.secondsPerRound(), value -> draft = draft.withSecondsPerRound(value));
        addRenderableWidget(seconds);
        row += 28;

        EditBox radius = numberField(left, row, column, "gui.craftguessr.create.radius", draft.movementRadius(),
                value -> draft = draft.withMovementRadius(value));
        addRenderableWidget(radius);
        addRenderableWidget(Checkbox.builder(Component.translatable("gui.craftguessr.create.invisible"), font)
                .pos(left + column + 8, row + 2)
                .selected(draft.invisiblePlayers())
                .onValueChange((box, value) -> draft = draft.withInvisiblePlayers(value))
                .build());
        row += 28;

        addRenderableWidget(Checkbox.builder(Component.translatable("gui.craftguessr.create.commentary"), font)
                .pos(left, row)
                .selected(draft.aiCommentary())
                .onValueChange((box, value) -> draft = draft.withAiCommentary(value))
                .build());
        addRenderableWidget(Checkbox.builder(Component.translatable("gui.craftguessr.create.accelerate"), font)
                .pos(left + column + 8, row)
                .selected(draft.accelerateAfterFirstGuess())
                .onValueChange((box, value) -> draft = draft.withAccelerateAfterFirstGuess(value))
                .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.create.confirm"),
                        button -> {
                            ClientPlayNetworking.send(new Payloads.CreateRoomC2S(draft.sanitized()));
                            minecraft.gui.setScreen(new LobbyScreen());
                        })
                .bounds(left, height - 34, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.craftguessr.create.cancel"),
                        button -> minecraft.gui.setScreen(new LobbyScreen()))
                .bounds(left + 158, height - 34, 150, 20).build());
    }

    private EditBox numberField(int x, int y, int width, String labelKey, int value, IntConsumer sink) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.translatable(labelKey));
        box.setValue(Integer.toString(value));
        box.setResponder(text -> {
            try {
                sink.accept(Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
                // Keep the previous value while the player is mid-edit.
            }
        });
        return box;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, title.getString(), width / 2, 20, 0xFFFFFFFF);
        int left = (width - PANEL_WIDTH) / 2;
        int column = PANEL_WIDTH / 2 - 4;
        g.text(font, Component.translatable("gui.craftguessr.create.rounds").getString(), left, 58, 0xFFB0BEC5);
        g.text(font, Component.translatable("gui.craftguessr.create.seconds").getString(),
                left + column + 8, 58, 0xFFB0BEC5);
        g.text(font, Component.translatable("gui.craftguessr.create.radius").getString(), left, 86, 0xFFB0BEC5);
        g.text(font, Component.translatable("gui.craftguessr.create.summary",
                        draft.rounds(), draft.secondsPerRound(), draft.movementRadius()).getString(),
                left, height - 50, 0xFF90A4AE);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
