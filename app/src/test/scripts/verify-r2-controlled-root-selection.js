"use strict";

const cryptoModule = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const {
    projectInputAuthority,
    record: inputRecord,
} = require("./r2-input-archive-authority.js");

const [workerArgument, compilerArgument, expectedVersion] = process.argv.slice(2);
if (!workerArgument || !compilerArgument || !expectedVersion) {
    throw new Error(
        "Usage: node verify-r2-controlled-root-selection.js " +
            "<worker.js> <typescript.js> <expected-version>",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const workerSource = fs.readFileSync(workerPath, "utf8");
const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;
const exactKeys = (value, expected) =>
    JSON.stringify(Object.keys(value).sort(compareCodeUnits)) ===
    JSON.stringify(expected.slice().sort(compareCodeUnits));
const sha256 = (bytes) =>
    cryptoModule.createHash("sha256").update(bytes).digest("hex");

if (!global.crypto) {
    Object.defineProperty(global, "crypto", {
        configurable: true,
        value: cryptoModule.webcrypto,
    });
}

const realTypeScript = require(compilerPath);
let compilerIoAccessCount = 0;
const poisonedCompilerIo = new Proxy(realTypeScript.sys, {
    get() {
        compilerIoAccessCount += 1;
        throw new Error("Controlled compilation accessed compiler host I/O.");
    },
});
const programConstructionCalls = [];
const typeScriptFacade = Object.create(realTypeScript);
Object.defineProperty(typeScriptFacade, "sys", { value: poisonedCompilerIo });
Object.defineProperty(typeScriptFacade, "createIncrementalProgram", {
    value: (configuration) => {
        programConstructionCalls.push({
            optionKeys: Object.keys(configuration.options),
            options: JSON.parse(JSON.stringify(configuration.options)),
            rootNames: configuration.rootNames.slice(),
        });
        return realTypeScript.createIncrementalProgram(configuration);
    },
});
global.ts = typeScriptFacade;
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
        publishedMessages[0].tsVersion === expectedVersion &&
        expectedVersion === "6.0.3",
    "Worker did not publish the pinned TypeScript 6.0.3 authority.",
);
publishedMessages.length = 0;

const workerBinding = (expression) =>
    vm.runInThisContext(expression, {
        filename: `${workerPath}#controlled-root-selection`,
    });
const dispatch = async (message) => {
    publishedMessages.length = 0;
    await self.onmessage({ data: message });
    return publishedMessages.slice();
};
const expectAck = (messages, requestId, sequence, label) => {
    const acknowledgements = messages.filter(
        (message) => message.kind === "projectInputAck",
    );
    assert(
        acknowledgements.length === 1 &&
            acknowledgements[0].requestId === requestId &&
            acknowledgements[0].sequence === sequence &&
            messages.every(
                (message) =>
                    message.kind !== "projectError" &&
                    message.kind !== "projectConfigError",
            ),
        `${label} did not publish the exact ACK: ${JSON.stringify(messages)}.`,
    );
};
const expectConfigError = (messages, requestId, policyCode, label) => {
    assert(
        messages.length === 1 &&
            exactKeys(messages[0], [
                "emittedModuleCount",
                "errorCode",
                "kind",
                "message",
                "outcome",
                "policyCode",
                "requestId",
                "stage",
            ]) &&
            messages[0].kind === "projectConfigError" &&
            messages[0].requestId === requestId &&
            messages[0].policyCode === policyCode &&
            messages[0].stage === "config" &&
            messages[0].outcome === "diagnostics" &&
            messages[0].errorCode ===
                "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_DIAGNOSTICS" &&
            messages[0].emittedModuleCount === 0 &&
            !messages.some((message) => message.kind === "projectInputAck"),
        `${label} did not publish the exact typed config terminal.`,
    );
};
const assertWorkerIdle = (label) => {
    assert(
        workerBinding("framedInput") === null &&
            workerBinding("framedCompilationRequestId") === null &&
            workerBinding("framedResult") === null,
        `${label} retained Worker operation state.`,
    );
};

const minimalLibrary =
    "interface Array<T> { readonly length: number; [n: number]: T; }\n" +
    "interface Boolean {}\n" +
    "interface CallableFunction extends Function {}\n" +
    "interface Function {}\n" +
    "interface IArguments { length: number; [n: number]: any; }\n" +
    "interface NewableFunction extends Function {}\n" +
    "interface Number {}\n" +
    "interface Object {}\n" +
    "interface RegExp {}\n" +
    "interface String {}\n";
const projectFiles = Object.freeze([
    Object.freeze({ path: "/declarations/lib.d.ts", text: minimalLibrary }),
    Object.freeze({
        path: "/sources/src/contracts.d.ts",
        text: "export interface Contract { readonly value: number; }\n",
    }),
    Object.freeze({
        path: "/sources/src/entry.ts",
        text:
            'import { importedValue } from "./imported";\n' +
            "export const entryValue: number = importedValue;\n",
    }),
    Object.freeze({
        path: "/sources/src/imported.ts",
        text: "export const importedValue: number = 7;\n",
    }),
    Object.freeze({
        path: "/sources/src/selected.ts",
        text: "export const selectedValue: number = 11;\n",
    }),
    Object.freeze({
        path: "/sources/src/unreachable.ts",
        text: "export const unreachableValue: number = 13;\n",
    }),
]);
const controlledPolicy = Object.freeze({
    configKind: "tsconfig",
    configPolicyFingerprint:
        "64ef1cf36d6531f84088cec49127e6407b107ab86eeaf7f4512358fd1343598b",
    configPolicyRevision: 2,
    configSchemaRevision: 2,
    profileRevision: 2,
    profileSchemaRevision: 1,
    targetProfile: "rhino",
});
const legacyPolicy = Object.freeze({
    configKind: "none",
    configSchemaRevision: 1,
    profileRevision: 1,
    profileSchemaRevision: 1,
    targetProfile: "rhino",
});

let requestCounter = 0;
const createOperation = (config, files, entryPath = "/sources/src/entry.ts") => {
    requestCounter += 1;
    const configBytes =
        config === null ? null : Buffer.from(JSON.stringify(config), "utf8");
    const records = files.map((file) => {
        const bytes = Buffer.from(file.text, "utf8");
        return {
            bytes,
            path: file.path,
            sha256: sha256(bytes),
        };
    });
    return {
        configBytes,
        entryPath,
        inputAuthority: projectInputAuthority({
            configRecord:
                configBytes === null
                    ? null
                    : inputRecord("config", "tsconfig.json", configBytes),
            fileRecords: records.map((record) =>
                inputRecord(
                    record.path.startsWith("/declarations/")
                        ? "declaration"
                        : "source",
                    record.path,
                    record.bytes,
                ),
            ),
        }),
        records,
        requestId: `controlled-root-c7-${requestCounter}`,
        sequence: 0,
    };
};
const controlledStartFrame = (operation, extra = {}) => ({
    configByteLength: operation.configBytes.length,
    configKind: controlledPolicy.configKind,
    configPolicyFingerprint: controlledPolicy.configPolicyFingerprint,
    configPolicyRevision: controlledPolicy.configPolicyRevision,
    configSchemaRevision: controlledPolicy.configSchemaRevision,
    configSha256: sha256(operation.configBytes),
    entryPath: operation.entryPath,
    ...operation.inputAuthority,
    kind: "projectInputStart",
    profileRevision: controlledPolicy.profileRevision,
    profileSchemaRevision: controlledPolicy.profileSchemaRevision,
    requestId: operation.requestId,
    sequence: operation.sequence,
    targetProfile: controlledPolicy.targetProfile,
    workerSpinMilliseconds: 0,
    workerSpinPhase: "none",
    ...extra,
});
const legacyStartFrame = (operation, extra = {}) => ({
    configKind: legacyPolicy.configKind,
    configSchemaRevision: legacyPolicy.configSchemaRevision,
    ...operation.inputAuthority,
    kind: "projectInputStart",
    profileRevision: legacyPolicy.profileRevision,
    profileSchemaRevision: legacyPolicy.profileSchemaRevision,
    requestId: operation.requestId,
    sequence: operation.sequence,
    targetProfile: legacyPolicy.targetProfile,
    workerSpinMilliseconds: 0,
    workerSpinPhase: "none",
    ...extra,
});
const sendStart = async (operation, controlled = true) => {
    const frame = controlled
        ? controlledStartFrame(operation)
        : legacyStartFrame(operation);
    const messages = await dispatch(frame);
    expectAck(messages, operation.requestId, operation.sequence, "project start");
    operation.sequence += 1;
};
const sendConfig = async (operation) => {
    let messages = await dispatch({
        byteLength: operation.configBytes.length,
        kind: "projectInputConfigStart",
        path: "tsconfig.json",
        requestId: operation.requestId,
        sequence: operation.sequence,
        sha256: sha256(operation.configBytes),
    });
    expectAck(messages, operation.requestId, operation.sequence, "config start");
    operation.sequence += 1;
    messages = await dispatch({
        chunkIndex: 0,
        data: operation.configBytes.toString("base64"),
        kind: "projectInputConfigChunk",
        requestId: operation.requestId,
        sequence: operation.sequence,
    });
    expectAck(messages, operation.requestId, operation.sequence, "config chunk");
    operation.sequence += 1;
    messages = await dispatch({
        chunkCount: 1,
        kind: "projectInputConfigEnd",
        requestId: operation.requestId,
        sequence: operation.sequence,
    });
    expectAck(messages, operation.requestId, operation.sequence, "config end");
    operation.sequence += 1;
};
const sendFiles = async (operation) => {
    for (let fileIndex = 0; fileIndex < operation.records.length; fileIndex += 1) {
        const record = operation.records[fileIndex];
        let messages = await dispatch({
            byteLength: record.bytes.length,
            fileIndex,
            kind: "projectInputFileStart",
            path: record.path,
            requestId: operation.requestId,
            sequence: operation.sequence,
            sha256: record.sha256,
        });
        expectAck(messages, operation.requestId, operation.sequence, `${record.path} start`);
        operation.sequence += 1;
        if (record.bytes.length !== 0) {
            messages = await dispatch({
                chunkIndex: 0,
                data: record.bytes.toString("base64"),
                fileIndex,
                kind: "projectInputFileChunk",
                requestId: operation.requestId,
                sequence: operation.sequence,
            });
            expectAck(
                messages,
                operation.requestId,
                operation.sequence,
                `${record.path} chunk`,
            );
            operation.sequence += 1;
        }
        messages = await dispatch({
            chunkCount: record.bytes.length === 0 ? 0 : 1,
            fileIndex,
            kind: "projectInputFileEnd",
            requestId: operation.requestId,
            sequence: operation.sequence,
        });
        expectAck(messages, operation.requestId, operation.sequence, `${record.path} end`);
        operation.sequence += 1;
    }
};
const sendInputEnd = (operation) =>
    dispatch({
        fileCount: operation.records.length,
        kind: "projectInputEnd",
        requestId: operation.requestId,
        sequence: operation.sequence,
        totalBytes: operation.inputAuthority.totalBytes,
    });
const readFramedResult = async (operation, inputEndMessages) => {
    expectAck(
        inputEndMessages,
        operation.requestId,
        operation.sequence,
        "project input end",
    );
    const start = inputEndMessages.find(
        (message) =>
            message.kind === "projectResultStart" &&
            message.requestId === operation.requestId,
    );
    assert(start, "Successful compilation did not begin a framed result.");
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
                `Result chunk ${sequence + 1} was not stop-and-wait framed.`,
            );
            chunks.push(Buffer.from(chunk.data, "base64"));
        } else {
            assert(
                messages.some(
                    (message) =>
                        message.kind === "projectResultEnd" &&
                        message.requestId === operation.requestId &&
                        message.sha256 === start.sha256 &&
                        message.byteLength === start.byteLength,
                ),
                "Successful compilation did not finish its framed result.",
            );
        }
    }
    const bytes = Buffer.concat(chunks);
    assert(
        bytes.length === start.byteLength && sha256(bytes) === start.sha256,
        "Framed result bytes do not match their declared identity.",
    );
    assertWorkerIdle("framed result completion");
    return JSON.parse(bytes.toString("utf8"));
};
const compileControlled = async (config, files = projectFiles) => {
    const operation = createOperation(config, files);
    await sendStart(operation, true);
    await sendConfig(operation);
    await sendFiles(operation);
    return readFramedResult(operation, await sendInputEnd(operation));
};
const compileLegacy = async (files) => {
    const operation = createOperation(null, files, null);
    await sendStart(operation, false);
    await sendFiles(operation);
    return readFramedResult(operation, await sendInputEnd(operation));
};

