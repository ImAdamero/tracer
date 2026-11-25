package pl.tracer.nametags;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Ustawienia moda, zapisywane w config/tracer-nametags.properties. */
public final class TracerConfig {
    public static int tracerThickness = 1;   // grubość linii w pikselach
    public static int tracerRange = 128;     // do ilu CHUNKÓW rysować tracery
    public static int nametagRange = 32;     // do ilu CHUNKÓW rysować napisy
    public static int tracerColor = 0;
    public static int tagScale = 70;          // rozmiar napisów w % (ściśnięte info)
    public static int freecamSpeed = 12;      // prędkość freecam w blokach/s

    public static boolean showTracers = true;
    public static boolean showNametags = true;
    public static boolean showNick = true;
    public static boolean showHearts = true;
    public static boolean showHeld = true;
    public static boolean showArmor = true;
    public static boolean showEnchants = true;
    public static boolean showDurability = true;
    public static boolean showCoords = true;   // koordynaty nad graczem
    public static boolean showList = true;     // lista graczy z koordynatami w rogu
    public static int memorySeconds = 120;     // jak długo pamiętać ostatnią pozycję gracza (0 = wyłączone)

    public static final int[] COLORS = {0xFF00FFFF, 0xFFFF5555, 0xFF55FF55, 0xFFFFFFFF, 0xFFFFFF55, 0xFFFF55FF};
    public static final String[] COLOR_NAMES = {"Cyjan", "Czerwony", "Zielony", "Biały", "Żółty", "Różowy"};

    private TracerConfig() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("tracer-nametags.properties");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        tracerThickness = clamp(geti(p, "tracerThickness", tracerThickness), 1, 6);
        tracerRange = clamp(geti(p, "tracerRangeChunks", tracerRange), 1, 256);
        nametagRange = clamp(geti(p, "nametagRangeChunks", nametagRange), 1, 256);
        tracerColor = clamp(geti(p, "tracerColor", tracerColor), 0, COLORS.length - 1);
        tagScale = clamp(geti(p, "tagScale", tagScale), 30, 120);
        freecamSpeed = clamp(geti(p, "freecamSpeed", freecamSpeed), 1, 100);
        showTracers = getb(p, "showTracers", showTracers);
        showNametags = getb(p, "showNametags", showNametags);
        showNick = getb(p, "showNick", showNick);
        showHearts = getb(p, "showHearts", showHearts);
        showHeld = getb(p, "showHeld", showHeld);
        showArmor = getb(p, "showArmor", showArmor);
        showEnchants = getb(p, "showEnchants", showEnchants);
        showDurability = getb(p, "showDurability", showDurability);
        showCoords = getb(p, "showCoords", showCoords);
        showList = getb(p, "showList", showList);
        memorySeconds = clamp(geti(p, "memorySeconds", memorySeconds), 0, 600);
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("tracerThickness", String.valueOf(tracerThickness));
        p.setProperty("tracerRangeChunks", String.valueOf(tracerRange));
        p.setProperty("nametagRangeChunks", String.valueOf(nametagRange));
        p.setProperty("tracerColor", String.valueOf(tracerColor));
        p.setProperty("tagScale", String.valueOf(tagScale));
        p.setProperty("freecamSpeed", String.valueOf(freecamSpeed));
        p.setProperty("showTracers", String.valueOf(showTracers));
        p.setProperty("showNametags", String.valueOf(showNametags));
        p.setProperty("showNick", String.valueOf(showNick));
        p.setProperty("showHearts", String.valueOf(showHearts));
        p.setProperty("showHeld", String.valueOf(showHeld));
        p.setProperty("showArmor", String.valueOf(showArmor));
        p.setProperty("showEnchants", String.valueOf(showEnchants));
        p.setProperty("showDurability", String.valueOf(showDurability));
        p.setProperty("showCoords", String.valueOf(showCoords));
        p.setProperty("showList", String.valueOf(showList));
        p.setProperty("memorySeconds", String.valueOf(memorySeconds));
        try (OutputStream out = Files.newOutputStream(file())) {
            p.store(out, "Tracer Nametags");
        } catch (IOException ignored) {
        }
    }

    private static int geti(Properties p, String k, int def) {
        try {
            return Integer.parseInt(p.getProperty(k, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean getb(Properties p, String k, boolean def) {
        return Boolean.parseBoolean(p.getProperty(k, String.valueOf(def)).trim());
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
