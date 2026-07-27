"use strict";

importScripts("typescript.js");

const flattenDiagnosticMessage = (messageText) =>
    ts.flattenDiagnosticMessageText(messageText, "\n");

const compilerOptions = Object.freeze({
    inlineSourceMap: false,
    inlineSources: false,
    module: ts.ModuleKind.CommonJS,
    newLine: ts.NewLineKind.LineFeed,
    sourceMap: true,
    target: ts.ScriptTarget.ES2018,
});

const transpile = (sourceText, sourceUri) =>
    ts.transpileModule(sourceText, {
        compilerOptions,
        fileName: sourceUri,
        reportDiagnostics: true,
    });

const sha256 = async (text) => {
    const bytes = new TextEncoder().encode(text);
    const digest = await crypto.subtle.digest("SHA-256", bytes);
    return Array.from(new Uint8Array(digest))
        .map((value) => value.toString(16).padStart(2, "0"))
        .join("");
};

const runProbe = async (request) => {
    // Probe sources are transpiled and the emitted text is parsed, never
    // evaluated. Executing caller-supplied source is outside this host.
    const enumResult = transpile(
        request.vfs.enumSourceText,
        request.vfs.enumSourceUri,
    );
    if ((enumResult.diagnostics || []).length !== 0) {
        throw new Error("Enum probe unexpectedly produced diagnostics.");
    }
    const emittedJavaScript = ts.createSourceFile(
        "enum.js",
        enumResult.outputText,
        ts.ScriptTarget.ES2018,
        true,
        ts.ScriptKind.JS,
    );
    const emittedParseDiagnostics = emittedJavaScript.parseDiagnostics || [];

    const syntaxResult = transpile(
        request.vfs.syntaxSourceText,
        request.vfs.syntaxSourceUri,
    );
    const diagnostic = (syntaxResult.diagnostics || []).find(
        (item) => item.file && typeof item.start === "number",
    );
    if (!diagnostic) {
        throw new Error("Syntax probe did not produce a source diagnostic.");
    }
    const location = diagnostic.file.getLineAndCharacterOfPosition(
        diagnostic.start,
    );

    const sourceMap = JSON.parse(enumResult.sourceMapText || "null");
    if (sourceMap === null || typeof sourceMap !== "object") {
        throw new Error("Enum probe did not produce a Source Map.");
    }

    return {
        diagnostic: {
            category: diagnostic.category,
            code: diagnostic.code,
            column: location.character + 1,
            length: diagnostic.length,
            line: location.line + 1,
            message: flattenDiagnosticMessage(diagnostic.messageText),
            sourceUri: diagnostic.file.fileName,
            start: diagnostic.start,
        },
        emittedJavaScript: {
            enumRuntimeAssignmentPresent: enumResult.outputText.includes(
                "self.__r1ProbeValue = Direction.Down;",
            ),
            parseDiagnosticCount: emittedParseDiagnostics.length,
            sha256: await sha256(enumResult.outputText),
        },
        scope: {
            documentAbsent: typeof globalThis.document === "undefined",
            nativeBridgeAbsent:
                typeof globalThis.AutoJsCompilerBridge === "undefined",
            windowAbsent: typeof globalThis.window === "undefined",
            workerGlobal: typeof WorkerGlobalScope !== "undefined",
        },
        sourceMap: {
            hasInlineSources: Object.prototype.hasOwnProperty.call(
                sourceMap,
                "sourcesContent",
            ),
            mappings: sourceMap.mappings,
            sha256: await sha256(enumResult.sourceMapText),
            version: sourceMap.version,
        },
        syntaxEmitSha256: await sha256(syntaxResult.outputText),
        syntaxMapSha256: await sha256(syntaxResult.sourceMapText),
        tsVersion: ts.version,
    };
};

self.onmessage = async (event) => {
    const request = event.data;
    if (
        request === null ||
        typeof request !== "object" ||
        request.kind !== "probe" ||
        typeof request.requestId !== "string" ||
        request.vfs === null ||
        typeof request.vfs !== "object"
    ) {
        return;
    }
    try {
        self.postMessage({
            kind: "probeResult",
            requestId: request.requestId,
            result: await runProbe(request),
        });
    } catch (error) {
        self.postMessage({
            kind: "probeError",
            message: error instanceof Error ? error.message : String(error),
            requestId: request.requestId,
        });
    }
};

self.postMessage({
    kind: "ready",
    tsVersion: ts.version,
});
