package net.minelight.fabric.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minelight.core.api.FixtureBlock;
import net.minelight.fabric.blockentity.FixtureBlockEntity;

import java.util.List;

/**
 * Server side of the fixture configuration screen.
 *
 * <p>Exposes the block's port mappings to the client and applies edits back
 * to the block entity (and through it to the engine registry + NBT).</p>
 */
public class FixtureScreenHandler extends ScreenHandler {

    private final FixtureBlockEntity blockEntity;

    public FixtureScreenHandler(int syncId, PlayerInventory playerInventory, FixtureBlockEntity blockEntity) {
        super(ModScreenHandlers.FIXTURE, syncId);
        this.blockEntity = blockEntity;
    }

    public FixtureBlockEntity blockEntity() {
        return blockEntity;
    }

    public List<FixtureBlock.PortMapping> ports() {
        return blockEntity.ports();
    }

    public void updatePort(String portName, FixtureBlock.PortMapping mapping) {
        blockEntity.updatePort(portName, mapping);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return blockEntity.getWorld() != null
                && blockEntity.getWorld().getBlockEntity(blockEntity.getPos()) == blockEntity
                && player.squaredDistanceTo(blockEntity.getPos().toCenterPos()) < 64.0;
    }
}
