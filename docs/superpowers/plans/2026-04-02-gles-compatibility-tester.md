# GLES 兼容性测试器 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建一个 Android App，初始化 GLES 2.0 上下文并测试 GLES 3.0/3.1/3.2 特性的兼容性。

**Architecture:** 单 Activity 应用，使用 GLSurfaceView 承载 GLES 2.0 上下文，TestRenderer 在 GL 线程中执行特性测试，结果通过回调传回 UI 线程展示在列表中。测试类按 GLES 版本分组，共享 BaseGLTest 基类处理错误检查。

**Tech Stack:** Java, Android SDK (minSdk 24, targetSdk 34), Gradle Groovy DSL, 无第三方依赖

---

## File Structure

```
RenderTest/
  build.gradle                                    — 根项目 Gradle 配置
  settings.gradle                                 — 项目设置
  gradle.properties                               — Gradle 属性
  app/
    build.gradle                                  — App 模块 Gradle 配置
    src/main/
      AndroidManifest.xml                         — 应用清单
      java/com/rendertest/
        MainActivity.java                         — 入口 Activity，UI + GLSurfaceView
        TestRenderer.java                         — GL 渲染器，执行测试并回调结果
        model/
          DeviceInfo.java                         — 设备/GL 信息模型
          TestResult.java                         — 测试结果模型
        tests/
          BaseGLTest.java                         — 测试基类（glGetError、异常捕获）
          GLES30Tests.java                        — GLES 3.0 测试集
          GLES31Tests.java                        — GLES 3.1 测试集
          GLES32Tests.java                        — GLES 3.2 测试集
        ui/
          TestResultAdapter.java                  — 测试结果列表适配器（含分组和展开）
      res/
        layout/
          activity_main.xml                       — 主布局
          item_test_result.xml                    — 测试结果列表项
        values/
          strings.xml                             — 字符串资源
          colors.xml                              — 颜色资源（PASS/FAIL/CRASH）
          themes.xml                              — 主题
```

---

### Task 1: Android 项目脚手架

**Files:**
- Create: `build.gradle`
- Create: `settings.gradle`
- Create: `gradle.properties`
- Create: `app/build.gradle`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/themes.xml`

- [ ] **Step 1: 创建根项目 build.gradle**

```groovy
// build.gradle
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.2.0'
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
```

- [ ] **Step 2: 创建 settings.gradle**

```groovy
// settings.gradle
rootProject.name = 'RenderTest'
include ':app'
```

- [ ] **Step 3: 创建 gradle.properties**

```properties
# gradle.properties
android.useAndroidX=true
org.gradle.jvmargs=-Xmx2048m
```

- [ ] **Step 4: 创建 app/build.gradle**

```groovy
// app/build.gradle
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.rendertest'
    compileSdk 34

    defaultConfig {
        applicationId "com.rendertest"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'androidx.cardview:cardview:1.0.0'
}
```

- [ ] **Step 5: 创建 AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:glEsVersion="0x00020000" android:required="true" />

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:theme="@style/Theme.RenderTest">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 6: 创建资源文件**

`app/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">RenderTest</string>
</resources>
```

`app/src/main/res/values/colors.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="pass_green">#4CAF50</color>
    <color name="fail_red">#F44336</color>
    <color name="crash_yellow">#FF9800</color>
    <color name="card_background">#FFFFFF</color>
    <color name="text_primary">#212121</color>
    <color name="text_secondary">#757575</color>
    <color name="divider">#BDBDBD</color>
    <color name="background">#F5F5F5</color>
</resources>
```

`app/src/main/res/values/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.RenderTest" parent="Theme.AppCompat.Light.NoActionBar">
        <item name="android:windowBackground">@color/background</item>
        <item name="android:statusBarColor">@color/text_primary</item>
    </style>
</resources>
```

- [ ] **Step 7: 安装 Gradle Wrapper 并验证构建**

```bash
# 下载 gradle wrapper（如果系统有 gradle）
gradle wrapper --gradle-version 8.2
# 或者手动创建 gradle/wrapper 目录和文件

# 验证项目可以配置成功
./gradlew tasks
```

- [ ] **Step 8: 提交**

```bash
git add build.gradle settings.gradle gradle.properties app/build.gradle \
  app/src/main/AndroidManifest.xml app/src/main/res/values/ \
  gradle/ gradlew gradlew.bat
