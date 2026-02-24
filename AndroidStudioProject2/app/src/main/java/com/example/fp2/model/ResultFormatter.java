package com.example.fp2.model;

import android.text.TextUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;

public final class ResultFormatter {
    private ResultFormatter(){}

    public static String format(ApiResponse r){
        if (r == null) return "無結果";

        // -------------------------
        // 1) 風險（一定有）
        // -------------------------
        String risk = safe(r.risk).toLowerCase(Locale.ROOT).trim();
        if (risk.isEmpty()) risk = r.is_scam ? "high" : "low";

        String riskWord = riskWord(risk);
        String riskIcon = riskIcon(risk);

        // -------------------------
        // 2) 取得詐騙類型
        // -------------------------
        List<String> cats = new ArrayList<>();

        if (r.analysis != null && r.analysis.isJsonObject()){
            JsonObject a = r.analysis.getAsJsonObject();
            cats = arr(a, "matched_categories");
        }

        String typeLine = buildTypeLine(r, cats);

        // -------------------------
        // 3) 建議（一定有）
        // -------------------------
        List<String> adv = buildAdvicesAlways(risk, r);

        // -------------------------
        // 固定只輸出三段
        // -------------------------
        StringBuilder sb = new StringBuilder();

        // ① 風險
        sb.append(riskIcon).append(" 風險：").append(riskWord).append("\n\n");

        // ② 詐騙類型
        sb.append("\uD83E\uDDE9詐騙類型：").append(typeLine).append("\n\n");

        // ③ 建議（最後）
        sb.append("⚠\uFE0F建議作為：\n");
        for (String a : adv){
            if (a != null && !a.trim().isEmpty()){
                sb.append("- ").append(a.trim()).append("\n");
            }
        }

        return sb.toString().trim();
    }

    // -------------------------
    // 詐騙類型（一定非空）
    // -------------------------
    private static String buildTypeLine(ApiResponse r, List<String> cats){

        // 優先使用後端 scam_type
        if (r.scam_type != null && !r.scam_type.isEmpty()){
            List<String> cleaned = new ArrayList<>();
            for (String s : r.scam_type){
                if (s != null){
                    String t = s.trim();
                    if (!t.isEmpty()) cleaned.add(t);
                }
            }
            if (!cleaned.isEmpty()){
                if (cleaned.size() > 3) cleaned = cleaned.subList(0, 3);
                return TextUtils.join("、", cleaned);
            }
        }

        // fallback
        if (!cats.isEmpty()){
            return TextUtils.join("、", cats);
        }

        return "未明確分類（需更多資訊）";
    }

    // -------------------------
    // 建議一定有
    // -------------------------
    private static List<String> buildAdvicesAlways(String risk, ApiResponse r){
        List<String> adv;

        if ("low".equals(risk)){
            adv = new ArrayList<>();
            adv.add("若仍有疑慮，建議致電 165 反詐騙或聯繫銀行客服進一步查證。");
        } else {
            adv = (r.advices != null && !r.advices.isEmpty())
                    ? r.advices
                    : defaultsByRisk(risk);
        }

        if (adv == null || adv.isEmpty()){
            adv = new ArrayList<>();
            adv.add("建議先停止操作，並自行撥打 165 或官方客服查證。");
        }

        return dedup(adv);
    }

    private static String riskWord(String risk){
        switch (risk){
            case "high":   return "高";
            case "medium": return "中";
            default:       return "低";
        }
    }

    private static String riskIcon(String risk){
        switch (risk){
            case "high":   return "🔴";
            case "medium": return "🟠";
            default:       return "🟢";
        }
    }

    private static List<String> arr(JsonObject o, String key){
        List<String> out = new ArrayList<>();
        if (o.has(key) && o.get(key).isJsonArray()){
            JsonArray a = o.getAsJsonArray(key);
            for (JsonElement e : a){
                if (e.isJsonObject()){
                    JsonObject jo = e.getAsJsonObject();
                    if (jo.has("name")){
                        out.add(jo.get("name").getAsString());
                    } else if (jo.has("code")){
                        out.add(jo.get("code").getAsString());
                    }
                }
            }
        }
        return out;
    }

    private static List<String> defaultsByRisk(String risk){
        ArrayList<String> out = new ArrayList<>();
        if ("high".equals(risk)){
            out.add("請立即結束通話與所有操作。");
            out.add("請勿提供任何驗證碼或帳戶資訊。");
            out.add("請改由本人主動撥打 165 或銀行客服查證。");
        } else if ("medium".equals(risk)){
            out.add("請避免提供個資或驗證碼，並保留相關紀錄。");
            out.add("建議撥打 165 或銀行客服確認。");
        } else {
            out.add("若仍有疑慮，建議致電 165 反詐騙或聯繫銀行客服進一步查證。");
        }
        return out;
    }

    private static String safe(String s){
        return s == null ? "" : s;
    }

    private static List<String> dedup(List<String> xs){
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String x : xs){
            if (x != null && !x.trim().isEmpty()){
                set.add(x.trim());
            }
        }
        return new ArrayList<>(set);
    }
}