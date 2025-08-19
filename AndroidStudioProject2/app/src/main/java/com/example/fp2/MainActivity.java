package com.example.fp2;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;

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
