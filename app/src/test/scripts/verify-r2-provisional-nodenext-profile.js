"use strict";

const childProcess = require("node:child_process");
const crypto = require("node:crypto");
const dns = require("node:dns");
const fs = require("node:fs");
const http = require("node:http");
const https = require("node:https");
const net = require("node:net");
const path = require("node:path");
const tls = require("node:tls");
const vm = require("node:vm");
const {
    projectInputAuthority,
    record: inputArchiveRecord,
} = require("./r2-input-archive-authority.js");
const workerThreads = require("node:worker_threads");

const [
    workerArgument,
    distributionArgument,
    typeScriptLibArgument,
    fixtureArgument,
    expectedTypeScriptVersion,
] = process.argv.slice(2);
if (
    !workerArgument ||
    !distributionArgument ||
    !typeScriptLibArgument ||
    !fixtureArgument ||
    !expectedTypeScriptVersion
) {
    throw new Error(
        "Usage: node verify-r2-provisional-nodenext-profile.js " +
            "<compiler-worker.js> <r2-declarations/v2> <typescript-lib-directory> " +
            "<provisional-nodenext-p1.json> <typescript-version>",
    );
}

const workerPath = path.resolve(workerArgument);
const distributionRoot = path.resolve(distributionArgument);
const typeScriptLibRoot = path.resolve(typeScriptLibArgument);
const fixturePath = path.resolve(fixtureArgument);
const manifestPath = path.join(distributionRoot, "distribution.json");
const compilerPath = path.join(typeScriptLibRoot, "typescript.js");
const workerSource = fs.readFileSync(workerPath, "utf8");
const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;
const exactKeys = (value, expected) =>
    value !== null &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    JSON.stringify(Object.keys(value).sort(compareCodeUnits)) ===
        JSON.stringify(expected.slice().sort(compareCodeUnits));
const sha256 = (value, encoding) =>
    crypto.createHash("sha256").update(value, encoding).digest("hex");
const sha256Pattern = /^[0-9a-f]{64}$/;
const normalizeVfsPath = (candidate) => {
    const replaced = candidate.replaceAll("\\", "/");
    return path.posix.normalize(
        replaced.startsWith("/") ? replaced : `/${replaced}`,
    );
};
const decodeUtf8 = (bytes, label) => {
    try {
        return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
    } catch (_) {
        throw new Error(`P1 input is not valid UTF-8: ${label}`);
    }
};

assert(fs.existsSync(manifestPath), `Missing declaration manifest: ${manifestPath}`);
assert(fs.existsSync(compilerPath), `Missing TypeScript compiler: ${compilerPath}`);
assert(fs.existsSync(fixturePath), `Missing P1 fixture: ${fixturePath}`);
assert(workerSource.length > 0, `Missing production Worker: ${workerPath}`);

const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
const fixture = JSON.parse(fs.readFileSync(fixturePath, "utf8"));
const ts = require(compilerPath);

assert(
    expectedTypeScriptVersion === "6.0.3" &&
        ts.version === expectedTypeScriptVersion,
    `P1 requires TypeScript 6.0.3, received ${ts.version}.`,
);
assert(
    ts.ModuleKind.NodeNext === 199 &&
        ts.ModuleResolutionKind.NodeNext === 99,
    "TypeScript NodeNext enum authority drifted from module=199/resolution=99.",
);
assert(
    exactKeys(fixture, ["schemaVersion", "authority", "golden", "failure"]) &&
        fixture.schemaVersion === 1,
    "P1 fixture top-level schema drifted.",
);
assert(
    exactKeys(fixture.authority, [
        "targetProfile",
        "profileSchemaRevision",
        "profileRevision",
        "configSchemaRevision",
        "configKind",
        "sourceExtensions",
        "outputExtension",
        "canonicalEffectiveOptionsJson",
        "effectiveOptionsFingerprint",
        "runtimeCapabilitiesCanonicalInput",
        "runtimeCapabilitiesFingerprint",
    ]),
    "P1 profile authority schema drifted.",
);
assert(
    fixture.authority.targetProfile === "node" &&
        fixture.authority.profileSchemaRevision === 1 &&
        fixture.authority.profileRevision === 1 &&
        fixture.authority.configSchemaRevision === 1 &&
        fixture.authority.configKind === "none" &&
        JSON.stringify(fixture.authority.sourceExtensions) ===
            JSON.stringify([".ts"]) &&
        fixture.authority.outputExtension === ".js",
    "P1 must remain config-none and admit only .ts -> .js.",
);
assert(
    exactKeys(fixture.golden, [
        "sourcePath",
        "sourceText",
        "outputPaths",
        "javaScriptMarkers",
    ]) &&
        fixture.golden.sourcePath === "/sources/main.ts" &&
        typeof fixture.golden.sourceText === "string" &&
        fixture.golden.sourceText.length > 0 &&
        JSON.stringify(fixture.golden.outputPaths) ===
            JSON.stringify(["/outputs/main.js", "/outputs/main.js.map"]) &&
        Array.isArray(fixture.golden.javaScriptMarkers) &&
        fixture.golden.javaScriptMarkers.length > 0,
    "P1 golden fixture schema drifted.",
);
assert(
    exactKeys(fixture.failure, ["sourceText", "diagnosticCodes"]) &&
        typeof fixture.failure.sourceText === "string" &&
        fixture.failure.sourceText.length > 0 &&
        JSON.stringify(fixture.failure.diagnosticCodes) ===
            JSON.stringify([2304, 2304]),
    "P1 failure/clean-successor fixture drifted.",
);

