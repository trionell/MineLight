package net.minelight.fabric.screen;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import net.minelight.fabric.MineLightMod;

/**
 * Screen handler registration.
 *
 * <p>Both menus are {@link ExtendedMenuType}s: the client cannot read the
 * engine, so the config it needs to draw the screen is sent along with the
 * open-screen packet.</p>
 */
public final class ModScreenHandlers {

    private ModScreenHandlers() {
    }

    public static MenuType<FixtureScreenHandler> FIXTURE;
    public static MenuType<SoundScreenHandler> SOUND;

    public static void register() {
        FIXTURE = Registry.register(BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(MineLightMod.MOD_ID, "fixture"),
                new ExtendedMenuType<>(FixtureScreenHandler::new, FixtureMenuData.STREAM_CODEC));
        SOUND = Registry.register(BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(MineLightMod.MOD_ID, "sound"),
                new ExtendedMenuType<>(SoundScreenHandler::new, SoundMenuData.STREAM_CODEC));
    }
}
