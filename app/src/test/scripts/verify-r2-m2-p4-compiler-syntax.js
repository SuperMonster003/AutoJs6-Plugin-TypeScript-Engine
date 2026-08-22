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
const workerThreads = require("node:worker_threads");

const [compilerArgument, declarationsArgument, libraryArgument, fixtureArgument, expectedVersion] =
    process.argv.slice(2);
if (
    !compilerArgument ||
    !declarationsArgument ||
    !libraryArgument ||
    !fixtureArgument ||
    !expectedVersion
) {
    throw new Error(
        "Usage: node verify-r2-m2-p4-compiler-syntax.js " +
            "<typescript.js> <generated-r2-declarations-dir> " +
            "<typescript-lib-dir> <fixture> <expected-version>",
    );
}

const compilerPath = path.resolve(compilerArgument);
const declarationRoot = path.resolve(declarationsArgument);
const libraryRoot = path.resolve(libraryArgument);
const fixturePath = path.resolve(fixtureArgument);
const ts = require(compilerPath);
const fixture = JSON.parse(fs.readFileSync(fixturePath, "utf8"));

const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const compareCodeUnits = (left, right) => (left < right ? -1 : left > right ? 1 : 0);
const sha256 = (value) => crypto.createHash("sha256").update(value, "utf8").digest("hex");
const normalizeVfsPath = (value) => {
    const normalized = path.posix.normalize(String(value).replaceAll("\\", "/"));
    assert(
        normalized.startsWith("/") && normalized !== "/.." && !normalized.startsWith("/../"),
        `P4 VFS path escaped its snapshot: ${value}`,
    );
    return normalized;
};
const readUtf8 = (fileName) => fs.readFileSync(fileName, "utf8");

assert(ts.version === expectedVersion, `Expected TypeScript ${expectedVersion}, received ${ts.version}.`);
assert(
    fixture.schema === "autojs6.typescript.r2-m2-p4.compiler-syntax" &&
        fixture.revision === 1 &&
        fixture.typescriptVersion === expectedVersion &&
        fixture.scope === "desktop-compiler-only/no-production-admission",
    "P4 fixture identity or compiler-only scope drifted.",
);
assert(
    JSON.stringify(fixture.execution.inputOrders) === JSON.stringify(["declared", "reversed"]) &&
        fixture.execution.repetitionsPerOrder === 2 &&
        fixture.execution.expectedHostFsCalls === 0 &&
        fixture.execution.expectedTsSysCalls === 0 &&
        fixture.execution.expectedNetworkCalls === 0 &&
        fixture.execution.expectedEmittedJavaScriptExecutions === 0 &&
        fixture.execution.sourceCollectionCanonical ===
            "sorted files; UTF-8(path + NUL + decimal byteLength + NUL + lowercase sha256 + LF)" &&
        fixture.execution.outputCollectionCanonical ===
            "sorted outputs; UTF-8(path + NUL + decimal byteLength + NUL + lowercase sha256 + LF)",
    "P4 deterministic execution authority drifted.",
);

const syntaxDefinitions = [
    ["enum", "numeric-enum-runtime-object"],
    ["namespace", "namespace-runtime-object"],
    ["class-fields", "es2018-constructor-field-initialization"],
    ["generics", "type-parameter-erasure"],
    ["optional-chaining", "es2018-nullish-short-circuit-lowering"],
    ["async", "es2018-native-async-await"],
    ["decorators", "standard-stage-3-transform/no-legacy-experimental-decorators"],
];
assert(
    JSON.stringify(fixture.syntaxMatrix) ===
        JSON.stringify(
            syntaxDefinitions.map(([syntax, semanticContract]) => ({
                syntax,
                profiles: ["rhino", "node"],
                semanticContract,
            })),
        ),
    "P4 syntax/profile/semantic matrix drifted.",
);

const manifest = JSON.parse(readUtf8(path.join(declarationRoot, "distribution.json")));
assert(
    manifest.schemaVersion === 1 &&
        manifest.sources.common.version === expectedVersion &&
        JSON.stringify(manifest.profiles.rhino.layers) === JSON.stringify(["common", "rhino"]) &&
        JSON.stringify(manifest.profiles.node.layers) === JSON.stringify(["common", "node"]),
    "Generated R2 declaration profile authority drifted.",
);

