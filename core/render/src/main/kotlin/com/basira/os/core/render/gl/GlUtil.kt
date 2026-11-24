package com.basira.os.core.render.gl

import android.opengl.GLES11Ext
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

internal object GlUtil {

    fun floatBuffer(capacity: Int): FloatBuffer =
        ByteBuffer.allocateDirect(capacity * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()

    fun floatBuffer(data: FloatArray): FloatBuffer = floatBuffer(data.size).apply {
        put(data)
        position(0)
    }

    fun shortBuffer(data: ShortArray): ShortBuffer =
        ByteBuffer.allocateDirect(data.size * Short.SIZE_BYTES).order(ByteOrder.nativeOrder()).asShortBuffer().apply {
            put(data)
            position(0)
        }

    fun genBuffer(): Int {
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        return ids[0]
    }

    fun genVertexArray(): Int {
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        return ids[0]
    }

    /** Texture that receives camera images (GL_TEXTURE_EXTERNAL_OES). */
    fun createOesTexture(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        checkError("createOesTexture")
        return ids[0]
    }

    fun compileProgram(vertexSource: String, fragmentSource: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw IllegalStateException("Program link failed: $log")
        }
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw IllegalStateException("Shader compile failed: $log")
        }
        return shader
    }

    fun checkError(where: String) {
        val error = GLES30.glGetError()
        check(error == GLES30.GL_NO_ERROR) { "GL error 0x${Integer.toHexString(error)} at $where" }
    }
}
