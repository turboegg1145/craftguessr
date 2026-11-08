# MC 26.3 — Server / Data / Networking Recon

**Ground truth:** every signature below was copied verbatim from `javap -p` output against the
deobf jars inside `geoguessr-dev`. Nothing here is inferred from memory or from older versions.

```
MCJAR  /home/dev/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common-deobf/26.3/minecraft-common-deobf-26.3.jar
CLJAR  /home/dev/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/26.3/minecraft-clientonly-deobf-26.3.jar
FABRIC fabric-api modules under /home/dev/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/
       fabric-networking-api-v1-6.4.1+c58129785d, fabric-lifecycle-events-v1-4.1.9+ffef5f675d,
       fabric-command-api-v2-3.1.2+fcdff87f5d
BRIG   /home/dev/.gradle/caches/modules-2/files-2.1/com.mojang/brigadier/1.3.11/.../brigadier-1.3.11.jar
```

Reproduce with `./scripts/javap.sh <fqcn>` (add the fabric/brigadier jars to `-classpath` for the
Fabric/Brigadier sections).

---

## 1. Networking

### 1.1 `CustomPacketPayload` — `type()`, not `getId()`; `Type` wraps an `Identifier`

```java
public interface net.minecraft.network.protocol.common.custom.CustomPacketPayload {
  public abstract net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type();
  public static <B extends io.netty.buffer.ByteBuf, T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> net.minecraft.network.codec.StreamCodec<B, T> codec(net.minecraft.network.codec.StreamMemberEncoder<B, T>, net.minecraft.network.codec.StreamDecoder<B, T>);
  public static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<T> createType(java.lang.String);
  public static <B extends net.minecraft.network.FriendlyByteBuf> net.minecraft.network.codec.StreamCodec<B, net.minecraft.network.protocol.common.custom.CustomPacketPayload> codec(net.minecraft.network.protocol.common.custom.CustomPacketPayload$FallbackProvider<B>, java.util.List<net.minecraft.network.protocol.common.custom.CustomPacketPayload$TypeAndCodec<? super B, ?>>);
}
```

```java
public final class net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> extends java.lang.Record {
  public net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type(net.minecraft.resources.Identifier);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.minecraft.resources.Identifier id();
}
```

```java
public final class net.minecraft.network.protocol.common.custom.CustomPacketPayload$TypeAndCodec<B extends net.minecraft.network.FriendlyByteBuf, T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> extends java.lang.Record {
  public net.minecraft.network.protocol.common.custom.CustomPacketPayload$TypeAndCodec(net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<T>, net.minecraft.network.codec.StreamCodec<B, T>);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<T> type();
  public net.minecraft.network.codec.StreamCodec<B, T> codec();
}
```

- `TYPE` is `new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ns, path))`; there is also a
  static `CustomPacketPayload.createType(String)` helper.
- `Type` is a **record over `Identifier`** — build the id with `Identifier`, never `ResourceLocation`.

### 1.2 `PayloadTypeRegistry` — MC class is **gone**; only Fabric's remains, and `register` **returns** `TypeAndCodec`

```
net.minecraft.network.protocol.common.custom.PayloadTypeRegistry  ->  NOT FOUND in jar
Error: class not found: net.minecraft.network.protocol.common.custom.PayloadTypeRegistry
```

```java
public interface net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry<B extends net.minecraft.network.FriendlyByteBuf> {
  public abstract <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> net.minecraft.network.protocol.common.custom.CustomPacketPayload$TypeAndCodec<? super B, T> register(net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<T>, net.minecraft.network.codec.StreamCodec<? super B, T>);
  public abstract <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> net.minecraft.network.protocol.common.custom.CustomPacketPayload$TypeAndCodec<? super B, T> register(net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<T>, net.minecraft.network.codec.StreamCodec<? super B, T>, java.util.function.Predicate<net.fabricmc.fabric.api.networking.v1.context.PacketContext>);
  public abstract <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> net.minecraft.network.protocol.common.custom.CustomPacketPayload$TypeAndCodec<? super B, T> registerLarge(net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<T>, net.minecraft.network.codec.StreamCodec<? super B, T>, int);
  public abstract <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> net.minecraft.network.protocol.common.custom.CustomPacketPayload$TypeAndCodec<? super B, T> registerLarge(net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<T>, net.minecraft.network.codec.StreamCodec<? super B, T>, int, java.util.function.Predicate<net.fabricmc.fabric.api.networking.v1.context.PacketContext>);
  public abstract <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> net.minecraft.network.protocol.common.custom.CustomPacketPayload$TypeAndCodec<? super B, T> registerLarge(net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<T>, net.minecraft.network.codec.StreamCodec<? super B, T>, java.util.function.IntSupplier);
  public abstract <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> net.minecraft.network.protocol.common.custom.CustomPacketPayload$TypeAndCodec<? super B, T> registerLarge(net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<T>, net.minecraft.network.codec.StreamCodec<? super B, T>, java.util.function.IntSupplier, java.util.function.Predicate<net.fabricmc.fabric.api.networking.v1.context.PacketContext>);
  public static net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry<net.minecraft.network.FriendlyByteBuf> serverboundConfiguration();
  public static net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry<net.minecraft.network.FriendlyByteBuf> clientboundConfiguration();
  public static net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry<net.minecraft.network.RegistryFriendlyByteBuf> serverboundPlay();
  public static net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry<net.minecraft.network.RegistryFriendlyByteBuf> clientboundPlay();
}
```

- Register **both directions** at mod init: `PayloadTypeRegistry.playC2S()`/`playS2C()` no longer exist —
  the names are `serverboundPlay()` / `clientboundPlay()`.
- `register(...)` returns `CustomPacketPayload.TypeAndCodec`, not `void` — safe to ignore, but its
  presence means call sites that used it as a statement still compile while ones expecting `void`
  in a lambda/expression context need care.

### 1.3 `StreamCodec` — `of`, `ofMember`, `composite`, `unit`, `cast`

```java
public interface net.minecraft.network.codec.StreamCodec<B, V> extends net.minecraft.network.codec.StreamEncoder<B, V>, net.minecraft.network.codec.StreamDecoder<B, V> {
  public static <B, V> net.minecraft.network.codec.StreamCodec<B, V> of(net.minecraft.network.codec.StreamEncoder<B, V>, net.minecraft.network.codec.StreamDecoder<B, V>);
  public static <B, V> net.minecraft.network.codec.StreamCodec<B, V> ofMember(net.minecraft.network.codec.StreamMemberEncoder<B, V>, net.minecraft.network.codec.StreamDecoder<B, V>);
  public static <B, V> net.minecraft.network.codec.StreamCodec<B, V> unit(V);
  public static <B, C, T1> net.minecraft.network.codec.StreamCodec<B, C> composite(net.minecraft.network.codec.StreamCodec<? super B, T1>, java.util.function.Function<C, T1>, java.util.function.Function<T1, C>);
  public static <B, C, T1, T2> net.minecraft.network.codec.StreamCodec<B, C> composite(net.minecraft.network.codec.StreamCodec<? super B, T1>, java.util.function.Function<C, T1>, net.minecraft.network.codec.StreamCodec<? super B, T2>, java.util.function.Function<C, T2>, java.util.function.BiFunction<T1, T2, C>);
  public default <S extends B> net.minecraft.network.codec.StreamCodec<S, V> cast();
}
```