const canonicalOptions = JSON.parse(
    fixture.authority.canonicalEffectiveOptionsJson,
);
assert(
    JSON.stringify(canonicalOptions) ===
        fixture.authority.canonicalEffectiveOptionsJson &&
        exactKeys(canonicalOptions, [
            "allowJs",
            "checkJs",
            "ignoreDeprecations",
            "incremental",
            "inlineSourceMap",
            "inlineSources",
            "module",
            "moduleResolution",
            "newLine",
            "noEmitOnError",
            "noLib",
            "outDir",
            "rootDir",
            "skipLibCheck",
            "sourceMap",
            "strict",
            "target",
            "types",
        ]) &&
        canonicalOptions.allowJs === false &&
        canonicalOptions.checkJs === false &&
        canonicalOptions.ignoreDeprecations === "6.0" &&
        canonicalOptions.incremental === false &&
        canonicalOptions.inlineSourceMap === false &&
        canonicalOptions.inlineSources === false &&
        canonicalOptions.module === ts.ModuleKind.NodeNext &&
        canonicalOptions.moduleResolution === ts.ModuleResolutionKind.NodeNext &&
        canonicalOptions.newLine === ts.NewLineKind.LineFeed &&
        canonicalOptions.noEmitOnError === true &&
        canonicalOptions.noLib === false &&
        canonicalOptions.outDir === "/outputs" &&
        canonicalOptions.rootDir === "/sources" &&
        canonicalOptions.skipLibCheck === false &&
        canonicalOptions.sourceMap === true &&
        canonicalOptions.strict === true &&
        canonicalOptions.target === ts.ScriptTarget.ES2018 &&
        Array.isArray(canonicalOptions.types) &&
        canonicalOptions.types.length === 0,
    "P1 NodeNext effective options drifted.",
);
assert(
    sha256(fixture.authority.canonicalEffectiveOptionsJson, "ascii") ===
            fixture.authority.effectiveOptionsFingerprint &&
        sha256Pattern.test(fixture.authority.effectiveOptionsFingerprint) &&
        sha256(fixture.authority.runtimeCapabilitiesCanonicalInput, "ascii") ===
            fixture.authority.runtimeCapabilitiesFingerprint &&
        sha256Pattern.test(fixture.authority.runtimeCapabilitiesFingerprint),
    "P1 effective-options or runtime-capability fingerprint drifted.",
);
assert(
    fixture.authority.runtimeCapabilitiesCanonicalInput ===
        "autojs6.typescript.runtime-capabilities.provisional" +
            "|runtimeCapabilitySchemaRevision=1" +
            "|profileSchemaRevision=1" +
            "|profileRevision=1" +
            "|configSchemaRevision=1" +
            "|configKind=none" +
            "|profile=node" +
            "|extensions=.ts" +
            "|compilerApi=createIncrementalProgram" +
            "|allowJs=false" +
            "|checkJs=false" +
            "|ignoreDeprecations=6.0" +
            "|incremental=false" +
            "|inlineSourceMap=false" +
            "|inlineSources=false" +
            "|module=NodeNext" +
            "|moduleResolution=NodeNext" +
            "|newLine=LF" +
            "|noEmitOnError=true" +
            "|noLib=false" +
            "|outDir=/outputs" +
            "|rootDir=/sources" +
            "|skipLibCheck=false" +
            "|sourceMap=true" +
            "|strict=true" +
            "|target=ES2018" +
            "|types=[]",
    "P1 runtime-capability canonical authority drifted.",
);

const requireP1SourcePath = (sourcePath) => {
    const canonical = normalizeVfsPath(sourcePath);
    assert(
        canonical.startsWith("/sources/") &&
            canonical.endsWith(".ts") &&
            !canonical.endsWith(".d.ts") &&
            !canonical.endsWith(".mts") &&
            !canonical.endsWith(".cts"),
        `P1 rejected deferred or unsupported source kind: ${sourcePath}`,
    );
    return canonical;
};
const sourcePath = requireP1SourcePath(fixture.golden.sourcePath);
for (const deferredSourcePath of ["/sources/main.mts", "/sources/main.cts"]) {
    let rejected = false;
    try {
        requireP1SourcePath(deferredSourcePath);
    } catch (error) {
        rejected = error instanceof Error &&
            error.message.startsWith("P1 rejected deferred or unsupported source kind:");
    }
    assert(rejected, `P1 unexpectedly admitted ${deferredSourcePath}.`);
}

assert(
    manifest !== null &&
        typeof manifest === "object" &&
        manifest.layers !== null &&
        typeof manifest.layers === "object" &&
        manifest.profiles !== null &&
        typeof manifest.profiles === "object" &&
        Array.isArray(manifest.files),
    "Generated declaration manifest schema is unavailable to P1.",
);
assert(
    JSON.stringify(manifest.profiles.node.layers) ===
            JSON.stringify(["common", "node"]) &&
        JSON.stringify(manifest.profiles.rhino.layers) ===
            JSON.stringify(["common", "rhino"]) &&
        Array.isArray(manifest.layers.node.files) &&
        manifest.layers.node.files.length > 0 &&
        Array.isArray(manifest.layers.rhino.files) &&
        manifest.layers.rhino.files.length > 0 &&
        manifest.layers.node.entryPoint === "layers/node/index.d.ts" &&
        manifest.layers.rhino.entryPoint === "layers/rhino/index.d.ts",
    "Generated declaration profile layering drifted before P1 admission.",
);

