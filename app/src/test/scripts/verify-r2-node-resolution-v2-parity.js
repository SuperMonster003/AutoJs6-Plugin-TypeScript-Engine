"use strict";

const fs = require("fs");
const path = require("path");

const compilerPath = process.argv[2];
const fixturePath = process.argv[3];
const expectedVersion = process.argv[4];
if (!compilerPath || !fixturePath || !expectedVersion || process.argv.length !== 5) {
    throw new Error(
        "Usage: node <script> <typescript.js> <parity-v2.json> 6.0.3",
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
        fixture.revision === 2 &&
        fixture.resolverPolicyRevision === 2 &&
        fixture.typescriptVersion === expectedVersion &&
        fixture.scope ===
            "R2-M2-P2-5 pure resolver v2 parity; Worker and Program remain P2-6 closed" &&
        Array.isArray(fixture.cases),
    "R2-M2-P2-5 parity fixture authority is malformed.",
);
assert(
    ts.ModuleResolutionKind.NodeNext === 99 &&
        ts.ModuleKind.NodeNext === 199 &&
        ts.ModuleKind.ESNext === 99 &&
        ts.ModuleKind.CommonJS === 1,
    "TypeScript NodeNext/mode enum authority drifted.",
);

const toTypeScriptPath = (fixturePathValue) => {
    if (fixturePathValue === "package.json") return "/workspace/package.json";
    if (fixturePathValue === "packages/pkg/package.json") {
        return "/workspace/node_modules/pkg/package.json";
    }
    if (fixturePathValue.startsWith("packages/pkg/")) {
        return `/workspace/node_modules/pkg/${fixturePathValue.slice("packages/pkg/".length)}`;
    }
    if (fixturePathValue.startsWith("/sources/")) {
        return `/workspace/sources/${fixturePathValue.slice("/sources/".length)}`;
    }
    if (fixturePathValue.startsWith("/declarations/")) {
        return `/workspace/declarations/${fixturePathValue.slice("/declarations/".length)}`;
    }
    throw new Error(`Unsupported fixture path: ${fixturePathValue}`);
};

const fromTypeScriptPath = (typescriptPath) => {
    if (typescriptPath.startsWith("/workspace/node_modules/pkg/")) {
        return `/packages/pkg/${typescriptPath.slice("/workspace/node_modules/pkg/".length)}`;
    }
    if (typescriptPath.startsWith("/workspace/sources/")) {
        return `/sources/${typescriptPath.slice("/workspace/sources/".length)}`;
    }
    if (typescriptPath.startsWith("/workspace/declarations/")) {
        return `/declarations/${typescriptPath.slice("/workspace/declarations/".length)}`;
    }
    throw new Error(`TypeScript escaped the P2-5 parity VFS: ${typescriptPath}`);
};

const impliedResolutionMode = (testCase) => {
    const containingPath = testCase.containingPath;
    if (containingPath.endsWith(".d.mts") || containingPath.endsWith(".mts")) {
        return ts.ModuleKind.ESNext;
    }
    if (containingPath.endsWith(".d.cts") || containingPath.endsWith(".cts")) {
        return ts.ModuleKind.CommonJS;
    }
    assert(
        containingPath.endsWith(".d.ts") || containingPath.endsWith(".ts"),
        `${testCase.id}: containing source kind is invalid.`,
    );
    const project =
        testCase.projectJson === null ? {} : JSON.parse(testCase.projectJson);
    return project.type === "module"
        ? ts.ModuleKind.ESNext
        : ts.ModuleKind.CommonJS;
};

const resolveWithTypeScript = (testCase) => {
    const files = new Map();
    if (testCase.projectJson !== null) {
        files.set("/workspace/package.json", testCase.projectJson);
    }
    if (testCase.packageJson !== null) {
        files.set("/workspace/node_modules/pkg/package.json", testCase.packageJson);
    }
    for (const targetPath of testCase.targetPaths) {
        files.set(toTypeScriptPath(targetPath), "export {};\n");
    }
    const containingPath = toTypeScriptPath(testCase.containingPath);
    files.set(containingPath, "export {};\n");

    const directories = new Set(["/", "/workspace"]);
    for (const filePath of files.keys()) {
        const segments = filePath.split("/");
        for (let index = 2; index < segments.length; index += 1) {
            directories.add(segments.slice(0, index).join("/"));
        }
    }
    const hostCalls = [];
    const record = (operation, requestedPath) => {
        assert(
            typeof requestedPath === "string" &&
                requestedPath.startsWith("/") &&
                !requestedPath.includes("\\") &&
                !requestedPath.includes("://"),
            `${testCase.id}: TypeScript requested a non-canonical host path.`,
        );
        hostCalls.push([operation, requestedPath]);
    };
    const host = {
        directoryExists(requestedPath) {
            record("directoryExists", requestedPath);
            return directories.has(requestedPath);
        },
        fileExists(requestedPath) {
            record("fileExists", requestedPath);
            return files.has(requestedPath);
        },
        getCurrentDirectory: () => "/workspace",
        getDirectories(requestedPath) {
            record("getDirectories", requestedPath);
            const prefix = requestedPath === "/" ? "/" : `${requestedPath}/`;
            return Array.from(directories)
                .filter(
                    (candidate) =>
                        candidate.startsWith(prefix) &&
                        candidate !== requestedPath &&
                        !candidate.slice(prefix.length).includes("/"),
                )
                .sort();
        },
        readFile(requestedPath) {
            record("readFile", requestedPath);
            return files.get(requestedPath);
        },
        realpath(requestedPath) {
            record("realpath", requestedPath);
            return requestedPath;
        },
        useCaseSensitiveFileNames: () => true,
    };
    const options = Object.freeze({
        allowJs: false,
        customConditions: [],
        module: ts.ModuleKind.NodeNext,
        moduleResolution: ts.ModuleResolutionKind.NodeNext,
        noDtsResolution: false,
        resolveJsonModule: false,
        resolvePackageJsonExports: true,
        resolvePackageJsonImports: true,
        types: [],
    });
    const result =
        testCase.usage === "type-reference"
            ? ts.resolveTypeReferenceDirective(
                  testCase.specifier,
                  containingPath,
                  options,
                  host,
                  undefined,
                  undefined,
                  impliedResolutionMode(testCase),
              )
            : ts.resolveModuleName(
                  testCase.specifier,
                  containingPath,
                  options,
                  host,
                  undefined,
                  undefined,
                  impliedResolutionMode(testCase),
              );
    const resolved =
        testCase.usage === "type-reference"
            ? result.resolvedTypeReferenceDirective
            : result.resolvedModule;
    return {
        hostCalls,
        target: resolved === undefined ? null : fromTypeScriptPath(resolved.resolvedFileName),
    };
};

let supported = 0;
let failClosed = 0;
const ids = new Set();
for (const testCase of fixture.cases) {
    assert(
        exactKeys(testCase, [
            "id",
            "usage",
            "classification",
            "projectJson",
            "packageJson",
            "containingPath",
            "specifier",
            "targetPaths",
            "expectedTypeScriptTarget",
            "expectedV2",
        ]),
        "P2-5 parity case shape is not exact.",
    );
    assert(
        typeof testCase.id === "string" && !ids.has(testCase.id),
        "P2-5 parity case id is missing or duplicated.",
    );
    ids.add(testCase.id);
    assert(
        testCase.usage === "module" || testCase.usage === "type-reference",
        `${testCase.id}: resolver usage is invalid.`,
    );
    assert(Array.isArray(testCase.targetPaths), `${testCase.id}: targets are malformed.`);
    if (testCase.classification === "supported") supported += 1;
    else if (testCase.classification === "fail-closed") failClosed += 1;
    else throw new Error(`${testCase.id}: classification is invalid.`);

    const expectedMode = {
        moduleFormat:
            impliedResolutionMode(testCase) === ts.ModuleKind.ESNext
                ? "esm"
                : "commonjs",
        conditionMode:
            testCase.usage === "type-reference"
                ? "types"
                : impliedResolutionMode(testCase) === ts.ModuleKind.ESNext
                  ? "import"
                  : "require",
    };
    assert(
        testCase.expectedV2.moduleFormat === expectedMode.moduleFormat &&
            testCase.expectedV2.conditionMode === expectedMode.conditionMode,
        `${testCase.id}: expected v2 implied-format evidence drifted.`,
    );
    if (testCase.classification === "supported") {
        assert(
            exactKeys(testCase.expectedV2, [
                "kind",
                "target",
                "moduleFormat",
                "conditionMode",
            ]) &&
                testCase.expectedV2.kind === "resolved" &&
                testCase.expectedV2.target === testCase.expectedTypeScriptTarget,
            `${testCase.id}: supported v2 expectation is malformed.`,
        );
    } else {
        assert(
            exactKeys(testCase.expectedV2, [
                "kind",
                "code",
                "moduleFormat",
                "conditionMode",
            ]) &&
                testCase.expectedV2.kind === "failure" &&
                testCase.expectedV2.code ===
                    "NODE_RESOLUTION_TARGET_NOT_IN_SNAPSHOT",
            `${testCase.id}: fail-closed v2 expectation is malformed.`,
        );
    }

    const actual = resolveWithTypeScript(testCase);
    assert(
        actual.target === testCase.expectedTypeScriptTarget,
        `${testCase.id}: TypeScript target drifted: ${actual.target}`,
    );
    assert(actual.hostCalls.length > 0, `${testCase.id}: in-memory host was not used.`);
}

assert(
    supported === 14 && failClosed === 4 && fixture.cases.length === 18,
    "R2-M2-P2-5 parity case inventory drifted.",
);
process.stdout.write(
    `R2-M2-P2-5 TypeScript ${expectedVersion} resolver v2 parity verified: ` +
        `${supported} supported, ${failClosed} fail-closed, ` +
        "implied-module-format+types-hook, complete-target-universe, " +
        "ModuleResolutionHost-only, Program=0\n",
);
