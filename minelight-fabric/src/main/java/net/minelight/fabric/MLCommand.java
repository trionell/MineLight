package net.minelight.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * The {@code /ml} command tree.
 *
 * <ul>
 *   <li>{@code /ml console} — open the WebConsole URL</li>
 *   <li>{@code /ml patch list}</li>
 *   <li>{@code /ml patch add <name> [mode] [universe] [address]} — patch the block you're looking at</li>
 *   <li>{@code /ml patch remove <id>}</li>
 *   <li>{@code /ml fixture <id> intensity <0-255>}</li>
 *   <li>{@code /ml preset <name>} / {@code /ml preset save <name>}</li>
 *   <li>{@code /ml cue <list> go [index]}</li>
 *   <li>{@code /ml event <kind> [json]} — command-block trigger</li>
 *   <li>{@code /ml script} — print the Lua editor URL</li>
 * </ul>
 */
public final class MLCommand {

    private MLCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("ml")
                .then(literal("console").executes(ctx -> {
                    ctx.getSource().sendFeedback(() ->
                            Text.literal("§6MineLight WebConsole: §bhttp://localhost:8090/"), false);
                    return 1;
                }))

                .then(literal("patch")
                        .then(literal("list").executes(ctx -> {
                            var engine = MineLightMod.engine();
                            if (engine == null) {
                                return 0;
                            }
                            engine.patch().fixtures().forEach(f ->
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            "§7#" + f.id() + " §f" + f.name()
                                                    + " §8(u" + f.universe() + "." + f.address()
                                                    + " @ " + f.x() + "," + f.y() + "," + f.z() + ")"), false));
                            return 1;
                        }))
                        .then(literal("add")
                                .then(argument("name", StringArgumentType.word())
                                        .executes(ctx -> addFixture(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                "Dimmer", 1, 0))
                                        .then(argument("mode", StringArgumentType.word())
                                                .then(argument("universe", IntegerArgumentType.integer(1))
                                                        .then(argument("address", IntegerArgumentType.integer(1, 512))
                                                                .executes(ctx -> addFixture(ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "name"),
                                                                        StringArgumentType.getString(ctx, "mode"),
                                                                        IntegerArgumentType.getInteger(ctx, "universe"),
                                                                        IntegerArgumentType.getInteger(ctx, "address"))))))))
                        .then(literal("remove")
                                .then(argument("id", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            int id = IntegerArgumentType.getInteger(ctx, "id");
                                            boolean ok = MineLightMod.engine().patch().remove(id);
                                            ctx.getSource().sendFeedback(() -> Text.literal(
                                                    ok ? "§aRemoved fixture #" + id : "§cNo fixture #" + id), false);
                                            return ok ? 1 : 0;
                                        }))))

                .then(literal("fixture")
                        .then(argument("id", IntegerArgumentType.integer(1))
                                .then(literal("intensity")
                                        .then(argument("value", IntegerArgumentType.integer(0, 255))
                                                .executes(ctx -> {
                                                    int id = IntegerArgumentType.getInteger(ctx, "id");
                                                    int v = IntegerArgumentType.getInteger(ctx, "value");
                                                    MineLightMod.engine().setFixtureIntensity(id, v);
                                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                                            "§aFixture #" + id + " -> " + v), false);
                                                    return 1;
                                                })))))

                .then(literal("preset")
                        .then(argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    boolean ok = MineLightMod.engine().applyPreset(name);
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            ok ? "§aApplied preset " + name : "§cNo preset " + name), false);
                                    return ok ? 1 : 0;
                                })))

                .then(literal("cue")
                        .then(argument("list", StringArgumentType.word())
                                .then(literal("go")
                                        .executes(ctx -> cueGo(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "list"), -1))
                                        .then(argument("index", IntegerArgumentType.integer(0))
                                                .executes(ctx -> cueGo(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "list"),
                                                        IntegerArgumentType.getInteger(ctx, "index")))))))

                .then(literal("event")
                        .then(argument("kind", StringArgumentType.word())
                                .executes(ctx -> {
                                    String kind = StringArgumentType.getString(ctx, "kind");
                                    MineLightMod.engine().emit(net.minelight.core.api.GameEvent.of("custom." + kind));
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            "§aEmitted custom." + kind), false);
                                    return 1;
                                })))

                .then(literal("script").executes(ctx -> {
                    ctx.getSource().sendFeedback(() ->
                            Text.literal("§6Edit the Lua trigger script in the WebConsole: §bhttp://localhost:8090/"), false);
                    return 1;
                })));
    }

    private static int addFixture(ServerCommandSource source, String name, String mode, int universe, int address) {
        var engine = MineLightMod.engine();
        if (engine == null) {
            return 0;
        }
        // patch at the command block / executor position
        var pos = source.getPosition();
        int id = engine.patch().addFixture(name, modeOf(mode), universe,
                address == 0 ? nextFreeAddress(engine) : address,
                (int) pos.x, (int) pos.y, (int) pos.z, "lamp");
        source.sendFeedback(() -> Text.literal("§aPatched §f" + name + " §7as #" + id), false);
        return 1;
    }

    private static int nextFreeAddress(net.minelight.core.engine.ConsoleEngine engine) {
        // find first free address in universe 1
        boolean[] used = new boolean[513];
        engine.patch().fixtures().forEach(f -> {
            if (f.universe() == 1 && f.address() >= 1 && f.address() <= 512) {
                used[f.address()] = true;
            }
        });
        for (int i = 1; i <= 512; i++) {
            if (!used[i]) {
                return i;
            }
        }
        return 1;
    }

    private static int cueGo(ServerCommandSource source, String list, int index) {
        var engine = MineLightMod.engine();
        if (engine == null) {
            return 0;
        }
        var cl = engine.cueList(list);
        var cue = index < 0 ? cl.next() : cl.go(index);
        if (cue != null && cue.levels() != null) {
            cue.levels().forEach(engine::setFixtureLevels);
        }
        source.sendFeedback(() -> Text.literal("§aCue list " + list + " -> " + cl.index()), false);
        return 1;
    }

    private static net.minelight.core.api.Patch.FixtureMode modeOf(String name) {
        return switch (name) {
            case "RGB" -> net.minelight.core.api.Patch.RGB;
            case "RGB+Dimmer" -> net.minelight.core.api.Patch.RGB_DIMMER;
            case "Strobe" -> net.minelight.core.api.Patch.STROBE;
            default -> net.minelight.core.api.Patch.DIMMER;
        };
    }
}
