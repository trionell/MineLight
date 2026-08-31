package net.minelight.fabric.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minelight.fabric.MineLightMod;

/**
 * The four MineLight fixture blocks.
 */
public final class ModBlocks {

    private ModBlocks() {
    }

    public static Block DIMMER;
    public static Block RGB;
    public static Block EVENT;
    public static Block FEEDBACK;

    public static void register() {
        DIMMER = register("dimmer_block", new DimmerBlock(fixtureSettings()));
        RGB = register("rgb_block", new RgbBlock(fixtureSettings()));
        EVENT = register("event_block", new EventBlock(fixtureSettings()));
        FEEDBACK = register("feedback_block", new FeedbackBlock(fixtureSettings()));
    }

    private static AbstractBlock.Settings fixtureSettings() {
        // like a redstone lamp: solid, opaque, mineable
        return AbstractBlock.Settings.copy(Blocks.REDSTONE_LAMP);
    }

    private static Block register(String name, Block block) {
        Identifier id = Identifier.of(MineLightMod.MOD_ID, name);
        Registry.register(Registries.BLOCK, id, block);
        Registry.register(Registries.ITEM, id,
                new BlockItem(block, new Item.Settings()));
        return block;
    }
}
