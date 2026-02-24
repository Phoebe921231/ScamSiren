import os, re, uuid, json, tempfile, unicodedata, subprocess, shutil, requests, time

from typing import List, Dict, Optional



from flask import Flask, request, jsonify

from flask_cors import CORS



from stats_db import init_db, insert_stat



print("### ScamSiren backend loaded (rules + ollama + whisper) ###")



# -------------------------

# ✅ 簡轉繁（不影響英文；OpenCC 不存在也不會壞）

# 只會用在「輸出 detected_text/text 前」做轉換

# -------------------------

try:

    from opencc import OpenCC

    _cc = OpenCC("s2t")  # 簡體->繁體（通用）

except Exception:

    _cc = None



def to_trad(s: str) -> str:

    if not s:

        return s

    if _cc is None:

        return s

    try:

        return _cc.convert(s)

    except Exception:

        return s



# -------------------------

# Config

# -------------------------

OLLAMA_HOST = os.getenv("OLLAMA_HOST", "http://127.0.0.1:11434")

SCAM_MODEL  = os.getenv("SCAM_MODEL", "scam-guard")



ASR_MODEL   = os.getenv("ASR_MODEL", "small")

ASR_DEVICE  = os.getenv("ASR_DEVICE", "cpu")

ASR_COMPUTE = os.getenv("ASR_COMPUTE", "int8")



MAX_CONTENT_MB = int(os.getenv("MAX_CONTENT_MB", "25"))

UPLOAD_DIR = os.getenv("UPLOAD_DIR", "uploads")

os.makedirs(UPLOAD_DIR, exist_ok=True)



# Ollama timeouts

OLLAMA_CONNECT_TIMEOUT = int(os.getenv("OLLAMA_CONNECT_TIMEOUT", "10"))

OLLAMA_READ_TIMEOUT    = int(os.getenv("OLLAMA_READ_TIMEOUT", "180"))



# Text budget for LLM (避免太長 timeout)

LLM_MAX_CHARS = int(os.getenv("LLM_MAX_CHARS", "500"))



app = Flask(__name__)

init_db()



app.config["JSON_AS_ASCII"] = False

try:

    app.json.ensure_ascii = False

except Exception:

    pass



CORS(app, supports_credentials=True)

app.config["MAX_CONTENT_LENGTH"] = MAX_CONTENT_MB * 1024 * 1024



# -------------------------

# Utils

# -------------------------

def _norm(s: str) -> str:

    # NFKC: 全形半形統一；lower 不影響中文但對英文有用

    s = unicodedata.normalize("NFKC", s).lower()

    # 統一空白

    s = re.sub(r"\s+", " ", s).strip()

    return s



def _has_any(text: str, patterns: List[str]) -> bool:

    return any(re.search(p, text, re.I) for p in patterns)



# -------------------------

# Scam patterns (Taiwan context)

# -------------------------

