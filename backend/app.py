import os, re, uuid, json, tempfile, unicodedata, subprocess, shutil, requests
from flask import Flask, request, jsonify
from flask_cors import CORS

OLLAMA_HOST = os.getenv("OLLAMA_HOST", "http://127.0.0.1:11434")
SCAM_MODEL = os.getenv("SCAM_MODEL", "scam-guard")
ASR_MODEL = os.getenv("ASR_MODEL", "small")
ASR_DEVICE = os.getenv("ASR_DEVICE", "cpu")
ASR_COMPUTE = os.getenv("ASR_COMPUTE", "int8")
MAX_CONTENT_MB = int(os.getenv("MAX_CONTENT_MB", "25"))
UPLOAD_DIR = os.getenv("UPLOAD_DIR", "uploads")
os.makedirs(UPLOAD_DIR, exist_ok=True)

app = Flask(__name__)
app.config["JSON_AS_ASCII"] = False
try: app.json.ensure_ascii = False
except: pass
CORS(app, supports_credentials=True)
app.config["MAX_CONTENT_LENGTH"] = MAX_CONTENT_MB * 1024 * 1024

def _norm(s):
    s = unicodedata.normalize("NFKC", s).lower()
    return re.sub(r"\s+", " ", s)

PATTERNS = {
    "otp_harvest": r"(otp|o\W*t\W*p|一次性(密碼|驗證碼|验证码)|動態密碼|簡訊(驗證)?碼|驗證碼|验证码)",
    "atm_operation": r"(atm|自動?櫃(員)?機|提款機|讀卡機|读卡机|臨?櫃?轉帳|跨行转?帳|跨行转账|到 ?atm ?操作)",
    "line_add": r"(加(入|到)?(官方)?\s*line|加賴|line\s*id|加入.*line.*客服)",
    "remote_control": r"(遠端(協助|連線)|teamviewer|anydesk|remote|螢幕共享|安裝.*(控制|協助).*app|远端(协助|连线))",
    "qr_scan": r"(qr|二維碼|二维码|條碼|掃碼|掃描.*(驗證|支付|登入))",
    "supervisor_account": r"(監管(帳(號|戶)|專戶)|监管(账(号|户)|专户)|安全帳戶|安全账户|安全專戶|安全专户|指定帳戶|指定账户|黑名單帳戶|黑名单账户)",
    "freeze_protection": r"((資金|账户|帳戶).*(轉移|移轉|转移).*(保全|凍結|冻结)|凍結保全|冻结保全|臨時轉移|临时转移|臨時.*轉存|临时.*转存)",
    "customs_scam": r"(關務|海關|海关|清關|清关|關稅|关税|違禁品|限制品|包裹(暫扣|逾期)|關務專員|关务专员)",
    "urgency_keep_line": r"(不要掛斷|不要挂断|保持通話|保持通话|限時|逾期|立即(處理|辦理)|立刻|轉接專員|转接专员|身分(再次)?驗證|身份(再次)?验证|一級保密|一级保密|不要告訴任何人|不要告诉任何人)",
    "install_app": r"(安裝(應用|app)|安装(应用|app)|下載.*app|下载.*app|授權.*存取)",
    "small_test": r"(小額測試|小额测试|測試轉帳|测试转账)",
    "unfreeze_installments": r"(解除分期|解凍|解冻|凍結|冻结)"
}
ACTIONS_PAT = {
    "要求提供OTP": r"(提供|告知).*(otp|驗證碼|验证码|簡訊碼|一次性密碼)",
    "要求操作ATM": r"((到|前往).*(atm|自動?櫃(員)?機|提款機|讀卡機|读卡机)|解除.*分期|跨行转?帳|跨行转账)",
    "要求加LINE": r"(加(入|到)?.*line|加賴|加入.*line.*客服)",
    "要求匯款轉帳": r"((匯|转)款.*給|匯入指定(帳戶|账户)|轉入指定(帳戶|账户))",
    "要求安裝遠端軟體": r"(teamviewer|anydesk|遠端(協助|安裝|控制)|远端(协助|安装|控制))",
    "要求轉入安全專戶": r"(轉(移|入).*(安全(專|专)戶|監管|监管|專戶|专户|安全(帳|账)戶|安全账户)|資金.*(保全|凍結|冻结).*(轉移|移轉|转移))"
}
FLOOR = {
    "otp_harvest":"high","atm_operation":"high","remote_control":"high","supervisor_account":"high","freeze_protection":"high",
    "customs_scam":"medium","line_add":"medium","qr_scan":"medium","urgency_keep_line":"medium","install_app":"medium",
    "small_test":"medium","unfreeze_installments":"medium"
}
RANK = {"low":0,"medium":1,"high":2}
REV = {0:"low",1:"medium",2:"high"}

