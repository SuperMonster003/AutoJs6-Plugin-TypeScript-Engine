"use strict";

// The parser page (TypeScript parser contract v1). Like the compiler page it never loads
// TypeScript itself: it relays framed requests from the native host to one dedicated parser
// Worker and frames the Worker's JSON tree back, so no single port message grows unbounded.
(() => {
    const nativeMessage = "__autojs_typescript_parser_port_v1__";
    const maximumFrameChars = 16 * 1024;
    const maximumInboundChunks = 64;
    let nativePort = null;
    let worker = null;
    let workerReady = false;
    let tsVersion = null;
    const inbound = new Map();

    const isRecord = (value) =>
        value !== null && typeof value === "object" && !Array.isArray(value);

    const isRequestId = (value) =>
        typeof value === "string" && value.length > 0 && value.length <= 128;

    const boundedError = (error, fallback) => {
        const message = error instanceof Error ? error.message : String(error || fallback);
        return (message || fallback).slice(0, 4096);
    };

    const publish = (message) => {
        if (nativePort === null) {
            return false;
        }
        try {
            nativePort.postMessage(JSON.stringify(message));
            return true;
        } catch (_) {
            return false;
        }
    };

    const publishError = (requestId, code, message) =>
        publish({ kind: "parseError", requestId, code, message: boundedError(message, "Parse failed.") });

    const relayResult = (message) => {
        const tree = message.tree;
        if (typeof tree !== "string" || !isRequestId(message.requestId)) {
            return;
        }
        // Frames never end between the two halves of a surrogate pair: each one crosses the port alone.
        const chunks = [];
        for (let start = 0; start < tree.length || chunks.length === 0;) {
            let end = Math.min(tree.length, start + maximumFrameChars);
            const last = tree.charCodeAt(end - 1);
            if (end < tree.length && last >= 0xd800 && last <= 0xdbff) {
                end -= 1;
            }
            chunks.push(tree.slice(start, end));
            start = end;
        }
        const started = publish({
            kind: "parseResultStart",
            requestId: message.requestId,
            chunkCount: chunks.length,
            treeChars: tree.length,
            nodeCount: message.nodeCount,
            diagnosticCount: message.diagnosticCount,
            elapsedMs: message.elapsedMs,
        });
        if (!started) {
            return;
        }
        for (let index = 0; index < chunks.length; index += 1) {
            if (!publish({ kind: "parseResultChunk", requestId: message.requestId, index, data: chunks[index] })) {
                return;
            }
        }
        publish({ kind: "parseResultEnd", requestId: message.requestId });
    };

    const spawnWorker = () => {
        if (worker !== null) {
            worker.terminate();
        }
        worker = null;
        workerReady = false;
        let created;
        try {
            created = new Worker("parser-worker.js");
        } catch (error) {
            publish({ kind: "workerFailed", message: boundedError(error, "Parser worker creation failed.") });
            return;
        }
        worker = created;
        created.onmessage = (event) => {
            if (worker !== created) {
                return;
            }
            const message = event.data;
            if (!isRecord(message) || typeof message.kind !== "string") {
                return;
            }
            switch (message.kind) {
                case "ready":
                    workerReady = true;
                    tsVersion = typeof message.tsVersion === "string" ? message.tsVersion : null;
                    publish({ kind: "ready", tsVersion });
                    return;
                case "parseResult":
                    relayResult(message);
                    return;
                case "parseError":
                    if (isRequestId(message.requestId)) {
                        publishError(message.requestId, String(message.code), message.message);
                    }
                    return;
                default:
                    return;
            }
        };
        created.onerror = (event) => {
            event.preventDefault();
            if (worker !== created) {
                return;
            }
            publish({ kind: "workerFailed", message: boundedError(event.message, "Parser worker failed.") });
        };
    };

    const handleNativeMessage = (event) => {
        let message;
        try {
            message = JSON.parse(event.data);
        } catch (_) {
            return;
        }
        if (!isRecord(message) || typeof message.kind !== "string") {
            return;
        }
        switch (message.kind) {
            case "parseStart": {
                const valid =
                    isRequestId(message.requestId) &&
                    typeof message.fileName === "string" &&
                    Number.isInteger(message.chunkCount) &&
                    message.chunkCount >= 1 &&
                    message.chunkCount <= maximumInboundChunks &&
                    isRecord(message.limits) &&
                    !inbound.has(message.requestId);
                if (!valid) {
                    if (isRequestId(message.requestId)) {
                        publishError(message.requestId, "ERR_AUTOJS6_TYPESCRIPT_PARSER_INVALID_REQUEST", "Malformed parse request.");
                    }
                    return;
                }
                inbound.set(message.requestId, {
                    fileName: message.fileName,
                    limits: message.limits,
                    chunkCount: message.chunkCount,
                    parts: [],
                });
                return;
            }
            case "parseChunk": {
                const entry = inbound.get(message.requestId);
                if (entry === undefined) {
                    return;
                }
                if (message.index !== entry.parts.length || typeof message.data !== "string") {
                    inbound.delete(message.requestId);
                    publishError(message.requestId, "ERR_AUTOJS6_TYPESCRIPT_PARSER_INVALID_REQUEST", "Parse request frames arrived out of order.");
                    return;
                }
                entry.parts.push(message.data);
                return;
            }
            case "parseEnd": {
                const entry = inbound.get(message.requestId);
                if (entry === undefined) {
                    return;
                }
                inbound.delete(message.requestId);
                if (entry.parts.length !== entry.chunkCount || worker === null || !workerReady) {
                    publishError(message.requestId, "ERR_AUTOJS6_TYPESCRIPT_PARSER_UNAVAILABLE", "The parser is not ready.");
                    return;
                }
                worker.postMessage({
                    kind: "parse",
                    requestId: message.requestId,
                    fileName: entry.fileName,
                    sourceText: entry.parts.join(""),
                    limits: entry.limits,
                });
                return;
            }
            case "restartWorker":
                inbound.clear();
                spawnWorker();
                return;
            case "close":
                inbound.clear();
                if (worker !== null) {
                    worker.terminate();
                    worker = null;
                }
                if (nativePort !== null) {
                    nativePort.onmessage = null;
                    nativePort.close();
                    nativePort = null;
                }
                return;
            default:
                return;
        }
    };

    // As on the compiler page: the native host transfers this port once, to the exact origin.
    self.addEventListener("message", (event) => {
        if (nativePort !== null || event.data !== nativeMessage || event.ports.length !== 1) {
            return;
        }
        nativePort = event.ports[0];
        nativePort.onmessage = handleNativeMessage;
        nativePort.start();
        spawnWorker();
    });
})();
