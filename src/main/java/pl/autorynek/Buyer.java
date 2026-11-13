package pl.autorynek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import java.util.*;
import java.util.regex.*;

public class Buyer {
    private static final Pattern PRICE = Pattern.compile("(\\d[\\d.,\\s]*\\d|\\d)\\s*([kKmM])?(?![a-zA-Z])");

    public boolean running = false;
    public int bought = 0;
    private long nextAt = 0, lastOpen = 0, confirmDeadline = 0;
    private boolean awaitingConfirm = false;
    private int marketSyncId = -1;

    public void start() { running = true; awaitingConfirm = false; nextAt = 0; msg("§aAutoRynek: START"); }
    public void stop()  { if (running) msg("§cAutoRynek: STOP (kupiono: " + bought + ")"); running = false; }

    private void msg(String s) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player != null) c.player.sendMessage(Text.literal(s), true);
    }

    public void tick(MinecraftClient c, Config cfg) {
        if (!running) return;
        if (c.player == null || c.world == null || c.interactionManager == null) { running = false; return; }
        long now = System.currentTimeMillis();
        if (now < nextAt) return;

        if (c.currentScreen instanceof GenericContainerScreen gcs) {
            GenericContainerScreenHandler h = gcs.getScreenHandler();
            // okno potwierdzenia (inne syncId niz rynek)
            if (awaitingConfirm && h.syncId != marketSyncId) {
                if (cfg.confirmSlot >= 0 && cfg.confirmSlot < h.slots.size()
                        && !h.getSlot(cfg.confirmSlot).getStack().isEmpty()) {
                    c.interactionManager.clickSlot(h.syncId, cfg.confirmSlot, 0, SlotActionType.PICKUP, c.player);
                    bought++; awaitingConfirm = false;
                    nextAt = now + cfg.actionDelayMs;
                } else if (now > confirmDeadline) { awaitingConfirm = false; }
                return;
            }
            String title = gcs.getTitle().getString();
            if (!cfg.titleContains.isBlank() && !title.toLowerCase().contains(cfg.titleContains.toLowerCase())) {
                nextAt = now + 200; return;
            }
            marketSyncId = h.syncId;
            int size = h.getRows() * 9;
            for (int i = 0; i < size && i < h.slots.size(); i++) {
                if (i == cfg.refreshSlot || i == cfg.confirmSlot) continue;
                ItemStack st = h.getSlot(i).getStack();
                if (st.isEmpty()) continue;
                if (matches(st, cfg)) {
                    c.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.PICKUP, c.player);
                    if (cfg.confirmSlot >= 0) { awaitingConfirm = true; confirmDeadline = now + 2000; }
                    else bought++;
                    nextAt = now + cfg.actionDelayMs;
                    return;
                }
            }
            // nic nie pasuje -> odswiez
            if (cfg.refreshSlot >= 0 && cfg.refreshSlot < h.slots.size()) {
                c.interactionManager.clickSlot(h.syncId, cfg.refreshSlot, 0, SlotActionType.PICKUP, c.player);
            } else {
                c.player.closeHandledScreen();
                lastOpen = 0;
            }
            nextAt = now + cfg.refreshDelayMs;
        } else if (c.currentScreen == null) {
            if (now - lastOpen > 1500) {
                c.player.networkHandler.sendChatCommand(cfg.command);
                lastOpen = now;
                nextAt = now + 500;
            }
        }
    }

    private boolean matches(ItemStack st, Config cfg) {
        long price = price(st);
        if (price < 0) return false;
        String id = Registries.ITEM.getId(st.getItem()).toString();
        String name = st.getName().getString().toLowerCase();
        Set<String> ench = enchants(st);
        for (Config.Rule r : cfg.rules) {
            if (r.maxPrice <= 0 || price > r.maxPrice) continue;
            if (!r.id.isBlank() && !id.equalsIgnoreCase(r.id.contains(":") ? r.id : "minecraft:" + r.id)) continue;
            if (!r.name.isBlank() && !name.contains(r.name.toLowerCase())) continue;
            boolean ok = true;
            for (String e : r.enchants.toLowerCase().split(",")) {
                e = e.trim();
                if (!e.isEmpty() && !ench.contains(e)) { ok = false; break; }
            }
            if (ok) return true;
        }
        return false;
    }

    private Set<String> enchants(ItemStack st) {
        Set<String> out = new HashSet<>();
        for (var type : List.of(DataComponentTypes.ENCHANTMENTS, DataComponentTypes.STORED_ENCHANTMENTS)) {
            ItemEnchantmentsComponent comp = st.get(type);
            if (comp == null) continue;
            for (var e : comp.getEnchantmentEntries()) {
                var key = e.getKey().getKey();
                if (key.isPresent()) out.add(key.get().getValue().getPath() + ":" + e.getIntValue());
            }
        }
        return out;
    }

    /** Cena z lore: preferuje linie z "cena"/"price", w ostatecznosci pierwsza liczba. -1 gdy brak. */
    private long price(ItemStack st) {
        LoreComponent lore = st.get(DataComponentTypes.LORE);
        if (lore == null) return -1;
        long fallback = -1;
        for (Text line : lore.lines()) {
            String s = line.getString();
            Matcher m = PRICE.matcher(s);
            if (!m.find()) continue;
            long v = parse(m.group(1), m.group(2));
            String l = s.toLowerCase();
            if (l.contains("cena") || l.contains("price") || l.contains("koszt")) return v;
            if (fallback < 0) fallback = v;
        }
        return fallback;
    }

    private long parse(String num, String suffix) {
        try {
            if (suffix != null && !suffix.isEmpty()) {
                double d = Double.parseDouble(num.replace(" ", "").replace(',', '.'));
                return (long) (d * (suffix.equalsIgnoreCase("k") ? 1_000L : 1_000_000L));
            }
            return Long.parseLong(num.replaceAll("[^0-9]", ""));
        } catch (Exception e) { return -1; }
    }
}
