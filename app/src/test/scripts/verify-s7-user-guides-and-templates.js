"use strict";

const fs = require("node:fs");
const path = require("node:path");

const [
    typeScriptLibArgument,
    declarationsArgument,
    templatesArgument,
    userDocsArgument,
    compilerContractArgument,
    r2ContractArgument,
    configPolicyArgument,
    nodeResolutionArgument,
] = process.argv.slice(2);

if (
    !typeScriptLibArgument ||
    !declarationsArgument ||
    !templatesArgument ||
    !userDocsArgument ||
    !compilerContractArgument ||
    !r2ContractArgument ||
    !configPolicyArgument ||
    !nodeResolutionArgument
) {
    throw new Error(
        "Usage: node verify-s7-user-guides-and-templates.js " +
            "<typescript-lib> <r2-declarations-v2> <templates> <docs/user> " +
            "<TypeScriptCompilerContract.kt> <TypeScriptR2Contract.kt> " +
            "<R2ControlledTsConfigPolicy.kt> <R2NodeResolutionKernel.kt>",
    );
}

const typeScriptLibRoot = path.resolve(typeScriptLibArgument);
const declarationsRoot = path.resolve(declarationsArgument);
const templatesRoot = path.resolve(templatesArgument);
const userDocsRoot = path.resolve(userDocsArgument);
const compilerContractPath = path.resolve(compilerContractArgument);
const r2ContractPath = path.resolve(r2ContractArgument);
const configPolicyPath = path.resolve(configPolicyArgument);
const nodeResolutionPath = path.resolve(nodeResolutionArgument);
const compilerPath = path.join(typeScriptLibRoot, "typescript.js");

const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const readUtf8 = (fileName) => fs.readFileSync(fileName, "utf8");
const normalizeVfsPath = (candidate) => {
    const normalized = path.posix.normalize(candidate.replaceAll("\\", "/"));
    return normalized.startsWith("/") ? normalized : `/${normalized}`;
};
const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;
const listFiles = (root, relative = "") => {
    const directory = path.join(root, ...relative.split("/").filter(Boolean));
    return fs
        .readdirSync(directory, { withFileTypes: true })
        .flatMap((entry) => {
            const child = relative ? `${relative}/${entry.name}` : entry.name;
            return entry.isDirectory() ? listFiles(root, child) : [child];
        })
        .sort(compareCodeUnits);
};
const exactKeys = (value, expected) =>
    value !== null &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    JSON.stringify(Object.keys(value).sort(compareCodeUnits)) ===
        JSON.stringify(expected.slice().sort(compareCodeUnits));
const blockBetween = (source, start, end) => {
    const startIndex = source.indexOf(start);
    const endIndex = source.indexOf(end, startIndex + start.length);
    assert(startIndex >= 0 && endIndex > startIndex, `Cannot find source block: ${start}`);
    return source.slice(startIndex, endIndex);
};
const uniqueMatches = (source, pattern, group = 1) =>
    Array.from(new Set(Array.from(source.matchAll(pattern), (match) => match[group])));
const requireUniqueTableEntry = (document, identity) => {
    const marker = `| \`${identity}\` |`;
    assert(
        document.split(marker).length - 1 === 1,
        `Error catalog must contain exactly one table entry for ${identity}.`,
    );
};

assert(fs.existsSync(compilerPath), `Missing TypeScript compiler: ${compilerPath}`);
assert(fs.existsSync(declarationsRoot), `Missing declarations: ${declarationsRoot}`);
assert(fs.existsSync(templatesRoot), `Missing templates: ${templatesRoot}`);
assert(fs.existsSync(userDocsRoot), `Missing user docs: ${userDocsRoot}`);
const ts = require(compilerPath);
assert(ts.version === "6.0.3", `Expected TypeScript 6.0.3, received ${ts.version}.`);

const standardLibraryEntries = fs
    .readdirSync(typeScriptLibRoot, { withFileTypes: true })
    .filter(
        (entry) =>
            entry.isFile() && /^lib(?:\..+)?\.d\.ts$/.test(entry.name),
    )
    .map((entry) => [
        `/declarations/${entry.name}`,
        readUtf8(path.join(typeScriptLibRoot, entry.name)),
    ]);
assert(
    standardLibraryEntries.some(([fileName]) => fileName === "/declarations/lib.es2018.d.ts"),
    "TypeScript ES2018 standard library is missing.",
);

