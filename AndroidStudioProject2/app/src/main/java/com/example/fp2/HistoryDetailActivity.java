package com.example.fp2;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
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

        // ===== 只收 record_id =====
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
        switch (r.detectType) {
            case "IMAGE":
                tvTitle.setText("圖片辨識結果");
                break;
            case "AUDIO":
                tvTitle.setText("錄音判別結果");
                break;
            case "URL":
                tvTitle.setText("網址檢查結果");
                break;
            default:
                tvTitle.setText("辨識結果");
        }

        // ===== 分析結果文字 =====
        if (r.summary == null || r.summary.trim().isEmpty()) {
            tvResultText.setText("(無分析結果)");
        } else {
            tvResultText.setText(r.summary);
        }

        // ===== 依類型顯示 =====
        if ("IMAGE".equals(r.detectType)) {
            showImage(r.content);

        } else if ("AUDIO".equals(r.detectType)) {
            showAudio(r.content);

        } else if ("URL".equals(r.detectType)) {
            showUrl(r.content);
        }
    }

    // =========================
    // IMAGE
    // =========================
    private void showImage(String uriStr) {
        ivImage.setVisibility(View.VISIBLE);
        tvContent.setVisibility(View.GONE);

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
        tvContent.setVisibility(View.GONE);

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