package com.rendertest;

import android.content.Intent;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rendertest.model.DeviceInfo;
import com.rendertest.model.TestResult;
import com.rendertest.ui.TestResultAdapter;

import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private GLSurfaceView glSurfaceView;
    private TextView tvDeviceInfo;
    private Button btnShare;
    private TestResultAdapter adapter;
    private File lastReportFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvDeviceInfo = findViewById(R.id.tv_device_info);
        btnShare = findViewById(R.id.btn_share);

        RecyclerView rvResults = findViewById(R.id.rv_test_results);
        adapter = new TestResultAdapter();
        rvResults.setLayoutManager(new LinearLayoutManager(this));
        rvResults.setAdapter(adapter);

        btnShare.setOnClickListener(v -> shareReport());

        glSurfaceView = findViewById(R.id.gl_surface_view);
        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setRenderer(new TestRenderer(new TestRenderer.Callback() {
            @Override
            public void onDeviceInfo(DeviceInfo info) {
                runOnUiThread(() -> tvDeviceInfo.setText(info.getSummary()));
            }

            @Override
            public void onTestResults(DeviceInfo deviceInfo, List<TestResult> results) {
                // 自动保存报告
                File report = ReportGenerator.generateAndSave(MainActivity.this, deviceInfo, results);

                runOnUiThread(() -> {
                    adapter.setResults(results);
                    if (report != null) {
                        lastReportFile = report;
                        btnShare.setVisibility(View.VISIBLE);
                        Toast.makeText(MainActivity.this,
                                "报告已保存: " + report.getName(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }));
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    private void shareReport() {
        if (lastReportFile == null || !lastReportFile.exists()) {
            Toast.makeText(this, "没有可分享的报告", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(this,
                "com.rendertest.fileprovider", lastReportFile);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT, "RenderTest Report - " + lastReportFile.getName());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(intent, "分享测试报告"));
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
