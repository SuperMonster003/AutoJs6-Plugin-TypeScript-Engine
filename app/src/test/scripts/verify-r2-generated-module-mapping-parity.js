"use strict";

const fs = require("node:fs");
const path = require("node:path");
const policy = require("./r2-generated-module-mapping-policy.js");

const [fixtureArgument, expectedPolicyFingerprint] = process.argv.slice(2);
if (!fixtureArgument || !expectedPolicyFingerprint || process.argv.length !== 4) {
    throw new Error(
        "Usage: node verify-r2-generated-module-mapping-parity.js " +
            "<parity-v1.json> <module-output-policy-fingerprint>",
    );
}

const fixture = JSON.parse(
    fs.readFileSync(path.resolve(fixtureArgument), "utf8"),
);
const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const exactKeys = (value, keys) =>
    value !== null &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    JSON.stringify(Object.keys(value).sort()) === JSON.stringify(keys.slice().sort());

assert(
    exactKeys(fixture, [
        "schema",
        "revision",
        "moduleOutputPolicyFingerprint",
        "packageMetadataInventoryFingerprints",
        "validCases",
        "invalidCases",
    ]) &&
        fixture.schema ===
            "autojs6.typescript.generated-module-mapping-parity" &&
        fixture.revision === 1 &&
        fixture.moduleOutputPolicyFingerprint === expectedPolicyFingerprint &&
        fixture.moduleOutputPolicyFingerprint ===
            policy.authority.moduleOutputPolicyFingerprint &&
        exactKeys(fixture.packageMetadataInventoryFingerprints, [
            "typeAbsent",
            "commonjs",
            "module",
        ]) &&
        Object.values(fixture.packageMetadataInventoryFingerprints).every(
            (fingerprint) => /^[0-9a-f]{64}$/.test(fingerprint),
        ) &&
        Array.isArray(fixture.validCases) &&
        Array.isArray(fixture.invalidCases),
    "P2-2 shared parity fixture authority is malformed.",
);

const validById = new Map();
for (const testCase of fixture.validCases) {
    assert(
        exactKeys(testCase, ["id", "request", "expected"]) &&
            typeof testCase.id === "string" &&
            !validById.has(testCase.id) &&
            exactKeys(testCase.expected, [
                "projectNamespace",
                "originalSourceUri",
                "outputExtension",
                "generatedPath",
                "sourceMapPath",
                "generatedUri",
                "fingerprint",
            ]),
        "P2-2 valid case shape or identity drifted.",
    );
    const mapping = policy.map(testCase.request);
    const packageAuthority = testCase.request.packageFormatAuthority;
    if (packageAuthority !== null && packageAuthority.typeState !== "no-boundary") {
        const exampleKey = packageAuthority.typeState === "missing"
            ? "typeAbsent"
            : packageAuthority.typeState;
        assert(
            packageAuthority.packageMetadataInventoryFingerprint ===
                fixture.packageMetadataInventoryFingerprints[exampleKey],
            `${testCase.id}: package inventory fingerprint is not instance-bound.`,
        );
    }
    for (const [key, expected] of Object.entries(testCase.expected)) {
        assert(
            mapping[key] === expected,
            `${testCase.id}: ${key} was ${mapping[key]}, expected ${expected}.`,
        );
    }
    assert(
        mapping.sourcePath === testCase.request.sourcePath &&
            mapping.moduleFormat === testCase.request.impliedModuleFormat &&
            mapping.originalSourceUri.length <= policy.authority.maximumUriAsciiBytes &&
            mapping.generatedUri.length <= policy.authority.maximumUriAsciiBytes &&
            mapping.canonicalEvidence.split("|").slice(1).every((token) =>
                token.indexOf("=") > 0 && token.indexOf("=") === token.lastIndexOf("="),
            ),
        `${testCase.id}: canonical mapping/evidence shape drifted.`,
    );
    validById.set(testCase.id, { mapping, request: testCase.request });
}

