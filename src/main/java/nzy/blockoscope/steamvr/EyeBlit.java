package nzy.blockoscope.steamvr;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.resources.Identifier;

/**
 * Draws an eye texture into the left or right half of a target, scaled to fit. (26.2's texture copy cannot write
 * at an offset on OpenGL, and a render area only clips, so the half is placed by the vertex shader.)
 */
public final class EyeBlit {
    /** Left half, right half, whole target, whole target from the source's left half. */
    private static final RenderPipeline[] PIPELINES = {pipeline(0), pipeline(1), pipeline(2), pipeline(3)};
    private static final BindGroupLayout PANINI_LAYOUT = BindGroupLayout.builder()
        .withSampler("InSampler")
        .withUniform("PaniniInfo", UniformType.UNIFORM_BUFFER)
        .build();
    /** Left half, right half, with edge correction (see eye_panini.fsh). */
    private static final RenderPipeline[] PANINI_PIPELINES = {paniniPipeline(0), paniniPipeline(1)};
    /** PaniniInfo: four floats. */
    private static final int PANINI_UBO_SIZE = 16;

    private static MappableRingBuffer paniniUbo;
    /** Set by {@link #setEdgeCorrection} for this frame's eye blits; false = plain copy. */
    private static boolean panini;

    private EyeBlit() {}

    private static RenderPipeline paniniPipeline(int half) {
        return RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("blockoscope_steamvr", "pipeline/eye_panini_" + half))
            .withVertexShader(Identifier.fromNamespaceAndPath("blockoscope_steamvr", "core/eye_blit"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("blockoscope_steamvr", "core/eye_panini"))
            .withShaderDefine("EYE_HALF", half)
            .withBindGroupLayout(PANINI_LAYOUT)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();
    }

    /**
     * Edge correction for this frame's eye blits: {@code compression} is the Panini distance (0 = off), the tangents
     * are those of half the eyes' field of view (from their projection, so sprinting's wider view is followed).
     * Call once per frame before {@link #draw}.
     */
    public static void setEdgeCorrection(float compression, float tanHalfX, float tanHalfY) {
        panini = compression > 0f && tanHalfX > 0f && tanHalfY > 0f;
        if (!panini) {
            return;
        }
        if (paniniUbo == null) {
            paniniUbo = new MappableRingBuffer(() -> "Blockoscope SteamVR edge correction UBO",
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, PANINI_UBO_SIZE);
        }
        paniniUbo.rotate();
        try (GpuBufferSlice.MappedView view = paniniUbo.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(view.data())
                .putFloat(compression)
                .putFloat(tanHalfX)
                .putFloat(tanHalfY)
                .putFloat(0f);
        }
    }

    private static RenderPipeline pipeline(int half) {
        return RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("blockoscope_steamvr", "pipeline/eye_blit_" + half))
            .withVertexShader(Identifier.fromNamespaceAndPath("blockoscope_steamvr", "core/eye_blit"))
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
        RenderPassDescriptor descriptor = RenderPassDescriptor.create(() -> "Blockoscope SteamVR preview blit")
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
        RenderPassDescriptor descriptor = RenderPassDescriptor.create(() -> "Blockoscope SteamVR eye blit")
            .withColorAttachment(target.getColorTextureView())
            .withRenderArea(new RenderPass.RenderArea(0, 0, target.width, target.height));
        try (RenderPass pass = encoder.createRenderPass(descriptor)) {
            pass.setPipeline(panini ? PANINI_PIPELINES[half] : PIPELINES[half]);
            RenderSystem.bindDefaultUniforms(pass);
            if (panini) {
                pass.setUniform("PaniniInfo", paniniUbo.currentBuffer());
            }
            pass.bindTexture("InSampler", eye.getColorTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.enableScissor(half * halfWidth, 0, halfWidth, target.height);
            pass.draw(3, 1, 0, 0);
        }
    }
}
