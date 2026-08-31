package net.minelight.fabric.screen;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minelight.fabric.MineLightMod;

/**
 * Screen handler registration.
 */
public final class ModScreenHandlers {

    private ModScreenHandlers() {
    }

    public static ScreenHandlerType<FixtureScreenHandler> FIXTURE;

    public static void register() {
        FIXTURE = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(MineLightMod.MOD_ID, "fixture"),
                new ScreenHandlerType<>((syncId, inv) -> {
                    // client side: block entity resolved from the screen factory
                    throw new UnsupportedOperationException("client-side factory via FabricScreenRegistry");
                }, FeatureSet.empty()));
    }
}
