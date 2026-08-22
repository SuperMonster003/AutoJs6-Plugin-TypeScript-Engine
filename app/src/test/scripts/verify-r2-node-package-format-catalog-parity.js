"use strict";

const fs = require("node:fs");
const path = require("node:path");
const policy = require("./r2-node-package-format-catalog-policy.js");

const [fixtureArgument] = process.argv.slice(2);
if (!fixtureArgument || process.argv.length !== 3) {
    throw new Error(
        "Usage: node verify-r2-node-package-format-catalog-parity.js <catalog-v1.json>",
    );
}

const fixture = JSON.parse(fs.readFileSync(path.resolve(fixtureArgument), "utf8"));
const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const exactKeys = (value, keys) =>
    value !== null &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    Object.keys(value).length === keys.length &&
    keys.every((key) => Object.prototype.hasOwnProperty.call(value, key));

assert(
    exactKeys(fixture, [
        "schema",
        "revision",
        "catalogAuthority",
        "noBoundary",
        "validCases",
        "invalidTypeCases",
        "invalidLookupCases",
    ]) &&
        fixture.schema ===
            "autojs6.typescript.node-package-format-catalog-parity" &&
        fixture.revision === 1 &&
        Array.isArray(fixture.validCases) &&
        Array.isArray(fixture.invalidTypeCases) &&
        Array.isArray(fixture.invalidLookupCases),
    "P2-3A shared catalog fixture shape is malformed.",
);
assert(
    exactKeys(fixture.catalogAuthority, [
        "schema",
        "schemaRevision",
        "catalogRevision",
        "canonicalDomain",
        "profileSchemaRevision",
        "profileRevision",
        "nodeModuleOutputPolicySchemaRevision",
        "nodeModuleOutputPolicyRevision",
        "nodeModuleOutputPolicyFingerprint",
        "packageMetadataSchemaRevision",
        "packageMetadataPolicyRevision",
        "packageMetadataPolicyFingerprint",
    ]) &&
        Object.entries(fixture.catalogAuthority).every(([key, value]) =>
            value === policy.authority[key],
        ),
    "P2-3A shared catalog authority drifted from the independent oracle.",
);
assert(
    exactKeys(fixture.noBoundary, [
        "typeState",
        "packageMetadataInventoryFingerprint",
        "boundaryPath",
    ]) &&
        fixture.noBoundary.typeState === "no-boundary" &&
        fixture.noBoundary.packageMetadataInventoryFingerprint === null &&
        fixture.noBoundary.boundaryPath === null,
    "P2-3A no-boundary representation is not exact null authority.",
);

const seenIds = new Set();
let lookupCatalog = null;
for (const testCase of fixture.validCases) {
    assert(
        exactKeys(testCase, [
            "id",
            "dependencyRoots",
            "files",
            "inventoryCanonicalInput",
            "packageMetadataInventoryFingerprint",
            "catalogCanonicalInput",
            "catalogFingerprint",
            "lookups",
        ]) &&
            typeof testCase.id === "string" &&
            !seenIds.has(testCase.id) &&
            Array.isArray(testCase.dependencyRoots) &&
            Array.isArray(testCase.files) &&
            Array.isArray(testCase.lookups),
        "P2-3A valid catalog case shape or identity is malformed.",
    );
    seenIds.add(testCase.id);
    for (const file of testCase.files) {
        assert(
            exactKeys(file, [
                "path",
                "json",
                "size",
                "sha256",
                "boundaryPath",
                "typeState",
            ]),
            `${testCase.id}: package metadata fixture shape drifted.`,
        );
    }
    const catalog = policy.deriveCatalog(testCase.dependencyRoots, testCase.files);
    if (lookupCatalog === null) lookupCatalog = catalog;
    for (const key of [
        "inventoryCanonicalInput",
        "packageMetadataInventoryFingerprint",
        "catalogCanonicalInput",
        "catalogFingerprint",
    ]) {
        assert(
            catalog[key] === testCase[key],
            `${testCase.id}: ${key} was ${catalog[key]}, expected ${testCase[key]}.`,
        );
    }
    assert(
        Object.isFrozen(catalog) &&
            Object.isFrozen(catalog.records) &&
            catalog.records.every(Object.isFrozen),
        `${testCase.id}: JavaScript catalog is mutable.`,
    );
    testCase.files.forEach((file, index) => {
        const record = catalog.records[index];
        assert(
            record.packageMetadataPath === file.path &&
                record.packageBoundaryPath === file.boundaryPath &&
                record.packageMetadataSize === file.size &&
                record.packageMetadataSha256 === file.sha256 &&
                record.typeState === file.typeState,
            `${testCase.id}: record ${index} drifted.`,
        );
    });
    for (const lookup of testCase.lookups) {
        assert(
            exactKeys(lookup, ["path", "boundaryPath", "typeState"]),
            `${testCase.id}: lookup fixture shape drifted.`,
        );
        const record = policy.nearestRecord(catalog, lookup.path);
        assert(
            record !== null &&
                record.packageBoundaryPath === lookup.boundaryPath &&
                record.typeState === lookup.typeState,
            `${testCase.id}: nearest boundary drifted for ${lookup.path}.`,
        );
    }
}

for (const testCase of fixture.invalidLookupCases) {
    assert(
        exactKeys(testCase, ["id", "path"]) &&
            typeof testCase.id === "string" &&
            typeof testCase.path === "string" &&
            !seenIds.has(testCase.id),
        "P2-3A invalid lookup case shape or identity is malformed.",
    );
    seenIds.add(testCase.id);
    let failure = null;
    try {
        policy.nearestRecord(lookupCatalog, testCase.path);
    } catch (error) {
        failure = error;
    }
    assert(failure !== null, `${testCase.id}: invalid lookup path was accepted.`);
}

for (const testCase of fixture.invalidTypeCases) {
    assert(
        exactKeys(testCase, ["id", "json"]) &&
            typeof testCase.id === "string" &&
            !seenIds.has(testCase.id),
        "P2-3A invalid type case shape or identity is malformed.",
    );
    seenIds.add(testCase.id);
    const bytes = Buffer.from(testCase.json, "utf8");
    let failure = null;
    try {
        policy.deriveCatalog([], [{
            path: "package.json",
            json: testCase.json,
            size: bytes.length,
            sha256: require("node:crypto").createHash("sha256").update(bytes).digest("hex"),
        }]);
    } catch (error) {
        failure = error;
    }
    assert(
        failure !== null &&
            failure.policyCode === policy.failureCodes.packageTypeInvalid,
        `${testCase.id}: invalid package type did not fail with exact policy code.`,
    );
}

assert(
    fixture.validCases.length === 4 &&
        fixture.invalidTypeCases.length === 8 &&
        fixture.invalidLookupCases.length === 5,
    "P2-3A shared catalog case counts drifted.",
);
process.stdout.write(
    "R2-M2-P2-3A Node package-format catalog parity: PASS " +
        `(valid=${fixture.validCases.length}, invalidType=${fixture.invalidTypeCases.length}, ` +
        `invalidLookup=${fixture.invalidLookupCases.length}, ` +
        "inventory/catalog fingerprints and longest-boundary authority=closed)\n",
);