const profileDeclarationEntries = (profile) => {
    const profileRoot = path.join(declarationsRoot, "layers", profile);
    const relativeFiles = listFiles(profileRoot).filter((fileName) =>
        fileName.endsWith(".d.ts"),
    );
    assert(relativeFiles.includes("index.d.ts"), `${profile} declaration index is missing.`);
    return relativeFiles.map((fileName) => [
        `/declarations/${profile}/${fileName}`,
        readUtf8(path.join(profileRoot, ...fileName.split("/"))),
    ]);
};

const profileDeclarations = Object.freeze({
    node: profileDeclarationEntries("node"),
    rhino: profileDeclarationEntries("rhino"),
});

const strictFamilyOptions = Object.freeze([
    "noImplicitAny",
    "strictNullChecks",
    "strictFunctionTypes",
    "strictBindCallApply",
    "strictPropertyInitialization",
    "strictBuiltinIteratorReturn",
    "noImplicitThis",
    "useUnknownInCatchVariables",
]);
const defaultFalseOptions = Object.freeze([
    "noUnusedLocals",
    "noUnusedParameters",
    "exactOptionalPropertyTypes",
    "noImplicitReturns",
    "noFallthroughCasesInSwitch",
    "noUncheckedIndexedAccess",
    "noImplicitOverride",
    "noPropertyAccessFromIndexSignature",
    "preserveConstEnums",
    "removeComments",
    "skipLibCheck",
]);
const controlledTargetValues = Object.freeze({
    ES2018: ts.ScriptTarget.ES2018,
    ES2019: ts.ScriptTarget.ES2019,
    ES2020: ts.ScriptTarget.ES2020,
    ES2021: ts.ScriptTarget.ES2021,
    ES2022: ts.ScriptTarget.ES2022,
});
const resolveControlledBooleanOptions = (rawCompilerOptions) => {
    const strict = rawCompilerOptions.strict ?? true;
    const resolved = { strict };
    for (const name of strictFamilyOptions) {
        resolved[name] = rawCompilerOptions[name] ?? strict;
    }
    for (const name of defaultFalseOptions) {
        resolved[name] = rawCompilerOptions[name] ?? false;
    }
    assert(
        resolved.strictNullChecks || !resolved.exactOptionalPropertyTypes,
        "Template enables exactOptionalPropertyTypes without strictNullChecks.",
    );
    assert(
        resolved.strictNullChecks || !resolved.strictPropertyInitialization,
        "Template enables strictPropertyInitialization without strictNullChecks.",
    );
    return resolved;
};
const resolveControlledTargetAndLibraries = (rawCompilerOptions, profile) => {
    const targetName = (rawCompilerOptions.target ?? "ES2018").toUpperCase();
    const allowedTargetNames =
        profile === "node" ? Object.keys(controlledTargetValues) : ["ES2018"];
    assert(
        allowedTargetNames.includes(targetName),
        `${profile} template selects unsupported target ${rawCompilerOptions.target}.`,
    );
    const rawLibraries = rawCompilerOptions.lib ?? [targetName];
    assert(
        Array.isArray(rawLibraries) && rawLibraries.length > 0,
        `${profile} template must select at least one controlled library.`,
    );
    const libraries = Array.from(
        new Set(
            rawLibraries.map((rawLibrary) => {
                assert(
                    typeof rawLibrary === "string" &&
                        allowedTargetNames.includes(rawLibrary.toUpperCase()),
                    `${profile} template selects unsupported library ${rawLibrary}.`,
                );
                return `lib.${rawLibrary.toLowerCase()}.d.ts`;
            }),
        ),
    ).sort(compareCodeUnits);
    return Object.freeze({
        libraries: Object.freeze(libraries),
        target: controlledTargetValues[targetName],
    });
};
const controlledPathAliasCompilerOptions = (rawCompilerOptions) => {
    if (rawCompilerOptions.baseUrl === undefined && rawCompilerOptions.paths === undefined) {
        return Object.freeze({});
    }
    const baseUrl = rawCompilerOptions.baseUrl ?? ".";
    const paths = rawCompilerOptions.paths ?? {};
    assert(
        typeof baseUrl === "string" &&
            paths !== null &&
            typeof paths === "object" &&
            !Array.isArray(paths),
        "Template path-alias options are not revision-4 values.",
    );
    return Object.freeze({
        baseUrl: path.posix.resolve("/sources", baseUrl),
        paths,
    });
};

