package net.minelight.fabric.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minelight.fabric.blockentity.SoundBlockEntity;

/**
 * Server side of the sound fixture configuration screen.
 */
public class SoundScreenHandler extends ScreenHandler {

    private final SoundBlockEntity blockEntity;

    public SoundScreenHandler(int syncId, PlayerInventory playerInventory, SoundBlockEntity blockEntity) {
        super(ModScreenHandlers.SOUND, syncId);
        this.blockEntity = blockEntity;
    }

    public SoundBlockEntity blockEntity() {
        return blockEntity;
    }

    public void applyConfig(int radius, int universe, int channel,
                            double gain, double decay, double beatThreshold) {
        blockEntity.applyConfig(radius, universe, channel, gain, decay, beatThreshold);
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
