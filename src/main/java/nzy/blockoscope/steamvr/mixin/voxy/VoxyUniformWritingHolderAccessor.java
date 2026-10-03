package nzy.blockoscope.steamvr.mixin.voxy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The name of one uniform in Voxy's uniform block (a private record), for VoxyUniformOrderMixin. */
@Mixin(targets = "me.cortex.voxy.client.iris.IrisVoxyRenderPipelineData$UniformWritingHolder", remap = false)
public interface VoxyUniformWritingHolderAccessor {
    @Accessor("name")
    String blockoscopeSteamVr$name();
}
