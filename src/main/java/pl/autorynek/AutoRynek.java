package pl.autorynek;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class AutoRynek implements ClientModInitializer {
    public static Config config;
    public static final Buyer buyer = new Buyer();
    private static KeyBinding menuKey, toggleKey;

    @Override
    public void onInitializeClient() {
        config = Config.load();
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.autorynek.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "AutoRynek"));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.autorynek.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "AutoRynek"));

        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            while (menuKey.wasPressed()) c.setScreen(new MenuScreen(c.currentScreen));
            while (toggleKey.wasPressed()) toggle();
            buyer.tick(c, config);
        });

        // olafrynek HUD (lewy gorny rog, pod paskiem launchera)
        HudRenderCallback.EVENT.register((ctx, tc) -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.player == null || c.options.hudHidden || buyer.startedAt == 0) return;
            var tr = c.textRenderer;
            long s = buyer.elapsedSecs();
            String l1 = String.format("Czas: %02d:%02d:%02d   Kupiono: %d szt", s / 3600, (s / 60) % 60, s % 60, buyer.bought);
            String l2 = "Wydano: " + buyer.spent + "$";
            String l3 = buyer.status + (buyer.running ? "..." : "");
            String title = "olafrynek HUD";
            int w = Math.max(Math.max(tr.getWidth(l1), tr.getWidth(l2)), Math.max(tr.getWidth(l3), tr.getWidth(title))) + 12;
            int x = 6, y = 30, h = 52;
            ctx.fill(x, y, x + w, y + h, 0xC00B1220);
            ctx.fill(x, y, x + w, y + 2, buyer.running ? 0xFF22C55E : 0xFF14B8A6);
            ctx.drawTextWithShadow(tr, title, x + 6, y + 5, 0xFF2DD4BF);
            ctx.drawTextWithShadow(tr, l1, x + 6, y + 17, 0xFFFFFFFF);
            ctx.drawTextWithShadow(tr, l2, x + 6, y + 28, 0xFFFFFFFF);
            ctx.drawTextWithShadow(tr, l3, x + 6, y + 39, 0xFF9FB0D0);
        });

        // komendy czatu: /ar, /ar start, /ar stop
        ClientCommandRegistrationCallback.EVENT.register((d, r) -> d.register(
            ClientCommandManager.literal("ar")
                .executes(ctx -> { MinecraftClient c = MinecraftClient.getInstance(); c.send(() -> c.setScreen(new MenuScreen(null))); return 1; })
                .then(ClientCommandManager.literal("start").executes(ctx -> { buyer.start(); return 1; }))
                .then(ClientCommandManager.literal("stop").executes(ctx -> { buyer.stop(); return 1; }))));

        // Telefon: przyciski ekranowe (srodek, zeby nie wchodzic na przyciski launchera)
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof GameMenuScreen) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("AutoRynek"),
                    b -> client.setScreen(new MenuScreen(screen))).dimensions(w / 2 - 50, h - 30, 100, 22).build());
            } else if (screen instanceof GenericContainerScreen) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(buyer.running ? "§cSTOP" : "§aSTART"),
                    b -> { toggle(); b.setMessage(Text.literal(buyer.running ? "§cSTOP" : "§aSTART")); })
                    .dimensions(w / 2 - 108, 4, 70, 20).build());
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("SLOTY"),
                    b -> buyer.dump(client, config)).dimensions(w / 2 - 34, 4, 70, 20).build());
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("OFERTY"),
                    b -> buyer.dumpItems(client, config)).dimensions(w / 2 + 40, 4, 70, 20).build());
            }
        });
    }

    public static void toggle() { if (buyer.running) buyer.stop(); else buyer.start(); }
}
