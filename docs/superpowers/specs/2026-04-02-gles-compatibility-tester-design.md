# GLES Compatibility Tester - Design Spec

## Overview

An Android app that initializes a GLES 2.0 context and attempts to call GLES 3.0/3.1/3.2 APIs within it, testing whether higher-version features work under a 2.0 context on various physical devices.

## Goals

- Verify whether GLES 3.x APIs can function in a GLES 2.0 EGL context
- Collect device/GPU info alongside test results for cross-device comparison
- Present results both in-app (UI) and via Logcat
- Support incremental addition of new test cases over time

## Tech Stack

- **Language**: Java
- **Build**: Gradle (Groovy DSL)
- **minSdk**: 24 (Android 7.0)
- **targetSdk**: 34
- **Dependencies**: None (pure Android SDK)

## Architecture

Single Activity app with the following components:

```
MainActivity
  ├── DeviceInfoPanel     — Top area, device/GPU/driver info
  ├── TestResultListView  — Middle area, scrollable test results
  └── GLSurfaceView       — Bottom/hidden, hosts GLES 2.0 context
        └── TestRenderer   — Runs all feature tests on GL thread
```

### Core Flow

1. App starts, creates `GLSurfaceView` with `setEGLContextClientVersion(2)`
2. `TestRenderer.onSurfaceCreated()` collects device/GL info (vendor, renderer, version, extensions)
3. Executes feature tests sequentially, each calling the corresponding GLES 3.x API and checking `glGetError()`
4. Results are passed back to UI thread via `runOnUiThread()` callback
5. Detailed logs are written to Logcat simultaneously

## First Batch of Test Features

Selected for maximum diagnostic value — features most likely to reveal meaningful compatibility differences across devices.

### GLES 3.0 (3 tests)

| Test | API | Why |
|------|-----|-----|
| Instanced Rendering | `GLES30.glDrawArraysInstanced()` | Very common, high test value |
| VAO | `GLES30.glGenVertexArrays()` | Foundational, many 3.0 features depend on it |
| Uniform Buffer Objects | `GLES30.glBindBufferBase(GL_UNIFORM_BUFFER, ...)` | Major change in data passing |

### GLES 3.1 (2 tests)

| Test | API | Why |
|------|-----|-----|
| Compute Shader | `GLES31.glDispatchCompute()` | Most iconic 3.1 feature |
| SSBO | `GLES31.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ...)` | Commonly paired with compute |

### GLES 3.2 (2 tests)

| Test | API | Why |
|------|-----|-----|
| Geometry Shader | `GLES32.glFramebufferTexture()` + shader compilation | Representative 3.2 feature |
| Tessellation Shader | Create tess control/evaluation shaders | Verify shader stage support |

### Per-Test Execution Logic

1. Call the target API
2. Check `glGetError()` return value
3. For shader tests: compile the corresponding shader type, check compilation result
4. Record result: `PASS` (API works) / `FAIL` (GL error) / `CRASH` (exception caught)

## Data Models

### TestResult

```java
String name;          // Feature name, e.g. "Instanced Rendering"
String apiLevel;      // "GLES 3.0" / "GLES 3.1" / "GLES 3.2"
String status;        // "PASS" / "FAIL" / "CRASH"
int glError;          // glGetError() return value
String detail;        // Shader compile log, exception message, etc.
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

## UI Design

- **Top card**: Device info summary (model, GPU, GL version)
- **Middle list**: Test results grouped by GLES version (3.0 / 3.1 / 3.2), each showing feature name + status badge (green PASS / red FAIL / yellow CRASH)
- **Tap to expand**: Shows detailed glError value and detail message
- **Bottom**: `GLSurfaceView` kept small or transparent, exists only to provide GL context

## Logcat Output Format

```
TAG: "RenderTest"
I/RenderTest: === Device Info ===
I/RenderTest: Model: Pixel 7, GPU: Adreno 730, GL: OpenGL ES 3.2
I/RenderTest: === GLES 3.0 Tests ===
I/RenderTest: [PASS] Instanced Rendering - glError: 0
I/RenderTest: [FAIL] VAO - glError: 1282 (GL_INVALID_OPERATION)
...
```

## Project Structure

```
app/src/main/java/com/rendertest/
  ├── MainActivity.java           — Entry point, UI + GLSurfaceView setup
  ├── TestRenderer.java           — GLSurfaceView.Renderer, executes tests
  ├── model/
  │   ├── DeviceInfo.java         — Device info model
  │   └── TestResult.java         — Test result model
  ├── tests/
  │   ├── BaseGLTest.java         — Base class (shared: glGetError, try-catch)
  │   ├── GLES30Tests.java        — GLES 3.0 feature tests
  │   ├── GLES31Tests.java        — GLES 3.1 feature tests
  │   └── GLES32Tests.java        — GLES 3.2 feature tests
  └── ui/
      ├── DeviceInfoAdapter.java  — Device info display
      └── TestResultAdapter.java  — Test result list adapter
```

## Error Handling

- Each test wrapped in `try-catch` to capture runtime exceptions (e.g. `UnsupportedOperationException`), marked as `CRASH`
- `glGetError()` called before and after each test to clear and check error state
- GL thread to UI thread communication via `runOnUiThread()` callback
- Single test failure does not block subsequent tests

## Future Expansion

The test framework is designed for incremental growth:
- Add new test methods to existing `GLES3xTests` classes
- Add new test classes for additional feature groups
- Potentially add EGL context version comparison (2.0 vs 3.x) in later phases
