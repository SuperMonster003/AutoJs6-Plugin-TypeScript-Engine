"use strict";

const fs = require("node:fs");
const path = require("node:path");

const [
    repositoryArgument,
    compilerManifestArgument,
    declarationManifestArgument,
] = process.argv.slice(2);
if (
    !repositoryArgument ||
    !compilerManifestArgument ||
    !declarationManifestArgument
) {
    throw new Error(
        "Usage: node verify-s10-typescript-upgrade.js " +
            "<repository-root> <compiler-manifest> <declaration-manifest>",
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
    assert(source.includes(marker), owner + " must contain " + JSON.stringify(marker) + ".");
const assertLowerSha256 = (value, owner) =>
    assert(
        typeof value === "string" && /^[0-9a-f]{64}$/u.test(value),
        owner + " must be a lowercase SHA-256.",
    );
const parseProperties = (source) => {
    const result = Object.create(null);
    for (const rawLine of source.split(/\r?\n/u)) {
        const line = rawLine.trim();
        if (!line || line.startsWith("#") || !line.includes("=")) continue;
        const separator = line.indexOf("=");
        result[line.slice(0, separator).trim()] = line
            .slice(separator + 1)
            .trim();
    }
    return result;
};
const sameJson = (left, right, owner) =>
    assert(
        JSON.stringify(left) === JSON.stringify(right),
        owner + " does not match source truth.",
    );

const versionProperties = parseProperties(readUtf8("version.properties"));
const currentVersion = versionProperties.TYPESCRIPT_VERSION;
assert(
    /^6\.\d+\.\d+$/u.test(currentVersion),
    "Production TypeScript must remain on the stable JavaScript-based 6.x line.",
);

const compilerLock = readJson("compiler-distribution.lock.json");
const declarationLockText = readUtf8("r2-declarations.lock.json");
const declarationLock = JSON.parse(declarationLockText);
const baselineCompilerLock = readJson(
    "docs",
    "maintenance",
    "typescript-upgrades",
    "rehearsal-6.0.2.compiler.lock.json",
);
const baselineDeclarationLockText = readUtf8(
    "docs",
    "maintenance",
    "typescript-upgrades",
    "rehearsal-6.0.2.declarations.lock.json",
);
const baselineDeclarationLock = JSON.parse(baselineDeclarationLockText);
const upgradeAuditDirectory = resolve(
    "docs",
    "maintenance",
    "typescript-upgrades",
);
const auditFiles = fs
    .readdirSync(upgradeAuditDirectory)
    .filter((name) => /^\d{4}-\d{2}-\d{2}\.json$/u.test(name))
    .sort();
assert(auditFiles.length > 0, "TypeScript upgrade history has no dated audit.");
const currentAuditFile = auditFiles.at(-1);
const audit = JSON.parse(
    fs.readFileSync(path.join(upgradeAuditDirectory, currentAuditFile), "utf8"),
);
const compilerManifest = JSON.parse(
    fs.readFileSync(path.resolve(compilerManifestArgument), "utf8"),
);
const declarationManifest = JSON.parse(
    fs.readFileSync(path.resolve(declarationManifestArgument), "utf8"),
);

assert(
    declarationLockText === JSON.stringify(declarationLock) + "\n",
    "Production declaration lock must remain canonical one-line JSON.",
);
assert(
    baselineDeclarationLockText === JSON.stringify(baselineDeclarationLock) + "\n",
    "Rehearsal declaration lock must remain canonical one-line JSON.",
);

assert(compilerLock.schemaVersion === 2, "Compiler lock schema must remain revision 2.");
assert(compilerLock.package === "typescript", "Compiler lock package must be TypeScript.");
assert(
    compilerLock.version === currentVersion,
    "version.properties and compiler lock TypeScript versions drifted.",
);
assert(
    compilerLock.source ===
        "https://registry.npmjs.org/typescript/-/typescript-" +
            currentVersion +
            ".tgz",
    "Compiler lock must use the exact official npm tarball URL.",
);
assert(
    declarationLock.common.version === currentVersion &&
        declarationLock.common.package === "typescript",
    "Declaration common lock must follow the production compiler version.",
);

assert(audit.schemaVersion === 1, "TypeScript upgrade audit schema must be revision 1.");
assert(
    currentAuditFile === audit.auditedAt + ".json",
    "Latest TypeScript upgrade audit filename and auditedAt date drifted.",
);
sameJson(
    audit.officialSources,
    {
        registryMetadata: "https://registry.npmjs.org/typescript",
        versions: "https://www.npmjs.com/package/typescript?activeTab=versions",
        releases: "https://github.com/microsoft/typescript/releases",
    },
    "Official TypeScript source set",
);
assert(audit.scope.acceptedLine === "stable-6.x-javascript", "Audit scope drifted.");
assert(
    Array.isArray(audit.scope.stableVersions) &&
        audit.scope.stableVersions.length >= 2 &&
        audit.scope.stableVersions.every((version) => /^6\.\d+\.\d+$/u.test(version)),
    "Audit must record the stable TypeScript 6.x versions observed.",
);
assert(
    audit.scope.stableVersions.at(-1) === audit.scope.latestStable6 &&
        audit.scope.latestStable6 === currentVersion &&
        audit.scope.currentVersion === currentVersion &&
        audit.scope.status === "already-current",
    "The checked audit must prove that production is the latest stable 6.x.",
);
assert(
    Number(audit.scope.overallLatest.split(".")[0]) > 6 &&
        audit.scope.typescript7Decision ===
            "out-of-scope-separate-compiler-host-architecture-review",
    "The TypeScript 7.x architecture boundary is missing.",
);

sameJson(
    audit.production.digests,
    {
        sha1: compilerLock.sha1,
        sha256: compilerLock.sha256,
        integrity: compilerLock.integrity,
    },
    "Production tarball digest audit",
);
assert(audit.production.source === compilerLock.source, "Production source audit drifted.");
assert(
    Number.isInteger(audit.production.tarballBytes) &&
        audit.production.tarballBytes > 0,
    "Production tarball size is missing.",
);

assert(
    compilerManifest.package.name === "typescript" &&
        compilerManifest.package.version === currentVersion,
    "Generated compiler manifest package identity drifted.",
);
sameJson(
    compilerManifest.tarball,
    {
        integrity: compilerLock.integrity,
        sha1: compilerLock.sha1,
        sha256: compilerLock.sha256,
        source: compilerLock.source,
    },
    "Generated compiler tarball identity",
);
assert(
    audit.production.distributionFingerprint === compilerManifest.fingerprint,
    "Audited production distribution fingerprint drifted.",
);
assert(
    audit.production.compilerFingerprint ===
        compilerManifest.components.compiler.fingerprint,
    "Audited compiler component fingerprint drifted.",
);
sameJson(
    audit.production.standardLibraries,
    {
        fingerprint: compilerManifest.components.standardLibraries.fingerprint,
        fileCount: compilerManifest.components.standardLibraries.fileCount,
        totalBytes: compilerManifest.components.standardLibraries.totalBytes,
    },
    "Audited standard-library identity",
);
sameJson(
    declarationLock.common,
    {
        declarationFileCount:
            compilerManifest.components.standardLibraries.fileCount,
        declarationTotalBytes:
            compilerManifest.components.standardLibraries.totalBytes,
        distributionFingerprint: compilerManifest.fingerprint,
        package: "typescript",
        standardLibrariesFingerprint:
            compilerManifest.components.standardLibraries.fingerprint,
        version: currentVersion,
    },
    "Declaration common lock",
);

assert(
    declarationManifest.schemaVersion === 2 &&
        declarationManifest.sources.common.version === currentVersion &&
        declarationManifest.sources.common.distributionAssetRoot ===
            "typescript/" + currentVersion &&
        declarationManifest.sources.common.distributionFingerprint ===
            compilerManifest.fingerprint,
    "Generated declarations are not bound to the verified compiler distribution.",
);
assert(
    audit.production.declarations.distributionFingerprint ===
        declarationManifest.fingerprint,
    "Audited declaration distribution fingerprint drifted.",
);
for (const profile of ["rhino", "node"]) {
    assert(
        audit.production.declarations.profiles[profile] ===
            declarationManifest.profiles[profile].fingerprint,
        "Audited " + profile + " profile fingerprint drifted.",
    );
}

assert(
    baselineCompilerLock.version === "6.0.2" &&
        baselineCompilerLock.package === "typescript" &&
        baselineCompilerLock.bundled === false,
    "The isolated rehearsal compiler lock must remain the 6.0.2 baseline.",
);
assert(
    baselineCompilerLock.source ===
        "https://registry.npmjs.org/typescript/-/typescript-6.0.2.tgz",
    "The rehearsal baseline must use the exact official npm tarball URL.",
);
for (const digest of ["sha1", "sha256", "integrity"]) {
    assert(
        baselineCompilerLock[digest] ===
            audit.rehearsal.baselineTarball.digests[digest],
        "Audited baseline " + digest + " drifted.",
    );
    assert(
        baselineCompilerLock[digest] !== compilerLock[digest],
        "Rehearsal " + digest + " did not roll.",
    );
}
assert(
    baselineDeclarationLock.common.version ===
        audit.rehearsal.baselineVersion &&
        baselineDeclarationLock.common.distributionFingerprint ===
            audit.rehearsal.compilerDistribution.baselineFingerprint,
    "Baseline declaration lock drifted from the rehearsal audit.",
);
assert(
    audit.rehearsal.currentVersion === currentVersion &&
        audit.rehearsal.tarballDigestsRolled === true &&
        audit.rehearsal.compilerDistribution.currentFingerprint ===
            compilerManifest.fingerprint &&
        audit.rehearsal.compilerDistribution.changed === true,
    "Compiler distribution rolling evidence is incomplete.",
);
assert(
    audit.rehearsal.standardLibraries.currentFingerprint ===
        compilerManifest.components.standardLibraries.fingerprint,
    "Current rehearsal standard-library fingerprint drifted.",
);

const declarationsChanged = audit.rehearsal.standardLibraries.changed;
for (const profile of ["rhino", "node"]) {
    const result = audit.rehearsal.declarationProfiles[profile];
    assert(
        result.current === declarationManifest.profiles[profile].fingerprint,
        "Current rehearsal " + profile + " fingerprint drifted.",
    );
    assert(
        result.changed === (result.baseline !== result.current),
        "Rehearsal " + profile + " changed flag is inconsistent.",
    );
    if (declarationsChanged) {
        assert(result.changed, profile + " profile must roll with changed declarations.");
    } else {
        assert(!result.changed, profile + " profile must preserve unchanged content identity.");
    }
}
assert(
    audit.rehearsal.cacheAuthorityChanged === true &&
        audit.rehearsal.cacheAuthorityReason ===
            "compilerVersion+compilerDistributionFingerprint",
    "Cache invalidation authority is not recorded.",
);
assert(
    audit.rehearsal.declarationDistribution.currentFingerprint ===
        declarationManifest.fingerprint &&
        audit.rehearsal.declarationDistribution.changed === true,
    "Overall declaration binding identity did not roll.",
);

for (const verification of [
    "rehearsal",
    "buildLogicTest",
    "hostCacheUnitTest",
    "physicalDeviceSmoke",
    "deviceSmoke",
]) {
    assert(
        audit.verification[verification].result === "pass",
        verification + " evidence must pass.",
    );
}
assert(
    audit.verification.preflight.result === "already-current" &&
        audit.verification.preflight.sourceMutation === "none",
    "Official preflight evidence is incomplete.",
);
assert(
    audit.verification.hostCacheUnitTest.assertion ===
        "compiler-version-or-distribution-fingerprint-change-invalidates-key",
    "Host cache-key assertion drifted.",
);
const physicalDeviceSmoke = audit.verification.physicalDeviceSmoke;
const referenceDeviceSmoke = audit.verification.deviceSmoke;
for (const [label, smoke] of [
    ["physical", physicalDeviceSmoke],
    ["reference", referenceDeviceSmoke],
]) {
    assert(
        smoke.api >= 24 &&
            Number(smoke.webViewVersion.split(".")[0]) >= 119 &&
            smoke.compilerVersion === currentVersion &&
            smoke.releaseVersion === versionProperties.VERSION_NAME &&
            smoke.instrumentationCode === -1 &&
            smoke.helperRemoved === true,
        label + " device smoke evidence is incomplete or unsupported.",
    );
    assertLowerSha256(smoke.releaseApkSha256, label + " release APK audit");
}
assert(
    physicalDeviceSmoke.kind === "physical" &&
        referenceDeviceSmoke.kind === "emulator" &&
        physicalDeviceSmoke.releaseApkSha256 ===
            referenceDeviceSmoke.releaseApkSha256,
    "Physical/reference device evidence must use the same signed release APK.",
);
assert(
    audit.decision.productionMutation === "none" &&
        audit.decision.remoteMutation === "none",
    "The already-current audit must not claim production or remote mutations.",
);

const manual = readUtf8("docs", "maintenance", "UPGRADE.md");
for (const marker of [
    "node tools/prepare-typescript-upgrade.mjs --major 6",
    ":app:downloadTypeScriptUpgradeRehearsalTarball",
    ":app:verifyTypeScriptUpgradeRehearsal",
    "TypeScript 7.x",
    "sourceMutation",
    "compilerVersion",
    "compilerDistributionFingerprint",
    "TypeScriptCompilationCacheKeyTest",
    "INSTRUMENTATION_CODE: -1",
    "GitHub Release",
    "2026-08-25.json",
]) {
    assertIncludes(manual, marker, "TypeScript upgrade manual");
}

const preflightSource = readUtf8("tools", "prepare-typescript-upgrade.mjs");
for (const marker of [
    "https://registry.npmjs.org/typescript",
    "result.major !== 6",
    "redirect: \"error\"",
    "sha256",
    "sourceMutation: \"none\"",
    "out-of-scope-separate-architecture-review",
]) {
    assertIncludes(preflightSource, marker, "TypeScript preflight");
}

const appBuild = readUtf8("app", "build.gradle.kts");
for (const marker of [
    "downloadTypeScriptUpgradeRehearsalTarball",
    "verifyTypeScriptUpgradeRehearsalDistribution",
    "verifyTypeScriptUpgradeRehearsalDeclarations",
    "verifyTypeScriptUpgradeRehearsal",
    "if (declarationsChanged)",
    "compilerVersion+compilerDistributionFingerprint",
    "\"gate\" to \"identity-only\"",
]) {
    assertIncludes(appBuild, marker, "S10-3 Gradle graph");
}

const declarationGenerator = readUtf8(
    "build-logic",
    "convention",
    "src",
    "main",
    "kotlin",
    "org",
    "autojs",
    "build",
    "R2DeclarationDistribution.kt",
);
for (const marker of [
    "STABLE_TYPESCRIPT_6_VERSION",
    "typeScriptAssetRoot(common.version)",
    "typeScriptAssetRoot(lock.common.version)",
    "stable TypeScript 6.x version",
    "positive library inventory",
]) {
    assertIncludes(declarationGenerator, marker, "R2 declaration generator");
}
for (const forbidden of [
    "private const val COMMON_TYPESCRIPT_VERSION",
    "private const val COMMON_DECLARATION_FILE_COUNT",
    "private const val COMMON_DECLARATION_TOTAL_BYTES",
    "3855b04b968db8a9529b60c96dd8b8ec94fa1ee040cac10a5ab569aa6e33f658",
    "6ee5c0bce41694b8ca86430b82abfe4fbb60bf96321fba43e99d9810a44a1c1f",
]) {
    assert(
        !declarationGenerator.includes(forbidden),
        "Production declaration generation still contains " + forbidden + ".",
    );
}

const roadmap = readUtf8("ROADMAP.md");
const maintenanceIndex = readUtf8("docs", "maintenance", "README.md");
const readme = readUtf8("README.md");
assertIncludes(roadmap, "- [x] **S10-3 TypeScript 版本升级演练**", "Roadmap");
assertIncludes(maintenanceIndex, "UPGRADE.md", "Maintenance index");
assertIncludes(readme, "UPGRADE.md", "README maintenance entry");

process.stdout.write(
    "S10-3 TypeScript upgrade verified: official stable 6.x " +
        currentVersion +
        " is already current; isolated 6.0.2 -> " +
        currentVersion +
        " rolled compiler identity, preserved unchanged declaration profiles, " +
        "invalidated host cache authority, and passed physical API " +
        physicalDeviceSmoke.api +
        " plus reference API " +
        referenceDeviceSmoke.api +
        " release smoke.\n",
);
