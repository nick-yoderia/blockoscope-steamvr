package nzy.parallaxscreen;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

/**
 * Draws an eye texture into the left or right half of a target, scaled to fit. (26.2's texture copy cannot write
 * at an offset on OpenGL, and a render area only clips, so the half is placed by the vertex shader.)
 */
public final class EyeBlit {
    /** Left half, right half, whole target, whole target from the source's left half. */
    private static final RenderPipeline[] PIPELINES = {pipeline(0), pipeline(1), pipeline(2), pipeline(3)};

    private EyeBlit() {}

    private static RenderPipeline pipeline(int half) {
        return RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("parallax_screen", "pipeline/eye_blit_" + half))
            .withVertexShader(Identifier.fromNamespaceAndPath("parallax_screen", "core/eye_blit"))
            .withFragmentShader(Identifier.withDefaultNamespace("core/blit_screen"))
            .withShaderDefine("EYE_HALF", half)
            .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();
    }

    /**
     * Draws {@code source} over the whole of {@code target}, scaled to fit (the window preview); with
     * {@code leftHalfOnly}, only the left half of {@code source} (one eye of a side-by-side picture).
     */
    public static void drawFull(CommandEncoder encoder, RenderTarget source, RenderTarget target, boolean leftHalfOnly) {
        RenderPassDescriptor descriptor = RenderPassDescriptor.create(() -> "Parallax Screen preview blit")
            .withColorAttachment(target.getColorTextureView())
            .withRenderArea(new RenderPass.RenderArea(0, 0, target.width, target.height));
        try (RenderPass pass = encoder.createRenderPass(descriptor)) {
            pass.setPipeline(PIPELINES[leftHalfOnly ? 3 : 2]);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", source.getColorTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
    }

    /** Draws {@code eye} into half {@code half} (0 = left, 1 = right) of {@code target}. Draw the left half first. */
    public static void draw(CommandEncoder encoder, RenderTarget eye, RenderTarget target, int half) {
        int halfWidth = target.width / 2;
        RenderPassDescriptor descriptor = RenderPassDescriptor.create(() -> "Parallax Screen eye blit")
            .withColorAttachment(target.getColorTextureView())
            .withRenderArea(new RenderPass.RenderArea(0, 0, target.width, target.height));
        try (RenderPass pass = encoder.createRenderPass(descriptor)) {
            pass.setPipeline(PIPELINES[half]);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", eye.getColorTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.enableScissor(half * halfWidth, 0, halfWidth, target.height);
            pass.draw(3, 1, 0, 0);
        }
    }
}
