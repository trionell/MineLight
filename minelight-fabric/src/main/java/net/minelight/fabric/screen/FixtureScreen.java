package net.minelight.fabric.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minelight.core.api.FixtureBlock;

/**
 * Right-click configuration screen for fixture blocks.
 *
 * <p>Shows one row per port: its name, which redstone face it reads, and the
 * target — a DMX universe.channel for dimmer/RGB/feedback blocks, or an event
 * name for event blocks. Edits apply immediately and persist.</p>
 */
public class FixtureScreen extends HandledScreen<FixtureScreenHandler> {

    public FixtureScreen(FixtureScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundHeight = 40 + handler.ports().size() * 24;
    }

    @Override
    protected void init() {
        super.init();
        int y = this.y + 24;
        for (FixtureBlock.PortMapping port : handler.ports()) {
            addPortRow(port, y);
            y += 24;
        }
    }

    private void addPortRow(FixtureBlock.PortMapping port, int y) {
        int x = this.x + 8;

        // side cycler
        ButtonWidget sideBtn = ButtonWidget.builder(Text.literal(port.side().name()), b -> {
            FixtureBlock.Side next = nextSide(port.side());
            handler.updatePort(port.name(), new FixtureBlock.PortMapping(
                    port.name(), next, port.action(), port.universe(), port.channel(), port.event()));
            b.setMessage(Text.literal(next.name()));
        }).dimensions(x, y, 60, 20).build();
        addDrawableChild(sideBtn);

        if (port.action() == FixtureBlock.Action.SET_CHANNEL) {
            // universe field
            TextFieldWidget uni = new TextFieldWidget(textRenderer, x + 68, y, 36, 20, Text.literal("universe"));
            uni.setText(String.valueOf(port.universe()));
            uni.setChangedListener(s -> tryUpdateChannel(port, s, null));
            addDrawableChild(uni);

            // channel field
            TextFieldWidget ch = new TextFieldWidget(textRenderer, x + 110, y, 48, 20, Text.literal("channel"));
            ch.setText(String.valueOf(port.channel()));
            ch.setChangedListener(s -> tryUpdateChannel(port, null, s));
            addDrawableChild(ch);
        } else {
            // event name field
            TextFieldWidget ev = new TextFieldWidget(textRenderer, x + 68, y, 110, 20, Text.literal("event"));
            ev.setText(port.event() == null ? "" : port.event());
            ev.setChangedListener(s -> handler.updatePort(port.name(),
                    FixtureBlock.PortMapping.event(port.name(), port.side(), s)));
            addDrawableChild(ev);
        }
    }

    private void tryUpdateChannel(FixtureBlock.PortMapping port, String uniStr, String chStr) {
        int universe = port.universe();
        int channel = port.channel();
        try {
            if (uniStr != null && !uniStr.isBlank()) {
                universe = Integer.parseInt(uniStr.trim());
            }
            if (chStr != null && !chStr.isBlank()) {
                channel = Integer.parseInt(chStr.trim());
            }
        } catch (NumberFormatException e) {
            return;
        }
        handler.updatePort(port.name(), FixtureBlock.PortMapping.channel(port.name(), port.side(), universe, channel));
    }

    private static FixtureBlock.Side nextSide(FixtureBlock.Side s) {
        FixtureBlock.Side[] values = FixtureBlock.Side.values();
        return values[(s.ordinal() + 1) % values.length];
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(this.x - 4, this.y - 4, this.x + backgroundWidth + 4,
                this.y + backgroundHeight + 4, 0xC0101015);
        context.drawBorder(this.x - 4, this.y - 4, backgroundWidth + 8,
                backgroundHeight + 8, 0xFFf7782f);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        super.drawForeground(context, mouseX, mouseY);
        int y = 12;
        for (FixtureBlock.PortMapping port : handler.ports()) {
            context.drawText(textRenderer, port.name(), 8, y + 12, 0x5fd0ff, false);
            y += 24;
        }
    }
}
