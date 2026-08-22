"use strict";

const fs = require("fs");
const path = require("path");

const compilerPath = process.argv[2];
const fixturePath = process.argv[3];
const expectedVersion = process.argv[4];
if (!compilerPath || !fixturePath || !expectedVersion || process.argv.length !== 5) {
    throw new Error(
        "Usage: node <script> <typescript.js> <parity-v1.json> 6.0.3",
    );
}

const ts = require(path.resolve(compilerPath));
const fixture = JSON.parse(fs.readFileSync(path.resolve(fixturePath), "utf8"));
const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const exactKeys = (value, expected) =>
    value !== null &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    JSON.stringify(Object.keys(value).sort()) ===
        JSON.stringify(expected.slice().sort());

assert(ts.version === expectedVersion, "TypeScript runtime version drifted.");
assert(
    fixture.schema === "autojs6.typescript.node-resolution-parity" &&
        fixture.revision === 1 &&
        fixture.typescriptVersion === expectedVersion &&
        fixture.scope ===
            "R2-M2-C6-B closed-world resolver parity; not complete R2-M2-C6" &&
        Array.isArray(fixture.cases),
    "R2-M2-C6-B parity fixture authority is malformed.",
);
for (const api of [
    "resolveModuleName",
    "createModuleResolutionCache",
    "getModeForResolutionAtIndex",
    "getModeForUsageLocation",
]) {
    assert(typeof ts[api] === "function", `TypeScript API is unavailable: ${api}`);
}
assert(
    ts.ModuleResolutionKind.NodeNext === 99 &&
        ts.ModuleKind.NodeNext === 199 &&
        ts.ModuleKind.ESNext === 99 &&
        ts.ModuleKind.CommonJS === 1,
    "TypeScript NodeNext/mode enum authority drifted.",
);

