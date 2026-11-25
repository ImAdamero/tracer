package pl.tracer.nametags;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public class ConfigScreen extends Screen {

    private static final int ACCENT = 0xFF00AEFF;
    private static final int W = 160, H = 20, GAP = 4, PAD = 12;

    private final Screen parent;
    private int panelX, panelY, panelW, panelH;
    private int labelTracery, labelNapisy, labelFreecam;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Tracer Nametags"));
        this.parent = parent;
    }

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
        addDrawableChild(new Slider(left, y, W, H, "Grubość", "", 1, 6, TracerConfig.tracerThickness,
                v -> TracerConfig.tracerThickness = (int) v));
        addDrawableChild(new Slider(right, y, W, H, "Zasięg", " bl.", 10, 256, TracerConfig.tracerRange,
                v -> TracerConfig.tracerRange = (int) v));
        y += row;
        addDrawableChild(ButtonWidget.builder(colorLabel(), b -> {
            TracerConfig.tracerColor = (TracerConfig.tracerColor + 1) % TracerConfig.COLORS.length;
            b.setMessage(colorLabel());
        }).dimensions(left, y, W, H).build());
        addDrawableChild(toggle(right, y, "Linie", () -> TracerConfig.showTracers, v -> TracerConfig.showTracers = v));
        y += row + 6;

        labelNapisy = y;
        y += 12;
        addDrawableChild(new Slider(left, y, W, H, "Zasięg napisów", " bl.", 10, 256, TracerConfig.nametagRange,
                v -> TracerConfig.nametagRange = (int) v));
        addDrawableChild(new Slider(right, y, W, H, "Rozmiar", "%", 30, 120, TracerConfig.tagScale,
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
        addDrawableChild(new Slider(left, y, W, H, "Prędkość", " bl/s", 1, 100, TracerConfig.freecamSpeed,
                v -> TracerConfig.freecamSpeed = (int) v));
        addDrawableChild(ButtonWidget.builder(freecamLabel(), b -> {
            if (client != null) Freecam.toggle(client);
            b.setMessage(freecamLabel());
        }).dimensions(right, y, W, H).build());
    }

    private static Text colorLabel() {
        return Text.literal("Kolor: ").append(Text.literal(TracerConfig.COLOR_NAMES[TracerConfig.tracerColor])
                .styled(s -> s.withColor(TracerConfig.COLORS[TracerConfig.tracerColor] & 0xFFFFFF)));
    }

    private static Text freecamLabel() {
        return onOff("Freecam", Freecam.isActive());
    }

    private static Text onOff(String name, boolean on) {
        return Text.literal(name + ": ").append(
                Text.literal(on ? "TAK" : "NIE").formatted(on ? Formatting.GREEN : Formatting.RED));
    }

    private ButtonWidget toggle(int x, int y, String name, BooleanSupplier get, Consumer<Boolean> set) {
        return ButtonWidget.builder(onOff(name, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(onOff(name, get.getAsBoolean()));
        }).dimensions(x, y, W, H).build();
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        // panel
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF000000);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0141418);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 2, ACCENT);
        // belki sekcji
        section(ctx, "TRACERY", labelTracery);
        section(ctx, "NAPISY", labelNapisy);
        section(ctx, "FREECAM", labelFreecam);
    }

    private void section(DrawContext ctx, String name, int y) {
        int x = panelX + PAD;
        ctx.drawText(textRenderer, name, x, y, ACCENT, true);
        int tw = textRenderer.getWidth(name);
        ctx.fill(x + tw + 6, y + 4, panelX + panelW - PAD, y + 5, 0x55FFFFFF);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawText(textRenderer, title, panelX + PAD, panelY + 8, 0xFFFFFFFF, true);
        String hint = "R tracery  |  V freecam  |  Prawy Shift menu";
        ctx.drawText(textRenderer, hint, panelX + PAD, panelY + 20, 0xFF8A8A95, false);
    }

    @Override
    public void removed() {
        TracerConfig.save();
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

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

        private int current() {
            return (int) Math.round(min + (max - min) * value);
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
