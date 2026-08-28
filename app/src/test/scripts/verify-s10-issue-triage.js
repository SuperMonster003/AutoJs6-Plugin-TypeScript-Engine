"use strict";

const fs = require("node:fs");
const path = require("node:path");
const { spawnSync } = require("node:child_process");

const [repositoryArgument] = process.argv.slice(2);
if (!repositoryArgument) {
    throw new Error("Usage: node verify-s10-issue-triage.js <repository-root>");
}

const repositoryRoot = path.resolve(repositoryArgument);
const resolve = (...segments) => path.join(repositoryRoot, ...segments);
const readUtf8 = (...segments) => fs.readFileSync(resolve(...segments), "utf8");
const readJson = (...segments) => JSON.parse(readUtf8(...segments));
const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const assertIncludes = (source, marker, owner) =>
    assert(source.includes(marker), owner + " must contain " + JSON.stringify(marker) + ".");

const issueForm = readUtf8(
    ".github",
    "ISSUE_TEMPLATE",
    "typescript-bug.yml",
);
for (const marker of [
    "name: TypeScript 编译问题",
    "id: privacy",
    "id: autojs_version",
    "id: plugin_version",
    "id: android_webview",
    "id: profile",
    "id: stable_error",
    "id: reproduction",
    "id: diagnostics",
    "id: performance",
    "collect-typescript-issue-evidence.mjs",
    "diagnostics.redacted.json",
    "typescript-compile-perf/schema=2",
    "required: true",
]) {
    assertIncludes(issueForm, marker, "TypeScript issue form");
}
const issueIds = [...issueForm.matchAll(/^\s+id:\s+([a-z][a-z0-9_]*)\s*$/gmu)].map(
    (match) => match[1],
);
assert(issueIds.length >= 10, "TypeScript issue form has too few structured fields.");
assert(
    new Set(issueIds).size === issueIds.length,
    "TypeScript issue form ids must be unique.",
);

const triageGuide = readUtf8("docs", "maintenance", "ISSUE_TRIAGE.md");
for (const marker of [
    "接收 -> 脱敏 -> 最小复现 -> 分类 -> 修复 -> 验证",
    "ERR_AUTOJS6_TYPESCRIPT_*",
    "collect-typescript-issue-evidence.mjs",
    "build/reports/issue-triage/<timestamp>/",
    "typescript-compile-perf schema=2",
    "typescript-compiler-perf schema=1",
    "expected boundary",
    "compiler regression",
    "host integration",
    "none-maintenance-only",
    "LOCAL-TS-2026-08-25-01",
    "不创建 GitHub issue",
]) {
    assertIncludes(triageGuide, marker, "Issue triage guide");
}

const caseRecord = readUtf8(
    "docs",
    "maintenance",
    "issue-triage",
    "LOCAL-TS-2026-08-25-01.md",
);
for (const marker of [
    "| 状态 | resolved |",
    "R2 common declaration lock is not the frozen TypeScript 6.0.3 component",
    "最小复现",
    "stable 6.x",
    "6.0.2 positive",
    "7.0.2 rejection",
    ":app:verifyTypeScriptUpgradeRehearsal",
    "API 35 / WebView 130",
    "API 37 / WebView 145",
    "none-maintenance-only",
    "无远端副作用",
]) {
    assertIncludes(caseRecord, marker, "Real local issue record");
}

const collectorSource = readUtf8(
    "tools",
    "collect-typescript-issue-evidence.mjs",
);
for (const marker of [
    "MAX_DIAGNOSTICS_BYTES = 512 * 1024",
    "MAX_LOGCAT_BYTES = 16 * 1024 * 1024",
    "ignored build/reports/issue-triage",
    "autojs6.typescript.diagnostics",
    "requestIdentityRedacted",
    "projectIdentityRedacted",
    "uriPathsRedacted",
    "messagesRedacted",
    "typescript-compile-perf",
    "typescript-compiler-perf",
    "unsafeOrUnknownLinesOmitted",
    "networkUsed: false",
    "sourceMutation: \"none\"",
]) {
    assertIncludes(collectorSource, marker, "Issue evidence collector");
}
for (const forbidden of [
    "fetch(",
    "node:http",
    "node:https",
    "child_process",
    "exec(",
    "spawn(",
]) {
    assert(
        !collectorSource.includes(forbidden),
        "Issue evidence collector must remain offline and non-executing: " + forbidden,
    );
}

