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
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import java.util.*;
import java.util.regex.*;

public class Buyer {
    private static final Pattern PRICE = Pattern.compile("(\\d[\\d.,\\s]*\\d|\\d)\\s*([kKmM])?(?![a-zA-Z])");
    private static final String[] SORT_KEYS = {"brak", "najwy", "najni", "najstar", "najnow"};
    private static final String MARKERS = "»>▶►✔✓➤●▸→";

    public boolean running = false;
    public int bought = 0;
    public long spent = 0, startedAt = 0, stoppedAt = 0;
    public String status = "Oczekiwanie...";
    private long pendingPrice = 0, curPrice = 0;
    private long nextAt = 0, lastOpen = 0, confirmDeadline = 0, seenAt = 0, openedAt = 0;
    private boolean awaitingConfirm = false, sortGaveUp = false;
    private int marketSyncId = -1, seenSync = -1, sortCheckedSync = -1, sortClicks = 0;

    public void start() {
        running = true; awaitingConfirm = false; nextAt = 0;
        bought = 0; spent = 0; startedAt = System.currentTimeMillis(); stoppedAt = 0; status = "Oczekiwanie...";
        seenSync = -1; sortCheckedSync = -1; sortClicks = 0; sortGaveUp = false;
        msg("§aAutoRynek: START");
    }
    public void stop() { if (running) msg("§cAutoRynek: STOP (kupiono: " + bought + ")"); halt(); }
    private void halt() { if (running) stoppedAt = System.currentTimeMillis(); running = false; status = "Zatrzymany"; }
    public long elapsedSecs() {
        if (startedAt == 0) return 0;
        long end = running ? System.currentTimeMillis() : (stoppedAt > 0 ? stoppedAt : startedAt);
        return Math.max(0, (end - startedAt) / 1000);
    }

