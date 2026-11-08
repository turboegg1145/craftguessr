#!/usr/bin/env bash
# Dump signatures of MC classes from the deobf jar inside the dev container.
# usage: scripts/javap.sh net.minecraft.resources.Identifier [...]
set -u
MCJAR=/home/dev/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common-deobf/26.3/minecraft-common-deobf-26.3.jar
CLJAR=/home/dev/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/26.3/minecraft-clientonly-deobf-26.3.jar
docker exec geoguessr-dev javap -p -classpath "$MCJAR:$CLJAR" "$@"