PATTERNS = {

    # ===== 高風險核心（台灣最常見）=====

    "otp_harvest": r"(otp|o\W*t\W*p|一次性(密碼|驗證碼|验证码)|動態密碼|簡訊(驗證)?碼|驗證碼|验证码|授權碼|安全碼|verification\s*code)",

    "atm_operation": r"(atm|自動?櫃(員)?機|提款機|讀卡機|读卡机|到\s*atm\s*操作|去\s*atm|臨?櫃|柜台|跨行轉帳|跨行转账|解除分期|取消分期)",

    "remote_control": r"(遠端(協助|連線|控制|操作)|远端(协助|连线|控制)|teamviewer|anydesk|rustdesk|向日葵|splashtop|螢幕共享|屏幕共享|共享螢幕|共享屏幕)",

    "supervisor_account": r"(監管(帳(號|戶)|专户|專戶)|监管(账(号|户)|专户|專戶)|安全帳(號|戶)|安全账户|安全帳戶|安全帐户|安全專戶|安全专户|指定帳(號|戶)|指定账户|指定帳戶|黑名單帳戶|黑名单账户)",

    "money_laundering": r"(洗錢|洗钱|金流異常|资金异常|資金異常|涉案|涉嫌|犯罪所得|反洗錢|aml)",

    "freeze_threat": r"(凍結|冻结|停用|限制使用|封鎖|封锁|鎖卡|锁卡|停卡|停用帳戶|账户将被|帳戶將被)",

    "transfer_money": r"(轉帳|转账|匯款|汇款|匯入|汇入|轉入|转入|打款|入金|出金|提領|提现)",



    # ✅ NEW：金流/個資/收款（你要的新類別）

    # 目標：對方要求「提供銀行帳號/個資」或「付款/支付/繳費/匯款到指定帳戶」

    "payment_personal_info": r"(提供.*(身分證|身份证|證件|證號|证号|姓名|住址|地址|電話|手机号|手機號|生日|戶籍|账号|帳號|账户|帳戶|銀行帳號|银行账号|卡號|卡号|信用卡|cvv|安全碼|有效期限|密碼|密码)|"

                           r"(匯款|汇款|轉帳|转账|付款|支付|繳費|缴费|刷卡|收款|入金|出金|轉入|转入|匯入|汇入).*(指定|我的|本|該|该).*(帳戶|账户|銀行|银行|卡|平台|連結|链接))",



    # ===== 中風險（常見引導/社工）=====

    "line_add": r"(加(入|到)?(官方)?\s*line|加賴|加line|line\s*id|line客服|加入.*line.*客服|加.*客服line)",

    "qr_scan": r"(qr|qrcode|二維碼|二维码|條碼|扫码|掃碼|掃描.*(驗證|支付|登入|登录)|掃一下|扫一下)",

    "urgency_keep_line": r"(不要掛斷|不要挂断|保持通話|保持通话|先不要掛|別掛|别挂|限時|逾期|立即處理|立刻處理|馬上處理|转接专员|轉接專員|一級保密|一级保密|不要告訴任何人|不要告诉任何人)",

    "install_app": r"(安裝(應用|app)|安装(应用|app)|下載.*app|download.*app|下载.*app|點連結下載|點擊連結下載|授權.*存取|开启权限|開啟權限|允許權限)",

    "customs_scam": r"(關務|海關|海关|清關|清关|關稅|关税|違禁品|限制品|包裹(暫扣|逾期|卡關)|关务专员|關務專員|物流异常|物流異常)",

    "fake_police": r"(警察|警方|刑警|偵查隊|检察官|檢察官|法院|法官|傳票|拘票|通緝|通缉|偵辦|侦办)",

    "investment_scam": r"(投資|投资|飆股|飙股|老師帶單|带单|群組|群组|保證獲利|保证获利|高報酬|高回报|內線|内幕|虛擬貨幣|虚拟货币|usdt|比特幣|比特币|入金|出金)",

    "romance_scam": r"(交友|感情|戀愛|恋爱|網戀|网恋|真心|結婚|结婚|匯生活費|汇生活费|急用錢|急用钱|幫我周轉|帮我周转)",

    "parcel_refund": r"(退款|退費|退货|退貨|重複扣款|重复扣款|訂單異常|订单异常|客服|退款流程|理賠|理赔)",

    "small_test": r"(小額測試|小额测试|測試轉帳|测试转账|驗證金額|验证金额)",

}



ACTIONS_PAT = {

    "要求提供OTP": r"(提供|告知|說出|输入|輸入).*(otp|驗證碼|验证码|簡訊碼|一次性密碼|授權碼|安全碼)",

    "要求操作ATM": r"((到|前往|去).*(atm|提款機|自動櫃員機|讀卡機|柜台)|解除.*分期|取消.*分期|跨行轉帳|跨行转账)",

    "要求加LINE": r"(加(入|到)?\s*line|加賴|line\s*id|line客服)",

    "要求匯款轉帳": r"(匯款|汇款|轉帳|转账|匯入|转入|打款|入金|出金|轉入指定|匯入指定)",

    "要求安裝遠端": r"(teamviewer|anydesk|rustdesk|向日葵|遠端(協助|控制|連線)|远端(协助|控制|连线))",

    "要求掃QR": r"(掃(描)?\s*(qr|二維碼|二维码|條碼)|扫码|掃一下|扫一下)",



    # ✅ NEW：要求提供銀行帳號/個資/付款（對應你新增類別）

    "要求提供銀行帳號或個資/付款": r"(提供|告知|傳|发|发送|填寫|填写).*(身分證|身份证|姓名|住址|地址|電話|手机号|手機號|帳號|账号|帳戶|账户|銀行帳號|银行账号|卡號|卡号|信用卡|cvv|安全碼|有效期限)|"

                            r"(付款|支付|繳費|缴费|匯款|汇款|轉帳|转账).*(指定|我的|本|該|该).*(帳戶|账户|銀行|银行)",

}



