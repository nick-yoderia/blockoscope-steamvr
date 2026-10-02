package nzy.stereotheater.mixin.iris;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import nzy.stereotheater.StereoRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** With a shader pack Iris draws the first-person hand itself, with its own projection; it gets the eye's offset too. */
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer", remap = false)
public abstract class IrisHandRendererMixin {
    @Redirect(method = "setupGlState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private GpuBufferSlice stereoTheater$handDepth(ProjectionMatrixBuffer buffer, Matrix4f projection) {
        return buffer.getBuffer(StereoRenderer.isRendering() ? StereoRenderer.eyeHandProjection(projection) : projection);
    }
}
