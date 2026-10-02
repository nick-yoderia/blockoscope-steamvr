package nzy.stereotheater.mixin.sodium;

import java.util.IdentityHashMap;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.renderer.DynamicUniformStorage;
import nzy.stereotheater.StereoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium writes the terrain's projection and model-view matrices once per frame. With two eyes per frame the second
 * eye's terrain would be drawn with the first eye's projection, which cancels the focus shift on blocks only: blocks
 * then sit much nearer than entities at the same distance. The matrices are written again for each eye.
 *
 * Iris swaps in a second uniform storage (and "updated" flag) for its shadow pass, so the eye is tracked per storage.
 */
@Mixin(value = UniformBufferManager.class, remap = false)
public abstract class SodiumUniformBufferManagerMixin {
    @Shadow
    private boolean hasUpdatedThisFrame;

    @Shadow
    private DynamicUniformStorage<?> uniformStorage;

    @Unique
    private final Map<Object, Integer> stereoTheater$eyePassPerStorage = new IdentityHashMap<>();

    @Inject(method = "update", at = @At("HEAD"))
    private void stereoTheater$updatePerEye(ChunkRenderMatrices matrices, FogParameters fogParameters, CallbackInfo ci) {
        if (!StereoRenderer.isRendering()) {
            return;
        }
        Integer eyePass = StereoRenderer.eyePassCounter();
        if (!eyePass.equals(stereoTheater$eyePassPerStorage.put(uniformStorage, eyePass))) {
            hasUpdatedThisFrame = false;
        }
    }
}