const verificationBase = resolve(
    "build",
    "reports",
    "issue-triage",
);
fs.mkdirSync(verificationBase, { recursive: true });
const forbiddenOutput = resolve("docs", "issue-triage-verifier-must-not-exist");
assert(
    !fs.existsSync(forbiddenOutput),
    "Forbidden-output fixture path already exists.",
);
const forbiddenResult = spawnSync(
    process.execPath,
    [
        resolve("tools", "collect-typescript-issue-evidence.mjs"),
        "--diagnostics",
        resolve(
            "app",
            "src",
            "test",
            "resources",
            "issue-triage",
            "diagnostics-sensitive.json",
        ),
        "--output-dir",
        path.relative(repositoryRoot, forbiddenOutput),
    ],
    {
        cwd: repositoryRoot,
        encoding: "utf8",
        windowsHide: true,
    },
);
assert(
    forbiddenResult.status !== 0 &&
        forbiddenResult.stderr.includes(
            "Output must be a child of the ignored build/reports/issue-triage directory.",
        ) &&
        !fs.existsSync(forbiddenOutput),
    "Issue collector did not reject a source-tree output path.",
);
const verificationDirectory = fs.mkdtempSync(
    path.join(verificationBase, "verify-"),
);
try {
    const relativeOutput = path.relative(repositoryRoot, verificationDirectory);
    const result = spawnSync(
        process.execPath,
        [
            resolve("tools", "collect-typescript-issue-evidence.mjs"),
            "--diagnostics",
            resolve(
                "app",
                "src",
                "test",
                "resources",
                "issue-triage",
                "diagnostics-sensitive.json",
            ),
            "--logcat",
            resolve(
                "app",
                "src",
                "test",
                "resources",
                "issue-triage",
                "logcat-sensitive.txt",
            ),
            "--output-dir",
            relativeOutput,
        ],
        {
            cwd: repositoryRoot,
            encoding: "utf8",
            windowsHide: true,
        },
    );
    assert(
        result.status === 0,
        "Issue collector fixture failed: " + (result.stderr || result.stdout),
    );
    assertIncludes(
        result.stdout,
        "diagnostics=1, performanceLines=2, omittedPerformanceCandidates=1",
        "Issue collector fixture output",
    );

    const outputNames = fs.readdirSync(verificationDirectory).sort();
    assert(
        JSON.stringify(outputNames) ===
            JSON.stringify([
                "README.md",
                "diagnostics.redacted.json",
                "manifest.json",
                "typescript-performance.log",
            ]),
        "Issue collector emitted an unexpected file set.",
    );
    const combinedOutput = outputNames
        .map((name) =>
            fs.readFileSync(path.join(verificationDirectory, name), "utf8"),
        )
        .join("\n");
    for (const secret of [
        "SECRET",
        "CustomerAccount",
        "/private/",
        "private/customer",
        "diagnostics-sensitive.json",
        "logcat-sensitive.txt",
    ]) {
        assert(
            !combinedOutput.includes(secret),
            "Issue collector leaked fixture secret " + JSON.stringify(secret) + ".",
        );
    }

    const redacted = JSON.parse(
        fs.readFileSync(
            path.join(verificationDirectory, "diagnostics.redacted.json"),
            "utf8",
        ),
    );
    assert(
        redacted.schema ===
            "autojs6.typescript.issue-evidence.diagnostics" &&
            redacted.messagesRedacted === true &&
            redacted.uriPathsRedacted === true &&
            redacted.requestIdentityRedacted === true &&
            redacted.projectIdentityRedacted === true &&
            redacted.diagnosticCount === 1 &&
            redacted.relatedInformationCount === 1,
        "Redacted diagnostics envelope is incomplete.",
    );
    const primary = redacted.diagnostics[0];
    assert(
        primary.phase === "semantic" &&
            primary.sourceDomain === "sources" &&
            primary.code === 2322 &&
            primary.range.line === 2 &&
            primary.relatedInformation[0].sourceDomain === "declarations" &&
            primary.relatedInformation[0].code === 6500,
        "Redacted diagnostics lost stable reproduction evidence.",
    );

    const performanceLines = fs
        .readFileSync(
            path.join(verificationDirectory, "typescript-performance.log"),
            "utf8",
        )
        .trim()
        .split(/\r?\n/u);
    assert(
        performanceLines.length === 2 &&
            performanceLines[0].startsWith("typescript-compile-perf schema=2 ") &&
            performanceLines[1].startsWith("typescript-compiler-perf schema=1 "),
        "Performance evidence did not retain both exact safe schemas.",
    );
    const manifest = JSON.parse(
        fs.readFileSync(
            path.join(verificationDirectory, "manifest.json"),
            "utf8",
        ),
    );
    assert(
        manifest.schema === "autojs6.typescript.issue-evidence" &&
            manifest.revision === 1 &&
            manifest.networkUsed === false &&
            manifest.sourceMutation === "none" &&
            manifest.outputPolicy === "ignored-build-reports-only" &&
            manifest.performance.safeLineCount === 2 &&
            manifest.performance.candidateLineCount === 3 &&
            manifest.performance.unsafeOrUnknownLinesOmitted === 1,
        "Issue evidence manifest drifted.",
    );
} finally {
    const relative = path.relative(verificationBase, verificationDirectory);
    assert(
        relative &&
            !relative.startsWith("..") &&
            !path.isAbsolute(relative) &&
            path.basename(verificationDirectory).startsWith("verify-"),
        "Refusing to clean an unexpected issue-verifier path.",
    );
    fs.rmSync(verificationDirectory, { recursive: true, force: false });
}

const roadmap = readUtf8("ROADMAP.md");
const maintenanceIndex = readUtf8("docs", "maintenance", "README.md");
const readme = readUtf8("README.md");
const sourceMapGuide = readUtf8("docs", "user", "SOURCE_MAPS.md");
assertIncludes(roadmap, "- [x] **S10-5 issue 分诊模板**", "Roadmap");
assertIncludes(maintenanceIndex, "ISSUE_TRIAGE.md", "Maintenance index");
assertIncludes(readme, "ISSUE_TRIAGE.md", "README maintenance entry");
assertIncludes(sourceMapGuide, "ISSUE_TRIAGE.md", "Source Map troubleshooting guide");

process.stdout.write(
    "S10-5 issue triage verified: structured local issue form, privacy-bounded " +
        "diagnostics/performance collector, one resolved real maintenance issue, " +
        "explicit changelog decision, and no remote mutation.\n",
);
