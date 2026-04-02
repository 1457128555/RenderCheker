# RenderChecker

Android 应用，用于测试在 GLES 2.0 EGL 上下文中调用 GLES 3.0 / 3.1 / 3.2 API 的兼容性。

## 背景

Android 设备的 GPU 驱动实现各异。本工具通过在 GLES 2.0 上下文中直接调用 GLES 3.x API，检测驱动是否在低版本上下文中向下兼容高版本接口。这对于需要在 GLES 2.0 上下文中使用部分 3.x 特性的应用场景有参考价值。

## 测试覆盖

共 **33 项**调用级测试 + **11 项**场景测试，覆盖 GLES 3.0 / 3.1 / 3.2 全部主要特性：

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

### 调用级测试（33 项）

1. 通过 `GLSurfaceView.setEGLContextClientVersion(2)` 创建 GLES 2.0 上下文
2. 在 GL 线程中直接调用 GLES 3.x API
3. 通过 `glGetError()` 判断调用是否被驱动接受

### 场景测试（11 项）

在调用级测试基础上，构建完整渲染管线并验证实际输出：

1. 编译链接完整的 VS+FS（或 CS/GS/TES）shader program
2. 渲染到离屏 FBO 或写入 buffer
3. 通过 `glReadPixels` / `glMapBufferRange` 回读结果
4. 与预期值比较，验证功能是否真正工作

#### GLES 3.0 场景（6 项）
| 场景 | 验证内容 |
|------|---------|
| 全屏纯色渲染 | 完整 VS+FS 管线产出正确像素 |
| MRT 多目标渲染 | FS 同时输出到 2 个 attachment，回读验证不同颜色 |
| Instanced 渲染 | `glDrawArraysInstanced` 在不同位置绘制多个实例 |
| Transform Feedback 数据捕获 | TF 捕获 VS 输出 varying，map 回读验证数据 |
| 3D 纹理采样 | 填充已知颜色到 3D 纹理，FS 采样后验证 |
| PBO 异步回读 | 渲染后通过 PBO + map 回读像素数据 |

#### GLES 3.1 场景（3 项）
| 场景 | 验证内容 |
|------|---------|
| Compute Shader SSBO 写入 | CS 写入 SSBO 数据，map 回读验证 |
| Image Store → Texture Sample | CS imageStore 写入纹理，FS texture() 采样验证 |
| Indirect Draw 渲染 | buffer 提供 draw command，indirect draw 后验证像素 |

#### GLES 3.2 场景（2 项）
| 场景 | 验证内容 |
|------|---------|
| Geometry Shader 放大 | 1 个点 → GS 生成全屏三角形，验证覆盖率 |
| Tessellation 细分渲染 | patch → TCS/TES 细分后渲染，验证像素覆盖 |

### 结果状态
- **PASS** — API 调用成功，`glGetError()` 返回 `GL_NO_ERROR`
- **FAIL** — API 调用返回了 GL 错误（如 `GL_INVALID_OPERATION`）
- **CRASH** — 调用过程中抛出异常

场景测试的 detail 字段额外包含验证结果：
- **VERIFIED** — 像素/数据回读与预期匹配，功能真正可用
- **MISMATCH** — API 没报错但输出不符合预期（驱动行为异常）

> 注意：调用级测试的 PASS 仅表示驱动接受了该 API 调用，不代表功能完全正确。场景测试的 PASS + VERIFIED 才表示功能真正可用。

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

| 设备 | GPU | Android | 调用级 | 场景 |
|------|-----|---------|--------|------|
| Pixel 9 | Mali-G715 | 16 | 33/33 PASS | — |
| vivo 1906 | Adreno 505 | 9 | 33/33 PASS | 11/11 PASS (VERIFIED) |

## 技术细节

- 纯 Java，无第三方依赖
- EGL 上下文版本：2.0
- 调用级测试中 draw call 类 API（`glDrawArraysInstanced`、`glDrawArraysIndirect`）不执行实际绘制，仅验证 API 入口点和 buffer 绑定，避免无 shader program 时触发驱动 SIGSEGV（已在 Adreno 505 上复现）。完整的 draw call 验证由场景测试负责
- Android SDK GLES32 Java binding 中 `glDebugMessageCallback` 等 debug 系列 API 未实现，Debug Output 测试仅验证 `glEnable/glIsEnabled`

## License

MIT
