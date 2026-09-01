package net.minelight.fabric.block;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minelight.fabric.MineLightMod;

import java.util.function.Function;

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

    public static Block NOTE;
    public static Block SOUND_METER;
    public static Block BEAT;
    public static Block SPECTRUM;

    public static void register() {
        DIMMER = register("dimmer_block", DimmerBlock::new);
        RGB = register("rgb_block", RgbBlock::new);
        EVENT = register("event_block", EventBlock::new);
        FEEDBACK = register("feedback_block", FeedbackBlock::new);

        NOTE = register("note_fixture_block", NoteBlockFixture::new);
        SOUND_METER = register("sound_meter_block", SoundMeterBlock::new);
        BEAT = register("beat_block", BeatBlock::new);
        SPECTRUM = register("spectrum_block", SpectrumBlock::new);
    }

    private static BlockBehaviour.Properties fixtureProperties() {
        // like a redstone lamp: solid, opaque, mineable. The copied light level
        // has to be replaced: the lamp's reads its LIT property, and these
        // blocks do not declare one, so building their states would throw.
        return BlockBehaviour.Properties.ofFullCopy(Blocks.REDSTONE_LAMP)
                .lightLevel(state -> 0);
    }

    // Blocks and items resolve their description id from the registry key, so
    // the key has to be on the properties before the instance is constructed.
    private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory) {
        Identifier id = Identifier.fromNamespaceAndPath(MineLightMod.MOD_ID, name);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);

        Block block = factory.apply(fixtureProperties().setId(blockKey));
        Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey)));
        return block;
    }
}