FLOOR = {

    "otp_harvest": "high",

    "atm_operation": "high",

    "remote_control": "high",

    "supervisor_account": "high",

    "money_laundering": "high",

    "freeze_threat": "high",

    "transfer_money": "high",



    # ✅ NEW：高風險

    "payment_personal_info": "high",



    "line_add": "medium",

    "qr_scan": "medium",

    "urgency_keep_line": "medium",

    "install_app": "medium",

    "customs_scam": "medium",

    "fake_police": "medium",

    "investment_scam": "medium",

    "romance_scam": "medium",

    "parcel_refund": "medium",

    "small_test": "medium",

}



RANK = {"low": 0, "medium": 1, "high": 2}

REV  = {0: "low", 1: "medium", 2: "high"}



# ✅ 這裡的文字就是你「詐騙類型」的顯示名稱（全部都有對應）

CATEGORY_NAME_ZH = {

    "otp_harvest": "驗證碼/OTP 詐騙",

    "atm_operation": "ATM 操作詐騙",

    "remote_control": "遠端控制/螢幕共享詐騙",

    "supervisor_account": "安全帳戶/監管帳戶詐騙",

    "money_laundering": "涉洗錢/涉案恐嚇詐騙",

    "freeze_threat": "帳戶凍結/停用恐嚇詐騙",

    "transfer_money": "匯款/轉帳詐騙",



    # ✅ NEW

    "payment_personal_info": "金流/個資/收款詐騙",



    "line_add": "引導加 LINE 客服詐騙",

    "qr_scan": "QR 掃碼/條碼詐騙",

    "urgency_keep_line": "催促保密/不准掛斷詐騙",

    "install_app": "誘導安裝 App/開權限詐騙",

    "customs_scam": "假海關/包裹清關詐騙",

    "fake_police": "假檢警/法院詐騙",

    "investment_scam": "假投資/帶單/虛擬幣詐騙",

    "romance_scam": "交友戀愛/感情勒索詐騙",

    "parcel_refund": "假客服退款/訂單異常詐騙",

    "small_test": "小額測試/驗證轉帳詐騙",

}



ADVICE_BY_CATEGORY = {

    "supervisor_account": ["銀行/檢調不會要你轉到『安全帳戶/監管帳戶』", "立即掛斷，改由你自己撥打銀行官方客服或 165 查證"],

    "atm_operation": ["任何要求你去 ATM 操作、解除分期、設定都是高風險", "立刻停止操作並致電 165 或銀行官方客服"],

    "otp_harvest": ["不要提供任何簡訊驗證碼/OTP/授權碼", "若已提供，立刻改密碼並通知銀行與 165"],

    "remote_control": ["不要安裝遠端控制或開螢幕共享給陌生人", "若已安裝，立刻斷網/移除並掃毒、聯絡銀行"],

    "investment_scam": ["不要把錢轉進不明平台/群組指定帳戶", "截圖保留對話與轉帳紀錄，盡快報警或 165"],

    "fake_police": ["警方/檢調不會在電話中要求匯款或提供驗證碼", "掛斷後自行撥打 110/165 或地方法院查證"],

    "parcel_refund": ["客服退款不會要求你操作 ATM 或提供 OTP", "請到官方 APP/網站查訂單，不要點不明連結"],



    # ✅ NEW：金流/個資

    "payment_personal_info": ["不要提供銀行帳號/卡號/身分證/驗證碼等個資", "不要依指示付款或匯款到對方指定帳戶，請改由你自行撥打 165 或銀行官方客服查證"],

}



# -------------------------

