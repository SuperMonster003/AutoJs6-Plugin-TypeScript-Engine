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
        "Usage: node verify-r2-m2-p3-compiler-bundle.js " +
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
const sha256 = (value) =>
    crypto.createHash("sha256").update(value, "utf8").digest("hex");
const normalizeVfsPath = (value) => {
    const normalized = path.posix.normalize(String(value).replaceAll("\\", "/"));
    assert(
        normalized.startsWith("/") && normalized !== "/.." && !normalized.startsWith("/../"),
        `P3 VFS path escaped its snapshot: ${value}`,
    );
    return normalized;
};
const readUtf8 = (fileName) => fs.readFileSync(fileName, "utf8");

assert(ts.version === expectedVersion, `Expected TypeScript ${expectedVersion}, received ${ts.version}.`);
assert(
    fixture.schema === "autojs6.typescript.r2-m2-p3.compiler-bundle" &&
        fixture.revision === 1 &&
        fixture.typescriptVersion === expectedVersion &&
        fixture.scope === "desktop-compiler-only/no-production-admission",
    "P3 fixture identity or compiler-only scope drifted.",
);
assert(
    JSON.stringify(fixture.execution.inputOrders) === JSON.stringify(["declared", "reversed"]) &&
        fixture.execution.repetitionsPerOrder === 2 &&
        fixture.execution.expectedHostFsCalls === 0 &&
        fixture.execution.expectedTsSysCalls === 0 &&
        fixture.execution.expectedNetworkCalls === 0 &&
        fixture.execution.expectedEmittedJavaScriptExecutions === 0 &&
        fixture.execution.outputCollectionCanonical ===
            "sorted outputs; UTF-8(path + NUL + decimal byteLength + NUL + lowercase sha256 + LF)",
    "P3 deterministic execution authority drifted.",
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

const expectedAuthorityNames = ["mixed", "node", "rhino"];
assert(
    JSON.stringify(Object.keys(fixture.authorities).sort(compareCodeUnits)) ===
        JSON.stringify(expectedAuthorityNames),
    "P3 compiler-option authority inventory drifted.",
);
assert(
    fixture.authorities.rhino.targetProfile === "rhino" &&
        fixture.authorities.rhino.profileRevision === 1 &&
        fixture.authorities.rhino.declarationLayer === "rhino" &&
        fixture.authorities.node.targetProfile === "node" &&
        fixture.authorities.node.profileRevision === 1 &&
        fixture.authorities.node.declarationLayer === "node" &&
        fixture.authorities.mixed.targetProfile === "compiler-only-mixed" &&
        fixture.authorities.mixed.declarationLayer === "rhino",
    "P3 frozen profile or oracle declaration authority drifted.",
);
for (const [name, authority] of Object.entries(fixture.authorities)) {
    assert(
        sha256(authority.canonicalOptionsJson) === authority.optionsSha256,
        `${name} compiler-option authority digest drifted.`,
    );
    assert(
        authority.compilerApi === "createIncrementalProgram",
        `${name} compiler API authority drifted.`,
    );
    const options = JSON.parse(authority.canonicalOptionsJson);
    assert(
        JSON.stringify(options) === authority.canonicalOptionsJson,
        `${name} compiler options are not canonical JSON.`,
    );
    if (name === "mixed") {
        assert(
            authority.kind === "oracle-local" &&
                authority.productionAdmission === false &&
                authority.profileRevision === null &&
                options.allowJs === true &&
                options.checkJs === false &&
                options.resolveJsonModule === true,
            "Mixed-source oracle crossed into a production profile.",
        );
    } else {
        assert(
            authority.kind === "frozen-profile" &&
                authority.productionAdmission === "unchanged" &&
                options.allowJs === false &&
                !Object.prototype.hasOwnProperty.call(options, "resolveJsonModule"),
            `${name} frozen profile options drifted.`,
        );
    }
}

const createSnapshotHost = (entries, expectedOutputPaths) => {
    const files = new Map();
    for (const [fileName, text] of entries) {
        const normalized = normalizeVfsPath(fileName);
        assert(!files.has(normalized), `P3 snapshot contains duplicate path: ${normalized}`);
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
                `P3 emitted unauthorized output: ${normalized}`,
            );
            assert(!outputs.has(normalized), `P3 emitted duplicate output: ${normalized}`);
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
            throw new Error(`P3 reached forbidden ${label}.${key}.`);
        },
    });
    restorations.push(() => Object.defineProperty(target, key, descriptor));
    return true;
};

