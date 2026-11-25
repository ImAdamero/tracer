package pl.tracer.nametags.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.tracer.nametags.Freecam;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    public abstract float getYaw();

    @Shadow
    public abstract float getPitch();

    @Shadow
    public abstract Vec3d getCameraPos();

    /** Co klatkę, po wyliczeniu kamery: podmienia pozycję na pozycję freecam. */
    @Inject(method = "update", at = @At("TAIL"))
    private void tracernametags$freecam(CallbackInfo ci) {
        if (!Freecam.isActive()) return;
        Vec3d p = Freecam.step(getCameraPos(), getYaw(), getPitch());
        setPos(p.x, p.y, p.z);
    }

    /** Dzięki temu widać własną postać podczas freecam. */
    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
    private void tracernametags$third(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.isActive()) cir.setReturnValue(true);
    }
}
