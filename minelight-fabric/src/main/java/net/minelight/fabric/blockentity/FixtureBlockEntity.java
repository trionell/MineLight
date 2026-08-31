package net.minelight.fabric.blockentity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minelight.core.api.FixtureBlock;
import net.minelight.fabric.MineLightMod;
import net.minelight.fabric.screen.FixtureScreenHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Persists the per-block console mapping (universe/channel/event per port)
 * and bridges the placed block to the engine's {@code FixtureBlockRegistry}.
 *
 * <p>On first tick after placement the block entity registers itself with the
 * engine so the registry starts polling its redstone inputs. On removal it
 * unregisters. Config edits made in the GUI are pushed straight into the
 * registry and saved to NBT.</p>
 */
public class FixtureBlockEntity extends BlockEntity implements NamedScreenHandlerFactory {

    private final FixtureBlock.Type engineType;
    /** Engine-side block id; -1 until registered. */
    private int engineId = -1;
    /** Serialized port config, applied on registration. */
    private NbtCompound portConfig = new NbtCompound();

    public FixtureBlockEntity(BlockPos pos, BlockState state, FixtureBlock.Type engineType) {
        super(ModBlockEntities.forType(engineType), pos, state);
        this.engineType = engineType;
    }

    public FixtureBlock.Type engineType() {
        return engineType;
    }

    public int engineId() {
        return engineId;
    }

    /** Called each server tick from the block's ticker. */
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
        var existing = engine.blocks().at(pos.getX(), pos.getY(), pos.getZ());
        if (existing != null) {
            engineId = existing.id();
            return;
        }
        var block = engine.blocks().add(engineType, defaultName(), pos.getX(), pos.getY(), pos.getZ());
        engineId = block.id();
        // re-apply any saved port config
        if (!portConfig.isEmpty()) {
            applyPortConfig(block);
        }
        engine.save();
    }

    private void applyPortConfig(FixtureBlock block) {
        for (String portName : portConfig.getKeys()) {
            NbtCompound p = portConfig.getCompound(portName);
            FixtureBlock.Side side = FixtureBlock.Side.valueOf(p.getString("side"));
            FixtureBlock.Action action = FixtureBlock.Action.valueOf(p.getString("action"));
            int universe = p.getInt("universe");
            int channel = p.getInt("channel");
            String event = p.getString("event");
            block.setPort(portName, new FixtureBlock.PortMapping(portName, side, action, universe, channel, event));
        }
    }

    private String defaultName() {
        return switch (engineType) {
            case DIMMER -> "Dimmer";
            case RGB -> "RGB";
            case EVENT -> "Event";
            case FEEDBACK -> "Feedback";
        } + " " + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** Unregister from the engine (block broken). */
    public void unregister() {
        var engine = MineLightMod.engine();
        if (engine != null && engineId >= 0) {
            engine.blocks().remove(engineId);
            engine.save();
        }
        engineId = -1;
    }

    /** Update a port from the GUI and persist. */
    public void updatePort(String portName, FixtureBlock.PortMapping mapping) {
        var engine = MineLightMod.engine();
        if (engine != null && engineId >= 0) {
            var block = engine.blocks().byId(engineId);
            if (block != null) {
                block.setPort(portName, mapping);
                engine.save();
            }
        }
        NbtCompound p = new NbtCompound();
        p.putString("side", mapping.side().name());
        p.putString("action", mapping.action().name());
        p.putInt("universe", mapping.universe());
        p.putInt("channel", mapping.channel());
        if (mapping.event() != null) {
            p.putString("event", mapping.event());
        }
        portConfig.put(portName, p);
        markDirty();
    }

    /** Current port config for the GUI. */
    public java.util.List<FixtureBlock.PortMapping> ports() {
        var engine = MineLightMod.engine();
        if (engine != null && engineId >= 0) {
            var block = engine.blocks().byId(engineId);
            if (block != null) {
                return block.ports();
            }
        }
        return FixtureBlock.defaultPorts(engineType, engineId < 0 ? 1 : engineId);
    }

    // ---- NBT -------------------------------------------------------------

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.putInt("engineId", engineId);
        nbt.put("ports", portConfig);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        engineId = nbt.getInt("engineId");
        portConfig = nbt.getCompound("ports");
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }

    // ---- screen ----------------------------------------------------------

    @Override
    public Text getDisplayName() {
        return Text.literal(defaultName());
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new FixtureScreenHandler(syncId, inv, this);
    }

    /** Open the config screen (called from block onUse). */
    public void openScreen(ServerPlayerEntity player) {
        player.openHandledScreen(this);
    }
}