# ✅ NEW: 單一詐騙類型分類器（scam_type 永遠只有 1 個，且永遠有）

# -------------------------

def build_scam_type_single(cats: List[str], acts: List[str]) -> List[str]:

    """

    回傳單一、明確的詐騙類型（繁中），永遠 1 個。

    優先以規則命中 cats（最穩最一致），再參考 acts。

    """

    if not cats and not acts:

        return ["未明確分類（需更多資訊）"]



    cats_set = set(cats or [])



    # ✅ 你可以視覺上理解成「最像你想要的明確標籤」優先順序

    priority_order = [

        "fake_police",

        "money_laundering",

        "supervisor_account",

        "freeze_threat",



        "payment_personal_info",  # NEW：金流/個資/收款（很關鍵）

        "atm_operation",

        "remote_control",

        "otp_harvest",



        "investment_scam",

        "romance_scam",

        "customs_scam",

        "parcel_refund",



        "transfer_money",

        "install_app",

        "qr_scan",

        "line_add",

        "urgency_keep_line",

        "small_test",

    ]



    for key in priority_order:

        if key in cats_set:

            return [CATEGORY_NAME_ZH.get(key, key)]



    # 如果 cats 沒有命中，但 acts 有命中，做一個保底映射

    acts_text = " ".join(acts or [])

    if "OTP" in acts_text or "otp" in acts_text.lower():

        return [CATEGORY_NAME_ZH["otp_harvest"]]

    if "ATM" in acts_text or "atm" in acts_text.lower():

        return [CATEGORY_NAME_ZH["atm_operation"]]

    if "遠端" in acts_text or "远端" in acts_text:

        return [CATEGORY_NAME_ZH["remote_control"]]

    if "匯款" in acts_text or "汇款" in acts_text or "轉帳" in acts_text or "转账" in acts_text:

        return [CATEGORY_NAME_ZH["transfer_money"]]



    return ["其他可疑詐騙"]



# -------------------------

# Rule check

# -------------------------

def rule_check(text: str):

    # ✅ 不做簡轉繁；直接用原文做規則判斷（你要求「不要動判斷流程」）

    t = _norm(text or "")



    cats, acts = [], []



    for k, p in PATTERNS.items():

        if re.search(p, t, re.I):

            cats.append(k)



    for k, p in ACTIONS_PAT.items():

        if re.search(p, t, re.I):

            acts.append(k)



    floor = "low"

    for c in cats:

        floor = REV[max(RANK[floor], RANK.get(FLOOR.get(c, "low"), 0))]



    # 組合升級：只要同時命中「轉帳 + 安全帳戶/監管」或「涉洗錢 + 凍結/轉帳」或「假警察 + 轉帳/OTP」 → high

    if (

        ("transfer_money" in cats and "supervisor_account" in cats)

        or ("money_laundering" in cats and ("freeze_threat" in cats or "transfer_money" in cats))

        or ("fake_police" in cats and ("transfer_money" in cats or "otp_harvest" in cats))

        or ("payment_personal_info" in cats and ("transfer_money" in cats or "otp_harvest" in cats))

    ):

        floor = "high"



    # 多個高風險訊號

    high_hits = [c for c in cats if FLOOR.get(c) == "high"]

    if len(high_hits) >= 2:

        floor = "high"



    # 很短但有命中 → 至少 medium（避免一句話就漏掉）

    if len(t) < 12 and (cats or acts) and floor == "low":

        floor = "medium"



    return cats, acts, floor



# -------------------------

# ffmpeg + whisper

# -------------------------

def ffmpeg_bin():

    b = os.getenv("FFMPEG_BIN") or "ffmpeg"

    return b if shutil.which(b) else None


def to_wav_16k(in_path: str) -> str:

    bin_ = ffmpeg_bin()

    if not bin_:

        raise RuntimeError("ffmpeg 不可用（找不到 ffmpeg 指令）")

    out_path = os.path.join(tempfile.gettempdir(), f"asr_{uuid.uuid4().hex}.wav")

    cmd = [bin_, "-y", "-nostdin", "-hide_banner", "-loglevel", "error",

           "-i", in_path, "-ac", "1", "-ar", "16000", "-sample_fmt", "s16", out_path]

    r = subprocess.run(cmd, capture_output=True, text=True)

    if r.returncode != 0 or not os.path.exists(out_path):

        raise RuntimeError(r.stderr.strip() or "ffmpeg 轉檔失敗")

    return out_path



