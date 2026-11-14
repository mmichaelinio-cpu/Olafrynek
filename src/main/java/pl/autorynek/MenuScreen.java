package pl.autorynek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.*;

/** Menu AutoRynek: ciemny panel, zakladki, duze przyciski pod dotyk. */
public class MenuScreen extends Screen {
    private static final int BG = 0xE60B1220, PANEL = 0xFF111A2E, PANEL2 = 0xFF1B2745,
        ACCENT = 0xFF14B8A6, ACCENT_H = 0xFF2DD4BF, GREEN = 0xFF15803D, GREEN_H = 0xFF22C55E,
        RED = 0xFFB91C1C, RED_H = 0xFFEF4444, MUTED = 0xFF9FB0D0, WHITE = 0xFFFFFFFF;

    /** Plaski przycisk z kolorem. */
    static class FlatButton extends ButtonWidget {
        private final int fill, hover;
        FlatButton(int x, int y, int w, int h, String label, Runnable r, int fill, int hover) {
            super(x, y, w, h, Text.literal(label), b -> r.run(), DEFAULT_NARRATION_SUPPLIER);
            this.fill = fill; this.hover = hover;
        }
        @Override
        public void renderWidget(DrawContext ctx, int mx, int my, float delta) {
            ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), isHovered() ? hover : fill);
            ctx.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), 0x66000000);
            var tr = MinecraftClient.getInstance().textRenderer;
            ctx.drawCenteredTextWithShadow(tr, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, WHITE);
        }
    }

    private enum Mode { LIST, FORM, PRESETS, SETTINGS }
    private static final String[][] PRESETS = Presets.LIST;
    private int ppage = 0;
    private String presetPrice = "", toast = "";
    private TextFieldWidget fPreset;
    private final Screen parent;
    private Mode mode = Mode.LIST;
    private int page = 0;
    private TextFieldWidget fId, fName, fEnch, fPrice;
    private final TextFieldWidget[] sf = new TextFieldWidget[10];
    private String dId = "", dName = "", dEnch = "", dPrice = "";
    private final List<Object[]> labels = new ArrayList<>(); // {text, x, y, color, centered}

    public MenuScreen(Screen parent) { super(Text.literal("AutoRynek")); this.parent = parent; }

    @Override protected void init() { build(); }

    private int pw() { return Math.min(width - 8, 320); }

    private TextFieldWidget field(int x, int y, int w, String text, String hint) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal(hint));
        f.setMaxLength(120); f.setText(text); f.setPlaceholder(Text.literal(hint));
        return addDrawableChild(f);
    }
    private void btn(String label, int x, int y, int w, int h, int fill, int hover, Runnable r) {
        addDrawableChild(new FlatButton(x, y, w, h, label, r, fill, hover));
    }
    private void label(String s, int x, int y, int color, boolean center) { labels.add(new Object[]{s, x, y, color, center}); }

    private void build() {
        if (mode == Mode.PRESETS && fPreset != null) presetPrice = fPreset.getText();
        clearChildren(); labels.clear();
        Config cfg = AutoRynek.config;
        int pw = pw(), px = (width - pw) / 2, pr = px + pw, ix = px + 10, iw = pw - 20;

        btn("X", pr - 34, 9, 26, 20, PANEL2, RED_H, this::close);

        // zakladki
        int tw = (iw - 12) / 4, ty = 34;
        String[] names = {"Reguly", "Nowa", "Przydatne", "Ustawienia"};
        Mode[] modes = {Mode.LIST, Mode.FORM, Mode.PRESETS, Mode.SETTINGS};
        for (int i = 0; i < 4; i++) {
            final Mode m = modes[i];
            btn(names[i], ix + i * (tw + 4), ty, tw, 22, mode == m ? ACCENT : PANEL2, mode == m ? ACCENT_H : 0xFF2A3B66, () -> switchTo(m));
        }
        int ct = 64;
        switch (mode) {
            case LIST -> {
                boolean run = AutoRynek.buyer.running;
                btn(run ? "STOP" : "START", ix, ct, iw, 28, run ? RED : GREEN, run ? RED_H : GREEN_H, () -> { AutoRynek.toggle(); build(); });
                int ry0 = ct + 36, rowH = 26;
                int perPage = Math.max(1, Math.min(6, (height - 8 - 34 - ry0) / rowH));
                int pages = Math.max(1, (cfg.rules.size() + perPage - 1) / perPage);
                page = Math.min(page, pages - 1);
                if (cfg.rules.isEmpty()) label("Brak regul. Otworz 'Nowa regula'.", width / 2, ry0 + 8, MUTED, true);
                for (int i = 0; i < perPage; i++) {
                    int idx = page * perPage + i;
                    if (idx >= cfg.rules.size()) break;
                    Config.Rule r = cfg.rules.get(idx);
                    boolean hasLabel = r.label != null && !r.label.isBlank();
                    String head = hasLabel ? r.label.replace('&', '§') + "§r" : (r.id.isBlank() ? "*" : r.id.replace("minecraft:", ""));
                    String txt = head
                        + (r.name.isBlank() ? "" : " \"" + r.name.replace('&', '§') + "§r\"")
                        + (hasLabel || r.enchants.isBlank() ? "" : " [" + r.enchants + "]") + "  <= " + r.maxPrice;
                    txt = textRenderer.trimToWidth(txt, iw - 40);
                    int y = ry0 + i * rowH;
                    btn(txt, ix, y, iw - 30, 22, PANEL2, PANEL2, () -> {});
                    btn("X", ix + iw - 26, y, 26, 22, RED, RED_H, () -> { cfg.rules.remove(idx); cfg.save(); build(); });
                }
                int by = height - 8 - 26;
                btn("<", ix, by, 44, 22, PANEL2, 0xFF2A3B66, () -> { if (page > 0) { page--; build(); } });
                btn(">", ix + iw - 44, by, 44, 22, PANEL2, 0xFF2A3B66, () -> { if (page < pages - 1) { page++; build(); } });
                label("Strona " + (page + 1) + "/" + pages, width / 2, by + 7, MUTED, true);
            }
            case FORM -> {
                String[] lb = {"ID przedmiotu (np. elytra)", "Fragment nazwy (& = kolor, np. &cMiecz)", "Enchanty (np. sharpness:5,unbreaking:3)", "Maksymalna cena (np. 5000, 2k, 1.5m)"};
                String[] dv = {dId, dName, dEnch, dPrice};
                TextFieldWidget[] f = new TextFieldWidget[4];
                for (int i = 0; i < 4; i++) {
                    int y = ct + i * 34;
                    label(lb[i], ix, y, MUTED, false);
                    f[i] = field(ix, y + 10, iw, dv[i], "");
                }
                fId = f[0]; fName = f[1]; fEnch = f[2]; fPrice = f[3];
                btn("Dodaj regule", ix, ct + 4 * 34 + 2, iw, 28, ACCENT, ACCENT_H, () -> {
                    long p = parsePrice(fPrice.getText());
                    if (p <= 0) { fPrice.setText(""); fPrice.setPlaceholder(Text.literal("§cPodaj poprawna cene!")); return; }
                    Config.Rule r = new Config.Rule();
                    r.id = fId.getText().trim(); r.name = fName.getText().trim();
                    r.enchants = fEnch.getText().trim(); r.maxPrice = p;
                    cfg.rules.add(r); cfg.save();
                    dId = dName = dEnch = dPrice = "";
                    switchTo(Mode.LIST);
                });
            }
            case PRESETS -> {
                label("Max cena dla dodawanych (np. 5000, 2k, 1.5m):", ix, ct, MUTED, false);
                fPreset = field(ix, ct + 10, iw, presetPrice, "");
                int y0 = ct + 36, rowH = 26;
                int perPage = Math.max(1, Math.min(7, (height - 8 - 34 - y0) / rowH));
                int pages = Math.max(1, (PRESETS.length + perPage - 1) / perPage);
                ppage = Math.min(ppage, pages - 1);
                for (int i = 0; i < perPage; i++) {
                    int idx = ppage * perPage + i;
                    if (idx >= PRESETS.length) break;
                    final String[] p = PRESETS[idx];
                    btn(p[0].replace('&', '§'), ix, y0 + i * rowH, iw, 22, PANEL2, 0xFF2A3B66, () -> addPreset(p));
                }
                int by = height - 8 - 26;
                btn("<", ix, by, 44, 22, PANEL2, 0xFF2A3B66, () -> { if (ppage > 0) { ppage--; build(); } });
                btn(">", ix + iw - 44, by, 44, 22, PANEL2, 0xFF2A3B66, () -> { if (ppage < pages - 1) { ppage++; build(); } });
                label(toast.isEmpty() ? "Dotknij przedmiot, aby dodac regule   " + (ppage + 1) + "/" + pages : toast, width / 2, by + 7, MUTED, true);
            }
            case SETTINGS -> {
                int cw = (iw - 8) / 2;
                String[] lb = {"Komenda rynku", "Tytul okna (fragment)", "Slot odswiez (-1=brak)", "Slot potwierdz (-1=brak)",
                    "Slot sortowania (-1=brak)", "Sortuj na", "Pierwsza kolumna przedm.", "Wiersze przedmiotow",
                    "Opoznienie akcji (ms)", "Opoznienie odsw. (ms)"};
                String[] val = {cfg.command, cfg.titleContains, String.valueOf(cfg.refreshSlot), String.valueOf(cfg.confirmSlot),
                    String.valueOf(cfg.sortSlot), cfg.wantedSort, String.valueOf(cfg.itemColStart), String.valueOf(cfg.itemRows),
                    String.valueOf(cfg.actionDelayMs), String.valueOf(cfg.refreshDelayMs)};
                for (int i = 0; i < 10; i++) {
                    int x = ix + (i % 2) * (cw + 8), y = ct + (i / 2) * 31;
                    label(lb[i], x, y, MUTED, false);
                    sf[i] = field(x, y + 10, cw, val[i], "");
                }
                btn("Zapisz", ix, ct + 5 * 31 + 2, iw, 28, ACCENT, ACCENT_H, () -> { applySettings(); switchTo(Mode.LIST); });
            }
        }
    }

    private void addPreset(String[] p) {
        presetPrice = fPreset.getText();
        long price = parsePrice(presetPrice);
        if (price <= 0) { toast = "§cNajpierw wpisz cene!"; build(); return; }
        Config.Rule r = new Config.Rule();
        r.id = p[1]; r.enchants = p[2]; r.label = p[0]; r.maxPrice = price;
        AutoRynek.config.rules.add(r); AutoRynek.config.save();
        toast = "§aDodano: " + p[0].replace('&', '§') + " §r<= " + price;
        build();
    }

    private void switchTo(Mode m) {
        if (mode == Mode.SETTINGS) applySettings();
        if (mode == Mode.FORM && fId != null) { dId = fId.getText(); dName = fName.getText(); dEnch = fEnch.getText(); dPrice = fPrice.getText(); }
        mode = m; build();
    }

    private void applySettings() {
        Config c = AutoRynek.config;
        if (mode != Mode.SETTINGS || sf[0] == null) return;
        c.command = sf[0].getText().trim().replaceFirst("^/", "");
        c.titleContains = sf[1].getText().trim();
        c.refreshSlot = i(sf[2].getText(), -1);
        c.confirmSlot = i(sf[3].getText(), -1);
        c.sortSlot = i(sf[4].getText(), -1);
        c.wantedSort = sf[5].getText().trim();
        c.itemColStart = Math.max(0, Math.min(8, i(sf[6].getText(), 2)));
        c.itemRows = Math.max(1, Math.min(6, i(sf[7].getText(), 4)));
        c.actionDelayMs = Math.max(50, i(sf[8].getText(), 250));
        c.refreshDelayMs = Math.max(100, i(sf[9].getText(), 400));
        c.save();
    }
    private static int i(String s, int d) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return d; } }
    private static long parsePrice(String s) {
        try {
            s = s.trim().toLowerCase().replace(" ", "").replace(',', '.');
            double m = 1;
            if (s.endsWith("k")) { m = 1_000; s = s.substring(0, s.length() - 1); }
            else if (s.endsWith("m")) { m = 1_000_000; s = s.substring(0, s.length() - 1); }
            return (long) (Double.parseDouble(s) * m);
        } catch (Exception e) { return -1; }
    }

    /** Wlasne tlo (bez rozmycia vanilli, zeby nie rysowac dwa razy). */
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float delta) {}

    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        int pw = pw(), px = (width - pw) / 2, pr = px + pw;
        ctx.fill(0, 0, width, height, BG);
        ctx.fill(px, 4, pr, height - 4, PANEL);
        ctx.fill(px, 4, pr, 6, ACCENT);
        ctx.drawTextWithShadow(textRenderer, "AutoRynek", px + 10, 15, ACCENT_H);
        ctx.drawTextWithShadow(textRenderer, "anarchia.gg", px + 16 + textRenderer.getWidth("AutoRynek"), 15, MUTED);
        boolean run = AutoRynek.buyer.running;
        String st = (run ? "DZIALA" : "STOP") + " | kupiono " + AutoRynek.buyer.bought;
        int sw = textRenderer.getWidth(st), sx = pr - 42 - sw;
        ctx.fill(sx - 5, 11, sx + sw + 5, 27, run ? GREEN : PANEL2);
        ctx.drawTextWithShadow(textRenderer, st, sx, 15, WHITE);
        super.render(ctx, mx, my, delta);
        for (Object[] l : labels) {
            String s = (String) l[0]; int x = (Integer) l[1], y = (Integer) l[2], col = (Integer) l[3];
            if ((Boolean) l[4]) ctx.drawCenteredTextWithShadow(textRenderer, s, x, y, col);
            else ctx.drawTextWithShadow(textRenderer, s, x, y, col);
        }
    }

    @Override public void close() { applySettings(); client.setScreen(parent); }
}
