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
        "Usage: node verify-r2-m2-p5-compiler-boundaries.js " +
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
const assertValidScalarString = (value, label) => {
    for (let index = 0; index < value.length; index += 1) {
        const codeUnit = value.charCodeAt(index);
        if (codeUnit >= 0xd800 && codeUnit <= 0xdbff) {
            assert(index + 1 < value.length, `${label} ends with a lone high surrogate.`);
            const low = value.charCodeAt(index + 1);
            assert(
                low >= 0xdc00 && low <= 0xdfff,
                `${label} contains a lone high surrogate.`,
            );
            index += 1;
        } else {
            assert(
                codeUnit < 0xdc00 || codeUnit > 0xdfff,
                `${label} contains a lone low surrogate.`,
            );
        }
    }
};
const assertValidScalarTree = (value, label = "fixture") => {
    if (typeof value === "string") {
        assertValidScalarString(value, label);
    } else if (Array.isArray(value)) {
        value.forEach((item, index) => assertValidScalarTree(item, `${label}[${index}]`));
    } else if (value !== null && typeof value === "object") {
        for (const [key, item] of Object.entries(value)) {
            assertValidScalarString(key, `${label} key`);
            assertValidScalarTree(item, `${label}.${key}`);
        }
    }
};
const normalizeVfsPath = (value) => {
    const normalized = path.posix.normalize(String(value).replaceAll("\\", "/"));
    assert(
        normalized.startsWith("/") && normalized !== "/.." && !normalized.startsWith("/../"),
        `P5 VFS path escaped its snapshot: ${value}`,
    );
    return normalized;
};
const readUtf8 = (fileName) => fs.readFileSync(fileName, "utf8");

assertValidScalarTree(fixture);
assert(ts.version === expectedVersion, `Expected TypeScript ${expectedVersion}, received ${ts.version}.`);
assert(
    fixture.schema === "autojs6.typescript.r2-m2-p5.compiler-boundaries" &&
        fixture.revision === 1 &&
        fixture.typescriptVersion === expectedVersion &&
        fixture.scope === "desktop-compiler-only/no-production-admission",
    "P5 fixture identity or compiler-only scope drifted.",
);
assert(
    JSON.stringify(fixture.execution.inputOrders) === JSON.stringify(["declared", "reversed"]) &&
        fixture.execution.repetitionsPerOrder === 2 &&
        fixture.execution.expectedHostFsCalls === 0 &&
        fixture.execution.expectedTsSysCalls === 0 &&
        fixture.execution.expectedNetworkCalls === 0 &&
        fixture.execution.expectedEmittedJavaScriptExecutions === 0 &&
        fixture.execution.expectedCompiles === 48 &&
        fixture.execution.expectedCollisionRejections === 8 &&
        fixture.execution.expectedTokenOverflowRejections === 8 &&
        fixture.execution.sourceCollectionCanonical ===
            "sorted files; UTF-8(path + NUL + decimal byteLength + NUL + lowercase sha256 + LF)" &&
        fixture.execution.outputCollectionCanonical ===
            "sorted outputs; UTF-8(path + NUL + decimal byteLength + NUL + lowercase sha256 + LF)",
    "P5 deterministic execution authority drifted.",
);

const boundaryDefinitions = [
    [
        "chinese-path",
        "chinese-path",
        "direct-compiler-positive/production-printable-ascii-negative",
    ],
    [
        "space-path",
        "space-path",
        "direct-compiler-positive/production-ordinary-source-compatible",
    ],
    ["crlf", "crlf", "raw-crlf-identity/emitted-lf"],
    ["leading-bom", "bom", "raw-leading-bom-identity/emitted-no-bom"],
    ["unicode-content", "unicode", "valid-scalars-opaque/no-normalization"],
    ["high-token", "high-token", "65536-inclusive/65537-pre-program-negative"],
    [
        "ascii-case-fold-collision",
        "canonical-collision",
        "ascii-a-z-fold/pre-program-negative",
    ],
];
assert(
    JSON.stringify(fixture.boundaryMatrix) ===
        JSON.stringify(
            boundaryDefinitions.map(([boundary, caseSuffix, oracleContract]) => ({
                boundary,
                caseSuffix,
                profiles: ["rhino", "node"],
                oracleContract,
            })),
        ),
    "P5 boundary/profile/oracle matrix drifted.",
);