const controlledRhinoCompilerOptions = (rawCompilerOptions = {}) => {
    const resolved = resolveControlledBooleanOptions(rawCompilerOptions);
    const targetAndLibraries = resolveControlledTargetAndLibraries(
        rawCompilerOptions,
        "rhino",
    );
    return Object.freeze({
        ...controlledPathAliasCompilerOptions(rawCompilerOptions),
        allowJs: false,
        allowSyntheticDefaultImports: true,
        alwaysStrict: true,
        checkJs: false,
        esModuleInterop: true,
        exactOptionalPropertyTypes: resolved.exactOptionalPropertyTypes,
        forceConsistentCasingInFileNames: true,
        ignoreDeprecations: "6.0",
        incremental: false,
        inlineSourceMap: false,
        inlineSources: false,
        jsx: ts.JsxEmit.React,
        jsxFactory: "__autojs6Tsx",
        jsxFragmentFactory: "__autojs6TsxFragment",
        lib: targetAndLibraries.libraries,
        module: ts.ModuleKind.CommonJS,
        moduleDetection: ts.ModuleDetectionKind.Auto,
        moduleResolution: ts.ModuleResolutionKind.Node10,
        newLine: ts.NewLineKind.LineFeed,
        noEmitOnError: true,
        noFallthroughCasesInSwitch: resolved.noFallthroughCasesInSwitch,
        noImplicitAny: resolved.noImplicitAny,
        noImplicitOverride: resolved.noImplicitOverride,
        noImplicitReturns: resolved.noImplicitReturns,
        noImplicitThis: resolved.noImplicitThis,
        noLib: false,
        noPropertyAccessFromIndexSignature:
            resolved.noPropertyAccessFromIndexSignature,
        preserveConstEnums: resolved.preserveConstEnums,
        removeComments: resolved.removeComments,
        noUncheckedIndexedAccess: resolved.noUncheckedIndexedAccess,
        noUnusedLocals: resolved.noUnusedLocals,
        noUnusedParameters: resolved.noUnusedParameters,
        outDir: "/outputs",
        resolveJsonModule: false,
        rootDir: "/sources",
        skipLibCheck: resolved.skipLibCheck,
        sourceMap: true,
        strict: resolved.strict,
        strictBindCallApply: resolved.strictBindCallApply,
        strictBuiltinIteratorReturn: resolved.strictBuiltinIteratorReturn,
        strictFunctionTypes: resolved.strictFunctionTypes,
        strictNullChecks: resolved.strictNullChecks,
        strictPropertyInitialization: resolved.strictPropertyInitialization,
        target: targetAndLibraries.target,
        types: [],
        useDefineForClassFields: false,
        useUnknownInCatchVariables: resolved.useUnknownInCatchVariables,
    });
};
const controlledNodeCompilerOptions = (rawCompilerOptions = {}) => {
    const resolved = resolveControlledBooleanOptions(rawCompilerOptions);
    const targetAndLibraries = resolveControlledTargetAndLibraries(
        rawCompilerOptions,
        "node",
    );
    return Object.freeze({
        ...controlledPathAliasCompilerOptions(rawCompilerOptions),
        allowJs: false,
        allowSyntheticDefaultImports: true,
        alwaysStrict: true,
        checkJs: false,
        esModuleInterop: true,
        exactOptionalPropertyTypes: resolved.exactOptionalPropertyTypes,
        forceConsistentCasingInFileNames: true,
        ignoreDeprecations: "6.0",
        incremental: false,
        inlineSourceMap: false,
        inlineSources: false,
        lib: targetAndLibraries.libraries,
        module: ts.ModuleKind.NodeNext,
        moduleDetection: ts.ModuleDetectionKind.Force,
        moduleResolution: ts.ModuleResolutionKind.NodeNext,
        newLine: ts.NewLineKind.LineFeed,
        noEmitOnError: true,
        noFallthroughCasesInSwitch: resolved.noFallthroughCasesInSwitch,
        noImplicitAny: resolved.noImplicitAny,
        noImplicitOverride: resolved.noImplicitOverride,
        noImplicitReturns: resolved.noImplicitReturns,
        noImplicitThis: resolved.noImplicitThis,
        noLib: false,
        noPropertyAccessFromIndexSignature:
            resolved.noPropertyAccessFromIndexSignature,
        noUncheckedIndexedAccess: resolved.noUncheckedIndexedAccess,
        noUnusedLocals: resolved.noUnusedLocals,
        noUnusedParameters: resolved.noUnusedParameters,
        outDir: "/outputs",
        preserveConstEnums: resolved.preserveConstEnums,
        removeComments: resolved.removeComments,
        resolveJsonModule: true,
        rootDir: "/sources",
        skipLibCheck: resolved.skipLibCheck,
        sourceMap: true,
        strict: resolved.strict,
        strictBindCallApply: resolved.strictBindCallApply,
        strictBuiltinIteratorReturn: resolved.strictBuiltinIteratorReturn,
        strictFunctionTypes: resolved.strictFunctionTypes,
        strictNullChecks: resolved.strictNullChecks,
        strictPropertyInitialization: resolved.strictPropertyInitialization,
        target: targetAndLibraries.target,
        types: [],
        useDefineForClassFields:
            targetAndLibraries.target >= ts.ScriptTarget.ES2022,
        useUnknownInCatchVariables: resolved.useUnknownInCatchVariables,
    });
};

