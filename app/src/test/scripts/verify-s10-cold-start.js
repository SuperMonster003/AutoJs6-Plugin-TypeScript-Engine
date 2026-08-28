"use strict";

const fs = require("node:fs");
const path = require("node:path");

const [repositoryArgument] = process.argv.slice(2);
if (!repositoryArgument) {
    throw new Error("Usage: node verify-s10-cold-start.js <repository-root>");
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
const median = (values) => {
    const sorted = values.slice().sort((left, right) => left - right);
    assert(sorted.length > 0 && sorted.length % 2 === 1, "Median input must be odd.");
    return sorted[Math.floor(sorted.length / 2)];
};
const roundedImprovement = (baseline, candidate) =>
    Math.round((((baseline - candidate) / baseline) * 100 + Number.EPSILON) * 10) / 10;

const kotlinTransport = readUtf8(
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
    "R2ProjectFramedTransport.kt",
);
const pageSource = readUtf8(
    "app",
    "src",
    "main",
    "assets",
    "typescript-compiler-r1",
    "compiler-host.js",
);
const workerSource = readUtf8(
    "app",
    "src",
    "main",
    "assets",
    "typescript-compiler-r1",
    "compiler-worker.js",
);
const instrumentation = readUtf8(
    "app",
    "src",
    "androidTest",
    "java",
    "io",
    "github",
    "supermonster003",
    "autojs6",
    "plugin",
    "typescript",
    "R2ProjectCompilerInstrumentationTest.kt",
);
const transportTest = readUtf8(
    "app",
    "src",
    "test",
    "java",
    "io",
    "github",
    "supermonster003",
    "autojs6",
    "plugin",
    "typescript",
    "R2ProjectFramedTransportTest.kt",
);
const escapeHarness = readUtf8(
    "app",
    "src",
    "test",
    "scripts",
    "verify-r2-compiler-escape-policy.js",
);

for (const marker of [
    "R2_PROJECT_MAXIMUM_BATCHABLE_FRAME_BYTES = 92 * 1024",
    "R2_PROJECT_INPUT_BATCH_MAXIMUM_FRAMES = 6",
    "R2_PROJECT_INPUT_BATCH_MAXIMUM_PAYLOAD_CHUNK_FRAMES = 4",
    "R2_PROJECT_FRAME_RAW_CHUNK_BYTES = 64 * 1024",
    "R2_PROJECT_MAXIMUM_START_FRAME_BYTES = 576 * 1024",
    "!delegate.nextFrameIsPayloadChunk",
    "encodedFrameBytes <= R2_PROJECT_MAXIMUM_BATCHABLE_FRAME_BYTES",
]) {
    assertIncludes(kotlinTransport, marker, "Native 6/4 transport");
}
for (const [source, owner] of [
    [pageSource, "Compiler page"],
    [workerSource, "Compiler Worker"],
]) {
    for (const marker of [
        "maximumProjectInputBatchFrames = 6",
        "maximumProjectInputBatchPayloadChunkFrames = 4",
        "batchedProjectInputPayloadChunkKinds",
        "Project input batch has too many payload chunks.",
    ]) {
        assertIncludes(source, marker, owner);
    }
}
for (const marker of [
    "R2_PROJECT_INPUT_BATCH_MAXIMUM_PAYLOAD_CHUNK_FRAMES",
    "payloadChunkFrames <=",
    "R2_PROJECT_MAXIMUM_BATCHABLE_FRAME_BYTES",
]) {
    assertIncludes(transportTest, marker, "Native transport regression");
}
for (const marker of [
    "Array.from({ length: 5 }",
    "project-input-batch payload-chunk cap",
    "too many payload chunks",
    "assertControlledSuccessorReusable",
]) {
    assertIncludes(escapeHarness, marker, "Worker payload-cap regression");
}

const expectedFixture = Object.freeze({
    testClass:
        "io.github.supermonster003.autojs6.plugin.typescript." +
        "R2ProjectCompilerInstrumentationTest",
    testMethod: "packagedStandardLibrariesCrossTheFormerSingleMessageLimit",
    profile: "rhino",
    sourceFiles: 109,
    minimumInputBytes: 3 * 1024 * 1024,
    logicalFrames: 379,
    phaseClock: "SystemClock.elapsedRealtimeNanos",
    observationLogSchema: 1,
    gate: "none",
});
for (const marker of [
    expectedFixture.testMethod,
    "assertEquals(109, snapshot.files.size)",
    "snapshot.totalUtf8Bytes > 3L * 1024L * 1024L",
    "typescript-cold-start-perf schema=1",
    "batchPayloadChunks=",
    "compilerMs=${outcome.elapsedMs}",
    "ingressMs=",
    "gate=none",
]) {
    assertIncludes(instrumentation, marker, "Cold-start instrumentation");
}

const history = readJson(
    "docs",
    "maintenance",
    "cold-start-observations.json",
);
assert(
    exactKeys(history, ["schemaVersion", "fixture", "optimization", "observations"]),
    "Cold-start history root shape drifted.",
);
assert(history.schemaVersion === 1, "Cold-start history schema must remain revision 1.");
assert(
    JSON.stringify(history.fixture) === JSON.stringify(expectedFixture),
    "Cold-start history fixture drifted from the Android instrumentation.",
);
assert(
    exactKeys(history.optimization, ["id", "baseline", "candidate", "unchanged"]),
    "Cold-start optimization shape drifted.",
);
assert(
    history.optimization.id === "s10-1-six-middle-four-payload",
    "Cold-start optimization identity drifted.",
);
const { baseline, candidate, unchanged } = history.optimization;
assert(
    baseline.maximumMiddleFramesPerEnvelope === 4 &&
        baseline.maximumPayloadChunksPerEnvelope === 4 &&
        baseline.envelopes === 97 &&
        baseline.maximumEncodedEnvelopeBytes === 350201,
    "Cold-start max4 baseline transport identity drifted.",
);
assert(
    candidate.maximumMiddleFramesPerEnvelope === 6 &&
        candidate.maximumPayloadChunksPerEnvelope === 4 &&
        candidate.maximumEncodedMiddleFrameBytes === 92 * 1024 &&
        candidate.envelopes === 68 &&
        candidate.maximumEncodedEnvelopeBytes === 350433,
    "Cold-start 6/4 candidate transport identity drifted.",
);
assert(
    unchanged.rawPayloadChunkBytes === 64 * 1024 &&
        unchanged.hardEnvelopeBytes === 576 * 1024 &&
        unchanged.outerAcknowledgementWindow === 1 &&
        unchanged.projectStartAndEndRemainSingle === true &&
        unchanged.resultTransportRemainsStopAndWait === true,
    "Cold-start unchanged safety boundary drifted.",
);
assert(candidate.envelopes < baseline.envelopes, "6/4 batching must reduce envelopes.");
assert(
    candidate.maximumEncodedEnvelopeBytes <= unchanged.hardEnvelopeBytes,
    "Observed candidate envelope exceeds the unchanged hard limit.",
);

assert(
    Array.isArray(history.observations) && history.observations.length === 4,
    "Cold-start history must contain exactly the four S10-1 device observations.",
);
const expectedApis = new Set([31, 35, 36, 37]);
const seenIds = new Set();
for (const observation of history.observations) {
    assert(
        exactKeys(observation, [
            "id",
            "observedAt",
            "releaseVersion",
            "buildType",
            "device",
            "webViewVersion",
            "baseline",
            "candidate",
            "comparison",
            "provenance",
        ]),
        `Cold-start observation ${observation.id} shape drifted.`,
    );
    assert(observation.id && !seenIds.has(observation.id), "Duplicate observation id.");
    seenIds.add(observation.id);
    assert(observation.observedAt === "2026-08-25", `${observation.id} date drifted.`);
    assert(observation.releaseVersion === "0.6.0", `${observation.id} release drifted.`);
    assert(observation.buildType === "debug", `${observation.id} must be debug A/B data.`);
    assert(expectedApis.delete(observation.device.api), `${observation.id} API is unexpected.`);
    assert(
        /^(?:130|134|145)\./u.test(observation.webViewVersion),
        `${observation.id} WebView identity is invalid.`,
    );
    for (const phase of ["baseline", "candidate"]) {
        const measurement = observation[phase];
        assert(
            measurement.sampleCount >= 3 && measurement.sampleCount % 2 === 1,
            `${observation.id}/${phase} sample count must be odd and at least three.`,
        );
        assert(
            measurement.compilerMs.length === measurement.sampleCount &&
                measurement.ingressMs.length === measurement.sampleCount,
            `${observation.id}/${phase} raw sample count drifted.`,
        );
        assert(
            measurement.compilerMs.every((value) => Number.isInteger(value) && value > 0) &&
                measurement.ingressMs.every((value) => Number.isInteger(value) && value > 0),
            `${observation.id}/${phase} contains an invalid duration.`,
        );
        assert(
            median(measurement.compilerMs) === measurement.compilerMedianMs &&
                median(measurement.ingressMs) === measurement.ingressMedianMs,
            `${observation.id}/${phase} median does not match raw samples.`,
        );
    }
    assert(
        observation.candidate.compilerMedianMs < observation.baseline.compilerMedianMs &&
            observation.candidate.ingressMedianMs < observation.baseline.ingressMedianMs,
        `${observation.id} does not show improvement in both observed medians.`,
    );
    assert(
        observation.comparison.compilerImprovementPercent ===
            roundedImprovement(
                observation.baseline.compilerMedianMs,
                observation.candidate.compilerMedianMs,
            ) &&
            observation.comparison.ingressImprovementPercent ===
                roundedImprovement(
                    observation.baseline.ingressMedianMs,
                    observation.candidate.ingressMedianMs,
                ),
        `${observation.id} comparison percentage drifted from its raw medians.`,
    );
    assert(
        observation.provenance.kind === "live-instrumentation" &&
            observation.provenance.baselineCapture &&
            observation.provenance.candidateCapture,
        `${observation.id} has incomplete provenance.`,
    );
}
assert(expectedApis.size === 0, "Cold-start four-device API matrix is incomplete.");

const guide = readUtf8("docs", "maintenance", "COLD_START.md");
const maintenanceIndex = readUtf8("docs", "maintenance", "README.md");
const releaseChecklist = readUtf8(
    "docs",
    "maintenance",
    "RELEASE_CHECKLIST.md",
);
const releaseNotes = readUtf8("docs", "releases", "v0.6.0.md");
const roadmap = readUtf8("ROADMAP.md");
const readme = readUtf8("README.md");
const transportArchitecture = readUtf8(
    "docs",
    "architecture",
    "PROJECT_TRANSPORT_R2_PROVISIONAL.md",
);
const fileBackedArchitecture = readUtf8(
    "docs",
    "architecture",
    "FILE_BACKED_INPUT_R2_P3A.md",
);
const observationScript = readUtf8("tools", "observe-cold-start.ps1");

for (const marker of [
    "最多 6 条",
    "最多 4 条",
    "97 个",
    "68 个",
    "350433 B",
    "API 31 Sony XQ-AT72",
    "API 35 Xiaomi 23046RP50C",
    "API 36 x86_64 AVD",
    "API 37 x86_64 AVD",
    "typescript-cold-start-perf schema=1",
    "不声称 bind + prewarm",
    "声明按需注入暂不实施",
    "SourceFile",
    "gate=none",
]) {
    assertIncludes(guide, marker, "Cold-start guide");
}
for (const [source, owner] of [
    [maintenanceIndex, "Maintenance index"],
    [releaseChecklist, "Release checklist"],
    [releaseNotes, "v0.6.0 release notes"],
    [roadmap, "Roadmap"],
    [readme, "README"],
]) {
    assertIncludes(source, "COLD_START.md", owner);
}
assertIncludes(roadmap, "- [x] **S10-1 冷启动继续压缩**", "Roadmap");
assertIncludes(roadmap, "68 envelopes", "Roadmap");
assertIncludes(readme, "97 降至 68", "README");
for (const source of [transportArchitecture, fileBackedArchitecture]) {
    assertIncludes(source, "S10-1 performance amendment", "Transport architecture");
    assertIncludes(source, "最多 6", "Transport architecture");
    assertIncludes(source, "最多 4", "Transport architecture");
    assertIncludes(source, "COLD_START.md", "Transport architecture");
}
for (const marker of [
    "-AllowInstall",
    "assembleDebugAndroidTest",
    expectedFixture.testMethod,
    "typescript-cold-start-perf schema=1",
    "build/reports/cold-start",
    "Get-ObservedMedian",
    "uninstall $testPackage",
    "gate = \"none\"",
]) {
    assertIncludes(observationScript, marker, "Cold-start observation script");
}

process.stdout.write(
    "S10-1 cold-start transport verified: 379 frames, 97 -> 68 envelopes, " +
        "6 total/4 payload, four Android-WebView medians improved, gate=none.\n",
);