const verifySelectorOracle = () => {
    const parseConfig = workerBinding("parseControlledTsConfig");
    const selectRoots = workerBinding("selectControlledRootNames");
    const sourceRecords = projectFiles.map((file) => ({ path: file.path }));
    const defaultRoots = selectRoots(
        parseConfig("{}"),
        sourceRecords,
        "/sources/src/entry.ts",
    );
    assert(
        JSON.stringify(defaultRoots) ===
            JSON.stringify([
                "/sources/src/contracts.d.ts",
                "/sources/src/entry.ts",
                "/sources/src/imported.ts",
                "/sources/src/selected.ts",
                "/sources/src/unreachable.ts",
            ]),
        "Default **/* did not select the complete TypeScript source inventory.",
    );
    const globRoots = selectRoots(
        parseConfig(
            JSON.stringify({
                exclude: ["src/unreachable.ts"],
                include: ["src/e*.ts", "src/contracts.d.ts"],
            }),
        ),
        sourceRecords,
        "/sources/src/entry.ts",
    );
    assert(
        JSON.stringify(globRoots) ===
            JSON.stringify([
                "/sources/src/contracts.d.ts",
                "/sources/src/entry.ts",
            ]),
        "Snapshot-only TypeScript include matching drifted.",
    );
    const unionRoots = selectRoots(
        parseConfig(
            JSON.stringify({
                exclude: ["src/selected.ts", "src/unreachable.ts"],
                files: ["src/selected.ts", "src/entry.ts", "src/selected.ts"],
                include: ["src/*.ts"],
            }),
        ),
        sourceRecords,
        "/sources/src/entry.ts",
    );
    assert(
        unionRoots.includes("/sources/src/selected.ts") &&
            !unionRoots.includes("/sources/src/unreachable.ts") &&
            JSON.stringify(unionRoots) ===
                JSON.stringify(unionRoots.slice().sort(compareCodeUnits)),
        "files/include union, exclude-only-include, dedup, or ordering drifted.",
    );
    const normalized = parseConfig(
        JSON.stringify({ files: ["src/e\u0301.ts", "src/é.ts"] }),
    );
    assert(
        JSON.stringify(normalized.selectors.files) === JSON.stringify(["src/é.ts"]),
        "Selector NFC normalization/dedup drifted.",
    );
};

