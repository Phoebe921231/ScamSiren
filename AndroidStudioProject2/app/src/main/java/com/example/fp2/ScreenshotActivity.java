package com.example.fp2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ScreenshotActivity extends AppCompatActivity {

    private ImageView imagePreview;
    private Uri selectedImageUri; // 儲存使用者選的圖片

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_screenshot);

        // 邊距處理
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 元件
        imagePreview = findViewById(R.id.imagePreview);
        ImageView backArrow = findViewById(R.id.backArrow);
        Button uploadButton = findViewById(R.id.uploadButton);
        Button recognizeButton = findViewById(R.id.recognizeButton);

        // 🔙 返回 MainActivity
        backArrow.setOnClickListener(v -> {
            Intent intent = new Intent(ScreenshotActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        // 📂 選擇圖片
        ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        imagePreview.setImageURI(uri);
                    }
                }
        );

        uploadButton.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        // 🟠 跳到 ResultActivity
        recognizeButton.setOnClickListener(v -> {
            if (selectedImageUri != null) {
                Intent intent = new Intent(ScreenshotActivity.this, ResultActivity.class);
                intent.putExtra("imageUri", selectedImageUri.toString());
                startActivity(intent);
            }
        });
    }
}