const commonDeclarationEntries = fs
    .readdirSync(libraryRoot)
    .filter((name) => /^lib(?:\..+)?\.d\.ts$/.test(name))
    .sort(compareCodeUnits)
    .map((name) => [`/declarations/${name}`, readUtf8(path.join(libraryRoot, name))]);
assert(
    commonDeclarationEntries.length === manifest.layers.common.fileCount &&
        commonDeclarationEntries.some(([name]) => name === "/declarations/lib.d.ts"),
    "Generated TypeScript standard-library inventory drifted.",
);

const profileDeclarations = new Map();
for (const profile of ["rhino", "node"]) {
    const layer = manifest.layers[profile];
    const entries = layer.files.map((relativePath) => {
        assert(
            relativePath.startsWith(`layers/${profile}/`) && relativePath.endsWith(".d.ts"),
            `${profile} declaration escaped its generated layer: ${relativePath}`,
        );
        const suffix = relativePath.slice(`layers/${profile}/`.length);
        return [
            `/declarations/${profile}/${suffix}`,
            readUtf8(path.join(declarationRoot, ...relativePath.split("/"))),
        ];
    });
    const entryPath = `/declarations/${profile}/${path.posix.basename(layer.entryPoint)}`;
    assert(
        entries.length === layer.fileCount && entries.some(([name]) => name === entryPath),
        `${profile} declaration entry inventory drifted.`,
    );
    profileDeclarations.set(profile, { entries, entryPath });
}

assert(
    JSON.stringify(Object.keys(fixture.authorities).sort(compareCodeUnits)) ===
        JSON.stringify(["node", "rhino"]),
    "P4 compiler-option authority inventory drifted.",
);
for (const profile of ["rhino", "node"]) {
    const authority = fixture.authorities[profile];
    assert(
        authority.kind === "frozen-profile" &&
            authority.compilerApi === "createIncrementalProgram" &&
            authority.targetProfile === profile &&
            authority.profileRevision === 1 &&
            authority.declarationLayer === profile &&
            authority.productionAdmission === "unchanged" &&
            authority.decoratorSemantics ===
                "standard-stage-3/experimentalDecorators-absent" &&
            sha256(authority.canonicalOptionsJson) === authority.optionsSha256,
        `${profile} P4 frozen profile authority drifted.`,
    );
    const options = JSON.parse(authority.canonicalOptionsJson);
    assert(
        JSON.stringify(options) === authority.canonicalOptionsJson &&
            options.allowJs === false &&
            options.target === ts.ScriptTarget.ES2018 &&
            options.sourceMap === true &&
            options.noEmitOnError === true &&
            !Object.prototype.hasOwnProperty.call(options, "experimentalDecorators"),
        `${profile} P4 frozen compiler options or standard decorator semantics drifted.`,
    );
}

const createSnapshotHost = (entries, expectedOutputPaths) => {
    const files = new Map();
    for (const [fileName, text] of entries) {
        const normalized = normalizeVfsPath(fileName);
        assert(!files.has(normalized), `P4 snapshot contains duplicate path: ${normalized}`);
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
    const host = {
        directoryExists: (directoryName) => directories.has(normalizeVfsPath(directoryName)),
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
            if (!files.has(normalized)) return undefined;
            if (!sourceFiles.has(normalized)) {
                const sourceFile = ts.createSourceFile(
                    normalized,
                    files.get(normalized),
                    languageVersion,
                    true,
                    ts.getScriptKindFromFileName(normalized),
                );
                sourceFile.version = "0";
                sourceFiles.set(normalized, sourceFile);
            }
            return sourceFiles.get(normalized);
        },
        readFile: (fileName) => files.get(normalizeVfsPath(fileName)),
        realpath: (fileName) => normalizeVfsPath(fileName),
        useCaseSensitiveFileNames: () => true,
        writeFile: (fileName, text, _bom, _onError, sourceFilesForOutput) => {
            const normalized = normalizeVfsPath(fileName);
            assert(
                expectedOutputPaths === null || expectedOutputPaths.has(normalized),
                `P4 emitted unauthorized output: ${normalized}`,
            );
            assert(!outputs.has(normalized), `P4 emitted duplicate output: ${normalized}`);
            outputs.set(normalized, {
                sourcePaths: (sourceFilesForOutput || [])
                    .map((sourceFile) => normalizeVfsPath(sourceFile.fileName))
                    .sort(compareCodeUnits),
                text,
            });
        },
    };
    return { host, outputs };
};

