#!/usr/bin/env python3
"""Phát và kiểm giấy phép SDK (2.1.0 SA2). Ed25519 thuần Python (RFC 8032 §6, PureEdDSA), chỉ stdlib.
  keygen --out <file ngoài repo>     ghi khoá riêng (mode 600), in DUY NHẤT khoá công khai hex
  issue  --key <f> --kid <id> --pkg <appId> --cert <sha256hex>... --days N --feat ads,brain   in token ALP1.<payload>.<sig>
  verify --pub <hex> <token> [--now <unix>]   rc 0 hợp lệ, 1 sai/malformed, 2 dùng sai, 3 quá hạn (chỉ khi có --now;
                                     không có --now thì verify KHÔNG kiểm exp — hạn do SDK thi hành lúc chạy)
Khoá riêng: một dòng `ALPK1.<b64url(seed32)>`. Không bao giờ in khoá riêng ra stdout/stderr."""
import argparse, base64, hashlib, json, os, re, secrets, sys, time

# --- Ed25519 (RFC 8032 §6) ---
P = 2**255 - 19
L = 2**252 + 27742317777372353535851937790883648493
D = -121665 * pow(121666, P - 2, P) % P
SQRT_M1 = pow(2, (P - 1) // 4, P)
BASE_Y = 4 * pow(5, P - 2, P) % P

def _sha512(s): return hashlib.sha512(s).digest()
def _inv(x): return pow(x, P - 2, P)

def _recover_x(y, sign):
    if y >= P: return None
    x2 = (y * y - 1) * _inv(D * y * y + 1) % P
    if x2 == 0: return None if sign else 0
    x = pow(x2, (P + 3) // 8, P)
    if (x * x - x2) % P: x = x * SQRT_M1 % P
    if (x * x - x2) % P: return None
    return P - x if (x & 1) != sign else x

_BX = _recover_x(BASE_Y, 0)
G = (_BX, BASE_Y, 1, _BX * BASE_Y % P)

def _add(p, q):
    a = (p[1] - p[0]) * (q[1] - q[0]) % P
    b = (p[1] + p[0]) * (q[1] + q[0]) % P
    c = 2 * p[3] * q[3] * D % P
    d = 2 * p[2] * q[2] % P
    e, f, g, h = b - a, d - c, d + c, b + a
    return (e * f % P, g * h % P, f * g % P, e * h % P)

def _mul(s, p):
    q = (0, 1, 1, 0)
    while s:
        if s & 1: q = _add(q, p)
        p = _add(p, p)
        s >>= 1
    return q

def _eq(p, q):
    return (p[0] * q[2] - q[0] * p[2]) % P == 0 and (p[1] * q[2] - q[1] * p[2]) % P == 0

def _enc(p):
    zi = _inv(p[2])
    x, y = p[0] * zi % P, p[1] * zi % P
    return int.to_bytes(y | ((x & 1) << 255), 32, "little")

def _dec(s):
    if len(s) != 32: return None
    y = int.from_bytes(s, "little")
    x = _recover_x(y & ((1 << 255) - 1), y >> 255)
    return None if x is None else (x, y & ((1 << 255) - 1), 1, x * (y & ((1 << 255) - 1)) % P)

def _clamp(seed):
    h = _sha512(seed)
    a = int.from_bytes(h[:32], "little")
    a &= (1 << 254) - 8
    return a | (1 << 254), h[32:]

def public_key(seed): return _enc(_mul(_clamp(seed)[0], G))

def sign(seed, msg):
    a, prefix = _clamp(seed)
    pub = _enc(_mul(a, G))
    r = int.from_bytes(_sha512(prefix + msg), "little") % L
    rs = _enc(_mul(r, G))
    h = int.from_bytes(_sha512(rs + pub + msg), "little") % L
    return rs + int.to_bytes((r + h * a) % L, 32, "little")

def verify_sig(pub, msg, sig):
    if len(pub) != 32 or len(sig) != 64: return False
    a, r = _dec(pub), _dec(sig[:32])
    s = int.from_bytes(sig[32:], "little")
    if a is None or r is None or s >= L: return False
    h = int.from_bytes(_sha512(sig[:32] + pub + msg), "little") % L
    return _eq(_mul(s, G), _add(r, _mul(h, a)))

# --- token ---
KEY_PREFIX = "ALPK1."
def b64(b): return base64.urlsafe_b64encode(b).rstrip(b"=").decode()
def unb64(s):
    """Giải mã nghiêm: chỉ [A-Za-z0-9_-], không đệm, dạng chuẩn (không bit thừa)."""
    if not re.fullmatch(r"[A-Za-z0-9_-]+", s): raise ValueError("malformed")
    raw = base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))
    if b64(raw) != s: raise ValueError("malformed")
    return raw