git commit -m "feat: 初始化 Android 项目脚手架"
```

---

### Task 2: 数据模型

**Files:**
- Create: `app/src/main/java/com/rendertest/model/TestResult.java`
- Create: `app/src/main/java/com/rendertest/model/DeviceInfo.java`

- [ ] **Step 1: 创建 TestResult.java**

```java
// app/src/main/java/com/rendertest/model/TestResult.java
package com.rendertest.model;

public class TestResult {
    public static final String STATUS_PASS = "PASS";
    public static final String STATUS_FAIL = "FAIL";
    public static final String STATUS_CRASH = "CRASH";

    private final String name;
    private final String apiLevel;
    private final String status;
    private final int glError;
    private final String detail;

    public TestResult(String name, String apiLevel, String status, int glError, String detail) {
        this.name = name;
        this.apiLevel = apiLevel;
        this.status = status;
        this.glError = glError;
        this.detail = detail;
    }

    public String getName() { return name; }
    public String getApiLevel() { return apiLevel; }
    public String getStatus() { return status; }
    public int getGlError() { return glError; }
    public String getDetail() { return detail; }

    public String getGlErrorName() {
        switch (glError) {
            case 0x0000: return "GL_NO_ERROR";
            case 0x0500: return "GL_INVALID_ENUM";
            case 0x0501: return "GL_INVALID_VALUE";
            case 0x0502: return "GL_INVALID_OPERATION";
            case 0x0505: return "GL_OUT_OF_MEMORY";
            case 0x0506: return "GL_INVALID_FRAMEBUFFER_OPERATION";
            default: return "0x" + Integer.toHexString(glError);
        }
    }
}
```

- [ ] **Step 2: 创建 DeviceInfo.java**

```java
// app/src/main/java/com/rendertest/model/DeviceInfo.java
package com.rendertest.model;

import android.opengl.GLES20;
import android.os.Build;

public class DeviceInfo {
    private final String deviceModel;
    private final String androidVersion;
    private String glVendor;
    private String glRenderer;
    private String glVersion;
    private String glslVersion;
    private String extensions;

    public DeviceInfo() {
        this.deviceModel = Build.MODEL;
        this.androidVersion = Build.VERSION.RELEASE;
    }

