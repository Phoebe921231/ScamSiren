package com.example.fp2;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // 邊距處理
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 1. 新增：歷史紀錄按鈕 (因為我們在 XML 是用 LinearLayout 包起來的)
        LinearLayout btnHistory = findViewById(R.id.btnHistory);
        if (btnHistory != null) {
            btnHistory.setOnClickListener(v -> {
                // 跳轉到新的 HistoryActivity
                Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
                startActivity(intent);
            });
        }

        // 截圖辨識按鈕
        Button btnImage = findViewById(R.id.btnImage);
        btnImage.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ScreenshotActivity.class);
            startActivity(intent);
        });

        // 錄音辨識按鈕
        Button btnAudio = findViewById(R.id.btnAudio);
        btnAudio.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AudioRecognitionActivity.class);
            startActivity(intent);
        });

        // 網址檢查按鈕
        Button btnLink = findViewById(R.id.btnLink);
        btnLink.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, UrlCheckActivity.class);
            startActivity(intent);
        });

        // 說明圖示（隱私頁面）
        ImageView helpIcon = findViewById(R.id.helpIcon);
        helpIcon.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, activity_privacy_dialog.class); // 請確認類別名稱正確
            startActivity(intent);
        });

        // 通話錄音教學圖示
        ImageView lightIcon = findViewById(R.id.lightIcon);
        lightIcon.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CallRecordSettingActivity.class);
            startActivity(intent);
        });

    }
}
