"use strict";

const cryptoModule = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const {
    CURRENT_ORDINARY_INPUT_QUOTA_REVISION,
    projectInputAuthority,
    record: inputRecord,
} = require("./r2-input-archive-authority.js");

const [
    workerArgument,
    compilerArgument,
    policyArgument,
    effectiveOptionsArgument,
    expectedVersion,
] = process.argv.slice(2);
if (
    process.argv.slice(2).length !== 5 ||
    !workerArgument ||
    !compilerArgument ||
    !policyArgument ||
    !effectiveOptionsArgument ||
    !expectedVersion
) {
    throw new Error(
        "Usage: node verify-t1-controlled-tsconfig-revision3.js " +
            "<worker.js> <typescript.js> <policy-v3.json> " +
            "<effective-options-v3.json> <expected-version>",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const policy = JSON.parse(fs.readFileSync(path.resolve(policyArgument), "utf8"));
const fixture = JSON.parse(
    fs.readFileSync(path.resolve(effectiveOptionsArgument), "utf8"),
);
const workerSource = fs.readFileSync(workerPath, "utf8");
const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;
const sorted = (values) => values.slice().sort(compareCodeUnits);
const exactJson = (actual, expected) =>
    JSON.stringify(actual) === JSON.stringify(expected);
const exactKeys = (value, expected) =>
    exactJson(sorted(Object.keys(value)), sorted(expected));
const sha256 = (value) =>
    cryptoModule.createHash("sha256").update(value).digest("hex");
const expectThrows = async (operation, validate, label) => {
    let failure = null;
    try {
        await operation();
    } catch (error) {
        failure = error;
    }
    assert(failure !== null, `${label} did not fail.`);
    validate(failure);
};

Object.defineProperty(global, "crypto", {
    configurable: true,
    value: cryptoModule.webcrypto,
});
global.ts = require(compilerPath);
global.importScripts = (specifier) => {
    assert(specifier === "typescript.js", "Worker imported an unexpected script.");
};
const messages = [];
const observedMessages = [];
global.self = {
    postMessage: (message) => {
        messages.push(message);
        observedMessages.push(message);
    },
};
vm.runInThisContext(workerSource, { filename: workerPath });
assert(
    messages.length === 1 &&
        messages[0].kind === "ready" &&
        messages[0].tsVersion === expectedVersion &&
        ts.version === expectedVersion,
    "Revision-3 verifier did not load the pinned TypeScript Worker.",
);
messages.length = 0;

const workerBinding = (expression) =>
    vm.runInThisContext(expression, {
        filename: `${workerPath}#t1-controlled-tsconfig-revision3-${expression}`,
    });
const dispatch = async (message) => {
    messages.length = 0;
    await self.onmessage({ data: message });
    return messages.slice();
};

const appendSet = (canonical, label, values) =>
    canonical + `|${label}=${sorted(values).join(",")}`;
const canonicalPolicyInput = () => {
    let canonical = "autojs6.typescript.controlled-tsconfig-policy.v3";
    canonical += `|revision=${policy.revision}`;
    canonical += `|configKind=${policy.configKind}`;
    canonical += `|configPath=${policy.configPath}`;
    canonical += `|grammar=${policy.grammar.name}`;
    canonical += `|bom=${policy.grammar.allowUtf8Bom}`;
    canonical += `|comments=${policy.grammar.allowComments}`;
    canonical += `|trailingComma=${policy.grammar.allowTrailingComma}`;
    canonical += `|duplicateKeysRejected=${policy.grammar.rejectDuplicateKeys}`;
    canonical += `|logicalPathDomain=${policy.rootSelection.logicalPathDomain}`;
    canonical += `|defaultInclude=${policy.rootSelection.defaultInclude}`;
    canonical += `|defaultExclude=${policy.rootSelection.defaultExclude.join(",")}`;
    canonical += `|filesAndInclude=${policy.rootSelection.filesAndInclude}`;
    canonical += `|excludeAffects=${policy.rootSelection.excludeAffects}`;
    canonical += `|jsonDepthCounting=${policy.budgetSemantics.jsonDepthCounting}`;
    canonical += `|jsonNodeCounting=${policy.budgetSemantics.jsonNodeCounting}`;
    canonical += `|selectorCounting=${policy.budgetSemantics.selectorCounting}`;
    canonical += `|selectorUtf8Measurement=${policy.budgetSemantics.selectorUtf8Measurement}`;
    canonical += `|maxBytes=${policy.budgets.maximumBytes}`;
    canonical += `|maxSelectors=${policy.budgets.maximumSelectors}`;
    canonical += `|maxSelectorUtf8Bytes=${policy.budgets.maximumSelectorUtf8Bytes}`;
    canonical += `|maxDepth=${policy.budgets.maximumJsonDepth}`;
    canonical += `|maxNodes=${policy.budgets.maximumJsonNodes}`;
    canonical = appendSet(canonical, "allowedTopLevel", policy.allowedTopLevel);
    canonical = appendSet(
        canonical,
        "unsupportedTopLevel",
        policy.unsupportedTopLevel,
    );
    canonical +=
        "|allowedBoolean=" +
        sorted(Object.keys(policy.allowedBooleanCompilerOptions))
            .map(
                (name) =>
                    `${name}:${policy.allowedBooleanCompilerOptions[name]}`,
            )
            .join(",");
    canonical = appendSet(
        canonical,
        "allowedValue",
        policy.allowedValueCompilerOptions,
    );
    canonical += `|rhinoTargets=${sorted(policy.profileTargets.rhino).join(",")}`;
    canonical += `|nodeTargets=${sorted(policy.profileTargets.node).join(",")}`;
    canonical += `|libValues=${policy.libValues}`;
    canonical = appendSet(
        canonical,
        "profileManaged",
        policy.profileManagedCompilerOptions,
    );
    canonical = appendSet(
        canonical,
        "escapeForbidden",
        policy.escapeForbiddenCompilerOptions,
    );
    canonical = appendSet(
        canonical,
        "outputForbidden",
        policy.outputForbiddenCompilerOptions,
    );
    canonical = appendSet(
        canonical,
        "customTransformers",
        policy.customTransformerCompilerOptions,
    );
    canonical += `|unknownTopLevelFallback=${policy.unknownTopLevelFallback}`;
    canonical += `|knownOptionFallback=${policy.knownOptionFallback}`;
    canonical +=
        `|ignoredTopLevelDiagnosticCode=` +
        policy.warningDiagnosticCodes.ignoredTopLevelProperty;
    canonical +=
        `|ignoredCompilerOptionDiagnosticCode=` +
        policy.warningDiagnosticCodes.ignoredCompilerOption;
    return appendSet(canonical, "policyCodes", policy.policyCodes);
};

const revision3Authority = workerBinding(
    "controlledTsConfigRevision3Authority",
);
const legacyAuthority = workerBinding("controlledTsConfigAuthority");
assert(
    policy.schema === "autojs6.typescript.controlled-tsconfig-policy" &&
        policy.revision === 3 &&
        policy.configKind === "tsconfig" &&
        policy.configPath === "tsconfig.json" &&
        Object.keys(policy.allowedBooleanCompilerOptions).length === 20 &&
        exactJson(sorted(policy.allowedValueCompilerOptions), ["lib", "target"]) &&
        exactJson(policy.profileTargets.rhino, ["ES2018"]) &&
        exactJson(policy.profileTargets.node, [
            "ES2018",
            "ES2019",
            "ES2020",
            "ES2021",
            "ES2022",
        ]) &&
        sha256(Buffer.from(canonicalPolicyInput(), "ascii")) === policy.fingerprint &&
        revision3Authority.configPolicyRevision === policy.revision &&
        revision3Authority.configPolicyFingerprint === policy.fingerprint &&
        fixture.schema ===
            "autojs6.typescript.controlled-tsconfig-effective-options" &&
        fixture.revision === 3 &&
        fixture.profiles.length === 2 &&
        fixture.rejected.length === 3,
    "Revision-3 policy/effective-options fixture authority drifted.",
);
assert(
    fixture.legacyCompatibility.policyRevision ===
        legacyAuthority.configPolicyRevision &&
        fixture.legacyCompatibility.policyFingerprint ===
            legacyAuthority.configPolicyFingerprint &&
        policy.fingerprint !== legacyAuthority.configPolicyFingerprint,
    "Revision 3 no longer retains a separately addressable revision-2 authority.",
);

const parseConfig = (source, targetProfile, authority = revision3Authority) =>
    workerBinding("parseControlledTsConfig")(
        source,
        authority,
        targetProfile,
    );
const resolveEffectiveOptions = (parsedConfig) =>
    workerBinding("resolveControlledTsConfigEffectiveOptions")(parsedConfig);
const assertDeepFrozenEffectiveOptions = (authority, label) => {
    assert(
        Object.isFrozen(authority) &&
            Object.isFrozen(authority.compilerOptions) &&
            Object.isFrozen(authority.compilerOptions.lib) &&
            Object.isFrozen(authority.compilerOptions.types) &&
            Object.isFrozen(authority.explicitProfileDefaults) &&
            Object.isFrozen(authority.resolvedBooleanOptions),
        `${label} effective options are not deeply immutable.`,
    );
};

const verifyDirectPolicyAndEffectiveOptions = async () => {
    for (const profile of fixture.profiles) {
        const parsed = parseConfig(profile.configSource, profile.targetProfile);
        assert(
            parsed.configPolicyRevision === 3 &&
                parsed.configPolicyFingerprint === policy.fingerprint &&
                parsed.targetProfile === profile.targetProfile &&
                parsed.explicitTarget === profile.normalizedTarget &&
                exactJson(
                    parsed.explicitLibraries,
                    profile.normalizedLibraries,
                ),
            `${profile.id} parse normalization drifted.`,
        );
        const effective = await resolveEffectiveOptions(parsed);
        assertDeepFrozenEffectiveOptions(effective, profile.id);
        assert(
            effective.revision === 3 &&
                Object.keys(effective.compilerOptions).length ===
                    profile.canonicalOptionCount &&
                effective.fingerprint === profile.effectiveOptionsFingerprint &&
                effective.canonicalJson ===
                    JSON.stringify(effective.compilerOptions) &&
                sha256(Buffer.from(effective.canonicalJson, "ascii")) ===
                    effective.fingerprint &&
                exactJson(
                    effective.compilerOptions.lib,
                    profile.normalizedLibraries,
                ),
            `${profile.id} canonical effective options drifted.`,
        );
    }

    for (const rejected of fixture.rejected) {
        await expectThrows(
            () => parseConfig(rejected.configSource, rejected.targetProfile),
            (error) =>
                assert(
                    error.policyCode === rejected.policyCode,
                    `${rejected.id} produced ${error.policyCode}.`,
                ),
            rejected.id,
        );
    }

    const legacyParsed = parseConfig("{}", "rhino", legacyAuthority);
    const legacyEffective = await resolveEffectiveOptions(legacyParsed);
    assert(
        legacyParsed.configPolicyRevision ===
            fixture.legacyCompatibility.policyRevision &&
            legacyEffective.revision === 2 &&
            legacyEffective.fingerprint ===
                fixture.legacyCompatibility.defaultEffectiveOptionsFingerprint &&
            Object.keys(legacyEffective.compilerOptions).length === 44,
        "Revision-2 parse/effective-options compatibility drifted.",
    );
    await expectThrows(
        () => parseConfig(
            '{"compilerOptions":{"target":"ES2018"}}',
            "rhino",
            legacyAuthority,
        ),
        (error) =>
            assert(
                error.policyCode === "CONFIG_PROFILE_CONFLICT",
                "Revision-2 target rejection changed classification.",
            ),
        "Revision-2 target override",
    );
};

const minimalGlobals =
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
const rhinoSource =
    "/* T1_REV3_RHINO_COMMENT */\n" +
    "const enum Step { Two = 2 }\n" +
    "const marker: RhinoLibMarker = { value: 7 };\n" +
    "const __t1RhinoResult = marker.value * Step.Two;\n";
const nodeSource =
    "/* T1_REV3_NODE_COMMENT */\n" +
    "const enum Delta { Two = 2 }\n" +
    "class Counter implements NodeLibMarker { value = 40; }\n" +
    "const marker: NodeLibMarker = new Counter();\n" +
    "export const result: number = marker.value + Delta.Two;\n";
const materialize = (filePath, text) => {
    const bytes = Buffer.from(text, "utf8");
    return Object.freeze({
        bytes,
        path: filePath,
        sha256: sha256(bytes),
        size: bytes.length,
        text,
    });
};
const projectFiles = (profile) => {
    const files = [materialize("/declarations/lib.d.ts", minimalGlobals)];
    if (profile.targetProfile === "rhino") {
        files.push(
            materialize(
                "/declarations/lib.es2018.d.ts",
                minimalGlobals +
                    "interface RhinoLibMarker { readonly value: number; }\n",
            ),
            materialize(profile.entryPath, rhinoSource),
        );
    } else {
        files.push(
            materialize(
                "/declarations/lib.es2022.d.ts",
                minimalGlobals +
                    "interface NodeLibMarker { readonly value: number; }\n",
            ),
            materialize("/declarations/node/index.d.ts", "export {};\n"),
            materialize(profile.entryPath, nodeSource),
        );
    }
    return files.sort((left, right) => compareCodeUnits(left.path, right.path));
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
const nodeResolutionIdentity = (files, profile) => {
    const nodeAuthority = fixture.nodeProgramAuthority;
    const records = files
        .slice()
        .sort((left, right) => compareCodeUnits(left.path, right.path));
    const targets = records
        .map((file) => ({
            kind: file.path.endsWith(".d.ts")
                ? "declaration"
                : file.path.startsWith("/sources/")
                  ? "project-source"
                  : "non-resolution",
            path: file.path,
        }))
        .filter((target) => target.kind !== "non-resolution");
    let snapshotCanonical =
        "autojs6.typescript.node-resolution.snapshot-targets.v2" +
        `|profileSchemaRevision=${profile.profileSchemaRevision}` +
        `|profileRevision=${profile.profileRevision}` +
        `|nodeModuleOutputPolicyFingerprint=${nodeAuthority.nodeModuleOutputPolicyFingerprint}`;
    for (const file of records) {
        const kind = file.path.endsWith(".d.ts")
            ? "declaration"
            : file.path.startsWith("/sources/")
              ? "project-source"
              : "non-resolution";
        snapshotCanonical +=
            `|file=${canonicalResolutionPath(file.path)},${file.size},` +
            `${file.text.length},${file.sha256},${kind}`;
    }
    const snapshotFingerprint = sha256(
        Buffer.from(snapshotCanonical, "ascii"),
    );
    const orderedTargets = targets.sort((left, right) =>
        compareCodeUnits(left.path, right.path),
    );
    const targetUniverseCanonical =
        "autojs6.typescript.node-resolution.target-universe.v2" +
        `|resolverPolicyRevision=${nodeAuthority.resolverPolicyRevision}` +
        `|packageTargetPolicyRevision=${nodeAuthority.packageTargetPolicyRevision}` +
        `|packageTargetPolicyFingerprint=${nodeAuthority.packageTargetPolicyFingerprint}` +
        `|snapshotTargetInventoryFingerprint=${snapshotFingerprint}` +
        "|packageTargetInventoryFingerprint=none" +
        orderedTargets
            .map(
                (target) =>
                    `|target=${canonicalResolutionPath(target.path)},${target.kind}`,
            )
            .join("");
    return Object.freeze({
        snapshotFingerprint,
        targetUniverseFingerprint: sha256(
            Buffer.from(targetUniverseCanonical, "ascii"),
        ),
    });
};

const legacyStartKeys = Object.freeze([
    "callerInventoryFingerprint",
    "configKind",
    "configSchemaRevision",
    "declarationByteLength",
    "declarationFileCount",
    "declarationInventoryFingerprint",
    "declarationProfileFingerprint",
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
    "sequence",
    "targetProfile",
    "totalBytes",
    "workerSpinMilliseconds",
    "workerSpinPhase",
]);
const configKeys = Object.freeze([
    "configByteLength",
    "configPolicyFingerprint",
    "configPolicyRevision",
    "configSha256",
]);
const rhinoStartKeys = Object.freeze([
    ...legacyStartKeys,
    ...configKeys,
    "entryPath",
]);
const nodeStartKeys = Object.freeze([
    ...legacyStartKeys,
    ...configKeys,
    "entryPath",
    "runtimeCapabilitiesFingerprint",
    "nodeModuleOutputPolicySchemaRevision",
    "nodeModuleOutputPolicyRevision",
    "nodeModuleOutputPolicyFingerprint",
    "resolverPolicyRevision",
    "snapshotTargetInventoryFingerprint",
    "targetUniverseFingerprint",
]);
assert(
    rhinoStartKeys.length === 27 && nodeStartKeys.length === 34,
    "Revision-3 controlled start-key inventory drifted.",
);

let requestCounter = 0;
const createOperation = (profile) => {
    const files = projectFiles(profile);
    const configBytes = Buffer.from(profile.configSource, "utf8");
    const configSha256 = sha256(configBytes);
    const inputAuthority = projectInputAuthority({
        configRecord: inputRecord("config", policy.configPath, configBytes),
        fileRecords: files.map((file) =>
            inputRecord(
                file.path.startsWith("/declarations/")
                    ? "declaration"
                    : "source",
                file.path,
                file.bytes,
            ),
        ),
        inputQuotaRevision: CURRENT_ORDINARY_INPUT_QUOTA_REVISION,
    });
    const requestId = `t1-tsconfig-rev3-${profile.id}-${++requestCounter}`;
    const start = {
        ...inputAuthority,
        configByteLength: configBytes.length,
        configKind: policy.configKind,
        configPolicyFingerprint: policy.fingerprint,
        configPolicyRevision: policy.revision,
        configSchemaRevision: fixture.configSchemaRevision,
        configSha256,
        entryPath: profile.entryPath,
        kind: "projectInputStart",
        profileRevision: profile.profileRevision,
        profileSchemaRevision: profile.profileSchemaRevision,
        requestId,
        sequence: 0,
        targetProfile: profile.targetProfile,
        workerSpinMilliseconds: 0,
        workerSpinPhase: "none",
    };
    if (profile.targetProfile === "node") {
        const nodeAuthority = fixture.nodeProgramAuthority;
        const resolution = nodeResolutionIdentity(files, profile);
        Object.assign(start, {
            nodeModuleOutputPolicyFingerprint:
                nodeAuthority.nodeModuleOutputPolicyFingerprint,
            nodeModuleOutputPolicyRevision:
                nodeAuthority.nodeModuleOutputPolicyRevision,
            nodeModuleOutputPolicySchemaRevision:
                nodeAuthority.nodeModuleOutputPolicySchemaRevision,
            resolverPolicyRevision: nodeAuthority.resolverPolicyRevision,
            runtimeCapabilitiesFingerprint:
                nodeAuthority.runtimeCapabilitiesFingerprint,
            snapshotTargetInventoryFingerprint: resolution.snapshotFingerprint,
            targetUniverseFingerprint: resolution.targetUniverseFingerprint,
        });
    }
    assert(
        exactKeys(
            start,
            profile.targetProfile === "node" ? nodeStartKeys : rhinoStartKeys,
        ),
        `${profile.id} start shape is not exact.`,
    );
    return {
        configBytes,
        configSha256,
        files,
        inputAuthority,
        profile,
        requestId,
        sequence: 0,
        start,
    };
};

const expectAck = (published, operation, label) => {
    const acknowledgements = published.filter(
        (message) => message.kind === "projectInputAck",
    );
    assert(
        acknowledgements.length === 1 &&
            acknowledgements[0].requestId === operation.requestId &&
            acknowledgements[0].sequence === operation.sequence &&
            !published.some(
                (message) =>
                    message.kind === "projectError" ||
                    message.kind === "projectConfigError",
            ),
        `${label} did not publish its exact ACK: ${JSON.stringify(published)}.`,
    );
    operation.sequence += 1;
};
const sendConfig = async (operation) => {
    expectAck(await dispatch(operation.start), operation, `${operation.profile.id} start`);
    expectAck(
        await dispatch({
            byteLength: operation.configBytes.length,
            kind: "projectInputConfigStart",
            path: policy.configPath,
            requestId: operation.requestId,
            sequence: operation.sequence,
            sha256: operation.configSha256,
        }),
        operation,
        `${operation.profile.id} config start`,
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
        `${operation.profile.id} config chunk`,
    );
    expectAck(
        await dispatch({
            chunkCount: 1,
            kind: "projectInputConfigEnd",
            requestId: operation.requestId,
            sequence: operation.sequence,
        }),
        operation,
        `${operation.profile.id} config end`,
    );
};
const sendFile = async (operation, fileIndex, file) => {
    expectAck(
        await dispatch({
            byteLength: file.size,
            fileIndex,
            kind: "projectInputFileStart",
            path: file.path,
            requestId: operation.requestId,
            sequence: operation.sequence,
            sha256: file.sha256,
        }),
        operation,
        `${operation.profile.id} ${file.path} start`,
    );
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
        `${operation.profile.id} ${file.path} chunk`,
    );
    expectAck(
        await dispatch({
            chunkCount: 1,
            fileIndex,
            kind: "projectInputFileEnd",
            requestId: operation.requestId,
            sequence: operation.sequence,
        }),
        operation,
        `${operation.profile.id} ${file.path} end`,
    );
};

const readFramedResult = async (operation, inputEndMessages) => {
    expectAck(inputEndMessages, operation, `${operation.profile.id} input end`);
    const starts = inputEndMessages.filter(
        (message) => message.kind === "projectResultStart",
    );
    assert(
        starts.length === 1 &&
            starts[0].requestId === operation.requestId &&
            exactKeys(starts[0], [
                "byteLength",
                "chunkCount",
                "kind",
                "requestId",
                "sha256",
            ]) &&
            starts[0].chunkCount > 0,
        `${operation.profile.id} did not publish one exact result start.`,
    );
    const start = starts[0];
    const chunks = [];
    for (let index = 0; index < start.chunkCount; index += 1) {
        const published = await dispatch({
            kind: "projectResultAck",
            requestId: operation.requestId,
            sequence: index - 1,
        });
        assert(
            published.length === 1 &&
                published[0].kind === "projectResultChunk" &&
                published[0].requestId === operation.requestId &&
                published[0].index === index,
            `${operation.profile.id} result chunk ${index} drifted.`,
        );
        chunks.push(Buffer.from(published[0].data, "base64"));
    }
    const terminal = await dispatch({
        kind: "projectResultAck",
        requestId: operation.requestId,
        sequence: start.chunkCount - 1,
    });
    assert(
        terminal.length === 2 &&
            terminal[0].kind === "projectPhase" &&
            terminal[0].phase === "emit" &&
            terminal[0].state === "completed" &&
            terminal[1].kind === "projectResultEnd",
        `${operation.profile.id} result did not close emit exactly.`,
    );
    const bytes = Buffer.concat(chunks);
    assert(
        bytes.length === start.byteLength && sha256(bytes) === start.sha256,
        `${operation.profile.id} result identity drifted.`,
    );
    return JSON.parse(bytes.toString("utf8"));
};

const assertCompiledProfile = (result, profile) => {
    const expectedTarget = profile.targetProfile === "rhino" ? 5 : 9;
    assert(
        result.compilerApi === "createIncrementalProgram" &&
            result.configKind === policy.configKind &&
            result.configSchemaRevision === fixture.configSchemaRevision &&
            result.profileSchemaRevision === profile.profileSchemaRevision &&
            result.profileRevision === profile.profileRevision &&
            result.targetProfile === profile.targetProfile &&
            result.tsVersion === expectedVersion &&
            result.emitSkipped === false &&
            result.effectiveOptionsRevision === 3 &&
            result.effectiveOptionsFingerprint ===
                profile.effectiveOptionsFingerprint &&
            Object.keys(result.options).length === profile.canonicalOptionCount &&
            result.options.target === expectedTarget &&
            exactJson(result.options.lib, profile.normalizedLibraries) &&
            result.options.preserveConstEnums === true &&
            exactJson(result.rootNames, [profile.entryPath]) &&
            exactJson(
                sorted(result.diagnostics.map((diagnostic) => diagnostic.code)),
                sorted(profile.warningDiagnosticCodes),
            ),
        `${profile.id} controlled result authority drifted: ${JSON.stringify(result.diagnostics)}.`,
    );
    const moduleOutput = result.outputs.find(
        (output) => output.path === profile.outputPath,
    );
    const sourceMapOutput = result.outputs.find(
        (output) => output.path === `${profile.outputPath}.map`,
    );
    assert(
        result.outputs.length === 2 &&
            moduleOutput !== undefined &&
            sourceMapOutput !== undefined &&
            moduleOutput.sha256 === sha256(Buffer.from(moduleOutput.text, "utf8")) &&
            sourceMapOutput.sha256 ===
                sha256(Buffer.from(sourceMapOutput.text, "utf8")) &&
            exactJson(moduleOutput.sourcePaths, [profile.entryPath]) &&
            exactJson(sourceMapOutput.sourcePaths, [profile.entryPath]),
        `${profile.id} output/source-map identity drifted.`,
    );
    const sourceMap = JSON.parse(sourceMapOutput.text);
    assert(
        sourceMap.version === 3 &&
            sourceMap.file === path.posix.basename(profile.outputPath) &&
            !Object.prototype.hasOwnProperty.call(sourceMap, "sourcesContent"),
        `${profile.id} source map is not the external v3 contract.`,
    );

    if (profile.targetProfile === "rhino") {
        assert(
            result.options.removeComments === true &&
                !moduleOutput.text.includes("T1_REV3_RHINO_COMMENT") &&
                moduleOutput.text.includes("var Step") &&
                vm.runInNewContext(
                    `${moduleOutput.text}\n__t1RhinoResult;`,
                    Object.create(null),
                ) === profile.runtimeResult,
            "Rhino revision-3 emit controls or runtime result drifted.",
        );
    } else {
        const commonJsExports = {};
        vm.runInNewContext(moduleOutput.text, {
            exports: commonJsExports,
            module: { exports: commonJsExports },
        });
        assert(
            result.options.removeComments === false &&
                result.options.useDefineForClassFields === true &&
                moduleOutput.text.includes("T1_REV3_NODE_COMMENT") &&
                moduleOutput.text.includes("var Delta") &&
                /value\s*=\s*40/.test(moduleOutput.text) &&
                commonJsExports.result === profile.runtimeResult,
            "Node ES2022 revision-3 emit controls or runtime result drifted.",
        );
    }
};

const compileProfile = async (profile) => {
    const operation = createOperation(profile);
    await sendConfig(operation);
    for (let index = 0; index < operation.files.length; index += 1) {
        await sendFile(operation, index, operation.files[index]);
    }
    const result = await readFramedResult(
        operation,
        await dispatch({
            fileCount: operation.files.length,
            kind: "projectInputEnd",
            requestId: operation.requestId,
            sequence: operation.sequence,
            totalBytes: operation.inputAuthority.totalBytes,
        }),
    );
    assertCompiledProfile(result, profile);
    const phaseSignature = observedMessages
        .filter(
            (message) =>
                message.kind === "projectPhase" &&
                message.requestId === operation.requestId,
        )
        .map((message) => `${message.phase}:${message.state}`);
    assert(
        exactJson(phaseSignature, [
            "ingress:started",
            "ingress:completed",
            "parse:started",
            "parse:completed",
            "semantic:started",
            "semantic:completed",
            "emit:started",
            "emit:completed",
        ]) &&
            workerBinding("framedInput") === null &&
            workerBinding("framedCompilationRequestId") === null &&
            workerBinding("framedResult") === null,
        `${profile.id} phase order or Worker cleanup drifted.`,
    );
};

(async () => {
    await verifyDirectPolicyAndEffectiveOptions();
    for (const profile of fixture.profiles) {
        await compileProfile(profile);
    }
    process.stdout.write(
        "T1-2 controlled tsconfig revision 3 verified: " +
            "rev2 compatibility, 20 booleans, target/lib normalization, " +
            "Rhino ES2018 and NodeNext ES2022 compile/execute\n",
    );
})().catch((error) => {
    process.stderr.write(`${error.stack || error}\n`);
    process.exitCode = 1;
});
