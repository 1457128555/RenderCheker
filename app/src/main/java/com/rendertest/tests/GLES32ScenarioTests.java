package com.rendertest.tests;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLES32;

import com.rendertest.model.TestResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class GLES32ScenarioTests extends ScenarioBaseTest {

    public GLES32ScenarioTests() {
        super("GLES 3.2 场景");
    }

    @Override
    public List<TestResult> runAll() {
        List<TestResult> results = new ArrayList<>();
        results.add(scenarioGeometryShaderAmplification());
        results.add(scenarioTessellationRendering());
        return results;
    }

    /**
     * 场景1：Geometry Shader 放大
     * 验证：输入 1 个点 → GS 输出一个全屏三角形 → 回读验证像素覆盖
     */
    private TestResult scenarioGeometryShaderAmplification() {
        return runTest("[场景] Geometry Shader 放大", () -> {
            String vs =
                    "#version 320 es\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(0.0, 0.0, 0.0, 1.0);\n" +
                    "}\n";
            String gs =
                    "#version 320 es\n" +
                    "layout(points) in;\n" +
                    "layout(triangle_strip, max_vertices = 3) out;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(-1.0, -1.0, 0.0, 1.0);\n" +
                    "    EmitVertex();\n" +
                    "    gl_Position = vec4( 3.0, -1.0, 0.0, 1.0);\n" +
                    "    EmitVertex();\n" +
                    "    gl_Position = vec4(-1.0,  3.0, 0.0, 1.0);\n" +
                    "    EmitVertex();\n" +
                    "    EndPrimitive();\n" +
                    "}\n";
            String fs =
                    "#version 320 es\n" +
                    "precision mediump float;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    fragColor = vec4(0.0, 1.0, 0.5, 1.0);\n" +
                    "}\n";

            int vsShader = compileShaderStage(GLES20.GL_VERTEX_SHADER, vs);
            int gsShader = compileShaderStage(GLES32.GL_GEOMETRY_SHADER, gs);
            int fsShader;
            try {
                fsShader = compileShaderStage(GLES20.GL_FRAGMENT_SHADER, fs);
            } catch (RuntimeException e) {
                GLES20.glDeleteShader(vsShader);
                GLES20.glDeleteShader(gsShader);
                throw e;
            }

            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vsShader);
            GLES20.glAttachShader(program, gsShader);
            GLES20.glAttachShader(program, fsShader);
            GLES20.glLinkProgram(program);

            int[] linked = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
            if (linked[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(program);
                GLES20.glDeleteProgram(program);
                GLES20.glDeleteShader(vsShader);
                GLES20.glDeleteShader(gsShader);
                GLES20.glDeleteShader(fsShader);
                throw new RuntimeException("GS program link failed: " + log);
            }
            GLES20.glDeleteShader(vsShader);
            GLES20.glDeleteShader(gsShader);
            GLES20.glDeleteShader(fsShader);

            // 空 VAO（绘制 1 个点，不需要顶点数据）
            int[] vaos = new int[1];
            GLES30.glGenVertexArrays(1, vaos, 0);
            GLES30.glBindVertexArray(vaos[0]);

            int[] fbo = createFBO(FB_WIDTH, FB_HEIGHT);

            GLES20.glViewport(0, 0, FB_WIDTH, FB_HEIGHT);
            GLES20.glClearColor(0, 0, 0, 0);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1);

            int[] pixel = readPixel(FB_WIDTH / 2, FB_HEIGHT / 2);
            boolean centerHasColor = isNonBlack(pixel, 10);

            ByteBuffer allPixels = readPixels(FB_WIDTH, FB_HEIGHT);
            int nonBlack = countNonBlackPixels(allPixels, FB_WIDTH * FB_HEIGHT, 10);
            int totalPixels = FB_WIDTH * FB_HEIGHT;
            float coverage = (float) nonBlack / totalPixels * 100;

            GLES30.glBindVertexArray(0);
            GLES30.glDeleteVertexArrays(1, vaos, 0);
            destroyFBO(fbo);
            GLES20.glDeleteProgram(program);

            return (centerHasColor && coverage > 90 ? "VERIFIED" : "MISMATCH") +
                    " | 中心像素: RGBA(" + pixel[0] + "," + pixel[1] + "," + pixel[2] + "," + pixel[3] + ")" +
                    " | 覆盖率: " + String.format("%.1f", coverage) + "% (" + nonBlack + "/" + totalPixels + ")";
        });
    }

    /**
     * 场景2：Tessellation 细分渲染
     * 验证：输入 patch → TCS/TES 细分 → 渲染 → 回读验证有像素输出
     */
    private TestResult scenarioTessellationRendering() {
        return runTest("[场景] Tessellation 细分渲染", () -> {
            String vs =
                    "#version 320 es\n" +
                    "layout(location = 0) in vec2 aPos;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
                    "}\n";
            String tcs =
                    "#version 320 es\n" +
                    "layout(vertices = 3) out;\n" +
                    "void main() {\n" +
                    "    gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position;\n" +
                    "    if (gl_InvocationID == 0) {\n" +
                    "        gl_TessLevelInner[0] = 3.0;\n" +
                    "        gl_TessLevelOuter[0] = 3.0;\n" +
                    "        gl_TessLevelOuter[1] = 3.0;\n" +
                    "        gl_TessLevelOuter[2] = 3.0;\n" +
                    "    }\n" +
                    "}\n";
            String tes =
                    "#version 320 es\n" +
                    "layout(triangles, equal_spacing, ccw) in;\n" +
                    "void main() {\n" +
                    "    gl_Position = gl_TessCoord.x * gl_in[0].gl_Position\n" +
                    "               + gl_TessCoord.y * gl_in[1].gl_Position\n" +
                    "               + gl_TessCoord.z * gl_in[2].gl_Position;\n" +
                    "}\n";
            String fs =
                    "#version 320 es\n" +
                    "precision mediump float;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    fragColor = vec4(0.5, 0.0, 1.0, 1.0);\n" +
                    "}\n";

            int vsShader = compileShaderStage(GLES20.GL_VERTEX_SHADER, vs);
            int tcsShader = compileShaderStage(GLES32.GL_TESS_CONTROL_SHADER, tcs);
            int tesShader;
            try {
                tesShader = compileShaderStage(GLES32.GL_TESS_EVALUATION_SHADER, tes);
            } catch (RuntimeException e) {
                GLES20.glDeleteShader(vsShader);
                GLES20.glDeleteShader(tcsShader);
                throw e;
            }
            int fsShader;
            try {
                fsShader = compileShaderStage(GLES20.GL_FRAGMENT_SHADER, fs);
            } catch (RuntimeException e) {
                GLES20.glDeleteShader(vsShader);
                GLES20.glDeleteShader(tcsShader);
                GLES20.glDeleteShader(tesShader);
                throw e;
            }

            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vsShader);
            GLES20.glAttachShader(program, tcsShader);
            GLES20.glAttachShader(program, tesShader);
            GLES20.glAttachShader(program, fsShader);
            GLES20.glLinkProgram(program);

            int[] linked = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
            if (linked[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(program);
                GLES20.glDeleteProgram(program);
                GLES20.glDeleteShader(vsShader);
                GLES20.glDeleteShader(tcsShader);
                GLES20.glDeleteShader(tesShader);
                GLES20.glDeleteShader(fsShader);
                throw new RuntimeException("Tessellation program link failed: " + log);
            }
            GLES20.glDeleteShader(vsShader);
            GLES20.glDeleteShader(tcsShader);
            GLES20.glDeleteShader(tesShader);
            GLES20.glDeleteShader(fsShader);

            float[] patchVerts = {
                    0.0f,  0.9f,
                   -0.9f, -0.9f,
                    0.9f, -0.9f
            };

            int[] vaos = new int[1];
            GLES30.glGenVertexArrays(1, vaos, 0);
            GLES30.glBindVertexArray(vaos[0]);
            int[] vbos = new int[1];
            GLES20.glGenBuffers(1, vbos, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[0]);
            FloatBuffer fb = ByteBuffer.allocateDirect(patchVerts.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            fb.put(patchVerts).position(0);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, patchVerts.length * 4, fb, GLES20.GL_STATIC_DRAW);
            GLES20.glEnableVertexAttribArray(0);
            GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0);

            int[] fbo = createFBO(FB_WIDTH, FB_HEIGHT);

            GLES20.glViewport(0, 0, FB_WIDTH, FB_HEIGHT);
            GLES20.glClearColor(0, 0, 0, 0);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);

            // GL_PATCHES = 0x000E
            GLES32.glPatchParameteri(GLES32.GL_PATCH_VERTICES, 3);
            GLES20.glDrawArrays(0x000E, 0, 3);

            int[] pixel = readPixel(FB_WIDTH / 2, FB_HEIGHT / 3);
            boolean centerHasColor = isNonBlack(pixel, 10);

            ByteBuffer allPixels = readPixels(FB_WIDTH, FB_HEIGHT);
            int nonBlack = countNonBlackPixels(allPixels, FB_WIDTH * FB_HEIGHT, 10);
            int totalPixels = FB_WIDTH * FB_HEIGHT;
            float coverage = (float) nonBlack / totalPixels * 100;

            GLES20.glDeleteBuffers(1, vbos, 0);
            GLES30.glBindVertexArray(0);
            GLES30.glDeleteVertexArrays(1, vaos, 0);
            destroyFBO(fbo);
            GLES20.glDeleteProgram(program);

            return (centerHasColor && coverage > 30 ? "VERIFIED" : "MISMATCH") +
                    " | 像素: RGBA(" + pixel[0] + "," + pixel[1] + "," + pixel[2] + "," + pixel[3] + ")" +
                    " | 覆盖率: " + String.format("%.1f", coverage) + "% (" + nonBlack + "/" + totalPixels + ")";
        });
    }

    private int compileShaderStage(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) throw new RuntimeException("glCreateShader returned 0 for type 0x" + Integer.toHexString(type));
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("Shader compile failed: " + log);
        }
        return shader;
    }
}
