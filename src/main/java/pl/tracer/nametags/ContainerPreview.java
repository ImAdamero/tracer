package pl.tracer.nametags;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.lwjgl.glfw.GLFW;

/** Podgląd zawartości shulkera (siatka 9x3) po przytrzymaniu lewego Shifta nad nim w ekwipunku. */
public final class ContainerPreview {
    private static final int COLS = 9, ROWS = 3, CELL = 18, PAD = 6, TITLE_H = 14;

    private ContainerPreview() {}

    /** Zwraca true, gdy narysował podgląd (wtedy zwykły tooltip jest pomijany). */
    public static boolean render(DrawContext ctx, ItemStack stack, int mx, int my) {
        if (stack == null || stack.isEmpty()) return false;
        ContainerComponent comp = stack.get(DataComponentTypes.CONTAINER);
        if (comp == null || !comp.iterateNonEmpty().iterator().hasNext()) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (!InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)) return false;

        DefaultedList<ItemStack> items = DefaultedList.ofSize(COLS * ROWS, ItemStack.EMPTY);
        try {
            comp.copyTo(items);
        } catch (RuntimeException e) {
            return false; // kontener o innym rozmiarze niż shulker
        }

        TextRenderer tr = mc.textRenderer;
        int boxW = COLS * CELL + PAD * 2;
        int boxH = TITLE_H + ROWS * CELL + PAD * 2;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        int x = mx + 12;
        int y = my + 12;
        if (x + boxW > sw - 2) x = mx - boxW - 12;
        if (x < 2) x = 2;
        if (y + boxH > sh - 2) y = sh - boxH - 2;
        if (y < 2) y = 2;

        // tło i obramowanie w stylu tooltipa
        ctx.fill(x - 1, y - 1, x + boxW + 1, y + boxH + 1, 0xF0300060);
        ctx.fill(x, y, x + boxW, y + boxH, 0xF0100010);
        ctx.drawText(tr, stack.getName(), x + PAD, y + PAD - 1, 0xFFFFFFFF, true);

        for (int i = 0; i < COLS * ROWS; i++) {
            int cx = x + PAD + (i % COLS) * CELL;
            int cy = y + PAD + TITLE_H + (i / COLS) * CELL;
            ctx.fill(cx, cy, cx + 16, cy + 16, 0x33FFFFFF);
            ItemStack s = items.get(i);
            if (!s.isEmpty()) {
                ctx.drawItem(s, cx, cy);
                ctx.drawStackOverlay(tr, s, cx, cy);
            }
        }
        return true;
    }
}
