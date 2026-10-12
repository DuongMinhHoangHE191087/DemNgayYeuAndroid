#!/usr/bin/env bash
# 2.2.0 IG: kiểm tĩnh docs/integration/INTEGRATION.md với mã nguồn (0 Gradle, 0 mạng).
# Dùng: bash tools/integration_doc_check.sh [--final] [<doc>]. --final: còn khối TODO-* thì FAIL. exit 0 = PASS.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd); FINAL=0
[ "${1:-}" = "--final" ] && { FINAL=1; shift; }
exec python3 - "$ROOT" "$FINAL" "${1:-$ROOT/docs/integration/INTEGRATION.md}" <<'PY'
import sys, re, glob, os
root, final, doc = sys.argv[1], sys.argv[2] == "1", sys.argv[3]
def rd(*p):
    try: return open(os.path.join(root, *p), encoding="utf-8", errors="replace").read()
    except OSError: return ""
raw = open(doc, encoding="utf-8").read(); bad = []
todo = re.findall(r"<!-- TODO-(\w+) -->", raw)
text = re.sub(r"<!-- TODO-(\w+) -->.*?<!-- /TODO-\1 -->", "", raw, flags=re.S)
if final and todo: bad.append("còn khối TODO: " + ",".join(todo))
src = "".join(rd(*os.path.relpath(f, root).split(os.sep)) for d in ("src/main", "app/src/main", "appplugin-game/src/main", "appplugin-max/src/main")
              for f in glob.glob(os.path.join(root, d, "**", "*"), recursive=True) if os.path.isfile(f))
gradle = "".join(rd(f) for f in ("build.gradle.kts", "settings.gradle.kts", "appplugin-max/build.gradle.kts", "appplugin-game/build.gradle.kts", "appplugin-nextgen/build.gradle.kts", "gradle/module-defaults.gradle", "gradle/publish.gradle"))
# 1. API: mọi `Lớp.thành_viên` phải có trong api/*.api (khối lớp + $Companion)
blocks = {}
for f in ("api/appplugin.api", "api/appplugin-game.api"):
    cur = None
    for ln in rd(f).splitlines():
        m = re.match(r"^(?:\w+ )*(?:class|interface) (\S+)", ln)
        if m: cur = blocks.setdefault(re.split(r"[.]", m.group(1))[-1], [])
        if cur is not None: cur.append(ln)
def member_ok(cls, mem):
    body = "\n".join(blocks.get(cls, []) + blocks.get(cls + "$Companion", []))
    cap = mem[:1].upper() + mem[1:]
    return re.search(r"(?<![\w$])(%s|get%s|set%s|is%s)(?![\w$])" % (mem, cap, cap, cap), body) is not None
NONSDK = {"Intent", "Log", "Build", "BuildConfig", "Activity", "Application", "Context", "StrictMode", "System", "Manifest", "RepositoriesMode"}; FILEEXT = {"md", "kt", "xml", "pro", "json", "kts", "gradle", "sh", "py", "yml", "properties", "toml", "api"}; REF = re.compile(r"([A-Z]\w*)\.(?:(?:instance|INSTANCE)\.)?([A-Za-z_]\w*)")
api_text = re.sub(r"^\| \d+\.\d+\.\d+.*$", "", text, flags=re.M)  # hàng bảng nâng cấp nhắc API đã gỡ
for sp in re.findall(r"`([^`\n]+)`", api_text):
    m = REF.match(sp)
    if m and m.group(1) not in NONSDK and m.group(2) not in FILEEXT:
        c, mem = m.groups()
        if c not in blocks: bad.append("API: lớp %s không có trong api/appplugin.api" % c)
        elif not member_ok(c, mem): bad.append("API: %s.%s không có trong api/appplugin.api" % (c, mem))
for c, mem in REF.findall("\n".join(re.findall(r"```[^\n]*\n(.*?)```", text, flags=re.S))):
    if c in blocks and not member_ok(c, mem): bad.append("API (khối mã): %s.%s không có trong api/appplugin.api" % (c, mem))
