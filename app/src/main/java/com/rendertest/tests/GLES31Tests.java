package com.rendertest.tests;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLES31;

import com.rendertest.model.TestResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class GLES31Tests extends BaseGLTest {

    public GLES31Tests() {
        super("GLES 3.1");
    }

    @Override
    public List<TestResult> runAll() {
        List<TestResult> results = new ArrayList<>();
        results.add(testComputeShader());
        results.add(testSSBO());
        results.add(testIndirectDraw());
        results.add(testImageLoadStore());
        results.add(testAtomicCounter());
        results.add(testSeparateShaderProgram());
        results.add(testTextureMultisample());
        results.add(testFramebufferNoAttachments());
        results.add(testVertexAttribBinding());
        return results;
    }

    private TestResult testComputeShader() {
        return runTest("Compute Shader", () -> {
            String source =
                    "#version 310 es\n" +
                    "layout(local_size_x = 1) in;\n" +
                    "void main() {\n" +
                    "}\n";

            String compileResult = compileShader(GLES31.GL_COMPUTE_SHADER, source);

            int shader = GLES20.glCreateShader(GLES31.GL_COMPUTE_SHADER);
            if (shader == 0) {
                return "glCreateShader(GL_COMPUTE_SHADER) returned 0";
            }
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);

            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                GLES20.glDeleteShader(shader);
                return compileResult;
            }

            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, shader);
            GLES20.glLinkProgram(program);

            int[] linked = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
            if (linked[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(program);
                GLES20.glDeleteProgram(program);
                GLES20.glDeleteShader(shader);
                return "Link FAILED: " + log;
            }

            GLES20.glUseProgram(program);
            GLES31.glDispatchCompute(1, 1, 1);
            GLES31.glMemoryBarrier(GLES31.GL_ALL_BARRIER_BITS);

            GLES20.glUseProgram(0);
            GLES20.glDeleteProgram(program);
            GLES20.glDeleteShader(shader);

            return compileResult + " | glDispatchCompute(1,1,1) 调用完成";
        });
    }

    private TestResult testSSBO() {
        return runTest("SSBO (Shader Storage Buffer Object)", () -> {
            int[] buffers = new int[1];
            GLES20.glGenBuffers(1, buffers, 0);
            int ssbo = buffers[0];
            if (ssbo == 0) {
                return "glGenBuffers returned 0";
            }

            GLES30.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, ssbo);
            GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, ssbo);
            GLES20.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, 256, null, GLES20.GL_DYNAMIC_DRAW);

            GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, 0);
            GLES20.glDeleteBuffers(1, buffers, 0);

            return "SSBO id=" + ssbo + ", bindBufferBase(GL_SHADER_STORAGE_BUFFER)/bufferData/delete 均成功";
        });
    }

    private TestResult testIndirectDraw() {
        return runTest("Indirect Draw", () -> {
            // 创建 VAO + VBO
            int[] vaos = new int[1];
            GLES30.glGenVertexArrays(1, vaos, 0);
            GLES30.glBindVertexArray(vaos[0]);

            int[] vbos = new int[1];
            GLES20.glGenBuffers(1, vbos, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[0]);
            float[] vertices = {0.0f, 0.0f, 0.0f};
            ByteBuffer vb = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder());
            vb.asFloatBuffer().put(vertices);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 12, vb, GLES20.GL_STATIC_DRAW);
            GLES20.glEnableVertexAttribArray(0);
            GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 0, 0);

            // 创建 indirect buffer: DrawArraysIndirectCommand {count, instanceCount, first, reserved}
            int[] cmd = {1, 1, 0, 0};
            ByteBuffer ib = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            ib.asIntBuffer().put(cmd);
            int[] indirectBuf = new int[1];
            GLES20.glGenBuffers(1, indirectBuf, 0);
            GLES20.glBindBuffer(GLES31.GL_DRAW_INDIRECT_BUFFER, indirectBuf[0]);
            GLES20.glBufferData(GLES31.GL_DRAW_INDIRECT_BUFFER, 16, ib, GLES20.GL_STATIC_DRAW);

            // 注意：不执行 glDrawArraysIndirect，因为在无 shader program 的情况下
            // 部分驱动（如 Adreno 505）会直接 SIGSEGV（native crash，Java 无法捕获）。
            // 仅验证 indirect buffer 的绑定和数据上传是否被驱动接受。
            // 完整的 indirect draw 验证在场景测试中进行（带完整 shader pipeline）。

            // 清理
            GLES20.glDisableVertexAttribArray(0);
            GLES20.glDeleteBuffers(1, indirectBuf, 0);
            GLES20.glDeleteBuffers(1, vbos, 0);
            GLES30.glBindVertexArray(0);
            GLES30.glDeleteVertexArrays(1, vaos, 0);

            return "GL_DRAW_INDIRECT_BUFFER bind/bufferData 成功 (跳过 draw call 以避免无 program 时驱动崩溃)";
        });
    }

    private TestResult testImageLoadStore() {
        return runTest("Image Load/Store", () -> {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

            // 使用 glTexStorage2D 分配不可变存储
            GLES30.glTexStorage2D(GLES20.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8, 4, 4);

            // 绑定为 image unit (GL_READ_ONLY = 0x88B8)
            GLES31.glBindImageTexture(0, textures[0], 0, false, 0,
                    0x88B8, GLES30.GL_RGBA8);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glDeleteTextures(1, textures, 0);

            return "glTexStorage2D + glBindImageTexture(GL_READ_ONLY, GL_RGBA8) 调用完成";
        });
    }

    private TestResult testAtomicCounter() {
        return runTest("Atomic Counter", () -> {
            int[] buffers = new int[1];
            GLES20.glGenBuffers(1, buffers, 0);
            int acbo = buffers[0];
            if (acbo == 0) {
                return "glGenBuffers returned 0";
            }

            // GL_ATOMIC_COUNTER_BUFFER = 0x92C0
            GLES30.glBindBufferBase(0x92C0, 0, acbo);
            GLES20.glBindBuffer(0x92C0, acbo);

            // 分配 4 字节（一个 uint counter）
            ByteBuffer initData = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
            initData.asIntBuffer().put(0);
            GLES20.glBufferData(0x92C0, 4, initData, GLES20.GL_DYNAMIC_DRAW);

            GLES20.glBindBuffer(0x92C0, 0);
            GLES20.glDeleteBuffers(1, buffers, 0);

            return "Atomic Counter Buffer id=" + acbo + ", bindBufferBase/bufferData/delete 均成功";
        });
    }

    private TestResult testSeparateShaderProgram() {
        return runTest("Separate Shader Program", () -> {
            // 创建 program pipeline
            int[] pipelines = new int[1];
            GLES31.glGenProgramPipelines(1, pipelines, 0);
            int pipeline = pipelines[0];
            if (pipeline == 0) {
                return "glGenProgramPipelines returned 0";
            }

            GLES31.glBindProgramPipeline(pipeline);

            // 创建 separable vertex shader program
            String vsSource =
                    "#version 310 es\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(0.0);\n" +
                    "}\n";

            int vs = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
            GLES20.glShaderSource(vs, vsSource);
            GLES20.glCompileShader(vs);

            int program = GLES20.glCreateProgram();
            GLES31.glProgramParameteri(program, GLES31.GL_PROGRAM_SEPARABLE, GLES20.GL_TRUE);
            GLES20.glAttachShader(program, vs);
            GLES20.glLinkProgram(program);

            int[] linked = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
            String linkResult = (linked[0] != 0) ? "Link OK" : "Link FAILED";

            if (linked[0] != 0) {
                GLES31.glUseProgramStages(pipeline, GLES31.GL_VERTEX_SHADER_BIT, program);
            }

            // 清理
            GLES31.glBindProgramPipeline(0);
            GLES20.glDeleteProgram(program);
            GLES20.glDeleteShader(vs);
            GLES31.glDeleteProgramPipelines(1, pipelines, 0);

            return "Pipeline id=" + pipeline + " | " + linkResult + " | glUseProgramStages 调用完成";
        });
    }

    private TestResult testTextureMultisample() {
        return runTest("Texture Multisample", () -> {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);

            // GL_TEXTURE_2D_MULTISAMPLE = 0x9100
            GLES20.glBindTexture(0x9100, textures[0]);
            GLES31.glTexStorage2DMultisample(0x9100, 4, GLES30.GL_RGBA8, 4, 4, true);

            GLES20.glBindTexture(0x9100, 0);
            GLES20.glDeleteTextures(1, textures, 0);

            return "glTexStorage2DMultisample(4 samples, RGBA8, 4x4) 调用完成";
        });
    }

    private TestResult testFramebufferNoAttachments() {
        return runTest("Framebuffer No Attachments", () -> {
            int[] fbos = new int[1];
            GLES20.glGenFramebuffers(1, fbos, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[0]);

            // GL_FRAMEBUFFER_DEFAULT_WIDTH = 0x9310
            // GL_FRAMEBUFFER_DEFAULT_HEIGHT = 0x9311
            GLES31.glFramebufferParameteri(GLES20.GL_FRAMEBUFFER, 0x9310, 4);
            GLES31.glFramebufferParameteri(GLES20.GL_FRAMEBUFFER, 0x9311, 4);

            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            String fbResult = (status == GLES20.GL_FRAMEBUFFER_COMPLETE)
                    ? "Framebuffer complete" : "status: 0x" + Integer.toHexString(status);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glDeleteFramebuffers(1, fbos, 0);

            return "glFramebufferParameteri(DEFAULT_WIDTH/HEIGHT=4) | " + fbResult;
        });
    }

    private TestResult testVertexAttribBinding() {
        return runTest("Vertex Attrib Binding", () -> {
            int[] vaos = new int[1];
            GLES30.glGenVertexArrays(1, vaos, 0);
            GLES30.glBindVertexArray(vaos[0]);

            // 创建 VBO
            int[] vbos = new int[1];
            GLES20.glGenBuffers(1, vbos, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[0]);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 48, null, GLES20.GL_STATIC_DRAW);

            // 使用新的 vertex attrib binding API
            GLES31.glVertexAttribFormat(0, 3, GLES20.GL_FLOAT, false, 0);
            GLES31.glVertexAttribBinding(0, 0);
            GLES31.glBindVertexBuffer(0, vbos[0], 0, 12);
            GLES20.glEnableVertexAttribArray(0);

            // 清理
            GLES20.glDisableVertexAttribArray(0);
            GLES20.glDeleteBuffers(1, vbos, 0);
            GLES30.glBindVertexArray(0);
            GLES30.glDeleteVertexArrays(1, vaos, 0);

            return "glVertexAttribFormat/glVertexAttribBinding/glBindVertexBuffer 均成功";
        });
    }
}
