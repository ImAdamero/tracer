package pl.tracer.nametags;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntSupplier;

public class ConfigScreen extends Screen {

    private static final int ACCENT = 0xFF00AEFF;
    private static final int WIDGET = 0xFF22222A;
    private static final int WIDGET_HOVER = 0xFF30303A;
    private static final int MUTED = 0xFF8A8A95;
    private static final int W = 160, H = 20, GAP = 4, PAD = 12;

    // animacje (wspólne dla widgetów)
    private static float fade = 1f;
    private static float frameDt = 0.016f;

    private final Screen parent;
    private final long openAt = System.nanoTime();
    private long lastNs = openAt;
    private int panelX, panelY, panelW, panelH;
    private int labelTracery, labelNapisy, labelFreecam;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Tracer Nametags"));
        this.parent = parent;
    }

    // ---------- pomocnicze ----------

    /** Mnoży alfę koloru przez aktualny współczynnik wjazdu okna. */
    private static int a(int argb) {
        int al = Math.max(4, (int) (((argb >>> 24) & 0xFF) * fade));
        return (al << 24) | (argb & 0xFFFFFF);
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
        panelW = W * 2 + GAP + PAD * 2;
        panelH = 262;
        panelX = width / 2 - panelW / 2;
        panelY = Math.max(6, (height - panelH) / 2);

        int left = panelX + PAD;
        int right = left + W + GAP;
        int row = H + GAP;
        int y = panelY + 34;

        labelTracery = y;
        y += 12;
        addDrawableChild(new FlatSlider(left, y, W, H, "Grubość", " px", 1, 6, TracerConfig.tracerThickness,
                v -> TracerConfig.tracerThickness = (int) v));
        addDrawableChild(new FlatSlider(right, y, W, H, "Zasięg", " ch.", 1, 256, TracerConfig.tracerRange,
                v -> TracerConfig.tracerRange = (int) v));
        y += row;
        addDrawableChild(new FlatButton(left, y, W, H, "Kolor", false, null,
                () -> TracerConfig.COLORS[TracerConfig.tracerColor],
                b -> TracerConfig.tracerColor = (TracerConfig.tracerColor + 1) % TracerConfig.COLORS.length));
        addDrawableChild(toggle(right, y, "Linie", () -> TracerConfig.showTracers, v -> TracerConfig.showTracers = v));
        y += row + 6;

        labelNapisy = y;
        y += 12;
        addDrawableChild(new FlatSlider(left, y, W, H, "Zasięg napisów", " ch.", 1, 256, TracerConfig.nametagRange,
                v -> TracerConfig.nametagRange = (int) v));
        addDrawableChild(new FlatSlider(right, y, W, H, "Rozmiar", "%", 30, 120, TracerConfig.tagScale,
                v -> TracerConfig.tagScale = (int) v));
        y += row;
        addDrawableChild(toggle(left, y, "Napisy", () -> TracerConfig.showNametags, v -> TracerConfig.showNametags = v));
        addDrawableChild(toggle(right, y, "Nick", () -> TracerConfig.showNick, v -> TracerConfig.showNick = v));
        y += row;
        addDrawableChild(toggle(left, y, "Serca", () -> TracerConfig.showHearts, v -> TracerConfig.showHearts = v));
        addDrawableChild(toggle(right, y, "Ręce", () -> TracerConfig.showHeld, v -> TracerConfig.showHeld = v));
        y += row;
        addDrawableChild(toggle(left, y, "Zbroja", () -> TracerConfig.showArmor, v -> TracerConfig.showArmor = v));
        addDrawableChild(toggle(right, y, "Enchanty", () -> TracerConfig.showEnchants, v -> TracerConfig.showEnchants = v));
        y += row;
        addDrawableChild(toggle(left, y, "Wytrzymałość", () -> TracerConfig.showDurability, v -> TracerConfig.showDurability = v));
        y += row + 6;

        labelFreecam = y;
        y += 12;
        addDrawableChild(new FlatSlider(left, y, W, H, "Prędkość", " bl/s", 1, 100, TracerConfig.freecamSpeed,
                v -> TracerConfig.freecamSpeed = (int) v));
        addDrawableChild(new FlatButton(right, y, W, H, "Freecam", true, Freecam::isActive, null,
                b -> {
                    if (client != null) Freecam.toggle(client);
                }));
    }

    private static FlatButton toggle(int x, int y, String name, BooleanSupplier get, Consumer<Boolean> set) {
        return new FlatButton(x, y, W, H, name, true, get, null, b -> set.accept(!get.getAsBoolean()));
    }

    // ---------- rysowanie ----------

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        // panel + obramowanie
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

        // wjazd okna: zanikanie + przesunięcie z dołu (ease-out)
        float t = Math.min(1f, (now - openAt) / 260_000_000f);
        float e = 1f - (1f - t) * (1f - t) * (1f - t);
        fade = 0.15f + 0.85f * e;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(0f, (1f - e) * 14f);
        super.render(ctx, mouseX, mouseY, delta);
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

    // ---------- widgety ----------

    private static class FlatButton extends ButtonWidget {
        private final String name;
        private final boolean isToggle;
        private final BooleanSupplier on;
        private final IntSupplier swatch;
        private float hover = 0f;
        private float onT;

        FlatButton(int x, int y, int w, int h, String name, boolean isToggle, BooleanSupplier on,
                   IntSupplier swatch, ButtonWidget.PressAction press) {
            super(x, y, w, h, Text.literal(name), press, DEFAULT_NARRATION_SUPPLIER);
            this.name = name;
            this.isToggle = isToggle;
            this.on = on;
            this.swatch = swatch;
            this.onT = (on != null && on.getAsBoolean()) ? 1f : 0f;
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            hover = approach(hover, isHovered() ? 1f : 0f, 14f);
            onT = approach(onT, (on != null && on.getAsBoolean()) ? 1f : 0f, 14f);

            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            ctx.fill(x, y, x + w, y + h, a(lerp(WIDGET, WIDGET_HOVER, hover)));

            float glow = Math.max(hover * 0.6f, onT);
            int edge = Math.round(2f * glow);
            if (edge > 0) ctx.fill(x, y, x + edge, y + h, a(ACCENT));

            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            ctx.drawText(tr, name, x + 8, y + (h - 8) / 2, a(lerp(0xFFB4B4C0, 0xFFFFFFFF, Math.max(hover, onT))), false);

            if (isToggle) {
                int tw = 18, th = 8;
                int tx = x + w - 8 - tw;
                int ty = y + (h - th) / 2;
                ctx.fill(tx, ty, tx + tw, ty + th, a(lerp(0xFF3C3C48, ACCENT, onT)));
                int kx = tx + 1 + Math.round((tw - th) * onT);
                ctx.fill(kx, ty + 1, kx + th - 2, ty + th - 1, a(0xFFFFFFFF));
            } else if (swatch != null) {
                int sx = x + w - 8 - 12;
                ctx.fill(sx - 1, y + 4, sx + 13, y + h - 4, a(0xFF000000));
                ctx.fill(sx, y + 5, sx + 12, y + h - 5, a(swatch.getAsInt()));
            }
        }
    }

    private static class FlatSlider extends SliderWidget {
        private final String label;
        private final String unit;
        private final double min;
        private final double max;
        private final DoubleConsumer onChange;
        private float shown;
        private float hover = 0f;

        FlatSlider(int x, int y, int w, int h, String label, String unit, double min, double max,
                   double cur, DoubleConsumer onChange) {
            super(x, y, w, h, Text.empty(), (cur - min) / (max - min));
            this.label = label;
            this.unit = unit;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            this.shown = (float) value;
            updateMessage();
        }

        private int current() {
            return (int) Math.round(min + (max - min) * value);
        }

        @Override
        protected void updateMessage() {
            if (label == null) return;
            setMessage(Text.literal(label + ": " + current() + unit));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            hover = approach(hover, isHovered() ? 1f : 0f, 14f);
            shown = approach(shown, (float) value, 18f);

            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            ctx.fill(x, y, x + w, y + h, a(lerp(WIDGET, WIDGET_HOVER, hover)));
            int fw = Math.round(w * shown);
            ctx.fill(x, y, x + fw, y + h, a(lerp(0xFF0B5C85, ACCENT, 0.35f + 0.65f * hover)));
            ctx.fill(Math.max(x, x + fw - 1), y, Math.min(x + w, x + fw + 1), y + h, a(0xFFFFFFFF));

            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            String val = current() + unit;
            ctx.drawText(tr, label, x + 6, y + (h - 8) / 2, a(0xFFFFFFFF), true);
            ctx.drawText(tr, val, x + w - 6 - tr.getWidth(val), y + (h - 8) / 2, a(0xFFFFFFFF), true);
        }
    }
}