    /** 必须在 GL 线程中调用 */
    public void collectGLInfo() {
        this.glVendor = GLES20.glGetString(GLES20.GL_VENDOR);
        this.glRenderer = GLES20.glGetString(GLES20.GL_RENDERER);
        this.glVersion = GLES20.glGetString(GLES20.GL_VERSION);
        this.glslVersion = GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION);
        this.extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
    }

    public String getDeviceModel() { return deviceModel; }
    public String getAndroidVersion() { return androidVersion; }
    public String getGlVendor() { return glVendor; }
    public String getGlRenderer() { return glRenderer; }
    public String getGlVersion() { return glVersion; }
    public String getGlslVersion() { return glslVersion; }
    public String getExtensions() { return extensions; }

    public String getSummary() {
        return deviceModel + " | Android " + androidVersion + "\n"
             + "GPU: " + glRenderer + "\n"
             + "GL: " + glVersion;
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/rendertest/model/
git commit -m "feat: 添加 TestResult 和 DeviceInfo 数据模型"
```

---

### Task 3: 测试基类

**Files:**
- Create: `app/src/main/java/com/rendertest/tests/BaseGLTest.java`

- [ ] **Step 1: 创建 BaseGLTest.java**

```java
// app/src/main/java/com/rendertest/tests/BaseGLTest.java
package com.rendertest.tests;

import android.opengl.GLES20;
import android.util.Log;

import com.rendertest.model.TestResult;

public abstract class BaseGLTest {
    private static final String TAG = "RenderTest";

    protected final String apiLevel;

    protected BaseGLTest(String apiLevel) {
        this.apiLevel = apiLevel;
    }

    /** 子类实现：返回本测试集的所有测试结果 */
    public abstract java.util.List<TestResult> runAll();

    /**
     * 执行单项测试：清除 GL 错误 -> 运行测试体 -> 检查 GL 错误/异常
     */
    protected TestResult runTest(String name, TestBody body) {
        // 清除之前的 GL 错误
        while (GLES20.glGetError() != GLES20.GL_NO_ERROR) { /* drain */ }

        try {
            String detail = body.execute();
            int error = GLES20.glGetError();

            String status;
            if (error == GLES20.GL_NO_ERROR) {
                status = TestResult.STATUS_PASS;
            } else {
                status = TestResult.STATUS_FAIL;
            }

            TestResult result = new TestResult(name, apiLevel, status, error, detail);
            logResult(result);
            return result;

        } catch (Exception e) {
            TestResult result = new TestResult(name, apiLevel, TestResult.STATUS_CRASH, 0,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            logResult(result);
            return result;
        }
    }

    private void logResult(TestResult result) {
        String msg = "[" + result.getStatus() + "] " + result.getName()
                + " - glError: " + result.getGlError() + " (" + result.getGlErrorName() + ")";
        if (result.getDetail() != null && !result.getDetail().isEmpty()) {
            msg += " | " + result.getDetail();
        }
        Log.i(TAG, msg);
    }

    /** 编译着色器并返回编译日志。编译失败不会抛异常，而是通过 glGetError 和日志体现 */
    protected String compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) {
            return "glCreateShader returned 0";
        }
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        String log = GLES20.glGetShaderInfoLog(shader);
        GLES20.glDeleteShader(shader);

        if (compiled[0] == 0) {
            return "Compile FAILED: " + log;
        }
        return "Compile OK" + (log.isEmpty() ? "" : " | " + log);
    }

    @FunctionalInterface
    protected interface TestBody {
        /** 执行测试逻辑，返回详情字符串 */
        String execute();
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/rendertest/tests/BaseGLTest.java
git commit -m "feat: 添加 GL 测试基类 BaseGLTest"
```

---

### Task 4: GLES 3.0 测试集

**Files:**
- Create: `app/src/main/java/com/rendertest/tests/GLES30Tests.java`

- [ ] **Step 1: 创建 GLES30Tests.java**

```java
// app/src/main/java/com/rendertest/tests/GLES30Tests.java
package com.rendertest.tests;

import android.opengl.GLES20;
import android.opengl.GLES30;

import com.rendertest.model.TestResult;

import java.util.ArrayList;
import java.util.List;

public class GLES30Tests extends BaseGLTest {

    public GLES30Tests() {
        super("GLES 3.0");
    }

    @Override
    public List<TestResult> runAll() {
        List<TestResult> results = new ArrayList<>();
        results.add(testVAO());
        results.add(testInstancedRendering());
        results.add(testUniformBufferObject());
        return results;
    }

    private TestResult testVAO() {
        return runTest("VAO (Vertex Array Object)", () -> {
            int[] vaos = new int[1];
            GLES30.glGenVertexArrays(1, vaos, 0);
            int vao = vaos[0];
            if (vao == 0) {
                return "glGenVertexArrays returned 0";
            }
            GLES30.glBindVertexArray(vao);
            GLES30.glBindVertexArray(0);
            GLES30.glDeleteVertexArrays(1, vaos, 0);
            return "VAO id=" + vao + ", bindVAO/unbindVAO/deleteVAO 均成功";
        });
    }

    private TestResult testInstancedRendering() {
        return runTest("Instanced Rendering", () -> {
            // 创建一个最小的 VBO + VAO 来测试 glDrawArraysInstanced
            int[] vaos = new int[1];
            GLES30.glGenVertexArrays(1, vaos, 0);
            GLES30.glBindVertexArray(vaos[0]);

            int[] buffers = new int[1];
            GLES20.glGenBuffers(1, buffers, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);

            float[] vertices = {0.0f, 0.0f, 0.0f};
            java.nio.FloatBuffer fb = java.nio.ByteBuffer
                    .allocateDirect(vertices.length * 4)
                    .order(java.nio.ByteOrder.nativeOrder())
                    .asFloatBuffer();
            fb.put(vertices).position(0);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.length * 4, fb, GLES20.GL_STATIC_DRAW);

            GLES20.glEnableVertexAttribArray(0);
            GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 0, 0);

            // 关键调用：instanced draw
            GLES30.glDrawArraysInstanced(GLES20.GL_POINTS, 0, 1, 4);

            // 清理
            GLES20.glDisableVertexAttribArray(0);
            GLES20.glDeleteBuffers(1, buffers, 0);
            GLES30.glBindVertexArray(0);
            GLES30.glDeleteVertexArrays(1, vaos, 0);

            return "glDrawArraysInstanced(GL_POINTS, 0, 1, 4) 调用完成";
        });
    }

    private TestResult testUniformBufferObject() {
        return runTest("Uniform Buffer Object", () -> {
            int[] buffers = new int[1];
            GLES20.glGenBuffers(1, buffers, 0);
            int ubo = buffers[0];
            if (ubo == 0) {
                return "glGenBuffers returned 0";
            }

            GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, 0, ubo);

            // 分配 64 字节
            GLES20.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, ubo);
            GLES20.glBufferData(GLES30.GL_UNIFORM_BUFFER, 64, null, GLES20.GL_DYNAMIC_DRAW);

            // 清理
            GLES20.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, 0);
            GLES20.glDeleteBuffers(1, buffers, 0);

            return "UBO id=" + ubo + ", bindBufferBase/bufferData/delete 均成功";
        });
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/rendertest/tests/GLES30Tests.java
git commit -m "feat: 添加 GLES 3.0 测试集（VAO、Instanced Rendering、UBO）"
```

---

### Task 5: GLES 3.1 测试集

**Files:**
- Create: `app/src/main/java/com/rendertest/tests/GLES31Tests.java`

- [ ] **Step 1: 创建 GLES31Tests.java**

```java
// app/src/main/java/com/rendertest/tests/GLES31Tests.java
package com.rendertest.tests;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLES31;