const requireNodeDeclarationLayers = (requestedLayers) => {
    const expectedLayers = manifest.profiles.node.layers;
    if (JSON.stringify(requestedLayers) !== JSON.stringify(expectedLayers)) {
        const error = new Error(
            "P1_NODE_DECLARATION_PROFILE_REJECTED: only common+node is admitted.",
        );
        error.code = "P1_NODE_DECLARATION_PROFILE_REJECTED";
        throw error;
    }
    return expectedLayers.slice();
};
assert(
    JSON.stringify(requireNodeDeclarationLayers(["common", "node"])) ===
        JSON.stringify(["common", "node"]),
    "P1 Node declaration selection drifted.",
);
let rhinoLayerRejected = false;
try {
    requireNodeDeclarationLayers(["common", "node", "rhino"]);
} catch (error) {
    rhinoLayerRejected =
        error instanceof Error &&
        error.code === "P1_NODE_DECLARATION_PROFILE_REJECTED";
}
assert(rhinoLayerRejected, "P1 failed to reject the Rhino declaration layer.");

const manifestFiles = new Map(
    manifest.files.map((record) => {
        assert(
            exactKeys(record, ["path", "sha256", "size"]) &&
                typeof record.path === "string" &&
                Number.isSafeInteger(record.size) &&
                record.size >= 0 &&
                sha256Pattern.test(record.sha256),
            "Generated declaration file record is malformed.",
        );
        return [record.path, record];
    }),
);
const readManifestDeclaration = (relativePath) => {
    const record = manifestFiles.get(relativePath);
    assert(record !== undefined, `Unmanifested declaration: ${relativePath}`);
    const absolutePath = path.join(
        distributionRoot,
        ...relativePath.split("/"),
    );
    const bytes = fs.readFileSync(absolutePath);
    assert(
        bytes.length === record.size && sha256(bytes) === record.sha256,
        `Generated declaration identity drifted: ${relativePath}`,
    );
    return decodeUtf8(bytes, relativePath);
};

const typeScriptLibraryNames = fs
    .readdirSync(typeScriptLibRoot, { withFileTypes: true })
    .filter(
        (entry) =>
            entry.isFile() && /^lib(?:\..+)?\.d\.ts$/.test(entry.name),
    )
    .map((entry) => entry.name)
    .sort(compareCodeUnits);
assert(
    typeScriptLibraryNames.includes("lib.d.ts") &&
        typeScriptLibraryNames.includes("lib.es5.d.ts"),
    "Generated common TypeScript declaration layer is incomplete.",
);
const commonDeclarationEntries = typeScriptLibraryNames.map((fileName) => {
    const absolutePath = path.join(typeScriptLibRoot, fileName);
    return [
        `/declarations/${fileName}`,
        decodeUtf8(fs.readFileSync(absolutePath), fileName),
    ];
});
const nodeDeclarationEntries = manifest.layers.node.files.map((relativePath) => {
    assert(
        relativePath.startsWith("layers/node/") &&
            relativePath.endsWith(".d.ts"),
        `Node declaration escaped its generated layer: ${relativePath}`,
    );
    return [
        `/declarations/node/${relativePath.slice("layers/node/".length)}`,
        readManifestDeclaration(relativePath),
    ];
});
const nodeDeclarationEntryPath = "/declarations/node/index.d.ts";
assert(
    nodeDeclarationEntries.some(
        ([declarationPath]) => declarationPath === nodeDeclarationEntryPath,
    ),
    "Generated Node declaration entry is missing.",
);
assert(
    nodeDeclarationEntries.every(
        ([declarationPath]) => !declarationPath.startsWith("/declarations/rhino/"),
    ),
    "P1 loaded the Rhino declaration layer into its Node inventory.",
);

const compilerOptions = Object.freeze({ ...canonicalOptions });
const createVfsCompilerHost = (entries, outputs) => {
    const files = new Map(
        entries.map(([fileName, text]) => [normalizeVfsPath(fileName), text]),
    );
    const directories = new Set(["/"]);
    for (const fileName of files.keys()) {
        let directory = path.posix.dirname(fileName);
        while (!directories.has(directory)) {
            directories.add(directory);
            directory = path.posix.dirname(directory);
        }
    }
    const sourceFiles = new Map();
    return {
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
                        candidate.startsWith(prefix) &&
                        candidate !== directory &&
                        !candidate.slice(prefix.length).includes("/"),
                )
                .map((candidate) => path.posix.basename(candidate))
                .sort(compareCodeUnits);
        },
        getNewLine: () => "\n",
        getSourceFile: (fileName, languageVersion) => {
            const canonical = normalizeVfsPath(fileName);
            if (!files.has(canonical)) return undefined;
            if (!sourceFiles.has(canonical)) {
                const sourceFile = ts.createSourceFile(
                    canonical,
                    files.get(canonical),
                    languageVersion,
                    true,
                    ts.ScriptKind.TS,
                );
                sourceFile.version = "0";
                sourceFiles.set(canonical, sourceFile);
            }
            return sourceFiles.get(canonical);
        },
        readFile: (fileName) => files.get(normalizeVfsPath(fileName)),
        realpath: (fileName) => normalizeVfsPath(fileName),
        useCaseSensitiveFileNames: () => true,
        writeFile: (
            fileName,
            text,
            _writeByteOrderMark,
            _onError,
            sourceFilesForOutput,
        ) => {
            const canonical = normalizeVfsPath(fileName);
            assert(
                fixture.golden.outputPaths.includes(canonical) &&
                    !outputs.has(canonical) &&
                    !canonical.endsWith(".mjs") &&
                    !canonical.endsWith(".cjs"),
                `P1 emitted an unauthorized output: ${canonical}`,
            );
            outputs.set(canonical, {
                sourcePaths: (sourceFilesForOutput || [])
                    .map((sourceFile) => sourceFile.fileName)
                    .sort(compareCodeUnits),
                text,
            });
        },
    };
};

