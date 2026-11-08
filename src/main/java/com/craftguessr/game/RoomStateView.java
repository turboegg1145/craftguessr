package com.craftguessr.game;

import java.util.List;
import java.util.UUID;

/**
 * Client-visible snapshot of one room. Lives in one packet so the lobby and the in-game HUD read the
 * same source of truth. All lists are immutable.
 */
public record RoomStateView(
        String roomId,
        GamePhase phase,
        int round,
        int totalRounds,
        int secondsLeft,
        UUID hostId,
        List<PlayerView> players,
        RoomSettings settings,
        int poolSize,
        boolean host,
        String notice
) {

    public RoomStateView {
        roomId = roomId == null ? "" : roomId;
        phase = phase == null ? GamePhase.IDLE : phase;
        players = players == null ? List.of() : List.copyOf(players);
        settings = settings == null ? RoomSettings.defaults() : settings;
        notice = notice == null ? "" : notice;
    }

    public int size() {
        return players.size();
    }

    public PlayerView me(UUID id) {
        for (PlayerView player : players) {
            if (player.id().equals(id)) {
                return player;
            }
        }
        return null;
    }

    public List<PlayerView> leaderboard() {
        java.util.List<PlayerView> sorted = new java.util.ArrayList<>(players);
        sorted.sort(java.util.Comparator.comparingInt(PlayerView::score).reversed());
        return List.copyOf(sorted);
    }

    /** One player's public scoreboard row. */
    public record PlayerView(UUID id, String name, int score, int streak, boolean guessed, boolean online) {
        public PlayerView {
            name = name == null || name.isBlank() ? "?" : name;
        }
    }
}