    private void msg(String s) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player != null) c.player.sendMessage(Text.literal(s), true);
    }

    private static boolean inArea(int i, Config cfg) { return i % 9 >= cfg.itemColStart && i / 9 < cfg.itemRows; }

    public void tick(MinecraftClient c, Config cfg) {
        if (!running) return;
        if (c.player == null || c.world == null || c.interactionManager == null) { halt(); return; }
        if (cfg.rules.isEmpty()) {
            halt();
            msg("§cBrak regul. Dodaj regule w AutoRynek -> Przydatne.");
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextAt) return;

        if (c.currentScreen instanceof GenericContainerScreen gcs) {
            GenericContainerScreenHandler h = gcs.getScreenHandler();
            // okno potwierdzenia (inne syncId niz rynek)
            if (awaitingConfirm && h.syncId != marketSyncId) {
                if (cfg.confirmSlot >= 0 && cfg.confirmSlot < h.slots.size()
                        && !h.getSlot(cfg.confirmSlot).getStack().isEmpty()) {
                    c.interactionManager.clickSlot(h.syncId, cfg.confirmSlot, 0, SlotActionType.PICKUP, c.player);
                    bought++; spent += pendingPrice; awaitingConfirm = false; status = "Potwierdzam zakup";
                    nextAt = now + cfg.actionDelayMs;
                } else if (now > confirmDeadline) { awaitingConfirm = false; }
                return;
            }
            String title = gcs.getTitle().getString();
            if (!cfg.titleContains.isBlank() && !title.toLowerCase().contains(cfg.titleContains.toLowerCase())) {
                nextAt = now + 200; return;
            }
            marketSyncId = h.syncId;
            int size = Math.min(h.getRows() * 9, h.slots.size());

            // nowe okno: poczekaj az serwer wysle zawartosc
            if (h.syncId != seenSync) {
                seenSync = h.syncId; seenAt = now; openedAt = now; status = "Skanuje oferty";
                nextAt = now + Math.max(100, cfg.settleMs);
                return;
            }
            boolean any = false;
            for (int i = 0; i < size && !any; i++) if (inArea(i, cfg) && !h.getSlot(i).getStack().isEmpty()) any = true;
            if (!any && now - seenAt < 2000) { nextAt = now + 100; return; }

            // sortowanie -> ma byc ustawione na wantedSort
            if (cfg.sortSlot >= 0 && cfg.sortSlot < size && !sortGaveUp && sortCheckedSync != h.syncId) {
                Boolean st = sortState(h.getSlot(cfg.sortSlot).getStack(), cfg.wantedSort);
                if (st == null) {
                    sortGaveUp = true;
                    msg("§cNie moge odczytac sortowania (slot " + cfg.sortSlot + "). Uzyj przycisku SLOTY i wyslij zrzut.");
                } else if (st) {
                    sortCheckedSync = h.syncId; sortClicks = 0; openedAt = now;
                } else if (sortClicks >= 8) {
                    sortGaveUp = true;
                    msg("§cNie udalo sie ustawic sortowania: " + cfg.wantedSort);
                } else {
                    c.interactionManager.clickSlot(h.syncId, cfg.sortSlot, 1, SlotActionType.PICKUP, c.player);
                    sortClicks++; seenAt = now; status = "Ustawiam sortowanie";
                    nextAt = now + cfg.actionDelayMs + 250;
                    return;
                }
            }

            for (int i = 0; i < size; i++) {
                if (!inArea(i, cfg)) continue;
                if (i == cfg.refreshSlot || i == cfg.confirmSlot || i == cfg.sortSlot) continue;
                ItemStack st = h.getSlot(i).getStack();
                if (st.isEmpty()) continue;
                if (matches(st, cfg)) {
                    c.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.PICKUP, c.player);
                    pendingPrice = curPrice; status = "Kupuje...";
                    if (cfg.confirmSlot >= 0) { awaitingConfirm = true; confirmDeadline = now + 2000; }
                    else { bought++; spent += pendingPrice; }
                    nextAt = now + cfg.actionDelayMs;
                    return;
                }
            }
            // nic nie pasuje -> odswiez
            if (cfg.refreshSlot >= 0 && cfg.refreshSlot < h.slots.size()) {
                c.interactionManager.clickSlot(h.syncId, cfg.refreshSlot, 0, SlotActionType.PICKUP, c.player);
                nextAt = now + cfg.refreshDelayMs;
            } else if (now - openedAt < Math.max(1000, cfg.refreshDelayMs)) {
                nextAt = now + 100;   // okno zostaje otwarte, skanujemy dalej
            } else {
                c.player.closeHandledScreen();
                lastOpen = 0; status = "Odswiezam rynek";
                nextAt = now + 300;
            }
        } else if (c.currentScreen == null) {
            if (now - lastOpen > 1500) {
                c.player.networkHandler.sendChatCommand(cfg.command);
                lastOpen = now; status = "Otwieram rynek";
                nextAt = now + 500;
            }
        }
    }

    // ---------- sortowanie ----------
    private static String strip(String s) { return s.replaceAll("§.", ""); }

    private static String colorKey(Text t) {
        String raw = t.getString();
        if (raw.length() >= 2 && raw.charAt(0) == '§') return "L" + raw.charAt(1);
        final String[] out = {null};
        t.visit((style, s) -> {
            if (out[0] == null && !s.isBlank())
                out[0] = style.getColor() == null ? "none" : Integer.toHexString(style.getColor().getRgb());
            return Optional.empty();
        }, Style.EMPTY);
        return out[0] == null ? "none" : out[0];
    }

    /** true = wybrane, false = niewybrane, null = nie da sie ustalic. */
    private Boolean sortState(ItemStack st, String wanted) {
        LoreComponent lore = st.get(DataComponentTypes.LORE);
        if (lore == null || wanted == null || wanted.isBlank()) return null;
        String w = wanted.toLowerCase().trim();
        List<Text> lines = lore.lines();
        int wi = -1;
        List<Integer> opts = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String s = strip(lines.get(i).getString()).toLowerCase();
            if (wi < 0 && s.contains(w)) wi = i;
            for (String k : SORT_KEYS) if (s.contains(k)) { opts.add(i); break; }
        }
        if (wi < 0) return null;
        String ws = strip(lines.get(wi).getString()).toLowerCase();
        if (ws.contains("aktualn") || ws.contains("wybran")) return true;
        if (!opts.contains(wi)) opts.add(wi);
        if (opts.size() < 2) return null;

        // znaczniki ">" przed wybrana opcja
        boolean wm = MARKERS.indexOf(ws.trim().isEmpty() ? ' ' : ws.trim().charAt(0)) >= 0;
        int otherMarked = 0;
        for (int i : opts) {
            if (i == wi) continue;
            String s = strip(lines.get(i).getString()).trim();
            if (!s.isEmpty() && MARKERS.indexOf(s.charAt(0)) >= 0) otherMarked++;
        }
        if (wm && otherMarked == 0) return true;
        if (!wm && otherMarked > 0) return false;

        // kolor: wybrana opcja ma inny kolor niz reszta
        String wc = colorKey(lines.get(wi));
        Set<String> others = new HashSet<>();
        int diff = 0, same = 0;
        for (int i : opts) {
            if (i == wi) continue;
            String ck = colorKey(lines.get(i));
            others.add(ck);
            if (ck.equals(wc)) same++; else diff++;
        }
        if (same == 0 && others.size() == 1) return true;      // jedyna w innym kolorze
        if (diff == 1 && same >= 1) return false;               // ktos inny jest wyrozniony
        return null;
    }

    // ---------- diagnostyka: wypisz sloty poza obszarem przedmiotow ----------
    public void dump(MinecraftClient c, Config cfg) {
        if (!(c.currentScreen instanceof GenericContainerScreen gcs)) return;
        GenericContainerScreenHandler h = gcs.getScreenHandler();
        var chat = c.inGameHud.getChatHud();
        int size = Math.min(h.getRows() * 9, h.slots.size());
        chat.addMessage(Text.literal("§e[AutoRynek] " + gcs.getTitle().getString() + " | wiersze: " + h.getRows()));
        for (int i = 0; i < size; i++) {
            if (inArea(i, cfg)) continue;
            ItemStack st = h.getSlot(i).getStack();
            if (st.isEmpty()) continue;
            StringBuilder sb = new StringBuilder("§b" + i + "§f: " + st.getName().getString());
            LoreComponent lore = st.get(DataComponentTypes.LORE);
            if (lore != null) for (Text l : lore.lines()) {
                String s = strip(l.getString());
                if (!s.isBlank()) sb.append(" §7| §f").append(s).append(" §8(").append(colorKey(l)).append(")");
            }
            chat.addMessage(Text.literal(sb.toString()));
        }
        msg("§aSloty wypisane na czacie");
    }

    // ---------- diagnostyka: wypisz oferty z okna rynku ----------
    public void dumpItems(MinecraftClient c, Config cfg) {
        if (!(c.currentScreen instanceof GenericContainerScreen gcs)) return;
        GenericContainerScreenHandler h = gcs.getScreenHandler();
        var chat = c.inGameHud.getChatHud();
        int size = Math.min(h.getRows() * 9, h.slots.size());
        for (int i = 0; i < size; i++) {
            if (!inArea(i, cfg)) continue;
            ItemStack st = h.getSlot(i).getStack();
            if (st.isEmpty()) continue;
            String id = Registries.ITEM.getId(st.getItem()).getPath();
            StringBuilder sb = new StringBuilder("§b" + i + "§f: " + id + " §7| §f" + st.getName().getString()
                + " §8(" + colorKey(st.getName()) + ") §7| §e" + String.join(",", enchants(st)) + " §7| §acena " + price(st));
            LoreComponent lore = st.get(DataComponentTypes.LORE);
            if (lore != null) {
                StringBuilder lr = new StringBuilder();
                for (Text l : lore.lines()) { String s = strip(l.getString()); if (!s.isBlank()) lr.append(s).append(" / "); }
                String t = lr.toString();
                sb.append(" §8").append(t.length() > 160 ? t.substring(0, 160) : t);
            }
            chat.addMessage(Text.literal(sb.toString()));
        }
        msg("§aOferty wypisane na czacie");
    }

    // ---------- dopasowanie ----------
    private boolean matches(ItemStack st, Config cfg) {
        long price = price(st);
        if (price < 0) return false;
        String id = Registries.ITEM.getId(st.getItem()).toString();
        String name = strip(st.getName().getString()).toLowerCase();
        Set<String> ench = enchants(st);
        for (Config.Rule r : cfg.rules) {
            if (r.maxPrice <= 0 || price > r.maxPrice) continue;
            if (!r.id.isBlank() && !id.equalsIgnoreCase(r.id.contains(":") ? r.id : "minecraft:" + r.id)) continue;
            if (!r.name.isBlank() && !name.contains(strip(r.name.replace('&', '§')).toLowerCase())) continue;
            boolean ok = true;
            for (String e : r.enchants.toLowerCase().split(",")) {
                e = e.trim();
                if (!e.isEmpty() && !ench.contains(e)) { ok = false; break; }
            }
            if (ok) { curPrice = price; return true; }
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
            String s = strip(line.getString());
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
