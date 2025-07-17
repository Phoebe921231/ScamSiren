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

public class ScreenshotActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_screenshot);

        // 處理畫面邊距
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 🔙 點 backArrow 回到 MainActivity
        ImageView backArrow = findViewById(R.id.backArrow);
        backArrow.setOnClickListener(v -> {
            Intent intent = new Intent(ScreenshotActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        // 🟠 點「開始辨識」跳到 ResultActivity
        Button recognizeButton = findViewById(R.id.recognizeButton);
        recognizeButton.setOnClickListener(v -> {
            Intent intent = new Intent(ScreenshotActivity.this, ResultActivity.class);
            startActivity(intent);
        });
    }
}