const replaceFunction = (target, key, counter, label, restorations) => {
    const descriptor = Object.getOwnPropertyDescriptor(target, key);
    if (!descriptor || typeof descriptor.value !== "function") return false;
    if (!descriptor.configurable && !descriptor.writable) return false;
    Object.defineProperty(target, key, {
        ...descriptor,
        value: (..._arguments) => {
            counter.count += 1;
            throw new Error(`P4 reached forbidden ${label}.${key}.`);
        },
    });
    restorations.push(() => Object.defineProperty(target, key, descriptor));
    return true;
};

const counterFor = (counts, key) => ({
    get count() {
        return counts[key];
    },
    set count(value) {
        counts[key] = value;
    },
});

const withEscapeFences = (action) => {
    const counts = { emittedJavaScriptExecutions: 0, hostFs: 0, network: 0, tsSys: 0 };
    const restorations = [];
    const installedFences = [];
    const installFence = (target, key, counterKey, label) => {
        assert(
            replaceFunction(
                target,
                key,
                counterFor(counts, counterKey),
                label,
                restorations,
            ),
            `P4 could not install the ${label}.${key} escape fence.`,
        );
        installedFences.push(`${label}.${key}`);
    };
    const tsSysFunctionKeys = Object.keys(ts.sys).filter(
        (key) => typeof ts.sys[key] === "function",
    );
    for (const key of tsSysFunctionKeys) installFence(ts.sys, key, "tsSys", "ts.sys");
    assert(
        installedFences.includes("ts.sys.readFile") &&
            installedFences.includes("ts.sys.fileExists") &&
            installedFences.includes("ts.sys.directoryExists"),
        "P4 could not install the ts.sys escape fence.",
    );
    for (const key of ["existsSync", "readFileSync", "readdirSync", "statSync"]) {
        installFence(fs, key, "hostFs", "fs");
    }
    for (const [target, keys, label] of [
        [http, ["get", "request"], "http"],
        [https, ["get", "request"], "https"],
        [net, ["connect", "createConnection"], "net"],
        [tls, ["connect"], "tls"],
        [dns, ["lookup", "resolve"], "dns"],
    ]) {
        for (const key of keys) {
            installFence(target, key, "network", label);
        }
    }
    for (const key of ["exec", "execFile", "fork", "spawn"]) {
        installFence(childProcess, key, "emittedJavaScriptExecutions", "child_process");
    }
    installFence(workerThreads, "Worker", "emittedJavaScriptExecutions", "worker_threads");
    if (typeof globalThis.fetch === "function") {
        installFence(globalThis, "fetch", "network", "globalThis");
    }
    assert(
        installedFences.length ===
            tsSysFunctionKeys.length + 4 + 9 + 4 + 1 +
                (typeof globalThis.fetch === "function" ? 1 : 0),
        "P4 escape-fence installation inventory drifted.",
    );
    try {
        const result = action();
        assert(
            counts.tsSys === fixture.execution.expectedTsSysCalls &&
                counts.hostFs === fixture.execution.expectedHostFsCalls &&
                counts.network === fixture.execution.expectedNetworkCalls &&
                counts.emittedJavaScriptExecutions ===
                    fixture.execution.expectedEmittedJavaScriptExecutions,
            `P4 snapshot compiler escaped authority: ${JSON.stringify(counts)}.`,
        );
        return result;
    } finally {
        for (const restore of restorations.reverse()) restore();
    }
};

const describeOutput = ([outputPath, output]) => {
    const descriptor = {
        path: outputPath,
        byteLength: Buffer.byteLength(output.text, "utf8"),
        sha256: sha256(output.text),
        sourcePaths: output.sourcePaths,
    };
    if (outputPath.endsWith(".js.map")) {
        const map = JSON.parse(output.text);
        assert(
            JSON.stringify(Object.keys(map)) ===
                JSON.stringify(["version", "file", "sourceRoot", "sources", "names", "mappings"]),
            `P4 source map metadata keys drifted for ${outputPath}.`,
        );
        descriptor.sourceMap = {
            version: map.version,
            file: map.file,
            sourceRoot: map.sourceRoot,
            sources: map.sources,
            names: map.names,
            mappingsByteLength: Buffer.byteLength(map.mappings, "utf8"),
            mappingsSha256: sha256(map.mappings),
        };
    }
    return descriptor;
};

