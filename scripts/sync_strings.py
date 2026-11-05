#!/usr/bin/env python3
"""
Keep the three string catalogues in step.

Source of truth: shared/src/commonMain/kotlin/com/retally/shared/core/strings/EnglishStrings.kt
(the `StringAssetIdentifiers.KEY to "text"` pairs) together with the key names in
StringAssetIdentifiers.kt. For every shared key this script makes sure the base-locale
Android `strings.xml` and iOS `Localizable.strings` carry the same text, adding missing
entries and rewriting changed ones in place. Platform-only entries (app_name, Android/iOS
system strings) are left alone, and so are every other locale folder: translators work
from the base files this keeps correct.

    python3 scripts/sync_strings.py          # write
    python3 scripts/sync_strings.py --check  # exit 1 if anything is out of step (CI)
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
IDS = ROOT / "shared/src/commonMain/kotlin/com/retally/shared/core/strings/StringAssetIdentifiers.kt"
EN = ROOT / "shared/src/commonMain/kotlin/com/retally/shared/core/strings/EnglishStrings.kt"
ANDROID = ROOT / "androidApp/src/main/res/values/strings.xml"
IOS = ROOT / "iosApp/ReTally/Resources/en.lproj/Localizable.strings"

CHECK = "--check" in sys.argv


def kotlin_literal(text: str) -> str:
    """Decode the escapes Kotlin allows inside a plain "..." literal."""
    return (
        text.replace("\\$", "$")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\'", "'")
        .replace("\\\\", "\\")
    )


def load_source() -> dict[str, str]:
    ids = dict(re.findall(r'const val (\w+) = "([^"]+)"', IDS.read_text()))
    pairs = re.findall(r'StringAssetIdentifiers\.(\w+) to "((?:[^"\\]|\\.)*)"', EN.read_text())
    out = {}
    for const, text in pairs:
        key = ids.get(const)
        if key is None:
            print(f"warning: {const} has English text but no identifier", file=sys.stderr)
            continue
        out[key] = kotlin_literal(text)
    return out


def android_escape(text: str) -> str:
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("'", "\\'").replace("\n", "\\n")
    # A literal % that is not a format placeholder would break getString(..., args).
    return re.sub(r"%(?!\d+\$[sd]|[sd])", "%%", text)


def android_unescape(text: str) -> str:
    text = text.replace("%%", "%").replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"")
    return text.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")


def ios_escape(text: str) -> str:
    # %1$s is replaced in Kotlin (formatPlaceholders), so the Java form stays as-is.
    return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")


def ios_unescape(text: str) -> str:
    return text.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")


def sync_android(source: dict[str, str]) -> int:
    xml = ANDROID.read_text()
    changed = 0
    present = {}
    for m in re.finditer(r'<string name="([^"]+)">(.*?)</string>', xml, re.S):
        present[m.group(1)] = (m, android_unescape(m.group(2)))
    additions = []
    for key, text in source.items():
        if key in present:
            m, current = present[key]
            if current != text:
                xml = xml.replace(m.group(0), f'<string name="{key}">{android_escape(text)}</string>', 1)
                changed += 1
        else:
            additions.append(f'    <string name="{key}">{android_escape(text)}</string>\n')
    if additions:
        xml = xml.replace("</resources>", "".join(additions) + "</resources>", 1)
        changed += len(additions)
    if changed and not CHECK:
        ANDROID.write_text(xml)
    return changed


def sync_ios(source: dict[str, str]) -> int:
    txt = IOS.read_text()
    changed = 0
    present = {}
    for m in re.finditer(r'^"([^"]+)" = "((?:[^"\\]|\\.)*)";', txt, re.M):
        present[m.group(1)] = (m, ios_unescape(m.group(2)))
    additions = []
    for key, text in source.items():
        if key in present:
            m, current = present[key]
            if current != text:
                txt = txt.replace(m.group(0), f'"{key}" = "{ios_escape(text)}";', 1)
                changed += 1
        else:
            additions.append(f'"{key}" = "{ios_escape(text)}";\n')
    if additions:
        txt = txt.rstrip("\n") + "\n" + "".join(additions)
        changed += len(additions)
    if changed and not CHECK:
        IOS.write_text(txt)
    return changed


def main() -> int:
    source = load_source()
    a = sync_android(source)
    i = sync_ios(source)
    verb = "out of step" if CHECK else "updated"
    print(f"{len(source)} shared keys · Android {verb}: {a} · iOS {verb}: {i}")
    return 1 if (CHECK and (a or i)) else 0


if __name__ == "__main__":
    sys.exit(main())
