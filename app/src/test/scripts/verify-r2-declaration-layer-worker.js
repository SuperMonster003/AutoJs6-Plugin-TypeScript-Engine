"use strict";

const cryptoModule = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const {
    projectInputAuthority,
    record: inputRecord,
    sha256: inputSha256,
} = require("./r2-input-archive-authority.js");

const [workerArgument, compilerArgument, expectedVersion] =
    process.argv.slice(2);
if (!workerArgument || !compilerArgument || !expectedVersion) {
    throw new Error(
        "Usage: node verify-r2-declaration-layer-worker.js " +
            "<worker.js> <typescript.js> 6.0.3",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const workerSource = fs.readFileSync(workerPath, "utf8");
const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};

assert(expectedVersion === "6.0.3", "Declaration-layer gate requires TypeScript 6.0.3.");

if (!global.crypto) {
    Object.defineProperty(global, "crypto", {
        configurable: true,
        value: cryptoModule.webcrypto,
    });
}
const realTypeScript = require(compilerPath);
let programCount = 0;
const typeScriptFacade = Object.create(realTypeScript);
Object.defineProperty(typeScriptFacade, "createIncrementalProgram", {
    value: () => {
        programCount += 1;
        throw new Error("Declaration-layer policy constructed a Program too early.");
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
        filename: `${workerPath}#declaration-layer-worker`,
    });
const countSourceTokens = workerBinding("countSourceTokens");
const createDirectoryIndex = workerBinding("createDirectoryIndex");
const createProjectCompilerHost = workerBinding("createProjectCompilerHost");
const createReadonlyProjectVfs = workerBinding("createReadonlyProjectVfs");

const textRecord = (filePath, text) => ({
    byteLength: Buffer.byteLength(text, "utf8"),
    path: filePath,
    text,
    tokenCount: countSourceTokens(text),
});
const buildLegacyProject = (additionalFiles = []) => {
    const files = [
        textRecord("/declarations/lib.d.ts", ""),
        textRecord("/sources/main.ts", 'toast("ready");\n'),
        ...additionalFiles,
    ];
    return {
        configKind: "none",
        configSchemaRevision: 1,
        directoryIndex: createDirectoryIndex(files.map((file) => file.path)),
        files,
        pathChars: files.reduce((total, file) => total + file.path.length, 0),
        profileRevision: 1,
        profileSchemaRevision: 1,
        rootNames: ["/sources/main.ts"],
        targetProfile: "rhino",
        tokenCount: files.reduce((total, file) => total + file.tokenCount, 0),
        totalBytes: files.reduce((total, file) => total + file.byteLength, 0),
    };
};
const expectThrow = (operation, expectedMessage, label) => {
    let error = null;
    try {
        operation();
    } catch (caught) {
        error = caught;
    }
    assert(
        error instanceof Error && error.message.includes(expectedMessage),
        `${label} was not rejected exactly: ${error?.message || "no error"}.`,
    );
};

const commonOnlyVfs = createReadonlyProjectVfs(buildLegacyProject());
assert(
    JSON.stringify(commonOnlyVfs.rootNames) ===
        JSON.stringify(["/sources/main.ts"]) &&
        JSON.stringify(commonOnlyVfs.compilerRootNames) ===
            JSON.stringify(["/sources/main.ts"]),
    "Legacy common-only project changed its root authority.",
);
commonOnlyVfs.release();

const layeredVfs = createReadonlyProjectVfs(
    buildLegacyProject([
        textRecord(
            "/declarations/rhino/index.d.ts",
            "declare function toast(message: unknown): void;\n",
        ),
    ]),
);
assert(
    JSON.stringify(layeredVfs.rootNames) ===
        JSON.stringify(["/sources/main.ts"]) &&
        JSON.stringify(layeredVfs.compilerRootNames) ===
            JSON.stringify([
                "/sources/main.ts",
                "/declarations/rhino/index.d.ts",
            ]) &&
        JSON.stringify(layeredVfs.sourcePaths) ===
            JSON.stringify(["/sources/main.ts"]),
    "Rhino declarations were not isolated as one compiler-only root.",
);
layeredVfs.release();
assert(
    layeredVfs.rootNames.length === 0 &&
        layeredVfs.compilerRootNames.length === 0 &&
        layeredVfs.sourcePaths.length === 0,
    "Declaration-layer VFS retained its root inventory after release.",
);

const emitVfs = createReadonlyProjectVfs(
    buildLegacyProject([
        textRecord(
            "/declarations/rhino/index.d.ts",
            "declare function toast(message: unknown): void;\n",
        ),
    ]),
);
const emittedOutputs = new Map();
const emitHost = createProjectCompilerHost(
    emitVfs,
    emittedOutputs,
    { bytes: 0 },
    new Map(),
    {
        activePhase: "emit",
        phaseProgress: {
            completedUnits: 0,
            nextSequence: 1,
            phase: "emit",
            totalUnits: 1,
        },
        requestId: "r2-declaration-emit-fence",
    },
);
emitHost.writeFile("/outputs/main.js", "", false, undefined, [
    { fileName: "/declarations/rhino/index.d.ts" },
    { fileName: "/sources/main.ts" },
]);
assert(
    JSON.stringify(emittedOutputs.get("/outputs/main.js").sourcePaths) ===
        JSON.stringify(["/sources/main.ts"]),
    "Declaration compiler root leaked into emitted source provenance.",
);
emitVfs.release();

expectThrow(
    () =>
        createReadonlyProjectVfs(
            buildLegacyProject([
                textRecord(
                    "/declarations/rhino/core.d.ts",
                    "declare function sleep(milliseconds: number): void;\n",
                ),
            ]),
        ),
    "Rhino declaration layer has no canonical entry point.",
    "Partial Rhino layer",
);
expectThrow(
    () =>
        createReadonlyProjectVfs(
            buildLegacyProject([
                textRecord(
                    "/declarations/node/index.d.ts",
                    'declare module "autojs6:profile" {}\n',
                ),
            ]),
        ),
    "Rhino project snapshot contains the Node declaration layer.",
    "Node layer in a Rhino snapshot",
);

assert(
    workerSource.includes("rootNames: vfs.compilerRootNames") &&
        workerSource.includes("result.rootNames = vfs.rootNames.slice()"),
    "Worker compiler-root, public-result, or source-output fence drifted.",
);

const dispatch = async (message) => {
    publishedMessages.length = 0;
    await self.onmessage({ data: message });
    return publishedMessages.slice();
};
const requestId = "r2-declaration-node-frame";
(async () => {
    const rejectedDeclarationBytes = Buffer.alloc(0);
    const inputAuthority = projectInputAuthority({
        fileRecords: [
            inputRecord(
                "declaration",
                "/declarations/node/index.d.ts",
                rejectedDeclarationBytes,
            ),
        ],
    });
    const startMessages = await dispatch({
        configKind: "none",
        configSchemaRevision: 1,
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
    assert(
        startMessages.some(
            (message) =>
                message.kind === "projectInputAck" && message.sequence === 0,
        ),
        "Declaration-layer transport did not accept its start frame.",
    );
    const rejectionMessages = await dispatch({
        byteLength: 0,
        fileIndex: 0,
        kind: "projectInputFileStart",
        path: "/declarations/node/index.d.ts",
        requestId,
        sequence: 1,
        sha256: inputSha256(rejectedDeclarationBytes),
    });
    assert(
        rejectionMessages.length === 1 &&
            rejectionMessages[0].kind === "projectError" &&
            rejectionMessages[0].requestId === requestId &&
            rejectionMessages[0].message.includes(
                "Rhino input cannot transport the Node declaration layer.",
            ) &&
            !rejectionMessages.some(
                (message) => message.kind === "projectInputAck",
            ),
        `Node declaration frame was not rejected: ${JSON.stringify(rejectionMessages)}.`,
    );
    assert(
        workerBinding("framedInput") === null &&
            workerBinding("framedCompilationRequestId") === null &&
            workerBinding("framedResult") === null &&
            programCount === 0,
        "Node declaration frame retained state or reached Program construction.",
    );

    console.log(
        "R2-M2-C8 Worker declaration layering verified: " +
            "Rhino compiler-only root=1, user root=1, " +
            "Node-layer rejections=2, legacy common-only=1",
    );
})().catch((error) => {
    process.nextTick(() => {
        throw error;
    });
});
