package pl.tracer.nametags;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntSupplier;

/**
 * Menu: zwykłe widgety Minecrafta obsługują kliknięcia, a wygląd (animacje, kolory)
 * rysujemy sami na wierzchu.
 */
public class ConfigScreen extends Screen {

    private static final int ACCENT = 0xFF00AEFF;
    private static final int PANEL_BG = 0xFF141418;
    private static final int WIDGET = 0xFF22222A;
    private static final int WIDGET_HOVER = 0xFF30303A;
    private static final int MUTED = 0xFF8A8A95;
    private static final int W = 160, H = 20, GAP = 4, PAD = 12;

    // animacje (wspólne)
    private static float fade = 1f;
    private static float frameDt = 0.016f;

    private final Screen parent;
    private final long openAt = System.nanoTime();
    private long lastNs = openAt;
    private int panelX, panelY, panelW, panelH;
    private int labelTracery, labelNapisy, labelFreecam;
    private final List<Vis> vis = new ArrayList<>();

    public ConfigScreen(Screen parent) {
        super(Text.literal("Tracer Nametags"));
        this.parent = parent;
    }

    // ---------- kolory / animacje ----------

    /** Kolor półprzezroczysty (panel, napisy poza widgetami). */
    private static int a(int argb) {
        int al = Math.max(4, (int) (((argb >>> 24) & 0xFF) * fade));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    /** Kolor widgetu: zamiast alfy mieszamy z tłem panelu, bo widgety rysujemy na wierzchu. */
    private static int c(int argb) {
        return lerp(PANEL_BG, 0xFF000000 | (argb & 0xFFFFFF), fade) | 0xFF000000;
    }

    private static int lerp(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int al = (int) (((c1 >>> 24) & 0xFF) + (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t);
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (al << 24) | (r << 16) | (g << 8) | b;
    }

    private static float approach(float cur, float target, float speed) {
        return cur + (target - cur) * Math.min(1f, frameDt * speed);
    }

    // ---------- układ ----------

    @Override
    protected void init() {
        vis.clear();
        panelW = W * 2 + GAP + PAD * 2;
        panelH = 286;
        panelX = width / 2 - panelW / 2;
        panelY = Math.max(6, (height - panelH) / 2);

        int left = panelX + PAD;
        int right = left + W + GAP;
        int row = H + GAP;
        int y = panelY + 34;

        labelTracery = y;
        y += 12;
        addSlider(left, y, "Grubość", " px", 1, 6, TracerConfig.tracerThickness, v -> TracerConfig.tracerThickness = (int) v);
        addSlider(right, y, "Zasięg", " ch.", 1, 256, TracerConfig.tracerRange, v -> TracerConfig.tracerRange = (int) v);
        y += row;
        addColorButton(left, y);
        addToggle(right, y, "Linie", () -> TracerConfig.showTracers, v -> TracerConfig.showTracers = v);
        y += row;
        addSlider(left, y, "Pamięć pozycji", " s", 0, 600, TracerConfig.memorySeconds, v -> TracerConfig.memorySeconds = (int) v);
        addToggle(right, y, "Lista graczy", () -> TracerConfig.showList, v -> TracerConfig.showList = v);
        y += row + 6;

        labelNapisy = y;
        y += 12;
        addSlider(left, y, "Zasięg napisów", " ch.", 1, 256, TracerConfig.nametagRange, v -> TracerConfig.nametagRange = (int) v);
        addSlider(right, y, "Rozmiar", "%", 30, 120, TracerConfig.tagScale, v -> TracerConfig.tagScale = (int) v);
        y += row;
        addToggle(left, y, "Napisy", () -> TracerConfig.showNametags, v -> TracerConfig.showNametags = v);
        addToggle(right, y, "Nick", () -> TracerConfig.showNick, v -> TracerConfig.showNick = v);
        y += row;
        addToggle(left, y, "Serca", () -> TracerConfig.showHearts, v -> TracerConfig.showHearts = v);
        addToggle(right, y, "Ręce", () -> TracerConfig.showHeld, v -> TracerConfig.showHeld = v);
        y += row;
        addToggle(left, y, "Zbroja", () -> TracerConfig.showArmor, v -> TracerConfig.showArmor = v);
        addToggle(right, y, "Enchanty", () -> TracerConfig.showEnchants, v -> TracerConfig.showEnchants = v);
        y += row;
        addToggle(left, y, "Wytrzymałość", () -> TracerConfig.showDurability, v -> TracerConfig.showDurability = v);
        addToggle(right, y, "Koordy", () -> TracerConfig.showCoords, v -> TracerConfig.showCoords = v);
        y += row + 6;

        labelFreecam = y;
        y += 12;
        addSlider(left, y, "Prędkość", " bl/s", 1, 100, TracerConfig.freecamSpeed, v -> TracerConfig.freecamSpeed = (int) v);
        ButtonWidget fc = ButtonWidget.builder(Text.literal("Freecam"), b -> {
            if (client != null) Freecam.toggle(client);
        }).dimensions(right, y, W, H).build();
        addDrawableChild(fc);
        vis.add(new Vis(right, y, W, H, "Freecam", true, Freecam::isActive, null, null));
    }

    private void addSlider(int x, int y, String label, String unit, double min, double max, double cur, DoubleConsumer cb) {
        Slider s = new Slider(x, y, W, H, label, unit, min, max, cur, cb);
        addDrawableChild(s);
        Vis v = new Vis(x, y, W, H, label, false, null, null, s);
        vis.add(v);
    }

    private void addToggle(int x, int y, String name, BooleanSupplier get, Consumer<Boolean> set) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(name), btn -> set.accept(!get.getAsBoolean()))
                .dimensions(x, y, W, H).build();
        addDrawableChild(b);
        vis.add(new Vis(x, y, W, H, name, true, get, null, null));
    }

