package pl.tracer.nametags;

import net.minecraft.client.MinecraftClient;
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

public class ConfigScreen extends Screen {

    private static final int ACCENT = 0xFF2F7BFF;
    private static final int ACCENT_HOVER = 0xFF4A8EFF;
    private static final int PANEL = 0xF0141418;
    private static final int WIDGET = 0xFF26262C;
    private static final int WIDGET_HOVER = 0xFF34343C;
    private static final int MUTED = 0xFF8A8A96;

    private static final int PANEL_W = 330;
    private static final int PAD = 6;
    private static final int GAP = 4;
    private static final int H = 20;

    private final Screen parent;
    private int panelX, panelY, panelH;
    private final List<Object[]> headers = new ArrayList<>(); // {Integer y, String text}

    public ConfigScreen(Screen parent) {
        super(Text.literal("Tracer Nametags"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        headers.clear();
        final int colW = (PANEL_W - PAD * 2 - GAP) / 2;
        final int fullW = PANEL_W - PAD * 2;

        // wysokość panelu: tytuł 24 + 3 sekcje + wiersze + przycisk
        int rows = 2 + 5 + 1;
        int totalH = 24 + 3 * 14 + rows * (H + GAP) + (H + 12) + PAD;
        panelH = totalH;
        panelX = width / 2 - PANEL_W / 2;
        panelY = Math.max(4, height / 2 - totalH / 2);

        int left = panelX + PAD;
        int right = left + colW + GAP;
        int y = panelY + 24;

        // --- Tracery ---
        headers.add(new Object[]{y, "TRACERY"});
        y += 14;
        addDrawableChild(new FlatSlider(left, y, colW, H, "Grubość", " px", 1, 6, TracerConfig.tracerThickness,
                v -> TracerConfig.tracerThickness = (int) v));
        addDrawableChild(new FlatSlider(right, y, colW, H, "Zasięg", " bl.", 10, 256, TracerConfig.tracerRange,
                v -> TracerConfig.tracerRange = (int) v));
        y += H + GAP;
        addDrawableChild(new FlatButton(left, y, colW, H, colorLabel(), b -> {
            TracerConfig.tracerColor = (TracerConfig.tracerColor + 1) % TracerConfig.COLORS.length;
            b.setMessage(colorLabel());
        }, null));
        addDrawableChild(toggle(right, y, colW, "Linie tracerów", () -> TracerConfig.showTracers, v -> TracerConfig.showTracers = v));
        y += H + GAP;

        // --- Napisy ---
        headers.add(new Object[]{y, "NAPISY NAD GŁOWĄ"});
        y += 14;
        addDrawableChild(new FlatSlider(left, y, colW, H, "Zasięg", " bl.", 10, 256, TracerConfig.nametagRange,
                v -> TracerConfig.nametagRange = (int) v));
        addDrawableChild(new FlatSlider(right, y, colW, H, "Rozmiar", "%", 30, 150, TracerConfig.tagScale,
                v -> TracerConfig.tagScale = (int) v));
        y += H + GAP;
        addDrawableChild(toggle(left, y, colW, "Napisy", () -> TracerConfig.showNametags, v -> TracerConfig.showNametags = v));
        addDrawableChild(toggle(right, y, colW, "Nick", () -> TracerConfig.showNick, v -> TracerConfig.showNick = v));
        y += H + GAP;
        addDrawableChild(toggle(left, y, colW, "Serca", () -> TracerConfig.showHearts, v -> TracerConfig.showHearts = v));
        addDrawableChild(toggle(right, y, colW, "Itemy w rękach", () -> TracerConfig.showHeld, v -> TracerConfig.showHeld = v));
        y += H + GAP;
        addDrawableChild(toggle(left, y, colW, "Zbroja (set)", () -> TracerConfig.showArmor, v -> TracerConfig.showArmor = v));
        addDrawableChild(toggle(right, y, colW, "Enchanty", () -> TracerConfig.showEnchants, v -> TracerConfig.showEnchants = v));
        y += H + GAP;
        addDrawableChild(toggle(left, y, colW, "Wytrzymałość", () -> TracerConfig.showDurability, v -> TracerConfig.showDurability = v));
        y += H + GAP;

        // --- Freecam ---
        headers.add(new Object[]{y, "FREECAM  (klawisz G, Ctrl = szybciej)"});
        y += 14;
        addDrawableChild(new FlatSlider(left, y, fullW, H, "Prędkość lotu", " bl/s", 1, 50, TracerConfig.freecamSpeed,
                v -> TracerConfig.freecamSpeed = (int) v));
        y += H + GAP + 8;

        addDrawableChild(new FlatButton(left, y, fullW, H, Text.literal("Gotowe"), b -> close(), () -> true));
    }

    private static Text colorLabel() {
        return Text.literal("Kolor: " + TracerConfig.COLOR_NAMES[TracerConfig.tracerColor]);
    }

    private static FlatButton toggle(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set) {
        return new FlatButton(x, y, w, H, Text.literal(name), b -> set.accept(!get.getAsBoolean()), get);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        // panel z niebieską belką u góry
        ctx.fill(panelX - 1, panelY - 1, panelX + PANEL_W + 1, panelY + panelH + 1, 0xFF000000);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, PANEL);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + 2, ACCENT);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawText(textRenderer, title, panelX + PAD, panelY + 9, 0xFFFFFFFF, true);
        String sub = "Prawy Shift = menu";
        ctx.drawText(textRenderer, sub, panelX + PANEL_W - PAD - textRenderer.getWidth(sub), panelY + 9, MUTED, false);
        for (Object[] h : headers) {
            int hy = (Integer) h[0];
            String txt = (String) h[1];
            ctx.drawText(textRenderer, txt, panelX + PAD, hy + 2, ACCENT, false);
            int lineX = panelX + PAD + textRenderer.getWidth(txt) + 6;
            ctx.fill(lineX, hy + 6, panelX + PANEL_W - PAD, hy + 7, 0xFF2A2A32);
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

    // ---------- własne, płaskie widgety ----------

    private static class FlatButton extends ButtonWidget {
        private final BooleanSupplier active;

        FlatButton(int x, int y, int w, int h, Text msg, ButtonWidget.PressAction onPress, BooleanSupplier active) {
            super(x, y, w, h, msg, onPress, DEFAULT_NARRATION_SUPPLIER);
            this.active = active;
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            boolean hov = isHovered();
            boolean on = active != null && active.getAsBoolean();
            int bg = on ? (hov ? ACCENT_HOVER : ACCENT) : (hov ? WIDGET_HOVER : WIDGET);
            ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            Text t = getMessage();
            ctx.drawText(tr, t, getX() + (getWidth() - tr.getWidth(t)) / 2,
                    getY() + (getHeight() - 8) / 2, on ? 0xFFFFFFFF : 0xFFD0D0D8, false);
        }
    }

    private static class FlatSlider extends SliderWidget {
        private final String label;
        private final String unit;
        private final double min;
        private final double max;
        private final DoubleConsumer onChange;

        FlatSlider(int x, int y, int w, int h, String label, String unit, double min, double max,
                   double cur, DoubleConsumer onChange) {
            super(x, y, w, h, Text.empty(), (cur - min) / (max - min));
            this.label = label;
            this.unit = unit;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
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
            boolean hov = isHovered();
            ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hov ? WIDGET_HOVER : WIDGET);
            int filled = (int) Math.round(getWidth() * value);
            ctx.fill(getX(), getY(), getX() + filled, getY() + getHeight(), hov ? ACCENT_HOVER : ACCENT);
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            Text t = getMessage();
            ctx.drawText(tr, t, getX() + (getWidth() - tr.getWidth(t)) / 2,
                    getY() + (getHeight() - 8) / 2, 0xFFFFFFFF, true);
        }
    }
}