_whisper = None

def transcribe(filepath: str) -> str:

    global _whisper

    if _whisper is None:

        from faster_whisper import WhisperModel

        _whisper = WhisperModel(ASR_MODEL, device=ASR_DEVICE, compute_type=ASR_COMPUTE)

    segs, _ = _whisper.transcribe(filepath, language=None, vad_filter=True, beam_size=1, condition_on_previous_text=False)

    text = "".join(s.text for s in segs).strip()



    # ✅ 不在這裡轉繁（你要求只在輸出前轉）

    return text



# -------------------------

# Ollama

# -------------------------

PROMPT = (

    "You are a Taiwan-context scam detector.\n"

    "Task:\n"

    "1) Decide is_scam (true/false)\n"

    "2) risk must be one of: high, medium, low\n"

    "3) Provide concise reasons (Traditional Chinese)\n"

    "4) Provide actionable advices (Traditional Chinese)\n"

    "Rules:\n"

    "- If any red flag appears (ATM, OTP, add LINE, remote control, QR scan, safe/supervisor account, police/prosecutor, customs, investment group, payment request, personal info request), risk >= medium.\n"

    "- If two or more strong red flags appear (ATM/OTP/remote/safe-account/transfer/freeze threats/money laundering/payment+personal-info), risk=high.\n"

    'Output ONLY valid minified JSON: {"is_scam":bool,"risk":"high"|"medium"|"low","reasons":[string],"advices":[string]}'

)



def call_llm(text: str) -> dict:

    text = (text or "").strip()



    # ✅ 不在送入 LLM 前轉繁（你要求只在輸出偵測文字前轉）

    if len(text) > LLM_MAX_CHARS:

        text = text[:LLM_MAX_CHARS]



    payload = {

        "model": SCAM_MODEL,

        "prompt": f"{PROMPT}\nText:\n{text}\n",

        "format": "json",

        "options": {

            "temperature": 0.0,

            "top_p": 0.9,

            "num_predict": 256,

            "num_ctx": 2048,

            "seed": 42

        },

        "stream": False

    }



    r = requests.post(

        f"{OLLAMA_HOST}/api/generate",

        json=payload,

        timeout=(OLLAMA_CONNECT_TIMEOUT, OLLAMA_READ_TIMEOUT)

    )

    r.raise_for_status()

    raw = r.json().get("response", "")



    try:

        obj = json.loads(raw)

        if isinstance(obj, dict):

            if "risk" in obj and isinstance(obj["risk"], str):

                obj["risk"] = obj["risk"].strip().lower()

            return obj

    except Exception:

        pass



    m = re.search(r"\{.*\}", raw, re.S)

    if m:

        obj = json.loads(m.group(0))

        if isinstance(obj, dict):

            if "risk" in obj and isinstance(obj["risk"], str):

                obj["risk"] = obj["risk"].strip().lower()

            return obj



    return {"is_scam": False, "risk": "low", "reasons": [], "advices": []}



# -------------------------

# Fuse rules + model

# -------------------------

def build_advices(base_risk: str, cats: List[str], llm_obj: Dict):

    adv = list(llm_obj.get("advices", []) or [])



    if len(adv) < 2:

        for c in cats[:3]:

            adv.extend(ADVICE_BY_CATEGORY.get(c, []))

        if base_risk == "high":

            adv.extend(["立刻停止轉帳/操作並保留證據（對話、來電、匯款紀錄）", "請撥打 165 反詐騙或銀行官方客服自行查證"])

        elif base_risk == "medium":

            adv.extend(["請勿提供個資/驗證碼/帳戶資訊，先暫停所有操作", "建議撥打 165 或銀行官方客服查證"])

        else:

            adv.extend(["如仍不安心，建議自行撥打官方客服或 165 查證"])



    seen = set()

    out = []

    for x in adv:

        x = str(x).strip()

        if x and x not in seen:

            seen.add(x)

            out.append(x)

    return out[:6]



