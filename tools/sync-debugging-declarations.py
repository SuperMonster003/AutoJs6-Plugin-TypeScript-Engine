#!/usr/bin/env python3
"""Vendor the local debugging declarations without changing the public npm lock.

AutoJs6 6.8.1 adds the global `debugging` / `$debugging` module for Rhino scripts
(3-Probe Debugger). No npm release of @sm003/autojs6-dts declares it yet, so the
plugin bundles the module from an explicit declarations checkout, like the Compose
supplement. Node.js scripts do not get the module (they use `inspector`), so only
the Rhino layer receives it.

The explicit checkout is used only by this maintenance command. Gradle consumes
the checked-in resources and never reads a sibling checkout.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess


MODULE = "aj6-int-debugging.d.ts"
GLOBALS_PATTERN = re.compile(
    r"(?m)^/\*\*[^\n]*\*/\ndeclare let debugging: Internal\.Debugging;\ndeclare let \$debugging: Internal\.Debugging;$"
)


def outputs(source: Path) -> dict[str, bytes]:
    declarations = source / "declarations/autojs6"
    module = (declarations / MODULE).read_text(encoding="utf-8")
    init = (declarations / "aj6-int-init.d.ts").read_text(encoding="utf-8")
    globals_blocks = GLOBALS_PATTERN.findall(init)
    if len(globals_blocks) != 1:
        raise ValueError("Expected exactly one debugging globals block in aj6-int-init.d.ts")
    if "interface Debugging {" not in module:
        raise ValueError("The debugging declaration module lacks Internal.Debugging")
    # The module stays verbatim; the two globals come from aj6-int-init.d.ts, which the
    # pinned 2.1.3 projection cannot provide for a module it does not contain.
    supplement = (
        module.rstrip("\n") + "\n\n"
        "// Globals of the debugging module, from aj6-int-init.d.ts of the same source.\n"
        + globals_blocks[0] + "\n"
    ).encode("utf-8")
    version = json.loads((source / "package.json").read_text(encoding="utf-8"))["version"]
    commit = subprocess.check_output(
        ["git", "-C", str(source), "rev-parse", "HEAD"], text=True,
    ).strip()
    files = {MODULE: supplement}
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
    target = Path(__file__).resolve().parents[1] / "build-logic/convention/src/main/resources/r2-debugging"
    expected = outputs(args.declarations.resolve())
    existing = {path.name for path in target.iterdir()} if target.is_dir() else set()
    if existing - expected.keys():
        raise ValueError("Unexpected files in the debugging supplement")
    changed = [name for name, data in expected.items() if not (target / name).is_file() or (target / name).read_bytes() != data]
    if args.check and changed:
        raise SystemExit("Debugging supplement differs: " + ", ".join(changed))
    if not args.check:
        target.mkdir(parents=True, exist_ok=True)
        for name, data in expected.items():
            (target / name).write_bytes(data)
    print("Debugging declaration supplement: " + ("checked" if args.check else "synchronized"))


if __name__ == "__main__":
    main()
