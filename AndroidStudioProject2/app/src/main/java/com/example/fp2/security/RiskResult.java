package com.example.fp2.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RiskResult {
    public final String url;
    public final String verdict;
    public final int score;
    public final List<String> reasons;
    public final String summary;
    public final String advice;

    public RiskResult(String url, String verdict, int score, List<String> reasons, String summary, String advice) {
        this.url = url;
        this.verdict = verdict;
        this.score = score;
        this.reasons = reasons != null ? Collections.unmodifiableList(new ArrayList<>(reasons)) : Collections.emptyList();
        this.summary = summary == null ? "" : summary;
        this.advice = advice == null ? "" : advice;
    }

    @Deprecated
    public RiskResult(String url, String verdict, int score, List<String> reasons) {
        this(url, verdict, score, reasons, "", "");
    }
}