const sourceCollectionSha256 = (testCase) => {
    const canonical = testCase.files
        .slice()
        .sort((left, right) => compareCodeUnits(left.path, right.path))
        .map(
            (file) =>
                `${file.path}\0${Buffer.byteLength(file.text, "utf8")}\0${sha256(file.text)}\n`,
        )
        .join("");
    return sha256(canonical);
};

const verifySemanticMarkers = (testCase, outputs) => {
    const markers = testCase.semanticMarkers;
    assert(
        JSON.stringify(Object.keys(markers)) ===
            JSON.stringify([
                "outputPath",
                "requiredJavaScriptSubstrings",
                "forbiddenJavaScriptSubstrings",
                "sourceMapPath",
                "sourceMapSources",
            ]),
        `${testCase.id} semantic marker schema drifted.`,
    );
    const javaScript = outputs.get(markers.outputPath);
    assert(javaScript, `${testCase.id} semantic JavaScript output is missing.`);
    for (const marker of markers.requiredJavaScriptSubstrings) {
        assert(
            javaScript.text.includes(marker),
            `${testCase.id} required JavaScript semantic marker is missing: ${marker}`,
        );
    }
    for (const marker of markers.forbiddenJavaScriptSubstrings) {
        assert(
            !javaScript.text.includes(marker),
            `${testCase.id} reached forbidden JavaScript semantic marker: ${marker}`,
        );
    }
    const sourceMapOutput = outputs.get(markers.sourceMapPath);
    assert(sourceMapOutput, `${testCase.id} semantic source map output is missing.`);
    const sourceMap = JSON.parse(sourceMapOutput.text);
    assert(
        JSON.stringify(sourceMap.sources) === JSON.stringify(markers.sourceMapSources),
        `${testCase.id} semantic source map sources drifted.`,
    );
};

const compileCase = (testCase, order, expectedOutputPaths) =>
    withEscapeFences(() => {
        const authority = fixture.authorities[testCase.authority];
        assert(authority, `${testCase.id} references an unknown authority.`);
        const declarationProfile = profileDeclarations.get(authority.declarationLayer);
        assert(declarationProfile, `${testCase.id} references an unknown declaration profile.`);
        const sourceEntries = testCase.files.map((file) => [file.path, file.text]);
        const orderedSources = order === "reversed" ? sourceEntries.slice().reverse() : sourceEntries;
        const orderedRoots =
            order === "reversed" ? testCase.rootNames.slice().reverse() : testCase.rootNames.slice();
        const { host, outputs } = createSnapshotHost(
            [...commonDeclarationEntries, ...declarationProfile.entries, ...orderedSources],
            expectedOutputPaths,
        );
        const compilerRoots = [...orderedRoots, declarationProfile.entryPath];
        const builder = ts.createIncrementalProgram({
            host,
            options: Object.freeze(JSON.parse(authority.canonicalOptionsJson)),
            rootNames: compilerRoots,
        });
        const program = builder.getProgram();
        assert(
            JSON.stringify(program.getRootFileNames()) === JSON.stringify(compilerRoots),
            `${testCase.id}/${order} root order drifted.`,
        );
        const diagnostics = ts
            .getPreEmitDiagnostics(program)
            .filter((diagnostic) => diagnostic.category === ts.DiagnosticCategory.Error);
        const diagnosticDescriptions = diagnostics.map((diagnostic) => ({
            category: diagnostic.category,
            code: diagnostic.code,
            length: diagnostic.length ?? null,
            message: ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n"),
            sourcePath: diagnostic.file?.fileName ?? null,
            start: diagnostic.start ?? null,
        }));
        assert(
            diagnosticDescriptions.length === 0,
            `${testCase.id}/${order} diagnostics: ${JSON.stringify(diagnosticDescriptions)}`,
        );
        const emit = builder.emit();
        assert(!emit.emitSkipped, `${testCase.id}/${order} unexpectedly skipped emit.`);
        verifySemanticMarkers(testCase, outputs);
        const outputDescriptions = Array.from(outputs.entries())
            .sort(([left], [right]) => compareCodeUnits(left, right))
            .map(describeOutput);
        const collectionCanonical = outputDescriptions
            .map((output) => `${output.path}\0${output.byteLength}\0${output.sha256}\n`)
            .join("");
        return {
            diagnostics: diagnosticDescriptions,
            emitSkipped: emit.emitSkipped,
            outputCollectionSha256: sha256(collectionCanonical),
            outputs: outputDescriptions,
        };
    });