def rule_check(text):
    t = _norm(text)
    cats, acts = [], []
    for k, p in PATTERNS.items():
        if re.search(p, t, re.I): cats.append(k)
    for k, p in ACTIONS_PAT.items():
        if re.search(p, t, re.I): acts.append(k)
    floor = "low"
    for c in cats:
        floor = REV[max(RANK[floor], RANK[FLOOR.get(c, "low")])]
    if len([c for c in cats if FLOOR.get(c) == "high"]) >= 2:
        floor = "high"
    if len(t.split()) < 8 and (cats or acts) and floor == "low":
        floor = "medium"
    return cats, acts, floor

def ffmpeg_bin():
    b = os.getenv("FFMPEG_BIN") or "ffmpeg"
    return b if shutil.which(b) else None

def to_wav_16k(in_path):
    bin_ = ffmpeg_bin()
    if not bin_:
        raise RuntimeError("ffmpeg 不可用")
    out_path = os.path.join(tempfile.gettempdir(), f"asr_{uuid.uuid4().hex}.wav")
    cmd = [bin_, "-y", "-nostdin", "-hide_banner", "-loglevel", "error", "-i", in_path, "-ac", "1", "-ar", "16000", "-sample_fmt", "s16", out_path]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0 or not os.path.exists(out_path):
        raise RuntimeError(r.stderr.strip() or "ffmpeg 轉檔失敗")
    return out_path

_whisper = None
def transcribe(filepath):
    global _whisper
    if _whisper is None:
        from faster_whisper import WhisperModel
        _whisper = WhisperModel(ASR_MODEL, device=ASR_DEVICE, compute_type=ASR_COMPUTE)
    segs, _ = _whisper.transcribe(filepath, language=None, vad_filter=True, beam_size=1, condition_on_previous_text=False)
    return "".join(s.text for s in segs).strip()

PROMPT = (
    "You are a Taiwan-context scam detector. If any red flag appears "
    "(ATM operation, OTP request, adding LINE, remote control, QR scan, supervisor/safe account, customs officer, "
    "'do not hang up', unfreeze installments, small test), set is_scam=true and risk >= medium; "
    "if two or more high-risk flags (ATM/OTP/remote/supervisor account) appear, risk=high. "
    'Respond ONLY with valid minified JSON: {"is_scam":bool,"risk":"high"|"medium"|"low","reasons":[string],"advices":[string]}.'
)

def call_llm(text):
    payload = {
        "model": SCAM_MODEL,
        "prompt": f"{PROMPT}\nText:\n{text}\n",
        "format": "json",
        "options": {"temperature":0.0, "top_p":0.9, "num_predict":256, "num_ctx":2048, "seed":42},
        "stream": False
    }
    r = requests.post(f"{OLLAMA_HOST}/api/generate", json=payload, timeout=(5, 120))
    r.raise_for_status()
    raw = r.json().get("response", "")
    try:
        return json.loads(raw)
    except:
        m = re.search(r"\{.*\}", raw, re.S)
        return json.loads(m.group(0)) if m else {"is_scam": False, "risk": "low", "reasons": [], "advices": []}

