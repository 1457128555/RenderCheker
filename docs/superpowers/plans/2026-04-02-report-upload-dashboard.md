# Report Upload & Web Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add automatic report uploading from the Android app to a remote Flask server, and build a web dashboard for aggregated report viewing across devices.

**Architecture:** App side adds OkHttp for async POST of the existing JSON report after test completion. Server side is a lightweight Flask app with SQLite storage and an HTML dashboard. Two independent parts: first the app changes, then the server.

**Tech Stack:** Java (Android), OkHttp 4.12, Python 3, Flask, SQLite

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/AndroidManifest.xml` | Modify | Add INTERNET permission |
| `app/build.gradle` | Modify | Add OkHttp dependency |
| `app/src/main/java/com/rendertest/ReportGenerator.java` | Modify | Return JSON string alongside file |
| `app/src/main/java/com/rendertest/ReportUploader.java` | **Create** | HTTP POST upload logic |
| `app/src/main/java/com/rendertest/MainActivity.java` | Modify | Call uploader after report generation |
| `server/requirements.txt` | **Create** | Flask dependency |
| `server/server.py` | **Create** | Flask app: API + dashboard logic |
| `server/templates/index.html` | **Create** | Web dashboard template |

---

## Task 1: Android App — Add Internet Permission & OkHttp Dependency

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle`

- [ ] **Step 1: Add INTERNET permission to AndroidManifest.xml**

Insert `<uses-permission>` before `<application>`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:glEsVersion="0x00020000" android:required="true" />
    <uses-permission android:name="android.permission.INTERNET" />

    <application
```

- [ ] **Step 2: Add OkHttp dependency to app/build.gradle**

Add to `dependencies` block:

```gradle
dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'androidx.cardview:cardview:1.0.0'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/build.gradle
git commit -m "feat: add INTERNET permission and OkHttp dependency"
```

---

## Task 2: Android App — Modify ReportGenerator to Return JSON

**Files:**
- Modify: `app/src/main/java/com/rendertest/ReportGenerator.java`

- [ ] **Step 1: Refactor `generateAndSave()` to return both JSON string and file**

Replace the existing `generateAndSave` method. The method should return the JSON string (for upload use), while keeping the file-saving behavior. Add a new static inner result class:

```java
public class ReportGenerator {
    private static final String TAG = "RenderTest";

    public static class Result {
        public final File file;
        public final String json;

        Result(File file, String json) {
            this.file = file;
            this.json = json;
        }
    }

    public static Result generateAndSave(Context context, DeviceInfo deviceInfo, List<TestResult> results) {
        try {
            String json = generateJson(deviceInfo, results);
            File file = saveToFile(context, deviceInfo, json);
            return new Result(file, json);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate report", e);
            return null;
        }
    }

    // generateJson() and saveToFile() remain unchanged
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/rendertest/ReportGenerator.java
git commit -m "refactor: return JSON string from ReportGenerator for upload use"
```

---

## Task 3: Android App — Create ReportUploader

**Files:**
- Create: `app/src/main/java/com/rendertest/ReportUploader.java`

- [ ] **Step 1: Write ReportUploader.java**

```java
package com.rendertest;

import android.util.Log;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.concurrent.TimeUnit;

public class ReportUploader {
    private static final String TAG = "RenderTest";
    private static final String SERVER_URL = "http://10.0.2.2:5000/api/report";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public static void upload(String jsonReport) {
        RequestBody body = RequestBody.create(
                jsonReport, MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(SERVER_URL)
                .post(body)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                Log.w(TAG, "Report upload failed: " + e.getMessage());
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) {
                if (response.isSuccessful()) {
                    Log.i(TAG, "Report uploaded successfully");
                } else {
                    Log.w(TAG, "Report upload returned: " + response.code());
                }
                response.close();
            }
        });
    }
}
```

Note: `SERVER_URL` uses `10.0.2.2` (Android emulator localhost alias). Change to actual server IP for real devices.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/rendertest/ReportUploader.java
git commit -m "feat: add ReportUploader for async report upload via OkHttp"
```

---

## Task 4: Android App — Wire Up Upload in MainActivity

**Files:**
- Modify: `app/src/main/java/com/rendertest/MainActivity.java`

- [ ] **Step 1: Update `onTestResults` callback to use new `ReportGenerator.Result` and trigger upload**

Replace the `onTestResults` method:

```java
@Override
public void onTestResults(DeviceInfo deviceInfo, List<TestResult> results) {
    ReportGenerator.Result result = ReportGenerator.generateAndSave(MainActivity.this, deviceInfo, results);

    runOnUiThread(() -> {
        adapter.setResults(results);
        if (result != null && result.file != null) {
            lastReportFile = result.file;
            btnShare.setVisibility(View.VISIBLE);
            Toast.makeText(MainActivity.this,
                    "报告已保存: " + result.file.getName(), Toast.LENGTH_SHORT).show();
        }
    });

    if (result != null && result.json != null) {
        ReportUploader.upload(result.json);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/rendertest/MainActivity.java
git commit -m "feat: auto-upload report to server after test completion"
```

