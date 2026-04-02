# GLES 兼容性测试器 - 设计规格

## 概述

一个 Android 应用，初始化 GLES 2.0 上下文，然后在其中尝试调用 GLES 3.0/3.1/3.2 的 API，测试高版本特性在 2.0 上下文中是否可用，用于在不同物理设备上验证兼容性。

## 目标

- 验证 GLES 3.x API 能否在 GLES 2.0 EGL 上下文中正常工作
- 收集设备/GPU 信息，便于跨设备对比
- 测试结果同时在 App 内（UI）和 Logcat 中呈现
- 支持渐进式地添加新测试用例

## 技术栈

- **语言**: Java
- **构建**: Gradle (Groovy DSL)
- **minSdk**: 24 (Android 7.0)
- **targetSdk**: 34
- **依赖**: 无（纯 Android SDK）

## 架构

单 Activity 应用，包含以下核心组件：

```
MainActivity
  ├── DeviceInfoPanel     — 顶部区域，显示设备/GPU/驱动信息
  ├── TestResultListView  — 中部区域，可滚动的测试结果列表
  └── GLSurfaceView       — 底部或隐藏，承载 GLES 2.0 上下文
        └── TestRenderer   — 在 GL 线程中执行所有特性测试
```

### 核心流程

1. App 启动，创建 `GLSurfaceView`，调用 `setEGLContextClientVersion(2)` 初始化 GLES 2.0 上下文
2. `TestRenderer.onSurfaceCreated()` 中收集设备/GL 信息（vendor、renderer、version、extensions）
3. 按顺序执行各项特性测试，每项调用对应的 GLES 3.x API 并检查 `glGetError()`
4. 测试结果通过 `runOnUiThread()` 回调传回 UI 线程更新列表
5. 同时将详细日志输出到 Logcat

## 首批测试特性

选择原则：最能体现"2.0 上下文中调用 3.x API"兼容性差异的特性，最大化测试诊断价值。

### GLES 3.0（3 项测试）

| 测试项 | API | 选择理由 |
|--------|-----|----------|
| Instanced Rendering | `GLES30.glDrawArraysInstanced()` | 非常常用，测试价值高 |
| VAO (顶点数组对象) | `GLES30.glGenVertexArrays()` | 基础功能，很多 3.0 特性依赖它 |
| Uniform Buffer Objects | `GLES30.glBindBufferBase(GL_UNIFORM_BUFFER, ...)` | 数据传递方式的重大变化 |

### GLES 3.1（2 项测试）

| 测试项 | API | 选择理由 |
|--------|-----|----------|
| Compute Shader | `GLES31.glDispatchCompute()` | 3.1 最标志性的特性 |
| SSBO (着色器存储缓冲) | `GLES31.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ...)` | 常与 Compute Shader 配合使用 |

### GLES 3.2（2 项测试）

| 测试项 | API | 选择理由 |
|--------|-----|----------|
| Geometry Shader | `GLES32.glFramebufferTexture()` + 着色器编译 | 3.2 代表性特性 |
| Tessellation Shader | 创建 tess control/evaluation 着色器 | 验证着色器阶段支持 |

### 单项测试执行逻辑

1. 调用目标 API
2. 检查 `glGetError()` 返回值
3. 对于着色器类测试：编译对应类型的着色器，检查编译结果
4. 记录结果：`PASS`（API 正常工作）/ `FAIL`（GL error）/ `CRASH`（捕获到异常）

## 数据模型

### TestResult

```java
String name;          // 特性名称，如 "Instanced Rendering"
String apiLevel;      // "GLES 3.0" / "GLES 3.1" / "GLES 3.2"
String status;        // "PASS" / "FAIL" / "CRASH"
int glError;          // glGetError() 返回值
String detail;        // 着色器编译日志、异常信息等
```

### DeviceInfo

```java
String deviceModel;   // Build.MODEL
String androidVersion;// Build.VERSION.RELEASE
String glVendor;      // glGetString(GL_VENDOR)
String glRenderer;    // glGetString(GL_RENDERER)
String glVersion;     // glGetString(GL_VERSION)
String glslVersion;   // glGetString(GL_SHADING_LANGUAGE_VERSION)
String extensions;    // glGetString(GL_EXTENSIONS)
```

## UI 设计

- **顶部卡片**：设备信息摘要（型号、GPU、GL 版本）
- **中部列表**：按 GLES 版本分组（3.0 / 3.1 / 3.2），每项显示特性名 + 状态标签（绿色 PASS / 红色 FAIL / 黄色 CRASH）
- **点击展开**：显示详细的 glError 值和 detail 信息
- **底部**：`GLSurfaceView` 保持很小或透明，仅用于提供 GL 上下文

## Logcat 输出格式

```
TAG: "RenderTest"
I/RenderTest: === 设备信息 ===
I/RenderTest: 型号: Pixel 7, GPU: Adreno 730, GL: OpenGL ES 3.2
I/RenderTest: === GLES 3.0 测试 ===
I/RenderTest: [PASS] Instanced Rendering - glError: 0
I/RenderTest: [FAIL] VAO - glError: 1282 (GL_INVALID_OPERATION)
...
```

## 项目结构

```
app/src/main/java/com/rendertest/
  ├── MainActivity.java           — 入口，初始化 UI 和 GLSurfaceView
  ├── TestRenderer.java           — GLSurfaceView.Renderer，执行测试
  ├── model/
  │   ├── DeviceInfo.java         — 设备信息模型
  │   └── TestResult.java         — 测试结果模型
  ├── tests/
  │   ├── BaseGLTest.java         — 测试基类（公共逻辑：glGetError、异常捕获）
  │   ├── GLES30Tests.java        — GLES 3.0 特性测试集
  │   ├── GLES31Tests.java        — GLES 3.1 特性测试集
  │   └── GLES32Tests.java        — GLES 3.2 特性测试集
  └── ui/
      ├── DeviceInfoAdapter.java  — 设备信息展示
      └── TestResultAdapter.java  — 测试结果列表适配器
```

## 错误处理

- 每项测试用 `try-catch` 包裹，捕获任何运行时异常（如 `UnsupportedOperationException`），标记为 `CRASH`
- 测试前后都调用 `glGetError()` 清除和检查错误状态
- GL 线程与 UI 线程通信使用 `runOnUiThread()` 回调
- 单项测试失败不影响后续测试继续执行

## 后续扩展

测试框架支持渐进式扩展：
- 在现有 `GLES3xTests` 类中添加新测试方法
- 添加新的测试类覆盖更多特性组
- 后续可引入 EGL 上下文版本对比测试（2.0 vs 3.x）
