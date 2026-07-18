#!/usr/bin/env python3
"""
security_checks.py — 安全基线验证脚本

扫描多层观测点的敏感字段：明文密码、PIN、JWT token、完整手机号。

用法:
    python3 security_checks.py list
    python3 security_checks.py backend-log <logfile>
    python3 security_checks.py api-response <response-file-or-url>
    python3 security_checks.py browser-storage <localStorage.json>
    python3 security_checks.py all <logfile>
"""
import os
import re
import sys
import json
from datetime import datetime
from pathlib import Path

# 敏感字符串模式
# 密码：明确标识 password=xxx 或 "password":"xxx" 后跟非空值
PASSWORD_PATTERNS = [
    re.compile(r'(?i)\bpassword\s*[=:]\s*["\']?(?!\s*$|\s*["\']?\s*null\s*["\']?|\s*\*+)([^"\'\s,}]{1,200})["\']?'),
]

# PIN：4-6 位数字（但 child PIN 是 6 位），需要严格上下文避免误报
PIN_PATTERNS = [
    re.compile(r'(?i)\bpin\s*[=:]\s*["\']?(\d{4,8})["\']?'),
]

# JWT token：3 段 base64.urlsafe 用 . 分隔，第一段通常以 eyJ 开头
JWT_PATTERN = re.compile(r'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}')

# 手机号：中国大陆 1[3-9]xxxxxxxxx（11 位），完整号码（不允许中间脱敏）
PHONE_PATTERN = re.compile(r'(?<![\d*])1[3-9]\d{9}(?!\d)')

# 排除项：测试样本/明文常数本身（如 .env.dev 中的 JWT_SECRET 是允许的，但运行日志不应输出实际 token）
EXCLUDE_KEYWORDS = ("JWT_SECRET=", "INIT_TOKEN_HASH=", "DEV_INIT_TOKEN=", "test-", "example", "<", ">")


def scan_text(text: str, source: str):
    findings = []
    for pat in PASSWORD_PATTERNS:
        for m in pat.finditer(text):
            val = m.group(1).strip()
            if val and not any(kw in val.upper() for kw in ("NULL", "REDACTED", "***")):
                # 跳过明显占位符
                if val.upper() in ("NULL", "UNSET", "EMPTY", ""):
                    continue
                if len(val) > 0 and not any(kw.upper() in m.group(0).upper() for kw in EXCLUDE_KEYWORDS):
                    findings.append({
                        "type": "plaintext_password",
                        "source": source,
                        "match_context": m.group(0)[:120],
                        "masked_value": val[:2] + "***" + val[-2:] if len(val) > 4 else "***",
                    })
    for pat in PIN_PATTERNS:
        for m in pat.finditer(text):
            findings.append({
                "type": "plaintext_pin",
                "source": source,
                "match_context": m.group(0)[:120],
                "masked_value": m.group(1)[:1] + "***",
            })
    for m in JWT_PATTERN.finditer(text):
        # 排除配置文件中的占位符
        ctx = text[max(0, m.start()-40):min(len(text), m.end()+10)]
        if "JWT_SECRET" in ctx.upper() and "=" in ctx:
            continue
        findings.append({
            "type": "jwt_token_in_log",
            "source": source,
            "match_context": m.group(0)[:30] + "..." + m.group(0)[-10:],
            "masked_value": m.group(0)[:8] + "***" + m.group(0)[-6:],
        })
    for m in PHONE_PATTERN.finditer(text):
        phone = m.group(0)
        # 跳过明显脱敏的（中间有 *）
        ctx = text[max(0, m.start()-30):min(len(text), m.end()+30)]
        if "PHONE_MASK" in ctx.upper():
            continue
        findings.append({
            "type": "full_phone_number",
            "source": source,
            "match_context": ctx[:140],
            "masked_value": phone[:3] + "****" + phone[-4:],
        })
    return findings


def scan_file(path: str):
    p = Path(path)
    if not p.exists():
        return {"error": f"file not found: {path}"}
    if p.is_dir():
        results = []
        for sub in p.rglob("*"):
            if sub.is_file() and sub.suffix in (".log", ".txt", ".json"):
                try:
                    txt = sub.read_text(encoding="utf-8", errors="ignore")
                    results.extend(scan_text(txt, str(sub)))
                except Exception:
                    pass
        return results
    txt = p.read_text(encoding="utf-8", errors="ignore")
    return scan_text(txt, str(p))


def scan_backend_log(logfile: str):
    findings = scan_file(logfile)
    ts = datetime.now().isoformat(timespec="seconds")
    print(json.dumps({
        "check": "backend-log",
        "label": "S-001/S-002/S-003/S-004 backend log sensitive data scan",
        "source": logfile,
        "timestamp": ts,
        "passed": not findings,
        "findings_count": len(findings) if isinstance(findings, list) else 0,
        "findings": findings if isinstance(findings, list) else [],
        "error": findings if isinstance(findings, dict) else None,
    }, ensure_ascii=False, indent=2))
    sys.exit(0 if not findings else 1)


def scan_api_response(target: str):
    """target 可以是文件路径或 URL（http 开头）"""
    if target.startswith("http"):
        import urllib.request
        try:
            with urllib.request.urlopen(target, timeout=10) as resp:
                txt = resp.read().decode("utf-8", errors="ignore")
        except Exception as e:
            print(json.dumps({"error": str(e)}, ensure_ascii=False))
            sys.exit(2)
    else:
        findings = scan_file(target)
        ts = datetime.now().isoformat(timespec="seconds")
        print(json.dumps({
            "check": "api-response",
            "label": "S-006 API response sensitive data scan",
            "source": target,
            "timestamp": ts,
            "passed": not findings,
            "findings_count": len(findings) if isinstance(findings, list) else 0,
            "findings": findings if isinstance(findings, list) else [],
        }, ensure_ascii=False, indent=2))
        sys.exit(0 if not findings else 1)


def scan_browser_storage(json_file: str):
    findings = scan_file(json_file)
    ts = datetime.now().isoformat(timespec="seconds")
    print(json.dumps({
        "check": "browser-storage",
        "label": "S-005 browser localStorage sensitive data scan",
        "source": json_file,
        "timestamp": ts,
        "passed": not findings,
        "findings_count": len(findings) if isinstance(findings, list) else 0,
        "findings": findings if isinstance(findings, list) else [],
    }, ensure_ascii=False, indent=2))
    sys.exit(0 if not findings else 1)


def main():
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    cmd = sys.argv[1]
    if cmd == "list":
        print("Available checks: backend-log, api-response, browser-storage")
        return
    if cmd == "backend-log":
        scan_backend_log(sys.argv[2])
    elif cmd == "api-response":
        scan_api_response(sys.argv[2])
    elif cmd == "browser-storage":
        scan_browser_storage(sys.argv[2])
    else:
        print(f"Unknown check: {cmd}", file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()
