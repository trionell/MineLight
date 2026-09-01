package net.minelight.fabric.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minelight.fabric.network.SoundConfigPayload;
import org.lwjgl.glfw.GLFW;

/**
 * Right-click configuration screen for sound-reactive fixture blocks.
 *
 * <p>One field per tunable: listening radius, the DMX universe/channel the
 * level is written to, and the gain/decay/threshold shaping it. Edits are sent
 * to the server as they are typed.</p>
 */
public class SoundScreen extends AbstractContainerScreen<SoundScreenHandler> {

    private static final String[] LABELS =
            {"radius", "universe", "channel", "gain", "decay", "threshold"};

    private final EditBox[] fields = new EditBox[LABELS.length];

    public SoundScreen(SoundScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, DEFAULT_IMAGE_WIDTH, 40 + LABELS.length * 24);
    }

    @Override
    protected void init() {
        super.init();
        SoundMenuData d = menu.data();
        String[] values = {
                String.valueOf(d.radius()), String.valueOf(d.universe()), String.valueOf(d.channel()),
                String.valueOf(d.gain()), String.valueOf(d.decay()), String.valueOf(d.beatThreshold())
        };
        int y = this.topPos + 24;
        for (int i = 0; i < LABELS.length; i++) {
            EditBox box = new EditBox(font, this.leftPos + 88, y, 72, 20, Component.literal(LABELS[i]));
            box.setValue(values[i]);
            box.setResponder(s -> send());
            addRenderableWidget(box);
            fields[i] = box;
            y += 24;
        }
    }

    /** Sends the whole config; a half-parsed field keeps its last good value. */
    private void send() {
        SoundMenuData d = menu.data();
        ClientPlayNetworking.send(new SoundConfigPayload(
                menu.pos(),
                asInt(0, d.radius()), asInt(1, d.universe()), asInt(2, d.channel()),
                asDouble(3, d.gain()), asDouble(4, d.decay()), asDouble(5, d.beatThreshold())));
    }

    private int asInt(int i, int fallback) {
        try {
            return Integer.parseInt(fields[i].getValue().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double asDouble(int i, double fallback) {
        try {
            return Double.parseDouble(fields[i].getValue().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // While a field has focus every key belongs to it; otherwise the inventory
    // key ('e' by default) would close the screen mid-word.
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() != GLFW.GLFW_KEY_ESCAPE
                && getFocused() instanceof EditBox box && box.canConsumeInput()) {
            box.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(this.leftPos - 4, this.topPos - 4, this.leftPos + imageWidth + 4,
                this.topPos + imageHeight + 4, 0xC0101015);
        context.outline(this.leftPos - 4, this.topPos - 4, imageWidth + 8,
                imageHeight + 8, 0xFFf7782f);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        super.extractLabels(context, mouseX, mouseY);
        int y = 12;
        for (String label : LABELS) {
            context.text(font, label, 8, y + 12, 0x5fd0ff, false);
            y += 24;
        }
    }
}
