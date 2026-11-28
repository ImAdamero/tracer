package pl.tracer.nametags;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Set;

/** Lista graczy z animacjami: per nick wyłączasz napis (zostaje dystans) albo tracer. */
public class PlayersScreen extends Screen {
    private static final int ACCENT = 0xFF00AEFF, ACCENT2 = 0xFF8A5CFF, MUTED = 0xFF8A8A98;
    private static final int ON = 0xFF3DDC84, OFF = 0xFF3A3A46;
    private static final int MAX_ROWS = 12, ROW_H = 26, PW = 380, PILL_W = 74;

    private List<TracerNametags.Row> rows;
    private final long openAt = System.nanoTime();
    private long lastNs = openAt;
    private float[] kTag, kTr, hover;
    private int px, py, ph, n;

    public PlayersScreen() {
        super(Text.literal("Gracze"));
    }

    private static String nameOf(List<TracerNametags.Row> r, int i) {
        return r.get(i).name();
    }

    @Override
    protected void init() {
        rows = TracerNametags.lastRows;
        n = Math.min(rows.size(), MAX_ROWS);
        kTag = new float[n];
        kTr = new float[n];
        hover = new float[n];
        ph = 58 + Math.max(1, n) * ROW_H + 40;
        px = width / 2 - PW / 2;
        py = height / 2 - ph / 2;
        for (int i = 0; i < n; i++) {
            String name = nameOf(rows, i);
            kTag[i] = TracerNametags.HIDE_TAG.contains(name) ? 0f : 1f;
            kTr[i] = TracerNametags.HIDE_TRACER.contains(name) ? 0f : 1f;
            int y = rowY(i);
            addInvisible(px + PW - 2 * PILL_W - 20, y, () -> toggle(TracerNametags.HIDE_TAG, name));
            addInvisible(px + PW - PILL_W - 12, y, () -> toggle(TracerNametags.HIDE_TRACER, name));
        }
        addInvisible(px + PW / 2 - 40, py + ph - 30, this::close, 80);
    }

    private int rowY(int i) {
        return py + 54 + i * ROW_H;
    }

    private void addInvisible(int x, int y, Runnable r) {
        addInvisible(x, y, r, PILL_W);
    }

    private void addInvisible(int x, int y, Runnable r, int w) {
        ButtonWidget b = ButtonWidget.builder(Text.empty(), btn -> r.run())
                .dimensions(x, y, w, 20).build();
        b.setAlpha(0f);
        addDrawableChild(b);
    }

