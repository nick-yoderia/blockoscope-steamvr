package nzy.blockoscope.steamvr.mixin.voxy;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import me.cortex.voxy.client.core.AbstractRenderPipeline;
import me.cortex.voxy.client.core.IrisVoxyRenderPipeline;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.util.DepthFramebuffer;
import me.cortex.voxy.client.iris.IGetIrisVoxyPipelineData;
import me.cortex.voxy.client.iris.IrisVoxyRenderPipelineData;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With a shader pack, Voxy draws its distant terrain with the data of the Iris pipeline that existed when Voxy started:
 * its draw targets, the pack's textures (colortex, depthtex, shadow maps...), storage buffers and uniform values. Each
 * eye has its own Iris pipeline (see IrisPipelineManagerMixin), so the other eye got no distant terrain at first, and
 * once it drew into that eye's targets, its water and other translucents still sampled the first eye's textures:
 * screen-space reflections on distant water showed the other eye's picture, out of place.
 *
 * Every Iris pipeline prepares its own Voxy data. Before Voxy draws, its framebuffers are pointed at the current eye's
 * draw targets and that eye's data replaces the one Voxy was built with, so its shaders get that eye's textures,
 * buffers and uniforms, and the eye's composite finds Voxy's depth. Voxy's shaders were compiled for the first
 * pipeline's uniform block; VoxyUniformOrderMixin gives every pipeline the same layout (before, each listed the
 * uniforms in a different order, and writing another pipeline's block made Voxy's culling read garbage and draw
 * nothing in one eye). Data that still doesn't match only lends its draw targets.
 */
@Mixin(value = IrisVoxyRenderPipeline.class, remap = false)
public abstract class VoxyIrisPipelineMixin {
    @Unique
    private static final int COLOR_ATTACHMENT0 = 0x8CE0;

    @Shadow
    @Final
    @Mutable
    private IrisVoxyRenderPipelineData data;

    @Shadow
    @Final
    public DepthFramebuffer fbTranslucent;

    /** The data Voxy was built with (null until the first draw). */
    @Unique
    private IrisVoxyRenderPipelineData blockoscopeSteamVr$own;

    /** The eye data whose draw targets the framebuffers point at. */
    @Unique
    private IrisVoxyRenderPipelineData blockoscopeSteamVr$current;

    /** Every eye's data linked to this pipeline. */
    @Unique
    private final Set<IrisVoxyRenderPipelineData> blockoscopeSteamVr$linked = Collections.newSetFromMap(new IdentityHashMap<>());

    @Unique
    private boolean blockoscopeSteamVr$warned;

    @Inject(method = "preSetup", at = @At("HEAD"))
    private void blockoscopeSteamVr$followCurrentEye(Viewport<?> viewport, CallbackInfo ci) {
        if (blockoscopeSteamVr$own == null) {
            blockoscopeSteamVr$own = data;
            blockoscopeSteamVr$current = data;
        }
        WorldRenderingPipeline currentPipeline = Iris.getPipelineManager().getPipelineNullable();
        if (!(currentPipeline instanceof IGetIrisVoxyPipelineData holder)) {
            return;
        }
        IrisVoxyRenderPipelineData own = blockoscopeSteamVr$own;
        IrisVoxyRenderPipelineData target = holder.voxy$getPipelineData();
        if (target == null || target == blockoscopeSteamVr$current
            || (target.thePipeline != null && target.thePipeline != (Object) this)
            || target.opaqueDrawTargets.length != own.opaqueDrawTargets.length
            || target.translucentDrawTargets.length != own.translucentDrawTargets.length) {
            return;
        }
        IrisVoxyRenderPipeline self = (IrisVoxyRenderPipeline) (Object) this;
        target.thePipeline = self;
        blockoscopeSteamVr$linked.add(target);
        blockoscopeSteamVr$linked.add(own);
        blockoscopeSteamVr$current = target;
        if (blockoscopeSteamVr$sameShaderInterface(target, own)) {
            data = target;
        } else {
            data = own;
            if (!blockoscopeSteamVr$warned) {
                blockoscopeSteamVr$warned = true;
                System.out.println("[Blockoscope SteamVR] Voxy's shader data differs between the eyes' shader pipelines; "
                    + "the right eye's distant terrain uses the left eye's textures and uniforms");
            }
        }
        DepthFramebuffer opaque = ((AbstractRenderPipeline) self).fb;
        for (int i = 0; i < target.opaqueDrawTargets.length; i++) {
            opaque.framebuffer.bind(COLOR_ATTACHMENT0 + i, target.opaqueDrawTargets[i], 0);
        }
        for (int i = 0; i < target.translucentDrawTargets.length; i++) {
            fbTranslucent.framebuffer.bind(COLOR_ATTACHMENT0 + i, target.translucentDrawTargets[i], 0);
        }
    }

    /** Whether Voxy's shaders, compiled for {@code own}, read {@code other}'s uniforms, textures and buffers correctly. */
    @Unique
    private static boolean blockoscopeSteamVr$sameShaderInterface(IrisVoxyRenderPipelineData other, IrisVoxyRenderPipelineData own) {
        IrisVoxyRenderPipelineData.StructLayout a = other.getUniforms();
        IrisVoxyRenderPipelineData.StructLayout b = own.getUniforms();
        if (a == null ? b != null : b == null || a.size() != b.size() || !a.layout().equals(b.layout())) {
            return false;
        }
        IrisVoxyRenderPipelineData.ImageSet imagesA = other.getImageSet();
        IrisVoxyRenderPipelineData.ImageSet imagesB = own.getImageSet();
        IrisVoxyRenderPipelineData.SSBOSet buffersA = other.getSsboSet();
        IrisVoxyRenderPipelineData.SSBOSet buffersB = own.getSsboSet();
        return Objects.equals(imagesA == null ? null : imagesA.layout(), imagesB == null ? null : imagesB.layout())
            && Objects.equals(buffersA == null ? null : buffersA.layout(), buffersB == null ? null : buffersB.layout())
            && Objects.equals(other.opaqueFragPatch(), own.opaqueFragPatch())
            && Objects.equals(other.translucentFragPatch(), own.translucentFragPatch())
            && Objects.equals(other.TAA, own.TAA);
    }

    /** Voxy frees the data it was built with. */
    @Inject(method = "free", at = @At("HEAD"))
    private void blockoscopeSteamVr$restoreOwnData(CallbackInfo ci) {
        if (blockoscopeSteamVr$own != null) {
            data = blockoscopeSteamVr$own;
        }
    }

    /** Voxy refuses to bind new data that still points at a pipeline, so the links go when this one is freed. */
    @Inject(method = "free", at = @At("RETURN"))
    private void blockoscopeSteamVr$unlink(CallbackInfo ci) {
        for (IrisVoxyRenderPipelineData linked : blockoscopeSteamVr$linked) {
            if (linked.thePipeline == (Object) this) {
                linked.thePipeline = null;
            }
        }
        blockoscopeSteamVr$linked.clear();
    }
}
