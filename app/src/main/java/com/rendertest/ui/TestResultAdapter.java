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
