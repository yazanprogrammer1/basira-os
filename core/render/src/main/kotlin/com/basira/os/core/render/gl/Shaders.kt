package com.basira.os.core.render.gl

/**
 * GLSL ES 3.00 sources. Kept in Kotlin (not assets) so the renderer needs no Context and the
 * sources ship with the class that uses them.
 */
internal object Shaders {

    /** Shared by all passes: position in NDC (location 0) + one varying (location 1). */
    const val QUAD_VERTEX = """#version 300 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aCoord;
out vec2 vCoord;
void main() {
    vCoord = aCoord;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""

    /**
     * Direct passthrough: the distortion mesh gives each screen pixel its eye tan-angle; we rotate it back
     * to where the camera was looking at capture time and sample the camera texture there. One fetch from
     * the full-resolution camera image per pixel, no intermediate eye buffer.
     * Mirrors DirectCameraMath.
     */
    const val DIRECT_CAMERA_FRAGMENT = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
uniform samplerExternalOES uCamera;
uniform vec2 uFovMin;
uniform vec2 uFovSize;
uniform mat3 uCaptureFromNow;
uniform vec2 uHalfTan;
uniform float uShift;
uniform vec2 uUv00;
uniform vec2 uUvX;
uniform vec2 uUvY;
in vec2 vCoord;
out vec4 fragColor;
const vec3 NIGHT = vec3(0.043, 0.059, 0.051);
void main() {
    if (vCoord.x < 0.0 || vCoord.x > 1.0 || vCoord.y < 0.0 || vCoord.y > 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec2 tanNow = uFovMin + vCoord * uFovSize;
    vec3 d = uCaptureFromNow * vec3(tanNow, -1.0);
    if (d.z > -1e-4) {
        fragColor = vec4(NIGHT, 1.0);
        return;
    }
    vec2 t = d.xy / -d.z;
    t.x -= uShift;
    vec2 q = t / uHalfTan * 0.5 + 0.5;
    if (q.x < 0.0 || q.x > 1.0 || q.y < 0.0 || q.y > 1.0) {
        fragColor = vec4(NIGHT, 1.0);
        return;
    }
    vec2 uv = uUv00 + q.x * uUvX + q.y * uUvY;
    fragColor = vec4(texture(uCamera, uv).rgb, 1.0);
}
"""

    /** Lens pre-warp: the mesh already holds distorted coordinates, so this is one fetch. */
    const val DISTORTION_FRAGMENT = """#version 300 es
precision mediump float;
uniform sampler2D uEye;
in vec2 vCoord;
out vec4 fragColor;
void main() {
    if (vCoord.x < 0.0 || vCoord.x > 1.0 || vCoord.y < 0.0 || vCoord.y > 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    fragColor = texture(uEye, vCoord);
}
"""

    /** Eye buffer over the passthrough: transparent outside the lens field (premultiplied alpha). */
    const val DISTORTION_COMPOSITE_FRAGMENT = """#version 300 es
precision mediump float;
uniform sampler2D uEye;
in vec2 vCoord;
out vec4 fragColor;
void main() {
    if (vCoord.x < 0.0 || vCoord.x > 1.0 || vCoord.y < 0.0 || vCoord.y > 1.0) {
        fragColor = vec4(0.0);
        return;
    }
    fragColor = texture(uEye, vCoord);
}
"""

    /** World-space window quad; local position in [-0.5, 0.5]^2 scaled by its size in metres. */
    const val WINDOW_VERTEX = """#version 300 es
layout(location = 0) in vec2 aPosition;
uniform mat4 uMvp;
uniform vec2 uSize;
out vec2 vLocal;
void main() {
    vLocal = aPosition;
    gl_Position = uMvp * vec4(aPosition * uSize, 0.0, 1.0);
}
"""

    /**
     * Window content with rounded corners and a hairline glass border, drawn from a signed distance
     * field (no extra textures). Four rotated-grid taps per pixel keep small text from shimmering:
     * the page is minified about 2-3x on the way to the eye buffer and OES textures have no mipmaps.
     * Output is premultiplied alpha.
     */
    const val WINDOW_FRAGMENT = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
uniform samplerExternalOES uPanel;
uniform mat4 uTexMatrix;
uniform vec2 uSize;
uniform float uRadius;
uniform float uBorder;
uniform float uBrightness;
in vec2 vLocal;
out vec4 fragColor;
float roundBox(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
void main() {
    vec2 p = vLocal * uSize;
    float d = roundBox(p, uSize * 0.5, uRadius);
    float aa = fwidth(d);
    float alpha = 1.0 - smoothstep(-aa, aa, d);
    if (alpha <= 0.0) discard;
    vec2 uv = (uTexMatrix * vec4(vLocal + 0.5, 0.0, 1.0)).xy;
    vec2 dx = dFdx(uv) * 0.25;
    vec2 dy = dFdy(uv) * 0.25;
    vec3 c = texture(uPanel, uv + dx + dy * 0.5).rgb
           + texture(uPanel, uv - dx - dy * 0.5).rgb
           + texture(uPanel, uv + dy - dx * 0.5).rgb
           + texture(uPanel, uv - dy + dx * 0.5).rgb;
    c *= 0.25 * uBrightness;
    float border = smoothstep(-uBorder - aa, -uBorder + aa, d);
    c = mix(c, vec3(1.0), 0.12 * border);
    fragColor = vec4(c * alpha, alpha);
}
"""

    /**
     * Calibration pattern in eye tan-angle space (vCoord = tan angle):
     * grid every 0.1, crosshair at the optical axis, ring at 30 degrees.
     * Straight grid lines through the lens = good k1/k2; one fused crosshair = good IPD.
     */
    const val TEST_PATTERN_FRAGMENT = """#version 300 es
precision highp float;
in vec2 vCoord;
out vec4 fragColor;
const vec3 NIGHT = vec3(0.043, 0.059, 0.051);
const vec3 GRID = vec3(0.45, 0.50, 0.48);
const vec3 EMERALD = vec3(0.063, 0.725, 0.506);
const vec3 MINT = vec3(0.431, 0.906, 0.718);
float gridLine(float coord, float spacing) {
    float d = abs(fract(coord / spacing + 0.5) - 0.5) * spacing;
    return 1.0 - smoothstep(0.0, 1.5 * fwidth(coord), d);
}
void main() {
    vec2 t = vCoord;
    float grid = max(gridLine(t.x, 0.1), gridLine(t.y, 0.1));
    float axisX = 1.0 - smoothstep(0.0, 3.0 * fwidth(t.x), abs(t.x));
    float axisY = 1.0 - smoothstep(0.0, 3.0 * fwidth(t.y), abs(t.y));
    float cross = max(axisX, axisY) * step(max(abs(t.x), abs(t.y)), 0.2);
    float r = length(t);
    float ring = 1.0 - smoothstep(0.0, 2.0 * fwidth(r), abs(r - 0.5774));
    vec3 color = mix(NIGHT, GRID, grid);
    color = mix(color, MINT, ring);
    color = mix(color, EMERALD, cross);
    fragColor = vec4(color, 1.0);
}
"""
}
