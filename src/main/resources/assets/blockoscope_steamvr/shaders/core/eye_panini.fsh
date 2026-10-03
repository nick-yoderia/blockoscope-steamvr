#version 330

// Edge correction: shows the eye's rectilinear picture through a Panini projection (Sharpless et al.), which keeps
// the middle as it is and vertical lines straight but squeezes the stretched edges of a wide field of view back in.
// The full width of the picture stays; a little of the top and bottom middle is cropped.

uniform sampler2D InSampler;

layout(std140) uniform PaniniInfo {
    float Compression;  // Panini distance d: 0 = rectilinear (unchanged), 1 = full Panini
    float TanHalfX;     // tan of half the eye's horizontal field of view
    float TanHalfY;     // tan of half the eye's vertical field of view
    float Unused;
} panini;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float d = panini.Compression;
    float maxLon = atan(panini.TanHalfX);
    float maxX = (d + 1.0) * sin(maxLon) / (d + cos(maxLon));
    vec2 p = texCoord * 2.0 - 1.0;
    float x = p.x * maxX;
    float y = p.y * maxX * panini.TanHalfY / panini.TanHalfX;

    // Inverse Panini: the viewing direction (longitude, latitude) shown at (x, y).
    float k = x * x / ((d + 1.0) * (d + 1.0));
    float disc = k * k * d * d - (k + 1.0) * (k * d * d - 1.0);
    float cosLon = (-k * d + sqrt(max(disc, 0.0))) / (k + 1.0);
    float s = (d + 1.0) / (d + cosLon);
    float lon = atan(x, s * cosLon);
    float lat = atan(y, s);

    // Where that direction lies in the rectilinear eye picture.
    vec2 source = vec2(tan(lon) / panini.TanHalfX, tan(lat) / cos(lon) / panini.TanHalfY) * 0.5 + 0.5;
    if (any(lessThan(source, vec2(0.0))) || any(greaterThan(source, vec2(1.0)))) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
    } else {
        fragColor = texture(InSampler, source);
    }
}