const createVfsCompilerHost = (entries, outputs, options) => {
    const files = new Map(
        entries.map(([fileName, text]) => [normalizeVfsPath(fileName), text]),
    );
    const directories = new Set(["/"]);
    for (const fileName of files.keys()) {
        let directory = path.posix.dirname(fileName);
        while (!directories.has(directory)) {
            directories.add(directory);
            directory = path.posix.dirname(directory);
        }
    }
    const sourceFiles = new Map();
    return {
        directoryExists: (directoryName) =>
            directories.has(normalizeVfsPath(directoryName)),
        fileExists: (fileName) => files.has(normalizeVfsPath(fileName)),
        getCanonicalFileName: (fileName) => fileName,
        getCurrentDirectory: () => "/",
        // The shipped Worker deliberately roots lib.es2018.d.ts, not TypeScript's
        // browser-oriented lib.es2018.full.d.ts (which would inject DOM globals).
        getDefaultLibFileName: () => "/declarations/lib.es2018.d.ts",
        getDefaultLibLocation: () => "/declarations",
        getDirectories: (directoryName) => {
            const directory = normalizeVfsPath(directoryName);
            const prefix = directory === "/" ? "/" : `${directory}/`;
            return Array.from(directories)
                .filter(
                    (candidate) =>
                        candidate.startsWith(prefix) &&
                        candidate !== directory &&
                        !candidate.slice(prefix.length).includes("/"),
                )
                .map((candidate) => path.posix.basename(candidate))
                .sort(compareCodeUnits);
        },
        getNewLine: () => "\n",
        getSourceFile: (fileName, languageVersion) => {
            const canonical = normalizeVfsPath(fileName);
            if (!files.has(canonical)) return undefined;
            if (!sourceFiles.has(canonical)) {
                const sourceFile = ts.createSourceFile(
                    canonical,
                    files.get(canonical),
                    languageVersion,
                    true,
                    ts.getScriptKindFromFileName(canonical),
                );
                sourceFile.version = "0";
                sourceFiles.set(canonical, sourceFile);
            }
            return sourceFiles.get(canonical);
        },
        readFile: (fileName) => files.get(normalizeVfsPath(fileName)),
        realpath: (fileName) => normalizeVfsPath(fileName),
        useCaseSensitiveFileNames: () => true,
        writeFile: (fileName, text) => {
            const canonical = normalizeVfsPath(fileName);
            assert(
                canonical.startsWith("/outputs/") && !outputs.has(canonical),
                `TypeScript emitted an unauthorized/duplicate output: ${canonical}`,
            );
            outputs.set(canonical, text);
        },
    };
};

const formatDiagnostic = (diagnostic) => {
    const fileName = diagnostic.file?.fileName ?? "<global>";
    const location =
        diagnostic.file && diagnostic.start !== undefined
            ? diagnostic.file.getLineAndCharacterOfPosition(diagnostic.start)
            : null;
    return (
        `${fileName}` +
        (location === null
            ? ""
            : `:${location.line + 1}:${location.character + 1}`) +
        ` TS${diagnostic.code}: ` +
        ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n")
    );
};