def make_token(seed, kid, pkg, certs, exp, feat):
    payload = json.dumps({"v": 1, "kid": kid, "pkg": pkg, "certs": certs, "exp": exp, "feat": feat},
                         separators=(",", ":")).encode()
    head = "ALP1." + b64(payload)
    return head + "." + b64(sign(seed, head.encode("ascii")))

def check_token(pub, token):
    parts = token.strip().split(".")
    if len(parts) != 3 or parts[0] != "ALP1": return None
    try:
        if not verify_sig(pub, (parts[0] + "." + parts[1]).encode("ascii"), unb64(parts[2])): return None
        return json.loads(unb64(parts[1]))
    except Exception:
        return None

# --- CLI ---
def _repo_root():
    return os.path.realpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

def _inside_repo(path):
    root, p = _repo_root(), os.path.realpath(os.path.expanduser(path))
    return p == root or p.startswith(root + os.sep)

def _fail(msg, rc=2):
    print("license_issue: " + msg, file=sys.stderr)
    return rc

def cmd_keygen(a):
    out = os.path.expanduser(a.out)
    if _inside_repo(out): return _fail("đường dẫn nằm trong repo — khoá riêng phải ở ngoài repo")
    if os.path.lexists(out): return _fail("file đã tồn tại — không ghi đè")
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    seed = secrets.token_bytes(32)
    fd = os.open(out, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as f: f.write(KEY_PREFIX + b64(seed) + "\n")
    os.chmod(out, 0o600)
    print(public_key(seed).hex())
    return 0

def _load_seed(path):
    txt = open(os.path.expanduser(path)).read().strip()
    if not txt.startswith(KEY_PREFIX): raise ValueError("không phải khoá ALPK1")
    seed = unb64(txt[len(KEY_PREFIX):])
    if len(seed) != 32: raise ValueError("seed sai độ dài")
    return seed

def cmd_issue(a):
    if a.days < 1: return _fail("--days phải ≥ 1")
    if not a.cert: return _fail("thiếu --cert")
    certs = [c.lower() for c in a.cert]
    if not all(re.fullmatch(r"[0-9a-f]{64}", c) for c in certs): return _fail("--cert phải là sha256 hex 64 ký tự")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", a.kid) or not re.fullmatch(r"[A-Za-z0-9_.]+", a.pkg): return _fail("kid/pkg không hợp lệ")
    feat = [f for f in a.feat.split(",") if f]
    if not feat: return _fail("--feat rỗng")
    try: seed = _load_seed(a.key)
    except (OSError, ValueError) as e: return _fail("không đọc được khoá (%s)" % type(e).__name__)
    print(make_token(seed, a.kid, a.pkg, certs, int(time.time()) + a.days * 86400, feat))
    return 0

def cmd_verify(a):
    try: pub = bytes.fromhex(a.pub)
    except ValueError: return _fail("--pub không phải hex")
    claims = check_token(pub, a.token)
    if claims is None:
        print("INVALID")
        return 1
    if a.now is not None and claims.get("exp", 0) <= a.now:
        print("EXPIRED")
        return 3
    print("VALID " + json.dumps(claims, separators=(",", ":")))
    return 0

def main(argv=None):
    ap = argparse.ArgumentParser(prog="license_issue.py")
    sub = ap.add_subparsers(dest="cmd", required=True)
    k = sub.add_parser("keygen"); k.add_argument("--out", required=True); k.set_defaults(fn=cmd_keygen)
    i = sub.add_parser("issue")
    i.add_argument("--key", required=True); i.add_argument("--kid", required=True); i.add_argument("--pkg", required=True)
    i.add_argument("--cert", action="append", default=[]); i.add_argument("--days", type=int, required=True)
    i.add_argument("--feat", default="ads,brain"); i.set_defaults(fn=cmd_issue)
    v = sub.add_parser("verify"); v.add_argument("--pub", required=True); v.add_argument("token"); v.add_argument("--now", type=int, default=None, help="unix s; có thì báo quá hạn rc 3 (không có: không kiểm exp, SDK thi hành)"); v.set_defaults(fn=cmd_verify)
    try: args = ap.parse_args(argv)
    except SystemExit as e: return 2 if e.code else 0
    return args.fn(args)

if __name__ == "__main__":
    sys.exit(main())