---

## Task 5: Server — Create Flask App with API Endpoints

**Files:**
- Create: `server/requirements.txt`
- Create: `server/server.py`

- [ ] **Step 1: Create requirements.txt**

```
flask
```

- [ ] **Step 2: Create server.py**

```python
import json
import os
import sqlite3

from flask import Flask, request, jsonify, render_template

app = Flask(__name__)

DB_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
DB_PATH = os.path.join(DB_DIR, "reports.db")


def get_db():
    os.makedirs(DB_DIR, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    conn = get_db()
    conn.execute("""
        CREATE TABLE IF NOT EXISTS reports (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_model TEXT,
            android_version TEXT,
            gpu TEXT,
            gl_version TEXT,
            glsl_version TEXT,
            vendor TEXT,
            total INTEGER,
            pass INTEGER,
            fail INTEGER,
            crash INTEGER,
            results_json TEXT,
            uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    conn.commit()
    conn.close()


@app.route("/api/report", methods=["POST"])
def receive_report():
    try:
        data = request.get_json(force=True)
    except Exception:
        return jsonify({"error": "Invalid JSON"}), 400

    device = data.get("device", {})
    summary = data.get("summary", {})
    results = data.get("results", [])

    conn = get_db()
    conn.execute(
        """INSERT INTO reports
           (device_model, android_version, gpu, gl_version, glsl_version, vendor,
            total, pass, fail, crash, results_json)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        (
            device.get("model", ""),
            device.get("android", ""),
            device.get("gpu", ""),
            device.get("glVersion", ""),
            device.get("glslVersion", ""),
            device.get("vendor", ""),
            summary.get("total", 0),
            summary.get("pass", 0),
            summary.get("fail", 0),
            summary.get("crash", 0),
            json.dumps(results, ensure_ascii=False),
        ),
    )
    conn.commit()
    conn.close()

    return jsonify({"status": "ok"}), 200


@app.route("/api/reports", methods=["GET"])
def list_reports():
    conn = get_db()
    rows = conn.execute(
        "SELECT * FROM reports ORDER BY uploaded_at DESC"
    ).fetchall()
    conn.close()

    reports = []
    for row in rows:
        reports.append({
            "id": row["id"],
            "device_model": row["device_model"],
            "android_version": row["android_version"],
            "gpu": row["gpu"],
            "gl_version": row["gl_version"],
            "total": row["total"],
            "pass": row["pass"],
            "fail": row["fail"],
            "crash": row["crash"],
            "uploaded_at": row["uploaded_at"],
        })

    return jsonify(reports)


@app.route("/")
def dashboard():
    conn = get_db()

    # Overall summary
    row = conn.execute(
        "SELECT COUNT(*) as total_reports, "
        "COUNT(DISTINCT device_model) as unique_devices, "
        "SUM(total) as total_tests, SUM(pass) as total_pass, "
        "SUM(fail) as total_fail, SUM(crash) as total_crash "
        "FROM reports"
    ).fetchone()

    summary = {
        "total_reports": row["total_reports"],
        "unique_devices": row["unique_devices"],
        "total_tests": row["total_tests"] or 0,
        "total_pass": row["total_pass"] or 0,
        "total_fail": row["total_fail"] or 0,
        "total_crash": row["total_crash"] or 0,
    }

    # Per-device stats
    device_rows = conn.execute(
        "SELECT device_model, COUNT(*) as report_count, "
        "SUM(total) as total_tests, SUM(pass) as total_pass, "
        "SUM(fail) as total_fail, SUM(crash) as total_crash "
        "FROM reports GROUP BY device_model ORDER BY report_count DESC"
    ).fetchall()

    devices = []
    for d in device_rows:
        total = d["total_tests"] or 1
        devices.append({
            "model": d["device_model"],
            "report_count": d["report_count"],
            "total_tests": d["total_tests"],
            "total_pass": d["total_pass"],
            "total_fail": d["total_fail"],
            "total_crash": d["total_crash"],
            "pass_rate": round(d["total_pass"] / total * 100, 1),
        })

    # Recent reports
    recent = conn.execute(
        "SELECT * FROM reports ORDER BY uploaded_at DESC LIMIT 50"
    ).fetchall()

    conn.close()

    return render_template("index.html", summary=summary, devices=devices, recent=recent)


init_db()

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
```

- [ ] **Step 3: Commit**

```bash
git add server/requirements.txt server/server.py
git commit -m "feat: add Flask server with report API and SQLite storage"
```

---

## Task 6: Server — Create Web Dashboard Template

**Files:**
- Create: `server/templates/index.html`

