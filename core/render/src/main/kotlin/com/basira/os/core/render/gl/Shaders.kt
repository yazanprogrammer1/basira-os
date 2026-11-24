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

    /** Camera image from ARCore (external OES texture). */
    const val CAMERA_OES_FRAGMENT = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform samplerExternalOES uCamera;
in vec2 vCoord;
out vec4 fragColor;
void main() {
    fragColor = vec4(texture(uCamera, vCoord).rgb, 1.0);
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
