package net.minelight.fabric.block;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minelight.fabric.MineLightMod;

/**
 * The creative-inventory tab holding every MineLight fixture, so the blocks
 * can be picked out of the browser instead of only through {@code /give}.
 */
public final class ModItemGroups {

    private ModItemGroups() {
    }

    public static final ResourceKey<CreativeModeTab> FIXTURES = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(MineLightMod.MOD_ID, "fixtures"));

    // Called after ModBlocks.register(): the display list captures the block
    // instances, which are null until then.
    public static void register() {
        CreativeModeTab tab = FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.minelight.fixtures"))
                .icon(() -> new ItemStack(ModBlocks.DIMMER))
                .displayItems((parameters, output) -> {
                    output.accept(ModBlocks.DIMMER);
                    output.accept(ModBlocks.RGB);
                    output.accept(ModBlocks.EVENT);
                    output.accept(ModBlocks.FEEDBACK);

                    output.accept(ModBlocks.NOTE);
                    output.accept(ModBlocks.SOUND_METER);
                    output.accept(ModBlocks.BEAT);
                    output.accept(ModBlocks.SPECTRUM);
                })
                .build();

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, FIXTURES, tab);
    }
}
