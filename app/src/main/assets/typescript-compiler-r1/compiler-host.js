"use strict";

(() => {
    const nativeMessage = "__autojs_typescript_compiler_port_v1__";
    let generation = 0;
    let nativePort = null;
    let ready = false;
    let worker = null;

    const boundedError = (error, fallback) => {
        const message = error instanceof Error
            ? error.message
            : String(error || fallback);
        return (message || fallback).slice(0, 4096);
    };

    const publish = (message, messageGeneration = generation) => {
        if (nativePort === null) {
            return;
        }
        nativePort.postMessage(JSON.stringify(Object.assign({}, message, {
            generation: messageGeneration,
            pageHasTypeScript: typeof self.ts !== "undefined",
        })));
    };

    const createWorker = () => {
        if (worker !== null) {
            worker.terminate();
        }
        generation += 1;
        const createdGeneration = generation;
        ready = false;
        let createdWorker;
        try {
            createdWorker = new Worker("compiler-worker.js");
        } catch (error) {
            worker = null;
            publish({
                kind: "workerError",
                message: boundedError(error, "Compiler worker creation failed."),
            }, createdGeneration);
            return;
        }
        worker = createdWorker;
        createdWorker.onmessage = (event) => {
            if (worker !== createdWorker || generation !== createdGeneration) {
                return;
            }
            const message = event.data;
            if (message === null || typeof message !== "object") {
                publish({
                    kind: "workerError",
                    message: "Compiler worker returned a non-object message.",
                }, createdGeneration);
                return;
            }
            if (message.kind === "ready") {
                ready = true;
            }
            publish(message, createdGeneration);
        };
        createdWorker.onerror = (event) => {
            event.preventDefault();
            if (worker !== createdWorker || generation !== createdGeneration) {
                return;
            }
            publish({
                kind: "workerError",
                message: boundedError(
                    event.message,
                    "Compiler worker failed.",
                ),
            }, createdGeneration);
        };
        createdWorker.onmessageerror = () => {
            if (worker !== createdWorker || generation !== createdGeneration) {
                return;
            }
            publish({
                kind: "workerError",
                message: "Compiler worker returned an unreadable message.",
            }, createdGeneration);
        };
    };

    const handleNativeMessage = (event) => {
        if (typeof event.data !== "string") {
            return;
        }
        let message;
        try {
            message = JSON.parse(event.data);
        } catch (_) {
            return;
        }
        if (message === null || typeof message !== "object") {
            return;
        }
        switch (message.kind) {
            case "close":
                if (worker !== null) {
                    worker.terminate();
                    worker = null;
                }
                ready = false;
                nativePort.onmessage = null;
                nativePort.close();
                nativePort = null;
                break;
            case "probe":
                if (
                    ready &&
                    worker !== null &&
                    typeof message.requestId === "string" &&
                    message.vfs !== null &&
                    typeof message.vfs === "object"
                ) {
                    worker.postMessage(message);
                }
                break;
            case "restartWorker":
                createWorker();
                break;
            default:
                break;
        }
    };

    // Android's native WebView.postWebMessage does not expose a stable
    // MessageEvent.origin across supported providers. The native host binds
    // delivery to the exact HTTPS target origin and transfers this port once.
    self.addEventListener("message", (event) => {
        if (
            nativePort !== null ||
            event.data !== nativeMessage ||
            event.ports.length !== 1
        ) {
            return;
        }
        nativePort = event.ports[0];
        nativePort.onmessage = handleNativeMessage;
        nativePort.start();
        createWorker();
    });
})();