# 2. toạ độ Gradle, AGP/compileSdk/minSdk, URL repo
for g, a, v in re.findall(r"\b([a-z][\w-]*(?:\.[\w-]+)+):([\w.-]+):([\w.-]+)\b", text):
    if g == "com.app.plugin": continue
    if "%s:%s:%s" % (g, a, v) not in gradle: bad.append("toạ độ %s:%s:%s không có trong build.gradle.kts" % (g, a, v))
for g, a in set(re.findall(r"\b(com\.app\.plugin):([\w-]+)", text)):
    if a not in ("appplugin", "appplugin-max", "appplugin-game", "appplugin-nextgen"): bad.append("artifact lạ %s:%s" % (g, a))
for k, pat, src_text in (("AGP", r"AGP (\d+\.\d+\.\d+)", r'com\.android\.library"\) version "([\d.]+)"'), ("compileSdk", r"compileSdk (\d+)", r"compileSdk = (\d+)"), ("SDK minSdk", r"SDK minSdk (\d+)", r"minSdk = (\d+)")):
    d = re.findall(pat, text); s = re.findall(src_text, gradle)
    for x in set(d):
        if s and x != s[0]: bad.append("%s: tài liệu %s ≠ mã %s" % (k, x, s[0]))
for u in set(re.findall(r"https://[^\s`)\"]+", text)):
    if ("maven.pkg" in u or "applovin" in u) and u.rstrip(".,") not in gradle: bad.append("URL repo %s không có trong gradle" % u)
# 3. đường dẫn, khoá manifest, resource, khoá Remote Config
for sp in re.findall(r"`([^`\n]+)`", text):
    if re.fullmatch(r"(tools|docs|app|src|api|DOC|gradle)/[\w./-]+", sp) and not os.path.exists(os.path.join(root, sp)): bad.append("đường dẫn %s không tồn tại" % sp)
    if re.fullmatch(r"com\.[a-z.]+\.[A-Z_]{4,}", sp) and sp not in src: bad.append("khoá manifest %s không có trong src/main" % sp)
    if re.fullmatch(r"(appplugin_\w+|@(?:string|xml|raw|array|integer)/\w+)", sp) and sp.split("/")[-1] not in src: bad.append("resource %s không có trong src/main" % sp)
    if re.fullmatch(r"(seg_\w+|\w+_android|logevent_control|cf_ids_placement|cf_ads_placement)", sp) and sp not in src: bad.append("khoá RC %s không có trong src/main" % sp)
segrc = set(re.findall(r'(?:overrideInt|rcLong|rcString|countries)\(\s*"(seg_\w+)"', src)) | set(re.findall(r'RC_KEY_\w+\s*=\s*"(seg_\w+)"', src))
for k in set(re.findall(r'getString\("([A-Z][A-Z0-9_]{5,})"\)', rd("src/main/java/com/app/plugin/app/AppPluginBase.kt"))) | segrc:
    if k not in text: bad.append("khoá %s %s đọc ở src/main nhưng tài liệu không nêu" % ("RC" if k in segrc else "meta-data", k))
for i, v in re.findall(r'id\("([\w.-]+)"\) version "([\d.]+)"', text):
    if 'id("%s") version "%s"' % (i, v) not in gradle: bad.append("plugin %s version %s không khớp settings.gradle.kts" % (i, v))
if re.search(r"Kotlin \d+\.\d+\.\d+", text): bad.append("tài liệu nêu số phiên bản Kotlin không có nguồn trong repo (AGP 9 tích hợp sẵn Kotlin)")
for n in set(re.findall(r"@\w+/([\w*]+)", rd("src/main/res/raw/appplugin_keep.xml"))) - {"appplugin_keep"}:
    if n not in text: bad.append("resource app-contract %s (appplugin_keep.xml) không có trong tài liệu" % n)