const generator = fixture.generatorAuthority;
assert(
    JSON.stringify(Object.keys(generator)) ===
        JSON.stringify([
            "kind",
            "line",
            "repetitions",
            "renderedUtf8ByteLength",
            "renderedSha256",
            "tokensPerRepetition",
            "acceptedTokenCount",
            "overflowSuffix",
            "overflowTokenCount",
            "maximumProjectTokens",
            "profileAggregateTokenCounts",
            "overflowExpected",
        ]) &&
        generator.kind === "repeated-interface-v1" &&
        generator.line === "interface Boundary{}\r\n" &&
        generator.repetitions === 16_384 &&
        generator.renderedUtf8ByteLength === 360_448 &&
        generator.renderedSha256 ===
            "f55b81564428621c455a2860f5ab99a30827ace8089e5a3118b1af35ecbae26b" &&
        generator.tokensPerRepetition === 4 &&
        generator.acceptedTokenCount === 65_536 &&
        generator.overflowSuffix === ";\r\n" &&
        generator.overflowTokenCount === 65_537 &&
        generator.maximumProjectTokens === 262_144 &&
        JSON.stringify(generator.profileAggregateTokenCounts) ===
            JSON.stringify({ rhino: 152_036, node: 191_040 }) &&
        JSON.stringify(generator.overflowExpected) ===
            JSON.stringify({
                outcome: "rejected",
                stage: "pre-program",
                code: "ORACLE_FILE_TOKEN_LIMIT_EXCEEDED",
                programCreated: false,
                diagnostics: [],
                outputs: [],
                compilerApiCalls: 0,
                outputWrites: 0,
            }),
    "P5 high-token generator authority drifted.",
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
    "P5 compiler-option authority inventory drifted.",
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
        `${profile} P5 frozen profile authority drifted.`,
    );
    const options = JSON.parse(authority.canonicalOptionsJson);
    assert(
        JSON.stringify(options) === authority.canonicalOptionsJson &&
            options.allowJs === false &&
            options.target === ts.ScriptTarget.ES2018 &&
            options.sourceMap === true &&
            options.noEmitOnError === true &&
            !Object.prototype.hasOwnProperty.call(options, "experimentalDecorators"),
        `${profile} P5 frozen compiler options or standard decorator semantics drifted.`,
    );
}

