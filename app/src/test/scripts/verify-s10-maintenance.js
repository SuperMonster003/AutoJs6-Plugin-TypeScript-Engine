"use strict";

const fs = require("node:fs");
const path = require("node:path");

const [
    repositoryArgument,
    selectedGradle,
    selectedAgp,
    selectedKotlin,
    selectedKsp,
    selectedR8,
    selectedJavaArgument,
    selectedJvmTargetArgument,
] = process.argv.slice(2);
if (
    !repositoryArgument ||
    !selectedGradle ||
    !selectedAgp ||
    !selectedKotlin ||
    !selectedKsp ||
    !selectedR8 ||
    !selectedJavaArgument ||
    !selectedJvmTargetArgument
) {
    throw new Error(
        "Usage: node verify-s10-maintenance.js <repository-root> " +
            "<gradle> <agp> <kotlin> <ksp> <r8> <java> <jvm-target>",
    );
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

const parseProperties = (source) => {
    const result = Object.create(null);
    for (const rawLine of source.split(/\r?\n/u)) {
        const line = rawLine.trim();
        if (!line || line.startsWith("#") || !line.includes("=")) continue;
        const separator = line.indexOf("=");
        result[line.slice(0, separator).trim()] = line.slice(separator + 1).trim();
    }
    return result;
};
const roadmap = readUtf8("ROADMAP.md");
const readme = readUtf8("README.md");
const settings = readUtf8("settings.gradle.kts");
const releaseNotes = readUtf8("docs", "releases", "v0.6.0.md");
const maintenanceIndex = readUtf8("docs", "maintenance", "README.md");
const releaseChecklist = readUtf8(
    "docs",
    "maintenance",
    "RELEASE_CHECKLIST.md",
);
const webViewPolicy = readUtf8("docs", "maintenance", "WEBVIEW_SUPPORT.md");
const dependencyPolicy = readUtf8(
    "docs",
    "maintenance",
    "DEPENDENCY_HEALTH.md",
);
const observationScript = readUtf8("tools", "observe-release-memory.ps1");
const instrumentationSource = readUtf8(
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
    "R2BinderCompilationInstrumentationTest.kt",
);

const readKotlinNumericConstant = (name) => {
    const match = instrumentationSource.match(
        new RegExp(`const\\s+val\\s+${name}\\s*=\\s*([0-9_]+)(?:L)?`, "u"),
    );
    assert(match, `Cannot resolve ${name} from the PSS instrumentation source.`);
    return Number(match[1].replaceAll("_", ""));
};

const expectedFixture = Object.freeze({
    testClass:
        "io.github.supermonster003.autojs6.plugin.typescript." +
        "R2BinderCompilationInstrumentationTest",
    testMethod:
        "recordsTypicalCompilationTimingAndActivePssWithoutPerformanceThreshold",
    profile: "rhino",
    sourceFiles: 8,
    activeMemoryHoldMs: readKotlinNumericConstant("PERFORMANCE_MEMORY_HOLD_MILLIS"),
    pssSampleCount: readKotlinNumericConstant("PERFORMANCE_PSS_SAMPLE_COUNT"),
    pssSampleIntervalMs: readKotlinNumericConstant(
        "PERFORMANCE_PSS_SAMPLE_INTERVAL_MILLIS",
    ),
    gate: "none",
});
assert(
    expectedFixture.activeMemoryHoldMs === 5000 &&
        expectedFixture.pssSampleCount === 3 &&
        expectedFixture.pssSampleIntervalMs === 50,
    "The fixed release-memory fixture changed; review the history schema and checklist.",
);
assertIncludes(
    instrumentationSource,
    "typescript-compiler-perf schema=1",
    "PSS instrumentation",
);
assertIncludes(instrumentationSource, "gate=none", "PSS instrumentation");

const memoryHistory = readJson(
    "docs",
    "maintenance",
    "memory-observations.json",
);
assert(memoryHistory.schemaVersion === 1, "Memory history schema must remain revision 1.");
assert(
    JSON.stringify(memoryHistory.fixture) === JSON.stringify(expectedFixture),
    "Memory history fixture must match the Android instrumentation identity and constants.",
);
assert(Array.isArray(memoryHistory.records), "Memory history records must be an array.");
assert(memoryHistory.records.length >= 3, "Memory history must preserve the three S5-5 baselines.");

const memoryIds = new Set();
for (const record of memoryHistory.records) {
    assert(typeof record.id === "string" && record.id, "Every memory record needs an id.");
    assert(!memoryIds.has(record.id), `Duplicate memory observation id: ${record.id}`);
    memoryIds.add(record.id);
    assert(
        /^\d{4}-\d{2}-\d{2}(?:T\d{2}:\d{2}:\d{2}Z)?$/u.test(record.observedAt),
        `Memory record ${record.id} has a non-canonical observedAt value.`,
    );
    assert(record.releaseVersion, `Memory record ${record.id} needs a release version.`);
    assert(record.buildType === "debug", `Memory record ${record.id} must remain comparable debug data.`);
    assert(
        record.device &&
            typeof record.device.label === "string" &&
            Number.isInteger(record.device.api) &&
            record.device.api >= 24,
        `Memory record ${record.id} has invalid device identity.`,
    );
    const webViewMajor = Number.parseInt(record.webView?.version?.split(".")[0], 10);
    assert(
        Number.isInteger(webViewMajor) && webViewMajor >= 119,
        `Memory record ${record.id} is below the supported WebView 119 baseline.`,
    );
    const values = record.measurements;
    assert(values && values.inputBytes > 0, `Memory record ${record.id} has no input size.`);
    assert(
        values.hostRoundTripMs === null || values.hostRoundTripMs > 0,
        `Memory record ${record.id} has invalid host timing.`,
    );
    for (const key of [
        "compilerMs",
        "compilerServiceMedianPssKiB",
        "rendererMedianPssKiB",
        "combinedMedianPssKiB",
    ]) {
        assert(values[key] > 0, `Memory record ${record.id} has invalid ${key}.`);
    }
    assert(
        values.combinedMedianPssKiB >= values.compilerServiceMedianPssKiB &&
            values.combinedMedianPssKiB >= values.rendererMedianPssKiB,
        `Memory record ${record.id} has an impossible combined PSS value.`,
    );
    assert(record.provenance?.kind, `Memory record ${record.id} needs provenance.`);
}

const migratedCombinedValues = new Set(
    memoryHistory.records
        .filter((record) => record.provenance.kind === "migrated-roadmap-baseline")
        .map((record) => record.measurements.combinedMedianPssKiB),
);
for (const value of [168054, 191542, 225047]) {
    assert(migratedCombinedValues.has(value), `Missing S5-5 combined PSS baseline ${value}.`);
}

for (const marker of [
    expectedFixture.testClass,
    expectedFixture.testMethod,
    "$logTag = \"TypeScriptPerf\"",
    "-AllowInstall",
    "$Promote",
    "gate -ne \"none\"",
    "build/reports/release-memory",
]) {
    assertIncludes(observationScript, marker, "Release-memory script");
}
for (const marker of [
    "tools\\observe-release-memory.ps1",
    "-AllowInstall",
    "-Promote",
    "gate=none",
    "不设置数值阈值",
    "memory-observations.json",
]) {
    assertIncludes(releaseChecklist, marker, "Release checklist");
}

const versionProperties = parseProperties(readUtf8("version.properties"));
const wrapperProperties = parseProperties(
    readUtf8("gradle", "wrapper", "gradle-wrapper.properties"),
);
const wrapperMatch = wrapperProperties.distributionUrl?.match(
    /gradle-([0-9]+(?:\.[0-9]+)+)-bin\.zip$/u,
);
assert(wrapperMatch, "Cannot resolve the Gradle wrapper version.");
assert(
    wrapperMatch[1] === selectedGradle,
    `Running Gradle ${selectedGradle} does not match wrapper ${wrapperMatch[1]}.`,
);
const selectedJava = Number(selectedJavaArgument);
const selectedJvmTarget = Number(selectedJvmTargetArgument);
assert(
    Number.isInteger(selectedJava) && selectedJava > 0,
    `Invalid selected Java version: ${selectedJavaArgument}.`,
);
assert(
    Number.isInteger(selectedJvmTarget) && selectedJvmTarget > 0,
    `Invalid JVM target: ${selectedJvmTargetArgument}.`,
);
assert(
    selectedJava === Number(versionProperties.JAVA_VERSION_MIN_SUGGESTED),
    `Selected Java ${selectedJava} does not match the repository suggestion.`,
);
for (const [name, version] of Object.entries({
    Gradle: selectedGradle,
    AGP: selectedAgp,
    Kotlin: selectedKotlin,
    KSP: selectedKsp,
})) {
    assert(
        /^\d+\.\d+(?:\.\d+)*(?:-[A-Za-z0-9.-]+)?$/u.test(version),
        `Invalid ${name} version from the central platform decision: ${version}.`,
    );
}
assert(
    selectedR8 === "AGP-bundled" ||
        /^\d+\.\d+(?:\.\d+)*(?:-[A-Za-z0-9.-]+)?$/u.test(selectedR8),
    `Invalid R8 decision from the central platform plugin: ${selectedR8}.`,
);
const platformPluginMatch = settings.match(
    /id\("io\.github\.supermonster003\.autojs6-platform-versions"\)\s+version\s+"([^"]+)"/u,
);
assert(platformPluginMatch, "Cannot resolve the central platform plugin version from settings.");
const platformAuthority =
    `io.github.supermonster003.autojs6-platform-versions:${platformPluginMatch[1]}`;