- `ofMember` **does exist** (encoder takes the value as first arg: `(buf, value) -> ...`).
- `composite` has arities 1..14; arity 1 is `(codec, getter, ctor)` and arity 2 ends in `BiFunction`.
- For a `RegistryFriendlyByteBuf` payload, declare the codec as
  `StreamCodec<RegistryFriendlyByteBuf, MyPayload>` and register it via `clientboundPlay()`.

### 1.4 `RegistryFriendlyByteBuf` / `FriendlyByteBuf`

```java
public class net.minecraft.network.RegistryFriendlyByteBuf extends net.minecraft.network.FriendlyByteBuf {
  public net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.ByteBuf, net.minecraft.core.RegistryAccess);
  public net.minecraft.core.RegistryAccess registryAccess();
  public static java.util.function.Function<io.netty.buffer.ByteBuf, net.minecraft.network.RegistryFriendlyByteBuf> decorator(net.minecraft.core.RegistryAccess);
}
```

```java
public class net.minecraft.network.FriendlyByteBuf extends io.netty.buffer.ByteBuf {
  public static final short MAX_STRING_LENGTH;
  public int readVarInt();
  public net.minecraft.network.FriendlyByteBuf writeVarInt(int);
  public boolean readBoolean();
  public net.minecraft.network.FriendlyByteBuf writeBoolean(boolean);
  public long readLong();
  public net.minecraft.network.FriendlyByteBuf writeLong(long);
  public float readFloat();
  public net.minecraft.network.FriendlyByteBuf writeFloat(float);
  public double readDouble();
  public net.minecraft.network.FriendlyByteBuf writeDouble(double);
  public byte[] readByteArray();
  public net.minecraft.network.FriendlyByteBuf writeByteArray(byte[]);
  public java.lang.String readUtf();
  public net.minecraft.network.FriendlyByteBuf writeUtf(java.lang.String);
  public net.minecraft.network.FriendlyByteBuf writeUtf(java.lang.String, int);
  public net.minecraft.resources.Identifier readIdentifier();
  public net.minecraft.network.FriendlyByteBuf writeIdentifier(net.minecraft.resources.Identifier);
  public net.minecraft.network.FriendlyByteBuf writeBlockPos(net.minecraft.core.BlockPos);
  public net.minecraft.network.FriendlyByteBuf writeUUID(java.util.UUID);
  public java.util.UUID readUUID();
  public net.minecraft.network.FriendlyByteBuf writeNbt(net.minecraft.nbt.Tag);
  public net.minecraft.nbt.CompoundTag readNbt();
}
```

- It is `writeIdentifier`/`readIdentifier` — **`writeResourceLocation` does not exist** (see §10).
- `writeByteArray`/`readByteArray` and `writeUtf(String)`/`readUtf()` round-trip a `String` or blob with
  no length prefix of your own.

### 1.5 Fabric `ServerPlayNetworking` — handler is `void receive(T, Context)`

```java
public final class net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking {
  public static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> boolean registerGlobalReceiver(net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<T>, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking$PlayPayloadHandler<T>);
  public static net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking$PlayPayloadHandler<?> unregisterGlobalReceiver(net.minecraft.resources.Identifier);
  public static java.util.Set<net.minecraft.resources.Identifier> getGlobalReceivers();
  public static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> boolean registerReceiver(net.minecraft.server.network.ServerGamePacketListenerImpl, net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<T>, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking$PlayPayloadHandler<T>);
  public static net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking$PlayPayloadHandler<?> unregisterReceiver(net.minecraft.server.network.ServerGamePacketListenerImpl, net.minecraft.resources.Identifier);
  public static java.util.Set<net.minecraft.resources.Identifier> getReceived(net.minecraft.server.level.ServerPlayer);
  public static java.util.Set<net.minecraft.resources.Identifier> getSendable(net.minecraft.server.level.ServerPlayer);
  public static boolean canSend(net.minecraft.server.level.ServerPlayer, net.minecraft.resources.Identifier);
  public static boolean canSend(net.minecraft.server.level.ServerPlayer, net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<?>);
  public static boolean canSend(net.minecraft.server.network.ServerGamePacketListenerImpl, net.minecraft.resources.Identifier);
  public static boolean canSend(net.minecraft.server.network.ServerGamePacketListenerImpl, net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<?>);
  public static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.common.ClientCommonPacketListener> createClientboundPacket(T);
  public static net.fabricmc.fabric.api.networking.v1.PacketSender getSender(net.minecraft.server.level.ServerPlayer);
  public static void send(net.minecraft.server.level.ServerPlayer, net.minecraft.network.protocol.common.custom.CustomPacketPayload);
  public static void reconfigure(net.minecraft.server.level.ServerPlayer);
}
```

```java
public interface net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking$PlayPayloadHandler<T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> {
  public abstract void receive(T, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking$Context);
}
```

```java
public interface net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking$Context {
  public abstract net.minecraft.server.MinecraftServer server();
  public abstract net.minecraft.server.level.ServerPlayer player();
  public abstract net.fabricmc.fabric.api.networking.v1.PacketSender responseSender();
  public default net.fabricmc.fabric.api.networking.v1.context.PacketContext packetContext();
}
```

- Lambda shape: `ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> { ... })`
  where `context` is `ServerPlayNetworking.Context` (`context.player()`, `context.server()`).
- `send` is only `send(ServerPlayer, CustomPacketPayload)` — no `(player, Identifier, buf)` overload, no
  broadcast helper; loop over `server.getPlayerList().getPlayers()`.
- `canSend` takes `Identifier` or `Type<?>` (the old `(player, Identifier)` form survives).

### 1.6 Fabric `ClientPlayNetworking`

```java
public final class net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking {
  public static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> boolean registerGlobalReceiver(net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<T>, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking$PlayPayloadHandler<T>);
  public static net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking$PlayPayloadHandler<?> unregisterGlobalReceiver(net.minecraft.resources.Identifier);
  public static java.util.Set<net.minecraft.resources.Identifier> getGlobalReceivers();
  public static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> boolean registerReceiver(net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<T>, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking$PlayPayloadHandler<T>);
  public static net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking$PlayPayloadHandler<?> unregisterReceiver(net.minecraft.resources.Identifier);
  public static java.util.Set<net.minecraft.resources.Identifier> getReceived() throws java.lang.IllegalStateException;
  public static java.util.Set<net.minecraft.resources.Identifier> getSendable() throws java.lang.IllegalStateException;
  public static boolean canSend(net.minecraft.resources.Identifier) throws java.lang.IllegalArgumentException;
  public static boolean canSend(net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type<?>);
  public static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.common.ServerCommonPacketListener> createServerboundPacket(T);
  public static net.fabricmc.fabric.api.networking.v1.PacketSender getSender() throws java.lang.IllegalStateException;
  public static void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload);
}
```