const createSnapshotHost = (entries, expectedOutputPaths) => {
    const files = new Map();
    for (const [fileName, text] of entries) {
        const normalized = normalizeVfsPath(fileName);
        assert(!files.has(normalized), `P5 snapshot contains duplicate path: ${normalized}`);
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
        writeFile: (fileName, text, bom, _onError, sourceFilesForOutput) => {
            const normalized = normalizeVfsPath(fileName);
            assert(
                expectedOutputPaths === null || expectedOutputPaths.has(normalized),
                `P5 emitted unauthorized output: ${normalized}`,
            );
            assert(bom === false, `P5 emitted an unauthorized BOM for ${normalized}.`);
            assert(!outputs.has(normalized), `P5 emitted duplicate output: ${normalized}`);
            assertValidScalarString(text, `P5 output ${normalized}`);
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
            throw new Error(`P5 reached forbidden ${label}.${key}.`);
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
            `P5 could not install the ${label}.${key} escape fence.`,
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
        "P5 could not install the ts.sys escape fence.",
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
        "P5 escape-fence installation inventory drifted.",
    );
    try {
        const result = action();
        assert(
            counts.tsSys === fixture.execution.expectedTsSysCalls &&
                counts.hostFs === fixture.execution.expectedHostFsCalls &&
                counts.network === fixture.execution.expectedNetworkCalls &&
                counts.emittedJavaScriptExecutions ===
                    fixture.execution.expectedEmittedJavaScriptExecutions,
            `P5 snapshot compiler escaped authority: ${JSON.stringify(counts)}.`,
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
            `P5 source map metadata keys drifted for ${outputPath}.`,
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

const materializeFile = (file) => {
    assert(
        JSON.stringify(Object.keys(file)) ===
            (Object.prototype.hasOwnProperty.call(file, "text")
                ? JSON.stringify(["path", "text"])
                : JSON.stringify(["path", "generator"])),
        `P5 file descriptor shape drifted for ${file.path}.`,
    );
    if (Object.prototype.hasOwnProperty.call(file, "text")) {
        return { path: file.path, text: file.text };
    }
    assert(
        JSON.stringify(file.generator) === JSON.stringify({ kind: "repeated-interface-v1" }),
        `P5 source generator drifted for ${file.path}.`,
    );
    const text = generator.line.repeat(generator.repetitions);
    assert(
        Buffer.byteLength(text, "utf8") === generator.renderedUtf8ByteLength &&
            sha256(text) === generator.renderedSha256,
        `P5 rendered generator identity drifted for ${file.path}.`,
    );
    return { path: file.path, text };
};

const materializeFiles = (testCase) => testCase.files.map(materializeFile);

const sourceCollectionSha256 = (files) => {
    const canonical = files
        .slice()
        .sort((left, right) => compareCodeUnits(left.path, right.path))
        .map(
            (file) =>
                `${file.path}\0${Buffer.byteLength(file.text, "utf8")}\0${sha256(file.text)}\n`,
        )
        .join("");
    return sha256(canonical);
};

const countSourceTokens = (text) => {
    const scanner = ts.createScanner(
        ts.ScriptTarget.ES2018,
        true,
        ts.LanguageVariant.Standard,
        text,
    );
    let count = 0;
    while (scanner.scan() !== ts.SyntaxKind.EndOfFileToken) count += 1;
    return count;
};

const requireFileTokenLimit = (text) => {
    const tokenCount = countSourceTokens(text);
    if (tokenCount > generator.acceptedTokenCount) {
        const error = new Error("P5 source file exceeds the oracle token limit.");
        error.code = "ORACLE_FILE_TOKEN_LIMIT_EXCEEDED";
        error.tokenCount = tokenCount;
        throw error;
    }
    return tokenCount;
};

const asciiCaseFold = (value) =>
    Array.from(value, (character) =>
        character >= "A" && character <= "Z"
            ? String.fromCharCode(character.charCodeAt(0) + 0x20)
            : character,
    ).join("");

const verifyCanonicalCollisionRejection = (testCase, files) => {
    const paths = files.map((file) => file.path);
    assert(
        paths.every(
            (filePath) =>
                Array.from(filePath).every(
                    (character) => character.charCodeAt(0) >= 0x20 && character.charCodeAt(0) <= 0x7e,
                ) &&
                filePath.split("/").every((segment, index) =>
                    index === 0 || (segment.length > 0 && segment !== "." && segment !== ".."),
                ),
        ),
        `${testCase.id} collision path escaped printable ASCII canonical authority.`,
    );
    assert(
        paths[0] !== paths[1] && asciiCaseFold(paths[0]) === asciiCaseFold(paths[1]),
        `${testCase.id} does not contain an ASCII case-fold collision.`,
    );
    const actual = {
        outcome: "rejected",
        stage: "pre-program",
        code: "ORACLE_ASCII_CASE_FOLD_COLLISION",
        programCreated: false,
        diagnostics: [],
        outputs: [],
        compilerApiCalls: 0,
        outputWrites: 0,
    };
    assert(
        JSON.stringify(actual) === JSON.stringify(testCase.expected),
        `${testCase.id} collision rejection contract drifted.`,
    );
};

const verifyTokenOverflowRejection = (testCase, files) => {
    const generated = files.find((file) =>
        testCase.files.some(
            (descriptor) => descriptor.path === file.path && descriptor.generator?.kind === generator.kind,
        ),
    );
    assert(generated, `${testCase.id} has no generated high-token source.`);
    assert(
        requireFileTokenLimit(generated.text) === generator.acceptedTokenCount,
        `${testCase.id} accepted token count drifted.`,
    );
    const overflowText = `${generated.text}${generator.overflowSuffix}`;
    let compilerApiCalls = 0;
    let outputWrites = 0;
    let rejection = null;
    try {
        requireFileTokenLimit(overflowText);
        compilerApiCalls += 1;
        outputWrites += 1;
    } catch (error) {
        rejection = error;
    }
    assert(
        rejection?.code === "ORACLE_FILE_TOKEN_LIMIT_EXCEEDED" &&
            rejection.tokenCount === generator.overflowTokenCount,
        `${testCase.id} did not produce the exact token-overflow rejection.`,
    );
    const actual = {
        outcome: "rejected",
        stage: "pre-program",
        code: rejection.code,
        programCreated: false,
        diagnostics: [],
        outputs: [],
        compilerApiCalls,
        outputWrites,
    };
    assert(
        JSON.stringify(actual) === JSON.stringify(generator.overflowExpected),
        `${testCase.id} ORACLE_FILE_TOKEN_LIMIT_EXCEEDED boundary drifted.`,
    );
};

const verifyProjectTokenAggregate = (testCase, files) => {
    const authority = fixture.authorities[testCase.authority];
    const declarationProfile = profileDeclarations.get(authority.declarationLayer);
    const total = [
        ...commonDeclarationEntries,
        ...declarationProfile.entries,
        ...files.map((file) => [file.path, file.text]),
    ].reduce((sum, [_filePath, text]) => sum + countSourceTokens(text), 0);
    assert(
        total === generator.profileAggregateTokenCounts[testCase.authority] &&
            total <= generator.maximumProjectTokens,
        `${testCase.id} project token aggregate drifted: ${total}.`,
    );
};

const verifyBoundaryAssertions = (testCase, files, outputs) => {
    const javaScriptOutputs = Array.from(outputs.entries()).filter(([name]) =>
        name.endsWith(".js"),
    );
    const sourceMapOutputs = Array.from(outputs.entries()).filter(([name]) =>
        name.endsWith(".js.map"),
    );
    assert(
        javaScriptOutputs.length === 2 && sourceMapOutputs.length === 2,
        `${testCase.id} did not emit exactly two JavaScript modules and two maps.`,
    );
    for (const [mapPath, output] of sourceMapOutputs) {
        const sourceMap = JSON.parse(output.text);
        assert(output.sourcePaths.length === 1, `${testCase.id} map source identity drifted.`);
        const expectedSource = path.posix.relative(
            path.posix.dirname(mapPath),
            output.sourcePaths[0],
        );
        assert(
            JSON.stringify(sourceMap.sources) === JSON.stringify([expectedSource]),
            `${testCase.id} Source Map path identity drifted.`,
        );
    }
    switch (testCase.boundary) {
        case "chinese-path":
            assert(
                testCase.assertions.directCompilerUnicodePath === true &&
                    testCase.assertions.productionPrintableAsciiAdmission === false &&
                    files.some((file) => /[^\x20-\x7e]/u.test(file.path)) &&
                    Array.from(outputs.keys()).some((outputPath) => /[^\x20-\x7e]/u.test(outputPath)),
                `${testCase.id} Unicode-path split authority drifted.`,
            );
            break;
        case "space-path":
            assert(
                testCase.assertions.productionOrdinarySourceCompatible === true &&
                    files.every((file) => file.path.includes(" ")) &&
                    Array.from(outputs.keys()).every((outputPath) => outputPath.includes(" ")),
                `${testCase.id} space-path identity drifted.`,
            );
            break;
        case "crlf":
            assert(
                testCase.assertions.rawInputLineEnding === "CRLF" &&
                    testCase.assertions.emittedLineEnding === "LF" &&
                    files.every((file) => file.text.includes("\r\n")) &&
                    Array.from(outputs.values()).every((output) => !output.text.includes("\r")),
                `${testCase.id} CRLF-to-LF boundary drifted.`,
            );
            break;
        case "leading-bom":
            assert(
                testCase.assertions.leadingBomCodePoint === 0xfeff &&
                    testCase.assertions.emittedBom === false &&
                    files.every((file) => file.text.charCodeAt(0) === 0xfeff) &&
                    Array.from(outputs.values()).every((output) => !output.text.startsWith("\ufeff")),
                `${testCase.id} leading-BOM boundary drifted.`,
            );
            break;
        case "unicode-content":
            assert(
                testCase.assertions.contentNormalization === "opaque" &&
                    testCase.assertions.requiredSourceSubstrings.every((marker) =>
                        files.some((file) => file.text.includes(marker)),
                    ),
                `${testCase.id} Unicode-content identity drifted.`,
            );
            break;
        case "high-token":
            assert(
                testCase.assertions.acceptedTokenCount === generator.acceptedTokenCount &&
                    testCase.assertions.overflowTokenCount === generator.overflowTokenCount,
                `${testCase.id} high-token assertion drifted.`,
            );
            break;
        default:
            throw new Error(`${testCase.id} reached unknown positive boundary ${testCase.boundary}.`);
    }
};

const compileCase = (testCase, files, order, expectedOutputPaths) =>
    withEscapeFences(() => {
        const authority = fixture.authorities[testCase.authority];
        assert(authority, `${testCase.id} references an unknown authority.`);
        const declarationProfile = profileDeclarations.get(authority.declarationLayer);
        assert(declarationProfile, `${testCase.id} references an unknown declaration profile.`);
        const sourceEntries = files.map((file) => [file.path, file.text]);
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
        verifyBoundaryAssertions(testCase, files, outputs);
        const outputDescriptions = Array.from(outputs.entries())
            .sort(([left], [right]) => compareCodeUnits(left, right))
            .map(describeOutput);
        const collectionCanonical = outputDescriptions
            .map((output) => `${output.path}\0${output.byteLength}\0${output.sha256}\n`)
            .join("");
        return {
            outcome: "compiled",
            diagnostics: diagnosticDescriptions,
            emitSkipped: emit.emitSkipped,
            outputCollectionSha256: sha256(collectionCanonical),
            outputs: outputDescriptions,
        };
    });

const expectedCaseIds = boundaryDefinitions.flatMap(([_boundary, caseSuffix]) => [
    `rhino-${caseSuffix}`,
    `node-${caseSuffix}`,
]);
assert(
    fixture.cases.length === 14 &&
        JSON.stringify(fixture.cases.map((testCase) => testCase.id)) ===
            JSON.stringify(expectedCaseIds),
    "P5 case inventory drifted.",
);

let positiveCases = 0;
let compiles = 0;
let collisionRejections = 0;
let tokenOverflowRejections = 0;
for (const testCase of fixture.cases) {
    const files = materializeFiles(testCase);
    const filePaths = files.map((file) => file.path);
    const sortedFilePaths = filePaths.slice().sort(compareCodeUnits);
    const sortedRootNames = testCase.rootNames.slice().sort(compareCodeUnits);
    const definition = boundaryDefinitions.find(([boundary]) => boundary === testCase.boundary);
    assert(
        definition &&
            testCase.id === `${testCase.authority}-${definition[1]}` &&
            ["rhino", "node"].includes(testCase.authority) &&
            files.length === 2 &&
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
        `${testCase.id} boundary/profile/source inventory drifted.`,
    );
    assertValidScalarTree(files, `${testCase.id}.materializedFiles`);
    const actualSourceCollectionSha256 = sourceCollectionSha256(files);
    assert(
        typeof testCase.sourceCollectionSha256 === "string" &&
            actualSourceCollectionSha256 === testCase.sourceCollectionSha256,
        `${testCase.id} source collection identity drifted.`,
    );
    if (testCase.boundary === "ascii-case-fold-collision") {
        for (const order of fixture.execution.inputOrders) {
            for (
                let repetition = 0;
                repetition < fixture.execution.repetitionsPerOrder;
                repetition += 1
            ) {
                const orderedFiles = order === "reversed" ? files.slice().reverse() : files;
                verifyCanonicalCollisionRejection(testCase, orderedFiles);
                collisionRejections += 1;
            }
        }
        continue;
    }

    positiveCases += 1;
    const sourceOutputPaths = filePaths
        .flatMap((filePath) => {
            const relativePath = filePath.slice("/sources/".length, -".ts".length);
            return [`/outputs/${relativePath}.js`, `/outputs/${relativePath}.js.map`];
        })
        .sort(compareCodeUnits);
    const expectedOutputPaths = new Set(
        testCase.expected.outputs.map((output) => output.path),
    );
    if (testCase.boundary === "high-token") {
        verifyProjectTokenAggregate(testCase, files);
        for (const order of fixture.execution.inputOrders) {
            for (
                let repetition = 0;
                repetition < fixture.execution.repetitionsPerOrder;
                repetition += 1
            ) {
                const orderedFiles = order === "reversed" ? files.slice().reverse() : files;
                verifyTokenOverflowRejection(testCase, orderedFiles);
                tokenOverflowRejections += 1;
            }
        }
    }
    assert(
        testCase.expected.outcome === "compiled" &&
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
            const actual = compileCase(testCase, files, order, expectedOutputPaths);
            compiles += 1;
            if (baseline === null) baseline = actual;
            assert(
                JSON.stringify(actual) === JSON.stringify(baseline),
                `${testCase.id} changed under ${order} order, repetition ${repetition + 1}.`,
            );
        }
    }
    assert(
        JSON.stringify(baseline) === JSON.stringify(testCase.expected),
        `${testCase.id} exact compiler-boundary golden drifted.`,
    );
}

assert(
    positiveCases === 12 &&
        compiles === fixture.execution.expectedCompiles &&
        collisionRejections === fixture.execution.expectedCollisionRejections &&
        tokenOverflowRejections === fixture.execution.expectedTokenOverflowRejections,
    "P5 execution/rejection inventory drifted.",
);

process.stdout.write(
    `R2-M2-P5 compiler-boundary golden passed (TypeScript ${expectedVersion}; ` +
        "kinds=7, profiles=2, rows=14, positive=12, compiles=48, " +
        "collision-rejections=8, token-overflow-rejections=8, " +
        "ts.sys/host-fs/network/output execution=0).\n",
);
