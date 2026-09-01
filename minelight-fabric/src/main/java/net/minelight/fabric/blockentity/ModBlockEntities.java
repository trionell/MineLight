package net.minelight.fabric.blockentity;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minelight.core.api.FixtureBlock;
import net.minelight.fabric.MineLightMod;
import net.minelight.fabric.block.ModBlocks;

/**
 * Block entity types for the four fixture blocks.
 */
public final class ModBlockEntities {

    private ModBlockEntities() {
    }

    public static BlockEntityType<FixtureBlockEntity> DIMMER;
    public static BlockEntityType<FixtureBlockEntity> RGB;
    public static BlockEntityType<FixtureBlockEntity> EVENT;
    public static BlockEntityType<FixtureBlockEntity> FEEDBACK;

    public static BlockEntityType<SoundBlockEntity> NOTE;
    public static BlockEntityType<SoundBlockEntity> SOUND_METER;
    public static BlockEntityType<SoundBlockEntity> BEAT;
    public static BlockEntityType<SoundBlockEntity> SPECTRUM;

    public static void register() {
        DIMMER = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MineLightMod.MOD_ID, "dimmer"),
                FabricBlockEntityTypeBuilder.create(
                        (pos, state) -> new FixtureBlockEntity(pos, state, FixtureBlock.Type.DIMMER),
                        ModBlocks.DIMMER).build());
        RGB = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MineLightMod.MOD_ID, "rgb"),
                FabricBlockEntityTypeBuilder.create(
                        (pos, state) -> new FixtureBlockEntity(pos, state, FixtureBlock.Type.RGB),
                        ModBlocks.RGB).build());
        EVENT = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MineLightMod.MOD_ID, "event"),
                FabricBlockEntityTypeBuilder.create(
                        (pos, state) -> new FixtureBlockEntity(pos, state, FixtureBlock.Type.EVENT),
                        ModBlocks.EVENT).build());
        FEEDBACK = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MineLightMod.MOD_ID, "feedback"),
                FabricBlockEntityTypeBuilder.create(
                        (pos, state) -> new FixtureBlockEntity(pos, state, FixtureBlock.Type.FEEDBACK),
                        ModBlocks.FEEDBACK).build());

        NOTE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MineLightMod.MOD_ID, "note_fixture"),
                FabricBlockEntityTypeBuilder.create(
                        (pos, state) -> new SoundBlockEntity(pos, state,
                                net.minelight.core.sound.SoundEngine.Mode.NOTE),
                        ModBlocks.NOTE).build());
        SOUND_METER = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MineLightMod.MOD_ID, "sound_meter"),
                FabricBlockEntityTypeBuilder.create(
                        (pos, state) -> new SoundBlockEntity(pos, state,
                                net.minelight.core.sound.SoundEngine.Mode.LEVEL),
                        ModBlocks.SOUND_METER).build());
        BEAT = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MineLightMod.MOD_ID, "beat"),
                FabricBlockEntityTypeBuilder.create(
                        (pos, state) -> new SoundBlockEntity(pos, state,
                                net.minelight.core.sound.SoundEngine.Mode.BEAT),
                        ModBlocks.BEAT).build());
        SPECTRUM = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MineLightMod.MOD_ID, "spectrum"),
                FabricBlockEntityTypeBuilder.create(
                        (pos, state) -> new SoundBlockEntity(pos, state,
                                net.minelight.core.sound.SoundEngine.Mode.SPECTRUM),
                        ModBlocks.SPECTRUM).build());
    }

    /** Resolve the block entity type for an engine fixture type. */
    public static BlockEntityType<FixtureBlockEntity> forType(FixtureBlock.Type type) {
        return switch (type) {
            case DIMMER -> DIMMER;
            case RGB -> RGB;
            case EVENT -> EVENT;
            case FEEDBACK -> FEEDBACK;
        };
    }

    /** Resolve the block entity type for a sound mode. */
    public static BlockEntityType<SoundBlockEntity> forSoundMode(net.minelight.core.sound.SoundEngine.Mode mode) {
        return switch (mode) {
            case NOTE -> NOTE;
            case LEVEL -> SOUND_METER;
            case BEAT -> BEAT;
            case SPECTRUM -> SPECTRUM;
        };
    }
}
