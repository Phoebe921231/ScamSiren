package com.example.fp2;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.fp2.db.AppDatabase;
import com.example.fp2.db.RiskRecordEntity;
import com.example.fp2.model.ApiResponse;
import com.example.fp2.model.ResultFormatter;
import com.example.fp2.net.BackendService;

import java.io.IOException;
import java.util.Locale;

public class AudioRecognitionActivity extends AppCompatActivity {

    private final BackendService backend = new BackendService();

    private TextView resultText;
    private ImageView backArrow;
    private Button selectAudioButton, startRecognitionButton;

    private ImageView micIcon;
    private View audioSelectedGroup;
    private TextView selectedFileName;
    private ImageButton playPauseBtn;

    private Uri selectedAudioUri;
    private MediaPlayer mediaPlayer;
    private boolean isPrepared = false;

    // ===== 選音檔 =====
    private final ActivityResultLauncher<Intent> pickAudioLocal =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                    Uri uri = r.getData().getData();
                    if (uri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                        } catch (Exception ignored) {}

                        selectedAudioUri = uri;
                        showSelectedAudioUi(FileUtils.displayName(this, uri));
                        preparePlayer(uri);
                        toast("已選擇音檔");
                        return;
                    }
                }
                toast("未選擇任何檔案");
            });

    private final ActivityResultLauncher<String> pickAudioSimple =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedAudioUri = uri;
                    showSelectedAudioUi(FileUtils.displayName(this, uri));
                    preparePlayer(uri);
                    toast("已選擇音檔");
                } else {
                    toast("未選擇任何檔案");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_audio_recognition);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        resultText = findViewById(R.id.resultText);
        backArrow = findViewById(R.id.backArrow);
        selectAudioButton = findViewById(R.id.selectAudioButton);
        startRecognitionButton = findViewById(R.id.startRecognitionButton);

        micIcon = findViewById(R.id.micIcon);
        audioSelectedGroup = findViewById(R.id.audioSelectedGroup);
        selectedFileName = findViewById(R.id.selectedFileName);
        playPauseBtn = findViewById(R.id.playPauseBtn);

        backArrow.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        selectAudioButton.setOnClickListener(this::onPickAudioClicked);
        startRecognitionButton.setOnClickListener(this::onStartRecognitionClicked);
        playPauseBtn.setOnClickListener(v -> togglePlay());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }

    // ===== 使用者操作 =====
    public void onPickAudioClicked(View v) {
        try {
            Uri downloadsRoot = Uri.parse(
                    "content://com.android.providers.downloads.documents/root/downloads"
            );
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("audio/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsRoot);
            }
            pickAudioLocal.launch(intent);
        } catch (Exception e) {
            pickAudioSimple.launch("audio/*");
        }
    }

    public void onStartRecognitionClicked(View v) {
        if (selectedAudioUri == null) {
            toast("請先選擇錄音檔");
            return;
        }
        uploadAudioToBackend(selectedAudioUri);
    }

    // ===== 上傳 & 判斷 =====
    private void uploadAudioToBackend(Uri uri) {
        setButtonsEnabled(false);
        resultText.setText("上傳並分析中…");

        backend.uploadAudio(this, uri, new BackendService.Callback() {
            @Override
            public void onSuccess(ApiResponse data) {
                runOnUiThread(() -> {
                    String pretty = ResultFormatter.format(data);
                    resultText.setText(pretty);

                    saveAudioRiskIfNeeded(data, uri, pretty);

                    setButtonsEnabled(true);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    resultText.setText("錯誤：" + message);
                    setButtonsEnabled(true);
                });
            }
        });
    }

    // ===== 寫入 Room（音檔）=====
    private void saveAudioRiskIfNeeded(ApiResponse data, Uri audioUri, String summary) {
        if (data == null || audioUri == null) return;

        String riskLevel = normalizeRiskLevel(data.risk, data.is_scam);
        if (!"MEDIUM".equals(riskLevel) && !"HIGH".equals(riskLevel)) return;

        int score = "HIGH".equals(riskLevel) ? 90 : 65;

        RiskRecordEntity record = new RiskRecordEntity(
                "AUDIO",
                audioUri.toString(),
                riskLevel,
                score,
                summary,
                System.currentTimeMillis()
        );

        AppDatabase.getInstance(this)
                .riskRecordDao()
                .insert(record);
    }

    // ===== 工具 =====
    private String normalizeRiskLevel(String risk, boolean isScam) {
        if (risk == null) return isScam ? "HIGH" : "LOW";
        String r = risk.trim().toUpperCase(Locale.ROOT);
        if (r.contains("HIGH") || r.contains("高")) return "HIGH";
        if (r.contains("MED") || r.contains("中")) return "MEDIUM";
        if (r.contains("LOW") || r.contains("低")) return "LOW";
        return isScam ? "HIGH" : "LOW";
    }

    private void showSelectedAudioUi(String name) {
        micIcon.setVisibility(View.GONE);
        audioSelectedGroup.setVisibility(View.VISIBLE);
        selectedFileName.setText(name == null ? "已選擇音檔" : name);
    }

    private void preparePlayer(Uri uri) {
        releasePlayer();
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.setOnPreparedListener(mp -> {
                isPrepared = true;
                playPauseBtn.setImageResource(android.R.drawable.ic_media_play);
            });
            mediaPlayer.setOnCompletionListener(mp ->
                    playPauseBtn.setImageResource(android.R.drawable.ic_media_play));
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            toast("無法預覽音檔");
        }
    }

    private void togglePlay() {
        if (mediaPlayer == null || !isPrepared) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            playPauseBtn.setImageResource(android.R.drawable.ic_media_play);
        } else {
            mediaPlayer.start();
            playPauseBtn.setImageResource(android.R.drawable.ic_media_pause);
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) {}
            try { mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
            isPrepared = false;
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        selectAudioButton.setEnabled(enabled);
        startRecognitionButton.setEnabled(enabled);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}


