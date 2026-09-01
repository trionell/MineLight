package net.minelight.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import net.minelight.fabric.screen.FixtureScreen;
import net.minelight.fabric.screen.SoundScreen;
import net.minelight.fabric.screen.ModScreenHandlers;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side entrypoint: keybinds, HUD overlay, and the WebConsole quick
 * open. The heavy lifting lives in the server-side engine; the client just
 * provides a fast way to open the console UI.
 */
public final class MineLightClient implements ClientModInitializer {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MineLightMod.MOD_ID, "main"));

    private static KeyMapping openConsole;

    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModScreenHandlers.FIXTURE, FixtureScreen::new);
        MenuScreens.register(ModScreenHandlers.SOUND, SoundScreen::new);

        openConsole = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.minelight.console",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConsole.consumeClick()) {
                // Open the WebConsole in the system browser
                String url = "http://localhost:8090/";
                try {
                    if (java.awt.Desktop.isDesktopSupported()
                            && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                    } else {
                        MineLightMod.LOGGER.info("[MineLight] Open the WebConsole at {}", url);
                    }
                } catch (Exception e) {
                    MineLightMod.LOGGER.warn("[MineLight] Could not open browser: {}", e.getMessage());
                }
            }
        });
    }
}