# 4. bảng nâng cấp phủ mọi bản có mục "Phải làm khi nâng cấp" từ 1.3.0 tới bản đang mở ([Unreleased] = UNRELEASED_AS)
UNRELEASED_AS = "2.4.0"; ver, vs = None, []
for ln in rd("CHANGELOG.md").splitlines():
    m = re.match(r"## \[([^\]]+)\]", ln)
    if m: ver = UNRELEASED_AS if m.group(1) == "Unreleased" else m.group(1)
    if ln.startswith("### Phải làm khi nâng cấp") and ver and tuple(map(int, ver.split("."))) >= (1, 3, 0): vs.append(ver)
for v in sorted(set(vs)):
    if not re.search(r"^\| %s[ |]" % re.escape(v), text, flags=re.M): bad.append("bảng nâng cấp thiếu hàng %s" % v)
# 6. §9 (2.3.0 ROB3): kiểu/thành viên §9 bảo app dùng phải public trong nguồn Kotlin (tools/lib/doc_visibility.py, IDC_SRC cho test)
sys.dont_write_bytecode = True; sys.path.insert(0, os.path.join(root, "tools", "lib")); import doc_visibility
bad += doc_visibility.check(root, text, NONSDK, FILEEXT)
# 5. mục bắt buộc
for need in ('tools:replace="android:dataExtractionRules,android:fullBackupContent"', "com.app.plugin.LICENSE", "google-services.json", "AppPluginBase", "## English", "license.properties", "Policy brain qua Play Integrity"):
    if need not in text: bad.append("thiếu mục bắt buộc: " + need)
# 7. (2.3.0 IG2) DC14: khối ```kotlin có exclusiveContent + filter { includeGroup("com.app.plugin") } (không tính văn xuôi); DC16: mọi meta-data literal "com.app.plugin.<KHOÁ>" ở nguồn phải có trong tài liệu
if not re.search(r'```(?:kotlin|kts)\n(?:(?!```).)*?exclusiveContent\s*\{(?:(?!```).)*?filter\s*\{\s*includeGroup\("com\.app\.plugin"\)', text, flags=re.S): bad.append("thiếu mục bắt buộc: exclusiveContent { … includeGroup(\"com.app.plugin\") }")
DEMO_ONLY = {"BRAIN_HARNESS", "IAP_HARNESS"}  # F4: 2 khoá chỉ có ở demo/harness, app không khai
meta = {k for k in re.findall(r'"com\.app\.plugin\.([A-Z][A-Z0-9_]{3,})"', src) if k not in DEMO_ONLY and not k.endswith("_HARNESS")}
if not meta: bad.append("census khoá meta-data \"com.app.plugin.*\" ở nguồn rỗng (fail-closed)")
bad += ["khoá meta-data com.app.plugin.%s đọc ở src/main nhưng tài liệu không nêu" % k for k in sorted(meta) if "com.app.plugin." + k not in text]
# 8. (2.4.0 IG3) DC19: §3 có hàng bảng cho đủ 4 artifactId; DC20: §3 nêu `-max` + nextgen "không hỗ trợ"
m3 = re.search(r"^## 3\..*?(?=^## |\Z)", text, flags=re.M | re.S); sec3 = m3.group(0) if m3 else ""
bad += ["§3: thiếu hàng bảng artifact " + a for a in ("appplugin", "appplugin-nextgen", "appplugin-game", "appplugin-max") if not re.search(r"^\| `com\.app\.plugin:%s` \|" % re.escape(a), sec3, flags=re.M)]
if "`appplugin-max` + `appplugin-nextgen`: không hỗ trợ" not in sec3: bad.append('§3: thiếu câu "`appplugin-max` + `appplugin-nextgen`: không hỗ trợ"')
print("\n".join("FAIL: " + b for b in bad) if bad else "integration_doc_check: PASS (TODO còn %d)" % len(todo)); sys.exit(1 if bad else 0)
PY