const withEscapeFences = (action) => {
    const counts = { emittedJavaScriptExecutions: 0, hostFs: 0, network: 0, tsSys: 0 };
    const restorations = [];
    const patchedTsSys = Object.keys(ts.sys).filter((key) =>
        replaceFunction(ts.sys, key, { get count() { return counts.tsSys; }, set count(value) { counts.tsSys = value; } }, "ts.sys", restorations),
    );
    assert(
        patchedTsSys.includes("readFile") &&
            patchedTsSys.includes("fileExists") &&
            patchedTsSys.includes("directoryExists"),
        "P3 could not install the ts.sys escape fence.",
    );
    for (const key of ["existsSync", "readFileSync", "readdirSync", "statSync"]) {
        replaceFunction(fs, key, { get count() { return counts.hostFs; }, set count(value) { counts.hostFs = value; } }, "fs", restorations);
    }
    for (const [target, keys, label] of [
        [http, ["get", "request"], "http"],
        [https, ["get", "request"], "https"],
        [net, ["connect", "createConnection"], "net"],
        [tls, ["connect"], "tls"],
        [dns, ["lookup", "resolve"], "dns"],
    ]) {
        for (const key of keys) {
            replaceFunction(target, key, { get count() { return counts.network; }, set count(value) { counts.network = value; } }, label, restorations);
        }
    }
    for (const key of ["exec", "execFile", "fork", "spawn"]) {
        replaceFunction(childProcess, key, { get count() { return counts.emittedJavaScriptExecutions; }, set count(value) { counts.emittedJavaScriptExecutions = value; } }, "child_process", restorations);
    }
    replaceFunction(workerThreads, "Worker", { get count() { return counts.emittedJavaScriptExecutions; }, set count(value) { counts.emittedJavaScriptExecutions = value; } }, "worker_threads", restorations);
    if (typeof globalThis.fetch === "function") {
        replaceFunction(globalThis, "fetch", { get count() { return counts.network; }, set count(value) { counts.network = value; } }, "globalThis", restorations);
    }
    try {
        const result = action();
        assert(
            counts.tsSys === fixture.execution.expectedTsSysCalls &&
                counts.hostFs === fixture.execution.expectedHostFsCalls &&
                counts.network === fixture.execution.expectedNetworkCalls &&
                counts.emittedJavaScriptExecutions ===
                    fixture.execution.expectedEmittedJavaScriptExecutions,
            `P3 snapshot compiler escaped authority: ${JSON.stringify(counts)}.`,
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
            `P3 source map metadata keys drifted for ${outputPath}.`,
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
            `${testCase.id}/${order} diagnostics: ${JSON.stringify(
                diagnosticDescriptions,
            )}`,
        );
        const emit = builder.emit();
        assert(!emit.emitSkipped, `${testCase.id}/${order} unexpectedly skipped emit.`);
        const outputDescriptions = Array.from(outputs.entries())
            .sort(([left], [right]) => compareCodeUnits(left, right))
            .map(describeOutput);
        const collectionCanonical = outputDescriptions
            .map(
                (output) =>
                    `${output.path}\0${output.byteLength}\0${output.sha256}\n`,
            )
            .join("");
        return {
            diagnostics: diagnosticDescriptions,
            emitSkipped: emit.emitSkipped,
            outputCollectionSha256: sha256(collectionCanonical),
            outputs: outputDescriptions,
        };
    });

assert(
    fixture.cases.length === 5 &&
        JSON.stringify(fixture.cases.map((testCase) => testCase.id)) ===
            JSON.stringify([
                "rhino-two-file-import",
                "node-two-file-import",
                "rhino-source-cycle",
                "node-source-cycle",
                "mixed-ts-js-json",
            ]),
    "P3 case inventory drifted.",
);

for (const testCase of fixture.cases) {
    assert(testCase.expected !== null, `${testCase.id} compiler-bundle golden is unsealed.`);
    const expectedOutputPaths = new Set(
        testCase.expected.outputs.map((output) => output.path),
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
        `${testCase.id} exact compiler-bundle golden drifted.`,
    );
}

process.stdout.write(
    `R2-M2-P3 compiler-bundle golden passed (TypeScript ${expectedVersion}; ` +
        "cases=5, compiles=20, ts.sys/host-fs/network/output execution=0).\n",
);
