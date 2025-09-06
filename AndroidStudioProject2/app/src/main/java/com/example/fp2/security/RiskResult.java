package com.example.fp2.security;

import java.util.List;

public class RiskResult {
    public final String url;
    public final String verdict;   // 高風險 / 中風險 / 低風險 / 未知
    public final int score;        // 0~100（urlscan 沒提供時用估值）
    public final List<String> reasons;

    public RiskResult(String url, String verdict, int score, List<String> reasons) {
        this.url = url;
        this.verdict = verdict;
        this.score = score;
        this.reasons = reasons;
    }
}