package nzy.parallaxtheater.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import nzy.parallaxtheater.StereoConfig;
import nzy.parallaxtheater.StereoRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * GameRenderer.render() runs once per eye. It must not resize the (half-width) eye target back to the window
 * size, screen-size uniforms should describe the eye target, and the end-of-frame cleanup should only run after
 * the second eye.
 */
@Mixin(value = GameRenderer.class, remap = false)
public abstract class GameRendererMixin {
    @Shadow
    private void bobView(CameraRenderState cameraState, PoseStack poseStack) {
        throw new AssertionError();
    }

    @Shadow
    private void bobHurt(CameraRenderState cameraState, PoseStack poseStack) {
        throw new AssertionError();
    }

    // --- Comfort: on a fixed virtual screen, a camera that sways, rolls or warps is hard to watch in 3D (Vivecraft
    // turns these off for the same reason). Only the camera is affected; the hand still bobs as in vanilla.

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void parallaxTheater$cameraBob(GameRenderer self, CameraRenderState cameraState, PoseStack poseStack) {
        if (!StereoRenderer.isRendering() || StereoConfig.cameraBobbing()) {
            bobView(cameraState, poseStack);
        }
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void parallaxTheater$cameraTilt(GameRenderer self, CameraRenderState cameraState, PoseStack poseStack) {
        if (!StereoRenderer.isRendering() || StereoConfig.damageTilt()) {
            bobHurt(cameraState, poseStack);
        }
    }

    /** Nausea and portal warp strength (the larger of the two effects). */
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    private float parallaxTheater$calmWarpStrength(float portal, float nausea) {
        float strength = Math.max(portal, nausea);
        return StereoRenderer.isRendering() ? strength * StereoConfig.warpPercent() / 100f : strength;
    }

    /** Nausea and portal warp spin, slowed along with the strength. */
    @ModifyArg(method = "renderLevel", index = 0, at = @At(value = "INVOKE",
        target = "Lorg/joml/Matrix4f;rotate(FLorg/joml/Vector3fc;)Lorg/joml/Matrix4f;"))
    private float parallaxTheater$calmWarpSpin(float angle) {
        return StereoRenderer.isRendering() ? angle * Math.max(0.2f, StereoConfig.warpPercent() / 100f) : angle;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;resize(II)V"))
    private void parallaxTheater$keepEyeSize(GameRenderer self, int width, int height) {
        if (!StereoRenderer.isRendering()) {
            self.resize(width, height);
        }
    }

    @ModifyArg(method = "render", index = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GlobalSettingsUniform;update(IIDJLnet/minecraft/client/DeltaTracker;ILnet/minecraft/world/phys/Vec3;Z)V"))
    private int parallaxTheater$eyeScreenWidth(int width) {
        return StereoRenderer.isRendering() ? StereoRenderer.eyeWidth() : width;
    }

    @ModifyArg(method = "render", index = 1, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GlobalSettingsUniform;update(IIDJLnet/minecraft/client/DeltaTracker;ILnet/minecraft/world/phys/Vec3;Z)V"))
    private int parallaxTheater$eyeScreenHeight(int height) {
        return StereoRenderer.isRendering() ? StereoRenderer.eyeHeight() : height;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V"))
    private void parallaxTheater$guiEndFrame(GuiRenderer renderer) {
        if (!StereoRenderer.isFirstEye()) {
            renderer.endFrame();
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/RenderBuffers;endFrame()V"))
    private void parallaxTheater$buffersEndFrame(RenderBuffers buffers) {
        if (!StereoRenderer.isFirstEye()) {
            buffers.endFrame();
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/resource/CrossFrameResourcePool;endFrame()V"))
    private void parallaxTheater$poolEndFrame(CrossFrameResourcePool pool) {
        if (!StereoRenderer.isFirstEye()) {
            pool.endFrame();
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void parallaxTheater$fogEndFrame(FogRenderer fog) {
        if (!StereoRenderer.isFirstEye()) {
            fog.endFrame();
        }
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lnet/minecraft/client/renderer/Projection;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private GpuBufferSlice parallaxTheater$handProjection(ProjectionMatrixBuffer buffer, Projection projection) {
        if (!StereoRenderer.isRendering()) {
            return buffer.getBuffer(projection);
        }
        return buffer.getBuffer(StereoRenderer.eyeHandProjection(projection.getMatrix(new Matrix4f())));
    }
}
