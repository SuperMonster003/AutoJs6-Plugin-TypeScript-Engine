"use strict";

const cryptoModule = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const {
    projectInputAuthority,
    record: inputRecord,
} = require("./r2-input-archive-authority.js");

const [
    workerArgument,
    compilerArgument,
    fixtureArgument,
    policyArgument,
    expectedVersion,
] = process.argv.slice(2);
if (
    !workerArgument ||
    !compilerArgument ||
    !fixtureArgument ||
    !policyArgument ||
    !expectedVersion
) {
    throw new Error(
        "Usage: node verify-r2-controlled-tsconfig-effective-options.js " +
            "<worker.js> <typescript.js> <effective-options-v1.json> " +
            "<policy-v2.json> <expected-version>",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const fixture = JSON.parse(fs.readFileSync(path.resolve(fixtureArgument), "utf8"));
const policy = JSON.parse(fs.readFileSync(path.resolve(policyArgument), "utf8"));
const workerSource = fs.readFileSync(workerPath, "utf8");
const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const expectThrows = async (operation, validate, message) => {
    let failure = null;
    try {
        await operation();
    } catch (error) {
        failure = error;
    }
    assert(failure !== null, `${message} did not fail.`);
    validate(failure);
};
const sha256 = (bytes) =>
    cryptoModule.createHash("sha256").update(bytes).digest("hex");
const sortedKeys = (value) => Object.keys(value).sort().join("\n");

let digestInvocationCount = 0;
const webcrypto = cryptoModule.webcrypto;
Object.defineProperty(global, "crypto", {
    configurable: true,
    value: Object.freeze({
        subtle: Object.freeze({
            digest: async (...arguments_) => {
                digestInvocationCount += 1;
                return webcrypto.subtle.digest(...arguments_);
            },
        }),
    }),
});
global.ts = require(compilerPath);
global.importScripts = (specifier) => {
    assert(specifier === "typescript.js", "Worker imported an unexpected script.");
};
const messages = [];
global.self = {
    postMessage: (message) => messages.push(message),
};
vm.runInThisContext(workerSource, { filename: workerPath });
assert(
    messages.length === 1 &&
        messages[0].kind === "ready" &&
        messages[0].tsVersion === expectedVersion,
    "Worker did not publish the pinned TypeScript version.",
);
messages.length = 0;

const workerBinding = (expression) =>
    vm.runInThisContext(expression, {
        filename: `${workerPath}#controlled-effective-options-${expression}`,
    });
const parseConfig = (source) =>
    workerBinding("parseControlledTsConfig")(source);
const resolveEffectiveOptions = (parsedConfig) =>
    workerBinding("resolveControlledTsConfigEffectiveOptions")(parsedConfig);
const dispatch = async (message) => {
    messages.length = 0;
    await self.onmessage({ data: message });
    return messages.slice();
};
const expectAck = (published, requestId, sequence, label) => {
    const acknowledgements = published.filter(
        (message) => message.kind === "projectInputAck",
    );
    assert(
        acknowledgements.length === 1 &&
            published.every(
                (message) =>
                    message.kind !== "projectError" &&
                    message.kind !== "projectConfigError",
            ),
        `${label} published an ambiguous response.`,
    );
    const acknowledgement = acknowledgements[0];
    assert(
        acknowledgement.kind === "projectInputAck" &&
            acknowledgement.requestId === requestId &&
            acknowledgement.sequence === sequence,
        `${label} did not publish the exact ACK.`,
    );
};
const expectConfigError = (published, requestId, policyCode, label) => {
    assert(published.length === 1, `${label} published an ambiguous terminal.`);
    const error = published[0];
    assert(
        sortedKeys(error) ===
            [
                "emittedModuleCount",
                "errorCode",
                "kind",
                "message",
                "outcome",
                "policyCode",
                "requestId",
                "stage",
            ].sort().join("\n"),
        `${label} terminal shape drifted.`,
    );
    assert(
        error.kind === "projectConfigError" &&
            error.requestId === requestId &&
            error.stage === "config" &&
            error.outcome === "diagnostics" &&
            error.errorCode ===
                "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_DIAGNOSTICS" &&
            error.policyCode === policyCode &&
            error.emittedModuleCount === 0 &&
            typeof error.message === "string" &&
            error.message.length > 0 &&
            error.message.length <= 4096,
        `${label} typed config invariants drifted.`,
    );
};

const assertDeepFrozenAuthority = (authority, label) => {
    assert(Object.isFrozen(authority), `${label} authority is mutable.`);
    assert(
        Object.isFrozen(authority.compilerOptions) &&
            Object.isFrozen(authority.compilerOptions.types) &&
            Object.isFrozen(authority.explicitProfileDefaults) &&
            Object.isFrozen(authority.resolvedBooleanOptions),
        `${label} retained a mutable nested value.`,
    );
};

const verifyFixtureAuthority = async () => {
    assert(
        fixture.schema ===
            "autojs6.typescript.controlled-tsconfig-effective-options" &&
            fixture.revision === 2 &&
            fixture.profileSchemaRevision === 1 &&
            fixture.profileRevision === 2 &&
            fixture.configSchemaRevision === 2 &&
            fixture.configKind === "tsconfig",
        "C3 effective-options fixture authority drifted.",
    );
    const workerKeys = workerBinding("controlledTsConfigCanonicalOptionKeys");
    assert(
        fixture.canonicalOptionKeys.length === 44 &&
            JSON.stringify(fixture.canonicalOptionKeys) ===
                JSON.stringify(workerKeys) &&
            JSON.stringify(workerKeys) ===
                JSON.stringify(workerKeys.slice().sort()),
        "C3 canonical 44-key inventory is not exact code-unit order.",
    );
    assert(
        workerBinding("controlledTsConfigEffectiveOptionsRevision") === 2,
        "C3 effective-options revision drifted.",
    );
    assert(
        policy.fingerprint ===
            workerBinding("controlledTsConfigAuthority.configPolicyFingerprint") &&
            policy.revision ===
                workerBinding("controlledTsConfigAuthority.configPolicyRevision") &&
            JSON.stringify(
                Object.keys(policy.allowedBooleanCompilerOptions).sort(),
            ) ===
                JSON.stringify(
                    Array.from(
                        workerBinding("controlledTsConfigAllowedBooleanOptions"),
                    ).sort(),
                ),
        "C3 merge no longer consumes the exact C1 policy authority.",
    );

    for (const testCase of fixture.accepted) {
        const source = JSON.stringify({
            compilerOptions: testCase.explicitBooleanOptions,
        });
        const parsed = parseConfig(source);
        const authority = await resolveEffectiveOptions(parsed);
        assertDeepFrozenAuthority(authority, testCase.name);
        assert(
            authority.revision === fixture.revision &&
                authority.canonicalJson === testCase.canonicalJson &&
                authority.fingerprint === testCase.fingerprint,
            `${testCase.name} canonical authority drifted.`,
        );
        assert(
            Object.keys(authority.compilerOptions).length === 44 &&
                JSON.stringify(Object.keys(authority.compilerOptions)) ===
                    JSON.stringify(fixture.canonicalOptionKeys) &&
                JSON.stringify(authority.compilerOptions) ===
                    authority.canonicalJson &&
                JSON.stringify(JSON.parse(authority.canonicalJson)) ===
                    authority.canonicalJson &&
                sha256(Buffer.from(authority.canonicalJson, "ascii")) ===
                    authority.fingerprint,
            `${testCase.name} compact JSON/fingerprint drifted.`,
        );
        assert(
            Object.keys(authority.resolvedBooleanOptions).length === 18,
            `${testCase.name} did not resolve all C1 booleans.`,
        );
        const optionDiagnostics = ts
            .createProgram({
                rootNames: [],
                options: authority.compilerOptions,
            })
            .getOptionsDiagnostics();
        assert(
            optionDiagnostics.length === 0,
            `${testCase.name} produced unexpected TypeScript option diagnostics: ` +
                optionDiagnostics.map((diagnostic) => diagnostic.code).join(","),
        );
    }
};

const verifyPrecedenceAndPins = async () => {
    const defaults = await resolveEffectiveOptions(parseConfig("{}"));
    const options = defaults.compilerOptions;
    const exactPins = {
        allowSyntheticDefaultImports: true,
        alwaysStrict: true,
        esModuleInterop: true,
        forceConsistentCasingInFileNames: true,
        moduleDetection: ts.ModuleDetectionKind.Auto,
        resolveJsonModule: false,
        useDefineForClassFields: false,
    };
    assert(
        JSON.stringify(defaults.explicitProfileDefaults) ===
            JSON.stringify(exactPins),
        "C3 seven explicit profile pins drifted.",
    );
    assert(
        ts.getAllowSyntheticDefaultImports(options) ===
            options.allowSyntheticDefaultImports &&
            ts.getAlwaysStrict(options) === options.alwaysStrict &&
            ts.getESModuleInterop(options) === options.esModuleInterop &&
            ts.getEmitModuleDetectionKind(options) === options.moduleDetection &&
            ts.getResolveJsonModule(options) === options.resolveJsonModule &&
            ts.getUseDefineForClassFields(options) ===
                options.useDefineForClassFields &&
            (options.forceConsistentCasingInFileNames !== false) ===
                options.forceConsistentCasingInFileNames,
        "C3 explicit pins disagree with TypeScript 6.0.3 computed semantics.",
    );

    const strictFalse = await resolveEffectiveOptions(
        parseConfig('{"compilerOptions":{"strict":false}}'),
    );
    for (const [name, defaultValue] of Object.entries(
        policy.allowedBooleanCompilerOptions,
    )) {
        const expected =
            defaultValue === "inherit-strict"
                ? false
                : defaultValue === "true";
        if (name !== "strict") {
            assert(
                strictFalse.resolvedBooleanOptions[name] === expected,
                `${name} did not use its frozen strict/default precedence.`,
            );
        }
    }
    const childTrue = await resolveEffectiveOptions(
        parseConfig(
            '{"compilerOptions":{"strict":false,"noImplicitAny":true}}',
        ),
    );
    const childFalse = await resolveEffectiveOptions(
        parseConfig(
            '{"compilerOptions":{"strict":true,"noImplicitAny":false}}',
        ),
    );
    assert(
        childTrue.compilerOptions.noImplicitAny === true &&
            childTrue.compilerOptions.noImplicitThis === false &&
            childFalse.compilerOptions.noImplicitAny === false &&
            childFalse.compilerOptions.noImplicitThis === true,
        "C3 explicit strict-child precedence drifted.",
    );
};

const verifyTs5052Oracle = async () => {
    for (const testCase of fixture.rejected) {
        const explicit = testCase.explicitBooleanOptions;
        const strict = explicit.strict ?? true;
        const options = {};
        for (const [name, defaultValue] of Object.entries(
            policy.allowedBooleanCompilerOptions,
        )) {
            options[name] =
                explicit[name] ??
                (defaultValue === "inherit-strict"
                    ? strict
                    : defaultValue === "true");
        }
        const diagnostics = ts
            .createProgram({ rootNames: [], options })
            .getOptionsDiagnostics();
        assert(
            diagnostics.some((diagnostic) => diagnostic.code === 5052),
            `${testCase.name} is no longer a TypeScript TS5052 relation.`,
        );
        await expectThrows(
            () =>
                resolveEffectiveOptions(
                    parseConfig(
                        JSON.stringify({ compilerOptions: explicit }),
                    ),
                ),
            (error) => {
                assert(
                    error.policyCode === testCase.policyCode &&
                        error.policyCode === "CONFIG_INVALID_VALUE",
                    `${testCase.name} did not map TS5052 to CONFIG_INVALID_VALUE.`,
                );
            },
            testCase.name,
        );
    }
};

const verifySemanticInvarianceAndMutation = async () => {
    const defaults = await resolveEffectiveOptions(parseConfig("{}"));
    const formatted = await resolveEffectiveOptions(
        parseConfig(
            '\ufeff{\n' +
                '  // presentation and selector identity are not options\n' +
                '  "include": ["src/**/*",],\n' +
                '  "$schema": "https://json.schemastore.org/tsconfig",\n' +
                '  "compilerOptions": {},\n' +
                '}\n',
        ),
    );
    assert(
        defaults.canonicalJson === formatted.canonicalJson &&
            defaults.fingerprint === formatted.fingerprint,
        "Non-semantic JSONC/$schema/selector changes altered the options fingerprint.",
    );
    for (const name of Object.keys(policy.allowedBooleanCompilerOptions)) {
        const explicitBooleanOptions = {
            [name]: !defaults.resolvedBooleanOptions[name],
        };
        if (
            name === "strictNullChecks" &&
            explicitBooleanOptions.strictNullChecks === false
        ) {
            // C3 deliberately makes the inherited SPI=true value explicit;
            // disable it here so this remains a valid one-axis fingerprint
            // mutation rather than the frozen TS5052 policy failure.
            explicitBooleanOptions.strictPropertyInitialization = false;
        }
        const mutated = await resolveEffectiveOptions(
            parseConfig(
                JSON.stringify({ compilerOptions: explicitBooleanOptions }),
            ),
        );
        assert(
            mutated.resolvedBooleanOptions[name] !==
                defaults.resolvedBooleanOptions[name] &&
                defaults.canonicalJson !== mutated.canonicalJson &&
                defaults.fingerprint !== mutated.fingerprint,
            `Allowed option ${name} retained the default canonical authority.`,
        );
        const optionDiagnostics = ts
            .createProgram({
                rootNames: [],
                options: mutated.compilerOptions,
            })
            .getOptionsDiagnostics();
        assert(
            optionDiagnostics.length === 0,
            `Allowed option ${name} mutation produced TypeScript option diagnostics: ` +
                optionDiagnostics.map((diagnostic) => diagnostic.code).join(","),
        );
    }
    await expectThrows(
        () => {
            defaults.compilerOptions.strict = false;
        },
        () => {},
        "Frozen compiler options mutation",
    );
    await expectThrows(
        () => defaults.compilerOptions.types.push("escape"),
        () => {},
        "Frozen compiler option list mutation",
    );
    await expectThrows(
        () => {
            defaults.resolvedBooleanOptions.strict = false;
        },
        () => {},
        "Frozen resolved options mutation",
    );
};

let requestCounter = 0;
const beginFramedConfig = async (source, label) => {
    requestCounter += 1;
    const requestId = `c3-${requestCounter}-${label}`;
    const bytes = Buffer.from(source, "utf8");
    const digest = sha256(bytes);
    const inputAuthority = projectInputAuthority({
        configRecord: inputRecord("config", policy.configPath, bytes),
        fileRecords: [
            inputRecord("declaration", "/declarations/lib.d.ts", ""),
            inputRecord("source", "/sources/main.ts", ""),
        ],
    });
    let sequence = 0;
    expectAck(
        await dispatch({
            configByteLength: bytes.length,
            configKind: "tsconfig",
            configPolicyFingerprint: policy.fingerprint,
            configPolicyRevision: policy.revision,
            configSchemaRevision: fixture.configSchemaRevision,
            configSha256: digest,
            entryPath: "/sources/main.ts",
            ...inputAuthority,
            kind: "projectInputStart",
            profileRevision: fixture.profileRevision,
            profileSchemaRevision: fixture.profileSchemaRevision,
            requestId,
            sequence,
            targetProfile: "rhino",
            workerSpinMilliseconds: 0,
            workerSpinPhase: "none",
        }),
        requestId,
        sequence,
        `${label} project start`,
    );
    sequence += 1;
    expectAck(
        await dispatch({
            byteLength: bytes.length,
            kind: "projectInputConfigStart",
            path: policy.configPath,
            requestId,
            sequence,
            sha256: digest,
        }),
        requestId,
        sequence,
        `${label} config start`,
    );
    sequence += 1;
    let chunkCount = 0;
    if (bytes.length !== 0) {
        expectAck(
            await dispatch({
                chunkIndex: 0,
                data: bytes.toString("base64"),
                kind: "projectInputConfigChunk",
                requestId,
                sequence,
            }),
            requestId,
            sequence,
            `${label} config chunk`,
        );
        sequence += 1;
        chunkCount = 1;
    }
    return {
        accumulator: workerBinding("framedInput.configTransport.bytes"),
        bytes,
        chunkCount,
        label,
        requestId,
        sequence,
    };
};

const endFramedConfig = async (operation, expectedPolicyCode = null) => {
    const digestsBefore = digestInvocationCount;
    const published = await dispatch({
        chunkCount: operation.chunkCount,
        kind: "projectInputConfigEnd",
        requestId: operation.requestId,
        sequence: operation.sequence,
    });
    assert(
        Array.from(operation.accumulator).every((value) => value === 0),
        `${operation.label} retained raw config bytes.`,
    );
    if (expectedPolicyCode !== null) {
        expectConfigError(
            published,
            operation.requestId,
            expectedPolicyCode,
            operation.label,
        );
        assert(
            digestInvocationCount === digestsBefore + 1 &&
                workerBinding("framedInput") === null,
            `${operation.label} hashed effective options or retained rejected state.`,
        );
        return null;
    }
    expectAck(
        published,
        operation.requestId,
        operation.sequence,
        `${operation.label} config end`,
    );
    const state = workerBinding("framedInput");
    assert(
        digestInvocationCount === digestsBefore + 2 &&
            state.controlledConfig !== null &&
            state.controlledEffectiveOptions !== null,
        `${operation.label} ACK preceded parse/merge/effective hash.`,
    );
    assertDeepFrozenAuthority(
        state.controlledEffectiveOptions,
        `${operation.label} framed`,
    );
    return state.controlledEffectiveOptions;
};

const releaseAcceptedEffectiveOptionsOperation = async (operation) => {
    const state = workerBinding("framedInput");
    assert(
        state !== null && state.requestId === operation.requestId,
        `${operation.label} lost its accepted config authority.`,
    );
    workerBinding("releaseProjectInputState")(state);
    workerBinding("framedInput = null");
    assert(
        workerBinding("framedInput") === null,
        `${operation.label} retained accepted config state.`,
    );
};

const verifyFramedAtomicityAndSuccessor = async () => {
    const accepted = await beginFramedConfig("{}", "accepted-defaults");
    const authority = await endFramedConfig(accepted);
    assert(
        authority.canonicalJson === fixture.accepted[0].canonicalJson &&
            authority.fingerprint === fixture.accepted[0].fingerprint,
        "Framed default effective authority disagrees with the fixture.",
    );
    await releaseAcceptedEffectiveOptionsOperation(accepted);

    const rejected = await beginFramedConfig(
        '{"compilerOptions":{"strict":false,"exactOptionalPropertyTypes":true}}',
        "typed-ts5052",
    );
    await endFramedConfig(rejected, "CONFIG_INVALID_VALUE");

    const successor = await beginFramedConfig(
        '{"compilerOptions":{"strict":false}}',
        "successor",
    );
    const successorAuthority = await endFramedConfig(successor);
    assert(
        successorAuthority.fingerprint === fixture.accepted[1].fingerprint,
        "A typed config failure contaminated its successor.",
    );
    await releaseAcceptedEffectiveOptionsOperation(successor);
};

(async () => {
    await verifyFixtureAuthority();
    await verifyPrecedenceAndPins();
    await verifyTs5052Oracle();
    await verifySemanticInvarianceAndMutation();
    await verifyFramedAtomicityAndSuccessor();

    process.stdout.write(
        `R2-M2-C3 controlled tsconfig effective options verified: ` +
            `${fixture.canonicalOptionKeys.length} canonical keys, ` +
            `${fixture.accepted.length} accepted, ${fixture.rejected.length} rejected\n`,
    );
})().catch((error) => {
    process.stderr.write(`${error.stack || error}\n`);
    process.exitCode = 1;
});