const compileProject = ({ label, profile, sourceEntries, rootNames, options }) => {
    const outputs = new Map();
    const declarationEntry = `/declarations/${profile}/index.d.ts`;
    const entries = [
        ...standardLibraryEntries,
        ...profileDeclarations[profile],
        ...sourceEntries,
    ];
    const host = createVfsCompilerHost(entries, outputs, options);
    const program = ts.createProgram({
        host,
        options,
        rootNames: [declarationEntry, ...rootNames],
    });
    const preEmitDiagnostics = ts.getPreEmitDiagnostics(program);
    const emitResult = program.emit();
    const diagnostics = [...preEmitDiagnostics, ...emitResult.diagnostics].filter(
        (diagnostic, index, all) =>
            all.findIndex(
                (candidate) =>
                    candidate.code === diagnostic.code &&
                    candidate.start === diagnostic.start &&
                    candidate.file?.fileName === diagnostic.file?.fileName &&
                    ts.flattenDiagnosticMessageText(candidate.messageText, "\n") ===
                        ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n"),
            ) === index,
    );
    assert(
        diagnostics.length === 0,
        `${label} failed TypeScript compilation:\n${diagnostics
            .map(formatDiagnostic)
            .join("\n")}`,
    );
    assert(!emitResult.emitSkipped, `${label} unexpectedly skipped emit.`);

    const javaScriptOutputs = Array.from(outputs.keys()).filter((fileName) =>
        /\.(?:js|mjs|cjs)$/.test(fileName),
    );
    const sourceMapOutputs = Array.from(outputs.keys()).filter((fileName) =>
        fileName.endsWith(".map"),
    );
    assert(
        javaScriptOutputs.length === rootNames.length &&
            sourceMapOutputs.length === rootNames.length,
        `${label} emitted ${javaScriptOutputs.length} JS and ${sourceMapOutputs.length} maps ` +
            `for ${rootNames.length} sources.`,
    );
    for (const javaScriptPath of javaScriptOutputs) {
        const mapPath = `${javaScriptPath}.map`;
        assert(outputs.has(mapPath), `${label} has no paired map for ${javaScriptPath}.`);
        const javaScript = outputs.get(javaScriptPath);
        const sourceMap = JSON.parse(outputs.get(mapPath));
        assert(
            javaScript.includes(`//# sourceMappingURL=${path.posix.basename(mapPath)}`),
            `${label} JavaScript has no external sourceMappingURL: ${javaScriptPath}.`,
        );
        assert(
            sourceMap.version === 3 &&
                sourceMap.file === path.posix.basename(javaScriptPath) &&
                Array.isArray(sourceMap.sources) &&
                sourceMap.sources.length === 1 &&
                !("sourcesContent" in sourceMap),
            `${label} emitted a non-contract Source Map: ${mapPath}.`,
        );
    }
    return outputs;
};

const configPolicySource = readUtf8(configPolicyPath);
const legacyAllowedBooleanBlock = blockBetween(
    configPolicySource,
    "val allowedBooleanCompilerOptions",
    "val revision3AllowedBooleanCompilerOptions",
);
const legacyAllowedBooleanOptions = uniqueMatches(
    legacyAllowedBooleanBlock,
    /"([A-Za-z0-9]+)"\s+to\s+R2ControlledTsConfigDefault/g,
);
assert(
    legacyAllowedBooleanOptions.length === 18,
    "Controlled tsconfig revision 2 no longer has 18 booleans.",
);
const revision3BooleanAdditions = uniqueMatches(
    blockBetween(
        configPolicySource,
        "val revision3AllowedBooleanCompilerOptions",
        "val revision3AllowedValueCompilerOptions",
    ),
    /"([A-Za-z0-9]+)"\s+to\s+R2ControlledTsConfigDefault/g,
);
const allowedBooleanOptions = Array.from(
    new Set([...legacyAllowedBooleanOptions, ...revision3BooleanAdditions]),
).sort(compareCodeUnits);
assert(allowedBooleanOptions.length === 20, "Controlled tsconfig revision 3 must have 20 booleans.");
const allowedValueOptions = ["baseUrl", "lib", "paths", "target"];
const policySet = (start, end) =>
    uniqueMatches(blockBetween(configPolicySource, start, end), /"([A-Za-z0-9]+)"/g);
const profileManagedOptions = policySet(
    "val profileManagedCompilerOptions",
    "val escapeForbiddenCompilerOptions",
);
const escapeForbiddenOptions = policySet(
    "val escapeForbiddenCompilerOptions",
    "val outputForbiddenCompilerOptions",
);
const outputForbiddenOptions = policySet(
    "val outputForbiddenCompilerOptions",
    "val customTransformerCompilerOptions",
);
const customTransformerOptions = policySet(
    "val customTransformerCompilerOptions",
    "val policyCodes",
);

