"use strict";

const cryptoModule = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const {
    CURRENT_ORDINARY_INPUT_QUOTA_REVISION,
    projectInputAuthority,
    record: inputRecord,
} = require("./r2-input-archive-authority.js");

const [
    workerArgument,
    compilerArgument,
    policyArgument,
    effectiveOptionsArgument,
    expectedVersion,
] = process.argv.slice(2);
if (
    process.argv.slice(2).length !== 5 ||
    !workerArgument ||
    !compilerArgument ||
    !policyArgument ||
    !effectiveOptionsArgument ||
    !expectedVersion
) {
    throw new Error(
        "Usage: node verify-t1-controlled-path-aliases.js " +
            "<worker.js> <typescript.js> <policy-v4.json> " +
            "<effective-options-v4.json> <expected-version>",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const policy = JSON.parse(fs.readFileSync(path.resolve(policyArgument), "utf8"));
const fixture = JSON.parse(
    fs.readFileSync(path.resolve(effectiveOptionsArgument), "utf8"),
);
const workerSource = fs.readFileSync(workerPath, "utf8");
const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;
const sorted = (values) => values.slice().sort(compareCodeUnits);
const exactJson = (actual, expected) =>
    JSON.stringify(actual) === JSON.stringify(expected);
const exactKeys = (value, expected) =>
    exactJson(sorted(Object.keys(value)), sorted(expected));
const sha256 = (value) =>
    cryptoModule.createHash("sha256").update(value).digest("hex");

Object.defineProperty(global, "crypto", {
    configurable: true,
    value: cryptoModule.webcrypto,
});
global.ts = require(compilerPath);
global.importScripts = (specifier) => {
    assert(specifier === "typescript.js", "Worker imported an unexpected script.");
};
const messages = [];
const observedMessages = [];
global.self = {
    postMessage: (message) => {
        messages.push(message);
        observedMessages.push(message);
    },
};
vm.runInThisContext(workerSource, { filename: workerPath });
assert(
    messages.length === 1 &&
        messages[0].kind === "ready" &&
        messages[0].tsVersion === expectedVersion &&
        ts.version === expectedVersion,
    "T1-3 verifier did not load the pinned TypeScript Worker.",
);
messages.length = 0;

const workerBinding = (expression) =>
    vm.runInThisContext(expression, {
        filename: `${workerPath}#t1-controlled-path-aliases-${expression}`,
    });
const dispatch = async (message) => {
    messages.length = 0;
    await self.onmessage({ data: message });
    return messages.slice();
};

const canonicalPolicyInput = () => {
    const alias = policy.pathAliases;
    return (
        "autojs6.typescript.controlled-tsconfig-policy.v4" +
        `|revision=${policy.revision}` +
        `|parentRevision=${policy.parentRevision}` +
        `|parentFingerprint=${policy.parentFingerprint}` +
        `|allowedValue=${sorted(policy.allowedValueCompilerOptions).join(",")}` +
        `|escapeForbidden=${sorted(policy.escapeForbiddenCompilerOptions).join(",")}` +
        `|pathAliasBaseUrlDefault=${alias.baseUrlDefault}` +
        `|pathAliasResolutionDomain=${alias.resolutionDomain}` +
        `|pathAliasMatching=${alias.matching}` +
        `|pathAliasEmitRewrite=${alias.emitRewrite}` +
        `|maxPathAliases=${alias.maximumAliases}` +
        `|maxPathTargetsPerAlias=${alias.maximumTargetsPerAlias}` +
        `|maxPathTargets=${alias.maximumTargets}` +
        `|maxPathPatternUtf8Bytes=${alias.maximumPatternUtf8Bytes}` +
        `|baseUrlGrammar=${alias.baseUrlGrammar}` +
        `|pathKeyGrammar=${alias.keyGrammar}` +
        `|pathTargetGrammar=${alias.targetGrammar}` +
        `|wildcardCardinality=${alias.wildcardCardinality}` +
        `|canonicalOrdering=${alias.canonicalOrdering}` +
        `|emptyPaths=${alias.emptyPaths}`
    );
};

const revision4Authority = workerBinding(
    "controlledTsConfigRevision4Authority",
);
const revision3Authority = workerBinding(
    "controlledTsConfigRevision3Authority",
);
assert(
    policy.schema === "autojs6.typescript.controlled-tsconfig-policy" &&
        policy.revision === 4 &&
        policy.parentRevision === 3 &&
        revision4Authority.configPolicyRevision === policy.revision &&
        revision4Authority.configPolicyFingerprint === policy.fingerprint &&
        revision3Authority.configPolicyRevision === policy.parentRevision &&
        revision3Authority.configPolicyFingerprint === policy.parentFingerprint &&
        fixture.schema ===
            "autojs6.typescript.controlled-tsconfig-effective-options" &&
        fixture.revision === 4 &&
        fixture.profiles.length === 2 &&
        fixture.rejected.length === 4 &&
        sha256(Buffer.from(canonicalPolicyInput(), "ascii")) ===
            policy.fingerprint,
    "T1-3 revision-4 policy authority drifted.",
);

const parseConfig = (source, targetProfile) =>
    workerBinding("parseControlledTsConfig")(
        source,
        revision4Authority,
        targetProfile,
    );
const resolveEffectiveOptions = (parsed) =>
    workerBinding("resolveControlledTsConfigEffectiveOptions")(parsed);
const expectConfigFailure = (source, expectedCode, label) => {
    let failure = null;
    try {
        parseConfig(source, "rhino");
    } catch (error) {
        failure = error;
    }
    assert(failure !== null, `${label} did not fail.`);
    assert(
        failure.policyCode === expectedCode,
        `${label} produced ${failure.policyCode}, expected ${expectedCode}.`,
    );
};

const aliasConfig = (targetProfile, missing = false) => JSON.stringify({
    compilerOptions: {
        baseUrl: ".",
        lib: [targetProfile === "node" ? "ES2022" : "ES2018"],
        paths: {
            "@/*": [missing ? "missing/*" : "src/*"],
            "@feature/*": ["src/features/*"],
        },
        target: targetProfile === "node" ? "ES2022" : "ES2018",
    },
    files: ["src/index.ts"],
});

const directEffectiveOptions = new Map();
const verifyDirectPolicy = async () => {
    for (const targetProfile of ["rhino", "node"]) {
        const parsed = parseConfig(aliasConfig(targetProfile), targetProfile);
        assert(
            parsed.configPolicyRevision === 4 &&
                parsed.configPolicyFingerprint === policy.fingerprint &&
                parsed.pathAliases.baseUrl === "." &&
                exactJson(Object.keys(parsed.pathAliases.paths), [
                    "@/*",
                    "@feature/*",
                ]) &&
                exactJson(parsed.pathAliases.paths["@/*"], ["src/*"]) &&
                Object.isFrozen(parsed.pathAliases) &&
                Object.isFrozen(parsed.pathAliases.paths) &&
                Object.values(parsed.pathAliases.paths).every(Object.isFrozen),
            `${targetProfile} alias normalization or immutability drifted.`,
        );
        const effective = await resolveEffectiveOptions(parsed);
        const expectedProfile = fixture.profiles.find(
            (profile) => profile.targetProfile === targetProfile,
        );
        assert(
            expectedProfile !== undefined &&
            effective.revision === 4 &&
                Object.keys(effective.compilerOptions).length ===
                    expectedProfile.canonicalOptionCount &&
                effective.compilerOptions.baseUrl === "." &&
                exactJson(effective.compilerOptions.paths, parsed.pathAliases.paths) &&
                effective.canonicalJson === JSON.stringify(effective.compilerOptions) &&
                effective.fingerprint ===
                    sha256(Buffer.from(effective.canonicalJson, "ascii")) &&
                effective.fingerprint ===
                    expectedProfile.effectiveOptionsFingerprint &&
                Object.isFrozen(effective) &&
                Object.isFrozen(effective.compilerOptions) &&
                Object.isFrozen(effective.compilerOptions.paths) &&
                Object.values(effective.compilerOptions.paths).every(Object.isFrozen),
            `${targetProfile} revision-4 effective options drifted.`,
        );
        directEffectiveOptions.set(targetProfile, effective);
    }

    const implicitBaseUrl = parseConfig(
        '{"compilerOptions":{"paths":{"@/*":["src/*"]}}}',
        "rhino",
    );
    assert(
        implicitBaseUrl.pathAliases.baseUrl === ".",
        "paths without baseUrl did not receive the project-root default.",
    );
    for (const rejected of fixture.rejected) {
        expectConfigFailure(
            rejected.configSource,
            rejected.policyCode,
            rejected.id,
        );
    }
};

const minimalGlobals =
    "interface Array<T> { readonly length: number; [n: number]: T; }\n" +
    "interface Boolean {}\n" +
    "interface CallableFunction extends Function {}\n" +
    "declare class Error { constructor(message?: string); message: string; stack?: string; }\n" +
    "interface Function {}\n" +
    "interface IArguments { length: number; [n: number]: any; }\n" +
    "interface NewableFunction extends Function {}\n" +
    "interface Number {}\n" +
    "interface Object {}\n" +
    "interface RegExp {}\n" +
    "interface String {}\n";
const entrySource =
    Array.from({ length: 10 }, (_, index) => {
        const number = String(index + 1).padStart(2, "0");
        return `import { value${number} } from \"@/value${number}\";`;
    }).join("\n") +
    "\nimport { raise } from \"@feature/fault\";\n" +
    "export const sum = " +
    Array.from({ length: 10 }, (_, index) =>
        `value${String(index + 1).padStart(2, "0")}`,
    ).join(" + ") +
    ";\n" +
    "export const runFault = (): never => raise();\n";
const faultSource =
    "export const raise = (): never => {\n" +
    "    throw new Error(\"alias-runtime-fault\");\n" +
    "};\n";
const materialize = (filePath, text) => {
    const bytes = Buffer.from(text, "utf8");
    return Object.freeze({
        bytes,
        path: filePath,
        sha256: sha256(bytes),
        size: bytes.length,
        text,
    });
};
const projectFiles = (targetProfile, missing = false) => {
    const files = [
        materialize("/declarations/lib.d.ts", minimalGlobals),
        materialize(
            targetProfile === "node"
                ? "/declarations/lib.es2022.d.ts"
                : "/declarations/lib.es2018.d.ts",
            minimalGlobals,
        ),
    ];
    if (targetProfile === "node") {
        files.push(materialize("/declarations/node/index.d.ts", "export {};\n"));
    }
    files.push(
        materialize(
            "/sources/src/index.ts",
            missing
                ? 'import { absent } from "@/absent";\nexport const value = absent;\n'
                : entrySource,
        ),
    );
    if (!missing) {
        for (let index = 1; index <= 10; index += 1) {
            const number = String(index).padStart(2, "0");
            files.push(
                materialize(
                    `/sources/src/value${number}.ts`,
                    `export const value${number}: number = ${index};\n`,
                ),
            );
        }
        files.push(materialize("/sources/src/features/fault.ts", faultSource));
    }
    return files.sort((left, right) => compareCodeUnits(left.path, right.path));
};

const nodeProgramAuthority = Object.freeze({
    runtimeCapabilitiesFingerprint:
        "a19e8da1a4ab5961aada8d49e1f70eef847b84a0a8f348b6f784f43dc26e2d3a",
    nodeModuleOutputPolicySchemaRevision: 1,
    nodeModuleOutputPolicyRevision: 1,
    nodeModuleOutputPolicyFingerprint:
        "5c2294f0ee9d6d25bd2132b4ea24866d0a5f9caaa521a63bf81ffe1d897f6ce7",
    resolverPolicyRevision: 2,
    packageTargetPolicyRevision: 2,
    packageTargetPolicyFingerprint:
        "175cc694dae3e925d66eac3f77f308c95dc0db7b939238a33022d27812578224",
});
const canonicalResolutionPath = (value) =>
    Array.from(Buffer.from(value, "utf8"))
        .map((byte) =>
            byte >= 0x20 &&
            byte <= 0x7e &&
            byte !== "%".charCodeAt(0) &&
            byte !== "|".charCodeAt(0) &&
            byte !== ",".charCodeAt(0)
                ? String.fromCharCode(byte)
                : `%${byte.toString(16).toUpperCase().padStart(2, "0")}`,
        )
        .join("");
const nodeResolutionIdentity = (files, profile) => {
    const records = files
        .slice()
        .sort((left, right) => compareCodeUnits(left.path, right.path));
    const targets = records
        .map((file) => ({
            kind: file.path.endsWith(".d.ts")
                ? "declaration"
                : file.path.startsWith("/sources/")
                  ? "project-source"
                  : "non-resolution",
            path: file.path,
        }))
        .filter((target) => target.kind !== "non-resolution");
    let snapshotCanonical =
        "autojs6.typescript.node-resolution.snapshot-targets.v2" +
        `|profileSchemaRevision=${profile.profileSchemaRevision}` +
        `|profileRevision=${profile.profileRevision}` +
        `|nodeModuleOutputPolicyFingerprint=${nodeProgramAuthority.nodeModuleOutputPolicyFingerprint}`;
    for (const file of records) {
        const kind = file.path.endsWith(".d.ts")
            ? "declaration"
            : file.path.startsWith("/sources/")
              ? "project-source"
              : "non-resolution";
        snapshotCanonical +=
            `|file=${canonicalResolutionPath(file.path)},${file.size},` +
            `${file.text.length},${file.sha256},${kind}`;
    }
    const snapshotFingerprint = sha256(Buffer.from(snapshotCanonical, "ascii"));
    const targetUniverseCanonical =
        "autojs6.typescript.node-resolution.target-universe.v2" +
        `|resolverPolicyRevision=${nodeProgramAuthority.resolverPolicyRevision}` +
        `|packageTargetPolicyRevision=${nodeProgramAuthority.packageTargetPolicyRevision}` +
        `|packageTargetPolicyFingerprint=${nodeProgramAuthority.packageTargetPolicyFingerprint}` +
        `|snapshotTargetInventoryFingerprint=${snapshotFingerprint}` +
        "|packageTargetInventoryFingerprint=none" +
        targets
            .sort((left, right) => compareCodeUnits(left.path, right.path))
            .map(
                (target) =>
                    `|target=${canonicalResolutionPath(target.path)},${target.kind}`,
            )
            .join("");
    return Object.freeze({
        snapshotFingerprint,
        targetUniverseFingerprint: sha256(
            Buffer.from(targetUniverseCanonical, "ascii"),
        ),
    });
};

const legacyStartKeys = Object.freeze([
    "callerInventoryFingerprint",
    "configKind",
    "configSchemaRevision",
    "declarationByteLength",
    "declarationFileCount",
    "declarationInventoryFingerprint",
    "declarationProfileFingerprint",
    "fileCount",
    "inputArchiveSchemaRevision",
    "inputQuotaRevision",
    "kind",
    "largestFileBytes",
    "profileRevision",
    "profileSchemaRevision",
    "requestId",
    "retainedFileCount",
    "retainedInventoryFingerprint",
    "sequence",
    "targetProfile",
    "totalBytes",
    "workerSpinMilliseconds",
    "workerSpinPhase",
]);
const configKeys = Object.freeze([
    "configByteLength",
    "configPolicyFingerprint",
    "configPolicyRevision",
    "configSha256",
]);
const rhinoStartKeys = Object.freeze([
    ...legacyStartKeys,
    ...configKeys,
    "entryPath",
]);
const nodeStartKeys = Object.freeze([
    ...legacyStartKeys,
    ...configKeys,
    "entryPath",
    "runtimeCapabilitiesFingerprint",
    "nodeModuleOutputPolicySchemaRevision",
    "nodeModuleOutputPolicyRevision",
    "nodeModuleOutputPolicyFingerprint",
    "resolverPolicyRevision",
    "snapshotTargetInventoryFingerprint",
    "targetUniverseFingerprint",
]);

let requestCounter = 0;
const createOperation = (targetProfile, missing = false) => {
    const profile = {
        id: `${targetProfile}-${missing ? "missing" : "aliases"}`,
        targetProfile,
        profileSchemaRevision: 1,
        profileRevision: 2,
        entryPath: "/sources/src/index.ts",
    };
    const files = projectFiles(targetProfile, missing);
    const configSource = aliasConfig(targetProfile, missing);
    const configBytes = Buffer.from(configSource, "utf8");
    const configSha256 = sha256(configBytes);
    const inputAuthority = projectInputAuthority({
        configRecord: inputRecord("config", policy.configPath, configBytes),
        fileRecords: files.map((file) =>
            inputRecord(
                file.path.startsWith("/declarations/")
                    ? "declaration"
                    : "source",
                file.path,
                file.bytes,
            ),
        ),
        inputQuotaRevision: CURRENT_ORDINARY_INPUT_QUOTA_REVISION,
    });
    const requestId = `t1-path-alias-${profile.id}-${++requestCounter}`;
    const start = {
        ...inputAuthority,
        configByteLength: configBytes.length,
        configKind: policy.configKind,
        configPolicyFingerprint: policy.fingerprint,
        configPolicyRevision: policy.revision,
        configSchemaRevision: 2,
        configSha256,
        entryPath: profile.entryPath,
        kind: "projectInputStart",
        profileRevision: profile.profileRevision,
        profileSchemaRevision: profile.profileSchemaRevision,
        requestId,
        sequence: 0,
        targetProfile,
        workerSpinMilliseconds: 0,
        workerSpinPhase: "none",
    };
    if (targetProfile === "node") {
        const resolution = nodeResolutionIdentity(files, profile);
        Object.assign(start, {
            nodeModuleOutputPolicyFingerprint:
                nodeProgramAuthority.nodeModuleOutputPolicyFingerprint,
            nodeModuleOutputPolicyRevision:
                nodeProgramAuthority.nodeModuleOutputPolicyRevision,
            nodeModuleOutputPolicySchemaRevision:
                nodeProgramAuthority.nodeModuleOutputPolicySchemaRevision,
            resolverPolicyRevision: nodeProgramAuthority.resolverPolicyRevision,
            runtimeCapabilitiesFingerprint:
                nodeProgramAuthority.runtimeCapabilitiesFingerprint,
            snapshotTargetInventoryFingerprint: resolution.snapshotFingerprint,
            targetUniverseFingerprint: resolution.targetUniverseFingerprint,
        });
    }
    assert(
        exactKeys(
            start,
            targetProfile === "node" ? nodeStartKeys : rhinoStartKeys,
        ),
        `${profile.id} start shape is not exact.`,
    );
    return {
        configBytes,
        configSha256,
        files,
        inputAuthority,
        profile,
        requestId,
        sequence: 0,
        start,
    };
};

const expectAck = (published, operation, label) => {
    const acknowledgements = published.filter(
        (message) => message.kind === "projectInputAck",
    );
    assert(
        acknowledgements.length === 1 &&
            acknowledgements[0].requestId === operation.requestId &&
            acknowledgements[0].sequence === operation.sequence &&
            !published.some(
                (message) =>
                    message.kind === "projectError" ||
                    message.kind === "projectConfigError",
            ),
        `${label} did not publish its exact ACK: ${JSON.stringify(published)}.`,
    );
    operation.sequence += 1;
};
const sendConfig = async (operation) => {
    expectAck(await dispatch(operation.start), operation, `${operation.profile.id} start`);
    expectAck(
        await dispatch({
            byteLength: operation.configBytes.length,
            kind: "projectInputConfigStart",
            path: policy.configPath,
            requestId: operation.requestId,
            sequence: operation.sequence,
            sha256: operation.configSha256,
        }),
        operation,
        `${operation.profile.id} config start`,
    );
    expectAck(
        await dispatch({
            chunkIndex: 0,
            data: operation.configBytes.toString("base64"),
            kind: "projectInputConfigChunk",
            requestId: operation.requestId,
            sequence: operation.sequence,
        }),
        operation,
        `${operation.profile.id} config chunk`,
    );
    expectAck(
        await dispatch({
            chunkCount: 1,
            kind: "projectInputConfigEnd",
            requestId: operation.requestId,
            sequence: operation.sequence,
        }),
        operation,
        `${operation.profile.id} config end`,
    );
};
const sendFile = async (operation, fileIndex, file) => {
    expectAck(
        await dispatch({
            byteLength: file.size,
            fileIndex,
            kind: "projectInputFileStart",
            path: file.path,
            requestId: operation.requestId,
            sequence: operation.sequence,
            sha256: file.sha256,
        }),
        operation,
        `${operation.profile.id} ${file.path} start`,
    );
    expectAck(
        await dispatch({
            chunkIndex: 0,
            data: file.bytes.toString("base64"),
            fileIndex,
            kind: "projectInputFileChunk",
            requestId: operation.requestId,
            sequence: operation.sequence,
        }),
        operation,
        `${operation.profile.id} ${file.path} chunk`,
    );
    expectAck(
        await dispatch({
            chunkCount: 1,
            fileIndex,
            kind: "projectInputFileEnd",
            requestId: operation.requestId,
            sequence: operation.sequence,
        }),
        operation,
        `${operation.profile.id} ${file.path} end`,
    );
};
const readFramedResult = async (operation, inputEndMessages) => {
    expectAck(inputEndMessages, operation, `${operation.profile.id} input end`);
    const starts = inputEndMessages.filter(
        (message) => message.kind === "projectResultStart",
    );
    assert(starts.length === 1, `${operation.profile.id} result start drifted.`);
    const start = starts[0];
    const chunks = [];
    for (let index = 0; index < start.chunkCount; index += 1) {
        const published = await dispatch({
            kind: "projectResultAck",
            requestId: operation.requestId,
            sequence: index - 1,
        });
        assert(
            published.length === 1 &&
                published[0].kind === "projectResultChunk" &&
                published[0].index === index,
            `${operation.profile.id} result chunk ${index} drifted.`,
        );
        chunks.push(Buffer.from(published[0].data, "base64"));
    }
    const terminal = await dispatch({
        kind: "projectResultAck",
        requestId: operation.requestId,
        sequence: start.chunkCount - 1,
    });
    assert(
        terminal.length === 2 &&
            terminal[0].kind === "projectPhase" &&
            terminal[0].phase === "emit" &&
            terminal[0].state === "completed" &&
            terminal[1].kind === "projectResultEnd",
        `${operation.profile.id} result terminal drifted.`,
    );
    const bytes = Buffer.concat(chunks);
    assert(
        bytes.length === start.byteLength && sha256(bytes) === start.sha256,
        `${operation.profile.id} result identity drifted.`,
    );
    return JSON.parse(bytes.toString("utf8"));
};
const compileProfile = async (targetProfile, missing = false) => {
    const operation = createOperation(targetProfile, missing);
    await sendConfig(operation);
    for (let index = 0; index < operation.files.length; index += 1) {
        await sendFile(operation, index, operation.files[index]);
    }
    const result = await readFramedResult(
        operation,
        await dispatch({
            fileCount: operation.files.length,
            kind: "projectInputEnd",
            requestId: operation.requestId,
            sequence: operation.sequence,
            totalBytes: operation.inputAuthority.totalBytes,
        }),
    );
    assert(
        workerBinding("framedInput") === null &&
            workerBinding("framedCompilationRequestId") === null &&
            workerBinding("framedResult") === null,
        `${operation.profile.id} retained Worker request state.`,
    );
    return result;
};

const BASE64 =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
const decodeVlq = (segment) => {
    const values = [];
    let value = 0;
    let shift = 0;
    for (const character of segment) {
        const digit = BASE64.indexOf(character);
        assert(digit >= 0, "Source Map contains an invalid base64 VLQ digit.");
        value += (digit & 31) << shift;
        if ((digit & 32) !== 0) {
            shift += 5;
            continue;
        }
        const negative = (value & 1) === 1;
        const decoded = value >> 1;
        values.push(negative ? -decoded : decoded);
        value = 0;
        shift = 0;
    }
    assert(shift === 0, "Source Map contains a truncated VLQ segment.");
    return values;
};
const originalPositionFor = (sourceMap, generatedLine, generatedColumn) => {
    let sourceIndex = 0;
    let originalLine = 0;
    let originalColumn = 0;
    let nameIndex = 0;
    const lines = sourceMap.mappings.split(";");
    let found = null;
    for (let lineIndex = 0; lineIndex < lines.length; lineIndex += 1) {
        let generated = 0;
        for (const encoded of lines[lineIndex].split(",")) {
            if (encoded.length === 0) continue;
            const values = decodeVlq(encoded);
            generated += values[0];
            if (values.length >= 4) {
                sourceIndex += values[1];
                originalLine += values[2];
                originalColumn += values[3];
                if (values.length === 5) nameIndex += values[4];
                if (
                    lineIndex === generatedLine - 1 &&
                    generated <= generatedColumn - 1
                ) {
                    found = { sourceIndex, originalLine, originalColumn, nameIndex };
                }
            }
        }
        if (lineIndex >= generatedLine - 1) break;
    }
    return found;
};

const executeCommonJsProject = (result) => {
    const modules = new Map(
        result.outputs
            .filter((output) => !output.path.endsWith(".map"))
            .map((output) => [output.path, output.text]),
    );
    const cache = new Map();
    const load = (modulePath) => {
        if (cache.has(modulePath)) return cache.get(modulePath).exports;
        const text = modules.get(modulePath);
        assert(text !== undefined, `Runtime module is missing: ${modulePath}.`);
        const module = { exports: {} };
        cache.set(modulePath, module);
        const localRequire = (specifier) => {
            assert(
                specifier.startsWith("./") || specifier.startsWith("../"),
                `Runtime retained a non-relative alias: ${specifier}.`,
            );
            const resolved = path.posix.normalize(
                path.posix.join(path.posix.dirname(modulePath), specifier),
            );
            assert(
                resolved.startsWith("/outputs/"),
                `Runtime module escaped outputs: ${resolved}.`,
            );
            return load(resolved);
        };
        const wrapper = vm.runInNewContext(
            `(function (exports, module, require) {\n${text}\n})`,
            Object.create(null),
            { filename: modulePath },
        );
        wrapper(module.exports, module, localRequire);
        return module.exports;
    };
    const entry = load("/outputs/src/index.js");
    assert(entry.sum === 55, "Alias project returned the wrong runtime sum.");
    let runtimeError = null;
    try {
        entry.runFault();
    } catch (error) {
        runtimeError = error;
    }
    assert(
        runtimeError !== null && runtimeError.message === "alias-runtime-fault",
        "Alias runtime exception did not propagate.",
    );
    const stackMatch = String(runtimeError.stack).match(
        /\/outputs\/src\/features\/fault\.js:(\d+):(\d+)/,
    );
    assert(stackMatch !== null, `Runtime stack is not generated-module based: ${runtimeError.stack}`);
    const mapOutput = result.outputs.find(
        (output) => output.path === "/outputs/src/features/fault.js.map",
    );
    assert(mapOutput !== undefined, "Fault Source Map is missing.");
    const sourceMap = JSON.parse(mapOutput.text);
    const original = originalPositionFor(
        sourceMap,
        // The desktop CommonJS wrapper contributes one generated line; Android's
        // materialized module loader does not alter the Source Map itself.
        Number(stackMatch[1]) - 1,
        Number(stackMatch[2]),
    );
    assert(original !== null, "Fault stack position has no Source Map segment.");
    const originalPath = path.posix.normalize(
        path.posix.join(
            path.posix.dirname(mapOutput.path),
            sourceMap.sourceRoot || "",
            sourceMap.sources[original.sourceIndex],
        ),
    );
    assert(
        originalPath === "/sources/src/features/fault.ts" &&
            original.originalLine + 1 === 2,
        `Runtime fault mapped to ${originalPath}:${original.originalLine + 1}.`,
    );
};

const assertSuccessfulAliasCompilation = (result, targetProfile) => {
    const effective = directEffectiveOptions.get(targetProfile);
    assert(
        result.compilerApi === "createIncrementalProgram" &&
            result.configKind === "tsconfig" &&
            result.configSchemaRevision === 2 &&
            result.targetProfile === targetProfile &&
            result.effectiveOptionsRevision === 4 &&
            result.effectiveOptionsFingerprint === effective.fingerprint &&
            result.emitSkipped === false &&
            result.diagnostics.length === 0 &&
            result.outputs.length === 24 &&
            exactJson(result.rootNames, ["/sources/src/index.ts"]) &&
            result.options.baseUrl === "." &&
            exactJson(result.options.paths, {
                "@/*": ["src/*"],
                "@feature/*": ["src/features/*"],
            }),
        `${targetProfile} alias compilation authority drifted: ` +
            JSON.stringify(result.diagnostics),
    );
    const entry = result.outputs.find(
        (output) => output.path === "/outputs/src/index.js",
    );
    assert(
        entry !== undefined &&
            !entry.text.includes('"@/') &&
            !entry.text.includes('"@feature/') &&
            entry.text.includes('require("./value01.js")') &&
            entry.text.includes('require("./features/fault.js")'),
        `${targetProfile} emit did not rewrite aliases to output-relative specifiers.`,
    );
    executeCommonJsProject(result);
};

const assertMissingAliasCompilation = (result, targetProfile) => {
    const diagnostic = result.diagnostics.find(
        (candidate) => candidate.code === 2307,
    );
    assert(
        result.targetProfile === targetProfile &&
            result.effectiveOptionsRevision === 4 &&
            result.emitSkipped === true &&
            result.outputs.length === 0 &&
            diagnostic !== undefined &&
            diagnostic.sourcePath === "/sources/src/index.ts" &&
            diagnostic.line === 1 &&
            diagnostic.column > 1,
        `${targetProfile} missing alias did not map TS2307 to the original TS import: ` +
            JSON.stringify(result.diagnostics),
    );
};

(async () => {
    await verifyDirectPolicy();
    for (const targetProfile of ["rhino", "node"]) {
        assertSuccessfulAliasCompilation(
            await compileProfile(targetProfile),
            targetProfile,
        );
        assertMissingAliasCompilation(
            await compileProfile(targetProfile, true),
            targetProfile,
        );
    }
    process.stdout.write(
        "T1-3 controlled baseUrl/paths verified: rev4 append-only authority, " +
            "12-file @/ project compile/execute in Rhino and Node, relative emit rewrite, " +
            "TS2307 source locations, and runtime stack Source Map recovery; " +
            `effective fingerprints rhino=${directEffectiveOptions.get("rhino").fingerprint}, ` +
            `node=${directEffectiveOptions.get("node").fingerprint}\n`,
    );
})().catch((error) => {
    process.stderr.write(`${error.stack || error}\n`);
    process.exitCode = 1;
});
