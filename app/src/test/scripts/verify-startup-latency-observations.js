"use strict";

const fs = require("node:fs");
const path = require("node:path");

const [repositoryArgument] = process.argv.slice(2);
if (!repositoryArgument) {
    throw new Error("Usage: node verify-startup-latency-observations.js <repository-root>");
}

const repositoryRoot = path.resolve(repositoryArgument);
const resolve = (...segments) => path.join(repositoryRoot, ...segments);
const readUtf8 = (...segments) => fs.readFileSync(resolve(...segments), "utf8");
const readJson = (...segments) => JSON.parse(readUtf8(...segments));
const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const assertIncludes = (source, marker, owner) =>
    assert(source.includes(marker), `${owner} must contain ${JSON.stringify(marker)}.`);
const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;
const exactKeys = (value, expected) =>
    value !== null &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    JSON.stringify(Object.keys(value).sort(compareCodeUnits)) ===
        JSON.stringify(expected.slice().sort(compareCodeUnits));
const isPositiveInteger = (value) => Number.isInteger(value) && value > 0;
const isNonNegativeInteger = (value) => Number.isInteger(value) && value >= 0;
const sha256Pattern = /^[0-9a-f]{64}$/;
const isoDatePattern = /^\d{4}-\d{2}-\d{2}(T\d{2}:\d{2}:\d{2}(\.\d+)?Z)?$/;

// ---------------------------------------------------------------------------------------------
// Fixed identities that the observation history keeps aligned with code and documents.
// ---------------------------------------------------------------------------------------------

const roadmap = readUtf8("startup-latency-roadmap.md");
const coldStart = readUtf8("docs", "maintenance", "COLD_START.md");
const maintenanceReadme = readUtf8("docs", "maintenance", "README.md");
const releaseChecklist = readUtf8("docs", "maintenance", "RELEASE_CHECKLIST.md");
const observeScript = readUtf8("tools", "observe-startup-latency.ps1");
const fixture = readUtf8("tools", "fixtures", "startup-latency", "single-file.ts");
const bootTelemetry = readUtf8(
    "app",
    "src",
    "main",
    "java",
    "io",
    "github",
    "supermonster003",
    "autojs6",
    "plugin",
    "typescript",
    "R2CompilerBootTelemetry.kt",
);
const workerSource = readUtf8(
    "app",
    "src",
    "main",
    "assets",
    "typescript-compiler-r1",
    "compiler-worker.js",
);
const observations = readJson("docs", "maintenance", "startup-latency-observations.json");

const bootSchemaMatch = bootTelemetry.match(/const val SCHEMA_REVISION = (\d+)/);
assert(bootSchemaMatch, "R2CompilerBootTelemetry must declare SCHEMA_REVISION.");
const bootSchemaRevision = Number.parseInt(bootSchemaMatch[1], 10);
assertIncludes(
    bootTelemetry,
    'const val LOG_PREFIX = "typescript-compiler-boot"',
    "R2CompilerBootTelemetry",
);
for (const marker of [
    "const workerBootstrapStartedAt = performance.now();",
    "bootstrapMs: workerBootstrapMs,",
]) {
    assertIncludes(workerSource, marker, "Compiler Worker boot telemetry");
}

