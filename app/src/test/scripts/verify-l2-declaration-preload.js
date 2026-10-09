"use strict";

// Startup-latency L2-1 desktop golden. Boots the compiler Worker with the generated declaration
// bundles reachable through importScripts, then proves three things against the same Rhino
// project: a request that frames only its caller file is completed from the preloaded layers,
// its outputs are byte-identical to the historical fully framed transport, and a tampered
// declaration inventory fingerprint is refused before any Program is created.

const crypto = require("node:crypto");
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
    bundleDirectoryArgument,
    fixtureArgument,
    expectedVersion,
] = process.argv.slice(2);
if (
    !workerArgument ||
    !compilerArgument ||
    !bundleDirectoryArgument ||
    !fixtureArgument ||
    !expectedVersion
) {
    throw new Error(
        "Usage: node verify-l2-declaration-preload.js <worker.js> <typescript.js> " +
            "<bundle-directory> <provisional-rhino.json> <expected-version>",
    );
}

const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const sha256Hex = (bytes) => crypto.createHash("sha256").update(bytes).digest("hex");

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const bundleDirectory = path.resolve(bundleDirectoryArgument);
const fixture = JSON.parse(fs.readFileSync(path.resolve(fixtureArgument), "utf8"));
const bundleManifest = JSON.parse(
    fs.readFileSync(path.join(bundleDirectory, "bundles.json"), "utf8"),
);
assert(
    bundleManifest.schemaVersion === 1 &&
        bundleManifest.globalRegistry === "__autojs6DeclarationLayers" &&
        JSON.stringify(Object.keys(bundleManifest.bundles).sort()) ===
            JSON.stringify(["common", "node", "rhino"]),
    "Declaration bundle manifest shape drifted.",
);
const bundleFileToLayer = new Map();
for (const [layer, bundle] of Object.entries(bundleManifest.bundles)) {
    const bytes = fs.readFileSync(path.join(bundleDirectory, bundle.file));
    assert(
        bytes.length === bundle.size && sha256Hex(bytes) === bundle.sha256,
        `Declaration bundle ${bundle.file} does not match its manifest digest.`,
    );
    bundleFileToLayer.set(bundle.file, layer);
}

if (!global.crypto) {
    Object.defineProperty(global, "crypto", { value: crypto.webcrypto });
}
global.ts = require(compilerPath);
const importedScripts = [];
global.importScripts = (specifier) => {
    importedScripts.push(specifier);
    if (specifier === "typescript.js") return;
    assert(
        bundleFileToLayer.has(specifier),
        `Worker imported an unexpected script: ${specifier}`,
    );
    const file = path.join(bundleDirectory, specifier);
    vm.runInThisContext(fs.readFileSync(file, "utf8"), { filename: file });
};
const messages = [];
global.self = {
    postMessage: (message) => {
        messages.push(message);
    },
};
vm.runInThisContext(fs.readFileSync(workerPath, "utf8"), { filename: workerPath });
{
    const ready = messages.find((message) => message.kind === "ready");
    assert(
        ready && ready.tsVersion === expectedVersion,
        "Compiler Worker did not publish its exact ready version.",
    );
    messages.length = 0;
}
const workerBinding = (name) =>
    vm.runInThisContext(name, { filename: `${workerPath}#desktop-binding-${name}` });

const dispatch = async (data) => {
    assert(messages.length === 0, "Worker response queue retained an earlier frame.");
    await self.onmessage({ data });
    return messages.splice(0);
};
const expectAck = (responses, requestId, sequence) => {
    const failure = responses.find(
        (message) => message.kind === "projectError" && message.requestId === requestId,
    );
    assert(!failure, `Worker rejected input sequence ${sequence}: ${failure?.message}`);
    assert(
        responses.some(
            (message) =>
                message.kind === "projectInputAck" &&
                message.requestId === requestId &&
                message.sequence === sequence,
        ),
        `Worker did not ACK input sequence ${sequence}.`,
    );
};