```java
public interface net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking$Context {
  public abstract net.minecraft.client.Minecraft client();
  public abstract net.minecraft.client.player.LocalPlayer player();
  public abstract net.fabricmc.fabric.api.networking.v1.PacketSender responseSender();
  public default net.fabricmc.fabric.api.networking.v1.context.PacketContext packetContext();
}
```

- Client sends with `ClientPlayNetworking.send(payload)` — no player/context argument.

### 1.7 `PacketSender`

```java
public interface net.fabricmc.fabric.api.networking.v1.PacketSender {
  public abstract net.minecraft.network.protocol.Packet<?> createPacket(net.minecraft.network.protocol.common.custom.CustomPacketPayload);
  public default void sendPacket(net.minecraft.network.protocol.Packet<?>);
  public default void sendPacket(net.minecraft.network.protocol.common.custom.CustomPacketPayload);
  public abstract void sendPacket(net.minecraft.network.protocol.Packet<?>, io.netty.channel.ChannelFutureListener);
  public default void sendPacket(net.minecraft.network.protocol.common.custom.CustomPacketPayload, io.netty.channel.ChannelFutureListener);
}
```

---

## 2. Server lifecycle, ticking, connection, entity events

### 2.1 `ServerLifecycleEvents`

```java
public final class net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents {
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStarting> SERVER_STARTING;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStarted> SERVER_STARTED;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStopping> SERVER_STOPPING;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStopped> SERVER_STOPPED;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$SyncDataPackContents> SYNC_DATA_PACK_CONTENTS;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$StartDataPackReload> START_DATA_PACK_RELOAD;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$EndDataPackReload> END_DATA_PACK_RELOAD;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$BeforeSave> BEFORE_SAVE;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$AfterSave> AFTER_SAVE;
  private net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents();
}
```

```java
public interface net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStarting {
  public abstract void onServerStarting(net.minecraft.server.MinecraftServer);
}
public interface net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStarted {
  public abstract void onServerStarted(net.minecraft.server.MinecraftServer);
}
public interface net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStopping {
  public abstract void onServerStopping(net.minecraft.server.MinecraftServer);
}
public interface net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStopped {
  public abstract void onServerStopped(net.minecraft.server.MinecraftServer);
}
public interface net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$BeforeSave {
  public abstract void onBeforeSave(net.minecraft.server.MinecraftServer, boolean, boolean);
}
```

- `SERVER_STARTED` = world is available (`server.overworld()` works); do SavedData/state bootstrap there.

### 2.2 `ServerTickEvents`

```java
public final class net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents {
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents$StartTick> START_SERVER_TICK;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents$EndTick> END_SERVER_TICK;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents$StartLevelTick> START_LEVEL_TICK;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents$EndLevelTick> END_LEVEL_TICK;
  private net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents();
}
```

```java
public interface net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents$EndTick {
  public abstract void onEndTick(net.minecraft.server.MinecraftServer);
}
public interface net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents$EndLevelTick {
  public abstract void onEndTick(net.minecraft.server.level.ServerLevel);
}
```

(Arg types also confirmed by the class's own bridges: `lambda$static$3(EndTick[], net.minecraft.server.MinecraftServer)`,
`lambda$static$7(EndLevelTick[], net.minecraft.server.level.ServerLevel)`.)

- `END_SERVER_TICK` gives `MinecraftServer`, `END_LEVEL_TICK` gives `ServerLevel`.

### 2.3 `ServerPlayConnectionEvents`

```java
public final class net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents {
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents$Init> INIT;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents$Join> JOIN;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents$Disconnect> DISCONNECT;
}
```

```java
public interface net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents$Join {
  public abstract void onPlayReady(net.minecraft.server.network.ServerGamePacketListenerImpl, net.fabricmc.fabric.api.networking.v1.PacketSender, net.minecraft.server.MinecraftServer);
}
```

```java
public interface net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents$Disconnect {
  public abstract void onPlayDisconnect(net.minecraft.server.network.ServerGamePacketListenerImpl, net.minecraft.server.MinecraftServer);
}
```

- `JOIN` lambda gets **3** args: `(ServerGamePacketListenerImpl conn, PacketSender sender, MinecraftServer server)`.
  The player is `conn.getPlayer()` (or `conn.player`).
- `DISCONNECT` gets 2: `(conn, server)`.

### 2.4 `ServerEntityEvents`

```java
public final class net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents {
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents$Load> ENTITY_LOAD;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents$AllowLoad> ALLOW_LOAD;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents$Unload> ENTITY_UNLOAD;
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents$EquipmentChange> EQUIPMENT_CHANGE;
}
```

Verified arg lists from bridges:
`lambda$static$1(Load[], net.minecraft.world.entity.Entity, net.minecraft.server.level.ServerLevel)`,
`lambda$static$5(Unload[], net.minecraft.world.entity.Entity, net.minecraft.server.level.ServerLevel)`,
`lambda$static$3(AllowLoad[], Entity, ServerLevel, net.minecraft.world.entity.EntitySpawnReason, boolean)`.

### 2.5 `CommandRegistrationCallback` — **3** args

```java
public interface net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback {
  public static final net.fabricmc.fabric.api.event.Event<net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback> EVENT;
  public abstract void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>, net.minecraft.commands.CommandBuildContext, net.minecraft.commands.Commands$CommandSelection);
}
```

- Lambda: `(dispatcher, registryAccess, environment) -> ...` — the middle arg is a `CommandBuildContext`,
  third is `Commands.CommandSelection`.

---

## 3. Commands

```java
public class com.mojang.brigadier.builder.LiteralArgumentBuilder<S> extends com.mojang.brigadier.builder.ArgumentBuilder<S, com.mojang.brigadier.builder.LiteralArgumentBuilder<S>> {
  public static <S> com.mojang.brigadier.builder.LiteralArgumentBuilder<S> literal(java.lang.String);
}
```

```java
public abstract class com.mojang.brigadier.builder.ArgumentBuilder<S, T extends com.mojang.brigadier.builder.ArgumentBuilder<S, T>> {
  public T then(com.mojang.brigadier.builder.ArgumentBuilder<S, ?>);
  public T then(com.mojang.brigadier.tree.CommandNode<S>);
  public T executes(com.mojang.brigadier.Command<S>);
  public T requires(java.util.function.Predicate<S>);
}
```