for (const overrideName of [
    "OVERRIDDEN_ANDROID_GRADLE_PLUGIN_VERSION",
    "OVERRIDDEN_JAVA_VERSION",
    "OVERRIDDEN_KOTLIN_GRADLE_PLUGIN_VERSION",
    "OVERRIDDEN_KSP_GRADLE_PLUGIN_VERSION",
]) {
    assert(
        versionProperties[overrideName] === "NONE",
        `${overrideName} must be NONE or the maintenance verifier needs explicit override handling.`,
    );
}

const catalogSource = readUtf8("gradle", "libs.versions.toml");
const catalogSection = catalogSource.match(/\[versions\]([\s\S]*?)\n\[/u)?.[1];
assert(catalogSection, "Cannot resolve the version catalog's [versions] section.");
const catalog = Object.create(null);
for (const match of catalogSection.matchAll(/^([a-z0-9-]+)\s*=\s*"([^"]+)"\s*$/gmu)) {
    catalog[match[1]] = match[2];
}
assert(Object.keys(catalog).length > 0, "Version catalog contains no pinned versions.");
for (const [name, version] of Object.entries(catalog)) {
    assert(
        !/[+*]|latest|snapshot/iu.test(version),
        `Version catalog entry ${name} is not exactly pinned: ${version}`,
    );
}

