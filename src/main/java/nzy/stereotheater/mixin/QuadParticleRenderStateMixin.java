package nzy.stereotheater.mixin;

import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import nzy.stereotheater.StereoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Particles are stored relative to the centre camera when the frame is extracted, so without this they would have
 * no parallax between the eyes and float at the wrong depth. Each quad is moved by the eye's offset as it is built.
 */
@Mixin(value = QuadParticleRenderState.class, remap = false)
public abstract class QuadParticleRenderStateMixin {
    @ModifyVariable(method = "renderRotatedQuad", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float stereoTheater$eyeX(float x) {
        return x + StereoRenderer.eyeShift().x;
    }

    @ModifyVariable(method = "renderRotatedQuad", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float stereoTheater$eyeY(float y) {
        return y + StereoRenderer.eyeShift().y;
    }

    @ModifyVariable(method = "renderRotatedQuad", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private float stereoTheater$eyeZ(float z) {
        return z + StereoRenderer.eyeShift().z;
    }
}
