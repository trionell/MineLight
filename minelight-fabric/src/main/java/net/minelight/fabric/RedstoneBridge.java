package net.minelight.fabric;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minelight.core.engine.ConsoleEngine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Polls patched fixtures' block positions for redstone power and pushes
 * changes into the engine as {@code redstone.on}/{@code redstone.off}
 * events. Runs on the server thread each tick, but only scans patched
 * positions, so cost stays O(#fixtures).
 */
public final class RedstoneBridge {

    private final ConsoleEngine engine;
    private final Map<Integer, Boolean> lastState = new ConcurrentHashMap<>();

    public RedstoneBridge(ConsoleEngine engine) {
        this.engine = engine;
    }

    public void tick(MinecraftServer server) {
        ServerLevel world = server.overworld();
        for (var f : engine.patch().fixtures()) {
            if (!f.redstone()) {
                continue;
            }
            BlockPos pos = new BlockPos(f.x(), f.y(), f.z());
            boolean powered = world.hasNeighborSignal(pos);
            Boolean prev = lastState.get(f.id());
            if (prev == null || prev != powered) {
                lastState.put(f.id(), powered);
                engine.setRedstone(f.id(), powered);
            }
        }
    }
}