const replaceFunctionForFence = (
    target,
    key,
    counter,
    label,
    restorations,
) => {
    const descriptor = Object.getOwnPropertyDescriptor(target, key);
    if (descriptor === undefined || typeof descriptor.value !== "function") {
        return false;
    }
    if (!descriptor.configurable && !descriptor.writable) return false;
    Object.defineProperty(target, key, {
        ...descriptor,
        value: (..._arguments) => {
            counter.count += 1;
            throw new Error(`P1 attempted forbidden ${label}.${key}.`);
        },
    });
    restorations.push(() => Object.defineProperty(target, key, descriptor));
    return true;
};
const withExternalEscapeFences = (action) => {
    const oracle = {
        nodeRuntimeCalls: { count: 0 },
        networkCalls: { count: 0 },
        typeScriptSystemCalls: { count: 0 },
    };
    const restorations = [];
    const patchedTypeScriptSystemFunctions = Object.keys(ts.sys).filter((key) =>
        replaceFunctionForFence(
            ts.sys,
            key,
            oracle.typeScriptSystemCalls,
            "ts.sys",
            restorations,
        ),
    );
    assert(
        patchedTypeScriptSystemFunctions.includes("readFile") &&
            patchedTypeScriptSystemFunctions.includes("fileExists") &&
            patchedTypeScriptSystemFunctions.includes("directoryExists"),
        "P1 could not install the TypeScript system escape fence.",
    );
    for (const [target, keys, label] of [
        [http, ["get", "request"], "http"],
        [https, ["get", "request"], "https"],
        [net, ["connect", "createConnection"], "net"],
        [tls, ["connect"], "tls"],
        [dns, ["lookup", "resolve"], "dns"],
    ]) {
        for (const key of keys) {
            replaceFunctionForFence(
                target,
                key,
                oracle.networkCalls,
                label,
                restorations,
            );
        }
    }
    for (const key of ["exec", "execFile", "fork", "spawn"]) {
        replaceFunctionForFence(
            childProcess,
            key,
            oracle.nodeRuntimeCalls,
            "child_process",
            restorations,
        );
    }
    replaceFunctionForFence(
        workerThreads,
        "Worker",
        oracle.nodeRuntimeCalls,
        "worker_threads",
        restorations,
    );
    if (typeof globalThis.fetch === "function") {
        replaceFunctionForFence(
            globalThis,
            "fetch",
            oracle.networkCalls,
            "globalThis",
            restorations,
        );
    }
    try {
        const result = action();
        assert(
            oracle.typeScriptSystemCalls.count === 0 &&
                oracle.networkCalls.count === 0 &&
                oracle.nodeRuntimeCalls.count === 0,
            "P1 escaped its snapshot-only compiler authority.",
        );
        return { oracle, result };
    } finally {
        for (const restore of restorations.reverse()) restore();
    }
};