const templateSets = fs
    .readdirSync(templatesRoot, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort(compareCodeUnits);
assert(
    JSON.stringify(templateSets) ===
        JSON.stringify(["node-esm-cjs", "rhino-multi-file", "rhino-single-ui"]),
    `S7 must expose exactly three template sets, received ${templateSets.join(", ")}.`,
);

const projects = [
    {
        apiMarkers: ["ui.layout(", "console.log(", "toast("],
        entry: "main.tsx",
        id: "rhino-single-ui",
        packageType: null,
        profile: "rhino",
        relativeRoot: "rhino-single-ui",
    },
    {
        apiMarkers: [
            'from "@/lib/device-report"',
            'from "@/lib/format-report"',
            "device.summary()",
            "console.log(",
            "toast(",
        ],
        entry: "main.ts",
        id: "rhino-multi-file",
        packageType: null,
        profile: "rhino",
        relativeRoot: "rhino-multi-file",
    },
    {
        apiMarkers: [
            "toast.showToast(",
            "app.isInstalled(",
            "device.isScreenOn(",
            "console.log(",
        ],
        entry: "main.mts",
        id: "node-esm",
        packageType: "module",
        profile: "node",
        relativeRoot: "node-esm-cjs/esm",
    },
    {
        apiMarkers: [
            "toast.showToast(",
            "app.isInstalled(",
            "device.isScreenOn(",
            "console.log(",
        ],
        entry: "main.cts",
        id: "node-cjs",
        packageType: "commonjs",
        profile: "node",
        relativeRoot: "node-esm-cjs/cjs",
    },
];

for (const fixture of projects) {
    const projectRoot = path.join(
        templatesRoot,
        ...fixture.relativeRoot.split("/"),
    );
    const projectFiles = listFiles(projectRoot);
    assert(
        projectFiles.includes("project.json") &&
            projectFiles.includes("tsconfig.json") &&
            projectFiles.includes(fixture.entry),
        `${fixture.id} is missing project.json, tsconfig.json, or its entry.`,
    );
    assert(
        !projectFiles.some(
            (fileName) =>
                fileName === "node_modules" || fileName.startsWith("node_modules/"),
        ),
        `${fixture.id} must not contain node_modules.`,
    );
    const project = JSON.parse(readUtf8(path.join(projectRoot, "project.json")));
    assert(project.main === fixture.entry, `${fixture.id} project main drifted.`);
    if (fixture.profile === "node") {
        assert(
            project.type === "node" &&
                exactKeys(project.node, ["backend", "timeoutMs", "permissions"]) &&
                project.node.backend === "embedded" &&
                project.node.timeoutMs === 30000 &&
                JSON.stringify(project.node.permissions) ===
                    JSON.stringify(["toast", "app", "device"]),
            `${fixture.id} Node project authority drifted.`,
        );
        const packageFiles = projectFiles.filter((fileName) =>
            fileName.endsWith("package.json"),
        );
        assert(
            JSON.stringify(packageFiles) === JSON.stringify(["package.json"]),
            `${fixture.id} must contain one root package.json and no nested boundary.`,
        );
        const packageMetadata = JSON.parse(
            readUtf8(path.join(projectRoot, "package.json")),
        );
        assert(
            exactKeys(packageMetadata, ["type"]) &&
                packageMetadata.type === fixture.packageType,
            `${fixture.id} package type drifted.`,
        );
    } else {
        assert(project.type !== "node", `${fixture.id} unexpectedly selects Node.`);
        assert(
            !projectFiles.some((fileName) => fileName.endsWith("package.json")),
            `${fixture.id} Rhino template must not add a package boundary.`,
        );
    }

    const tsconfig = JSON.parse(readUtf8(path.join(projectRoot, "tsconfig.json")));
    assert(
        tsconfig !== null && typeof tsconfig === "object" && !Array.isArray(tsconfig),
        `${fixture.id} tsconfig is not an object.`,
    );
    const sourceFiles = projectFiles.filter((fileName) =>
        /\.(?:ts|tsx|mts|cts)$/.test(fileName),
    );
    const combinedSource = sourceFiles
        .map((fileName) => readUtf8(path.join(projectRoot, ...fileName.split("/"))))
        .join("\n");
    for (const marker of fixture.apiMarkers) {
        assert(combinedSource.includes(marker), `${fixture.id} misses API call ${marker}.`);
    }

    const configuredOptions = tsconfig.compilerOptions ?? {};
    for (const [name, value] of Object.entries(configuredOptions)) {
        assert(
            (allowedBooleanOptions.includes(name) && typeof value === "boolean") ||
                (name === "target" && typeof value === "string") ||
                (name === "lib" && Array.isArray(value)) ||
                (name === "baseUrl" && typeof value === "string") ||
                (name === "paths" &&
                    value !== null &&
                    typeof value === "object" &&
                    !Array.isArray(value) &&
                    Object.values(value).every(Array.isArray)),
            `${fixture.id} uses a non-controlled revision-4 compiler option: ${name}.`,
        );
    }
    const options =
        fixture.profile === "rhino"
            ? controlledRhinoCompilerOptions(configuredOptions)
            : controlledNodeCompilerOptions(configuredOptions);
    const sourceEntries = sourceFiles.map((fileName) => [
        `/sources/${fileName}`,
        readUtf8(path.join(projectRoot, ...fileName.split("/"))),
    ]);
    if (fixture.profile === "node") {
        sourceEntries.push([
            "/sources/package.json",
            readUtf8(path.join(projectRoot, "package.json")),
        ]);
    }
    compileProject({
        label: fixture.id,
        options,
        profile: fixture.profile,
        rootNames: sourceFiles.map((fileName) => `/sources/${fileName}`),
        sourceEntries,
    });
}

const quickStartPath = path.join(userDocsRoot, "QUICK_START.md");
const configReferencePath = path.join(userDocsRoot, "CONFIGURATION.md");
const errorCatalogPath = path.join(userDocsRoot, "ERRORS.md");
const sourceMapsPath = path.join(userDocsRoot, "SOURCE_MAPS.md");
for (const documentPath of [
    quickStartPath,
    configReferencePath,
    errorCatalogPath,
    sourceMapsPath,
]) {
    assert(fs.existsSync(documentPath), `Missing S7 user document: ${documentPath}`);
}

const quickStart = readUtf8(quickStartPath);
const snippetPattern =
    /<!-- verify-s7:(rhino|node):([^ >]+) -->\s*```(?:ts|tsx)\r?\n([\s\S]*?)\r?\n```/g;
const snippets = Array.from(quickStart.matchAll(snippetPattern), (match) => ({
    fileName: match[2],
    profile: match[1],
    source: match[3],
}));
assert(
    JSON.stringify(snippets.map((snippet) => snippet.fileName)) ===
        JSON.stringify([
            "hello.ts",
            "decorators.ts",
            "ui-string.ts",
            "ui-native.tsx",
        ]),
    "Quick-start verified snippet inventory drifted.",
);
for (const snippet of snippets) {
    const options =
        snippet.profile === "rhino"
            ? controlledRhinoCompilerOptions()
            : controlledNodeCompilerOptions();
    compileProject({
        label: `quick-start/${snippet.fileName}`,
        options,
        profile: snippet.profile,
        rootNames: [`/sources/${snippet.fileName}`],
        sourceEntries: [[`/sources/${snippet.fileName}`, snippet.source]],
    });
}
assert(
    [".ts", ".tsx", ".mts", ".cts", '"type": "module"', '"type": "commonjs"'].every(
        (marker) => quickStart.includes(marker),
    ),
    "Quick-start profile/extension matrix is incomplete.",
);

const configurationReference = readUtf8(configReferencePath);
for (const name of [
    ...allowedBooleanOptions,
    ...allowedValueOptions,
    ...profileManagedOptions,
    ...escapeForbiddenOptions,
    ...outputForbiddenOptions,
    ...customTransformerOptions,
]) {
    assert(
        configurationReference.includes(`\`${name}\``),
        `Configuration reference does not classify ${name}.`,
    );
}
for (const marker of [
    "48 KiB",
    "512",
    "4096",
    "8 MiB",
    "16 MiB",
    "64 MiB",
    "512 KiB",
    "910001",
    "910002",
    "configKind=\"tsconfig\"",
    "configKind=\"none\"",
    "configPolicyRevision=4",
    "4a9a5e70150f81e1ec9a5e2f749cc081f93f207f092300c96a79abfdcc152dab",
    "1f77d37080466f2752176fb5aa1b96e9a3f3dbdc89b853f30b4a0fed4313e90a",
    "64ef1cf36d6531f84088cec49127e6407b107ab86eeaf7f4512358fd1343598b",
]) {
    assert(
        configurationReference.includes(marker),
        `Configuration reference misses contract marker ${marker}.`,
    );
}

const compilerContractSource = readUtf8(compilerContractPath);
const r2ContractSource = readUtf8(r2ContractPath);
const nodeResolutionSource = readUtf8(nodeResolutionPath);
const errorCatalog = readUtf8(errorCatalogPath);
const compilerTerminalCodes = uniqueMatches(
    compilerContractSource,
    /"(ERR_AUTOJS6_TYPESCRIPT_[A-Z0-9_]+)"/g,
);
assert(compilerTerminalCodes.length === 13, "Compiler terminal error catalog drifted.");
const hostRuntimeCodes = [
    "ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED",
    "ERR_AUTOJS6_TYPESCRIPT_SNAPSHOT_MODULE_NOT_FOUND",
    "ERR_AUTOJS6_TYPESCRIPT_SNAPSHOT_MODULE_AMBIGUOUS",
    "ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION",
];
for (const code of [...compilerTerminalCodes, ...hostRuntimeCodes]) {
    requireUniqueTableEntry(errorCatalog, code);
}

const configDetailCodes = uniqueMatches(
    r2ContractSource,
    /CONFIG_POLICY_CODE_[A-Z_]+\s*=\s*"(CONFIG_[A-Z0-9_]+)"/g,
);
assert(configDetailCodes.length === 11, "Config detail error catalog drifted.");
for (const code of configDetailCodes) requireUniqueTableEntry(errorCatalog, code);
const warningCodes = uniqueMatches(
    r2ContractSource,
    /CONFIG_DIAGNOSTIC_CODE_[A-Z_]+\s*=\s*([0-9_]+)/g,
).map((value) => value.replaceAll("_", ""));
assert(
    JSON.stringify(warningCodes) === JSON.stringify(["910001", "910002"]),
    "Controlled-config warning identities drifted.",
);
for (const code of warningCodes) requireUniqueTableEntry(errorCatalog, code);

const resolutionPairs = Array.from(
    nodeResolutionSource.matchAll(
        /\w+\("(NODE_RESOLUTION_[A-Z0-9_]+)",\s*([0-9_]+)\)/g,
    ),
    (match) => ({ identity: match[1], code: match[2].replaceAll("_", "") }),
);
assert(resolutionPairs.length === 18, "Node resolution failure catalog drifted.");
for (const pair of resolutionPairs) {
    requireUniqueTableEntry(errorCatalog, pair.identity);
    assert(
        errorCatalog.includes(
            `| \`${pair.identity}\` | \`${pair.code}\` |`,
        ),
        `Node resolution pair is missing: ${pair.identity}/${pair.code}.`,
    );
}

const sourceMaps = readUtf8(sourceMapsPath);
for (const marker of [
    "Node 运行时 stack",
    "Rhino 运行时 stack",
    "column=null",
    "sourcesContent",
    "inlineSources=false",
    "无网络权限",
    "不记录源码正文",
]) {
    assert(sourceMaps.includes(marker), `Source Map contract misses ${marker}.`);
}

const markdownRoots = [userDocsRoot, templatesRoot];
for (const markdownRoot of markdownRoots) {
    for (const relativePath of listFiles(markdownRoot).filter((fileName) =>
        fileName.endsWith(".md"),
    )) {
        const documentPath = path.join(markdownRoot, ...relativePath.split("/"));
        const document = readUtf8(documentPath);
        for (const match of document.matchAll(/\]\(([^)]+)\)/g)) {
            const target = match[1].trim().replace(/^<|>$/g, "");
            if (/^(?:https?:|mailto:|#)/.test(target)) continue;
            const withoutAnchor = target.split("#", 1)[0];
            const absoluteTarget = path.resolve(
                path.dirname(documentPath),
                decodeURIComponent(withoutAnchor),
            );
            assert(
                fs.existsSync(absoluteTarget),
                `${documentPath} contains a broken local link: ${target}.`,
            );
        }
    }
}

const nodeBundleReadme = readUtf8(path.join(templatesRoot, "node-esm-cjs", "README.md"));
assert(
    nodeBundleReadme.includes("controlled tsconfig revision 4") &&
        nodeBundleReadme.includes("运行时真实消费") &&
        nodeBundleReadme.includes("ES2022"),
    "Node template does not disclose its runtime controlled-config authority.",
);

console.log(
    "S7 user guides/templates verified: 4 docs, 3 template sets/4 runnable projects, " +
        `${snippets.length} copy-ready snippets, ${compilerTerminalCodes.length + hostRuntimeCodes.length} ` +
        `terminal errors, ${configDetailCodes.length} config details, ` +
        `${resolutionPairs.length} resolver pairs, TypeScript ${ts.version}.`,
);