const stableFailures = new Set([
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

const toTypeScriptPath = (path) => {
    if (path === "package.json" || path === "/package.json") {
        return "/workspace/package.json";
    }
    if (path.startsWith("packages/")) {
        return `/workspace/node_modules/${path.slice("packages/".length)}`;
    }
    if (path.startsWith("/packages/")) {
        return `/workspace/node_modules/${path.slice("/packages/".length)}`;
    }
    if (path.startsWith("/sources/")) {
        return `/workspace/sources/${path.slice("/sources/".length)}`;
    }
    if (path.startsWith("/declarations/")) {
        return `/workspace/declarations/${path.slice("/declarations/".length)}`;
    }
    throw new Error(`Unsupported fixture path: ${path}`);
};

const fromTypeScriptPath = (path) => {
    if (path === "/workspace/package.json") return "/package.json";
    if (path.startsWith("/workspace/node_modules/")) {
        return `/packages/${path.slice("/workspace/node_modules/".length)}`;
    }
    if (path.startsWith("/workspace/sources/")) {
        return `/sources/${path.slice("/workspace/sources/".length)}`;
    }
    if (path.startsWith("/workspace/declarations/")) {
        return `/declarations/${path.slice("/workspace/declarations/".length)}`;
    }
    throw new Error(`TypeScript escaped the parity VFS: ${path}`);
};

const resolveWithTypeScript = (testCase) => {
    const files = new Map();
    for (const metadata of testCase.metadata) {
        assert(
            exactKeys(metadata, ["path", "json"]) &&
                typeof metadata.path === "string" &&
                typeof metadata.json === "string",
            `${testCase.id}: metadata fixture is malformed.`,
        );
        files.set(toTypeScriptPath(metadata.path), metadata.json);
    }
    for (const target of testCase.targets) {
        assert(
            exactKeys(target, ["path", "kind"]) &&
                (target.kind === "source" || target.kind === "declaration"),
            `${testCase.id}: target fixture is malformed.`,
        );
        files.set(toTypeScriptPath(target.path), "export {};\n");
    }
    const containingPath = toTypeScriptPath(testCase.containingPath);
    if (!files.has(containingPath)) files.set(containingPath, "export {};\n");

    const directories = new Set(["/", "/workspace"]);
    for (const path of files.keys()) {
        const segments = path.split("/");
        for (let index = 2; index < segments.length; index += 1) {
            directories.add(segments.slice(0, index).join("/"));
        }
    }
    const hostCalls = [];
    const record = (operation, path) => {
        assert(
            typeof path === "string" &&
                path.startsWith("/") &&
                !path.includes("\\") &&
                !path.includes("://"),
            `${testCase.id}: TypeScript requested a non-canonical host path: ${path}`,
        );
        hostCalls.push([operation, path]);
    };
    const host = {
        directoryExists(path) {
            record("directoryExists", path);
            return directories.has(path);
        },
        fileExists(path) {
            record("fileExists", path);
            return files.has(path);
        },
        getCurrentDirectory: () => "/workspace",
        getDirectories(path) {
            record("getDirectories", path);
            const prefix = path === "/" ? "/" : `${path}/`;
            return Array.from(directories)
                .filter(
                    (candidate) =>
                        candidate.startsWith(prefix) &&
                        candidate !== path &&
                        !candidate.slice(prefix.length).includes("/"),
                )
                .sort();
        },
        readFile(path) {
            record("readFile", path);
            return files.get(path);
        },
        realpath(path) {
            record("realpath", path);
            return path;
        },
        useCaseSensitiveFileNames: () => true,
    };
    const options = Object.freeze({
        allowJs: false,
        customConditions: testCase.customConditions.slice(),
        module: ts.ModuleKind.NodeNext,
        moduleResolution: ts.ModuleResolutionKind.NodeNext,
        noDtsResolution: false,
        resolveJsonModule: false,
        resolvePackageJsonExports: true,
        resolvePackageJsonImports: true,
        types: [],
    });
    const resolutionMode =
        testCase.mode === "import"
            ? ts.ModuleKind.ESNext
            : ts.ModuleKind.CommonJS;
    const result = ts.resolveModuleName(
        testCase.specifier,
        containingPath,
        options,
        host,
        undefined,
        undefined,
        resolutionMode,
    );
    return {
        hostCalls,
        target:
            result.resolvedModule === undefined
                ? null
                : fromTypeScriptPath(result.resolvedModule.resolvedFileName),
    };
};

let supported = 0;
let failClosed = 0;
const ids = new Set();
for (const testCase of fixture.cases) {
    assert(
        exactKeys(testCase, [
            "id",
            "classification",
            "mode",
            "customConditions",
            "containingPath",
            "specifier",
            "dependencyRoots",
            "metadata",
            "targets",
            "expectedTypeScriptTarget",
            "expectedR2",
        ]),
        "Parity case shape is not exact.",
    );
    assert(
        typeof testCase.id === "string" && !ids.has(testCase.id),
        "Parity case id is missing or duplicated.",
    );
    ids.add(testCase.id);
    assert(
        testCase.mode === "import" || testCase.mode === "require",
        `${testCase.id}: resolution mode is invalid.`,
    );
    assert(
        Array.isArray(testCase.customConditions) &&
            Array.isArray(testCase.dependencyRoots) &&
            Array.isArray(testCase.metadata) &&
            Array.isArray(testCase.targets),
        `${testCase.id}: fixture lists are malformed.`,
    );
    if (testCase.classification === "supported") supported += 1;
    else if (testCase.classification === "fail-closed") failClosed += 1;
    else throw new Error(`${testCase.id}: classification is invalid.`);

    const expectedR2 = testCase.expectedR2;
    assert(
        expectedR2 !== null && typeof expectedR2 === "object",
        `${testCase.id}: R2 expectation is missing.`,
    );
    if (expectedR2.kind === "failure") {
        assert(
            exactKeys(expectedR2, ["kind", "code"]) &&
                stableFailures.has(expectedR2.code),
            `${testCase.id}: R2 failure expectation is invalid.`,
        );
    } else {
        assert(
            expectedR2.kind === "resolved" &&
                exactKeys(expectedR2, [
                    "kind",
                    "target",
                    "packageBoundary",
                    "packageMetadataPath",
                    "matchedKey",
                    "matchedConditions",
                ]),
            `${testCase.id}: R2 resolved expectation is invalid.`,
        );
    }

    const actual = resolveWithTypeScript(testCase);
    assert(
        actual.target === testCase.expectedTypeScriptTarget,
        `${testCase.id}: TypeScript target drifted: ${actual.target}`,
    );
    assert(
        actual.hostCalls.length > 0,
        `${testCase.id}: TypeScript did not use the in-memory ModuleResolutionHost.`,
    );
}

assert(
    supported === 12 && failClosed === 6 && fixture.cases.length === 18,
    "Parity case inventory drifted.",
);
process.stdout.write(
    `R2-M2-C6-B TypeScript ${expectedVersion} resolver parity verified: ` +
        `${supported} supported, ${failClosed} fail-closed, ` +
        `${stableFailures.size} stable failures, ModuleResolutionHost-only\n`,
);
