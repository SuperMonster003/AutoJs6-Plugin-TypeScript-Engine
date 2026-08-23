"use strict";

const cryptoModule = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const {
    projectInputAuthority,
    record: inputRecord,
} = require("./r2-input-archive-authority.js");

const [workerArgument, compilerArgument, fixtureArgument, expectedVersion] =
    process.argv.slice(2);
if (
    !workerArgument ||
    !compilerArgument ||
    !fixtureArgument ||
    !expectedVersion ||
    process.argv.slice(2).length !== 4
) {
    throw new Error(
        "Usage: node verify-r2-m2-x3-stable-failures.js " +
            "<compiler-worker.js> <typescript.js> <x3-v1.json> 6.0.3",
    );
}
if (expectedVersion !== "6.0.3") {
    throw new Error("The X3 direct oracle requires TypeScript 6.0.3.");
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const fixturePath = path.resolve(fixtureArgument);
const workerSource = fs.readFileSync(workerPath, "utf8");
const fixture = JSON.parse(fs.readFileSync(fixturePath, "utf8"));
const realTypeScript = require(compilerPath);

const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;
const exactJson = (left, right) => JSON.stringify(left) === JSON.stringify(right);
const sorted = (values) => Array.from(values).sort(compareCodeUnits);
const exactKeys = (value, expected) =>
    value !== null &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    exactJson(sorted(Object.keys(value)), sorted(expected));
const sha256 = (value) =>
    cryptoModule.createHash("sha256").update(value).digest("hex");

const expectedConfigurationCases = Object.freeze([
    Object.freeze([
        "encoding-invalid",
        "CONFIG_ENCODING_INVALID",
        "config-end",
        "base64",
    ]),
    Object.freeze([
        "syntax-invalid",
        "CONFIG_SYNTAX_INVALID",
        "config-end",
        "utf8",
    ]),
    Object.freeze([
        "duplicate-key",
        "CONFIG_DUPLICATE_KEY",
        "config-end",
        "utf8",
    ]),
    Object.freeze([
        "unknown-property",
        "CONFIG_UNKNOWN_PROPERTY",
        "config-end",
        "utf8",
    ]),
    Object.freeze([
        "invalid-value",
        "CONFIG_INVALID_VALUE",
        "config-end",
        "utf8",
    ]),
    Object.freeze([
        "unsupported-feature",
        "CONFIG_UNSUPPORTED_FEATURE",
        "config-end",
        "utf8",
    ]),
    Object.freeze([
        "profile-conflict",
        "CONFIG_PROFILE_CONFLICT",
        "config-end",
        "utf8",
    ]),
    Object.freeze([
        "path-invalid",
        "CONFIG_PATH_INVALID",
        "config-end",
        "utf8",
    ]),
    Object.freeze([
        "complexity-exceeded",
        "CONFIG_COMPLEXITY_EXCEEDED",
        "config-end",
        "utf8",
    ]),
    Object.freeze([
        "no-inputs",
        "CONFIG_NO_INPUTS",
        "input-end",
        "utf8",
    ]),
    Object.freeze([
        "entry-not-selected",
        "CONFIG_ENTRY_NOT_SELECTED",
        "input-end",
        "utf8",
    ]),
]);
const expectedResolutionCases = Object.freeze([
    "NODE_RESOLUTION_INVALID_CONTAINING_PATH",
    "NODE_RESOLUTION_INVALID_SPECIFIER",
    "NODE_RESOLUTION_INVALID_CONDITION",
    "NODE_RESOLUTION_AMBIGUOUS_CASE",
    "NODE_RESOLUTION_PACKAGE_NOT_ALLOWED",
    "NODE_RESOLUTION_PACKAGE_METADATA_MISSING",
    "NODE_RESOLUTION_PACKAGE_METADATA_INVALID",
    "NODE_RESOLUTION_IMPORTS_NOT_DEFINED",
    "NODE_RESOLUTION_IMPORTS_NO_MATCH",
    "NODE_RESOLUTION_EXPORTS_NOT_DEFINED",
    "NODE_RESOLUTION_EXPORTS_NO_MATCH",
    "NODE_RESOLUTION_CONDITION_NO_MATCH",
    "NODE_RESOLUTION_INVALID_TARGET",
    "NODE_RESOLUTION_TARGET_BLOCKED",
    "NODE_RESOLUTION_TARGET_ESCAPE",
    "NODE_RESOLUTION_TARGET_NOT_IN_SNAPSHOT",
    "NODE_RESOLUTION_UNSUPPORTED_SOURCE_KIND",
    "NODE_RESOLUTION_CYCLE",
]);
const expectedResolutionNames = Object.freeze([
    "invalid-containing-path",
    "invalid-specifier",
    "invalid-condition",
    "ambiguous-case",
    "package-not-allowed",
    "package-metadata-missing",
    "package-metadata-invalid",
    "imports-not-defined",
    "imports-no-match",
    "exports-not-defined",
    "exports-no-match",
    "condition-no-match",
    "invalid-target",
    "target-blocked",
    "target-escape",
    "target-not-in-snapshot",
    "unsupported-source-kind",
    "cycle",
]);
const expectedExcludedDomains = Object.freeze([
    "archive-integrity-quota-and-output",
    "config-and-project-transport-frame-validation",
    "snapshot-and-package-ingress-validation",
    "module-output-and-ordinary-typescript-diagnostics",
    "internal-invariant-cancel-timeout-and-compiler-unavailable",
    "public-and-runtime-promotion",
]);

const validateFixture = () => {
    assert(
        exactKeys(fixture, [
            "callerErrorCode",
            "cleanSuccessors",
            "configurationFailures",
            "excludedDomains",
            "resolutionFailures",
            "revision",
            "schema",
            "scope",
            "typescriptVersion",
        ]),
        "X3 fixture root shape drifted.",
    );
    assert(
        fixture.schema ===
            "autojs6.typescript.r2-m2-x3.configuration-resolution-failures" &&
            fixture.revision === 1 &&
            fixture.typescriptVersion === "6.0.3" &&
            fixture.typescriptVersion === expectedVersion &&
            fixture.callerErrorCode ===
                "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_DIAGNOSTICS",
        "X3 fixture identity or caller-visible error code drifted.",
    );
    assert(
        exactKeys(fixture.scope, [
            "configuration",
            "evidenceLevel",
            "resolution",
        ]) &&
            fixture.scope.configuration ===
                "accepted-controlled-tsconfig-parser-policy-and-root-selection" &&
            fixture.scope.resolution ===
                "accepted-snapshot-resolver-decision" &&
            fixture.scope.evidenceLevel === "worker-direct-oracle-only",
        "X3 fixture scope drifted or promoted beyond Worker direct evidence.",
    );
    assert(
        exactJson(fixture.excludedDomains, expectedExcludedDomains),
        "X3 excluded-domain boundary drifted.",
    );
    assert(
        Array.isArray(fixture.configurationFailures) &&
            fixture.configurationFailures.length === 11,
        "X3 fixture does not contain exactly eleven configuration failures.",
    );
    fixture.configurationFailures.forEach((testCase, index) => {
        const expected = expectedConfigurationCases[index];
        assert(
            exactKeys(testCase, [
                "boundary",
                "name",
                "policyCode",
                "source",
                "sourceEncoding",
            ]) &&
                testCase.name === expected[0] &&
                testCase.policyCode === expected[1] &&
                testCase.boundary === expected[2] &&
                testCase.sourceEncoding === expected[3] &&
                typeof testCase.source === "string",
            `Configuration fixture ${index} drifted.`,
        );
        if (testCase.sourceEncoding === "base64") {
            const decoded = Buffer.from(testCase.source, "base64");
            assert(
                decoded.toString("base64") === testCase.source &&
                    decoded.length !== 0,
                `${testCase.name} is not canonical non-empty base64.`,
            );
        }
    });
    assert(
        Array.isArray(fixture.resolutionFailures) &&
            fixture.resolutionFailures.length === 18,
        "X3 fixture does not contain exactly eighteen resolution failures.",
    );
    fixture.resolutionFailures.forEach((testCase, index) => {
        assert(
            exactKeys(testCase, ["diagnosticCode", "name", "policyCode"]) &&
                testCase.name === expectedResolutionNames[index] &&
                testCase.policyCode === expectedResolutionCases[index] &&
                testCase.diagnosticCode === 900001 + index,
            `Resolution fixture ${index} drifted.`,
        );
    });
    assert(
        exactJson(fixture.cleanSuccessors, [
            {
                name: "controlled-program-after-configuration-failures",
                kind: "controlled-program",
            },
            {
                name: "relative-resolution-after-resolution-failures",
                kind: "snapshot-resolution",
            },
        ]) &&
            fixture.cleanSuccessors.every((successor) =>
                exactKeys(successor, ["kind", "name"]),
            ),
        "X3 clean-successor contract drifted.",
    );
};
validateFixture();

if (!global.crypto) {
    Object.defineProperty(global, "crypto", {
        configurable: true,
        value: cryptoModule.webcrypto,
    });
}

const authorityLedger = [];
const appendLedger = (kind, detail = {}) => {
    authorityLedger.push(Object.freeze({ kind, ...detail }));
};
const networkGlobals = Object.freeze([
    "EventSource",
    "WebSocket",
    "XMLHttpRequest",
    "fetch",
]);
const poisonNetwork = (name) => function forbiddenNetworkAuthority() {
    appendLedger("network", { name });
    throw new Error(`Worker accessed forbidden network authority ${name}.`);
};
for (const name of networkGlobals) {
    Object.defineProperty(global, name, {
        configurable: true,
        value: poisonNetwork(name),
        writable: true,
    });
}
Object.defineProperty(global, "navigator", {
    configurable: true,
    value: Object.freeze({
        sendBeacon: poisonNetwork("navigator.sendBeacon"),
    }),
    writable: true,
});

let tsSystemAccessCount = 0;
const poisonedTsSystem = new Proxy(realTypeScript.sys, {
    get(_target, property) {
        tsSystemAccessCount += 1;
        appendLedger("ts.sys", { property: String(property) });
        throw new Error("Worker accessed forbidden TypeScript system I/O.");
    },
});
let defaultResolverAccessCount = 0;
const poisonDefaultResolver = (name) => function forbiddenDefaultResolver() {
    defaultResolverAccessCount += 1;
    appendLedger("default-resolver", { name });
    throw new Error(`Worker accessed forbidden TypeScript resolver ${name}.`);
};

let activeSnapshotPaths = null;
let readLedgerPhase = "bootstrap";
let programConstructionCount = 0;
let failureProgramConstructionCount = 0;
let emitCount = 0;
let positiveSnapshotReadCount = 0;
let positiveOutsideSnapshotReadCount = 0;
let positiveOrdinaryPackageReadCount = 0;
const observedHosts = [];

const snapshotDirectoriesFor = (paths) => {
    const directories = new Set(["/"]);
    for (const filePath of paths) {
        const segments = filePath.split("/").filter(Boolean);
        segments.pop();
        let current = "";
        for (const segment of segments) {
            current += `/${segment}`;
            directories.add(current);
        }
    }
    return directories;
};
const observeHostRead = (authority, method, arguments_, result) => {
    const requestedPath = arguments_[0];
    const filePositive =
        (method === "fileExists" && result === true) ||
        (method === "readFile" && typeof result === "string") ||
        (method === "getSourceFile" && result !== undefined);
    const directoryPositive = method === "directoryExists" && result === true;
    const directoryListPositive =
        method === "getDirectories" &&
        Array.isArray(result) &&
        result.length !== 0;
    const realpathPositive = method === "realpath" && typeof result === "string";
    const positive =
        filePositive ||
        directoryPositive ||
        directoryListPositive ||
        realpathPositive;
    const allowed = filePositive
        ? authority.paths.has(requestedPath)
        : directoryPositive || directoryListPositive
          ? authority.directories.has(requestedPath)
          : realpathPositive
            ? authority.paths.has(requestedPath) ||
              authority.directories.has(requestedPath)
            : true;
    appendLedger("snapshot-read", {
        allowed,
        method,
        path: typeof requestedPath === "string" ? requestedPath : "<invalid>",
        phase: readLedgerPhase,
        positive,
    });
    if (!positive) return;
    if (
        typeof requestedPath === "string" &&
        requestedPath.startsWith("/packages/")
    ) {
        positiveOrdinaryPackageReadCount += 1;
    }
    if (allowed) {
        positiveSnapshotReadCount += 1;
    } else {
        positiveOutsideSnapshotReadCount += 1;
        throw new Error(
            `${method} returned a positive result outside the accepted snapshot: ` +
                `${String(requestedPath)}.`,
        );
    }
};
const wrapCompilerHost = (host, snapshotPaths) => {
    const authority = Object.freeze({
        directories: snapshotDirectoriesFor(snapshotPaths),
        paths: new Set(snapshotPaths),
    });
    const wrapped = { ...host };
    for (const method of [
        "directoryExists",
        "fileExists",
        "getDirectories",
        "getSourceFile",
        "readFile",
        "realpath",
    ]) {
        if (typeof host[method] !== "function") continue;
        wrapped[method] = (...arguments_) => {
            const result = host[method](...arguments_);
            observeHostRead(authority, method, arguments_, result);
            return result;
        };
    }
    observedHosts.push(Object.freeze({ authority, host: wrapped }));
    return wrapped;
};

const typeScriptFacade = Object.create(realTypeScript);
Object.defineProperty(typeScriptFacade, "sys", { value: poisonedTsSystem });
for (const name of [
    "classicNameResolver",
    "getAutomaticTypeDirectiveNames",
    "nodeModuleNameResolver",
    "resolveModuleName",
    "resolveTypeReferenceDirective",
]) {
    Object.defineProperty(typeScriptFacade, name, {
        value: poisonDefaultResolver(name),
    });
}
Object.defineProperty(typeScriptFacade, "createIncrementalProgram", {
    value: (configuration) => {
        programConstructionCount += 1;
        appendLedger("program", { ordinal: programConstructionCount });
        assert(
            activeSnapshotPaths instanceof Set && activeSnapshotPaths.size > 0,
            "Program construction had no accepted snapshot authority.",
        );
        const observedHost = wrapCompilerHost(
            configuration.host,
            activeSnapshotPaths,
        );
        const builder = realTypeScript.createIncrementalProgram({
            ...configuration,
            host: observedHost,
        });
        return new Proxy(builder, {
            get(target, property) {
                if (property === "emit") {
                    return (...arguments_) => {
                        emitCount += 1;
                        appendLedger("emit", { ordinal: emitCount });
                        return target.emit(...arguments_);
                    };
                }
                const value = Reflect.get(target, property, target);
                return typeof value === "function" ? value.bind(target) : value;
            },
        });
    },
});
global.ts = typeScriptFacade;

let importedScriptCount = 0;
global.importScripts = (specifier) => {
    importedScriptCount += 1;
    appendLedger("import-script", { specifier });
    assert(
        specifier === "typescript.js" && importedScriptCount === 1,
        "Worker imported an unexpected or repeated compiler script.",
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
        publishedMessages[0].tsVersion === expectedVersion &&
        realTypeScript.version === expectedVersion &&
        importedScriptCount === 1,
    "Worker did not publish the pinned TypeScript 6.0.3 authority.",
);
publishedMessages.length = 0;

const workerBinding = (expression) =>
    vm.runInThisContext(expression, {
        filename: `${workerPath}#r2-m2-x3-stable-failures`,
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
            !messages.some(
                (message) =>
                    message.kind === "projectError" ||
                    message.kind === "projectConfigError",
            ),
        `${label} did not publish the exact ACK: ${JSON.stringify(messages)}.`,
    );
};
const expectConfigError = (messages, operation, testCase) => {
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
            messages[0].requestId === operation.requestId &&
            messages[0].policyCode === testCase.policyCode &&
            messages[0].stage === "config" &&
            messages[0].outcome === "diagnostics" &&
            messages[0].errorCode === fixture.callerErrorCode &&
            messages[0].emittedModuleCount === 0 &&
            typeof messages[0].message === "string" &&
            messages[0].message.length <= 4096 &&
            !messages.some((message) => message.kind === "projectInputAck"),
        `${testCase.name} did not publish its exact typed config terminal: ` +
            `${JSON.stringify(messages)}.`,
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
const cleanFiles = Object.freeze([
    Object.freeze({ path: "/declarations/lib.d.ts", text: minimalLibrary }),
    Object.freeze({
        path: "/sources/main.ts",
        text: "export const answer: number = 42;\n",
    }),
]);
const selectionFiles = Object.freeze([
    ...cleanFiles,
    Object.freeze({
        path: "/sources/other.ts",
        text: "export const other: number = 7;\n",
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

let requestCounter = 0;
const configBytesFor = (testCase) =>
    testCase.sourceEncoding === "base64"
        ? Buffer.from(testCase.source, "base64")
        : Buffer.from(testCase.source, "utf8");
const createOperation = (configBytes, files) => {
    requestCounter += 1;
    const records = files.map((file) => {
        const bytes = Buffer.from(file.text, "utf8");
        return Object.freeze({
            bytes,
            path: file.path,
            sha256: sha256(bytes),
        });
    });
    return {
        configBytes,
        inputAuthority: projectInputAuthority({
            configRecord: inputRecord("config", "tsconfig.json", configBytes),
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
        requestId: `r2-m2-x3-${requestCounter}`,
        sequence: 0,
    };
};
const startFrame = (operation) => ({
    configByteLength: operation.configBytes.length,
    configKind: controlledPolicy.configKind,
    configPolicyFingerprint: controlledPolicy.configPolicyFingerprint,
    configPolicyRevision: controlledPolicy.configPolicyRevision,
    configSchemaRevision: controlledPolicy.configSchemaRevision,
    configSha256: sha256(operation.configBytes),
    entryPath: "/sources/main.ts",
    ...operation.inputAuthority,
    kind: "projectInputStart",
    profileRevision: controlledPolicy.profileRevision,
    profileSchemaRevision: controlledPolicy.profileSchemaRevision,
    requestId: operation.requestId,
    sequence: operation.sequence,
    targetProfile: controlledPolicy.targetProfile,
    workerSpinMilliseconds: 0,
    workerSpinPhase: "none",
});
const sendStart = async (operation) => {
    const messages = await dispatch(startFrame(operation));
    expectAck(messages, operation.requestId, operation.sequence, "project start");
    operation.sequence += 1;
};
const sendConfig = async (operation, expectedFailure = null) => {
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
    if (operation.configBytes.length !== 0) {
        messages = await dispatch({
            chunkIndex: 0,
            data: operation.configBytes.toString("base64"),
            kind: "projectInputConfigChunk",
            requestId: operation.requestId,
            sequence: operation.sequence,
        });
        expectAck(messages, operation.requestId, operation.sequence, "config chunk");
        operation.sequence += 1;
    }
    messages = await dispatch({
        chunkCount: operation.configBytes.length === 0 ? 0 : 1,
        kind: "projectInputConfigEnd",
        requestId: operation.requestId,
        sequence: operation.sequence,
    });
    if (expectedFailure !== null) {
        expectConfigError(messages, operation, expectedFailure);
        return false;
    }
    expectAck(messages, operation.requestId, operation.sequence, "config end");
    operation.sequence += 1;
    return true;
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
        expectAck(
            messages,
            operation.requestId,
            operation.sequence,
            `${record.path} start`,
        );
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
        expectAck(
            messages,
            operation.requestId,
            operation.sequence,
            `${record.path} end`,
        );
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
    assert(start, "Clean successor did not begin a framed result.");
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
                "Clean successor did not finish its framed result.",
            );
        }
    }
    const bytes = Buffer.concat(chunks);
    assert(
        bytes.length === start.byteLength && sha256(bytes) === start.sha256,
        "Clean successor result bytes do not match their declared identity.",
    );
    assertWorkerIdle("clean controlled successor result");
    return JSON.parse(bytes.toString("utf8"));
};

const verifyConfigurationFailures = async () => {
    for (const testCase of fixture.configurationFailures) {
        const constructionCount = programConstructionCount;
        const operation = createOperation(
            configBytesFor(testCase),
            selectionFiles,
        );
        await sendStart(operation);
        if (testCase.boundary === "config-end") {
            await sendConfig(operation, testCase);
        } else {
            await sendConfig(operation);
            await sendFiles(operation);
            const messages = await sendInputEnd(operation);
            expectConfigError(messages, operation, testCase);
            assert(
                !messages.some(
                    (message) =>
                        message.kind === "projectResultStart" ||
                        (message.kind === "projectPhase" &&
                            message.phase === "parse"),
                ),
                `${testCase.name} crossed the pre-Program root-selection boundary.`,
            );
        }
        if (programConstructionCount !== constructionCount) {
            failureProgramConstructionCount +=
                programConstructionCount - constructionCount;
        }
        assert(
            programConstructionCount === constructionCount && emitCount === 0,
            `${testCase.name} crossed the pre-Program/no-emit boundary.`,
        );
        assertWorkerIdle(testCase.name);
    }
};

const verifyControlledCleanSuccessor = async () => {
    const operation = createOperation(Buffer.from("{}", "utf8"), cleanFiles);
    await sendStart(operation);
    await sendConfig(operation);
    await sendFiles(operation);
    activeSnapshotPaths = new Set(operation.records.map((record) => record.path));
    readLedgerPhase = "clean-controlled-program";
    const result = await readFramedResult(
        operation,
        await sendInputEnd(operation),
    );
    activeSnapshotPaths = null;
    assert(
        result.compilerApi === "createIncrementalProgram" &&
            result.configKind === "tsconfig" &&
            result.configSchemaRevision === 2 &&
            result.targetProfile === "rhino" &&
            result.tsVersion === expectedVersion &&
            result.diagnostics.length === 0 &&
            result.emitSkipped === false &&
            exactJson(result.rootNames, ["/sources/main.ts"]) &&
            exactJson(
                result.outputs.map((output) => output.path),
                ["/outputs/main.js", "/outputs/main.js.map"],
            ),
        "Controlled clean successor result drifted.",
    );
    assert(
        programConstructionCount === 1 && emitCount === 1,
        "Controlled clean successor did not construct and emit exactly once.",
    );
    const retainedHost = observedHosts.at(-1)?.host;
    assert(retainedHost, "Controlled clean successor did not expose its host oracle.");
    readLedgerPhase = "post-release";
    assert(
        retainedHost.fileExists("/sources/main.ts") === false &&
            retainedHost.readFile("/sources/main.ts") === undefined &&
            retainedHost.getSourceFile(
                "/sources/main.ts",
                realTypeScript.ScriptTarget.ES2018,
            ) === undefined,
        "Released compiler host retained accepted snapshot text.",
    );
};

const deepFreeze = (value) => {
    if (value !== null && typeof value === "object" && !Object.isFrozen(value)) {
        Reflect.ownKeys(value).forEach((key) => deepFreeze(value[key]));
        Object.freeze(value);
    }
    return value;
};
const metadataRecord = (archivePath, root) => {
    const text = JSON.stringify(root);
    return deepFreeze({
        boundaryPath:
            archivePath === "package.json"
                ? "/"
                : `/${archivePath.slice(0, -"/package.json".length)}`,
        path: archivePath,
        root: deepFreeze(root),
        sha256: sha256(text),
        size: Buffer.byteLength(text, "utf8"),
    });
};
const catalog = (dependencyRoots, ...records) => {
    const sortedRoots = dependencyRoots.slice().sort(compareCodeUnits);
    return deepFreeze({
        dependencyRoots: sortedRoots,
        inventoryFingerprint: sha256(
            JSON.stringify({
                records: records.map((record) => record.path),
                sortedRoots,
            }),
        ),
        records,
    });
};

const verifyResolutionFailures = () => {
    const resolveSnapshot = workerBinding("resolveControlledNodeSnapshot");
    const failureCodes = workerBinding("controlledNodeResolutionFailureCodes");
    const diagnosticCodes = workerBinding(
        "controlledNodeResolutionFailureDiagnosticCodes",
    );
    const modes = workerBinding("controlledNodeResolutionModes");
    const targetKinds = workerBinding("controlledNodeResolutionTargetKinds");
    assert(
        exactJson(Object.values(failureCodes), expectedResolutionCases),
        "Worker resolver failure catalog drifted from the X3 fixture.",
    );
    assert(
        exactJson(
            expectedResolutionCases.map((code) => diagnosticCodes[code]),
            fixture.resolutionFailures.map((testCase) => testCase.diagnosticCode),
        ),
        "Worker resolver diagnostic catalog drifted from the X3 fixture.",
    );
    const compilerDiagnosticCodes = new Set(
        Object.values(realTypeScript.Diagnostics)
            .map((diagnostic) => diagnostic?.code)
            .filter(Number.isSafeInteger),
    );
    assert(
        fixture.resolutionFailures.every(
            (testCase) => !compilerDiagnosticCodes.has(testCase.diagnosticCode),
        ),
        "X3 resolver diagnostic range collides with TypeScript diagnostics.",
    );

    const target = (targetPath, kind) => deepFreeze({ kind, path: targetPath });
    const source = (targetPath) => target(targetPath, targetKinds.projectSource);
    const declaration = (targetPath) =>
        target(targetPath, targetKinds.declaration);
    const targets = (...values) => deepFreeze(values);
    const request = (
        specifier,
        {
            conditions = [],
            containingPath = "/sources/main.ts",
            mode = modes.import,
        } = {},
    ) =>
        deepFreeze({
            conditions: conditions.slice(),
            containingPath,
            mode,
            specifier,
        });
    const resolve = (metadata, inventory, specifier, options) =>
        resolveSnapshot(request(specifier, options), metadata, inventory);
    const relativeMetadata = catalog([], metadataRecord("package.json", {}));
    const relativeTargets = targets(
        source("/sources/nested/main.ts"),
        source("/sources/util.ts"),
    );

    const evaluate = (name) => {
        switch (name) {
            case "invalid-containing-path":
                return resolve(relativeMetadata, relativeTargets, "#app", {
                    containingPath: "sources/main.ts",
                });
            case "invalid-specifier":
                return resolve(relativeMetadata, relativeTargets, "bad\\specifier");
            case "invalid-condition":
                return resolve(relativeMetadata, relativeTargets, "#app", {
                    conditions: ["custom", "custom"],
                });
            case "ambiguous-case":
                return resolve(
                    relativeMetadata,
                    targets(source("/sources/A.ts"), source("/sources/a.ts")),
                    "./a.ts",
                );
            case "package-not-allowed":
                return resolve(relativeMetadata, relativeTargets, "other");
            case "package-metadata-missing":
                return resolve(catalog([]), targets(), "#app");
            case "package-metadata-invalid": {
                const metadata = catalog(
                    ["packages/numeric"],
                    metadataRecord("package.json", {}),
                    metadataRecord("packages/numeric/package.json", {
                        exports: {
                            "4294967294": "./index.d.ts",
                            default: "./fallback.d.ts",
                        },
                    }),
                );
                return resolve(
                    metadata,
                    targets(declaration("/packages/numeric/fallback.d.ts")),
                    "numeric",
                );
            }
            case "imports-not-defined":
                return resolve(relativeMetadata, relativeTargets, "#missing");
            case "imports-no-match":
                return resolve(
                    catalog(
                        [],
                        metadataRecord("package.json", {
                            imports: { "#known": "./sources/util.ts" },
                        }),
                    ),
                    relativeTargets,
                    "#missing",
                );
            case "exports-not-defined":
                return resolve(
                    catalog(
                        ["packages/empty"],
                        metadataRecord("package.json", {}),
                        metadataRecord("packages/empty/package.json", {}),
                    ),
                    targets(),
                    "empty",
                );
            case "exports-no-match":
                return resolve(
                    catalog(
                        ["packages/nomatch"],
                        metadataRecord("package.json", {}),
                        metadataRecord("packages/nomatch/package.json", {
                            exports: { "./known": "./known.d.ts" },
                        }),
                    ),
                    targets(),
                    "nomatch/unknown",
                );
            case "condition-no-match":
                return resolve(
                    catalog(
                        ["packages/condition"],
                        metadataRecord("package.json", {}),
                        metadataRecord("packages/condition/package.json", {
                            exports: { browser: "./browser.d.ts" },
                        }),
                    ),
                    targets(),
                    "condition",
                );
            case "invalid-target":
                return resolve(
                    catalog(
                        ["packages/invalid"],
                        metadataRecord("package.json", {}),
                        metadataRecord("packages/invalid/package.json", {
                            exports: "other",
                        }),
                    ),
                    targets(),
                    "invalid",
                );
            case "target-blocked":
                return resolve(
                    catalog(
                        ["packages/null"],
                        metadataRecord("package.json", {}),
                        metadataRecord("packages/null/package.json", {
                            exports: null,
                        }),
                    ),
                    targets(),
                    "null",
                );
            case "target-escape":
                return resolve(
                    catalog(
                        ["packages/escape"],
                        metadataRecord("package.json", {}),
                        metadataRecord("packages/escape/package.json", {
                            exports: "./../escape.d.ts",
                        }),
                    ),
                    targets(),
                    "escape",
                );
            case "target-not-in-snapshot":
                return resolve(
                    catalog(
                        ["packages/missing"],
                        metadataRecord("package.json", {}),
                        metadataRecord("packages/missing/package.json", {
                            exports: "./missing.d.ts",
                        }),
                    ),
                    targets(),
                    "missing",
                );
            case "unsupported-source-kind":
                return resolve(
                    relativeMetadata,
                    targets(
                        target(
                            "/packages/pkg/index.mts",
                            targetKinds.projectSource,
                        ),
                    ),
                    "./missing.ts",
                );
            case "cycle":
                return resolve(
                    catalog(
                        [],
                        metadataRecord("package.json", {
                            imports: { "#one": "#two", "#two": "#one" },
                        }),
                    ),
                    targets(),
                    "#one",
                );
            default:
                throw new Error(`Unknown X3 resolution fixture ${name}.`);
        }
    };

    const ledgerCount = authorityLedger.length;
    const programCount = programConstructionCount;
    const emitCountBefore = emitCount;
    for (const testCase of fixture.resolutionFailures) {
        const result = evaluate(testCase.name);
        assert(
            result.kind === "failure" &&
                result.code === testCase.policyCode &&
                Object.isFrozen(result) &&
                Object.isFrozen(result.matchedConditions),
            `${testCase.name} did not fail immutably with ` +
                `${testCase.policyCode}: ${JSON.stringify(result)}.`,
        );
    }
    const successor = resolve(
        relativeMetadata,
        targets(source("/sources/dep.ts")),
        "./dep.ts",
    );
    assert(
        successor.kind === "resolved" &&
            successor.target.path === "/sources/dep.ts" &&
            Object.isFrozen(successor) &&
            Object.isFrozen(successor.target) &&
            Object.isFrozen(successor.matchedConditions),
        "Snapshot resolver clean successor did not resolve immutably.",
    );
    assert(
        authorityLedger.length === ledgerCount &&
            programConstructionCount === programCount &&
            emitCount === emitCountBefore,
        "Pure snapshot resolver touched host reads, poison authority, Program, or emit.",
    );

    const kernelStart = workerSource.indexOf(
        "const controlledNodeResolutionFailureCodes = Object.freeze({",
    );
    const kernelEnd = workerSource.indexOf(
        "const parseControlledPackageMetadataCanonical = async (",
        kernelStart,
    );
    assert(
        kernelStart >= 0 && kernelEnd > kernelStart,
        "Worker snapshot resolver kernel is not statically bounded.",
    );
    const kernelSource = workerSource.slice(kernelStart, kernelEnd);
    for (const forbidden of [
        /\bts\.sys\b/,
        /\bfetch\s*\(/,
        /XMLHttpRequest/,
        /WebSocket/,
        /\brequire\s*\(/,
        /createIncrementalProgram/,
        /resolveModuleNameLiterals/,
    ]) {
        assert(
            !forbidden.test(kernelSource),
            `Worker snapshot resolver kernel contains escape ${forbidden}.`,
        );
    }
};

(async () => {
    const workerConfigurationCodes = workerBinding(
        "controlledTsConfigPolicyCodes",
    );
    assert(
        exactJson(
            sorted(Object.values(workerConfigurationCodes)),
            sorted(expectedConfigurationCases.map((item) => item[1])),
        ),
        "Worker configuration failure catalog drifted from the X3 fixture.",
    );
    await verifyConfigurationFailures();
    assert(
        programConstructionCount === 0 &&
            failureProgramConstructionCount === 0 &&
            emitCount === 0,
        "A configuration failure crossed the pre-Program boundary.",
    );
    await verifyControlledCleanSuccessor();
    verifyResolutionFailures();

    const networkAccessCount = authorityLedger.filter(
        (entry) => entry.kind === "network",
    ).length;
    const positiveOutsideLedgerCount = authorityLedger.filter(
        (entry) =>
            entry.kind === "snapshot-read" &&
            entry.positive === true &&
            entry.allowed === false,
    ).length;
    assert(
        positiveSnapshotReadCount > 0 &&
            positiveOutsideSnapshotReadCount === 0 &&
            positiveOutsideLedgerCount === 0 &&
            positiveOrdinaryPackageReadCount === 0,
        "Compiler host read ledger did not remain snapshot-only.",
    );
    assert(
        tsSystemAccessCount === 0 &&
            defaultResolverAccessCount === 0 &&
            networkAccessCount === 0 &&
            importedScriptCount === 1 &&
            programConstructionCount === 1 &&
            emitCount === 1,
        "X3 poison or clean-successor counters drifted.",
    );
    for (const forbiddenReference of [
        "EventSource",
        "WebSocket",
        "XMLHttpRequest",
        "fetch",
        "navigator.sendBeacon",
    ]) {
        assert(
            !workerSource.includes(forbiddenReference),
            `Worker references forbidden network authority ${forbiddenReference}.`,
        );
    }
    assert(
        !/\brequire\s*\(/.test(workerSource) &&
            !workerSource.includes("ts.sys"),
        "Worker contains a host filesystem bridge.",
    );

    process.stdout.write(
        "R2-M2-X3 stable configuration/resolution failures verified: " +
            "config=11/11, resolution=18/18, clean successors=2, " +
            "failure Program=0, outside-snapshot positive reads=0, " +
            "ts.sys/default resolver/network=0\n",
    );
})().catch((error) => {
    process.stderr.write(`${error.stack || error}\n`);
    process.exitCode = 1;
});
