package nzy.blockoscope.steamvr.mixin.voxy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import me.cortex.voxy.client.iris.IrisVoxyRenderPipelineData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Voxy lays out the shader pack's uniforms for its terrain shaders in the order it finds them, and the pack's custom
 * uniforms come out of a hash map keyed by object identity: every Iris pipeline (one per eye, see
 * IrisPipelineManagerMixin) got the same uniforms in a different order. Sorted by name, all pipelines share one
 * layout, so VoxyIrisPipelineMixin can hand Voxy's shaders (compiled for the first pipeline) the current eye's data.
 */
@Mixin(value = IrisVoxyRenderPipelineData.class, remap = false)
public abstract class VoxyUniformOrderMixin {
    @ModifyVariable(method = "createUniformLayoutStructAndUpdater", at = @At("HEAD"), argsOnly = true)
    private static List<?> blockoscopeSteamVr$sortByName(List<?> uniforms) {
        List<Object> sorted = new ArrayList<>(uniforms);
        sorted.sort(Comparator.comparing(uniform -> ((VoxyUniformWritingHolderAccessor) uniform).blockoscopeSteamVr$name()));
        return sorted;
    }
}
