#!/usr/bin/env python3
"""Vendor the local Compose declarations without changing the public npm lock.

The explicit checkout is used only by this maintenance command. Gradle consumes
the checked-in resources and never reads a sibling checkout.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess


def outputs(source: Path) -> dict[str, bytes]:
    declarations = source / "declarations/autojs6"
    module = (declarations / "aj6-int-compose.d.ts").read_text(encoding="utf-8").encode("utf-8")
    jsx = (declarations / "aj6-jsx-element-extension.d.ts").read_text(encoding="utf-8")
    # Only the generated Compose region belongs to the supplement. The compiler
    # retains its existing, stricter XML intrinsic declarations.
    block = re.search(r"(?ms)^// <autojs6-compose-jsx:v1>\n.*?^// </autojs6-compose-jsx:v1>", jsx)
    if not block:
        raise ValueError("The generated Compose JSX region is missing")
    jsx_module = (
        "// Local Compose UI supplement; XML intrinsics remain compiler-owned.\n"
        "declare let compose: Internal.Compose;\n"
        "declare let $compose: Internal.Compose;\n"
        "declare const ComposeError: Internal.ComposeErrorConstructor;\n"
        + block.group(0).strip() + "\n"
    ).encode("utf-8")
    version = json.loads((source / "package.json").read_text(encoding="utf-8"))["version"]
    commit = subprocess.check_output(
        ["git", "-C", str(source), "rev-parse", "HEAD"], text=True,
    ).strip()
    files = {"aj6-int-compose.d.ts": module, "compose-jsx.d.ts": jsx_module}
    provenance = {
        "sourceRepository": "https://github.com/SuperMonster003/AutoJs6-TypeScript-Declarations",
        "sourceVersion": version,
        "sourceCommit": commit,
        "distribution": "local integration snapshot, not an npm release",
        "license": "MPL-2.0",
        "files": {name: hashlib.sha256(data).hexdigest() for name, data in files.items()},
    }
    files["source.json"] = (json.dumps(provenance, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    return files


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--declarations", required=True, type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    target = Path(__file__).resolve().parents[1] / "build-logic/convention/src/main/resources/r2-compose"
    expected = outputs(args.declarations.resolve())
    existing = {path.name for path in target.iterdir()} if target.is_dir() else set()
    if existing - expected.keys():
        raise ValueError("Unexpected files in the Compose supplement")
    changed = [name for name, data in expected.items() if not (target / name).is_file() or (target / name).read_bytes() != data]
    if args.check and changed:
        raise SystemExit("Compose supplement differs: " + ", ".join(changed))
    if not args.check:
        target.mkdir(parents=True, exist_ok=True)
        for name, data in expected.items():
            (target / name).write_bytes(data)
    print("Compose declaration supplement: " + ("checked" if args.check else "synchronized"))


if __name__ == "__main__":
    main()