const dependencyHistory = readJson(
    "docs",
    "maintenance",
    "dependency-inspections.json",
);
assert(dependencyHistory.schemaVersion === 1, "Dependency history schema must remain revision 1.");
assert(
    Array.isArray(dependencyHistory.records) && dependencyHistory.records.length > 0,
    "Dependency history must contain at least one inspection.",
);
const inspectionIds = dependencyHistory.records.map((record) => record.id);
assert(
    new Set(inspectionIds).size === inspectionIds.length,
    "Dependency inspection ids must be unique.",
);
const currentInspection = dependencyHistory.records.at(-1);
assert(currentInspection.networkUsed === false, "The current offline inspection must record no network use.");
assert(currentInspection.result === "pass", "The current dependency inspection must pass.");
assert(
    currentInspection.mode === "central-platform-decision",
    "The current inspection must not claim an upstream-latest audit.",
);
assert(
    currentInspection.authority === platformAuthority,
    `Dependency inspection authority drifted from ${platformAuthority}.`,
);

const expectedSelected = {
    gradle: selectedGradle,
    agp: selectedAgp,
    kotlin: selectedKotlin,
    ksp: selectedKsp,
    r8: selectedR8,
    java: selectedJava,
    jvmTarget: selectedJvmTarget,
    compileSdk: Number(versionProperties.COMPILE_SDK_VERSION),
    targetSdk: Number(versionProperties.TARGET_SDK_VERSION),
    minSdk: Number(versionProperties.MIN_SDK_VERSION),
};
assert(
    exactKeys(currentInspection.selected, Object.keys(expectedSelected)),
    "The current dependency inspection has an unexpected selected-version shape.",
);
for (const [name, expected] of Object.entries(expectedSelected)) {
    assert(
        currentInspection.selected[name] === expected,
        `Dependency inspection ${name} drifted: expected ${expected}, received ${currentInspection.selected[name]}.`,
    );
}
assert(
    JSON.stringify(currentInspection.catalog) === JSON.stringify(catalog),
    "Dependency inspection catalog must match gradle/libs.versions.toml exactly.",
);

