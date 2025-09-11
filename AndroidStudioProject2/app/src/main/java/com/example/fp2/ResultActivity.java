package com.example.fp2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.fp2.model.ApiResponse;
import com.example.fp2.model.ResultFormatter;
import com.example.fp2.net.BackendService;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.io.IOException;

public class ResultActivity extends AppCompatActivity {

    private TextView resultText;
    private final BackendService backend = new BackendService();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_result);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        ImageView backArrow = findViewById(R.id.backArrow);
        Button uploadButton = findViewById(R.id.uploadButton);
        resultText = findViewById(R.id.resultText);

        backArrow.setOnClickListener(v -> {
            startActivity(new Intent(ResultActivity.this, MainActivity.class));
            finish();
        });
        uploadButton.setOnClickListener(v -> {
            startActivity(new Intent(ResultActivity.this, ScreenshotActivity.class));
            finish();
        });

        String imageUriStr = getIntent().getStringExtra("imageUri");
        if (imageUriStr != null) {
            runTextRecognition(Uri.parse(imageUriStr));
        } else {
            resultText.setText("未收到圖片");
        }
    }

    private void runTextRecognition(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            TextRecognizer zh = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
            zh.process(image)
                    .addOnSuccessListener(t -> {
                        String txt = t.getText();
                        if (!TextUtils.isEmpty(txt)) analyzeOcrText(txt); else runEnglishRecognition(image);
                    })
                    .addOnFailureListener(e -> runEnglishRecognition(image));
        } catch (IOException e) {
            resultText.setText("讀取圖片失敗");
        }
    }

    private void runEnglishRecognition(InputImage image) {
        TextRecognizer en = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        en.process(image)
                .addOnSuccessListener(t -> {
                    String txt = t.getText();
                    if (TextUtils.isEmpty(txt)) resultText.setText("未偵測到文字"); else analyzeOcrText(txt);
                })
                .addOnFailureListener(e -> resultText.setText("辨識失敗：" + e.getMessage()));
    }

    private void analyzeOcrText(String text) {
        resultText.setText("分析中…");
        backend.analyzeText(text, new BackendService.Callback() {
            @Override public void onSuccess(ApiResponse data) {
                runOnUiThread(() -> {
                    String pretty = ResultFormatter.format(data);
                    resultText.setText(pretty);
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> resultText.setText("分析失敗：" + message));
            }
        });
    }
}