const expectedCaseIds = syntaxDefinitions.flatMap(([syntax]) => [
    `rhino-${syntax}`,
    `node-${syntax}`,
]);
assert(
    fixture.cases.length === 14 &&
        JSON.stringify(fixture.cases.map((testCase) => testCase.id)) ===
            JSON.stringify(expectedCaseIds),
    "P4 case inventory drifted.",
);

for (const testCase of fixture.cases) {
    const filePaths = testCase.files.map((file) => file.path);
    const sortedFilePaths = filePaths.slice().sort(compareCodeUnits);
    const sortedRootNames = testCase.rootNames.slice().sort(compareCodeUnits);
    assert(
        testCase.id === `${testCase.authority}-${testCase.syntax}` &&
            syntaxDefinitions.some(([syntax]) => syntax === testCase.syntax) &&
            testCase.files.length === 2 &&
            testCase.rootNames.length === 2 &&
            new Set(filePaths).size === 2 &&
            new Set(testCase.rootNames).size === 2 &&
            filePaths.every(
                (filePath) =>
                    filePath.startsWith("/sources/") &&
                    filePath.endsWith(".ts") &&
                    !filePath.endsWith(".d.ts"),
            ) &&
            JSON.stringify(sortedRootNames) === JSON.stringify(sortedFilePaths),
        `${testCase.id} syntax/profile/source inventory drifted.`,
    );
    assert(
        sourceCollectionSha256(testCase) === testCase.sourceCollectionSha256,
        `${testCase.id} source collection identity drifted.`,
    );
    assert(testCase.expected !== null, `${testCase.id} compiler-syntax golden is unsealed.`);
    const expectedOutputPaths = new Set(
        testCase.expected.outputs.map((output) => output.path),
    );
    const sourceOutputPaths = filePaths
        .flatMap((filePath) => {
            const relativePath = filePath.slice("/sources/".length, -".ts".length);
            return [`/outputs/${relativePath}.js`, `/outputs/${relativePath}.js.map`];
        })
        .sort(compareCodeUnits);
    assert(
        testCase.expected.diagnostics.length === 0 &&
            testCase.expected.emitSkipped === false &&
            testCase.expected.outputs.length === 4 &&
            testCase.expected.outputs.filter((output) => output.path.endsWith(".js")).length ===
                2 &&
            testCase.expected.outputs.filter((output) => output.path.endsWith(".js.map"))
                .length === 2 &&
            JSON.stringify(Array.from(expectedOutputPaths).sort(compareCodeUnits)) ===
                JSON.stringify(sourceOutputPaths),
        `${testCase.id} exact two-module/four-output golden shape drifted.`,
    );
    let baseline = null;
    for (const order of fixture.execution.inputOrders) {
        for (let repetition = 0; repetition < fixture.execution.repetitionsPerOrder; repetition += 1) {
            const actual = compileCase(testCase, order, expectedOutputPaths);
            if (baseline === null) baseline = actual;
            assert(
                JSON.stringify(actual) === JSON.stringify(baseline),
                `${testCase.id} changed under ${order} order, repetition ${repetition + 1}.`,
            );
        }
    }
    assert(
        JSON.stringify(baseline) === JSON.stringify(testCase.expected),
        `${testCase.id} exact compiler-syntax golden drifted.`,
    );
}

process.stdout.write(
    `R2-M2-P4 compiler-syntax golden passed (TypeScript ${expectedVersion}; ` +
        "syntax=7, profiles=2, cases=14, compiles=56, " +
        "ts.sys/host-fs/network/output execution=0).\n",
);
