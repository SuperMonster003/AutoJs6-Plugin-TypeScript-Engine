"use strict";

const crypto = require("crypto");
const fs = require("fs");

function fail(message) {
    throw new Error(message);
}

function assert(condition, message) {
    if (!condition) fail(message);
}

function sorted(values) {
    return Array.from(values).sort();
}

function appendSet(input, label, values) {
    return input + `|${label}=${sorted(values).join(",")}`;
}

function assertExactKeys(value, expected, label) {
    assert(
        sorted(Object.keys(value)).join(",") === sorted(expected).join(","),
        `${label} keys drifted.`,
    );
}

function buildCanonicalInput(policy) {
    let input = "autojs6.typescript.controlled-tsconfig-policy.v1";
    input += `|revision=${policy.revision}`;
    input += `|configKind=${policy.configKind}`;
    input += `|configPath=${policy.configPath}`;
    input += `|grammar=${policy.grammar.name}`;
    input += `|bom=${policy.grammar.allowUtf8Bom}`;
    input += `|comments=${policy.grammar.allowComments}`;
    input += `|trailingComma=${policy.grammar.allowTrailingComma}`;
    input += `|duplicateKeysRejected=${policy.grammar.rejectDuplicateKeys}`;
    input += `|logicalPathDomain=${policy.rootSelection.logicalPathDomain}`;
    input += `|defaultInclude=${policy.rootSelection.defaultInclude}`;
    input += `|defaultExclude=${policy.rootSelection.defaultExclude.join(",")}`;
    input += `|filesAndInclude=${policy.rootSelection.filesAndInclude}`;
    input += `|excludeAffects=${policy.rootSelection.excludeAffects}`;
    input += `|jsonDepthCounting=${policy.budgetSemantics.jsonDepthCounting}`;
    input += `|jsonNodeCounting=${policy.budgetSemantics.jsonNodeCounting}`;
    input += `|selectorCounting=${policy.budgetSemantics.selectorCounting}`;
    input += `|selectorUtf8Measurement=${policy.budgetSemantics.selectorUtf8Measurement}`;
    input += `|maxBytes=${policy.budgets.maximumBytes}`;
    input += `|maxSelectors=${policy.budgets.maximumSelectors}`;
    input += `|maxSelectorUtf8Bytes=${policy.budgets.maximumSelectorUtf8Bytes}`;
    input += `|maxDepth=${policy.budgets.maximumJsonDepth}`;
    input += `|maxNodes=${policy.budgets.maximumJsonNodes}`;
    input = appendSet(input, "allowedTopLevel", policy.allowedTopLevel);
    input = appendSet(input, "unsupportedTopLevel", policy.unsupportedTopLevel);
    input += "|allowedBoolean=" + Object.entries(policy.allowedBooleanCompilerOptions)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([name, defaultValue]) => `${name}:${defaultValue}`)
        .join(",");
    input = appendSet(input, "profileManaged", policy.profileManagedCompilerOptions);
    input = appendSet(input, "escapeForbidden", policy.escapeForbiddenCompilerOptions);
    input = appendSet(input, "outputForbidden", policy.outputForbiddenCompilerOptions);
    input += `|knownOptionFallback=${policy.knownOptionFallback}`;
    input = appendSet(input, "policyCodes", policy.policyCodes);
    return input;
}

function parseJsonc(ts, source) {
    const parsed = ts.parseConfigFileTextToJson("tsconfig.json", source);
    assert(!parsed.error, `TypeScript rejected controlled JSONC golden: ${parsed.error?.code}`);
    return parsed.config;
}

