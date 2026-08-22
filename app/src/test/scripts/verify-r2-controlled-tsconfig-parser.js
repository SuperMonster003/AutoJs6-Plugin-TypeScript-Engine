"use strict";

const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const {
    descriptorRecord: inputDescriptorRecord,
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
        "Usage: node verify-r2-controlled-tsconfig-parser.js " +
            "<worker.js> <typescript.js> <parser-fixture.json> " +
            "<policy-v1.json> <expected-version>",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const fixture = JSON.parse(fs.readFileSync(path.resolve(fixtureArgument), "utf8"));
const policy = JSON.parse(fs.readFileSync(path.resolve(policyArgument), "utf8"));
const workerSource = fs.readFileSync(workerPath, "utf8");

const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const sortedKeys = (value) => Object.keys(value).sort().join(",");
const sha256 = (bytes) =>
    crypto.createHash("sha256").update(bytes).digest("hex");

if (!global.crypto) {
    Object.defineProperty(global, "crypto", { value: crypto.webcrypto });
}
global.ts = require(compilerPath);
global.importScripts = (specifier) => {
    assert(specifier === "typescript.js", "Worker imported an unexpected script.");
};
const messages = [];
global.self = {
    postMessage: (message) => messages.push(message),
};
vm.runInThisContext(workerSource, { filename: workerPath });
assert(
    messages.some(
        (message) =>
            message.kind === "ready" && message.tsVersion === expectedVersion,
    ),
    "Worker did not publish the pinned TypeScript version.",
);
messages.length = 0;

const workerBinding = (expression) =>
    vm.runInThisContext(expression, {
        filename: `${workerPath}#controlled-config-test`,
    });
const dispatch = async (message) => {
    messages.length = 0;
    await self.onmessage({ data: message });
    return messages.slice();
};
const expectAck = (published, requestId, sequence, label) => {
    const acknowledgements = published.filter(
        (message) => message.kind === "projectInputAck",
    );
    assert(acknowledgements.length === 1, `${label} did not publish one ACK.`);
    assert(
        acknowledgements[0].requestId === requestId &&
            acknowledgements[0].sequence === sequence,
        `${label} published the wrong ACK identity.`,
    );
};
const expectProjectError = (published, requestId, label) => {
    assert(
        published.length === 1 &&
            published[0].kind === "projectError" &&
            published[0].requestId === requestId,
        `${label} did not fail as a transport/project error.`,
    );
};
const expectConfigError = (published, requestId, policyCode, label) => {
    assert(published.length === 1, `${label} published an ambiguous terminal.`);
    const error = published[0];
    assert(
        sortedKeys(error) ===
            [
                "emittedModuleCount",
                "errorCode",
                "kind",
                "message",
                "outcome",
                "policyCode",
                "requestId",
                "stage",
            ].sort().join(","),
        `${label} config error shape drifted.`,
    );
    assert(error.kind === "projectConfigError", `${label} kind drifted.`);
    assert(error.requestId === requestId, `${label} request ID drifted.`);
    assert(error.stage === "config", `${label} stage drifted.`);
    assert(error.outcome === "diagnostics", `${label} outcome drifted.`);
    assert(
        error.errorCode ===
            "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_DIAGNOSTICS",
        `${label} error code drifted.`,
    );
    assert(error.policyCode === policyCode, `${label} policy code drifted.`);
    assert(error.emittedModuleCount === 0, `${label} module count drifted.`);
    assert(
        typeof error.message === "string" && error.message.length <= 4096,
        `${label} message is not bounded.`,
    );
};

let requestCounter = 0;
const beginConfig = async (bytes, options = {}) => {
    requestCounter += 1;
    const requestId = `config-${requestCounter}`;
    const digest = options.declaredSha256 || sha256(bytes);
    const declaredByteLength = options.declaredByteLength ?? bytes.length;
    const inputAuthority = projectInputAuthority({
        configRecord: inputDescriptorRecord({
            byteLength: declaredByteLength,
            domain: "config",
            path: "tsconfig.json",
            sha256: digest,
        }),
        fileRecords: [
            inputRecord("declaration", "/declarations/lib.d.ts", ""),
            inputRecord("source", "/sources/main.ts", ""),
        ],
    });
    let sequence = 0;
    const start = {
        configByteLength: declaredByteLength,
        configKind: "tsconfig",
        configPolicyFingerprint:
            "849f882618d294d61683cc5930ecd4855f53435d7a673ceda08ad127504fa24d",
        configPolicyRevision: 1,
        configSchemaRevision: 2,
        configSha256: digest,
        entryPath: "/sources/main.ts",
        ...inputAuthority,
        kind: "projectInputStart",
        profileRevision: 1,
        profileSchemaRevision: 1,
        requestId,
        sequence,
        targetProfile: "rhino",
        workerSpinMilliseconds: 0,
        workerSpinPhase: "none",
        ...(options.startExtra || {}),
    };
    let published = await dispatch(start);
    if (options.expectStartError) {
        expectProjectError(published, requestId, options.label || requestId);
        assert(workerBinding("framedInput") === null, "Rejected start retained state.");
        return { requestId, rejectedAtStart: true };
    }
    expectAck(published, requestId, sequence, `${requestId} start`);

    sequence += 1;
    published = await dispatch({
        byteLength: declaredByteLength,
        kind: "projectInputConfigStart",
        path: options.path || "tsconfig.json",
        requestId,
        sequence,
        sha256: digest,
        ...(options.configStartExtra || {}),
    });
    if (options.expectConfigStartError) {
        expectProjectError(published, requestId, options.label || requestId);
        assert(workerBinding("framedInput") === null, "Rejected config start retained state.");
        return { requestId, rejectedAtConfigStart: true };
    }
    expectAck(published, requestId, sequence, `${requestId} config start`);

    sequence += 1;
    if (bytes.length !== 0) {
        const chunkBytes = options.chunkBytes || bytes;
        published = await dispatch({
            chunkIndex: 0,
            data: Buffer.from(chunkBytes).toString("base64"),
            kind: "projectInputConfigChunk",
            requestId,
            sequence,
        });
        if (options.expectChunkError) {
            expectProjectError(published, requestId, options.label || requestId);
            assert(workerBinding("framedInput") === null, "Rejected chunk retained state.");
            return { requestId, rejectedAtChunk: true };
        }
        expectAck(published, requestId, sequence, `${requestId} config chunk`);
        sequence += 1;
    }
    const accumulator = workerBinding("framedInput.configTransport.bytes");
    return { accumulator, digest, requestId, sequence };
};

const endConfig = async (operation, expectedPolicyCode = null) => {
    const published = await dispatch({
        chunkCount: operation.accumulator.length === 0 ? 0 : 1,
        kind: "projectInputConfigEnd",
        requestId: operation.requestId,
        sequence: operation.sequence,
    });
    assert(
        Array.from(operation.accumulator).every((value) => value === 0),
        `${operation.requestId} did not zero the config accumulator.`,
    );
    if (expectedPolicyCode !== null) {
        expectConfigError(
            published,
            operation.requestId,
            expectedPolicyCode,
            operation.requestId,
        );
        assert(workerBinding("framedInput") === null, "Config rejection retained state.");
        return null;
    }
    expectAck(
        published,
        operation.requestId,
        operation.sequence,
        `${operation.requestId} config end`,
    );
    return workerBinding("framedInput.controlledConfig");
};

const releaseAcceptedParserOperation = async (operation) => {
    const state = workerBinding("framedInput");
    assert(
        state !== null && state.requestId === operation.requestId,
        "Accepted parser state was not retained through its config-end ACK.",
    );
    workerBinding("releaseProjectInputState")(state);
    workerBinding("framedInput = null");
    assert(workerBinding("framedInput") === null, "Parser cleanup retained input state.");
};

const verifyAccepted = async (testCase) => {
    const operation = await beginConfig(Buffer.from(testCase.source, "utf8"));
    const parsed = await endConfig(operation);
    assert(Object.isFrozen(parsed), `${testCase.name} parsed state is mutable.`);
    assert(
        parsed.configKind === "tsconfig" &&
            parsed.configSchemaRevision === 2 &&
            parsed.configPolicyRevision === 1 &&
            parsed.configPolicyFingerprint ===
                "849f882618d294d61683cc5930ecd4855f53435d7a673ceda08ad127504fa24d",
        `${testCase.name} parsed authority drifted.`,
    );
    assert(
        JSON.stringify(parsed.presentTopLevelKeys) ===
            JSON.stringify(testCase.presentTopLevelKeys),
        `${testCase.name} top-level presence was not retained.`,
    );
    assert(
        JSON.stringify(parsed.selectors.files) ===
            JSON.stringify(testCase.normalizedFiles),
        `${testCase.name} files presence/normalization drifted.`,
    );
    assert(
        Object.keys(parsed.explicitBooleanOptions).length ===
            testCase.explicitOptionCount,
        `${testCase.name} explicit option count drifted.`,
    );
    assert(
        Object.isFrozen(parsed.presentTopLevelKeys) &&
            Object.isFrozen(parsed.explicitBooleanOptions) &&
            Object.isFrozen(parsed.selectors) &&
            ["files", "include", "exclude"].every(
                (key) =>
                    parsed.selectors[key] === null ||
                    Object.isFrozen(parsed.selectors[key]),
            ),
        `${testCase.name} nested parsed state is mutable.`,
    );
    await releaseAcceptedParserOperation(operation);
};

const verifyRejected = async (testCase) => {
    const operation = await beginConfig(Buffer.from(testCase.source, "utf8"));
    await endConfig(operation, testCase.policyCode);
};

const nestedArray = (depth) => {
    let source = "0";
    for (let index = 1; index < depth; index += 1) source = `[${source}]`;
    return source;
};
const nodeBoundary = (childNodes) =>
    `{"unknown":[${Array(childNodes).fill("0").join(",")}]}`;
const selectorCountBoundary = (count) =>
    JSON.stringify({ include: Array(count).fill("src/**/*.ts") });
const selectorByteBoundary = (count) =>
    JSON.stringify({ files: [`${"a".repeat(count - 3)}.ts`] });

const verifyCompilerHostConfigTerminal = () => {
    const hostPath = path.join(path.dirname(workerPath), "compiler-host.js");
    const hostSource = fs.readFileSync(hostPath, "utf8");
    const nativeMessages = [];
    const workers = [];
    let bootstrapListener = null;

    class MockWorker {
        constructor(specifier) {
            assert(specifier === "compiler-worker.js", "Host spawned the wrong Worker.");
            this.posted = [];
            this.terminationCount = 0;
            workers.push(this);
        }

        postMessage(message) {
            this.posted.push(message);
        }

        terminate() {
            this.terminationCount += 1;
        }
    }

    const nativePort = {
        close: () => {},
        onmessage: null,
        postMessage: (encoded) => nativeMessages.push(JSON.parse(encoded)),
        start: () => {},
    };
    const hostSelf = {
        addEventListener: (kind, listener) => {
            assert(kind === "message", "Host registered an unexpected listener.");
            bootstrapListener = listener;
        },
    };
    const context = vm.createContext({
        TextEncoder,
        Worker: MockWorker,
        self: hostSelf,
    });
    vm.runInContext(hostSource, context, { filename: hostPath });
    assert(bootstrapListener !== null, "Host did not register its bootstrap listener.");
    bootstrapListener({
        data: "__autojs_typescript_compiler_port_v1__",
        ports: [nativePort],
    });
    assert(workers.length === 1, "Host did not create its first Worker.");
    const firstWorker = workers[0];
    firstWorker.onmessage({
        data: { kind: "ready", tsVersion: expectedVersion },
    });

    const sendNative = (message) =>
        nativePort.onmessage({ data: JSON.stringify(message) });
    const sendWorker = (message) => firstWorker.onmessage({ data: message });
    const driveToConfigEnd = (requestId) => {
        const configBytes = Buffer.from("{}");
        const inputAuthority = projectInputAuthority({
            configRecord: inputRecord("config", "tsconfig.json", configBytes),
            fileRecords: [
                inputRecord("declaration", "/declarations/lib.d.ts", ""),
                inputRecord("source", "/sources/main.ts", ""),
            ],
        });
        sendNative({
            configByteLength: 2,
            configKind: "tsconfig",
            configPolicyFingerprint:
                "849f882618d294d61683cc5930ecd4855f53435d7a673ceda08ad127504fa24d",
            configPolicyRevision: 1,
            configSchemaRevision: 2,
            configSha256: sha256(configBytes),
            entryPath: "/sources/main.ts",
            ...inputAuthority,
            kind: "projectInputStart",
            profileRevision: 1,
            profileSchemaRevision: 1,
            requestId,
            sequence: 0,
            targetProfile: "rhino",
            workerSpinMilliseconds: 0,
            workerSpinPhase: "none",
        });
        sendWorker({
            kind: "projectPhase",
            phase: "ingress",
            requestId,
            state: "started",
        });
        sendWorker({ kind: "projectInputAck", requestId, sequence: 0 });
        sendNative({
            byteLength: 2,
            kind: "projectInputConfigStart",
            path: "tsconfig.json",
            requestId,
            sequence: 1,
            sha256: sha256(configBytes),
        });
        sendWorker({ kind: "projectInputAck", requestId, sequence: 1 });
        sendNative({
            chunkIndex: 0,
            data: "e30=",
            kind: "projectInputConfigChunk",
            requestId,
            sequence: 2,
        });
        sendWorker({ kind: "projectInputAck", requestId, sequence: 2 });
        sendNative({
            chunkCount: 1,
            kind: "projectInputConfigEnd",
            requestId,
            sequence: 3,
        });
        assert(
            firstWorker.posted.at(-1).kind === "projectInputConfigEnd",
            "Host did not forward the frozen config-end kind.",
        );
    };
    const configError = (requestId) => ({
        emittedModuleCount: 0,
        errorCode: "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_DIAGNOSTICS",
        kind: "projectConfigError",
        message: "Controlled tsconfig was rejected.",
        outcome: "diagnostics",
        policyCode: "CONFIG_SYNTAX_INVALID",
        requestId,
        stage: "config",
    });

    driveToConfigEnd("host-config-valid");
    nativeMessages.length = 0;
    sendWorker(configError("host-config-valid"));
    assert(nativeMessages.length === 1, "Host did not publish one typed terminal.");
    assert(
        nativeMessages[0].kind === "projectConfigError" &&
            nativeMessages[0].generation === 1 &&
            nativeMessages[0].pageHasTypeScript === false,
        "Host typed config terminal lost its page/session identity.",
    );
    assert(
        workers.length === 1 && firstWorker.terminationCount === 0,
        "Valid config rejection unnecessarily replaced the reusable Worker.",
    );

    driveToConfigEnd("host-config-invalid");
    nativeMessages.length = 0;
    sendWorker({
        ...configError("host-config-invalid"),
        unknown: true,
    });
    assert(
        nativeMessages.length === 1 &&
            nativeMessages[0].kind === "projectError",
        "Host did not fail closed on typed-terminal shape drift.",
    );
    assert(
        workers.length === 2 && firstWorker.terminationCount === 1,
        "Host did not replace the Worker after a protocol-invalid terminal.",
    );
};

(async () => {
    assert(
        fixture.schema ===
            "autojs6.typescript.controlled-tsconfig-parser-fixture" &&
            fixture.revision === 1,
        "Controlled-config parser fixture authority drifted.",
    );
    assert(global.ts.version === expectedVersion, "TypeScript version drifted.");
    const workerAuthority = workerBinding("controlledTsConfigAuthority");
    assert(
        workerAuthority.configKind === policy.configKind &&
            workerAuthority.configPath === policy.configPath &&
            workerAuthority.configPolicyRevision === policy.revision &&
            workerAuthority.configPolicyFingerprint === policy.fingerprint &&
            workerAuthority.configSchemaRevision === 2,
        "Worker controlled-config identity drifted from policy-v1.",
    );
    assert(
        workerAuthority.maximumBytes === policy.budgets.maximumBytes &&
            workerAuthority.maximumJsonDepth ===
                policy.budgets.maximumJsonDepth &&
            workerAuthority.maximumJsonNodes ===
                policy.budgets.maximumJsonNodes &&
            workerAuthority.maximumSelectorBytes ===
                policy.budgets.maximumSelectorUtf8Bytes &&
            workerAuthority.maximumSelectors ===
                policy.budgets.maximumSelectors,
        "Worker controlled-config budgets drifted from policy-v1.",
    );
    const assertExactStringSet = (bindingName, expected, label) => {
        const actual = Array.from(workerBinding(bindingName)).sort();
        assert(
            JSON.stringify(actual) === JSON.stringify(Array.from(expected).sort()),
            `${label} drifted from policy-v1.`,
        );
    };
    assertExactStringSet(
        "controlledTsConfigAllowedTopLevel",
        policy.allowedTopLevel,
        "Worker allowed top-level keys",
    );
    assertExactStringSet(
        "controlledTsConfigUnsupportedTopLevel",
        policy.unsupportedTopLevel,
        "Worker unsupported top-level keys",
    );
    assertExactStringSet(
        "controlledTsConfigAllowedBooleanOptions",
        Object.keys(policy.allowedBooleanCompilerOptions),
        "Worker boolean compiler-option allowlist",
    );
    assertExactStringSet(
        "controlledTsConfigProfileManagedOptions",
        policy.profileManagedCompilerOptions,
        "Worker profile-managed compiler options",
    );
    assert(
        Object.keys(policy.allowedBooleanCompilerOptions).length === 18 &&
            policy.profileManagedCompilerOptions.length === 18,
        "C1 compiler-option classification cardinality drifted.",
    );
    assert(
        /finally\s*{\s*bytes\.fill\(0\)/s.test(
            workerBinding("acceptProjectInputConfigChunk").toString(),
        ),
        "Config chunk temporary bytes are not zeroed in a finally block.",
    );
    verifyCompilerHostConfigTerminal();

    for (const testCase of fixture.accepted) await verifyAccepted(testCase);
    for (const testCase of fixture.rejected) await verifyRejected(testCase);

    {
        const operation = await beginConfig(Buffer.from([0xc3, 0x28]));
        await endConfig(operation, "CONFIG_ENCODING_INVALID");
    }

    {
        const operation = await beginConfig(Buffer.from("{}"), {
            declaredSha256: "0".repeat(64),
        });
        const published = await dispatch({
            chunkCount: 1,
            kind: "projectInputConfigEnd",
            requestId: operation.requestId,
            sequence: operation.sequence,
        });
        assert(
            Array.from(operation.accumulator).every((value) => value === 0),
            "Digest failure did not zero the config accumulator.",
        );
        expectProjectError(published, operation.requestId, "config digest mismatch");
    }

    await beginConfig(Buffer.from("{}"), {
        configStartExtra: { unknown: true },
        expectConfigStartError: true,
        label: "config start exact shape",
    });
    await beginConfig(Buffer.from("{}"), {
        expectConfigStartError: true,
        label: "config path identity",
        path: "nested/tsconfig.json",
    });
    await beginConfig(Buffer.from("{}"), {
        chunkBytes: Buffer.from("{"),
        expectChunkError: true,
        label: "short config chunk",
    });

    await verifyRejected({
        name: "depth-16-accepted-by-scanner",
        source: nestedArray(16),
        policyCode: "CONFIG_INVALID_VALUE",
    });
    await verifyRejected({
        name: "depth-17-rejected",
        source: nestedArray(17),
        policyCode: "CONFIG_COMPLEXITY_EXCEEDED",
    });
    await verifyRejected({
        name: "node-4096-accepted-by-scanner",
        source: nodeBoundary(4094),
        policyCode: "CONFIG_UNKNOWN_PROPERTY",
    });
    await verifyRejected({
        name: "node-4097-rejected",
        source: nodeBoundary(4095),
        policyCode: "CONFIG_COMPLEXITY_EXCEEDED",
    });

    {
        const operation = await beginConfig(
            Buffer.from(selectorCountBoundary(512), "utf8"),
        );
        const parsed = await endConfig(operation);
        assert(parsed.selectors.include.length === 1, "Selector dedup drifted.");
        await releaseAcceptedParserOperation(operation);
    }
    await verifyRejected({
        name: "selector-513-rejected",
        source: selectorCountBoundary(513),
        policyCode: "CONFIG_COMPLEXITY_EXCEEDED",
    });
    {
        const operation = await beginConfig(
            Buffer.from(selectorByteBoundary(4096), "utf8"),
        );
        await endConfig(operation);
        await releaseAcceptedParserOperation(operation);
    }
    await verifyRejected({
        name: "selector-4097-bytes-rejected",
        source: selectorByteBoundary(4097),
        policyCode: "CONFIG_PATH_INVALID",
    });

    {
        const maximum = Buffer.from("{}" + " ".repeat(48 * 1024 - 2));
        const operation = await beginConfig(maximum);
        await endConfig(operation);
        await releaseAcceptedParserOperation(operation);
    }
    await beginConfig(Buffer.alloc(48 * 1024 + 1, 0x20), {
        expectStartError: true,
        label: "raw config plus one",
    });

    process.stdout.write(
        `R2-M2-C2 controlled tsconfig parser verified: ` +
            `${fixture.accepted.length} accepted, ${fixture.rejected.length} rejected fixtures\n`,
    );
})().catch((error) => {
    process.stderr.write(`${error.stack || error}\n`);
    process.exitCode = 1;
});