const constructors = Object.freeze([
    {
        create: (rootNames, host) => {
            const program = ts.createProgram({
                host,
                options: compilerOptions,
                rootNames,
            });
            return { emit: () => program.emit(), program };
        },
        name: "createProgram",
    },
    {
        create: (rootNames, host) => {
            const builder = ts.createIncrementalProgram({
                host,
                options: compilerOptions,
                rootNames,
            });
            return {
                emit: () => builder.emit(),
                program: builder.getProgram(),
            };
        },
        name: "createIncrementalProgram",
    },
]);
const compile = (constructor, sourceText, expectedDiagnosticCodes, label) =>
    withExternalEscapeFences(() => {
        const userRootNames = Object.freeze([sourcePath]);
        const compilerRootNames = Object.freeze([
            ...userRootNames,
            nodeDeclarationEntryPath,
        ]);
        const outputs = new Map();
        const host = createVfsCompilerHost(
            [
                ...commonDeclarationEntries,
                ...nodeDeclarationEntries,
                [sourcePath, sourceText],
            ],
            outputs,
        );
        const compilation = constructor.create(compilerRootNames, host);
        const program = compilation.program;
        assert(
            JSON.stringify(userRootNames) ===
                    JSON.stringify(["/sources/main.ts"]) &&
                JSON.stringify(program.getRootFileNames()) ===
                    JSON.stringify(compilerRootNames) &&
                compilerRootNames[1] === nodeDeclarationEntryPath &&
                !userRootNames.includes(nodeDeclarationEntryPath),
            `${label}/${constructor.name} mixed compiler-only declarations into user roots.`,
        );
        const sourceInventory = new Set(
            program.getSourceFiles().map((sourceFile) => sourceFile.fileName),
        );
        assert(
            sourceInventory.has("/declarations/lib.d.ts") &&
                sourceInventory.has("/declarations/lib.es5.d.ts") &&
                sourceInventory.has(nodeDeclarationEntryPath) &&
                !Array.from(sourceInventory).some((fileName) =>
                    fileName.startsWith("/declarations/rhino/"),
                ),
            `${label}/${constructor.name} declaration profile isolation drifted.`,
        );
        const diagnostics = ts
            .getPreEmitDiagnostics(program)
            .filter(
                (diagnostic) => diagnostic.category === ts.DiagnosticCategory.Error,
            );
        const actualDiagnosticCodes = diagnostics
            .map((diagnostic) => diagnostic.code)
            .sort((left, right) => left - right);
        const expectedCodes = expectedDiagnosticCodes
            .slice()
            .sort((left, right) => left - right);
        assert(
            JSON.stringify(actualDiagnosticCodes) === JSON.stringify(expectedCodes),
            `${label}/${constructor.name} diagnostics were ${JSON.stringify(
                diagnostics.map((diagnostic) => ({
                    code: diagnostic.code,
                    message: ts.flattenDiagnosticMessageText(
                        diagnostic.messageText,
                        "\n",
                    ),
                })),
            )}, expected ${JSON.stringify(expectedCodes)}.`,
        );
        const emitResult = compilation.emit();
        if (expectedCodes.length > 0) {
            assert(
                emitResult.emitSkipped === true && outputs.size === 0,
                `${label}/${constructor.name} emitted after a failed Node profile compile.`,
            );
            return {
                diagnostics: actualDiagnosticCodes,
                javaScriptSha256: null,
                sourceMapSha256: null,
            };
        }
        const actualOutputPaths = Array.from(outputs.keys()).sort(compareCodeUnits);
        assert(
            emitResult.emitSkipped === false &&
                JSON.stringify(actualOutputPaths) ===
                    JSON.stringify(fixture.golden.outputPaths) &&
                Array.from(outputs.values()).every(
                    (output) =>
                        JSON.stringify(output.sourcePaths) ===
                        JSON.stringify([sourcePath]),
                ),
            `${label}/${constructor.name} output inventory drifted.`,
        );
        const javaScript = outputs.get("/outputs/main.js").text;
        const sourceMapText = outputs.get("/outputs/main.js.map").text;
        for (const marker of fixture.golden.javaScriptMarkers) {
            assert(
                javaScript.includes(marker),
                `${label}/${constructor.name} JavaScript golden is missing: ${marker}`,
            );
        }
        assert(
            !javaScript.includes(": string") &&
                javaScript.endsWith("//# sourceMappingURL=main.js.map"),
            `${label}/${constructor.name} did not emit canonical .ts -> .js output; ` +
                `tail=${JSON.stringify(javaScript.slice(-96))}.`,
        );
        const sourceMap = JSON.parse(sourceMapText);
        assert(
            exactKeys(sourceMap, [
                "version",
                "file",
                "sourceRoot",
                "sources",
                "names",
                "mappings",
            ]) &&
                sourceMap.version === 3 &&
                sourceMap.file === "main.js" &&
                sourceMap.sourceRoot === "" &&
                JSON.stringify(sourceMap.sources) ===
                    JSON.stringify(["../sources/main.ts"]) &&
                Array.isArray(sourceMap.names) &&
                sourceMap.names.length === 0 &&
                typeof sourceMap.mappings === "string" &&
                sourceMap.mappings.length > 0,
            `${label}/${constructor.name} source-map golden drifted.`,
        );
        return {
            diagnostics: actualDiagnosticCodes,
            javaScriptSha256: sha256(javaScript, "utf8"),
            sourceMapSha256: sha256(sourceMapText, "utf8"),
        };
    });

const successfulGoldens = constructors.map((constructor) =>
    compile(constructor, fixture.golden.sourceText, [], "NodeNext golden"),
);
assert(
    successfulGoldens.every(
        ({ oracle }) =>
            oracle.typeScriptSystemCalls.count === 0 &&
            oracle.networkCalls.count === 0 &&
            oracle.nodeRuntimeCalls.count === 0,
    ) &&
        successfulGoldens.every(
            ({ result }, index, all) =>
                index === 0 ||
                (result.javaScriptSha256 === all[0].result.javaScriptSha256 &&
                    result.sourceMapSha256 === all[0].result.sourceMapSha256),
        ),
    "P1 real-TypeScript APIs produced non-deterministic output or escaped their VFS.",
);

const incrementalConstructor = constructors.find(
    (constructor) => constructor.name === "createIncrementalProgram",
);
const failedCompilation = compile(
    incrementalConstructor,
    fixture.failure.sourceText,
    fixture.failure.diagnosticCodes,
    "Rhino-layer rejection",
);
assert(
    JSON.stringify(failedCompilation.result.diagnostics) ===
            JSON.stringify([2304, 2304]) &&
        failedCompilation.result.javaScriptSha256 === null &&
        failedCompilation.result.sourceMapSha256 === null,
    "P1 Rhino-layer failure did not fail closed.",
);
const cleanSuccessor = compile(
    incrementalConstructor,
    fixture.golden.sourceText,
    [],
    "Clean successor",
);
const incrementalGolden = successfulGoldens.find(
    ({ result }, index) => constructors[index].name === "createIncrementalProgram",
);
assert(
    cleanSuccessor.result.javaScriptSha256 ===
            incrementalGolden.result.javaScriptSha256 &&
        cleanSuccessor.result.sourceMapSha256 ===
            incrementalGolden.result.sourceMapSha256 &&
        cleanSuccessor.oracle.typeScriptSystemCalls.count === 0 &&
        cleanSuccessor.oracle.networkCalls.count === 0 &&
        cleanSuccessor.oracle.nodeRuntimeCalls.count === 0,
    "P1 failure polluted its clean successor.",
);

