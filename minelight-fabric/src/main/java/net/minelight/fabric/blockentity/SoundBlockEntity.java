package net.minelight.fabric.blockentity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minelight.core.sound.SoundEngine;
import net.minelight.fabric.MineLightMod;
import net.minelight.fabric.screen.SoundScreenHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Persists a sound fixture block's config (radius, gain, channel, thresholds)
 * and registers it with the engine's {@code SoundEngine}.
 */
public class SoundBlockEntity extends BlockEntity implements NamedScreenHandlerFactory {

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
        if (world == null || world.isClient()) {
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
            if (b.x == pos.getX() && b.y == pos.getY() && b.z == pos.getZ()) {
                engineId = b.id;
                return;
            }
        }
        var b = engine.sound().add(mode, defaultName(), pos.getX(), pos.getY(), pos.getZ());
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
        } + " " + pos.getX() + "," + pos.getY() + "," + pos.getZ();
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
        markDirty();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.putInt("engineId", engineId);
        nbt.putInt("radius", radius);
        nbt.putInt("universe", universe);
        nbt.putInt("channel", channel);
        nbt.putDouble("gain", gain);
        nbt.putDouble("decay", decay);
        nbt.putDouble("beatThreshold", beatThreshold);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        engineId = nbt.getInt("engineId");
        radius = nbt.getInt("radius");
        universe = nbt.getInt("universe");
        channel = nbt.getInt("channel");
        gain = nbt.getDouble("gain");
        decay = nbt.getDouble("decay");
        beatThreshold = nbt.getDouble("beatThreshold");
    }

    @Override
    public Text getDisplayName() {
        return Text.literal(defaultName());
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new SoundScreenHandler(syncId, inv, this);
    }
}