    private static void toggle(Set<String> set, String name) {
        if (!set.remove(name)) set.add(name);
    }

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static int al(int argb, float a) {
        int v = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 0xFF) * a)));
        return (v << 24) | (argb & 0xFFFFFF);
    }

    private static int mix(int c1, int c2, float t) {
        int r = (int) (((c1 >> 16) & 0xFF) * (1 - t) + ((c2 >> 16) & 0xFF) * t);
        int g = (int) (((c1 >> 8) & 0xFF) * (1 - t) + ((c2 >> 8) & 0xFF) * t);
        int b = (int) ((c1 & 0xFF) * (1 - t) + (c2 & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        float t = (now - openAt) / 1e9f;
        float e = ease(t / 0.35f);
        float k = Math.min(1f, dt * 14f);

        int oy = (int) ((1f - e) * 14);
        int y0 = py + oy;
        // panel + cień + ramka z gradientem
        g.fill(px - 2, y0 - 2, px + PW + 2, y0 + ph + 2, al(0x66000000, e));
        g.fill(px, y0, px + PW, y0 + ph, al(0xF0141419, e));
        g.fillGradient(px, y0, px + PW, y0 + 3, al(ACCENT, e), al(ACCENT2, e));
        g.fillGradient(px, y0 + 3, px + PW, y0 + 40, al(0x30FFFFFF, e), 0x00000000);
        // pulsujące światełko przy tytule
        float pulse = 0.55f + 0.45f * (float) Math.sin(t * 3.2);
        g.fill(px + 12, y0 + 15, px + 18, y0 + 21, al(mix(ACCENT, ACCENT2, pulse), e));
        g.drawText(textRenderer, "Gracze", px + 24, y0 + 13, al(0xFFFFFFFF, e), true);
        String hint = "klik = włącz/wyłącz";
        g.drawText(textRenderer, hint, px + PW - 12 - textRenderer.getWidth(hint), y0 + 13, al(MUTED, e), false);
        // nagłówki kolumn
        int c1 = px + PW - 2 * PILL_W - 20 + PILL_W / 2, c2 = px + PW - PILL_W - 12 + PILL_W / 2;
        g.drawCenteredTextWithShadow(textRenderer, "Napis", c1, y0 + 38, al(MUTED, e));
        g.drawCenteredTextWithShadow(textRenderer, "Tracer", c2, y0 + 38, al(MUTED, e));

        if (n == 0) {
            g.drawCenteredTextWithShadow(textRenderer, "Brak graczy w pobliżu (włącz tracery klawiszem R)",
                    px + PW / 2, y0 + 70, al(MUTED, e));
        }

        for (int i = 0; i < n; i++) {
            float re = ease((t - 0.08f - i * 0.04f) / 0.3f);
            if (re <= 0f) continue;
            TracerNametags.Row r = rows.get(i);
            String name = r.name();
            int y = rowY(i) - py + y0 + (int) ((1f - re) * 10);
            int x = px + 8 + (int) ((1f - re) * -16);
            boolean over = my >= y && my < y + 22 && mx >= px && mx < px + PW;
            hover[i] += ((over ? 1f : 0f) - hover[i]) * k;
            int bg = mix(0xFF1B1B22, 0xFF272733, hover[i]);
            g.fill(x, y, px + PW - 8, y + 22, al(bg, re * e));
            g.fill(x, y, x + 2, y + 22, al(r.ageSec() > 0 ? 0xFFFFD27F : ACCENT, re * e));

            g.drawText(textRenderer, name, x + 8, y + 3, al(0xFFFFFFFF, re * e), true);
            String sub = (int) r.dist() + "m" + (r.ageSec() > 0 ? "  ·  ostatnio widziany " + r.ageSec() + "s temu" : "  ·  widoczny");
            g.drawText(textRenderer, sub, x + 8, y + 13, al(r.ageSec() > 0 ? 0xFFFFD27F : MUTED, re * e), false);

            kTag[i] += ((TracerNametags.HIDE_TAG.contains(name) ? 0f : 1f) - kTag[i]) * k;
            kTr[i] += ((TracerNametags.HIDE_TRACER.contains(name) ? 0f : 1f) - kTr[i]) * k;
            pill(g, px + PW - 2 * PILL_W - 20, y + 1, kTag[i], re * e);
            pill(g, px + PW - PILL_W - 12, y + 1, kTr[i], re * e);
        }

        // przycisk Zamknij
        int bx = px + PW / 2 - 40, by = y0 + ph - 30;
        boolean bo = mx >= bx && mx < bx + 80 && my >= by && my < by + 20;
        g.fill(bx, by, bx + 80, by + 20, al(bo ? 0xFF2E2E3A : 0xFF22222C, e));
        g.fill(bx, by + 19, bx + 80, by + 20, al(bo ? ACCENT : 0xFF444455, e));
        g.drawCenteredTextWithShadow(textRenderer, "Zamknij", bx + 40, by + 6, al(0xFFFFFFFF, e));
    }

    /** Przełącznik z animowaną gałką; v = 1 włączony. */
    private void pill(DrawContext g, int x, int y, float v, float a) {
        int w = PILL_W, h = 20;
        g.fill(x, y, x + w, y + h, al(mix(OFF, ON, v), a));
        int kx = x + 2 + (int) ((w - 20) * v);
        g.fill(kx, y + 2, kx + 16, y + h - 2, al(0xFFFFFFFF, a));
        String s = v > 0.5f ? "ON" : "OFF";
        int tx = v > 0.5f ? x + 8 : x + w - 8 - textRenderer.getWidth(s);
        g.drawText(textRenderer, s, tx, y + 6, al(0xFF101014, a), false);
    }
}
