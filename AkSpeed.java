package pl.autokopanie.speed;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dodaje do moda AutoKopanie komendę /ak speed <0-10>.
 * Zmienia tylko tempo celowania i przełączania między blokami
 * (ustawienia obrotu, reakcji i tryb szybki). Nie rusza reszty moda.
 *
 * Poziom 0 = najwolniej, 5 = domyślne ustawienia moda, 10 = szybko.
 */
public class AkSpeed implements ClientModInitializer {

    // maxTurnSpeed, reactionMinMs, reactionMaxMs, tryb szybki (od poziomu 9)
    private static final float[] TURN = {1.5f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.5f, 9.0f, 11.0f, 12.0f, 14.0f};
    private static final int[] RMIN = {500, 400, 320, 240, 160, 80, 70, 60, 50, 40, 30};
    private static final int[] RMAX = {1100, 900, 750, 600, 400, 220, 180, 150, 120, 100, 80};

    private static Path levelFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("autokopanie-speed.txt");
    }

    private static int readLevel() {
        try {
            Path f = levelFile();
            if (Files.exists(f)) {
                int v = Integer.parseInt(Files.readString(f).trim());
                if (v >= 0 && v <= 10) return v;
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static void writeLevel(int level) {
        try {
            Files.writeString(levelFile(), Integer.toString(level));
        } catch (Exception ignored) {
        }
    }

    /** Ustawia pola w konfiguracji AutoKopanie (przez refleksję, żeby nie zależeć od kodu tamtego moda). */
    private static void apply(int level) throws Exception {
        Class<?> main = Class.forName("pl.autokopanie.AutoKopanie");
        Object cfg = main.getField("CONFIG").get(null);
        Class<?> c = cfg.getClass();
        c.getField("maxTurnSpeed").setFloat(cfg, TURN[level]);
        c.getField("reactionMinMs").setInt(cfg, RMIN[level]);
        c.getField("reactionMaxMs").setInt(cfg, RMAX[level]);
        c.getField("fast").setBoolean(cfg, level >= 9);
        c.getMethod("save").invoke(cfg);
    }

    private static String describe(int level) {
        return "Szybkosc: " + level + "/10  (obrot " + TURN[level]
                + ", reakcja " + RMIN[level] + "-" + RMAX[level] + " ms"
                + ", tryb szybki: " + (level >= 9 ? "TAK" : "NIE") + ")";
    }

    private static int show(CommandContext<FabricClientCommandSource> ctx) {
        int level = readLevel();
        String text = level < 0
                ? "Szybkosc: jeszcze nie ustawiona. Wpisz /ak speed 0-10 (0 = najwolniej, 5 = domyslna, 10 = szybko)."
                : describe(level);
        ctx.getSource().sendFeedback(Text.literal(text).formatted(Formatting.YELLOW));
        return 1;
    }

    private static int set(CommandContext<FabricClientCommandSource> ctx) {
        int level = IntegerArgumentType.getInteger(ctx, "level");
        try {
            apply(level);
            writeLevel(level);
            ctx.getSource().sendFeedback(Text.literal(describe(level)).formatted(Formatting.GREEN));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Nie udalo sie ustawic szybkosci: " + e));
            return 0;
        }
    }

    private static LiteralCommandNode<FabricClientCommandSource> speedNode() {
        return ClientCommandManager.literal("speed")
                .executes(AkSpeed::show)
                .then(ClientCommandManager.argument("level", IntegerArgumentType.integer(0, 10))
                        .executes(AkSpeed::set))
                .build();
    }

    @Override
    public void onInitializeClient() {
        // Wykonaj się PO zarejestrowaniu komend przez AutoKopanie, żeby dopisać "speed" do istniejącego /ak.
        Identifier late = Identifier.of("autokopaniespeed", "late");
        ClientCommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, late);
        ClientCommandRegistrationCallback.EVENT.register(late, (dispatcher, registryAccess) -> {
            for (String name : new String[]{"ak", "autokopanie"}) {
                CommandNode<FabricClientCommandSource> node = dispatcher.getRoot().getChild(name);
                if (node == null) {
                    dispatcher.register(ClientCommandManager.literal(name).then(speedNode()));
                    continue;
                }
                CommandNode<FabricClientCommandSource> target =
                        node.getRedirect() != null ? node.getRedirect() : node;
                if (target.getChild("speed") == null) {
                    target.addChild(speedNode());
                }
            }
        });
    }
}
