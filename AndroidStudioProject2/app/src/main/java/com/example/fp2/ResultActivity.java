package com.example.fp2;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_result);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 點 backArrow 回到 MainActivity
        ImageView backArrow = findViewById(R.id.backArrow); // 確保你 layout 裡有這個 ID
        backArrow.setOnClickListener(v -> {
            Intent intent = new Intent(ResultActivity.this, MainActivity.class);
            startActivity(intent);
            finish(); // optional：關掉這頁避免重疊
        });

        // 點 uploadButton 跳轉 ScreenshotActivity
        Button uploadButton = findViewById(R.id.uploadButton); // 確保你 layout 裡有這個 ID
        uploadButton.setOnClickListener(v -> {
            Intent intent = new Intent(ResultActivity.this, ScreenshotActivity.class);
            startActivity(intent);
        });
    }
}
