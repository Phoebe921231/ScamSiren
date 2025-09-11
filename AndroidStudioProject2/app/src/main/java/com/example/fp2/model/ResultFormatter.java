package com.example.fp2.model;

import android.text.TextUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.regex.Pattern;

public final class ResultFormatter {
    private ResultFormatter(){}

    public static String format(ApiResponse r){
        if (r == null) return "無結果";
        String risk = safe(r.risk).toLowerCase(Locale.ROOT);
        if (risk.isEmpty()) risk = r.is_scam ? "high" : "low";

        String riskWord = riskWord(risk);
        String riskIcon = riskIcon(risk);

        List<String> cats = new ArrayList<>();
        List<String> acts = new ArrayList<>();
        if (r.analysis != null && r.analysis.isJsonObject()){
            JsonObject a = r.analysis.getAsJsonObject();
            cats = arr(a, "matched_categories");
            acts = arr(a, "actions_requested");
        }

        if (cats.isEmpty() && acts.isEmpty()){
            Scan s = localScan(r.text);
            cats = s.cats;
            acts = s.acts;
        }

        List<String> catsZh = new ArrayList<>();
        for (String c : cats){
            String k = c == null ? "" : c.trim().toLowerCase(Locale.ROOT);
            catsZh.add(CAT_ZH.getOrDefault(k, c));
        }

        LinkedHashSet<String> lines = new LinkedHashSet<>();
        lines.add(riskIcon + " 風險：" + riskWord);

        String type = inferType(cats);
        if (!type.isEmpty()) lines.add("可能類型：" + type);

        if (!catsZh.isEmpty()) lines.add("命中關鍵：" + TextUtils.join("、", catsZh));
        if (!acts.isEmpty())  lines.add("對方要求：" + TextUtils.join("、", mapActs(acts)));

        List<String> adv;
        if ("low".equals(risk)) {
            adv = new ArrayList<>();
            adv.add("若仍有疑慮，建議致電 165 反詐騙或聯繫銀行客服進一步查證。");
        } else {
            adv = (r.advices != null && !r.advices.isEmpty()) ? r.advices : defaultsByRisk(risk);
        }
        if (!adv.isEmpty()) lines.add("建議：" + TextUtils.join("、", dedup(adv)));

        return TextUtils.join("\n", lines);
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
                if (e.isJsonPrimitive()){
                    String s = e.getAsString();
                    if (s != null && !s.trim().isEmpty()) out.add(s.trim());
                }
            }
        }
        return out;
    }

    private static String inferType(List<String> cats){
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String c : cats){
            String k = c == null ? "" : c.trim().toLowerCase(Locale.ROOT);
            set.add(k);
        }
        if (set.contains("customs_scam")) return "假海關/包裹稅金";
        if (set.contains("supervisor_account")) return "監管帳戶/安全帳戶";
        if (set.contains("remote_control")) return "遠端連線詐騙";
        if (set.contains("atm_operation")) return "ATM 操作詐騙";
        if (set.contains("otp_harvest")) return "驗證碼/OTP 詐騙";
        if (set.contains("qr_scan")) return "QR 掃碼詐騙";
        if (set.contains("line_add")) return "加 LINE 引導";
        if (set.contains("unfreeze_installments")) return "解除分期/解凍名義";
        if (set.contains("install_app")) return "誘導安裝 App";
        return "";
    }

    private static List<String> mapActs(List<String> in){
        List<String> out = new ArrayList<>();
        for (String k : in){
            String v = ACT_ZH.get(k);
            out.add(v == null ? k : v);
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

    private static String safe(String s){ return s == null ? "" : s; }

    private static List<String> dedup(List<String> xs){
        return new ArrayList<>(new LinkedHashSet<>(xs));
    }

    private static final Map<String,String> CAT_ZH = new HashMap<String,String>() {{
        put("otp_harvest", "OTP/驗證碼");
        put("atm_operation", "ATM 操作");
        put("line_add", "加入 LINE");
        put("remote_control", "遠端連線");
        put("qr_scan", "QR 掃碼");
        put("supervisor_account", "監管/安全帳戶");
        put("customs_scam", "海關/關務");
        put("urgency_keep_line", "保持通話/限時");
        put("install_app", "安裝 App");
        put("small_test", "小額測試");
        put("unfreeze_installments", "解除分期/解凍");
    }};

    private static final Map<String,String> ACT_ZH = new HashMap<String,String>() {{
        put("要求提供OTP", "提供驗證碼");
        put("要求操作ATM", "操作 ATM");
        put("要求加LINE", "加入 LINE");
        put("要求匯款轉帳", "匯款/轉帳");
        put("要求安裝遠端軟體", "安裝遠端軟體");
    }};

    private static final Map<String, Pattern> P_CAT = new LinkedHashMap<String, Pattern>() {{
        put("atm_operation", Pattern.compile("atm|自動?櫃(員)?機|提款機|到 ?atm ?操作", Pattern.CASE_INSENSITIVE));
        put("otp_harvest", Pattern.compile("otp|一次性(密碼|驗證碼)|簡訊(驗證)?碼|驗證碼", Pattern.CASE_INSENSITIVE));
        put("remote_control", Pattern.compile("遠端(協助|連線)|teamviewer|anydesk|螢幕共享", Pattern.CASE_INSENSITIVE));
        put("supervisor_account", Pattern.compile("監管(帳(號|戶)|專戶)|安全帳戶|指定帳戶", Pattern.CASE_INSENSITIVE));
        put("customs_scam", Pattern.compile("海關|關務|清關|關稅|包裹(暫扣|逾期)", Pattern.CASE_INSENSITIVE));
        put("qr_scan", Pattern.compile("qr|二維碼|掃碼|掃描", Pattern.CASE_INSENSITIVE));
        put("line_add", Pattern.compile("加(入|到)?.*line|加賴|line\\s*id", Pattern.CASE_INSENSITIVE));
        put("unfreeze_installments", Pattern.compile("解除分期|解凍|凍結|鎖定", Pattern.CASE_INSENSITIVE));
        put("small_test", Pattern.compile("小額測試|測試轉帳", Pattern.CASE_INSENSITIVE));
        put("install_app", Pattern.compile("安裝.*app|下載.*app", Pattern.CASE_INSENSITIVE));
        put("urgency_keep_line", Pattern.compile("不要掛斷|保持通話|限時|逾期|立即(處理|辦理)|立刻", Pattern.CASE_INSENSITIVE));
    }};

    private static final Map<String, Pattern> P_ACT = new LinkedHashMap<String, Pattern>() {{
        put("要求提供OTP", Pattern.compile("(提供|告知).*(otp|驗證碼|簡訊碼|一次性密碼)", Pattern.CASE_INSENSITIVE));
        put("要求操作ATM", Pattern.compile("到.*atm|解除.*分期|跨行轉帳", Pattern.CASE_INSENSITIVE));
        put("要求加LINE", Pattern.compile("加(入|到)?.*line|加賴", Pattern.CASE_INSENSITIVE));
        put("要求匯款轉帳", Pattern.compile("(匯|轉)款.*給|轉入指定帳戶|匯入指定帳戶", Pattern.CASE_INSENSITIVE));
        put("要求安裝遠端軟體", Pattern.compile("teamviewer|anydesk|遠端(協助|安裝|控制)", Pattern.CASE_INSENSITIVE));
    }};

    private static Scan localScan(String text){
        LinkedHashSet<String> cats = new LinkedHashSet<>();
        LinkedHashSet<String> acts = new LinkedHashSet<>();
        String t = text == null ? "" : text;
        for (Map.Entry<String,Pattern> e : P_CAT.entrySet()){
            if (e.getValue().matcher(t).find()) cats.add(e.getKey());
        }
        for (Map.Entry<String,Pattern> e : P_ACT.entrySet()){
            if (e.getValue().matcher(t).find()) acts.add(e.getKey());
        }
        Scan s = new Scan();
        s.cats = new ArrayList<>(cats);
        s.acts = new ArrayList<>(acts);
        return s;
    }

    private static class Scan {
        List<String> cats = Collections.emptyList();
        List<String> acts = Collections.emptyList();
    }
}