```java
public class com.mojang.brigadier.builder.RequiredArgumentBuilder<S, T> extends com.mojang.brigadier.builder.ArgumentBuilder<S, com.mojang.brigadier.builder.RequiredArgumentBuilder<S, T>> {
  public static <S, T> com.mojang.brigadier.builder.RequiredArgumentBuilder<S, T> argument(java.lang.String, com.mojang.brigadier.arguments.ArgumentType<T>);
}
```

```java
public class net.minecraft.commands.Commands {
  public static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> literal(java.lang.String);
  public static <T> com.mojang.brigadier.builder.RequiredArgumentBuilder<net.minecraft.commands.CommandSourceStack, T> argument(java.lang.String, com.mojang.brigadier.arguments.ArgumentType<T>);
}
```

```java
public class net.minecraft.commands.CommandSourceStack implements net.minecraft.commands.SharedSuggestionProvider, net.minecraft.commands.ExecutionCommandSource<net.minecraft.commands.CommandSourceStack> {
  public net.minecraft.world.phys.Vec3 getPosition();
  public net.minecraft.server.level.ServerLevel getLevel();
  public net.minecraft.server.level.ServerPlayer getPlayerOrException() throws com.mojang.brigadier.exceptions.CommandSyntaxException;
  public net.minecraft.server.level.ServerPlayer getPlayer();
  public net.minecraft.server.MinecraftServer getServer();
  public void sendSuccess(java.util.function.Supplier<net.minecraft.network.chat.Component>, boolean);
  public void sendFailure(net.minecraft.network.chat.Component);
}
```

- `sendSuccess` now takes a **`Supplier<Component>`** plus a `boolean` (broadcastToAdmins) —
  `source.sendSuccess(() -> Component.literal("ok"), false)`.
- `sendFailure(Component)` is unchanged; `getLevel()` returns `ServerLevel` directly.

---

## 4. SavedData

```java
public abstract class net.minecraft.world.level.saveddata.SavedData {
  public net.minecraft.world.level.saveddata.SavedData();
  public void setDirty();
  public void setDirty(boolean);
  public boolean isDirty();
}
```

```java
public final class net.minecraft.world.level.saveddata.SavedDataType<T extends net.minecraft.world.level.saveddata.SavedData> extends java.lang.Record {
  public net.minecraft.world.level.saveddata.SavedDataType(net.minecraft.resources.Identifier, java.util.function.Supplier<T>, com.mojang.serialization.Codec<T>, net.minecraft.util.datafix.DataFixTypes);
  public boolean equals(java.lang.Object);
  public int hashCode();
  public java.lang.String toString();
  public net.minecraft.resources.Identifier id();
  public java.util.function.Supplier<T> constructor();
  public com.mojang.serialization.Codec<T> codec();
  public net.minecraft.util.datafix.DataFixTypes dataFixType();
}
```

```java
public class net.minecraft.world.level.storage.SavedDataStorage implements java.lang.AutoCloseable {
  public net.minecraft.world.level.storage.SavedDataStorage(java.nio.file.Path, com.mojang.datafixers.DataFixer, net.minecraft.core.HolderLookup$Provider);
  public <T extends net.minecraft.world.level.saveddata.SavedData> T computeIfAbsent(net.minecraft.world.level.saveddata.SavedDataType<T>);
  public <T extends net.minecraft.world.level.saveddata.SavedData> T get(net.minecraft.world.level.saveddata.SavedDataType<T>);
  public <T extends net.minecraft.world.level.saveddata.SavedData> void set(net.minecraft.world.level.saveddata.SavedDataType<T>, T);
  public net.minecraft.nbt.CompoundTag readTagFromDisk(java.nio.file.Path, net.minecraft.util.datafix.DataFixTypes, int) throws java.io.IOException;
  public java.util.concurrent.CompletableFuture<?> scheduleSave();
  public void saveAndJoin();
  public void close();
}
```

```
net.minecraft.world.level.storage.DimensionDataStorage  ->  NOT FOUND in jar
```

Accessors (verbatim from their host classes):

```java
public net.minecraft.world.level.storage.SavedDataStorage net.minecraft.server.MinecraftServer.getDataStorage();
public final net.minecraft.server.level.ServerLevel net.minecraft.server.MinecraftServer.overworld();
public net.minecraft.server.level.ServerLevel net.minecraft.server.MinecraftServer.getLevel(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>);
public net.minecraft.world.level.storage.SavedDataStorage net.minecraft.server.level.ServerLevel.getDataStorage();
```

- `SavedData` is now **pure state**: no `save(CompoundTag)`, no `load` factory. Serialization is the
  `Codec<T>` you pass to `SavedDataType`, and you never call save — `setDirty()` schedules it.
- Get state with `server.overworld().getDataStorage().computeIfAbsent(TYPE)`; `TYPE` is a
  `SavedDataType<>(Identifier.fromNamespaceAndPath(...), MyData::new, MyData.CODEC, DataFixTypes.LEVEL)`.
- `DimensionDataStorage` is gone — if you copy any 1.21 snippet it will not compile.

---

## 5. Player

```java
public class net.minecraft.server.level.ServerPlayer extends net.minecraft.world.entity.player.Player {
  public net.minecraft.server.network.ServerGamePacketListenerImpl connection;      // public field
  public void teleportTo(double, double, double);
  public boolean teleportTo(net.minecraft.server.level.ServerLevel, double, double, double, java.util.Set<net.minecraft.world.entity.Relative>, float, float, boolean);
  public net.minecraft.server.level.ServerLevel level();                            // covariant ServerLevel
  public boolean setGameMode(net.minecraft.world.level.GameType);
  public void setServerLevel(net.minecraft.server.level.ServerLevel);
  public void setRespawnPosition(net.minecraft.server.level.ServerPlayer$RespawnConfig, boolean);
}
```

```
net.minecraft.server.level.ServerPlayer#serverLevel()  ->  NOT FOUND in jar
```
(use `player.level()`, which already returns `ServerLevel`.)

```java
public final class net.minecraft.world.entity.Relative extends java.lang.Enum<net.minecraft.world.entity.Relative> {
  public static final net.minecraft.world.entity.Relative X;
  public static final net.minecraft.world.entity.Relative Y;
  public static final net.minecraft.world.entity.Relative Z;
  public static final net.minecraft.world.entity.Relative Y_ROT;
  public static final net.minecraft.world.entity.Relative X_ROT;
  public static final net.minecraft.world.entity.Relative DELTA_X;
  public static final net.minecraft.world.entity.Relative DELTA_Y;
  public static final net.minecraft.world.entity.Relative DELTA_Z;
  public static final net.minecraft.world.entity.Relative ROTATE_DELTA;
  public static final java.util.Set<net.minecraft.world.entity.Relative> ALL;
  public static final java.util.Set<net.minecraft.world.entity.Relative> ROTATION;
  public static final java.util.Set<net.minecraft.world.entity.Relative> DELTA;
  public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, java.util.Set<net.minecraft.world.entity.Relative>> SET_STREAM_CODEC;
  public static net.minecraft.world.entity.Relative[] values();
  public static net.minecraft.world.entity.Relative valueOf(java.lang.String);
  public static java.util.Set<net.minecraft.world.entity.Relative> union(java.util.Set<net.minecraft.world.entity.Relative>...);
  public static java.util.Set<net.minecraft.world.entity.Relative> rotation(boolean, boolean);
}
```

