"use strict";

const cryptoModule = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const [
    workerArgument,
    compilerArgument,
    declarationsArgument,
    libraryArgument,
    policyArgument,
    expectedVersion,
] = process.argv.slice(2);
if (
    process.argv.slice(2).length !== 6 ||
    !workerArgument ||
    !compilerArgument ||
    !declarationsArgument ||
    !libraryArgument ||
    !policyArgument ||
    !expectedVersion
) {
    throw new Error(
        "Usage: node verify-t1-standard-decorators.js " +
            "<worker.js> <typescript.js> <generated-r2-declarations-dir> " +
            "<typescript-lib-dir> <policy-v4.json> <expected-version>",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const declarationRoot = path.resolve(declarationsArgument);
const libraryRoot = path.resolve(libraryArgument);
const policyPath = path.resolve(policyArgument);
const ts = require(compilerPath);
const workerSource = fs.readFileSync(workerPath, "utf8");
const policy = JSON.parse(fs.readFileSync(policyPath, "utf8"));
const declarationManifest = JSON.parse(
    fs.readFileSync(path.join(declarationRoot, "distribution.json"), "utf8"),
);

const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;
const exactJson = (actual, expected) =>
    JSON.stringify(actual) === JSON.stringify(expected);
const sha256 = (value) =>
    cryptoModule.createHash("sha256").update(value).digest("hex");
const normalizeVfsPath = (value) => {
    const normalized = path.posix.normalize(String(value).replaceAll("\\", "/"));
    assert(
        normalized.startsWith("/") &&
            normalized !== "/.." &&
            !normalized.startsWith("/../"),
        `T1-4 VFS path escaped its snapshot: ${value}`,
    );
    return normalized;
};

assert(ts.version === expectedVersion, "T1-4 loaded the wrong TypeScript version.");
assert(
    policy.schema === "autojs6.typescript.controlled-tsconfig-policy" &&
        policy.revision === 4 &&
        policy.fingerprint ===
            "4a9a5e70150f81e1ec9a5e2f749cc081f93f207f092300c96a79abfdcc152dab",
    "T1-4 did not receive the current revision-4 policy authority.",
);
assert(
    declarationManifest.schemaVersion === 2 &&
        declarationManifest.sources.common.version === expectedVersion &&
        exactJson(declarationManifest.profiles.rhino.layers, ["common", "rhino"]) &&
        exactJson(declarationManifest.profiles.node.layers, ["common", "node"]),
    "T1-4 declaration profile authority drifted.",
);

const standardLibraryEntries = fs
    .readdirSync(libraryRoot)
    .filter((name) => /^lib(?:\..+)?\.d\.ts$/.test(name))
    .sort(compareCodeUnits)
    .map((name) => [
        `/declarations/${name}`,
        fs.readFileSync(path.join(libraryRoot, name), "utf8"),
    ]);
const standardLibraryNames = new Set(
    standardLibraryEntries.map(([fileName]) => path.posix.basename(fileName)),
);
assert(
    standardLibraryEntries.length === declarationManifest.layers.common.fileCount &&
        standardLibraryNames.has("lib.d.ts") &&
        standardLibraryNames.has("lib.es5.d.ts") &&
        standardLibraryNames.has("lib.decorators.d.ts") &&
        standardLibraryNames.has("lib.decorators.legacy.d.ts"),
    "T1-4 standard decorator declaration inventory is incomplete.",
);
const es5Source = standardLibraryEntries.find(
    ([fileName]) => fileName === "/declarations/lib.es5.d.ts",
)[1];
assert(
    es5Source.includes('/// <reference lib="decorators" />') &&
        es5Source.includes('/// <reference lib="decorators.legacy" />'),
    "TypeScript's ES declaration closure no longer exposes decorator context types.",
);

const profileDeclarations = new Map();
for (const targetProfile of ["rhino", "node"]) {
    const layer = declarationManifest.layers[targetProfile];
    const entries = layer.files.map((relativePath) => {
        assert(
            relativePath.startsWith(`layers/${targetProfile}/`) &&
                relativePath.endsWith(".d.ts"),
            `${targetProfile} declaration escaped its generated layer.`,
        );
        const suffix = relativePath.slice(`layers/${targetProfile}/`.length);
        return [
            `/declarations/${targetProfile}/${suffix}`,
            fs.readFileSync(
                path.join(declarationRoot, ...relativePath.split("/")),
                "utf8",
            ),
        ];
    });
    const entryPath =
        `/declarations/${targetProfile}/${path.posix.basename(layer.entryPoint)}`;
    assert(
        entries.length === layer.fileCount &&
            entries.some(([fileName]) => fileName === entryPath),
        `${targetProfile} declaration entry is missing.`,
    );
    profileDeclarations.set(targetProfile, { entries, entryPath });
}

Object.defineProperty(global, "crypto", {
    configurable: true,
    value: cryptoModule.webcrypto,
});
global.ts = ts;
global.importScripts = (specifier) => {
    assert(specifier === "typescript.js", "Worker imported an unexpected script.");
};
const workerMessages = [];
global.self = {
    postMessage: (message) => workerMessages.push(message),
};
vm.runInThisContext(workerSource, { filename: workerPath });
assert(
    workerMessages.length === 1 &&
        workerMessages[0].kind === "ready" &&
        workerMessages[0].tsVersion === expectedVersion,
    "T1-4 verifier did not load the production Worker.",
);
const workerBinding = (expression) =>
    vm.runInThisContext(expression, {
        filename: `${workerPath}#t1-standard-decorators-${expression}`,
    });
const revision4Authority = workerBinding("controlledTsConfigRevision4Authority");
const parseControlledConfig = workerBinding("parseControlledTsConfig");
const resolveEffectiveOptions = workerBinding(
    "resolveControlledTsConfigEffectiveOptions",
);
const createWarnings = workerBinding(
    "createControlledTsConfigWarningDiagnostics",
);
assert(
    revision4Authority.configPolicyRevision === policy.revision &&
        revision4Authority.configPolicyFingerprint === policy.fingerprint,
    "Production Worker and native revision-4 decorator authority diverged.",
);

const decoratorSource =
    'const events: string[] = [];\n' +
    'const record = (event: string): void => { events.push(event); };\n' +
    'type Constructor = new (...args: any[]) => object;\n' +
    'function markClass<T extends Constructor>(\n' +
    '  value: T,\n' +
    '  context: ClassDecoratorContext<T>,\n' +
    '): T {\n' +
    '  if (context.kind !== "class" || context.name !== "Calculator") throw new Error("class-context");\n' +
    '  record("class:apply");\n' +
    '  context.addInitializer(function (): void { record("class:init"); });\n' +
    '  return value;\n' +
    '}\n' +
    'function traceMethod<This, Args extends unknown[], Return>(\n' +
    '  value: (this: This, ...args: Args) => Return,\n' +
    '  context: ClassMethodDecoratorContext<This, (this: This, ...args: Args) => Return>,\n' +
    '): (this: This, ...args: Args) => Return {\n' +
    '  if (context.kind !== "method" || context.name !== "answer") throw new Error("method-context");\n' +
    '  record("method:apply");\n' +
    '  context.addInitializer(function (): void { record("method:init"); });\n' +
    '  return function (this: This, ...args: Args): Return {\n' +
    '    record("method:call");\n' +
    '    return value.apply(this, args);\n' +
    '  };\n' +
    '}\n' +
    '@markClass\n' +
    'class Calculator {\n' +
    '  private readonly offset: number = 2;\n' +
    '  @traceMethod\n' +
    '  answer(value: number): number { return value * 2 + this.offset; }\n' +
    '}\n' +
    'const calculator = new Calculator();\n' +
    'export const decoratedAnswer: number = calculator.answer(20);\n' +
    'export const decoratorTrace: string = events.join("|");\n';
const expectedTrace =
    "method:apply|class:apply|class:init|method:init|method:call";
const expectedEffectiveOptionsFingerprints = Object.freeze({
    node: "b77bfac45d80ad99a9a65168ecd97a10607f0574189e521e3355eb080b4a97ff",
    rhino: "a5a49efc19206d54f61c5db67323c72da05250a023398ebcae84557bbcbcd8a7",
});

const createSnapshotHost = (entries) => {
    const files = new Map();
    for (const [fileName, text] of entries) {
        const normalized = normalizeVfsPath(fileName);
        assert(!files.has(normalized), `Duplicate T1-4 VFS path: ${normalized}`);
        files.set(normalized, text);
    }
    const directories = new Set(["/"]);
    for (const fileName of files.keys()) {
        let directory = path.posix.dirname(fileName);
        while (!directories.has(directory)) {
            directories.add(directory);
            directory = path.posix.dirname(directory);
        }
    }
    const outputs = new Map();
    const sourceFiles = new Map();
    const host = {
        createDirectory: () => {},
        directoryExists: (directoryName) =>
            directories.has(normalizeVfsPath(directoryName)),
        fileExists: (fileName) => files.has(normalizeVfsPath(fileName)),
        getCanonicalFileName: (fileName) => fileName,
        getCurrentDirectory: () => "/",
        getDefaultLibFileName: () => "/declarations/lib.d.ts",
        getDefaultLibLocation: () => "/declarations",
        getDirectories: (directoryName) => {
            const directory = normalizeVfsPath(directoryName);
            const prefix = directory === "/" ? "/" : `${directory}/`;
            return Array.from(directories)
                .filter(
                    (candidate) =>
                        candidate !== directory &&
                        candidate.startsWith(prefix) &&
                        !candidate.slice(prefix.length).includes("/"),
                )
                .map((candidate) => path.posix.basename(candidate))
                .sort(compareCodeUnits);
        },
        getNewLine: () => "\n",
        getSourceFile: (fileName, languageVersion) => {
            const normalized = normalizeVfsPath(fileName);
            const text = files.get(normalized);
            if (text === undefined) return undefined;
            if (!sourceFiles.has(normalized)) {
                const sourceFile = ts.createSourceFile(
                    normalized,
                    text,
                    languageVersion,
                    true,
                    ts.getScriptKindFromFileName(normalized),
                );
                sourceFile.version = "0";
                sourceFiles.set(normalized, sourceFile);
            }
            return sourceFiles.get(normalized);
        },
        readFile: (fileName) => files.get(normalizeVfsPath(fileName)),
        realpath: (fileName) => normalizeVfsPath(fileName),
        useCaseSensitiveFileNames: () => true,
        writeFile: (fileName, text) => {
            const normalized = normalizeVfsPath(fileName);
            assert(
                normalized.startsWith("/outputs/") && !outputs.has(normalized),
                `T1-4 emitted an invalid output: ${normalized}`,
            );
            outputs.set(normalized, text);
        },
    };
    return { host, outputs };
};

const executeCommonJs = (javaScript, fileName) => {
    const module = { exports: {} };
    const wrapper = vm.runInNewContext(
        `(function (exports, module, require) {\n${javaScript}\n})`,
        Object.create(null),
        { filename: fileName },
    );
    wrapper(module.exports, module, (specifier) => {
        throw new Error(`Decorator fixture imported unexpected module ${specifier}.`);
    });
    return module.exports;
};

const verifyProfile = async (targetProfile) => {
    const node = targetProfile === "node";
    const sourcePath = node ? "/sources/main.cts" : "/sources/main.ts";
    const target = node ? "ES2022" : "ES2018";
    const config = JSON.stringify({
        compilerOptions: { lib: [target], target },
        files: [path.posix.basename(sourcePath)],
    });
    const parsed = parseControlledConfig(
        config,
        revision4Authority,
        targetProfile,
    );
    assert(
        parsed.ignoredProperties.length === 0,
        `${targetProfile} standard decorator config unexpectedly warned.`,
    );
    const effective = await resolveEffectiveOptions(parsed);
    assert(
        effective.revision === 4 &&
            effective.fingerprint ===
                expectedEffectiveOptionsFingerprints[targetProfile],
        `${targetProfile} revision-4 effective decorator identity drifted.`,
    );

    const legacyAttempt = parseControlledConfig(
        JSON.stringify({
            compilerOptions: {
                emitDecoratorMetadata: true,
                experimentalDecorators: true,
                lib: [target],
                target,
            },
            files: [path.posix.basename(sourcePath)],
        }),
        revision4Authority,
        targetProfile,
    );
    assert(
        exactJson(legacyAttempt.ignoredProperties, [
            {
                kind: "compiler-option",
                known: true,
                name: "emitDecoratorMetadata",
            },
            {
                kind: "compiler-option",
                known: true,
                name: "experimentalDecorators",
            },
        ]),
        `${targetProfile} legacy decorator options were not inert warning-only inputs.`,
    );
    const warnings = createWarnings(legacyAttempt.ignoredProperties);
    const legacyEffective = await resolveEffectiveOptions(legacyAttempt);
    assert(
        warnings.length === 2 &&
            warnings.every(
                (diagnostic) =>
                    diagnostic.category === ts.DiagnosticCategory.Warning &&
                    diagnostic.code === 910002,
            ) &&
            legacyEffective.fingerprint === effective.fingerprint &&
            legacyEffective.canonicalJson === effective.canonicalJson &&
            !Object.prototype.hasOwnProperty.call(
                effective.compilerOptions,
                "experimentalDecorators",
            ) &&
            !Object.prototype.hasOwnProperty.call(
                effective.compilerOptions,
                "emitDecoratorMetadata",
            ),
        `${targetProfile} legacy decorator attempt changed effective compiler semantics.`,
    );

    const declarations = profileDeclarations.get(targetProfile);
    const { host, outputs } = createSnapshotHost([
        ...standardLibraryEntries,
        ...declarations.entries,
        [sourcePath, decoratorSource],
    ]);
    const rootNames = [sourcePath, declarations.entryPath];
    const builder = ts.createIncrementalProgram({
        host,
        options: effective.compilerOptions,
        rootNames,
    });
    const program = builder.getProgram();
    assert(
        exactJson(program.getRootFileNames(), rootNames),
        `${targetProfile} decorator compiler roots drifted.`,
    );
    const errors = ts
        .getPreEmitDiagnostics(program)
        .filter((diagnostic) => diagnostic.category === ts.DiagnosticCategory.Error)
        .map((diagnostic) => ({
            code: diagnostic.code,
            message: ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n"),
            sourcePath: diagnostic.file?.fileName ?? null,
            start: diagnostic.start ?? null,
        }));
    assert(
        errors.length === 0,
        `${targetProfile} decorator diagnostics: ${JSON.stringify(errors)}`,
    );
    const emit = builder.emit();
    assert(!emit.emitSkipped, `${targetProfile} standard decorators skipped emit.`);

    const javaScriptPath = node ? "/outputs/main.cjs" : "/outputs/main.js";
    const sourceMapPath = `${javaScriptPath}.map`;
    const javaScript = outputs.get(javaScriptPath);
    const sourceMapText = outputs.get(sourceMapPath);
    assert(
        outputs.size === 2 &&
            typeof javaScript === "string" &&
            typeof sourceMapText === "string" &&
            javaScript.includes("var __esDecorate") &&
            javaScript.includes("var __runInitializers") &&
            !javaScript.includes("var __decorate") &&
            !javaScript.includes("__metadata") &&
            !javaScript.includes("Reflect.decorate"),
        `${targetProfile} did not emit the standard Stage-3 decorator dialect.`,
    );
    const sourceMap = JSON.parse(sourceMapText);
    assert(
        sourceMap.version === 3 &&
            sourceMap.file === path.posix.basename(javaScriptPath) &&
            exactJson(sourceMap.sources, [
                `../sources/${path.posix.basename(sourcePath)}`,
            ]) &&
            typeof sourceMap.mappings === "string" &&
            sourceMap.mappings.length > 0,
        `${targetProfile} decorator Source Map drifted.`,
    );
    const runtime = executeCommonJs(javaScript, javaScriptPath);
    assert(
        runtime.decoratedAnswer === 42 &&
            runtime.decoratorTrace === expectedTrace,
        `${targetProfile} decorator runtime semantics drifted: ` +
            JSON.stringify({
                decoratedAnswer: runtime.decoratedAnswer,
                decoratorTrace: runtime.decoratorTrace,
            }),
    );
    return Object.freeze({
        effectiveOptionsFingerprint: effective.fingerprint,
        outputSha256: sha256(Buffer.from(javaScript, "utf8")),
        target,
    });
};

(async () => {
    const rhino = await verifyProfile("rhino");
    const node = await verifyProfile("node");
    process.stdout.write(
        "T1-4 standard decorators verified: typed class/method contexts, " +
            `Stage-3 helpers, runtime answer=42, trace=${expectedTrace}, ` +
            "legacy/metadata options warning-ignore without fingerprint changes; " +
            `rhino=${rhino.effectiveOptionsFingerprint}, ` +
            `node=${node.effectiveOptionsFingerprint}\n`,
    );
})().catch((error) => {
    process.stderr.write(`${error.stack || error}\n`);
    process.exitCode = 1;
});
