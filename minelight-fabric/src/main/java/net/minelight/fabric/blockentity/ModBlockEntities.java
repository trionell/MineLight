package net.minelight.fabric.blockentity;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
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
        DIMMER = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MineLightMod.MOD_ID, "dimmer"),
                BlockEntityType.Builder.create(
                        (pos, state) -> new FixtureBlockEntity(pos, state, FixtureBlock.Type.DIMMER),
                        ModBlocks.DIMMER).build());
        RGB = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MineLightMod.MOD_ID, "rgb"),
                BlockEntityType.Builder.create(
                        (pos, state) -> new FixtureBlockEntity(pos, state, FixtureBlock.Type.RGB),
                        ModBlocks.RGB).build());
        EVENT = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MineLightMod.MOD_ID, "event"),
                BlockEntityType.Builder.create(
                        (pos, state) -> new FixtureBlockEntity(pos, state, FixtureBlock.Type.EVENT),
                        ModBlocks.EVENT).build());
        FEEDBACK = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MineLightMod.MOD_ID, "feedback"),
                BlockEntityType.Builder.create(
                        (pos, state) -> new FixtureBlockEntity(pos, state, FixtureBlock.Type.FEEDBACK),
                        ModBlocks.FEEDBACK).build());

        NOTE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MineLightMod.MOD_ID, "note_fixture"),
                BlockEntityType.Builder.create(
                        (pos, state) -> new SoundBlockEntity(pos, state,
                                net.minelight.core.sound.SoundEngine.Mode.NOTE),
                        ModBlocks.NOTE).build());
        SOUND_METER = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MineLightMod.MOD_ID, "sound_meter"),
                BlockEntityType.Builder.create(
                        (pos, state) -> new SoundBlockEntity(pos, state,
                                net.minelight.core.sound.SoundEngine.Mode.LEVEL),
                        ModBlocks.SOUND_METER).build());
        BEAT = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MineLightMod.MOD_ID, "beat"),
                BlockEntityType.Builder.create(
                        (pos, state) -> new SoundBlockEntity(pos, state,
                                net.minelight.core.sound.SoundEngine.Mode.BEAT),
                        ModBlocks.BEAT).build());
        SPECTRUM = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MineLightMod.MOD_ID, "spectrum"),
                BlockEntityType.Builder.create(
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