function main() {
    const [compilerFile, policyFile, expectedVersion] = process.argv.slice(2);
    assert(compilerFile && policyFile && expectedVersion, "Expected compiler, policy, and version arguments.");
    const ts = require(compilerFile);
    const policy = JSON.parse(fs.readFileSync(policyFile, "utf8"));

    assertExactKeys(
        policy,
        [
            "schema",
            "revision",
            "configKind",
            "configPath",
            "fingerprint",
            "grammar",
            "budgets",
            "budgetSemantics",
            "rootSelection",
            "allowedTopLevel",
            "unsupportedTopLevel",
            "allowedBooleanCompilerOptions",
            "profileManagedCompilerOptions",
            "escapeForbiddenCompilerOptions",
            "outputForbiddenCompilerOptions",
            "knownOptionFallback",
            "policyCodes",
        ],
        "Policy root",
    );
    assertExactKeys(
        policy.grammar,
        ["name", "allowUtf8Bom", "allowComments", "allowTrailingComma", "rejectDuplicateKeys"],
        "Grammar",
    );
    assertExactKeys(
        policy.budgets,
        [
            "maximumBytes",
            "maximumSelectors",
            "maximumSelectorUtf8Bytes",
            "maximumJsonDepth",
            "maximumJsonNodes",
        ],
        "Budgets",
    );
    assertExactKeys(
        policy.budgetSemantics,
        ["jsonDepthCounting", "jsonNodeCounting", "selectorCounting", "selectorUtf8Measurement"],
        "Budget semantics",
    );
    assertExactKeys(
        policy.rootSelection,
        ["logicalPathDomain", "defaultInclude", "defaultExclude", "filesAndInclude", "excludeAffects"],
        "Root selection",
    );
    assert(ts.version === expectedVersion, `Expected TypeScript ${expectedVersion}, found ${ts.version}.`);
    assert(policy.revision === 1, "Controlled tsconfig policy revision drifted.");
    assert(policy.configKind === "tsconfig", "Controlled config kind drifted.");
    assert(policy.grammar.name === `typescript-jsonc-${expectedVersion}`, "JSONC grammar/version drifted.");
    assert(policy.budgets.maximumBytes === 48 * 1024, "Raw config budget drifted.");
    assert(policy.allowedTopLevel.join(",") === "$schema,compilerOptions,exclude,files,include", "Top-level allowlist drifted.");

    const groups = [
        Object.keys(policy.allowedBooleanCompilerOptions),
        policy.profileManagedCompilerOptions,
        policy.escapeForbiddenCompilerOptions,
        policy.outputForbiddenCompilerOptions,
    ];
    const classified = new Set();
    for (const group of groups) {
        for (const name of group) {
            assert(!classified.has(name), `Compiler option is classified more than once: ${name}`);
            classified.add(name);
        }
    }

    const declarations = new Map(ts.optionDeclarations.map((option) => [option.name, option]));
    for (const name of Object.keys(policy.allowedBooleanCompilerOptions)) {
        const declaration = declarations.get(name);
        assert(declaration, `Allowed compiler option is missing from TypeScript ${expectedVersion}: ${name}`);
        assert(declaration.type === "boolean", `Allowed compiler option is not boolean: ${name}`);
    }
    for (const name of classified) {
        assert(declarations.has(name), `Classified compiler option is unknown to TypeScript ${expectedVersion}: ${name}`);
    }
    assert(policy.knownOptionFallback === "unsupported-feature", "Known-option fallback must fail closed.");
    assert(
        ts.optionDeclarations.every((option) => classified.has(option.name) || policy.knownOptionFallback === "unsupported-feature"),
        "A TypeScript option has no fail-closed classification.",
    );

    const strictDependents = Object.entries(policy.allowedBooleanCompilerOptions)
        .filter(([, defaultValue]) => defaultValue === "inherit-strict")
        .map(([name]) => name);
    for (const name of strictDependents) {
        assert(ts.getStrictOptionValue({ strict: true }, name) === true, `${name} no longer inherits strict=true.`);
        assert(ts.getStrictOptionValue({ strict: false }, name) === false, `${name} no longer inherits strict=false.`);
        assert(ts.getStrictOptionValue({ strict: true, [name]: false }, name) === false, `${name} explicit override drifted.`);
    }

    const commentsAndTrailing = parseJsonc(
        ts,
        "{\n  // controlled comment\n  \"compilerOptions\": { \"strict\": true, },\n}\n",
    );
    assert(commentsAndTrailing.compilerOptions.strict === true, "JSONC comment/trailing-comma golden drifted.");
    const bom = parseJsonc(ts, "\ufeff{\"compilerOptions\":{\"strict\":true}}");
    assert(bom.compilerOptions.strict === true, "UTF-8 BOM golden drifted.");
    const duplicate = parseJsonc(
        ts,
        "{\"compilerOptions\":{\"strict\":true,\"strict\":false}}",
    );
    assert(duplicate.compilerOptions.strict === false, "Duplicate-key threat golden drifted.");
    assert(policy.grammar.rejectDuplicateKeys === true, "Policy must reject TypeScript's duplicate-key last-wins behavior.");

    const canonicalInput = buildCanonicalInput(policy);
    const fingerprint = crypto.createHash("sha256").update(canonicalInput, "ascii").digest("hex");
    assert(
        policy.fingerprint === fingerprint,
        `Controlled tsconfig policy fingerprint mismatch: expected ${policy.fingerprint}, actual ${fingerprint}.`,
    );
    process.stdout.write(
        `R2-M2-C1 controlled tsconfig policy verified: ${Object.keys(policy.allowedBooleanCompilerOptions).length} options, ${fingerprint}\n`,
    );
}

main();
