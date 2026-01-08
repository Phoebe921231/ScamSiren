package com.example.fp2.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "risk_records")
public class RiskRecordEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // URL / IMAGE / AUDIO
    public String detectType;

    // url / imageUri / audioUri
    public String content;

    // LOW / MEDIUM / HIGH
    public String riskLevel;

    // ⚠️ 關鍵：一定要叫 riskScore（不要改名）
    public int riskScore;

    // 分析結果文字
    public String summary;

    public long createdAt;

    public RiskRecordEntity(
            String detectType,
            String content,
            String riskLevel,
            int riskScore,
            String summary,
            long createdAt
    ) {
        this.detectType = detectType;
        this.content = content;
        this.riskLevel = riskLevel;
        this.riskScore = riskScore;
        this.summary = summary;
        this.createdAt = createdAt;
    }
}


