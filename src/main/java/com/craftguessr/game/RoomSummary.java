package com.craftguessr.game;

/** Compact room entry rendered in the lobby list. */
public record RoomSummary(
        String id,
        String hostName,
        GamePhase phase,
        int players,
        int totalRounds,
        int round,
        GameMode mode,
        MovementMode movement,
        boolean locked
) {
    public RoomSummary {
        id = id == null ? "" : id;
        hostName = hostName == null || hostName.isBlank() ? "?" : hostName;
        phase = phase == null ? GamePhase.IDLE : phase;
        mode = mode == null ? GameMode.CLASSIC : mode;
        movement = movement == null ? MovementMode.MOVING : movement;
    }

    public String label() {
        return hostName + "'s room";
    }
}
