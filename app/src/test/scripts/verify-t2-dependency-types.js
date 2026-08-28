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
        "Usage: node verify-t2-dependency-types.js " +
            "<worker.js> <typescript.js> 6.0.3",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const workerSource = fs.readFileSync(workerPath, "utf8");
const ts = require(compilerPath);
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
global.ts = ts;
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
const workerBinding = (expression) => vm.runInThisContext(expression, {
    filename: `${workerPath}#t2-dependency-types`,
});
const createDependencyLayerResolution = workerBinding(
    "createDependencyLayerResolution",
);
const createDependencyAwareCompilerOptions = workerBinding(
    "createDependencyAwareCompilerOptions",
);
const createReportedProvisionalCompilerOptions = workerBinding(
    "createReportedProvisionalCompilerOptions",
);
const modes = workerBinding("controlledNodeResolutionModes");

const commonFiles = {
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
        name: "dayjs",
        main: "dayjs.min.js",
        types: "legacy/index.d.ts",
        typesVersions: {
            ">=6.0": {
                "*": ["ts6/*"],
            },
        },
    }),
    "/node_modules/dayjs/dayjs.min.js":
        "module.exports = function dayjs() {};",
    "/node_modules/dayjs/legacy/index.d.ts": [
        "interface LegacyDayjs { legacyOnly(): never; }",
        "declare function dayjs(): LegacyDayjs;",
        "export = dayjs;",
    ].join("\n"),
    "/node_modules/dayjs/ts6/legacy/index.d.ts": [
        'import { Dayjs } from "./internal";',
        "declare function dayjs(): Dayjs;",
        "export = dayjs;",
    ].join("\n"),
    "/node_modules/dayjs/ts6/legacy/internal.d.ts":
        "export interface Dayjs { format(template?: string): string; unix(): number; }",
    "/node_modules/@types/ambient/package.json": JSON.stringify({
        name: "@types/ambient",
        types: "index.d.ts",
    }),
    "/node_modules/@types/ambient/index.d.ts":
        "declare const ambientAnswer: 42;",
};
const sourceText = [
    'import dayjs from "dayjs";',
    "const value = dayjs();",
    "const formatted: string = value.format();",
    "const unix: number = value.unix();",
    "const ambient: 42 = ambientAnswer;",
    "const wrong: number = value.format();",
    "export { formatted, unix, ambient, wrong };",
].join("\n");

