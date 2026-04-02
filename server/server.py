import json
import os
import sqlite3

from flask import Flask, request, jsonify, render_template

app = Flask(__name__)

DB_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
DB_PATH = os.path.join(DB_DIR, "reports.db")


@app.template_filter("fromjson")
def fromjson_filter(s):
    return json.loads(s)


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
    app.run(host="0.0.0.0", port=5000, debug=False)
