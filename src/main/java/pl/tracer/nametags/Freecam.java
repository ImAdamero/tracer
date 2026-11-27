package pl.tracer.nametags;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;

/**
 * Freecam: postać stoi w miejscu, a kamerę przesuwa CameraMixin (ustawia pozycję kamery
 * co klatkę). Ruch: klawisze ruchu, spacja = góra, shift = dół, sprint = szybciej.
 */
public final class Freecam {
    private static boolean active;
    private static Vec3d pos;
    private static long lastNano;
    private static float savedYaw, savedPitch;
    private static boolean savedCulling;

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
        savedYaw = mc.player.getYaw();
        savedPitch = mc.player.getPitch();
        pos = null;          // ustawi się przy pierwszej klatce na aktualną pozycję kamery
        lastNano = 0;
        active = true;
        // bez ukrywania niewidocznych fragmentów mapy: widać jaskinie i nic nie dochodzi w locie
        savedCulling = mc.chunkCullingEnabled;
        mc.chunkCullingEnabled = false;
        if (mc.worldRenderer != null) mc.worldRenderer.scheduleTerrainUpdate();
    }

    public static void stop(MinecraftClient mc) {
        active = false;
        pos = null;
        mc.chunkCullingEnabled = savedCulling;
        if (mc.worldRenderer != null) mc.worldRenderer.scheduleTerrainUpdate();
        if (mc.player != null) {
            // mysz obracała postacią podczas freecam - przywracamy
            mc.player.setYaw(savedYaw);
            mc.player.setPitch(savedPitch);
        }
        KeyBinding.updatePressedStates();
    }

    /** Co tick: blokuje ruch i akcje postaci, żeby ciało stało w miejscu. */
    public static void tick(MinecraftClient mc) {
        if (!active) return;
        if (mc.player == null || mc.world == null) {
            active = false;
            pos = null;
            mc.chunkCullingEnabled = savedCulling;
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
    }

    /** Wołane co klatkę z Camera.update (CameraMixin). Zwraca nową pozycję kamery. */
    public static Vec3d step(Vec3d current, float yawDeg, float pitchDeg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (pos == null) pos = current;

        long now = System.nanoTime();
        double dt = lastNano == 0 ? 0 : Math.min(0.1, (now - lastNano) / 1e9);
        lastNano = now;

        if (mc.currentScreen == null) {
            GameOptions o = mc.options;
            double speed = TracerConfig.freecamSpeed * (down(mc, o.sprintKey) ? 2.5 : 1.0);
            double fwd = (down(mc, o.forwardKey) ? 1 : 0) - (down(mc, o.backKey) ? 1 : 0);
            double side = (down(mc, o.leftKey) ? 1 : 0) - (down(mc, o.rightKey) ? 1 : 0);
            double up = (down(mc, o.jumpKey) ? 1 : 0) - (down(mc, o.sneakKey) ? 1 : 0);

            double yr = Math.toRadians(yawDeg);
            double pr = Math.toRadians(pitchDeg);
            double fx = -Math.sin(yr) * Math.cos(pr);
            double fy = -Math.sin(pr);
            double fz = Math.cos(yr) * Math.cos(pr);
            double lx = Math.cos(yr);
            double lz = Math.sin(yr);

            pos = pos.add(
                    (fx * fwd + lx * side) * speed * dt,
                    (fy * fwd + up) * speed * dt,
                    (fz * fwd + lz * side) * speed * dt);
        }
        return pos;
    }

    private static boolean down(MinecraftClient mc, KeyBinding kb) {
        int code = KeyBindingHelper.getBoundKeyOf(kb).getCode();
        return InputUtil.isKeyPressed(mc.getWindow(), code);
    }
}