const runProductionWorkerOracle = async () => {
    if (!global.crypto) {
        Object.defineProperty(global, "crypto", {
            configurable: true,
            value: crypto.webcrypto,
        });
    }

    let compilerIoAccessCount = 0;
    let importScriptsCount = 0;
    const programConstructionCalls = [];
    const poisonedCompilerIo = new Proxy(ts.sys, {
        get() {
            compilerIoAccessCount += 1;
            throw new Error("P1 production Worker accessed TypeScript system I/O.");
        },
    });
    const typeScriptFacade = Object.create(ts);
    Object.defineProperty(typeScriptFacade, "sys", {
        value: poisonedCompilerIo,
    });
    Object.defineProperty(typeScriptFacade, "createIncrementalProgram", {
        value: (configuration) => {
            programConstructionCalls.push({
                options: JSON.parse(JSON.stringify(configuration.options)),
                rootNames: configuration.rootNames.slice(),
            });
            return ts.createIncrementalProgram(configuration);
        },
    });
    global.ts = typeScriptFacade;
    global.importScripts = (specifier) => {
        importScriptsCount += 1;
        assert(
            specifier === "typescript.js",
            `Production Worker imported an unexpected script: ${specifier}.`,
        );
    };
    const publishedMessages = [];
    global.self = {
        postMessage: (message) => publishedMessages.push(message),
    };
    vm.runInThisContext(workerSource, { filename: workerPath });
    assert(
        publishedMessages.length === 1 &&
            publishedMessages[0].kind === "ready" &&
            publishedMessages[0].tsVersion === expectedTypeScriptVersion &&
            importScriptsCount === 1,
        "Production Worker bootstrap did not preserve the pinned compiler authority.",
    );
    publishedMessages.length = 0;

    const workerBinding = (expression) =>
        vm.runInThisContext(expression, {
            filename: `${workerPath}#provisional-nodenext-p1`,
        });
    const dispatch = async (message) => {
        publishedMessages.length = 0;
        await self.onmessage({ data: message });
        return publishedMessages.slice();
    };
    const assertWorkerIdle = (label) => {
        assert(
            workerBinding("framedInput") === null &&
                workerBinding("framedCompilationRequestId") === null &&
                workerBinding("framedResult") === null,
            `${label} retained production Worker operation state.`,
        );
    };
    const expectAck = (messages, requestId, sequence, label) => {
        const acknowledgements = messages.filter(
            (message) => message.kind === "projectInputAck",
        );
        assert(
            acknowledgements.length === 1 &&
                acknowledgements[0].requestId === requestId &&
                acknowledgements[0].sequence === sequence &&
                !messages.some(
                    (message) =>
                        message.kind === "projectError" ||
                        message.kind === "projectConfigError",
                ),
            `${label} did not publish its exact ACK: ${JSON.stringify(messages)}.`,
        );
    };
    const frameBytes = 64 * 1024;
    let requestCounter = 0;
    const createOperation = (files, label) => {
        requestCounter += 1;
        const records = files.map(([filePath, text]) => {
            const bytes = Buffer.from(text, "utf8");
            return {
                bytes,
                path: filePath,
                sha256: sha256(bytes),
            };
        });
        return {
            authority: projectInputAuthority({
                fileRecords: records.map((record) =>
                    inputArchiveRecord(
                        record.path.startsWith("/declarations/")
                            ? "declaration"
                            : "source",
                        record.path,
                        record.bytes,
                    ),
                ),
            }),
            records,
            requestId: `provisional-node-p1-${label}-${requestCounter}`,
            sequence: 0,
            totalBytes: records.reduce(
                (total, record) => total + record.bytes.length,
                0,
            ),
        };
    };
    const sendStart = async (operation) => {
        const messages = await dispatch({
            ...operation.authority,
            configKind: fixture.authority.configKind,
            configSchemaRevision: fixture.authority.configSchemaRevision,
            fileCount: operation.records.length,
            kind: "projectInputStart",
            profileRevision: fixture.authority.profileRevision,
            profileSchemaRevision: fixture.authority.profileSchemaRevision,
            requestId: operation.requestId,
            sequence: operation.sequence,
            targetProfile: fixture.authority.targetProfile,
            totalBytes: operation.totalBytes,
            workerSpinMilliseconds: 0,
            workerSpinPhase: "none",
        });
        expectAck(messages, operation.requestId, operation.sequence, "Node start");
        operation.sequence += 1;
    };
    const sendFile = async (operation, record, fileIndex) => {
        let messages = await dispatch({
            byteLength: record.bytes.length,
            fileIndex,
            kind: "projectInputFileStart",
            path: record.path,
            requestId: operation.requestId,
            sequence: operation.sequence,
            sha256: record.sha256,
        });
        expectAck(
            messages,
            operation.requestId,
            operation.sequence,
            `${record.path} start`,
        );
        operation.sequence += 1;
        let chunkCount = 0;
        for (let offset = 0; offset < record.bytes.length; offset += frameBytes) {
            messages = await dispatch({
                chunkIndex: chunkCount,
                data: record.bytes
                    .subarray(offset, Math.min(offset + frameBytes, record.bytes.length))
                    .toString("base64"),
                fileIndex,
                kind: "projectInputFileChunk",
                requestId: operation.requestId,
                sequence: operation.sequence,
            });
            expectAck(
                messages,
                operation.requestId,
                operation.sequence,
                `${record.path} chunk ${chunkCount}`,
            );
            operation.sequence += 1;
            chunkCount += 1;
        }
        messages = await dispatch({
            chunkCount,
            fileIndex,
            kind: "projectInputFileEnd",
            requestId: operation.requestId,
            sequence: operation.sequence,
        });
        expectAck(
            messages,
            operation.requestId,
            operation.sequence,
            `${record.path} end`,
        );
        operation.sequence += 1;
    };
    const sendFiles = async (operation) => {
        for (let index = 0; index < operation.records.length; index += 1) {
            await sendFile(operation, operation.records[index], index);
        }
    };
    const readResult = async (operation, inputEndMessages) => {
        expectAck(
            inputEndMessages,
            operation.requestId,
            operation.sequence,
            "Node input end",
        );
        const start = inputEndMessages.find(
            (message) =>
                message.kind === "projectResultStart" &&
                message.requestId === operation.requestId,
        );
        assert(start, "Production Worker did not begin its framed result.");
        const chunks = [];
        for (let sequence = -1; sequence < start.chunkCount; sequence += 1) {
            const messages = await dispatch({
                kind: "projectResultAck",
                requestId: operation.requestId,
                sequence,
            });
            if (sequence + 1 < start.chunkCount) {
                const chunk = messages.find(
                    (message) => message.kind === "projectResultChunk",
                );
                assert(
                    chunk && chunk.index === sequence + 1,
                    `Production result chunk ${sequence + 1} drifted.`,
                );
                chunks.push(Buffer.from(chunk.data, "base64"));
            } else {
                assert(
                    messages.some(
                        (message) =>
                            message.kind === "projectResultEnd" &&
                            message.requestId === operation.requestId &&
                            message.byteLength === start.byteLength &&
                            message.sha256 === start.sha256,
                    ),
                    "Production Worker did not finish its framed result.",
                );
            }
        }
        const bytes = Buffer.concat(chunks);
        assert(
            bytes.length === start.byteLength && sha256(bytes) === start.sha256,
            "Production Worker result identity drifted.",
        );
        assertWorkerIdle("successful framed result");
        return JSON.parse(bytes.toString("utf8"));
    };
    const compileProductionNode = async (label) => {
        const operation = createOperation(
            [
                ...commonDeclarationEntries,
                ...nodeDeclarationEntries,
                [sourcePath, fixture.golden.sourceText],
            ],
            label,
        );
        await sendStart(operation);
        await sendFiles(operation);
        return readResult(
            operation,
            await dispatch({
                fileCount: operation.records.length,
                kind: "projectInputEnd",
                requestId: operation.requestId,
                sequence: operation.sequence,
                totalBytes: operation.totalBytes,
            }),
        );
    };
    const assertProductionSuccess = (result, label) => {
        assert(
            result.targetProfile === "node" &&
                result.profileSchemaRevision === 1 &&
                result.profileRevision === 1 &&
                result.configSchemaRevision === 1 &&
                result.configKind === "none" &&
                result.compilerApi === "createIncrementalProgram" &&
                result.tsVersion === expectedTypeScriptVersion &&
                result.emitSkipped === false &&
                result.diagnostics.length === 0 &&
                JSON.stringify(result.options) ===
                    fixture.authority.canonicalEffectiveOptionsJson,
            `${label} lost the exact Node rev1/config-none/options authority.`,
        );
        assert(
            JSON.stringify(result.outputs.map((output) => output.path)) ===
                JSON.stringify(fixture.golden.outputPaths) &&
                result.outputs.every(
                    (output) =>
                        JSON.stringify(output.sourcePaths) ===
                        JSON.stringify([sourcePath]),
                ) &&
                !Object.prototype.hasOwnProperty.call(result, "rootNames"),
            `${label} leaked compiler-only declarations into its user-result roots.`,
        );
        const javaScript = result.outputs.find(
            (output) => output.path === "/outputs/main.js",
        ).text;
        const sourceMapText = result.outputs.find(
            (output) => output.path === "/outputs/main.js.map",
        ).text;
        for (const marker of fixture.golden.javaScriptMarkers) {
            assert(javaScript.includes(marker), `${label} is missing ${marker}.`);
        }
        assert(
            JSON.parse(sourceMapText).file === "main.js" &&
                !result.outputs.some(
                    (output) =>
                        output.path.endsWith(".mjs") || output.path.endsWith(".cjs"),
                ),
            `${label} did not preserve the P1 .ts -> .js/.map output contract.`,
        );
        return result.outputs.map((output) => output.sha256).join(":");
    };

    const baseline = await compileProductionNode("baseline");
    const baselineIdentity = assertProductionSuccess(
        baseline,
        "Production Node baseline",
    );
    assert(
        programConstructionCalls.length === 1 &&
            JSON.stringify(programConstructionCalls[0].options) ===
                fixture.authority.canonicalEffectiveOptionsJson &&
            JSON.stringify(programConstructionCalls[0].rootNames) ===
                JSON.stringify([sourcePath, nodeDeclarationEntryPath]),
        "Production Worker did not construct Program from user root + Node compiler-only root.",
    );

    const rejectedProgramCount = programConstructionCalls.length;
    for (const rejectedRevision of [0, 2, 3]) {
        const revisionDrift = createOperation(
            [
                ...commonDeclarationEntries,
                ...nodeDeclarationEntries,
                [sourcePath, fixture.golden.sourceText],
            ],
            `revision-${rejectedRevision}-rejection`,
        );
        const rejectionMessages = await dispatch({
            ...revisionDrift.authority,
            configKind: fixture.authority.configKind,
            configSchemaRevision: fixture.authority.configSchemaRevision,
            fileCount: revisionDrift.records.length,
            kind: "projectInputStart",
            profileRevision: rejectedRevision,
            profileSchemaRevision: fixture.authority.profileSchemaRevision,
            requestId: revisionDrift.requestId,
            sequence: revisionDrift.sequence,
            targetProfile: fixture.authority.targetProfile,
            totalBytes: revisionDrift.totalBytes,
            workerSpinMilliseconds: 0,
            workerSpinPhase: "none",
        });
        assert(
            rejectionMessages.length === 1 &&
                rejectionMessages[0].kind === "projectError" &&
                rejectionMessages[0].requestId === revisionDrift.requestId &&
                !rejectionMessages.some(
                    (message) =>
                        message.kind === "projectInputAck" ||
                        message.kind === "projectResultStart" ||
                        message.kind === "projectResultChunk" ||
                        message.kind === "projectResultEnd",
                ) &&
                programConstructionCalls.length === rejectedProgramCount,
            `Production Worker admitted Node revision ${rejectedRevision}: ${JSON.stringify(
                rejectionMessages,
            )}.`,
        );
        assertWorkerIdle(`Node revision ${rejectedRevision} rejection`);
    }

    const rejected = createOperation(
        [["/declarations/rhino/index.d.ts", "declare function toast(value: unknown): void;\n"]],
        "rhino-layer-rejection",
    );
    await sendStart(rejected);
    const rejectionMessages = await dispatch({
        byteLength: rejected.records[0].bytes.length,
        fileIndex: 0,
        kind: "projectInputFileStart",
        path: rejected.records[0].path,
        requestId: rejected.requestId,
        sequence: rejected.sequence,
        sha256: rejected.records[0].sha256,
    });
    assert(
        rejectionMessages.length === 1 &&
            rejectionMessages[0].kind === "projectError" &&
            rejectionMessages[0].requestId === rejected.requestId &&
            rejectionMessages[0].message.includes(
                "NodeNext input cannot transport the Rhino declaration layer.",
            ) &&
            !rejectionMessages.some(
                (message) =>
                    message.kind === "projectInputAck" ||
                    message.kind === "projectResultStart" ||
                    message.kind === "projectResultChunk" ||
                    message.kind === "projectResultEnd",
            ) &&
            programConstructionCalls.length === rejectedProgramCount,
        `Production Worker did not fail closed before Program: ${JSON.stringify(
            rejectionMessages,
        )}.`,
    );
    assertWorkerIdle("Rhino declaration rejection");

    const successor = await compileProductionNode("clean-successor");
    assert(
        assertProductionSuccess(successor, "Production clean successor") ===
                baselineIdentity &&
            programConstructionCalls.length === rejectedProgramCount + 1 &&
            compilerIoAccessCount === 0 &&
            importScriptsCount === 1,
        "Production Worker failure polluted its clean successor or escaped its VFS.",
    );
    return {
        compilerIoAccessCount,
        programCount: programConstructionCalls.length,
    };
};

