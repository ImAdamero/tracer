package pl.tracer.nametags;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Lista graczy: per nick można wyłączyć napis (zostaje dystans) albo tracer. */
public class PlayersScreen extends Screen {
    private static final int MAX_ROWS = 12;
    private List<TracerNametags.Row> rows;

    public PlayersScreen() {
        super(Text.literal("Gracze"));
    }

    @Override
    protected void init() {
        rows = TracerNametags.lastRows;
        int n = Math.min(rows.size(), MAX_ROWS);
        int x0 = width / 2 - 170;
        int y0 = height / 2 - (n * 24) / 2 + 6;
        for (int i = 0; i < n; i++) {
            String name = rows.get(i).name();
            int y = y0 + i * 24;
            ButtonWidget tag = ButtonWidget.builder(Text.literal(tagLabel(name)), b -> {
                toggle(TracerNametags.HIDE_TAG, name);
                b.setMessage(Text.literal(tagLabel(name)));
            }).dimensions(x0 + 190, y, 74, 20).build();
            ButtonWidget tr = ButtonWidget.builder(Text.literal(trLabel(name)), b -> {
                toggle(TracerNametags.HIDE_TRACER, name);
                b.setMessage(Text.literal(trLabel(name)));
            }).dimensions(x0 + 266, y, 74, 20).build();
            addDrawableChild(tag);
            addDrawableChild(tr);
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Zamknij"), b -> close())
                .dimensions(width / 2 - 40, y0 + n * 24 + 6, 80, 20).build());
    }

    private static void toggle(java.util.Set<String> set, String name) {
        if (!set.remove(name)) set.add(name);
    }

    private static String tagLabel(String n) {
        return TracerNametags.HIDE_TAG.contains(n) ? "Napis: OFF" : "Napis: ON";
    }

    private static String trLabel(String n) {
        return TracerNametags.HIDE_TRACER.contains(n) ? "Tracer: OFF" : "Tracer: ON";
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        int n = Math.min(rows.size(), MAX_ROWS);
        int x0 = width / 2 - 170;
        int y0 = height / 2 - (n * 24) / 2 + 6;
        g.drawCenteredTextWithShadow(textRenderer, "Gracze (K) - wyłącz napis lub tracer dla wybranego nicku",
                width / 2, y0 - 22, 0xFFFFFFFF);
        if (n == 0) {
            g.drawCenteredTextWithShadow(textRenderer, "Brak graczy (włącz tracery klawiszem R)",
                    width / 2, y0 + 4, 0xFF8A8A98);
        }
        for (int i = 0; i < n; i++) {
            TracerNametags.Row r = rows.get(i);
            int y = y0 + i * 24;
            String t = r.name() + "  " + (int) r.dist() + "m" + (r.ageSec() > 0 ? "  (" + r.ageSec() + "s temu)" : "");
            g.drawTextWithShadow(textRenderer, t, x0, y + 6, r.ageSec() > 0 ? 0xFFFFD27F : 0xFFFFFFFF);
        }
    }
}
