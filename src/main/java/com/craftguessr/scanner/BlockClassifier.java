package com.craftguessr.scanner;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Heuristic natural/man-made split, keyed off registry names. Deliberately dependency-free: tags
 * change every version and break silently, block ids are stable and readable.
 */
public final class BlockClassifier {

    private static final String[] MANUFACTURED = {
            "planks", "bricks", "brick", "glass", "wool", "concrete", "terracotta", "stair", "slab",
            "door", "fence", "pane", "carpet", "bed", "chest", "barrel", "furnace", "smithing",
            "anvil", "lantern", "torch", "ladder", "rail", "button", "lever", "piston", "observer",
            "hopper", "cauldron", "beacon", "bell", "campfire", "sign", "banner", "pot", "table",
            "bookshelf", "loom", "stonecutter", "grindstone", "smoker", "composter", "brewing",
            "enchanting", "respawn_anchor", "jukebox", "note_block", "trapdoor", "wall", "chain",
            "iron_bars", "scaffolding", "conduit", "light", "candle", "amethyst_cluster", "copper_grate"
    };

    private static final String[] NATURAL = {
            "stone", "deepslate", "dirt", "grass", "sand", "sandstone", "gravel", "clay", "log",
            "leaves", "wood", "water", "lava", "snow", "ice", "moss", "vine", "flower", "fern",
            "bamboo", "cactus", "mushroom", "kelp", "seagrass", "coral", "tuff", "calcite", "basalt",
            "blackstone", "obsidian", "netherrack", "soul_", "end_stone", "purpur", "magma", "sculk",
            "glow_lichen", "amethyst_block", "budding_amethyst", "spore", "mycelium", "podzol",
            "mud", "snow_block", "powder_snow", "leaf_litter", "azalea", "dripleaf", "glowstone",
            "pointed_dripstone", "dripstone_block", "rooted_dirt", "hanging_roots", "crimson_",
            "warped_", "nether_wart", "chorus", "bedrock", "diorite", "andesite", "granite"
    };

    private BlockClassifier() {
    }

    public static String id(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    public static String path(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }

    public static boolean isArtificial(BlockState state) {
        String path = path(state);
        for (String keyword : MANUFACTURED) {
            if (path.contains(keyword)) {
                return true;
            }
        }
        if (state.hasBlockEntity()) {
            for (String keyword : NATURAL) {
                if (path.contains(keyword)) {
                    return false;
                }
            }
            return true;
        }
        for (String keyword : NATURAL) {
            if (path.contains(keyword)) {
                return false;
            }
        }
        return false;
    }

    public static boolean isNatural(BlockState state) {
        return !isArtificial(state);
    }
}