```
net.minecraft.world.entity.Entity$Relative  ->  NOT FOUND in jar (it moved to net.minecraft.world.entity.Relative)
```

Inherited members that matter (declared on `net.minecraft.world.entity.Entity`):

```java
  public void setPos(double, double, double);
  public final double getX();
  public double getX(double);
  public final double getY();
  public double getY(double);
  public final double getZ();
  public double getZ(double);
  public net.minecraft.world.phys.Vec3 position();
  public net.minecraft.world.phys.Vec3 getDeltaMovement();
  public void setDeltaMovement(net.minecraft.world.phys.Vec3);
  public void setDeltaMovement(double, double, double);
  public void setInvisible(boolean);
  public net.minecraft.world.level.Level level();
  public net.minecraft.core.BlockPos blockPosition();
```

```
net.minecraft.world.entity.Entity#getServer()  ->  NOT FOUND in jar (no MinecraftServer reference on Entity)
```
Use `player.level().getServer()`.

```java
  // net.minecraft.world.entity.LivingEntity
  public final boolean addEffect(net.minecraft.world.effect.MobEffectInstance);
  public boolean addEffect(net.minecraft.world.effect.MobEffectInstance, net.minecraft.world.entity.Entity);
  public boolean removeEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>);
  public net.minecraft.world.effect.MobEffectInstance getEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>);
```

```java
  // net.minecraft.world.entity.player.Player
  public net.minecraft.world.entity.player.Inventory getInventory();
  public boolean isCreative();
```

```java
  // net.minecraft.world.entity.player.Inventory
  public void setItem(int, net.minecraft.world.item.ItemStack);
  public net.minecraft.world.item.ItemStack getItem(int);
  public void dropAll();
  public void clearContent();
```

```
net.minecraft.world.entity.player.Player#saveWithoutId  ->  NOT FOUND in jar
```

`MinecraftServer.getPlayerList()` / `net.minecraft.server.players.PlayerList`:

```java
public net.minecraft.server.players.PlayerList net.minecraft.server.MinecraftServer.getPlayerList();
```

```java
  public net.minecraft.server.level.ServerPlayer getPlayerByName(java.lang.String);
  public java.util.List<net.minecraft.server.level.ServerPlayer> getPlayersWithAddress(java.lang.String);
  public void broadcastSystemMessage(net.minecraft.network.chat.Component, boolean);
  public void broadcastSystemMessage(net.minecraft.network.chat.Component, java.util.function.Function<net.minecraft.server.level.ServerPlayer, net.minecraft.network.chat.Component>, boolean);
  public java.util.List<net.minecraft.server.level.ServerPlayer> getPlayers();
  public java.util.Map<java.util.UUID, net.minecraft.server.level.ServerPlayer> getPlayersByUUID();
  public net.minecraft.server.level.ServerPlayer getPlayer(java.util.UUID);
  public net.minecraft.server.level.ServerPlayer getPlayer(java.lang.String);
```

```java
  public java.util.List<net.minecraft.server.level.ServerPlayer> net.minecraft.server.level.ServerLevel.players();
```

- `teleportTo` needs a `ServerLevel` and a `Set<Relative>` (pass `java.util.Set.of()` for absolute) —
  the old `(x, y, z)` form exists but keeps the current dimension and does **not** do the full
  cross-dimension dance.
- `addEffect` is `final` on `LivingEntity`; the two-arg form takes a source `Entity`.
- `connection` is a public field of type `ServerGamePacketListenerImpl` (not a getter).

---

## 6. World sampling for a surface map

```java
public class net.minecraft.world.level.chunk.LevelChunk extends net.minecraft.world.level.chunk.ChunkAccess {
  public net.minecraft.world.level.chunk.LevelChunk(net.minecraft.world.level.Level, net.minecraft.world.level.ChunkPos);
  public net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos);
  public net.minecraft.world.level.material.FluidState getFluidState(net.minecraft.core.BlockPos);
  public net.minecraft.world.level.material.FluidState getFluidState(int, int, int);
}
```

```java
public abstract class net.minecraft.world.level.chunk.ChunkAccess implements net.minecraft.world.level.chunk.LightChunk, net.minecraft.world.level.chunk.StructureAccess, net.minecraft.world.level.biome.BiomeResolver {
  public int getHeight(net.minecraft.world.level.levelgen.Heightmap$Types, int, int);
  public int getMinY();
  public int getHeight();
  public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getNoiseBiome(int, int, int);
  public net.minecraft.world.level.chunk.LevelChunkSection[] getSections();
}
```

```java
  // net.minecraft.world.level.Level
  public net.minecraft.world.level.chunk.LevelChunk getChunk(int, int);                    // loads
  public net.minecraft.world.level.chunk.ChunkAccess getChunk(int, int, net.minecraft.world.level.chunk.status.ChunkStatus, boolean);
  public int getHeight(net.minecraft.world.level.levelgen.Heightmap$Types, int, int);
  public net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos);
  public boolean isLoaded(net.minecraft.core.BlockPos);
  public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension();
  public net.minecraft.world.level.chunk.ChunkSource getChunkSource();
```

```java
  // net.minecraft.world.level.chunk.ChunkSource
  public net.minecraft.world.level.chunk.LevelChunk getChunk(int, int, boolean);
  public net.minecraft.world.level.chunk.LevelChunk getChunkNow(int, int);
  public boolean hasChunk(int, int);
```

```java
  // net.minecraft.server.level.ServerChunkCache
  public net.minecraft.world.level.chunk.ChunkAccess getChunk(int, int, net.minecraft.world.level.chunk.status.ChunkStatus, boolean);
  public net.minecraft.world.level.chunk.LevelChunk getChunkNow(int, int);
  public boolean hasChunk(int, int);
```

```java
public final class net.minecraft.world.level.levelgen.Heightmap$Types extends java.lang.Enum<net.minecraft.world.level.levelgen.Heightmap$Types> implements net.minecraft.util.StringRepresentable {
  public static final net.minecraft.world.level.levelgen.Heightmap$Types WORLD_SURFACE_WG;
  public static final net.minecraft.world.level.levelgen.Heightmap$Types WORLD_SURFACE;
  public static final net.minecraft.world.level.levelgen.Heightmap$Types OCEAN_FLOOR_WG;
  public static final net.minecraft.world.level.levelgen.Heightmap$Types OCEAN_FLOOR;
  public static final net.minecraft.world.level.levelgen.Heightmap$Types MOTION_BLOCKING;
  public static final net.minecraft.world.level.levelgen.Heightmap$Types MOTION_BLOCKING_NO_LEAVES;
  public static final com.mojang.serialization.Codec<net.minecraft.world.level.levelgen.Heightmap$Types> CODEC;
  public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, net.minecraft.world.level.levelgen.Heightmap$Types> STREAM_CODEC;
  public static net.minecraft.world.level.levelgen.Heightmap$Types[] values();
  public java.lang.String getSerializationKey();
  public boolean sendToClient();
  public boolean keepAfterWorldgen();
  public java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> isOpaque();
}
```

