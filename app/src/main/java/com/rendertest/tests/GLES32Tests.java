package com.rendertest.tests;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLES32;

import com.rendertest.model.TestResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class GLES32Tests extends BaseGLTest {

    public GLES32Tests() {
        super("GLES 3.2");
    }

    @Override
    public List<TestResult> runAll() {
        List<TestResult> results = new ArrayList<>();
        results.add(testGeometryShader());
        results.add(testTessellationShader());
        results.add(testASTCCompression());
        results.add(testTextureBufferObject());
        results.add(testDebugOutput());
        results.add(testAdvancedBlend());
        results.add(testSampleShading());
        results.add(testCopyImage());
        results.add(testPrimitiveBoundingBox());
        return results;
    }

    private TestResult testGeometryShader() {
        return runTest("Geometry Shader", () -> {
            String source =
                    "#version 320 es\n" +
                    "layout(points) in;\n" +
                    "layout(points, max_vertices = 1) out;\n" +
                    "void main() {\n" +
                    "    gl_Position = gl_in[0].gl_Position;\n" +
                    "    EmitVertex();\n" +
                    "    EndPrimitive();\n" +
                    "}\n";

            String compileResult = compileShader(GLES32.GL_GEOMETRY_SHADER, source);

            // 测试 glFramebufferTexture（GLES 3.2 新增）
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

            int[] fbos = new int[1];
            GLES20.glGenFramebuffers(1, fbos, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[0]);
            GLES32.glFramebufferTexture(GLES20.GL_FRAMEBUFFER,
                    GLES20.GL_COLOR_ATTACHMENT0, textures[0], 0);

            int fbStatus = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            String fbResult = (fbStatus == GLES20.GL_FRAMEBUFFER_COMPLETE)
                    ? "Framebuffer complete"
                    : "Framebuffer incomplete: 0x" + Integer.toHexString(fbStatus);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glDeleteFramebuffers(1, fbos, 0);
            GLES20.glDeleteTextures(1, textures, 0);

            return compileResult + " | glFramebufferTexture: " + fbResult;
        });
    }

    private TestResult testTessellationShader() {
        return runTest("Tessellation Shader", () -> {
            String tcsSource =
                    "#version 320 es\n" +
                    "layout(vertices = 3) out;\n" +
                    "void main() {\n" +
                    "    gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position;\n" +
                    "    if (gl_InvocationID == 0) {\n" +
                    "        gl_TessLevelInner[0] = 1.0;\n" +
                    "        gl_TessLevelOuter[0] = 1.0;\n" +
                    "        gl_TessLevelOuter[1] = 1.0;\n" +
                    "        gl_TessLevelOuter[2] = 1.0;\n" +
                    "    }\n" +
                    "}\n";

            String tesSource =
                    "#version 320 es\n" +
                    "layout(triangles, equal_spacing, ccw) in;\n" +
                    "void main() {\n" +
                    "    gl_Position = gl_TessCoord.x * gl_in[0].gl_Position\n" +
                    "               + gl_TessCoord.y * gl_in[1].gl_Position\n" +
                    "               + gl_TessCoord.z * gl_in[2].gl_Position;\n" +
                    "}\n";

            String tcsResult = compileShader(GLES32.GL_TESS_CONTROL_SHADER, tcsSource);
            String tesResult = compileShader(GLES32.GL_TESS_EVALUATION_SHADER, tesSource);

            return "TCS: " + tcsResult + " | TES: " + tesResult;
        });
    }

    private TestResult testASTCCompression() {
        return runTest("ASTC Texture Compression", () -> {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

            // ASTC 4x4 block = 16 bytes
            ByteBuffer data = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            for (int i = 0; i < 16; i++) data.put((byte) 0);
            data.position(0);

            // GL_COMPRESSED_RGBA_ASTC_4x4 = 0x93B0
            GLES20.glCompressedTexImage2D(GLES20.GL_TEXTURE_2D, 0, 0x93B0,
                    4, 4, 0, 16, data);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glDeleteTextures(1, textures, 0);

            return "glCompressedTexImage2D(GL_COMPRESSED_RGBA_ASTC_4x4, 4x4) 调用完成";
        });
    }

    private TestResult testTextureBufferObject() {
        return runTest("Texture Buffer Object", () -> {
            // 创建 buffer
            int[] buffers = new int[1];
            GLES20.glGenBuffers(1, buffers, 0);
            GLES20.glBindBuffer(GLES32.GL_TEXTURE_BUFFER, buffers[0]);
            GLES20.glBufferData(GLES32.GL_TEXTURE_BUFFER, 64, null, GLES20.GL_STATIC_DRAW);

            // 创建纹理并绑定 buffer
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES32.GL_TEXTURE_BUFFER, textures[0]);

            // GL_RGBA32F = 0x8814
            GLES32.glTexBuffer(GLES32.GL_TEXTURE_BUFFER, 0x8814, buffers[0]);

            GLES20.glBindTexture(GLES32.GL_TEXTURE_BUFFER, 0);
            GLES20.glDeleteTextures(1, textures, 0);
            GLES20.glDeleteBuffers(1, buffers, 0);

            return "glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F) 调用完成";
        });
    }

    private TestResult testDebugOutput() {
        return runTest("Debug Output", () -> {
            // Android SDK 的 GLES32 Java binding 中 debug 系列 API（glDebugMessageCallback、
            // glDebugMessageInsert、glGetDebugMessageLog）均未实现，调用会抛 UnsupportedOperationException。
            // 因此只测试 glEnable(GL_DEBUG_OUTPUT) 是否被驱动接受。

            // GL_DEBUG_OUTPUT = 0x92E0
            GLES20.glEnable(0x92E0);
            boolean enabled = GLES20.glIsEnabled(0x92E0);
            GLES20.glDisable(0x92E0);

            return "glEnable(GL_DEBUG_OUTPUT), isEnabled=" + enabled
                    + " (注: Android SDK Java binding 未实现 debug message 系列 API)";
        });
    }

    private TestResult testAdvancedBlend() {
        return runTest("Advanced Blend Equations", () -> {
            GLES32.glBlendBarrier();

            return "glBlendBarrier() 调用完成";
        });
    }

    private TestResult testSampleShading() {
        return runTest("Sample Shading", () -> {
            // GL_SAMPLE_SHADING = 0x8C36
            GLES20.glEnable(0x8C36);
            GLES32.glMinSampleShading(1.0f);
            boolean enabled = GLES20.glIsEnabled(0x8C36);
            GLES20.glDisable(0x8C36);

            return "glEnable(GL_SAMPLE_SHADING) + glMinSampleShading(1.0), isEnabled=" + enabled;
        });
    }

    private TestResult testCopyImage() {
        return runTest("Copy Image", () -> {
            // 创建两个纹理
            int[] textures = new int[2];
            GLES20.glGenTextures(2, textures, 0);

            // 源纹理
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES30.glTexStorage2D(GLES20.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8, 4, 4);

            // 目标纹理
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1]);
            GLES30.glTexStorage2D(GLES20.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8, 4, 4);

            // 复制
            GLES32.glCopyImageSubData(
                    textures[0], GLES20.GL_TEXTURE_2D, 0, 0, 0, 0,
                    textures[1], GLES20.GL_TEXTURE_2D, 0, 0, 0, 0,
                    4, 4, 1);

            GLES20.glDeleteTextures(2, textures, 0);

            return "glCopyImageSubData(4x4, RGBA8) 调用完成";
        });
    }

    private TestResult testPrimitiveBoundingBox() {
        return runTest("Primitive Bounding Box", () -> {
            GLES32.glPrimitiveBoundingBox(
                    -1.0f, -1.0f, -1.0f, 1.0f,
                    1.0f, 1.0f, 1.0f, 1.0f);

            return "glPrimitiveBoundingBox(-1,-1,-1,1, 1,1,1,1) 调用完成";
        });
    }
}
