package com.example.fp2.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "risk_records")
public class RiskRecordEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String type;        // TEXT / URL / AUDIO / IMAGE

    @NonNull
    public String content;     // 原始內容（文字 / 網址 / 音檔名稱 / 圖片來源）

    @NonNull
    public String riskLevel;   // LOW / MEDIUM / HIGH

    public int score;          // 風險分數

    @NonNull
    public String summary;     // 顯示用摘要（你那段格式化結果）

    // ✅ 新增：偵測到的文字（語音轉文字 / OCR 文字）
    @NonNull
    @ColumnInfo(name = "detected_text")
    public String detectedText;

    @NonNull
    public String extra;       // 其他擴充（可先留空）

    public Long createdAt;     // 建立時間（毫秒）

    // ✅ Room 正式用（8 參數）
    public RiskRecordEntity(
            @NonNull String type,
            @NonNull String content,
            @NonNull String riskLevel,
            int score,
            @NonNull String summary,
            @NonNull String detectedText,
            @NonNull String extra,
            Long createdAt
    ) {
        this.type = type;
        this.content = content;
        this.riskLevel = riskLevel;
        this.score = score;
        this.summary = summary;
        this.detectedText = detectedText;
        this.extra = extra;
        this.createdAt = createdAt;
    }

    // ✅ 你原本 TEXT 用的 6 參數（自動補 detectedText/extra）
    @Ignore
    public RiskRecordEntity(
            @NonNull String type,
            @NonNull String content,
            @NonNull String riskLevel,
            int score,
            @NonNull String summary,
            Long createdAt
    ) {
        this(type, content, riskLevel, score, summary, "", "", createdAt);
    }

    // ✅ 提供一個 7 參數：你只想塞 detectedText（extra 自動空）
    @Ignore
    public RiskRecordEntity(
            @NonNull String type,
            @NonNull String content,
            @NonNull String riskLevel,
            int score,
            @NonNull String summary,
            @NonNull String detectedText,
            Long createdAt
    ) {
        this(type, content, riskLevel, score, summary, detectedText, "", createdAt);
    }
}