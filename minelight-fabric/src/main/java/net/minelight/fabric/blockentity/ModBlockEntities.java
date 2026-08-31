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
}