const withProductionExternalEscapeFences = async (action) => {
    const oracle = {
        networkCalls: { count: 0 },
        nodeRuntimeCalls: { count: 0 },
    };
    const restorations = [];
    for (const [target, keys, label] of [
        [http, ["get", "request"], "http"],
        [https, ["get", "request"], "https"],
        [net, ["connect", "createConnection"], "net"],
        [tls, ["connect"], "tls"],
        [dns, ["lookup", "resolve"], "dns"],
    ]) {
        for (const key of keys) {
            replaceFunctionForFence(
                target,
                key,
                oracle.networkCalls,
                label,
                restorations,
            );
        }
    }
    for (const key of ["exec", "execFile", "fork", "spawn"]) {
        replaceFunctionForFence(
            childProcess,
            key,
            oracle.nodeRuntimeCalls,
            "child_process",
            restorations,
        );
    }
    replaceFunctionForFence(
        workerThreads,
        "Worker",
        oracle.nodeRuntimeCalls,
        "worker_threads",
        restorations,
    );
    if (typeof globalThis.fetch === "function") {
        replaceFunctionForFence(
            globalThis,
            "fetch",
            oracle.networkCalls,
            "globalThis",
            restorations,
        );
    }
    try {
        const result = await action();
        assert(
            oracle.networkCalls.count === 0 && oracle.nodeRuntimeCalls.count === 0,
            "Production Worker escaped to network or a Node Runtime subprocess.",
        );
        return { oracle, result };
    } finally {
        for (const restore of restorations.reverse()) restore();
    }
};

(async () => {
    const production = await withProductionExternalEscapeFences(
        runProductionWorkerOracle,
    );
    assert(
        production.result.compilerIoAccessCount === 0 &&
            production.oracle.networkCalls.count === 0 &&
            production.oracle.nodeRuntimeCalls.count === 0,
        "Production Worker escape oracle drifted.",
    );
    console.log(
        "R2-M2-P1 provisional NodeNext desktop profile verified: " +
            `TypeScript=${ts.version}, module=${canonicalOptions.module}, ` +
            `moduleResolution=${canonicalOptions.moduleResolution}, config=none, ` +
            `source=.ts, output=.js, APIs=${constructors.length}, ` +
            `production Worker Programs=${production.result.programCount}, ` +
            `common d.ts=${commonDeclarationEntries.length}, ` +
            `Node d.ts=${nodeDeclarationEntries.length}, Rhino rejected=2, ` +
            "ts.sys/network/Node Runtime=0, clean successors=2",
    );
})().catch((error) => {
    process.nextTick(() => {
        throw error;
    });
});
