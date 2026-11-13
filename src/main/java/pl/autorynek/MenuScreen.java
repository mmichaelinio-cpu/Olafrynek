package pl.autorynek;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Nowe menu: duze przyciski pod dotyk, trzy zakladki (Reguly / Nowa regula / Ustawienia). */
public class MenuScreen extends Screen {
    private enum Mode { LIST, FORM, SETTINGS }
    private final Screen parent;
    private Mode mode = Mode.LIST;
    private int page = 0;
    private TextFieldWidget fId, fName, fEnch, fPrice;
    private TextFieldWidget sCmd, sTitle, sRefresh, sConfirm, sAct, sRef;
    private String dId = "", dName = "", dEnch = "", dPrice = "";

    public MenuScreen(Screen parent) { super(Text.literal("AutoRynek")); this.parent = parent; }

    @Override protected void init() { build(); }

    private TextFieldWidget field(int x, int y, int w, String text, String hint) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 22, Text.literal(hint));
        f.setMaxLength(120); f.setText(text); f.setPlaceholder(Text.literal(hint));
        return addDrawableChild(f);
    }
    private void btn(String label, int x, int y, int w, Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> r.run()).dimensions(x, y, w, 24).build());
    }

    private void build() {
        clearChildren();
        Config cfg = AutoRynek.config;
        int cx = width / 2, w = Math.min(width - 20, 300), x = cx - w / 2, third = (w - 8) / 3;
        // zakladki
        btn("Reguly",    x,                 6, third, () -> switchTo(Mode.LIST));
        btn("Nowa",      x + third + 4,     6, third, () -> switchTo(Mode.FORM));
        btn("Ustawienia",x + 2 * (third+4), 6, third, () -> switchTo(Mode.SETTINGS));
        int top = 40;
        switch (mode) {
            case LIST -> {
                btn(AutoRynek.buyer.running ? "§cSTOP" : "§aSTART", x, top, w, () -> { AutoRynek.toggle(); build(); });
                int perPage = Math.max(1, Math.min(5, (height - top - 24 - 34 - 30) / 26));
                int pages = Math.max(1, (cfg.rules.size() + perPage - 1) / perPage);
                page = Math.min(page, pages - 1);
                for (int i = 0; i < perPage; i++) {
                    int idx = page * perPage + i;
                    if (idx >= cfg.rules.size()) break;
                    Config.Rule r = cfg.rules.get(idx);
                    String label = "✕ " + (r.id.isBlank() ? "*" : r.id.replace("minecraft:", ""))
                        + (r.name.isBlank() ? "" : " \"" + r.name + "\"")
                        + (r.enchants.isBlank() ? "" : " [" + r.enchants + "]") + " <= " + r.maxPrice;
                    btn(label, x, top + 30 + i * 26, w, () -> { cfg.rules.remove(idx); cfg.save(); build(); });
                }
                int by = height - 30;
                btn("◀", x, by, 60, () -> { if (page > 0) { page--; build(); } });
                btn("▶", x + w - 60, by, 60, () -> { if (page < pages - 1) { page++; build(); } });
                btn("Zamknij", cx - 50, by, 100, this::close);
            }
            case FORM -> {
                fId    = field(x, top,      w, dId,    "ID przedmiotu, np. elytra");
                fName  = field(x, top + 30, w, dName,  "Fragment nazwy (opcjonalnie)");
                fEnch  = field(x, top + 60, w, dEnch,  "Enchanty np. sharpness:5,unbreaking:3");
                fPrice = field(x, top + 90, w, dPrice, "Maks. cena (np. 5000, 2k, 1.5m)");
                btn("Dodaj regule", x, top + 122, w, () -> {
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
            case SETTINGS -> {
                int half = (w - 6) / 2, x2 = x + half + 6;
                sCmd     = field(x,  top,      half, cfg.command,       "Komenda rynku");
                sTitle   = field(x2, top,      half, cfg.titleContains, "Tytul GUI (fragment)");
                sRefresh = field(x,  top + 32, half, String.valueOf(cfg.refreshSlot), "Slot odswiez (-1=brak)");
                sConfirm = field(x2, top + 32, half, String.valueOf(cfg.confirmSlot), "Slot potwierdz (-1=brak)");
                sAct     = field(x,  top + 64, half, String.valueOf(cfg.actionDelayMs),  "Opoznienie akcji ms");
                sRef     = field(x2, top + 64, half, String.valueOf(cfg.refreshDelayMs), "Opoznienie odswiez ms");
                btn("Zapisz", x, top + 100, w, () -> { applySettings(); switchTo(Mode.LIST); });
            }
        }
    }

    private void switchTo(Mode m) {
        if (mode == Mode.SETTINGS) applySettings();
        if (mode == Mode.FORM && fId != null) { dId = fId.getText(); dName = fName.getText(); dEnch = fEnch.getText(); dPrice = fPrice.getText(); }
        mode = m; build();
    }

    private void applySettings() {
        Config c = AutoRynek.config;
        if (sCmd == null) return;
        c.command = sCmd.getText().trim().replaceFirst("^/", "");
        c.titleContains = sTitle.getText().trim();
        c.refreshSlot = i(sRefresh.getText(), -1);
        c.confirmSlot = i(sConfirm.getText(), -1);
        c.actionDelayMs = Math.max(50, i(sAct.getText(), 250));
        c.refreshDelayMs = Math.max(100, i(sRef.getText(), 400));
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

    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);
        super.render(ctx, mx, my, delta);
        String st = AutoRynek.buyer.running ? "§aDziala" : "§7Zatrzymany";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("AutoRynek • " + st + " §f• kupiono: " + AutoRynek.buyer.bought), width / 2, height - 12, 0xFFFFFF);
    }

    @Override public void close() { applySettings(); client.setScreen(parent); }
}
