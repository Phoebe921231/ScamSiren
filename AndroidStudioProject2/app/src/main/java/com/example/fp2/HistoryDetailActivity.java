package com.example.fp2;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fp2.db.AppDatabase;
import com.example.fp2.db.RiskRecordEntity;

import java.io.IOException;

public class HistoryDetailActivity extends AppCompatActivity {

    // ===== UI =====
    private TextView tvTitle;
    private TextView tvResultText;

    // ✅ 顯示「偵測到的文字 / 原文」
    private TextView tvContent;

    private ImageView ivImage;
    private View audioLayout;
    private ImageButton btnPlayPause;

    // ===== Audio =====
    private MediaPlayer mediaPlayer;
    private boolean isPrepared = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_detail);

        // ===== bind UI =====
        tvTitle = findViewById(R.id.tv_page_title);
        tvResultText = findViewById(R.id.tv_result_text);
        tvContent = findViewById(R.id.tv_content);

        ivImage = findViewById(R.id.iv_image);
        audioLayout = findViewById(R.id.layout_audio);
        btnPlayPause = findViewById(R.id.btn_play_pause);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 一開始全部隱藏
        ivImage.setVisibility(View.GONE);
        audioLayout.setVisibility(View.GONE);
        tvContent.setVisibility(View.GONE);

        long recordId = getIntent().getLongExtra("record_id", -1);
        if (recordId == -1) {
            toast("資料錯誤");
            finish();
            return;
        }

        loadRecord(recordId);
    }

    private void loadRecord(long id) {
        new Thread(() -> {
            RiskRecordEntity record =
                    AppDatabase.getInstance(this)
                            .riskRecordDao()
                            .getById(id);

            runOnUiThread(() -> {
                if (record == null) {
                    toast("找不到紀錄");
                    finish();
                    return;
                }
                bindRecord(record);
            });
        }).start();
    }

    private void bindRecord(RiskRecordEntity r) {

        // ===== 標題 =====
        switch (safeUpper(r.type)) {
            case "IMAGE":
                tvTitle.setText("圖片辨識結果");
                break;
            case "AUDIO":
                tvTitle.setText("錄音判別結果");
                break;
            case "URL":
                tvTitle.setText("網址檢查結果");
                break;
            case "TEXT":
                tvTitle.setText("文字辨識結果");
                break;
            default:
                tvTitle.setText("辨識結果");
        }

        // ===== 分析結果文字 =====
        if (TextUtils.isEmpty(safeTrim(r.summary))) {
            tvResultText.setText("(無分析結果)");
        } else {
            tvResultText.setText(r.summary);
        }

        // ✅ 取「偵測到的文字」：有 detectedText 就用，沒有才退回 content
        String detectedOrContent = pickDetectedOrContent(r);

        // ===== 依類型顯示 =====
        String type = safeUpper(r.type);

        if ("IMAGE".equals(type)) {
            // 1) 顯示圖片
            showImage(r.content);
            // 2) 同時顯示 OCR 文字（如果有）
            showDetectedTextIfAny(detectedOrContent);

        } else if ("AUDIO".equals(type)) {
            // 1) 播放器
            showAudio(r.content);
            // 2) 同時顯示語音文字（如果有）
            showDetectedTextIfAny(detectedOrContent);

        } else if ("URL".equals(type)) {
            showUrl(r.content);

        } else if ("TEXT".equals(type)) {
            // 文字：優先顯示 detectedText（若你存的是完整原文），否則顯示 content
            showPlainText(detectedOrContent);
        }
    }

    // =========================
    // IMAGE
    // =========================
    private void showImage(String uriStr) {
        ivImage.setVisibility(View.VISIBLE);
        audioLayout.setVisibility(View.GONE);

        try {
            ivImage.setImageURI(Uri.parse(uriStr));
        } catch (Exception e) {
            toast("圖片讀取失敗");
        }
    }

    // =========================
    // AUDIO
    // =========================
    private void showAudio(String uriStr) {
        audioLayout.setVisibility(View.VISIBLE);
        ivImage.setVisibility(View.GONE);

        Uri uri = Uri.parse(uriStr);
        mediaPlayer = new MediaPlayer();

        try {
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.setOnPreparedListener(mp -> {
                isPrepared = true;
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            });
            mediaPlayer.setOnCompletionListener(mp ->
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            );
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            toast("音檔載入失敗");
        }

        btnPlayPause.setOnClickListener(v -> togglePlay());
    }

    private void togglePlay() {
        if (!isPrepared || mediaPlayer == null) return;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        } else {
            mediaPlayer.start();
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        }
    }

    // =========================
    // URL
    // =========================
    private void showUrl(String url) {
        ivImage.setVisibility(View.GONE);
        audioLayout.setVisibility(View.GONE);

        tvContent.setVisibility(View.VISIBLE);
        tvContent.setText(url);
        tvContent.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(i);
            } catch (Exception e) {
                toast("網址格式錯誤");
            }
        });
    }

    // =========================
    // TEXT
    // =========================
    private void showPlainText(String text) {
        ivImage.setVisibility(View.GONE);
        audioLayout.setVisibility(View.GONE);

        tvContent.setVisibility(View.VISIBLE);
        tvContent.setOnClickListener(null);

        if (TextUtils.isEmpty(safeTrim(text))) {
            tvContent.setText("(無原文內容)");
        } else {
            tvContent.setText(text);
        }
    }

    /**
     * ✅ 給 IMAGE/AUDIO 用：同頁面也顯示偵測文字
     * - 你 layout 已經有 tv_content，所以直接打開顯示即可
     */
    private void showDetectedTextIfAny(String detectedOrContent) {
        // AUDIO/IMAGE 的 content 通常是 uri，所以只有 detectedText 有意義
        // 但我們保守判斷：如果看起來像 content(uri) 而不是文字，就不顯示
        String t = safeTrim(detectedOrContent);
        if (TextUtils.isEmpty(t)) {
            tvContent.setVisibility(View.GONE);
            return;
        }

        // 如果內容是 uri（content:// 或 file:// 或 http），通常不是你要的「偵測文字」
        if (t.startsWith("content://") || t.startsWith("file://") || t.startsWith("http")) {
            tvContent.setVisibility(View.GONE);
            return;
        }

        tvContent.setVisibility(View.VISIBLE);
        tvContent.setOnClickListener(null);
        tvContent.setText(t);
    }

    /**
     * ✅ 核心：優先拿 detectedText（偵測到的文字），沒有才退回 content
     */
    private String pickDetectedOrContent(RiskRecordEntity r) {
        if (r == null) return "";

        String detected = "";
        try {
            detected = r.detectedText; // 你的 Entity 欄位名（@ColumnInfo(name="detected_text")）
        } catch (Exception ignored) {}

        if (!TextUtils.isEmpty(safeTrim(detected))) return detected;
        return r.content == null ? "" : r.content;
    }

    private String safeUpper(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            try { mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}