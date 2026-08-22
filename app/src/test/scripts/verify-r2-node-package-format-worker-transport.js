"use strict";

const cryptoModule = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const catalogOracle = require("./r2-node-package-format-catalog-policy.js");
const {
    projectInputAuthority,
    record: inputArchiveRecord,
} = require("./r2-input-archive-authority.js");

const [
    workerArgument,
    compilerArgument,
    transportArgument,
    catalogArgument,
    packagePolicyArgument,
    nodeOutputPolicyArgument,
    expectedVersion,
] = process.argv.slice(2);
if (
    !workerArgument ||
    !compilerArgument ||
    !transportArgument ||
    !catalogArgument ||
    !packagePolicyArgument ||
    !nodeOutputPolicyArgument ||
    !expectedVersion
) {
    throw new Error(
        "Usage: node verify-r2-node-package-format-worker-transport.js " +
            "<compiler-worker.js> <typescript.js> <transport-v1.json> " +
            "<catalog-v1.json> <package-policy-v1.json> " +
            "<node-module-output-policy-v1.json> <typescript-version>",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const transport = JSON.parse(
    fs.readFileSync(path.resolve(transportArgument), "utf8"),
);
const catalogFixture = JSON.parse(
    fs.readFileSync(path.resolve(catalogArgument), "utf8"),
);
const packagePolicy = JSON.parse(
    fs.readFileSync(path.resolve(packagePolicyArgument), "utf8"),
);
const nodeOutputPolicy = JSON.parse(
    fs.readFileSync(path.resolve(nodeOutputPolicyArgument), "utf8"),
);

const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const sha256 = (value) =>
    cryptoModule.createHash("sha256").update(value).digest("hex");
const sorted = (values) => Array.from(values).sort();
const exactJson = (left, right) => JSON.stringify(left) === JSON.stringify(right);
const exactKeys = (value, expected) =>
    exactJson(sorted(Object.keys(value)), sorted(expected));
const allZero = (bytes) => bytes.every((value) => value === 0);
const hasOwn = (value, key) =>
    Object.prototype.hasOwnProperty.call(value, key);
const isDeepFrozen = (value, seen = new Set()) => {
    if (
        value === null ||
        (typeof value !== "object" && typeof value !== "function") ||
        seen.has(value)
    ) {
        return true;
    }
    seen.add(value);
    return (
        Object.isFrozen(value) &&
        Reflect.ownKeys(value).every((key) => isDeepFrozen(value[key], seen))
    );
};

const noBoundaryKeys = [
    "callerInventoryFingerprint",
    "configKind",
    "configSchemaRevision",
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
    "profileRevision",
    "profileSchemaRevision",
    "requestId",
    "retainedFileCount",
    "retainedInventoryFingerprint",
    "runtimeCapabilitiesFingerprint",
    "sequence",
    "targetProfile",
    "totalBytes",
    "workerSpinMilliseconds",
    "workerSpinPhase",
];
const presentKeys = noBoundaryKeys.concat([
    "nodeModuleOutputPolicyFingerprint",
    "nodeModuleOutputPolicyRevision",
    "nodeModuleOutputPolicySchemaRevision",
    "nodePackageFormatCatalogFingerprint",
    "nodePackageFormatCatalogRevision",
    "nodePackageFormatSchemaRevision",
    "packageMetadataByteLength",
    "packageMetadataCanonical",
    "packageMetadataFileCount",
    "packageMetadataInventoryFingerprint",
    "packageMetadataPolicyFingerprint",
    "packageMetadataPolicyRevision",
    "packageMetadataSchemaRevision",
]);

assert(
    exactKeys(transport, [
        "authority",
        "exactStartKeys",
        "invalidTypeCaseIds",
        "requiredRejectionIds",
        "revision",
        "schema",
        "validCatalogCaseIds",
    ]) &&
        transport.schema ===
            "autojs6.typescript.node-package-format-worker-transport" &&
        transport.revision === 1,
    "P2 Worker transport fixture identity or shape drifted.",
);
assert(
    exactKeys(transport.authority, [
        "configKind",
        "configSchemaRevision",
        "entryPath",
        "profileRevision",
        "profileSchemaRevision",
        "runtimeCapabilitiesFingerprint",
        "targetProfile",
        "terminalPolicyCode",
    ]) &&
        transport.authority.configKind === "none" &&
        transport.authority.configSchemaRevision === 1 &&
        transport.authority.profileSchemaRevision === 1 &&
        transport.authority.profileRevision === 2 &&
        transport.authority.targetProfile === "node" &&
        transport.authority.entryPath === "/sources/main.ts" &&
        transport.authority.runtimeCapabilitiesFingerprint ===
            "a19e8da1a4ab5961aada8d49e1f70eef847b84a0a8f348b6f784f43dc26e2d3a" &&
        transport.authority.terminalPolicyCode ===
            "P2_NODE_PROGRAM_NOT_ACTIVATED",
    "P2 Worker transport authority drifted.",
);
assert(
    exactKeys(transport.exactStartKeys, ["noBoundary", "present"]) &&
        exactJson(sorted(transport.exactStartKeys.noBoundary), sorted(noBoundaryKeys)) &&
        exactJson(sorted(transport.exactStartKeys.present), sorted(presentKeys)) &&
        new Set(transport.exactStartKeys.noBoundary).size === 24 &&
        new Set(transport.exactStartKeys.present).size === 37,
    "P2 Worker start transport is not the exact 24/37 protocol.",
);
assert(
    catalogFixture.schema ===
        "autojs6.typescript.node-package-format-catalog-parity" &&
        catalogFixture.revision === 1 &&
        packagePolicy.schema === "autojs6.typescript.package-metadata-policy" &&
        packagePolicy.schemaRevision === 1 &&
        packagePolicy.policyRevision === 1 &&
        nodeOutputPolicy.schema ===
            "autojs6.typescript.node-module-output-policy" &&
        nodeOutputPolicy.schemaRevision === 1 &&
        nodeOutputPolicy.policyRevision === 1,
    "P2 catalog/package/output fixture identity drifted.",
);
assert(
    catalogFixture.catalogAuthority.profileSchemaRevision ===
            transport.authority.profileSchemaRevision &&
        catalogFixture.catalogAuthority.profileRevision ===
            transport.authority.profileRevision &&
        catalogFixture.catalogAuthority.packageMetadataSchemaRevision ===
            packagePolicy.schemaRevision &&
        catalogFixture.catalogAuthority.packageMetadataPolicyRevision ===
            packagePolicy.policyRevision &&
        catalogFixture.catalogAuthority.packageMetadataPolicyFingerprint ===
            packagePolicy.fingerprint &&
        catalogFixture.catalogAuthority.nodeModuleOutputPolicySchemaRevision ===
            nodeOutputPolicy.schemaRevision &&
        catalogFixture.catalogAuthority.nodeModuleOutputPolicyRevision ===
            nodeOutputPolicy.policyRevision &&
        catalogFixture.catalogAuthority.nodeModuleOutputPolicyFingerprint ===
            nodeOutputPolicy.fingerprint &&
        nodeOutputPolicy.nodeProfileRevision ===
            transport.authority.profileRevision,
    "P2 catalog authority drifted from its C5 or output-policy dependency.",
);
assert(
    exactJson(
        transport.validCatalogCaseIds,
        catalogFixture.validCases.map((testCase) => testCase.id),
    ) &&
        exactJson(
            transport.invalidTypeCaseIds,
            catalogFixture.invalidTypeCases.map((testCase) => testCase.id),
        ) &&
        transport.validCatalogCaseIds.length === 4 &&
        transport.invalidTypeCaseIds.length === 8,
    "P2 Worker fixture case inventory drifted from the catalog fixture.",
);
assert(
    new Set(transport.requiredRejectionIds).size ===
        transport.requiredRejectionIds.length,
    "P2 Worker rejection inventory contains duplicates.",
);

for (const testCase of catalogFixture.validCases) {
    const derived = catalogOracle.deriveCatalog(
        testCase.dependencyRoots,
        testCase.files,
    );
    assert(
        derived.inventoryCanonicalInput === testCase.inventoryCanonicalInput &&
            derived.packageMetadataInventoryFingerprint ===
                testCase.packageMetadataInventoryFingerprint &&
            derived.catalogCanonicalInput === testCase.catalogCanonicalInput &&
            derived.catalogFingerprint === testCase.catalogFingerprint,
        `Independent catalog oracle drifted for ${testCase.id}.`,
    );
}

const replaceExactlyOnce = (source, search, replacement, label) => {
    const first = source.indexOf(search);
    assert(first >= 0, `Worker instrumentation marker is missing: ${label}.`);
    assert(
        source.indexOf(search, first + search.length) < 0,
        `Worker instrumentation marker is ambiguous: ${label}.`,
    );
    return source.slice(0, first) + replacement + source.slice(first + search.length);
};

let workerSource = fs.readFileSync(workerPath, "utf8");
assert(
    !workerSource.includes("r2-node-package-format-catalog-policy") &&
        !workerSource.includes("transport-v1.json"),
    "Production Worker imported or embedded a desktop P2 oracle/fixture.",
);
workerSource = replaceExactlyOnce(
    workerSource,
    "const deriveNodePackageFormatCatalog = async (packageMetadata, records) => {",
    "const deriveNodePackageFormatCatalogImplementation = async " +
        "(packageMetadata, records) => {",
    "catalog derivation definition",
);
workerSource = replaceExactlyOnce(
    workerSource,
    "\nconst rejectControlledPackageTarget = (code, message) => {",
    "\nconst deriveNodePackageFormatCatalog = async (packageMetadata, records) => {\n" +
        "    globalThis.__r2P2WorkerTest.catalogDerivations += 1;\n" +
        "    globalThis.__r2P2WorkerTest.lastDerivationInput = {\n" +
        "        packageMetadata,\n" +
        "        records: records.slice(),\n" +
        "    };\n" +
        "    const catalog = await deriveNodePackageFormatCatalogImplementation(\n" +
        "        packageMetadata,\n" +
        "        records,\n" +
        "    );\n" +
        "    globalThis.__r2P2WorkerTest.lastCatalog = catalog;\n" +
        "    return catalog;\n" +
        "};\n\n" +
        "const rejectControlledPackageTarget = (code, message) => {",
    "catalog derivation capture",
);
workerSource = replaceExactlyOnce(
    workerSource,
    "const resolveControlledNodeSnapshot = (request, packageMetadata, targets) => {",
    "const resolveControlledNodeSnapshot = (request, packageMetadata, targets) => {\n" +
        "    globalThis.__r2P2WorkerTest.snapshotResolverCalls += 1;",
    "snapshot resolver counter",
);
workerSource = replaceExactlyOnce(
    workerSource,
    "const createControlledNodeResolutionSession = (vfs, options) => {",
    "const createControlledNodeResolutionSession = (vfs, options) => {\n" +
        "    globalThis.__r2P2WorkerTest.resolutionSessionCalls += 1;",
    "resolution session counter",
);

const realWorkerDigest =
    cryptoModule.webcrypto.subtle.digest.bind(cryptoModule.webcrypto.subtle);
let nextWorkerDigestGate = null;
const blockNextWorkerDigest = (label) => {
    assert(
        nextWorkerDigestGate === null,
        `${label} overlapped another Worker digest gate.`,
    );
    let markEntered;
    let release;
    const entered = new Promise((resolve) => {
        markEntered = resolve;
    });
    const released = new Promise((resolve) => {
        release = resolve;
    });
    nextWorkerDigestGate = { label, markEntered, released };
    return { entered, release };
};
Object.defineProperty(global, "crypto", {
    configurable: true,
    value: {
        subtle: {
            digest: async (...arguments_) => {
                const gate = nextWorkerDigestGate;
                if (gate !== null) {
                    nextWorkerDigestGate = null;
                    gate.markEntered();
                    await gate.released;
                }
                return realWorkerDigest(...arguments_);
            },
        },
    },
});
const realTypeScript = require(compilerPath);
const compilerCalls = {
    emit: 0,
    impliedFormat: 0,
    program: 0,
    resolveModule: 0,
    resolveType: 0,
    systemIo: 0,
};
const poisonedCompilerIo = new Proxy(realTypeScript.sys, {
    get() {
        compilerCalls.systemIo += 1;
        throw new Error("P2/P1 production Worker accessed TypeScript system I/O.");
    },
});
const typeScriptFacade = Object.create(realTypeScript);
Object.defineProperty(typeScriptFacade, "sys", {
    value: poisonedCompilerIo,
});
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

global.__r2P2WorkerTest = {
    catalogDerivations: 0,
    lastCatalog: null,
    lastDerivationInput: null,
    resolutionSessionCalls: 0,
    snapshotResolverCalls: 0,
};
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
        publishedMessages.length === 1 &&
        publishedMessages[0].kind === "ready" &&
        publishedMessages[0].tsVersion === expectedVersion,
    "Worker bootstrap, import boundary, or TypeScript version drifted.",
);
publishedMessages.length = 0;

const workerBinding = (expression) =>
    vm.runInThisContext(expression, {
        filename: `${workerPath}#node-package-format-worker-transport`,
    });
const workerNodeProfile = workerBinding(
    "provisionalNodeNextRevision2ProfileAuthority",
);
const workerCatalogAuthority = workerBinding("nodePackageFormatAuthority");
const workerCatalogCodes = workerBinding("nodePackageFormatPolicyCodes");
const workerPackageAuthority = workerBinding(
    "controlledPackageMetadataAuthority",
);
assert(
    exactJson(workerNodeProfile, {
        configKind: transport.authority.configKind,
        configSchemaRevision: transport.authority.configSchemaRevision,
        profileRevision: transport.authority.profileRevision,
        profileSchemaRevision: transport.authority.profileSchemaRevision,
        runtimeCapabilitiesFingerprint:
            transport.authority.runtimeCapabilitiesFingerprint,
        targetProfile: transport.authority.targetProfile,
    }),
    "Worker Node revision-2 profile authority drifted.",
);
assert(
    workerCatalogAuthority.schemaRevision ===
            catalogFixture.catalogAuthority.schemaRevision &&
        workerCatalogAuthority.catalogRevision ===
            catalogFixture.catalogAuthority.catalogRevision &&
        workerCatalogAuthority.canonicalPrefix ===
            catalogFixture.catalogAuthority.canonicalDomain &&
        workerCatalogAuthority.nodeModuleOutputPolicySchemaRevision ===
            nodeOutputPolicy.schemaRevision &&
        workerCatalogAuthority.nodeModuleOutputPolicyRevision ===
            nodeOutputPolicy.policyRevision &&
        workerCatalogAuthority.nodeModuleOutputPolicyFingerprint ===
            nodeOutputPolicy.fingerprint &&
        workerCatalogCodes.packageTypeInvalid === "NODE_PACKAGE_TYPE_INVALID" &&
        workerCatalogCodes.programNotActivated ===
            transport.authority.terminalPolicyCode,
    "Worker catalog/output authority drifted from the frozen fixtures.",
);
assert(
    workerPackageAuthority.schemaRevision === packagePolicy.schemaRevision &&
        workerPackageAuthority.policyRevision === packagePolicy.policyRevision &&
        workerPackageAuthority.policyFingerprint === packagePolicy.fingerprint &&
        workerPackageAuthority.canonicalPrefix ===
            packagePolicy.inventoryCanonicalDomain,
    "Worker package-metadata authority drifted from C5.",
);

const dispatch = async (message) => {
    publishedMessages.length = 0;
    await self.onmessage({ data: message });
    return publishedMessages.slice();
};
const waitForDigestGate = async (gate, label) => {
    let timeout = null;
    try {
        await Promise.race([
            gate.entered,
            new Promise((_, reject) => {
                timeout = setTimeout(
                    () => reject(new Error(`${label} did not reach WebCrypto.`)),
                    5_000,
                );
            }),
        ]);
    } finally {
        if (timeout !== null) clearTimeout(timeout);
    }
};
const expectAck = (messages, operation, sequence, label) => {
    const acknowledgements = messages.filter(
        (message) => message.kind === "projectInputAck",
    );
    assert(
        acknowledgements.length === 1 &&
            acknowledgements[0].requestId === operation.requestId &&
            acknowledgements[0].sequence === sequence &&
            !messages.some(
                (message) =>
                    message.kind === "projectError" ||
                    message.kind === "projectConfigError",
            ),
        `${label} did not publish its exact ACK: ${JSON.stringify(messages)}.`,
    );
};
const expectProjectErrorWithoutAck = (
    messages,
    requestId,
    label,
    expectedCode = null,
) => {
    const errors = messages.filter(
        (message) =>
            message.kind === "projectError" && message.requestId === requestId,
    );
    assert(
        errors.length === 1 &&
            !messages.some((message) => message.kind === "projectInputAck") &&
            !messages.some((message) => message.kind.startsWith("projectResult")),
        `${label} did not publish one transport-only projectError without ACK.`,
    );
    if (expectedCode !== null) {
        assert(
            errors[0].message.includes(expectedCode),
            `${label} did not preserve stable code ${expectedCode}: ` +
                errors[0].message,
        );
    }
    return errors[0];
};
const assertWorkerIdle = (label) => {
    assert(
        workerBinding("framedInput") === null &&
            workerBinding("framedCompilationRequestId") === null &&
            workerBinding("framedResult") === null &&
            workerBinding("pendingProjectInputFrame") === null &&
            workerBinding("frameHandlerBusy") === false,
        `${label} retained Worker operation state.`,
    );
};
const assertNoP2Result = (requestId, label) => {
    assert(
        !allPublishedMessages.some(
            (message) =>
                message.requestId === requestId &&
                message.kind.startsWith("projectResult"),
        ),
        `${label} activated a framed project result.`,
    );
};
const assertP2SemanticZero = (label) => {
    assert(
        compilerCalls.program === 0 &&
            compilerCalls.emit === 0 &&
            compilerCalls.impliedFormat === 0 &&
            compilerCalls.resolveModule === 0 &&
            compilerCalls.resolveType === 0 &&
            compilerCalls.systemIo === 0 &&
            global.__r2P2WorkerTest.resolutionSessionCalls === 0 &&
            global.__r2P2WorkerTest.snapshotResolverCalls === 0,
        `${label} activated P2 resolver/Program/emit or host I/O: ` +
            JSON.stringify({
                ...compilerCalls,
                resolutionSessionCalls:
                    global.__r2P2WorkerTest.resolutionSessionCalls,
                snapshotResolverCalls:
                    global.__r2P2WorkerTest.snapshotResolverCalls,
            }),
    );
};
const resetCatalogCapture = () => {
    global.__r2P2WorkerTest.lastCatalog = null;
    global.__r2P2WorkerTest.lastDerivationInput = null;
};

const libraryText =
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
const ordinaryFileDefinitions = [
    { path: "/declarations/lib.d.ts", text: libraryText },
    { path: "/declarations/node/index.d.ts", text: "export {};\n" },
    { path: "/sources/main.ts", text: "export const answer: number = 42;\n" },
];
const createRecord = (filePath, bytes, kind) => ({
    bytes: Buffer.from(bytes),
    kind,
    path: filePath,
    sha256: sha256(bytes),
});
const ordinaryRecords = () =>
    ordinaryFileDefinitions.map((file) =>
        createRecord(file.path, Buffer.from(file.text, "utf8"), "ordinary"),
    );

const authorityRecord = (record) =>
    inputArchiveRecord(
        record.kind === "metadata"
            ? "package-metadata"
            : record.path.startsWith("/declarations/")
              ? "declaration"
              : "source",
        record.path,
        record.bytes,
    );

const buildInventory = (dependencyRoots, packageFiles) => {
    const roots = dependencyRoots.slice().sort();
    const files = packageFiles
        .map((file) => {
            const bytes = Buffer.from(file.json, "utf8");
            return {
                bytes,
                json: file.json,
                path: file.path,
                sha256: sha256(bytes),
                size: bytes.length,
            };
        })
        .sort((left, right) =>
            left.path < right.path ? -1 : left.path > right.path ? 1 : 0,
        );
    const canonicalInput =
        packagePolicy.inventoryCanonicalDomain +
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

let requestCounter = 0;
const nextRequestId = (label) => {
    requestCounter += 1;
    return `p2-node-format-${requestCounter}-${label}`.slice(0, 128);
};
const createP2Operation = (
    label,
    dependencyRoots = null,
    packageFiles = null,
    declaredCatalogFingerprint = null,
) => {
    const inventory = packageFiles === null
        ? null
        : buildInventory(dependencyRoots, packageFiles);
    const metadataRecords = inventory === null
        ? []
        : inventory.files.map((file) =>
              createRecord(`/${file.path}`, file.bytes, "metadata"),
          );
    const records = metadataRecords.concat(ordinaryRecords());
    const authority = projectInputAuthority({
        fileRecords: records.map(authorityRecord),
    });
    return {
        authority,
        catalogFingerprint: declaredCatalogFingerprint,
        inventory,
        records,
        requestId: nextRequestId(label),
        sequence: 0,
        totalBytes: records.reduce(
            (total, record) => total + record.bytes.length,
            0,
        ),
    };
};
const noBoundaryStartFrame = (operation) => ({
    ...operation.authority,
    configKind: transport.authority.configKind,
    configSchemaRevision: transport.authority.configSchemaRevision,
    entryPath: transport.authority.entryPath,
    fileCount: operation.records.length,
    kind: "projectInputStart",
    profileRevision: transport.authority.profileRevision,
    profileSchemaRevision: transport.authority.profileSchemaRevision,
    requestId: operation.requestId,
    runtimeCapabilitiesFingerprint:
        transport.authority.runtimeCapabilitiesFingerprint,
    sequence: operation.sequence,
    targetProfile: transport.authority.targetProfile,
    totalBytes: operation.totalBytes,
    workerSpinMilliseconds: 0,
    workerSpinPhase: "none",
});
const presentStartFrame = (operation) => ({
    ...noBoundaryStartFrame(operation),
    nodeModuleOutputPolicyFingerprint: nodeOutputPolicy.fingerprint,
    nodeModuleOutputPolicyRevision: nodeOutputPolicy.policyRevision,
    nodeModuleOutputPolicySchemaRevision: nodeOutputPolicy.schemaRevision,
    nodePackageFormatCatalogFingerprint: operation.catalogFingerprint,
    nodePackageFormatCatalogRevision:
        catalogFixture.catalogAuthority.catalogRevision,
    nodePackageFormatSchemaRevision:
        catalogFixture.catalogAuthority.schemaRevision,
    packageMetadataByteLength: operation.inventory.totalBytes,
    packageMetadataCanonical: operation.inventory.canonicalInput,
    packageMetadataFileCount: operation.inventory.files.length,
    packageMetadataInventoryFingerprint: operation.inventory.fingerprint,
    packageMetadataPolicyFingerprint: packagePolicy.fingerprint,
    packageMetadataPolicyRevision: packagePolicy.policyRevision,
    packageMetadataSchemaRevision: packagePolicy.schemaRevision,
});
const startFrame = (operation) =>
    operation.inventory === null
        ? noBoundaryStartFrame(operation)
        : presentStartFrame(operation);
const acceptStart = async (operation, frame = startFrame(operation)) => {
    assert(
        exactKeys(
            frame,
            operation.inventory === null
                ? transport.exactStartKeys.noBoundary
                : transport.exactStartKeys.present,
        ),
        `${operation.requestId} start did not use its exact frozen key set.`,
    );
    const messages = await dispatch(frame);
    expectAck(messages, operation, operation.sequence, `${operation.requestId} start`);
    operation.sequence += 1;
};

const fileStartFrame = (
    operation,
    record,
    fileIndex,
    pathValue = record.path,
    digest = record.sha256,
) => ({
    byteLength: record.bytes.length,
    fileIndex,
    kind: "projectInputFileStart",
    path: pathValue,
    requestId: operation.requestId,
    sequence: operation.sequence,
    sha256: digest,
});
const sendAcceptedFile = async (operation, record, fileIndex) => {
    let messages = await dispatch(fileStartFrame(operation, record, fileIndex));
    expectAck(
        messages,
        operation,
        operation.sequence,
        `${operation.requestId} ${record.path} start`,
    );
    operation.sequence += 1;
    let chunkCount = 0;
    for (let offset = 0; offset < record.bytes.length; offset += 64 * 1024) {
        messages = await dispatch({
            chunkIndex: chunkCount,
            data: record.bytes
                .subarray(
                    offset,
                    Math.min(offset + 64 * 1024, record.bytes.length),
                )
                .toString("base64"),
            fileIndex,
            kind: "projectInputFileChunk",
            requestId: operation.requestId,
            sequence: operation.sequence,
        });
        expectAck(
            messages,
            operation,
            operation.sequence,
            `${operation.requestId} ${record.path} chunk ${chunkCount}`,
        );
        operation.sequence += 1;
        chunkCount += 1;
    }
    const rawBytes = workerBinding("framedInput.currentFile.bytes");
    messages = await dispatch({
        chunkCount,
        fileIndex,
        kind: "projectInputFileEnd",
        requestId: operation.requestId,
        sequence: operation.sequence,
    });
    expectAck(
        messages,
        operation,
        operation.sequence,
        `${operation.requestId} ${record.path} end`,
    );
    assert(
        allZero(rawBytes),
        `${operation.requestId} ${record.path} retained raw bytes after fileEnd.`,
    );
    operation.sequence += 1;
};
const prepareAcceptedFileEnd = async (operation, record, fileIndex) => {
    let messages = await dispatch(fileStartFrame(operation, record, fileIndex));
    expectAck(
        messages,
        operation,
        operation.sequence,
        `${operation.requestId} ${record.path} race start`,
    );
    operation.sequence += 1;
    let chunkCount = 0;
    for (let offset = 0; offset < record.bytes.length; offset += 64 * 1024) {
        messages = await dispatch({
            chunkIndex: chunkCount,
            data: record.bytes
                .subarray(
                    offset,
                    Math.min(offset + 64 * 1024, record.bytes.length),
                )
                .toString("base64"),
            fileIndex,
            kind: "projectInputFileChunk",
            requestId: operation.requestId,
            sequence: operation.sequence,
        });
        expectAck(
            messages,
            operation,
            operation.sequence,
            `${operation.requestId} ${record.path} race chunk ${chunkCount}`,
        );
        operation.sequence += 1;
        chunkCount += 1;
    }
    return {
        frame: {
            chunkCount,
            fileIndex,
            kind: "projectInputFileEnd",
            requestId: operation.requestId,
            sequence: operation.sequence,
        },
        rawBytes: workerBinding("framedInput.currentFile.bytes"),
    };
};
const assertTypedRecords = (operation, acceptedMetadataCount, expectedCatalog) => {
    const records = workerBinding("framedInput.nodePackageFormatRecords");
    assert(
        Array.isArray(records) && records.length === acceptedMetadataCount,
        `${operation.requestId} did not install one typed record per metadata file.`,
    );
    for (let index = 0; index < records.length; index += 1) {
        const actual = records[index];
        const expected = expectedCatalog.records[index];
        assert(
            Object.isFrozen(actual) &&
                exactKeys(actual, [
                    "packageBoundaryPath",
                    "packageMetadataPath",
                    "packageMetadataSha256",
                    "packageMetadataSize",
                    "typeState",
                ]) &&
                !hasOwn(actual, "root") &&
                actual.packageBoundaryPath === expected.packageBoundaryPath &&
                actual.packageMetadataPath === expected.packageMetadataPath &&
                actual.packageMetadataSha256 === expected.packageMetadataSha256 &&
                actual.packageMetadataSize === expected.packageMetadataSize &&
                actual.typeState === expected.typeState,
            `${operation.requestId} typed record ${index} drifted or retained raw root.`,
        );
    }
};
const sendAllAcceptedFiles = async (
    operation,
    expectedCatalog = null,
) => {
    let acceptedMetadataCount = 0;
    for (let index = 0; index < operation.records.length; index += 1) {
        const record = operation.records[index];
        await sendAcceptedFile(operation, record, index);
        if (record.kind === "metadata") {
            acceptedMetadataCount += 1;
            assertTypedRecords(operation, acceptedMetadataCount, expectedCatalog);
        }
    }
};
const inputEndFrame = (operation, extra = {}) => ({
    fileCount: operation.records.length,
    kind: "projectInputEnd",
    requestId: operation.requestId,
    sequence: operation.sequence,
    totalBytes: operation.totalBytes,
    ...extra,
});
const assertCatalogCapture = (operation, expectedCatalog, label) => {
    const capture = global.__r2P2WorkerTest;
    const actual = capture.lastCatalog;
    const derivationInput = capture.lastDerivationInput;
    assert(
        actual !== null &&
            derivationInput !== null &&
            exactKeys(actual, [
                "canonicalInput",
                "dependencyRoots",
                "fingerprint",
                "inventoryCanonicalInput",
                "inventoryFingerprint",
                "records",
            ]) &&
            isDeepFrozen(actual) &&
            actual.canonicalInput === expectedCatalog.catalogCanonicalInput &&
            actual.fingerprint === expectedCatalog.catalogFingerprint &&
            actual.inventoryCanonicalInput ===
                expectedCatalog.inventoryCanonicalInput &&
            actual.inventoryFingerprint ===
                expectedCatalog.packageMetadataInventoryFingerprint &&
            exactJson(actual.dependencyRoots, operation.inventory.dependencyRoots) &&
            actual.records.length === expectedCatalog.records.length &&
            derivationInput.records.length === expectedCatalog.records.length,
        `${label} did not derive the exact deep-frozen catalog identity.`,
    );
    for (let index = 0; index < actual.records.length; index += 1) {
        const record = actual.records[index];
        const inputRecord = derivationInput.records[index];
        const expected = expectedCatalog.records[index];
        for (const candidate of [record, inputRecord]) {
            assert(
                exactKeys(candidate, [
                    "packageBoundaryPath",
                    "packageMetadataPath",
                    "packageMetadataSha256",
                    "packageMetadataSize",
                    "typeState",
                ]) &&
                    Object.isFrozen(candidate) &&
                    !hasOwn(candidate, "root") &&
                    candidate.packageBoundaryPath === expected.packageBoundaryPath &&
                    candidate.packageMetadataPath === expected.packageMetadataPath &&
                    candidate.packageMetadataSha256 ===
                        expected.packageMetadataSha256 &&
                    candidate.packageMetadataSize === expected.packageMetadataSize &&
                    candidate.typeState === expected.typeState,
                `${label} catalog record ${index} drifted or retained parsed root.`,
            );
        }
    }
};
const finishP2TransportOnly = async (
    operation,
    label,
    expectedCatalog = null,
    expectedCode = transport.authority.terminalPolicyCode,
) => {
    const derivationsBefore = global.__r2P2WorkerTest.catalogDerivations;
    resetCatalogCapture();
    const messages = await dispatch(inputEndFrame(operation));
    expectProjectErrorWithoutAck(
        messages,
        operation.requestId,
        label,
        expectedCode,
    );
    if (expectedCatalog === null) {
        assert(
            global.__r2P2WorkerTest.catalogDerivations === derivationsBefore &&
                global.__r2P2WorkerTest.lastCatalog === null,
            `${label} unexpectedly derived a package-boundary catalog.`,
        );
    } else {
        assert(
            global.__r2P2WorkerTest.catalogDerivations ===
                derivationsBefore + 1,
            `${label} did not derive exactly one catalog at inputEnd.`,
        );
        assertCatalogCapture(operation, expectedCatalog, label);
    }
    assertWorkerIdle(label);
    assertNoP2Result(operation.requestId, label);
    assertP2SemanticZero(label);
};

const executedRejections = new Set();
const recordRejection = (id) => {
    assert(
        transport.requiredRejectionIds.includes(id),
        `Harness executed an undeclared rejection: ${id}.`,
    );
    assert(!executedRejections.has(id), `Harness repeated rejection: ${id}.`);
    executedRejections.add(id);
};

const runRejectedStart = async (id, mode, mutate) => {
    const testCase = catalogFixture.validCases[0];
    const expectedCatalog = catalogOracle.deriveCatalog(
        testCase.dependencyRoots,
        testCase.files,
    );
    const operation = mode === "noBoundary"
        ? createP2Operation(id)
        : createP2Operation(
              id,
              testCase.dependencyRoots,
              testCase.files,
              expectedCatalog.catalogFingerprint,
          );
    const frame = startFrame(operation);
    mutate(frame, operation);
    const messages = await dispatch(frame);
    expectProjectErrorWithoutAck(messages, operation.requestId, id);
    recordRejection(id);
    assertWorkerIdle(id);
    assertNoP2Result(operation.requestId, id);
    assertP2SemanticZero(id);
};

const runEntryNotInInventoryRejection = async () => {
    const id = "entry-path-not-in-inventory";
    const operation = createP2Operation(id);
    const frame = noBoundaryStartFrame(operation);
    frame.entryPath = "/sources/missing.ts";
    await acceptStart(operation, frame);
    await sendAllAcceptedFiles(operation);
    const messages = await dispatch(inputEndFrame(operation));
    expectProjectErrorWithoutAck(messages, operation.requestId, id);
    recordRejection(id);
    assertWorkerIdle(id);
    assertNoP2Result(operation.requestId, id);
    assertP2SemanticZero(id);
};

const runSourceKindRejection = async (extension) => {
    const id = `source-kind-${extension.slice(1)}`;
    const operation = createP2Operation(id);
    await acceptStart(operation);
    const rejectedRecord = createRecord(
        `/sources/rejected${extension}`,
        Buffer.from("export {};\n", "utf8"),
        "ordinary",
    );
    const messages = await dispatch(
        fileStartFrame(operation, rejectedRecord, 0),
    );
    expectProjectErrorWithoutAck(messages, operation.requestId, id);
    recordRejection(id);
    assertWorkerIdle(id);
    assertNoP2Result(operation.requestId, id);
    assertP2SemanticZero(id);
};

const runEndAccountingRejection = async (id, field) => {
    const testCase = catalogFixture.validCases[0];
    const expectedCatalog = catalogOracle.deriveCatalog(
        testCase.dependencyRoots,
        testCase.files,
    );
    const operation = createP2Operation(
        id,
        testCase.dependencyRoots,
        testCase.files,
        expectedCatalog.catalogFingerprint,
    );
    const frame = presentStartFrame(operation);
    await acceptStart(operation, frame);
    await sendAllAcceptedFiles(operation, expectedCatalog);
    const end = {
        fileCount: frame.fileCount,
        kind: "projectInputEnd",
        requestId: operation.requestId,
        sequence: operation.sequence,
        totalBytes: frame.totalBytes,
    };
    end[field] += 1;
    const messages = await dispatch(end);
    expectProjectErrorWithoutAck(messages, operation.requestId, id);
    recordRejection(id);
    assertWorkerIdle(id);
    assertNoP2Result(operation.requestId, id);
    assertP2SemanticZero(id);
};

const runCatalogFingerprintRejection = async () => {
    const id = "fingerprint-catalog";
    const testCase = catalogFixture.validCases[0];
    const expectedCatalog = catalogOracle.deriveCatalog(
        testCase.dependencyRoots,
        testCase.files,
    );
    const operation = createP2Operation(
        id,
        testCase.dependencyRoots,
        testCase.files,
        "0".repeat(64),
    );
    await acceptStart(operation);
    await sendAllAcceptedFiles(operation, expectedCatalog);
    const derivationsBefore = global.__r2P2WorkerTest.catalogDerivations;
    resetCatalogCapture();
    const messages = await dispatch(inputEndFrame(operation));
    expectProjectErrorWithoutAck(messages, operation.requestId, id);
    assert(
        global.__r2P2WorkerTest.catalogDerivations === derivationsBefore + 1,
        `${id} did not reach independent catalog derivation.`,
    );
    assertCatalogCapture(operation, expectedCatalog, id);
    recordRejection(id);
    assertWorkerIdle(id);
    assertNoP2Result(operation.requestId, id);
    assertP2SemanticZero(id);
};

const runWrongPathRejection = async () => {
    const id = "order-package-path";
    const testCase = catalogFixture.validCases[0];
    const expectedCatalog = catalogOracle.deriveCatalog(
        testCase.dependencyRoots,
        testCase.files,
    );
    const operation = createP2Operation(
        id,
        testCase.dependencyRoots,
        testCase.files,
        expectedCatalog.catalogFingerprint,
    );
    await acceptStart(operation);
    const messages = await dispatch(
        fileStartFrame(
            operation,
            operation.records[0],
            0,
            "/packages/out-of-order/package.json",
        ),
    );
    expectProjectErrorWithoutAck(messages, operation.requestId, id);
    recordRejection(id);
    assertWorkerIdle(id);
    assertNoP2Result(operation.requestId, id);
    assertP2SemanticZero(id);
};

const runDescriptorDigestRejection = async () => {
    const id = "digest-package-descriptor";
    const testCase = catalogFixture.validCases[0];
    const expectedCatalog = catalogOracle.deriveCatalog(
        testCase.dependencyRoots,
        testCase.files,
    );
    const operation = createP2Operation(
        id,
        testCase.dependencyRoots,
        testCase.files,
        expectedCatalog.catalogFingerprint,
    );
    await acceptStart(operation);
    const messages = await dispatch(
        fileStartFrame(
            operation,
            operation.records[0],
            0,
            operation.records[0].path,
            "f".repeat(64),
        ),
    );
    expectProjectErrorWithoutAck(messages, operation.requestId, id);
    recordRejection(id);
    assertWorkerIdle(id);
    assertNoP2Result(operation.requestId, id);
    assertP2SemanticZero(id);
};

const runContentDigestRejection = async () => {
    const id = "digest-package-content";
    const testCase = catalogFixture.validCases[0];
    const expectedCatalog = catalogOracle.deriveCatalog(
        testCase.dependencyRoots,
        testCase.files,
    );
    const operation = createP2Operation(
        id,
        testCase.dependencyRoots,
        testCase.files,
        expectedCatalog.catalogFingerprint,
    );
    const record = operation.records[0];
    await acceptStart(operation);
    let messages = await dispatch(fileStartFrame(operation, record, 0));
    expectAck(messages, operation, operation.sequence, `${id} file start`);
    operation.sequence += 1;
    const changedBytes = Buffer.from(record.bytes);
    changedBytes[0] ^= 1;
    messages = await dispatch({
        chunkIndex: 0,
        data: changedBytes.toString("base64"),
        fileIndex: 0,
        kind: "projectInputFileChunk",
        requestId: operation.requestId,
        sequence: operation.sequence,
    });
    expectAck(messages, operation, operation.sequence, `${id} file chunk`);
    operation.sequence += 1;
    const rawBytes = workerBinding("framedInput.currentFile.bytes");
    messages = await dispatch({
        chunkCount: 1,
        fileIndex: 0,
        kind: "projectInputFileEnd",
        requestId: operation.requestId,
        sequence: operation.sequence,
    });
    expectProjectErrorWithoutAck(messages, operation.requestId, id);
    assert(allZero(rawBytes), `${id} retained rejected raw metadata bytes.`);
    recordRejection(id);
    assertWorkerIdle(id);
    assertNoP2Result(operation.requestId, id);
    assertP2SemanticZero(id);
};

const runInvalidTypeCase = async (testCase) => {
    const label = `invalid-type-${testCase.id}`;
    const operation = createP2Operation(
        label,
        [],
        [{ path: "package.json", json: testCase.json }],
        "0".repeat(64),
    );
    await acceptStart(operation);
    const record = operation.records[0];
    let messages = await dispatch(fileStartFrame(operation, record, 0));
    expectAck(messages, operation, operation.sequence, `${label} start`);
    operation.sequence += 1;
    messages = await dispatch({
        chunkIndex: 0,
        data: record.bytes.toString("base64"),
        fileIndex: 0,
        kind: "projectInputFileChunk",
        requestId: operation.requestId,
        sequence: operation.sequence,
    });
    expectAck(messages, operation, operation.sequence, `${label} chunk`);
    operation.sequence += 1;
    const rawBytes = workerBinding("framedInput.currentFile.bytes");
    const derivationsBefore = global.__r2P2WorkerTest.catalogDerivations;
    messages = await dispatch({
        chunkCount: 1,
        fileIndex: 0,
        kind: "projectInputFileEnd",
        requestId: operation.requestId,
        sequence: operation.sequence,
    });
    expectProjectErrorWithoutAck(
        messages,
        operation.requestId,
        label,
        catalogOracle.failureCodes.packageTypeInvalid,
    );
    assert(
        allZero(rawBytes) &&
            global.__r2P2WorkerTest.catalogDerivations === derivationsBefore,
        `${label} retained raw bytes or reached catalog derivation.`,
    );
    assertWorkerIdle(label);
    assertNoP2Result(operation.requestId, label);
    assertP2SemanticZero(label);
};

const runStaleCatalogAttack = async () => {
    const original = catalogFixture.validCases.find(
        (testCase) => testCase.id === "root-module",
    );
    const changedFiles = original.files.map((file) => ({
        path: file.path,
        json: file.path === "package.json"
            ? "{\"type\":\"commonjs\"}"
            : file.json,
    }));
    const expectedCatalog = catalogOracle.deriveCatalog(
        original.dependencyRoots,
        changedFiles.map((file) => {
            const bytes = Buffer.from(file.json, "utf8");
            return {
                ...file,
                sha256: sha256(bytes),
                size: bytes.length,
            };
        }),
    );
    assert(
        expectedCatalog.catalogFingerprint !== original.catalogFingerprint,
        "Stale-catalog attack did not change the independently derived catalog.",
    );
    const operation = createP2Operation(
        "stale-catalog-recomputed-c5",
        original.dependencyRoots,
        changedFiles,
        original.catalogFingerprint,
    );
    await acceptStart(operation);
    await sendAllAcceptedFiles(operation, expectedCatalog);
    const derivationsBefore = global.__r2P2WorkerTest.catalogDerivations;
    resetCatalogCapture();
    const messages = await dispatch(inputEndFrame(operation));
    expectProjectErrorWithoutAck(
        messages,
        operation.requestId,
        "stale catalog with recomputed C5",
    );
    assert(
        global.__r2P2WorkerTest.catalogDerivations === derivationsBefore + 1,
        "Stale-catalog attack did not reach end-of-input catalog derivation.",
    );
    assertCatalogCapture(operation, expectedCatalog, "stale catalog attack");
    assertWorkerIdle("stale catalog attack");
    assertNoP2Result(operation.requestId, "stale catalog attack");
    assertP2SemanticZero("stale catalog attack");
};

const runActiveCollision = async () => {
    const id = "cleanup-active-operation-collision";
    const incumbent = createP2Operation(`${id}-incumbent`);
    await acceptStart(incumbent);
    const challenger = createP2Operation(`${id}-challenger`);
    const messages = await dispatch(noBoundaryStartFrame(challenger));
    expectProjectErrorWithoutAck(messages, challenger.requestId, id);
    assert(
        workerBinding("framedInput.requestId") === incumbent.requestId &&
            workerBinding("framedCompilationRequestId") === null &&
            workerBinding("framedResult") === null,
        "Rejected challenger disturbed its active incumbent.",
    );
    recordRejection(id);
    assertNoP2Result(challenger.requestId, id);
    await sendAllAcceptedFiles(incumbent);
    await finishP2TransportOnly(
        incumbent,
        "active-operation incumbent cleanup",
    );
    assertP2SemanticZero(id);
};

const messagesForRequestSince = (marker, requestId) =>
    allPublishedMessages
        .slice(marker)
        .filter((message) => message.requestId === requestId);

const assertCancelledRaceMessages = (messages, operation, id) => {
    expectProjectErrorWithoutAck(messages, operation.requestId, id);
    assert(
        !messages.some(
            (message) =>
                message.kind === "projectPhase" ||
                message.kind === "projectPhaseProgress",
        ),
        `${id} published phase state after its terminal race.`,
    );
    recordRejection(id);
    assertWorkerIdle(id);
    assertNoP2Result(operation.requestId, id);
    assertP2SemanticZero(id);
};

const runDelayedStartSameIdRace = async () => {
    const id = "race-delayed-start-same-id";
    const testCase = catalogFixture.validCases[0];
    const expectedCatalog = catalogOracle.deriveCatalog(
        testCase.dependencyRoots,
        testCase.files,
    );
    const operation = createP2Operation(
        id,
        testCase.dependencyRoots,
        testCase.files,
        expectedCatalog.catalogFingerprint,
    );
    const frame = presentStartFrame(operation);
    const gate = blockNextWorkerDigest(id);
    const marker = allPublishedMessages.length;
    const owner = self.onmessage({ data: frame });
    await waitForDigestGate(gate, id);
    await self.onmessage({ data: frame });
    await self.onmessage({ data: frame });
    gate.release();
    await owner;
    assertCancelledRaceMessages(
        messagesForRequestSince(marker, operation.requestId),
        operation,
        id,
    );
};

const runDelayedFileEndSameIdRace = async () => {
    const id = "race-delayed-file-end-same-id";
    const testCase = catalogFixture.validCases[0];
    const expectedCatalog = catalogOracle.deriveCatalog(
        testCase.dependencyRoots,
        testCase.files,
    );
    const operation = createP2Operation(
        id,
        testCase.dependencyRoots,
        testCase.files,
        expectedCatalog.catalogFingerprint,
    );
    await acceptStart(operation);
    const prepared = await prepareAcceptedFileEnd(
        operation,
        operation.records[0],
        0,
    );
    const gate = blockNextWorkerDigest(id);
    const marker = allPublishedMessages.length;
    const owner = self.onmessage({ data: prepared.frame });
    await waitForDigestGate(gate, id);
    await self.onmessage({ data: prepared.frame });
    await self.onmessage({ data: prepared.frame });
    gate.release();
    await owner;
    assert(
        allZero(prepared.rawBytes),
        `${id} retained its cancelled raw file bytes.`,
    );
    assertCancelledRaceMessages(
        messagesForRequestSince(marker, operation.requestId),
        operation,
        id,
    );
};

const runDelayedInputEndSameIdRace = async () => {
    const id = "race-delayed-input-end-same-id";
    const testCase = catalogFixture.validCases[0];
    const expectedCatalog = catalogOracle.deriveCatalog(
        testCase.dependencyRoots,
        testCase.files,
    );
    const operation = createP2Operation(
        id,
        testCase.dependencyRoots,
        testCase.files,
        expectedCatalog.catalogFingerprint,
    );
    await acceptStart(operation);
    await sendAllAcceptedFiles(operation, expectedCatalog);
    const frame = inputEndFrame(operation);
    const gate = blockNextWorkerDigest(id);
    const marker = allPublishedMessages.length;
    const owner = self.onmessage({ data: frame });
    await waitForDigestGate(gate, id);
    await self.onmessage({ data: frame });
    await self.onmessage({ data: frame });
    gate.release();
    await owner;
    const messages = messagesForRequestSince(marker, operation.requestId);
    const error = expectProjectErrorWithoutAck(
        messages,
        operation.requestId,
        id,
    );
    assert(
        !error.message.includes(transport.authority.terminalPolicyCode),
        `${id} published a late P2 terminal after cancellation.`,
    );
    assertCancelledRaceMessages(messages, operation, id);
};

const runDelayedStartDifferentIdRace = async () => {
    const id = "race-delayed-start-different-id";
    const testCase = catalogFixture.validCases[0];
    const expectedCatalog = catalogOracle.deriveCatalog(
        testCase.dependencyRoots,
        testCase.files,
    );
    const incumbent = createP2Operation(
        `${id}-incumbent`,
        testCase.dependencyRoots,
        testCase.files,
        expectedCatalog.catalogFingerprint,
    );
    const challenger = createP2Operation(`${id}-challenger`);
    const gate = blockNextWorkerDigest(id);
    const marker = allPublishedMessages.length;
    const owner = self.onmessage({ data: presentStartFrame(incumbent) });
    await waitForDigestGate(gate, id);
    await self.onmessage({ data: noBoundaryStartFrame(challenger) });
    expectProjectErrorWithoutAck(
        messagesForRequestSince(marker, challenger.requestId),
        challenger.requestId,
        id,
    );
    assert(
        workerBinding("pendingProjectInputFrame.requestId") ===
                incumbent.requestId &&
            workerBinding("pendingProjectInputFrame.cancelled") === false,
        `${id} cancelled or replaced the delayed incumbent reservation.`,
    );
    gate.release();
    await owner;
    expectAck(
        messagesForRequestSince(marker, incumbent.requestId),
        incumbent,
        incumbent.sequence,
        `${id} incumbent start`,
    );
    assert(
        workerBinding("framedInput.requestId") === incumbent.requestId,
        `${id} did not preserve its incumbent after the challenger terminal.`,
    );
    incumbent.sequence += 1;
    recordRejection(id);
    assertNoP2Result(challenger.requestId, id);
    await sendAllAcceptedFiles(incumbent, expectedCatalog);
    await finishP2TransportOnly(
        incumbent,
        `${id} incumbent cleanup`,
        expectedCatalog,
    );
};

const verifyCompilerHostExtendedStartEnvelope = (largeStartFrame) => {
    const hostPath = path.join(path.dirname(workerPath), "compiler-host.js");
    const hostSource = fs.readFileSync(hostPath, "utf8");
    const runCase = (encoded, label) => {
        const nativeMessages = [];
        const workers = [];
        let bootstrapListener = null;
        class FakeWorker {
            constructor(specifier) {
                assert(
                    specifier === "compiler-worker.js",
                    `${label} spawned an unexpected Worker.`,
                );
                this.onmessage = null;
                this.onerror = null;
                this.onmessageerror = null;
                this.posted = [];
                this.terminated = false;
                workers.push(this);
            }

            postMessage(message) {
                assert(!this.terminated, `${label} posted to a terminated Worker.`);
                this.posted.push(message);
            }

            terminate() {
                this.terminated = true;
            }

            emit(message) {
                this.onmessage({ data: message });
            }
        }
        const nativePort = {
            close() {},
            onmessage: null,
            postMessage(message) {
                assert(
                    Buffer.byteLength(message, "utf8") <= 128 * 1024,
                    `${label} let the host publish an oversized native frame.`,
                );
                nativeMessages.push(JSON.parse(message));
            },
            start() {},
        };
        const hostSelf = {
            addEventListener(kind, listener) {
                assert(kind === "message", `${label} registered a wrong listener.`);
                bootstrapListener = listener;
            },
        };
        const context = vm.createContext({
            JSON,
            TextEncoder,
            Worker: FakeWorker,
            self: hostSelf,
        });
        vm.runInContext(hostSource, context, { filename: hostPath });
        assert(bootstrapListener !== null, `${label} missed host bootstrap.`);
        bootstrapListener({
            data: "__autojs_typescript_compiler_port_v1__",
            ports: [nativePort],
        });
        assert(workers.length === 1, `${label} did not create one Worker.`);
        workers[0].emit({ kind: "ready", tsVersion: expectedVersion });
        nativePort.onmessage({ data: encoded });
        return { nativeMessages, worker: workers[0] };
    };

    const encodedLargeStart = JSON.stringify(largeStartFrame);
    const largeStartBytes = Buffer.byteLength(encodedLargeStart, "utf8");
    assert(
        largeStartBytes > 128 * 1024 && largeStartBytes <= 576 * 1024,
        "Generated Node revision-2 start missed the host extended envelope.",
    );
    const accepted = runCase(encodedLargeStart, "extended Node2 start");
    assert(
        accepted.worker.posted.length === 1 &&
            exactJson(accepted.worker.posted[0], largeStartFrame),
        "Compiler host did not forward the >128 KiB privileged Node2 start.",
    );

    const oversizedProbe = JSON.stringify({
        kind: "probe",
        padding: "x".repeat(140 * 1024),
        requestId: "host-oversized-probe",
        vfs: {},
    });
    assert(
        Buffer.byteLength(oversizedProbe, "utf8") > 128 * 1024 &&
            Buffer.byteLength(oversizedProbe, "utf8") < 576 * 1024,
        "Oversized host probe fixture missed its intended boundary.",
    );
    assert(
        runCase(oversizedProbe, "oversized ordinary probe").worker.posted
            .length === 0,
        "Compiler host forwarded a >128 KiB ordinary frame.",
    );

    const nonPrivilegedStart = JSON.stringify({
        ...largeStartFrame,
        targetProfile: "rhino",
    });
    assert(
        runCase(nonPrivilegedStart, "oversized non-privileged start").worker
            .posted.length === 0,
        "Compiler host forwarded a >128 KiB non-privileged start.",
    );

    const paddedNodeStart = JSON.stringify({
        ...largeStartFrame,
        ordinaryPadding: "x".repeat(140 * 1024),
    });
    assert(
        Buffer.byteLength(paddedNodeStart, "utf8") > 128 * 1024 &&
            Buffer.byteLength(paddedNodeStart, "utf8") < 576 * 1024,
        "Padded Node2 start fixture missed the extended page boundary.",
    );
    assert(
        runCase(paddedNodeStart, "padded non-exact Node2 start").worker.posted
            .length === 0,
        "Compiler host granted the extended envelope to a non-exact Node2 start.",
    );

    const absoluteOversize = JSON.stringify({
        ...largeStartFrame,
        packageMetadataCanonical: "x".repeat(576 * 1024),
    });
    assert(
        Buffer.byteLength(absoluteOversize, "utf8") > 576 * 1024 &&
            runCase(absoluteOversize, "absolute oversized Node2 start").worker
                .posted.length === 0,
        "Compiler host did not enforce the absolute 576 KiB ingress fence.",
    );

    const outbound = runCase(
        JSON.stringify({
            kind: "probe",
            requestId: "host-outbound-limit",
            vfs: {},
        }),
        "host outbound limit",
    );
    assert(
        outbound.worker.posted.length === 1,
        "Compiler host did not forward the outbound-limit probe setup.",
    );
    const nativeMessageCount = outbound.nativeMessages.length;
    outbound.worker.emit({
        kind: "probeError",
        message: "x".repeat(140 * 1024),
        requestId: "host-outbound-limit",
    });
    assert(
        outbound.nativeMessages.length === nativeMessageCount,
        "Compiler host published a >128 KiB Worker message to native.",
    );
};

const runGeneratedLargeCatalog = async () => {
    const dependencyRoots = Array.from(
        { length: 96 },
        (_, index) =>
            `packages/p${String(index).padStart(3, "0")}-${"x".repeat(880)}`,
    );
    const packageFiles = [
        { path: "package.json", json: "{}" },
        ...dependencyRoots.map((root) => ({
            path: `${root}/package.json`,
            json: "{}",
        })),
    ];
    const operation = createP2Operation(
        "large-start-over-128-kib",
        dependencyRoots,
        packageFiles,
    );
    const expectedCatalog = catalogOracle.deriveCatalog(
        dependencyRoots,
        operation.inventory.files,
    );
    operation.catalogFingerprint = expectedCatalog.catalogFingerprint;
    const frame = presentStartFrame(operation);
    assert(
        Buffer.byteLength(JSON.stringify(frame), "utf8") > 128 * 1024,
        "Generated P2 metadata start did not exceed 128 KiB.",
    );
    verifyCompilerHostExtendedStartEnvelope(frame);
    await acceptStart(operation, frame);
    await sendAllAcceptedFiles(operation, expectedCatalog);
    await finishP2TransportOnly(
        operation,
        "generated 96-root large catalog",
        expectedCatalog,
    );
};

const createP1Operation = () => {
    const records = ordinaryRecords();
    return {
        authority: projectInputAuthority({
            fileRecords: records.map(authorityRecord),
        }),
        records,
        requestId: nextRequestId("clean-p1-successor"),
        sequence: 0,
        totalBytes: records.reduce(
            (total, record) => total + record.bytes.length,
            0,
        ),
    };
};
const p1StartFrame = (operation) => ({
    ...operation.authority,
    configKind: "none",
    configSchemaRevision: 1,
    fileCount: operation.records.length,
    kind: "projectInputStart",
    profileRevision: 1,
    profileSchemaRevision: 1,
    requestId: operation.requestId,
    sequence: operation.sequence,
    targetProfile: "node",
    totalBytes: operation.totalBytes,
    workerSpinMilliseconds: 0,
    workerSpinPhase: "none",
});
const readP1Result = async (operation, inputEndMessages) => {
    expectAck(
        inputEndMessages,
        operation,
        operation.sequence,
        "clean P1 input end",
    );
    const start = inputEndMessages.find(
        (message) =>
            message.kind === "projectResultStart" &&
            message.requestId === operation.requestId,
    );
    assert(start, "Clean P1 successor did not start a framed result.");
    const chunks = [];
    for (let sequence = -1; sequence < start.chunkCount; sequence += 1) {
        const messages = await dispatch({
            kind: "projectResultAck",
            requestId: operation.requestId,
            sequence,
        });
        if (sequence + 1 < start.chunkCount) {
            const chunk = messages.find(
                (message) =>
                    message.kind === "projectResultChunk" &&
                    message.requestId === operation.requestId &&
                    message.index === sequence + 1,
            );
            assert(chunk, `Clean P1 result chunk ${sequence + 1} drifted.`);
            chunks.push(Buffer.from(chunk.data, "base64"));
        } else {
            assert(
                messages.some(
                    (message) =>
                        message.kind === "projectResultEnd" &&
                        message.requestId === operation.requestId &&
                        message.byteLength === start.byteLength &&
                        message.chunkCount === start.chunkCount &&
                        message.sha256 === start.sha256,
                ),
                "Clean P1 successor did not finish its framed result.",
            );
        }
    }
    const bytes = Buffer.concat(chunks);
    assert(
        bytes.length === start.byteLength && sha256(bytes) === start.sha256,
        "Clean P1 successor result bytes drifted from their frame identity.",
    );
    return JSON.parse(bytes.toString("utf8"));
};
const runCleanP1Successor = async () => {
    assertP2SemanticZero("before clean P1 successor");
    const operation = createP1Operation();
    const start = p1StartFrame(operation);
    assert(
        exactKeys(start, noBoundaryKeys.filter(
            (key) => key !== "entryPath" && key !== "runtimeCapabilitiesFingerprint",
        )),
        "Clean P1 successor did not use the exact schema-v1 legacy start.",
    );
    let messages = await dispatch(start);
    expectAck(messages, operation, operation.sequence, "clean P1 start");
    operation.sequence += 1;
    for (let index = 0; index < operation.records.length; index += 1) {
        await sendAcceptedFile(operation, operation.records[index], index);
    }
    messages = await dispatch({
        fileCount: operation.records.length,
        kind: "projectInputEnd",
        requestId: operation.requestId,
        sequence: operation.sequence,
        totalBytes: operation.totalBytes,
    });
    const result = await readP1Result(operation, messages);
    assert(
        result.targetProfile === "node" &&
            result.profileSchemaRevision === 1 &&
            result.profileRevision === 1 &&
            result.configSchemaRevision === 1 &&
            result.configKind === "none" &&
            result.compilerApi === "createIncrementalProgram" &&
            result.tsVersion === expectedVersion &&
            result.emitSkipped === false &&
            Array.isArray(result.diagnostics) &&
            result.diagnostics.length === 0 &&
            exactJson(
                result.outputs.map((output) => output.path).sort(),
                ["/outputs/main.js", "/outputs/main.js.map"],
            ),
        "Clean P1 successor did not preserve its successful rev1 result contract.",
    );
    assert(
        compilerCalls.program === 1 &&
            compilerCalls.emit === 1 &&
            compilerCalls.systemIo === 0 &&
            global.__r2P2WorkerTest.resolutionSessionCalls === 0 &&
            global.__r2P2WorkerTest.snapshotResolverCalls === 0,
        "Clean P1 successor did not activate exactly one Program/emit or used P2 resolution.",
    );
    assertWorkerIdle("clean P1 successor");
};

const main = async () => {
    const noBoundary = createP2Operation("no-boundary");
    await acceptStart(noBoundary);
    await sendAllAcceptedFiles(noBoundary);
    await finishP2TransportOnly(noBoundary, "P2 no-boundary operation");

    for (const testCase of catalogFixture.validCases) {
        const expectedCatalog = catalogOracle.deriveCatalog(
            testCase.dependencyRoots,
            testCase.files,
        );
        const operation = createP2Operation(
            `valid-${testCase.id}`,
            testCase.dependencyRoots,
            testCase.files,
            expectedCatalog.catalogFingerprint,
        );
        await acceptStart(operation);
        await sendAllAcceptedFiles(operation, expectedCatalog);
        await finishP2TransportOnly(
            operation,
            `valid catalog ${testCase.id}`,
            expectedCatalog,
        );
    }
    await runGeneratedLargeCatalog();

    await runStaleCatalogAttack();
    for (const testCase of catalogFixture.invalidTypeCases) {
        await runInvalidTypeCase(testCase);
    }

    await runRejectedStart("exact-key-extra-no-boundary", "noBoundary", (frame) => {
        frame.unexpected = true;
    });
    await runRejectedStart(
        "exact-key-missing-no-boundary",
        "noBoundary",
        (frame) => {
            delete frame.runtimeCapabilitiesFingerprint;
        },
    );
    await runRejectedStart("exact-key-extra-present", "present", (frame) => {
        frame.unexpected = true;
    });
    await runRejectedStart("exact-key-missing-present", "present", (frame) => {
        delete frame.packageMetadataInventoryFingerprint;
    });
    await runRejectedStart("entry-path-missing", "noBoundary", (frame) => {
        delete frame.entryPath;
    });
    await runRejectedStart("entry-path-wrong-type", "noBoundary", (frame) => {
        frame.entryPath = 7;
    });
    await runRejectedStart(
        "entry-path-non-executable",
        "noBoundary",
        (frame) => {
            frame.entryPath = "/sources/main.d.ts";
        },
    );
    await runRejectedStart("exact-key-downgraded-21", "present", (frame) => {
        for (const key of [
            "nodePackageFormatSchemaRevision",
            "nodePackageFormatCatalogRevision",
            "nodePackageFormatCatalogFingerprint",
            "nodeModuleOutputPolicySchemaRevision",
            "nodeModuleOutputPolicyRevision",
            "nodeModuleOutputPolicyFingerprint",
        ]) {
            delete frame[key];
        }
        assert(
            Object.keys(frame).length === 31,
            "Downgraded frame did not preserve S1 authority while dropping P2 policy keys.",
        );
    });

    for (const [id, key, value] of [
        ["revision-profile-schema", "profileSchemaRevision", 2],
        ["revision-profile-low", "profileRevision", 1],
        ["revision-profile-high", "profileRevision", 3],
        ["revision-config-schema", "configSchemaRevision", 2],
        ["revision-package-schema", "packageMetadataSchemaRevision", 2],
        ["revision-package-policy", "packageMetadataPolicyRevision", 2],
        ["revision-catalog-schema", "nodePackageFormatSchemaRevision", 2],
        ["revision-catalog", "nodePackageFormatCatalogRevision", 2],
        ["revision-node-output-schema", "nodeModuleOutputPolicySchemaRevision", 2],
        ["revision-node-output-policy", "nodeModuleOutputPolicyRevision", 2],
    ]) {
        await runRejectedStart(id, "present", (frame) => {
            frame[key] = value;
        });
    }
    for (const [id, key] of [
        ["fingerprint-runtime", "runtimeCapabilitiesFingerprint"],
        ["fingerprint-package-policy", "packageMetadataPolicyFingerprint"],
        ["fingerprint-inventory", "packageMetadataInventoryFingerprint"],
        ["fingerprint-node-output-policy", "nodeModuleOutputPolicyFingerprint"],
    ]) {
        await runRejectedStart(id, "present", (frame) => {
            frame[key] = "0".repeat(64);
        });
    }
    await runCatalogFingerprintRejection();

    await runEndAccountingRejection("accounting-file-count", "fileCount");
    await runRejectedStart("accounting-package-file-count", "present", (frame) => {
        frame.packageMetadataFileCount += 1;
    });
    await runRejectedStart(
        "accounting-package-byte-length",
        "present",
        (frame) => {
            frame.packageMetadataByteLength += 1;
        },
    );
    await runEndAccountingRejection("accounting-total-bytes", "totalBytes");
    await runWrongPathRejection();
    await runDescriptorDigestRejection();
    await runContentDigestRejection();
    await runEntryNotInInventoryRejection();
    await runSourceKindRejection(".mts");
    await runSourceKindRejection(".cts");
    await runActiveCollision();
    await runDelayedStartSameIdRace();
    await runDelayedFileEndSameIdRace();
    await runDelayedInputEndSameIdRace();
    await runDelayedStartDifferentIdRace();

    assert(
        exactJson(
            sorted(executedRejections),
            sorted(transport.requiredRejectionIds),
        ),
        "Harness did not execute the exact required rejection inventory: " +
            JSON.stringify({
                actual: sorted(executedRejections),
                expected: sorted(transport.requiredRejectionIds),
            }),
    );
    assertP2SemanticZero("after complete P2 rejection inventory");
    await runCleanP1Successor();

    console.log(
        "R2 Node package-format Worker transport verification passed " +
            "(24/37 exact keys, 96-root >128 KiB host/Worker envelope, " +
            "catalog/type/source/race negatives, zero P2 semantic activation, " +
            "clean P1 successor).",
    );
};

main().catch((error) => {
    console.error(error && error.stack ? error.stack : error);
    process.exitCode = 1;
});