def fuse(text, llm_obj):
    cats, acts, floor = rule_check(text)
    risk = llm_obj.get("risk", "low").lower()
    base = REV[max(RANK.get(risk,0), RANK.get(floor,0))]
    is_scam = bool(llm_obj.get("is_scam", False))
    if floor == "high" or (floor == "medium" and (cats or acts)):
        is_scam = True
    reasons = list(llm_obj.get("reasons", []))
    if floor == "high" and "命中高風險關鍵規則" not in reasons:
        reasons.append("命中高風險關鍵規則")
    adv = list(llm_obj.get("advices", []))
    if not adv:
        if base == "high":
            adv = ["請立即結束通話與所有操作", "請勿提供任何驗證碼或帳戶資訊", "建議改由本人主動撥打 165 或銀行客服查證"]
        elif base == "medium":
            adv = ["請避免提供個資或驗證碼並保留紀錄", "建議撥打 165 或銀行客服確認"]
        else:
            adv = ["若仍有疑慮，建議致電 165 反詐騙或聯繫銀行客服進一步查證"]
    return {
        "text": text,
        "is_scam": is_scam,
        "risk": base,
        "reasons": reasons,
        "advices": adv,
        "analysis": {
            "matched_categories": sorted(set(cats)),
            "actions_requested": sorted(set(acts))
        },
        "meta": {
            "asr_backend": "whisper",
            "asr_model": ASR_MODEL,
            "ollama_model": SCAM_MODEL
        }
    }

def decide(text):
    cats, acts, floor = rule_check(text)
    if floor in ("high", "medium"):
        seed = {"is_scam": True, "risk": floor, "reasons": ["規則判定"], "advices": []}
        return fuse(text, seed)
    try:
        llm_obj = call_llm(text)
        ok = isinstance(llm_obj, dict) and all(k in llm_obj for k in ("is_scam","risk","reasons","advices"))
        if not ok:
            llm_obj = {"is_scam": False, "risk": "low", "reasons": [], "advices": []}
        return fuse(text, llm_obj)
    except:
        seed = {"is_scam": False, "risk": "medium", "reasons": ["模型逾時"], "advices": []}
        return fuse(text, seed)

@app.get("/")
def home():
    return jsonify({"message":"OK"})

@app.post("/analyze_text")
def analyze_text():
    data = request.get_json(force=True, silent=True) or {}
    text = (data.get("text") or "").strip()
    if not text:
        return jsonify({"text":"", "is_scam":False, "risk":"low",
                        "reasons":["text required"], "advices":["請提供文字"],
                        "analysis":{"matched_categories":[],"actions_requested":[]},
                        "meta":{"asr_backend":"whisper","asr_model":ASR_MODEL,"ollama_model":SCAM_MODEL}}), 200
    return jsonify(decide(text)), 200

@app.post("/upload_audio")
def upload_audio():
    f = request.files.get("file")
    if not f or not f.filename:
        return jsonify({"text":"", "is_scam":False, "risk":"low",
                        "reasons":["缺少音檔"], "advices":["請用 multipart/form-data，欄位名為 file"],
                        "analysis":{"matched_categories":[],"actions_requested":[]},
                        "meta":{"asr_backend":"whisper","asr_model":ASR_MODEL,"ollama_model":SCAM_MODEL}}), 200
    ext = os.path.splitext(f.filename)[1] or ".wav"
    raw_path = os.path.join(UPLOAD_DIR, f"{uuid.uuid4()}{ext}")
    f.save(raw_path)
    wav_path = None
    try:
        wav_path = to_wav_16k(raw_path)
        text = transcribe(wav_path)
        if not text or len(text) < 6:
            return jsonify({"text": text or "", "is_scam": False, "risk": "low",
                            "reasons": ["辨識文字過短或空白"], "advices": ["請提供更清晰或更長的音檔"],
                            "analysis": {"matched_categories": [], "actions_requested": []},
                            "meta": {"asr_backend":"whisper","asr_model":ASR_MODEL,"ollama_model":SCAM_MODEL}}), 200
        return jsonify(decide(text)), 200
    except Exception as e:
        return jsonify({"text":"", "is_scam":False, "risk":"medium",
                        "reasons":[f"轉檔/ASR/LLM 異常: {str(e)}"], "advices":["請改用文字分析或稍後再試"],
                        "analysis":{"matched_categories":[],"actions_requested":[]},
                        "meta":{"asr_backend":"whisper","asr_model":ASR_MODEL,"ollama_model":SCAM_MODEL}}), 200
    finally:
        try:
            if os.path.exists(raw_path): os.remove(raw_path)
            if wav_path and os.path.exists(wav_path): os.remove(wav_path)
        except: pass

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    app.run(host="0.0.0.0", port=port, debug=True)
