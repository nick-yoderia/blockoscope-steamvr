package nzy.parallaxscreen;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Passes vertices on with x moved by a fixed amount: used to move single GUI elements while their meshes are built
 * (see {@link StereoRenderer#edgeShiftGui}). Reused for every element, so it holds no other state.
 */
public final class ShiftedVertexConsumer implements VertexConsumer {
    private VertexConsumer delegate;
    private float shiftX;

    public ShiftedVertexConsumer set(VertexConsumer delegate, float shiftX) {
        this.delegate = delegate;
        this.shiftX = shiftX;
        return this;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x + shiftX, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        delegate.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
        delegate.setColor(color);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        delegate.setLineWidth(width);
        return this;
    }
}
