"use strict";

const cryptoModule = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const [
    workerArgument,
    compilerArgument,
    fixtureArgument,
    p1FixtureArgument,
    legacyTransportArgument,
    metadataPolicyArgument,
    outputPolicyArgument,
    targetPolicyArgument,
    expectedVersion,
] = process.argv.slice(2);
if (
    process.argv.slice(2).length !== 9 ||
    !workerArgument ||
    !compilerArgument ||
    !fixtureArgument ||
    !p1FixtureArgument ||
    !legacyTransportArgument ||
    !metadataPolicyArgument ||
    !outputPolicyArgument ||
    !targetPolicyArgument ||
    !expectedVersion
) {
    throw new Error(
        "Usage: node verify-r2-nodenext-revision2-program.js " +
            "<worker.js> <typescript.js> <p2-fixture.json> <p1-fixture.json> " +
            "<legacy-transport.json> <metadata-policy.json> " +
            "<module-output-policy.json> <target-policy-v2.json> 6.0.3",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const fixture = JSON.parse(fs.readFileSync(path.resolve(fixtureArgument), "utf8"));
const p1Fixture = JSON.parse(
    fs.readFileSync(path.resolve(p1FixtureArgument), "utf8"),
);
const legacyTransport = JSON.parse(
    fs.readFileSync(path.resolve(legacyTransportArgument), "utf8"),
);
const metadataPolicy = JSON.parse(
    fs.readFileSync(path.resolve(metadataPolicyArgument), "utf8"),
);
const outputPolicy = JSON.parse(
    fs.readFileSync(path.resolve(outputPolicyArgument), "utf8"),
);
const targetPolicy = JSON.parse(
    fs.readFileSync(path.resolve(targetPolicyArgument), "utf8"),
);
const catalogPolicy = require("./r2-node-package-format-catalog-policy.js");
const {
    projectInputAuthority,
    record: inputArchiveRecord,
} = require("./r2-input-archive-authority.js");
const workerSource = fs.readFileSync(workerPath, "utf8");

const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;
const asciiCaseFold = (value) =>
    value.replace(/[A-Z]/g, (character) =>
        String.fromCharCode(character.charCodeAt(0) + 0x20),
    );
const sorted = (values) => values.slice().sort(compareCodeUnits);
const exactJson = (actual, expected) =>
    JSON.stringify(actual) === JSON.stringify(expected);
const exactKeys = (value, expected) =>
    value !== null &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    exactJson(sorted(Object.keys(value)), sorted(expected));
const sha256 = (value) =>
    cryptoModule.createHash("sha256").update(value).digest("hex");
const occurrences = (source, marker) => source.split(marker).length - 1;

const resolverIdentityKeys = Object.freeze([
    "resolverPolicyRevision",
    "snapshotTargetInventoryFingerprint",
    "targetUniverseFingerprint",
]);
const moduleOutputIdentityKeys = Object.freeze([
    "nodeModuleOutputPolicyFingerprint",
    "nodeModuleOutputPolicyRevision",
    "nodeModuleOutputPolicySchemaRevision",
]);
const nodeTargetIdentityKeys = Object.freeze([
    "nodePackageTargetByteLength",
    "nodePackageTargetCanonical",
    "nodePackageTargetFileCount",
    "nodePackageTargetFingerprint",
    "nodePackageTargetPolicyFingerprint",
    "nodePackageTargetPolicyRevision",
    "nodePackageTargetSchemaRevision",
]);
const configNoneResultKeys = Object.freeze([
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
]);
const outputKeys = Object.freeze(["path", "sha256", "sourcePaths", "text"]);
const resolutionDiagnosticKeys = Object.freeze([
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
]);

assert(expectedVersion === "6.0.3", "P2-6 requires TypeScript 6.0.3.");
assert(
    fixture.schema === "autojs6.typescript.nodenext-program-integration" &&
        fixture.revision === 1 &&
        fixture.typescriptVersion === expectedVersion &&
        fixture.scope ===
            "R2-M2-P2-6 production Worker Program only; native result/private output remain closed" &&
        Array.isArray(fixture.scenarios) &&
        fixture.scenarios.length === 5,
    "P2-6 Program fixture authority is malformed.",
);
assert(
    legacyTransport.schema ===
        "autojs6.typescript.node-package-format-worker-transport" &&
        legacyTransport.revision === 1 &&
        legacyTransport.authority.terminalPolicyCode ===
            "P2_NODE_PROGRAM_NOT_ACTIVATED",
    "Legacy P2-3B transport fixture identity drifted.",
);
assert(
    metadataPolicy.schema === "autojs6.typescript.package-metadata-policy" &&
        metadataPolicy.schemaRevision === 1 &&
        metadataPolicy.policyRevision === 1 &&
        metadataPolicy.fingerprint ===
            "fab8fec29ed0f0a1de65594620e233c9a9bef2fa514dac16c3079d5570325e97",
    "Package-metadata policy identity drifted.",
);
assert(
    outputPolicy.schema === "autojs6.typescript.node-module-output-policy" &&
        outputPolicy.schemaRevision === 1 &&
        outputPolicy.policyRevision === 1 &&
        outputPolicy.fingerprint === fixture.authority.nodeModuleOutputPolicyFingerprint &&
        outputPolicy.resolverPolicyRevision === fixture.authority.resolverPolicyRevision,
    "Node module-output policy identity drifted.",
);
assert(
    targetPolicy.schema === "autojs6.typescript.node-package-target-policy" &&
        targetPolicy.schemaRevision === fixture.authority.nodePackageTargetSchemaRevision &&
        targetPolicy.policyRevision === fixture.authority.nodePackageTargetPolicyRevision &&
        targetPolicy.fingerprint === fixture.authority.nodePackageTargetPolicyFingerprint &&
        exactJson(targetPolicy.allowedExecutableSuffixes, [".ts", ".mts", ".cts"]) &&
        exactJson(targetPolicy.allowedDeclarationSuffixes, [
            ".d.ts",
            ".d.mts",
            ".d.cts",
        ]),
    "Node package-target v2 policy identity drifted.",
);
assert(
    p1Fixture.schemaVersion === 1 &&
        p1Fixture.authority.targetProfile === "node" &&
        p1Fixture.authority.profileRevision === 1 &&
        p1Fixture.authority.configKind === "none",
    "P1 NodeNext regression fixture identity drifted.",
);

const legacyNoBoundary = legacyTransport.exactStartKeys.noBoundary;
const legacyPresent = legacyTransport.exactStartKeys.present;
const programNoMetadata = fixture.exactProgramStartKeys.noMetadata;
const programMetadata = fixture.exactProgramStartKeys.metadata;
const programMetadataAndTargets = fixture.exactProgramStartKeys.metadataAndTargets;
assert(
    legacyNoBoundary.length === 24 &&
        legacyPresent.length === 37 &&
        programNoMetadata.length === 30 &&
        programMetadata.length === 40 &&
        programMetadataAndTargets.length === 47 &&
        exactJson(
            sorted(programNoMetadata.filter((key) =>
                !moduleOutputIdentityKeys.includes(key) &&
                !resolverIdentityKeys.includes(key),
            )),
            sorted(legacyNoBoundary),
        ) &&
        exactJson(
            sorted(programMetadata.filter((key) => !resolverIdentityKeys.includes(key))),
            sorted(legacyPresent),
        ) &&
        exactJson(
            sorted(programMetadataAndTargets.filter(
                (key) => !nodeTargetIdentityKeys.includes(key),
            )),
            sorted(programMetadata),
        ) &&
        resolverIdentityKeys.every(
            (key) =>
                !legacyNoBoundary.includes(key) && !legacyPresent.includes(key),
        ) &&
        nodeTargetIdentityKeys.every((key) => !programMetadata.includes(key)),
    "30/40/47 Program shapes do not extend the schema-v1 24/37 transports exactly.",
);

for (const marker of [
    "parseNodeRevisionTwoPackageTargetCanonical",
    "deriveNodeRevisionTwoResolutionAuthority",
    "resolveNodeRevisionTwoSnapshot",
    "createNodeRevisionTwoTrustedDeclarationModuleIndex",
    "createNodeRevisionTwoResolutionSession",
    "nodeRevisionTwoImpliedModuleFormat",
    "createNodeRevisionTwoSourceFile",
    "resolveModuleNameLiterals",
    "resolveTypeReferenceDirectiveReferences",
    "createIncrementalProgram",
    "expectedGeneratedModulePath",
]) {
    assert(workerSource.includes(marker), `P2-6 Worker integration is missing ${marker}.`);
}
assert(
    !workerSource.includes("P2_NODE_RESULT_NOT_ACTIVATED") &&
        occurrences(workerSource, "const expectedGeneratedModulePath =") === 1 &&
        occurrences(
            workerSource,
            "expectedGeneratedModulePath(\n                onlySource,\n                isNodeRevisionTwoProject,\n            )",
        ) === 1 &&
        occurrences(
            workerSource,
            'programNotActivated: "P2_NODE_PROGRAM_NOT_ACTIVATED"',
        ) === 1,
    "P2-7 must open only the revision-2 result fence and retain one profile-aware mapping helper plus the legacy Program fence.",
);

if (!global.crypto) {
    Object.defineProperty(global, "crypto", {
        configurable: true,
        value: cryptoModule.webcrypto,
    });
}

let networkAccessCount = 0;
const rejectNetworkAccess = (name) => {
    networkAccessCount += 1;
    throw new Error(`P2-6 Program gate reached network API ${name}.`);
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
assert(realTypeScript.version === expectedVersion, "TypeScript runtime version drifted.");
assert(
    realTypeScript.ModuleResolutionKind.NodeNext === 99 &&
        realTypeScript.ModuleKind.NodeNext === 199 &&
        realTypeScript.ModuleKind.ESNext === 99 &&
        realTypeScript.ModuleKind.CommonJS === 1,
    "TypeScript NodeNext enum authority drifted.",
);
const typeScriptDiagnosticCodes = new Set(
    Object.values(realTypeScript.Diagnostics)
        .map((diagnostic) => diagnostic?.code)
        .filter(Number.isSafeInteger),
);
for (let code = 900001; code <= 900018; code += 1) {
    assert(
        !typeScriptDiagnosticCodes.has(code),
        `Internal resolution diagnostic code collides with TypeScript: ${code}.`,
    );
}

let compilerSystemAccessCount = 0;
let defaultResolverAccessCount = 0;
let importScriptsCount = 0;
let resolverHookCallCount = 0;
let typeReferenceResolverHookCallCount = 0;
let activePackageTargetPaths = Object.freeze([]);
const programCalls = [];
const poisonedCompilerSystem = new Proxy(realTypeScript.sys, {
    get() {
        compilerSystemAccessCount += 1;
        throw new Error("P2-6 Program gate reached ts.sys.");
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

const refreshProgramCall = (call) => {
    if (call.program === null) return;
    call.sourceFiles = call.program
        .getSourceFiles()
        .map((sourceFile) => ({
            fileName: sourceFile.fileName,
            impliedNodeFormat:
                typeof sourceFile.impliedNodeFormat === "number"
                    ? sourceFile.impliedNodeFormat
                    : null,
        }))
        .sort((left, right) => compareCodeUnits(left.fileName, right.fileName));
};

Object.defineProperty(typeScriptFacade, "createIncrementalProgram", {
    configurable: true,
    value: (configuration) => {
        const host = configuration.host;
        const hasModuleHook = typeof host?.resolveModuleNameLiterals === "function";
        const hasTypeReferenceHook =
            typeof host?.resolveTypeReferenceDirectiveReferences === "function";
        assert(
            host !== null &&
                typeof host === "object" &&
                hasModuleHook === hasTypeReferenceHook,
            "Program host installed a partial resolution-hook pair.",
        );
        for (const targetPath of activePackageTargetPaths) {
            assert(
                host.fileExists(targetPath) === false &&
                    host.readFile(targetPath) === undefined,
                `Package target escaped into ordinary host reads: ${targetPath}.`,
            );
        }

        const call = {
            emitCalls: 0,
            emitSkipped: null,
            hasResolutionHooks: hasModuleHook,
            options: { ...configuration.options },
            outputs: [],
            packageGetSourceFileReads: [],
            packageOrdinaryReads: [],
            program: null,
            resolutions: [],
            rootNames: configuration.rootNames.slice(),
            sourceFiles: [],
            typeReferenceResolutions: [],
        };
        const wrappedResolution = (...arguments_) => {
            resolverHookCallCount += 1;
            const literals = arguments_[0];
            const containingFile = arguments_[1];
            const resolved = host.resolveModuleNameLiterals(...arguments_);
            assert(
                Array.isArray(resolved) && resolved.length === literals.length,
                "P2-6 module hook did not return one result per literal.",
            );
            for (let index = 0; index < literals.length; index += 1) {
                const module = resolved[index]?.resolvedModule;
                call.resolutions.push({
                    containingFile,
                    extension: module?.extension ?? null,
                    resolvedFileName: module?.resolvedFileName ?? null,
                    specifier: literals[index].text,
                });
            }
            return resolved;
        };
        const wrappedTypeReferenceResolution = (...arguments_) => {
            typeReferenceResolverHookCallCount += 1;
            const references = arguments_[0];
            const containingFile = arguments_[1];
            const resolved = host.resolveTypeReferenceDirectiveReferences(...arguments_);
            assert(
                Array.isArray(resolved) && resolved.length === references.length,
                "P2-6 type-reference hook did not return one result per directive.",
            );
            for (let index = 0; index < references.length; index += 1) {
                const directive = resolved[index]?.resolvedTypeReferenceDirective;
                const reference = references[index];
                const resolvedFileName = directive?.resolvedFileName ?? null;
                const inferredDeclarationExtension =
                    typeof resolvedFileName !== "string"
                        ? null
                        : [".d.mts", ".d.cts", ".d.ts"].find((suffix) =>
                              resolvedFileName.endsWith(suffix),
                          ) ?? null;
                call.typeReferenceResolutions.push({
                    containingFile,
                    // ResolvedTypeReferenceDirective has no TypeScript `extension`
                    // member. Preserve declaration-first evidence by deriving the
                    // exact accepted suffix from its resolved file name.
                    extension: inferredDeclarationExtension,
                    resolvedFileName,
                    specifier:
                        typeof reference === "string" ? reference : reference.fileName,
                });
            }
            return resolved;
        };
        const observedHost = new Proxy(host, {
            get(target, property, receiver) {
                if (property === "resolveModuleNameLiterals" && hasModuleHook) {
                    return wrappedResolution;
                }
                if (
                    property === "resolveTypeReferenceDirectiveReferences" &&
                    hasTypeReferenceHook
                ) {
                    return wrappedTypeReferenceResolution;
                }
                const value = Reflect.get(target, property, receiver);
                if (
                    typeof value === "function" &&
                    (property === "fileExists" ||
                        property === "readFile" ||
                        property === "getSourceFile")
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
                if (property === "writeFile" && typeof value === "function") {
                    return (...arguments_) => {
                        const result = value.apply(target, arguments_);
                        call.outputs.push({
                            path: arguments_[0],
                            sourcePaths: (arguments_[4] || [])
                                .map((sourceFile) => sourceFile.fileName)
                                .filter((sourcePath) =>
                                    sourcePath.startsWith("/sources/"),
                                )
                                .sort(compareCodeUnits),
                            text: arguments_[1],
                        });
                        return result;
                    };
                }
                return typeof value === "function" ? value.bind(target) : value;
            },
        });
        const realBuilder = realTypeScript.createIncrementalProgram({
            ...configuration,
            host: observedHost,
        });
        const observedBuilder = new Proxy(realBuilder, {
            get(target, property, receiver) {
                if (property === "getProgram") {
                    return () => {
                        call.program = target.getProgram();
                        refreshProgramCall(call);
                        return call.program;
                    };
                }
                if (property === "emit") {
                    return (...arguments_) => {
                        call.emitCalls += 1;
                        const result = target.emit(...arguments_);
                        call.emitSkipped = result.emitSkipped;
                        refreshProgramCall(call);
                        return result;
                    };
                }
                const value = Reflect.get(target, property, receiver);
                return typeof value === "function" ? value.bind(target) : value;
            },
        });
        call.program = realBuilder.getProgram();
        refreshProgramCall(call);
        programCalls.push(call);
        return observedBuilder;
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
        filename: `${workerPath}#p2-6-program`,
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

const declarationSuffixes = [".d.ts", ".d.mts", ".d.cts"];
const executableSuffixes = [".ts", ".mts", ".cts"];
const isDeclarationPath = (filePath) =>
    declarationSuffixes.some((suffix) => filePath.endsWith(suffix));
const isExecutablePath = (filePath) =>
    !isDeclarationPath(filePath) &&
    executableSuffixes.some((suffix) => filePath.endsWith(suffix));
const materializeFiles = (files) =>
    files
        .map((file) => {
            const bytes = Buffer.from(file.text, "utf8");
            return Object.freeze({
                bytes,
                path: file.path,
                sha256: sha256(bytes),
                size: bytes.length,
                text: file.text,
                utf16CodeUnits: file.text.length,
            });
        })
        .sort((left, right) => compareCodeUnits(left.path, right.path));
const buildMetadata = (dependencyRootsInput, filesInput) => {
    const dependencyRoots = sorted(dependencyRootsInput || []);
    const files = materializeFiles(filesInput || []);
    const canonicalInput =
        metadataPolicy.inventoryCanonicalDomain +
        dependencyRoots.map((root) => `|root=${root}`).join("") +
        files
            .map((file) => `|file=${file.path},${file.size},${file.sha256}`)
            .join("");
    const fingerprint = sha256(Buffer.from(canonicalInput, "ascii"));
    const catalog = catalogPolicy.deriveCatalog(
        dependencyRoots,
        files.map((file) => ({
            json: file.text,
            path: file.path,
            sha256: file.sha256,
            size: file.size,
        })),
    );
    assert(
        catalog.packageMetadataInventoryFingerprint === fingerprint &&
            catalog.inventoryCanonicalInput === canonicalInput,
        "Independent package catalog did not bind the metadata inventory.",
    );
    return Object.freeze({
        canonicalInput,
        catalog,
        dependencyRoots,
        files,
        fingerprint,
        totalBytes: files.reduce((total, file) => total + file.size, 0),
    });
};
const targetKind = (filePath) =>
    isDeclarationPath(filePath) ? "declaration" : "source";
const buildTargets = (metadataFingerprint, filesInput) => {
    const files = materializeFiles(filesInput || []).map((file) =>
        Object.freeze({ ...file, kind: targetKind(file.path) }),
    );
    const canonicalInput =
        targetPolicy.inventoryCanonicalDomain +
        `|packageMetadataInventoryFingerprint=${metadataFingerprint}` +
        files
            .map(
                (file) =>
                    `|file=${file.path},${file.kind},${file.size},${file.sha256}`,
            )
            .join("");
    return Object.freeze({
        canonicalInput,
        files,
        fingerprint: sha256(Buffer.from(canonicalInput, "ascii")),
        totalBytes: files.reduce((total, file) => total + file.size, 0),
    });
};
const snapshotResolutionKind = (filePath) => {
    if (isDeclarationPath(filePath)) return "declaration";
    if (filePath.startsWith("/sources/") && isExecutablePath(filePath)) {
        return "project-source";
    }
    return "non-resolution";
};
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
assert(
    canonicalResolutionPath("/sources/节点.mts") ===
        "/sources/%E8%8A%82%E7%82%B9.mts" &&
        canonicalResolutionPath("/sources/节点.mts") !==
            canonicalResolutionPath("/sources/%E8%8A%82%E7%82%B9.mts") &&
        workerBinding(
            'isNodeRevisionTwoCanonicalTargetPath("/sources/节点.mts")',
        ) === true &&
        workerBinding(
            'isControlledNodeCanonicalTargetPath("/sources/节点.mts")',
        ) === false &&
        workerBinding(
            'resolveControlledNodeRelativePath("/sources/目录", "./dep.mts", false, true)',
        ) === "/sources/目录/dep.mts",
    "Node revision-2 UTF-8 path identity or Worker resolver policy drifted.",
);
const buildSnapshotIdentity = (ordinary) => {
    const canonicalInput =
        "autojs6.typescript.node-resolution.snapshot-targets.v2" +
        `|profileSchemaRevision=${fixture.authority.profileSchemaRevision}` +
        `|profileRevision=${fixture.authority.profileRevision}` +
        `|nodeModuleOutputPolicyFingerprint=${outputPolicy.fingerprint}` +
        ordinary
            .map(
                (file) =>
                    `|file=${canonicalResolutionPath(file.path)},${file.size},` +
                    `${file.utf16CodeUnits},` +
                    `${file.sha256},${snapshotResolutionKind(file.path)}`,
            )
            .join("");
    return Object.freeze({
        canonicalInput,
        fingerprint: sha256(Buffer.from(canonicalInput, "ascii")),
    });
};
const buildTargetUniverseIdentity = (snapshot, ordinary, targets) => {
    const mergedTargets = ordinary
        .map((file) => ({
            kind: snapshotResolutionKind(file.path),
            path: file.path,
        }))
        .filter((target) => target.kind !== "non-resolution")
        .concat(
            targets === null
                ? []
                : targets.files.map((file) => ({
                      kind: file.kind === "declaration" ? "declaration" : "project-source",
                      path: `/${file.path}`,
                  })),
        )
        .sort((left, right) => compareCodeUnits(left.path, right.path));
    assert(
        new Set(mergedTargets.map((target) => asciiCaseFold(target.path))).size ===
            mergedTargets.length,
        "P2-6 target universe contains a path collision.",
    );
    const canonicalInput =
        "autojs6.typescript.node-resolution.target-universe.v2" +
        `|resolverPolicyRevision=${fixture.authority.resolverPolicyRevision}` +
        `|packageTargetPolicyRevision=${targetPolicy.policyRevision}` +
        `|packageTargetPolicyFingerprint=${targetPolicy.fingerprint}` +
        `|snapshotTargetInventoryFingerprint=${snapshot.fingerprint}` +
        `|packageTargetInventoryFingerprint=${targets?.fingerprint ?? "none"}` +
        mergedTargets
            .map(
                (target) =>
                    `|target=${canonicalResolutionPath(target.path)},${target.kind}`,
            )
            .join("");
    return Object.freeze({
        canonicalInput,
        fingerprint: sha256(Buffer.from(canonicalInput, "ascii")),
        targets: Object.freeze(mergedTargets),
    });
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
const compilerOnlyFiles = Object.freeze([
    Object.freeze({ path: "/declarations/lib.d.ts", text: minimalLibrary }),
    Object.freeze({ path: "/declarations/node/index.d.ts", text: "export {};\n" }),
]);

let requestCounter = 0;
const createP2Operation = (scenario, repeat = 0) => {
    assert(
        exactKeys(scenario, [
            "classification",
            "dependencyRoots",
            "entryPath",
            "expected",
            "id",
            "metadataFiles",
            "sourceFiles",
            "targetFiles",
            "transportMode",
        ]),
        `${scenario.id}: scenario shape is not exact.`,
    );
    const ordinary = materializeFiles([...compilerOnlyFiles, ...scenario.sourceFiles]);
    const metadata =
        scenario.transportMode === "noMetadata"
            ? null
            : buildMetadata(scenario.dependencyRoots, scenario.metadataFiles);
    const targets =
        scenario.transportMode === "metadataAndTargets"
            ? buildTargets(metadata.fingerprint, scenario.targetFiles)
            : null;
    const snapshot = buildSnapshotIdentity(ordinary);
    const universe = buildTargetUniverseIdentity(snapshot, ordinary, targets);
    assert(
        ordinary.some((file) => file.path === scenario.entryPath) &&
            (scenario.transportMode === "noMetadata"
                ? metadata === null && targets === null
                : scenario.transportMode === "metadata"
                  ? metadata !== null && targets === null
                  : scenario.transportMode === "metadataAndTargets" &&
                    metadata !== null &&
                    targets !== null),
        `${scenario.id}: transport mode or entry inventory is malformed.`,
    );
    const authority = projectInputAuthority({
        fileRecords: [
            ...(metadata?.files ?? []).map((file) =>
                inputArchiveRecord("package-metadata", `/${file.path}`, file.bytes),
            ),
            ...(targets?.files ?? []).map((file) =>
                inputArchiveRecord("package-target-v2", `/${file.path}`, file.bytes),
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
        ],
    });
    return {
        authority,
        entryPath: scenario.entryPath,
        metadata,
        ordinary,
        requestId: `nodenext-p2-6-${scenario.id}-${repeat}-${++requestCounter}`,
        scenario,
        sequence: 0,
        snapshot,
        targets,
        universe,
    };
};
const operationFileCount = (operation) =>
    (operation.metadata?.files.length ?? 0) +
    (operation.targets?.files.length ?? 0) +
    operation.ordinary.length;
const operationTotalBytes = (operation) =>
    (operation.metadata?.totalBytes ?? 0) +
    (operation.targets?.totalBytes ?? 0) +
    operation.ordinary.reduce((total, file) => total + file.size, 0);
const p2StartFrame = (operation) => {
    const frame = {
        ...operation.authority,
        configKind: fixture.authority.configKind,
        configSchemaRevision: fixture.authority.configSchemaRevision,
        entryPath: operation.entryPath,
        fileCount: operationFileCount(operation),
        kind: "projectInputStart",
        nodeModuleOutputPolicyFingerprint:
            fixture.authority.nodeModuleOutputPolicyFingerprint,
        nodeModuleOutputPolicyRevision:
            fixture.authority.nodeModuleOutputPolicyRevision,
        nodeModuleOutputPolicySchemaRevision:
            fixture.authority.nodeModuleOutputPolicySchemaRevision,
        profileRevision: fixture.authority.profileRevision,
        profileSchemaRevision: fixture.authority.profileSchemaRevision,
        requestId: operation.requestId,
        resolverPolicyRevision: fixture.authority.resolverPolicyRevision,
        runtimeCapabilitiesFingerprint:
            fixture.authority.runtimeCapabilitiesFingerprint,
        sequence: operation.sequence,
        snapshotTargetInventoryFingerprint: operation.snapshot.fingerprint,
        targetProfile: fixture.authority.targetProfile,
        targetUniverseFingerprint: operation.universe.fingerprint,
        totalBytes: operationTotalBytes(operation),
        workerSpinMilliseconds: 0,
        workerSpinPhase: "none",
    };
    if (operation.metadata !== null) {
        Object.assign(frame, {
            nodePackageFormatCatalogFingerprint:
                operation.metadata.catalog.catalogFingerprint,
            nodePackageFormatCatalogRevision: catalogPolicy.authority.catalogRevision,
            nodePackageFormatSchemaRevision: catalogPolicy.authority.schemaRevision,
            packageMetadataByteLength: operation.metadata.totalBytes,
            packageMetadataCanonical: operation.metadata.canonicalInput,
            packageMetadataFileCount: operation.metadata.files.length,
            packageMetadataInventoryFingerprint: operation.metadata.fingerprint,
            packageMetadataPolicyFingerprint: metadataPolicy.fingerprint,
            packageMetadataPolicyRevision: metadataPolicy.policyRevision,
            packageMetadataSchemaRevision: metadataPolicy.schemaRevision,
        });
    }
    if (operation.targets !== null) {
        Object.assign(frame, {
            nodePackageTargetByteLength: operation.targets.totalBytes,
            nodePackageTargetCanonical: operation.targets.canonicalInput,
            nodePackageTargetFileCount: operation.targets.files.length,
            nodePackageTargetFingerprint: operation.targets.fingerprint,
            nodePackageTargetPolicyFingerprint: targetPolicy.fingerprint,
            nodePackageTargetPolicyRevision: targetPolicy.policyRevision,
            nodePackageTargetSchemaRevision: targetPolicy.schemaRevision,
        });
    }
    const expectedKeys =
        operation.targets !== null
            ? programMetadataAndTargets
            : operation.metadata !== null
              ? programMetadata
              : programNoMetadata;
    assert(
        exactKeys(frame, expectedKeys),
        `${operation.scenario.id}: Program start is not exact ${expectedKeys.length}-field framing.`,
    );
    return frame;
};
const sendFile = async (operation, fileIndex, file) => {
    const transportPath = file.path.startsWith("/") ? file.path : `/${file.path}`;
    expectAck(
        await dispatch({
            byteLength: file.size,
            fileIndex,
            kind: "projectInputFileStart",
            path: transportPath,
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
const sendP2Input = async (operation) => {
    expectAck(
        await dispatch(p2StartFrame(operation)),
        operation,
        `${operation.scenario.id} start`,
    );
    let fileIndex = 0;
    for (const file of operation.metadata?.files ?? []) {
        await sendFile(operation, fileIndex++, file);
    }
    for (const file of operation.targets?.files ?? []) {
        await sendFile(operation, fileIndex++, file);
    }
    for (const file of operation.ordinary) {
        await sendFile(operation, fileIndex++, file);
    }
};
const inputEndFrame = (operation) => ({
    fileCount: operationFileCount(operation),
    kind: "projectInputEnd",
    requestId: operation.requestId,
    sequence: operation.sequence,
    totalBytes: operationTotalBytes(operation),
});
const phaseSignature = (requestId) =>
    observedPhaseEvents
        .filter((event) => event.requestId === requestId)
        .map((event) => `${event.phase}:${event.state}`);
const expectedCompletePhaseSignature = Object.freeze([
    "ingress:started",
    "ingress:completed",
    "parse:started",
    "parse:completed",
    "semantic:started",
    "semantic:completed",
    "emit:started",
    "emit:completed",
]);
const compileP2Operation = async (operation) => {
    const callStart = programCalls.length;
    activePackageTargetPaths = Object.freeze(
        (operation.targets?.files ?? []).map((file) => `/${file.path}`),
    );
    await sendP2Input(operation);
    const result = await readFramedResult(
        operation,
        await dispatch(inputEndFrame(operation)),
        operation.scenario.id,
    );
    const calls = programCalls.slice(callStart);
    assert(
        calls.length === 1 && calls[0].hasResolutionHooks,
        `${operation.scenario.id}: did not construct exactly one resolver-bound Program.`,
    );
    refreshProgramCall(calls[0]);
    assert(
        exactJson(phaseSignature(operation.requestId), expectedCompletePhaseSignature),
        `${operation.scenario.id}: phase order drifted: ` +
            JSON.stringify(phaseSignature(operation.requestId)),
    );
    assertWorkerIdle(`${operation.scenario.id} framed result`);
    const lateMessages = await dispatch({
        kind: "projectResultAck",
        requestId: operation.requestId,
        sequence: 0,
    });
    assert(
        lateMessages.length === 0,
        `${operation.scenario.id}: late result ACK was not harmless.`,
    );
    activePackageTargetPaths = Object.freeze([]);
    return {
        call: calls[0],
        result,
    };
};

const normalizedResolutions = (items) =>
    items
        .map((item) => ({
            containingFile: item.containingFile,
            specifier: item.specifier,
            resolvedFileName: item.resolvedFileName,
            extension: item.extension,
        }))
        .sort((left, right) =>
            compareCodeUnits(
                `${left.containingFile}|${left.specifier}`,
                `${right.containingFile}|${right.specifier}`,
            ),
        );
const assertExpectedResolutions = (actualInput, expectedInput, label) => {
    const actual = normalizedResolutions(actualInput);
    const expected = normalizedResolutions(expectedInput);
    assert(
        exactJson(actual, expected),
        `${label} resolution trace drifted: ${JSON.stringify({ actual, expected })}.`,
    );
};
const impliedFormatName = (value) =>
    value === realTypeScript.ModuleKind.ESNext
        ? "esm"
        : value === realTypeScript.ModuleKind.CommonJS
          ? "commonjs"
          : null;
const expectedModulePathForSource = (sourcePath) => {
    const sourceExtension = sourcePath.endsWith(".mts")
        ? ".mts"
        : sourcePath.endsWith(".cts")
          ? ".cts"
          : ".ts";
    const outputExtension = sourceExtension === ".mts"
        ? ".mjs"
        : sourceExtension === ".cts"
          ? ".cjs"
          : ".js";
    return `/outputs/${sourcePath.slice(
        "/sources/".length,
        -sourceExtension.length,
    )}${outputExtension}`;
};
const assertConfigNoneResultShape = (result, label) => {
    assert(
        exactKeys(result, configNoneResultKeys) &&
            Array.isArray(result.diagnostics) &&
            Array.isArray(result.outputs) &&
            result.outputs.every(
                (output) =>
                    exactKeys(output, outputKeys) &&
                    typeof output.path === "string" &&
                    typeof output.text === "string" &&
                    Array.isArray(output.sourcePaths) &&
                    output.sha256 === sha256(Buffer.from(output.text, "utf8")),
            ),
        `${label}: config-none result is not exact 11-key/4-key framing.`,
    );
};
const assertResultMatchesObservedOutputs = (result, call, label) => {
    assert(
        result.outputs.length === call.outputs.length &&
            result.outputs.every((output) => {
                const observed = call.outputs.filter(
                    (candidate) => candidate.path === output.path,
                );
                return (
                    observed.length === 1 &&
                    observed[0].text === output.text &&
                    exactJson(observed[0].sourcePaths, output.sourcePaths)
                );
            }),
        `${label}: framed outputs do not match the Program writeFile evidence.`,
    );
};
const assertExpectedOutputMappings = (outputs, sourcePaths, label) => {
    for (const sourcePath of sourcePaths) {
        const modulePath = expectedModulePathForSource(sourcePath);
        const matchingModule = outputs.filter((output) => output.path === modulePath);
        const matchingMap = outputs.filter(
            (output) => output.path === `${modulePath}.map`,
        );
        assert(
            matchingModule.length === 1 &&
                matchingMap.length === 1 &&
                exactJson(matchingModule[0].sourcePaths, [sourcePath]) &&
                exactJson(matchingMap[0].sourcePaths, [sourcePath]),
            `${label}: ${sourcePath} did not map one-to-one to ${modulePath} and its map.`,
        );
    }
};
const assertSuccessfulP2 = (compiled, scenario) => {
    const { call, result } = compiled;
    assertConfigNoneResultShape(result, scenario.id);
    assert(
        call.options.module === realTypeScript.ModuleKind.NodeNext &&
            call.options.moduleResolution === realTypeScript.ModuleResolutionKind.NodeNext &&
            call.options.noEmitOnError === true &&
            call.emitCalls === 1 &&
            call.emitSkipped === false &&
            exactJson(
                call.rootNames,
                [...scenario.expected.rootNames, "/declarations/node/index.d.ts"],
            ) &&
            exactJson(
                sorted(call.outputs.map((output) => output.path)),
                scenario.expected.outputPaths,
            ) &&
            result.compilerApi === "createIncrementalProgram" &&
            result.targetProfile === fixture.authority.targetProfile &&
            result.profileSchemaRevision === fixture.authority.profileSchemaRevision &&
            result.profileRevision === fixture.authority.profileRevision &&
            result.configKind === fixture.authority.configKind &&
            result.configSchemaRevision === fixture.authority.configSchemaRevision &&
            result.tsVersion === expectedVersion &&
            result.options.module === realTypeScript.ModuleKind.NodeNext &&
            result.options.moduleResolution ===
                realTypeScript.ModuleResolutionKind.NodeNext &&
            result.options.noEmitOnError === true &&
            result.emitSkipped === false &&
            result.diagnostics.length === 0 &&
            exactJson(
                result.outputs.map((output) => output.path),
                scenario.expected.outputPaths,
            ),
        `${scenario.id}: Program/result roots, options, diagnostics, or emit outputs drifted.`,
    );
    assertResultMatchesObservedOutputs(result, call, scenario.id);
    assertExpectedOutputMappings(
        result.outputs,
        scenario.expected.rootNames,
        scenario.id,
    );
    assertExpectedResolutions(
        call.resolutions,
        scenario.expected.moduleResolutions,
        `${scenario.id} module`,
    );
    assertExpectedResolutions(
        call.typeReferenceResolutions,
        scenario.expected.typeReferenceResolutions,
        `${scenario.id} type-reference`,
    );
    const impliedFormats = Object.fromEntries(
        call.sourceFiles
            .filter((sourceFile) =>
                Object.prototype.hasOwnProperty.call(
                    scenario.expected.impliedNodeFormats,
                    sourceFile.fileName,
                ),
            )
            .map((sourceFile) => [
                sourceFile.fileName,
                impliedFormatName(sourceFile.impliedNodeFormat),
            ]),
    );
    assert(
        exactJson(impliedFormats, scenario.expected.impliedNodeFormats),
        `${scenario.id}: implied Node formats drifted: ${JSON.stringify(impliedFormats)}.`,
    );
    const loadedPackageTargets = call.sourceFiles
        .map((sourceFile) => sourceFile.fileName)
        .filter((fileName) => fileName.startsWith("/packages/"));
    assert(
        exactJson(loadedPackageTargets, scenario.expected.loadedPackageTargets) &&
            !call.rootNames.some((rootName) => rootName.startsWith("/packages/")) &&
            !call.outputs.some(
                (output) =>
                    output.path.includes("/packages/") ||
                    output.sourcePaths.some((sourcePath) =>
                        sourcePath.startsWith("/packages/"),
                    ),
            ) &&
            call.packageOrdinaryReads.every((read) => !read.visible) &&
            scenario.expected.loadedPackageTargets.every((targetPath) =>
                call.packageGetSourceFileReads.some(
                    (read) => read.path === targetPath && read.resolved,
                ),
            ),
        `${scenario.id}: package target grant/root/output isolation drifted.`,
    );
    for (const output of call.outputs.filter((candidate) =>
        candidate.path.endsWith(".map"),
    )) {
        const sourceMap = JSON.parse(output.text);
        assert(
            sourceMap.version === 3 &&
                sourceMap.file === path.posix.basename(output.path, ".map") &&
                !Object.prototype.hasOwnProperty.call(sourceMap, "sourcesContent"),
            `${scenario.id}: Source Map v3 output drifted: ${output.path}.`,
        );
    }
};

const failureDiagnosticCodes = workerBinding(
    "controlledNodeResolutionFailureDiagnosticCodes",
);
const assertFailedP2 = (compiled, scenario) => {
    const { call, result } = compiled;
    const expected = scenario.expected;
    assertConfigNoneResultShape(result, scenario.id);
    const matchingModule = call.resolutions.filter(
        (resolution) =>
            resolution.containingFile === expected.sourcePath &&
            resolution.specifier === expected.specifier,
    );
    assert(
        failureDiagnosticCodes[expected.policyCode] === expected.diagnosticCode &&
            call.emitCalls === 0 &&
            call.emitSkipped === null &&
            call.outputs.length === 0 &&
            matchingModule.length === 1 &&
            matchingModule[0].resolvedFileName === null &&
            matchingModule[0].extension === null &&
            !call.sourceFiles.some((sourceFile) =>
                sourceFile.fileName.startsWith("/packages/"),
            ) &&
            !call.packageGetSourceFileReads.some((read) => read.resolved) &&
            call.packageOrdinaryReads.every((read) => !read.visible) &&
            result.compilerApi === "createIncrementalProgram" &&
            result.targetProfile === fixture.authority.targetProfile &&
            result.profileSchemaRevision === fixture.authority.profileSchemaRevision &&
            result.profileRevision === fixture.authority.profileRevision &&
            result.configKind === fixture.authority.configKind &&
            result.configSchemaRevision === fixture.authority.configSchemaRevision &&
            result.tsVersion === expectedVersion &&
            result.options.module === realTypeScript.ModuleKind.NodeNext &&
            result.options.moduleResolution ===
                realTypeScript.ModuleResolutionKind.NodeNext &&
            result.options.noEmitOnError === true &&
            result.emitSkipped === true &&
            result.outputs.length === 0 &&
            result.diagnostics.length === 1 &&
            exactKeys(result.diagnostics[0], resolutionDiagnosticKeys) &&
            result.diagnostics[0].category === realTypeScript.DiagnosticCategory.Error &&
            result.diagnostics[0].code === expected.diagnosticCode &&
            result.diagnostics[0].phase === "resolution" &&
            result.diagnostics[0].sourcePath === expected.sourcePath &&
            result.diagnostics[0].start === null &&
            result.diagnostics[0].length === null &&
            result.diagnostics[0].line === null &&
            result.diagnostics[0].column === null &&
            result.diagnostics[0].message.includes(expected.policyCode) &&
            exactKeys(result.diagnostics[0].nodeResolution, ["policyCode"]) &&
            result.diagnostics[0].nodeResolution.policyCode === expected.policyCode,
        `${scenario.id}: stable fail-closed resolution/diagnostic authority drifted.`,
    );
};

const createP1Operation = () => {
    const ordinary = materializeFiles([
        ...compilerOnlyFiles,
        {
            path: "/sources/main.ts",
            text: "export const legacy: number = 1;\n",
        },
    ]);
    return {
        authority: projectInputAuthority({
            fileRecords: ordinary.map((file) =>
                inputArchiveRecord(
                    file.path.startsWith("/declarations/")
                        ? "declaration"
                        : "source",
                    file.path,
                    file.bytes,
                ),
            ),
        }),
        metadata: null,
        ordinary,
        requestId: `nodenext-p1-regression-${++requestCounter}`,
        sequence: 0,
        targets: null,
    };
};
const p1StartFrame = (operation) => ({
    ...operation.authority,
    configKind: "none",
    configSchemaRevision: 1,
    fileCount: operation.ordinary.length,
    kind: "projectInputStart",
    profileRevision: 1,
    profileSchemaRevision: 1,
    requestId: operation.requestId,
    sequence: operation.sequence,
    targetProfile: "node",
    totalBytes: operationTotalBytes(operation),
    workerSpinMilliseconds: 0,
    workerSpinPhase: "none",
});
const readFramedResult = async (operation, inputEndMessages, label) => {
    expectAck(inputEndMessages, operation, `${label} input end`);
    const starts = inputEndMessages.filter(
        (message) =>
            message.kind === "projectResultStart" &&
            message.requestId === operation.requestId,
    );
    const start = starts[0];
    assert(
        starts.length === 1 &&
            exactKeys(start, ["byteLength", "chunkCount", "kind", "requestId", "sha256"]) &&
            !inputEndMessages.some(
                (message) =>
                    message.kind === "projectError" ||
                    message.kind === "projectConfigError" ||
                    message.kind === "projectResultChunk" ||
                    message.kind === "projectResultEnd",
            ) &&
            inputEndMessages.every(
                (message) =>
                    message.kind === "projectInputAck" ||
                    message.kind === "projectPhase" ||
                    message.kind === "projectPhaseProgress" ||
                    message.kind === "projectResultStart",
            ) &&
            Number.isSafeInteger(start.byteLength) &&
            start.byteLength > 0 &&
            Number.isSafeInteger(start.chunkCount) &&
            start.chunkCount > 0 &&
            /^[0-9a-f]{64}$/.test(start.sha256),
        `${label}: did not begin one exact framed result.`,
    );
    const chunks = [];
    for (let index = 0; index < start.chunkCount; index += 1) {
        const messages = await dispatch({
            kind: "projectResultAck",
            requestId: operation.requestId,
            sequence: index - 1,
        });
        assert(
            messages.length === 1 &&
                exactKeys(messages[0], ["data", "index", "kind", "requestId"]) &&
                messages[0].kind === "projectResultChunk" &&
                messages[0].requestId === operation.requestId &&
                messages[0].index === index &&
                typeof messages[0].data === "string",
            `${label}: result chunk ${index} drifted.`,
        );
        chunks.push(Buffer.from(messages[0].data, "base64"));
    }
    const terminal = await dispatch({
        kind: "projectResultAck",
        requestId: operation.requestId,
        sequence: start.chunkCount - 1,
    });
    const end = terminal.find((message) => message.kind === "projectResultEnd");
    assert(
        terminal.length === 2 &&
            terminal[0].kind === "projectPhase" &&
            terminal[0].phase === "emit" &&
            terminal[0].state === "completed" &&
            exactKeys(end, ["byteLength", "chunkCount", "kind", "requestId", "sha256"]) &&
            end?.requestId === operation.requestId &&
            end.byteLength === start.byteLength &&
            end.chunkCount === start.chunkCount &&
            end.sha256 === start.sha256,
        `${label}: did not finish emit before its exact result end.`,
    );
    const bytes = Buffer.concat(chunks);
    assert(
        bytes.length === start.byteLength && sha256(bytes) === start.sha256,
        `${label}: framed result identity drifted.`,
    );
    try {
        return JSON.parse(bytes.toString("utf8"));
    } catch (error) {
        throw new Error(`${label}: framed result was not JSON: ${error.message}`);
    }
};
const runP1Regression = async () => {
    const operation = createP1Operation();
    const callStart = programCalls.length;
    const start = p1StartFrame(operation);
    assert(
        exactKeys(start, legacyNoBoundary.filter(
            (key) => key !== "entryPath" && key !== "runtimeCapabilitiesFingerprint",
        )),
        "P1 successor no longer uses its exact schema-v1 config-none start.",
    );
    expectAck(await dispatch(start), operation, "P1 start");
    for (let index = 0; index < operation.ordinary.length; index += 1) {
        await sendFile(operation, index, operation.ordinary[index]);
    }
    const result = await readFramedResult(
        operation,
        await dispatch(inputEndFrame(operation)),
        "P1 successor",
    );
    const calls = programCalls.slice(callStart);
    assertConfigNoneResultShape(result, "P1 successor");
    assert(
        calls.length === 1 &&
            !calls[0].hasResolutionHooks &&
            calls[0].emitCalls === 1 &&
            calls[0].emitSkipped === false &&
            exactJson(calls[0].rootNames, [
                "/sources/main.ts",
                "/declarations/node/index.d.ts",
            ]) &&
            result.targetProfile === "node" &&
            result.profileSchemaRevision === 1 &&
            result.profileRevision === 1 &&
            result.configKind === "none" &&
            result.configSchemaRevision === 1 &&
            result.compilerApi === "createIncrementalProgram" &&
            result.tsVersion === expectedVersion &&
            result.emitSkipped === false &&
            result.diagnostics.length === 0 &&
            exactJson(result.outputs.map((output) => output.path), [
                "/outputs/main.js",
                "/outputs/main.js.map",
            ]),
        "P1 successor result/Program contract regressed after P2-6 operations.",
    );
    assertResultMatchesObservedOutputs(result, calls[0], "P1 successor");
    assertExpectedOutputMappings(result.outputs, ["/sources/main.ts"], "P1 successor");
    assertWorkerIdle("P1 successor");
};

(async () => {
    const scenarioById = new Map(
        fixture.scenarios.map((scenario) => [scenario.id, scenario]),
    );
    assert(
        scenarioById.size === fixture.scenarios.length &&
            exactJson(sorted(Array.from(scenarioById.keys())), [
                "exports-no-match",
                "metadata-only-root-imports",
                "no-metadata-clean-successor",
                "target-bearing-relative-exports-types",
                "target-not-in-snapshot",
            ]),
        "P2-6 scenario inventory drifted.",
    );

    if (process.env.R2_S1_SMOKE_ONLY === "1") {
        const scenario = scenarioById.get(
            "target-bearing-relative-exports-types",
        );
        const compiled = await compileP2Operation(createP2Operation(scenario));
        assertSuccessfulP2(compiled, scenario);
        assert(
            programCalls.length === 1 &&
                programCalls[0].hasResolutionHooks &&
                compilerSystemAccessCount === 0 &&
                defaultResolverAccessCount === 0 &&
                networkAccessCount === 0,
            "S1 Node smoke escaped its exact Worker resolver authority.",
        );
        assertWorkerIdle("S1 Node revision-2 smoke");
        process.stdout.write(
            `S1 Node revision-2 Worker smoke passed (TypeScript ${expectedVersion}; ` +
                ".mts/.cts -> .mjs/.cjs + paired source maps).\n",
        );
        return;
    }

    for (const id of [
        "metadata-only-root-imports",
        "target-bearing-relative-exports-types",
    ]) {
        const scenario = scenarioById.get(id);
        const compiled = await compileP2Operation(createP2Operation(scenario));
        assertSuccessfulP2(compiled, scenario);
    }

    for (const id of ["exports-no-match", "target-not-in-snapshot"]) {
        const scenario = scenarioById.get(id);
        const first = await compileP2Operation(createP2Operation(scenario, 1));
        const second = await compileP2Operation(createP2Operation(scenario, 2));
        assertFailedP2(first, scenario);
        assertFailedP2(second, scenario);
        assert(
            exactJson(first.result, second.result) &&
                exactJson(
                    normalizedResolutions(first.call.resolutions),
                    normalizedResolutions(second.call.resolutions),
                ) &&
                exactJson(
                    normalizedResolutions(first.call.typeReferenceResolutions),
                    normalizedResolutions(second.call.typeReferenceResolutions),
                ),
            `${scenario.id}: repeated failure was not stable.`,
        );
    }

    const cleanScenario = scenarioById.get("no-metadata-clean-successor");
    const clean = await compileP2Operation(createP2Operation(cleanScenario));
    assertSuccessfulP2(clean, cleanScenario);
    assert(
        !clean.call.resolutions.some(
            (resolution) =>
                resolution.resolvedFileName === null ||
                resolution.specifier === "dual" ||
                resolution.specifier === "dual/missing",
        ) &&
            !clean.call.sourceFiles.some((sourceFile) =>
                sourceFile.fileName.startsWith("/packages/"),
            ),
        "Clean P2 successor inherited a failure or package-target grant.",
    );

    await runP1Regression();
    assert(
        programCalls.length === 8 &&
            programCalls.filter((call) => call.hasResolutionHooks).length === 7 &&
            resolverHookCallCount >= 7 &&
            typeReferenceResolverHookCallCount >= 1 &&
            compilerSystemAccessCount === 0 &&
            defaultResolverAccessCount === 0 &&
            networkAccessCount === 0 &&
            importScriptsCount === 1,
        "P2-6 integration used default resolver/fs/network or constructed the wrong Programs.",
    );
    assertWorkerIdle("final P2-7/P1 sequence");
    process.stdout.write(
        "R2-M2-P2-7 Worker framed results verified: exact starts=30/40/47, " +
            "exact result=11 keys, outputs=4 keys, stop-and-wait ACK/sha256, " +
            "ts/mts/cts implied formats, relative/imports/exports/type-reference, " +
            "ts/mts/cts->js/mjs/cjs+maps, stable result failures=2x2, " +
            "clean P2 successor=1, " +
            "schema-v1 24/37 identity preserved, P1 regression=1, " +
            "P2 framed results=7, default resolver/ts.sys/network=0\n",
    );
})().catch((error) => {
    process.stderr.write(`${error.stack || error}\n`);
    process.exitCode = 1;
});
