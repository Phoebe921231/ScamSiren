package com.example.fp2;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.ImageView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fp2.model.HistoryItem;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<HistoryItem> dataList;
    private final Context context;

    // 風險顏色
    private static final int COLOR_HIGH = Color.parseColor("#E74C3C"); // 紅
    private static final int COLOR_MID  = Color.parseColor("#E67E22"); // 橘
    private static final int COLOR_LOW  = Color.parseColor("#27AE60"); // 綠
    private static final int COLOR_ICON = Color.parseColor("#227D60"); // 主色綠

    public HistoryAdapter(Context context, List<HistoryItem> dataList) {
        this.context = context;
        this.dataList = dataList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryItem item = dataList.get(position);

        // ✅ 類型 icon
        setTypeIcon(holder, item);

        // 標題
        holder.tvResultTitle.setText(item.getTitle());

        // 日期
        holder.tvDate.setText(item.getDate());

        // 風險等級文字
        String risk = item.getRiskLevel();
        holder.tvRiskLevel.setText("風險程度-" + risk);

        // 風險顏色（同時支援 中文 / 英文）
        if ("高".equals(risk) || "HIGH".equalsIgnoreCase(risk)) {
            holder.tvRiskLevel.setTextColor(COLOR_HIGH);
        } else if ("中".equals(risk) || "MEDIUM".equalsIgnoreCase(risk)) {
            holder.tvRiskLevel.setTextColor(COLOR_MID);
        } else if ("低".equals(risk) || "LOW".equalsIgnoreCase(risk)) {
            holder.tvRiskLevel.setTextColor(COLOR_LOW);
        } else {
            holder.tvRiskLevel.setTextColor(Color.GRAY);
        }

        // 點整個 item 或「查看詳情」都可進詳情頁
        View.OnClickListener goDetail = v -> {
            Intent intent = new Intent(context, HistoryDetailActivity.class);
            intent.putExtra("record_id", item.getId());
            context.startActivity(intent);
        };

        holder.itemView.setOnClickListener(goDetail);
        holder.tvDetailBtn.setOnClickListener(goDetail);
    }

    private void setTypeIcon(@NonNull ViewHolder holder, @NonNull HistoryItem item) {
        if (holder.ivTypeIcon == null) return;

        switch (item.getType()) {
            case HistoryItem.TYPE_URL:
                holder.ivTypeIcon.setImageResource(R.drawable.ic_link2);
                break;
            case HistoryItem.TYPE_AUDIO:
                holder.ivTypeIcon.setImageResource(R.drawable.ic_mic);
                break;
            case HistoryItem.TYPE_TEXT:
                holder.ivTypeIcon.setImageResource(R.drawable.ic_text);
                break;
            case HistoryItem.TYPE_IMAGE:
            default:
                holder.ivTypeIcon.setImageResource(R.drawable.ic_screenshot);
                break;
        }

        // vector 圖示統一染成綠色
        holder.ivTypeIcon.setColorFilter(COLOR_ICON);
    }

    @Override
    public int getItemCount() {
        return dataList == null ? 0 : dataList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView ivTypeIcon;
        TextView tvResultTitle;
        TextView tvRiskLevel;
        TextView tvDate;
        TextView tvDetailBtn;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivTypeIcon    = itemView.findViewById(R.id.iv_type_icon);
            tvResultTitle = itemView.findViewById(R.id.tv_result_title);
            tvRiskLevel   = itemView.findViewById(R.id.tv_risk_level);
            tvDate        = itemView.findViewById(R.id.tv_date);
            tvDetailBtn   = itemView.findViewById(R.id.tv_details_btn);
        }
    }
}
