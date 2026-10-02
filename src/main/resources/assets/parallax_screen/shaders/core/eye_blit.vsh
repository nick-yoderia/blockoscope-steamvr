#version 330

// The full-screen triangle from core/screenquad, squeezed into one half of the target.
// EYE_HALF is 0 for the left half and 1 for the right half; 2 fills the whole target with the whole source,
// 3 fills it with the left half of the source (the left eye of a side-by-side picture).

out vec2 texCoord;

void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
#if EYE_HALF >= 2
    gl_Position = vec4(uv.x * 2.0 - 1.0, uv.y * 2.0 - 1.0, 0.0, 1.0);
#else
    gl_Position = vec4(uv.x + float(EYE_HALF) - 1.0, uv.y * 2.0 - 1.0, 0.0, 1.0);
#endif
#if EYE_HALF == 3
    texCoord = vec2(uv.x * 0.5, uv.y);
#else
    texCoord = uv;
#endif
}