```java
public class net.minecraft.world.level.block.state.BlockState extends net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase {
  public static final com.mojang.serialization.Codec<net.minecraft.world.level.block.state.BlockState> FULL_CODEC;
  public static final com.mojang.serialization.Codec<net.minecraft.world.level.block.state.BlockState> CODEC;
  public net.minecraft.world.level.block.state.BlockState(net.minecraft.world.level.block.Block, net.minecraft.world.level.block.state.properties.Property<?>[], java.lang.Comparable<?>[]);
}
```

```java
  // net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase   (inherited by BlockState)
  public net.minecraft.world.level.block.Block getBlock();
  public boolean isAir();
  public boolean isSolid();
  public boolean isSolidRender();
  public net.minecraft.world.level.material.MapColor getMapColor(net.minecraft.world.level.BlockGetter, net.minecraft.core.BlockPos);
  public net.minecraft.world.level.material.FluidState getFluidState();
  public int getLightEmission();
  public float getDestroySpeed(net.minecraft.world.level.BlockGetter, net.minecraft.core.BlockPos);
```

```java
public class net.minecraft.world.level.material.MapColor {
  public final int id;                                                            // public final FIELD
  public static final net.minecraft.world.level.material.MapColor NONE;           // + GRASS, SAND, ... 
  public static int getColorFromPackedId(int);
  public int calculateARGBColor(net.minecraft.world.level.material.MapColor$Brightness);
  public byte getPackedId(net.minecraft.world.level.material.MapColor$Brightness);
}
```

```java
public final class net.minecraft.world.level.material.MapColor$Brightness extends java.lang.Enum<net.minecraft.world.level.material.MapColor$Brightness> {
  public static final net.minecraft.world.level.material.MapColor$Brightness LOW;
  public static final net.minecraft.world.level.material.MapColor$Brightness NORMAL;
  public static final net.minecraft.world.level.material.MapColor$Brightness HIGH;
  public static final net.minecraft.world.level.material.MapColor$Brightness LOWEST;
}
```

```java
public class net.minecraft.core.BlockPos extends net.minecraft.core.Vec3i {
  public net.minecraft.core.BlockPos(int, int, int);
  public static net.minecraft.core.BlockPos containing(double, double, double);
  public static long offset(long, net.minecraft.core.Direction);
  public static long offset(long, int, int, int);
  public static int getX(long);
  public static int getY(long);
  public static int getZ(long);
  public long asLong();
  public static long asLong(int, int, int);
  public net.minecraft.core.BlockPos offset(int, int, int);
  public net.minecraft.core.BlockPos offset(net.minecraft.core.Vec3i);
  public net.minecraft.core.BlockPos immutable();
}

public class net.minecraft.core.Vec3i implements java.lang.Comparable<net.minecraft.core.Vec3i> {
  public int getX();
  public int getY();
  public int getZ();
}
```

- `Level#getChunk(int,int)` returns a **`LevelChunk`** (already-loaded or synchronously loaded); for
  non-blocking scans use `getChunkSource().getChunkNow(x, z)` and skip `null`.
- `getHeight(Heightmap.Types, x, z)` exists on both `Level` and `ChunkAccess`; `Heightmap.Types` is still
  an enum (values `WORLD_SURFACE`, `OCEAN_FLOOR`, `MOTION_BLOCKING`, …).
- `MapColor.id` is a public final **field** (no getter); `calculateARGBColor(Brightness)` gives the
  display color, `getColorFromPackedId(int)` decodes `getPackedId(Brightness)`.

---

## 7. Entities for the 3D reveal + particles

```java
public abstract class net.minecraft.world.entity.Display extends net.minecraft.world.entity.Entity {
  public net.minecraft.world.entity.Display(net.minecraft.world.entity.EntityType<?>, net.minecraft.world.level.Level);
  public net.minecraft.world.entity.Display$RenderState renderState();
  public net.minecraft.world.phys.AABB getInterpolatedBoundingBox(float);
  public float calculateInterpolationProgress(float);
  public void setPos(double, double, double);                                     // ONLY public setter
  public boolean affectedByCulling();
  public boolean isIgnoringBlockTriggers();
}
```

```java
public class net.minecraft.world.entity.Display$TextDisplay extends net.minecraft.world.entity.Display {
  public static final java.lang.String TAG_TEXT;
  public static final byte FLAG_SHADOW;
  public static final byte FLAG_SEE_THROUGH;
  public static final byte FLAG_USE_DEFAULT_BACKGROUND;
  public static final byte FLAG_ALIGN_LEFT;
  public static final byte FLAG_ALIGN_RIGHT;
  public static final int INITIAL_BACKGROUND;
  public net.minecraft.world.entity.Display$TextDisplay(net.minecraft.world.entity.EntityType<?>, net.minecraft.world.level.Level);
  public net.minecraft.world.entity.Display$TextDisplay$TextRenderState textRenderState();
  public net.minecraft.world.entity.Display$TextDisplay$CachedInfo cacheDisplay(net.minecraft.world.entity.Display$TextDisplay$LineSplitter);
  public static net.minecraft.world.entity.Display$TextDisplay$Align getAlign(byte);
}
```

```java
public final class net.minecraft.world.entity.Display$TextDisplay$TextRenderState extends java.lang.Record {
  public net.minecraft.world.entity.Display$TextDisplay$TextRenderState(net.minecraft.network.chat.Component, int, net.minecraft.world.entity.Display$IntInterpolator, net.minecraft.world.entity.Display$IntInterpolator, byte);
  public net.minecraft.network.chat.Component text();
  public int lineWidth();
  public net.minecraft.world.entity.Display$IntInterpolator textOpacity();
  public net.minecraft.world.entity.Display$IntInterpolator backgroundColor();
  public byte flags();
}
```

```java
public final class net.minecraft.world.entity.Display$RenderState extends java.lang.Record {
  public net.minecraft.world.entity.Display$RenderState(net.minecraft.world.entity.Display$GenericInterpolator<com.mojang.math.Transformation>, net.minecraft.world.entity.Display$BillboardConstraints, int, net.minecraft.world.entity.Display$FloatInterpolator, net.minecraft.world.entity.Display$FloatInterpolator, int);
  public net.minecraft.world.entity.Display$GenericInterpolator<com.mojang.math.Transformation> transformation();
  public net.minecraft.world.entity.Display$BillboardConstraints billboardConstraints();
  public int brightnessOverride();
  public net.minecraft.world.entity.Display$FloatInterpolator shadowRadius();
  public net.minecraft.world.entity.Display$FloatInterpolator shadowStrength();
  public int glowColorOverride();
}
```