const verifyControlledStartShape = async () => {
    const missingEntry = createOperation({}, projectFiles);
    const frame = controlledStartFrame(missingEntry);
    delete frame.entryPath;
    let messages = await dispatch(frame);
    assert(
        messages.length === 1 &&
            messages[0].kind === "projectError" &&
            !messages.some((message) => message.kind === "projectInputAck"),
        "Controlled start accepted a missing entryPath.",
    );
    assertWorkerIdle("missing controlled entryPath");

    const declarationEntry = createOperation(
        {},
        projectFiles,
        "/sources/src/contracts.d.ts",
    );
    messages = await dispatch(controlledStartFrame(declarationEntry));
    assert(
        messages.length === 1 && messages[0].kind === "projectError",
        "Controlled start accepted a declaration entry.",
    );
    assertWorkerIdle("declaration controlled entryPath");
};

const rejectSelection = async (config, expectedPolicyCode, label) => {
    const constructionCount = programConstructionCalls.length;
    const operation = createOperation(config, projectFiles);
    await sendStart(operation, true);
    await sendConfig(operation);
    await sendFiles(operation);
    const messages = await sendInputEnd(operation);
    expectConfigError(messages, operation.requestId, expectedPolicyCode, label);
    assert(
        programConstructionCalls.length === constructionCount &&
            !messages.some(
                (message) =>
                    message.kind === "projectResultStart" ||
                    (message.kind === "projectPhase" && message.phase === "parse"),
            ),
        `${label} crossed the pre-Program selection boundary.`,
    );
    assertWorkerIdle(label);
};

