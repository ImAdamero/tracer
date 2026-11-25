package pl.tracer.nametags;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/**
 * Freecam bez mixinów: kamera podpięta pod "duszka" (kopię gracza), który lata niezależnie.
 * Ruch: klawisze ruchu, spacja = góra, shift = dół, sprint = szybciej.
 */
public final class Freecam {
    private static boolean active;
    private static OtherClientPlayerEntity cam;
    private static double x, y, z;
    private static long lastNano;

    private Freecam() {}

    public static boolean isActive() {
        return active;
    }

    public static void toggle(MinecraftClient mc) {
        if (active) stop(mc);
        else start(mc);
    }

    private static void start(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        cam = new OtherClientPlayerEntity(mc.world, mc.player.getGameProfile());
        x = mc.player.getX();
        y = mc.player.getY();
        z = mc.player.getZ();
        cam.refreshPositionAndAngles(x, y, z, mc.player.getYaw(), mc.player.getPitch());
        mc.setCameraEntity(cam);
        lastNano = System.nanoTime();
        active = true;
    }

    public static void stop(MinecraftClient mc) {
        active = false;
        cam = null;
        if (mc.player != null) mc.setCameraEntity(mc.player);
    }

    /** Co tick: blokuje ruch i akcje postaci, żeby ciało stało w miejscu. */
    public static void tick(MinecraftClient mc) {
        if (!active) return;
        if (mc.player == null || mc.world == null) {
            stop(mc);
            return;
        }
        GameOptions o = mc.options;
        KeyBinding[] move = {o.forwardKey, o.backKey, o.leftKey, o.rightKey, o.jumpKey, o.sneakKey, o.sprintKey};
        for (KeyBinding k : move) k.setPressed(false);
        KeyBinding[] act = {o.attackKey, o.useKey, o.pickItemKey};
        for (KeyBinding k : act) {
            k.setPressed(false);
            while (k.wasPressed()) { /* zjedz kliknięcia */ }
        }
        if (o.hudHidden) update(mc); // gdy F1: render HUD nie działa, ruszaj z ticka
    }

    /** Co klatkę: przesuwa kamerę. */
    public static void update(MinecraftClient mc) {
        if (!active || cam == null || mc.player == null) return;

        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastNano) / 1e9);
        lastNano = now;

        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        if (mc.currentScreen == null) {
            GameOptions o = mc.options;
            double speed = TracerConfig.freecamSpeed * (down(mc, o.sprintKey) ? 2.5 : 1.0);
            double fwd = (down(mc, o.forwardKey) ? 1 : 0) - (down(mc, o.backKey) ? 1 : 0);
            double side = (down(mc, o.leftKey) ? 1 : 0) - (down(mc, o.rightKey) ? 1 : 0);
            double up = (down(mc, o.jumpKey) ? 1 : 0) - (down(mc, o.sneakKey) ? 1 : 0);

            double yr = Math.toRadians(yaw);
            double pr = Math.toRadians(pitch);
            double fx = -Math.sin(yr) * Math.cos(pr);
            double fy = -Math.sin(pr);
            double fz = Math.cos(yr) * Math.cos(pr);
            double lx = Math.cos(yr);
            double lz = Math.sin(yr);

            x += (fx * fwd + lx * side) * speed * dt;
            y += (fy * fwd + up) * speed * dt;
            z += (fz * fwd + lz * side) * speed * dt;
        }
        cam.refreshPositionAndAngles(x, y, z, yaw, pitch);
    }

    private static boolean down(MinecraftClient mc, KeyBinding kb) {
        int code = KeyBindingHelper.getBoundKeyOf(kb).getCode();
        return InputUtil.isKeyPressed(mc.getWindow(), code);
    }
}
