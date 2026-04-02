# GLES 全量特性测试 - 设计规格

## 概述

在现有 GLES 兼容性测试器基础上，将测试覆盖从 7 项扩展到 33 项，完整覆盖 GLES 3.0/3.1/3.2 相对于前一版本的所有主要新特性。

## 目标

- 完整覆盖 GLES 3.0/3.1/3.2 的所有主要新 API
- 保持调用级验证方式（调用 API → 检查 glGetError → 记录结果）
- 不改动现有架构，仅在三个测试类中追加测试方法

## 测试深度

**调用级验证：** 每项测试创建必要的 GL 对象，调用目标 API，检查 glGetError 返回值。不验证输出数据的正确性。这足以回答"该 API 在 GLES 2.0 上下文中是否可用"这个核心问题。

## 变更范围

仅修改三个文件：
- `app/src/main/java/com/rendertest/tests/GLES30Tests.java`
- `app/src/main/java/com/rendertest/tests/GLES31Tests.java`
- `app/src/main/java/com/rendertest/tests/GLES32Tests.java`

其他文件（BaseGLTest、TestRenderer、UI 等）无需修改。

## GLES 3.0 测试项（15 项，现有 3 + 新增 12）

| # | 测试项 | 核心 API | 状态 |
|---|--------|----------|------|
| 1 | VAO (Vertex Array Object) | `GLES30.glGenVertexArrays` / `glBindVertexArray` | 已有 |
| 2 | Instanced Rendering | `GLES30.glDrawArraysInstanced` | 已有 |
| 3 | Uniform Buffer Object | `GLES30.glBindBufferBase(GL_UNIFORM_BUFFER)` | 已有 |
| 4 | Transform Feedback | `GLES30.glBeginTransformFeedback` / `glEndTransformFeedback` | 新增 |
| 5 | Multiple Render Targets | `GLES30.glDrawBuffers` + 多个 `GL_COLOR_ATTACHMENT` | 新增 |
| 6 | 3D Textures | `GLES30.glTexImage3D(GL_TEXTURE_3D)` | 新增 |
| 7 | 2D Texture Array | `GLES30.glTexImage3D(GL_TEXTURE_2D_ARRAY)` | 新增 |
| 8 | Pixel Buffer Object | `glBindBuffer(GL_PIXEL_PACK_BUFFER)` + `glReadPixels` | 新增 |
| 9 | Occlusion Query | `GLES30.glBeginQuery(GL_ANY_SAMPLES_PASSED)` | 新增 |
| 10 | Sync / Fence | `GLES30.glFenceSync` / `glClientWaitSync` / `glDeleteSync` | 新增 |
| 11 | Sampler Object | `GLES30.glGenSamplers` / `glBindSampler` / `glDeleteSamplers` | 新增 |
| 12 | ETC2 Texture Compression | `glCompressedTexImage2D(GL_COMPRESSED_RGB8_ETC2)` | 新增 |
| 13 | sRGB Framebuffer | `glRenderbufferStorage(GL_SRGB8_ALPHA8)` | 新增 |
| 14 | Primitive Restart | `glEnable(GL_PRIMITIVE_RESTART_FIXED_INDEX)` | 新增 |
| 15 | Map Buffer Range | `GLES30.glMapBufferRange` / `glUnmapBuffer` | 新增 |

### 各测试方法逻辑

**Transform Feedback：**
创建 transform feedback object → 绑定 buffer → `glBeginTransformFeedback(GL_POINTS)` → `glEndTransformFeedback` → 删除资源

**Multiple Render Targets：**
创建 FBO → 附加 2 个颜色纹理到 `COLOR_ATTACHMENT0` 和 `COLOR_ATTACHMENT1` → `glDrawBuffers({COLOR_ATTACHMENT0, COLOR_ATTACHMENT1})` → 检查 FBO 完整性

