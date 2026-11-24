package pl.tracer.nametags;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public class ConfigScreen extends Screen {

    private final Screen parent;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Tracer Nametags"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final int w = 150, h = 20, gap = 4;
        int left = width / 2 - w - gap / 2;
        int right = width / 2 + gap / 2;
        int y = 36;
        int row = h + gap;

        addDrawableChild(new Slider(left, y, w, h, "Grubość tracera", 1, 6, TracerConfig.tracerThickness,
                v -> TracerConfig.tracerThickness = (int) v));
        addDrawableChild(new Slider(right, y, w, h, "Zasięg tracerów", 10, 256, TracerConfig.tracerRange,
                v -> TracerConfig.tracerRange = (int) v));
        y += row;
        addDrawableChild(new Slider(left, y, w, h, "Zasięg napisów", 10, 256, TracerConfig.nametagRange,
                v -> TracerConfig.nametagRange = (int) v));
        addDrawableChild(ButtonWidget.builder(colorLabel(), b -> {
            TracerConfig.tracerColor = (TracerConfig.tracerColor + 1) % TracerConfig.COLORS.length;
            b.setMessage(colorLabel());
        }).dimensions(right, y, w, h).build());
        y += row + 6;

        addDrawableChild(toggle(left, y, w, h, "Linie tracerów", () -> TracerConfig.showTracers, v -> TracerConfig.showTracers = v));
        addDrawableChild(toggle(right, y, w, h, "Napisy nad głową", () -> TracerConfig.showNametags, v -> TracerConfig.showNametags = v));
        y += row;
        addDrawableChild(toggle(left, y, w, h, "Nick", () -> TracerConfig.showNick, v -> TracerConfig.showNick = v));
        addDrawableChild(toggle(right, y, w, h, "Serca", () -> TracerConfig.showHearts, v -> TracerConfig.showHearts = v));
        y += row;
        addDrawableChild(toggle(left, y, w, h, "Itemy w rękach", () -> TracerConfig.showHeld, v -> TracerConfig.showHeld = v));
        addDrawableChild(toggle(right, y, w, h, "Zbroja (set)", () -> TracerConfig.showArmor, v -> TracerConfig.showArmor = v));
        y += row;
        addDrawableChild(toggle(left, y, w, h, "Enchanty", () -> TracerConfig.showEnchants, v -> TracerConfig.showEnchants = v));
        addDrawableChild(toggle(right, y, w, h, "Wytrzymałość", () -> TracerConfig.showDurability, v -> TracerConfig.showDurability = v));
        y += row + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal("Gotowe"), b -> close())
                .dimensions(width / 2 - 75, y, 150, h).build());
    }

    private static Text colorLabel() {
        return Text.literal("Kolor: " + TracerConfig.COLOR_NAMES[TracerConfig.tracerColor]);
    }

    private static Text onOff(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "TAK" : "NIE"));
    }

    private static ButtonWidget toggle(int x, int y, int w, int h, String name,
                                       BooleanSupplier get, Consumer<Boolean> set) {
        return ButtonWidget.builder(onOff(name, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(onOff(name, get.getAsBoolean()));
        }).dimensions(x, y, w, h).build();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawText(textRenderer, title, width / 2 - textRenderer.getWidth(title) / 2, 16, 0xFFFFFFFF, true);
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
        private final double min;
        private final double max;
        private final DoubleConsumer onChange;

        Slider(int x, int y, int w, int h, String label, double min, double max, double cur, DoubleConsumer onChange) {
            super(x, y, w, h, Text.empty(), (cur - min) / (max - min));
            this.label = label;
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
            setMessage(Text.literal(label + ": " + current()));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }
    }
}
