# Report Remote Upload & Web Dashboard Design

## Overview

Add automatic report uploading from the Android app to a remote server, plus a lightweight Flask web dashboard for aggregated report viewing and cross-device analysis.

## User Decisions

- **Server URL**: Hardcoded in the Android app
- **Upload timing**: Automatically after test completion
- **HTTP library**: OkHttp 4.12
- **Upload protocol**: HTTP POST with JSON body
- **Server tech**: Python 3 + Flask + SQLite

---

## Part 1: Android App Changes

### Data Flow

```
TestRenderer completes tests
  → ReportGenerator.generateAndSave() generates JSON + saves file
  → ReportUploader.upload(jsonString) POSTs to server (async, non-blocking)
  → Original UI logic: show results, show share button
```

### Files to Modify

| File | Change |
|------|--------|
| `AndroidManifest.xml` | Add `<uses-permission android:name="android.permission.INTERNET"/>` |
| `app/build.gradle` | Add `implementation 'com.squareup.okhttp3:okhttp:4.12.0'` |
| `ReportGenerator.java` | Return JSON string from `generateAndSave()` for upload use |
| `MainActivity.java` | Call `ReportUploader.upload()` after report generation |

### New File

**`ReportUploader.java`**
- Hardcoded `SERVER_URL = "http://<server-ip>:5000/api/report"`
- Uses OkHttp async (`enqueue`), posts JSON string as `application/json`
- Logs success/failure only — no UI impact on upload failure
- Single static method: `upload(String jsonReport)`

### Upload Payload

The app sends the exact same JSON that `ReportGenerator.generateJson()` produces. No transformation needed.

---

## Part 2: Server

### Tech Stack

- Python 3, Flask, SQLite (built-in)
- Single `server.py` entry point
- No frontend frameworks — pure HTML + inline CSS

### Directory Structure

```
server/
├── server.py
├── requirements.txt        # flask
├── templates/
│   └── index.html          # Web dashboard
└── data/
    └── reports.db          # Auto-created SQLite database
```

### Database Schema

```sql
CREATE TABLE reports (
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
);
```

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/report` | Receive and store report JSON |
| `GET` | `/` | Web dashboard (summary + per-device stats) |
| `GET` | `/api/reports` | JSON list of all reports |

### Web Dashboard Features

- Summary stats: total reports, unique device models, overall pass/fail/crash rates
- Per-device breakdown: grouped by model, showing pass rate per model
- Report list: reverse chronological, expandable for full test details
- Simple HTML with inline CSS, no JavaScript frameworks

---

## Error Handling

- **Network failure on app**: Log warning, continue normally — upload is fire-and-forget
- **Server unavailable**: Same as above — no user-facing error
- **Invalid JSON on server**: Return 400 with error message, log the bad payload
- **Duplicate reports**: Allowed — same device can submit multiple times (different timestamps)

---

## Deployment

```bash
cd server/
pip install -r requirements.txt
python server.py     # Runs on 0.0.0.0:5000
```
