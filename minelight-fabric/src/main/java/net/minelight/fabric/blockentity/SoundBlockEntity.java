package net.minelight.fabric.blockentity;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minelight.fabric.screen.SoundMenuData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minelight.core.sound.SoundEngine;
import net.minelight.fabric.MineLightMod;
import net.minelight.fabric.screen.SoundScreenHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Persists a sound fixture block's config (radius, gain, channel, thresholds)
 * and registers it with the engine's {@code SoundEngine}.
 */
public class SoundBlockEntity extends BlockEntity implements ExtendedMenuProvider<SoundMenuData> {

    private final SoundEngine.Mode mode;
    private int engineId = -1;

    // config (persisted; applied to the engine block on registration)
    public int radius = 8;
    public int universe = 1;
    public int channel = 1;
    public double gain = 1.0;
    public double decay = 0.15;
    public double beatThreshold = 1.6;

    public SoundBlockEntity(BlockPos pos, BlockState state, SoundEngine.Mode mode) {
        super(ModBlockEntities.forSoundMode(mode), pos, state);
        this.mode = mode;
    }

    public SoundEngine.Mode mode() {
        return mode;
    }

    public int engineId() {
        return engineId;
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (engineId < 0) {
            registerWithEngine();
        }
    }

    private void registerWithEngine() {
        var engine = MineLightMod.engine();
        if (engine == null) {
            return;
        }
        // reuse an existing engine block at this position if present
        for (var b : engine.sound().all()) {
            if (b.x == worldPosition.getX() && b.y == worldPosition.getY() && b.z == worldPosition.getZ()) {
                engineId = b.id;
                return;
            }
        }
        var b = engine.sound().add(mode, defaultName(), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        engineId = b.id;
        applyTo(b);
        engine.save();
    }

    private void applyTo(SoundEngine.SoundBlock b) {
        b.radius = radius;
        b.universe = universe;
        b.channel = channel;
        b.gain = gain;
        b.decay = decay;
        b.beatThreshold = beatThreshold;
    }

    private String defaultName() {
        return switch (mode) {
            case NOTE -> "Note";
            case LEVEL -> "Meter";
            case BEAT -> "Beat";
            case SPECTRUM -> "Spectrum";
        } + " " + worldPosition.getX() + "," + worldPosition.getY() + "," + worldPosition.getZ();
    }

    public void unregister() {
        var engine = MineLightMod.engine();
        if (engine != null && engineId >= 0) {
            engine.sound().remove(engineId);
            engine.save();
        }
        engineId = -1;
    }

    /** Push a config edit from the GUI into the engine and persist. */
    public void applyConfig(int radius, int universe, int channel,
                            double gain, double decay, double beatThreshold) {
        this.radius = radius;
        this.universe = universe;
        this.channel = channel;
        this.gain = gain;
        this.decay = decay;
        this.beatThreshold = beatThreshold;
        var engine = MineLightMod.engine();
        if (engine != null && engineId >= 0) {
            var b = engine.sound().byId(engineId);
            if (b != null) {
                applyTo(b);
                engine.save();
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput view) {
        super.saveAdditional(view);
        view.putInt("engineId", engineId);
        view.putInt("radius", radius);
        view.putInt("universe", universe);
        view.putInt("channel", channel);
        view.putDouble("gain", gain);
        view.putDouble("decay", decay);
        view.putDouble("beatThreshold", beatThreshold);
    }

    // Defaults fall back to the field initializers, so a block saved before a
    // field existed keeps that field's default rather than zeroing it.
    @Override
    protected void loadAdditional(ValueInput view) {
        super.loadAdditional(view);
        engineId = view.getIntOr("engineId", engineId);
        radius = view.getIntOr("radius", radius);
        universe = view.getIntOr("universe", universe);
        channel = view.getIntOr("channel", channel);
        gain = view.getDoubleOr("gain", gain);
        decay = view.getDoubleOr("decay", decay);
        beatThreshold = view.getDoubleOr("beatThreshold", beatThreshold);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(defaultName());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new SoundScreenHandler(syncId, inv, getScreenOpeningData(null));
    }

    @Override
    public SoundMenuData getScreenOpeningData(ServerPlayer player) {
        return new SoundMenuData(worldPosition, radius, universe, channel, gain, decay, beatThreshold);
    }

    // Called before the chunk drops this block entity, which is the last point
    // at which the engine registration can still be cleaned up.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        unregister();
        super.preRemoveSideEffects(pos, state);
    }

}