for (const testCase of fixture.invalidCases) {
    let error = null;
    try {
        if (testCase.operation === "map") {
            policy.map(testCase.request);
        } else if (testCase.operation === "inventory") {
            policy.mapInventory(testCase.requests);
        } else if (testCase.operation === "claim") {
            const valid = validById.get(testCase.validCaseId);
            assert(valid, `${testCase.id}: referenced valid case is missing.`);
            policy.requireExact(
                valid.request,
                { ...valid.mapping, [testCase.field]: testCase.replacement },
            );
        } else {
            throw new Error(`${testCase.id}: unknown invalid operation.`);
        }
    } catch (caught) {
        error = caught;
    }
    assert(
        error instanceof Error && error.policyCode === testCase.expectedCode,
        `${testCase.id}: failure was ${error?.policyCode || error?.message || "none"}, ` +
            `expected ${testCase.expectedCode}.`,
    );
}

const singleProjectRequests = fixture.validCases
    .map((testCase) => testCase.request)
    .filter((request) => request.projectId === "demo");
const inventory = policy.mapInventory(singleProjectRequests);
assert(
    inventory.length === singleProjectRequests.length &&
        Object.isFrozen(inventory) &&
        inventory.every(
            (mapping, index, all) =>
                index === 0 || all[index - 1].sourcePath < mapping.sourcePath,
        ),
    "P2-2 valid inventory is not deterministic and source-sorted.",
);

const canonicalClaim = validById.get("node-mts-esm");
for (const key of Object.keys(canonicalClaim.mapping)) {
    const drifted = {
        ...canonicalClaim.mapping,
        [key]: `${canonicalClaim.mapping[key]}#drift`,
    };
    let driftError;
    try {
        policy.requireExact(canonicalClaim.request, drifted);
    } catch (caught) {
        driftError = caught;
    }
    assert(
        driftError?.policyCode === policy.failureCodes.claimMismatch,
        `P2-2 exact claim field was not bound: ${key}.`,
    );
}
const reorderedClaim = Object.fromEntries(
    Object.entries(canonicalClaim.mapping).reverse(),
);
policy.requireExact(canonicalClaim.request, reorderedClaim);
const forgedClaim = Object.create({ toJSON: () => canonicalClaim.mapping });
Object.assign(forgedClaim, canonicalClaim.mapping);
let forgedClaimError;
try {
    policy.requireExact(canonicalClaim.request, forgedClaim);
} catch (caught) {
    forgedClaimError = caught;
}
assert(
    forgedClaimError?.policyCode === policy.failureCodes.claimMismatch,
    "P2-2 prototype/toJSON forged claim was not rejected.",
);

const maximumStem = "a".repeat(
    policy.authority.maximumRelativePathChars - "outputs/".length - ".js.map".length,
);
policy.map({
    ...validById.get("rhino-ts-commonjs").request,
    sourcePath: `/sources/${maximumStem}.ts`,
});
let outputBudgetError;
try {
    policy.map({
        ...validById.get("rhino-ts-commonjs").request,
        sourcePath: `/sources/a${maximumStem}.ts`,
    });
} catch (caught) {
    outputBudgetError = caught;
}
assert(
    outputBudgetError?.policyCode === policy.failureCodes.outputPathLimitExceeded,
    "P2-2 source-map archive path plus-one was not rejected.",
);

const rawDelimiter = validById.get(
    "raw-evidence-delimiter-is-single-token-encoded",
).mapping;
assert(
    rawDelimiter.canonicalEvidence.includes("sourcePath=%2Fsources%2Fraw%7Cpipe.mts") &&
        !rawDelimiter.canonicalEvidence.includes("sourcePath=/sources/raw|pipe.mts"),
    "P2-2 canonical evidence retained a raw delimiter-bearing path.",
);
const literalPercent = validById.get(
    "literal-percent-is-not-decoded-as-traversal",
).mapping;
assert(
    literalPercent.generatedUri.includes("/%252e%252e/") &&
        !literalPercent.generatedUri.includes("/../"),
    "P2-2 literal percent path was decoded or normalized as traversal.",
);

console.log(
    "R2-M2-P2-2 generated module mapping JS parity verified: " +
        `valid=${fixture.validCases.length}, invalid=${fixture.invalidCases.length}, ` +
        "matrix=5, strict UTF-8 namespace=1, percent encoding=1, " +
        "instance identity/path budgets/collision/traversal/claim drift=closed",
);
