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

        // 歷史紀錄按鈕 (XML 用 LinearLayout 包起來)
        LinearLayout btnHistory = findViewById(R.id.btnHistory);
        if (btnHistory != null) {
            btnHistory.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
                startActivity(intent);
            });
        }

        // ✅ 新增：文字辨識按鈕
        Button btnText = findViewById(R.id.btnText);
        if (btnText != null) {
            btnText.setOnClickListener(v -> {
                // 你等等要新增這個 Activity
                Intent intent = new Intent(MainActivity.this, TextCheckActivity.class);
                startActivity(intent);
            });
        }

        // 截圖辨識按鈕
        Button btnImage = findViewById(R.id.btnImage);
        if (btnImage != null) {
            btnImage.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ScreenshotActivity.class);
                startActivity(intent);
            });
        }

        // 錄音辨識按鈕
        Button btnAudio = findViewById(R.id.btnAudio);
        if (btnAudio != null) {
            btnAudio.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, AudioRecognitionActivity.class);
                startActivity(intent);
            });
        }

        // 網址檢查按鈕
        Button btnLink = findViewById(R.id.btnLink);
        if (btnLink != null) {
            btnLink.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, UrlCheckActivity.class);
                startActivity(intent);
            });
        }

        // 說明圖示（隱私頁面）
        ImageView helpIcon = findViewById(R.id.helpIcon);
        if (helpIcon != null) {
            helpIcon.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, activity_privacy_dialog.class); // 請確認類別名稱正確
                startActivity(intent);
            });
        }

        // 通話錄音教學圖示
        ImageView lightIcon = findViewById(R.id.lightIcon);
        if (lightIcon != null) {
            lightIcon.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, CallRecordSettingActivity.class);
                startActivity(intent);
            });
        }
    }
}


