package pl.autorynek;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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

        // Telefon: komendy czatu /ar, /ar start, /ar stop
        ClientCommandRegistrationCallback.EVENT.register((d, r) -> d.register(
            ClientCommandManager.literal("ar")
                .executes(ctx -> { MinecraftClient c = MinecraftClient.getInstance(); c.send(() -> c.setScreen(new MenuScreen(null))); return 1; })
                .then(ClientCommandManager.literal("start").executes(ctx -> { buyer.start(); return 1; }))
                .then(ClientCommandManager.literal("stop").executes(ctx -> { buyer.stop(); return 1; }))));

        // Telefon: przyciski ekranowe (menu pauzy + okno rynku)
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof GameMenuScreen) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("AutoRynek"),
                    b -> client.setScreen(new MenuScreen(screen))).dimensions(6, 6, 90, 24).build());
            } else if (screen instanceof GenericContainerScreen) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(buyer.running ? "§cSTOP" : "§aSTART"),
                    b -> { toggle(); b.setMessage(Text.literal(buyer.running ? "§cSTOP" : "§aSTART")); })
                    .dimensions(6, 6, 70, 24).build());
            }
        });
    }

    public static void toggle() { if (buyer.running) buyer.stop(); else buyer.start(); }
}
