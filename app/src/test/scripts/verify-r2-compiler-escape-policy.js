"use strict";

const cryptoModule = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const {
    projectInputAuthority,
    record: inputRecord,
} = require("./r2-input-archive-authority.js");

const [
    workerArgument,
    compilerArgument,
    fixtureArgument,
    policyArgument,
    expectedVersion,
] = process.argv.slice(2);
if (
    !workerArgument ||
    !compilerArgument ||
    !fixtureArgument ||
    !policyArgument ||
    !expectedVersion
) {
    throw new Error(
        "Usage: node verify-r2-compiler-escape-policy.js " +
            "<worker.js> <typescript.js> <escape-policy-v2.json> " +
            "<controlled-policy-v2.json> <expected-version>",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const fixture = JSON.parse(
    fs.readFileSync(path.resolve(fixtureArgument), "utf8"),
);
const controlledPolicy = JSON.parse(
    fs.readFileSync(path.resolve(policyArgument), "utf8"),
);
const workerSource = fs.readFileSync(workerPath, "utf8");
const compilerHostSource = fs.readFileSync(
    path.join(path.dirname(workerPath), "compiler-host.js"),
    "utf8",
);

const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const sha256 = (value) =>
    cryptoModule.createHash("sha256").update(value).digest("hex");
const emptySha256 = sha256(Buffer.alloc(0));
const sorted = (values) => Array.from(values).sort();
const exactJson = (left, right) => JSON.stringify(left) === JSON.stringify(right);
const exactKeys = (value, expected) =>
    exactJson(sorted(Object.keys(value)), sorted(expected));
const callbackTransformerNames = Object.freeze([
    "after",
    "afterDeclarations",
    "before",
]);
const representativeFrameInjectionNames = Object.freeze([
    "customTransformers",
    "fetch",
    "plugins",
    "typeRoots",
    "hostPath",
]);
const expectedExactFrameInjectionKinds = Object.freeze([
    "projectInputStart",
    "projectInputConfigStart",
    "projectInputConfigChunk",
    "projectInputConfigEnd",
    "projectInputBatch",
    "projectInputFileStart",
    "projectInputFileChunk",
    "projectInputFileEnd",
    "projectInputEnd",
    "projectResultAck",
]);
const coveredExactFrameInjectionKinds = new Set();
const recordExactFrameInjection = (kind) => {
    assert(
        expectedExactFrameInjectionKinds.includes(kind) &&
            !coveredExactFrameInjectionKinds.has(kind),
        `Exact-frame injection coverage is duplicated or unknown: ${kind}.`,
    );
    coveredExactFrameInjectionKinds.add(kind);
};

const fixtureFields = [
    "binderMetadataMode",
    "configPolicyFingerprint",
    "customTransformerOptionNames",
    "deferredNodeSourceSuffixes",
    "escapeForbiddenCompilerOptions",
    "fingerprint",
    "fixedWorkerImportScript",
    "forbiddenPackageFileNames",
    "forbiddenPackageMetadataKeys",
    "forbiddenPackagePathSegments",
    "forbiddenWorkerNetworkGlobals",
    "hostIoMode",
    "inlineCompilerOptionsJson",
    "revision",
    "schema",
];
assert(exactKeys(fixture, fixtureFields), "Escape-policy fixture shape drifted.");
assert(
    fixture.schema === "autojs6.typescript.compiler-escape-policy" &&
        fixture.revision === 2,
    "Escape-policy fixture identity drifted.",
);
for (const [field, values] of Object.entries({
    customTransformerOptionNames: fixture.customTransformerOptionNames,
    deferredNodeSourceSuffixes: fixture.deferredNodeSourceSuffixes,
    escapeForbiddenCompilerOptions: fixture.escapeForbiddenCompilerOptions,
    forbiddenPackageFileNames: fixture.forbiddenPackageFileNames,
    forbiddenPackageMetadataKeys: fixture.forbiddenPackageMetadataKeys,
    forbiddenPackagePathSegments: fixture.forbiddenPackagePathSegments,
    forbiddenWorkerNetworkGlobals: fixture.forbiddenWorkerNetworkGlobals,
})) {
    assert(exactJson(values, sorted(values)), `${field} is not strictly sorted.`);
    assert(new Set(values).size === values.length, `${field} is not unique.`);
}

const canonicalInput =
    "autojs6.typescript.compiler-escape-policy.v2" +
    `|revision=${fixture.revision}` +
    `|configPolicyFingerprint=${fixture.configPolicyFingerprint}` +
    `|inlineCompilerOptionsJson=${fixture.inlineCompilerOptionsJson}` +
    `|escapeForbidden=${fixture.escapeForbiddenCompilerOptions.join(",")}` +
    `|customTransformers=${fixture.customTransformerOptionNames.join(",")}` +
    `|packageMetadataKeys=${fixture.forbiddenPackageMetadataKeys.join(",")}` +
    `|packageFiles=${fixture.forbiddenPackageFileNames.join(",")}` +
    `|packageSegments=${fixture.forbiddenPackagePathSegments.join(",")}` +
    `|deferredNodeSourceSuffixes=${fixture.deferredNodeSourceSuffixes.join(",")}` +
    `|fixedWorkerImportScript=${fixture.fixedWorkerImportScript}` +
    `|networkGlobals=${fixture.forbiddenWorkerNetworkGlobals.join(",")}` +
    `|binderMetadataMode=${fixture.binderMetadataMode}` +
    `|hostIoMode=${fixture.hostIoMode}`;
assert(
    /^[\x20-\x7e]+$/.test(canonicalInput) &&
        canonicalInput.length === Buffer.byteLength(canonicalInput, "ascii"),
    "Escape-policy canonical input is not ASCII.",
);
assert(canonicalInput.length === 735, "Escape-policy canonical byte count drifted.");
assert(
    sha256(Buffer.from(canonicalInput, "ascii")) === fixture.fingerprint,
    "Escape-policy fixture fingerprint drifted.",
);
assert(
    fixture.configPolicyFingerprint === controlledPolicy.fingerprint,
    "Escape policy drifted from the C1 policy fingerprint.",
);
assert(
    exactJson(
        fixture.escapeForbiddenCompilerOptions,
        controlledPolicy.escapeForbiddenCompilerOptions,
    ) && fixture.escapeForbiddenCompilerOptions.length === 10,
    "Escape option authority drifted from the C1 exact ten-item set.",
);
assert(
    fixture.inlineCompilerOptionsJson === "{}" &&
        fixture.binderMetadataMode ===
            "primitive-additive-no-executable-authority" &&
        fixture.hostIoMode === "snapshot-only-no-ts-sys",
    "Binder or host-I/O authority drifted.",
);

// The fixed compiler import stays the single `typescript.js` alias. The declaration preload
// bundles (startup-latency L2-1) are the only other same-origin scripts the Worker may load; each
// is a literal call so the allowlist is auditable from source.
const trustedWorkerImportScripts = Object.freeze([
    fixture.fixedWorkerImportScript,
    "declarations-common.js",
    "declarations-rhino.js",
    "declarations-node.js",
]);
const importCalls = workerSource.match(/\bimportScripts\s*\([^)]*\)/g) || [];
const workerImportLiterals = importCalls.map((call) => {
    const literal = /^importScripts\(\s*"([^"]+)"\s*\)$/.exec(call);
    assert(literal, `Worker importScripts call is not a string literal: ${call}`);
    return literal[1];
});
assert(
    workerImportLiterals.length === trustedWorkerImportScripts.length &&
        workerImportLiterals.slice().sort().join(",") ===
            trustedWorkerImportScripts.slice().sort().join(","),
    `Worker importScripts authority drifted: ${workerImportLiterals.join(",")}.`,
);
assert(
    /^importScripts\(\s*["']typescript\.js["']\s*\)$/.test(importCalls[0]),
    "Worker must load the trusted typescript.js alias first.",
);
assert(
    fixture.fixedWorkerImportScript === "typescript.js",
    "Fixture fixed Worker import script drifted.",
);
for (const forbiddenReference of [
    "ts.sys",
    "addJavascriptInterface",
    "evaluateJavascript",
]) {
    assert(
        !workerSource.includes(forbiddenReference),
        `Worker references forbidden host/network authority: ${forbiddenReference}.`,
    );
}
assert(!/\brequire\s*\(/.test(workerSource), "Worker contains a CommonJS host bridge.");
assert(!/\beval\s*\(/.test(workerSource), "Worker contains direct eval execution.");
assert(
    !/\b(?:new\s+)?Function\s*\(/.test(workerSource),
    "Worker contains a Function constructor.",
);
assert(
    !/\bimport\s*\(/.test(workerSource),
    "Worker contains a dynamic import expression.",
);
assert(
    (compilerHostSource.match(/new\s+Worker\s*\(/g) || []).length === 1 &&
        /new\s+Worker\s*\(\s*["']compiler-worker\.js["']\s*\)/.test(
            compilerHostSource,
        ),
    "Compiler page does not create exactly the trusted Worker asset.",
);
for (const forbiddenReference of fixture.forbiddenWorkerNetworkGlobals) {
    assert(
        !workerSource.includes(forbiddenReference) &&
            !compilerHostSource.includes(forbiddenReference),
        `Trusted compiler assets reference forbidden network authority: ${forbiddenReference}.`,
    );
}

if (!global.crypto) {
    Object.defineProperty(global, "crypto", { value: cryptoModule.webcrypto });
}
const poisonAccesses = new Map(
    fixture.forbiddenWorkerNetworkGlobals.map((name) => [name, 0]),
);
const poison = (name) => function forbiddenNetworkAccess() {
    poisonAccesses.set(name, poisonAccesses.get(name) + 1);
    throw new Error(`Worker accessed forbidden network global ${name}.`);
};
for (const name of fixture.forbiddenWorkerNetworkGlobals) {
    if (name === "navigator.sendBeacon") continue;
    Object.defineProperty(global, name, {
        configurable: true,
        value: poison(name),
        writable: true,
    });
}
Object.defineProperty(global, "navigator", {
    configurable: true,
    value: Object.freeze({ sendBeacon: poison("navigator.sendBeacon") }),
    writable: true,
});

const realTypeScript = require(compilerPath);
let tsSysAccessCount = 0;
const poisonedTsSys = new Proxy(realTypeScript.sys, {
    get() {
        tsSysAccessCount += 1;
        throw new Error("Worker accessed forbidden ts.sys authority.");
    },
});
const emitArgumentCounts = [];
const programConstructionCalls = [];
let programConstructionCount = 0;
const typeScriptFacade = Object.create(realTypeScript);
Object.defineProperty(typeScriptFacade, "sys", { value: poisonedTsSys });
Object.defineProperty(typeScriptFacade, "createIncrementalProgram", {
    value: (...arguments_) => {
        programConstructionCount += 1;
        const configuration = arguments_[0];
        programConstructionCalls.push({
            argumentCount: arguments_.length,
            configurationKeys:
                configuration !== null && typeof configuration === "object"
                    ? sorted(Object.keys(configuration))
                    : null,
            plainObject:
                configuration !== null &&
                typeof configuration === "object" &&
                Object.getPrototypeOf(configuration) === Object.prototype,
        });
        const builder = realTypeScript.createIncrementalProgram(...arguments_);
        return new Proxy(builder, {
            get(target, property) {
                if (property === "emit") {
                    return (...emitArguments) => {
                        emitArgumentCounts.push(emitArguments.length);
                        return target.emit(...emitArguments);
                    };
                }
                const value = Reflect.get(target, property, target);
                return typeof value === "function" ? value.bind(target) : value;
            },
        });
    },
});
global.ts = typeScriptFacade;

const importedScripts = [];
global.importScripts = (specifier) => {
    importedScripts.push(specifier);
    assert(
        specifier === fixture.fixedWorkerImportScript,
        "Worker dynamically imported an untrusted script.",
    );
};
const publishedMessages = [];
global.self = {
    postMessage: (message) => publishedMessages.push(message),
};
vm.runInThisContext(workerSource, { filename: workerPath });
assert(
    importedScripts.length === 1 && importedScripts[0] === "typescript.js",
    "Runtime Worker import script authority drifted.",
);
assert(
    publishedMessages.some(
        (message) =>
            message.kind === "ready" && message.tsVersion === expectedVersion,
    ),
    "Worker did not publish the pinned TypeScript version.",
);
publishedMessages.length = 0;

const workerBinding = (expression) =>
    vm.runInThisContext(expression, {
        filename: `${workerPath}#compiler-escape-policy-test`,
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
    assert(acknowledgements.length === 1, `${label} did not publish one ACK.`);
    assert(
        acknowledgements[0].requestId === requestId &&
            acknowledgements[0].sequence === sequence,
        `${label} ACK identity drifted.`,
    );
};
const expectProjectErrorWithoutAck = (
    messages,
    requestId,
    label,
    expectedFirstLine = null,
) => {
    assert(
        !messages.some((message) => message.kind === "projectInputAck"),
        `${label} published an ACK before rejection.`,
    );
    assert(
        messages.length === 1 &&
            messages[0].kind === "projectError" &&
            messages[0].requestId === requestId,
        `${label} did not publish one project error.`,
    );
    if (expectedFirstLine !== null) {
        assert(
            messages[0].message.split("\n", 1)[0] === expectedFirstLine,
            `${label} project error message drifted.`,
        );
    }
};
const expectConfigError = (messages, requestId, policyCode, label) => {
    assert(
        messages.length === 1 &&
            messages[0].kind === "projectConfigError" &&
            messages[0].requestId === requestId &&
            messages[0].policyCode === policyCode &&
            messages[0].stage === "config" &&
            messages[0].emittedModuleCount === 0,
        `${label} typed config rejection drifted.`,
    );
};

const workerEscapeOptions = sorted(
    workerBinding("controlledTsConfigEscapeForbiddenOptions"),
);
assert(
    exactJson(workerEscapeOptions, fixture.escapeForbiddenCompilerOptions),
    "Worker explicit escape option set drifted from the fixture.",
);
assert(
    exactJson(workerBinding("compilerEscapeSourcePathRejectionCodes"), {
        deferredSourceKind: "deferred-source-kind",
        packageDependencyTree: "package-dependency-tree",
        packageMetadata: "package-metadata",
    }),
    "Worker source-path rejection authority drifted.",
);
const compileProjectSource = workerBinding("compileProject").toString();
const emitProjectBuilderSource = workerBinding("emitProjectBuilder").toString();
assert(
    /\bemitProjectBuilder\(\s*builder,\s*controlledRevision4\s*\?\s*createControlledPathAliasEmitTransformer\(\s*vfs,\s*options,\s*isNodeRevisionTwoProject,?\s*\)\s*:\s*null,?\s*\)/s.test(
        compileProjectSource,
    ) &&
        (workerSource.match(/\bemitProjectBuilder\s*\(/g) ?? []).length === 1,
    "Worker emit transformer is not exclusively derived from the revision-4 alias authority.",
);
assert(
    /beforeTransformer\s*===\s*null\s*\?\s*builder\.emit\(\s*\)\s*:\s*Reflect\.apply\(\s*builder\.emit,\s*builder,/s.test(
        emitProjectBuilderSource,
    ) &&
        /\{\s*before:\s*\[beforeTransformer\]\s*\}/s.test(emitProjectBuilderSource) &&
        !/project|message|request|config|plugin/i.test(
            emitProjectBuilderSource.replace(/emitProjectBuilder/g, ""),
        ),
    "Worker emit helper can obtain transformer authority from caller-controlled state.",
);
assert(
    /const incrementalConfiguration\s*=\s*{\s*host,\s*options,\s*rootNames:\s*vfs\.compilerRootNames,\s*}/s.test(
        compileProjectSource,
    ) &&
        /ts\.createIncrementalProgram\(\s*incrementalConfiguration\s*\)/s.test(
            compileProjectSource,
        ) &&
        (compileProjectSource.match(/ts\.createIncrementalProgram\s*\(/g) ?? []).length === 1,
    "Worker Program construction source shape drifted.",
);

let requestCounter = 0;
const createControlledOperation = (source, sourceText = "") => {
    requestCounter += 1;
    const requestId = `escape-${requestCounter}`;
    const bytes = Buffer.from(source, "utf8");
    const digest = sha256(bytes);
    const sourceBytes = Buffer.from(sourceText, "utf8");
    return {
        bytes,
        digest,
        inputAuthority: projectInputAuthority({
            configRecord: inputRecord("config", "tsconfig.json", bytes),
            fileRecords: [
                inputRecord("source", "/sources/main.ts", sourceBytes),
                inputRecord("declaration", "/declarations/lib.d.ts", ""),
            ],
        }),
        requestId,
        sequence: 0,
        sourceBytes,
    };
};
const controlledStartFrame = (operation, extra = {}) => ({
    configByteLength: operation.bytes.length,
    configKind: "tsconfig",
    configPolicyFingerprint: fixture.configPolicyFingerprint,
    configPolicyRevision: controlledPolicy.revision,
    configSchemaRevision: 2,
    configSha256: operation.digest,
    entryPath: "/sources/main.ts",
    ...operation.inputAuthority,
    kind: "projectInputStart",
    profileRevision: 2,
    profileSchemaRevision: 1,
    requestId: operation.requestId,
    sequence: operation.sequence,
    targetProfile: "rhino",
    workerSpinMilliseconds: 0,
    workerSpinPhase: "none",
    ...extra,
});
const beginControlledStart = async (source, sourceText = "") => {
    const operation = createControlledOperation(source, sourceText);
    const messages = await dispatch(controlledStartFrame(operation));
    expectAck(
        messages,
        operation.requestId,
        operation.sequence,
        `${operation.requestId} start`,
    );
    return { ...operation, sequence: operation.sequence + 1 };
};
const acceptControlledConfigStart = async (operation) => {
    const messages = await dispatch({
        byteLength: operation.bytes.length,
        kind: "projectInputConfigStart",
        path: "tsconfig.json",
        requestId: operation.requestId,
        sequence: operation.sequence,
        sha256: operation.digest,
    });
    expectAck(
        messages,
        operation.requestId,
        operation.sequence,
        `${operation.requestId} config start`,
    );
    return { ...operation, sequence: operation.sequence + 1 };
};
const acceptControlledConfigChunk = async (operation) => {
    const messages = await dispatch({
        chunkIndex: 0,
        data: operation.bytes.toString("base64"),
        kind: "projectInputConfigChunk",
        requestId: operation.requestId,
        sequence: operation.sequence,
    });
    expectAck(
        messages,
        operation.requestId,
        operation.sequence,
        `${operation.requestId} config chunk`,
    );
    return { ...operation, sequence: operation.sequence + 1 };
};
const beginControlledConfig = async (source, sourceText = "") => {
    let operation = await beginControlledStart(source, sourceText);
    operation = await acceptControlledConfigStart(operation);
    return acceptControlledConfigChunk(operation);
};
const assertWorkerProjectStateCleared = (label) => {
    assert(
        workerBinding("framedInput") === null &&
            workerBinding("framedCompilationRequestId") === null &&
            workerBinding("framedResult") === null,
        `${label} retained Worker project state.`,
    );
};
const releaseAcceptedControlledInput = () => {
    workerBinding("releaseProjectInputState")(workerBinding("framedInput"));
    workerBinding("framedInput = null");
};
const assertControlledSuccessorReusable = async (label) => {
    const successor = await beginControlledConfig("{}");
    await endControlledConfig(successor);
    assert(
        workerBinding("framedInput")?.requestId === successor.requestId,
        `${label} did not admit a controlled successor.`,
    );
    releaseAcceptedControlledInput();
    assertWorkerProjectStateCleared(`${label} successor cleanup`);
};
const endControlledConfig = async (operation, expectedPolicyCode = null) => {
    const messages = await dispatch({
        chunkCount: 1,
        kind: "projectInputConfigEnd",
        requestId: operation.requestId,
        sequence: operation.sequence,
    });
    if (expectedPolicyCode === null) {
        expectAck(
            messages,
            operation.requestId,
            operation.sequence,
            `${operation.requestId} config end`,
        );
        return operation.sequence + 1;
    }
    expectConfigError(
        messages,
        operation.requestId,
        expectedPolicyCode,
        operation.requestId,
    );
    assert(workerBinding("framedInput") === null, "Config rejection retained state.");
    return null;
};
const rejectFileStart = async (pathValue, extra = {}, label = pathValue) => {
    const operation = await beginControlledConfig("{}");
    const sequence = await endControlledConfig(operation);
    const messages = await dispatch({
        byteLength: 0,
        fileIndex: 0,
        kind: "projectInputFileStart",
        path: pathValue,
        requestId: operation.requestId,
        sequence,
        sha256: emptySha256,
        ...extra,
    });
    expectProjectErrorWithoutAck(messages, operation.requestId, label);
    assert(workerBinding("framedInput") === null, `${label} retained input state.`);
};
const startFile = async (operation, sequence) => {
    const bytes = operation.sourceBytes;
    let messages = await dispatch({
        byteLength: bytes.length,
        fileIndex: 0,
        kind: "projectInputFileStart",
        path: "/sources/main.ts",
        requestId: operation.requestId,
        sequence,
        sha256: sha256(bytes),
    });
    expectAck(messages, operation.requestId, sequence, "file start");
    return { bytes, sequence: sequence + 1 };
};
const acceptEmptyDeclaration = async (operation, sequence) => {
    let messages = await dispatch({
        byteLength: 0,
        fileIndex: 1,
        kind: "projectInputFileStart",
        path: "/declarations/lib.d.ts",
        requestId: operation.requestId,
        sequence,
        sha256: emptySha256,
    });
    expectAck(messages, operation.requestId, sequence, "declaration start");
    sequence += 1;
    messages = await dispatch({
        chunkCount: 0,
        fileIndex: 1,
        kind: "projectInputFileEnd",
        requestId: operation.requestId,
        sequence,
    });
    expectAck(messages, operation.requestId, sequence, "declaration end");
    return sequence + 1;
};

const totalPoisonAccesses = () =>
    Array.from(poisonAccesses.values()).reduce((sum, count) => sum + count, 0);

const makeProject = (sourceText) => {
    const files = [
        { path: "/declarations/lib.d.ts", text: "" },
        {
            path: "/declarations/lib.es2018.d.ts",
            text:
                "interface Array<T>{length:number;[n:number]:T}" +
                "interface Boolean{} interface Function{} " +
                "interface CallableFunction extends Function{} " +
                "interface NewableFunction extends Function{} " +
                "interface IArguments{length:number;[n:number]:any}" +
                "interface Number{} interface Object{} interface RegExp{} " +
                "interface String{}",
        },
        { path: "/sources/main.ts", text: sourceText },
    ].map((file) => ({
        ...file,
        byteLength: Buffer.byteLength(file.text, "utf8"),
        tokenCount: workerBinding("countSourceTokens")(file.text),
    }));
    return {
        configKind: "none",
        configSchemaRevision: 1,
        directoryIndex: workerBinding("createDirectoryIndex")(
            files.map((file) => file.path),
        ),
        execution: {
            activePhase: null,
            // Direct compile canaries begin after framed ingress has completed.
            nextPhaseIndex: 1,
            phaseProgress: null,
            requestId: `canary-${++requestCounter}`,
            workerSpinMilliseconds: 0,
            workerSpinPhase: null,
        },
        files,
        pathChars: files.reduce((sum, file) => sum + file.path.length, 0),
        profileRevision: 2,
        profileSchemaRevision: 1,
        rootNames: ["/sources/main.ts"],
        runtimeCapabilitiesFingerprint: workerBinding(
            "provisionalRhinoRevision2ProfileAuthority.runtimeCapabilitiesFingerprint",
        ),
        targetProfile: "rhino",
        tokenCount: files.reduce((sum, file) => sum + file.tokenCount, 0),
        totalBytes: files.reduce((sum, file) => sum + file.byteLength, 0),
    };
};

(async () => {
    assert(realTypeScript.version === expectedVersion, "TypeScript version drifted.");

    {
        const operation = createControlledOperation("{}");
        const messages = await dispatch(
            controlledStartFrame(operation, { plugins: true }),
        );
        expectProjectErrorWithoutAck(
            messages,
            operation.requestId,
            "project-start injection",
        );
        assertWorkerProjectStateCleared("project-start injection");
        recordExactFrameInjection("projectInputStart");
        await assertControlledSuccessorReusable("project-start injection");
    }
    {
        const operation = await beginControlledStart("{}");
        const messages = await dispatch({
            byteLength: operation.bytes.length,
            kind: "projectInputConfigStart",
            path: "tsconfig.json",
            plugins: true,
            requestId: operation.requestId,
            sequence: operation.sequence,
            sha256: operation.digest,
        });
        expectProjectErrorWithoutAck(
            messages,
            operation.requestId,
            "config-start injection",
        );
        assertWorkerProjectStateCleared("config-start injection");
        recordExactFrameInjection("projectInputConfigStart");
        await assertControlledSuccessorReusable("config-start injection");
    }
    {
        let operation = await beginControlledStart("{}");
        operation = await acceptControlledConfigStart(operation);
        const messages = await dispatch({
            chunkIndex: 0,
            data: operation.bytes.toString("base64"),
            kind: "projectInputConfigChunk",
            plugins: true,
            requestId: operation.requestId,
            sequence: operation.sequence,
        });
        expectProjectErrorWithoutAck(
            messages,
            operation.requestId,
            "config-chunk injection",
        );
        assertWorkerProjectStateCleared("config-chunk injection");
        recordExactFrameInjection("projectInputConfigChunk");
        await assertControlledSuccessorReusable("config-chunk injection");
    }
    {
        const operation = await beginControlledStart("{}");
        const messages = await dispatch({
            frames: [{
                byteLength: operation.bytes.length,
                kind: "projectInputConfigStart",
                path: "tsconfig.json",
                requestId: operation.requestId,
                sequence: operation.sequence,
                sha256: operation.digest,
            }],
            kind: "projectInputBatch",
            plugins: true,
            requestId: operation.requestId,
            sequence: operation.sequence,
        });
        expectProjectErrorWithoutAck(
            messages,
            operation.requestId,
            "project-input-batch injection",
        );
        assertWorkerProjectStateCleared("project-input-batch injection");
        recordExactFrameInjection("projectInputBatch");
        await assertControlledSuccessorReusable("project-input-batch injection");
    }
    {
        const operation = await beginControlledStart("{}");
        const frames = Array.from({ length: 5 }, (_, index) => ({
            chunkIndex: index,
            data: "",
            kind: "projectInputConfigChunk",
            requestId: operation.requestId,
            sequence: operation.sequence + index,
        }));
        const messages = await dispatch({
            frames,
            kind: "projectInputBatch",
            requestId: operation.requestId,
            sequence: operation.sequence + frames.length - 1,
        });
        expectProjectErrorWithoutAck(
            messages,
            operation.requestId,
            "project-input-batch payload-chunk cap",
        );
        assert(
            messages[0].message.includes("too many payload chunks"),
            "Five payload chunks did not fail at the independent Worker batch cap.",
        );
        assertWorkerProjectStateCleared("project-input-batch payload-chunk cap");
        await assertControlledSuccessorReusable(
            "project-input-batch payload-chunk cap",
        );
    }
    {
        const operation = await beginControlledConfig("{}");
        const retainedConfigBytes = workerBinding(
            "framedInput.configTransport.bytes",
        );
        assert(
            Array.from(retainedConfigBytes).some((value) => value !== 0),
            "Config-end injection did not retain a non-empty pre-rejection buffer.",
        );
        const messages = await dispatch({
            chunkCount: 1,
            kind: "projectInputConfigEnd",
            plugins: true,
            requestId: operation.requestId,
            sequence: operation.sequence,
        });
        expectProjectErrorWithoutAck(
            messages,
            operation.requestId,
            "config-end injection",
        );
        assert(
            Array.from(retainedConfigBytes).every((value) => value === 0),
            "Config-end injection retained buffered config bytes.",
        );
        assertWorkerProjectStateCleared("config-end injection");
        recordExactFrameInjection("projectInputConfigEnd");
        await assertControlledSuccessorReusable("config-end injection");
    }

    for (const optionName of fixture.escapeForbiddenCompilerOptions) {
        const operation = await beginControlledConfig(
            JSON.stringify({ compilerOptions: { [optionName]: true } }),
        );
        await endControlledConfig(operation, "CONFIG_UNSUPPORTED_FEATURE");
    }
    for (const optionName of fixture.customTransformerOptionNames) {
        const operation = await beginControlledConfig(
            JSON.stringify({ compilerOptions: { [optionName]: true } }),
        );
        await endControlledConfig(operation, "CONFIG_UNKNOWN_PROPERTY");
    }
    for (const callbackName of callbackTransformerNames) {
        const operation = await beginControlledConfig(
            JSON.stringify({ compilerOptions: { [callbackName]: true } }),
        );
        await endControlledConfig(operation);
        assert(
            exactJson(workerBinding("framedInput.controlledConfig.ignoredProperties"), [
                {
                    kind: "compiler-option",
                    known: false,
                    name: callbackName,
                },
            ]),
            `${callbackName} did not remain inert ignored JSON data.`,
        );
        releaseAcceptedControlledInput();
        assertWorkerProjectStateCleared(`${callbackName} ignored-option cleanup`);
    }

    const poisonBeforeRemoteSchema = totalPoisonAccesses();
    const remoteSchema = await beginControlledConfig(
        JSON.stringify({
            $schema: "https://example.invalid/tsconfig.schema.json",
            files: [],
        }),
    );
    let sequence = await endControlledConfig(remoteSchema);
    let file = await startFile(remoteSchema, sequence);
    sequence = file.sequence;
    let messages = await dispatch({
        chunkCount: 0,
        fileIndex: 0,
        kind: "projectInputFileEnd",
        requestId: remoteSchema.requestId,
        sequence,
    });
    expectAck(messages, remoteSchema.requestId, sequence, "remote schema file end");
    sequence += 1;
    sequence = await acceptEmptyDeclaration(remoteSchema, sequence);
    messages = await dispatch({
        fileCount: remoteSchema.inputAuthority.fileCount,
        kind: "projectInputEnd",
        requestId: remoteSchema.requestId,
        sequence,
        totalBytes: remoteSchema.inputAuthority.totalBytes,
    });
    expectConfigError(
        messages,
        remoteSchema.requestId,
        "CONFIG_NO_INPUTS",
        "snapshot-only empty selection",
    );
    assertWorkerProjectStateCleared("snapshot-only empty selection");
    assert(
        totalPoisonAccesses() === poisonBeforeRemoteSchema,
        "Remote $schema attempted network access.",
    );

    const pathAttacks = [
        ["/sources/package.json", "package-metadata"],
        ["/sources/pkg/PACKAGE.JSON", "package-metadata"],
        ["/sources/node_modules/pkg/index.ts", "package-dependency-tree"],
        ["/sources/pkg/Node_Modules/index.ts", "package-dependency-tree"],
        ["/sources/main.cts", "deferred-source-kind"],
        ["/sources/main.CTS", "deferred-source-kind"],
        ["/sources/main.mts", "deferred-source-kind"],
        ["/sources/main.MTS", "deferred-source-kind"],
        ["/declarations/package.json", "package-metadata"],
        ["/declarations/pkg/PACKAGE.JSON", "package-metadata"],
        [
            "/declarations/node_modules/pkg/index.d.ts",
            "package-dependency-tree",
        ],
        [
            "/declarations/pkg/Node_Modules/index.d.ts",
            "package-dependency-tree",
        ],
        ["/declarations/main.cts", "deferred-source-kind"],
        ["/declarations/main.MTS", "deferred-source-kind"],
    ];
    for (const [attackPath, rejectionCode] of pathAttacks) {
        const operation = await beginControlledConfig("{}");
        const fileSequence = await endControlledConfig(operation);
        const attackMessages = await dispatch({
            byteLength: 0,
            fileIndex: 0,
            kind: "projectInputFileStart",
            path: attackPath,
            requestId: operation.requestId,
            sequence: fileSequence,
            sha256: emptySha256,
        });
        expectProjectErrorWithoutAck(attackMessages, operation.requestId, attackPath);
        assert(
            attackMessages[0].message.includes(rejectionCode),
            `${attackPath} did not preserve its rejection code.`,
        );
        assert(workerBinding("framedInput") === null, `${attackPath} retained state.`);
    }
    for (const allowedPath of [
        "/sources/data.json",
        "/sources/script.js",
        "/sources/main.ts",
        "/sources/types.d.ts",
    ]) {
        assert(
            workerBinding("validateCanonicalVfsPath")(allowedPath, ["/sources/"]) ===
                allowedPath,
            `${allowedPath} was over-rejected by the C4 path boundary.`,
        );
    }

    for (const injectedName of representativeFrameInjectionNames) {
        await rejectFileStart(
            "/sources/main.ts",
            { [injectedName]: true },
            `file-start injection ${injectedName}`,
        );
    }
    recordExactFrameInjection("projectInputFileStart");

    {
        const operation = await beginControlledConfig("{}", "x");
        sequence = await endControlledConfig(operation);
        file = await startFile(operation, sequence);
        messages = await dispatch({
            chunkIndex: 0,
            data: file.bytes.toString("base64"),
            fileIndex: 0,
            kind: "projectInputFileChunk",
            plugins: true,
            requestId: operation.requestId,
            sequence: file.sequence,
        });
        expectProjectErrorWithoutAck(messages, operation.requestId, "file-chunk injection");
        assertWorkerProjectStateCleared("file-chunk injection");
        recordExactFrameInjection("projectInputFileChunk");
    }
    {
        const operation = await beginControlledConfig("{}", "x");
        sequence = await endControlledConfig(operation);
        file = await startFile(operation, sequence);
        messages = await dispatch({
            chunkIndex: 0,
            data: file.bytes.toString("base64"),
            fileIndex: 0,
            kind: "projectInputFileChunk",
            requestId: operation.requestId,
            sequence: file.sequence,
        });
        expectAck(messages, operation.requestId, file.sequence, "file chunk");
        messages = await dispatch({
            chunkCount: 1,
            fileIndex: 0,
            hostPath: "C:/escape",
            kind: "projectInputFileEnd",
            requestId: operation.requestId,
            sequence: file.sequence + 1,
        });
        expectProjectErrorWithoutAck(messages, operation.requestId, "file-end injection");
        assertWorkerProjectStateCleared("file-end injection");
        recordExactFrameInjection("projectInputFileEnd");
    }
    {
        const operation = await beginControlledConfig("{}");
        sequence = await endControlledConfig(operation);
        file = await startFile(operation, sequence);
        messages = await dispatch({
            chunkCount: 0,
            fileIndex: 0,
            kind: "projectInputFileEnd",
            requestId: operation.requestId,
            sequence: file.sequence,
        });
        expectAck(messages, operation.requestId, file.sequence, "input-end file end");
        messages = await dispatch({
            fileCount: 1,
            kind: "projectInputEnd",
            requestId: operation.requestId,
            sequence: file.sequence + 1,
            totalBytes: operation.bytes.length,
            typeRoots: true,
        });
        expectProjectErrorWithoutAck(messages, operation.requestId, "input-end injection");
        assertWorkerProjectStateCleared("input-end injection");
        recordExactFrameInjection("projectInputEnd");
    }

    {
        const resultState = workerBinding(`framedResult = ({
            awaitingAck: -1,
            bytes: new Uint8Array([1, 2, 3]),
            chunkCount: 1,
            execution: {},
            requestId: "result-ack-injection",
            sha256: "${"0".repeat(64)}"
        })`);
        messages = await dispatch({
            kind: "projectResultAck",
            plugins: true,
            requestId: resultState.requestId,
            sequence: -1,
        });
        expectProjectErrorWithoutAck(messages, resultState.requestId, "result ACK injection");
        assert(
            Array.from(resultState.bytes).every((value) => value === 0) &&
                workerBinding("framedResult") === null,
            "Rejected result ACK retained result bytes.",
        );
        assertWorkerProjectStateCleared("result ACK injection");
        recordExactFrameInjection("projectResultAck");
        messages = await dispatch({
            hostPath: "C:/late",
            kind: "projectResultAck",
            requestId: "retired-result",
            sequence: -1,
        });
        assert(messages.length === 0, "Late retired result ACK disturbed the Worker.");
    }

    const poisonBeforeSourceCanaries = totalPoisonAccesses();
    const cleanResult = await workerBinding("compileProject")(
        makeProject("export const value: number = 1;"),
    );
    assert(
        cleanResult.diagnostics.every((diagnostic) => diagnostic.category !== 1),
        "Clean source canary did not compile without errors.",
    );
    const maliciousResult = await workerBinding("compileProject")(
        makeProject(
            "declare function require(name:string):unknown;" +
                "declare function fetch(url:string):unknown;" +
                "declare class XMLHttpRequest{} declare class WebSocket{" +
                "constructor(url:string)} declare class EventSource{" +
                "constructor(url:string)} require('child_process');" +
                "fetch('https://example.invalid/');" +
                "new XMLHttpRequest();new WebSocket('wss://example.invalid');" +
                "new EventSource('https://example.invalid/events');" +
                "void import('https://example.invalid/module.js');",
        ),
    );
    assert(
        maliciousResult.diagnostics.length > 0 &&
            maliciousResult.outputs.length === 0,
        "Malicious source canary did not remain diagnostics-only.",
    );
    assert(
        totalPoisonAccesses() === poisonBeforeSourceCanaries &&
            tsSysAccessCount === 0,
        "Source compilation reached network or ts.sys authority.",
    );
    assert(
        programConstructionCount === 2 &&
            programConstructionCalls.length === 2 &&
            programConstructionCalls.every(
                (call) =>
                    call.argumentCount === 1 &&
                    call.plainObject &&
                    exactJson(call.configurationKeys, [
                        "host",
                        "options",
                        "rootNames",
                    ]),
            ) &&
            emitArgumentCounts.length === 1 &&
            emitArgumentCounts[0] === 0,
        "Program/emit canary did not prove transformer-free construction.",
    );

    const successor = await beginControlledConfig("{}");
    await endControlledConfig(successor);
    assert(
        workerBinding("framedInput")?.requestId === successor.requestId,
        "Successor operation was not reusable after escape rejections.",
    );
    releaseAcceptedControlledInput();
    assertWorkerProjectStateCleared("final successor cleanup");

    assert(
        exactJson(
            sorted(coveredExactFrameInjectionKinds),
            sorted(expectedExactFrameInjectionKinds),
        ),
        "Exact-frame injection coverage is incomplete.",
    );

    process.stdout.write(
        `R2-M2-C4 compiler escape policy verified: ` +
            `${fixture.escapeForbiddenCompilerOptions.length} escape options, ` +
            `${fixture.customTransformerOptionNames.length} authority transformer aliases, ` +
            `${callbackTransformerNames.length} inert callback-shaped names, ` +
            `${pathAttacks.length} path attacks, ` +
            `${coveredExactFrameInjectionKinds.size} exact framed shapes, ` +
            `${representativeFrameInjectionNames.length} representative ` +
            `frame-injection names\n`,
    );
})().catch((error) => {
    process.stderr.write(`${error.stack || error}\n`);
    process.exitCode = 1;
});
