"use strict";

const cryptoModule = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const [workerArgument, compilerArgument, expectedVersion] = process.argv.slice(2);
if (!workerArgument || !compilerArgument || !expectedVersion) {
    throw new Error(
        "Usage: node verify-r2-node-resolution-worker.js " +
            "<compiler-worker.js> <typescript.js> 6.0.3",
    );
}
if (expectedVersion !== "6.0.3") {
    throw new Error("The Worker resolver oracle requires TypeScript 6.0.3.");
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const workerSource = fs.readFileSync(workerPath, "utf8");
const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const sha256 = (text) =>
    cryptoModule.createHash("sha256").update(text).digest("hex");
const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;

if (!global.crypto) {
    Object.defineProperty(global, "crypto", {
        configurable: true,
        value: cryptoModule.webcrypto,
    });
}
const realTypeScript = require(compilerPath);
let compilerHostIoAccessCount = 0;
let programConstructionCount = 0;
const typeScriptFacade = Object.create(realTypeScript);
Object.defineProperty(typeScriptFacade, "sys", {
    value: new Proxy(realTypeScript.sys, {
        get() {
            compilerHostIoAccessCount += 1;
            throw new Error("Snapshot resolver accessed TypeScript host I/O.");
        },
    }),
});
Object.defineProperty(typeScriptFacade, "createIncrementalProgram", {
    value: () => {
        programConstructionCount += 1;
        throw new Error("Snapshot resolver constructed a TypeScript Program.");
    },
});
global.ts = typeScriptFacade;
global.importScripts = (specifier) => {
    assert(specifier === "typescript.js", "Worker imported an unexpected script.");
};
const publishedMessages = [];
global.self = {
    postMessage: (message) => publishedMessages.push(message),
};
vm.runInThisContext(workerSource, { filename: workerPath });
assert(
    publishedMessages.length === 1 &&
        publishedMessages[0].kind === "ready" &&
        publishedMessages[0].tsVersion === expectedVersion,
    "Worker did not publish the pinned TypeScript authority.",
);
publishedMessages.length = 0;

const workerBinding = (expression) =>
    vm.runInThisContext(expression, {
        filename: `${workerPath}#node-resolution-worker`,
    });
const resolveSnapshot = workerBinding("resolveControlledNodeSnapshot");
const failureCodes = workerBinding("controlledNodeResolutionFailureCodes");
const modes = workerBinding("controlledNodeResolutionModes");
const targetKinds = workerBinding("controlledNodeResolutionTargetKinds");

const kotlinC6AFailureCatalog = Object.freeze([
    "NODE_RESOLUTION_INVALID_CONTAINING_PATH",
    "NODE_RESOLUTION_INVALID_SPECIFIER",
    "NODE_RESOLUTION_INVALID_CONDITION",
    "NODE_RESOLUTION_AMBIGUOUS_CASE",
    "NODE_RESOLUTION_PACKAGE_NOT_ALLOWED",
    "NODE_RESOLUTION_PACKAGE_METADATA_MISSING",
    "NODE_RESOLUTION_PACKAGE_METADATA_INVALID",
    "NODE_RESOLUTION_IMPORTS_NOT_DEFINED",
    "NODE_RESOLUTION_IMPORTS_NO_MATCH",
    "NODE_RESOLUTION_EXPORTS_NOT_DEFINED",
    "NODE_RESOLUTION_EXPORTS_NO_MATCH",
    "NODE_RESOLUTION_CONDITION_NO_MATCH",
    "NODE_RESOLUTION_INVALID_TARGET",
    "NODE_RESOLUTION_TARGET_BLOCKED",
    "NODE_RESOLUTION_TARGET_ESCAPE",
    "NODE_RESOLUTION_TARGET_NOT_IN_SNAPSHOT",
    "NODE_RESOLUTION_UNSUPPORTED_SOURCE_KIND",
    "NODE_RESOLUTION_CYCLE",
]);
assert(
    JSON.stringify(Object.values(failureCodes)) ===
        JSON.stringify(kotlinC6AFailureCatalog),
    "Worker failure catalog drifted from the Kotlin C6-A catalog.",
);

const deepFreeze = (value) => {
    if (value !== null && typeof value === "object" && !Object.isFrozen(value)) {
        Reflect.ownKeys(value).forEach((key) => deepFreeze(value[key]));
        Object.freeze(value);
    }
    return value;
};
const metadataRecord = (archivePath, root) => {
    const text = JSON.stringify(root);
    return deepFreeze({
        boundaryPath:
            archivePath === "package.json"
                ? "/"
                : `/${archivePath.slice(0, -"/package.json".length)}`,
        path: archivePath,
        root: deepFreeze(root),
        sha256: sha256(text),
        size: Buffer.byteLength(text, "utf8"),
    });
};
const catalog = (dependencyRoots, ...records) => {
    const sortedRoots = dependencyRoots.slice().sort(compareCodeUnits);
    return deepFreeze({
        dependencyRoots: sortedRoots,
        inventoryFingerprint: sha256(
            JSON.stringify({ records: records.map((record) => record.path), sortedRoots }),
        ),
        records,
    });
};
const target = (targetPath, kind) => deepFreeze({ kind, path: targetPath });
const source = (targetPath) => target(targetPath, targetKinds.projectSource);
const declaration = (targetPath) => target(targetPath, targetKinds.declaration);
const targets = (...values) => deepFreeze(values);
const request = (
    specifier,
    {
        conditions = [],
        containingPath = "/sources/main.ts",
        mode = modes.import,
    } = {},
) => deepFreeze({
    conditions: conditions.slice(),
    containingPath,
    mode,
    specifier,
});
const resolve = (metadata, inventory, specifier, options) =>
    resolveSnapshot(request(specifier, options), metadata, inventory);

let successCount = 0;
let failureCount = 0;
const expectResolved = (result, expectedPath, label) => {
    assert(
        result.kind === "resolved" &&
            result.target.path === expectedPath &&
            Object.isFrozen(result) &&
            Object.isFrozen(result.target) &&
            Object.isFrozen(result.matchedConditions),
        `${label} did not resolve immutably to ${expectedPath}: ${JSON.stringify(result)}.`,
    );
    successCount += 1;
    return result;
};
const expectFailure = (result, expectedCode, label) => {
    assert(
        result.kind === "failure" &&
            result.code === expectedCode &&
            Object.isFrozen(result) &&
            Object.isFrozen(result.matchedConditions),
        `${label} did not fail immutably with ${expectedCode}: ${JSON.stringify(result)}.`,
    );
    failureCount += 1;
    return result;
};

const conditionMetadata = catalog(
    ["packages/mode", "packages/node", "packages/pkg"],
    metadataRecord("package.json", {}),
    metadataRecord("packages/mode/package.json", {
        exports: { import: "./import.js", require: "./require.js", default: "./default.js" },
    }),
    metadataRecord("packages/node/package.json", {
        exports: { node: "./node.js", default: "./default.js" },
    }),
    metadataRecord("packages/pkg/package.json", {
        exports: {
            custom: "./custom.js",
            types: "./index.js",
            import: "./import.js",
            require: "./require.js",
            default: "./default.js",
        },
    }),
);
const conditionTargets = targets(
    source("/packages/mode/import.ts"),
    source("/packages/mode/require.ts"),
    source("/packages/mode/default.ts"),
    source("/packages/node/node.ts"),
    source("/packages/node/default.ts"),
    source("/packages/pkg/custom.ts"),
    declaration("/packages/pkg/index.d.ts"),
    source("/packages/pkg/import.ts"),
    source("/packages/pkg/require.ts"),
    source("/packages/pkg/default.ts"),
);
let result = expectResolved(
    resolve(conditionMetadata, conditionTargets, "pkg", {
        conditions: ["custom"],
    }),
    "/packages/pkg/custom.ts",
    "custom condition",
);
assert(
    JSON.stringify(result.matchedConditions) === JSON.stringify(["custom"]),
    "Custom condition ordering drifted.",
);
result = expectResolved(
    resolve(conditionMetadata, conditionTargets, "pkg"),
    "/packages/pkg/index.d.ts",
    "implicit types condition and .js substitution",
);
assert(result.matchedConditions[0] === "types", "Implicit types condition was absent.");
expectResolved(
    resolve(conditionMetadata, conditionTargets, "mode"),
    "/packages/mode/import.ts",
    "implicit import mode",
);
expectResolved(
    resolve(conditionMetadata, conditionTargets, "mode", { mode: modes.require }),
    "/packages/mode/require.ts",
    "implicit require mode",
);
expectResolved(
    resolve(conditionMetadata, conditionTargets, "node"),
    "/packages/node/node.ts",
    "implicit node condition",
);

const patternMetadata = catalog(
    ["packages/@scope/pkg"],
    metadataRecord("package.json", {}),
    metadataRecord("packages/@scope/pkg/package.json", {
        exports: {
            "./feature/exact": "./exact.d.ts",
            "./feature/*": "./types/*.d.ts",
            "./feature/special/*": "./special/*.d.ts",
        },
    }),
);
const patternTargets = targets(
    declaration("/packages/@scope/pkg/exact.d.ts"),
    declaration("/packages/@scope/pkg/types/other.d.ts"),
    declaration("/packages/@scope/pkg/special/value.d.ts"),
);
expectResolved(
    resolve(patternMetadata, patternTargets, "@scope/pkg/feature/exact"),
    "/packages/@scope/pkg/exact.d.ts",
    "exact exports",
);
result = expectResolved(
    resolve(patternMetadata, patternTargets, "@scope/pkg/feature/special/value"),
    "/packages/@scope/pkg/special/value.d.ts",
    "most-specific exports pattern",
);
assert(result.matchedKey === "./feature/special/*", "Most-specific key was lost.");

const importsMetadata = catalog(
    ["packages/pkg"],
    metadataRecord("package.json", {
        imports: {
            "#app/*": "./sources/*.ts",
            "#dep": "pkg",
            "#one": "#two",
            "#two": "#one",
        },
    }),
    metadataRecord("packages/pkg/package.json", {
        exports: "./index.d.ts",
        imports: { "#self": "./root.d.ts" },
    }),
    metadataRecord("packages/pkg/nested/package.json", {
        imports: { "#self": "./nested.d.ts" },
    }),
);
const importsTargets = targets(
    source("/sources/util.ts"),
    declaration("/packages/pkg/index.d.ts"),
    declaration("/packages/pkg/root.d.ts"),
    declaration("/packages/pkg/nested/nested.d.ts"),
);
expectResolved(
    resolve(importsMetadata, importsTargets, "#app/util"),
    "/sources/util.ts",
    "project imports pattern",
);
result = expectResolved(
    resolve(importsMetadata, importsTargets, "#self", {
        containingPath: "/packages/pkg/nested/file.d.ts",
    }),
    "/packages/pkg/nested/nested.d.ts",
    "nearest nested package boundary",
);
assert(
    result.packageMetadataPath === "packages/pkg/nested/package.json",
    "Nested package metadata identity was lost.",
);
expectResolved(
    resolve(importsMetadata, importsTargets, "#dep"),
    "/packages/pkg/index.d.ts",
    "imports package delegation",
);
expectFailure(
    resolve(importsMetadata, importsTargets, "#one"),
    failureCodes.cycle,
    "imports cycle",
);

const relativeMetadata = catalog([], metadataRecord("package.json", {}));
const relativeTargets = targets(
    source("/sources/nested/main.ts"),
    source("/sources/util.ts"),
);
expectResolved(
    resolve(relativeMetadata, relativeTargets, "../util.ts", {
        containingPath: "/sources/nested/main.ts",
    }),
    "/sources/util.ts",
    "exact relative target",
);
expectResolved(
    resolve(relativeMetadata, relativeTargets, "../util.js", {
        containingPath: "/sources/nested/main.ts",
    }),
    "/sources/util.ts",
    "relative .js to .ts substitution",
);

const arrayMetadata = catalog(
    ["packages/array", "packages/blocked"],
    metadataRecord("package.json", {}),
    metadataRecord("packages/array/package.json", {
        exports: ["./missing.js", "./fallback.js"],
    }),
    metadataRecord("packages/blocked/package.json", {
        exports: [null, "./fallback.js"],
    }),
);
const arrayTargets = targets(declaration("/packages/array/fallback.d.ts"));
expectResolved(
    resolve(arrayMetadata, arrayTargets, "array"),
    "/packages/array/fallback.d.ts",
    "array fallback",
);
expectFailure(
    resolve(arrayMetadata, arrayTargets, "blocked"),
    failureCodes.targetBlocked,
    "array null stop",
);

expectFailure(
    resolve(relativeMetadata, relativeTargets, "#app", {
        containingPath: "sources/main.ts",
    }),
    failureCodes.invalidContainingPath,
    "invalid containing path",
);
expectFailure(
    resolve(relativeMetadata, relativeTargets, "bad\\specifier"),
    failureCodes.invalidSpecifier,
    "invalid specifier",
);
expectFailure(
    resolve(relativeMetadata, relativeTargets, "#app", {
        conditions: ["custom", "custom"],
    }),
    failureCodes.invalidCondition,
    "duplicate custom condition",
);
expectFailure(
    resolve(
        relativeMetadata,
        targets(source("/sources/A.ts"), source("/sources/a.ts")),
        "./a.ts",
    ),
    failureCodes.ambiguousCase,
    "target case-fold collision",
);
expectFailure(
    resolve(relativeMetadata, relativeTargets, "other"),
    failureCodes.packageNotAllowed,
    "unallowlisted package",
);
expectFailure(
    resolve(catalog([], /* intentionally no records */), targets(), "#app"),
    failureCodes.packageMetadataMissing,
    "missing nearest metadata",
);
const numericConditionMetadata = catalog(
    ["packages/numeric"],
    metadataRecord("package.json", {}),
    metadataRecord("packages/numeric/package.json", {
        exports: {
            "4294967294": "./index.d.ts",
            default: "./fallback.d.ts",
        },
    }),
);
expectFailure(
    resolve(
        numericConditionMetadata,
        targets(declaration("/packages/numeric/fallback.d.ts")),
        "numeric",
    ),
    failureCodes.packageMetadataInvalid,
    "maximum canonical integer-index condition",
);
const nonIndexConditionMetadata = catalog(
    ["packages/non-index"],
    metadataRecord("package.json", {}),
    metadataRecord("packages/non-index/package.json", {
        exports: {
            "4294967295": "./never.d.ts",
            default: "./fallback.d.ts",
        },
    }),
);
expectResolved(
    resolve(
        nonIndexConditionMetadata,
        targets(declaration("/packages/non-index/fallback.d.ts")),
        "non-index",
    ),
    "/packages/non-index/fallback.d.ts",
    "4294967295 non-index property-order fallback",
);
expectFailure(
    resolve(relativeMetadata, relativeTargets, "#missing"),
    failureCodes.importsNotDefined,
    "imports absent",
);
const noMatchImports = catalog(
    [],
    metadataRecord("package.json", { imports: { "#known": "./sources/util.ts" } }),
);
expectFailure(
    resolve(noMatchImports, relativeTargets, "#missing"),
    failureCodes.importsNoMatch,
    "imports no match",
);
const exportsFailures = catalog(
    ["packages/empty", "packages/nomatch", "packages/condition", "packages/escape"],
    metadataRecord("package.json", {}),
    metadataRecord("packages/empty/package.json", {}),
    metadataRecord("packages/nomatch/package.json", {
        exports: { "./known": "./known.d.ts" },
    }),
    metadataRecord("packages/condition/package.json", {
        exports: { browser: "./browser.d.ts" },
    }),
    metadataRecord("packages/escape/package.json", { exports: "./../escape.d.ts" }),
);
expectFailure(
    resolve(exportsFailures, targets(), "empty"),
    failureCodes.exportsNotDefined,
    "exports absent",
);
expectFailure(
    resolve(exportsFailures, targets(), "nomatch/unknown"),
    failureCodes.exportsNoMatch,
    "exports no match",
);
expectFailure(
    resolve(exportsFailures, targets(), "condition"),
    failureCodes.conditionNoMatch,
    "condition no match",
);
expectFailure(
    resolve(exportsFailures, targets(), "escape"),
    failureCodes.targetEscape,
    "package target escape",
);
const invalidTargetMetadata = catalog(
    ["packages/invalid", "packages/missing", "packages/null"],
    metadataRecord("package.json", {}),
    metadataRecord("packages/invalid/package.json", { exports: "other" }),
    metadataRecord("packages/missing/package.json", { exports: "./missing.d.ts" }),
    metadataRecord("packages/null/package.json", { exports: null }),
);
expectFailure(
    resolve(invalidTargetMetadata, targets(), "invalid"),
    failureCodes.invalidTarget,
    "invalid exports target",
);
expectFailure(
    resolve(invalidTargetMetadata, targets(), "null"),
    failureCodes.targetBlocked,
    "blocked exports target",
);
expectFailure(
    resolve(invalidTargetMetadata, targets(), "missing"),
    failureCodes.targetNotInSnapshot,
    "target outside snapshot",
);
expectFailure(
    resolve(
        relativeMetadata,
        targets(target("/packages/pkg/index.mts", targetKinds.projectSource)),
        "./missing.ts",
    ),
    failureCodes.unsupportedSourceKind,
    "unsupported source kind",
);

const kernelStart = workerSource.indexOf(
    "const controlledNodeResolutionFailureCodes = Object.freeze({",
);
const kernelEnd = workerSource.indexOf(
    "const parseControlledPackageMetadataCanonical = async (",
    kernelStart,
);
assert(kernelStart >= 0 && kernelEnd > kernelStart, "Worker kernel is not bounded.");
const kernelSource = workerSource.slice(kernelStart, kernelEnd);
for (const forbidden of [
    /\bts\.sys\b/,
    /\bfetch\s*\(/,
    /XMLHttpRequest/,
    /WebSocket/,
    /createIncrementalProgram/,
    /resolveModuleNameLiterals/,
]) {
    assert(!forbidden.test(kernelSource), `Worker kernel contains escape: ${forbidden}.`);
}
assert(
    (workerSource.match(/resolveControlledNodeSnapshot/g) || []).length >= 2,
    "Worker is missing the production resolver activation seam.",
);
assert(
    compilerHostIoAccessCount === 0 && programConstructionCount === 0,
    "Pure resolver touched compiler host I/O or Program construction.",
);
assert(
    successCount === 14 && failureCount === 19,
    "Worker resolver coverage counts drifted.",
);

process.stdout.write(
    "R2-M2-C6 Worker snapshot resolver verified: " +
        `${successCount} resolutions, ${failureCount} failures, ` +
        "18-code Kotlin catalog, host I/O=0, Program=0\n",
);
