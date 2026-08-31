package net.minelight.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minelight.fabric.screen.FixtureScreen;
import net.minelight.fabric.screen.ModScreenHandlers;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side entrypoint: keybinds, HUD overlay, and the WebConsole quick
 * open. The heavy lifting lives in the server-side engine; the client just
 * provides a fast way to open the console UI.
 */
public final class MineLightClient implements ClientModInitializer {

    private static KeyBinding openConsole;

    @Override
    public void onInitializeClient() {
        HandledScreens.register(ModScreenHandlers.FIXTURE, FixtureScreen::new);

        openConsole = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.minelight.console",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.minelight"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConsole.wasPressed()) {
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
