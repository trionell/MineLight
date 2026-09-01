package net.minelight.fabric.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.HolderLookup;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minelight.fabric.screen.FixtureMenuData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minelight.core.api.FixtureBlock;
import net.minelight.fabric.MineLightMod;
import net.minelight.fabric.screen.FixtureScreenHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persists the per-block console mapping (universe/channel/event per port)
 * and bridges the placed block to the engine's {@code FixtureBlockRegistry}.
 *
 * <p>On first tick after placement the block entity registers itself with the
 * engine so the registry starts polling its redstone inputs. On removal it
 * unregisters. Config edits made in the GUI are pushed straight into the
 * registry and saved to NBT.</p>
 */
public class FixtureBlockEntity extends BlockEntity implements ExtendedMenuProvider<FixtureMenuData> {

    /** Persistence format for a port; {@code event} is absent for SET_CHANNEL ports. */
    private static final Codec<FixtureBlock.PortMapping> PORT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(FixtureBlock.PortMapping::name),
            Codec.STRING.xmap(FixtureBlock.Side::valueOf, FixtureBlock.Side::name)
                    .fieldOf("side").forGetter(FixtureBlock.PortMapping::side),
            Codec.STRING.xmap(FixtureBlock.Action::valueOf, FixtureBlock.Action::name)
                    .fieldOf("action").forGetter(FixtureBlock.PortMapping::action),
            Codec.INT.fieldOf("universe").forGetter(FixtureBlock.PortMapping::universe),
            Codec.INT.fieldOf("channel").forGetter(FixtureBlock.PortMapping::channel),
            Codec.STRING.optionalFieldOf("event").forGetter(p -> Optional.ofNullable(p.event()))
    ).apply(i, (name, side, action, universe, channel, event) ->
            new FixtureBlock.PortMapping(name, side, action, universe, channel, event.orElse(null))));

    private static final Codec<List<FixtureBlock.PortMapping>> PORTS_CODEC = PORT_CODEC.listOf();

    private final FixtureBlock.Type engineType;
    /** Engine-side block id; -1 until registered. */
    private int engineId = -1;
    /** Saved port config, applied on registration. */
    private List<FixtureBlock.PortMapping> portConfig = new ArrayList<>();

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
        var existing = engine.blocks().at(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        if (existing != null) {
            engineId = existing.id();
            return;
        }
        var block = engine.blocks().add(engineType, defaultName(), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        engineId = block.id();
        // re-apply any saved port config
        if (!portConfig.isEmpty()) {
            applyPortConfig(block);
        }
        engine.save();
    }

    private void applyPortConfig(FixtureBlock block) {
        for (FixtureBlock.PortMapping mapping : portConfig) {
            block.setPort(mapping.name(), mapping);
        }
    }

    private String defaultName() {
        return switch (engineType) {
            case DIMMER -> "Dimmer";
            case RGB -> "RGB";
            case EVENT -> "Event";
            case FEEDBACK -> "Feedback";
        } + " " + worldPosition.getX() + "," + worldPosition.getY() + "," + worldPosition.getZ();
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
        portConfig.removeIf(p -> p.name().equals(portName));
        portConfig.add(mapping);
        setChanged();
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
        // No engine to ask: start from the defaults and lay the saved edits
        // over them, so a partially configured block keeps its other ports.
        List<FixtureBlock.PortMapping> merged =
                new ArrayList<>(FixtureBlock.defaultPorts(engineType, engineId < 0 ? 1 : engineId));
        for (FixtureBlock.PortMapping saved : portConfig) {
            int at = -1;
            for (int i = 0; i < merged.size(); i++) {
                if (merged.get(i).name().equals(saved.name())) {
                    at = i;
                    break;
                }
            }
            if (at >= 0) {
                merged.set(at, saved);
            } else {
                merged.add(saved);
            }
        }
        return List.copyOf(merged);
    }

    // ---- NBT -------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput view) {
        super.saveAdditional(view);
        view.putInt("engineId", engineId);
        view.store("ports", PORTS_CODEC, portConfig);
    }

    @Override
    protected void loadAdditional(ValueInput view) {
        super.loadAdditional(view);
        engineId = view.getIntOr("engineId", -1);
        portConfig = new ArrayList<>(view.read("ports", PORTS_CODEC).orElseGet(List::of));
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return saveWithoutMetadata(registryLookup);
    }

    // ---- screen ----------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.literal(defaultName());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new FixtureScreenHandler(syncId, inv, getScreenOpeningData(null));
    }

    @Override
    public FixtureMenuData getScreenOpeningData(ServerPlayer player) {
        return new FixtureMenuData(worldPosition, ports());
    }

    /** Open the config screen (called from block useWithoutItem). */
    public void openScreen(ServerPlayer player) {
        player.openMenu(this);
    }

    // Called before the chunk drops this block entity, which is the last point
    // at which the engine registration can still be cleaned up.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        unregister();
        super.preRemoveSideEffects(pos, state);
    }

}
