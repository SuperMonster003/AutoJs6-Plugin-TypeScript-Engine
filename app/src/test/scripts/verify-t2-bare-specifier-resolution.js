"use strict";

const cryptoModule = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const [workerArgument, compilerArgument, expectedVersion] = process.argv.slice(2);
if (
    process.argv.slice(2).length !== 3 ||
    !workerArgument ||
    !compilerArgument ||
    expectedVersion !== "6.0.3"
) {
    throw new Error(
        "Usage: node verify-t2-bare-specifier-resolution.js " +
            "<worker.js> <typescript.js> 6.0.3",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const workerSource = fs.readFileSync(workerPath, "utf8");
const realTypeScript = require(compilerPath);
const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;

if (!global.crypto) {
    Object.defineProperty(global, "crypto", {
        configurable: true,
        value: cryptoModule.webcrypto,
    });
}
global.ts = realTypeScript;
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
    "Worker bootstrap drifted from TypeScript 6.0.3.",
);
const workerBinding = (expression) =>
    vm.runInThisContext(expression, {
        filename: `${workerPath}#t2-bare-specifier-resolution`,
    });

const fixtureFiles = new Map(
    Object.entries({
        "/declarations/lib.d.ts": [
            "interface Object {}",
            "interface Function {}",
            "interface CallableFunction extends Function {}",
            "interface NewableFunction extends Function {}",
            "interface IArguments { length: number; [index: number]: unknown; }",
            "interface String {}",
            "interface Boolean {}",
            "interface Number {}",
            "interface RegExp {}",
            "interface Array<T> { length: number; [index: number]: T; }",
            "interface Symbol {}",
        ].join("\n"),
        "/node_modules/dayjs/package.json": JSON.stringify({
            main: "dayjs.min.js",
            name: "dayjs",
            types: "index.d.ts",
        }),
        "/node_modules/dayjs/index.d.ts":
            "declare function dayjs(value?: unknown): string; export = dayjs;",
        "/node_modules/dayjs/dayjs.min.js": "module.exports = function dayjs() {};",
        "/node_modules/zod/package.json": JSON.stringify({
            exports: {
                ".": {
                    import: "./index.js",
                    require: "./index.cjs",
                    types: "./index.d.ts",
                },
                "./mini": {
                    import: "./mini/index.js",
                    require: "./mini/index.cjs",
                    types: "./mini/index.d.ts",
                },
            },
            name: "zod",
            type: "module",
        }),
        "/node_modules/zod/index.d.ts":
            "export declare const z: { string(): string };",
        "/node_modules/zod/index.js": "export const z = {};",
        "/node_modules/zod/index.cjs": "exports.z = {};",
        "/node_modules/zod/mini/index.d.ts":
            "export declare const mini: unique symbol;",
        "/node_modules/zod/mini/index.js": "export const mini = Symbol();",
        "/node_modules/zod/mini/index.cjs": "exports.mini = Symbol();",
        "/node_modules/@scope/tool/package.json": JSON.stringify({
            exports: {
                "./feature": {
                    import: "./feature.mjs",
                    require: "./feature.cjs",
                },
            },
            name: "@scope/tool",
        }),
        "/node_modules/@scope/tool/feature.d.mts":
            "export declare const branch: 'import';",
        "/node_modules/@scope/tool/feature.mjs":
            "export const branch = 'import';",
        "/node_modules/@scope/tool/feature.d.cts":
            "export declare const branch: 'require';",
        "/node_modules/@scope/tool/feature.cjs":
            "exports.branch = 'require';",
        "/node_modules/fallback/package.json": JSON.stringify({
            main: "./main.js",
            module: "./module.js",
            name: "fallback",
        }),
        "/node_modules/fallback/module.d.ts":
            "export declare const selected: 'module';",
        "/node_modules/fallback/module.js": "export const selected = 'module';",
        "/node_modules/fallback/main.d.ts":
            "export declare const selected: 'main';",
        "/node_modules/fallback/main.js": "exports.selected = 'main';",
        "/sources/main.mts": [
            'import dayjs from "dayjs";',
            'import { z } from "zod";',
            'import { mini } from "zod/mini";',
            "const formatted: string = dayjs();",
            "const parsed: string = z.string();",
            "const token: symbol = mini;",
            "export { formatted, parsed, token };",
        ].join("\n"),
    }).sort(([left], [right]) => compareCodeUnits(left, right)),
);

const directoryPaths = new Set(["/", "/node_modules", "/sources"]);
for (const filePath of fixtureFiles.keys()) {
    let directory = filePath.slice(0, filePath.lastIndexOf("/"));
    while (directory.length > 0) {
        directoryPaths.add(directory);
        if (directory === "/") break;
        directory = directory.slice(0, directory.lastIndexOf("/")) || "/";
    }
}
const vfs = Object.freeze({
    directoryExists: (candidate) => directoryPaths.has(candidate),
    fileExists: (candidate) => fixtureFiles.has(candidate),
    getDependencyLayer: () => Object.freeze({
        fingerprint:
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    }),
    getDirectories: (candidate) => {
        const prefix = candidate === "/" ? "/" : `${candidate}/`;
        return Array.from(directoryPaths)
            .filter(
                (directory) =>
                    directory.startsWith(prefix) &&
                    !directory.slice(prefix.length).includes("/"),
            )
            .map((directory) => directory.slice(prefix.length))
            .filter(Boolean)
            .sort(compareCodeUnits);
    },
    readFile: (candidate) => fixtureFiles.get(candidate),
    readRecord: (candidate) => {
        const text = fixtureFiles.get(candidate);
        return text === undefined ? undefined : { path: candidate, text };
    },
});
const options = Object.freeze({
    allowJs: false,
    allowSyntheticDefaultImports: true,
    customConditions: [],
    esModuleInterop: true,
    module: realTypeScript.ModuleKind.NodeNext,
    moduleResolution: realTypeScript.ModuleResolutionKind.NodeNext,
    noEmitOnError: true,
    noDtsResolution: false,
    outDir: "/outputs",
    resolveJsonModule: true,
    resolvePackageJsonExports: true,
    resolvePackageJsonImports: true,
    strict: true,
    target: realTypeScript.ScriptTarget.ES2022,
    types: [],
});
const resolver = workerBinding("createDependencyLayerResolution")(vfs, options);
assert(resolver !== null, "Dependency resolver did not activate its read-only layer.");

const modes = workerBinding("controlledNodeResolutionModes");
const failures = workerBinding("controlledNodeResolutionFailureCodes");
const resolvedPath = (specifier, mode) => {
    const result = resolver.resolveModule(specifier, "/sources/main.mts", mode);
    assert(
        result.kind === "resolved",
        `${specifier} did not resolve: ${JSON.stringify(result)}.`,
    );
    return result.target.path;
};

assert(
    resolvedPath("dayjs", modes.import) === "/node_modules/dayjs/index.d.ts",
    "dayjs did not use its desktop TypeScript types fallback.",
);
assert(
    resolvedPath("zod", modes.import) === "/node_modules/zod/index.d.ts" &&
        resolvedPath("zod/mini", modes.import) ===
            "/node_modules/zod/mini/index.d.ts",
    "zod root or subpath exports did not resolve through the types condition.",
);
assert(
    resolvedPath("@scope/tool/feature", modes.import) ===
        "/node_modules/@scope/tool/feature.d.mts" &&
        resolvedPath("@scope/tool/feature", modes.require) ===
            "/node_modules/@scope/tool/feature.d.cts",
    "Scoped import/require export conditions did not select distinct declarations.",
);
assert(
    resolvedPath("fallback", modes.import) ===
        "/node_modules/fallback/module.d.ts",
    "module did not precede main in the dependency fallback order.",
);
const missingExport = resolver.resolveModule(
    "zod/not-exported",
    "/sources/main.mts",
    modes.import,
);
assert(
    missingExport.kind === "failure" &&
        missingExport.code === failures.exportsNoMatch,
    "An unexported dependency subpath did not produce the stable exports miss.",
);
assert(
    resolver.resolveModule("not-installed", "/sources/main.mts", modes.import)
        .kind === "absent",
    "An absent dependency package did not defer to the existing resolver.",
);
assert(
    resolver.impliedModuleFormatFor("/node_modules/zod/index.d.ts") === "esm" &&
        resolver.impliedModuleFormatFor("/node_modules/dayjs/index.d.ts") ===
            "commonjs",
    "Dependency package type did not drive implied Node module format.",
);

const semanticOutputs = new Map();
const semanticHost = {
    createDirectory: () => {},
    directoryExists: vfs.directoryExists,
    fileExists: vfs.fileExists,
    getCanonicalFileName: (fileName) => fileName,
    getCurrentDirectory: () => "/",
    getDefaultLibFileName: () => "/declarations/lib.d.ts",
    getDirectories: vfs.getDirectories,
    getNewLine: () => "\n",
    getSourceFile: (fileName, languageVersion) => {
        const text = fixtureFiles.get(fileName);
        if (text === undefined) return undefined;
        const impliedNodeFormat = fileName.endsWith(".mts") ||
            (fileName.startsWith("/node_modules/") &&
                resolver.impliedModuleFormatFor(fileName) === "esm")
            ? realTypeScript.ModuleKind.ESNext
            : realTypeScript.ModuleKind.CommonJS;
        return realTypeScript.createSourceFile(
            fileName,
            text,
            { languageVersion, impliedNodeFormat },
            true,
        );
    },
    readFile: vfs.readFile,
    realpath: (candidate) => candidate,
    resolveModuleNameLiterals: (
        moduleLiterals,
        containingFile,
        _redirectedReference,
        _compilerOptions,
        containingSourceFile,
    ) => moduleLiterals.map((literal) => {
        const mode = realTypeScript.getModeForUsageLocation(
            containingSourceFile,
            literal,
            options,
        ) === realTypeScript.ModuleKind.CommonJS
            ? modes.require
            : modes.import;
        const result = resolver.resolveModule(literal.text, containingFile, mode);
        return result.kind !== "resolved"
            ? { resolvedModule: undefined }
            : {
                  resolvedModule: {
                      extension: result.target.path.endsWith(".d.mts")
                          ? realTypeScript.Extension.Dmts
                          : result.target.path.endsWith(".d.cts")
                            ? realTypeScript.Extension.Dcts
                            : realTypeScript.Extension.Dts,
                      isExternalLibraryImport: true,
                      resolvedFileName: result.target.path,
                  },
              };
    }),
    useCaseSensitiveFileNames: () => true,
    writeFile: (fileName, text) => semanticOutputs.set(fileName, text),
};
const semanticProgram = realTypeScript.createProgram({
    host: semanticHost,
    options,
    rootNames: ["/sources/main.mts"],
});
const semanticDiagnostics = realTypeScript.getPreEmitDiagnostics(semanticProgram);
assert(
    semanticDiagnostics.length === 0,
    "dayjs + zod full Program semantics failed: " +
        semanticDiagnostics
            .map((diagnostic) =>
                `${diagnostic.code}: ${realTypeScript.flattenDiagnosticMessageText(
                    diagnostic.messageText,
                    "\n",
                )}`,
            )
            .join(" | "),
);
const semanticEmit = semanticProgram.emit();
assert(
    !semanticEmit.emitSkipped &&
        semanticOutputs.has("/outputs/main.mjs") &&
        semanticOutputs.get("/outputs/main.mjs").includes('from "zod/mini"'),
    "dayjs + zod full Program emit did not preserve the Node ESM imports.",
);

resolver.release();
console.log(
    "T2-2 bare specifier resolution verified: dayjs + zod full Program emit, " +
        "scoped import/require conditions, module fallback, and stable misses.",
);
