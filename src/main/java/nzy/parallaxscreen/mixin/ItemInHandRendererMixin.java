package nzy.parallaxscreen.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import nzy.parallaxscreen.StereoConfig;
import nzy.parallaxscreen.StereoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla holds items so low and far to the side that the item's centre is below the bottom of the view, and in 3D
 * the eye offset pushes it partly past the side edge in one eye. In 3D each arm reaches further forward, as if it were
 * longer: perspective draws it in towards the middle, so more of the hand and held item is in view, and being farther
 * away it has gentler depth. Optional raise and inward offsets (mirrored for the left arm) fine-tune it.
 */
@Mixin(value = ItemInHandRenderer.class, remap = false)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER, ordinal = 0))
    private void parallaxScreen$handPosition(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand,
                                              float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
                                              SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        if (!StereoRenderer.isRendering()) {
            return;
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
        poseStack.translate(-side * StereoConfig.handInward() / 100f, StereoConfig.handRaise() / 100f,
            -StereoConfig.handReach() / 100f);
    }
}
