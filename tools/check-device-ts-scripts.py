#!/usr/bin/env python3
"""Runs small TypeScript scripts through a real AutoJs6 host on one device (0.7.1 checks).

The scripts are pushed to /sdcard/Download/ts-engine-check and run by AutoJs6's
RunIntentActivity, after bringing AutoJs6 to the foreground. Each case prints one
line: PASS / FAIL, its name and the key values. Nothing secret is printed.

Cases:
  debugging  a Rhino .ts calling debugging.waitForAttach(1) compiles and runs
             (needs a host with the debugging module, AutoJs6 6.8.1 or later)
  plain      a Rhino .ts without debugging still compiles and runs
  old-webview  (with --expect-old-webview) the plain script does not run and the
             host log shows the "Android System WebView" requirement error

Usage:
  py tools/check-device-ts-scripts.py --serial <serial> [--apk <plugin apk>] [--expect-old-webview]
  py tools/check-device-ts-scripts.py --serial <serial> --cleanup

zh-CN: 在一台设备上经真实 AutoJs6 宿主运行几个 TypeScript 小脚本 (0.7.1 的检查). 脚本推到
/sdcard/Download/ts-engine-check, 先把 AutoJs6 切到前台再由 RunIntentActivity 运行. 每项输出一行
PASS / FAIL 与关键数值. --expect-old-webview 用于系统 WebView 过旧的设备: 期望脚本不运行, 宿主日志里
出现需要 Android System WebView 的错误. --cleanup 删除推到设备上的目录.
"""

import argparse
import subprocess
import sys
import time

HOST_PACKAGE = "org.autojs.autojs6"
PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.typescript"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
DIRECTORY = "/sdcard/Download/ts-engine-check"
WEBVIEW_ERROR_MARKER = "Android System WebView"

SCRIPTS = {
    "warm-up.js": [
        f"files.write('{DIRECTORY}/warm-up.result', 'ready');",
    ],
    "debugging.ts": [
        "const probeAttached: boolean = debugging.waitForAttach(1);",
        "const probeKinds: string = [typeof debugging.on, typeof $debugging.isAttached].join('/');",
        f"files.write('{DIRECTORY}/debugging.result', 'ran ' + probeAttached + ' ' + probeKinds);",
    ],
    "plain.ts": [
        "const probeSum: number = [1, 2, 3].reduce((total: number, value: number) => total + value, 0);",
        f"files.write('{DIRECTORY}/plain.result', 'ran ' + probeSum);",
    ],
}


def adb(serial: str, *args: str, check: bool = True, timeout: int = 120) -> str:
    completed = subprocess.run(
        ["adb", "-s", serial, *args], capture_output=True, text=True,
        encoding="utf-8", errors="replace", timeout=timeout,
    )
    if check and completed.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args[:2])} failed: {completed.stderr.strip()[:200]}")
    return completed.stdout


def package_version(serial: str, package: str) -> str:
    dump = adb(serial, "shell", f"dumpsys package {package}", check=False)
    code = next((part.split("=", 1)[1] for line in dump.splitlines() for part in line.split()
                 if part.startswith("versionCode=")), None)
    name = next((line.split("=", 1)[1].strip() for line in dump.splitlines()
                 if line.strip().startswith("versionName=")), None)
    return f"{name} ({code})" if code else "not installed"


def webview(serial: str) -> str:
    dump = adb(serial, "shell", "dumpsys webviewupdate", check=False)
    line = next((line.strip() for line in dump.splitlines() if "Current WebView package" in line), "")
    return line.split(":", 1)[-1].strip() or "unknown"


def device_time(serial: str) -> str:
    return adb(serial, "shell", "date '+%m-%d %H:%M:%S.000'").strip()


def read_result(serial: str, name: str, seconds: int) -> str:
    for _ in range(seconds):
        time.sleep(1.0)
        text = adb(serial, "shell", f"cat {DIRECTORY}/{name}", check=False).strip()
        if text and "No such file" not in text:
            return text
    return ""


def run_script(serial: str, name: str) -> None:
    adb(serial, "shell", f"am start -n {HOST_PACKAGE}/{RUN_ACTIVITY} -e path {DIRECTORY}/{name}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", help="install this TypeScript Engine APK first (adb install -r)")
    parser.add_argument("--expect-old-webview", action="store_true")
    parser.add_argument("--wait", type=int, default=60, help="seconds to wait for each result")
    parser.add_argument("--cleanup", action="store_true")
    args = parser.parse_args()
    serial = args.serial

    if args.cleanup:
        adb(serial, "shell", f"rm -rf {DIRECTORY}", check=False)
        print(f"CLEANUP removed {DIRECTORY}")
        return 0
    if args.apk:
        adb(serial, "install", "-r", args.apk, timeout=300)
    print(f"INFO host={package_version(serial, HOST_PACKAGE)} "
          f"plugin={package_version(serial, PLUGIN_PACKAGE)} webview={webview(serial)}")

    adb(serial, "shell", f"rm -rf {DIRECTORY}", check=False)
    adb(serial, "shell", f"mkdir -p {DIRECTORY}")
    for name, lines in SCRIPTS.items():
        adb(serial, "shell", f"rm -f {DIRECTORY}/{name}", check=False)
        local = subprocess.run(["adb", "-s", serial, "shell", f"cat > {DIRECTORY}/{name}"],
                               input=("\n".join(lines) + "\n").encode("utf-8"), capture_output=True, timeout=60)
        if local.returncode != 0:
            raise RuntimeError(f"push {name} failed")
    # AutoJs6 in the foreground first: some systems freeze or rate limit background starts.
    adb(serial, "shell", f"monkey -p {HOST_PACKAGE} -c android.intent.category.LAUNCHER 1", check=False)
    time.sleep(8.0)
    # A run request sent soon after a cold start can be dropped: repeat a plain JavaScript
    # warm-up until it answers, so every case below is sent to a ready host.
    warmed = False
    for _ in range(3):
        run_script(serial, "warm-up.js")
        if read_result(serial, "warm-up.result", 15):
            warmed = True
            break
    print(f"INFO warm-up={'ok' if warmed else 'no answer'}")

    failures = 0
    if args.expect_old_webview:
        since = device_time(serial)
        run_script(serial, "plain.ts")
        result = read_result(serial, "plain.result", min(args.wait, 30))
        log = adb(serial, "logcat", "-d", "-v", "brief", "-T", since, check=False)
        lines = [line for line in log.splitlines() if WEBVIEW_ERROR_MARKER in line]
        passed = not result and bool(lines)
        failures += 0 if passed else 1
        shown = lines[0].split(":", 1)[-1].strip()[:240] if lines else "-"
        print(f"{'PASS' if passed else 'FAIL'} old-webview ran={'yes' if result else 'no'} "
              f"log_lines={len(lines)} first={shown}")
    else:
        for case, script, expected in (
            ("debugging", "debugging.ts", "ran false function/function"),
            ("plain", "plain.ts", "ran 6"),
        ):
            run_script(serial, script)
            result = read_result(serial, script.replace(".ts", ".result"), args.wait)
            passed = result == expected
            failures += 0 if passed else 1
            print(f"{'PASS' if passed else 'FAIL'} {case} result={result or '-'} expected={expected}")
    print(f"SUMMARY {'PASS' if failures == 0 else 'FAIL'} failures={failures}")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