assertIncludes(fixture, 'const RUN_MARKER = "run-00000000";', "Startup-latency fixture");
assertIncludes(fixture, "startup-latency first-statement", "Startup-latency fixture");
assertIncludes(fixture, 'const STAMP_PATH = "first-statement.txt";', "Startup-latency fixture");
for (const marker of [
    "typescript-compile-perf",
    "typescript-execution-perf",
    "typescript-compiler-boot",
    "typescript-prewarm",
    "org.autojs.autojs.external.open.RunIntentActivity",
    'const RUN_MARKER = "run-[0-9A-Za-z]+";',
    'const STAMP_PATH = "[^"]+";',
    "gate = \"none\"",
]) {
    assertIncludes(observeScript, marker, "observe-startup-latency.ps1");
}
assert(
    !/\binstall\b|\buninstall\b/.test(observeScript.replace(/^\s*#.*$/gm, "").replace(/<#[\s\S]*?#>/g, "")),
    "observe-startup-latency.ps1 must never install or uninstall packages.",
);

for (const marker of [
    "tools/observe-startup-latency.ps1",
    "docs/maintenance/startup-latency-observations.json",
    "tools/fixtures/startup-latency/single-file.ts",
    "| S1 全冷 |",
    "| S2 热路径 |",
    "| S3 缓存命中 |",
]) {
    assertIncludes(roadmap, marker, "startup-latency-roadmap.md");
}
assertIncludes(coldStart, "startup-latency-roadmap.md", "COLD_START.md");
assertIncludes(coldStart, "startup-latency-observations.json", "COLD_START.md");
assertIncludes(maintenanceReadme, "startup-latency-observations.json", "docs/maintenance/README.md");
assertIncludes(releaseChecklist, "observe-startup-latency.ps1", "RELEASE_CHECKLIST.md");

// ---------------------------------------------------------------------------------------------
// Observation history schema. Values are evidence, never thresholds: `gate` stays "none".
// ---------------------------------------------------------------------------------------------

assert(
    exactKeys(observations, [
        "schemaVersion",
        "roadmap",
        "fixture",
        "scenarios",
        "targets",
        "logSchemas",
        "observations",
    ]),
    "startup-latency-observations.json has unexpected top-level keys.",
);
assert(observations.schemaVersion === 1, "Observation history schema must stay at 1.");
assert(observations.roadmap === "startup-latency-roadmap.md", "Observation roadmap link drifted.");
assert(
    exactKeys(observations.fixture, ["source", "profile", "sourceFiles", "tool", "gate"]) &&
        observations.fixture.source === "tools/fixtures/startup-latency/single-file.ts" &&
        observations.fixture.profile === "rhino" &&
        observations.fixture.sourceFiles === 1 &&
        observations.fixture.tool === "tools/observe-startup-latency.ps1" &&
        observations.fixture.gate === "none",
    "Observation fixture identity drifted.",
);
assert(
    exactKeys(observations.scenarios, ["S1", "S2", "S3"]) &&
        Object.values(observations.scenarios).every(
            (definition) => typeof definition === "string" && definition.length > 0,
        ),
    "Observation scenarios must define exactly S1, S2 and S3.",
);
assert(
    exactKeys(observations.targets, ["referenceDevice", "metric", "S1", "S2", "S3"]) &&
        observations.targets.referenceDevice === "api35-23046rp50c" &&
        observations.targets.metric === "execution.userWaitMs p50" &&
        observations.targets.S1 === 1500 &&
        observations.targets.S2 === 500 &&
        observations.targets.S3 === 300,
    "Observation targets drifted from the roadmap decision of 2026-10-09.",
);
for (const [targetKey, roadmapMarker] of [
    ["S1", "<= 1.5 s"],
    ["S2", "<= 500 ms"],
    ["S3", "<= 300 ms"],
]) {
    assertIncludes(roadmap, roadmapMarker, `Roadmap target ${targetKey}`);
}
assert(
    exactKeys(observations.logSchemas, ["hostCompile", "hostExecution", "hostPrewarm", "pluginBoot"]) &&
        observations.logSchemas.pluginBoot === `typescript-compiler-boot schema=${bootSchemaRevision}` &&
        /^typescript-compile-perf schema=\d+$/.test(observations.logSchemas.hostCompile) &&
        /^typescript-execution-perf schema=\d+$/.test(observations.logSchemas.hostExecution) &&
        /^typescript-prewarm schema=\d+$/.test(observations.logSchemas.hostPrewarm),
    "Observation log schemas drifted from the plugin boot telemetry or host line names.",
);

assert(Array.isArray(observations.observations), "Observations must be an array.");
const seenIds = new Set();
for (const observation of observations.observations) {
    assert(
        exactKeys(observation, [
            "id",
            "observedAt",
            "deviceLabel",
            "device",
            "webView",
            "host",
            "plugin",
            "buildType",
            "runsPerScenario",
            "scenarios",
            "provenance",
        ]),
        `Observation ${JSON.stringify(observation.id)} has unexpected keys.`,
    );
    assert(
        typeof observation.id === "string" && /^\d{8}-l\d-[a-z0-9-]+$/.test(observation.id),
        `Observation id must look like YYYYMMDD-l<phase>-<device>: ${observation.id}`,
    );
    assert(!seenIds.has(observation.id), `Duplicate observation id ${observation.id}.`);
    seenIds.add(observation.id);
    assert(isoDatePattern.test(observation.observedAt), `Bad observedAt in ${observation.id}.`);
    assert(
        typeof observation.deviceLabel === "string" && /^[a-z0-9-]+$/.test(observation.deviceLabel),
        `Bad deviceLabel in ${observation.id}.`,
    );
    assert(
        exactKeys(observation.device, ["api", "model", "abi"]) &&
            isPositiveInteger(observation.device.api) &&
            typeof observation.device.model === "string" &&
            typeof observation.device.abi === "string",
        `Bad device in ${observation.id}.`,
    );
    assert(
        exactKeys(observation.webView, ["package", "version"]) &&
            typeof observation.webView.version === "string",
        `Bad webView in ${observation.id}.`,
    );
    for (const side of ["host", "plugin"]) {
        const value = observation[side];
        assert(
            exactKeys(value, ["versionName", "versionCode", "commit"]) &&
                typeof value.versionName === "string" &&
                isPositiveInteger(value.versionCode) &&
                (value.commit === null || /^[0-9a-f]{7,40}$/.test(value.commit)),
            `Bad ${side} identity in ${observation.id}.`,
        );
    }
    assert(
        ["debug", "release"].includes(observation.buildType),
        `Bad buildType in ${observation.id}.`,
    );
    assert(
        isPositiveInteger(observation.runsPerScenario) && observation.runsPerScenario % 2 === 1,
        `runsPerScenario must be odd in ${observation.id}.`,
    );
    assert(
        typeof observation.scenarios === "object" &&
            Object.keys(observation.scenarios).every((key) => ["S1", "S2", "S3"].includes(key)) &&
            Object.keys(observation.scenarios).length > 0,
        `Scenarios must be a non-empty subset of S1/S2/S3 in ${observation.id}.`,
    );
    for (const [scenarioId, scenario] of Object.entries(observation.scenarios)) {
        assert(
            exactKeys(scenario, ["samples", "medians"]) &&
                Array.isArray(scenario.samples) &&
                scenario.samples.length === observation.runsPerScenario,
            `Scenario ${scenarioId} in ${observation.id} must hold exactly runsPerScenario samples.`,
        );
        for (const sample of scenario.samples) {
            assert(
                typeof sample === "object" &&
                    sample !== null &&
                    typeof sample.coldCompilerProcess === "boolean" &&
                    typeof sample.fixtureSha256 === "string" &&
                    sha256Pattern.test(sample.fixtureSha256) &&
                    Object.entries(sample).every(
                        ([key, value]) =>
                            key === "coldCompilerProcess" ||
                            key === "fixtureSha256" ||
                            (key === "warmBindingRetained" && typeof value === "boolean") ||
                            isNonNegativeInteger(value) ||
                            value === null,
                    ),
                `Scenario ${scenarioId} sample in ${observation.id} must contain only ` +
                    "non-negative integer durations or counters, coldCompilerProcess, " +
                    "warmBindingRetained and fixtureSha256.",
            );
            assert(
                !Object.keys(sample).some((key) => /path|request|uri|sourcename|sourcetext|identity/i.test(key)),
                `Scenario ${scenarioId} sample in ${observation.id} leaks identity fields.`,
            );
        }
        if (scenarioId === "S1") {
            assert(
                scenario.samples.every((sample) => sample.coldCompilerProcess === true),
                `S1 samples in ${observation.id} must all start from a cold compiler process.`,
            );
        } else {
            assert(
                scenario.samples.every((sample) => sample.coldCompilerProcess === false),
                `${scenarioId} samples in ${observation.id} must all run in a warm compiler process.`,
            );
        }
        assert(
            typeof scenario.medians === "object" &&
                scenario.medians !== null &&
                isNonNegativeInteger(scenario.medians.userWaitMs),
            `Scenario ${scenarioId} in ${observation.id} must record a userWaitMs median.`,
        );
        const sorted = scenario.samples
            .map((sample) => sample.userWaitMs)
            .filter(isNonNegativeInteger)
            .sort((left, right) => left - right);
        assert(
            sorted.length === scenario.samples.length &&
                sorted[Math.floor(sorted.length / 2)] === scenario.medians.userWaitMs,
            `Scenario ${scenarioId} userWaitMs median in ${observation.id} does not match its samples.`,
        );
    }
    assert(
        exactKeys(observation.provenance, ["kind", "reportFile", "note"]) &&
            observation.provenance.kind === "live-device-run" &&
            typeof observation.provenance.reportFile === "string" &&
            typeof observation.provenance.note === "string",
        `Bad provenance in ${observation.id}.`,
    );
}

process.stdout.write(
    `startup-latency observations verified: ${observations.observations.length} records, ` +
        `boot schema ${bootSchemaRevision}, gate=none\n`,
);
