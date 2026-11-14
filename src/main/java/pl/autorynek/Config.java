package pl.autorynek;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.*;

public class Config {
    public static class Rule {
        public String id = "";        // np. minecraft:elytra
        public String name = "";      // fragment nazwy (opcjonalnie)
        public String enchants = "";  // np. sharpness:5,unbreaking:3 (opcjonalnie)
        public long maxPrice = 0;
    }
    public String command = "rynek";   // komenda otwierajaca rynek (bez /)
    public String titleContains = "";  // fragment tytulu GUI rynku (puste = kazde)
    public int refreshSlot = -1;       // slot "odswiez" (-1 = zamknij i otworz ponownie)
    public int confirmSlot = -1;       // slot "potwierdz" w oknie zakupu (-1 = brak)
    public int sortSlot = -1;          // slot przycisku sortowania (-1 = nie ruszaj sortowania)
    public String wantedSort = "najnowsze"; // jaka opcja sortowania ma byc ustawiona
    public int itemColStart = 2;       // pierwsza kolumna z przedmiotami (0-8)
    public int itemRows = 4;           // ile gornych wierszy zajmuja przedmioty
    public int settleMs = 300;         // ile czekac po otwarciu okna na zaladowanie
    public int actionDelayMs = 250;
    public int refreshDelayMs = 400;
    public List<Rule> rules = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("autorynek.json"); }

    public static Config load() {
        try {
            if (Files.exists(file())) {
                Config c = GSON.fromJson(Files.readString(file()), Config.class);
                if (c != null) {
                    if (c.rules == null) c.rules = new ArrayList<>();
                    if (c.wantedSort == null) c.wantedSort = "najnowsze";
                    return c;
                }
            }
        } catch (Exception ignored) {}
        return new Config();
    }
    public void save() {
        try { Files.writeString(file(), GSON.toJson(this)); } catch (Exception ignored) {}
    }
}
