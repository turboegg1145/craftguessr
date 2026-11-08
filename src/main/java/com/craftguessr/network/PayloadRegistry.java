package com.craftguessr.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers every payload type once, on both logical sides. */
public final class PayloadRegistry {

    private PayloadRegistry() {
    }

    public static void init() {
        var c2s = PayloadTypeRegistry.serverboundPlay();
        c2s.register(Payloads.LobbyRequestC2S.TYPE, Payloads.LobbyRequestC2S.CODEC);
        c2s.register(Payloads.CreateRoomC2S.TYPE, Payloads.CreateRoomC2S.CODEC);
        c2s.register(Payloads.JoinRoomC2S.TYPE, Payloads.JoinRoomC2S.CODEC);
        c2s.register(Payloads.LeaveRoomC2S.TYPE, Payloads.LeaveRoomC2S.CODEC);
        c2s.register(Payloads.StartGameC2S.TYPE, Payloads.StartGameC2S.CODEC);
        c2s.register(Payloads.NextRoundC2S.TYPE, Payloads.NextRoundC2S.CODEC);
        c2s.register(Payloads.GuessC2S.TYPE, Payloads.GuessC2S.CODEC);
        c2s.register(Payloads.ScanRequestC2S.TYPE, Payloads.ScanRequestC2S.CODEC);
        c2s.register(Payloads.PoolRequestC2S.TYPE, Payloads.PoolRequestC2S.CODEC);
        c2s.register(Payloads.PoolEditC2S.TYPE, Payloads.PoolEditC2S.CODEC);
        c2s.register(Payloads.PoolDeleteC2S.TYPE, Payloads.PoolDeleteC2S.CODEC);
        c2s.register(Payloads.ThumbRequestC2S.TYPE, Payloads.ThumbRequestC2S.CODEC);
        c2s.register(Payloads.SubmitSpotC2S.TYPE, Payloads.SubmitSpotC2S.CODEC);
        c2s.register(Payloads.TileRequestC2S.TYPE, Payloads.TileRequestC2S.CODEC);

        var s2c = PayloadTypeRegistry.clientboundPlay();
        s2c.register(Payloads.RoomListS2C.TYPE, Payloads.RoomListS2C.CODEC);
        s2c.register(Payloads.RoomStateS2C.TYPE, Payloads.RoomStateS2C.CODEC);
        s2c.register(Payloads.RoundStartS2C.TYPE, Payloads.RoundStartS2C.CODEC);
        s2c.register(Payloads.RevealS2C.TYPE, Payloads.RevealS2C.CODEC);
        s2c.register(Payloads.GameOverS2C.TYPE, Payloads.GameOverS2C.CODEC);
        s2c.register(Payloads.ScanProgressS2C.TYPE, Payloads.ScanProgressS2C.CODEC);
        s2c.register(Payloads.PoolS2C.TYPE, Payloads.PoolS2C.CODEC);
        s2c.register(Payloads.ThumbS2C.TYPE, Payloads.ThumbS2C.CODEC);
        s2c.register(Payloads.TileS2C.TYPE, Payloads.TileS2C.CODEC);
        s2c.register(Payloads.NoticeS2C.TYPE, Payloads.NoticeS2C.CODEC);
        s2c.register(Payloads.OpenScreenS2C.TYPE, Payloads.OpenScreenS2C.CODEC);
        s2c.register(Payloads.PingS2C.TYPE, Payloads.PingS2C.CODEC);
        s2c.register(Payloads.CaptureRequestS2C.TYPE, Payloads.CaptureRequestS2C.CODEC);
    }
}
