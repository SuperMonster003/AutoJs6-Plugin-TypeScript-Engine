"use strict";

const cryptoModule = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const {
    projectInputAuthority,
    record: inputRecord,
} = require("./r2-input-archive-authority.js");

const [workerArgument, compilerArgument, fixtureArgument, policyArgument, expectedVersion] =
    process.argv.slice(2);
if (
    !workerArgument ||
    !compilerArgument ||
    !fixtureArgument ||
    !policyArgument ||
    !expectedVersion
) {
    throw new Error(
        "Usage: node verify-r2-package-metadata-transport.js " +
            "<worker.js> <typescript.js> <package-policy-v1.json> " +
            "<controlled-policy-v1.json> <expected-version>",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const fixture = JSON.parse(fs.readFileSync(path.resolve(fixtureArgument), "utf8"));
const controlledPolicy = JSON.parse(
    fs.readFileSync(path.resolve(policyArgument), "utf8"),
);
const workerSource = fs.readFileSync(workerPath, "utf8");

const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const sha256 = (value) =>
    cryptoModule.createHash("sha256").update(value).digest("hex");
const sorted = (values) => Array.from(values).sort();
const exactJson = (left, right) => JSON.stringify(left) === JSON.stringify(right);
const exactKeys = (value, expected) =>
    exactJson(sorted(Object.keys(value)), sorted(expected));

assert(
    exactKeys(fixture, [
        "budgets",
        "dependencyRootPrefix",
        "emptyDependencyRootsExample",
        "fingerprint",
        "forbiddenTopLevelKeys",
        "inventoryCanonicalDomain",
        "jsonMode",
        "policyCodes",
        "policyRevision",
        "projectPackagePath",
        "resolverMode",
        "safeRelativePathGrammar",
        "schema",
        "schemaRevision",
    ]),
    "Package-policy fixture shape drifted.",
);
assert(
    fixture.schema === "autojs6.typescript.package-metadata-policy" &&
        fixture.schemaRevision === 1 &&
        fixture.policyRevision === 1 &&
        fixture.projectPackagePath === "package.json" &&
        fixture.dependencyRootPrefix === "packages/" &&
        fixture.inventoryCanonicalDomain ===
            "autojs6.typescript.package-metadata.inventory.v1" &&
        fixture.safeRelativePathGrammar === "^[A-Za-z0-9@._/+~-]+$" &&
        fixture.jsonMode === "strict-utf8-duplicate-safe-raw" &&
        fixture.resolverMode === "raw-not-resolver-authority" &&
        exactJson(fixture.forbiddenTopLevelKeys, ["scripts"]),
    "Package-policy fixture identity drifted.",
);
assert(
    exactJson(fixture.budgets, {
        maximumDependencyRoots: 128,
        maximumFiles: 256,
        maximumFileBytes: 65536,
        maximumTotalBytes: 1048576,
        maximumJsonDepth: 16,
        maximumJsonNodes: 4096,
    }),
    "Package-policy budgets drifted.",
);
const policyCanonical =
    "autojs6.typescript.package-metadata-policy.v1" +
    `|schemaRevision=${fixture.schemaRevision}` +
    `|policyRevision=${fixture.policyRevision}` +
    `|projectPackagePath=${fixture.projectPackagePath}` +
    `|dependencyRootPrefix=${fixture.dependencyRootPrefix}` +
    `|maximumDependencyRoots=${fixture.budgets.maximumDependencyRoots}` +
    `|maximumFiles=${fixture.budgets.maximumFiles}` +
    `|maximumFileBytes=${fixture.budgets.maximumFileBytes}` +
    `|maximumTotalBytes=${fixture.budgets.maximumTotalBytes}` +
    `|maximumJsonDepth=${fixture.budgets.maximumJsonDepth}` +
    `|maximumJsonNodes=${fixture.budgets.maximumJsonNodes}` +
    `|forbiddenTopLevelKeys=${fixture.forbiddenTopLevelKeys.join(",")}` +
    `|safeRelativePathGrammar=${fixture.safeRelativePathGrammar}` +
    `|jsonMode=${fixture.jsonMode}` +
    `|resolverMode=${fixture.resolverMode}`;
assert(
    /^[\x20-\x7e]+$/.test(policyCanonical) &&
        sha256(Buffer.from(policyCanonical, "ascii")) === fixture.fingerprint,
    "Package-policy canonical fingerprint drifted.",
);
assert(
    fixture.emptyDependencyRootsExample.canonicalInput ===
        fixture.inventoryCanonicalDomain +
            "|file=package.json,2," +
            "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a" &&
        sha256(
            Buffer.from(
                fixture.emptyDependencyRootsExample.canonicalInput,
                "ascii",
            ),
        ) === fixture.emptyDependencyRootsExample.fingerprint,
    "Package inventory canonical example drifted.",
);

if (!global.crypto) {
    Object.defineProperty(global, "crypto", { value: cryptoModule.webcrypto });
}
const realTypeScript = require(compilerPath);
const compilerCalls = {
    emit: 0,
    impliedFormat: 0,
    program: 0,
    resolveModule: 0,
    resolveType: 0,
};
const typeScriptFacade = Object.create(realTypeScript);
for (const [name, counter] of [
    ["getImpliedNodeFormatForFile", "impliedFormat"],
    ["resolveModuleName", "resolveModule"],
    ["resolveTypeReferenceDirective", "resolveType"],
]) {
    Object.defineProperty(typeScriptFacade, name, {
        value: (...arguments_) => {
            compilerCalls[counter] += 1;
            return realTypeScript[name](...arguments_);
        },
    });
}
Object.defineProperty(typeScriptFacade, "createIncrementalProgram", {
    value: (...arguments_) => {
        compilerCalls.program += 1;
        const builder = realTypeScript.createIncrementalProgram(...arguments_);
        return new Proxy(builder, {
            get(target, property) {
                if (property === "emit") {
                    return (...emitArguments) => {
                        compilerCalls.emit += 1;
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
global.importScripts = (specifier) => importedScripts.push(specifier);
const publishedMessages = [];
const allPublishedMessages = [];
global.self = {
    postMessage: (message) => {
        publishedMessages.push(message);
        allPublishedMessages.push(message);
    },
};
vm.runInThisContext(workerSource, { filename: workerPath });
assert(
    realTypeScript.version === expectedVersion &&
        importedScripts.length === 1 &&
        importedScripts[0] === "typescript.js" &&
        publishedMessages.some(
            (message) =>
                message.kind === "ready" && message.tsVersion === expectedVersion,
        ),
    "Worker bootstrap or TypeScript version drifted.",
);
publishedMessages.length = 0;

const workerBinding = (expression) =>
    vm.runInThisContext(expression, {
        filename: `${workerPath}#package-metadata-transport-test`,
    });
const workerAuthority = workerBinding("controlledPackageMetadataAuthority");
const workerPolicyCodes = workerBinding("controlledPackageMetadataPolicyCodes");
assert(
    workerAuthority.schemaRevision === fixture.schemaRevision &&
        workerAuthority.policyRevision === fixture.policyRevision &&
        workerAuthority.policyFingerprint === fixture.fingerprint &&
        workerAuthority.maximumRoots === fixture.budgets.maximumDependencyRoots &&
        workerAuthority.maximumFiles === fixture.budgets.maximumFiles &&
        workerAuthority.maximumFileBytes === fixture.budgets.maximumFileBytes &&
        workerAuthority.maximumBytes === fixture.budgets.maximumTotalBytes &&
        workerAuthority.canonicalPrefix === fixture.inventoryCanonicalDomain,
    "Worker package authority drifted from the fixture.",
);
assert(
    exactJson(sorted(Object.values(workerPolicyCodes)), sorted(fixture.policyCodes)),
    "Worker package policy-code catalog drifted.",
);

const dispatch = async (message) => {
    publishedMessages.length = 0;
    await self.onmessage({ data: message });
    return publishedMessages.slice();
};
const expectAck = (messages, operation, sequence, label) => {
    const acknowledgements = messages.filter(
        (message) => message.kind === "projectInputAck",
    );
    assert(
        acknowledgements.length === 1 &&
            acknowledgements[0].requestId === operation.requestId &&
            acknowledgements[0].sequence === sequence,
        `${label} did not publish its exact ACK: ${JSON.stringify(messages)}.`,
    );
};
const expectProjectErrorWithoutAck = (
    messages,
    operation,
    label,
    expectedCode = null,
) => {
    assert(
        messages.length === 1 &&
            messages[0].kind === "projectError" &&
            messages[0].requestId === operation.requestId &&
            !messages.some((message) => message.kind === "projectInputAck"),
        `${label} did not fail without an ACK.`,
    );
    if (expectedCode !== null) {
        assert(
            messages[0].message.includes(expectedCode),
            `${label} did not preserve ${expectedCode}.`,
        );
    }
};
const expectConfigErrorWithoutAck = (
    messages,
    operation,
    policyCode,
    label,
) => {
    assert(
        !messages.some((message) => message.kind === "projectInputAck") &&
            messages.length === 1 &&
            messages[0].kind === "projectConfigError" &&
            messages[0].requestId === operation.requestId &&
            messages[0].policyCode === policyCode &&
            messages[0].stage === "config" &&
            messages[0].emittedModuleCount === 0,
        `${label} did not publish the exact config terminal.`,
    );
};
const assertStateCleared = (label) => {
    assert(
        workerBinding("framedInput") === null &&
            workerBinding("framedCompilationRequestId") === null &&
            workerBinding("framedResult") === null,
        `${label} retained Worker state.`,
    );
};

let requestCounter = 0;
const buildInventory = (dependencyRoots, packageFiles) => {
    const roots = dependencyRoots.slice().sort();
    const files = packageFiles
        .map((file) => {
            const bytes = Buffer.isBuffer(file.bytes)
                ? Buffer.from(file.bytes)
                : Buffer.from(file.text, "utf8");
            return {
                bytes,
                path: file.path,
                sha256: sha256(bytes),
                size: bytes.length,
            };
        })
        .sort((left, right) =>
            left.path < right.path ? -1 : left.path > right.path ? 1 : 0,
        );
    const canonicalInput =
        fixture.inventoryCanonicalDomain +
        roots.map((root) => `|root=${root}`).join("") +
        files
            .map((file) => `|file=${file.path},${file.size},${file.sha256}`)
            .join("");
    return {
        canonicalInput,
        dependencyRoots: roots,
        files,
        fingerprint: sha256(Buffer.from(canonicalInput, "ascii")),
        totalBytes: files.reduce((total, file) => total + file.size, 0),
    };
};
const createOperation = (dependencyRoots, packageFiles, source = "") => {
    requestCounter += 1;
    const configBytes = Buffer.from('{"files":[]}', "utf8");
    const sourceBytes = Buffer.from(source, "utf8");
    const inventory = buildInventory(dependencyRoots, packageFiles);
    const inputAuthority = projectInputAuthority({
        configRecord: inputRecord("config", "tsconfig.json", configBytes),
        fileRecords: [
            ...inventory.files.map((file) =>
                inputRecord("package-metadata", `/${file.path}`, file.bytes),
            ),
            inputRecord("declaration", "/declarations/lib.d.ts", ""),
            inputRecord("source", "/sources/main.ts", sourceBytes),
        ],
    });
    return {
        configBytes,
        configSha256: sha256(configBytes),
        inventory,
        inputAuthority,
        requestId: `package-c5-${requestCounter}`,
        sequence: 0,
        sourceBytes,
    };
};
const startFrame = (operation, extra = {}) => ({
    configByteLength: operation.configBytes.length,
    configKind: "tsconfig",
    configPolicyFingerprint: controlledPolicy.fingerprint,
    configPolicyRevision: 1,
    configSchemaRevision: 2,
    configSha256: operation.configSha256,
    entryPath: "/sources/main.ts",
    ...operation.inputAuthority,
    kind: "projectInputStart",
    packageMetadataByteLength: operation.inventory.totalBytes,
    packageMetadataCanonical: operation.inventory.canonicalInput,
    packageMetadataFileCount: operation.inventory.files.length,
    packageMetadataFingerprint: operation.inventory.fingerprint,
    packageMetadataPolicyFingerprint: fixture.fingerprint,
    packageMetadataPolicyRevision: fixture.policyRevision,
    packageMetadataSchemaRevision: fixture.schemaRevision,
    profileRevision: 1,
    profileSchemaRevision: 1,
    requestId: operation.requestId,
    sequence: operation.sequence,
    targetProfile: "rhino",
    workerSpinMilliseconds: 0,
    workerSpinPhase: "none",
    ...extra,
});
const acceptStart = async (operation, extra = {}) => {
    const messages = await dispatch(startFrame(operation, extra));
    expectAck(messages, operation, operation.sequence, `${operation.requestId} start`);
    operation.sequence += 1;
};
const acceptConfig = async (operation) => {
    let messages = await dispatch({
        byteLength: operation.configBytes.length,
        kind: "projectInputConfigStart",
        path: "tsconfig.json",
        requestId: operation.requestId,
        sequence: operation.sequence,
        sha256: operation.configSha256,
    });
    expectAck(messages, operation, operation.sequence, "config start");
    operation.sequence += 1;
    messages = await dispatch({
        chunkIndex: 0,
        data: operation.configBytes.toString("base64"),
        kind: "projectInputConfigChunk",
        requestId: operation.requestId,
        sequence: operation.sequence,
    });
    expectAck(messages, operation, operation.sequence, "config chunk");
    operation.sequence += 1;
    messages = await dispatch({
        chunkCount: 1,
        kind: "projectInputConfigEnd",
        requestId: operation.requestId,
        sequence: operation.sequence,
    });
    expectAck(messages, operation, operation.sequence, "config end");
    operation.sequence += 1;
};
const acceptFile = async (operation, fileIndex, pathValue, bytes, digest) => {
    let messages = await dispatch({
        byteLength: bytes.length,
        fileIndex,
        kind: "projectInputFileStart",
        path: pathValue,
        requestId: operation.requestId,
        sequence: operation.sequence,
        sha256: digest,
    });
    expectAck(messages, operation, operation.sequence, `${pathValue} start`);
    operation.sequence += 1;
    if (bytes.length > 0) {
        messages = await dispatch({
            chunkIndex: 0,
            data: bytes.toString("base64"),
            fileIndex,
            kind: "projectInputFileChunk",
            requestId: operation.requestId,
            sequence: operation.sequence,
        });
        expectAck(messages, operation, operation.sequence, `${pathValue} chunk`);
        operation.sequence += 1;
    }
    messages = await dispatch({
        chunkCount: bytes.length === 0 ? 0 : 1,
        fileIndex,
        kind: "projectInputFileEnd",
        requestId: operation.requestId,
        sequence: operation.sequence,
    });
    expectAck(messages, operation, operation.sequence, `${pathValue} end`);
    operation.sequence += 1;
};
const beginPackageOperation = async (operation) => {
    await acceptStart(operation);
    await acceptConfig(operation);
};
const releaseAcceptedOperation = () => {
    workerBinding("releaseProjectInputState")(workerBinding("framedInput"));
    workerBinding("framedInput = null");
};

const exactNodeLimitPackageJson =
    '{"values":[' +
    Array(fixture.budgets.maximumJsonNodes - 1).fill("0").join(",") +
    "]}";
const acceptedPackagePaths = [
    { path: "package.json", text: exactNodeLimitPackageJson },
    {
        path: "packages/@scope/bravo/package.json",
        text:
            '{"name":"@scope/bravo","Scripts":{"install":"inert"},' +
            '"innerBom":"\ufeff","huge":1e400}',
    },
    {
        path: "packages/alpha/meta/package.json",
        text: '{"nested":{"scripts":{"inert":true}}}',
    },
    { path: "packages/alpha/package.json", text: '{"name":"alpha"}' },
];

const rejectPackageBody = async (body, expectedCode, label) => {
    const operation = createOperation([], [{ path: "package.json", bytes: body }]);
    await beginPackageOperation(operation);
    const descriptor = operation.inventory.files[0];
    let messages = await dispatch({
        byteLength: descriptor.size,
        fileIndex: 0,
        kind: "projectInputFileStart",
        path: `/${descriptor.path}`,
        requestId: operation.requestId,
        sequence: operation.sequence,
        sha256: descriptor.sha256,
    });
    expectAck(messages, operation, operation.sequence, `${label} start`);
    operation.sequence += 1;
    if (descriptor.size > 0) {
        messages = await dispatch({
            chunkIndex: 0,
            data: descriptor.bytes.toString("base64"),
            fileIndex: 0,
            kind: "projectInputFileChunk",
            requestId: operation.requestId,
            sequence: operation.sequence,
        });
        expectAck(messages, operation, operation.sequence, `${label} chunk`);
        operation.sequence += 1;
    }
    const retainedBytes = workerBinding("framedInput.currentFile.bytes");
    messages = await dispatch({
        chunkCount: descriptor.size === 0 ? 0 : 1,
        fileIndex: 0,
        kind: "projectInputFileEnd",
        requestId: operation.requestId,
        sequence: operation.sequence,
    });
    expectProjectErrorWithoutAck(messages, operation, label, expectedCode);
    assert(
        Array.from(retainedBytes).every((value) => value === 0),
        `${label} retained raw package bytes.`,
    );
    assertStateCleared(label);
};

(async () => {
    {
        const operation = createOperation(
            ["packages/@scope/bravo", "packages/alpha"],
            acceptedPackagePaths,
        );
        await beginPackageOperation(operation);
        for (let index = 0; index < operation.inventory.files.length; index += 1) {
            const descriptor = operation.inventory.files[index];
            await acceptFile(
                operation,
                index,
                `/${descriptor.path}`,
                descriptor.bytes,
                descriptor.sha256,
            );
        }
        const packageState = workerBinding("framedInput");
        assert(
            packageState.packageMetadataFiles.length ===
                operation.inventory.files.length &&
                packageState.files.length === 0 &&
                packageState.pathChars === 0 &&
                packageState.tokenCount === 0 &&
                packageState.directoryIndex.pathChars() === 0 &&
                packageState.packageMetadataFiles.every((record) =>
                    exactKeys(record, ["byteLength", "path", "sha256"]),
                ),
            "Package records entered compiler file/token/directory authority.",
        );
        const declarationIndex = operation.inventory.files.length;
        await acceptFile(
            operation,
            declarationIndex,
            "/declarations/lib.d.ts",
            Buffer.alloc(0),
            sha256(Buffer.alloc(0)),
        );
        const sourceIndex = declarationIndex + 1;
        await acceptFile(
            operation,
            sourceIndex,
            "/sources/main.ts",
            operation.sourceBytes,
            sha256(operation.sourceBytes),
        );
        const messages = await dispatch({
            fileCount: operation.inputAuthority.fileCount,
            kind: "projectInputEnd",
            requestId: operation.requestId,
            sequence: operation.sequence,
            totalBytes: operation.inputAuthority.totalBytes,
        });
        expectConfigErrorWithoutAck(
            messages,
            operation,
            "CONFIG_NO_INPUTS",
            "package metadata excluded from root selection",
        );
        assertStateCleared("package metadata excluded from root selection");
    }

    await rejectPackageBody(
        Buffer.from('{"scripts":{"preinstall":"escape"}}', "utf8"),
        "PACKAGE_LIFECYCLE_FORBIDDEN",
        "lifecycle scripts",
    );
    await rejectPackageBody(
        Buffer.from('{"scr\\u0069pts":{}}', "utf8"),
        "PACKAGE_LIFECYCLE_FORBIDDEN",
        "escaped lifecycle scripts",
    );
    await rejectPackageBody(
        Buffer.from('{"name":"first","name":"second"}', "utf8"),
        "PACKAGE_DUPLICATE_KEY",
        "duplicate key",
    );
    await rejectPackageBody(
        Buffer.from("{", "utf8"),
        "PACKAGE_JSON_INVALID",
        "malformed JSON",
    );
    await rejectPackageBody(
        Buffer.from("[]", "utf8"),
        "PACKAGE_JSON_INVALID",
        "non-object JSON",
    );
    await rejectPackageBody(
        Buffer.from([0xff]),
        "PACKAGE_ENCODING_INVALID",
        "invalid UTF-8",
    );
    await rejectPackageBody(
        Buffer.from('{"value":"\\uD800"}', "utf8"),
        "PACKAGE_ENCODING_INVALID",
        "lone Unicode surrogate",
    );
    await rejectPackageBody(
        Buffer.from(`${'{"x":'.repeat(18)}0${"}".repeat(18)}`, "utf8"),
        "PACKAGE_COMPLEXITY_EXCEEDED",
        "JSON depth",
    );
    await rejectPackageBody(
        Buffer.from(exactNodeLimitPackageJson.replace("]}", ",0]}"), "utf8"),
        "PACKAGE_COMPLEXITY_EXCEEDED",
        "JSON node count",
    );

    for (const [label, mutation] of [
        ["start injection", { plugins: true }],
        ["policy fingerprint mismatch", { packageMetadataPolicyFingerprint: "0".repeat(64) }],
        ["inventory fingerprint mismatch", { packageMetadataFingerprint: "0".repeat(64) }],
        ["inventory file-count mismatch", { packageMetadataFileCount: 2 }],
    ]) {
        const operation = createOperation([], [{ path: "package.json", text: "{}" }]);
        const messages = await dispatch(startFrame(operation, mutation));
        expectProjectErrorWithoutAck(messages, operation, label);
        assertStateCleared(label);
    }

    {
        const operation = createOperation(
            ["packages/alpha"],
            [
                { path: "package.json", text: "{}" },
                { path: "packages/alpha/nested/Package.json", text: "{}" },
                { path: "packages/alpha/package.json", text: "{}" },
            ],
        );
        const messages = await dispatch(startFrame(operation));
        expectProjectErrorWithoutAck(
            messages,
            operation,
            "non-canonical package basename",
            "PACKAGE_PATH_INVALID",
        );
        assertStateCleared("non-canonical package basename");
    }

    for (const [label, mutation] of [
        ["package path order mismatch", { path: "/sources/main.ts" }],
        ["package descriptor size mismatch", { byteLength: 3 }],
        ["package descriptor digest mismatch", { sha256: "0".repeat(64) }],
    ]) {
        const operation = createOperation([], [{ path: "package.json", text: "{}" }]);
        await beginPackageOperation(operation);
        const descriptor = operation.inventory.files[0];
        const frame = {
            byteLength: descriptor.size,
            fileIndex: 0,
            kind: "projectInputFileStart",
            path: `/${descriptor.path}`,
            requestId: operation.requestId,
            sequence: operation.sequence,
            sha256: descriptor.sha256,
            ...mutation,
        };
        const messages = await dispatch(frame);
        expectProjectErrorWithoutAck(messages, operation, label);
        assertStateCleared(label);
    }

    {
        const configBytes = Buffer.from("{}", "utf8");
        const operation = {
            configBytes,
            configSha256: sha256(configBytes),
            inputAuthority: projectInputAuthority({
                configRecord: inputRecord("config", "tsconfig.json", configBytes),
                fileRecords: [
                    inputRecord("declaration", "/declarations/lib.d.ts", ""),
                    inputRecord("source", "/sources/main.ts", ""),
                ],
            }),
            requestId: `package-c5-${++requestCounter}`,
            sequence: 0,
        };
        let messages = await dispatch({
            configByteLength: configBytes.length,
            configKind: "tsconfig",
            configPolicyFingerprint: controlledPolicy.fingerprint,
            configPolicyRevision: 1,
            configSchemaRevision: 2,
            configSha256: operation.configSha256,
            entryPath: "/sources/main.ts",
            ...operation.inputAuthority,
            kind: "projectInputStart",
            profileRevision: 1,
            profileSchemaRevision: 1,
            requestId: operation.requestId,
            sequence: 0,
            targetProfile: "rhino",
            workerSpinMilliseconds: 0,
            workerSpinPhase: "none",
        });
        expectAck(messages, operation, 0, "metadata-absent controlled start");
        operation.sequence = 1;
        await acceptConfig(operation);
        messages = await dispatch({
            byteLength: 0,
            fileIndex: 0,
            kind: "projectInputFileStart",
            path: "/sources/package.json",
            requestId: operation.requestId,
            sequence: operation.sequence,
            sha256: sha256(Buffer.alloc(0)),
        });
        expectProjectErrorWithoutAck(
            messages,
            operation,
            "metadata-absent package attack",
            "package-metadata",
        );
        assertStateCleared("metadata-absent package attack");
    }

    {
        const successor = createOperation([], [{ path: "package.json", text: "{}" }]);
        await beginPackageOperation(successor);
        assert(
            workerBinding("framedInput")?.requestId === successor.requestId,
            "Clean package successor was not admitted after rejections.",
        );
        releaseAcceptedOperation();
        assertStateCleared("successor cleanup");
    }

    assert(
        Object.values(compilerCalls).every((count) => count === 0),
        `Package transport reached TypeScript authority: ${JSON.stringify(compilerCalls)}.`,
    );
    assert(
        !allPublishedMessages.some((message) =>
            [
                "projectResultStart",
                "projectResultChunk",
                "projectResultEnd",
            ].includes(message.kind),
        ),
        "C5 package transport published a compiler result before C7.",
    );

    process.stdout.write(
        `R2-M2-C5 package metadata transport verified: ` +
            `${acceptedPackagePaths.length} accepted paths, ` +
            `9 package JSON rejections, 8 transport authority rejections, ` +
            `resolver/program/emit/result=0\n`,
    );
})().catch((error) => {
    process.stderr.write(`${error.stack || error}\n`);
    process.exitCode = 1;
});
