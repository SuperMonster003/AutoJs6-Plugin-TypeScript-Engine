"""Generate ten localized README and changelog files; --check never writes."""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANGUAGES = ("en", "ar", "es", "fr", "ja", "ko", "ru", "zh-Hans", "zh-Hant-HK", "zh-Hant-TW")


def render(template, values):
    return re.sub(r"\{\{\s*(\w+)\s*\}\}", lambda m: str(values[m[1]]), template)


def build_artifacts(root):
    common = json.loads((root / ".readme/common.json").read_text(encoding="utf-8"))
    readme_template = (root / ".readme/template_readme.md").read_text(encoding="utf-8")
    changelog_template = (root / ".changelog/template_changelog.md").read_text(encoding="utf-8")
    localized = {code: json.loads((root / f".readme/lang_{code}.json").read_text(encoding="utf-8")) for code in LANGUAGES}
    artifacts = {}
    for code in LANGUAGES:
        values = {**common, **localized[code], "code": code}
        values["languages"] = " | ".join(f"[{localized[item]['language']}]({common['repo_url']}/blob/master/.readme/README-{item}.md)" for item in LANGUAGES)
        content = render(readme_template, values)
        artifacts[root / f".readme/README-{code}.md"] = content
        if code == "zh-Hans": artifacts[root / "README.md"] = content
        data = json.loads((root / f".changelog/lang_{code}.json").read_text(encoding="utf-8"))
        lines = []
        for version, release in data["$data"].items():
            lines += [f"# {version}", "", f"###### {release['released_date']}", ""]
            for category in ("hint", "feature", "fix", "improvement", "dependency"):
                for entry in release.get(category, []): lines.append(f"* `{data['changelog_label_' + category]}` {entry}")
            lines.append("")
        text = render(changelog_template, {**data, "placeholder_release_history": "\n".join(lines).rstrip()}).rstrip() + "\n"
        artifacts[root / f"app/src/main/assets/doc/CHANGELOG-{code}.md"] = text
    return artifacts


def finish_outputs(artifacts, check):
    drift = [p for p, text in artifacts.items() if not p.is_file() or p.read_text(encoding="utf-8") != text]
    if check:
        for path in drift: print(f"Out of date: {path}", file=sys.stderr)
        return 1 if drift else 0
    for path, text in artifacts.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8", newline="\n")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    artifacts = build_artifacts(ROOT)
    result = finish_outputs(artifacts, args.check)
    print(f"MARKDOWN_{'FAIL' if result else 'OK'} artifacts={len(artifacts)}")
    return result


if __name__ == "__main__": sys.exit(main())
