package net.minelight.fabric.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minelight.fabric.blockentity.SoundBlockEntity;

/**
 * Sound fixture configuration menu. Built from a {@link SoundMenuData}
 * snapshot on both sides; edits travel back as {@code SoundConfigPayload}.
 */
public class SoundScreenHandler extends AbstractContainerMenu {

    private final SoundMenuData data;

    public SoundScreenHandler(int syncId, Inventory playerInventory, SoundMenuData data) {
        super(ModScreenHandlers.SOUND, syncId);
        this.data = data;
    }

    public SoundMenuData data() {
        return data;
    }

    public BlockPos pos() {
        return data.pos();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(data.pos()) instanceof SoundBlockEntity
                && player.distanceToSqr(Vec3.atCenterOf(data.pos())) < 64.0;
    }
}
