package net.minelight.fabric;

import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minelight.core.api.FixtureBlock;
import net.minelight.core.engine.ConsoleEngine;
import net.minelight.fabric.block.FeedbackBlock;

/**
 * Server-tick bridge between Minecraft's redstone and the engine's
 * {@link net.minelight.core.engine.FixtureBlockRegistry}.
 *
 * <p>Each tick it hands the registry a {@code PowerReader} that reports the
 * redstone power on any face of any block, then pushes feedback levels back
 * into the world by updating feedback blocks' emitted power.</p>
 */
public final class FixtureBlockBridge {

    private final ConsoleEngine engine;

    public FixtureBlockBridge(ConsoleEngine engine) {
        this.engine = engine;
    }

    public void tick(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        engine.blocks().tick((x, y, z, side) -> readPower(world, x, y, z, side));

        // feedback: write console levels back into the world
        for (FixtureBlock b : engine.blocks().all()) {
            if (b.type() != FixtureBlock.Type.FEEDBACK) {
                continue;
            }
            BlockPos pos = new BlockPos(b.x(), b.y(), b.z());
            BlockState state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof FeedbackBlock)) {
                continue;
            }
            int level = engine.blocks().feedbackLevel(b.id());
            int current = state.get(Properties.POWER);
            if (current != level) {
                world.setBlockState(pos, state.with(Properties.POWER, level));
            }
        }
    }

    private static int readPower(ServerWorld world, int x, int y, int z, FixtureBlock.Side side) {
        BlockPos pos = new BlockPos(x, y, z);
        if (side == FixtureBlock.Side.ANY) {
            int max = 0;
            for (Direction d : Direction.values()) {
                max = Math.max(max, world.getEmittedRedstonePower(pos.offset(d), d.getOpposite()));
            }
            return max;
        }
        Direction d = toDirection(side);
        return world.getEmittedRedstonePower(pos.offset(d), d.getOpposite());
    }

    private static Direction toDirection(FixtureBlock.Side side) {
        return switch (side) {
            case NORTH -> Direction.NORTH;
            case EAST -> Direction.EAST;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
            case ANY -> Direction.UP; // unused
        };
    }
}