def fuse(

    text: str,

    llm_obj: Dict,

    cats: List[str],

    acts: List[str],

    floor: str,

    model_used: bool,

    model_error: Optional[str],

    t_model_ms: Optional[int]

):

    mr = str(llm_obj.get("risk", "low")).lower()

    if mr not in ("low", "medium", "high"):

        mr = "low"



    base = REV[max(RANK.get(floor, 0), RANK.get(mr, 0))]



    # ✅ risk 保底（防呆）

    if base not in ("low", "medium", "high"):

        base = "low"



    is_scam = bool(llm_obj.get("is_scam", False)) or (base != "low")



    reasons = list(llm_obj.get("reasons", []) or [])

    if cats:

        zh = [CATEGORY_NAME_ZH.get(c, c) for c in cats]

        reasons.insert(0, "命中風險類別：" + "、".join(zh[:6]))



    if floor == "high":

        reasons.append("規則判定：命中高風險關鍵規則")

    elif floor == "medium":

        reasons.append("規則判定：命中中風險關鍵規則")



    seen = set()

    r2 = []

    for x in reasons:

        x = str(x).strip()

        if x and x not in seen:

            seen.add(x)

            r2.append(x)

    reasons = r2[:8]



    # ✅ advices 一定有

    advices = build_advices(base, cats, llm_obj)

    if not advices:

        advices = ["建議先停止操作，並自行撥打 165 或官方客服查證"]



    # ✅ NEW：scam_type 一定有、且只會 1 個（明確單一）

    scam_type = build_scam_type_single(cats, acts)



    # -------------------------

    # ✅ 你要的改動：只在「輸出偵測到的文字」前做簡轉繁

    # -------------------------

    text_out = to_trad(text)



    return {

        "text": text_out,                 # 保留：舊版可用

        "detected_text": text_out,        # 前端統一吃這個（OCR/ASR 都用）

        "risk": base,                     # 一定有

        "scam_type": scam_type,           # ✅ 一定有、且單一

        "advices": advices,               # 一定有

        "is_scam": is_scam,

        "reasons": reasons,

        "analysis": {

            "matched_categories": [{"code": c, "name": CATEGORY_NAME_ZH.get(c, c)} for c in sorted(set(cats))],

            "actions_requested": sorted(set(acts)),

            "rule_floor": floor

        },

        "meta": {

            "asr_backend": "whisper",

            "asr_model": ASR_MODEL,

            "ollama_model": SCAM_MODEL,

            "model_used": model_used,

            "model_error": model_error if model_error else None,

            "model_time_ms": t_model_ms

        }

    }



def decide(text: str):

    t0 = time.time()

    text = (text or "").strip()



    cats, acts, floor = rule_check(text)



    llm_obj = {"is_scam": False, "risk": "low", "reasons": [], "advices": []}

    model_used = False

    model_error = None

    t_model_ms = None



    should_call_model = (floor != "high")



    if should_call_model:

        try:

            t1 = time.time()

            llm_obj = call_llm(text)

            model_used = True

            t_model_ms = int((time.time() - t1) * 1000)

        except Exception as e:

            model_error = str(e)



    if not model_used:

        llm_obj = {

            "is_scam": (floor != "low"),

            "risk": floor,

            "reasons": [],

            "advices": []

        }



    result = fuse(text, llm_obj, cats, acts, floor, model_used, model_error, t_model_ms)

    result["meta"]["total_time_ms"] = int((time.time() - t0) * 1000)



    # ===== 儲存統計資料（不含任何個資）=====

    try:

        insert_stat(

            source="text",

            risk=result["risk"],

            is_scam=result["is_scam"],

            categories=[c["code"] for c in result["analysis"]["matched_categories"]],

            actions=result["analysis"]["actions_requested"],

            model_used=result["meta"]["model_used"],

            total_time_ms=result["meta"]["total_time_ms"]

        )

    except Exception as e:

        print("DB insert failed:", e)



    return result



# -------------------------

# Routes

# -------------------------

@app.get("/")

def home():

    return jsonify({"message": "OK", "service": "ScamSiren backend"})



