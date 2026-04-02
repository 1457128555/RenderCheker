package com.rendertest.tests;

import android.opengl.GLES20;
import android.opengl.GLES30;

import com.rendertest.model.TestResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public abstract class ScenarioBaseTest extends BaseGLTest {

    // 标准 FBO 尺寸
    protected static final int FB_WIDTH = 16;
    protected static final int FB_HEIGHT = 16;

    protected ScenarioBaseTest(String apiLevel) {
        super(apiLevel);
    }

    // ---- Shader Program 工具 ----

    /**
     * 编译链接完整的 VS+FS 程序，返回 program ID。失败抛 RuntimeException。
     */
    protected int buildProgram(String vertexSource, String fragmentSource) {
        int vs = compileAndGetShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fs = compileAndGetShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);

        int program = GLES20.glCreateProgram();
        if (program == 0) throw new RuntimeException("glCreateProgram returned 0");

        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);

        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            GLES20.glDeleteShader(vs);
            GLES20.glDeleteShader(fs);
            throw new RuntimeException("Link FAILED: " + log);
        }

        // Shader 可以 detach+delete，program 保留引用
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);

        return program;
    }

    /**
     * 编译单个 shader，返回 shader ID。失败抛异常。
     */
    private int compileAndGetShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) throw new RuntimeException("glCreateShader returned 0");

        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("Compile FAILED: " + log);
        }
        return shader;
    }

    // ---- FBO 工具 ----

    /**
     * 创建一个带颜色纹理附件的 FBO，返回 int[]{fbo, colorTexture}。
     */
    protected int[] createFBO(int width, int height) {
        int[] fbos = new int[1];
        GLES20.glGenFramebuffers(1, fbos, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[0]);

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textures[0], 0);

        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("FBO incomplete: 0x" + Integer.toHexString(status));
        }

        return new int[]{fbos[0], textures[0]};
    }

    /**
     * 销毁 createFBO 返回的资源。
     */
    protected void destroyFBO(int[] fboAndTex) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glDeleteFramebuffers(1, new int[]{fboAndTex[0]}, 0);
        GLES20.glDeleteTextures(1, new int[]{fboAndTex[1]}, 0);
    }

    // ---- 像素回读与验证 ----

    /**
     * 读取 FBO 在 (x, y) 处的像素，返回 RGBA 四字节数组（0~255）。
     */
    protected int[] readPixel(int x, int y) {
        ByteBuffer buf = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        GLES20.glReadPixels(x, y, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
        buf.position(0);
        return new int[]{
                buf.get() & 0xFF,
                buf.get() & 0xFF,
                buf.get() & 0xFF,
                buf.get() & 0xFF
        };
    }

    /**
     * 读取整个 FBO 的像素数据，返回 width*height*4 字节的 buffer。
     */
    protected ByteBuffer readPixels(int width, int height) {
        ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
        buf.position(0);
        return buf;
    }

    /**
     * 检查像素是否与期望颜色匹配（允许 tolerance 误差，适应不同 GPU 精度）。
     */
    protected boolean colorMatches(int[] actual, int[] expected, int tolerance) {
        for (int i = 0; i < 4; i++) {
            if (Math.abs(actual[i] - expected[i]) > tolerance) return false;
        }
        return true;
    }

    /**
     * 检查像素是否非黑色（至少有一个通道 > threshold）。
     */
    protected boolean isNonBlack(int[] pixel, int threshold) {
        return pixel[0] > threshold || pixel[1] > threshold || pixel[2] > threshold;
    }

    /**
     * 统计 buffer 中非黑色像素的数量。
     */
    protected int countNonBlackPixels(ByteBuffer pixels, int totalPixels, int threshold) {
        pixels.position(0);
        int count = 0;
        for (int i = 0; i < totalPixels; i++) {
            int r = pixels.get() & 0xFF;
            int g = pixels.get() & 0xFF;
            int b = pixels.get() & 0xFF;
            int a = pixels.get() & 0xFF;
            if (r > threshold || g > threshold || b > threshold) {
                count++;
            }
        }
        return count;
    }

    // ---- 全屏四边形工具 ----

    /** 覆盖整个 viewport 的全屏三角形 strip 的顶点数据 */
    protected static final float[] FULLSCREEN_QUAD_VERTICES = {
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
    };

    /**
     * 创建全屏四边形的 VAO+VBO，返回 int[]{vao, vbo}。
     */
    protected int[] createFullscreenQuad() {
        int[] vaos = new int[1];
        GLES30.glGenVertexArrays(1, vaos, 0);
        GLES30.glBindVertexArray(vaos[0]);

        int[] vbos = new int[1];
        GLES20.glGenBuffers(1, vbos, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[0]);

        FloatBuffer fb = ByteBuffer.allocateDirect(FULLSCREEN_QUAD_VERTICES.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(FULLSCREEN_QUAD_VERTICES).position(0);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                FULLSCREEN_QUAD_VERTICES.length * 4, fb, GLES20.GL_STATIC_DRAW);

        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0);

        return new int[]{vaos[0], vbos[0]};
    }

    /**
     * 销毁全屏四边形资源。
     */
    protected void destroyFullscreenQuad(int[] vaoAndVbo) {
        GLES20.glDisableVertexAttribArray(0);
        GLES20.glDeleteBuffers(1, new int[]{vaoAndVbo[1]}, 0);
        GLES30.glBindVertexArray(0);
        GLES30.glDeleteVertexArrays(1, new int[]{vaoAndVbo[0]}, 0);
    }
}