const frameBytes = 64 * 1024;
const sourcePath = "/sources/main.ts";
const sourceText =
    "export const sdk: number = device.sdkInt;\n" +
    "export const preloaded: Promise<number> = Promise.resolve(sdk);\n";

const compileProject = async (
    requestId,
    declarationFiles,
    { frameDeclarations, tamperInventory = false },
) => {
    const files = declarationFiles
        .map((file) => ({ path: file.path, text: file.text, declaration: true }))
        .concat([{ path: sourcePath, text: sourceText, declaration: false }]);
    const records = files.map((file) =>
        inputArchiveRecord(
            file.declaration ? "declaration" : "source",
            file.path,
            Buffer.from(file.text, "utf8"),
        ),
    );
    const authority = { ...projectInputAuthority({ fileRecords: records }) };
    if (tamperInventory) {
        authority.declarationInventoryFingerprint =
            authority.declarationInventoryFingerprint.replace(/^./, (c) =>
                c === "0" ? "1" : "0",
            );
    }
    let sequence = 0;
    const startResponses = await dispatch({
        ...authority,
        kind: "projectInputStart",
        requestId,
        sequence,
        configKind: fixture.profileAuthority.configKind,
        configSchemaRevision: fixture.profileAuthority.configSchemaRevision,
        profileRevision: fixture.profileAuthority.profileRevision,
        profileSchemaRevision: fixture.profileAuthority.profileSchemaRevision,
        targetProfile: fixture.targetProfile,
        workerSpinMilliseconds: 0,
        workerSpinPhase: "none",
    });
    expectAck(startResponses, requestId, sequence);
    const extraMessages = startResponses.filter(
        (message) => message.kind !== "projectInputAck",
    );
    sequence += 1;

    const framed = files.filter((file) => frameDeclarations || !file.declaration);
    let fileIndex = 0;
    for (const file of framed) {
        const bytes = Buffer.from(file.text, "utf8");
        expectAck(
            await dispatch({
                byteLength: bytes.length,
                fileIndex,
                kind: "projectInputFileStart",
                path: file.path,
                requestId,
                sequence,
                sha256: sha256Hex(bytes),
            }),
            requestId,
            sequence,
        );
        sequence += 1;
        let chunkIndex = 0;
        for (let offset = 0; offset < bytes.length; offset += frameBytes) {
            expectAck(
                await dispatch({
                    chunkIndex,
                    data: bytes
                        .subarray(offset, Math.min(offset + frameBytes, bytes.length))
                        .toString("base64"),
                    fileIndex,
                    kind: "projectInputFileChunk",
                    requestId,
                    sequence,
                }),
                requestId,
                sequence,
            );
            sequence += 1;
            chunkIndex += 1;
        }
        expectAck(
            await dispatch({
                chunkCount: chunkIndex,
                fileIndex,
                kind: "projectInputFileEnd",
                requestId,
                sequence,
            }),
            requestId,
            sequence,
        );
        sequence += 1;
        fileIndex += 1;
    }

    const endResponses = await dispatch({
        fileCount: authority.fileCount,
        kind: "projectInputEnd",
        requestId,
        sequence,
        totalBytes: authority.totalBytes,
    });
    const failure = endResponses.find(
        (message) => message.kind === "projectError" && message.requestId === requestId,
    );
    if (tamperInventory) {
        assert(
            failure && /Preloaded declaration layers do not match/.test(failure.message),
            `Tampered inventory was not refused: ${JSON.stringify(endResponses)}`,
        );
        return { extraMessages, framedFileCount: framed.length, refused: true };
    }
    assert(!failure, `Worker rejected the project at input end: ${failure?.message}`);
    const start = endResponses.find(
        (message) => message.kind === "projectResultStart" && message.requestId === requestId,
    );
    assert(start, "Worker did not begin the framed project result.");
    const chunks = [];
    for (let index = -1; index < start.chunkCount; index += 1) {
        const responses = await dispatch({ kind: "projectResultAck", requestId, sequence: index });
        const chunkFailure = responses.find(
            (message) => message.kind === "projectError" && message.requestId === requestId,
        );
        assert(!chunkFailure, `Worker result framing failed: ${chunkFailure?.message}`);
        if (index + 1 < start.chunkCount) {
            const chunk = responses.find((message) => message.kind === "projectResultChunk");
            assert(chunk && chunk.index === index + 1, "Worker result chunk is out of sequence.");
            chunks.push(Buffer.from(chunk.data, "base64"));
        } else {
            const end = responses.find((message) => message.kind === "projectResultEnd");
            assert(end && end.sha256 === start.sha256, "Worker did not end the framed result.");
        }
    }
    const resultBytes = Buffer.concat(chunks);
    assert(sha256Hex(resultBytes) === start.sha256, "Result digest mismatch.");
    const result = JSON.parse(resultBytes.toString("utf8"));
    assert(result.tsVersion === expectedVersion, "Result TypeScript version mismatch.");
    assert(
        !result.emitSkipped && result.diagnostics.length === 0,
        `Project did not compile cleanly: ${JSON.stringify(result.diagnostics)}`,
    );
    return { extraMessages, framedFileCount: framed.length, result };
};