function verifyProfile(profile) {
    const sourcePath = profile === "node" ? "/sources/main.mts" : "/sources/main.ts";
    const fixtureFiles = new Map(
        Object.entries({ ...commonFiles, [sourcePath]: sourceText })
            .sort(([left], [right]) => compareCodeUnits(left, right)),
    );
    const directoryPaths = new Set(["/", "/declarations", "/node_modules", "/sources"]);
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
        getDependencyTypeNames: () => Object.freeze(["ambient"]),
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
    const baseOptions = Object.freeze({
        allowJs: false,
        allowSyntheticDefaultImports: true,
        alwaysStrict: true,
        esModuleInterop: true,
        forceConsistentCasingInFileNames: true,
        ignoreDeprecations: "6.0",
        module: profile === "node" ? ts.ModuleKind.NodeNext : ts.ModuleKind.CommonJS,
        moduleResolution: profile === "node"
            ? ts.ModuleResolutionKind.NodeNext
            : ts.ModuleResolutionKind.Node10,
        noEmit: true,
        noLib: false,
        skipLibCheck: false,
        strict: true,
        target: profile === "node" ? ts.ScriptTarget.ES2022 : ts.ScriptTarget.ES2018,
        types: [],
    });
    const options = createDependencyAwareCompilerOptions(vfs, baseOptions);
    assert(
        Object.isFrozen(options) &&
            Object.isFrozen(options.types) &&
            JSON.stringify(options.types) === JSON.stringify(["ambient"]) &&
            baseOptions.types.length === 0,
        `${profile} did not derive immutable installed @types compiler options.`,
    );
    const reportedOptions = createReportedProvisionalCompilerOptions(
        baseOptions,
        false,
    );
    assert(
        Array.isArray(reportedOptions.types) &&
            reportedOptions.types.length === 0 &&
            options.types.length === 1,
        `${profile} leaked Program-only @types into the public profile contract.`,
    );
    const resolver = createDependencyLayerResolution(vfs, options);
    assert(resolver !== null, `${profile} dependency type resolver did not activate.`);
    const directDayjs = resolver.resolveModule(
        "dayjs",
        sourcePath,
        profile === "node" ? modes.import : modes.require,
    );
    assert(
        directDayjs.kind === "resolved" &&
            directDayjs.target.path ===
                "/node_modules/dayjs/ts6/legacy/index.d.ts",
        `${profile} did not select the TypeScript 6 typesVersions branch: ` +
            JSON.stringify(directDayjs),
    );

    const host = {
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
            return text === undefined
                ? undefined
                : ts.createSourceFile(fileName, text, languageVersion, true);
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
            const usageMode = profile === "node" &&
                ts.getModeForUsageLocation(containingSourceFile, literal, options) ===
                    ts.ModuleKind.ESNext
                ? modes.import
                : modes.require;
            const result = resolver.resolveModule(literal.text, containingFile, usageMode);
            if (result.kind !== "resolved") return { resolvedModule: undefined };
            const resolvedFileName = result.target.path;
            const extension = resolvedFileName.endsWith(".d.mts")
                ? ts.Extension.Dmts
                : resolvedFileName.endsWith(".d.cts")
                  ? ts.Extension.Dcts
                  : resolvedFileName.endsWith(".d.ts")
                    ? ts.Extension.Dts
                    : resolvedFileName.endsWith(".mts")
                      ? ts.Extension.Mts
                      : resolvedFileName.endsWith(".cts")
                        ? ts.Extension.Cts
                        : ts.Extension.Ts;
            return {
                resolvedModule: {
                    extension,
                    isExternalLibraryImport: resolvedFileName.startsWith("/node_modules/"),
                    resolvedFileName,
                },
            };
        }),
        resolveTypeReferenceDirectiveReferences: (
            references,
            containingFile,
        ) => references.map((reference) => {
            const name = typeof reference === "string" ? reference : reference.fileName;
            const result = resolver.resolveTypeReference(name, containingFile);
            return result.kind !== "resolved"
                ? { resolvedTypeReferenceDirective: undefined }
                : {
                      resolvedTypeReferenceDirective: {
                          isExternalLibraryImport: true,
                          primary: true,
                          resolvedFileName: result.target.path,
                      },
                  };
        }),
        useCaseSensitiveFileNames: () => true,
        writeFile: () => {},
    };
    const program = ts.createProgram({
        host,
        options,
        rootNames: [sourcePath],
    });
    const diagnostics = ts.getPreEmitDiagnostics(program);
    const codes = diagnostics.map((diagnostic) => diagnostic.code).sort();
    assert(
        JSON.stringify(codes) === JSON.stringify([2322]),
        `${profile} dependency types produced diagnostics ${diagnostics
            .map((diagnostic) =>
                `${diagnostic.code}: ${ts.flattenDiagnosticMessageText(
                    diagnostic.messageText,
                    "\n",
                )}`,
            )
            .join(" | ")}.`,
    );
    assert(
        program.getSourceFile("/node_modules/@types/ambient/index.d.ts") !== undefined &&
            program.getSourceFile("/node_modules/dayjs/ts6/legacy/internal.d.ts") !== undefined &&
            program.getSourceFile("/node_modules/dayjs/legacy/index.d.ts") === undefined,
        `${profile} Program did not contain the exact @types/typesVersions declaration closure.`,
    );
    resolver.release();
    return codes;
}

const rhinoCodes = verifyProfile("rhino");
const nodeCodes = verifyProfile("node");
assert(
    JSON.stringify(rhinoCodes) === JSON.stringify(nodeCodes),
    "Rhino and Node compiler profiles disagree on dependency type diagnostics.",
);
console.log(
    "T2-4 dependency types verified: package types/typings, TypeScript 6 " +
        "typesVersions, installed @types, nested declaration imports, and identical " +
        "Rhino/Node TS2322 diagnostics.",
);
