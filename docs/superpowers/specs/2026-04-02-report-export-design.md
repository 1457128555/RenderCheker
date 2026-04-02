# 测试报告导出系统 - 设计规格

## 概述

在 GLES 兼容性测试器中加入报告导出功能：每次测试自动保存 JSON 报告到 App 私有目录，并提供分享按钮通过 Android Share Intent 发送报告文件。

## JSON 报告格式

```json
{
  "timestamp": "2026-04-02T14:19:20",
  "device": {
    "model": "Pixel 9",
    "android": "16",
    "gpu": "Mali-G715",
    "glVersion": "OpenGL ES 3.2 ...",
    "glslVersion": "OpenGL ES GLSL ES 3.20",
    "vendor": "ARM"
  },
  "summary": { "total": 33, "pass": 31, "fail": 1, "crash": 1 },
  "results": [
    {
      "name": "VAO (Vertex Array Object)",
      "apiLevel": "GLES 3.0",
      "status": "PASS",
      "glError": 0,
      "glErrorName": "GL_NO_ERROR",
      "detail": "..."
    }
  ]
}
```

## 文件存储

- 路径：`getExternalFilesDir(null)/reports/`（无需运行时权限）
- 文件名：`RenderTest_{设备型号}_{yyyyMMdd_HHmmss}.json`
- 每次测试完成自动保存

## 分享

- UI 加"分享报告"按钮
- 通过 FileProvider + Share Intent 分享最新 JSON 文件
- FileProvider authority: `com.rendertest.fileprovider`

## 变更文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `ReportGenerator.java` | 新增 | 生成 JSON、保存文件 |
| `TestRenderer.java` | 修改 | Callback 增加 onReportSaved 回调 |
| `MainActivity.java` | 修改 | 接收报告路径、分享按钮逻辑 |
| `activity_main.xml` | 修改 | 加分享按钮 |
| `AndroidManifest.xml` | 修改 | 加 FileProvider 声明 |
| `res/xml/file_paths.xml` | 新增 | FileProvider 路径配置 |
