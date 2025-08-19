package com.example.fp2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.io.IOException;

public class ResultActivity extends AppCompatActivity {

    private TextView resultText;

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

        // 元件
        ImageView backArrow = findViewById(R.id.backArrow);
        Button uploadButton = findViewById(R.id.uploadButton);
        resultText = findViewById(R.id.resultText);

        // 🔙 返回 MainActivity
        backArrow.setOnClickListener(v -> {
            Intent intent = new Intent(ResultActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        // 📂 再次上傳
        uploadButton.setOnClickListener(v -> {
            Intent intent = new Intent(ResultActivity.this, ScreenshotActivity.class);
            startActivity(intent);
        });

        // 📸 取得 ScreenshotActivity 傳來的圖片 URI
        String imageUriStr = getIntent().getStringExtra("imageUri");
        if (imageUriStr != null) {
            Uri imageUri = Uri.parse(imageUriStr);
            runTextRecognition(imageUri);
        }
    }

    // 🟢 OCR 辨識 (同時支援中 + 英)
    private void runTextRecognition(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);

            // 先嘗試中文
            TextRecognizer chineseRecognizer = TextRecognition.getClient(
                    new ChineseTextRecognizerOptions.Builder().build()
            );

            chineseRecognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String result = visionText.getText();
                        if (!result.isEmpty()) {
                            resultText.setText(result);
                        } else {
                            // 如果中文沒結果 → 再用英文辨識
                            runEnglishRecognition(image);
                        }
                    })
                    .addOnFailureListener(e -> {
                        // 中文失敗 → 試英文
                        runEnglishRecognition(image);
                    });

        } catch (IOException e) {
            e.printStackTrace();
            resultText.setText("讀取圖片失敗");
        }
    }

    // 🟠 英文辨識
    private void runEnglishRecognition(InputImage image) {
        TextRecognizer englishRecognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
        );

        englishRecognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String result = visionText.getText();
                    if (result.isEmpty()) {
                        resultText.setText("未偵測到文字");
                    } else {
                        resultText.setText(result);
                    }
                })
                .addOnFailureListener(e -> resultText.setText("辨識失敗：" + e.getMessage()));
    }
}
