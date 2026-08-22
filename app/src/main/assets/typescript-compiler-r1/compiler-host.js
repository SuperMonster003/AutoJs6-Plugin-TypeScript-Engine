"use strict";

(() => {
    const nativeMessage = "__autojs_typescript_compiler_port_v1__";
    const maximumJsonBytes = 128 * 1024;
    // A maximum legal C5 inventory can make the exact Node revision-2 start
    // envelope larger than an ordinary frame. The classifier below is the
    // only path allowed to consume this separate native-to-page budget.
    const maximumProjectInputStartJsonBytes = 576 * 1024;
    const nodeRevisionTwoPackageFormatStartKeys = Object.freeze([
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
    ]);
    const nodeRevisionTwoProgramPackageFormatStartKeys = Object.freeze(
        nodeRevisionTwoPackageFormatStartKeys.concat([
            "resolverPolicyRevision",
            "snapshotTargetInventoryFingerprint",
            "targetUniverseFingerprint",
        ]),
    );
    const nodeRevisionTwoProgramPackageTargetStartKeys = Object.freeze(
        nodeRevisionTwoProgramPackageFormatStartKeys.concat([
            "nodePackageTargetByteLength",
            "nodePackageTargetCanonical",
            "nodePackageTargetFileCount",
            "nodePackageTargetFingerprint",
            "nodePackageTargetPolicyFingerprint",
            "nodePackageTargetPolicyRevision",
            "nodePackageTargetSchemaRevision",
        ]),
    );
    const maximumProjectExecutionProgressUnits = 2 * 4_607;
    const projectExecutionPhases = Object.freeze([
        "ingress",
        "parse",
        "semantic",
        "emit",
    ]);
    const controlledConfigPolicyCodes = new Set([
        "CONFIG_COMPLEXITY_EXCEEDED",
        "CONFIG_DUPLICATE_KEY",
        "CONFIG_ENCODING_INVALID",
        "CONFIG_ENTRY_NOT_SELECTED",
        "CONFIG_INVALID_VALUE",
        "CONFIG_NO_INPUTS",
        "CONFIG_PATH_INVALID",
        "CONFIG_PROFILE_CONFLICT",
        "CONFIG_SYNTAX_INVALID",
        "CONFIG_UNKNOWN_PROPERTY",
        "CONFIG_UNSUPPORTED_FEATURE",
    ]);
    let generation = 0;
    let nativePort = null;
    let ready = false;
    let worker = null;
    let activeProject = null;

    const boundedError = (error, fallback) => {
        const message = error instanceof Error
            ? error.message
            : String(error || fallback);
        return (message || fallback).slice(0, 4096);
    };

    const isRecord = (value) =>
        value !== null && typeof value === "object" && !Array.isArray(value);

    const isRequestId = (value) =>
        typeof value === "string" && value.length > 0 && value.length <= 128;

    const hasExactKeys = (value, expectedKeys) =>
        Object.keys(value).sort().join("\n") ===
        expectedKeys.slice().sort().join("\n");

    const jsonByteLength = (value) =>
        new TextEncoder().encode(value).byteLength;

    const publish = (message, messageGeneration = generation) => {
        if (nativePort === null) {
            return false;
        }
        let encoded;
        try {
            encoded = JSON.stringify(Object.assign({}, message, {
                generation: messageGeneration,
                pageHasTypeScript: typeof self.ts !== "undefined",
            }));
        } catch (_) {
            return false;
        }
        if (
            encoded.length > maximumJsonBytes ||
            jsonByteLength(encoded) > maximumJsonBytes
        ) {
            return false;
        }
        try {
            nativePort.postMessage(encoded);
            return true;
        } catch (_) {
            return false;
        }
    };

    const publishProjectError = (requestId, message, messageGeneration) =>
        publish({
            kind: "projectError",
            message: boundedError(message, "Project transport failed."),
            requestId,
        }, messageGeneration);

    const spawnReplacementWorker = (termination = null) => {
        if (worker !== null) {
            worker.terminate();
        }
        worker = null;
        ready = false;
        generation += 1;
        const createdGeneration = generation;

        let createdWorker;
        try {
            createdWorker = new Worker("compiler-worker.js");
        } catch (error) {
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
            if (!isRecord(message) || typeof message.kind !== "string") {
                const requestId = activeProject?.requestId;
                publish({
                    kind: "workerError",
                    message: "Compiler worker returned a non-object message.",
                    ...(requestId === undefined ? {} : { requestId }),
                }, createdGeneration);
                return;
            }

            switch (message.kind) {
                case "ready":
                    ready = true;
                    if (termination !== null) {
                        const acknowledged = publish({
                            kind: "workerTerminated",
                            phaseAtTermination: termination.phaseAtTermination,
                            phaseProgressCompletedUnitsAtTermination:
                                termination.phaseProgressCompletedUnitsAtTermination,
                            phaseProgressSequenceAtTermination:
                                termination.phaseProgressSequenceAtTermination,
                            phaseProgressTotalUnitsAtTermination:
                                termination.phaseProgressTotalUnitsAtTermination,
                            replacementGeneration: createdGeneration,
                            requestId: termination.requestId,
                            terminatedGeneration: termination.terminatedGeneration,
                        }, createdGeneration);
                        if (!acknowledged) {
                            return;
                        }
                    }
                    publish(message, createdGeneration);
                    return;
                case "probeResult":
                case "probeError":
                    if (activeProject === null) {
                        publish(message, createdGeneration);
                    }
                    return;
                case "projectInputAck":
                    handleWorkerInputAck(message, createdGeneration);
                    return;
                case "projectConfigError":
                    handleWorkerProjectConfigError(message, createdGeneration);
                    return;
                case "projectPhase":
                    handleWorkerProjectPhase(message, createdGeneration);
                    return;
                case "projectPhaseProgress":
                    handleWorkerProjectPhaseProgress(message, createdGeneration);
                    return;
                case "projectResultStart":
                    handleWorkerResultStart(message, createdGeneration);
                    return;
                case "projectResultChunk":
                    handleWorkerResultChunk(message, createdGeneration);
                    return;
                case "projectResultEnd":
                    handleWorkerResultEnd(message, createdGeneration);
                    return;
                case "projectError":
                    if (
                        activeProject !== null &&
                        activeProject.generation === createdGeneration &&
                        message.requestId === activeProject.requestId
                    ) {
                        activeProject = null;
                        publish(message, createdGeneration);
                    }
                    return;
                default:
                    if (activeProject !== null) {
                        failActiveProjectAndReplaceWorker(
                            "Compiler Worker returned an unsupported message kind.",
                        );
                    } else {
                        publish({
                            kind: "workerError",
                            message: "Compiler Worker returned an unsupported message kind.",
                        }, createdGeneration);
                        spawnReplacementWorker();
                    }
            }
        };

        createdWorker.onerror = (event) => {
            event.preventDefault();
            if (worker !== createdWorker || generation !== createdGeneration) {
                return;
            }
            ready = false;
            const requestId = activeProject?.requestId;
            publish({
                kind: "workerError",
                message: boundedError(
                    event.message,
                    "Compiler worker failed.",
                ),
                ...(requestId === undefined ? {} : { requestId }),
            }, createdGeneration);
        };

        // A keyed stop is acknowledged from the replacement's exact `ready`
        // event above, after construction, script loading, TypeScript startup
        // and all event fences have succeeded. Constructor/load failure leaves
        // native holding the old operation until exact-session teardown.

        createdWorker.onmessageerror = () => {
            if (worker !== createdWorker || generation !== createdGeneration) {
                return;
            }
            ready = false;
            const requestId = activeProject?.requestId;
            publish({
                kind: "workerError",
                message: "Compiler worker returned an unreadable message.",
                ...(requestId === undefined ? {} : { requestId }),
            }, createdGeneration);
        };
    };

    const failActiveProjectAndReplaceWorker = (message) => {
        const failed = activeProject;
        if (failed === null) {
            return;
        }
        activeProject = null;
        publishProjectError(failed.requestId, message, failed.generation);
        spawnReplacementWorker();
    };

    const handleWorkerInputAck = (message, messageGeneration) => {
        const active = activeProject;
        if (active === null || active.generation !== messageGeneration) {
            return;
        }
        if (
            active.phase !== "input" ||
            message.requestId !== active.requestId ||
            !Number.isSafeInteger(message.sequence) ||
            message.sequence !== active.awaitingInputAck
        ) {
            failActiveProjectAndReplaceWorker(
                "Compiler Worker input acknowledgement is out of sequence.",
            );
            return;
        }
        active.awaitingInputAck = null;
        active.nextInputSequence = message.sequence + 1;
        if (active.lastInputKind === "projectInputEnd") {
            active.phase = "compiling";
        }
        active.lastInputKind = null;
        if (!publish(message, messageGeneration)) {
            failActiveProjectAndReplaceWorker(
                "Project input acknowledgement exceeded the page message limit.",
            );
        }
    };

    const handleWorkerProjectConfigError = (message, messageGeneration) => {
        const active = activeProject;
        if (
            !hasExactKeys(message, [
                "emittedModuleCount",
                "errorCode",
                "kind",
                "message",
                "outcome",
                "policyCode",
                "requestId",
                "stage",
            ]) ||
            active === null ||
            active.generation !== messageGeneration ||
            active.phase !== "input" ||
            active.lastInputKind !== "projectInputConfigEnd" ||
            !Number.isSafeInteger(active.awaitingInputAck) ||
            message.requestId !== active.requestId ||
            message.stage !== "config" ||
            message.outcome !== "diagnostics" ||
            message.errorCode !==
                "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_DIAGNOSTICS" ||
            message.emittedModuleCount !== 0 ||
            !controlledConfigPolicyCodes.has(message.policyCode) ||
            typeof message.message !== "string" ||
            message.message.length > 4096
        ) {
            failActiveProjectAndReplaceWorker(
                "Compiler Worker returned an invalid controlled-config error.",
            );
            return;
        }
        activeProject = null;
        publish(message, messageGeneration);
    };

    const handleWorkerProjectPhase = (message, messageGeneration) => {
        const active = activeProject;
        const expectedPhase =
            active === null
                ? undefined
                : projectExecutionPhases[active.nextExecutionPhaseIndex];
        const isResultEmitCompletion =
            message.phase === "emit" &&
            message.state === "completed" &&
            active?.phase === "result";
        const expectedTransportPhase =
            message.phase === "ingress"
                ? "input"
                : isResultEmitCompletion
                  ? "result"
                  : "compiling";
        if (
            active === null ||
            active.generation !== messageGeneration ||
            active.phase !== expectedTransportPhase ||
            message.requestId !== active.requestId ||
            message.phase !== expectedPhase ||
            (
                isResultEmitCompletion &&
                (
                    active.resultAwaitingNativeAck !== null ||
                    active.resultNextIndex !== active.resultChunkCount
                )
            ) ||
            (
                message.state === "started"
                    ? active.activeExecutionPhase !== null
                    : message.state === "completed"
                      ? active.activeExecutionPhase !== message.phase
                      : true
            )
        ) {
            failActiveProjectAndReplaceWorker(
                "Compiler Worker execution phase is out of sequence.",
            );
            return;
        }
        if (message.state === "started") {
            active.activeExecutionPhase = message.phase;
            active.phaseProgressCompletedUnits = 0;
            active.phaseProgressSequence = 0;
            active.phaseProgressTotalUnits = 0;
        } else {
            active.activeExecutionPhase = null;
            active.phaseProgressCompletedUnits = 0;
            active.phaseProgressSequence = 0;
            active.phaseProgressTotalUnits = 0;
            active.nextExecutionPhaseIndex += 1;
        }
        if (!publish(message, messageGeneration)) {
            failActiveProjectAndReplaceWorker(
                "Project execution phase exceeded the page message limit.",
            );
        }
    };

    const handleWorkerProjectPhaseProgress = (message, messageGeneration) => {
        const active = activeProject;
        const expectedSequence =
            active === null ? undefined : active.phaseProgressSequence + 1;
        const divisor = expectedSequence === 1 ? 8 : 4;
        if (
            !hasExactKeys(message, [
                "completedUnits",
                "kind",
                "phase",
                "requestId",
                "sequence",
                "totalUnits",
            ]) ||
            active === null ||
            active.generation !== messageGeneration ||
            active.phase !== "compiling" ||
            active.activeExecutionPhase !== message.phase ||
            message.phase === "ingress" ||
            message.requestId !== active.requestId ||
            !Number.isSafeInteger(message.sequence) ||
            message.sequence !== expectedSequence ||
            message.sequence < 1 ||
            message.sequence > 2 ||
            !Number.isSafeInteger(message.completedUnits) ||
            !Number.isSafeInteger(message.totalUnits) ||
            message.totalUnits < 8 ||
            message.totalUnits > maximumProjectExecutionProgressUnits ||
            message.completedUnits !== Math.ceil(message.totalUnits / divisor) ||
            message.completedUnits >= message.totalUnits ||
            (
                active.phaseProgressSequence === 0
                    ? active.phaseProgressTotalUnits !== 0
                    : active.phaseProgressTotalUnits !== message.totalUnits
            )
        ) {
            failActiveProjectAndReplaceWorker(
                "Compiler Worker execution progress is unexpected or out of sequence.",
            );
            return;
        }
        active.phaseProgressCompletedUnits = message.completedUnits;
        active.phaseProgressSequence = message.sequence;
        active.phaseProgressTotalUnits = message.totalUnits;
        if (!publish(message, messageGeneration)) {
            failActiveProjectAndReplaceWorker(
                "Project execution progress exceeded the page message limit.",
            );
        }
    };

    const handleWorkerResultStart = (message, messageGeneration) => {
        const active = activeProject;
        if (
            active === null ||
            active.generation !== messageGeneration ||
            active.phase !== "compiling" ||
            active.activeExecutionPhase !== "emit" ||
            projectExecutionPhases[active.nextExecutionPhaseIndex] !== "emit" ||
            message.requestId !== active.requestId ||
            !Number.isSafeInteger(message.byteLength) ||
            message.byteLength < 0 ||
            typeof message.sha256 !== "string" ||
            !/^[0-9a-f]{64}$/.test(message.sha256) ||
            !Number.isSafeInteger(message.chunkCount) ||
            message.chunkCount !== Math.ceil(message.byteLength / (64 * 1024))
        ) {
            failActiveProjectAndReplaceWorker(
                "Compiler worker returned an invalid result start frame.",
            );
            return;
        }
        active.phase = "result";
        active.resultByteLength = message.byteLength;
        active.resultChunkCount = message.chunkCount;
        active.resultNextIndex = 0;
        active.resultSha256 = message.sha256;
        active.resultAwaitingNativeAck = -1;
        if (!publish(message, messageGeneration)) {
            failActiveProjectAndReplaceWorker(
                "Project result start exceeded the page message limit.",
            );
        }
    };

    const handleWorkerResultChunk = (message, messageGeneration) => {
        const active = activeProject;
        if (
            active === null ||
            active.generation !== messageGeneration ||
            active.phase !== "result" ||
            message.requestId !== active.requestId ||
            active.activeExecutionPhase !== "emit" ||
            projectExecutionPhases[active.nextExecutionPhaseIndex] !== "emit" ||
            active.resultAwaitingNativeAck !== null ||
            !Number.isSafeInteger(message.index) ||
            message.index !== active.resultNextIndex ||
            message.index >= active.resultChunkCount ||
            typeof message.data !== "string"
        ) {
            failActiveProjectAndReplaceWorker(
                "Compiler worker returned an out-of-sequence result chunk.",
            );
            return;
        }
        active.resultNextIndex += 1;
        active.resultAwaitingNativeAck = message.index;
        if (!publish(message, messageGeneration)) {
            failActiveProjectAndReplaceWorker(
                "Project result chunk exceeded the page message limit.",
            );
        }
    };

    const handleWorkerResultEnd = (message, messageGeneration) => {
        const active = activeProject;
        if (
            active === null ||
            active.generation !== messageGeneration ||
            active.phase !== "result" ||
            message.requestId !== active.requestId ||
            active.activeExecutionPhase !== null ||
            active.nextExecutionPhaseIndex !== projectExecutionPhases.length ||
            active.resultAwaitingNativeAck !== null ||
            active.resultNextIndex !== active.resultChunkCount ||
            message.byteLength !== active.resultByteLength ||
            message.sha256 !== active.resultSha256 ||
            message.chunkCount !== active.resultChunkCount
        ) {
            failActiveProjectAndReplaceWorker(
                "Compiler worker returned an invalid result end frame.",
            );
            return;
        }
        activeProject = null;
        if (!publish(message, messageGeneration)) {
            publishProjectError(
                message.requestId,
                "Project result end exceeded the page message limit.",
                messageGeneration,
            );
        }
    };

    const forwardProjectInputFrame = (message) => {
        if (!isRequestId(message.requestId)) {
            return;
        }
        if (message.kind === "projectInputStart") {
            if (activeProject !== null) {
                if (message.requestId !== activeProject.requestId) {
                    publishProjectError(
                        message.requestId,
                        "Compiler page already has an active project.",
                        generation,
                    );
                } else {
                    failActiveProjectAndReplaceWorker(
                        "Project input start was repeated.",
                    );
                }
                return;
            }
            if (
                !ready ||
                worker === null ||
                message.sequence !== 0
            ) {
                publishProjectError(
                    message.requestId,
                    "Compiler Worker is not ready for project input.",
                    generation,
                );
                return;
            }
            activeProject = {
                awaitingInputAck: null,
                activeExecutionPhase: null,
                generation,
                lastInputKind: null,
                nextExecutionPhaseIndex: 0,
                nextInputSequence: 0,
                phase: "input",
                phaseProgressCompletedUnits: 0,
                phaseProgressSequence: 0,
                phaseProgressTotalUnits: 0,
                requestId: message.requestId,
            };
        }

        const active = activeProject;
        if (
            active === null ||
            message.requestId !== active.requestId
        ) {
            return;
        }
        if (
            active.generation !== generation ||
            active.phase !== "input" ||
            active.awaitingInputAck !== null ||
            !Number.isSafeInteger(message.sequence) ||
            message.sequence !== active.nextInputSequence ||
            worker === null
        ) {
            failActiveProjectAndReplaceWorker(
                "Project input violated stop-and-wait framing.",
            );
            return;
        }
        active.awaitingInputAck = message.sequence;
        active.lastInputKind = message.kind;
        try {
            worker.postMessage(message);
        } catch (error) {
            failActiveProjectAndReplaceWorker(
                boundedError(error, "Project input could not reach the Worker."),
            );
        }
    };

    const forwardProjectResultAck = (message) => {
        const active = activeProject;
        if (
            active === null ||
            message.requestId !== active.requestId
        ) {
            return;
        }
        if (
            active.phase !== "result" ||
            active.generation !== generation ||
            message.expectedGeneration !== generation ||
            active.activeExecutionPhase !== "emit" ||
            projectExecutionPhases[active.nextExecutionPhaseIndex] !== "emit" ||
            !Number.isSafeInteger(message.sequence) ||
            message.sequence !== active.resultAwaitingNativeAck ||
            worker === null
        ) {
            failActiveProjectAndReplaceWorker(
                "Project result acknowledgement is out of sequence.",
            );
            return;
        }
        active.resultAwaitingNativeAck = null;
        try {
            worker.postMessage({
                kind: "projectResultAck",
                requestId: message.requestId,
                sequence: message.sequence,
            });
        } catch (error) {
            failActiveProjectAndReplaceWorker(
                boundedError(error, "Project result ACK could not reach the Worker."),
            );
        }
    };

    const handleNativeMessage = (event) => {
        if (
            typeof event.data !== "string" ||
            event.data.length > maximumProjectInputStartJsonBytes
        ) {
            if (activeProject !== null) {
                failActiveProjectAndReplaceWorker(
                    "Native compiler message exceeded the page limit.",
                );
            }
            return;
        }
        const encodedByteLength = jsonByteLength(event.data);
        if (encodedByteLength > maximumProjectInputStartJsonBytes) {
            if (activeProject !== null) {
                failActiveProjectAndReplaceWorker(
                    "Native compiler message exceeded the page limit.",
                );
            }
            return;
        }
        const exceedsOrdinaryJsonLimit =
            event.data.length > maximumJsonBytes ||
            encodedByteLength > maximumJsonBytes;
        let message;
        try {
            message = JSON.parse(event.data);
        } catch (_) {
            if (exceedsOrdinaryJsonLimit && activeProject !== null) {
                failActiveProjectAndReplaceWorker(
                    "Native compiler message exceeded the page limit.",
                );
            }
            return;
        }
        if (!isRecord(message) || typeof message.kind !== "string") {
            if (exceedsOrdinaryJsonLimit && activeProject !== null) {
                failActiveProjectAndReplaceWorker(
                    "Native compiler message exceeded the page limit.",
                );
            }
            return;
        }
        const usesExtendedProjectInputStartEnvelope =
            (
                hasExactKeys(message, nodeRevisionTwoPackageFormatStartKeys) ||
                hasExactKeys(
                    message,
                    nodeRevisionTwoProgramPackageFormatStartKeys,
                ) ||
                hasExactKeys(
                    message,
                    nodeRevisionTwoProgramPackageTargetStartKeys,
                )
            ) &&
            message.kind === "projectInputStart" &&
            message.targetProfile === "node" &&
            message.profileRevision === 2 &&
            typeof message.packageMetadataCanonical === "string";
        if (
            exceedsOrdinaryJsonLimit &&
            !usesExtendedProjectInputStartEnvelope
        ) {
            if (activeProject !== null) {
                failActiveProjectAndReplaceWorker(
                    "Native compiler message exceeded the page limit.",
                );
            }
            return;
        }

        switch (message.kind) {
            case "close":
                activeProject = null;
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
                    activeProject === null &&
                    isRequestId(message.requestId) &&
                    isRecord(message.vfs)
                ) {
                    worker.postMessage(message);
                }
                break;
            case "projectInputStart":
            case "projectInputConfigStart":
            case "projectInputConfigChunk":
            case "projectInputConfigEnd":
            case "projectInputFileStart":
            case "projectInputFileChunk":
            case "projectInputFileEnd":
            case "projectInputEnd":
                forwardProjectInputFrame(message);
                break;
            case "projectResultAck":
                forwardProjectResultAck(message);
                break;
            case "terminateWorker":
                if (
                    activeProject !== null &&
                    isRequestId(message.requestId) &&
                    message.requestId === activeProject.requestId &&
                    Number.isSafeInteger(message.expectedGeneration) &&
                    message.expectedGeneration === generation &&
                    activeProject.generation === generation
                ) {
                    // This receipt freezes the last FIFO Worker state observed by
                    // the page before this exact terminate() call. It does not
                    // claim an instruction-level position inside TypeScript.
                    const hasNaturalPhaseReceipt =
                        activeProject.activeExecutionPhase !== null &&
                        activeProject.activeExecutionPhase !== "ingress" &&
                        activeProject.phaseProgressSequence === 2;
                    const terminated = {
                        phaseAtTermination: hasNaturalPhaseReceipt
                            ? activeProject.activeExecutionPhase
                            : "none",
                        phaseProgressCompletedUnitsAtTermination:
                            hasNaturalPhaseReceipt
                                ? activeProject.phaseProgressCompletedUnits
                                : 0,
                        phaseProgressSequenceAtTermination:
                            hasNaturalPhaseReceipt
                                ? activeProject.phaseProgressSequence
                                : 0,
                        phaseProgressTotalUnitsAtTermination:
                            hasNaturalPhaseReceipt
                                ? activeProject.phaseProgressTotalUnits
                                : 0,
                        requestId: activeProject.requestId,
                        terminatedGeneration: generation,
                    };
                    activeProject = null;
                    spawnReplacementWorker(terminated);
                }
                break;
            case "restartWorker":
                // Frozen probe recovery remains unkeyed. It must never become
                // an alternate way to kill a keyed project operation.
                if (activeProject === null) {
                    spawnReplacementWorker();
                }
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
        spawnReplacementWorker();
    });
})();
