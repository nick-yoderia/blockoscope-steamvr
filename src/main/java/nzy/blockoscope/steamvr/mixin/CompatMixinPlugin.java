package nzy.blockoscope.steamvr.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Applies the mixins in nzy.blockoscope.steamvr.mixin.<modid> only when that mod is installed. */
public final class CompatMixinPlugin implements IMixinConfigPlugin {
    private static final String PACKAGE = "nzy.blockoscope.steamvr.mixin.";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String rest = mixinClassName.substring(PACKAGE.length());
        int dot = rest.indexOf('.');
        return dot < 0 || FabricLoader.getInstance().isModLoaded(rest.substring(0, dot));
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
