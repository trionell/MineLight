package net.minelight.fabric.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minelight.core.api.FixtureBlock;
import net.minelight.fabric.blockentity.FixtureBlockEntity;

import java.util.List;

/**
 * Fixture configuration menu.
 *
 * <p>Built identically on both sides from a {@link FixtureMenuData} snapshot.
 * It carries no block entity reference: the client has none, and edits are
 * applied on the server through {@code PortUpdatePayload} instead.</p>
 */
public class FixtureScreenHandler extends AbstractContainerMenu {

    private final BlockPos pos;
    private final List<FixtureBlock.PortMapping> ports;

    public FixtureScreenHandler(int syncId, Inventory playerInventory, FixtureMenuData data) {
        super(ModScreenHandlers.FIXTURE, syncId);
        this.pos = data.pos();
        this.ports = List.copyOf(data.ports());
    }

    public BlockPos pos() {
        return pos;
    }

    public List<FixtureBlock.PortMapping> ports() {
        return ports;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof FixtureBlockEntity
                && player.distanceToSqr(Vec3.atCenterOf(pos)) < 64.0;
    }
}