    private void addColorButton(int x, int y) {
        ButtonWidget b = ButtonWidget.builder(Text.literal("Kolor"), btn ->
                TracerConfig.tracerColor = (TracerConfig.tracerColor + 1) % TracerConfig.COLORS.length)
                .dimensions(x, y, W, H).build();
        addDrawableChild(b);
        vis.add(new Vis(x, y, W, H, "Kolor", false, null, () -> TracerConfig.COLORS[TracerConfig.tracerColor], null));
    }

    // ---------- rysowanie ----------

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, a(0xFF000000));
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, a(0xF0141418));
        // niebieski pasek rozjeżdża się od środka
        float open = Math.min(1f, (System.nanoTime() - openAt) / 350_000_000f);
        int barW = (int) (panelW * (1f - (1f - open) * (1f - open)));
        int barX = panelX + (panelW - barW) / 2;
        ctx.fill(barX, panelY, barX + barW, panelY + 2, a(ACCENT));
        section(ctx, "TRACERY", labelTracery);
        section(ctx, "NAPISY", labelNapisy);
        section(ctx, "FREECAM", labelFreecam);
    }

    private void section(DrawContext ctx, String name, int y) {
        int x = panelX + PAD;
        ctx.drawText(textRenderer, name, x, y, a(ACCENT), true);
        int tw = textRenderer.getWidth(name);
        ctx.fill(x + tw + 6, y + 4, panelX + panelW - PAD, y + 5, a(0x44FFFFFF));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        frameDt = Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;

        // wjazd okna: przejaśnienie + przesunięcie z dołu (ease-out)
        float t = Math.min(1f, (now - openAt) / 260_000_000f);
        float e = 1f - (1f - t) * (1f - t) * (1f - t);
        fade = 0.15f + 0.85f * e;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(0f, (1f - e) * 14f);
        super.render(ctx, mouseX, mouseY, delta);          // widgety Minecrafta (klikalne)
        for (Vis v : vis) v.draw(ctx, textRenderer, mouseX, mouseY - Math.round((1f - e) * 14f)); // nasz wygląd na wierzchu
        ctx.drawText(textRenderer, title, panelX + PAD, panelY + 8, a(0xFFFFFFFF), true);
        ctx.drawText(textRenderer, "R tracery  |  V freecam  |  Prawy Shift menu",
                panelX + PAD, panelY + 20, a(MUTED), false);
        ctx.getMatrices().popMatrix();
    }

    @Override
    public void removed() {
        TracerConfig.save();
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    // ---------- wygląd jednego widgetu ----------

    private static class Vis {
        final int x, y, w, h;
        final String name;
        final boolean toggle;
        final BooleanSupplier on;
        final IntSupplier swatch;
        final Slider slider;
        float hover = 0f;
        float onT;
        float shown;

        Vis(int x, int y, int w, int h, String name, boolean toggle, BooleanSupplier on, IntSupplier swatch, Slider slider) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.name = name;
            this.toggle = toggle;
            this.on = on;
            this.swatch = swatch;
            this.slider = slider;
            this.onT = (on != null && on.getAsBoolean()) ? 1f : 0f;
            this.shown = slider != null ? (float) slider.getValue() : 0f;
        }

        void draw(DrawContext ctx, TextRenderer tr, int mx, int my) {
            boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
            hover = approach(hover, hov ? 1f : 0f, 14f);

            if (slider != null) {
                shown = approach(shown, (float) slider.getValue(), 18f);
                ctx.fill(x, y, x + w, y + h, c(lerp(WIDGET, WIDGET_HOVER, hover)));
                int fw = Math.round(w * shown);
                ctx.fill(x, y, x + fw, y + h, c(lerp(0xFF0B5C85, ACCENT, 0.35f + 0.65f * hover)));
                ctx.fill(Math.max(x, x + fw - 1), y, Math.min(x + w, x + fw + 1), y + h, c(0xFFFFFFFF));
                String val = slider.text();
                ctx.drawText(tr, name, x + 6, y + (h - 8) / 2, c(0xFFFFFFFF), true);
                ctx.drawText(tr, val, x + w - 6 - tr.getWidth(val), y + (h - 8) / 2, c(0xFFFFFFFF), true);
                return;
            }

            onT = approach(onT, (on != null && on.getAsBoolean()) ? 1f : 0f, 14f);
            ctx.fill(x, y, x + w, y + h, c(lerp(WIDGET, WIDGET_HOVER, hover)));
            int edge = Math.round(2f * Math.max(hover * 0.6f, onT));
            if (edge > 0) ctx.fill(x, y, x + edge, y + h, c(ACCENT));
            int tcol = lerp(0xFFB4B4C0, 0xFFFFFFFF, Math.max(hover, onT));
            ctx.drawText(tr, name, x + 8, y + (h - 8) / 2, c(tcol), false);

            if (toggle) {
                int tw = 18, th = 8;
                int tx = x + w - 8 - tw;
                int ty = y + (h - th) / 2;
                ctx.fill(tx, ty, tx + tw, ty + th, c(lerp(0xFF3C3C48, ACCENT, onT)));
                int kx = tx + 1 + Math.round((tw - th) * onT);
                ctx.fill(kx, ty + 1, kx + th - 2, ty + th - 1, c(0xFFFFFFFF));
            } else if (swatch != null) {
                int sx = x + w - 8 - 12;
                ctx.fill(sx - 1, y + 4, sx + 13, y + h - 4, c(0xFF000000));
                ctx.fill(sx, y + 5, sx + 12, y + h - 5, c(swatch.getAsInt()));
            }
        }
    }

    // ---------- suwak (obsługa przeciągania z Minecrafta) ----------

    private static class Slider extends SliderWidget {
        private final String label;
        private final String suffix;
        private final double min;
        private final double max;
        private final DoubleConsumer onChange;

        Slider(int x, int y, int w, int h, String label, String suffix, double min, double max, double cur,
               DoubleConsumer onChange) {
            super(x, y, w, h, Text.empty(), (cur - min) / (max - min));
            this.label = label;
            this.suffix = suffix;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        double getValue() {
            return value;
        }

        private int current() {
            return (int) Math.round(min + (max - min) * value);
        }

        String text() {
            return current() + suffix;
        }

        @Override
        protected void updateMessage() {
            if (label == null) return;
            setMessage(Text.literal(label + ": " + current() + suffix));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }
    }
}
