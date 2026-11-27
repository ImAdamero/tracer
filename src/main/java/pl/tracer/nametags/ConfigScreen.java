package pl.tracer.nametags;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntSupplier;

/**
 * Menu z zakładkami i animacjami. Kliknięcia i przeciąganie obsługują zwykłe, niewidoczne
 * widgety Minecrafta (alfa 0), a cały wygląd rysujemy sami.
 */
public class ConfigScreen extends Screen {

    private static final int ACCENT = 0xFF00AEFF;
    private static final int ACCENT2 = 0xFF8A5CFF;
    private static final int WIDGET = 0xFF22222C;
    private static final int WIDGET_HOVER = 0xFF2E2E3A;
    private static final int MUTED = 0xFF8A8A98;

    private static final int SIDE_W = 92, PAD = 10, W = 150, H = 20, GAP = 4, ROW = H + GAP;
    private static final String[] TAB_NAMES = {"Tracery", "Napisy", "Tracker", "Freecam", "Ekwipunek"};
    private static final String[] TAB_HINTS = {
            "Linie od celownika do graczy. Zasięg w chunkach.",
            "Napis nad głową: nick, serca, itemy, enchanty.",
            "Zapamiętuje ostatnią pozycję graczy poza zasięgiem.",
            "Klawisz V. Sprint = szybciej. Postać stoi w miejscu.",
            "Anty-kostka cofa mieszanie. Lewy Shift = podgląd shulkera."
    };

    // animacje wspólne dla widgetów
    private static float fade = 1f;
    private static float frameDt = 0.016f;

    private final Screen parent;
    private final long openAt = System.nanoTime();
    private long lastNs = openAt;
    private long tabAt = openAt;
    private int tab = 0;
    private float indY;
    private int panelX, panelY, panelW, panelH, contentX;
    private final int[] tabCounts = new int[TAB_NAMES.length];
    private final List<Vis> vis = new ArrayList<>();
    private final List<TabVis> tabs = new ArrayList<>();

    // cząsteczki w tle
    private static final int PARTS = 42;
    private final float[] px = new float[PARTS], py = new float[PARTS], pv = new float[PARTS],
            pz = new float[PARTS], pp = new float[PARTS];

    public ConfigScreen(Screen parent) {
        super(Text.literal("Tracer Nametags"));
        this.parent = parent;
        Random r = new Random(7);
        for (int i = 0; i < PARTS; i++) {
            px[i] = r.nextFloat();
            py[i] = r.nextFloat();
            pv[i] = 0.015f + r.nextFloat() * 0.05f;
            pz[i] = 1 + r.nextInt(2);
            pp[i] = r.nextFloat() * 6.28f;
        }
    }

    // ---------- kolory / animacje ----------