**3D Textures：**
`glTexImage3D(GL_TEXTURE_3D, 0, GL_RGBA, 4, 4, 4, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)` → 检查 glGetError

**2D Texture Array：**
`glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA, 4, 4, 4, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)` → 检查 glGetError

**Pixel Buffer Object：**
创建 buffer → `glBindBuffer(GL_PIXEL_PACK_BUFFER)` → `glBufferData` 分配空间 → `glReadPixels` 读入 PBO → 解绑删除

**Occlusion Query：**
`glGenQueries` → `glBeginQuery(GL_ANY_SAMPLES_PASSED)` → `glEndQuery` → `glGetQueryObjectuiv(GL_QUERY_RESULT_AVAILABLE)` → `glDeleteQueries`

**Sync / Fence：**
`glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0)` → `glClientWaitSync` → `glDeleteSync`

**Sampler Object：**
`glGenSamplers` → `glSamplerParameteri(GL_TEXTURE_MIN_FILTER, GL_LINEAR)` → `glBindSampler(0, sampler)` → `glBindSampler(0, 0)` → `glDeleteSamplers`

**ETC2 Texture Compression：**
创建纹理 → 准备最小 ETC2 压缩数据块(8字节) → `glCompressedTexImage2D(GL_COMPRESSED_RGB8_ETC2, 0, 4, 4, 0, 8, data)` → 检查 glGetError

**sRGB Framebuffer：**
创建 FBO + Renderbuffer → `glRenderbufferStorage(GL_RENDERBUFFER, GL_SRGB8_ALPHA8, 1, 1)` → 附加到 FBO → 检查 FBO 完整性

**Primitive Restart：**
`glEnable(GL_PRIMITIVE_RESTART_FIXED_INDEX)` → 检查 `glIsEnabled` → `glDisable`

**Map Buffer Range：**
创建 buffer → `glBufferData` → `glMapBufferRange(GL_MAP_WRITE_BIT)` → 写入数据 → `glUnmapBuffer` → 删除

## GLES 3.1 测试项（9 项，现有 2 + 新增 7）

| # | 测试项 | 核心 API | 状态 |
|---|--------|----------|------|
| 1 | Compute Shader | `GLES31.glDispatchCompute` | 已有 |
| 2 | SSBO | `glBindBufferBase(GL_SHADER_STORAGE_BUFFER)` | 已有 |
| 3 | Indirect Draw | `GLES31.glDrawArraysIndirect` | 新增 |
| 4 | Image Load/Store | `GLES31.glBindImageTexture` | 新增 |
| 5 | Atomic Counter | `glBindBufferBase(GL_ATOMIC_COUNTER_BUFFER)` | 新增 |
| 6 | Separate Shader Program | `GLES31.glGenProgramPipelines` / `glUseProgramStages` | 新增 |
| 7 | Texture Multisample | `GLES31.glTexStorage2DMultisample` | 新增 |
| 8 | Framebuffer No Attachments | `GLES31.glFramebufferParameteri` | 新增 |
| 9 | Vertex Attrib Binding | `GLES31.glVertexAttribFormat` / `glVertexAttribBinding` | 新增 |

### 各测试方法逻辑

**Indirect Draw：**
创建 VAO + VBO → 创建 indirect buffer（含 DrawArraysIndirectCommand 结构：count=1, instanceCount=1, first=0, reservedMustBeZero=0）→ `glDrawArraysIndirect(GL_POINTS, 0)` → 清理

**Image Load/Store：**
创建 2D 纹理 → `glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, 4, 4)` → `glBindImageTexture(0, tex, 0, false, 0, GL_READ_ONLY, GL_RGBA8UI)` → 检查 glGetError

**Atomic Counter：**
创建 buffer → `glBindBufferBase(GL_ATOMIC_COUNTER_BUFFER, 0, buffer)` → `glBufferData` 分配 4 字节 → 清理

