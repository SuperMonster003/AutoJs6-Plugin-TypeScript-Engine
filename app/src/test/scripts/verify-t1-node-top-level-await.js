"use strict";

const cryptoModule = require("node:crypto");
const childProcess = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const vm = require("node:vm");

const [
    workerArgument,
    compilerArgument,
    declarationsArgument,
    libraryArgument,
    policyArgument,
    expectedVersion,
] = process.argv.slice(2);
if (
    process.argv.slice(2).length !== 6 ||
    !workerArgument ||
    !compilerArgument ||
    !declarationsArgument ||
    !libraryArgument ||
    !policyArgument ||
    !expectedVersion
) {
    throw new Error(
        "Usage: node verify-t1-node-top-level-await.js " +
            "<worker.js> <typescript.js> <generated-r2-declarations-dir> " +
            "<typescript-lib-dir> <policy-v4.json> <expected-version>",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const declarationRoot = path.resolve(declarationsArgument);
const libraryRoot = path.resolve(libraryArgument);
const policyPath = path.resolve(policyArgument);
const ts = require(compilerPath);
const workerSource = fs.readFileSync(workerPath, "utf8");
const policy = JSON.parse(fs.readFileSync(policyPath, "utf8"));
const declarationManifest = JSON.parse(
    fs.readFileSync(path.join(declarationRoot, "distribution.json"), "utf8"),
);

const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;
const exactJson = (actual, expected) =>
    JSON.stringify(actual) === JSON.stringify(expected);
const sha256 = (value) =>
    cryptoModule.createHash("sha256").update(value).digest("hex");
const normalizeVfsPath = (value) => {
    const normalized = path.posix.normalize(String(value).replaceAll("\\", "/"));
    assert(
        normalized.startsWith("/") &&
            normalized !== "/.." &&
            !normalized.startsWith("/../"),
        `T1-5 VFS path escaped its snapshot: ${value}`,
    );
    return normalized;
};

assert(ts.version === expectedVersion, "T1-5 loaded the wrong TypeScript version.");
assert(
    policy.schema === "autojs6.typescript.controlled-tsconfig-policy" &&
        policy.revision === 4 &&
        policy.fingerprint ===
            "4a9a5e70150f81e1ec9a5e2f749cc081f93f207f092300c96a79abfdcc152dab",
    "T1-5 did not receive the current revision-4 policy authority.",
);
assert(
    declarationManifest.schemaVersion === 2 &&
        declarationManifest.sources.common.version === expectedVersion &&
        exactJson(declarationManifest.profiles.node.layers, ["common", "node"]),
    "T1-5 Node declaration profile authority drifted.",
);

const standardLibraryEntries = fs
    .readdirSync(libraryRoot)
    .filter((name) => /^lib(?:\..+)?\.d\.ts$/.test(name))
    .sort(compareCodeUnits)
    .map((name) => [
        `/declarations/${name}`,
        fs.readFileSync(path.join(libraryRoot, name), "utf8"),
    ]);
const nodeLayer = declarationManifest.layers.node;
const nodeDeclarationEntries = nodeLayer.files.map((relativePath) => {
    assert(
        relativePath.startsWith("layers/node/") && relativePath.endsWith(".d.ts"),
        `T1-5 Node declaration escaped its generated layer: ${relativePath}`,
    );
    return [
        `/declarations/node/${relativePath.slice("layers/node/".length)}`,
        fs.readFileSync(
            path.join(declarationRoot, ...relativePath.split("/")),
            "utf8",
        ),
    ];
});
const nodeDeclarationEntry =
    `/declarations/node/${path.posix.basename(nodeLayer.entryPoint)}`;
assert(
    standardLibraryEntries.some(
        ([fileName]) => fileName === "/declarations/lib.es2022.d.ts",
    ) &&
        nodeDeclarationEntries.some(
            ([fileName]) => fileName === nodeDeclarationEntry,
        ),
    "T1-5 ES2022 or Node declaration entry is missing.",
);

Object.defineProperty(global, "crypto", {
    configurable: true,
    value: cryptoModule.webcrypto,
});
global.ts = ts;
global.importScripts = (specifier) => {
    assert(specifier === "typescript.js", "Worker imported an unexpected script.");
};
const workerMessages = [];
global.self = {
    postMessage: (message) => workerMessages.push(message),
};
vm.runInThisContext(workerSource, { filename: workerPath });
assert(
    workerMessages.length === 1 &&
        workerMessages[0].kind === "ready" &&
        workerMessages[0].tsVersion === expectedVersion,
    "T1-5 verifier did not load the production Worker.",
);
const workerBinding = (expression) =>
    vm.runInThisContext(expression, {
        filename: `${workerPath}#t1-node-top-level-await-${expression}`,
    });
const revision4Authority = workerBinding("controlledTsConfigRevision4Authority");
const parseControlledConfig = workerBinding("parseControlledTsConfig");
const resolveEffectiveOptions = workerBinding(
    "resolveControlledTsConfigEffectiveOptions",
);
const impliedModuleFormat = workerBinding("nodeRevisionTwoImpliedModuleFormat");
const createNodeSourceFile = workerBinding("createNodeRevisionTwoSourceFile");
assert(
    revision4Authority.configPolicyRevision === policy.revision &&
        revision4Authority.configPolicyFingerprint === policy.fingerprint,
    "Production Worker and native revision-4 T1-5 authority diverged.",
);

const packageFormatCatalog = Object.freeze({
    records: Object.freeze([
        Object.freeze({
            packageBoundaryPath: "/",
            packageMetadataPath: "package.json",
            packageMetadataSha256: sha256(Buffer.from('{"type":"module"}', "utf8")),
            packageMetadataSize: Buffer.byteLength('{"type":"module"}', "utf8"),
            typeState: "module",
        }),
        Object.freeze({
            packageBoundaryPath: "/packages/cjs",
            packageMetadataPath: "packages/cjs/package.json",
            packageMetadataSha256: sha256(
                Buffer.from('{"type":"commonjs"}', "utf8"),
            ),
            packageMetadataSize: Buffer.byteLength(
                '{"type":"commonjs"}',
                "utf8",
            ),
            typeState: "commonjs",
        }),
        Object.freeze({
            packageBoundaryPath: "/packages/esm",
            packageMetadataPath: "packages/esm/package.json",
            packageMetadataSha256: sha256(
                Buffer.from('{"type":"module"}', "utf8"),
            ),
            packageMetadataSize: Buffer.byteLength(
                '{"type":"module"}',
                "utf8",
            ),
            typeState: "module",
        }),
    ]),
});
assert(
    impliedModuleFormat("/sources/main.mts", packageFormatCatalog) === "esm" &&
        impliedModuleFormat("/sources/package-entry.ts", packageFormatCatalog) ===
            "esm" &&
        impliedModuleFormat(
            "/sources/packages/esm/value.ts",
            packageFormatCatalog,
        ) === "esm" &&
        impliedModuleFormat(
            "/sources/packages/cjs/value.ts",
            packageFormatCatalog,
        ) === "commonjs",
    "Production Worker did not classify root and nested package boundaries exactly.",
);

const sources = Object.freeze([
    Object.freeze({
        format: "mts",
        moduleFormat: "esm",
        outputPath: "/outputs/main.mjs",
        sourcePath: "/sources/main.mts",
        topLevelAwait: true,
    }),
    Object.freeze({
        format: "type-module-ts",
        moduleFormat: "esm",
        outputPath: "/outputs/package-entry.js",
        sourcePath: "/sources/package-entry.ts",
        topLevelAwait: true,
    }),
    Object.freeze({
        format: "workspace-commonjs",
        moduleFormat: "commonjs",
        outputPath: "/outputs/packages/cjs/value.js",
        sourcePath: "/sources/packages/cjs/value.ts",
        topLevelAwait: false,
    }),
    Object.freeze({
        format: "workspace-esm",
        moduleFormat: "esm",
        outputPath: "/outputs/packages/esm/value.js",
        sourcePath: "/sources/packages/esm/value.ts",
        topLevelAwait: false,
    }),
]);
const sourceText = (scenario) =>
    scenario.topLevelAwait
        ? 'const sleep = (milliseconds: number): Promise<void> =>\n' +
          '  new Promise((resolve): void => {\n' +
          '    setTimeout((): void => resolve(), milliseconds);\n' +
          '  });\n' +
          'const startedAt: number = Date.now();\n' +
          'await sleep(4);\n' +
          'export const answer: number = 40 + 2;\n' +
          `export const format: string = ${JSON.stringify(scenario.format)};\n` +
          'export const elapsedMilliseconds: number = Date.now() - startedAt;\n'
        : 'export const answer: number = 40 + 2;\n' +
          `export const format: string = ${JSON.stringify(scenario.format)};\n`;
const config = JSON.stringify({
    compilerOptions: {
        lib: ["ES2022"],
        target: "ES2022",
    },
    files: sources.map(({ sourcePath }) =>
        sourcePath.slice("/sources/".length),
    ),
});
const parsed = parseControlledConfig(config, revision4Authority, "node");
assert(
    parsed.ignoredProperties.length === 0,
    "T1-5 ES2022 config unexpectedly produced ignored properties.",
);

const createSnapshotHost = (entries) => {
    const files = new Map();
    for (const [fileName, text] of entries) {
        const normalized = normalizeVfsPath(fileName);
        assert(!files.has(normalized), `Duplicate T1-5 VFS path: ${normalized}`);
        files.set(normalized, text);
    }
    const directories = new Set(["/"]);
    for (const fileName of files.keys()) {
        let directory = path.posix.dirname(fileName);
        while (!directories.has(directory)) {
            directories.add(directory);
            directory = path.posix.dirname(directory);
        }
    }
    const outputs = new Map();
    const sourceFiles = new Map();
    const resolutionSession = Object.freeze({
        impliedModuleFormatFor: (fileName) =>
            impliedModuleFormat(fileName, packageFormatCatalog),
    });
    const host = {
        createDirectory: () => {},
        directoryExists: (directoryName) =>
            directories.has(normalizeVfsPath(directoryName)),
        fileExists: (fileName) => files.has(normalizeVfsPath(fileName)),
        getCanonicalFileName: (fileName) => fileName,
        getCurrentDirectory: () => "/",
        getDefaultLibFileName: () => "/declarations/lib.d.ts",
        getDefaultLibLocation: () => "/declarations",
        getDirectories: (directoryName) => {
            const directory = normalizeVfsPath(directoryName);
            const prefix = directory === "/" ? "/" : `${directory}/`;
            return Array.from(directories)
                .filter(
                    (candidate) =>
                        candidate !== directory &&
                        candidate.startsWith(prefix) &&
                        !candidate.slice(prefix.length).includes("/"),
                )
                .map((candidate) => path.posix.basename(candidate))
                .sort(compareCodeUnits);
        },
        getNewLine: () => "\n",
        getSourceFile: (fileName, languageVersion) => {
            const normalized = normalizeVfsPath(fileName);
            const text = files.get(normalized);
            if (text === undefined) return undefined;
            if (!sourceFiles.has(normalized)) {
                const sourceFile = createNodeSourceFile(
                    normalized,
                    text,
                    languageVersion,
                    resolutionSession,
                );
                sourceFile.version = "0";
                sourceFiles.set(normalized, sourceFile);
            }
            return sourceFiles.get(normalized);
        },
        readFile: (fileName) => files.get(normalizeVfsPath(fileName)),
        realpath: (fileName) => normalizeVfsPath(fileName),
        useCaseSensitiveFileNames: () => true,
        writeFile: (fileName, text) => {
            const normalized = normalizeVfsPath(fileName);
            assert(
                normalized.startsWith("/outputs/") && !outputs.has(normalized),
                `T1-5 emitted an invalid output: ${normalized}`,
            );
            outputs.set(normalized, text);
        },
    };
    return { host, outputs, sourceFiles };
};

const importGeneratedModule = async (javaScript, label) => {
    const encoded = Buffer.from(javaScript, "utf8").toString("base64");
    return import(`data:text/javascript;base64,${encoded}#t1-5-${label}`);
};
const executeGeneratedCommonJs = (javaScript, label) => {
    const moduleRecord = { exports: {} };
    vm.runInNewContext(
        javaScript,
        {
            exports: moduleRecord.exports,
            module: moduleRecord,
            require: (specifier) => {
                throw new Error(
                    `T1-6 generated CommonJS unexpectedly required ${specifier}.`,
                );
            },
        },
        { filename: `t1-6-${label}.cjs` },
    );
    return moduleRecord.exports;
};

(async () => {
    const effective = await resolveEffectiveOptions(parsed);
    assert(
        effective.revision === 4 &&
            effective.compilerOptions.target === ts.ScriptTarget.ES2022 &&
            effective.compilerOptions.module === ts.ModuleKind.NodeNext &&
            effective.compilerOptions.moduleResolution ===
                ts.ModuleResolutionKind.NodeNext &&
            exactJson(effective.compilerOptions.lib, ["lib.es2022.d.ts"]) &&
            effective.fingerprint ===
                "b77bfac45d80ad99a9a65168ecd97a10607f0574189e521e3355eb080b4a97ff",
        "T1-5 did not resolve the canonical NodeNext/ES2022 effective options.",
    );

    const { host, outputs, sourceFiles } = createSnapshotHost([
        ...standardLibraryEntries,
        ...nodeDeclarationEntries,
        ...sources.map((scenario) => [
            scenario.sourcePath,
            sourceText(scenario),
        ]),
    ]);
    const rootNames = [
        ...sources.map(({ sourcePath }) => sourcePath),
        nodeDeclarationEntry,
    ];
    const builder = ts.createIncrementalProgram({
        host,
        options: effective.compilerOptions,
        rootNames,
    });
    const program = builder.getProgram();
    const errors = ts
        .getPreEmitDiagnostics(program)
        .filter((diagnostic) => diagnostic.category === ts.DiagnosticCategory.Error)
        .map((diagnostic) => ({
            code: diagnostic.code,
            message: ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n"),
            sourcePath: diagnostic.file?.fileName ?? null,
            start: diagnostic.start ?? null,
        }));
    assert(errors.length === 0, `T1-5 diagnostics: ${JSON.stringify(errors)}`);
    const emit = builder.emit();
    assert(!emit.emitSkipped, "T1-5 NodeNext/ES2022 emit was skipped.");
    assert(
        sourceFiles.get("/sources/main.mts")?.impliedNodeFormat ===
            ts.ModuleKind.ESNext &&
            sourceFiles.get("/sources/package-entry.ts")?.impliedNodeFormat ===
                ts.ModuleKind.ESNext &&
            sourceFiles.get("/sources/packages/esm/value.ts")
                ?.impliedNodeFormat === ts.ModuleKind.ESNext &&
            sourceFiles.get("/sources/packages/cjs/value.ts")
                ?.impliedNodeFormat === ts.ModuleKind.CommonJS,
        "T1-5/T1-6 Program did not retain root and nearest-package formats.",
    );
    assert(
        exactJson(Array.from(outputs.keys()).sort(compareCodeUnits), [
            "/outputs/main.mjs",
            "/outputs/main.mjs.map",
            "/outputs/package-entry.js",
            "/outputs/package-entry.js.map",
            "/outputs/packages/cjs/value.js",
            "/outputs/packages/cjs/value.js.map",
            "/outputs/packages/esm/value.js",
            "/outputs/packages/esm/value.js.map",
        ]),
        `T1-5/T1-6 output inventory drifted: ${JSON.stringify(Array.from(outputs.keys()))}`,
    );

    const runtimeResults = {};
    for (const scenario of sources) {
        const javaScript = outputs.get(scenario.outputPath);
        const sourceMapText = outputs.get(`${scenario.outputPath}.map`);
        assert(
            typeof javaScript === "string" &&
                typeof sourceMapText === "string",
            `${scenario.format} did not emit JavaScript and Source Map.`,
        );
        if (scenario.moduleFormat === "esm") {
            assert(
                javaScript.includes("export const answer") &&
                    !javaScript.includes("exports.answer") &&
                    (!scenario.topLevelAwait || javaScript.includes("await sleep(4)")),
                `${scenario.format} did not preserve ESM semantics.`,
            );
        } else {
            assert(
                javaScript.includes("exports.answer") &&
                    !javaScript.includes("export const answer") &&
                    !javaScript.includes("await sleep(4)"),
                `${scenario.format} did not preserve CommonJS semantics.`,
            );
        }
        const sourceMap = JSON.parse(sourceMapText);
        const mappedSource =
            sourceMap.sources.length === 1
                ? normalizeVfsPath(
                      path.posix.resolve(
                          path.posix.dirname(`${scenario.outputPath}.map`),
                          sourceMap.sourceRoot || "",
                          sourceMap.sources[0],
                      ),
                  )
                : null;
        assert(
            sourceMap.version === 3 &&
                sourceMap.file === path.posix.basename(scenario.outputPath) &&
                mappedSource === scenario.sourcePath &&
                typeof sourceMap.mappings === "string" &&
                sourceMap.mappings.length > 0,
            `${scenario.format} Source Map drifted.`,
        );
        const runtime =
            scenario.moduleFormat === "esm"
                ? await importGeneratedModule(javaScript, scenario.format)
                : executeGeneratedCommonJs(javaScript, scenario.format);
        assert(
            runtime.answer === 42 &&
                runtime.format === scenario.format &&
                (!scenario.topLevelAwait ||
                    (Number.isInteger(runtime.elapsedMilliseconds) &&
                        runtime.elapsedMilliseconds >= 0)),
            `${scenario.format} runtime result drifted: ` +
                JSON.stringify({
                    answer: runtime.answer,
                    elapsedMilliseconds: runtime.elapsedMilliseconds,
                    format: runtime.format,
                }),
        );
        runtimeResults[scenario.format] = Object.freeze({
            elapsedMilliseconds: runtime.elapsedMilliseconds,
            outputSha256: sha256(Buffer.from(javaScript, "utf8")),
        });
    }

    const liveBindingEntries = Object.freeze([
        Object.freeze([
            "/sources/live/main.mts",
            'import { snapshot, setValue } from "./state.mjs";\n' +
                'console.log("t1-7.initial=" + snapshot());\n' +
                'setValue(42);\n' +
                'console.log("t1-7.updated=" + snapshot());\n',
        ]),
        Object.freeze([
            "/sources/live/state.mts",
            'import { readValue } from "./reader.mjs";\n' +
                'export let value: number = 1;\n' +
                'export function setValue(next: number): void { value = next; }\n' +
                'export function snapshot(): number { return readValue(); }\n',
        ]),
        Object.freeze([
            "/sources/live/reader.mts",
            'import { value } from "./barrel.mjs";\n' +
                'export function readValue(): number { return value; }\n',
        ]),
        Object.freeze([
            "/sources/live/barrel.mts",
            'export { value } from "./state.mjs";\n',
        ]),
    ]);
    const {
        host: liveBindingHost,
        outputs: liveBindingOutputs,
        sourceFiles: liveBindingSourceFiles,
    } = createSnapshotHost([
        ...standardLibraryEntries,
        ...nodeDeclarationEntries,
        ...liveBindingEntries,
    ]);
    const liveBindingBuilder = ts.createIncrementalProgram({
        host: liveBindingHost,
        options: effective.compilerOptions,
        rootNames: [
            ...liveBindingEntries.map(([sourcePath]) => sourcePath),
            nodeDeclarationEntry,
        ],
    });
    const liveBindingProgram = liveBindingBuilder.getProgram();
    const liveBindingErrors = ts
        .getPreEmitDiagnostics(liveBindingProgram)
        .filter(
            (diagnostic) =>
                diagnostic.category === ts.DiagnosticCategory.Error,
        )
        .map((diagnostic) => ({
            code: diagnostic.code,
            message: ts.flattenDiagnosticMessageText(
                diagnostic.messageText,
                "\n",
            ),
            sourcePath: diagnostic.file?.fileName ?? null,
            start: diagnostic.start ?? null,
        }));
    assert(
        liveBindingErrors.length === 0,
        `T1-7 diagnostics: ${JSON.stringify(liveBindingErrors)}`,
    );
    const liveBindingEmit = liveBindingBuilder.emit();
    assert(!liveBindingEmit.emitSkipped, "T1-7 ESM cycle emit was skipped.");
    assert(
        liveBindingEntries.every(
            ([sourcePath]) =>
                liveBindingSourceFiles.get(sourcePath)?.impliedNodeFormat ===
                ts.ModuleKind.ESNext,
        ),
        "T1-7 Program did not retain ESM format across the cycle.",
    );
    assert(
        exactJson(
            Array.from(liveBindingOutputs.keys()).sort(compareCodeUnits),
            [
                "/outputs/live/barrel.mjs",
                "/outputs/live/barrel.mjs.map",
                "/outputs/live/main.mjs",
                "/outputs/live/main.mjs.map",
                "/outputs/live/reader.mjs",
                "/outputs/live/reader.mjs.map",
                "/outputs/live/state.mjs",
                "/outputs/live/state.mjs.map",
            ],
        ),
        `T1-7 output inventory drifted: ${JSON.stringify(Array.from(liveBindingOutputs.keys()))}`,
    );
    const stateJavaScript = liveBindingOutputs.get(
        "/outputs/live/state.mjs",
    );
    const readerJavaScript = liveBindingOutputs.get(
        "/outputs/live/reader.mjs",
    );
    const barrelJavaScript = liveBindingOutputs.get(
        "/outputs/live/barrel.mjs",
    );
    assert(
        stateJavaScript.includes("export let value = 1") &&
            stateJavaScript.includes('from "./reader.mjs"') &&
            readerJavaScript.includes('from "./barrel.mjs"') &&
            barrelJavaScript.includes('export { value } from "./state.mjs"') &&
            !Array.from(liveBindingOutputs.values()).some((output) =>
                output.includes("exports.value"),
            ),
        "T1-7 emit did not preserve native ESM imports, mutable export, and re-export.",
    );
    const liveBindingRuntimeRoot = fs.mkdtempSync(
        path.join(os.tmpdir(), "autojs6-typescript-t1-7-"),
    );
    let liveBindingRuntime;
    try {
        for (const [outputPath, output] of liveBindingOutputs) {
            if (!outputPath.endsWith(".mjs")) continue;
            const relativePath = outputPath.slice("/outputs/".length);
            const target = path.join(
                liveBindingRuntimeRoot,
                ...relativePath.split("/"),
            );
            fs.mkdirSync(path.dirname(target), { recursive: true });
            fs.writeFileSync(target, output, "utf8");
        }
        liveBindingRuntime = childProcess.spawnSync(
            process.execPath,
            [path.join(liveBindingRuntimeRoot, "live", "main.mjs")],
            {
                cwd: liveBindingRuntimeRoot,
                encoding: "utf8",
                timeout: 15_000,
                windowsHide: true,
            },
        );
    } finally {
        fs.rmSync(liveBindingRuntimeRoot, { recursive: true, force: true });
    }
    assert(
        !liveBindingRuntime.error &&
            liveBindingRuntime.status === 0 &&
            liveBindingRuntime.stdout.includes("t1-7.initial=1") &&
            liveBindingRuntime.stdout.includes("t1-7.updated=42"),
        "T1-7 native Node ESM cycle did not preserve live bindings: " +
            JSON.stringify({
                error: liveBindingRuntime.error?.message,
                status: liveBindingRuntime.status,
                stderr: liveBindingRuntime.stderr,
                stdout: liveBindingRuntime.stdout,
            }),
    );

    process.stdout.write(
        "T1-5 Node top-level await verified: NodeNext + ES2022, " +
            ".mts -> .mjs and type:module .ts -> ESM .js, await sleep(4) " +
            "executed with answer=42; " +
            `effective=${effective.fingerprint}, ` +
            `mts=${runtimeResults.mts.outputSha256}, ` +
            `typeModuleTs=${runtimeResults["type-module-ts"].outputSha256}, ` +
            `workspaceEsm=${runtimeResults["workspace-esm"].outputSha256}, ` +
            `workspaceCommonJs=${
                runtimeResults["workspace-commonjs"].outputSha256
            }\n`,
    );
    process.stdout.write(
        "T1-7 Node ESM live binding verified: TypeScript .mts cycle + re-export " +
            "executed with initial=1 and updated=42; " +
            `state=${sha256(Buffer.from(stateJavaScript, "utf8"))}\n`,
    );
})().catch((error) => {
    process.stderr.write(`${error.stack || error}\n`);
    process.exitCode = 1;
});
