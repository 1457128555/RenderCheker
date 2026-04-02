# RenderChecker

Android 应用，用于测试在 GLES 2.0 EGL 上下文中调用 GLES 3.0 / 3.1 / 3.2 API 的兼容性。

## 背景

Android 设备的 GPU 驱动实现各异。本工具通过在 GLES 2.0 上下文中直接调用 GLES 3.x API，检测驱动是否在低版本上下文中向下兼容高版本接口。这对于需要在 GLES 2.0 上下文中使用部分 3.x 特性的应用场景有参考价值。

## 测试覆盖

共 **33 项**测试，覆盖 GLES 3.0 / 3.1 / 3.2 全部主要特性：

### GLES 3.0（15 项）
| 测试项 | API |
|--------|-----|
| VAO | `glGenVertexArrays` / `glBindVertexArray` |
| Instanced Rendering | `glDrawArraysInstanced` |
| Uniform Buffer Object | `glBindBufferBase(GL_UNIFORM_BUFFER)` |
| Transform Feedback | `glBeginTransformFeedback` |
| Multiple Render Targets | `glDrawBuffers` |
| 3D Texture | `glTexImage3D` |
| 2D Texture Array | `glTexStorage2D` + `GL_TEXTURE_2D_ARRAY` |
| Pixel Buffer Object | `glBindBuffer(GL_PIXEL_PACK_BUFFER)` |
| Occlusion Query | `glBeginQuery(GL_ANY_SAMPLES_PASSED)` |
| Sync / Fence | `glFenceSync` / `glClientWaitSync` |
| Sampler Object | `glGenSamplers` / `glBindSampler` |
| ETC2 Compression | `glCompressedTexImage2D(GL_COMPRESSED_RGB8_ETC2)` |
| sRGB Framebuffer | `GL_FRAMEBUFFER_SRGB` |
| Primitive Restart | `GL_PRIMITIVE_RESTART_FIXED_INDEX` |
| Map Buffer Range | `glMapBufferRange` / `glUnmapBuffer` |

### GLES 3.1（9 项）
| 测试项 | API |
|--------|-----|
| Compute Shader | `GL_COMPUTE_SHADER` 编译 |
| SSBO | `glBindBufferBase(GL_SHADER_STORAGE_BUFFER)` |
| Indirect Draw | `glDrawArraysIndirect` |
| Image Load/Store | `glBindImageTexture` |
| Atomic Counter | `glBindBufferBase(GL_ATOMIC_COUNTER_BUFFER)` |
| Separate Shader Program | `glCreateShaderProgramv` |
| Texture Multisample | `glTexStorage2DMultisample` |
| Framebuffer No Attachments | `glFramebufferParameteri` |
| Vertex Attrib Binding | `glVertexAttribFormat` / `glVertexAttribBinding` |

### GLES 3.2（9 项）
| 测试项 | API |
|--------|-----|
| Geometry Shader | `GL_GEOMETRY_SHADER` 编译 + `glFramebufferTexture` |
| Tessellation Shader | `GL_TESS_CONTROL_SHADER` / `GL_TESS_EVALUATION_SHADER` 编译 |
| ASTC Compression | `glCompressedTexImage2D(GL_COMPRESSED_RGBA_ASTC_4x4)` |
| Texture Buffer Object | `glTexBuffer` |
| Debug Output | `glEnable(GL_DEBUG_OUTPUT)` |
| Advanced Blend | `glBlendBarrier` |
| Sample Shading | `glMinSampleShading` |
| Copy Image | `glCopyImageSubData` |
| Primitive Bounding Box | `glPrimitiveBoundingBox` |

## 测试原理

1. 通过 `GLSurfaceView.setEGLContextClientVersion(2)` 创建 GLES 2.0 上下文
2. 在 GL 线程中直接调用 GLES 3.x API
3. 通过 `glGetError()` 判断调用是否被驱动接受

### 结果状态
- **PASS** — API 调用成功，`glGetError()` 返回 `GL_NO_ERROR`
- **FAIL** — API 调用返回了 GL 错误（如 `GL_INVALID_OPERATION`）
- **CRASH** — 调用过程中抛出异常

> 注意：PASS 仅表示驱动接受了该 API 调用，不代表功能完全正确。

## 报告导出

测试完成后自动保存 JSON 报告到 `Android/data/com.rendertest/files/reports/`，包含：
- 设备信息（型号、Android 版本、GPU、GL 版本）
- 测试汇总（pass / fail / crash 计数）
- 每项测试的详细结果

支持通过分享按钮将报告发送到其他应用。

## 构建

```bash
# 需要 Android SDK，minSdk 24，targetSdk 34
./gradlew assembleDebug
```

或在 Android Studio 中直接打开项目构建。

## 已测试设备

| 设备 | GPU | Android | 结果 |
|------|-----|---------|------|
| Pixel 9 | Mali-G715 | 16 | 33/33 PASS |

## 技术细节

- 纯 Java，无第三方依赖
- EGL 上下文版本：2.0
- Android SDK GLES32 Java binding 中 `glDebugMessageCallback` 等 debug 系列 API 未实现，Debug Output 测试仅验证 `glEnable/glIsEnabled`

## License

MIT