import com.rendertest.model.TestResult;

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

            // 尝试创建 program 并 dispatch
            int shader = GLES20.glCreateShader(GLES31.GL_COMPUTE_SHADER);
            if (shader == 0) {
                return "glCreateShader(GL_COMPUTE_SHADER) returned 0";
            }
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);

            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(shader);
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

            // 清理
            GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, 0);
            GLES20.glDeleteBuffers(1, buffers, 0);

            return "SSBO id=" + ssbo + ", bindBufferBase(GL_SHADER_STORAGE_BUFFER)/bufferData/delete 均成功";
        });
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/rendertest/tests/GLES31Tests.java
git commit -m "feat: 添加 GLES 3.1 测试集（Compute Shader、SSBO）"
```

---

### Task 6: GLES 3.2 测试集

**Files:**
- Create: `app/src/main/java/com/rendertest/tests/GLES32Tests.java`

- [ ] **Step 1: 创建 GLES32Tests.java**

```java
// app/src/main/java/com/rendertest/tests/GLES32Tests.java
package com.rendertest.tests;

import android.opengl.GLES20;
import android.opengl.GLES32;

import com.rendertest.model.TestResult;

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

            // 清理
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
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/rendertest/tests/GLES32Tests.java
git commit -m "feat: 添加 GLES 3.2 测试集（Geometry Shader、Tessellation Shader）"
```

---

### Task 7: TestRenderer（GL 渲染器）

**Files:**
- Create: `app/src/main/java/com/rendertest/TestRenderer.java`

- [ ] **Step 1: 创建 TestRenderer.java**

```java
// app/src/main/java/com/rendertest/TestRenderer.java
package com.rendertest;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.rendertest.model.DeviceInfo;
import com.rendertest.model.TestResult;
import com.rendertest.tests.GLES30Tests;
import com.rendertest.tests.GLES31Tests;
import com.rendertest.tests.GLES32Tests;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class TestRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "RenderTest";

    public interface Callback {
        void onDeviceInfo(DeviceInfo info);
        void onTestResults(List<TestResult> results);
    }

    private final Callback callback;
    private boolean testsExecuted = false;

    public TestRenderer(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        if (testsExecuted) return;
        testsExecuted = true;

        // 收集设备信息
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.collectGLInfo();
        logDeviceInfo(deviceInfo);
        callback.onDeviceInfo(deviceInfo);

        // 执行所有测试
        Log.i(TAG, "=== GLES 3.0 测试 ===");
        List<TestResult> allResults = new ArrayList<>();
        allResults.addAll(new GLES30Tests().runAll());

        Log.i(TAG, "=== GLES 3.1 测试 ===");
        allResults.addAll(new GLES31Tests().runAll());

        Log.i(TAG, "=== GLES 3.2 测试 ===");
        allResults.addAll(new GLES32Tests().runAll());

        callback.onTestResults(allResults);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    private void logDeviceInfo(DeviceInfo info) {
        Log.i(TAG, "=== 设备信息 ===");
        Log.i(TAG, "型号: " + info.getDeviceModel());
        Log.i(TAG, "Android: " + info.getAndroidVersion());
        Log.i(TAG, "GPU: " + info.getGlRenderer());
        Log.i(TAG, "GL: " + info.getGlVersion());
        Log.i(TAG, "GLSL: " + info.getGlslVersion());
        Log.i(TAG, "Vendor: " + info.getGlVendor());
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/rendertest/TestRenderer.java
git commit -m "feat: 添加 TestRenderer，在 GL 线程执行测试并回调结果"
```

---

### Task 8: UI — 布局文件

**Files:**
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/layout/item_test_result.xml`

- [ ] **Step 1: 创建 activity_main.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/layout/activity_main.xml -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/background">

    <!-- 设备信息卡片 -->
    <androidx.cardview.widget.CardView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_margin="12dp"
        app:cardCornerRadius="8dp"
        app:cardElevation="2dp"
        xmlns:app="http://schemas.android.com/apk/res-auto">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="设备信息"
                android:textSize="16sp"
                android:textStyle="bold"
                android:textColor="@color/text_primary" />

            <TextView
                android:id="@+id/tv_device_info"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:text="正在初始化 GL 上下文..."
                android:textSize="13sp"
                android:textColor="@color/text_secondary"
                android:fontFamily="monospace" />
        </LinearLayout>
    </androidx.cardview.widget.CardView>

    <!-- EGL 上下文版本提示 -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingHorizontal="16dp"
        android:paddingBottom="4dp"
        android:text="EGL 上下文: GLES 2.0 | 测试 3.x API 兼容性"
        android:textSize="12sp"
        android:textColor="@color/text_secondary" />

    <!-- 测试结果列表 -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_test_results"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:paddingHorizontal="12dp" />

    <!-- 隐藏的 GLSurfaceView -->
    <android.opengl.GLSurfaceView
        android:id="@+id/gl_surface_view"
        android:layout_width="1dp"
        android:layout_height="1dp"
        android:visibility="visible" />

</LinearLayout>
```

- [ ] **Step 2: 创建 item_test_result.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/layout/item_test_result.xml -->
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="6dp"
    app:cardCornerRadius="6dp"
    app:cardElevation="1dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="12dp">

        <!-- 第一行：特性名 + 状态标签 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical">

            <TextView
                android:id="@+id/tv_test_name"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:textSize="14sp"
                android:textColor="@color/text_primary"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tv_status"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:paddingHorizontal="10dp"
                android:paddingVertical="2dp"
                android:textSize="12sp"
                android:textColor="#FFFFFF"
                android:textStyle="bold" />
        </LinearLayout>

        <!-- 详情区域（默认隐藏，点击展开） -->
        <LinearLayout
            android:id="@+id/layout_detail"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:layout_marginTop="8dp"
            android:visibility="gone">

            <TextView
                android:id="@+id/tv_gl_error"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textSize="12sp"
                android:textColor="@color/text_secondary"
                android:fontFamily="monospace" />

            <TextView
                android:id="@+id/tv_detail"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:textSize="12sp"
                android:textColor="@color/text_secondary"
                android:fontFamily="monospace" />
        </LinearLayout>
    </LinearLayout>
</androidx.cardview.widget.CardView>
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/res/layout/
git commit -m "feat: 添加主布局和测试结果列表项布局"
```

---

### Task 9: UI — TestResultAdapter

**Files:**
- Create: `app/src/main/java/com/rendertest/ui/TestResultAdapter.java`

- [ ] **Step 1: 创建 TestResultAdapter.java**

```java
// app/src/main/java/com/rendertest/ui/TestResultAdapter.java
package com.rendertest.ui;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rendertest.R;
import com.rendertest.model.TestResult;

import java.util.ArrayList;
import java.util.List;

public class TestResultAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final List<Object> items = new ArrayList<>();

    public void setResults(List<TestResult> results) {
        items.clear();

        String currentLevel = "";
        for (TestResult r : results) {
            if (!r.getApiLevel().equals(currentLevel)) {
                currentLevel = r.getApiLevel();
                items.add(currentLevel); // 分组标题
            }
            items.add(r);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            TextView tv = new TextView(parent.getContext());
            tv.setTextSize(14);
            tv.setTextColor(parent.getContext().getColor(R.color.text_secondary));
            tv.setPadding(8, 24, 8, 8);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            return new HeaderHolder(tv);
        }
        View view = inflater.inflate(R.layout.item_test_result, parent, false);
        return new ItemHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).textView.setText((String) items.get(position));
        } else {
            ((ItemHolder) holder).bind((TestResult) items.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView textView;
        HeaderHolder(TextView tv) {
            super(tv);
            this.textView = tv;
        }
    }

    static class ItemHolder extends RecyclerView.ViewHolder {
        final TextView tvName, tvStatus, tvGlError, tvDetail;
        final LinearLayout layoutDetail;

        ItemHolder(View v) {
            super(v);
            tvName = v.findViewById(R.id.tv_test_name);
            tvStatus = v.findViewById(R.id.tv_status);
            tvGlError = v.findViewById(R.id.tv_gl_error);
            tvDetail = v.findViewById(R.id.tv_detail);
            layoutDetail = v.findViewById(R.id.layout_detail);
        }

        void bind(TestResult result) {
            tvName.setText(result.getName());
            tvStatus.setText(result.getStatus());

            // 状态标签背景色
            int color;
            switch (result.getStatus()) {
                case TestResult.STATUS_PASS:
                    color = itemView.getContext().getColor(R.color.pass_green);
                    break;
                case TestResult.STATUS_FAIL:
                    color = itemView.getContext().getColor(R.color.fail_red);
                    break;
                default:
                    color = itemView.getContext().getColor(R.color.crash_yellow);
                    break;
            }
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(color);
            bg.setCornerRadius(12);
            tvStatus.setBackground(bg);

            // 详情
            tvGlError.setText("glError: " + result.getGlError() + " (" + result.getGlErrorName() + ")");
            tvDetail.setText(result.getDetail());

            // 点击展开/收起
            layoutDetail.setVisibility(View.GONE);
            itemView.setOnClickListener(v -> {
                boolean visible = layoutDetail.getVisibility() == View.VISIBLE;
                layoutDetail.setVisibility(visible ? View.GONE : View.VISIBLE);
            });
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/rendertest/ui/TestResultAdapter.java
git commit -m "feat: 添加 TestResultAdapter，支持分组和展开详情"
```

---

### Task 10: MainActivity — 整合所有组件

**Files:**
- Create: `app/src/main/java/com/rendertest/MainActivity.java`

- [ ] **Step 1: 创建 MainActivity.java**

```java
// app/src/main/java/com/rendertest/MainActivity.java
package com.rendertest;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rendertest.model.DeviceInfo;
import com.rendertest.model.TestResult;
import com.rendertest.ui.TestResultAdapter;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private GLSurfaceView glSurfaceView;
    private TextView tvDeviceInfo;
    private TestResultAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvDeviceInfo = findViewById(R.id.tv_device_info);

        RecyclerView rvResults = findViewById(R.id.rv_test_results);
        adapter = new TestResultAdapter();
        rvResults.setLayoutManager(new LinearLayoutManager(this));
        rvResults.setAdapter(adapter);

        glSurfaceView = findViewById(R.id.gl_surface_view);
        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setRenderer(new TestRenderer(new TestRenderer.Callback() {
            @Override
            public void onDeviceInfo(DeviceInfo info) {
                runOnUiThread(() -> tvDeviceInfo.setText(info.getSummary()));
            }

            @Override
            public void onTestResults(List<TestResult> results) {
                runOnUiThread(() -> adapter.setResults(results));
            }
        }));
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        glSurfaceView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause();
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/rendertest/MainActivity.java
git commit -m "feat: 添加 MainActivity，整合 GL 测试与 UI 展示"
```

---

### Task 11: 构建验证

- [ ] **Step 1: 执行 Gradle 构建**

```bash
./gradlew assembleDebug
```

预期：BUILD SUCCESSFUL，生成 `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 2: 修复构建问题（如有）**

检查编译错误，逐一修复。

- [ ] **Step 3: 提交修复（如有）**

```bash
git add -A
git commit -m "fix: 修复构建问题"
```