```java
public final class net.minecraft.world.entity.Display$BillboardConstraints extends java.lang.Enum<net.minecraft.world.entity.Display$BillboardConstraints> implements net.minecraft.util.StringRepresentable {
  public static final net.minecraft.world.entity.Display$BillboardConstraints FIXED;
  public static final net.minecraft.world.entity.Display$BillboardConstraints VERTICAL;
  public static final net.minecraft.world.entity.Display$BillboardConstraints HORIZONTAL;
  public static final net.minecraft.world.entity.Display$BillboardConstraints CENTER;
  public static final com.mojang.serialization.Codec<net.minecraft.world.entity.Display$BillboardConstraints> CODEC;
  public static final java.util.function.IntFunction<net.minecraft.world.entity.Display$BillboardConstraints> BY_ID;
}
```

**`Display` setters are all `private`** (verified with `javap -p`, e.g.):

```
private void setTransformation(com.mojang.math.Transformation);
private void setTransformationInterpolationDuration(int);
private void setTransformationInterpolationDelay(int);
private void setPosRotInterpolationDuration(int);
private void setBillboardConstraints(net.minecraft.world.entity.Display$BillboardConstraints);
private void setBrightnessOverride(net.minecraft.util.Brightness);
private void setViewRange(float);
private void setShadowRadius(float);
private void setShadowStrength(float);
private void setWidth(float);
private void setHeight(float);
private void setGlowColorOverride(int);
// Display$TextDisplay:
private void setText(net.minecraft.network.chat.Component);
private int  getLineWidth();          private void setLineWidth(int);
private byte getTextOpacity();        private void setTextOpacity(byte);
private void setBackgroundColor(int);
private byte getFlags();              private void setFlags(byte);
```

- **This is the biggest blocker in this file**: `TextDisplay#setText`, `setBackgroundColor`,
  `setBillboardConstraints`, `setTransformation`, `setViewRange`, … are all `private` in 26.3. To drive
  them from a mod you need a Fabric **access widener** (project currently has none:
  `src/main/resources` only holds `assets`, `craftguessr.mixins.json`, `fabric.mod.json`) or a mixin
  `@Invoker`/`@Accessor`.
- The readable surface is `textRenderState()` / `renderState()` returning immutable records — good for
  reading state, useless for setting it.

```java
  // net.minecraft.world.entity.EntityType<T>
  public T spawn(net.minecraft.server.level.ServerLevel, net.minecraft.core.BlockPos, net.minecraft.world.entity.EntitySpawnReason);
  public T spawn(net.minecraft.server.level.ServerLevel, net.minecraft.world.item.ItemStack, net.minecraft.world.entity.LivingEntity, net.minecraft.core.BlockPos, net.minecraft.world.entity.EntitySpawnReason, boolean, boolean);
  public T spawn(net.minecraft.server.level.ServerLevel, net.minecraft.world.entity.PostSpawnProcessor<T>, net.minecraft.core.BlockPos, net.minecraft.world.entity.EntitySpawnReason, boolean, boolean);
  public T create(net.minecraft.world.level.Level, net.minecraft.world.entity.EntitySpawnReason);
  public T create(net.minecraft.server.level.ServerLevel, net.minecraft.world.entity.PostSpawnProcessor<T>, net.minecraft.core.BlockPos, net.minecraft.world.entity.EntitySpawnReason, boolean, boolean);
```

```java
  // net.minecraft.server.level.ServerLevel
  public boolean addFreshEntity(net.minecraft.world.entity.Entity);
  public boolean addWithUUID(net.minecraft.world.entity.Entity);
```

```
net.minecraft.world.level.Level#addFreshEntity  ->  NOT FOUND in jar (it lives on ServerLevel)
```

```java
  // net.minecraft.server.level.ServerLevel#sendParticles
  public <T extends net.minecraft.core.particles.ParticleOptions> int sendParticles(T, double, double, double, int, double, double, double, double);
  public <T extends net.minecraft.core.particles.ParticleOptions> int sendParticles(T, double, double, double, int, double, double, double, double, net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket$RandomizationType);
  public <T extends net.minecraft.core.particles.ParticleOptions> int sendParticles(T, double, double, double, int, double, double, double, double, double, double);
  public <T extends net.minecraft.core.particles.ParticleOptions> int sendParticles(T, double, double, double, int, double, double, double, double, double, double, net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket$RandomizationType);
  public <T extends net.minecraft.core.particles.ParticleOptions> int sendParticles(T, boolean, boolean, double, double, double, int, double, double, double, double);
  public <T extends net.minecraft.core.particles.ParticleOptions> boolean sendParticles(net.minecraft.server.level.ServerPlayer, T, boolean, boolean, double, double, double, int, double, double, double, double);
```

```java
  // net.minecraft.core.particles.ParticleTypes  (all SimpleParticleType unless noted)
  public static final net.minecraft.core.particles.SimpleParticleType END_ROD;
  public static final net.minecraft.core.particles.SimpleParticleType FIREWORK;
  public static final net.minecraft.core.particles.SimpleParticleType FLAME;
  public static final net.minecraft.core.particles.SimpleParticleType SOUL_FIRE_FLAME;
  public static final net.minecraft.core.particles.SimpleParticleType HAPPY_VILLAGER;
  public static final net.minecraft.core.particles.SimpleParticleType CRIT;
  public static final net.minecraft.core.particles.SimpleParticleType ENCHANT;
  public static final net.minecraft.core.particles.SimpleParticleType TOTEM_OF_UNDYING;
  public static final net.minecraft.core.particles.SimpleParticleType SMOKE;
  public static final net.minecraft.core.particles.ParticleType<net.minecraft.core.particles.DustParticleOptions> DUST;
```

```java
public class net.minecraft.world.phys.Vec3 implements net.minecraft.core.Position {
  public final double x;
  public final double y;
  public final double z;
  public net.minecraft.world.phys.Vec3(double, double, double);
  public net.minecraft.world.phys.Vec3(org.joml.Vector3fc);
  public net.minecraft.world.phys.Vec3(net.minecraft.core.Vec3i);
  public final double x();
  public final double y();
  public final double z();
}
```

- Spawn path: build the entity, then `serverLevel.addFreshEntity(entity)`; `EntityType#spawn` covers the
  common `(ServerLevel, BlockPos, EntitySpawnReason)` case. `EntitySpawnReason.EVENT` / `MOB_SUMMONED`
  exist as constants.
