"use strict";

const cryptoModule = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const {
    projectInputAuthority,
    record: inputArchiveRecord,
} = require("./r2-input-archive-authority.js");

const [
    workerArgument,
    compilerArgument,
    targetPolicyArgument,
    metadataPolicyArgument,
    configPolicyArgument,
    expectedVersion,
] = process.argv.slice(2);
if (
    !workerArgument ||
    !compilerArgument ||
    !targetPolicyArgument ||
    !metadataPolicyArgument ||
    !configPolicyArgument ||
    !expectedVersion
) {
    throw new Error(
        "Usage: node verify-r2-package-target-worker-transport.js " +
            "<worker.js> <typescript.js> <target-policy.json> " +
            "<metadata-policy.json> <config-policy.json> 6.0.3",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const targetPolicy = JSON.parse(fs.readFileSync(path.resolve(targetPolicyArgument), "utf8"));
const metadataPolicy = JSON.parse(
    fs.readFileSync(path.resolve(metadataPolicyArgument), "utf8"),
);
const configPolicy = JSON.parse(
    fs.readFileSync(path.resolve(configPolicyArgument), "utf8"),
);
const workerSource = fs.readFileSync(workerPath, "utf8");
const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const sha256 = (value) =>
    cryptoModule.createHash("sha256").update(value).digest("hex");
const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;
const exactKeys = (value, expected) =>
    JSON.stringify(Object.keys(value).sort(compareCodeUnits)) ===
    JSON.stringify(expected.slice().sort(compareCodeUnits));

assert(expectedVersion === "6.0.3", "Package-target gate requires TypeScript 6.0.3.");
assert(
    targetPolicy.schema === "autojs6.typescript.package-target-policy" &&
        targetPolicy.schemaRevision === 1 &&
        targetPolicy.policyRevision === 1 &&
        targetPolicy.fingerprint ===
            "e34f4825d8f75ba1b674849ff79c2be8f9c1b88fe940add86e6e466a139bbbaf",
    "Package-target fixture identity drifted.",
);

if (!global.crypto) {
    Object.defineProperty(global, "crypto", {
        configurable: true,
        value: cryptoModule.webcrypto,
    });
}
const realTypeScript = require(compilerPath);
let programCount = 0;
let hostIoCount = 0;
const typeScriptFacade = Object.create(realTypeScript);
Object.defineProperty(typeScriptFacade, "sys", {
    value: new Proxy(realTypeScript.sys, {
        get() {
            hostIoCount += 1;
            throw new Error("Package-target gate reached TypeScript host I/O.");
        },
    }),
});
Object.defineProperty(typeScriptFacade, "createIncrementalProgram", {
    value: () => {
        programCount += 1;
        throw new Error("Package-target transport constructed a Program.");
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
        publishedMessages[0].tsVersion === expectedVersion,
    "Worker bootstrap drifted.",
);
publishedMessages.length = 0;

const workerBinding = (expression) =>
    vm.runInThisContext(expression, {
        filename: `${workerPath}#package-target-worker-transport`,
    });
const workerAuthority = workerBinding("controlledPackageTargetAuthority");
const workerCodes = workerBinding("controlledPackageTargetPolicyCodes");
assert(
    workerAuthority.policyFingerprint === targetPolicy.fingerprint &&
        workerAuthority.canonicalPrefix === targetPolicy.inventoryCanonicalDomain &&
        workerAuthority.maximumFiles === targetPolicy.budgets.maximumFiles &&
        workerAuthority.maximumPathChars === targetPolicy.budgets.maximumPathChars &&
        workerAuthority.maximumAggregatePathChars ===
            targetPolicy.budgets.maximumAggregatePathChars &&
        workerAuthority.maximumFileBytes === targetPolicy.budgets.maximumFileBytes &&
        workerAuthority.maximumTotalBytes === targetPolicy.budgets.maximumTotalBytes &&
        JSON.stringify(Object.values(workerCodes).sort(compareCodeUnits)) ===
            JSON.stringify(targetPolicy.policyCodes.slice().sort(compareCodeUnits)),
    "Worker package-target authority drifted from Kotlin/fixture authority.",
);

const dispatch = async (message) => {
    publishedMessages.length = 0;
    await self.onmessage({ data: message });
    return publishedMessages.slice();
};
const expectAck = (messages, operation, label) => {
    const acknowledgements = messages.filter(
        (message) => message.kind === "projectInputAck",
    );
    assert(
        acknowledgements.length === 1 &&
            acknowledgements[0].requestId === operation.requestId &&
            acknowledgements[0].sequence === operation.sequence,
        `${label} did not publish its exact ACK: ${JSON.stringify(messages)}.`,
    );
    operation.sequence += 1;
};
let rejectionCount = 0;
const expectReject = (messages, operation, label, code = null) => {
    assert(
        messages.length === 1 &&
            messages[0].kind === "projectError" &&
            messages[0].requestId === operation.requestId &&
            !messages.some((message) => message.kind === "projectInputAck") &&
            (code === null || messages[0].message.includes(code)),
        `${label} was not rejected exactly: ${JSON.stringify(messages)}.`,
    );
    assert(
        workerBinding("framedInput") === null &&
            workerBinding("framedCompilationRequestId") === null &&
            workerBinding("framedResult") === null,
        `${label} retained Worker operation state.`,
    );
    rejectionCount += 1;
};

const materializeFiles = (files) =>
    files
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
        .sort((left, right) => compareCodeUnits(left.path, right.path));
const buildMetadata = (roots, files) => {
    const dependencyRoots = roots.slice().sort(compareCodeUnits);
    const records = materializeFiles(files);
    const canonicalInput =
        metadataPolicy.inventoryCanonicalDomain +
        dependencyRoots.map((root) => `|root=${root}`).join("") +
        records
            .map((file) => `|file=${file.path},${file.size},${file.sha256}`)
            .join("");
    return {
        canonicalInput,
        dependencyRoots,
        files: records,
        fingerprint: sha256(Buffer.from(canonicalInput, "ascii")),
        totalBytes: records.reduce((total, file) => total + file.size, 0),
    };
};
const buildTargets = (metadataFingerprint, files) => {
    const records = materializeFiles(files);
    const canonicalInput =
        targetPolicy.inventoryCanonicalDomain +
        `|packageMetadataInventoryFingerprint=${metadataFingerprint}` +
        records
            .map((file) => `|file=${file.path},${file.size},${file.sha256}`)
            .join("");
    return {
        canonicalInput,
        files: records,
        fingerprint: sha256(Buffer.from(canonicalInput, "ascii")),
        totalBytes: records.reduce((total, file) => total + file.size, 0),
    };
};

let requestCounter = 0;
const createOperation = ({ targetFiles = null, targetBinding = null } = {}) => {
    const configBytes = Buffer.from('{"files":["main.ts"]}', "utf8");
    const metadata = buildMetadata(
        ["packages/pkg"],
        [
            { path: "package.json", text: "{}" },
            { path: "packages/pkg/package.json", text: '{"name":"pkg"}' },
        ],
    );
    const targets = buildTargets(
        targetBinding ?? metadata.fingerprint,
        targetFiles ?? [
            { path: "packages/pkg/index.d.ts", text: "export declare const answer: number;\n" },
            { path: "packages/pkg/runtime.ts", text: "export const answer = 42;\n" },
        ],
    );
    const ordinary = materializeFiles([
        { path: "/declarations/lib.d.ts", text: "interface Array<T> { length: number; }\n" },
        { path: "/sources/main.ts", text: "export const main = 1;\n" },
    ]);
    return {
        configBytes,
        metadata,
        ordinary,
        requestId: `package-target-c6d-${++requestCounter}`,
        sequence: 0,
        targets,
    };
};
const inputAuthority = (operation) =>
    projectInputAuthority({
        configRecord: inputArchiveRecord(
            "config",
            "tsconfig.json",
            operation.configBytes,
        ),
        fileRecords: [
            ...operation.metadata.files.map((file) =>
                inputArchiveRecord("package-metadata", `/${file.path}`, file.bytes),
            ),
            ...operation.targets.files.map((file) =>
                inputArchiveRecord("package-target-v1", `/${file.path}`, file.bytes),
            ),
            ...operation.ordinary.map((file) =>
                inputArchiveRecord(
                    file.path.startsWith("/declarations/")
                        ? "declaration"
                        : "source",
                    file.path,
                    file.bytes,
                ),
            ),
        ],
    });
const startFrame = (operation, extra = {}) => ({
    ...inputAuthority(operation),
    configByteLength: operation.configBytes.length,
    configKind: "tsconfig",
    configPolicyFingerprint: configPolicy.fingerprint,
    configPolicyRevision: configPolicy.revision,
    configSchemaRevision: 2,
    configSha256: sha256(operation.configBytes),
    entryPath: "/sources/main.ts",
    fileCount:
        operation.metadata.files.length +
        operation.targets.files.length +
        operation.ordinary.length,
    kind: "projectInputStart",
    packageMetadataByteLength: operation.metadata.totalBytes,
    packageMetadataCanonical: operation.metadata.canonicalInput,
    packageMetadataFileCount: operation.metadata.files.length,
    packageMetadataFingerprint: operation.metadata.fingerprint,
    packageMetadataPolicyFingerprint: metadataPolicy.fingerprint,
    packageMetadataPolicyRevision: metadataPolicy.policyRevision,
    packageMetadataSchemaRevision: metadataPolicy.schemaRevision,
    packageTargetByteLength: operation.targets.totalBytes,
    packageTargetCanonical: operation.targets.canonicalInput,
    packageTargetFileCount: operation.targets.files.length,
    packageTargetFingerprint: operation.targets.fingerprint,
    packageTargetPolicyFingerprint: targetPolicy.fingerprint,
    packageTargetPolicyRevision: targetPolicy.policyRevision,
    packageTargetSchemaRevision: targetPolicy.schemaRevision,
    profileRevision: 1,
    profileSchemaRevision: 1,
    requestId: operation.requestId,
    sequence: operation.sequence,
    targetProfile: "rhino",
    totalBytes:
        operation.configBytes.length +
        operation.metadata.totalBytes +
        operation.targets.totalBytes +
        operation.ordinary.reduce((total, file) => total + file.size, 0),
    workerSpinMilliseconds: 0,
    workerSpinPhase: "none",
    ...extra,
});
const rev5StartKeys = [
    "callerInventoryFingerprint",
    "configByteLength",
    "configKind",
    "configPolicyFingerprint",
    "configPolicyRevision",
    "configSchemaRevision",
    "configSha256",
    "declarationByteLength",
    "declarationFileCount",
    "declarationInventoryFingerprint",
    "declarationProfileFingerprint",
    "entryPath",
    "fileCount",
    "inputArchiveSchemaRevision",
    "inputQuotaRevision",
    "kind",
    "largestFileBytes",
    "packageMetadataByteLength",
    "packageMetadataCanonical",
    "packageMetadataFileCount",
    "packageMetadataFingerprint",
    "packageMetadataPolicyFingerprint",
    "packageMetadataPolicyRevision",
    "packageMetadataSchemaRevision",
    "packageTargetByteLength",
    "packageTargetCanonical",
    "packageTargetFileCount",
    "packageTargetFingerprint",
    "packageTargetPolicyFingerprint",
    "packageTargetPolicyRevision",
    "packageTargetSchemaRevision",
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
];
const sendStart = async (operation, extra = {}) =>
    expectAck(await dispatch(startFrame(operation, extra)), operation, "rev5 start");
const sendConfig = async (operation) => {
    expectAck(
        await dispatch({
            byteLength: operation.configBytes.length,
            kind: "projectInputConfigStart",
            path: "tsconfig.json",
            requestId: operation.requestId,
            sequence: operation.sequence,
            sha256: sha256(operation.configBytes),
        }),
        operation,
        "config start",
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
        "config chunk",
    );
    expectAck(
        await dispatch({
            chunkCount: 1,
            kind: "projectInputConfigEnd",
            requestId: operation.requestId,
            sequence: operation.sequence,
        }),
        operation,
        "config end",
    );
};
const sendFile = async (operation, fileIndex, file, mutation = {}) => {
    expectAck(
        await dispatch({
            byteLength: file.size,
            fileIndex,
            kind: "projectInputFileStart",
            path: file.path.startsWith("/") ? file.path : `/${file.path}`,
            requestId: operation.requestId,
            sequence: operation.sequence,
            sha256: file.sha256,
            ...mutation,
        }),
        operation,
        `${file.path} start`,
    );
    if (file.size !== 0) {
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
            `${file.path} chunk`,
        );
    }
    expectAck(
        await dispatch({
            chunkCount: file.size === 0 ? 0 : 1,
            fileIndex,
            kind: "projectInputFileEnd",
            requestId: operation.requestId,
            sequence: operation.sequence,
        }),
        operation,
        `${file.path} end`,
    );
};
const beginOperation = async (operation) => {
    await sendStart(operation);
    await sendConfig(operation);
};
const releaseInput = () => {
    workerBinding("releaseProjectInputState")(workerBinding("framedInput"));
    workerBinding("framedInput = null");
};

(async () => {
    const accepted = createOperation();
    assert(
        rev5StartKeys.length === 41 && exactKeys(startFrame(accepted), rev5StartKeys),
        "rev5 start does not have its exact 41-field schema-v1 shape.",
    );
    await beginOperation(accepted);
    let fileIndex = 0;
    for (const file of accepted.metadata.files) {
        await sendFile(accepted, fileIndex++, file);
    }
    for (const file of accepted.targets.files) {
        await sendFile(accepted, fileIndex++, file);
    }
    for (const file of accepted.ordinary) {
        await sendFile(accepted, fileIndex++, file);
    }
    const state = workerBinding("framedInput");
    assert(
        state.packageMetadataFiles.length === accepted.metadata.files.length &&
            state.packageTargetFiles.length === accepted.targets.files.length &&
            state.files.length === accepted.ordinary.length &&
            state.packageTargetFiles.every((record) => Object.isFrozen(record)),
        "rev5 ingress did not retain its three ordered inventories.",
    );
    const rootNames = workerBinding("selectControlledRootNames")(
        state.controlledConfig,
        state.files,
        state.entryPath,
    );
    const authority = workerBinding("createRetainedPackageTargetAuthority")(
        state.packageTargets,
    );
    const metadataCatalog = Object.freeze({
        dependencyRoots: state.packageMetadata.dependencyRoots,
        inventoryFingerprint: state.packageMetadata.fingerprint,
        records: Object.freeze(state.packageMetadataCatalogRecords.slice()),
    });
    const vfs = workerBinding("createReadonlyProjectVfs")({
        configKind: state.configKind,
        configPolicyFingerprint: state.configPolicyFingerprint,
        configPolicyRevision: state.configPolicyRevision,
        configSchemaRevision: state.configSchemaRevision,
        directoryIndex: state.directoryIndex,
        files: state.files,
        packageMetadataCatalog: metadataCatalog,
        packageTargetAuthority: authority,
        packageTargetFiles: state.packageTargetFiles.slice(),
        packageTargetTextHolders: state.packageTargetTextHolders.slice(),
        packageTargetTokenCount: state.packageTargetTokenCount,
        packageTargetTokenCounts: state.packageTargetTokenCounts.slice(),
        pathChars: state.pathChars,
        profileRevision: state.profileRevision,
        profileSchemaRevision: state.profileSchemaRevision,
        rootNames: rootNames.slice(),
        targetProfile: state.targetProfile,
        tokenCount: state.tokenCount,
        totalBytes: state.files.reduce((total, file) => total + file.byteLength, 0),
    });
    const targetPath = "/packages/pkg/index.d.ts";
    const retainedRecord = vfs.retainedPackageTargets.readRecord(targetPath);
    assert(
        JSON.stringify(vfs.rootNames) === JSON.stringify(["/sources/main.ts"]) &&
            !vfs.fileExists(targetPath) &&
            vfs.readFile(targetPath) === undefined &&
            vfs.readRecord(targetPath) === undefined &&
            vfs.retainedPackageTargets.fileExists(targetPath) &&
            vfs.retainedPackageTargets.readFile(targetPath) ===
                accepted.targets.files[0].bytes.toString("utf8") &&
            Object.isFrozen(retainedRecord) &&
            Object.isFrozen(vfs.retainedPackageTargets) &&
            Object.isFrozen(vfs.retainedPackageTargets.authority) &&
            vfs.retainedPackageTargets.fingerprint === accepted.targets.fingerprint &&
            vfs.retainedPackageTargets.packageMetadataFingerprint ===
                accepted.metadata.fingerprint &&
            state.packageTargetTokenCounts.length === accepted.targets.files.length &&
            state.packageTargetTokenCount > 0,
        "Target retention escaped into compiler-visible VFS or lost C5 identity.",
    );
    vfs.release();
    assert(retainedRecord.text === "", "VFS release retained package target text.");
    releaseInput();

    for (const [label, operation, mutation, code] of [
        [
            "metadata binding",
            createOperation({ targetBinding: "0".repeat(64) }),
            {},
            "PACKAGE_TARGET_METADATA_IDENTITY_INVALID",
        ],
        [
            "target fingerprint",
            createOperation(),
            { packageTargetFingerprint: "0".repeat(64) },
            "PACKAGE_TARGET_INVENTORY_INVALID",
        ],
    ]) {
        expectReject(await dispatch(startFrame(operation, mutation)), operation, label, code);
    }

    const unknownStartField = createOperation();
    expectReject(
        await dispatch(startFrame(unknownStartField, { unexpectedField: true })),
        unknownStartField,
        "revision-5 unknown start field",
    );

    const quota = createOperation({
        targetFiles: Array.from(
            { length: targetPolicy.budgets.maximumFiles + 1 },
            (_, index) => ({
                path: `packages/pkg/quota-${index.toString().padStart(3, "0")}.ts`,
                text: "",
            }),
        ),
    });
    expectReject(
        await dispatch(startFrame(quota)),
        quota,
        "target quota",
        "PACKAGE_TARGET_QUOTA_EXCEEDED",
    );

    const caseCollision = createOperation({
        targetFiles: [
            { path: "packages/pkg/Index.ts", text: "" },
            { path: "packages/pkg/index.ts", text: "" },
        ],
    });
    expectReject(
        await dispatch(startFrame(caseCollision)),
        caseCollision,
        "target case collision",
        "PACKAGE_TARGET_CASE_COLLISION",
    );

    const injectedRev4 = createOperation();
    const revision4 = startFrame(injectedRev4);
    for (const key of Object.keys(revision4)) {
        if (key.startsWith("packageTarget") && key !== "packageTargetSchemaRevision") {
            delete revision4[key];
        }
    }
    expectReject(
        await dispatch(revision4),
        injectedRev4,
        "revision-4 target-field injection",
    );

    const outOfOrder = createOperation();
    await beginOperation(outOfOrder);
    expectReject(
        await dispatch({
            byteLength: outOfOrder.targets.files[0].size,
            fileIndex: 0,
            kind: "projectInputFileStart",
            path: `/${outOfOrder.targets.files[0].path}`,
            requestId: outOfOrder.requestId,
            sequence: outOfOrder.sequence,
            sha256: outOfOrder.targets.files[0].sha256,
        }),
        outOfOrder,
        "metadata-target ordering",
    );

    const descriptorTamper = createOperation({
        targetFiles: [
            { path: "packages/pkg/index.d.ts", text: "export declare const ok: true;" },
            { path: "packages/pkg/util.ts", text: "export const value = 1;" },
        ],
    });
    await beginOperation(descriptorTamper);
    for (let index = 0; index < descriptorTamper.metadata.files.length; index += 1) {
        await sendFile(descriptorTamper, index, descriptorTamper.metadata.files[index]);
    }
    const firstTargetIndex = descriptorTamper.metadata.files.length;
    await sendFile(
        descriptorTamper,
        firstTargetIndex,
        descriptorTamper.targets.files[0],
    );
    const retainedTextHolder = workerBinding(
        "framedInput.packageTargetTextHolders[0]",
    );
    const secondTarget = descriptorTamper.targets.files[1];
    expectReject(
        await dispatch({
            byteLength: secondTarget.size,
            fileIndex: firstTargetIndex + 1,
            kind: "projectInputFileStart",
            path: `/${secondTarget.path}`,
            requestId: descriptorTamper.requestId,
            sequence: descriptorTamper.sequence,
            sha256: "0".repeat(64),
        }),
        descriptorTamper,
        "target descriptor tamper",
    );
    assert(
        retainedTextHolder.text === "",
        "Rejected later target retained an earlier accepted target text.",
    );

    const invalidUtf8 = createOperation({
        targetFiles: [{ path: "packages/pkg/index.d.ts", bytes: Buffer.from([0xff]) }],
    });
    await beginOperation(invalidUtf8);
    for (let index = 0; index < invalidUtf8.metadata.files.length; index += 1) {
        await sendFile(invalidUtf8, index, invalidUtf8.metadata.files[index]);
    }
    const invalidTarget = invalidUtf8.targets.files[0];
    let messages = await dispatch({
        byteLength: invalidTarget.size,
        fileIndex: invalidUtf8.metadata.files.length,
        kind: "projectInputFileStart",
        path: `/${invalidTarget.path}`,
        requestId: invalidUtf8.requestId,
        sequence: invalidUtf8.sequence,
        sha256: invalidTarget.sha256,
    });
    expectAck(messages, invalidUtf8, "invalid UTF-8 target start");
    messages = await dispatch({
        chunkIndex: 0,
        data: invalidTarget.bytes.toString("base64"),
        fileIndex: invalidUtf8.metadata.files.length,
        kind: "projectInputFileChunk",
        requestId: invalidUtf8.requestId,
        sequence: invalidUtf8.sequence,
    });
    expectAck(messages, invalidUtf8, "invalid UTF-8 target chunk");
    const rawBytes = workerBinding("framedInput.currentFile.bytes");
    messages = await dispatch({
        chunkCount: 1,
        fileIndex: invalidUtf8.metadata.files.length,
        kind: "projectInputFileEnd",
        requestId: invalidUtf8.requestId,
        sequence: invalidUtf8.sequence,
    });
    expectReject(
        messages,
        invalidUtf8,
        "target UTF-8",
        "PACKAGE_TARGET_INVENTORY_INVALID",
    );
    assert(
        Array.from(rawBytes).every((value) => value === 0),
        "Rejected target retained raw bytes.",
    );

    assert(
        rejectionCount === 9 &&
            (workerSource.match(/resolveControlledNodeSnapshot/g) || []).length >= 2 &&
            programCount === 0 &&
            hostIoCount === 0,
        "C6-D rejection count drifted, the activation seam is missing, or the " +
            "transport-only scenario reached Program/host I/O.",
    );
    process.stdout.write(
        "R2-M2-C6-D package target Worker transport verified: " +
            "2 retained targets, 9 rev5 rejections, host-visible=0, " +
            "resolver-declared, Program/I-O=0\n",
    );
})().catch((error) => {
    process.stderr.write(`${error.stack || error}\n`);
    process.exitCode = 1;
});