const verifyControlledProgramAndResult = async () => {
    const config = {
        compilerOptions: {
            noImplicitAny: false,
            strict: false,
        },
        exclude: [
            "src/imported.ts",
            "src/selected.ts",
            "src/unreachable.ts",
        ],
        files: ["src/selected.ts"],
        include: [
            "src/contracts.d.ts",
            "src/entry.ts",
            "src/imported.ts",
            "src/unreachable.ts",
        ],
    };
    const result = await compileControlled(config);
    const legacyResultKeys = [
        "compilerApi",
        "configKind",
        "configSchemaRevision",
        "diagnostics",
        "emitSkipped",
        "options",
        "outputs",
        "profileRevision",
        "profileSchemaRevision",
        "targetProfile",
        "tsVersion",
    ];
    assert(
        exactKeys(result, [
            ...legacyResultKeys,
            "effectiveOptionsFingerprint",
            "effectiveOptionsRevision",
            "rootNames",
        ]) &&
            result.configKind === "tsconfig" &&
            result.configSchemaRevision === 2 &&
            result.effectiveOptionsRevision === 1 &&
            /^[0-9a-f]{64}$/.test(result.effectiveOptionsFingerprint) &&
            result.diagnostics.length === 0 &&
            result.emitSkipped === false,
        "Controlled result authority shape drifted.",
    );
    const canonicalOptionKeys = workerBinding("controlledTsConfigCanonicalOptionKeys");
    assert(
        Object.keys(result.options).length === 41 &&
            JSON.stringify(Object.keys(result.options)) ===
                JSON.stringify(canonicalOptionKeys) &&
            result.options.strict === false &&
            result.options.noImplicitAny === false,
        "Controlled result did not expose the exact C3 41-key effective map.",
    );
    const expectedRoots = [
        "/sources/src/contracts.d.ts",
        "/sources/src/entry.ts",
        "/sources/src/selected.ts",
    ];
    assert(
        JSON.stringify(result.rootNames) === JSON.stringify(expectedRoots),
        "Controlled result rootNames do not match selector authority.",
    );
    const outputPaths = result.outputs.map((output) => output.path);
    const expectedExecutableSources = [
        "/sources/src/entry.ts",
        "/sources/src/imported.ts",
        "/sources/src/selected.ts",
    ];
    const expectedOutputPaths = expectedExecutableSources
        .flatMap((sourcePath) => {
            const modulePath = `/outputs/${sourcePath
                .slice("/sources/".length)
                .slice(0, -".ts".length)}.js`;
            return [modulePath, `${modulePath}.map`];
        })
        .sort(compareCodeUnits);
    assert(
        JSON.stringify(outputPaths) === JSON.stringify(expectedOutputPaths) &&
            !outputPaths.some((outputPath) => outputPath.includes("unreachable")) &&
            !outputPaths.some((outputPath) => outputPath.includes("contracts")),
        "Program-reachable executable output inventory drifted.",
    );
    for (const output of result.outputs) {
        assert(
            output.sourcePaths.length === 1 &&
                expectedExecutableSources.includes(output.sourcePaths[0]),
            `${output.path} lost its reachable source mapping.`,
        );
    }
    const call = programConstructionCalls.at(-1);
    assert(
        JSON.stringify(call.rootNames) === JSON.stringify(expectedRoots) &&
            call.optionKeys.length === 41 &&
            JSON.stringify(call.options) === JSON.stringify(result.options),
        "Program construction did not consume the selected roots/C3 options.",
    );
};