(async () => {
    const loadDeclarationLayer = workerBinding("loadDeclarationLayer");
    const common = await loadDeclarationLayer("common");
    const rhino = await loadDeclarationLayer("rhino");
    assert(
        common && rhino && common.files.length === bundleManifest.bundles.common.fileCount &&
            rhino.files.length === bundleManifest.bundles.rhino.fileCount,
        "Worker did not verify the common and Rhino declaration bundles.",
    );
    assert(
        common.fingerprint === bundleManifest.bundles.common.layerFingerprint &&
            rhino.fingerprint === bundleManifest.bundles.rhino.layerFingerprint,
        "Verified layer fingerprints drifted from the bundle manifest.",
    );
    const declarationFiles = common.files.concat(rhino.files);

    // Startup-latency L2-7: let the post-ready warm-up finish, then prove that the first
    // preloaded Program reuses every warmed SourceFile instead of parsing the layers again.
    const warmUp = workerBinding("declarationWarmUp");
    const warmUpDeadline = Date.now() + 120_000;
    while (warmUp.state !== "completed") {
        assert(
            warmUp.state !== "failed" && warmUp.state !== "unavailable",
            `Worker warm-up ended in state ${warmUp.state}.`,
        );
        assert(Date.now() < warmUpDeadline, "Worker warm-up did not complete.");
        await new Promise((resolve) => setTimeout(resolve, 20));
    }
    assert(
        warmUp.parsedFiles > 0 &&
            warmUp.queuedFiles === 0 &&
            warmUp.steps > 0 &&
            warmUp.bundles.has("common") &&
            warmUp.bundles.has("rhino") &&
            warmUp.bundles.get("common").fileCount === bundleManifest.bundles.common.fileCount,
        `Worker warm-up telemetry drifted: ${JSON.stringify({ ...warmUp, bundles: [...warmUp.bundles] })}`,
    );
    const sharedDeclarationSourceFiles = workerBinding("sharedDeclarationSourceFiles");
    const sharedDeclarationSourceFileKey = workerBinding("sharedDeclarationSourceFileKey");
    const warmedSourceFiles = new Map(sharedDeclarationSourceFiles);
    assert(
        warmedSourceFiles.size === warmUp.parsedFiles &&
            warmedSourceFiles.has(
                sharedDeclarationSourceFileKey("/declarations/lib.es2018.d.ts", 5),
            ),
        "Worker warm-up did not populate the shared declaration SourceFile cache.",
    );

    const preloaded = await compileProject("preload-1", declarationFiles, {
        frameDeclarations: false,
    });
    assert(preloaded.framedFileCount === 1, "Preloaded request framed more than the caller file.");
    const warmUpReports = preloaded.extraMessages.filter(
        (message) => message.kind === "declarationWarmUp",
    );
    assert(
        warmUpReports.length === 1 &&
            warmUpReports[0].state === "completed" &&
            warmUpReports[0].parsedFiles === warmUp.parsedFiles &&
            warmUpReports[0].queuedFiles === 0 &&
            warmUpReports[0].completedAtMs >= 0 &&
            warmUpReports[0].commonVerifyMs >= 0 &&
            warmUpReports[0].rhinoTokenMs >= 0 &&
            Object.values(warmUpReports[0]).every(
                (value) => typeof value === "string" || Number.isSafeInteger(value),
            ),
        `First project did not report the warm-up once: ${JSON.stringify(warmUpReports)}`,
    );
    for (const [key, sourceFile] of warmedSourceFiles) {
        assert(
            sharedDeclarationSourceFiles.get(key) === sourceFile,
            `First preloaded Program replaced the warmed SourceFile ${key.split("\u0000")[0]}.`,
        );
    }
    assert(
        sharedDeclarationSourceFiles.size === warmedSourceFiles.size,
        "First preloaded Program parsed declarations the warm-up had already parsed: " +
            `${sharedDeclarationSourceFiles.size - warmedSourceFiles.size} new shared entries.`,
    );
    const mainOutput = preloaded.result.outputs.find((output) => output.path === "/outputs/main.js");
    assert(
        mainOutput && mainOutput.text.includes("device.sdkInt"),
        "Preloaded project did not emit the Rhino-typed source.",
    );
    assert(
        preloaded.result.outputs.some((output) => output.path === "/outputs/main.js.map"),
        "Preloaded project did not emit its source map.",
    );

    const framed = await compileProject("framed-1", declarationFiles, {
        frameDeclarations: true,
    });
    assert(
        framed.framedFileCount === declarationFiles.length + 1,
        "Framed request did not transport every declaration.",
    );
    const normalize = (outputs) =>
        JSON.stringify(
            outputs
                .map((output) => ({ path: output.path, sha256: output.sha256, text: output.text }))
                .sort((left, right) => (left.path < right.path ? -1 : 1)),
        );
    assert(
        normalize(preloaded.result.outputs) === normalize(framed.result.outputs),
        "Preloaded and framed declaration transports produced different outputs.",
    );

    const tampered = await compileProject("tamper-1", declarationFiles, {
        frameDeclarations: false,
        tamperInventory: true,
    });
    assert(tampered.refused, "Tampered declaration inventory was accepted.");

    const second = await compileProject("preload-2", declarationFiles, {
        frameDeclarations: false,
    });
    assert(
        normalize(second.result.outputs) === normalize(preloaded.result.outputs),
        "Second preloaded compilation diverged from the first.",
    );
    assert(
        !second.extraMessages.some((message) => message.kind === "declarationWarmUp"),
        "Worker reported its warm-up more than once.",
    );
    const shared = workerBinding("sharedDeclarationSourceFiles");
    assert(
        shared.size > 0,
        "Worker did not retain any shared declaration SourceFile across jobs.",
    );
    assert(
        importedScripts.includes("declarations-common.js") &&
            importedScripts.includes("declarations-rhino.js") &&
            importedScripts.filter((name) => name === "declarations-common.js").length === 1,
        `Worker bundle imports drifted: ${importedScripts.join(",")}`,
    );
    process.stdout.write(
        "L2-1 declaration preload verified: " +
            `${declarationFiles.length} preloaded declarations, framed parity, ` +
            `tamper refused, ${shared.size} shared SourceFiles, ` +
            `warm-up ${warmUp.parsedFiles} files reused by the first Program\n`,
    );
    process.exit(0);
})().catch((error) => {
    process.stderr.write(`${error instanceof Error ? error.stack : String(error)}\n`);
    process.exit(1);
});
