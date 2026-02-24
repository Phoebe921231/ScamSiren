package com.example.fp2.model;

public class HistoryItem {

    // 類型常數
    public static final int TYPE_URL = 0;
    public static final int TYPE_AUDIO = 1;
    public static final int TYPE_IMAGE = 2;
    public static final int TYPE_TEXT  = 3;

    private long id;            // ⭐ 對應 Room 的 primary key
    private int type;           // 類型 (0=網址, 1=錄音, 2=圖片)
    private String title;       // 標題
    private String riskLevel;   // 風險 (高/中/低)
    private String date;        // 日期 (顯示用)
    private String content;     // 原始內容 (網址 / 文字 / 檔名)
    private String resultDetail;// 詳細結果摘要


    // ✅ 新的建構子（從 Room 轉過來用）
    public HistoryItem(long id,
                       int type,
                       String title,
                       String riskLevel,
                       String date,
                       String content,
                       String resultDetail){
        this.id = id;
        this.type = type;
        this.title = title;
        this.riskLevel = riskLevel;
        this.date = date;
        this.content = content;
        this.resultDetail = resultDetail;
    }

    // ===== Getter =====
    public long getId() {
        return id;
    }

    public int getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getDate() {
        return date;
    }

    public String getContent() {
        return content;
    }

    public String getResultDetail() {
        return resultDetail;
    }
}
