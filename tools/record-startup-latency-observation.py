"""Append one tools/observe-startup-latency.ps1 report to the committed observation history.

The committed history keeps every raw sample (durations only), the per-scenario medians and the
device/host/plugin identity, but never the raw logcat lines, device paths or request identifiers
that the report file carries. Records are append-only; re-running with an existing id fails.

Usage:
    py tools/record-startup-latency-observation.py <report.json> --id 20261009-l0-api35-23046rp50c
        --build-type release --plugin-commit <sha> [--host-commit <sha>] --note "..."
"""
import argparse
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
HISTORY = ROOT / "docs" / "maintenance" / "startup-latency-observations.json"
IDENTITY_KEYS = {"coldCompilerProcess", "fixtureSha256"}
GROUP_PREFIXES = {
    "execution": "",
    "compile": "compile",
    "boot": "boot",
    "prewarm": "prewarm",
    "warmup": "warmup",
}
FORBIDDEN_KEY = re.compile(r"path|request|uri|sourcename|sourcetext|identity", re.IGNORECASE)
SKIPPED_FIELDS = {
    "schema", "profile", "outcome", "error", "cache", "entry", "reason", "retained", "acquired",
    "projectSessionRevision", "event", "hostHeapStartKiB", "hostHeapEndKiB", "hostHeapDeltaKiB",
    "state",
}


def flatten_sample(sample):
    record = {
        "coldCompilerProcess": bool(sample["coldCompilerProcess"]),
        "fixtureSha256": sample["fixtureSha256"],
    }
    for counter in ("trimEventCount", "realmTeardownCount", "serviceDestroyCount"):
        value = sample.get(counter)
        if isinstance(value, int) and not isinstance(value, bool):
            record[counter] = max(0, value)
    if isinstance(sample.get("warmBindingRetained"), bool):
        record["warmBindingRetained"] = sample["warmBindingRetained"]
    stopwatch = sample.get("stopwatchMs")
    if isinstance(stopwatch, int) and not isinstance(stopwatch, bool):
        record["stopwatchMs"] = max(0, stopwatch)
    elif stopwatch is None and "stopwatchMs" in sample:
        record["stopwatchMs"] = None
    for group, prefix in GROUP_PREFIXES.items():
        fields = sample.get(group)
        if not fields:
            continue
        for key, value in fields.items():
            if key in SKIPPED_FIELDS:
                continue
            name = key if not prefix else prefix + key[0].upper() + key[1:]
            if FORBIDDEN_KEY.search(name):
                raise SystemExit(f"refusing to record identity-like field {name}")
            if isinstance(value, str) and value.isdigit():
                record[name] = int(value)
            elif value in (None, "na"):
                record[name] = None
    return record


def medians(samples):
    result = {}
    keys = {key for sample in samples for key in sample if key not in IDENTITY_KEYS}
    for key in sorted(keys):
        values = [sample.get(key) for sample in samples]
        if any(not isinstance(value, int) for value in values):
            continue
        if len(values) % 2 == 0:
            continue
        result[key] = sorted(values)[len(values) // 2]
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report")
    parser.add_argument("--id", required=True)
    parser.add_argument("--build-type", required=True, choices=("debug", "release"))
    parser.add_argument("--plugin-commit", required=True)
    parser.add_argument("--host-commit", default=None)
    parser.add_argument("--note", required=True)
    arguments = parser.parse_args()

    report_path = pathlib.Path(arguments.report)
    report = json.loads(report_path.read_text(encoding="utf-8-sig"))
    if report.get("schemaVersion") != 1 or report.get("gate") != "none":
        raise SystemExit("unsupported report schema")
    history = json.loads(HISTORY.read_text(encoding="utf-8"))
    if any(entry["id"] == arguments.id for entry in history["observations"]):
        raise SystemExit(f"observation {arguments.id} already exists; history is append-only")

    scenarios = {}
    for scenario_id, scenario in report["scenarios"].items():
        samples = [flatten_sample(sample) for sample in scenario["samples"]]
        scenarios[scenario_id] = {"samples": samples, "medians": medians(samples)}

    try:
        report_relative = report_path.resolve().relative_to(ROOT).as_posix()
    except ValueError:
        report_relative = report_path.name
    entry = {
        "id": arguments.id,
        "observedAt": report["observedAt"][:10],
        "deviceLabel": report["deviceLabel"],
        "device": {
            "api": int(report["device"]["api"]),
            "model": report["device"]["model"],
            "abi": report["device"]["abi"],
        },
        "webView": {
            "package": report["webView"]["package"],
            "version": report["webView"]["version"],
        },
        "host": {
            "versionName": report["host"]["versionName"],
            "versionCode": int(report["host"]["versionCode"]),
            "commit": arguments.host_commit,
        },
        "plugin": {
            "versionName": report["plugin"]["versionName"],
            "versionCode": int(report["plugin"]["versionCode"]),
            "commit": arguments.plugin_commit,
        },
        "buildType": arguments.build_type,
        "runsPerScenario": int(report["runsPerScenario"]),
        "scenarios": scenarios,
        "provenance": {
            "kind": "live-device-run",
            "reportFile": report_relative,
            "note": arguments.note,
        },
    }
    history["observations"].append(entry)
    HISTORY.write_text(json.dumps(history, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    for scenario_id, scenario in scenarios.items():
        wait = scenario["medians"].get("userWaitMs")
        print(f"{arguments.id} {scenario_id}: userWaitMs median = {wait}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
