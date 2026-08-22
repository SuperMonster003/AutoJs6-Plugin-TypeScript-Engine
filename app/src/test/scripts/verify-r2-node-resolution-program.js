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
    process.argv.slice(2).length !== 6 ||
    !workerArgument ||
    !compilerArgument ||
    !targetPolicyArgument ||
    !metadataPolicyArgument ||
    !configPolicyArgument ||
    !expectedVersion
) {
    throw new Error(
        "Usage: node verify-r2-node-resolution-program.js " +
            "<worker.js> <typescript.js> <target-policy.json> " +
            "<metadata-policy.json> <config-policy.json> 6.0.3",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const targetPolicy = JSON.parse(
    fs.readFileSync(path.resolve(targetPolicyArgument), "utf8"),
);
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
const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;
const exactKeys = (value, expected) =>
    value !== null &&
    typeof value === "object" &&
    JSON.stringify(Object.keys(value).sort(compareCodeUnits)) ===
        JSON.stringify(expected.slice().sort(compareCodeUnits));
const sha256 = (value) =>
    cryptoModule.createHash("sha256").update(value).digest("hex");

assert(expectedVersion === "6.0.3", "Program gate requires TypeScript 6.0.3.");
assert(
    targetPolicy.schema === "autojs6.typescript.package-target-policy" &&
        targetPolicy.schemaRevision === 1 &&
        targetPolicy.policyRevision === 1 &&
        targetPolicy.fingerprint ===
            "e34f4825d8f75ba1b674849ff79c2be8f9c1b88fe940add86e6e466a139bbbaf",
    "Package-target fixture identity drifted.",
);
assert(
    metadataPolicy.schema === "autojs6.typescript.package-metadata-policy" &&
        metadataPolicy.schemaRevision === 1 &&
        metadataPolicy.policyRevision === 1 &&
        metadataPolicy.fingerprint ===
            "fab8fec29ed0f0a1de65594620e233c9a9bef2fa514dac16c3079d5570325e97",
    "Package-metadata fixture identity drifted.",
);
assert(
    configPolicy.schema === "autojs6.typescript.controlled-tsconfig-policy" &&
        configPolicy.revision === 1 &&
        configPolicy.fingerprint ===
            "849f882618d294d61683cc5930ecd4855f53435d7a673ceda08ad127504fa24d",
    "Controlled-tsconfig fixture identity drifted.",
);
for (const marker of [
    "resolveControlledNodeSnapshot",
    "resolveModuleNameLiterals",
    "nodeResolution",
    "retainedPackageTargets",
]) {
    assert(workerSource.includes(marker), `Worker Program integration is missing ${marker}.`);
}

if (!global.crypto) {
    Object.defineProperty(global, "crypto", {
        configurable: true,
        value: cryptoModule.webcrypto,
    });
}

let networkAccessCount = 0;
const rejectNetworkAccess = (name) => {
    networkAccessCount += 1;
    throw new Error(`Node-resolution Program gate reached network API ${name}.`);
};
Object.defineProperty(global, "fetch", {
    configurable: true,
    value: () => rejectNetworkAccess("fetch"),
});
Object.defineProperty(global, "XMLHttpRequest", {
    configurable: true,
    value: function XMLHttpRequest() {
        rejectNetworkAccess("XMLHttpRequest");
    },
});
Object.defineProperty(global, "WebSocket", {
    configurable: true,
    value: function WebSocket() {
        rejectNetworkAccess("WebSocket");
    },
});

const realTypeScript = require(compilerPath);
const typeScriptDiagnosticCodes = new Set(
    Object.values(realTypeScript.Diagnostics)
        .map((diagnostic) => diagnostic?.code)
        .filter(Number.isSafeInteger),
);
for (let code = 900001; code <= 900018; code += 1) {
    assert(
        !typeScriptDiagnosticCodes.has(code),
        `Internal node-resolution diagnostic code collides with TypeScript: ${code}.`,
    );
}
let compilerSystemAccessCount = 0;
let defaultResolverAccessCount = 0;
let importScriptsCount = 0;
let resolverHookCallCount = 0;
let typeReferenceResolverHookCallCount = 0;
const programConstructionCalls = [];
const packageTargetPaths = Object.freeze([
    "/packages/pkg/custom.d.ts",
    "/packages/pkg/fallback.d.ts",
    "/packages/pkg/features/nested/consumer.d.ts",
    "/packages/pkg/features/nested/value.d.ts",
    "/packages/pkg/require.d.ts",
    "/packages/pkg/types.d.ts",
]);
const poisonedCompilerSystem = new Proxy(realTypeScript.sys, {
    get() {
        compilerSystemAccessCount += 1;
        throw new Error("Node-resolution Program gate reached ts.sys.");
    },
});
const typeScriptFacade = Object.create(realTypeScript);
Object.defineProperty(typeScriptFacade, "sys", { value: poisonedCompilerSystem });
for (const resolverName of [
    "resolveModuleName",
    "nodeModuleNameResolver",
    "classicNameResolver",
]) {
    Object.defineProperty(typeScriptFacade, resolverName, {
        configurable: true,
        value: () => {
            defaultResolverAccessCount += 1;
            throw new Error(`Worker used forbidden TypeScript resolver ${resolverName}.`);
        },
    });
}
Object.defineProperty(typeScriptFacade, "createIncrementalProgram", {
    configurable: true,
    value: (configuration) => {
        const host = configuration.host;
        assert(
            host !== null &&
                typeof host === "object" &&
                typeof host.resolveModuleNameLiterals === "function" &&
                typeof host.resolveTypeReferenceDirectiveReferences === "function",
            "Revision-5 Program did not install both controlled resolver hooks.",
        );
        for (const targetPath of packageTargetPaths) {
            assert(
                host.fileExists(targetPath) === false &&
                    host.readFile(targetPath) === undefined,
                `Package target escaped into ordinary compiler reads: ${targetPath}.`,
            );
        }

        const call = {
            host,
            packageGetSourceFileReads: [],
            packageOrdinaryReads: [],
            resolutions: [],
            rootNames: configuration.rootNames.slice(),
            sourceFiles: [],
            typeReferenceResolutions: [],
        };
        const wrappedResolution = (...arguments_) => {
            resolverHookCallCount += 1;
            const moduleLiterals = arguments_[0];
            const containingFile = arguments_[1];
            const resolved = host.resolveModuleNameLiterals(...arguments_);
            assert(
                Array.isArray(resolved) && resolved.length === moduleLiterals.length,
                "Program resolver did not return one result per module literal.",
            );
            for (let index = 0; index < moduleLiterals.length; index += 1) {
                call.resolutions.push({
                    containingFile,
                    resolvedFileName:
                        resolved[index]?.resolvedModule?.resolvedFileName ?? null,
                    specifier: moduleLiterals[index].text,
                });
            }
            return resolved;
        };
        const wrappedTypeReferenceResolution = (...arguments_) => {
            typeReferenceResolverHookCallCount += 1;
            const typeDirectiveReferences = arguments_[0];
            const containingFile = arguments_[1];
            const resolved = host.resolveTypeReferenceDirectiveReferences(...arguments_);
            assert(
                Array.isArray(resolved) &&
                    resolved.length === typeDirectiveReferences.length,
                "Program type-reference resolver did not return one result per directive.",
            );
            for (let index = 0; index < typeDirectiveReferences.length; index += 1) {
                const reference = typeDirectiveReferences[index];
                call.typeReferenceResolutions.push({
                    containingFile,
                    resolvedFileName:
                        resolved[index]?.resolvedTypeReferenceDirective
                            ?.resolvedFileName ?? null,
                    specifier:
                        typeof reference === "string" ? reference : reference.fileName,
                });
            }
            return resolved;
        };
        const observedHost = new Proxy(host, {
            get(target, property, receiver) {
                if (property === "resolveModuleNameLiterals") {
                    return wrappedResolution;
                }
                if (property === "resolveTypeReferenceDirectiveReferences") {
                    return wrappedTypeReferenceResolution;
                }
                const value = Reflect.get(target, property, receiver);
                if (
                    property === "fileExists" ||
                    property === "readFile" ||
                    property === "getSourceFile"
                ) {
                    return (...arguments_) => {
                        const result = value.apply(target, arguments_);
                        const requestedPath = arguments_[0];
                        if (
                            typeof requestedPath === "string" &&
                            requestedPath.startsWith("/packages/")
                        ) {
                            if (property === "getSourceFile") {
                                call.packageGetSourceFileReads.push({
                                    path: requestedPath,
                                    resolved: result !== undefined,
                                });
                            } else {
                                call.packageOrdinaryReads.push({
                                    method: property,
                                    path: requestedPath,
                                    visible: result !== undefined && result !== false,
                                });
                            }
                        }
                        return result;
                    };
                }
                return value;
            },
        });
        const builder = realTypeScript.createIncrementalProgram({
            ...configuration,
            host: observedHost,
        });
        call.sourceFiles = builder
            .getProgram()
            .getSourceFiles()
            .map((sourceFile) => sourceFile.fileName)
            .sort(compareCodeUnits);
        programConstructionCalls.push(call);
        return builder;
    },
});
global.ts = typeScriptFacade;
global.importScripts = (specifier) => {
    importScriptsCount += 1;
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
        importScriptsCount === 1,
    "Worker bootstrap drifted from its pinned TypeScript authority.",
);
publishedMessages.length = 0;

const workerBinding = (expression) =>
    vm.runInThisContext(expression, {
        filename: `${workerPath}#node-resolution-program`,
    });
const observedPhaseEvents = [];
const dispatch = async (message) => {
    publishedMessages.length = 0;
    await self.onmessage({ data: message });
    const messages = publishedMessages.slice();
    for (const published of messages) {
        if (published.kind === "projectPhase") {
            observedPhaseEvents.push({ ...published });
        }
    }
    return messages;
};
const expectAck = (messages, operation, label) => {
    const acknowledgements = messages.filter(
        (message) => message.kind === "projectInputAck",
    );
    assert(
        acknowledgements.length === 1 &&
            acknowledgements[0].requestId === operation.requestId &&
            acknowledgements[0].sequence === operation.sequence &&
            !messages.some(
                (message) =>
                    message.kind === "projectError" ||
                    message.kind === "projectConfigError",
            ),
        `${label} did not publish its exact ACK: ${JSON.stringify(messages)}.`,
    );
    operation.sequence += 1;
};
const assertWorkerIdle = (label) => {
    assert(
        workerBinding("framedInput") === null &&
            workerBinding("framedCompilationRequestId") === null &&
            workerBinding("framedResult") === null,
        `${label} retained Worker operation state.`,
    );
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
const projectPackageJson = Object.freeze({
    imports: Object.freeze({
        "#local": "./declarations/local.d.ts",
    }),
});
const dependencyPackageJson = Object.freeze({
    exports: Object.freeze({
        ".": Object.freeze({
            types: "./types.js",
            default: "./fallback.d.ts",
        }),
        "./custom": Object.freeze({
            custom: "./custom.d.ts",
            default: "./fallback.d.ts",
        }),
        "./feature/*": "./features/*.d.ts",
        "./require": Object.freeze({
            require: "./require.d.ts",
            default: "./fallback.d.ts",
        }),
    }),
    name: "pkg",
});
const nestedPackageJson = Object.freeze({
    imports: Object.freeze({
        "#nested": "./value.d.ts",
    }),
});
const baseMetadataFiles = Object.freeze([
    Object.freeze({ path: "package.json", text: JSON.stringify(projectPackageJson) }),
    Object.freeze({
        path: "packages/pkg/features/nested/package.json",
        text: JSON.stringify(nestedPackageJson),
    }),
    Object.freeze({
        path: "packages/pkg/package.json",
        text: JSON.stringify(dependencyPackageJson),
    }),
]);
const baseTargetFiles = Object.freeze([
    Object.freeze({
        path: "packages/pkg/custom.d.ts",
        text: 'export interface CustomBranch { readonly kind: "custom"; }\n',
    }),
    Object.freeze({
        path: "packages/pkg/fallback.d.ts",
        text: ";".repeat(65_536),
    }),
    Object.freeze({
        path: "packages/pkg/features/nested/consumer.d.ts",
        text:
            'import type { NestedValue } from "#nested";\n' +
            "export interface PatternBranch { readonly nested: NestedValue; }\n",
    }),
    Object.freeze({
        path: "packages/pkg/features/nested/value.d.ts",
        text: "export interface NestedValue { readonly nested: true; }\n",
    }),
    Object.freeze({
        path: "packages/pkg/require.d.ts",
        text:
            'declare const requiredValue: { readonly mode: "require" };\n' +
            "export = requiredValue;\n",
    }),
    Object.freeze({
        path: "packages/pkg/types.d.ts",
        text: 'export interface TypesBranch { readonly kind: "types"; }\n',
    }),
]);
const successMainSource =
    '/// <reference types="pkg" />\n' +
    'import type { TypesBranch } from "pkg";\n' +
    'import type { CustomBranch } from "pkg/custom";\n' +
    'import type { PatternBranch } from "pkg/feature/nested/consumer";\n' +
    'import requiredValue = require("pkg/require");\n' +
    'import type { LocalValue } from "#local";\n' +
    "export type Combined = TypesBranch & CustomBranch & PatternBranch & LocalValue;\n" +
    "export const requiredMode = requiredValue.mode;\n";
const ordinaryFiles = (mainSource) => [
    { path: "/declarations/lib.d.ts", text: minimalLibrary },
    {
        path: "/declarations/local.d.ts",
        text: "export interface LocalValue { readonly local: true; }\n",
    },
    { path: "/sources/main.ts", text: mainSource },
];

let requestCounter = 0;
const createOperation = ({ mainSource, targetFiles = baseTargetFiles }) => {
    const configBytes = Buffer.from('{"files":["main.ts"]}', "utf8");
    const metadata = buildMetadata(["packages/pkg"], baseMetadataFiles);
    const targets = buildTargets(metadata.fingerprint, targetFiles);
    const ordinary = materializeFiles(ordinaryFiles(mainSource));
    const fileRecords = [
        ...metadata.files.map((file) =>
            inputArchiveRecord("package-metadata", `/${file.path}`, file.bytes),
        ),
        ...targets.files.map((file) =>
            inputArchiveRecord("package-target-v1", `/${file.path}`, file.bytes),
        ),
        ...ordinary.map((file) =>
            inputArchiveRecord(
                file.path.startsWith("/declarations/")
                    ? "declaration"
                    : "source",
                file.path,
                file.bytes,
            ),
        ),
    ];
    return {
        authority: projectInputAuthority({
            configRecord: inputArchiveRecord(
                "config",
                "tsconfig.json",
                configBytes,
            ),
            fileRecords,
        }),
        configBytes,
        metadata,
        ordinary,
        requestId: `node-resolution-program-c6-${++requestCounter}`,
        sequence: 0,
        targets,
    };
};
const startFrame = (operation) => ({
    ...operation.authority,
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
});
const sendStart = async (operation) =>
    expectAck(await dispatch(startFrame(operation)), operation, "revision-5 start");
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
const sendFile = async (operation, fileIndex, file) => {
    expectAck(
        await dispatch({
            byteLength: file.size,
            fileIndex,
            kind: "projectInputFileStart",
            path: file.path.startsWith("/") ? file.path : `/${file.path}`,
            requestId: operation.requestId,
            sequence: operation.sequence,
            sha256: file.sha256,
        }),
        operation,
        `${file.path} start`,
    );
    const chunkCount = Math.ceil(file.size / (64 * 1024));
    for (let chunkIndex = 0; chunkIndex < chunkCount; chunkIndex += 1) {
        const offset = chunkIndex * 64 * 1024;
        expectAck(
            await dispatch({
                chunkIndex,
                data: file.bytes
                    .subarray(offset, Math.min(offset + 64 * 1024, file.size))
                    .toString("base64"),
                fileIndex,
                kind: "projectInputFileChunk",
                requestId: operation.requestId,
                sequence: operation.sequence,
            }),
            operation,
            `${file.path} chunk ${chunkIndex}`,
        );
    }
    expectAck(
        await dispatch({
            chunkCount,
            fileIndex,
            kind: "projectInputFileEnd",
            requestId: operation.requestId,
            sequence: operation.sequence,
        }),
        operation,
        `${file.path} end`,
    );
};
const sendInput = async (operation) => {
    await sendStart(operation);
    await sendConfig(operation);
    let fileIndex = 0;
    for (const file of operation.metadata.files) {
        await sendFile(operation, fileIndex++, file);
    }
    for (const file of operation.targets.files) {
        await sendFile(operation, fileIndex++, file);
    }
    for (const file of operation.ordinary) {
        await sendFile(operation, fileIndex++, file);
    }
};
const sendInputEnd = (operation) =>
    dispatch({
        fileCount:
            operation.metadata.files.length +
            operation.targets.files.length +
            operation.ordinary.length,
        kind: "projectInputEnd",
        requestId: operation.requestId,
        sequence: operation.sequence,
        totalBytes:
            operation.configBytes.length +
            operation.metadata.totalBytes +
            operation.targets.totalBytes +
            operation.ordinary.reduce((total, file) => total + file.size, 0),
    });
const readFramedResult = async (operation, inputEndMessages) => {
    expectAck(inputEndMessages, operation, "project input end");
    const starts = inputEndMessages.filter(
        (message) =>
            message.kind === "projectResultStart" &&
            message.requestId === operation.requestId,
    );
    const start = starts[0];
    assert(
        starts.length === 1 &&
            exactKeys(start, [
                "byteLength",
                "chunkCount",
                "kind",
                "requestId",
                "sha256",
            ]) &&
            Number.isSafeInteger(start.byteLength) &&
            start.byteLength > 0 &&
            start.chunkCount === Math.ceil(start.byteLength / (64 * 1024)) &&
            start.chunkCount > 0 &&
            !inputEndMessages.some(
                (message) =>
                    message.kind === "projectResultChunk" ||
                    message.kind === "projectResultEnd" ||
                    message.kind === "projectError",
            ),
        "Program compilation did not begin one exact, non-eager framed result.",
    );
    const chunks = [];
    const chunkIndexes = new Set();
    for (let index = 0; index < start.chunkCount; index += 1) {
        const messages = await dispatch({
            kind: "projectResultAck",
            requestId: operation.requestId,
            sequence: index - 1,
        });
        const chunk = messages[0];
        assert(
            messages.length === 1 &&
                exactKeys(chunk, ["data", "index", "kind", "requestId"]) &&
                chunk.kind === "projectResultChunk" &&
                chunk.requestId === operation.requestId &&
                chunk.index === index &&
                !chunkIndexes.has(chunk.index) &&
                !observedPhaseEvents.some(
                    (event) =>
                        event.requestId === operation.requestId &&
                        event.phase === "emit" &&
                        event.state === "completed",
                ),
            `Result chunk ${index} was not exact stop-and-wait framing.`,
        );
        chunkIndexes.add(chunk.index);
        const chunkBytes = Buffer.from(chunk.data, "base64");
        assert(
            chunkBytes.length ===
                Math.min(64 * 1024, start.byteLength - index * 64 * 1024),
            `Result chunk ${index} has an invalid raw byte length.`,
        );
        chunks.push(chunkBytes);
    }
    const terminalMessages = await dispatch({
        kind: "projectResultAck",
        requestId: operation.requestId,
        sequence: start.chunkCount - 1,
    });
    const end = terminalMessages[1];
    assert(
        terminalMessages.length === 2 &&
            exactKeys(terminalMessages[0], [
                "kind",
                "phase",
                "requestId",
                "state",
            ]) &&
            terminalMessages[0].kind === "projectPhase" &&
            terminalMessages[0].phase === "emit" &&
            terminalMessages[0].requestId === operation.requestId &&
            terminalMessages[0].state === "completed" &&
            exactKeys(end, [
                "byteLength",
                "chunkCount",
                "kind",
                "requestId",
                "sha256",
            ]) &&
            end.kind === "projectResultEnd" &&
            end.requestId === operation.requestId &&
            end.sha256 === start.sha256 &&
            end.byteLength === start.byteLength &&
            end.chunkCount === start.chunkCount,
        "Program compilation did not finish with emit completion then exact result end.",
    );
    const bytes = Buffer.concat(chunks);
    assert(
        bytes.length === start.byteLength && sha256(bytes) === start.sha256,
        "Framed Program result does not match its declared identity.",
    );
    assertWorkerIdle("framed result completion");
    assert(
        (await dispatch({
            kind: "projectResultAck",
            requestId: operation.requestId,
            sequence: start.chunkCount - 1,
        })).length === 0,
        "A late terminal result ACK was not harmless.",
    );
    return JSON.parse(bytes.toString("utf8"));
};

const compileOperation = async (operation) => {
    const programCallStart = programConstructionCalls.length;
    await sendInput(operation);
    const inputState = workerBinding("framedInput");
    const retainedRecords = inputState.packageTargetFiles.slice();
    const retainedTextHolders = inputState.packageTargetTextHolders.slice();
    const packageTargetCatalog = inputState.packageTargets;
    const result = await readFramedResult(operation, await sendInputEnd(operation));
    assert(
        retainedRecords.every((record) => record.text === "") &&
            retainedTextHolders.every((holder) => holder.text === "") &&
            packageTargetCatalog.filePaths.size === 0,
        "Program terminal retained target text or target catalog paths.",
    );
    const calls = programConstructionCalls.slice(programCallStart);
    assert(calls.length === 1, "Program operation did not construct exactly one Program.");
    const call = calls[0];
    const phaseEvents = observedPhaseEvents
        .filter((event) => event.requestId === operation.requestId)
        .map((event) => `${event.phase}:${event.state}`);
    assert(
        JSON.stringify(phaseEvents) ===
            JSON.stringify([
                "ingress:started",
                "ingress:completed",
                "parse:started",
                "parse:completed",
                "semantic:started",
                "semantic:completed",
                "emit:started",
                "emit:completed",
            ]),
        `Program phase order drifted: ${JSON.stringify(phaseEvents)}.`,
    );
    for (const targetPath of packageTargetPaths) {
        assert(
            call.host.fileExists(targetPath) === false &&
                call.host.readFile(targetPath) === undefined &&
                call.host.getSourceFile(targetPath, realTypeScript.ScriptTarget.ES2018) ===
                    undefined,
            `Released Program host retained a target grant: ${targetPath}.`,
        );
    }
    return { call, result };
};

const failureCatalog = Object.freeze([
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
const expectedResolutionDiagnosticCode = (policyCode) => {
    const index = failureCatalog.indexOf(policyCode);
    assert(index >= 0, `Unknown expected resolution policy code: ${policyCode}.`);
    return 900_001 + index;
};
const resolutionMap = (call) =>
    new Map(
        call.resolutions.map((resolution) => [
            `${resolution.containingFile}|${resolution.specifier}`,
            resolution.resolvedFileName,
        ]),
    );
const expectedResultKeys = Object.freeze([
    "compilerApi",
    "configKind",
    "configSchemaRevision",
    "diagnostics",
    "effectiveOptionsFingerprint",
    "effectiveOptionsRevision",
    "emitSkipped",
    "options",
    "outputs",
    "profileRevision",
    "profileSchemaRevision",
    "rootNames",
    "targetProfile",
    "tsVersion",
]);
const assertExactResultContract = (result, label) => {
    assert(
        exactKeys(result, expectedResultKeys) &&
            !Object.prototype.hasOwnProperty.call(result, "nodeResolution"),
        `${label} top-level result shape drifted: ${JSON.stringify(result)}.`,
    );
};
const assertSuccessfulProgram = ({ call, result }, label) => {
    assertExactResultContract(result, label);
    assert(
        result.emitSkipped === false &&
            Array.isArray(result.diagnostics) &&
            result.diagnostics.length === 0 &&
            JSON.stringify(result.rootNames) === JSON.stringify(["/sources/main.ts"]) &&
            Array.isArray(result.outputs) &&
            result.outputs.length === 2 &&
            JSON.stringify(call.rootNames) === JSON.stringify(["/sources/main.ts"]) &&
            call.resolutions.length === 6 &&
            call.resolutions.filter((resolution) =>
                resolution.resolvedFileName?.startsWith("/packages/"),
            ).length === 5 &&
            call.typeReferenceResolutions.length === 1,
        `${label} result/root/output authority drifted.`,
    );
    assert(
        !call.rootNames.some((rootName) => rootName.startsWith("/packages/")) &&
            !result.rootNames.some((rootName) => rootName.startsWith("/packages/")) &&
            !result.outputs.some(
                (output) =>
                    output.path.includes("/packages/") ||
                    output.sourcePaths.some((sourcePath) =>
                        sourcePath.startsWith("/packages/"),
                    ),
            ),
        `${label} promoted a package target into roots or outputs.`,
    );

    const resolved = resolutionMap(call);
    const expected = new Map([
        ["/sources/main.ts|pkg", "/packages/pkg/types.d.ts"],
        ["/sources/main.ts|pkg/custom", "/packages/pkg/custom.d.ts"],
        [
            "/sources/main.ts|pkg/feature/nested/consumer",
            "/packages/pkg/features/nested/consumer.d.ts",
        ],
        ["/sources/main.ts|pkg/require", "/packages/pkg/require.d.ts"],
        ["/sources/main.ts|#local", "/declarations/local.d.ts"],
        [
            "/packages/pkg/features/nested/consumer.d.ts|#nested",
            "/packages/pkg/features/nested/value.d.ts",
        ],
    ]);
    for (const [identity, expectedPath] of expected) {
        assert(
            resolved.get(identity) === expectedPath,
            `${label} resolution ${identity} did not select ${expectedPath}: ` +
                JSON.stringify(call.resolutions),
        );
    }
    assert(
        call.typeReferenceResolutions[0].containingFile === "/sources/main.ts" &&
            call.typeReferenceResolutions[0].specifier === "pkg" &&
            call.typeReferenceResolutions[0].resolvedFileName ===
                "/packages/pkg/types.d.ts",
        `${label} did not resolve the controlled type-reference directive.`,
    );
    const loadedPackageTargets = call.sourceFiles.filter((sourceFile) =>
        sourceFile.startsWith("/packages/"),
    );
    assert(
        JSON.stringify(loadedPackageTargets) ===
            JSON.stringify([
                "/packages/pkg/custom.d.ts",
                "/packages/pkg/features/nested/consumer.d.ts",
                "/packages/pkg/features/nested/value.d.ts",
                "/packages/pkg/require.d.ts",
                "/packages/pkg/types.d.ts",
            ]) &&
            !call.sourceFiles.includes("/packages/pkg/fallback.d.ts") &&
            call.packageGetSourceFileReads.filter((read) => read.resolved).length >= 5 &&
            call.packageOrdinaryReads.every((read) => !read.visible),
        `${label} did not materialize exactly the resolver-granted targets.`,
    );
};
const assertResolutionFailure = (
    compiled,
    expectedPolicyCode,
    expectedSpecifier,
    label,
) => {
    const { call, result } = compiled;
    assertExactResultContract(result, label);
    const resolutionDiagnostics = result.diagnostics.filter(
        (diagnostic) => diagnostic.phase === "resolution",
    );
    const resolutionDiagnostic = resolutionDiagnostics[0];
    assert(
        result.emitSkipped === true &&
            result.outputs.length === 0 &&
            result.diagnostics.length === 1 &&
            resolutionDiagnostics.length === 1 &&
            exactKeys(resolutionDiagnostic, [
                "category",
                "code",
                "column",
                "length",
                "line",
                "message",
                "nodeResolution",
                "phase",
                "sourcePath",
                "start",
            ]) &&
            exactKeys(resolutionDiagnostic.nodeResolution, ["policyCode"]) &&
            resolutionDiagnostic.nodeResolution.policyCode === expectedPolicyCode &&
            resolutionDiagnostic.code ===
                expectedResolutionDiagnosticCode(expectedPolicyCode) &&
            resolutionDiagnostic.category === 1 &&
            resolutionDiagnostic.start === null &&
            resolutionDiagnostic.length === null &&
            resolutionDiagnostic.line === null &&
            resolutionDiagnostic.column === null &&
            resolutionDiagnostic.sourcePath === "/sources/main.ts" &&
            resolutionDiagnostic.message ===
                `${expectedPolicyCode}: Controlled snapshot resolution rejected ` +
                    `${JSON.stringify(expectedSpecifier)}.` &&
            JSON.stringify(result.rootNames) === JSON.stringify(["/sources/main.ts"]) &&
            JSON.stringify(call.rootNames) === JSON.stringify(["/sources/main.ts"]),
        `${label} did not produce its exact stable resolution diagnostic: ` +
            JSON.stringify(result),
    );
    assert(
        !result.outputs.some((output) =>
            output.sourcePaths.some((sourcePath) => sourcePath.startsWith("/packages/")),
        ) &&
            call.packageOrdinaryReads.every((read) => !read.visible) &&
            call.resolutions.filter(
                (resolution) => resolution.specifier === expectedSpecifier,
            ).length === 1 &&
            call.resolutions.some(
                (resolution) =>
                    resolution.containingFile === "/sources/main.ts" &&
                    resolution.specifier === expectedSpecifier &&
                    resolution.resolvedFileName === null,
            ) &&
            !call.sourceFiles.some((sourceFile) =>
                sourceFile.startsWith("/packages/"),
            ) &&
            !call.packageGetSourceFileReads.some((read) => read.resolved),
        `${label} leaked a package target through failure output or ordinary reads.`,
    );
};

const assertOverTokenTargetRejected = async () => {
    const operation = createOperation({
        mainSource: successMainSource,
        targetFiles: [
            {
                path: "packages/pkg/oversized.d.ts",
                text: ";".repeat(65_537),
            },
        ],
    });
    await sendStart(operation);
    await sendConfig(operation);
    let fileIndex = 0;
    for (const file of operation.metadata.files) {
        await sendFile(operation, fileIndex++, file);
    }
    const file = operation.targets.files[0];
    expectAck(
        await dispatch({
            byteLength: file.size,
            fileIndex,
            kind: "projectInputFileStart",
            path: `/${file.path}`,
            requestId: operation.requestId,
            sequence: operation.sequence,
            sha256: file.sha256,
        }),
        operation,
        "over-token target start",
    );
    const chunkCount = Math.ceil(file.size / (64 * 1024));
    for (let chunkIndex = 0; chunkIndex < chunkCount; chunkIndex += 1) {
        const offset = chunkIndex * 64 * 1024;
        expectAck(
            await dispatch({
                chunkIndex,
                data: file.bytes
                    .subarray(offset, Math.min(offset + 64 * 1024, file.size))
                    .toString("base64"),
                fileIndex,
                kind: "projectInputFileChunk",
                requestId: operation.requestId,
                sequence: operation.sequence,
            }),
            operation,
            `over-token target chunk ${chunkIndex}`,
        );
    }
    const rawBytes = workerBinding("framedInput.currentFile.bytes");
    const messages = await dispatch({
        chunkCount,
        fileIndex,
        kind: "projectInputFileEnd",
        requestId: operation.requestId,
        sequence: operation.sequence,
    });
    assert(
        messages.length === 1 &&
            messages[0].kind === "projectError" &&
            messages[0].requestId === operation.requestId &&
            messages[0].message.includes("exceeds the token limit") &&
            rawBytes.every((byte) => byte === 0),
        `65,537-token target did not fail closed and zero bytes: ${JSON.stringify(messages)}.`,
    );
    assertWorkerIdle("over-token target rejection");
};

(async () => {
    await assertOverTokenTargetRejected();
    const baselineOperation = createOperation({ mainSource: successMainSource });
    const baseline = await compileOperation(baselineOperation);
    assertSuccessfulProgram(baseline, "baseline Program");

    const exportsFailureOperation = createOperation({
        mainSource: 'import type { Missing } from "pkg/missing";\nexport type Value = Missing;\n',
    });
    assertResolutionFailure(
        await compileOperation(exportsFailureOperation),
        "NODE_RESOLUTION_EXPORTS_NO_MATCH",
        "pkg/missing",
        "exports no-match Program",
    );

    const targetFailureOperation = createOperation({
        mainSource:
            'import type { CustomBranch } from "pkg/custom";\n' +
            "export type Value = CustomBranch;\n",
        targetFiles: baseTargetFiles.filter(
            (file) => file.path !== "packages/pkg/custom.d.ts",
        ),
    });
    assertResolutionFailure(
        await compileOperation(targetFailureOperation),
        "NODE_RESOLUTION_TARGET_NOT_IN_SNAPSHOT",
        "pkg/custom",
        "missing target Program",
    );

    const successorOperation = createOperation({ mainSource: successMainSource });
    const successor = await compileOperation(successorOperation);
    assertSuccessfulProgram(successor, "clean successor Program");
    assert(
        !successor.call.resolutions.some(
            (resolution) =>
                resolution.specifier === "pkg/missing" ||
                resolution.resolvedFileName === null,
        ),
        "Clean successor inherited a failed resolution.",
    );

    assert(
        resolverHookCallCount >= 4 &&
            typeReferenceResolverHookCallCount >= 2 &&
            compilerSystemAccessCount === 0 &&
            defaultResolverAccessCount === 0 &&
            networkAccessCount === 0 &&
            importScriptsCount === 1,
        "Program resolution used a default resolver, ts.sys, network, or extra importScripts.",
    );
    assertWorkerIdle("final clean successor");
    process.stdout.write(
        "R2-M2-C6 Worker/TypeScript Program resolution verified: " +
            "6 module + 1 type-reference resolutions (5 package targets), " +
            "2 stable resolution diagnostics, roots polluted=0, " +
            "token boundary=65536/65537, default resolver/ts.sys/network=0, " +
            "clean successor=1\n",
    );
})().catch((error) => {
    process.stderr.write(`${error.stack || error}\n`);
    process.exitCode = 1;
});