    private static int a(int argb) {
        int al = Math.max(4, (int) (((argb >>> 24) & 0xFF) * fade));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    /** Kolor widgetu: mieszany z tłem panelu zależnie od aktualnego fade. */
    private static int c(int argb) {
        return lerp(0xFF14141A, 0xFF000000 | (argb & 0xFFFFFF), fade) | 0xFF000000;
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

    private static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    // ---------- układ ----------

    @Override
    protected void init() {
        vis.clear();
        tabs.clear();
        java.util.Arrays.fill(tabCounts, 0);

        panelW = SIDE_W + PAD + (W * 2 + GAP) + PAD;
        panelH = 200;
        panelX = width / 2 - panelW / 2;
        panelY = Math.max(6, (height - panelH) / 2);
        contentX = panelX + SIDE_W + PAD;

        // zakładki w pasku bocznym
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int idx = i;
            ButtonWidget b = ButtonWidget.builder(Text.empty(), btn -> selectTab(idx))
                    .dimensions(panelX + 6, panelY + 44 + i * 24, SIDE_W - 12, 20).build();
            b.setAlpha(0f);
            addDrawableChild(b);
            tabs.add(new TabVis(panelX + 6, panelY + 44 + i * 24, SIDE_W - 12, 20, TAB_NAMES[i], i));
        }
        indY = panelY + 44 + tab * 24;

        int left = contentX;
        int right = contentX + W + GAP;
        int y = panelY + 38;

        // --- 0: Tracery ---
        addSlider(0, left, y, "Grubość", " px", 1, 6, TracerConfig.tracerThickness, v -> TracerConfig.tracerThickness = (int) v);
        addSlider(0, right, y, "Zasięg", " ch.", 1, 256, TracerConfig.tracerRange, v -> TracerConfig.tracerRange = (int) v);
        addColorButton(0, left, y + ROW);
        addToggle(0, right, y + ROW, "Linie", () -> TracerConfig.showTracers, v -> TracerConfig.showTracers = v);

        // --- 1: Napisy ---
        addSlider(1, left, y, "Zasięg napisów", " ch.", 1, 256, TracerConfig.nametagRange, v -> TracerConfig.nametagRange = (int) v);
        addSlider(1, right, y, "Rozmiar", "%", 30, 120, TracerConfig.tagScale, v -> TracerConfig.tagScale = (int) v);
        addToggle(1, left, y + ROW, "Napisy", () -> TracerConfig.showNametags, v -> TracerConfig.showNametags = v);
        addToggle(1, right, y + ROW, "Nick", () -> TracerConfig.showNick, v -> TracerConfig.showNick = v);
        addToggle(1, left, y + ROW * 2, "Serca", () -> TracerConfig.showHearts, v -> TracerConfig.showHearts = v);
        addToggle(1, right, y + ROW * 2, "Ręce", () -> TracerConfig.showHeld, v -> TracerConfig.showHeld = v);
        addToggle(1, left, y + ROW * 3, "Zbroja", () -> TracerConfig.showArmor, v -> TracerConfig.showArmor = v);
        addToggle(1, right, y + ROW * 3, "Enchanty", () -> TracerConfig.showEnchants, v -> TracerConfig.showEnchants = v);
        addToggle(1, left, y + ROW * 4, "Wytrzymałość", () -> TracerConfig.showDurability, v -> TracerConfig.showDurability = v);

        // --- 2: Tracker ---
        addSlider(2, left, y, "Pamięć pozycji", " s", 0, 600, TracerConfig.memorySeconds, v -> TracerConfig.memorySeconds = (int) v);
        addToggle(2, right, y, "Lista graczy", () -> TracerConfig.showList, v -> TracerConfig.showList = v);
        addToggle(2, left, y + ROW, "Koordy", () -> TracerConfig.showCoords, v -> TracerConfig.showCoords = v);

        // --- 3: Freecam ---
        addSlider(3, left, y, "Prędkość", " bl/s", 1, 100, TracerConfig.freecamSpeed, v -> TracerConfig.freecamSpeed = (int) v);
        ButtonWidget fc = ButtonWidget.builder(Text.empty(), b -> {
            if (client != null) Freecam.toggle(client);
        }).dimensions(right, y, W, H).build();
        fc.setAlpha(0f);
        addDrawableChild(fc);
        addVis(new Vis(3, right, y, W, H, "Freecam", true, Freecam::isActive, null, null, fc));

        // --- 4: Ekwipunek ---
        addToggle(4, left, y, "Anty-kostka", () -> TracerConfig.antiShuffle, v -> TracerConfig.antiShuffle = v);
        addToggle(4, right, y, "Podgląd shulkera", () -> TracerConfig.shulkerPreview, v -> TracerConfig.shulkerPreview = v);

        applyTab();
    }

    private void addVis(Vis v) {
        v.order = tabCounts[v.tab]++;
        vis.add(v);
    }

    private void selectTab(int i) {
        if (i == tab) return;
        tab = i;
        tabAt = System.nanoTime();
        applyTab();
    }

    private void applyTab() {
        for (Vis v : vis) v.widget.visible = (v.tab == tab);
    }

    private void addSlider(int tabIdx, int x, int y, String label, String unit, double min, double max,
                           double cur, DoubleConsumer cb) {
        Slider s = new Slider(x, y, W, H, min, max, cur, cb);
        s.setAlpha(0f);
        addDrawableChild(s);
        addVis(new Vis(tabIdx, x, y, W, H, label, false, null, null, s, s));
        vis.get(vis.size() - 1).unit = unit;
    }

    private void addToggle(int tabIdx, int x, int y, String name, BooleanSupplier get, Consumer<Boolean> set) {
        ButtonWidget b = ButtonWidget.builder(Text.empty(), btn -> set.accept(!get.getAsBoolean()))
                .dimensions(x, y, W, H).build();
        b.setAlpha(0f);
        addDrawableChild(b);
        addVis(new Vis(tabIdx, x, y, W, H, name, true, get, null, null, b));
    }

    private void addColorButton(int tabIdx, int x, int y) {
        ButtonWidget b = ButtonWidget.builder(Text.empty(), btn ->
                TracerConfig.tracerColor = (TracerConfig.tracerColor + 1) % TracerConfig.COLORS.length)
                .dimensions(x, y, W, H).build();
        b.setAlpha(0f);
        addDrawableChild(b);
        addVis(new Vis(tabIdx, x, y, W, H, "Kolor", false, null, () -> TracerConfig.COLORS[TracerConfig.tracerColor], null, b));
    }

    // ---------- rysowanie ----------

    private float time() {
        return (System.nanoTime() - openAt) / 1_000_000_000f;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        float t = time();

        // poświata obramowania (pulsuje)
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.0f);
        int glow = ((int) (0x30 + 0x40 * pulse) << 24) | 0x00AEFF;
        ctx.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, a(glow));
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, a(0xFF000000));

        // tło: pionowy gradient
        for (int yy = 0; yy < panelH; yy += 2) {
            int col = lerp(0xFF1B1B25, 0xFF0F0F14, yy / (float) panelH);
            ctx.fill(panelX, panelY + yy, panelX + panelW, Math.min(panelY + yy + 2, panelY + panelH), a(col));
        }
        // pasek boczny ciemniejszy
        ctx.fill(panelX, panelY, panelX + SIDE_W, panelY + panelH, a(0x55000000));
        ctx.fill(panelX + SIDE_W, panelY, panelX + SIDE_W + 1, panelY + panelH, a(0x22FFFFFF));

        // cząsteczki unoszące się w górę
        for (int i = 0; i < PARTS; i++) {
            py[i] -= pv[i] * frameDt;
            if (py[i] < 0f) {
                py[i] += 1f;
            }
            float xf = px[i] + 0.012f * (float) Math.sin(t * 0.8f + pp[i]);
            int x = panelX + 2 + (int) (xf * (panelW - 6));
            int y = panelY + 3 + (int) (py[i] * (panelH - 8));
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 1.6f + pp[i] * 2f);
            int col = lerp(ACCENT, ACCENT2, xf);
            int al = (int) (0x22 + 0x44 * tw);
            int sz = (int) pz[i];
            ctx.fill(x, y, x + sz, y + sz, a((al << 24) | (col & 0xFFFFFF)));
        }

        // górny pasek z przesuwającym się błyskiem
        float open = Math.min(1f, (System.nanoTime() - openAt) / 350_000_000f);
        int barW = (int) (panelW * easeOut(open));
        int barX = panelX + (panelW - barW) / 2;
        for (int xx = 0; xx < barW; xx += 4) {
            float k = (xx + (barX - panelX)) / (float) panelW;
            ctx.fill(barX + xx, panelY, Math.min(barX + xx + 4, barX + barW), panelY + 2, a(lerp(ACCENT, ACCENT2, k)));
        }
        int sweep = (int) ((t * 140f) % (panelW + 60)) - 30;
        int sx1 = Math.max(panelX, panelX + sweep);
        int sx2 = Math.min(panelX + panelW, panelX + sweep + 30);
        if (sx2 > sx1) ctx.fill(sx1, panelY, sx2, panelY + 2, a(0xCCFFFFFF));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        frameDt = Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;

        float t = Math.min(1f, (now - openAt) / 280_000_000f);
        float e = easeOut(t);
        fade = 0.15f + 0.85f * e;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(0f, (1f - e) * 16f);
        super.render(ctx, mouseX, mouseY, delta); // niewidoczne widgety (kliknięcia)

        // --- tytuł z falą kolorów ---
        waveText(ctx, "TRACER", panelX + 12, panelY + 10);
        waveText(ctx, "NAMETAGS", panelX + 12, panelY + 21);

        // --- zakładki ---
        float ty = panelY + 44 + tab * 24;
        indY = approach(indY, ty, 16f);
        ctx.fill(panelX, Math.round(indY) + 2, panelX + 3, Math.round(indY) + 18, a(ACCENT));
        for (TabVis tv : tabs) tv.draw(ctx, textRenderer, mouseX, mouseY - Math.round((1f - e) * 16f), tab == tv.index);

        // --- nagłówek zakładki + linia ---
        float te = easeOut((now - tabAt) / 260_000_000f);
        int hx = contentX + Math.round((1f - te) * 14f);
        ctx.drawText(textRenderer, TAB_NAMES[tab].toUpperCase(), hx, panelY + 14,
                a(lerp(0xFF000000 | (ACCENT & 0xFFFFFF), ACCENT, 1f)), true);
        int lineW = Math.round((W * 2 + GAP) * te);
        for (int xx = 0; xx < lineW; xx += 4) {
            ctx.fill(contentX + xx, panelY + 27, Math.min(contentX + xx + 4, contentX + lineW), panelY + 28,
                    a(lerp(ACCENT, ACCENT2, xx / (float) (W * 2 + GAP))));
        }

        // --- widgety aktywnej zakładki (wjeżdżają po kolei) ---
        for (Vis v : vis) {
            if (v.tab != tab) continue;
            float local = ((now - tabAt) / 1_000_000f - v.order * 45f) / 230f;
            float ee = easeOut(local);
            float saved = fade;
            fade = saved * ee;
            v.draw(ctx, textRenderer, mouseX, mouseY - Math.round((1f - e) * 16f), Math.round((1f - ee) * 20f), ee);
            fade = saved;
        }

        // --- podpowiedzi na dole ---
        ctx.drawText(textRenderer, TAB_HINTS[tab], contentX + Math.round((1f - te) * 10f), panelY + panelH - 28,
                a(lerp(0x00000000 | (MUTED & 0xFFFFFF), MUTED, te)), false);
        ctx.drawText(textRenderer, "R tracery  |  V freecam  |  Prawy Shift menu", contentX,
                panelY + panelH - 15, a(0xFF5C5C6A), false);
        ctx.getMatrices().popMatrix();
    }

    private void waveText(DrawContext ctx, String s, int x, int y) {
        float t = time();
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            float k = 0.5f + 0.5f * (float) Math.sin(t * 3.2f - i * 0.55f);
            ctx.drawText(textRenderer, ch, cx, y + Math.round(1.2f * (float) Math.sin(t * 3.2f - i * 0.55f)),
                    a(lerp(0xFFFFFFFF, ACCENT, k)), true);
            cx += textRenderer.getWidth(ch);
        }
    }

    @Override
    public void removed() {
        TracerConfig.save();
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    // ---------- zakładka w pasku bocznym ----------

    private static class TabVis {
        final int x, y, w, h, index;
        final String name;
        float hover, act;

        TabVis(int x, int y, int w, int h, String name, int index) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.name = name;
            this.index = index;
        }

        void draw(DrawContext ctx, TextRenderer tr, int mx, int my, boolean active) {
            boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
            hover = approach(hover, hov ? 1f : 0f, 14f);
            act = approach(act, active ? 1f : 0f, 12f);
            float k = Math.max(hover * 0.6f, act);
            int bg = ((int) (0x55 * k) << 24) | 0x00AEFF;
            ctx.fill(x, y, x + w, y + h, a(bg));
            int off = Math.round(3f * k);
            ctx.drawText(tr, name, x + 8 + off, y + (h - 8) / 2,
                    a(lerp(0xFFA0A0AE, 0xFFFFFFFF, k)), act > 0.5f);
        }
    }

    // ---------- widget w treści ----------

    private static class Vis {
        final int tab, x, y, w, h;
        final String name;
        final boolean toggle;
        final BooleanSupplier on;
        final IntSupplier swatch;
        final Slider slider;
        final ClickableWidget widget;
        String unit = "";
        int order;
        float hover = 0f;
        float onT;
        float shown;
        float pulse = 0f;
        boolean lastOn;

        Vis(int tab, int x, int y, int w, int h, String name, boolean toggle, BooleanSupplier on,
            IntSupplier swatch, Slider slider, ClickableWidget widget) {
            this.tab = tab;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.name = name;
            this.toggle = toggle;
            this.on = on;
            this.swatch = swatch;
            this.slider = slider;
            this.widget = widget;
            this.lastOn = on != null && on.getAsBoolean();
            this.onT = lastOn ? 1f : 0f;
            this.shown = slider != null ? (float) slider.getValue() : 0f;
        }

        void draw(DrawContext ctx, TextRenderer tr, int mx, int my, int dx, float appear) {
            boolean hov = appear > 0.9f && mx >= x && mx < x + w && my >= y && my < y + h;
            hover = approach(hover, hov ? 1f : 0f, 14f);

            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate((float) dx, 0f);

            if (slider != null) {
                shown = approach(shown, (float) slider.getValue(), 18f);
                ctx.fill(x, y, x + w, y + h, c(lerp(WIDGET, WIDGET_HOVER, hover)));
                int fw = Math.round(w * shown);
                for (int xx = 0; xx < fw; xx += 3) {
                    float k = xx / (float) w;
                    int col = lerp(0xFF0A6A9A, lerp(ACCENT, ACCENT2, k), 0.4f + 0.6f * hover);
                    ctx.fill(x + xx, y, x + Math.min(xx + 3, fw), y + h, c(col));
                }
                // jaśniejszy górny pasek i uchwyt
                ctx.fill(x, y, x + fw, y + 1, c(0x55FFFFFF));
                int hx = Math.max(x, Math.min(x + w - 2, x + fw - 1));
                ctx.fill(hx, y - 1, hx + 2, y + h + 1, c(0xFFFFFFFF));
                String val = slider.text(unit);
                ctx.drawText(tr, name, x + 6, y + (h - 8) / 2, c(0xFFFFFFFF), true);
                ctx.drawText(tr, val, x + w - 6 - tr.getWidth(val), y + (h - 8) / 2, c(0xFFFFFFFF), true);
                ctx.getMatrices().popMatrix();
                return;
            }

            boolean nowOn = on != null && on.getAsBoolean();
            if (nowOn != lastOn) {
                pulse = 1f; // błysk po przełączeniu
                lastOn = nowOn;
            }
            onT = approach(onT, nowOn ? 1f : 0f, 14f);
            pulse = approach(pulse, 0f, 4.5f);

            ctx.fill(x, y, x + w, y + h, c(lerp(WIDGET, WIDGET_HOVER, hover)));

            // delikatny gradient, gdy włączone
            if (onT > 0.02f) {
                int wOn = Math.round(w * 0.55f * onT);
                for (int xx = 0; xx < wOn; xx += 3) {
                    float k = 1f - xx / (float) Math.max(1, wOn);
                    int al = (int) (0x40 * k * onT);
                    ctx.fill(x + xx, y, x + Math.min(xx + 3, wOn), y + h, c(lerp(WIDGET, ACCENT, al / 255f * 3f)));
                }
                // błysk przesuwający się po włączonych
                float tt = (System.nanoTime() / 1_000_000_000f) * 70f;
                int sx = x + (int) (tt % (w + 40)) - 20;
                int a1 = Math.max(x, sx), a2 = Math.min(x + w, sx + 10);
                if (a2 > a1) ctx.fill(a1, y, a2, y + h, c(lerp(WIDGET, 0xFFFFFFFF, 0.10f * onT)));
            }

            // fala po przełączeniu: rozszerza się ze środka
            if (pulse > 0.02f) {
                int hw = Math.round((1f - pulse) * w / 2f);
                int cx = x + w / 2;
                ctx.fill(cx - hw, y, cx + hw, y + h, c(lerp(WIDGET, ACCENT, 0.55f * pulse)));
            }

            int edge = Math.round(2f * Math.max(hover * 0.6f, onT));
            if (edge > 0) ctx.fill(x, y, x + edge, y + h, c(ACCENT));
            int tcol = lerp(0xFFB4B4C0, 0xFFFFFFFF, Math.max(hover, onT));
            ctx.drawText(tr, name, x + 8 + Math.round(hover * 2f), y + (h - 8) / 2, c(tcol), false);

            if (toggle) {
                int tw = 20, th = 10;
                int tx = x + w - 8 - tw;
                int ty = y + (h - th) / 2;
                ctx.fill(tx, ty, tx + tw, ty + th, c(lerp(0xFF3C3C4A, ACCENT, onT)));
                int kx = tx + 1 + Math.round((tw - th) * onT);
                ctx.fill(kx, ty + 1, kx + th - 2, ty + th - 1, c(0xFFFFFFFF));
            } else if (swatch != null) {
                int sx = x + w - 8 - 12;
                ctx.fill(sx - 1, y + 4, sx + 13, y + h - 4, c(0xFF000000));
                ctx.fill(sx, y + 5, sx + 12, y + h - 5, c(swatch.getAsInt()));
            }
            ctx.getMatrices().popMatrix();
        }
    }

    // ---------- suwak (przeciąganie obsługuje Minecraft) ----------

    private static class Slider extends SliderWidget {
        private final double min;
        private final double max;
        private final DoubleConsumer onChange;

        Slider(int x, int y, int w, int h, double min, double max, double cur, DoubleConsumer onChange) {
            super(x, y, w, h, Text.empty(), (cur - min) / (max - min));
            this.min = min;
            this.max = max;
            this.onChange = onChange;
        }

        double getValue() {
            return value;
        }

        int current() {
            return (int) Math.round(min + (max - min) * value);
        }

        String text(String unit) {
            return current() + unit;
        }

        @Override
        protected void updateMessage() {
            // wygląd rysujemy sami
        }

        @Override
        protected void applyValue() {
            if (onChange != null) onChange.accept(current());
        }
    }
}
