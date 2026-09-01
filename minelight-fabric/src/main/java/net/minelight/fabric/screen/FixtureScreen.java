package net.minelight.fabric.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minelight.core.api.FixtureBlock;
import net.minelight.fabric.network.PortUpdatePayload;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Right-click configuration screen for fixture blocks.
 *
 * <p>Shows one row per port: its name, which redstone face it reads, and the
 * target — a DMX universe.channel for dimmer/RGB/feedback blocks, or an event
 * name for event blocks. Edits are sent to the server as they are made.</p>
 */
public class FixtureScreen extends AbstractContainerScreen<FixtureScreenHandler> {

    // Row geometry, relative to the panel's left content edge. The panel is
    // DEFAULT_IMAGE_WIDTH wide with an 8px margin, so nothing may pass x + 160.
    private static final int SIDE_W = 60;
    private static final int FIELD_X = 64;
    private static final int UNIVERSE_W = 44;
    private static final int CHANNEL_X = 112;
    private static final int CHANNEL_W = 48;
    private static final int EVENT_W = 96;
    private static final int ROW_H = 24;

    /**
     * Live working copy per port. Every widget edits the current mapping rather
     * than the one captured when the row was built, so changing the side and
     * then the channel keeps both.
     */
    private final Map<String, FixtureBlock.PortMapping> working = new LinkedHashMap<>();

    public FixtureScreen(FixtureScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, DEFAULT_IMAGE_WIDTH, 40 + handler.ports().size() * ROW_H);
    }

    @Override
    protected void init() {
        super.init();
        working.clear();
        for (FixtureBlock.PortMapping port : menu.ports()) {
            working.put(port.name(), port);
        }
        int y = this.topPos + 24;
        for (FixtureBlock.PortMapping port : menu.ports()) {
            addPortRow(port.name(), y);
            y += ROW_H;
        }
    }

    private void addPortRow(String name, int y) {
        int x = this.leftPos + 8;
        FixtureBlock.PortMapping port = working.get(name);

        Button sideBtn = Button.builder(Component.literal(port.side().name()), b -> {
            FixtureBlock.PortMapping current = working.get(name);
            FixtureBlock.Side next = nextSide(current.side());
            b.setMessage(Component.literal(next.name()));
            push(new FixtureBlock.PortMapping(name, next, current.action(),
                    current.universe(), current.channel(), current.event()));
        }).bounds(x, y, SIDE_W, 20).build();
        addRenderableWidget(sideBtn);

        if (port.action() == FixtureBlock.Action.SET_CHANNEL) {
            EditBox uni = new EditBox(font, x + FIELD_X, y, UNIVERSE_W, 20, Component.literal("universe"));
            uni.setValue(String.valueOf(port.universe()));
            uni.setResponder(s -> updateChannel(name, s, null));
            addRenderableWidget(uni);

            EditBox ch = new EditBox(font, x + CHANNEL_X, y, CHANNEL_W, 20, Component.literal("channel"));
            ch.setValue(String.valueOf(port.channel()));
            ch.setResponder(s -> updateChannel(name, null, s));
            addRenderableWidget(ch);
        } else {
            EditBox ev = new EditBox(font, x + FIELD_X, y, EVENT_W, 20, Component.literal("event"));
            ev.setValue(port.event() == null ? "" : port.event());
            ev.setResponder(s -> {
                FixtureBlock.PortMapping current = working.get(name);
                push(FixtureBlock.PortMapping.event(name, current.side(), s));
            });
            addRenderableWidget(ev);
        }
    }

    /** A half-typed number leaves the corresponding value untouched. */
    private void updateChannel(String name, String universeText, String channelText) {
        FixtureBlock.PortMapping current = working.get(name);
        int universe = current.universe();
        int channel = current.channel();
        try {
            if (universeText != null && !universeText.isBlank()) {
                universe = Integer.parseInt(universeText.trim());
            }
            if (channelText != null && !channelText.isBlank()) {
                channel = Integer.parseInt(channelText.trim());
            }
        } catch (NumberFormatException e) {
            return;
        }
        push(FixtureBlock.PortMapping.channel(name, current.side(), universe, channel));
    }

    private void push(FixtureBlock.PortMapping mapping) {
        working.put(mapping.name(), mapping);
        ClientPlayNetworking.send(new PortUpdatePayload(menu.pos(), mapping));
    }

    private static FixtureBlock.Side nextSide(FixtureBlock.Side s) {
        FixtureBlock.Side[] values = FixtureBlock.Side.values();
        return values[(s.ordinal() + 1) % values.length];
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
        for (FixtureBlock.PortMapping port : menu.ports()) {
            context.text(font, port.name(), 8, y + 12, 0x5fd0ff, false);
            y += ROW_H;
        }
    }
}