**Separate Shader Program：**
`glGenProgramPipelines` → `glBindProgramPipeline` → 创建 separable vertex program (`glProgramParameteri(GL_PROGRAM_SEPARABLE, GL_TRUE)`) → `glUseProgramStages(pipeline, GL_VERTEX_SHADER_BIT, program)` → 清理

**Texture Multisample：**
创建纹理 → `glBindTexture(GL_TEXTURE_2D_MULTISAMPLE)` → `glTexStorage2DMultisample(GL_TEXTURE_2D_MULTISAMPLE, 4, GL_RGBA8, 4, 4, true)` → 清理

**Framebuffer No Attachments：**
创建 FBO → `glFramebufferParameteri(GL_FRAMEBUFFER, GL_FRAMEBUFFER_DEFAULT_WIDTH, 4)` → `glFramebufferParameteri(GL_FRAMEBUFFER, GL_FRAMEBUFFER_DEFAULT_HEIGHT, 4)` → 检查 FBO 状态

**Vertex Attrib Binding：**
创建 VAO → `glVertexAttribFormat(0, 3, GL_FLOAT, false, 0)` → `glVertexAttribBinding(0, 0)` → `glBindVertexBuffer(0, vbo, 0, 12)` → 清理

## GLES 3.2 测试项（9 项，现有 2 + 新增 7）

| # | 测试项 | 核心 API | 状态 |
|---|--------|----------|------|
| 1 | Geometry Shader | 着色器编译 + `glFramebufferTexture` | 已有 |
| 2 | Tessellation Shader | TCS + TES 着色器编译 | 已有 |
| 3 | ASTC Texture Compression | `glCompressedTexImage2D(GL_COMPRESSED_RGBA_ASTC_4x4)` | 新增 |
| 4 | Texture Buffer Object | `GLES32.glTexBuffer` | 新增 |
| 5 | Debug Callback | `GLES32.glDebugMessageCallback` | 新增 |
| 6 | Advanced Blend | `GLES32.glBlendBarrier` | 新增 |
| 7 | Sample Shading | `GLES32.glMinSampleShading` | 新增 |
| 8 | Copy Image | `GLES32.glCopyImageSubData` | 新增 |
| 9 | Primitive Bounding Box | `GLES32.glPrimitiveBoundingBox` | 新增 |

### 各测试方法逻辑

**ASTC Texture Compression：**
创建纹理 → 准备最小 ASTC 4x4 压缩数据块(16字节) → `glCompressedTexImage2D(GL_COMPRESSED_RGBA_ASTC_4x4, 0, 4, 4, 0, 16, data)` → 检查 glGetError

**Texture Buffer Object：**
创建 buffer → `glBufferData` → 创建纹理 → `glBindTexture(GL_TEXTURE_BUFFER)` → `glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, buffer)` → 清理

**Debug Callback：**
`glDebugMessageCallback(callback, null)` → `glEnable(GL_DEBUG_OUTPUT)` → `glDebugMessageInsert(GL_DEBUG_SOURCE_APPLICATION, GL_DEBUG_TYPE_MARKER, 0, GL_DEBUG_SEVERITY_NOTIFICATION, -1, "test")` → 检查回调是否触发

**Advanced Blend：**
`glBlendBarrier()` → 检查 glGetError（只需验证 API 入口点存在）

**Sample Shading：**
`glEnable(GL_SAMPLE_SHADING)` → `glMinSampleShading(1.0f)` → 检查 glGetError → `glDisable(GL_SAMPLE_SHADING)`

**Copy Image：**
创建两个 4x4 纹理（GL_RGBA8）→ `glCopyImageSubData(src, GL_TEXTURE_2D, 0, 0, 0, 0, dst, GL_TEXTURE_2D, 0, 0, 0, 0, 4, 4, 1)` → 清理

**Primitive Bounding Box：**
`glPrimitiveBoundingBox(-1, -1, -1, 1, 1, 1, 1, 1)` → 检查 glGetError