- `sendParticles` classic form is `(type, x, y, z, count, xDist, yDist, zDist, speed)` → returns `int`;
  there is a new `RandomizationType` variant if you need deterministic offsets.

---

## 8. Persistence of blocks/entities

Skipped per task spec.

---

## 9. `ServerLevel` — seed, dimension, bounds

```java
public class net.minecraft.server.level.ServerLevel extends net.minecraft.world.level.Level implements net.minecraft.world.level.WorldGenLevel, net.minecraft.server.level.ServerEntityGetter {
  public net.minecraft.server.MinecraftServer getServer();
  public long getSeed();
  public net.minecraft.server.level.ServerChunkCache getChunkSource();
  public net.minecraft.world.level.storage.SavedDataStorage getDataStorage();
  public java.util.List<net.minecraft.server.level.ServerPlayer> players();
  public void tick(java.util.function.BooleanSupplier);
  public void tickChunk(net.minecraft.world.level.chunk.LevelChunk, int);
}
```

```
net.minecraft.server.level.ServerLevel#getMinBuildHeight  ->  NOT FOUND in jar
net.minecraft.server.level.ServerLevel#getMaxBuildHeight  ->  NOT FOUND in jar
```

Height accessors come from `LevelHeightAccessor` (reached via `Level → LevelAccessor → CommonLevelAccessor
→ LevelReader → BlockAndLightGetter → BlockGetter → LevelHeightAccessor`; verified:
`public interface net.minecraft.world.level.BlockGetter extends net.minecraft.world.level.LevelHeightAccessor`):

```java
public interface net.minecraft.world.level.LevelHeightAccessor {
  public abstract int getHeight();
  public abstract int getMinY();
  public default int getMaxY();
  public default int getSectionsCount();
  public default int getMinSectionY();
  public default int getMaxSectionY();
  public default boolean isInsideBuildHeight(net.minecraft.core.BlockPos);
  public default boolean isOutsideBuildHeight(net.minecraft.core.BlockPos);
  public static net.minecraft.world.level.LevelHeightAccessor create(int, int);
}
```

- `getMinBuildHeight()`/`getMaxBuildHeight()` are **gone** → `getMinY()` / `getMaxY()`; total height is
  `getHeight()`.
- `dimension()` is inherited from `Level`: `public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension();`

```java
  // net.minecraft.world.level.Level
  public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> OVERWORLD;
  public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> NETHER;
  public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> END;
  public static final com.mojang.serialization.Codec<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> RESOURCE_KEY_CODEC;
  public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension();
```

---

## 10. `ResourceKey` + `Registries`, and the `Identifier` rename

```
net.minecraft.resources.ResourceLocation  ->  NOT FOUND in jar  (only net/minecraft/resources/Identifier.class exists)
```

```java
public final class net.minecraft.resources.Identifier implements java.lang.Comparable<net.minecraft.resources.Identifier> {
  public static net.minecraft.resources.Identifier fromNamespaceAndPath(java.lang.String, java.lang.String);
  public static net.minecraft.resources.Identifier parse(java.lang.String);
  public static net.minecraft.resources.Identifier withDefaultNamespace(java.lang.String);
  public static net.minecraft.resources.Identifier tryParse(java.lang.String);
  public java.lang.String getPath();
  public java.lang.String getNamespace();
  public static boolean isAllowedInIdentifier(char);
}
```

```java
public class net.minecraft.resources.ResourceKey<T> {
  public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>>> REGISTRY_STREAM_CODEC;
  public static <T> com.mojang.serialization.Codec<net.minecraft.resources.ResourceKey<T>> codec(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>>);
  public static <T> net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, net.minecraft.resources.ResourceKey<T>> streamCodec(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>>);
  public static <T> net.minecraft.resources.ResourceKey<T> create(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>>, net.minecraft.resources.Identifier);
  public static <T> net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>> createRegistryKey(net.minecraft.resources.Identifier);
  public boolean isFor(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>>);
  public net.minecraft.resources.Identifier identifier();
  public net.minecraft.resources.Identifier registry();
  public net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>> registryKey();
}
```

```java
  // net.minecraft.core.registries.Registries
  public static final net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<net.minecraft.world.level.Level>> DIMENSION;
  public static final net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<net.minecraft.world.level.dimension.LevelStem>> LEVEL_STEM;
  public static final net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<net.minecraft.world.level.dimension.DimensionType>> DIMENSION_TYPE;
  public static final net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<net.minecraft.world.level.biome.Biome>> BIOME;
```

- Custom dimension key:
  `ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(ns, "arena"))`, then
  `server.getLevel(key)` (returns `null` if that dimension isn't loaded/registered).
- Vanilla dimensions: `Level.OVERWORLD` / `Level.NETHER` / `Level.END`, and `server.overworld()`.

---

## Appendix — compile-blocking deltas vs 1.21-era code

| 1.21-era | 26.3 reality (verified) |
|---|---|
| `ResourceLocation` | `net.minecraft.resources.Identifier` |
| `buf.writeResourceLocation` | `buf.writeIdentifier` |
| `CustomPacketPayload#getId()` | `#type()` returning `CustomPacketPayload.Type<T>` |
| `new CustomPacketPayload.Type<>(ResourceLocation)` | `new CustomPacketPayload.Type<>(Identifier)` |
| MC `PayloadTypeRegistry.playC2S()` | MC class gone; Fabric `PayloadTypeRegistry.serverboundPlay()` |
| `PayloadTypeRegistry.register(...)` returns void | returns `CustomPacketPayload.TypeAndCodec<? super B,T>` |
| `ServerPlayNetworking.send(player, id, buf)` | `send(ServerPlayer, CustomPacketPayload)` |
| receiver `(payload, context)` w/ `context.player()` | same shape, context is `ServerPlayNetworking.Context` |
| `Entity.Relative` | `net.minecraft.world.entity.Relative` |
| `teleportTo(x,y,z)` / varargs `Relative` | `teleportTo(ServerLevel, x,y,z, Set<Relative>, yaw, pitch, boolean)` |
| `player.serverLevel()` | `player.level()` (already `ServerLevel`) |
| `player.getServer()` | `player.level().getServer()` |
| `DimensionDataStorage` | `net.minecraft.world.level.storage.SavedDataStorage` |
| `SavedData#save(CompoundTag)` / `load` | codec-based `SavedDataType(Identifier, Supplier, Codec, DataFixTypes)` + `computeIfAbsent` |
| `level.getMinBuildHeight()` / `getMaxBuildHeight()` | `getMinY()` / `getMaxY()` |
| `source.sendSuccess(Component, boolean)` | `sendSuccess(Supplier<Component>, boolean)` |
| `CommandRegistrationCallback` 2 args | 3 args (`dispatcher, CommandBuildContext, CommandSelection`) |
| `Level#addFreshEntity` | `ServerLevel#addFreshEntity` |
| `TextDisplay#setText/setBackgroundColor/...` public | **private** → needs access widener / mixin |
