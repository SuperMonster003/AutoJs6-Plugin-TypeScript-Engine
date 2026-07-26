# -*- coding: utf-8 -*-
import argparse
import json
import re
import unicodedata
from pathlib import Path


LANGUAGE_CODES = [
    "zh-Hans",
    "zh-Hant-HK",
    "zh-Hant-TW",
    "en",
    "fr",
    "es",
    "ja",
    "ko",
    "ru",
    "ar",
]
LANGUAGE_CODE_DEFAULT = "zh-Hans"
CHANGELOG_CATEGORIES = ["hint", "feature", "fix", "improvement", "dependency"]

ROOT = Path(__file__).resolve().parents[1]
CHANGELOG_DIR = ROOT / ".changelog"
VERSION_PROPERTIES = ROOT / "version.properties"


def relative_path(path: Path) -> Path:
    return path.relative_to(ROOT)


def validate_no_fullwidth_symbols(path: Path, text: str):
    for line_number, line in enumerate(text.splitlines(), start=1):
        for column_number, char in enumerate(line, start=1):
            category = unicodedata.category(char)
            width = unicodedata.east_asian_width(char)
            if width in {"F", "W"} and category[0] in {"P", "S", "Z"}:
                raise ValueError(
                    f"Fullwidth symbol {char!r} at "
                    f"{relative_path(path)}:{line_number}:{column_number}"
                )


def load_json(path: Path):
    text = path.read_text(encoding="utf-8")
    validate_no_fullwidth_symbols(path, text)
    return json.loads(text)


def version_label() -> str:
    properties = {}
    for raw_line in VERSION_PROPERTIES.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()
    version_name = properties.get("VERSION_NAME")
    if not version_name:
        raise ValueError("VERSION_NAME is missing from version.properties")
    return version_name if version_name.startswith("v") else f"v{version_name}"


def validate_changelogs(changelogs: dict):
    base = changelogs[LANGUAGE_CODE_DEFAULT]
    base_keys = set(base)
    base_data = base["$data"]
    base_versions = list(base_data)
    allowed_release_keys = {"released_date", *CHANGELOG_CATEGORIES}

    expected_version = version_label()
    if not base_versions or base_versions[0] != expected_version:
        raise ValueError(
            f"Latest changelog version must be {expected_version!r}, "
            f"got {base_versions[:1]}"
        )

    for code, changelog in changelogs.items():
        keys = set(changelog)
        if keys != base_keys:
            missing = sorted(base_keys - keys)
            extra = sorted(keys - base_keys)
            raise ValueError(
                f"Changelog key mismatch for {code}: "
                f"missing={missing}, extra={extra}"
            )

        data = changelog["$data"]
        versions = list(data)
        if versions != base_versions:
            raise ValueError(
                f"Changelog version mismatch for {code}: {versions}"
            )

        for release_version, base_release in base_data.items():
            release = data[release_version]
            unknown = sorted(set(release) - allowed_release_keys)
            if unknown:
                raise ValueError(
                    f"Unknown fields for {code}.{release_version}: {unknown}"
                )
            if set(release) != set(base_release):
                missing = sorted(set(base_release) - set(release))
                extra = sorted(set(release) - set(base_release))
                raise ValueError(
                    f"Release shape mismatch for {code}.{release_version}: "
                    f"missing={missing}, extra={extra}"
                )
            for key, base_value in base_release.items():
                value = release[key]
                if type(value) is not type(base_value):
                    raise TypeError(
                        f"Value type mismatch for "
                        f"{code}.{release_version}.{key}"
                    )
                if isinstance(base_value, list) and len(value) != len(base_value):
                    raise ValueError(
                        f"List length mismatch for "
                        f"{code}.{release_version}.{key}: "
                        f"expected={len(base_value)}, got={len(value)}"
                    )


def render_template(text: str, values: dict) -> str:
    def replace(match):
        key = match.group(1).strip()
        if key not in values:
            raise KeyError(f"Missing template value: {key}")
        return str(values[key])

    return re.sub(r"\{\{\s*([A-Za-z0-9_$.-]+)\s*\}\}", replace, text)


def format_changelog(changelog: dict) -> str:
    chunks = []
    for release_version, release in changelog["$data"].items():
        lines = [
            f"# {release_version}",
            "",
            f"###### {release['released_date']}",
            "",
        ]
        for category in CHANGELOG_CATEGORIES:
            label = changelog[f"changelog_label_{category}"]
            for entry in release.get(category, []):
                lines.append(f"* `{label}` {entry}")
        chunks.append("\n".join(lines).rstrip())
    return "\n\n".join(chunks).rstrip() + "\n"


def expected_outputs() -> dict:
    changelogs = {
        code: load_json(CHANGELOG_DIR / f"lang_{code}.json")
        for code in LANGUAGE_CODES
    }
    validate_changelogs(changelogs)

    template_path = CHANGELOG_DIR / "template_changelog.md"
    template = template_path.read_text(encoding="utf-8")
    validate_no_fullwidth_symbols(template_path, template)

    outputs = {}
    for code, changelog in changelogs.items():
        values = dict(changelog)
        values["placeholder_release_history"] = format_changelog(changelog).rstrip()
        output = render_template(template, values).rstrip() + "\n"
        path = CHANGELOG_DIR / f"CHANGELOG-{code}.md"
        validate_no_fullwidth_symbols(path, output)
        outputs[path] = output
    return outputs


def generate(check_only: bool):
    for path, output in expected_outputs().items():
        if check_only:
            if not path.is_file() or path.read_text(encoding="utf-8") != output:
                raise ValueError(f"Generated changelog is stale: {relative_path(path)}")
            print(f"Verified {relative_path(path)}")
            continue
        path.write_text(output, encoding="utf-8", newline="\n")
        print(f"Generated {relative_path(path)}")


def main():
    parser = argparse.ArgumentParser(
        description="Generate and validate multilingual changelog Markdown."
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Validate that generated Markdown is up to date without writing files.",
    )
    args = parser.parse_args()
    generate(check_only=args.check)


if __name__ == "__main__":
    main()