- [ ] **Step 1: Create index.html**

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>RenderTest Dashboard</title>
<style>
  body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; color: #333; }
  h1 { margin-bottom: 24px; }
  .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-bottom: 32px; }
  .card { background: #fff; border-radius: 8px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
  .card .label { font-size: 13px; color: #888; margin-bottom: 4px; }
  .card .value { font-size: 28px; font-weight: 700; }
  .card .value.green { color: #22c55e; }
  .card .value.red { color: #ef4444; }
  .card .value.orange { color: #f59e0b; }
  table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 32px; }
  th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid #eee; }
  th { background: #fafafa; font-size: 13px; color: #888; text-transform: uppercase; }
  .pass-rate { font-weight: 600; }
  .pass-rate.high { color: #22c55e; }
  .pass-rate.mid { color: #f59e0b; }
  .pass-rate.low { color: #ef4444; }
  details { cursor: pointer; }
  details summary { font-weight: 500; }
  .detail-table { margin: 8px 0 8px 20px; font-size: 13px; }
  .detail-table th { font-size: 11px; }
  .status-pass { color: #22c55e; font-weight: 600; }
  .status-fail { color: #ef4444; font-weight: 600; }
  .status-crash { color: #dc2626; font-weight: 700; }
  .empty { text-align: center; padding: 40px; color: #999; }
</style>
</head>
<body>
<h1>RenderTest Dashboard</h1>

{% if summary.total_reports == 0 %}
<div class="empty">No reports yet. Run tests on a device to see results here.</div>
{% else %}

<div class="cards">
  <div class="card"><div class="label">Total Reports</div><div class="value">{{ summary.total_reports }}</div></div>
  <div class="card"><div class="label">Unique Devices</div><div class="value">{{ summary.unique_devices }}</div></div>
  <div class="card"><div class="label">Passed</div><div class="value green">{{ summary.total_pass }}</div></div>
  <div class="card"><div class="label">Failed</div><div class="value red">{{ summary.total_fail }}</div></div>
  <div class="card"><div class="label">Crashed</div><div class="value orange">{{ summary.total_crash }}</div></div>
</div>

<h2>Per-Device Stats</h2>
<table>
  <tr><th>Device Model</th><th>Reports</th><th>Total Tests</th><th>Pass</th><th>Fail</th><th>Crash</th><th>Pass Rate</th></tr>
  {% for d in devices %}
  <tr>
    <td>{{ d.model }}</td>
    <td>{{ d.report_count }}</td>
    <td>{{ d.total_tests }}</td>
    <td>{{ d.total_pass }}</td>
    <td>{{ d.total_fail }}</td>
    <td>{{ d.total_crash }}</td>
    <td><span class="pass-rate {{ 'high' if d.pass_rate >= 90 else 'mid' if d.pass_rate >= 70 else 'low' }}">{{ d.pass_rate }}%</span></td>
  </tr>
  {% endfor %}
</table>

<h2>Recent Reports</h2>
<table>
  <tr><th>Time</th><th>Device</th><th>Android</th><th>GPU</th><th>Total</th><th>Pass</th><th>Fail</th><th>Crash</th><th>Details</th></tr>
  {% for r in recent %}
  <tr>
    <td>{{ r.uploaded_at }}</td>
    <td>{{ r.device_model }}</td>
    <td>{{ r.android_version }}</td>
    <td>{{ r.gpu }}</td>
    <td>{{ r.total }}</td>
    <td>{{ r.pass }}</td>
    <td>{{ r.fail }}</td>
    <td>{{ r.crash }}</td>
    <td>
      <details>
        <summary>View</summary>
        <table class="detail-table">
          <tr><th>Test</th><th>API</th><th>Status</th><th>GL Error</th><th>Detail</th></tr>
          {% for t in r.results_json|fromjson %}
          <tr>
            <td>{{ t.name }}</td>
            <td>{{ t.apiLevel }}</td>
            <td class="status-{{ t.status|lower }}">{{ t.status }}</td>
            <td>{{ t.glErrorName }}</td>
            <td>{{ t.detail }}</td>
          </tr>
          {% endfor %}
        </table>
      </details>
    </td>
  </tr>
  {% endfor %}
</table>

{% endif %}
</body>
</html>
```

- [ ] **Step 2: Register the `fromjson` Jinja filter in server.py**

Add after `app = Flask(__name__)`:

```python
@app.template_filter("fromjson")
def fromjson_filter(s):
    return json.loads(s)
```

- [ ] **Step 3: Commit**

```bash
git add server/templates/index.html server/server.py
git commit -m "feat: add web dashboard with per-device stats and report details"
```

---

## Task 7: Verify & Clean Up

- [ ] **Step 1: Verify all files exist and are consistent**

```bash
# App side
ls app/src/main/java/com/rendertest/ReportUploader.java
ls app/src/main/java/com/rendertest/ReportGenerator.java
ls app/src/main/java/com/rendertest/MainActivity.java

# Server side
ls server/server.py
ls server/requirements.txt
ls server/templates/index.html
```

- [ ] **Step 2: Verify Android app compiles (check for syntax issues)**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Verify server starts**

```bash
cd server && python -c "import server; print('Server module loads OK')"
```

Expected: "Server module loads OK"

- [ ] **Step 4: Final commit if any fixes needed**
