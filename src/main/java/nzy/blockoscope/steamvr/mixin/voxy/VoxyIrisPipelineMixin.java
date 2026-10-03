package nzy.blockoscope.steamvr.mixin.voxy;

import java.util.Collections;
import java.util.IdentityHashMap;
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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With a shader pack, Voxy draws its distant terrain straight into the shader pack's buffers of the Iris pipeline that
 * existed when Voxy started. Each eye has its own Iris pipeline (see IrisPipelineManagerMixin), so the other eye got
 * no distant terrain, and its shader pack found no Voxy depth (BSL then also lost its clouds).
 *
 * Every Iris pipeline prepares its own Voxy data (draw targets, uniform block). Before Voxy draws, its framebuffers
 * are pointed at the current eye's draw targets and that data is linked to it, so the eye's composite finds Voxy's
 * depth. The data Voxy was built with stays in use otherwise: Voxy's shaders were compiled against its uniform block,
 * and another pipeline's block lists the same uniforms in a different order (writing that one made Voxy's culling read
 * garbage and draw nothing in one eye).
 */
@Mixin(value = IrisVoxyRenderPipeline.class, remap = false)
public abstract class VoxyIrisPipelineMixin {
    @Unique
    private static final int COLOR_ATTACHMENT0 = 0x8CE0;

    @Shadow
    @Final
    private IrisVoxyRenderPipelineData data;

    @Shadow
    @Final
    public DepthFramebuffer fbTranslucent;

    /** The eye data whose draw targets the framebuffers point at (null = the data Voxy was built with). */
    @Unique
    private IrisVoxyRenderPipelineData blockoscopeSteamVr$current;

    /** Every eye's data linked to this pipeline. */
    @Unique
    private final Set<IrisVoxyRenderPipelineData> blockoscopeSteamVr$linked = Collections.newSetFromMap(new IdentityHashMap<>());

    @Inject(method = "preSetup", at = @At("HEAD"))
    private void blockoscopeSteamVr$followCurrentEye(Viewport<?> viewport, CallbackInfo ci) {
        WorldRenderingPipeline currentPipeline = Iris.getPipelineManager().getPipelineNullable();
        if (!(currentPipeline instanceof IGetIrisVoxyPipelineData holder)) {
            return;
        }
        IrisVoxyRenderPipelineData target = holder.voxy$getPipelineData();
        IrisVoxyRenderPipelineData current = blockoscopeSteamVr$current == null ? data : blockoscopeSteamVr$current;
        if (target == null || target == current || (target.thePipeline != null && target.thePipeline != (Object) this)
            || target.opaqueDrawTargets.length != data.opaqueDrawTargets.length
            || target.translucentDrawTargets.length != data.translucentDrawTargets.length) {
            return;
        }
        IrisVoxyRenderPipeline self = (IrisVoxyRenderPipeline) (Object) this;
        target.thePipeline = self;
        blockoscopeSteamVr$linked.add(target);
        blockoscopeSteamVr$linked.add(data);
        blockoscopeSteamVr$current = target;
        DepthFramebuffer opaque = ((AbstractRenderPipeline) self).fb;
        for (int i = 0; i < target.opaqueDrawTargets.length; i++) {
            opaque.framebuffer.bind(COLOR_ATTACHMENT0 + i, target.opaqueDrawTargets[i], 0);
        }
        for (int i = 0; i < target.translucentDrawTargets.length; i++) {
            fbTranslucent.framebuffer.bind(COLOR_ATTACHMENT0 + i, target.translucentDrawTargets[i], 0);
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
