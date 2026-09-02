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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Right-click configuration screen for fixture blocks.
 *
 * <p>One control per row, label on the left and widget on the right: the
 * redstone face a port reads, and its target — a DMX universe and channel for
 * dimmer/RGB/feedback blocks, or an event name for event blocks. Event ports
 * carry a universe and channel too: leave the channel at 0 for a
 * MineLight-only event, or set one to also pulse it so a lighting desk can
 * trigger on the block. Edits are sent to the server as they are made.</p>
 */
public class FixtureScreen extends AbstractContainerScreen<FixtureScreenHandler> {

    private static final int ROW_H = 24;
    private static final int FIRST_ROW_Y = 24;
    private static final int LABEL_X = 8;
    private static final int FIELD_X = 88;
    private static final int FIELD_W = 72;
    /** Nudges an 8px glyph down into the middle of a 20px field. */
    private static final int LABEL_BASELINE = 6;
    /** Label colour needs a full alpha byte: a zero alpha draws nothing at all. */
    private static final int LABEL_ARGB = 0xFF5FD0FF;
    private static final int TITLE_ARGB = 0xFFE8E8E8;

    /**
     * Live working copy per port. Every widget edits the current mapping rather
     * than the one captured when the row was built, so changing the side and
     * then the channel keeps both.
     */
    private final Map<String, FixtureBlock.PortMapping> working = new LinkedHashMap<>();

    /** One label per control row, in layout order. */
    private final List<String> rowLabels = new ArrayList<>();

    public FixtureScreen(FixtureScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, DEFAULT_IMAGE_WIDTH, 40 + rowCount(handler.ports()) * ROW_H);
    }

    /**
     * A channel port needs side/universe/channel; an event port adds the
     * event name in front of the same universe/channel pair.
     */
    private static int rowCount(List<FixtureBlock.PortMapping> ports) {
        int rows = 0;
        for (FixtureBlock.PortMapping port : ports) {
            rows += port.action() == FixtureBlock.Action.SET_CHANNEL ? 3 : 4;
        }
        return rows;
    }

    @Override
    protected void init() {
        super.init();
        working.clear();
        rowLabels.clear();
        for (FixtureBlock.PortMapping port : menu.ports()) {
            working.put(port.name(), port);
        }

        // A single-port block has nothing to disambiguate, so drop the prefix.
        boolean prefixed = menu.ports().size() > 1;
        int y = this.topPos + FIRST_ROW_Y;
        for (FixtureBlock.PortMapping port : menu.ports()) {
            String name = port.name();
            String prefix = prefixed ? name + " " : "";

            rowLabels.add(prefix + "side");
            addRenderableWidget(Button.builder(Component.literal(port.side().name()), b -> {
                FixtureBlock.PortMapping current = working.get(name);
                FixtureBlock.Side next = nextSide(current.side());
                b.setMessage(Component.literal(next.name()));
                push(new FixtureBlock.PortMapping(name, next, current.action(),
                        current.universe(), current.channel(), current.event()));
            }).bounds(this.leftPos + FIELD_X, y, FIELD_W, 20).build());
            y += ROW_H;

            if (port.action() == FixtureBlock.Action.EMIT_EVENT) {
                rowLabels.add(prefix + "event");
                addRenderableWidget(field(y, port.event() == null ? "" : port.event(), "event", s -> {
                    // Keep the pulse target: rebuilding the mapping from the
                    // event name alone would silently zero the channel.
                    FixtureBlock.PortMapping current = working.get(name);
                    push(new FixtureBlock.PortMapping(name, current.side(), current.action(),
                            current.universe(), current.channel(), s));
                }));
                y += ROW_H;
            }

            rowLabels.add(prefix + "universe");
            addRenderableWidget(field(y, String.valueOf(port.universe()), "universe",
                    s -> updateChannel(name, s, null)));
            y += ROW_H;

            rowLabels.add(prefix + (port.action() == FixtureBlock.Action.EMIT_EVENT
                    ? "pulse ch" : "channel"));
            addRenderableWidget(field(y, String.valueOf(port.channel()), "channel",
                    s -> updateChannel(name, null, s)));
            y += ROW_H;
        }
    }

    private EditBox field(int y, String value, String hint, java.util.function.Consumer<String> onChange) {
        EditBox box = new EditBox(font, this.leftPos + FIELD_X, y, FIELD_W, 20, Component.literal(hint));
        box.setValue(value);
        box.setResponder(onChange);
        return box;
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
        // Preserve the action and event name: an event port keeps emitting its
        // event, the universe and channel only add a DMX pulse alongside it.
        push(new FixtureBlock.PortMapping(name, current.side(), current.action(),
                universe, channel, current.event()));
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

    // Deliberately not super.extractLabels(): there is no inventory grid on
    // this screen, so vanilla's "Inventory" label would only mislead.
    @Override
    protected void extractLabels(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        context.text(font, title, titleLabelX, titleLabelY, TITLE_ARGB, false);
        int y = FIRST_ROW_Y + LABEL_BASELINE;
        for (String label : rowLabels) {
            context.text(font, label, LABEL_X, y, LABEL_ARGB, false);
            y += ROW_H;
        }
    }
}
