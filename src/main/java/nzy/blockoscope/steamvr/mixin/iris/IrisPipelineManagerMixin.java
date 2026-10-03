package nzy.blockoscope.steamvr.mixin.iris;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import nzy.blockoscope.steamvr.StereoRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the right eye its own Iris pipeline. A shader pack keeps history between frames (temporal anti-aliasing,
 * accumulated clouds and lighting, previous-frame matrices); with one pipeline the two eyes would take turns
 * overwriting it and each eye would blend in the other's view. Iris prepares the pipeline at the start of every
 * level render, so the active pipeline is switched there.
 */
@Mixin(value = PipelineManager.class, remap = false)
public abstract class IrisPipelineManagerMixin {
    @Shadow
    private WorldRenderingPipeline pipeline;

    @Shadow
    @Final
    private Function<NamespacedId, WorldRenderingPipeline> pipelineFactory;

    @Unique
    private final Map<NamespacedId, WorldRenderingPipeline> blockoscopeSteamVr$rightEyePipelines = new HashMap<>();

    @Inject(method = "preparePipeline", at = @At("HEAD"), cancellable = true)
    private void blockoscopeSteamVr$rightEyePipeline(NamespacedId dimension,
                                               CallbackInfoReturnable<WorldRenderingPipeline> cir) {
        if (StereoRenderer.eye() != StereoRenderer.RIGHT) {
            return; // the left eye and 2D use Iris's own pipelines
        }
        WorldRenderingPipeline right = blockoscopeSteamVr$rightEyePipelines.computeIfAbsent(dimension, pipelineFactory);
        pipeline = right;
        cir.setReturnValue(right);
    }

    @Inject(method = "destroyPipeline", at = @At("HEAD"))
    private void blockoscopeSteamVr$destroyRightEyePipelines(CallbackInfo ci) {
        blockoscopeSteamVr$rightEyePipelines.values().forEach(WorldRenderingPipeline::destroy);
        blockoscopeSteamVr$rightEyePipelines.clear();
    }
}
