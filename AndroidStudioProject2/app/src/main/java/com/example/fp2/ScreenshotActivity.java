package com.example.fp2;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ScreenshotActivity extends AppCompatActivity {

    private ImageView imagePreview;
    @Nullable private Uri selectedImageUri; // 使用者選的圖片

    /** 方式一：首選 — ACTION_OPEN_DOCUMENT（嘗試預設到 Downloads，保留持久讀取權限） */
    private final ActivityResultLauncher<Intent> pickImageLocal =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        // 保留讀取權限（有些裝置 flags 會是 0，保守直接給 READ）
                        try {
                            getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                        } catch (Exception ignored) {}
                        selectedImageUri = uri;
                        imagePreview.setImageURI(uri);
                    }
                }
            });

    /** 方式二：備援 — GetContent（不指定初始目錄） */
    private final ActivityResultLauncher<String> pickImageSimple =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    imagePreview.setImageURI(uri);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_screenshot);

        // 邊距處理
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        imagePreview = findViewById(R.id.imagePreview);
        ImageView backArrow = findViewById(R.id.backArrow);
        Button uploadButton = findViewById(R.id.uploadButton);
        Button recognizeButton = findViewById(R.id.recognizeButton);

        if (savedInstanceState != null) {
            String saved = savedInstanceState.getString("selectedImageUri");
            if (saved != null) {
                selectedImageUri = Uri.parse(saved);
                imagePreview.setImageURI(selectedImageUri);
            }
        }

        // 🔙 返回
        backArrow.setOnClickListener(v -> {
            startActivity(new Intent(ScreenshotActivity.this, MainActivity.class));
            finish();
        });

        // 📂 選擇圖片（與語音相同策略）
        uploadButton.setOnClickListener(this::onPickImageClicked);

        // 🟠 跳到 ResultActivity（保持原本行為）
        recognizeButton.setOnClickListener(v -> {
            if (selectedImageUri != null) {
                Intent intent = new Intent(ScreenshotActivity.this, ResultActivity.class);
                intent.putExtra("imageUri", selectedImageUri.toString());
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedImageUri != null) {
            outState.putString("selectedImageUri", selectedImageUri.toString());
        }
    }

    /** 按「選擇圖片」：優先打開 Downloads，失敗就退回簡單挑檔 */
    public void onPickImageClicked(View v) {
        try {
            Uri downloadsRoot = Uri.parse("content://com.android.providers.downloads.documents/root/downloads");
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

            // EXTRA_INITIAL_URI 僅在 API 26+ 可用
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsRoot);
            }
            pickImageLocal.launch(intent);
        } catch (Exception e) {
            // 備援
            pickImageSimple.launch("image/*");
        }
    }
}
