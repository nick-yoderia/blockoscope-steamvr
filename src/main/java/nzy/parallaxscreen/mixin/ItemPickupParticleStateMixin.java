package nzy.parallaxscreen.mixin;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import nzy.parallaxscreen.StereoRenderer;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Items flying into the player on pickup are stored relative to the centre camera too (see QuadParticleRenderStateMixin). */
@Mixin(targets = "net.minecraft.client.particle.ItemPickupParticleGroup$State", remap = false)
public abstract class ItemPickupParticleStateMixin {
    @Redirect(method = "submit", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void parallaxScreen$eyeOffset(EntityRenderDispatcher dispatcher, EntityRenderState state, CameraRenderState camera,
                                         double x, double y, double z, PoseStack poseStack, SubmitNodeCollector collector) {
        Vector3f shift = StereoRenderer.eyeShift();
        dispatcher.submit(state, camera, x + shift.x, y + shift.y, z + shift.z, poseStack, collector);
    }
}