const verifyLegacyRegression = async () => {
    const legacyFiles = [
        projectFiles[0],
        {
            path: "/sources/main.ts",
            text: "export const legacyValue: number = 1;\n",
        },
    ];
    const result = await compileLegacy(legacyFiles);
    assert(
        exactKeys(result, [
            "compilerApi",
            "configKind",
            "configSchemaRevision",
            "diagnostics",
            "emitSkipped",
            "options",
            "outputs",
            "profileRevision",
            "profileSchemaRevision",
            "targetProfile",
            "tsVersion",
        ]) &&
            result.configKind === "none" &&
            result.configSchemaRevision === 1 &&
            exactKeys(result.options, [
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
            result.diagnostics.length === 0 &&
            result.outputs.length === 2,
        "Legacy config-none result/options shape drifted.",
    );
};

(async () => {
    verifySelectorOracle();
    await verifyControlledStartShape();
    await rejectSelection(
        { files: [] },
        "CONFIG_NO_INPUTS",
        "empty files selection",
    );
    await rejectSelection(
        { files: ["src/unreachable.ts"], include: [] },
        "CONFIG_ENTRY_NOT_SELECTED",
        "entry excluded from non-empty roots",
    );
    await rejectSelection(
        { files: [], include: [] },
        "CONFIG_NO_INPUTS",
        "NO_INPUTS precedence over entry membership",
    );
    await verifyControlledProgramAndResult();
    await verifyLegacyRegression();
    assert(
        compilerIoAccessCount === 0,
        "Root selection or compilation accessed poisoned compiler host I/O.",
    );
    process.stdout.write(
        "R2-M2-C7 controlled root selection verified: " +
            "5 selector semantics, 3 pre-Program failures, " +
            "3 reachable executable sources, 41 controlled options, " +
            "18 legacy options, compiler host I/O=0\n",
    );
})().catch((error) => {
    process.stderr.write(`${error.stack || error}\n`);
    process.exitCode = 1;
});