for (const [name, version] of Object.entries({
    Gradle: selectedGradle,
    AGP: selectedAgp,
    Kotlin: selectedKotlin,
    KSP: selectedKsp,
    R8: selectedR8,
})) {
    assertIncludes(dependencyPolicy, `\`${version}\``, `${name} dependency policy`);
}
for (const marker of [
    "每年 1/4/7/10 月",
    "repository compatibility snapshot",
    "中央平台插件",
    "不声称这些 library 是上游最新版本",
    "help --offline --console=plain",
    "check --offline",
    "WebView 支持策略",
]) {
    assertIncludes(dependencyPolicy, marker, "Dependency policy");
}

for (const marker of [
    "API 24 / Android 7.0",
    "major 119",
    "Chrome 51",
    "Chrome 55",
    "Chrome 69",
    "119.0.6045.194",
    "130.0.6723.86",
    "145.0.7632.218",
    "dumpsys webviewupdate",
]) {
    assertIncludes(webViewPolicy, marker, "WebView support policy");
}
assertIncludes(readme, "Chrome/WebView `119`", "README support baseline");
assertIncludes(releaseNotes, "Chrome/WebView 119", "Release-note support baseline");
assertIncludes(readme, "docs/maintenance/README.md", "README maintenance entry");
assertIncludes(releaseNotes, "RELEASE_CHECKLIST.md", "Release-note checklist entry");
assertIncludes(maintenanceIndex, "RELEASE_CHECKLIST.md", "Maintenance index");
assertIncludes(maintenanceIndex, "WEBVIEW_SUPPORT.md", "Maintenance index");
assertIncludes(maintenanceIndex, "DEPENDENCY_HEALTH.md", "Maintenance index");
assertIncludes(roadmap, "- [x] **S10-2 内存观察持续化**", "Roadmap S10-2 status");
assertIncludes(roadmap, "- [x] **S10-4 依赖健康巡检**", "Roadmap S10-4 status");

process.stdout.write(
    `S10 maintenance verified: ${memoryHistory.records.length} memory records, ` +
        `${dependencyHistory.records.length} dependency inspection, ` +
        `Gradle ${selectedGradle}/AGP ${selectedAgp}/Kotlin ${selectedKotlin}/` +
        `KSP ${selectedKsp}/R8 ${selectedR8}, Android API 24+ and WebView 119+.\n`,
);