@app.post("/analyze_text")

def analyze_text():

    data = request.get_json(force=True, silent=True) or {}

    text = (data.get("text") or "").strip()



    # ✅ 不在這裡轉繁；只在輸出 detected_text/text 前轉

    if not text:

        return jsonify({

            "text": "",

            "detected_text": "",

            "risk": "low",

            "scam_type": ["未明確分類（需更多資訊）"],

            "advices": ["請提供文字"],

            "is_scam": False,

            "reasons": ["text required"],

            "source": "ocr",

            "analysis": {"matched_categories": [], "actions_requested": [], "rule_floor": "low"},

            "meta": {"asr_backend": "whisper", "asr_model": ASR_MODEL, "ollama_model": SCAM_MODEL, "model_used": False}

        }), 200



    out = decide(text)

    out["source"] = "ocr"

    return jsonify(out), 200



@app.post("/upload_audio")

def upload_audio():

    f = request.files.get("file")

    if not f or not f.filename:

        return jsonify({

            "text": "",

            "detected_text": "",

            "risk": "low",

            "scam_type": ["未明確分類（需更多資訊）"],

            "advices": ["請用 multipart/form-data，欄位名為 file"],

            "is_scam": False,

            "reasons": ["缺少音檔"],

            "source": "asr",

            "analysis": {"matched_categories": [], "actions_requested": [], "rule_floor": "low"},

            "meta": {"asr_backend": "whisper", "asr_model": ASR_MODEL, "ollama_model": SCAM_MODEL, "model_used": False}

        }), 200



    ext = os.path.splitext(f.filename)[1] or ".wav"

    raw_path = os.path.join(UPLOAD_DIR, f"{uuid.uuid4()}{ext}")

    f.save(raw_path)



    wav_path = None

    text = ""



    try:

        wav_path = to_wav_16k(raw_path)

        text = transcribe(wav_path)  # ✅ 不轉繁



        if not text or len(text) < 6:

            # ✅ 輸出 detected_text/text 仍然會是繁體（這裡也照你的要求只在輸出前轉）

            text_out = to_trad(text or "")

            return jsonify({

                "text": text_out,

                "detected_text": text_out,

                "risk": "low",

                "scam_type": ["未明確分類（需更多資訊）"],

                "advices": ["請提供更清晰或更長的音檔"],

                "is_scam": False,

                "reasons": ["辨識文字過短或空白"],

                "source": "asr",

                "analysis": {"matched_categories": [], "actions_requested": [], "rule_floor": "low"},

                "meta": {"asr_backend": "whisper", "asr_model": ASR_MODEL, "ollama_model": SCAM_MODEL, "model_used": False}

            }), 200



        out = decide(text)  # ✅ decide 內部不轉繁；輸出 detected_text/text 才轉

        out["source"] = "asr"

        return jsonify(out), 200



    except Exception as e:

        # ✅ 這裡 text/detected_text 也照規則只在輸出前轉

        text_out = to_trad(text or "")

        return jsonify({

            "text": text_out,

            "detected_text": text_out,

            "risk": "medium",

            "scam_type": ["未明確分類（需更多資訊）"],

            "advices": ["請先保留音檔與來電資訊", "建議撥打 165 反詐騙諮詢", "若可行，改用文字貼上再分析以取得更完整結果"],

            "is_scam": True,

            "reasons": [f"語音流程異常（轉檔/辨識/模型）：{str(e)}"],

            "source": "asr",

            "analysis": {"matched_categories": [], "actions_requested": [], "rule_floor": "medium"},

            "meta": {"error_stage": "ASR_or_LLM", "asr_backend": "whisper", "asr_model": ASR_MODEL, "ollama_model": SCAM_MODEL, "model_used": False}

        }), 200



    finally:

        try:

            if os.path.exists(raw_path):

                os.remove(raw_path)

            if wav_path and os.path.exists(wav_path):

                os.remove(wav_path)

        except Exception:

            pass



# -------------------------

# Main

# -------------------------

if __name__ == "__main__":

    port = int(os.environ.get("PORT", 5000))

    app.run(host="0.0.0.0", port=port, debug=True)
