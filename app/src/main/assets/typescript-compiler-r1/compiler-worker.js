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

const bytesToSha256 = async (bytes) => {
    const digest = await crypto.subtle.digest("SHA-256", bytes);
    return Array.from(new Uint8Array(digest))
        .map((value) => value.toString(16).padStart(2, "0"))
        .join("");
};

const sha256 = async (text) =>
    bytesToSha256(new TextEncoder().encode(text));

// R2-S3 transfers project input and result bytes with stop-and-wait framing.
// The Worker still owns an independent materialization bound: transport
// framing must never turn the frozen archive limits into an allocation limit.
const projectFrameBytes = 64 * 1024;
const maximumProjectFiles = 4_607;
const maximumProjectFileBytes = 8 * 1024 * 1024;
const maximumProjectBytes = 72 * 1024 * 1024;
const inputArchiveAuthority = Object.freeze({
    inventoryCanonicalPrefix: "autojs6.typescript.input-inventory.v1\n",
    maximumFiles: 512,
    maximumSingleFileBytes: 8 * 1024 * 1024,
    maximumTotalBytes: 8 * 1024 * 1024,
    quotaRevision: 2,
    schemaRevision: 1,
});
// Direct historical Worker fixtures may still replay revision 1, whose quota
// covered the complete retained inventory including declarations.
const legacyRetainedInputQuotaAuthority = Object.freeze({
    maximumFiles: 512,
    maximumSingleFileBytes: 8 * 1024 * 1024,
    maximumTotalBytes: 8 * 1024 * 1024,
    quotaRevision: 1,
});
const ordinaryRetainedInputGuardrail = Object.freeze({
    maximumFiles: maximumProjectFiles,
    maximumSingleFileBytes: maximumProjectFileBytes,
    maximumTotalBytes: maximumProjectBytes,
});
// This sentinel can only be emitted by the guarded native test/evidence path.
// Public request manifests are pinned to ordinary caller-input quota revision 2.
const testOnlyExpandedInputQuotaAuthority = Object.freeze({
    maximumFiles: maximumProjectFiles,
    maximumSingleFileBytes: maximumProjectFileBytes,
    maximumTotalBytes: maximumProjectBytes,
    quotaRevision: 2_147_483_647,
});
const maximumVfsPathChars = 4096;
const maximumVfsPathDepth = 64;
const maximumProjectPathChars = 8 * 1024 * 1024;
const maximumProjectDirectoryNodes = 65_536;
const maximumProjectDirectoryChars = 4 * 1024 * 1024;
const maximumTokensPerFile = 65_536;
const maximumProjectTokens = 262_144;
const maximumOutputFiles = 512;
const maximumOutputBytes = 64 * 1024 * 1024;
const maximumJavaScriptBytes = 16 * 1024 * 1024;
const maximumSourceMapBytes = 16 * 1024 * 1024;
const maximumDiagnostics = 1000;
const maximumDiagnosticsBytes = 512 * 1024;
const maximumRelatedInformationPerDiagnostic = 1000;
const maximumRelatedInformationTotal = 1000;
const maximumProjectResultBytes = 144 * 1024 * 1024;
const sha256Pattern = /^[0-9a-f]{64}$/;
const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;
const asciiCaseFold = (value) =>
    value.replace(/[A-Z]/g, (character) =>
        String.fromCharCode(character.charCodeAt(0) + 0x20),
    );
const isSafeUnicodePathText = (value) => {
    for (let index = 0; index < value.length; index += 1) {
        const code = value.charCodeAt(index);
        if (code <= 0x1f || (code >= 0x7f && code <= 0x9f)) {
            return false;
        }
        if (code >= 0xd800 && code <= 0xdbff) {
            if (index + 1 >= value.length) return false;
            const trailing = value.charCodeAt(index + 1);
            if (trailing < 0xdc00 || trailing > 0xdfff) return false;
            index += 1;
        } else if (code >= 0xdc00 && code <= 0xdfff) {
            return false;
        }
    }
    return true;
};
const nodeRevisionTwoCanonicalPath = (value) =>
    Array.from(new TextEncoder().encode(value))
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
const inputInventoryDomainOrder = Object.freeze({
    source: 0,
    declaration: 1,
    config: 2,
    "package-metadata": 3,
    "package-target-v1": 4,
    "package-target-v2": 5,
});
const deriveInputInventoryFingerprint = async (records) => {
    const ordered = records.slice().sort((left, right) => {
        const domainOrder =
            inputInventoryDomainOrder[left.domain] -
            inputInventoryDomainOrder[right.domain];
        return domainOrder !== 0
            ? domainOrder
            : compareCodeUnits(left.path, right.path);
    });
    let canonical = inputArchiveAuthority.inventoryCanonicalPrefix;
    for (const record of ordered) {
        canonical +=
            `${record.domain}\u0000${record.path}\u0000${record.byteLength}` +
            `\u0000${record.sha256}\n`;
    }
    return sha256(canonical);
};
const isExecutableProjectSourcePath = (path) =>
    path.startsWith("/sources/") &&
    (path.endsWith(".ts") || path.endsWith(".tsx")) &&
    !path.endsWith(".d.ts");
const isNodeRevisionTwoExecutableSourcePath = (path) =>
    path.startsWith("/sources/") &&
    (path.endsWith(".ts") || path.endsWith(".mts") || path.endsWith(".cts")) &&
    !path.endsWith(".d.ts") &&
    !path.endsWith(".d.mts") &&
    !path.endsWith(".d.cts");
const isNodeRevisionTwoRuntimeSupportSourcePath = (path) =>
    path.startsWith("/sources/") &&
    (
        path.endsWith(".js") ||
        path.endsWith(".mjs") ||
        path.endsWith(".cjs") ||
        path.endsWith(".json")
    );
const expectedGeneratedModulePath = (sourcePath, nodeRevisionTwo) => {
    if (
        typeof sourcePath !== "string" ||
        !(
            nodeRevisionTwo
                ? isNodeRevisionTwoExecutableSourcePath(sourcePath)
                : isExecutableProjectSourcePath(sourcePath)
        )
    ) {
        return null;
    }
    const sourceExtension = nodeRevisionTwo && sourcePath.endsWith(".mts")
        ? ".mts"
        : nodeRevisionTwo && sourcePath.endsWith(".cts")
          ? ".cts"
          : sourcePath.endsWith(".tsx")
            ? ".tsx"
            : ".ts";
    const outputExtension = sourceExtension === ".mts"
        ? ".mjs"
        : sourceExtension === ".cts"
          ? ".cjs"
          : ".js";
    return `/outputs/${sourcePath.slice(
        "/sources/".length,
        -sourceExtension.length,
    )}${outputExtension}`;
};
const isNodeRevisionTwoDeclarationPath = (path) =>
    path.endsWith(".d.ts") || path.endsWith(".d.mts") || path.endsWith(".d.cts");
const isNodeRevisionTwoResolutionPath = (path) =>
    isNodeRevisionTwoDeclarationPath(path) ||
    (
        (path.endsWith(".ts") || path.endsWith(".mts") || path.endsWith(".cts")) &&
        !path.endsWith(".d.ts") &&
        !path.endsWith(".d.mts") &&
        !path.endsWith(".d.cts")
    );
const provisionalRhinoProfileAuthority = Object.freeze({
    configKind: "none",
    configSchemaRevision: 1,
    profileRevision: 1,
    profileSchemaRevision: 1,
    targetProfile: "rhino",
});
const provisionalRhinoRevision2ProfileAuthority = Object.freeze({
    configKind: "none",
    configSchemaRevision: 1,
    profileRevision: 2,
    profileSchemaRevision: 1,
    runtimeCapabilitiesFingerprint:
        "49374b84060ce634472aa2db34d960892501adebb7a6b7ba3a767018a81b3d25",
    targetProfile: "rhino",
});
const provisionalNodeNextProfileAuthority = Object.freeze({
    configKind: "none",
    configSchemaRevision: 1,
    profileRevision: 1,
    profileSchemaRevision: 1,
    targetProfile: "node",
});
const provisionalNodeNextRevision2ProfileAuthority = Object.freeze({
    configKind: "none",
    configSchemaRevision: 1,
    profileRevision: 2,
    profileSchemaRevision: 1,
    runtimeCapabilitiesFingerprint:
        "a19e8da1a4ab5961aada8d49e1f70eef847b84a0a8f348b6f784f43dc26e2d3a",
    targetProfile: "node",
});
const nodePackageFormatAuthority = Object.freeze({
    canonicalPrefix: "autojs6.typescript.node-package-format.catalog.v1",
    catalogRevision: 1,
    nodeModuleOutputPolicyFingerprint:
        "5c2294f0ee9d6d25bd2132b4ea24866d0a5f9caaa521a63bf81ffe1d897f6ce7",
    nodeModuleOutputPolicyRevision: 1,
    nodeModuleOutputPolicySchemaRevision: 1,
    schemaRevision: 1,
});
const nodePackageFormatPolicyCodes = Object.freeze({
    packageTypeInvalid: "NODE_PACKAGE_TYPE_INVALID",
    programNotActivated: "P2_NODE_PROGRAM_NOT_ACTIVATED",
});
const nodeRevisionTwoResolutionAuthority = Object.freeze({
    policyRevision: 2,
    snapshotCanonicalPrefix:
        "autojs6.typescript.node-resolution.snapshot-targets.v2",
    targetUniverseCanonicalPrefix:
        "autojs6.typescript.node-resolution.target-universe.v2",
});
const nodeRevisionTwoPackageTargetAuthority = Object.freeze({
    allowedDeclarationSuffixes: Object.freeze([".d.ts", ".d.mts", ".d.cts"]),
    allowedExecutableSuffixes: Object.freeze([".ts", ".mts", ".cts"]),
    canonicalPrefix: "autojs6.typescript.node-package-target.inventory.v2",
    dependencyRootPrefix: "packages/",
    maximumAggregatePathChars: 32 * 1024,
    maximumFileBytes: 8 * 1024 * 1024,
    maximumFiles: 256,
    maximumPathChars: 1024,
    maximumTotalBytes: 8 * 1024 * 1024,
    policyFingerprint:
        "175cc694dae3e925d66eac3f77f308c95dc0db7b939238a33022d27812578224",
    policyRevision: 2,
    schemaRevision: 1,
});
const declarationLayerAuthority = Object.freeze({
    nodeEntryPath: "/declarations/node/index.d.ts",
    nodePathPrefix: "/declarations/node/",
    rhinoEntryPath: "/declarations/rhino/index.d.ts",
    rhinoPathPrefix: "/declarations/rhino/",
});
const controlledTsConfigAuthority = Object.freeze({
    configKind: "tsconfig",
    configPath: "tsconfig.json",
    configPolicyFingerprint:
        "64ef1cf36d6531f84088cec49127e6407b107ab86eeaf7f4512358fd1343598b",
    configPolicyRevision: 2,
    configSchemaRevision: 2,
    maximumBytes: 48 * 1024,
    maximumJsonDepth: 16,
    maximumJsonNodes: 4 * 1024,
    maximumSelectorBytes: 4 * 1024,
    maximumSelectors: 512,
    profileRevision: 2,
    profileSchemaRevision: 1,
    targetProfile: "rhino",
});
const controlledPackageMetadataAuthority = Object.freeze({
    canonicalPrefix: "autojs6.typescript.package-metadata.inventory.v1",
    maximumBytes: 1024 * 1024,
    maximumCanonicalChars: 512 * 1024,
    maximumFileBytes: 64 * 1024,
    maximumFiles: 256,
    maximumJsonDepth: 16,
    maximumJsonNodes: 4 * 1024,
    maximumPathChars: 1024,
    maximumRoots: 128,
    policyFingerprint:
        "fab8fec29ed0f0a1de65594620e233c9a9bef2fa514dac16c3079d5570325e97",
    policyRevision: 1,
    schemaRevision: 1,
});
const controlledPackageMetadataPolicyCodes = Object.freeze({
    complexityExceeded: "PACKAGE_COMPLEXITY_EXCEEDED",
    duplicateKey: "PACKAGE_DUPLICATE_KEY",
    encodingInvalid: "PACKAGE_ENCODING_INVALID",
    inventoryInvalid: "PACKAGE_INVENTORY_INVALID",
    jsonInvalid: "PACKAGE_JSON_INVALID",
    lifecycleForbidden: "PACKAGE_LIFECYCLE_FORBIDDEN",
    pathInvalid: "PACKAGE_PATH_INVALID",
    quotaExceeded: "PACKAGE_QUOTA_EXCEEDED",
    rootNotAllowed: "PACKAGE_ROOT_NOT_ALLOWED",
});
const controlledPackageTargetAuthority = Object.freeze({
    allowedSourceSuffixes: Object.freeze([".d.ts", ".ts"]),
    canonicalPrefix: "autojs6.typescript.package-target.inventory.v1",
    dependencyRootPrefix: "packages/",
    maximumAggregatePathChars: 32 * 1024,
    maximumFileBytes: 8 * 1024 * 1024,
    maximumFiles: 256,
    maximumPathChars: 1024,
    maximumTotalBytes: 8 * 1024 * 1024,
    metadataBindingMode: "exact-c5-inventory-fingerprint",
    policyFingerprint:
        "e34f4825d8f75ba1b674849ff79c2be8f9c1b88fe940add86e6e466a139bbbaf",
    policyRevision: 1,
    schemaRevision: 1,
    sharedAggregateBudgetMode: "required-non-additive",
});
const controlledPackageTargetPolicyCodes = Object.freeze({
    caseCollision: "PACKAGE_TARGET_CASE_COLLISION",
    inventoryInvalid: "PACKAGE_TARGET_INVENTORY_INVALID",
    metadataIdentityInvalid: "PACKAGE_TARGET_METADATA_IDENTITY_INVALID",
    pathInvalid: "PACKAGE_TARGET_PATH_INVALID",
    quotaExceeded: "PACKAGE_TARGET_QUOTA_EXCEEDED",
    rootNotAllowed: "PACKAGE_TARGET_ROOT_NOT_ALLOWED",
    sourceKindUnsupported: "PACKAGE_TARGET_SOURCE_KIND_UNSUPPORTED",
});
const controlledTsConfigPolicyCodes = Object.freeze({
    complexityExceeded: "CONFIG_COMPLEXITY_EXCEEDED",
    duplicateKey: "CONFIG_DUPLICATE_KEY",
    encodingInvalid: "CONFIG_ENCODING_INVALID",
    entryNotSelected: "CONFIG_ENTRY_NOT_SELECTED",
    invalidValue: "CONFIG_INVALID_VALUE",
    noInputs: "CONFIG_NO_INPUTS",
    pathInvalid: "CONFIG_PATH_INVALID",
    profileConflict: "CONFIG_PROFILE_CONFLICT",
    syntaxInvalid: "CONFIG_SYNTAX_INVALID",
    unknownProperty: "CONFIG_UNKNOWN_PROPERTY",
    unsupportedFeature: "CONFIG_UNSUPPORTED_FEATURE",
});
const controlledTsConfigAllowedTopLevel = new Set([
    "$schema",
    "compilerOptions",
    "exclude",
    "files",
    "include",
]);
const controlledTsConfigUnsupportedTopLevel = new Set([
    "compileOnSave",
    "extends",
    "references",
    "typeAcquisition",
    "watchOptions",
]);
const controlledTsConfigAllowedBooleanOptions = new Set([
    "exactOptionalPropertyTypes",
    "noFallthroughCasesInSwitch",
    "noImplicitAny",
    "noImplicitOverride",
    "noImplicitReturns",
    "noImplicitThis",
    "noPropertyAccessFromIndexSignature",
    "noUncheckedIndexedAccess",
    "noUnusedLocals",
    "noUnusedParameters",
    "skipLibCheck",
    "strict",
    "strictBindCallApply",
    "strictBuiltinIteratorReturn",
    "strictFunctionTypes",
    "strictNullChecks",
    "strictPropertyInitialization",
    "useUnknownInCatchVariables",
]);
const controlledTsConfigProfileManagedOptions = new Set([
    "allowJs",
    "allowSyntheticDefaultImports",
    "alwaysStrict",
    "checkJs",
    "esModuleInterop",
    "forceConsistentCasingInFileNames",
    "ignoreDeprecations",
    "module",
    "moduleDetection",
    "moduleResolution",
    "newLine",
    "noEmitOnError",
    "outDir",
    "resolveJsonModule",
    "rootDir",
    "sourceMap",
    "target",
    "useDefineForClassFields",
]);
// R2-M2-C4 keeps every option that could delegate resolution or compiler
// execution outside the frozen profile on an explicit fail-closed branch.
// Do not rely only on TypeScript's version-specific optionDeclarations list:
// this set is an independently frozen cross-language authority.
const controlledTsConfigEscapeForbiddenOptions = new Set([
    "baseUrl",
    "customConditions",
    "lib",
    "moduleSuffixes",
    "paths",
    "plugins",
    "rootDirs",
    "traceResolution",
    "typeRoots",
    "types",
]);
const controlledTsConfigOutputForbiddenOptions = new Set([
    "declaration",
    "declarationDir",
    "declarationMap",
    "emitBOM",
    "emitDeclarationOnly",
    "importHelpers",
    "incremental",
    "inlineSourceMap",
    "inlineSources",
    "mapRoot",
    "noEmit",
    "noEmitHelpers",
    "outFile",
    "removeComments",
    "sourceRoot",
    "tsBuildInfoFile",
]);
const controlledTsConfigCustomTransformerOptions = new Set([
    "customTransformer",
    "customTransformers",
    "transformers",
]);
const controlledTsConfigIgnoredDiagnosticCodes = Object.freeze({
    compilerOption: 910_002,
    topLevelProperty: 910_001,
});
const compilerEscapeSourcePathRejectionCodes = Object.freeze({
    deferredSourceKind: "deferred-source-kind",
    packageDependencyTree: "package-dependency-tree",
    packageMetadata: "package-metadata",
});
const controlledTsConfigKnownOptions = new Set(
    ts.optionDeclarations.map((option) => option.name),
);
const controlledTsConfigEffectiveOptionsRevision = 2;
const controlledTsConfigStrictDependentOptions = new Set([
    "noImplicitAny",
    "noImplicitThis",
    "strictBindCallApply",
    "strictBuiltinIteratorReturn",
    "strictFunctionTypes",
    "strictNullChecks",
    "strictPropertyInitialization",
    "useUnknownInCatchVariables",
]);
const controlledTsConfigFalseDefaultOptions = new Set([
    "exactOptionalPropertyTypes",
    "noFallthroughCasesInSwitch",
    "noImplicitOverride",
    "noImplicitReturns",
    "noPropertyAccessFromIndexSignature",
    "noUncheckedIndexedAccess",
    "noUnusedLocals",
    "noUnusedParameters",
    "skipLibCheck",
]);
// TypeScript 6.0.3 computes these values when they are omitted. C3 makes
// them explicit so a future compiler-default change cannot silently alter
// the immutable Rhino profile.
const controlledTsConfigExplicitProfileDefaults = Object.freeze({
    allowSyntheticDefaultImports: true,
    alwaysStrict: true,
    esModuleInterop: true,
    forceConsistentCasingInFileNames: true,
    moduleDetection: ts.ModuleDetectionKind.Auto,
    resolveJsonModule: false,
    useDefineForClassFields: false,
});
const controlledTsConfigCanonicalOptionKeys = Object.freeze([
    "allowJs",
    "allowSyntheticDefaultImports",
    "alwaysStrict",
    "checkJs",
    "esModuleInterop",
    "exactOptionalPropertyTypes",
    "forceConsistentCasingInFileNames",
    "ignoreDeprecations",
    "incremental",
    "inlineSourceMap",
    "inlineSources",
    "jsx",
    "jsxFactory",
    "jsxFragmentFactory",
    "module",
    "moduleDetection",
    "moduleResolution",
    "newLine",
    "noEmitOnError",
    "noFallthroughCasesInSwitch",
    "noImplicitAny",
    "noImplicitOverride",
    "noImplicitReturns",
    "noImplicitThis",
    "noLib",
    "noPropertyAccessFromIndexSignature",
    "noUncheckedIndexedAccess",
    "noUnusedLocals",
    "noUnusedParameters",
    "outDir",
    "resolveJsonModule",
    "rootDir",
    "skipLibCheck",
    "sourceMap",
    "strict",
    "strictBindCallApply",
    "strictBuiltinIteratorReturn",
    "strictFunctionTypes",
    "strictNullChecks",
    "strictPropertyInitialization",
    "target",
    "types",
    "useDefineForClassFields",
    "useUnknownInCatchVariables",
]);
const projectExecutionPhases = Object.freeze([
    "ingress",
    "parse",
    "semantic",
    "emit",
]);
const projectExecutionPhaseSet = new Set(projectExecutionPhases);
const minimumProjectExecutionProgressUnits = 8;
const hasExactKeys = (value, expectedKeys) =>
    Object.keys(value).sort(compareCodeUnits).join("\n") ===
    expectedKeys.slice().sort(compareCodeUnits).join("\n");
const matchesProvisionalRhinoProfileAuthority = (value) =>
    value.targetProfile ===
        provisionalRhinoProfileAuthority.targetProfile &&
    value.profileSchemaRevision ===
        provisionalRhinoProfileAuthority.profileSchemaRevision &&
    value.profileRevision ===
        provisionalRhinoProfileAuthority.profileRevision &&
    value.configSchemaRevision ===
        provisionalRhinoProfileAuthority.configSchemaRevision &&
    value.configKind === provisionalRhinoProfileAuthority.configKind;
const matchesProvisionalRhinoRevision2ProfileAuthority = (value) =>
    value.targetProfile ===
        provisionalRhinoRevision2ProfileAuthority.targetProfile &&
    value.profileSchemaRevision ===
        provisionalRhinoRevision2ProfileAuthority.profileSchemaRevision &&
    value.profileRevision ===
        provisionalRhinoRevision2ProfileAuthority.profileRevision &&
    value.configSchemaRevision ===
        provisionalRhinoRevision2ProfileAuthority.configSchemaRevision &&
    value.configKind === provisionalRhinoRevision2ProfileAuthority.configKind &&
    value.runtimeCapabilitiesFingerprint ===
        provisionalRhinoRevision2ProfileAuthority.runtimeCapabilitiesFingerprint;
const matchesProvisionalNodeNextProfileAuthority = (value) =>
    value.targetProfile ===
        provisionalNodeNextProfileAuthority.targetProfile &&
    value.profileSchemaRevision ===
        provisionalNodeNextProfileAuthority.profileSchemaRevision &&
    value.profileRevision ===
        provisionalNodeNextProfileAuthority.profileRevision &&
    value.configSchemaRevision ===
        provisionalNodeNextProfileAuthority.configSchemaRevision &&
    value.configKind === provisionalNodeNextProfileAuthority.configKind;
const matchesProvisionalNodeNextRevision2ProfileAuthority = (value) =>
    value.targetProfile ===
        provisionalNodeNextRevision2ProfileAuthority.targetProfile &&
    value.profileSchemaRevision ===
        provisionalNodeNextRevision2ProfileAuthority.profileSchemaRevision &&
    value.profileRevision ===
        provisionalNodeNextRevision2ProfileAuthority.profileRevision &&
    value.configSchemaRevision ===
        provisionalNodeNextRevision2ProfileAuthority.configSchemaRevision &&
    value.configKind ===
        provisionalNodeNextRevision2ProfileAuthority.configKind &&
    value.runtimeCapabilitiesFingerprint ===
        provisionalNodeNextRevision2ProfileAuthority.runtimeCapabilitiesFingerprint;
const matchesProvisionalConfigNoneProfileAuthority = (value) =>
    matchesProvisionalRhinoProfileAuthority(value) ||
    matchesProvisionalRhinoRevision2ProfileAuthority(value) ||
    matchesProvisionalNodeNextProfileAuthority(value);
const matchesControlledTsConfigAuthority = (value) =>
    value.targetProfile === controlledTsConfigAuthority.targetProfile &&
    value.profileSchemaRevision ===
        controlledTsConfigAuthority.profileSchemaRevision &&
    value.profileRevision === controlledTsConfigAuthority.profileRevision &&
    value.configSchemaRevision ===
        controlledTsConfigAuthority.configSchemaRevision &&
    value.configKind === controlledTsConfigAuthority.configKind &&
    value.configPolicyRevision ===
        controlledTsConfigAuthority.configPolicyRevision &&
    value.configPolicyFingerprint ===
        controlledTsConfigAuthority.configPolicyFingerprint;

const compareStrictlySortedUnique = (values) =>
    values.every(
        (value, index) => index === 0 || compareCodeUnits(values[index - 1], value) < 0,
    );

// R2-M2-C6 pure snapshot resolver authority. This is intentionally not wired
// into project transport or the TypeScript Program yet. Its only inputs are an
// already accepted, deeply frozen metadata catalog and a deeply frozen target
// inventory; it has no compiler-host, filesystem, network, or Program escape.
const controlledNodeResolutionFailureCodes = Object.freeze({
    invalidContainingPath: "NODE_RESOLUTION_INVALID_CONTAINING_PATH",
    invalidSpecifier: "NODE_RESOLUTION_INVALID_SPECIFIER",
    invalidCondition: "NODE_RESOLUTION_INVALID_CONDITION",
    ambiguousCase: "NODE_RESOLUTION_AMBIGUOUS_CASE",
    packageNotAllowed: "NODE_RESOLUTION_PACKAGE_NOT_ALLOWED",
    packageMetadataMissing: "NODE_RESOLUTION_PACKAGE_METADATA_MISSING",
    packageMetadataInvalid: "NODE_RESOLUTION_PACKAGE_METADATA_INVALID",
    importsNotDefined: "NODE_RESOLUTION_IMPORTS_NOT_DEFINED",
    importsNoMatch: "NODE_RESOLUTION_IMPORTS_NO_MATCH",
    exportsNotDefined: "NODE_RESOLUTION_EXPORTS_NOT_DEFINED",
    exportsNoMatch: "NODE_RESOLUTION_EXPORTS_NO_MATCH",
    conditionNoMatch: "NODE_RESOLUTION_CONDITION_NO_MATCH",
    invalidTarget: "NODE_RESOLUTION_INVALID_TARGET",
    targetBlocked: "NODE_RESOLUTION_TARGET_BLOCKED",
    targetEscape: "NODE_RESOLUTION_TARGET_ESCAPE",
    targetNotInSnapshot: "NODE_RESOLUTION_TARGET_NOT_IN_SNAPSHOT",
    unsupportedSourceKind: "NODE_RESOLUTION_UNSUPPORTED_SOURCE_KIND",
    cycle: "NODE_RESOLUTION_CYCLE",
});
const controlledNodeResolutionFailureDiagnosticCodes = Object.freeze(
    Object.fromEntries(
        Object.values(controlledNodeResolutionFailureCodes).map(
            (policyCode, index) => [policyCode, 900_001 + index],
        ),
    ),
);
const controlledNodeResolutionTrustedConditions = Object.freeze(["custom"]);
const maximumControlledNodeResolutionMemoEntries = 1_024;
const controlledNodeResolutionModes = Object.freeze({
    import: "import",
    require: "require",
    types: "types",
});
const controlledNodeResolutionTargetKinds = Object.freeze({
    projectSource: "project-source",
    declaration: "declaration",
});
const controlledNodeResolutionConditionPattern =
    /^[A-Za-z0-9][A-Za-z0-9._-]*$/;
const controlledNodeResolutionDependencyPattern =
    /^(?:@[a-z0-9][a-z0-9._-]*\/)?[a-z0-9][a-z0-9._-]*$/;
const controlledNodeResolutionSpecifierSegmentPattern =
    /^@?[A-Za-z0-9][A-Za-z0-9._+~-]*$/;
const controlledNodeResolutionTargetSegmentPattern =
    /^[A-Za-z0-9@._+~-]+$/;
const controlledNodeResolutionMappingSegmentPattern =
    /^[#@A-Za-z0-9*][#@A-Za-z0-9._+*~-]*$/;
const controlledNodeResolutionAllowedTargetRoots = new Set([
    "sources",
    "declarations",
    "packages",
]);
const controlledNodeResolutionMaximumSpecifierChars = 1024;
const controlledNodeResolutionMaximumTargetPathChars = 4096;
const controlledNodeResolutionMaximumRecursionDepth = 32;

const controlledNodeResolutionFailure = (
    code,
    {
        packageBoundary = null,
        packageMetadataPath = null,
        matchedKey = null,
        matchedConditions = [],
    } = {},
) => Object.freeze({
    code,
    kind: "failure",
    matchedConditions: Object.freeze(matchedConditions.slice()),
    matchedKey,
    packageBoundary,
    packageMetadataPath,
});

const controlledNodeResolutionResolved = (
    target,
    packageBoundary,
    packageMetadataPath,
    matchedKey,
    matchedConditions,
) => Object.freeze({
    kind: "resolved",
    matchedConditions: Object.freeze(matchedConditions.slice()),
    matchedKey,
    packageBoundary,
    packageMetadataPath,
    target,
});

const isControlledNodeDeepFrozenJson = (value, seen = new WeakSet()) => {
    if (
        value === null ||
        typeof value === "string" ||
        typeof value === "boolean" ||
        typeof value === "number"
    ) {
        return true;
    }
    if (typeof value !== "object" || !Object.isFrozen(value) || seen.has(value)) {
        return false;
    }
    const prototype = Object.getPrototypeOf(value);
    if (
        !Array.isArray(value) &&
        prototype !== null &&
        prototype !== Object.prototype
    ) {
        return false;
    }
    if (Object.getOwnPropertySymbols(value).length !== 0) {
        return false;
    }
    seen.add(value);
    const valid = Reflect.ownKeys(value).every((key) => {
        if (typeof key !== "string") return false;
        const descriptor = Object.getOwnPropertyDescriptor(value, key);
        return descriptor !== undefined &&
            Object.prototype.hasOwnProperty.call(descriptor, "value") &&
            isControlledNodeDeepFrozenJson(descriptor.value, seen);
    });
    seen.delete(value);
    return valid;
};

const deepFreezeControlledNodeJson = (value) => {
    if (value === null || typeof value !== "object") return value;
    for (const key of Reflect.ownKeys(value)) {
        deepFreezeControlledNodeJson(value[key]);
    }
    return Object.freeze(value);
};

const isControlledNodeCanonicalTargetPath = (path) => {
    if (
        typeof path !== "string" ||
        !path.startsWith("/") ||
        path.length <= 1 ||
        path.length > controlledNodeResolutionMaximumTargetPathChars ||
        path.includes("\\") ||
        Array.from(path).some((character) => {
            const code = character.charCodeAt(0);
            return code < 0x20 || code > 0x7e;
        })
    ) {
        return false;
    }
    const segments = path.slice(1).split("/");
    return segments.length >= 2 &&
        controlledNodeResolutionAllowedTargetRoots.has(segments[0]) &&
        segments.every(
            (segment) =>
                segment.length > 0 &&
                segment !== "." &&
                segment !== ".." &&
                segment.toLowerCase() !== "node_modules" &&
                controlledNodeResolutionTargetSegmentPattern.test(segment),
        );
};

const isNodeRevisionTwoCanonicalTargetPath = (path) => {
    if (
        typeof path !== "string" ||
        !path.startsWith("/") ||
        path.length <= 1 ||
        path.length > controlledNodeResolutionMaximumTargetPathChars ||
        new TextEncoder().encode(path.slice(1)).byteLength >
            controlledNodeResolutionMaximumTargetPathChars ||
        path.includes("\\") ||
        !isSafeUnicodePathText(path)
    ) {
        return false;
    }
    const segments = path.slice(1).split("/");
    return segments.length >= 2 &&
        controlledNodeResolutionAllowedTargetRoots.has(segments[0]) &&
        segments.every(
            (segment) =>
                segment.length > 0 &&
                segment !== "." &&
                segment !== ".." &&
                segment.toLowerCase() !== "node_modules" &&
                isSafeUnicodePathText(segment),
        );
};

const hasControlledNodeSupportedTargetKind = (target) => {
    if (target.kind === controlledNodeResolutionTargetKinds.projectSource) {
        return (target.path.endsWith(".ts") || target.path.endsWith(".tsx")) &&
            !target.path.endsWith(".d.ts") &&
            !target.path.endsWith(".mts") &&
            !target.path.endsWith(".cts") &&
            (target.path.startsWith("/sources/") ||
                target.path.startsWith("/packages/"));
    }
    return target.kind === controlledNodeResolutionTargetKinds.declaration &&
        target.path.endsWith(".d.ts") &&
        (target.path.startsWith("/declarations/") ||
            target.path.startsWith("/packages/"));
};

const createControlledNodeTargetIndex = (targets) => {
    if (!Array.isArray(targets) || !Object.isFrozen(targets)) {
        return controlledNodeResolutionFailure(
            controlledNodeResolutionFailureCodes.invalidTarget,
        );
    }
    const exact = new Map();
    const folded = new Map();
    for (const target of targets) {
        if (
            !isRecord(target) ||
            !Object.isFrozen(target) ||
            !hasExactKeys(target, ["kind", "path"]) ||
            !isControlledNodeCanonicalTargetPath(target.path)
        ) {
            return controlledNodeResolutionFailure(
                controlledNodeResolutionFailureCodes.invalidTarget,
            );
        }
        if (!hasControlledNodeSupportedTargetKind(target)) {
            return controlledNodeResolutionFailure(
                controlledNodeResolutionFailureCodes.unsupportedSourceKind,
            );
        }
        const foldedPath = target.path.toLowerCase();
        if (exact.has(target.path) || folded.has(foldedPath)) {
            return controlledNodeResolutionFailure(
                controlledNodeResolutionFailureCodes.ambiguousCase,
            );
        }
        exact.set(target.path, target);
        folded.set(foldedPath, target);
    }
    const resolve = (
        candidate,
        packageBoundary,
        packageMetadataPath,
        matchedKey,
        matchedConditions,
    ) => {
        const candidates = candidate.endsWith(".js")
            ? [
                  `${candidate.slice(0, -3)}.ts`,
                  `${candidate.slice(0, -3)}.tsx`,
                  `${candidate.slice(0, -3)}.d.ts`,
              ]
            : [candidate];
        for (const substituted of candidates) {
            const target = exact.get(substituted);
            if (target !== undefined) {
                return controlledNodeResolutionResolved(
                    target,
                    packageBoundary,
                    packageMetadataPath,
                    matchedKey,
                    matchedConditions,
                );
            }
            if (folded.has(substituted.toLowerCase())) {
                return controlledNodeResolutionFailure(
                    controlledNodeResolutionFailureCodes.ambiguousCase,
                    {
                        matchedConditions,
                        matchedKey,
                        packageBoundary,
                        packageMetadataPath,
                    },
                );
            }
        }
        return controlledNodeResolutionFailure(
            controlledNodeResolutionFailureCodes.targetNotInSnapshot,
            {
                matchedConditions,
                matchedKey,
                packageBoundary,
                packageMetadataPath,
            },
        );
    };
    return Object.freeze({ resolve });
};

const validateControlledNodeMetadataCatalog = (catalog) => {
    if (
        !isRecord(catalog) ||
        !Object.isFrozen(catalog) ||
        !hasExactKeys(catalog, [
            "dependencyRoots",
            "inventoryFingerprint",
            "records",
        ]) ||
        !Array.isArray(catalog.dependencyRoots) ||
        !Object.isFrozen(catalog.dependencyRoots) ||
        !Array.isArray(catalog.records) ||
        !Object.isFrozen(catalog.records) ||
        typeof catalog.inventoryFingerprint !== "string" ||
        !sha256Pattern.test(catalog.inventoryFingerprint) ||
        !compareStrictlySortedUnique(catalog.dependencyRoots)
    ) {
        return null;
    }
    const dependencyRoots = new Set();
    for (const root of catalog.dependencyRoots) {
        if (
            typeof root !== "string" ||
            !root.startsWith("packages/") ||
            !controlledNodeResolutionDependencyPattern.test(
                root.slice("packages/".length),
            )
        ) {
            return null;
        }
        dependencyRoots.add(root);
    }
    const recordsByBoundary = new Map();
    for (const record of catalog.records) {
        if (
            !isRecord(record) ||
            !Object.isFrozen(record) ||
            !hasExactKeys(record, ["boundaryPath", "path", "root", "sha256", "size"]) ||
            typeof record.path !== "string" ||
            typeof record.boundaryPath !== "string" ||
            !Number.isSafeInteger(record.size) ||
            record.size < 0 ||
            typeof record.sha256 !== "string" ||
            !sha256Pattern.test(record.sha256) ||
            !isControlledNodeDeepFrozenJson(record.root)
        ) {
            return null;
        }
        const expectedBoundary = record.path === "package.json"
            ? "/"
            : record.path.endsWith("/package.json")
              ? `/${record.path.slice(0, -"/package.json".length)}`
              : null;
        if (
            expectedBoundary === null ||
            record.boundaryPath !== expectedBoundary ||
            recordsByBoundary.has(record.boundaryPath) ||
            (
                record.boundaryPath !== "/" &&
                !catalog.dependencyRoots.some(
                    (root) =>
                        record.boundaryPath === `/${root}` ||
                        record.boundaryPath.startsWith(`/${root}/`),
                )
            )
        ) {
            return null;
        }
        recordsByBoundary.set(record.boundaryPath, record);
    }
    const records = catalog.records.slice();
    return Object.freeze({ dependencyRoots, records, recordsByBoundary });
};

const isControlledNodeCanonicalSpecifier = (specifier) =>
    typeof specifier === "string" &&
    specifier.length > 0 &&
    specifier.length <= controlledNodeResolutionMaximumSpecifierChars &&
    Array.from(specifier).every((character) => {
        const code = character.charCodeAt(0);
        return code >= 0x20 && code <= 0x7e;
    }) &&
    !/[\\?%|, ]/.test(specifier);

const isControlledNodeCanonicalImportsSpecifier = (specifier) =>
    specifier.startsWith("#") &&
    specifier !== "#" &&
    !specifier.startsWith("#/") &&
    !specifier.includes("*") &&
    specifier.slice(1).split("/").every(
        (segment) =>
            segment.length > 0 &&
            segment !== "." &&
            segment !== ".." &&
            controlledNodeResolutionSpecifierSegmentPattern.test(segment),
    );

const parseControlledNodePackageSpecifier = (specifier) => {
    const segments = specifier.split("/");
    if (
        segments.length === 0 ||
        segments.some(
            (segment) =>
                segment.length === 0 ||
                segment === "." ||
                segment === ".." ||
                !controlledNodeResolutionSpecifierSegmentPattern.test(segment),
        )
    ) {
        return null;
    }
    const packageSegmentCount = segments[0].startsWith("@") ? 2 : 1;
    if (
        segments.length < packageSegmentCount ||
        (packageSegmentCount === 2 && segments[0].length === 1)
    ) {
        return null;
    }
    const packageName = segments.slice(0, packageSegmentCount).join("/");
    if (!controlledNodeResolutionDependencyPattern.test(packageName)) return null;
    const remaining = segments.slice(packageSegmentCount);
    return Object.freeze({
        packageName,
        subpath: remaining.length === 0 ? "." : `./${remaining.join("/")}`,
    });
};

const resolveControlledNodeRelativePath = (
    boundary,
    relative,
    enforceBoundary,
    versionTwo = false,
) => {
    const baseSegments = boundary === "/" ? [] : boundary.slice(1).split("/");
    for (const segment of relative.split("/")) {
        if (segment === "" || segment === ".") continue;
        if (segment === "..") {
            if (baseSegments.length === 0) return null;
            baseSegments.pop();
        } else {
            if (
                segment.toLowerCase() === "node_modules" ||
                !controlledNodeResolutionTargetSegmentPattern.test(segment)
            ) {
                return null;
            }
            baseSegments.push(segment);
        }
    }
    const candidate = `/${baseSegments.join("/")}`;
    const validCandidate = versionTwo
        ? isNodeRevisionTwoCanonicalTargetPath(candidate)
        : isControlledNodeCanonicalTargetPath(candidate);
    if (!validCandidate) return null;
    if (
        enforceBoundary &&
        boundary !== "/" &&
        candidate !== boundary &&
        !candidate.startsWith(`${boundary}/`)
    ) {
        return null;
    }
    return candidate;
};

const isControlledNodeValidMappingPath = (value) =>
    (value.match(/\*/g) || []).length <= 1 &&
    value.split("/").every(
        (segment) =>
            segment.length > 0 &&
            segment !== "." &&
            segment !== ".." &&
            segment.toLowerCase() !== "node_modules" &&
            controlledNodeResolutionMappingSegmentPattern.test(segment),
    );

const isControlledNodeValidMappingKey = (key, importsTarget) =>
    importsTarget
        ? key.startsWith("#") &&
          key !== "#" &&
          !key.startsWith("#/") &&
          isControlledNodeValidMappingPath(key.slice(1))
        : key === "." ||
          (key.startsWith("./") &&
              key.length > 2 &&
              isControlledNodeValidMappingPath(key.slice(2)));

const findControlledNodeMapping = (values, requestedKey, importsTarget) => {
    const keys = Object.keys(values);
    if (keys.some((key) => !isControlledNodeValidMappingKey(key, importsTarget))) {
        return null;
    }
    if (Object.prototype.hasOwnProperty.call(values, requestedKey)) {
        return Object.freeze({
            key: requestedKey,
            value: values[requestedKey],
            wildcard: null,
        });
    }
    const matches = [];
    for (const key of keys) {
        const star = key.indexOf("*");
        if (star < 0) continue;
        const prefix = key.slice(0, star);
        const suffix = key.slice(star + 1);
        if (
            requestedKey.startsWith(prefix) &&
            requestedKey.endsWith(suffix) &&
            requestedKey.length >= prefix.length + suffix.length
        ) {
            matches.push({
                key,
                value: values[key],
                wildcard: requestedKey.slice(
                    prefix.length,
                    requestedKey.length - suffix.length,
                ),
            });
        }
    }
    matches.sort((left, right) => {
        const prefixDifference =
            right.key.indexOf("*") - left.key.indexOf("*");
        if (prefixDifference !== 0) return prefixDifference;
        const lengthDifference = right.key.length - left.key.length;
        return lengthDifference !== 0
            ? lengthDifference
            : compareCodeUnits(left.key, right.key);
    });
    return matches.length === 0 ? undefined : Object.freeze(matches[0]);
};

const isControlledNodeArrayIndexCondition = (condition) => {
    if (!/^(0|[1-9][0-9]*)$/.test(condition)) return false;
    const value = Number(condition);
    return Number.isSafeInteger(value) &&
        value >= 0 &&
        value < 0xffffffff &&
        String(value) === condition;
};

const resolveControlledNodeSnapshot = (request, packageMetadata, targets) => {
    if (
        !isRecord(request) ||
        !Object.isFrozen(request) ||
        !hasExactKeys(request, ["conditions", "containingPath", "mode", "specifier"]) ||
        !isControlledNodeCanonicalTargetPath(request.containingPath)
    ) {
        return controlledNodeResolutionFailure(
            controlledNodeResolutionFailureCodes.invalidContainingPath,
        );
    }
    if (!isControlledNodeCanonicalSpecifier(request.specifier)) {
        return controlledNodeResolutionFailure(
            controlledNodeResolutionFailureCodes.invalidSpecifier,
        );
    }
    if (
        !Object.values(controlledNodeResolutionModes).includes(request.mode) ||
        !Array.isArray(request.conditions) ||
        !Object.isFrozen(request.conditions) ||
        new Set(request.conditions).size !== request.conditions.length ||
        request.conditions.some(
            (condition) =>
                typeof condition !== "string" ||
                !controlledNodeResolutionConditionPattern.test(condition),
        )
    ) {
        return controlledNodeResolutionFailure(
            controlledNodeResolutionFailureCodes.invalidCondition,
        );
    }
    const targetIndex = createControlledNodeTargetIndex(targets);
    if (targetIndex.kind === "failure") return targetIndex;
    const catalog = validateControlledNodeMetadataCatalog(packageMetadata);
    if (catalog === null) {
        return controlledNodeResolutionFailure(
            controlledNodeResolutionFailureCodes.packageMetadataInvalid,
        );
    }
    const activeConditions = new Set([
        request.mode,
        "types",
        "node",
        ...request.conditions,
    ]);
    const resolvingImports = new Set();
    const nearestRecord = (containingPath) => catalog.records
        .filter(
            (record) =>
                record.boundaryPath === "/" ||
                containingPath.startsWith(`${record.boundaryPath}/`),
        )
        .sort(
            (left, right) =>
                right.boundaryPath.length - left.boundaryPath.length,
        )[0];
    const recordFailure = (
        code,
        record,
        matchedKey = null,
        matchedConditions = [],
    ) => controlledNodeResolutionFailure(code, {
        matchedConditions,
        matchedKey,
        packageBoundary: record?.boundaryPath ?? null,
        packageMetadataPath: record?.path ?? null,
    });

    const resolveImports = (specifier, record, depth) => {
        if (depth > controlledNodeResolutionMaximumRecursionDepth) {
            return recordFailure(controlledNodeResolutionFailureCodes.cycle, record);
        }
        if (!isControlledNodeCanonicalImportsSpecifier(specifier)) {
            return recordFailure(
                controlledNodeResolutionFailureCodes.invalidSpecifier,
                record,
            );
        }
        const cycleKey = `${record.path}|${specifier}`;
        if (resolvingImports.has(cycleKey)) {
            return recordFailure(controlledNodeResolutionFailureCodes.cycle, record);
        }
        resolvingImports.add(cycleKey);
        try {
            if (!Object.prototype.hasOwnProperty.call(record.root, "imports")) {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.importsNotDefined,
                    record,
                );
            }
            const imports = record.root.imports;
            if (!isRecord(imports) || Array.isArray(imports)) {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.packageMetadataInvalid,
                    record,
                );
            }
            const mapping = findControlledNodeMapping(imports, specifier, true);
            if (mapping === null) {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.packageMetadataInvalid,
                    record,
                );
            }
            if (mapping === undefined) {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.importsNoMatch,
                    record,
                );
            }
            return evaluateTarget(
                mapping.value,
                record,
                mapping.key,
                mapping.wildcard,
                [],
                true,
                depth,
            );
        } finally {
            resolvingImports.delete(cycleKey);
        }
    };

    const resolvePackage = (specifier, depth) => {
        if (depth > controlledNodeResolutionMaximumRecursionDepth) {
            return controlledNodeResolutionFailure(
                controlledNodeResolutionFailureCodes.cycle,
            );
        }
        const parsed = parseControlledNodePackageSpecifier(specifier);
        if (parsed === null) {
            return controlledNodeResolutionFailure(
                controlledNodeResolutionFailureCodes.invalidSpecifier,
            );
        }
        const dependencyRoot = `packages/${parsed.packageName}`;
        if (!catalog.dependencyRoots.has(dependencyRoot)) {
            return controlledNodeResolutionFailure(
                controlledNodeResolutionFailureCodes.packageNotAllowed,
            );
        }
        const record = catalog.recordsByBoundary.get(`/${dependencyRoot}`);
        if (record === undefined) {
            return controlledNodeResolutionFailure(
                controlledNodeResolutionFailureCodes.packageMetadataMissing,
            );
        }
        if (!Object.prototype.hasOwnProperty.call(record.root, "exports")) {
            return recordFailure(
                controlledNodeResolutionFailureCodes.exportsNotDefined,
                record,
            );
        }
        const exportsValue = record.root.exports;
        let mapping;
        if (isRecord(exportsValue) && !Array.isArray(exportsValue)) {
            const keys = Object.keys(exportsValue);
            const hasSubpathKey = keys.some((key) => key.startsWith("."));
            if (hasSubpathKey && keys.some((key) => !key.startsWith("."))) {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.packageMetadataInvalid,
                    record,
                );
            }
            if (hasSubpathKey) {
                mapping = findControlledNodeMapping(
                    exportsValue,
                    parsed.subpath,
                    false,
                );
                if (mapping === null) {
                    return recordFailure(
                        controlledNodeResolutionFailureCodes.packageMetadataInvalid,
                        record,
                    );
                }
                if (mapping === undefined) {
                    return recordFailure(
                        controlledNodeResolutionFailureCodes.exportsNoMatch,
                        record,
                    );
                }
            } else {
                if (parsed.subpath !== ".") {
                    return recordFailure(
                        controlledNodeResolutionFailureCodes.exportsNoMatch,
                        record,
                    );
                }
                mapping = { key: ".", value: exportsValue, wildcard: null };
            }
        } else {
            if (parsed.subpath !== ".") {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.exportsNoMatch,
                    record,
                );
            }
            mapping = { key: ".", value: exportsValue, wildcard: null };
        }
        return evaluateTarget(
            mapping.value,
            record,
            mapping.key,
            mapping.wildcard,
            [],
            false,
            depth,
        );
    };

    const resolveMappedTarget = (
        target,
        record,
        matchedKey,
        wildcard,
        matchedConditions,
        importsTarget,
        depth,
    ) => {
        if (target.includes("*") && wildcard === null) {
            return recordFailure(
                controlledNodeResolutionFailureCodes.invalidTarget,
                record,
                matchedKey,
                matchedConditions,
            );
        }
        const substituted = wildcard === null
            ? target
            : target.replaceAll("*", wildcard);
        if (substituted.startsWith("./")) {
            const candidate = resolveControlledNodeRelativePath(
                record.boundaryPath,
                substituted,
                true,
            );
            if (candidate === null) {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.targetEscape,
                    record,
                    matchedKey,
                    matchedConditions,
                );
            }
            return targetIndex.resolve(
                candidate,
                record.boundaryPath,
                record.path,
                matchedKey,
                matchedConditions,
            );
        }
        if (!importsTarget) {
            return recordFailure(
                controlledNodeResolutionFailureCodes.invalidTarget,
                record,
                matchedKey,
                matchedConditions,
            );
        }
        if (substituted.startsWith("#")) {
            return resolveImports(substituted, record, depth + 1);
        }
        if (
            substituted.startsWith("../") ||
            substituted.startsWith("/") ||
            substituted.includes(":")
        ) {
            return recordFailure(
                controlledNodeResolutionFailureCodes.invalidTarget,
                record,
                matchedKey,
                matchedConditions,
            );
        }
        return resolvePackage(substituted, depth + 1);
    };

    const evaluateTarget = (
        value,
        record,
        matchedKey,
        wildcard,
        matchedConditions,
        importsTarget,
        depth,
    ) => {
        if (value === null) {
            return recordFailure(
                controlledNodeResolutionFailureCodes.targetBlocked,
                record,
                matchedKey,
                matchedConditions,
            );
        }
        if (typeof value === "string") {
            return resolveMappedTarget(
                value,
                record,
                matchedKey,
                wildcard,
                matchedConditions,
                importsTarget,
                depth,
            );
        }
        if (Array.isArray(value)) {
            if (value.length === 0) {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.packageMetadataInvalid,
                    record,
                    matchedKey,
                    matchedConditions,
                );
            }
            let lastFailure = null;
            for (const candidate of value) {
                const result = evaluateTarget(
                    candidate,
                    record,
                    matchedKey,
                    wildcard,
                    matchedConditions,
                    importsTarget,
                    depth,
                );
                if (result.kind === "resolved") return result;
                if (
                    result.code ===
                    controlledNodeResolutionFailureCodes.targetBlocked
                ) {
                    return result;
                }
                lastFailure = result;
            }
            return lastFailure;
        }
        if (isRecord(value)) {
            const conditions = Object.keys(value);
            if (
                conditions.length === 0 ||
                conditions.some(
                    (condition) =>
                        condition.startsWith(".") ||
                        isControlledNodeArrayIndexCondition(condition) ||
                        !controlledNodeResolutionConditionPattern.test(condition),
                )
            ) {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.packageMetadataInvalid,
                    record,
                    matchedKey,
                    matchedConditions,
                );
            }
            for (const condition of conditions) {
                if (condition === "default" || activeConditions.has(condition)) {
                    return evaluateTarget(
                        value[condition],
                        record,
                        matchedKey,
                        wildcard,
                        [...matchedConditions, condition],
                        importsTarget,
                        depth,
                    );
                }
            }
            return recordFailure(
                controlledNodeResolutionFailureCodes.conditionNoMatch,
                record,
                matchedKey,
                matchedConditions,
            );
        }
        return recordFailure(
            controlledNodeResolutionFailureCodes.packageMetadataInvalid,
            record,
            matchedKey,
            matchedConditions,
        );
    };

    if (request.specifier.startsWith("#")) {
        const record = nearestRecord(request.containingPath);
        return record === undefined
            ? controlledNodeResolutionFailure(
                  controlledNodeResolutionFailureCodes.packageMetadataMissing,
              )
            : resolveImports(request.specifier, record, 0);
    }
    if (
        request.specifier.startsWith("./") ||
        request.specifier.startsWith("../")
    ) {
        const candidate = resolveControlledNodeRelativePath(
            request.containingPath.slice(
                0,
                request.containingPath.lastIndexOf("/"),
            ) || "/",
            request.specifier,
            false,
        );
        if (candidate === null) {
            return controlledNodeResolutionFailure(
                controlledNodeResolutionFailureCodes.targetEscape,
            );
        }
        const record = nearestRecord(request.containingPath);
        return targetIndex.resolve(
            candidate,
            record?.boundaryPath ?? null,
            record?.path ?? null,
            request.specifier,
            [],
        );
    }
    if (request.specifier.startsWith("/") || request.specifier.includes(":")) {
        return controlledNodeResolutionFailure(
            controlledNodeResolutionFailureCodes.invalidSpecifier,
        );
    }
    return resolvePackage(request.specifier, 0);
};

const createNodeRevisionTwoTargetIndex = (targets) => {
    if (!Array.isArray(targets) || !Object.isFrozen(targets)) {
        throw new Error("Node revision 2 target universe is not immutable.");
    }
    const exact = new Map();
    const folded = new Map();
    for (const target of targets) {
        const isDeclaration =
            target?.kind === controlledNodeResolutionTargetKinds.declaration &&
            isNodeRevisionTwoDeclarationPath(target.path) &&
            (
                target.path.startsWith("/sources/") ||
                target.path.startsWith("/declarations/") ||
                target.path.startsWith("/packages/")
            );
        const isSource =
            target?.kind === controlledNodeResolutionTargetKinds.projectSource &&
            isNodeRevisionTwoResolutionPath(target.path) &&
            !isNodeRevisionTwoDeclarationPath(target.path) &&
            (
                target.path.startsWith("/sources/") ||
                target.path.startsWith("/packages/")
            );
        if (
            !isRecord(target) ||
            !Object.isFrozen(target) ||
            !hasExactKeys(target, ["kind", "path"]) ||
            !isNodeRevisionTwoCanonicalTargetPath(target.path) ||
            (!isDeclaration && !isSource) ||
            exact.has(target.path) ||
            folded.has(asciiCaseFold(target.path))
        ) {
            throw new Error("Node revision 2 target universe is malformed.");
        }
        exact.set(target.path, target);
        folded.set(asciiCaseFold(target.path), target);
    }
    const candidates = (candidate) => {
        if (
            candidate.endsWith(".d.ts") ||
            candidate.endsWith(".d.mts") ||
            candidate.endsWith(".d.cts") ||
            candidate.endsWith(".d.js") ||
            candidate.endsWith(".d.mjs") ||
            candidate.endsWith(".d.cjs")
        ) {
            return [candidate];
        }
        if (candidate.endsWith(".mjs")) {
            return [`${candidate.slice(0, -".mjs".length)}.mts`];
        }
        if (candidate.endsWith(".cjs")) {
            return [`${candidate.slice(0, -".cjs".length)}.cts`];
        }
        if (candidate.endsWith(".js")) {
            return [`${candidate.slice(0, -".js".length)}.ts`];
        }
        return [candidate];
    };
    const resolve = (
        candidate,
        packageBoundary,
        packageMetadataPath,
        matchedKey,
        matchedConditions,
    ) => {
        for (const substituted of candidates(candidate)) {
            const target = exact.get(substituted);
            if (target !== undefined) {
                return controlledNodeResolutionResolved(
                    target,
                    packageBoundary,
                    packageMetadataPath,
                    matchedKey,
                    matchedConditions,
                );
            }
            if (folded.has(asciiCaseFold(substituted))) {
                return controlledNodeResolutionFailure(
                    controlledNodeResolutionFailureCodes.ambiguousCase,
                    {
                        matchedConditions,
                        matchedKey,
                        packageBoundary,
                        packageMetadataPath,
                    },
                );
            }
        }
        return controlledNodeResolutionFailure(
            controlledNodeResolutionFailureCodes.targetNotInSnapshot,
            {
                matchedConditions,
                matchedKey,
                packageBoundary,
                packageMetadataPath,
            },
        );
    };
    return Object.freeze({
        contains: (path) => exact.has(path),
        release: () => {
            exact.clear();
            folded.clear();
        },
        resolve,
    });
};

const resolveNodeRevisionTwoSnapshot = (
    request,
    packageMetadata,
    targetIndex,
) => {
    if (
        !isRecord(request) ||
        !Object.isFrozen(request) ||
        !hasExactKeys(request, ["conditions", "containingPath", "mode", "specifier"]) ||
        !isNodeRevisionTwoCanonicalTargetPath(request.containingPath) ||
        !targetIndex.contains(request.containingPath)
    ) {
        return controlledNodeResolutionFailure(
            controlledNodeResolutionFailureCodes.invalidContainingPath,
        );
    }
    if (!isControlledNodeCanonicalSpecifier(request.specifier)) {
        return controlledNodeResolutionFailure(
            controlledNodeResolutionFailureCodes.invalidSpecifier,
        );
    }
    if (
        !Object.values(controlledNodeResolutionModes).includes(request.mode) ||
        !Array.isArray(request.conditions) ||
        !Object.isFrozen(request.conditions) ||
        request.conditions.length !== 0
    ) {
        return controlledNodeResolutionFailure(
            controlledNodeResolutionFailureCodes.invalidCondition,
        );
    }
    const catalog = packageMetadata === null
        ? null
        : validateControlledNodeMetadataCatalog(packageMetadata);
    if (packageMetadata !== null && catalog === null) {
        return controlledNodeResolutionFailure(
            controlledNodeResolutionFailureCodes.packageMetadataInvalid,
        );
    }
    const activeConditions = new Set([request.mode, "types", "node"]);
    const resolvingImports = new Set();
    const nearestRecord = (containingPath) => catalog?.records
        .filter(
            (record) =>
                record.boundaryPath === "/" ||
                containingPath.startsWith(`${record.boundaryPath}/`),
        )
        .sort(
            (left, right) =>
                right.boundaryPath.length - left.boundaryPath.length,
        )[0];
    const recordFailure = (
        code,
        record,
        matchedKey = null,
        matchedConditions = [],
    ) => controlledNodeResolutionFailure(code, {
        matchedConditions,
        matchedKey,
        packageBoundary: record?.boundaryPath ?? null,
        packageMetadataPath: record?.path ?? null,
    });
    const resolveImports = (specifier, record, depth) => {
        if (depth > controlledNodeResolutionMaximumRecursionDepth) {
            return recordFailure(controlledNodeResolutionFailureCodes.cycle, record);
        }
        if (!isControlledNodeCanonicalImportsSpecifier(specifier)) {
            return recordFailure(
                controlledNodeResolutionFailureCodes.invalidSpecifier,
                record,
            );
        }
        const cycleKey = `${record.path}|${specifier}`;
        if (resolvingImports.has(cycleKey)) {
            return recordFailure(controlledNodeResolutionFailureCodes.cycle, record);
        }
        resolvingImports.add(cycleKey);
        try {
            if (!Object.prototype.hasOwnProperty.call(record.root, "imports")) {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.importsNotDefined,
                    record,
                );
            }
            const imports = record.root.imports;
            if (!isRecord(imports) || Array.isArray(imports)) {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.packageMetadataInvalid,
                    record,
                );
            }
            const mapping = findControlledNodeMapping(imports, specifier, true);
            if (mapping === null) {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.packageMetadataInvalid,
                    record,
                );
            }
            if (mapping === undefined) {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.importsNoMatch,
                    record,
                );
            }
            return evaluateTarget(
                mapping.value,
                record,
                mapping.key,
                mapping.wildcard,
                [],
                true,
                depth,
            );
        } finally {
            resolvingImports.delete(cycleKey);
        }
    };
    const resolvePackage = (specifier, depth) => {
        if (depth > controlledNodeResolutionMaximumRecursionDepth) {
            return controlledNodeResolutionFailure(
                controlledNodeResolutionFailureCodes.cycle,
            );
        }
        const parsed = parseControlledNodePackageSpecifier(specifier);
        if (parsed === null) {
            return controlledNodeResolutionFailure(
                controlledNodeResolutionFailureCodes.invalidSpecifier,
            );
        }
        if (catalog === null) {
            return controlledNodeResolutionFailure(
                controlledNodeResolutionFailureCodes.packageNotAllowed,
            );
        }
        const dependencyRoot = `packages/${parsed.packageName}`;
        if (!catalog.dependencyRoots.has(dependencyRoot)) {
            return controlledNodeResolutionFailure(
                controlledNodeResolutionFailureCodes.packageNotAllowed,
            );
        }
        const record = catalog.recordsByBoundary.get(`/${dependencyRoot}`);
        if (record === undefined) {
            return controlledNodeResolutionFailure(
                controlledNodeResolutionFailureCodes.packageMetadataMissing,
            );
        }
        if (!Object.prototype.hasOwnProperty.call(record.root, "exports")) {
            return recordFailure(
                controlledNodeResolutionFailureCodes.exportsNotDefined,
                record,
            );
        }
        const exportsValue = record.root.exports;
        let mapping;
        if (isRecord(exportsValue) && !Array.isArray(exportsValue)) {
            const keys = Object.keys(exportsValue);
            const hasSubpathKey = keys.some((key) => key.startsWith("."));
            if (hasSubpathKey && keys.some((key) => !key.startsWith("."))) {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.packageMetadataInvalid,
                    record,
                );
            }
            if (hasSubpathKey) {
                mapping = findControlledNodeMapping(
                    exportsValue,
                    parsed.subpath,
                    false,
                );
                if (mapping === null) {
                    return recordFailure(
                        controlledNodeResolutionFailureCodes.packageMetadataInvalid,
                        record,
                    );
                }
                if (mapping === undefined) {
                    return recordFailure(
                        controlledNodeResolutionFailureCodes.exportsNoMatch,
                        record,
                    );
                }
            } else {
                if (parsed.subpath !== ".") {
                    return recordFailure(
                        controlledNodeResolutionFailureCodes.exportsNoMatch,
                        record,
                    );
                }
                mapping = { key: ".", value: exportsValue, wildcard: null };
            }
        } else {
            if (parsed.subpath !== ".") {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.exportsNoMatch,
                    record,
                );
            }
            mapping = { key: ".", value: exportsValue, wildcard: null };
        }
        return evaluateTarget(
            mapping.value,
            record,
            mapping.key,
            mapping.wildcard,
            [],
            false,
            depth,
        );
    };
    const resolveMappedTarget = (
        target,
        record,
        matchedKey,
        wildcard,
        matchedConditions,
        importsTarget,
        depth,
    ) => {
        if (target.includes("*") && wildcard === null) {
            return recordFailure(
                controlledNodeResolutionFailureCodes.invalidTarget,
                record,
                matchedKey,
                matchedConditions,
            );
        }
        const substituted = wildcard === null
            ? target
            : target.replaceAll("*", wildcard);
        if (substituted.startsWith("./")) {
            const candidate = resolveControlledNodeRelativePath(
                record.boundaryPath,
                substituted,
                true,
                true,
            );
            if (candidate === null) {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.targetEscape,
                    record,
                    matchedKey,
                    matchedConditions,
                );
            }
            return targetIndex.resolve(
                candidate,
                record.boundaryPath,
                record.path,
                matchedKey,
                matchedConditions,
            );
        }
        if (!importsTarget) {
            return recordFailure(
                controlledNodeResolutionFailureCodes.invalidTarget,
                record,
                matchedKey,
                matchedConditions,
            );
        }
        if (substituted.startsWith("#")) {
            return resolveImports(substituted, record, depth + 1);
        }
        if (
            substituted.startsWith("../") ||
            substituted.startsWith("/") ||
            substituted.includes(":")
        ) {
            return recordFailure(
                controlledNodeResolutionFailureCodes.invalidTarget,
                record,
                matchedKey,
                matchedConditions,
            );
        }
        return resolvePackage(substituted, depth + 1);
    };
    const evaluateTarget = (
        value,
        record,
        matchedKey,
        wildcard,
        matchedConditions,
        importsTarget,
        depth,
    ) => {
        if (value === null) {
            return recordFailure(
                controlledNodeResolutionFailureCodes.targetBlocked,
                record,
                matchedKey,
                matchedConditions,
            );
        }
        if (typeof value === "string") {
            return resolveMappedTarget(
                value,
                record,
                matchedKey,
                wildcard,
                matchedConditions,
                importsTarget,
                depth,
            );
        }
        if (Array.isArray(value)) {
            if (value.length === 0) {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.packageMetadataInvalid,
                    record,
                    matchedKey,
                    matchedConditions,
                );
            }
            let lastFailure = null;
            for (const candidate of value) {
                const result = evaluateTarget(
                    candidate,
                    record,
                    matchedKey,
                    wildcard,
                    matchedConditions,
                    importsTarget,
                    depth,
                );
                if (result.kind === "resolved") return result;
                if (
                    result.code === controlledNodeResolutionFailureCodes.targetBlocked
                ) {
                    return result;
                }
                lastFailure = result;
            }
            return lastFailure;
        }
        if (isRecord(value)) {
            const conditions = Object.keys(value);
            if (
                conditions.length === 0 ||
                conditions.some(
                    (condition) =>
                        condition.startsWith(".") ||
                        isControlledNodeArrayIndexCondition(condition) ||
                        !controlledNodeResolutionConditionPattern.test(condition),
                )
            ) {
                return recordFailure(
                    controlledNodeResolutionFailureCodes.packageMetadataInvalid,
                    record,
                    matchedKey,
                    matchedConditions,
                );
            }
            for (const condition of conditions) {
                if (condition === "default" || activeConditions.has(condition)) {
                    return evaluateTarget(
                        value[condition],
                        record,
                        matchedKey,
                        wildcard,
                        [...matchedConditions, condition],
                        importsTarget,
                        depth,
                    );
                }
            }
            return recordFailure(
                controlledNodeResolutionFailureCodes.conditionNoMatch,
                record,
                matchedKey,
                matchedConditions,
            );
        }
        return recordFailure(
            controlledNodeResolutionFailureCodes.packageMetadataInvalid,
            record,
            matchedKey,
            matchedConditions,
        );
    };
    if (request.specifier.startsWith("#")) {
        const record = nearestRecord(request.containingPath);
        return record === undefined
            ? controlledNodeResolutionFailure(
                  controlledNodeResolutionFailureCodes.packageMetadataMissing,
              )
            : resolveImports(request.specifier, record, 0);
    }
    if (
        request.specifier.startsWith("./") ||
        request.specifier.startsWith("../")
    ) {
        const candidate = resolveControlledNodeRelativePath(
            request.containingPath.slice(
                0,
                request.containingPath.lastIndexOf("/"),
            ) || "/",
            request.specifier,
            false,
            true,
        );
        if (candidate === null) {
            return controlledNodeResolutionFailure(
                controlledNodeResolutionFailureCodes.targetEscape,
            );
        }
        const record = nearestRecord(request.containingPath);
        return targetIndex.resolve(
            candidate,
            record?.boundaryPath ?? null,
            record?.path ?? null,
            request.specifier,
            [],
        );
    }
    if (request.specifier.startsWith("/") || request.specifier.includes(":")) {
        return controlledNodeResolutionFailure(
            controlledNodeResolutionFailureCodes.invalidSpecifier,
        );
    }
    return resolvePackage(request.specifier, 0);
};

const parseControlledPackageMetadataCanonical = async (
    canonicalInput,
    expectedFingerprint,
    expectedFileCount,
    expectedBytes,
) => {
    if (
        typeof canonicalInput !== "string" ||
        canonicalInput.length === 0 ||
        Array.from(canonicalInput).some((character) => {
            const code = character.charCodeAt(0);
            return code < 0x20 || code > 0x7e;
        }) ||
        typeof expectedFingerprint !== "string" ||
        !sha256Pattern.test(expectedFingerprint) ||
        !Number.isSafeInteger(expectedFileCount) ||
        expectedFileCount < 1 ||
        !Number.isSafeInteger(expectedBytes) ||
        expectedBytes < 0
    ) {
        throw new Error(
            `${controlledPackageMetadataPolicyCodes.inventoryInvalid}: ` +
                "Package metadata authority is malformed.",
        );
    }
    if (
        canonicalInput.length >
            controlledPackageMetadataAuthority.maximumCanonicalChars ||
        expectedFileCount > controlledPackageMetadataAuthority.maximumFiles ||
        expectedBytes > controlledPackageMetadataAuthority.maximumBytes
    ) {
        throw new Error(
            `${controlledPackageMetadataPolicyCodes.quotaExceeded}: ` +
                "Package metadata authority exceeds its limits.",
        );
    }
    if ((await sha256(canonicalInput)) !== expectedFingerprint) {
        throw new Error(
            `${controlledPackageMetadataPolicyCodes.inventoryInvalid}: ` +
                "Package metadata authority fingerprint does not match.",
        );
    }

    const parts = canonicalInput.split("|");
    if (parts.shift() !== controlledPackageMetadataAuthority.canonicalPrefix) {
        throw new Error(
            `${controlledPackageMetadataPolicyCodes.inventoryInvalid}: ` +
                "Package metadata canonical prefix is not supported.",
        );
    }
    const dependencyRoots = [];
    const files = [];
    let fileSectionStarted = false;
    for (const part of parts) {
        if (part.startsWith("root=")) {
            if (fileSectionStarted) {
                throw new Error(
                    `${controlledPackageMetadataPolicyCodes.inventoryInvalid}: ` +
                        "Package metadata roots are not canonical.",
                );
            }
            dependencyRoots.push(part.slice("root=".length));
            continue;
        }
        if (!part.startsWith("file=")) {
            throw new Error(
                `${controlledPackageMetadataPolicyCodes.inventoryInvalid}: ` +
                    "Package metadata canonical record is not supported.",
            );
        }
        fileSectionStarted = true;
        const fields = part.slice("file=".length).split(",");
        if (fields.length !== 3 || !/^(0|[1-9][0-9]*)$/.test(fields[1])) {
            throw new Error(
                `${controlledPackageMetadataPolicyCodes.inventoryInvalid}: ` +
                    "Package metadata file record is malformed.",
            );
        }
        const byteLength = Number(fields[1]);
        if (!Number.isSafeInteger(byteLength) || byteLength < 0 ||
            !sha256Pattern.test(fields[2])) {
            throw new Error(
                `${controlledPackageMetadataPolicyCodes.inventoryInvalid}: ` +
                    "Package metadata file authority is malformed.",
            );
        }
        if (byteLength > controlledPackageMetadataAuthority.maximumFileBytes) {
            throw new Error(
                `${controlledPackageMetadataPolicyCodes.quotaExceeded}: ` +
                    "Package metadata file authority exceeds its limit.",
            );
        }
        files.push(
            Object.freeze({
                byteLength,
                path: fields[0],
                sha256: fields[2],
            }),
        );
    }
    if (
        dependencyRoots.length > controlledPackageMetadataAuthority.maximumRoots ||
        files.length > controlledPackageMetadataAuthority.maximumFiles
    ) {
        throw new Error(
            `${controlledPackageMetadataPolicyCodes.quotaExceeded}: ` +
                "Package metadata inventory count exceeds its limit.",
        );
    }
    if (
        files.length !== expectedFileCount ||
        !compareStrictlySortedUnique(dependencyRoots) ||
        !compareStrictlySortedUnique(files.map((file) => file.path)) ||
        new Set(dependencyRoots.map((root) => root.toLowerCase())).size !==
            dependencyRoots.length ||
        new Set(files.map((file) => file.path.toLowerCase())).size !== files.length ||
        files.reduce((total, file) => total + file.byteLength, 0) !== expectedBytes ||
        !files.some((file) => file.path === "package.json")
    ) {
        throw new Error(
            `${controlledPackageMetadataPolicyCodes.inventoryInvalid}: ` +
                "Package metadata inventory is not canonical.",
        );
    }

    const rootPattern =
        /^packages\/(?:@[a-z0-9][a-z0-9._-]*\/)?[a-z0-9][a-z0-9._-]*$/;
    for (let index = 0; index < dependencyRoots.length; index += 1) {
        const root = dependencyRoots[index];
        if (
            root.length > controlledPackageMetadataAuthority.maximumPathChars ||
            !rootPattern.test(root) ||
            dependencyRoots.some(
                (candidate, candidateIndex) =>
                    candidateIndex !== index &&
                    (candidate.startsWith(`${root}/`) || root.startsWith(`${candidate}/`)),
            ) ||
            !files.some((file) => file.path === `${root}/package.json`)
        ) {
            throw new Error(
                `${controlledPackageMetadataPolicyCodes.rootNotAllowed}: ` +
                    "Package metadata dependency root is not allowed.",
            );
        }
    }
    for (const file of files) {
        const segments = file.path.split("/");
        const foldedSegments = segments.map((segment) => segment.toLowerCase());
        const owningRoots = dependencyRoots.filter(
            (root) => file.path.startsWith(`${root}/`),
        );
        if (
            file.path.length === 0 ||
            file.path.length > controlledPackageMetadataAuthority.maximumPathChars ||
            !/^[A-Za-z0-9@._/+~-]+$/.test(file.path) ||
            foldedSegments.includes("node_modules") ||
            segments[segments.length - 1] !== "package.json" ||
            (file.path === "package.json"
                ? owningRoots.length !== 0
                : owningRoots.length !== 1)
        ) {
            throw new Error(
                `${controlledPackageMetadataPolicyCodes.pathInvalid}: ` +
                    "Package metadata file path is not allowed.",
            );
        }
    }
    return Object.freeze({
        dependencyRoots: Object.freeze(dependencyRoots.slice()),
        filePaths: new Set(files.map((file) => `/${file.path}`)),
        files: Object.freeze(files.slice()),
        fingerprint: expectedFingerprint,
        totalBytes: expectedBytes,
    });
};

const nodePackageBoundaryPath = (packageMetadataPath) =>
    packageMetadataPath === "package.json"
        ? "/"
        : `/${packageMetadataPath.slice(0, -"/package.json".length)}`;

const deriveNodePackageTypeState = (root) => {
    if (!Object.prototype.hasOwnProperty.call(root, "type")) {
        return "missing";
    }
    if (root.type === "commonjs" || root.type === "module") {
        return root.type;
    }
    throw new Error(
        `${nodePackageFormatPolicyCodes.packageTypeInvalid}: ` +
            "Accepted package metadata contains an unsupported top-level type value.",
    );
};

const createNodePackageFormatRecord = (file, root) => {
    const packageMetadataPath = file.path.slice(1);
    return Object.freeze({
        packageBoundaryPath: nodePackageBoundaryPath(packageMetadataPath),
        packageMetadataPath,
        packageMetadataSha256: file.sha256,
        packageMetadataSize: file.byteLength,
        typeState: deriveNodePackageTypeState(root),
    });
};

const deriveNodePackageFormatCatalog = async (packageMetadata, records) => {
    if (
        packageMetadata === null ||
        !Array.isArray(records) ||
        records.length === 0 ||
        records.length !== packageMetadata.files.length
    ) {
        throw new Error("Node package-format catalog input is malformed.");
    }
    for (let index = 0; index < records.length; index += 1) {
        const descriptor = packageMetadata.files[index];
        const record = records[index];
        if (
            record.packageMetadataPath !== descriptor.path ||
            record.packageBoundaryPath !==
                nodePackageBoundaryPath(descriptor.path) ||
            record.packageMetadataSize !== descriptor.byteLength ||
            record.packageMetadataSha256 !== descriptor.sha256 ||
            !["missing", "commonjs", "module"].includes(record.typeState)
        ) {
            throw new Error(
                "Node package-format record does not match its C5 descriptor.",
            );
        }
    }
    if (
        records[0].packageMetadataPath !== "package.json" ||
        records[0].packageBoundaryPath !== "/" ||
        new Set(records.map((record) => record.packageBoundaryPath)).size !==
            records.length ||
        new Set(
            records.map((record) => record.packageBoundaryPath.toLowerCase()),
        ).size !== records.length ||
        packageMetadata.dependencyRoots.some(
            (root) =>
                !records.some(
                    (record) =>
                        record.packageMetadataPath === `${root}/package.json` &&
                        record.packageBoundaryPath === `/${root}`,
                ),
        )
    ) {
        throw new Error("Node package-format boundaries are not canonical.");
    }

    const inventoryCanonicalInput =
        controlledPackageMetadataAuthority.canonicalPrefix +
        packageMetadata.dependencyRoots
            .map((root) => `|root=${root}`)
            .join("") +
        packageMetadata.files
            .map(
                (file) =>
                    `|file=${file.path},${file.byteLength},${file.sha256}`,
            )
            .join("");
    const inventoryFingerprint = await sha256(inventoryCanonicalInput);
    if (inventoryFingerprint !== packageMetadata.fingerprint) {
        throw new Error(
            "Node package-format C5 inventory fingerprint does not match.",
        );
    }
    const canonicalInput =
        nodePackageFormatAuthority.canonicalPrefix +
        `|schemaRevision=${nodePackageFormatAuthority.schemaRevision}` +
        `|catalogRevision=${nodePackageFormatAuthority.catalogRevision}` +
        `|profileSchemaRevision=` +
        provisionalNodeNextRevision2ProfileAuthority.profileSchemaRevision +
        `|profileRevision=` +
        provisionalNodeNextRevision2ProfileAuthority.profileRevision +
        `|nodeModuleOutputPolicySchemaRevision=` +
        nodePackageFormatAuthority.nodeModuleOutputPolicySchemaRevision +
        `|nodeModuleOutputPolicyRevision=` +
        nodePackageFormatAuthority.nodeModuleOutputPolicyRevision +
        `|nodeModuleOutputPolicyFingerprint=` +
        nodePackageFormatAuthority.nodeModuleOutputPolicyFingerprint +
        `|packageMetadataSchemaRevision=` +
        controlledPackageMetadataAuthority.schemaRevision +
        `|packageMetadataPolicyRevision=` +
        controlledPackageMetadataAuthority.policyRevision +
        `|packageMetadataPolicyFingerprint=` +
        controlledPackageMetadataAuthority.policyFingerprint +
        `|packageMetadataInventoryFingerprint=${inventoryFingerprint}` +
        records
            .map(
                (record) =>
                    `|record=${record.packageMetadataPath},` +
                    `${record.packageBoundaryPath},` +
                    `${record.packageMetadataSize},` +
                    `${record.packageMetadataSha256},${record.typeState}`,
            )
            .join("");
    return Object.freeze({
        canonicalInput,
        dependencyRoots: Object.freeze(
            packageMetadata.dependencyRoots.slice(),
        ),
        fingerprint: await sha256(canonicalInput),
        inventoryCanonicalInput,
        inventoryFingerprint,
        records: Object.freeze(records.slice()),
    });
};

const rejectControlledPackageTarget = (code, message) => {
    throw new Error(`${code}: ${message}`);
};

const requireControlledPackageTargetPath = (path, dependencyRoots) => {
    if (
        typeof path !== "string" ||
        path.length === 0 ||
        path.length > controlledPackageTargetAuthority.maximumPathChars ||
        !path.startsWith(controlledPackageTargetAuthority.dependencyRootPrefix) ||
        path.startsWith("/") ||
        path.includes("\\") ||
        path.includes("|") ||
        path.includes(",") ||
        !/^[A-Za-z0-9@._/+~-]+$/.test(path)
    ) {
        rejectControlledPackageTarget(
            controlledPackageTargetPolicyCodes.pathInvalid,
            "Package target path is not safe printable ASCII.",
        );
    }
    const segments = path.split("/");
    if (
        segments.some(
            (segment) =>
                segment.length === 0 ||
                segment === "." ||
                segment === ".." ||
                segment.toLowerCase() === "node_modules",
        ) ||
        segments[segments.length - 1].toLowerCase() === "package.json"
    ) {
        rejectControlledPackageTarget(
            controlledPackageTargetPolicyCodes.pathInvalid,
            "Package target path contains a reserved or non-canonical segment.",
        );
    }
    if (
        !path.endsWith(".ts") ||
        path.endsWith(".mts") ||
        path.endsWith(".cts")
    ) {
        rejectControlledPackageTarget(
            controlledPackageTargetPolicyCodes.sourceKindUnsupported,
            "Package target source kind is not supported.",
        );
    }
    if (
        dependencyRoots.filter((root) => path.startsWith(`${root}/`)).length !== 1
    ) {
        rejectControlledPackageTarget(
            controlledPackageTargetPolicyCodes.rootNotAllowed,
            "Package target is outside the exact C5 dependency-root authority.",
        );
    }
};

const parseControlledPackageTargetCanonical = async (
    canonicalInput,
    expectedFingerprint,
    expectedFileCount,
    expectedBytes,
    packageMetadata,
) => {
    if (
        typeof canonicalInput !== "string" ||
        canonicalInput.length === 0 ||
        Array.from(canonicalInput).some((character) => {
            const code = character.charCodeAt(0);
            return code < 0x20 || code > 0x7e;
        }) ||
        typeof expectedFingerprint !== "string" ||
        !sha256Pattern.test(expectedFingerprint) ||
        !Number.isSafeInteger(expectedFileCount) ||
        expectedFileCount < 0 ||
        !Number.isSafeInteger(expectedBytes) ||
        expectedBytes < 0 ||
        packageMetadata === null ||
        typeof packageMetadata !== "object" ||
        typeof packageMetadata.fingerprint !== "string" ||
        !sha256Pattern.test(packageMetadata.fingerprint) ||
        !Array.isArray(packageMetadata.dependencyRoots)
    ) {
        rejectControlledPackageTarget(
            controlledPackageTargetPolicyCodes.inventoryInvalid,
            "Package target authority is malformed.",
        );
    }
    const maximumCanonicalChars =
        controlledPackageTargetAuthority.canonicalPrefix.length +
        "|packageMetadataInventoryFingerprint=".length +
        64 +
        controlledPackageTargetAuthority.maximumAggregatePathChars +
        controlledPackageTargetAuthority.maximumFiles *
            (`|file=,${controlledPackageTargetAuthority.maximumFileBytes},`.length + 64);
    if (
        canonicalInput.length > maximumCanonicalChars ||
        expectedFileCount > controlledPackageTargetAuthority.maximumFiles ||
        expectedBytes > controlledPackageTargetAuthority.maximumTotalBytes
    ) {
        rejectControlledPackageTarget(
            controlledPackageTargetPolicyCodes.quotaExceeded,
            "Package target authority exceeds its limits.",
        );
    }
    if ((await sha256(canonicalInput)) !== expectedFingerprint) {
        rejectControlledPackageTarget(
            controlledPackageTargetPolicyCodes.inventoryInvalid,
            "Package target authority fingerprint does not match.",
        );
    }

    const parts = canonicalInput.split("|");
    if (parts.shift() !== controlledPackageTargetAuthority.canonicalPrefix) {
        rejectControlledPackageTarget(
            controlledPackageTargetPolicyCodes.inventoryInvalid,
            "Package target canonical prefix is not supported.",
        );
    }
    const bindingPrefix = "packageMetadataInventoryFingerprint=";
    const bindingPart = parts.shift();
    if (
        typeof bindingPart !== "string" ||
        !bindingPart.startsWith(bindingPrefix) ||
        !sha256Pattern.test(bindingPart.slice(bindingPrefix.length))
    ) {
        rejectControlledPackageTarget(
            controlledPackageTargetPolicyCodes.metadataIdentityInvalid,
            "Package target metadata identity is malformed.",
        );
    }
    const packageMetadataFingerprint = bindingPart.slice(bindingPrefix.length);
    if (packageMetadataFingerprint !== packageMetadata.fingerprint) {
        rejectControlledPackageTarget(
            controlledPackageTargetPolicyCodes.metadataIdentityInvalid,
            "Package targets are bound to different package metadata.",
        );
    }

    const files = [];
    for (const part of parts) {
        if (!part.startsWith("file=")) {
            rejectControlledPackageTarget(
                controlledPackageTargetPolicyCodes.inventoryInvalid,
                "Package target canonical record is not supported.",
            );
        }
        const fields = part.slice("file=".length).split(",");
        if (fields.length !== 3 || !/^(0|[1-9][0-9]*)$/.test(fields[1])) {
            rejectControlledPackageTarget(
                controlledPackageTargetPolicyCodes.inventoryInvalid,
                "Package target file record is malformed.",
            );
        }
        const byteLength = Number(fields[1]);
        if (
            !Number.isSafeInteger(byteLength) ||
            byteLength < 0 ||
            !sha256Pattern.test(fields[2])
        ) {
            rejectControlledPackageTarget(
                controlledPackageTargetPolicyCodes.inventoryInvalid,
                "Package target file authority is malformed.",
            );
        }
        if (byteLength > controlledPackageTargetAuthority.maximumFileBytes) {
            rejectControlledPackageTarget(
                controlledPackageTargetPolicyCodes.quotaExceeded,
                "Package target file exceeds its byte limit.",
            );
        }
        requireControlledPackageTargetPath(
            fields[0],
            packageMetadata.dependencyRoots,
        );
        files.push(
            Object.freeze({
                byteLength,
                path: fields[0],
                sha256: fields[2],
            }),
        );
    }
    const filePaths = files.map((file) => file.path);
    if (
        files.length !== expectedFileCount ||
        !compareStrictlySortedUnique(filePaths) ||
        files.reduce((total, file) => total + file.byteLength, 0) !== expectedBytes
    ) {
        rejectControlledPackageTarget(
            controlledPackageTargetPolicyCodes.inventoryInvalid,
            "Package target inventory is not canonical.",
        );
    }
    if (new Set(filePaths.map((path) => path.toLowerCase())).size !== files.length) {
        rejectControlledPackageTarget(
            controlledPackageTargetPolicyCodes.caseCollision,
            "Package target inventory contains a case-fold collision.",
        );
    }
    const totalPathChars = filePaths.reduce(
        (total, path) => total + path.length,
        0,
    );
    if (totalPathChars > controlledPackageTargetAuthority.maximumAggregatePathChars) {
        rejectControlledPackageTarget(
            controlledPackageTargetPolicyCodes.quotaExceeded,
            "Package target paths exceed their aggregate limit.",
        );
    }
    return Object.freeze({
        filePaths: new Set(filePaths.map((path) => `/${path}`)),
        files: Object.freeze(files.slice()),
        fingerprint: expectedFingerprint,
        packageMetadataFingerprint,
        totalBytes: expectedBytes,
        totalPathChars,
    });
};

const rejectNodeRevisionTwoPackageTarget = (code, message) => {
    throw new Error(`${code}: ${message}`);
};

const classifyNodeRevisionTwoPackageTargetPath = (path, dependencyRoots) => {
    if (
        typeof path !== "string" ||
        path.length === 0 ||
        path.length > nodeRevisionTwoPackageTargetAuthority.maximumPathChars ||
        !path.startsWith(nodeRevisionTwoPackageTargetAuthority.dependencyRootPrefix) ||
        path.startsWith("/") ||
        path.includes("\\") ||
        path.includes("|") ||
        path.includes(",") ||
        !/^[A-Za-z0-9@._/+~-]+$/.test(path)
    ) {
        rejectNodeRevisionTwoPackageTarget(
            controlledPackageTargetPolicyCodes.pathInvalid,
            "Node package-target v2 path is not safe printable ASCII.",
        );
    }
    const segments = path.split("/");
    if (
        segments.some(
            (segment) =>
                segment.length === 0 ||
                segment === "." ||
                segment === ".." ||
                segment.toLowerCase() === "node_modules",
        ) ||
        segments[segments.length - 1].toLowerCase() === "package.json"
    ) {
        rejectNodeRevisionTwoPackageTarget(
            controlledPackageTargetPolicyCodes.pathInvalid,
            "Node package-target v2 path contains a reserved segment.",
        );
    }
    if (
        dependencyRoots.filter((root) => path.startsWith(`${root}/`)).length !== 1
    ) {
        rejectNodeRevisionTwoPackageTarget(
            controlledPackageTargetPolicyCodes.rootNotAllowed,
            "Node package-target v2 is outside the exact metadata roots.",
        );
    }
    if (
        nodeRevisionTwoPackageTargetAuthority.allowedDeclarationSuffixes.some(
            (suffix) => path.endsWith(suffix),
        )
    ) {
        return controlledNodeResolutionTargetKinds.declaration;
    }
    if (
        nodeRevisionTwoPackageTargetAuthority.allowedExecutableSuffixes.some(
            (suffix) => path.endsWith(suffix),
        )
    ) {
        return controlledNodeResolutionTargetKinds.projectSource;
    }
    rejectNodeRevisionTwoPackageTarget(
        controlledPackageTargetPolicyCodes.sourceKindUnsupported,
        "Node package-target v2 source kind is not supported.",
    );
};

const parseNodeRevisionTwoPackageTargetCanonical = async (
    canonicalInput,
    expectedFingerprint,
    expectedFileCount,
    expectedBytes,
    packageMetadata,
) => {
    if (
        typeof canonicalInput !== "string" ||
        canonicalInput.length === 0 ||
        Array.from(canonicalInput).some((character) => {
            const code = character.charCodeAt(0);
            return code < 0x20 || code > 0x7e;
        }) ||
        typeof expectedFingerprint !== "string" ||
        !sha256Pattern.test(expectedFingerprint) ||
        !Number.isSafeInteger(expectedFileCount) ||
        expectedFileCount < 0 ||
        !Number.isSafeInteger(expectedBytes) ||
        expectedBytes < 0 ||
        packageMetadata === null ||
        !Array.isArray(packageMetadata.dependencyRoots) ||
        !sha256Pattern.test(packageMetadata.fingerprint)
    ) {
        rejectNodeRevisionTwoPackageTarget(
            controlledPackageTargetPolicyCodes.inventoryInvalid,
            "Node package-target v2 authority is malformed.",
        );
    }
    const maximumCanonicalChars =
        nodeRevisionTwoPackageTargetAuthority.canonicalPrefix.length +
        "|packageMetadataInventoryFingerprint=".length +
        64 +
        nodeRevisionTwoPackageTargetAuthority.maximumAggregatePathChars +
        nodeRevisionTwoPackageTargetAuthority.maximumFiles *
            (`|file=,declaration,${nodeRevisionTwoPackageTargetAuthority.maximumFileBytes},`.length +
                64);
    if (
        canonicalInput.length > maximumCanonicalChars ||
        expectedFileCount > nodeRevisionTwoPackageTargetAuthority.maximumFiles ||
        expectedBytes > nodeRevisionTwoPackageTargetAuthority.maximumTotalBytes
    ) {
        rejectNodeRevisionTwoPackageTarget(
            controlledPackageTargetPolicyCodes.quotaExceeded,
            "Node package-target v2 authority exceeds its limits.",
        );
    }
    if ((await sha256(canonicalInput)) !== expectedFingerprint) {
        rejectNodeRevisionTwoPackageTarget(
            controlledPackageTargetPolicyCodes.inventoryInvalid,
            "Node package-target v2 fingerprint does not match its canonical input.",
        );
    }
    const parts = canonicalInput.split("|");
    if (parts.shift() !== nodeRevisionTwoPackageTargetAuthority.canonicalPrefix) {
        rejectNodeRevisionTwoPackageTarget(
            controlledPackageTargetPolicyCodes.inventoryInvalid,
            "Node package-target v2 canonical prefix is not supported.",
        );
    }
    const bindingPrefix = "packageMetadataInventoryFingerprint=";
    const binding = parts.shift();
    if (
        typeof binding !== "string" ||
        !binding.startsWith(bindingPrefix) ||
        !sha256Pattern.test(binding.slice(bindingPrefix.length))
    ) {
        rejectNodeRevisionTwoPackageTarget(
            controlledPackageTargetPolicyCodes.metadataIdentityInvalid,
            "Node package-target v2 metadata binding is malformed.",
        );
    }
    const packageMetadataFingerprint = binding.slice(bindingPrefix.length);
    if (packageMetadataFingerprint !== packageMetadata.fingerprint) {
        rejectNodeRevisionTwoPackageTarget(
            controlledPackageTargetPolicyCodes.metadataIdentityInvalid,
            "Node package-target v2 is bound to different package metadata.",
        );
    }
    const files = [];
    for (const part of parts) {
        if (!part.startsWith("file=")) {
            rejectNodeRevisionTwoPackageTarget(
                controlledPackageTargetPolicyCodes.inventoryInvalid,
                "Node package-target v2 canonical record is not supported.",
            );
        }
        const fields = part.slice("file=".length).split(",");
        if (
            fields.length !== 4 ||
            !["source", "declaration"].includes(fields[1]) ||
            !/^(0|[1-9][0-9]*)$/.test(fields[2]) ||
            !sha256Pattern.test(fields[3])
        ) {
            rejectNodeRevisionTwoPackageTarget(
                controlledPackageTargetPolicyCodes.inventoryInvalid,
                "Node package-target v2 file record is malformed.",
            );
        }
        const byteLength = Number(fields[2]);
        if (
            !Number.isSafeInteger(byteLength) ||
            byteLength < 0 ||
            byteLength > nodeRevisionTwoPackageTargetAuthority.maximumFileBytes
        ) {
            rejectNodeRevisionTwoPackageTarget(
                controlledPackageTargetPolicyCodes.quotaExceeded,
                "Node package-target v2 file exceeds its byte limit.",
            );
        }
        const kind = classifyNodeRevisionTwoPackageTargetPath(
            fields[0],
            packageMetadata.dependencyRoots,
        );
        const contractKind =
            kind === controlledNodeResolutionTargetKinds.declaration
                ? "declaration"
                : "source";
        if (fields[1] !== contractKind) {
            rejectNodeRevisionTwoPackageTarget(
                controlledPackageTargetPolicyCodes.sourceKindUnsupported,
                "Node package-target v2 kind does not match its suffix.",
            );
        }
        files.push(
            Object.freeze({
                byteLength,
                kind,
                path: fields[0],
                sha256: fields[3],
            }),
        );
    }
    const paths = files.map((file) => file.path);
    const totalPathChars = paths.reduce((total, path) => total + path.length, 0);
    if (
        files.length !== expectedFileCount ||
        !compareStrictlySortedUnique(paths) ||
        files.reduce((total, file) => total + file.byteLength, 0) !== expectedBytes
    ) {
        rejectNodeRevisionTwoPackageTarget(
            controlledPackageTargetPolicyCodes.inventoryInvalid,
            "Node package-target v2 inventory is not canonical.",
        );
    }
    if (new Set(paths.map((path) => path.toLowerCase())).size !== files.length) {
        rejectNodeRevisionTwoPackageTarget(
            controlledPackageTargetPolicyCodes.caseCollision,
            "Node package-target v2 inventory contains a case-fold collision.",
        );
    }
    if (
        totalPathChars >
        nodeRevisionTwoPackageTargetAuthority.maximumAggregatePathChars
    ) {
        rejectNodeRevisionTwoPackageTarget(
            controlledPackageTargetPolicyCodes.quotaExceeded,
            "Node package-target v2 paths exceed their aggregate limit.",
        );
    }
    return Object.freeze({
        filePaths: new Set(paths.map((path) => `/${path}`)),
        files: Object.freeze(files.slice()),
        fingerprint: expectedFingerprint,
        packageMetadataFingerprint,
        totalBytes: expectedBytes,
        totalPathChars,
    });
};

const deriveNodeRevisionTwoResolutionAuthority = async (
    files,
    packageTargets,
    declaredSnapshotFingerprint,
    declaredTargetUniverseFingerprint,
) => {
    if (
        !Array.isArray(files) ||
        files.length === 0 ||
        !sha256Pattern.test(declaredSnapshotFingerprint) ||
        !sha256Pattern.test(declaredTargetUniverseFingerprint)
    ) {
        throw new Error("Node revision 2 resolver authority is malformed.");
    }
    const orderedFiles = files.slice().sort((left, right) =>
        compareCodeUnits(left.path, right.path),
    );
    const foldedFiles = new Set();
    const snapshotTargets = [];
    let snapshotCanonical =
        nodeRevisionTwoResolutionAuthority.snapshotCanonicalPrefix +
        `|profileSchemaRevision=${provisionalNodeNextRevision2ProfileAuthority.profileSchemaRevision}` +
        `|profileRevision=${provisionalNodeNextRevision2ProfileAuthority.profileRevision}` +
        `|nodeModuleOutputPolicyFingerprint=${nodePackageFormatAuthority.nodeModuleOutputPolicyFingerprint}`;
    for (const file of orderedFiles) {
        if (
            !isRecord(file) ||
            typeof file.path !== "string" ||
            typeof file.text !== "string" ||
            !Number.isSafeInteger(file.byteLength) ||
            file.byteLength < 0 ||
            !sha256Pattern.test(file.sha256) ||
            !isNodeRevisionTwoCanonicalTargetPath(file.path) ||
            foldedFiles.has(asciiCaseFold(file.path))
        ) {
            throw new Error("Node revision 2 snapshot target inventory is malformed.");
        }
        foldedFiles.add(asciiCaseFold(file.path));
        const kind = file.path.endsWith(".d.ts")
            ? controlledNodeResolutionTargetKinds.declaration
            : isNodeRevisionTwoExecutableSourcePath(file.path)
              ? controlledNodeResolutionTargetKinds.projectSource
              : "non-resolution";
        snapshotCanonical +=
            `|file=${nodeRevisionTwoCanonicalPath(file.path)},${file.byteLength},` +
            `${file.text.length},${file.sha256},${kind}`;
        if (kind !== "non-resolution") {
            snapshotTargets.push(Object.freeze({ kind, path: file.path }));
        }
    }
    if (snapshotTargets.length === 0) {
        throw new Error("Node revision 2 snapshot has no resolution targets.");
    }
    const snapshotFingerprint = await sha256(snapshotCanonical);
    if (snapshotFingerprint !== declaredSnapshotFingerprint) {
        throw new Error(
            "Node revision 2 snapshot target fingerprint does not match its raw files.",
        );
    }
    const packageResolutionTargets = (packageTargets?.files ?? []).map((file) =>
        Object.freeze({ kind: file.kind, path: `/${file.path}` }),
    );
    const resolutionTargets = [...snapshotTargets, ...packageResolutionTargets].sort(
        (left, right) => compareCodeUnits(left.path, right.path),
    );
    const exactTargets = new Set();
    const foldedTargets = new Set();
    for (const target of resolutionTargets) {
        const folded = asciiCaseFold(target.path);
        if (exactTargets.has(target.path) || foldedTargets.has(folded)) {
            throw new Error(
                "Node revision 2 target universe contains a path collision.",
            );
        }
        exactTargets.add(target.path);
        foldedTargets.add(folded);
    }
    const targetUniverseCanonical =
        nodeRevisionTwoResolutionAuthority.targetUniverseCanonicalPrefix +
        `|resolverPolicyRevision=${nodeRevisionTwoResolutionAuthority.policyRevision}` +
        `|packageTargetPolicyRevision=${nodeRevisionTwoPackageTargetAuthority.policyRevision}` +
        `|packageTargetPolicyFingerprint=${nodeRevisionTwoPackageTargetAuthority.policyFingerprint}` +
        `|snapshotTargetInventoryFingerprint=${snapshotFingerprint}` +
        `|packageTargetInventoryFingerprint=${packageTargets?.fingerprint ?? "none"}` +
        resolutionTargets
            .map(
                (target) =>
                    `|target=${nodeRevisionTwoCanonicalPath(target.path)},${target.kind}`,
            )
            .join("");
    const targetUniverseFingerprint = await sha256(targetUniverseCanonical);
    if (targetUniverseFingerprint !== declaredTargetUniverseFingerprint) {
        throw new Error(
            "Node revision 2 target-universe fingerprint does not match its raw inventories.",
        );
    }
    return Object.freeze({
        resolutionTargets: Object.freeze(resolutionTargets),
        snapshotCanonical,
        snapshotFingerprint,
        targetUniverseCanonical,
        targetUniverseFingerprint,
    });
};

const validateCanonicalVfsPath = (
    path,
    allowedRoots,
    allowedPackageMetadataPaths = null,
    allowedPackageTargetPaths = null,
    allowNodeRevisionTwoSourceKinds = false,
) => {
    if (
        typeof path !== "string" ||
        path.length < 2 ||
        path.length > maximumVfsPathChars ||
        !path.startsWith("/") ||
        path.includes("\\") ||
        !isSafeUnicodePathText(path)
    ) {
        throw new Error("Project VFS path is not a canonical absolute path.");
    }
    const segments = path.slice(1).split("/");
    if (
        segments.length > maximumVfsPathDepth ||
        segments.some(
            (segment) =>
                segment.length === 0 ||
                segment === "." ||
                segment === "..",
        )
    ) {
        throw new Error("Project VFS path contains a non-canonical segment.");
    }
    const explicitlyAllowedPackageMetadata =
        allowedPackageMetadataPaths !== null && allowedPackageMetadataPaths.has(path);
    const explicitlyAllowedPackageTarget =
        allowedPackageTargetPaths !== null && allowedPackageTargetPaths.has(path);
    if (
        !explicitlyAllowedPackageMetadata &&
        !explicitlyAllowedPackageTarget &&
        !allowedRoots.some((root) => path.startsWith(root))
    ) {
        throw new Error("Project VFS path is outside the controlled roots.");
    }
    const foldedSegments = segments.map((segment) => segment.toLowerCase());
    const foldedFileName = foldedSegments[foldedSegments.length - 1];
    if (foldedFileName === "package.json" && !explicitlyAllowedPackageMetadata) {
        throw new Error(
            "Project VFS path uses forbidden package metadata: " +
                compilerEscapeSourcePathRejectionCodes.packageMetadata,
        );
    }
    if (foldedSegments.includes("node_modules")) {
        throw new Error(
            "Project VFS path uses a forbidden package dependency tree: " +
                compilerEscapeSourcePathRejectionCodes.packageDependencyTree,
        );
    }
    if (
        !allowNodeRevisionTwoSourceKinds &&
        (
            foldedFileName.endsWith(".cts") ||
            foldedFileName.endsWith(".mts")
        )
    ) {
        throw new Error(
            "Project VFS path uses a deferred Node source kind: " +
                compilerEscapeSourcePathRejectionCodes.deferredSourceKind,
        );
    }
    return path;
};

const countSourceTokens = (text) => {
    const scanner = ts.createScanner(
        ts.ScriptTarget.ES2018,
        true,
        ts.LanguageVariant.Standard,
        text,
    );
    let tokenCount = 0;
    while (scanner.scan() !== ts.SyntaxKind.EndOfFileToken) {
        tokenCount += 1;
        if (tokenCount > maximumTokensPerFile) {
            throw new Error("Project source file exceeds the token limit.");
        }
    }
    return tokenCount;
};

const createRetainedPackageTargetRecord = (file, text) => {
    const textHolder = { text };
    const record = {};
    Object.defineProperties(record, {
        byteLength: { enumerable: true, value: file.byteLength },
        path: { enumerable: true, value: file.path },
        sha256: { enumerable: true, value: file.sha256 },
        text: {
            enumerable: true,
            get: () => textHolder.text,
        },
    });
    return Object.freeze({
        record: Object.freeze(record),
        textHolder,
    });
};

const releaseRetainedPackageTargetTexts = (textHolders) => {
    if (!Array.isArray(textHolders)) return;
    for (const holder of textHolders) {
        if (holder !== null && typeof holder === "object") {
            holder.text = "";
        }
    }
    textHolders.length = 0;
};

const createRetainedPackageTargetAuthority = (inventory) =>
    inventory === null
        ? null
        : Object.freeze({
              fileCount: inventory.files.length,
              fingerprint: inventory.fingerprint,
              packageMetadataFingerprint: inventory.packageMetadataFingerprint,
              totalBytes: inventory.totalBytes,
              totalPathChars: inventory.totalPathChars,
          });

const createRetainedPackageTargetVfsWithSourceKinds = (
    records,
    textHolders,
    authority,
    allowNodeRevisionTwoSourceKinds,
) => {
    if (
        !Array.isArray(records) ||
        !Array.isArray(textHolders) ||
        records.length !== textHolders.length ||
        records.length > controlledPackageTargetAuthority.maximumFiles ||
        (authority === null
            ? records.length !== 0
            : !isRecord(authority) ||
              !Object.isFrozen(authority) ||
              !hasExactKeys(authority, [
                  "fileCount",
                  "fingerprint",
                  "packageMetadataFingerprint",
                  "totalBytes",
                  "totalPathChars",
              ]) ||
              authority.fileCount !== records.length ||
              !sha256Pattern.test(authority.fingerprint) ||
              !sha256Pattern.test(authority.packageMetadataFingerprint))
    ) {
        throw new Error("Retained package target catalog is malformed.");
    }
    const paths = records.map((record) => record?.path);
    const allowedPaths = new Set(paths);
    const files = new Map();
    const foldedPaths = new Set();
    const directoryIndex = createDirectoryIndex();
    let totalBytes = 0;
    let totalPathChars = 0;
    let released = false;
    try {
        for (let index = 0; index < records.length; index += 1) {
            const record = records[index];
            const holder = textHolders[index];
            if (
                !isRecord(record) ||
                !Object.isFrozen(record) ||
                !hasExactKeys(record, ["byteLength", "path", "sha256", "text"]) ||
                typeof record.text !== "string" ||
                !Number.isSafeInteger(record.byteLength) ||
                record.byteLength < 0 ||
                record.byteLength > controlledPackageTargetAuthority.maximumFileBytes ||
                typeof record.sha256 !== "string" ||
                !sha256Pattern.test(record.sha256) ||
                !isRecord(holder) ||
                holder.text !== record.text
            ) {
                throw new Error("Retained package target record is malformed.");
            }
            const path = validateCanonicalVfsPath(
                record.path,
                [],
                null,
                allowedPaths,
                allowNodeRevisionTwoSourceKinds,
            );
            const foldedPath = path.toLowerCase();
            if (files.has(path) || foldedPaths.has(foldedPath)) {
                throw new Error("Retained package targets contain a path collision.");
            }
            totalBytes += record.byteLength;
            totalPathChars += path.length - 1;
            if (
                totalBytes > controlledPackageTargetAuthority.maximumTotalBytes ||
                totalPathChars >
                    controlledPackageTargetAuthority.maximumAggregatePathChars
            ) {
                throw new Error("Retained package targets exceed their authority.");
            }
            files.set(path, record);
            foldedPaths.add(foldedPath);
            directoryIndex.addFilePath(path);
        }
    } catch (error) {
        files.clear();
        foldedPaths.clear();
        directoryIndex.release();
        throw error;
    }
    if (
        authority !== null &&
        (authority.totalBytes !== totalBytes ||
            authority.totalPathChars !== totalPathChars)
    ) {
        files.clear();
        foldedPaths.clear();
        directoryIndex.release();
        throw new Error("Retained package target authority accounting drifted.");
    }
    const release = () => {
        if (released) return;
        released = true;
        files.clear();
        foldedPaths.clear();
        directoryIndex.release();
        releaseRetainedPackageTargetTexts(textHolders);
    };
    return Object.freeze({
        authority,
        directoryExists: directoryIndex.directoryExists,
        fileCount: records.length,
        fileExists: (path) => files.has(path),
        fingerprint: authority?.fingerprint ?? null,
        getDirectories: directoryIndex.getDirectories,
        paths: Object.freeze(paths.slice()),
        readFile: (path) => files.get(path)?.text,
        readRecord: (path) => files.get(path),
        release,
        packageMetadataFingerprint:
            authority?.packageMetadataFingerprint ?? null,
        totalBytes,
        totalPathChars,
    });
};

// C6-D retains target text in an independent read-only catalog for a later
// resolver/Program activation slice. The existing compiler host must not use
// this API through fileExists/readFile/readRecord yet.
const createRetainedPackageTargetVfs = (records, textHolders, authority = null) => {
    return createRetainedPackageTargetVfsWithSourceKinds(
        records,
        textHolders,
        authority,
        false,
    );
};

// P2-6 admits the NodeNext revision-2 target source kinds without changing the
// frozen C6-D v1 retained-target helper contract.
const createNodeRevisionTwoRetainedPackageTargetVfs = (
    records,
    textHolders,
    authority = null,
) => {
    return createRetainedPackageTargetVfsWithSourceKinds(
        records,
        textHolders,
        authority,
        true,
    );
};

const createDirectoryIndex = (initialPaths = []) => {
    const createNode = () => ({
        children: new Map(),
    });
    const root = createNode();
    let directoryChars = 0;
    let directoryNodes = 1;
    let pathChars = 0;
    let released = false;

    const requireNode = (directoryName) => {
        if (released || typeof directoryName !== "string") {
            return undefined;
        }
        if (directoryName === "/") {
            return root;
        }
        if (!directoryName.startsWith("/") || directoryName.endsWith("/")) {
            return undefined;
        }
        let node = root;
        for (const segment of directoryName.slice(1).split("/")) {
            node = node.children.get(segment);
            if (node === undefined) {
                return undefined;
            }
        }
        return node;
    };

    const addFilePath = (path) => {
        if (released) {
            throw new Error("Project directory index is released.");
        }
        if (pathChars + path.length > maximumProjectPathChars) {
            throw new Error(
                "Project aggregate paths exceed the character limit.",
            );
        }
        const segments = path.slice(1).split("/");
        let node = root;
        for (let index = 0; index < segments.length - 1; index += 1) {
            const segment = segments[index];
            let child = node.children.get(segment);
            if (child === undefined) {
                if (directoryNodes >= maximumProjectDirectoryNodes) {
                    throw new Error(
                        "Project directory node limit is exceeded.",
                    );
                }
                if (
                    directoryChars + segment.length >
                    maximumProjectDirectoryChars
                ) {
                    throw new Error(
                        "Project directory character limit is exceeded.",
                    );
                }
                child = createNode();
                node.children.set(segment, child);
                directoryNodes += 1;
                directoryChars += segment.length;
            }
            node = child;
        }
        pathChars += path.length;
    };

    const release = () => {
        if (released) {
            return;
        }
        released = true;
        const pending = [root];
        while (pending.length > 0) {
            const node = pending.pop();
            for (const child of node.children.values()) {
                pending.push(child);
            }
            node.children.clear();
        }
        pathChars = 0;
    };

    const index = Object.freeze({
        addFilePath,
        directoryExists: (path) => requireNode(path) !== undefined,
        getDirectories: (path) => {
            const node = requireNode(path);
            return node === undefined
                ? []
                : Array.from(node.children.keys()).sort(compareCodeUnits);
        },
        pathChars: () => pathChars,
        release,
    });
    try {
        for (const path of initialPaths) {
            addFilePath(
                validateCanonicalVfsPath(path, [
                    "/sources/",
                    "/declarations/",
                ]),
            );
        }
    } catch (error) {
        release();
        throw error;
    }
    return index;
};

const createReadonlyProjectVfs = (project) => {
    const isProvisionalRhinoProject =
        project !== null &&
        typeof project === "object" &&
        (matchesProvisionalRhinoProfileAuthority(project) ||
            matchesProvisionalRhinoRevision2ProfileAuthority(project));
    const isProvisionalNodeNextProject =
        project !== null &&
        typeof project === "object" &&
        matchesProvisionalNodeNextProfileAuthority(project);
    const isNodeRevisionTwoProject =
        project !== null &&
        typeof project === "object" &&
        project.nodeRevisionTwoProgram === true &&
        matchesProvisionalNodeNextRevision2ProfileAuthority(project);
    const isConfigNoneProject =
        isProvisionalRhinoProject ||
        isProvisionalNodeNextProject ||
        isNodeRevisionTwoProject;
    const isControlledProject =
        project !== null &&
        typeof project === "object" &&
        matchesControlledTsConfigAuthority(project);
    const packageTargetFiles =
        project !== null &&
        typeof project === "object" &&
        Object.prototype.hasOwnProperty.call(project, "packageTargetFiles")
            ? project.packageTargetFiles
            : [];
    const packageTargetTextHolders =
        project !== null &&
        typeof project === "object" &&
        Object.prototype.hasOwnProperty.call(project, "packageTargetTextHolders")
            ? project.packageTargetTextHolders
            : [];
    const packageTargetAuthority =
        project !== null &&
        typeof project === "object" &&
        Object.prototype.hasOwnProperty.call(project, "packageTargetAuthority")
            ? project.packageTargetAuthority
            : null;
    let packageMetadataCatalog =
        project !== null &&
        typeof project === "object" &&
        Object.prototype.hasOwnProperty.call(project, "packageMetadataCatalog")
            ? project.packageMetadataCatalog
            : null;
    let nodePackageFormatCatalog =
        project !== null &&
        typeof project === "object" &&
        Object.prototype.hasOwnProperty.call(project, "nodePackageFormatCatalog")
            ? project.nodePackageFormatCatalog
            : null;
    let nodeResolutionAuthority =
        project !== null &&
        typeof project === "object" &&
        Object.prototype.hasOwnProperty.call(project, "nodeResolutionAuthority")
            ? project.nodeResolutionAuthority
            : null;
    const packageTargetTokenCountsInput =
        project !== null &&
        typeof project === "object" &&
        Object.prototype.hasOwnProperty.call(project, "packageTargetTokenCounts")
            ? project.packageTargetTokenCounts
            : [];
    const packageTargetTokenCounts = Array.isArray(packageTargetTokenCountsInput)
        ? packageTargetTokenCountsInput.slice()
        : packageTargetTokenCountsInput;
    const packageTargetTokenCount =
        project !== null &&
        typeof project === "object" &&
        Object.prototype.hasOwnProperty.call(project, "packageTargetTokenCount")
            ? project.packageTargetTokenCount
            : 0;
    if (
        project === null ||
        typeof project !== "object" ||
        (!isConfigNoneProject && !isControlledProject) ||
        !Array.isArray(project.files) ||
        !Array.isArray(project.rootNames) ||
        !Array.isArray(packageTargetFiles) ||
        !Array.isArray(packageTargetTextHolders) ||
        !Array.isArray(packageTargetTokenCounts) ||
        packageTargetFiles.length !== packageTargetTextHolders.length ||
        packageTargetFiles.length !== packageTargetTokenCounts.length ||
        !Number.isSafeInteger(packageTargetTokenCount) ||
        packageTargetTokenCount < 0 ||
        (
            isNodeRevisionTwoProject
                ? nodeResolutionAuthority === null ||
                  !isRecord(nodeResolutionAuthority) ||
                  !Object.isFrozen(nodeResolutionAuthority) ||
                  !Array.isArray(nodeResolutionAuthority.resolutionTargets) ||
                  !Object.isFrozen(nodeResolutionAuthority.resolutionTargets) ||
                  !sha256Pattern.test(nodeResolutionAuthority.snapshotFingerprint) ||
                  !sha256Pattern.test(nodeResolutionAuthority.targetUniverseFingerprint) ||
                  ((packageMetadataCatalog === null) !==
                      (nodePackageFormatCatalog === null)) ||
                  (packageMetadataCatalog !== null &&
                      validateControlledNodeMetadataCatalog(packageMetadataCatalog) === null) ||
                  (packageTargetAuthority === null
                      ? packageTargetFiles.length !== 0 ||
                        packageTargetTokenCount !== 0
                      : packageMetadataCatalog === null ||
                        packageMetadataCatalog.inventoryFingerprint !==
                            packageTargetAuthority.packageMetadataFingerprint)
                : packageTargetAuthority === null
                  ? packageMetadataCatalog !== null ||
                    packageTargetFiles.length !== 0 ||
                    packageTargetTokenCount !== 0 ||
                    nodePackageFormatCatalog !== null ||
                    nodeResolutionAuthority !== null
                  : !isControlledProject ||
                    packageMetadataCatalog === null ||
                    validateControlledNodeMetadataCatalog(packageMetadataCatalog) === null ||
                    packageMetadataCatalog.inventoryFingerprint !==
                        packageTargetAuthority.packageMetadataFingerprint
        ) ||
        project.files.length === 0 ||
        project.files.length > maximumProjectFiles ||
        project.rootNames.length === 0 ||
        project.rootNames.length > maximumProjectFiles
    ) {
        throw new Error("Project snapshot is malformed or uses an unsupported profile.");
    }

    const files = new Map();
    const foldedPaths = new Set();
    let totalBytes = 0;
    let totalPathChars = 0;
    let totalTokens = 0;
    for (const file of project.files) {
        if (
            file === null ||
            typeof file !== "object" ||
            typeof file.text !== "string" ||
            !Number.isSafeInteger(file.byteLength) ||
            file.byteLength < 0 ||
            !Number.isSafeInteger(file.tokenCount) ||
            file.tokenCount < 0 ||
            file.tokenCount > maximumTokensPerFile
        ) {
            throw new Error("Project snapshot contains a malformed text file.");
        }
        const path = validateCanonicalVfsPath(
            file.path,
            ["/sources/", "/declarations/"],
            null,
            null,
            isNodeRevisionTwoProject,
        );
        if (
            (isProvisionalRhinoProject || isControlledProject) &&
            path.toLowerCase().startsWith(
                declarationLayerAuthority.nodePathPrefix,
            )
        ) {
            throw new Error(
                "Rhino project snapshot contains the Node declaration layer.",
            );
        }
        if (
            (isProvisionalNodeNextProject || isNodeRevisionTwoProject) &&
            path.toLowerCase().startsWith(
                declarationLayerAuthority.rhinoPathPrefix,
            )
        ) {
            throw new Error(
                "NodeNext project snapshot contains the Rhino declaration layer.",
            );
        }
        if (path.startsWith("/declarations/") && !path.endsWith(".d.ts")) {
            throw new Error("Project declaration path must end with .d.ts.");
        }
        if (
            isNodeRevisionTwoProject &&
            path.startsWith("/sources/") &&
            !isNodeRevisionTwoExecutableSourcePath(path) &&
            !isNodeRevisionTwoRuntimeSupportSourcePath(path) &&
            !path.endsWith(".d.ts")
        ) {
            throw new Error(
                "Node revision 2 source inventory contains an unsupported source kind.",
            );
        }
        const foldedPath = asciiCaseFold(path);
        if (files.has(path) || foldedPaths.has(foldedPath)) {
            throw new Error("Project snapshot contains a canonical path collision.");
        }
        if (file.byteLength > maximumProjectFileBytes) {
            throw new Error("Project snapshot file exceeds the byte limit.");
        }
        totalBytes += file.byteLength;
        if (totalBytes > maximumProjectBytes) {
            throw new Error("Project snapshot exceeds the total byte limit.");
        }
        totalPathChars += path.length;
        if (totalPathChars > maximumProjectPathChars) {
            throw new Error("Project snapshot paths exceed the character limit.");
        }
        totalTokens += file.tokenCount;
        if (totalTokens > maximumProjectTokens) {
            throw new Error("Project snapshot exceeds the token limit.");
        }
        files.set(path, file);
        foldedPaths.add(foldedPath);
    }
    if (
        totalBytes !== project.totalBytes ||
        totalPathChars !== project.pathChars ||
        totalTokens !== project.tokenCount ||
        packageTargetTokenCounts.some(
            (tokenCount) =>
                !Number.isSafeInteger(tokenCount) ||
                tokenCount < 0 ||
                tokenCount > maximumTokensPerFile,
        ) ||
        packageTargetTokenCounts.reduce(
            (total, tokenCount) => total + tokenCount,
            0,
        ) !== packageTargetTokenCount ||
        totalTokens + packageTargetTokenCount > maximumProjectTokens
    ) {
        throw new Error("Project snapshot accounting does not match its input.");
    }

    if (!files.has("/declarations/lib.d.ts")) {
        throw new Error("Project snapshot does not contain the controlled standard library.");
    }
    const rootNames = [];
    const foldedRoots = new Set();
    for (const candidate of project.rootNames) {
        const rootName = validateCanonicalVfsPath(
            candidate,
            ["/sources/"],
            null,
            null,
            isNodeRevisionTwoProject,
        );
        const foldedRoot = asciiCaseFold(rootName);
        if (
            (
                isNodeRevisionTwoProject
                    ? !isNodeRevisionTwoExecutableSourcePath(rootName)
                    : !isExecutableProjectSourcePath(rootName)
            ) ||
            !files.has(rootName) ||
            foldedRoots.has(foldedRoot)
        ) {
            throw new Error("Project snapshot contains an invalid root source.");
        }
        rootNames.push(rootName);
        foldedRoots.add(foldedRoot);
    }
    if (
        isControlledProject &&
        !compareStrictlySortedUnique(rootNames)
    ) {
        throw new Error("Controlled project roots are not canonical and sorted.");
    }
    const hasRhinoDeclarationLayer = Array.from(files.keys()).some((path) =>
        path.toLowerCase().startsWith(
            declarationLayerAuthority.rhinoPathPrefix,
        ),
    );
    if (
        hasRhinoDeclarationLayer &&
        !files.has(declarationLayerAuthority.rhinoEntryPath)
    ) {
        throw new Error("Rhino declaration layer has no canonical entry point.");
    }
    const hasNodeDeclarationLayer = Array.from(files.keys()).some((path) =>
        path.toLowerCase().startsWith(
            declarationLayerAuthority.nodePathPrefix,
        ),
    );
    if (
        hasNodeDeclarationLayer &&
        !files.has(declarationLayerAuthority.nodeEntryPath)
    ) {
        throw new Error("Node declaration layer has no canonical entry point.");
    }
    if (
        (isProvisionalNodeNextProject || isNodeRevisionTwoProject) &&
        !files.has(declarationLayerAuthority.nodeEntryPath)
    ) {
        throw new Error("NodeNext project snapshot has no Node declaration entry point.");
    }
    const compilerRootNames = rootNames.slice();
    if (
        (isProvisionalRhinoProject || isControlledProject) &&
        files.has(declarationLayerAuthority.rhinoEntryPath)
    ) {
        compilerRootNames.push(declarationLayerAuthority.rhinoEntryPath);
    } else if (isProvisionalNodeNextProject || isNodeRevisionTwoProject) {
        compilerRootNames.push(declarationLayerAuthority.nodeEntryPath);
    }
    const sourcePaths = Array.from(files.keys()).filter((path) =>
        isNodeRevisionTwoProject
            ? isNodeRevisionTwoExecutableSourcePath(path)
            : isExecutableProjectSourcePath(path),
    );
    if (
        isConfigNoneProject &&
        (sourcePaths.length !== rootNames.length ||
            sourcePaths.some((path) => !foldedRoots.has(asciiCaseFold(path))))
    ) {
        throw new Error("Project roots do not exactly cover the source inventory.");
    }

    const directoryIndex = project.directoryIndex;
    if (
        directoryIndex === null ||
        typeof directoryIndex !== "object" ||
        typeof directoryIndex.directoryExists !== "function" ||
        typeof directoryIndex.getDirectories !== "function" ||
        typeof directoryIndex.pathChars !== "function" ||
        directoryIndex.pathChars() !== totalPathChars ||
        typeof directoryIndex.release !== "function"
    ) {
        throw new Error("Project snapshot directory index is unavailable.");
    }

    const retainedPackageTargets = isNodeRevisionTwoProject
        ? createNodeRevisionTwoRetainedPackageTargetVfs(
              packageTargetFiles,
              packageTargetTextHolders,
              packageTargetAuthority,
          )
        : createRetainedPackageTargetVfs(
              packageTargetFiles,
              packageTargetTextHolders,
              packageTargetAuthority,
          );
    let resolutionTargets = isNodeRevisionTwoProject
        ? nodeResolutionAuthority.resolutionTargets
        : packageTargetAuthority === null
          ? null
          : Object.freeze(
              [
                  ...Array.from(files.keys())
                      .filter(
                          (path) =>
                              path.endsWith(".ts") || path.endsWith(".tsx"),
                      )
                      .sort(compareCodeUnits)
                      .map((path) =>
                          Object.freeze({
                              kind: path.endsWith(".d.ts")
                                  ? controlledNodeResolutionTargetKinds.declaration
                                  : controlledNodeResolutionTargetKinds.projectSource,
                              path,
                          }),
                      ),
                  ...retainedPackageTargets.paths.map((path) =>
                      Object.freeze({
                          kind: path.endsWith(".d.ts")
                              ? controlledNodeResolutionTargetKinds.declaration
                              : controlledNodeResolutionTargetKinds.projectSource,
                          path,
                      }),
                  ),
              ],
            );
    if (
        resolutionTargets !== null &&
        (
            isNodeRevisionTwoProject
                ? (() => {
                      const index = createNodeRevisionTwoTargetIndex(resolutionTargets);
                      index.release();
                      return false;
                  })()
                : createControlledNodeTargetIndex(resolutionTargets).kind === "failure"
        )
    ) {
        retainedPackageTargets.release();
        throw new Error("Controlled node resolution target inventory is malformed.");
    }

    let released = false;
    const release = () => {
        if (released) {
            return;
        }
        released = true;
        files.clear();
        foldedPaths.clear();
        compilerRootNames.length = 0;
        rootNames.length = 0;
        sourcePaths.length = 0;
        packageTargetTokenCounts.length = 0;
        packageMetadataCatalog = null;
        nodePackageFormatCatalog = null;
        nodeResolutionAuthority = null;
        resolutionTargets = null;
        directoryIndex.release();
        retainedPackageTargets.release();
    };

    return Object.freeze({
        compilerRootNames,
        directoryExists: directoryIndex.directoryExists,
        fileExists: (path) => files.has(path),
        getDirectories: directoryIndex.getDirectories,
        getPackageMetadataCatalog: () => packageMetadataCatalog,
        getNodePackageFormatCatalog: () => nodePackageFormatCatalog,
        getNodeResolutionAuthority: () => nodeResolutionAuthority,
        getResolutionTargets: () => resolutionTargets,
        readFile: (path) => files.get(path)?.text,
        readRecord: (path) => files.get(path),
        release,
        retainedPackageTargets,
        rootNames,
        sourcePaths: sourcePaths.sort(compareCodeUnits),
    });
};

const createControlledNodeResolutionSession = (vfs, options) => {
    let packageMetadata = vfs.getPackageMetadataCatalog();
    let targets = vfs.getResolutionTargets();
    if (packageMetadata === null || targets === null) return null;

    const exactTargets = new Map(targets.map((target) => [target.path, target]));
    const foldedTargets = new Map(
        targets.map((target) => [target.path.toLowerCase(), target]),
    );
    const authorizedPackageTargets = new Set();
    const memo = new Map();
    const resolutionDiagnostics = [];
    const resolutionDiagnosticIdentities = new Set();
    let released = false;

    const unresolvedModule = Object.freeze({ resolvedModule: undefined });
    const unresolvedTypeReference = Object.freeze({
        resolvedTypeReferenceDirective: undefined,
    });
    const unresolvedLibrary = Object.freeze({ resolvedModule: undefined });

    const requireActive = () => {
        if (released || packageMetadata === null || targets === null) {
            throw new Error("Controlled node resolution session is released.");
        }
    };

    const rememberFailure = (
        failure,
        containingPath,
        specifier,
        containingSourceFile,
    ) => {
        const diagnosticCode =
            controlledNodeResolutionFailureDiagnosticCodes[failure.code];
        if (!Number.isSafeInteger(diagnosticCode)) {
            throw new Error("Controlled node resolution returned an unknown failure.");
        }
        const identity = JSON.stringify([
            failure.code,
            containingPath,
            specifier,
        ]);
        if (resolutionDiagnosticIdentities.has(identity)) return;
        if (resolutionDiagnostics.length >= maximumDiagnostics) {
            throw new Error("Project resolution diagnostics exceed the result limit.");
        }
        const boundedSpecifier = typeof specifier === "string"
            ? specifier.slice(0, controlledNodeResolutionMaximumSpecifierChars)
            : "<invalid>";
        const diagnosticFile =
            containingSourceFile !== undefined &&
            containingSourceFile !== null &&
            containingSourceFile.fileName === containingPath
                ? containingSourceFile
                : isControlledNodeCanonicalTargetPath(containingPath)
                  ? Object.freeze({ fileName: containingPath })
                  : undefined;
        resolutionDiagnostics.push(
            Object.freeze({
                category: ts.DiagnosticCategory.Error,
                code: diagnosticCode,
                file: diagnosticFile,
                length: undefined,
                messageText:
                    `${failure.code}: Controlled snapshot resolution rejected ` +
                    `${JSON.stringify(boundedSpecifier)}.`,
                nodeResolution: Object.freeze({ policyCode: failure.code }),
                start: undefined,
            }),
        );
        resolutionDiagnosticIdentities.add(identity);
    };

    const memoizedResolve = (request) => {
        requireActive();
        const key = JSON.stringify([
            packageMetadata.inventoryFingerprint,
            vfs.retainedPackageTargets.fingerprint,
            request.containingPath,
            request.mode,
            request.conditions,
            request.specifier,
        ]);
        const cached = memo.get(key);
        if (cached !== undefined) return cached;
        const result = resolveControlledNodeSnapshot(
            request,
            packageMetadata,
            targets,
        );
        if (memo.size < maximumControlledNodeResolutionMemoEntries) {
            memo.set(key, result);
        }
        return result;
    };

    const resolveOrdinaryExtensionlessRelative = (request) => {
        if (
            vfs.readRecord(request.containingPath) === undefined ||
            !isControlledNodeCanonicalSpecifier(request.specifier)
        ) {
            return controlledNodeResolutionFailure(
                controlledNodeResolutionFailureCodes.invalidSpecifier,
            );
        }
        const containingDirectory = request.containingPath.slice(
            0,
            request.containingPath.lastIndexOf("/"),
        ) || "/";
        const candidate = resolveControlledNodeRelativePath(
            containingDirectory,
            request.specifier,
            false,
        );
        if (candidate === null) {
            return controlledNodeResolutionFailure(
                controlledNodeResolutionFailureCodes.targetEscape,
            );
        }
        const candidates = [
            `${candidate}.ts`,
            `${candidate}.d.ts`,
            `${candidate}/index.ts`,
            `${candidate}/index.d.ts`,
        ];
        for (const path of candidates) {
            const target = exactTargets.get(path);
            if (
                target !== undefined &&
                (path.startsWith("/sources/") || path.startsWith("/declarations/"))
            ) {
                return controlledNodeResolutionResolved(
                    target,
                    null,
                    null,
                    request.specifier,
                    [],
                );
            }
            if (foldedTargets.has(path.toLowerCase())) {
                return controlledNodeResolutionFailure(
                    controlledNodeResolutionFailureCodes.ambiguousCase,
                );
            }
        }
        return controlledNodeResolutionFailure(
            controlledNodeResolutionFailureCodes.targetNotInSnapshot,
        );
    };

    const requestFor = (containingPath, mode, specifier) =>
        Object.freeze({
            conditions: controlledNodeResolutionTrustedConditions,
            containingPath,
            mode,
            specifier,
        });

    const hasKnownContainingPath = (path) =>
        vfs.readRecord(path) !== undefined || authorizedPackageTargets.has(path);

    const modeForModuleLiteral = (literal, containingSourceFile) => {
        const mode = ts.getModeForUsageLocation(
            containingSourceFile,
            literal,
            options,
        );
        if (mode === ts.ModuleKind.CommonJS) {
            return controlledNodeResolutionModes.require;
        }
        if (mode === ts.ModuleKind.ESNext) {
            return controlledNodeResolutionModes.import;
        }
        return mode === undefined &&
            options.module === ts.ModuleKind.CommonJS &&
            options.moduleResolution === ts.ModuleResolutionKind.Node10
            ? controlledNodeResolutionModes.require
            : null;
    };

    const isExtensionlessRelativeSpecifier = (specifier) => {
        if (
            typeof specifier !== "string" ||
            (!specifier.startsWith("./") && !specifier.startsWith("../"))
        ) {
            return false;
        }
        const finalSegment = specifier.slice(specifier.lastIndexOf("/") + 1);
        return !finalSegment.includes(".");
    };

    const resolveModuleNameLiterals = (
        moduleLiterals,
        containingFile,
        redirectedReference,
        callbackOptions,
        containingSourceFile,
        _reusedNames,
    ) => {
        requireActive();
        return moduleLiterals.map((literal) => {
            const specifier = literal?.text;
            let failure = null;
            if (
                redirectedReference !== undefined ||
                callbackOptions !== options ||
                containingSourceFile === undefined ||
                containingSourceFile.fileName !== containingFile ||
                !hasKnownContainingPath(containingFile)
            ) {
                failure = controlledNodeResolutionFailure(
                    controlledNodeResolutionFailureCodes.invalidContainingPath,
                );
            }
            const mode = failure === null
                ? modeForModuleLiteral(literal, containingSourceFile)
                : null;
            if (failure === null && mode === null) {
                failure = controlledNodeResolutionFailure(
                    controlledNodeResolutionFailureCodes.invalidCondition,
                );
            }
            const request = requestFor(
                containingFile,
                mode ?? controlledNodeResolutionModes.require,
                specifier,
            );
            const result = failure ?? (
                isExtensionlessRelativeSpecifier(specifier) &&
                vfs.readRecord(containingFile) !== undefined
                    ? resolveOrdinaryExtensionlessRelative(request)
                    : memoizedResolve(request)
            );
            if (result.kind === "failure") {
                rememberFailure(
                    result,
                    containingFile,
                    specifier,
                    containingSourceFile,
                );
                return unresolvedModule;
            }
            const target = result.target;
            if (target.path.startsWith("/packages/")) {
                if (!vfs.retainedPackageTargets.fileExists(target.path)) {
                    const missing = controlledNodeResolutionFailure(
                        controlledNodeResolutionFailureCodes.targetNotInSnapshot,
                    );
                    rememberFailure(
                        missing,
                        containingFile,
                        specifier,
                        containingSourceFile,
                    );
                    return unresolvedModule;
                }
                authorizedPackageTargets.add(target.path);
            }
            return Object.freeze({
                resolvedModule: Object.freeze({
                    extension:
                        target.kind ===
                        controlledNodeResolutionTargetKinds.declaration
                            ? ts.Extension.Dts
                            : target.path.endsWith(".tsx")
                              ? ts.Extension.Tsx
                              : ts.Extension.Ts,
                    isExternalLibraryImport: target.path.startsWith("/packages/"),
                    resolvedFileName: target.path,
                }),
            });
        });
    };

    const resolveTypeReferenceDirectiveReferences = (
        typeDirectiveReferences,
        containingFile,
        redirectedReference,
        callbackOptions,
        containingSourceFile,
        _reusedNames,
    ) => {
        requireActive();
        return typeDirectiveReferences.map((reference) => {
            const specifier =
                typeof reference === "string" ? reference : reference?.fileName;
            let result;
            if (
                redirectedReference !== undefined ||
                callbackOptions !== options ||
                !hasKnownContainingPath(containingFile)
            ) {
                result = controlledNodeResolutionFailure(
                    controlledNodeResolutionFailureCodes.invalidContainingPath,
                );
            } else {
                result = memoizedResolve(
                    requestFor(
                        containingFile,
                        controlledNodeResolutionModes.types,
                        specifier,
                    ),
                );
            }
            if (
                result.kind === "resolved" &&
                result.target.kind !==
                    controlledNodeResolutionTargetKinds.declaration
            ) {
                result = controlledNodeResolutionFailure(
                    controlledNodeResolutionFailureCodes.unsupportedSourceKind,
                );
            }
            if (result.kind === "failure") {
                rememberFailure(
                    result,
                    containingFile,
                    specifier,
                    containingSourceFile,
                );
                return unresolvedTypeReference;
            }
            if (!vfs.retainedPackageTargets.fileExists(result.target.path)) {
                const missing = controlledNodeResolutionFailure(
                    controlledNodeResolutionFailureCodes.targetNotInSnapshot,
                );
                rememberFailure(
                    missing,
                    containingFile,
                    specifier,
                    containingSourceFile,
                );
                return unresolvedTypeReference;
            }
            authorizedPackageTargets.add(result.target.path);
            return Object.freeze({
                resolvedTypeReferenceDirective: Object.freeze({
                    isExternalLibraryImport: true,
                    primary: true,
                    resolvedFileName: result.target.path,
                }),
            });
        });
    };

    const release = () => {
        if (released) return;
        released = true;
        authorizedPackageTargets.clear();
        exactTargets.clear();
        foldedTargets.clear();
        memo.clear();
        resolutionDiagnostics.length = 0;
        resolutionDiagnosticIdentities.clear();
        packageMetadata = null;
        targets = null;
    };

    return Object.freeze({
        diagnostics: () => resolutionDiagnostics.slice(),
        hasFailures: () => resolutionDiagnostics.length !== 0,
        isGrantedPackageTarget: (path) => authorizedPackageTargets.has(path),
        readGrantedPackageTarget: (path) =>
            authorizedPackageTargets.has(path)
                ? vfs.retainedPackageTargets.readRecord(path)
                : undefined,
        release,
        resolveLibrary: () => unresolvedLibrary,
        resolveModuleNameLiterals,
        resolveTypeReferenceDirectiveReferences,
    });
};

const nodeRevisionTwoImpliedModuleFormat = (path, packageFormatCatalog) => {
    if (!isNodeRevisionTwoCanonicalTargetPath(path)) {
        throw new Error("Node revision 2 implied-format path is not canonical.");
    }
    if (
        path.startsWith(declarationLayerAuthority.nodePathPrefix) &&
        path.endsWith(".d.ts")
    ) {
        return "commonjs";
    }
    if (path.endsWith(".d.mts") || path.endsWith(".mts")) return "esm";
    if (path.endsWith(".d.cts") || path.endsWith(".cts")) return "commonjs";
    if (!path.endsWith(".d.ts") && !path.endsWith(".ts")) {
        throw new Error("Node revision 2 implied-format source kind is unsupported.");
    }
    if (packageFormatCatalog === null) return "commonjs";
    if (
        !isRecord(packageFormatCatalog) ||
        !Object.isFrozen(packageFormatCatalog) ||
        !Array.isArray(packageFormatCatalog.records) ||
        !Object.isFrozen(packageFormatCatalog.records)
    ) {
        throw new Error("Node revision 2 package-format authority is malformed.");
    }
    const nearest = packageFormatCatalog.records
        .filter(
            (record) =>
                record.packageBoundaryPath === "/" ||
                path.startsWith(`${record.packageBoundaryPath}/`),
        )
        .sort(
            (left, right) =>
                right.packageBoundaryPath.length - left.packageBoundaryPath.length,
        )[0];
    return nearest?.typeState === "module" ? "esm" : "commonjs";
};

const nodeRevisionTwoTypeScriptExtension = (path) => {
    if (path.endsWith(".d.mts")) return ts.Extension.Dmts;
    if (path.endsWith(".d.cts")) return ts.Extension.Dcts;
    if (path.endsWith(".d.ts")) return ts.Extension.Dts;
    if (path.endsWith(".mts")) return ts.Extension.Mts;
    if (path.endsWith(".cts")) return ts.Extension.Cts;
    if (path.endsWith(".ts")) return ts.Extension.Ts;
    throw new Error("Node revision 2 resolved an unsupported source kind.");
};

// Trusted Node declarations are plugin-owned, fingerprinted inputs. Their ambient module names
// form a closed local catalog and must not be routed through caller-owned package metadata.
// Building the catalog from the TypeScript AST keeps aliases such as `node:http` and
// `autojs6:compat` bound to the exact declaration file that defines them.
const createNodeRevisionTwoTrustedDeclarationModuleIndex = (vfs, targets) => {
    const modules = new Map();
    for (const target of targets) {
        if (
            target.kind !== controlledNodeResolutionTargetKinds.declaration ||
            !target.path.startsWith(declarationLayerAuthority.nodePathPrefix)
        ) {
            continue;
        }
        const record = vfs.readRecord(target.path);
        if (record === undefined) {
            throw new Error(
                "Node revision 2 trusted declaration target is missing from the VFS.",
            );
        }
        const sourceFile = ts.createSourceFile(
            target.path,
            record.text,
            ts.ScriptTarget.Latest,
            false,
            ts.ScriptKind.TS,
        );
        for (const statement of sourceFile.statements) {
            if (
                !ts.isModuleDeclaration(statement) ||
                !ts.isStringLiteral(statement.name)
            ) {
                continue;
            }
            const specifier = statement.name.text;
            if (
                specifier.length === 0 ||
                specifier.length > controlledNodeResolutionMaximumSpecifierChars ||
                Array.from(specifier).some((character) => {
                    const code = character.charCodeAt(0);
                    return code < 0x21 || code > 0x7e;
                })
            ) {
                throw new Error(
                    "Node revision 2 trusted declaration module name is malformed.",
                );
            }
            const previous = modules.get(specifier);
            if (previous !== undefined && previous.path !== target.path) {
                throw new Error(
                    "Node revision 2 trusted declaration module name is ambiguous.",
                );
            }
            modules.set(specifier, target);
        }
    }
    return Object.freeze({
        release: () => modules.clear(),
        resolve: (specifier) => modules.get(specifier) ?? null,
    });
};

const createNodeRevisionTwoResolutionSession = (vfs, options) => {
    let packageMetadata = vfs.getPackageMetadataCatalog();
    let packageFormat = vfs.getNodePackageFormatCatalog();
    let authority = vfs.getNodeResolutionAuthority();
    if (
        authority === null ||
        !Array.isArray(authority.resolutionTargets) ||
        !Object.isFrozen(authority.resolutionTargets) ||
        ((packageMetadata === null) !== (packageFormat === null))
    ) {
        throw new Error("Node revision 2 resolver session authority is malformed.");
    }
    const targetIndex = createNodeRevisionTwoTargetIndex(
        authority.resolutionTargets,
    );
    const trustedDeclarationModules =
        createNodeRevisionTwoTrustedDeclarationModuleIndex(
            vfs,
            authority.resolutionTargets,
        );
    const authorizedPackageTargets = new Set();
    const memo = new Map();
    const resolutionDiagnostics = [];
    const resolutionDiagnosticIdentities = new Set();
    let released = false;
    const unresolvedModule = Object.freeze({ resolvedModule: undefined });
    const unresolvedTypeReference = Object.freeze({
        resolvedTypeReferenceDirective: undefined,
    });
    const unresolvedLibrary = Object.freeze({ resolvedModule: undefined });
    const requireActive = () => {
        if (released || authority === null) {
            throw new Error("Node revision 2 resolver session is released.");
        }
    };
    const hasKnownContainingPath = (path) =>
        vfs.readRecord(path) !== undefined || authorizedPackageTargets.has(path);
    const rememberFailure = (
        failure,
        containingPath,
        specifier,
        containingSourceFile,
    ) => {
        const diagnosticCode =
            controlledNodeResolutionFailureDiagnosticCodes[failure.code];
        if (!Number.isSafeInteger(diagnosticCode)) {
            throw new Error("Node revision 2 resolver returned an unknown failure.");
        }
        const identity = JSON.stringify([
            failure.code,
            containingPath,
            specifier,
        ]);
        if (resolutionDiagnosticIdentities.has(identity)) return;
        if (resolutionDiagnostics.length >= maximumDiagnostics) {
            throw new Error("Project resolution diagnostics exceed the result limit.");
        }
        const boundedSpecifier = typeof specifier === "string"
            ? specifier.slice(0, controlledNodeResolutionMaximumSpecifierChars)
            : "<invalid>";
        const diagnosticFile =
            containingSourceFile !== undefined &&
            containingSourceFile !== null &&
            containingSourceFile.fileName === containingPath
                ? containingSourceFile
                : isNodeRevisionTwoCanonicalTargetPath(containingPath)
                  ? Object.freeze({ fileName: containingPath })
                  : undefined;
        resolutionDiagnostics.push(
            Object.freeze({
                category: ts.DiagnosticCategory.Error,
                code: diagnosticCode,
                file: diagnosticFile,
                length: undefined,
                messageText:
                    `${failure.code}: Node revision 2 snapshot resolution rejected ` +
                    `${JSON.stringify(boundedSpecifier)}.`,
                nodeResolution: Object.freeze({ policyCode: failure.code }),
                start: undefined,
            }),
        );
        resolutionDiagnosticIdentities.add(identity);
    };
    const modeFor = (containingPath) =>
        nodeRevisionTwoImpliedModuleFormat(containingPath, packageFormat) === "esm"
            ? controlledNodeResolutionModes.import
            : controlledNodeResolutionModes.require;
    const memoizedResolve = (containingPath, specifier, mode) => {
        requireActive();
        const key = JSON.stringify([
            authority.targetUniverseFingerprint,
            packageMetadata?.inventoryFingerprint ?? null,
            containingPath,
            mode,
            specifier,
        ]);
        const cached = memo.get(key);
        if (cached !== undefined) return cached;
        const result = resolveNodeRevisionTwoSnapshot(
            Object.freeze({
                conditions: Object.freeze([]),
                containingPath,
                mode,
                specifier,
            }),
            packageMetadata,
            targetIndex,
        );
        if (memo.size < maximumControlledNodeResolutionMemoEntries) {
            memo.set(key, result);
        }
        return result;
    };
    const grantResolvedTarget = (
        result,
        containingPath,
        specifier,
        containingSourceFile,
    ) => {
        if (result.kind === "failure") {
            rememberFailure(
                result,
                containingPath,
                specifier,
                containingSourceFile,
            );
            return null;
        }
        if (result.target.path.startsWith("/packages/")) {
            if (!vfs.retainedPackageTargets.fileExists(result.target.path)) {
                rememberFailure(
                    controlledNodeResolutionFailure(
                        controlledNodeResolutionFailureCodes.targetNotInSnapshot,
                    ),
                    containingPath,
                    specifier,
                    containingSourceFile,
                );
                return null;
            }
            authorizedPackageTargets.add(result.target.path);
        }
        return result.target;
    };
    const resolveModuleNameLiterals = (
        moduleLiterals,
        containingFile,
        redirectedReference,
        callbackOptions,
        containingSourceFile,
        _reusedNames,
    ) => {
        requireActive();
        return moduleLiterals.map((literal) => {
            const specifier = literal?.text;
            if (
                redirectedReference !== undefined ||
                callbackOptions !== options ||
                containingSourceFile === undefined ||
                containingSourceFile.fileName !== containingFile ||
                !hasKnownContainingPath(containingFile)
            ) {
                rememberFailure(
                    controlledNodeResolutionFailure(
                        controlledNodeResolutionFailureCodes.invalidContainingPath,
                    ),
                    containingFile,
                    specifier,
                    containingSourceFile,
                );
                return unresolvedModule;
            }
            const trustedDeclarationTarget =
                typeof specifier === "string"
                    ? trustedDeclarationModules.resolve(specifier)
                    : null;
            const target = trustedDeclarationTarget ?? grantResolvedTarget(
                memoizedResolve(containingFile, specifier, modeFor(containingFile)),
                containingFile,
                specifier,
                containingSourceFile,
            );
            return target === null
                ? unresolvedModule
                : Object.freeze({
                      resolvedModule: Object.freeze({
                          extension: nodeRevisionTwoTypeScriptExtension(target.path),
                          isExternalLibraryImport: target.path.startsWith("/packages/"),
                          resolvedFileName: target.path,
                      }),
                  });
        });
    };
    const resolveTypeReferenceDirectiveReferences = (
        typeDirectiveReferences,
        containingFile,
        redirectedReference,
        callbackOptions,
        containingSourceFile,
        _reusedNames,
    ) => {
        requireActive();
        return typeDirectiveReferences.map((reference) => {
            const specifier =
                typeof reference === "string" ? reference : reference?.fileName;
            if (
                redirectedReference !== undefined ||
                callbackOptions !== options ||
                !hasKnownContainingPath(containingFile)
            ) {
                rememberFailure(
                    controlledNodeResolutionFailure(
                        controlledNodeResolutionFailureCodes.invalidContainingPath,
                    ),
                    containingFile,
                    specifier,
                    containingSourceFile,
                );
                return unresolvedTypeReference;
            }
            const result = memoizedResolve(
                containingFile,
                specifier,
                controlledNodeResolutionModes.types,
            );
            const target = result.kind === "resolved" &&
                result.target.kind !== controlledNodeResolutionTargetKinds.declaration
                    ? grantResolvedTarget(
                          controlledNodeResolutionFailure(
                              controlledNodeResolutionFailureCodes.unsupportedSourceKind,
                          ),
                          containingFile,
                          specifier,
                          containingSourceFile,
                      )
                    : grantResolvedTarget(
                          result,
                          containingFile,
                          specifier,
                          containingSourceFile,
                      );
            return target === null
                ? unresolvedTypeReference
                : Object.freeze({
                      resolvedTypeReferenceDirective: Object.freeze({
                          isExternalLibraryImport: target.path.startsWith("/packages/"),
                          primary: true,
                          resolvedFileName: target.path,
                      }),
                  });
        });
    };
    const applyImpliedModuleFormat = (sourceFile) => {
        requireActive();
        const implied = nodeRevisionTwoImpliedModuleFormat(
            sourceFile.fileName,
            packageFormat,
        );
        sourceFile.impliedNodeFormat = implied === "esm"
            ? ts.ModuleKind.ESNext
            : ts.ModuleKind.CommonJS;
        return sourceFile.impliedNodeFormat;
    };
    const release = () => {
        if (released) return;
        released = true;
        authorizedPackageTargets.clear();
        memo.clear();
        resolutionDiagnostics.length = 0;
        resolutionDiagnosticIdentities.clear();
        targetIndex.release();
        trustedDeclarationModules.release();
        packageMetadata = null;
        packageFormat = null;
        authority = null;
    };
    return Object.freeze({
        applyImpliedModuleFormat,
        diagnostics: () => resolutionDiagnostics.slice(),
        hasFailures: () => resolutionDiagnostics.length !== 0,
        impliedModuleFormatFor: (path) =>
            nodeRevisionTwoImpliedModuleFormat(path, packageFormat),
        isGrantedPackageTarget: (path) => authorizedPackageTargets.has(path),
        readGrantedPackageTarget: (path) =>
            authorizedPackageTargets.has(path)
                ? vfs.retainedPackageTargets.readRecord(path)
                : undefined,
        release,
        resolveLibrary: () => unresolvedLibrary,
        resolveModuleNameLiterals,
        resolveTypeReferenceDirectiveReferences,
    });
};

const createNodeRevisionTwoSourceFile = (
    fileName,
    text,
    languageVersion,
    resolutionSession,
) => {
    const impliedNodeFormat =
        resolutionSession.impliedModuleFormatFor(fileName) === "esm"
            ? ts.ModuleKind.ESNext
            : ts.ModuleKind.CommonJS;
    const sourceFile = ts.createSourceFile(
        fileName,
        text,
        { languageVersion, impliedNodeFormat },
        true,
    );
    if (sourceFile.impliedNodeFormat !== impliedNodeFormat) {
        sourceFile.impliedNodeFormat = impliedNodeFormat;
    }
    if (sourceFile.impliedNodeFormat !== impliedNodeFormat) {
        throw new Error(
            "Node revision 2 SourceFile rejected its implied module format.",
        );
    }
    return sourceFile;
};

const createProjectCompilerHost = (
    vfs,
    outputs,
    outputBudget,
    sourceFileCache,
    execution,
    resolutionSession = null,
    isNodeRevisionTwoProject = false,
    defaultLibraryPath = "/declarations/lib.d.ts",
) => {
    if (
        defaultLibraryPath !== "/declarations/lib.d.ts" &&
        defaultLibraryPath !== "/declarations/lib.es2018.d.ts"
    ) {
        throw new Error("Project compiler host received an invalid default library.");
    }
    const host = {
    createDirectory: () => {},
    directoryExists: vfs.directoryExists,
    fileExists: vfs.fileExists,
    getCanonicalFileName: (fileName) => fileName,
    getCurrentDirectory: () => "/",
    getDefaultLibFileName: () => defaultLibraryPath,
    getDefaultLibLocation: () => "/declarations",
    getDirectories: vfs.getDirectories,
    getNewLine: () => "\n",
    getSourceFile: (fileName, languageVersion) => {
        const cached = sourceFileCache.get(fileName);
        if (cached !== undefined) {
            return cached;
        }
        const record =
            vfs.readRecord(fileName) ??
            resolutionSession?.readGrantedPackageTarget(fileName);
        if (record === undefined) {
            return undefined;
        }
        const sourceFile = isNodeRevisionTwoProject
            ? createNodeRevisionTwoSourceFile(
                  fileName,
                  record.text,
                  languageVersion,
                  resolutionSession,
              )
            : ts.createSourceFile(
                  fileName,
                  record.text,
                  languageVersion,
                  true,
              );
        // Builder programs require a stable version on every SourceFile. R2-S1
        // creates a fresh program per job and deliberately does not share state.
        sourceFile.version = "0";
        sourceFileCache.set(fileName, sourceFile);
        if (
            isNodeRevisionTwoProject
                ? isNodeRevisionTwoExecutableSourcePath(fileName)
                : isExecutableProjectSourcePath(fileName)
        ) {
            advanceProjectExecutionProgress(execution, "parse");
        }
        return sourceFile;
    },
    readFile: vfs.readFile,
    realpath: (path) => path,
    useCaseSensitiveFileNames: () => true,
    writeFile: (fileName, text, _writeByteOrderMark, _onError, sourceFiles) => {
        const path = validateCanonicalVfsPath(fileName, ["/outputs/"]);
        if (
            (
                isNodeRevisionTwoProject
                    ? !path.endsWith(".js") &&
                      !path.endsWith(".mjs") &&
                      !path.endsWith(".cjs") &&
                      !path.endsWith(".js.map") &&
                      !path.endsWith(".mjs.map") &&
                      !path.endsWith(".cjs.map")
                    : !path.endsWith(".js") && !path.endsWith(".js.map")
            ) ||
            outputs.has(path) ||
            outputs.size >= maximumOutputFiles
        ) {
            throw new Error("TypeScript attempted to emit an unsupported output.");
        }
        const outputBytes = new TextEncoder().encode(text).byteLength;
        const maximumFileBytes = path.endsWith(".map")
            ? maximumSourceMapBytes
            : maximumJavaScriptBytes;
        if (
            outputBytes > maximumFileBytes ||
            outputBudget.bytes + outputBytes > maximumOutputBytes
        ) {
            throw new Error("TypeScript output exceeds the byte limit.");
        }
        outputBudget.bytes += outputBytes;
        outputs.set(path, {
            sourcePaths: (sourceFiles || [])
                .map((sourceFile) => sourceFile.fileName)
                .filter((sourcePath) => sourcePath.startsWith("/sources/"))
                .sort(),
            text,
        });
        advanceProjectExecutionProgress(execution, "emit");
    },
    };
    if (resolutionSession !== null) {
        host.resolveLibrary = resolutionSession.resolveLibrary;
        host.resolveModuleNameLiterals =
            resolutionSession.resolveModuleNameLiterals;
        host.resolveTypeReferenceDirectiveReferences =
            resolutionSession.resolveTypeReferenceDirectiveReferences;
    }
    return host;
};

const serializeDiagnosticRelatedInformation = (related) => {
    const message = flattenDiagnosticMessage(related.messageText);
    if (message.length > 4096) {
        throw new Error(
            "Project related-information message exceeds the character limit.",
        );
    }
    const serialized = {
        category: related.category,
        code: related.code,
        length: typeof related.length === "number" ? related.length : null,
        message,
        sourcePath: related.file ? related.file.fileName : null,
        start: typeof related.start === "number" ? related.start : null,
    };
    if (related.file && typeof related.start === "number") {
        const location = related.file.getLineAndCharacterOfPosition(related.start);
        serialized.column = location.character + 1;
        serialized.line = location.line + 1;
    } else {
        serialized.column = null;
        serialized.line = null;
    }
    return serialized;
};

const serializeDiagnostic = (phase, diagnostic) => {
    const message = flattenDiagnosticMessage(diagnostic.messageText);
    if (message.length > 4096) {
        throw new Error("Project diagnostic message exceeds the character limit.");
    }
    const serialized = {
        category: diagnostic.category,
        code: diagnostic.code,
        length:
            typeof diagnostic.length === "number" ? diagnostic.length : null,
        message,
        phase,
        sourcePath: diagnostic.file ? diagnostic.file.fileName : null,
        start: typeof diagnostic.start === "number" ? diagnostic.start : null,
    };
    if (
        diagnostic.relatedInformation !== undefined &&
        !Array.isArray(diagnostic.relatedInformation)
    ) {
        throw new Error("Project diagnostic related information is malformed.");
    }
    const relatedInformation = diagnostic.relatedInformation || [];
    if (relatedInformation.length > maximumRelatedInformationPerDiagnostic) {
        throw new Error("Project diagnostic related information exceeds the limit.");
    }
    if (relatedInformation.length !== 0) {
        serialized.relatedInformation = relatedInformation.map(
            serializeDiagnosticRelatedInformation,
        );
    }
    if (phase === "resolution") {
        if (
            !isRecord(diagnostic.nodeResolution) ||
            !hasExactKeys(diagnostic.nodeResolution, ["policyCode"]) ||
            !Object.prototype.hasOwnProperty.call(
                controlledNodeResolutionFailureDiagnosticCodes,
                diagnostic.nodeResolution.policyCode,
            ) ||
            controlledNodeResolutionFailureDiagnosticCodes[
                diagnostic.nodeResolution.policyCode
            ] !== diagnostic.code
        ) {
            throw new Error("Controlled node resolution diagnostic is malformed.");
        }
        serialized.nodeResolution = {
            policyCode: diagnostic.nodeResolution.policyCode,
        };
    } else if (
        Object.prototype.hasOwnProperty.call(diagnostic, "nodeResolution")
    ) {
        throw new Error("Non-resolution diagnostic carried node resolution data.");
    }
    if (diagnostic.file && typeof diagnostic.start === "number") {
        const location = diagnostic.file.getLineAndCharacterOfPosition(
            diagnostic.start,
        );
        serialized.column = location.character + 1;
        serialized.line = location.line + 1;
    } else {
        serialized.column = null;
        serialized.line = null;
    }
    return serialized;
};

const serializedDiagnosticIdentity = (diagnostic) =>
    JSON.stringify([
        diagnostic.category,
        diagnostic.code,
        diagnostic.sourcePath,
        diagnostic.start,
        diagnostic.length,
        diagnostic.message,
        diagnostic.nodeResolution?.policyCode ?? null,
        diagnostic.relatedInformation ?? [],
    ]);

const createDiagnosticCollector = () => {
    const diagnostics = [];
    const seenDiagnostics = new Set();
    const textEncoder = new TextEncoder();
    let diagnosticsBytes = 2;
    let relatedInformationCount = 0;
    let errorCount = 0;

    const append = (phase, diagnostic) => {
        if (diagnostics.length >= maximumDiagnostics) {
            throw new Error("Project diagnostics exceed the result limit.");
        }
        const serialized = serializeDiagnostic(phase, diagnostic);
        const identity = serializedDiagnosticIdentity(serialized);
        if (seenDiagnostics.has(identity)) {
            return;
        }
        const nextRelatedInformationCount =
            relatedInformationCount +
            (serialized.relatedInformation?.length ?? 0);
        if (nextRelatedInformationCount > maximumRelatedInformationTotal) {
            throw new Error("Project related information exceeds the result limit.");
        }
        const encoded = JSON.stringify(serialized);
        const nextBytes =
            diagnosticsBytes +
            (diagnostics.length === 0 ? 0 : 1) +
            textEncoder.encode(encoded).byteLength;
        if (
            nextBytes > maximumDiagnosticsBytes
        ) {
            throw new Error("Project diagnostics exceed the result limit.");
        }
        diagnosticsBytes = nextBytes;
        relatedInformationCount = nextRelatedInformationCount;
        diagnostics.push(serialized);
        seenDiagnostics.add(identity);
        if (serialized.category === ts.DiagnosticCategory.Error) {
            errorCount += 1;
        }
    };

    return Object.freeze({
        consume: (phase, items) => {
            for (const diagnostic of items) {
                append(phase, diagnostic);
            }
        },
        diagnostics,
        errorCount: () => errorCount,
        release: () => {
            seenDiagnostics.clear();
        },
    });
};

const maximumControlledTsConfigPropertyWarnings = 64;
const createControlledTsConfigWarningDiagnostics = (ignoredProperties) => {
    if (!Array.isArray(ignoredProperties) || !Object.isFrozen(ignoredProperties)) {
        throw new Error("Controlled tsconfig ignored-property authority is malformed.");
    }
    const diagnostics = ignoredProperties
        .slice(0, maximumControlledTsConfigPropertyWarnings)
        .map((property) => {
            const topLevel =
                isRecord(property) &&
                hasExactKeys(property, ["kind", "name"]) &&
                property.kind === "top-level";
            const compilerOption =
                isRecord(property) &&
                hasExactKeys(property, ["kind", "known", "name"]) &&
                property.kind === "compiler-option" &&
                typeof property.known === "boolean";
            if (
                (!topLevel && !compilerOption) ||
                typeof property.name !== "string"
            ) {
                throw new Error(
                    "Controlled tsconfig ignored-property record is malformed.",
                );
            }
            const boundedName = JSON.stringify(property.name.slice(0, 256));
            const label = topLevel
                ? "top-level property"
                : property.known
                  ? "unsupported compiler option"
                  : "unknown compiler option";
            return {
                category: ts.DiagnosticCategory.Warning,
                code: topLevel
                    ? controlledTsConfigIgnoredDiagnosticCodes.topLevelProperty
                    : controlledTsConfigIgnoredDiagnosticCodes.compilerOption,
                messageText:
                    `Ignored controlled tsconfig ${label} ${boundedName}; ` +
                    "the frozen compiler profile is unchanged.",
            };
        });
    if (ignoredProperties.length > maximumControlledTsConfigPropertyWarnings) {
        diagnostics.push({
            category: ts.DiagnosticCategory.Warning,
            code: controlledTsConfigIgnoredDiagnosticCodes.topLevelProperty,
            messageText:
                `Ignored ${ignoredProperties.length - maximumControlledTsConfigPropertyWarnings} ` +
                "additional controlled tsconfig properties; the warning list was bounded.",
        });
    }
    return diagnostics;
};

const resolveSourceMapPath = (
    mapPath,
    sourceRoot,
    source,
    allowNodeRevisionTwoSourceKinds = false,
) => {
    if (
        typeof sourceRoot !== "string" ||
        typeof source !== "string" ||
        source.includes("\\") ||
        sourceRoot.includes("\\") ||
        source.includes("://") ||
        sourceRoot.includes("://")
    ) {
        throw new Error("Project Source Map contains an unsupported source path.");
    }
    const baseSegments = mapPath.slice(1).split("/");
    baseSegments.pop();
    const combined =
        source.startsWith("/")
            ? source
            : `${baseSegments.length === 0 ? "/" : `/${baseSegments.join("/")}/`}` +
                `${sourceRoot.length === 0 ? "" : `${sourceRoot}/`}${source}`;
    const resolved = [];
    for (const segment of combined.split("/")) {
        if (segment.length === 0 || segment === ".") {
            continue;
        }
        if (segment === "..") {
            if (resolved.length === 0) {
                throw new Error("Project Source Map source escapes the VFS.");
            }
            resolved.pop();
        } else {
            resolved.push(segment);
        }
    }
    return validateCanonicalVfsPath(
        `/${resolved.join("/")}`,
        ["/sources/"],
        null,
        null,
        allowNodeRevisionTwoSourceKinds,
    );
};

const enterProjectExecutionPhase = (execution, phase, totalUnits = 0) => {
    if (
        execution.activePhase !== null ||
        execution.nextPhaseIndex >= projectExecutionPhases.length ||
        projectExecutionPhases[execution.nextPhaseIndex] !== phase ||
        (
            phase === "ingress"
                ? totalUnits !== 0
                : !Number.isSafeInteger(totalUnits) || totalUnits <= 0
        )
    ) {
        throw new Error("Project execution phase is out of sequence.");
    }
    execution.activePhase = phase;
    execution.phaseProgress = {
        completedUnits: 0,
        nextSequence: 1,
        phase,
        totalUnits,
    };
    self.postMessage({
        kind: "projectPhase",
        phase,
        requestId: execution.requestId,
        state: "started",
    });
    if (execution.workerSpinPhase !== phase) {
        return;
    }
    const spinDeadline = Date.now() + execution.workerSpinMilliseconds;
    while (Date.now() < spinDeadline) {
        // DEBUG-only native tests use this bounded phase hook to prove that
        // keyed stop or renderer loss terminates synchronous Worker execution.
        // Production framing always sends phase "none" with zero milliseconds.
    }
};

const advanceProjectExecutionProgress = (execution, phase) => {
    const progress = execution.phaseProgress;
    if (
        execution.activePhase !== phase ||
        progress === null ||
        progress.phase !== phase ||
        !Number.isSafeInteger(progress.completedUnits) ||
        progress.completedUnits < 0 ||
        progress.completedUnits >= progress.totalUnits
    ) {
        throw new Error("Project execution progress is out of sequence.");
    }
    progress.completedUnits += 1;
    if (
        progress.totalUnits < minimumProjectExecutionProgressUnits ||
        progress.nextSequence > 2
    ) {
        return;
    }
    const divisor = progress.nextSequence === 1 ? 8 : 4;
    const threshold = Math.ceil(progress.totalUnits / divisor);
    if (progress.completedUnits !== threshold) {
        return;
    }
    self.postMessage({
        completedUnits: progress.completedUnits,
        kind: "projectPhaseProgress",
        phase,
        requestId: execution.requestId,
        sequence: progress.nextSequence,
        totalUnits: progress.totalUnits,
    });
    progress.nextSequence += 1;
};

const completeProjectExecutionPhase = (execution, phase) => {
    if (
        execution.activePhase !== phase ||
        execution.phaseProgress === null ||
        execution.phaseProgress.phase !== phase ||
        projectExecutionPhases[execution.nextPhaseIndex] !== phase
    ) {
        throw new Error("Project execution phase completion is out of sequence.");
    }
    self.postMessage({
        kind: "projectPhase",
        phase,
        requestId: execution.requestId,
        state: "completed",
    });
    execution.activePhase = null;
    execution.phaseProgress = null;
    execution.nextPhaseIndex += 1;
};

const createProvisionalRhinoCompilerOptions = () => {
    const options = Object.freeze({
        allowJs: false,
        checkJs: false,
        ignoreDeprecations: "6.0",
        incremental: false,
        inlineSourceMap: false,
        inlineSources: false,
        module: ts.ModuleKind.CommonJS,
        moduleResolution: ts.ModuleResolutionKind.Node10,
        newLine: ts.NewLineKind.LineFeed,
        noEmitOnError: true,
        noLib: false,
        outDir: "/outputs",
        rootDir: "/sources",
        skipLibCheck: false,
        sourceMap: true,
        strict: true,
        target: ts.ScriptTarget.ES2018,
        types: [],
    });
    return options;
};

const createProvisionalRhinoRevision2CompilerOptions = () => {
    const options = Object.freeze({
        allowJs: false,
        checkJs: false,
        ignoreDeprecations: "6.0",
        incremental: false,
        inlineSourceMap: false,
        inlineSources: false,
        jsx: ts.JsxEmit.React,
        jsxFactory: "__autojs6Tsx",
        jsxFragmentFactory: "__autojs6TsxFragment",
        module: ts.ModuleKind.CommonJS,
        moduleResolution: ts.ModuleResolutionKind.Node10,
        newLine: ts.NewLineKind.LineFeed,
        noEmitOnError: true,
        noLib: false,
        outDir: "/outputs",
        rootDir: "/sources",
        skipLibCheck: false,
        sourceMap: true,
        strict: true,
        target: ts.ScriptTarget.ES2018,
        types: [],
    });
    return options;
};

const createProvisionalNodeNextCompilerOptions = () => {
    const options = Object.freeze({
        allowJs: false,
        checkJs: false,
        ignoreDeprecations: "6.0",
        incremental: false,
        inlineSourceMap: false,
        inlineSources: false,
        module: ts.ModuleKind.NodeNext,
        moduleResolution: ts.ModuleResolutionKind.NodeNext,
        newLine: ts.NewLineKind.LineFeed,
        noEmitOnError: true,
        noLib: false,
        outDir: "/outputs",
        rootDir: "/sources",
        skipLibCheck: false,
        sourceMap: true,
        strict: true,
        target: ts.ScriptTarget.ES2018,
        types: [],
    });
    return options;
};

const compileProject = async (project, frameToken = null) => {
    const outputs = new Map();
    const outputBudget = { bytes: 0 };
    const sourceFileCache = new Map();
    const isControlledProject = matchesControlledTsConfigAuthority(project);
    const isRhinoRevisionTwoProject =
        matchesProvisionalRhinoRevision2ProfileAuthority(project) ||
        isControlledProject;
    const isNodeRevisionTwoProject =
        project.nodeRevisionTwoProgram === true &&
        matchesProvisionalNodeNextRevision2ProfileAuthority(project);
    let builder = null;
    let diagnosticCollector = null;
    let host = null;
    let program = null;
    let resolutionSession = null;
    let vfs = null;
    try {
        vfs = createReadonlyProjectVfs(project);
        const controlledEffectiveOptions = project.controlledEffectiveOptions;
        const ignoredConfigProperties =
            project.ignoredConfigProperties ?? Object.freeze([]);
        if (
            isControlledProject &&
            (
                controlledEffectiveOptions === null ||
                typeof controlledEffectiveOptions !== "object" ||
                !Object.isFrozen(controlledEffectiveOptions) ||
                !Object.isFrozen(controlledEffectiveOptions.compilerOptions) ||
                controlledEffectiveOptions.revision !==
                    controlledTsConfigEffectiveOptionsRevision ||
                typeof controlledEffectiveOptions.fingerprint !== "string" ||
                !sha256Pattern.test(controlledEffectiveOptions.fingerprint) ||
                JSON.stringify(controlledEffectiveOptions.compilerOptions) !==
                    controlledEffectiveOptions.canonicalJson ||
                Object.keys(controlledEffectiveOptions.compilerOptions).join("\n") !==
                    controlledTsConfigCanonicalOptionKeys.join("\n") ||
                !Array.isArray(ignoredConfigProperties) ||
                !Object.isFrozen(ignoredConfigProperties)
            )
        ) {
            throw new Error("Controlled effective option authority is malformed.");
        }
        if (!isControlledProject && ignoredConfigProperties.length !== 0) {
            throw new Error("Config-none project carried ignored config properties.");
        }
        const options = isControlledProject
            ? controlledEffectiveOptions.compilerOptions
            : matchesProvisionalNodeNextProfileAuthority(project) ||
                isNodeRevisionTwoProject
              ? createProvisionalNodeNextCompilerOptions()
              : isRhinoRevisionTwoProject
                ? createProvisionalRhinoRevision2CompilerOptions()
                : createProvisionalRhinoCompilerOptions();
        resolutionSession = isNodeRevisionTwoProject
            ? createNodeRevisionTwoResolutionSession(vfs, options)
            : isControlledProject
              ? createControlledNodeResolutionSession(vfs, options)
              : null;
        host = createProjectCompilerHost(
            vfs,
            outputs,
            outputBudget,
            sourceFileCache,
            project.execution,
            resolutionSession,
            isNodeRevisionTwoProject,
            project.targetProfile === provisionalRhinoProfileAuthority.targetProfile
                ? "/declarations/lib.es2018.d.ts"
                : "/declarations/lib.d.ts",
        );
        enterProjectExecutionPhase(
            project.execution,
            "parse",
            Math.max(1, vfs.sourcePaths.length),
        );
        builder = ts.createIncrementalProgram({
            host,
            options,
            rootNames: vfs.compilerRootNames,
        });
        program = builder.getProgram();
        if (
            typeof program.getRootFileNames === "function" &&
            JSON.stringify(program.getRootFileNames()) !==
                JSON.stringify(vfs.compilerRootNames)
        ) {
            throw new Error("TypeScript Program root authority drifted.");
        }
        if (isNodeRevisionTwoProject) {
            for (const sourceFile of program.getSourceFiles()) {
                if (!isNodeRevisionTwoResolutionPath(sourceFile.fileName)) continue;
                const expectedImpliedFormat =
                    resolutionSession.impliedModuleFormatFor(sourceFile.fileName) ===
                    "esm"
                        ? ts.ModuleKind.ESNext
                        : ts.ModuleKind.CommonJS;
                if (sourceFile.impliedNodeFormat !== expectedImpliedFormat) {
                    throw new Error(
                        "TypeScript Program source implied module format drifted.",
                    );
                }
            }
        }
        diagnosticCollector = createDiagnosticCollector();
        if (isControlledProject) {
            diagnosticCollector.consume(
                "options",
                createControlledTsConfigWarningDiagnostics(
                    ignoredConfigProperties,
                ),
            );
        }
        if (resolutionSession !== null) {
            diagnosticCollector.consume(
                "resolution",
                resolutionSession.diagnostics(),
            );
        }
        const hasResolutionFailures =
            resolutionSession?.hasFailures() === true;
        diagnosticCollector.consume("options", program.getOptionsDiagnostics());
        for (const sourceFile of program.getSourceFiles()) {
            diagnosticCollector.consume(
                "syntactic",
                program.getSyntacticDiagnostics(sourceFile),
            );
        }
        diagnosticCollector.consume("global", program.getGlobalDiagnostics());
        completeProjectExecutionPhase(project.execution, "parse");
        enterProjectExecutionPhase(
            project.execution,
            "semantic",
            Math.max(1, vfs.sourcePaths.length),
        );
        for (const sourceFile of program.getSourceFiles()) {
            if (!hasResolutionFailures) {
                diagnosticCollector.consume(
                    "semantic",
                    program.getSemanticDiagnostics(sourceFile),
                );
            }
            if (
                isNodeRevisionTwoProject
                    ? isNodeRevisionTwoExecutableSourcePath(sourceFile.fileName)
                    : isExecutableProjectSourcePath(sourceFile.fileName)
            ) {
                advanceProjectExecutionProgress(
                    project.execution,
                    "semantic",
                );
            }
        }
        completeProjectExecutionPhase(project.execution, "semantic");
        enterProjectExecutionPhase(
            project.execution,
            "emit",
            Math.max(1, vfs.sourcePaths.length * 2),
        );
        let emitResult;
        if (diagnosticCollector.errorCount() > 0) {
            emitResult = {
                diagnostics: [],
                emitSkipped: true,
            };
        } else {
            emitResult = builder.emit();
            diagnosticCollector.consume(
                "emit",
                emitResult.diagnostics || [],
            );
        }
        const diagnostics = diagnosticCollector.diagnostics;
        const errorCount = diagnosticCollector.errorCount();
        const reachableExecutableSourcePaths = program
            .getSourceFiles()
            .map((sourceFile) => sourceFile.fileName)
            .filter(
                isNodeRevisionTwoProject
                    ? isNodeRevisionTwoExecutableSourcePath
                    : isExecutableProjectSourcePath,
            )
            .sort(compareCodeUnits);
        if (
            resolutionSession !== null &&
            program.getSourceFiles().some(
                (sourceFile) =>
                    sourceFile.fileName.startsWith("/packages/") &&
                    !resolutionSession.isGrantedPackageTarget(
                        sourceFile.fileName,
                    ),
            )
        ) {
            throw new Error(
                "TypeScript Program escaped the controlled resolver grants.",
            );
        }
        if (
            !compareStrictlySortedUnique(reachableExecutableSourcePaths) ||
            reachableExecutableSourcePaths.some(
                (sourcePath) => !vfs.sourcePaths.includes(sourcePath),
            )
        ) {
            throw new Error(
                "TypeScript Program escaped the controlled source inventory.",
            );
        }
        if ((errorCount > 0 || emitResult.emitSkipped) && outputs.size !== 0) {
            throw new Error("noEmitOnError produced executable output.");
        }
        if (errorCount === 0 && emitResult.emitSkipped) {
            throw new Error("TypeScript skipped an error-free project emit.");
        }

        const serializedOutputs = [];
        const sortedOutputs = Array.from(outputs.entries()).sort(
            ([left], [right]) => compareCodeUnits(left, right),
        );
        // Bound WebCrypto concurrency and retain deterministic output order.
        // A project may legitimately reach the 512-output Worker fence; issuing
        // every digest at once creates an avoidable resource spike.
        for (const [path, output] of sortedOutputs) {
            const outputSha256 = await sha256(output.text);
            if (frameToken !== null) {
                requireActiveProjectInputFrame(frameToken);
            }
            serializedOutputs.push({
                path,
                sha256: outputSha256,
                sourcePaths: output.sourcePaths,
                text: output.text,
            });
        }
        const executableOutputs = serializedOutputs.filter(
            (output) =>
                output.path.endsWith(".js") ||
                output.path.endsWith(".cjs") ||
                output.path.endsWith(".mjs"),
        );
        const sourceMaps = serializedOutputs.filter((output) =>
            output.path.endsWith(".map"),
        );
        if (
            errorCount === 0 &&
            (
                executableOutputs.length !==
                    reachableExecutableSourcePaths.length ||
                sourceMaps.length !== reachableExecutableSourcePaths.length
            )
        ) {
            throw new Error(
                "Project emit did not produce one module and map per source.",
            );
        }
        for (const executableOutput of executableOutputs) {
            const matchingSourceMap = sourceMaps.find(
                (sourceMap) =>
                    sourceMap.path === `${executableOutput.path}.map`,
            );
            const onlySource = executableOutput.sourcePaths[0];
            const expectedOutputPath = expectedGeneratedModulePath(
                onlySource,
                isNodeRevisionTwoProject,
            );
            if (
                executableOutput.sourcePaths.length !== 1 ||
                !reachableExecutableSourcePaths.includes(onlySource) ||
                executableOutput.path !== expectedOutputPath ||
                !matchingSourceMap ||
                matchingSourceMap.sourcePaths.length !== 1 ||
                matchingSourceMap.sourcePaths[0] !== onlySource
            ) {
                throw new Error(
                    "Project output does not map one-to-one to its source.",
                );
            }
        }
        for (const sourceMapOutput of sourceMaps) {
            const sourceMap = JSON.parse(sourceMapOutput.text);
            if (
                sourceMap === null ||
                typeof sourceMap !== "object" ||
                sourceMap.version !== 3 ||
                sourceMap.file !==
                    sourceMapOutput.path.slice(
                        sourceMapOutput.path.lastIndexOf("/") + 1,
                        -".map".length,
                    ) ||
                sourceMap.sourceRoot !== "" ||
                !Array.isArray(sourceMap.sources) ||
                typeof sourceMap.mappings !== "string" ||
                Object.prototype.hasOwnProperty.call(
                    sourceMap,
                    "sourcesContent",
                )
            ) {
                throw new Error(
                    "Project emit produced an invalid Source Map v3.",
                );
            }
            const resolvedSources = sourceMap.sources
                .map((source) =>
                    resolveSourceMapPath(
                        sourceMapOutput.path,
                        sourceMap.sourceRoot,
                        source,
                        isNodeRevisionTwoProject,
                    ),
                )
                .sort();
            if (
                JSON.stringify(resolvedSources) !==
                JSON.stringify(sourceMapOutput.sourcePaths.slice().sort())
            ) {
                throw new Error(
                    "Project Source Map does not match its source inventory.",
                );
            }
        }

        const result = {
            compilerApi: "createIncrementalProgram",
            configKind: project.configKind,
            configSchemaRevision: project.configSchemaRevision,
            diagnostics,
            emitSkipped: emitResult.emitSkipped,
            options: isControlledProject
                ? JSON.parse(controlledEffectiveOptions.canonicalJson)
                : {
                      allowJs: options.allowJs,
                      checkJs: options.checkJs,
                      ignoreDeprecations: options.ignoreDeprecations,
                      incremental: options.incremental,
                      inlineSourceMap: options.inlineSourceMap,
                      inlineSources: options.inlineSources,
                      ...(isRhinoRevisionTwoProject
                          ? {
                                jsx: options.jsx,
                                jsxFactory: options.jsxFactory,
                                jsxFragmentFactory: options.jsxFragmentFactory,
                            }
                          : {}),
                      module: options.module,
                      moduleResolution: options.moduleResolution,
                      newLine: options.newLine,
                      noEmitOnError: options.noEmitOnError,
                      noLib: options.noLib,
                      outDir: options.outDir,
                      rootDir: options.rootDir,
                      skipLibCheck: options.skipLibCheck,
                      sourceMap: options.sourceMap,
                      strict: options.strict,
                      target: options.target,
                      types: options.types.slice(),
                  },
            outputs: serializedOutputs,
            profileRevision: project.profileRevision,
            profileSchemaRevision: project.profileSchemaRevision,
            targetProfile: project.targetProfile,
            tsVersion: ts.version,
        };
        if (isControlledProject) {
            result.effectiveOptionsFingerprint =
                controlledEffectiveOptions.fingerprint;
            result.effectiveOptionsRevision = controlledEffectiveOptions.revision;
            result.rootNames = vfs.rootNames.slice();
        }
        return result;
    } finally {
        program = null;
        builder = null;
        host = null;
        resolutionSession?.release();
        resolutionSession = null;
        diagnosticCollector?.release();
        diagnosticCollector = null;
        sourceFileCache.clear();
        outputs.clear();
        if (vfs !== null) {
            vfs.release();
            vfs = null;
        } else {
            project.directoryIndex?.release();
        }
        releaseRetainedPackageTargetTexts(project.packageTargetTextHolders);
        if (Array.isArray(project.packageTargetFiles)) {
            project.packageTargetFiles.length = 0;
        }
        if (
            Object.prototype.hasOwnProperty.call(
                project,
                "packageMetadataCatalog",
            )
        ) {
            project.packageMetadataCatalog = null;
        }
        if (
            Object.prototype.hasOwnProperty.call(
                project,
                "nodePackageFormatCatalog",
            )
        ) {
            project.nodePackageFormatCatalog = null;
        }
        if (
            Object.prototype.hasOwnProperty.call(
                project,
                "nodeResolutionAuthority",
            )
        ) {
            project.nodeResolutionAuthority = null;
        }
        project.packageTargetAuthority = null;
        if (
            Object.prototype.hasOwnProperty.call(
                project,
                "packageTargetTokenCount",
            )
        ) {
            project.packageTargetTokenCount = 0;
        }
        if (Array.isArray(project.packageTargetTokenCounts)) {
            project.packageTargetTokenCounts.length = 0;
        }
        for (const record of project.files) {
            record.text = "";
        }
        project.files.length = 0;
        project.rootNames.length = 0;
        project.directoryIndex = null;
    }
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

const isRecord = (value) =>
    value !== null && typeof value === "object" && !Array.isArray(value);

const isRequestId = (value) =>
    typeof value === "string" && value.length > 0 && value.length <= 128;

const boundedWorkerError = (error) =>
    (
        error instanceof Error
            ? `${error.message}\n${error.stack || ""}`
            : String(error)
    ).slice(0, 4096);

const decodeCanonicalBase64 = (encoded) => {
    if (
        typeof encoded !== "string" ||
        encoded.length === 0 ||
        encoded.length % 4 !== 0 ||
        !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(
            encoded,
        )
    ) {
        throw new Error("Project frame contains non-canonical base64.");
    }
    let binary;
    try {
        binary = atob(encoded);
    } catch (_) {
        throw new Error("Project frame contains invalid base64.");
    }
    if (btoa(binary) !== encoded) {
        throw new Error("Project frame contains non-canonical base64.");
    }
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) {
        bytes[index] = binary.charCodeAt(index);
    }
    return bytes;
};

const encodeBase64 = (bytes) => {
    let binary = "";
    for (let index = 0; index < bytes.length; index += 1) {
        binary += String.fromCharCode(bytes[index]);
    }
    return btoa(binary);
};

class ControlledTsConfigPolicyError extends Error {
    constructor(policyCode, message) {
        super(message);
        this.name = "ControlledTsConfigPolicyError";
        this.policyCode = policyCode;
    }
}

const rejectControlledTsConfig = (policyCode, message) => {
    throw new ControlledTsConfigPolicyError(policyCode, message);
};

// This scanner is deliberately independent from TypeScript's permissive
// config helpers. It is the acceptance gate for duplicate keys, strict JSON
// tokens, Unicode scalar values and the frozen C1 complexity accounting.
const parseControlledJsonc = (
    source,
    {
        countRootNode = true,
        maximumJsonDepth = controlledTsConfigAuthority.maximumJsonDepth,
        maximumJsonNodes = controlledTsConfigAuthority.maximumJsonNodes,
        rejectEmbeddedBom = true,
        requireFiniteNumbers = true,
    } = {},
) => {
    let cursor = source.charCodeAt(0) === 0xfeff ? 1 : 0;
    let nodeCount = 0;

    if (rejectEmbeddedBom && source.indexOf("\ufeff", cursor) !== -1) {
        rejectControlledTsConfig(
            controlledTsConfigPolicyCodes.encodingInvalid,
            "Controlled tsconfig contains a misplaced UTF-8 BOM.",
        );
    }

    const syntaxFailure = (message = "Controlled tsconfig is not valid JSONC.") =>
        rejectControlledTsConfig(
            controlledTsConfigPolicyCodes.syntaxInvalid,
            message,
        );

    const complexityFailure = () =>
        rejectControlledTsConfig(
            controlledTsConfigPolicyCodes.complexityExceeded,
            "Controlled tsconfig exceeds the JSON complexity limit.",
        );

    const isWhitespace = (character) =>
        character === " " ||
        character === "\t" ||
        character === "\n" ||
        character === "\r";

    const skipTrivia = () => {
        while (cursor < source.length) {
            if (isWhitespace(source[cursor])) {
                cursor += 1;
                continue;
            }
            if (source[cursor] !== "/") {
                return;
            }
            if (source[cursor + 1] === "/") {
                cursor += 2;
                while (
                    cursor < source.length &&
                    source[cursor] !== "\n" &&
                    source[cursor] !== "\r"
                ) {
                    cursor += 1;
                }
                continue;
            }
            if (source[cursor + 1] === "*") {
                const end = source.indexOf("*/", cursor + 2);
                if (end === -1) {
                    syntaxFailure("Controlled tsconfig has an unterminated comment.");
                }
                cursor = end + 2;
                continue;
            }
            return;
        }
    };

    const countNode = (depth) => {
        if (depth > maximumJsonDepth) {
            complexityFailure();
        }
        nodeCount += 1;
        if (nodeCount > maximumJsonNodes) {
            complexityFailure();
        }
    };

    const parseHexCodeUnit = () => {
        const digits = source.slice(cursor, cursor + 4);
        if (digits.length !== 4 || !/^[0-9a-fA-F]{4}$/.test(digits)) {
            syntaxFailure("Controlled tsconfig has an invalid Unicode escape.");
        }
        cursor += 4;
        return Number.parseInt(digits, 16);
    };

    const appendScalarCodeUnit = (codeUnit, output) => {
        if (codeUnit >= 0xd800 && codeUnit <= 0xdbff) {
            if (
                source[cursor] !== "\\" ||
                source[cursor + 1] !== "u"
            ) {
                rejectControlledTsConfig(
                    controlledTsConfigPolicyCodes.encodingInvalid,
                    "Controlled tsconfig contains a lone Unicode surrogate.",
                );
            }
            cursor += 2;
            const low = parseHexCodeUnit();
            if (low < 0xdc00 || low > 0xdfff) {
                rejectControlledTsConfig(
                    controlledTsConfigPolicyCodes.encodingInvalid,
                    "Controlled tsconfig contains a lone Unicode surrogate.",
                );
            }
            return output + String.fromCharCode(codeUnit, low);
        }
        if (codeUnit >= 0xdc00 && codeUnit <= 0xdfff) {
            rejectControlledTsConfig(
                controlledTsConfigPolicyCodes.encodingInvalid,
                "Controlled tsconfig contains a lone Unicode surrogate.",
            );
        }
        return output + String.fromCharCode(codeUnit);
    };

    const parseString = () => {
        if (source[cursor] !== '"') {
            syntaxFailure("Controlled tsconfig object keys must be quoted strings.");
        }
        cursor += 1;
        let output = "";
        while (cursor < source.length) {
            const character = source[cursor];
            cursor += 1;
            if (character === '"') {
                return output;
            }
            const codeUnit = character.charCodeAt(0);
            if (codeUnit < 0x20) {
                syntaxFailure("Controlled tsconfig contains an unescaped control character.");
            }
            if (character === "\\") {
                if (cursor >= source.length) {
                    syntaxFailure();
                }
                const escape = source[cursor];
                cursor += 1;
                switch (escape) {
                    case '"':
                    case "\\":
                    case "/":
                        output += escape;
                        break;
                    case "b":
                        output += "\b";
                        break;
                    case "f":
                        output += "\f";
                        break;
                    case "n":
                        output += "\n";
                        break;
                    case "r":
                        output += "\r";
                        break;
                    case "t":
                        output += "\t";
                        break;
                    case "u":
                        output = appendScalarCodeUnit(
                            parseHexCodeUnit(),
                            output,
                        );
                        break;
                    default:
                        syntaxFailure("Controlled tsconfig contains an invalid string escape.");
                }
                continue;
            }
            if (codeUnit >= 0xd800 && codeUnit <= 0xdbff) {
                if (cursor >= source.length) {
                    rejectControlledTsConfig(
                        controlledTsConfigPolicyCodes.encodingInvalid,
                        "Controlled tsconfig contains a lone Unicode surrogate.",
                    );
                }
                const low = source.charCodeAt(cursor);
                if (low < 0xdc00 || low > 0xdfff) {
                    rejectControlledTsConfig(
                        controlledTsConfigPolicyCodes.encodingInvalid,
                        "Controlled tsconfig contains a lone Unicode surrogate.",
                    );
                }
                output += character + source[cursor];
                cursor += 1;
            } else if (codeUnit >= 0xdc00 && codeUnit <= 0xdfff) {
                rejectControlledTsConfig(
                    controlledTsConfigPolicyCodes.encodingInvalid,
                    "Controlled tsconfig contains a lone Unicode surrogate.",
                );
            } else {
                output += character;
            }
        }
        syntaxFailure("Controlled tsconfig has an unterminated string.");
    };

    const parseNumber = () => {
        const start = cursor;
        if (source[cursor] === "-") cursor += 1;
        if (source[cursor] === "0") {
            cursor += 1;
        } else if (source[cursor] >= "1" && source[cursor] <= "9") {
            cursor += 1;
            while (source[cursor] >= "0" && source[cursor] <= "9") {
                cursor += 1;
            }
        } else {
            syntaxFailure("Controlled tsconfig contains an invalid number.");
        }
        if (source[cursor] === ".") {
            cursor += 1;
            if (!(source[cursor] >= "0" && source[cursor] <= "9")) {
                syntaxFailure("Controlled tsconfig contains an invalid number.");
            }
            while (source[cursor] >= "0" && source[cursor] <= "9") {
                cursor += 1;
            }
        }
        if (source[cursor] === "e" || source[cursor] === "E") {
            cursor += 1;
            if (source[cursor] === "+" || source[cursor] === "-") {
                cursor += 1;
            }
            if (!(source[cursor] >= "0" && source[cursor] <= "9")) {
                syntaxFailure("Controlled tsconfig contains an invalid number.");
            }
            while (source[cursor] >= "0" && source[cursor] <= "9") {
                cursor += 1;
            }
        }
        const value = Number(source.slice(start, cursor));
        if (requireFiniteNumbers && !Number.isFinite(value)) {
            syntaxFailure("Controlled tsconfig contains a non-finite number.");
        }
        return value;
    };

    const parseValue = (depth) => {
        skipTrivia();
        if (cursor >= source.length) {
            syntaxFailure("Controlled tsconfig is empty or has a missing value.");
        }
        if (countRootNode || depth !== 1) {
            countNode(depth);
        } else if (depth > maximumJsonDepth) {
            complexityFailure();
        }
        const character = source[cursor];
        if (character === '"') {
            return parseString();
        }
        if (character === "{") {
            cursor += 1;
            const result = Object.create(null);
            const keys = new Set();
            skipTrivia();
            if (source[cursor] === "}") {
                cursor += 1;
                return result;
            }
            while (cursor < source.length) {
                skipTrivia();
                const key = parseString();
                if (keys.has(key)) {
                    rejectControlledTsConfig(
                        controlledTsConfigPolicyCodes.duplicateKey,
                        "Controlled tsconfig contains a duplicate object key.",
                    );
                }
                keys.add(key);
                skipTrivia();
                if (source[cursor] !== ":") {
                    syntaxFailure("Controlled tsconfig object property is missing a colon.");
                }
                cursor += 1;
                result[key] = parseValue(depth + 1);
                skipTrivia();
                if (source[cursor] === "}") {
                    cursor += 1;
                    return result;
                }
                if (source[cursor] !== ",") {
                    syntaxFailure("Controlled tsconfig object property is missing a comma.");
                }
                cursor += 1;
                skipTrivia();
                if (source[cursor] === "}") {
                    cursor += 1;
                    return result;
                }
            }
            syntaxFailure("Controlled tsconfig has an unterminated object.");
        }
        if (character === "[") {
            cursor += 1;
            const result = [];
            skipTrivia();
            if (source[cursor] === "]") {
                cursor += 1;
                return result;
            }
            while (cursor < source.length) {
                result.push(parseValue(depth + 1));
                skipTrivia();
                if (source[cursor] === "]") {
                    cursor += 1;
                    return result;
                }
                if (source[cursor] !== ",") {
                    syntaxFailure("Controlled tsconfig array item is missing a comma.");
                }
                cursor += 1;
                skipTrivia();
                if (source[cursor] === "]") {
                    cursor += 1;
                    return result;
                }
            }
            syntaxFailure("Controlled tsconfig has an unterminated array.");
        }
        if (source.startsWith("true", cursor)) {
            cursor += 4;
            return true;
        }
        if (source.startsWith("false", cursor)) {
            cursor += 5;
            return false;
        }
        if (source.startsWith("null", cursor)) {
            cursor += 4;
            return null;
        }
        if (character === "-" || (character >= "0" && character <= "9")) {
            return parseNumber();
        }
        syntaxFailure();
    };

    const value = parseValue(1);
    skipTrivia();
    if (cursor !== source.length) {
        syntaxFailure("Controlled tsconfig contains trailing tokens.");
    }
    return value;
};

const validateControlledTsConfigSelector = (selector, isFilesSelector) => {
    if (typeof selector !== "string") {
        rejectControlledTsConfig(
            controlledTsConfigPolicyCodes.invalidValue,
            "Controlled tsconfig selectors must be strings.",
        );
    }
    const normalized = selector.normalize("NFC");
    const encoder = new TextEncoder();
    if (
        Math.max(
            encoder.encode(selector).byteLength,
            encoder.encode(normalized).byteLength,
        ) > controlledTsConfigAuthority.maximumSelectorBytes
    ) {
        rejectControlledTsConfig(
            controlledTsConfigPolicyCodes.pathInvalid,
            "Controlled tsconfig selector exceeds the path limit.",
        );
    }
    if (
        normalized.length === 0 ||
        normalized.startsWith("/") ||
        normalized.includes("\\") ||
        normalized.includes("\u0000") ||
        /^[A-Za-z][A-Za-z0-9+.-]*:/.test(normalized) ||
        normalized.split("/").some(
            (segment) =>
                segment.length === 0 || segment === "." || segment === "..",
        ) ||
        (isFilesSelector && /[*?]/.test(normalized))
    ) {
        rejectControlledTsConfig(
            controlledTsConfigPolicyCodes.pathInvalid,
            "Controlled tsconfig contains an invalid project selector.",
        );
    }
    return normalized;
};

const parseControlledTsConfig = (source) => {
    const value = parseControlledJsonc(source);
    const parsedByTypeScript = ts.parseJsonText(
        controlledTsConfigAuthority.configPath,
        source,
    );
    if ((parsedByTypeScript.parseDiagnostics || []).length !== 0) {
        rejectControlledTsConfig(
            controlledTsConfigPolicyCodes.syntaxInvalid,
            "TypeScript rejected the controlled JSONC document.",
        );
    }
    if (!isRecord(value)) {
        rejectControlledTsConfig(
            controlledTsConfigPolicyCodes.invalidValue,
            "Controlled tsconfig root must be an object.",
        );
    }

    const ignoredProperties = [];
    for (const key of Object.keys(value).sort(compareCodeUnits)) {
        if (controlledTsConfigAllowedTopLevel.has(key)) {
            continue;
        }
        if (controlledTsConfigUnsupportedTopLevel.has(key)) {
            rejectControlledTsConfig(
                controlledTsConfigPolicyCodes.unsupportedFeature,
                "Controlled tsconfig uses an unsupported top-level feature.",
            );
        }
        ignoredProperties.push(Object.freeze({
            kind: "top-level",
            name: key,
        }));
    }

    if (
        Object.prototype.hasOwnProperty.call(value, "$schema") &&
        typeof value.$schema !== "string"
    ) {
        rejectControlledTsConfig(
            controlledTsConfigPolicyCodes.invalidValue,
            "Controlled tsconfig $schema must be a string.",
        );
    }

    const explicitBooleanOptions = Object.create(null);
    if (Object.prototype.hasOwnProperty.call(value, "compilerOptions")) {
        if (!isRecord(value.compilerOptions)) {
            rejectControlledTsConfig(
                controlledTsConfigPolicyCodes.invalidValue,
                "Controlled tsconfig compilerOptions must be an object.",
            );
        }
        for (
            const name of Object.keys(value.compilerOptions).sort(compareCodeUnits)
        ) {
            if (controlledTsConfigAllowedBooleanOptions.has(name)) {
                if (typeof value.compilerOptions[name] !== "boolean") {
                    rejectControlledTsConfig(
                        controlledTsConfigPolicyCodes.invalidValue,
                        "Controlled compiler option must be a boolean.",
                    );
                }
                explicitBooleanOptions[name] = value.compilerOptions[name];
                continue;
            }
            if (controlledTsConfigProfileManagedOptions.has(name)) {
                rejectControlledTsConfig(
                    controlledTsConfigPolicyCodes.profileConflict,
                    "Controlled tsconfig attempts to override a profile-managed option.",
                );
            }
            if (controlledTsConfigEscapeForbiddenOptions.has(name)) {
                rejectControlledTsConfig(
                    controlledTsConfigPolicyCodes.unsupportedFeature,
                    "Controlled tsconfig attempts to enable a host or resolution escape.",
                );
            }
            if (controlledTsConfigOutputForbiddenOptions.has(name)) {
                rejectControlledTsConfig(
                    controlledTsConfigPolicyCodes.unsupportedFeature,
                    "Controlled tsconfig attempts to change the frozen output topology.",
                );
            }
            if (controlledTsConfigCustomTransformerOptions.has(name)) {
                rejectControlledTsConfig(
                    controlledTsConfigPolicyCodes.unknownProperty,
                    "Controlled tsconfig attempts to install a custom transformer.",
                );
            }
            ignoredProperties.push(Object.freeze({
                kind: "compiler-option",
                known: controlledTsConfigKnownOptions.has(name),
                name,
            }));
        }
    }

    const selectorKeys = ["files", "include", "exclude"];
    let selectorCount = 0;
    for (const key of selectorKeys) {
        if (!Object.prototype.hasOwnProperty.call(value, key)) {
            continue;
        }
        if (!Array.isArray(value[key])) {
            rejectControlledTsConfig(
                controlledTsConfigPolicyCodes.invalidValue,
                "Controlled tsconfig selector fields must be arrays.",
            );
        }
        selectorCount += value[key].length;
    }
    if (selectorCount > controlledTsConfigAuthority.maximumSelectors) {
        rejectControlledTsConfig(
            controlledTsConfigPolicyCodes.complexityExceeded,
            "Controlled tsconfig exceeds the selector count limit.",
        );
    }

    const selectors = Object.create(null);
    for (const key of selectorKeys) {
        if (!Object.prototype.hasOwnProperty.call(value, key)) {
            selectors[key] = null;
            continue;
        }
        const normalized = [];
        const seen = new Set();
        for (const selector of value[key]) {
            const accepted = validateControlledTsConfigSelector(
                selector,
                key === "files",
            );
            if (!seen.has(accepted)) {
                seen.add(accepted);
                normalized.push(accepted);
            }
        }
        selectors[key] = Object.freeze(normalized);
    }

    return Object.freeze({
        configKind: controlledTsConfigAuthority.configKind,
        configPolicyFingerprint:
            controlledTsConfigAuthority.configPolicyFingerprint,
        configPolicyRevision:
            controlledTsConfigAuthority.configPolicyRevision,
        configSchemaRevision:
            controlledTsConfigAuthority.configSchemaRevision,
        explicitBooleanOptions: Object.freeze(explicitBooleanOptions),
        ignoredProperties: Object.freeze(ignoredProperties),
        presentTopLevelKeys: Object.freeze(
            Object.keys(value).sort(compareCodeUnits),
        ),
        selectors: Object.freeze(selectors),
    });
};

const validateControlledPackageMetadataJson = (source) => {
    try {
        JSON.parse(source);
    } catch (_) {
        throw new Error(
            `${controlledPackageMetadataPolicyCodes.jsonInvalid}: ` +
                "Package metadata is not strict JSON.",
        );
    }
    let value;
    try {
        value = parseControlledJsonc(source, {
            countRootNode: false,
            maximumJsonDepth:
                controlledPackageMetadataAuthority.maximumJsonDepth,
            maximumJsonNodes:
                controlledPackageMetadataAuthority.maximumJsonNodes,
            rejectEmbeddedBom: false,
            requireFiniteNumbers: false,
        });
    } catch (error) {
        const policyCode =
            error instanceof ControlledTsConfigPolicyError
                ? error.policyCode
                : null;
        const packageCode =
            policyCode === controlledTsConfigPolicyCodes.duplicateKey
                ? controlledPackageMetadataPolicyCodes.duplicateKey
                : policyCode === controlledTsConfigPolicyCodes.complexityExceeded
                  ? controlledPackageMetadataPolicyCodes.complexityExceeded
                  : policyCode === controlledTsConfigPolicyCodes.encodingInvalid
                    ? controlledPackageMetadataPolicyCodes.encodingInvalid
                    : controlledPackageMetadataPolicyCodes.jsonInvalid;
        throw new Error(`${packageCode}: Package metadata was rejected.`);
    }
    if (!isRecord(value)) {
        throw new Error(
            `${controlledPackageMetadataPolicyCodes.jsonInvalid}: ` +
                "Package metadata root must be an object.",
        );
    }
    if (Object.prototype.hasOwnProperty.call(value, "scripts")) {
        throw new Error(
            `${controlledPackageMetadataPolicyCodes.lifecycleForbidden}: ` +
                "Package lifecycle scripts are forbidden.",
        );
    }
    return deepFreezeControlledNodeJson(value);
};

const resolveControlledTsConfigEffectiveOptions = async (parsedConfig) => {
    const explicit = parsedConfig.explicitBooleanOptions;
    const explicitNames = Object.keys(explicit);
    const allowedNames = Array.from(
        controlledTsConfigAllowedBooleanOptions,
    ).sort(compareCodeUnits);
    if (
        !Object.isFrozen(parsedConfig) ||
        !Object.isFrozen(explicit) ||
        parsedConfig.configKind !== controlledTsConfigAuthority.configKind ||
        parsedConfig.configSchemaRevision !==
            controlledTsConfigAuthority.configSchemaRevision ||
        parsedConfig.configPolicyRevision !==
            controlledTsConfigAuthority.configPolicyRevision ||
        parsedConfig.configPolicyFingerprint !==
            controlledTsConfigAuthority.configPolicyFingerprint ||
        explicitNames.some(
            (name) =>
                !controlledTsConfigAllowedBooleanOptions.has(name) ||
                typeof explicit[name] !== "boolean",
        ) ||
        allowedNames.length !== 18 ||
        controlledTsConfigStrictDependentOptions.size !== 8 ||
        controlledTsConfigFalseDefaultOptions.size !== 9 ||
        allowedNames.some(
            (name) =>
                name !== "strict" &&
                !controlledTsConfigStrictDependentOptions.has(name) &&
                !controlledTsConfigFalseDefaultOptions.has(name),
        )
    ) {
        throw new Error("Controlled tsconfig effective-option policy drifted.");
    }

    const hasExplicit = (name) =>
        Object.prototype.hasOwnProperty.call(explicit, name);
    const strict = hasExplicit("strict") ? explicit.strict : true;
    const strictCarrier = Object.freeze({ strict });
    const resolved = Object.create(null);
    for (const name of allowedNames) {
        if (hasExplicit(name)) {
            resolved[name] = explicit[name];
        } else if (name === "strict") {
            resolved[name] = strict;
        } else if (controlledTsConfigStrictDependentOptions.has(name)) {
            resolved[name] = ts.getStrictOptionValue(strictCarrier, name);
        } else if (controlledTsConfigFalseDefaultOptions.has(name)) {
            resolved[name] = false;
        } else {
            throw new Error("Controlled tsconfig option has no frozen default.");
        }
    }

    // These are the two TS5052 option relationships exposed by the C1
    // boolean subset. They remain config-policy failures and therefore must
    // be classified before the config-end ACK.
    if (
        !resolved.strictNullChecks &&
        resolved.exactOptionalPropertyTypes
    ) {
        rejectControlledTsConfig(
            controlledTsConfigPolicyCodes.invalidValue,
            "Controlled tsconfig exactOptionalPropertyTypes requires strictNullChecks.",
        );
    }
    if (
        !resolved.strictNullChecks &&
        resolved.strictPropertyInitialization
    ) {
        rejectControlledTsConfig(
            controlledTsConfigPolicyCodes.invalidValue,
            "Controlled tsconfig strictPropertyInitialization requires strictNullChecks.",
        );
    }

    const frozenResolved = Object.freeze(resolved);
    const frozenTypes = Object.freeze([]);
    // Keep this literal in canonical code-unit key order. JSON.stringify is
    // deliberately the one compact canonical encoder because every value is
    // a frozen boolean, pinned integer, pinned ASCII string, or frozen [].
    const compilerOptions = Object.freeze({
        allowJs: false,
        allowSyntheticDefaultImports:
            controlledTsConfigExplicitProfileDefaults
                .allowSyntheticDefaultImports,
        alwaysStrict:
            controlledTsConfigExplicitProfileDefaults.alwaysStrict,
        checkJs: false,
        esModuleInterop:
            controlledTsConfigExplicitProfileDefaults.esModuleInterop,
        exactOptionalPropertyTypes:
            frozenResolved.exactOptionalPropertyTypes,
        forceConsistentCasingInFileNames:
            controlledTsConfigExplicitProfileDefaults
                .forceConsistentCasingInFileNames,
        ignoreDeprecations: "6.0",
        incremental: false,
        inlineSourceMap: false,
        inlineSources: false,
        jsx: ts.JsxEmit.React,
        jsxFactory: "__autojs6Tsx",
        jsxFragmentFactory: "__autojs6TsxFragment",
        module: ts.ModuleKind.CommonJS,
        moduleDetection:
            controlledTsConfigExplicitProfileDefaults.moduleDetection,
        moduleResolution: ts.ModuleResolutionKind.Node10,
        newLine: ts.NewLineKind.LineFeed,
        noEmitOnError: true,
        noFallthroughCasesInSwitch:
            frozenResolved.noFallthroughCasesInSwitch,
        noImplicitAny: frozenResolved.noImplicitAny,
        noImplicitOverride: frozenResolved.noImplicitOverride,
        noImplicitReturns: frozenResolved.noImplicitReturns,
        noImplicitThis: frozenResolved.noImplicitThis,
        noLib: false,
        noPropertyAccessFromIndexSignature:
            frozenResolved.noPropertyAccessFromIndexSignature,
        noUncheckedIndexedAccess: frozenResolved.noUncheckedIndexedAccess,
        noUnusedLocals: frozenResolved.noUnusedLocals,
        noUnusedParameters: frozenResolved.noUnusedParameters,
        outDir: "/outputs",
        resolveJsonModule:
            controlledTsConfigExplicitProfileDefaults.resolveJsonModule,
        rootDir: "/sources",
        skipLibCheck: frozenResolved.skipLibCheck,
        sourceMap: true,
        strict: frozenResolved.strict,
        strictBindCallApply: frozenResolved.strictBindCallApply,
        strictBuiltinIteratorReturn:
            frozenResolved.strictBuiltinIteratorReturn,
        strictFunctionTypes: frozenResolved.strictFunctionTypes,
        strictNullChecks: frozenResolved.strictNullChecks,
        strictPropertyInitialization:
            frozenResolved.strictPropertyInitialization,
        target: ts.ScriptTarget.ES2018,
        types: frozenTypes,
        useDefineForClassFields:
            controlledTsConfigExplicitProfileDefaults
                .useDefineForClassFields,
        useUnknownInCatchVariables:
            frozenResolved.useUnknownInCatchVariables,
    });
    if (
        Object.keys(compilerOptions).join("\n") !==
        controlledTsConfigCanonicalOptionKeys.join("\n") ||
        controlledTsConfigStrictDependentOptions.size !==
            Array.from(controlledTsConfigStrictDependentOptions).filter(
                (name) =>
                    ts.getStrictOptionValue(compilerOptions, name) ===
                    compilerOptions[name],
            ).length ||
        ts.getAllowSyntheticDefaultImports(compilerOptions) !==
            compilerOptions.allowSyntheticDefaultImports ||
        ts.getAlwaysStrict(compilerOptions) !== compilerOptions.alwaysStrict ||
        ts.getESModuleInterop(compilerOptions) !==
            compilerOptions.esModuleInterop ||
        ts.getEmitModuleDetectionKind(compilerOptions) !==
            compilerOptions.moduleDetection ||
        ts.getResolveJsonModule(compilerOptions) !==
            compilerOptions.resolveJsonModule ||
        ts.getUseDefineForClassFields(compilerOptions) !==
            compilerOptions.useDefineForClassFields ||
        (compilerOptions.forceConsistentCasingInFileNames !== false) !==
            compilerOptions.forceConsistentCasingInFileNames
    ) {
        throw new Error("Controlled tsconfig TypeScript defaults drifted.");
    }

    const canonicalJson = JSON.stringify(compilerOptions);
    if (
        canonicalJson.length === 0 ||
        Array.from(canonicalJson).some(
            (character) =>
                character.charCodeAt(0) < 0x20 ||
                character.charCodeAt(0) > 0x7e,
        )
    ) {
        throw new Error("Controlled tsconfig canonical options are invalid.");
    }
    const fingerprint = await sha256(canonicalJson);
    return Object.freeze({
        canonicalJson,
        compilerOptions,
        explicitProfileDefaults: controlledTsConfigExplicitProfileDefaults,
        fingerprint,
        resolvedBooleanOptions: frozenResolved,
        revision: controlledTsConfigEffectiveOptionsRevision,
    });
};

const controlledRootSelectionAuthority = Object.freeze({
    defaultInclude: "**/*",
    extensions: Object.freeze([".ts", ".tsx"]),
    typeScriptVersion: "6.0.3",
});

// C7 exposes only the already materialized /sources inventory to TypeScript's
// pinned glob matcher. The callback is a closed snapshot oracle: neither
// package metadata, compiler host I/O nor a host filesystem is reachable.
const createControlledSnapshotFileSystemEntries = (sourcePaths) => {
    const entries = new Map();
    const ensureDirectory = (directoryPath) => {
        let entry = entries.get(directoryPath);
        if (entry === undefined) {
            entry = {
                directories: new Set(),
                files: new Set(),
            };
            entries.set(directoryPath, entry);
        }
        return entry;
    };
    ensureDirectory("/");
    for (const sourcePath of sourcePaths) {
        validateCanonicalVfsPath(sourcePath, ["/sources/"]);
        const segments = sourcePath.slice(1).split("/");
        let directoryPath = "/";
        for (let index = 0; index < segments.length - 1; index += 1) {
            const segment = segments[index];
            const childPath =
                directoryPath === "/"
                    ? `/${segment}`
                    : `${directoryPath}/${segment}`;
            ensureDirectory(directoryPath).directories.add(segment);
            ensureDirectory(childPath);
            directoryPath = childPath;
        }
        ensureDirectory(directoryPath).files.add(segments[segments.length - 1]);
    }
    return (directoryPath) => {
        const entry = entries.get(directoryPath);
        return entry === undefined
            ? { directories: [], files: [] }
            : {
                  directories: Array.from(entry.directories).sort(compareCodeUnits),
                  files: Array.from(entry.files).sort(compareCodeUnits),
              };
    };
};

const selectControlledRootNames = (parsedConfig, files, entryPath) => {
    if (
        ts.version !== controlledRootSelectionAuthority.typeScriptVersion ||
        typeof ts.matchFiles !== "function" ||
        parsedConfig === null ||
        !Object.isFrozen(parsedConfig) ||
        !Object.isFrozen(parsedConfig.selectors)
    ) {
        throw new Error("Controlled root-selection authority drifted.");
    }
    const sourcePaths = files
        .map((file) => file.path)
        .filter(
            (path) =>
                path.startsWith("/sources/") &&
                (path.endsWith(".ts") || path.endsWith(".tsx")),
        )
        .map((path) => path.normalize("NFC"))
        .sort(compareCodeUnits);
    if (!compareStrictlySortedUnique(sourcePaths)) {
        rejectControlledTsConfig(
            controlledTsConfigPolicyCodes.pathInvalid,
            "Controlled source inventory contains a canonical collision.",
        );
    }
    const sourcePathSet = new Set(sourcePaths);
    const selected = new Set();
    const selectors = parsedConfig.selectors;
    if (selectors.files !== null) {
        for (const selector of selectors.files) {
            const candidate = `/sources/${selector.normalize("NFC")}`;
            if (sourcePathSet.has(candidate)) {
                selected.add(candidate);
            }
        }
    }
    const includes =
        selectors.include !== null
            ? selectors.include
            : selectors.files === null
              ? [controlledRootSelectionAuthority.defaultInclude]
              : null;
    if (includes !== null && includes.length !== 0) {
        const getFileSystemEntries =
            createControlledSnapshotFileSystemEntries(sourcePaths);
        const includeMatches = ts.matchFiles(
            "/sources",
            controlledRootSelectionAuthority.extensions,
            selectors.exclude === null ? [] : selectors.exclude,
            includes,
            true,
            "/sources",
            undefined,
            getFileSystemEntries,
            (path) => path,
        );
        for (const matchedPath of includeMatches) {
            const canonicalPath = matchedPath.normalize("NFC");
            if (sourcePathSet.has(canonicalPath)) {
                selected.add(canonicalPath);
            }
        }
    }
    const rootNames = Array.from(selected).sort(compareCodeUnits);
    if (rootNames.length === 0) {
        rejectControlledTsConfig(
            controlledTsConfigPolicyCodes.noInputs,
            "Controlled tsconfig did not select any TypeScript inputs.",
        );
    }
    if (!rootNames.includes(entryPath)) {
        rejectControlledTsConfig(
            controlledTsConfigPolicyCodes.entryNotSelected,
            "Controlled tsconfig did not select the project entry.",
        );
    }
    return Object.freeze(rootNames);
};

let framedInput = null;
let framedCompilationRequestId = null;
let framedResult = null;
let frameHandlerBusy = false;
let pendingProjectInputFrame = null;
let projectInputFrameGeneration = 0;

class CancelledProjectInputFrameError extends Error {
    constructor(frameToken) {
        super("Project input frame was cancelled by a protocol violation.");
        this.frameToken = frameToken;
    }
}

const createProjectInputFrameToken = (message) => {
    projectInputFrameGeneration =
        projectInputFrameGeneration === Number.MAX_SAFE_INTEGER
            ? 1
            : projectInputFrameGeneration + 1;
    return {
        cancelled: false,
        generation: projectInputFrameGeneration,
        kind: message.kind,
        requestId: message.requestId,
        terminalPosted: false,
    };
};

const requireActiveProjectInputFrame = (frameToken) => {
    if (
        frameToken.cancelled ||
        pendingProjectInputFrame !== frameToken
    ) {
        throw new CancelledProjectInputFrameError(frameToken);
    }
};

const releaseProjectInputState = (state) => {
    if (state === null) {
        return;
    }
    state.currentFile?.bytes?.fill(0);
    state.currentFile = null;
    state.configTransport?.bytes?.fill(0);
    state.configTransport = null;
    state.controlledConfig = null;
    state.controlledEffectiveOptions = null;
    state.entryPath = null;
    for (const record of state.files) {
        record.text = "";
    }
    state.files.length = 0;
    state.packageMetadataCatalogRecords.length = 0;
    state.packageMetadataFiles.length = 0;
    state.packageMetadata = null;
    state.nodePackageFormatCatalog = null;
    state.nodePackageFormatDeclaredFingerprint = null;
    state.nodePackageFormatMode = null;
    state.nodePackageFormatRecords.length = 0;
    state.nodePackageTargets?.filePaths?.clear();
    state.nodePackageTargets = null;
    state.nodeRevisionTwoProgramMode = null;
    state.nodeResolutionAuthority = null;
    state.nodeResolutionDeclaredSnapshotFingerprint = null;
    state.nodeResolutionDeclaredTargetUniverseFingerprint = null;
    releaseRetainedPackageTargetTexts(state.packageTargetTextHolders);
    state.packageTargetFiles.length = 0;
    state.packageTargetTokenCounts.length = 0;
    state.packageTargetTokenCount = 0;
    state.packageTargets?.filePaths?.clear();
    state.packageTargets = null;
    state.runtimeCapabilitiesFingerprint = null;
    state.paths.clear();
    state.foldedPaths.clear();
    state.directoryIndex.release();
};

const projectOperationActive = (frameToken = null) =>
    framedInput !== null ||
    framedCompilationRequestId !== null ||
    framedResult !== null ||
    (frameHandlerBusy && pendingProjectInputFrame !== frameToken) ||
    (pendingProjectInputFrame !== null &&
        pendingProjectInputFrame !== frameToken);

const postProjectError = (requestId, error) => {
    self.postMessage({
        kind: "projectError",
        message: boundedWorkerError(error),
        requestId,
    });
};

const postProjectConfigError = (requestId, error) => {
    self.postMessage({
        emittedModuleCount: 0,
        errorCode: "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_DIAGNOSTICS",
        kind: "projectConfigError",
        message: String(error.message || "Controlled tsconfig was rejected.")
            .slice(0, 4096),
        outcome: "diagnostics",
        policyCode: error.policyCode,
        requestId,
        stage: "config",
    });
};

const acknowledgeProjectInput = (requestId, sequence) => {
    self.postMessage({
        kind: "projectInputAck",
        requestId,
        sequence,
    });
};

const requireInputFrame = (message, expectedKind) => {
    const state = framedInput;
    if (
        state === null ||
        message.requestId !== state.requestId ||
        message.kind !== expectedKind ||
        !Number.isSafeInteger(message.sequence) ||
        message.sequence !== state.nextSequence
    ) {
        throw new Error("Project input frame is out of sequence.");
    }
    return state;
};

const acceptProjectInputStart = async (message, frameToken) => {
    const legacyKeys = [
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
    ];
    const nodeRevision2Keys = legacyKeys.concat([
        "entryPath",
        "runtimeCapabilitiesFingerprint",
    ]);
    const rhinoRevision2Keys = nodeRevision2Keys;
    const nodeRevisionTwoProgramKeys = nodeRevision2Keys.concat([
        "nodeModuleOutputPolicySchemaRevision",
        "nodeModuleOutputPolicyRevision",
        "nodeModuleOutputPolicyFingerprint",
        "resolverPolicyRevision",
        "snapshotTargetInventoryFingerprint",
        "targetUniverseFingerprint",
    ]);
    const nodePackageFormatKeys = nodeRevision2Keys.concat([
        "packageMetadataByteLength",
        "packageMetadataCanonical",
        "packageMetadataFileCount",
        "packageMetadataInventoryFingerprint",
        "packageMetadataPolicyFingerprint",
        "packageMetadataPolicyRevision",
        "packageMetadataSchemaRevision",
        "nodePackageFormatSchemaRevision",
        "nodePackageFormatCatalogRevision",
        "nodePackageFormatCatalogFingerprint",
        "nodeModuleOutputPolicySchemaRevision",
        "nodeModuleOutputPolicyRevision",
        "nodeModuleOutputPolicyFingerprint",
    ]);
    const nodePackageFormatProgramKeys = nodePackageFormatKeys.concat([
        "resolverPolicyRevision",
        "snapshotTargetInventoryFingerprint",
        "targetUniverseFingerprint",
    ]);
    const nodePackageTargetProgramKeys = nodePackageFormatProgramKeys.concat([
        "nodePackageTargetByteLength",
        "nodePackageTargetCanonical",
        "nodePackageTargetFileCount",
        "nodePackageTargetFingerprint",
        "nodePackageTargetPolicyFingerprint",
        "nodePackageTargetPolicyRevision",
        "nodePackageTargetSchemaRevision",
    ]);
    const controlledKeys = legacyKeys.concat([
        "configByteLength",
        "configPolicyFingerprint",
        "configPolicyRevision",
        "configSha256",
        "entryPath",
    ]);
    const packageMetadataKeys = controlledKeys.concat([
        "packageMetadataByteLength",
        "packageMetadataCanonical",
        "packageMetadataFileCount",
        "packageMetadataFingerprint",
        "packageMetadataPolicyFingerprint",
        "packageMetadataPolicyRevision",
        "packageMetadataSchemaRevision",
    ]);
    const packageTargetKeys = packageMetadataKeys.concat([
        "packageTargetByteLength",
        "packageTargetCanonical",
        "packageTargetFileCount",
        "packageTargetFingerprint",
        "packageTargetPolicyFingerprint",
        "packageTargetPolicyRevision",
        "packageTargetSchemaRevision",
    ]);
    const isConfigNoneStart =
        hasExactKeys(message, legacyKeys) &&
        matchesProvisionalConfigNoneProfileAuthority(message);
    const isRhinoRevision2Start =
        hasExactKeys(message, rhinoRevision2Keys) &&
        matchesProvisionalRhinoRevision2ProfileAuthority(message) &&
        typeof message.entryPath === "string";
    const isNodeRevision2Start =
        hasExactKeys(message, nodeRevision2Keys) &&
        matchesProvisionalNodeNextRevision2ProfileAuthority(message) &&
        typeof message.entryPath === "string";
    const isNodePackageFormatStart =
        hasExactKeys(message, nodePackageFormatKeys) &&
        matchesProvisionalNodeNextRevision2ProfileAuthority(message) &&
        typeof message.entryPath === "string" &&
        message.packageMetadataSchemaRevision ===
            controlledPackageMetadataAuthority.schemaRevision &&
        message.packageMetadataPolicyRevision ===
            controlledPackageMetadataAuthority.policyRevision &&
        message.packageMetadataPolicyFingerprint ===
            controlledPackageMetadataAuthority.policyFingerprint &&
        message.nodePackageFormatSchemaRevision ===
            nodePackageFormatAuthority.schemaRevision &&
        message.nodePackageFormatCatalogRevision ===
            nodePackageFormatAuthority.catalogRevision &&
        typeof message.nodePackageFormatCatalogFingerprint === "string" &&
        sha256Pattern.test(message.nodePackageFormatCatalogFingerprint) &&
        message.nodeModuleOutputPolicySchemaRevision ===
            nodePackageFormatAuthority.nodeModuleOutputPolicySchemaRevision &&
        message.nodeModuleOutputPolicyRevision ===
            nodePackageFormatAuthority.nodeModuleOutputPolicyRevision &&
        message.nodeModuleOutputPolicyFingerprint ===
            nodePackageFormatAuthority.nodeModuleOutputPolicyFingerprint;
    const hasExactNodeModuleOutputPolicy =
        message.nodeModuleOutputPolicySchemaRevision ===
            nodePackageFormatAuthority.nodeModuleOutputPolicySchemaRevision &&
        message.nodeModuleOutputPolicyRevision ===
            nodePackageFormatAuthority.nodeModuleOutputPolicyRevision &&
        message.nodeModuleOutputPolicyFingerprint ===
            nodePackageFormatAuthority.nodeModuleOutputPolicyFingerprint;
    const hasExactNodeRevisionTwoResolverAuthority =
        message.resolverPolicyRevision ===
            nodeRevisionTwoResolutionAuthority.policyRevision &&
        typeof message.snapshotTargetInventoryFingerprint === "string" &&
        sha256Pattern.test(message.snapshotTargetInventoryFingerprint) &&
        typeof message.targetUniverseFingerprint === "string" &&
        sha256Pattern.test(message.targetUniverseFingerprint);
    const isNodeRevisionTwoProgramStart =
        hasExactKeys(message, nodeRevisionTwoProgramKeys) &&
        matchesProvisionalNodeNextRevision2ProfileAuthority(message) &&
        typeof message.entryPath === "string" &&
        hasExactNodeModuleOutputPolicy &&
        hasExactNodeRevisionTwoResolverAuthority;
    const isNodePackageFormatProgramStart =
        hasExactKeys(message, nodePackageFormatProgramKeys) &&
        matchesProvisionalNodeNextRevision2ProfileAuthority(message) &&
        typeof message.entryPath === "string" &&
        message.packageMetadataSchemaRevision ===
            controlledPackageMetadataAuthority.schemaRevision &&
        message.packageMetadataPolicyRevision ===
            controlledPackageMetadataAuthority.policyRevision &&
        message.packageMetadataPolicyFingerprint ===
            controlledPackageMetadataAuthority.policyFingerprint &&
        message.nodePackageFormatSchemaRevision ===
            nodePackageFormatAuthority.schemaRevision &&
        message.nodePackageFormatCatalogRevision ===
            nodePackageFormatAuthority.catalogRevision &&
        typeof message.nodePackageFormatCatalogFingerprint === "string" &&
        sha256Pattern.test(message.nodePackageFormatCatalogFingerprint) &&
        hasExactNodeModuleOutputPolicy &&
        hasExactNodeRevisionTwoResolverAuthority;
    const isNodePackageTargetProgramStart =
        hasExactKeys(message, nodePackageTargetProgramKeys) &&
        matchesProvisionalNodeNextRevision2ProfileAuthority(message) &&
        typeof message.entryPath === "string" &&
        message.packageMetadataSchemaRevision ===
            controlledPackageMetadataAuthority.schemaRevision &&
        message.packageMetadataPolicyRevision ===
            controlledPackageMetadataAuthority.policyRevision &&
        message.packageMetadataPolicyFingerprint ===
            controlledPackageMetadataAuthority.policyFingerprint &&
        message.nodePackageFormatSchemaRevision ===
            nodePackageFormatAuthority.schemaRevision &&
        message.nodePackageFormatCatalogRevision ===
            nodePackageFormatAuthority.catalogRevision &&
        typeof message.nodePackageFormatCatalogFingerprint === "string" &&
        sha256Pattern.test(message.nodePackageFormatCatalogFingerprint) &&
        hasExactNodeModuleOutputPolicy &&
        hasExactNodeRevisionTwoResolverAuthority &&
        message.nodePackageTargetSchemaRevision ===
            nodeRevisionTwoPackageTargetAuthority.schemaRevision &&
        message.nodePackageTargetPolicyRevision ===
            nodeRevisionTwoPackageTargetAuthority.policyRevision &&
        message.nodePackageTargetPolicyFingerprint ===
            nodeRevisionTwoPackageTargetAuthority.policyFingerprint;
    const isControlledStart =
        hasExactKeys(message, controlledKeys) &&
        matchesControlledTsConfigAuthority(message) &&
        Number.isSafeInteger(message.configByteLength) &&
        message.configByteLength >= 0 &&
        message.configByteLength <= controlledTsConfigAuthority.maximumBytes &&
        typeof message.configSha256 === "string" &&
        sha256Pattern.test(message.configSha256) &&
        typeof message.entryPath === "string";
    const isPackageMetadataStart =
        hasExactKeys(message, packageMetadataKeys) &&
        matchesControlledTsConfigAuthority(message) &&
        Number.isSafeInteger(message.configByteLength) &&
        message.configByteLength >= 0 &&
        message.configByteLength <= controlledTsConfigAuthority.maximumBytes &&
        typeof message.configSha256 === "string" &&
        sha256Pattern.test(message.configSha256) &&
        typeof message.entryPath === "string" &&
        message.packageMetadataSchemaRevision ===
            controlledPackageMetadataAuthority.schemaRevision &&
        message.packageMetadataPolicyRevision ===
            controlledPackageMetadataAuthority.policyRevision &&
        message.packageMetadataPolicyFingerprint ===
            controlledPackageMetadataAuthority.policyFingerprint;
    const isPackageTargetStart =
        hasExactKeys(message, packageTargetKeys) &&
        matchesControlledTsConfigAuthority(message) &&
        Number.isSafeInteger(message.configByteLength) &&
        message.configByteLength >= 0 &&
        message.configByteLength <= controlledTsConfigAuthority.maximumBytes &&
        typeof message.configSha256 === "string" &&
        sha256Pattern.test(message.configSha256) &&
        typeof message.entryPath === "string" &&
        message.packageMetadataSchemaRevision ===
            controlledPackageMetadataAuthority.schemaRevision &&
        message.packageMetadataPolicyRevision ===
            controlledPackageMetadataAuthority.policyRevision &&
        message.packageMetadataPolicyFingerprint ===
            controlledPackageMetadataAuthority.policyFingerprint &&
        message.packageTargetSchemaRevision ===
            controlledPackageTargetAuthority.schemaRevision &&
        message.packageTargetPolicyRevision ===
            controlledPackageTargetAuthority.policyRevision &&
        message.packageTargetPolicyFingerprint ===
            controlledPackageTargetAuthority.policyFingerprint;
    const hasControlledConfig =
        isControlledStart || isPackageMetadataStart || isPackageTargetStart;
    const isNodeRevision2Transport =
        isNodeRevision2Start || isNodePackageFormatStart;
    const isNodeRevisionTwoProgramTransport =
        isNodeRevisionTwoProgramStart ||
        isNodePackageFormatProgramStart ||
        isNodePackageTargetProgramStart;
    const isAnyNodeRevisionTwoTransport =
        isNodeRevision2Transport || isNodeRevisionTwoProgramTransport;
    const callerInputQuotaAuthority =
        message.inputQuotaRevision === inputArchiveAuthority.quotaRevision
            ? inputArchiveAuthority
            : message.inputQuotaRevision ===
                legacyRetainedInputQuotaAuthority.quotaRevision
              ? legacyRetainedInputQuotaAuthority
            : message.inputQuotaRevision ===
                testOnlyExpandedInputQuotaAuthority.quotaRevision
              ? testOnlyExpandedInputQuotaAuthority
              : null;
    const retainedInputQuotaAuthority =
        message.inputQuotaRevision ===
        legacyRetainedInputQuotaAuthority.quotaRevision
            ? legacyRetainedInputQuotaAuthority
            : callerInputQuotaAuthority === null
              ? null
              : ordinaryRetainedInputGuardrail;
    if (
        projectOperationActive(frameToken) ||
        (!isConfigNoneStart &&
            !isRhinoRevision2Start &&
            !hasControlledConfig &&
            !isAnyNodeRevisionTwoTransport) ||
        message.sequence !== 0 ||
        callerInputQuotaAuthority === null ||
        retainedInputQuotaAuthority === null ||
        !Number.isSafeInteger(message.fileCount) ||
        message.fileCount < 1 ||
        message.fileCount > retainedInputQuotaAuthority.maximumFiles ||
        !Number.isSafeInteger(message.totalBytes) ||
        message.totalBytes < 0 ||
        message.totalBytes > retainedInputQuotaAuthority.maximumTotalBytes ||
        message.inputArchiveSchemaRevision !== inputArchiveAuthority.schemaRevision ||
        typeof message.callerInventoryFingerprint !== "string" ||
        !sha256Pattern.test(message.callerInventoryFingerprint) ||
        typeof message.retainedInventoryFingerprint !== "string" ||
        !sha256Pattern.test(message.retainedInventoryFingerprint) ||
        typeof message.declarationProfileFingerprint !== "string" ||
        !sha256Pattern.test(message.declarationProfileFingerprint) ||
        typeof message.declarationInventoryFingerprint !== "string" ||
        !sha256Pattern.test(message.declarationInventoryFingerprint) ||
        !Number.isSafeInteger(message.retainedFileCount) ||
        message.retainedFileCount !==
            message.fileCount + (hasControlledConfig ? 1 : 0) ||
        message.retainedFileCount > retainedInputQuotaAuthority.maximumFiles ||
        !Number.isSafeInteger(message.largestFileBytes) ||
        message.largestFileBytes < 0 ||
        message.largestFileBytes >
            retainedInputQuotaAuthority.maximumSingleFileBytes ||
        message.largestFileBytes > message.totalBytes ||
        !Number.isSafeInteger(message.declarationFileCount) ||
        message.declarationFileCount < 1 ||
        message.declarationFileCount > message.fileCount ||
        !Number.isSafeInteger(message.declarationByteLength) ||
        message.declarationByteLength < 0 ||
        message.declarationByteLength > message.totalBytes ||
        (hasControlledConfig && message.totalBytes < message.configByteLength) ||
        !Number.isSafeInteger(message.workerSpinMilliseconds) ||
        message.workerSpinMilliseconds < 0 ||
        message.workerSpinMilliseconds > 10_000 ||
        (
            message.workerSpinMilliseconds === 0
                ? message.workerSpinPhase !== "none"
                : !projectExecutionPhaseSet.has(message.workerSpinPhase)
        )
    ) {
        throw new Error("Project input start frame is malformed or busy.");
    }
    const entryPath =
        hasControlledConfig || isRhinoRevision2Start || isAnyNodeRevisionTwoTransport
        ? validateCanonicalVfsPath(
              message.entryPath,
              ["/sources/"],
              null,
              null,
              isNodeRevisionTwoProgramTransport,
          )
        : null;
    if (
        entryPath !== null &&
        (
            isNodeRevisionTwoProgramTransport
                ? !isNodeRevisionTwoExecutableSourcePath(entryPath)
                : !isExecutableProjectSourcePath(entryPath)
        )
    ) {
        throw new Error("Controlled project entry is not an executable TypeScript source.");
    }
    const packageMetadata =
        isPackageMetadataStart ||
        isPackageTargetStart ||
        isNodePackageFormatStart ||
        isNodePackageFormatProgramStart ||
        isNodePackageTargetProgramStart
        ? await parseControlledPackageMetadataCanonical(
              message.packageMetadataCanonical,
              isNodePackageFormatStart ||
                  isNodePackageFormatProgramStart ||
                  isNodePackageTargetProgramStart
                  ? message.packageMetadataInventoryFingerprint
                  : message.packageMetadataFingerprint,
              message.packageMetadataFileCount,
              message.packageMetadataByteLength,
          )
        : null;
    requireActiveProjectInputFrame(frameToken);
    const packageTargets = isPackageTargetStart
        ? await parseControlledPackageTargetCanonical(
              message.packageTargetCanonical,
              message.packageTargetFingerprint,
              message.packageTargetFileCount,
              message.packageTargetByteLength,
              packageMetadata,
          )
        : null;
    requireActiveProjectInputFrame(frameToken);
    const nodePackageTargets = isNodePackageTargetProgramStart
        ? await parseNodeRevisionTwoPackageTargetCanonical(
              message.nodePackageTargetCanonical,
              message.nodePackageTargetFingerprint,
              message.nodePackageTargetFileCount,
              message.nodePackageTargetByteLength,
              packageMetadata,
          )
        : null;
    requireActiveProjectInputFrame(frameToken);
    const packageAuthorityFileCount =
        (packageMetadata?.files.length ?? 0) +
        (packageTargets?.files.length ?? 0) +
        (nodePackageTargets?.files.length ?? 0);
    if (
        packageMetadata !== null &&
        (message.fileCount <= packageAuthorityFileCount ||
            message.totalBytes <
                (hasControlledConfig ? message.configByteLength : 0) +
                    packageMetadata.totalBytes +
                    (packageTargets?.totalBytes ?? 0) +
                    (nodePackageTargets?.totalBytes ?? 0))
    ) {
        throw new Error("Project package authority accounting is malformed.");
    }
    requireActiveProjectInputFrame(frameToken);
    framedInput = {
        activePhase: null,
        configAccepted: !hasControlledConfig,
        configByteLength: hasControlledConfig ? message.configByteLength : 0,
        configKind: message.configKind,
        configPolicyFingerprint: hasControlledConfig
            ? message.configPolicyFingerprint
            : null,
        configPolicyRevision: hasControlledConfig
            ? message.configPolicyRevision
            : null,
        configSchemaRevision: message.configSchemaRevision,
        configSha256: hasControlledConfig ? message.configSha256 : null,
        configTransport: null,
        controlledConfig: null,
        controlledEffectiveOptions: null,
        callerInputQuotaAuthority,
        currentFile: null,
        callerInventoryFingerprint: message.callerInventoryFingerprint,
        declarationByteLength: message.declarationByteLength,
        declarationFileCount: message.declarationFileCount,
        declarationInventoryFingerprint: message.declarationInventoryFingerprint,
        declarationProfileFingerprint: message.declarationProfileFingerprint,
        declaredBytes: hasControlledConfig ? message.configByteLength : 0,
        directoryIndex: createDirectoryIndex(),
        entryPath,
        fileCount: message.fileCount,
        files: [],
        foldedPaths: new Set(),
        nextPhaseIndex: 0,
        nextSequence: 1,
        nodePackageFormatCatalog: null,
        nodePackageFormatDeclaredFingerprint: isNodePackageFormatStart
            ? message.nodePackageFormatCatalogFingerprint
            : isNodePackageFormatProgramStart || isNodePackageTargetProgramStart
              ? message.nodePackageFormatCatalogFingerprint
            : null,
        nodePackageFormatMode:
            isNodePackageFormatStart ||
            isNodePackageFormatProgramStart ||
            isNodePackageTargetProgramStart
            ? "present"
            : isNodeRevision2Start || isNodeRevisionTwoProgramStart
              ? "absent"
              : null,
        nodePackageFormatRecords: [],
        nodePackageTargets,
        nodeRevisionTwoProgramMode: isNodePackageTargetProgramStart
            ? "targets"
            : isNodePackageFormatProgramStart
              ? "metadata"
              : isNodeRevisionTwoProgramStart
                ? "no-metadata"
                : null,
        nodeResolutionAuthority: null,
        nodeResolutionDeclaredSnapshotFingerprint:
            isNodeRevisionTwoProgramTransport
                ? message.snapshotTargetInventoryFingerprint
                : null,
        nodeResolutionDeclaredTargetUniverseFingerprint:
            isNodeRevisionTwoProgramTransport
                ? message.targetUniverseFingerprint
                : null,
        packageMetadata,
        packageMetadataCatalogRecords: [],
        packageMetadataFiles: [],
        packageTargetFiles: [],
        packageTargetTextHolders: [],
        packageTargetTokenCount: 0,
        packageTargetTokenCounts: [],
        packageTargets,
        pathChars: 0,
        paths: new Set(),
        profileRevision: message.profileRevision,
        profileSchemaRevision: message.profileSchemaRevision,
        requestId: message.requestId,
        retainedFileCount: message.retainedFileCount,
        retainedInventoryFingerprint: message.retainedInventoryFingerprint,
        largestFileBytes: message.largestFileBytes,
        runtimeCapabilitiesFingerprint:
            isRhinoRevision2Start || isAnyNodeRevisionTwoTransport
            ? message.runtimeCapabilitiesFingerprint
            : null,
        targetProfile: message.targetProfile,
        tokenCount: 0,
        totalBytes: message.totalBytes,
        workerSpinMilliseconds: message.workerSpinMilliseconds,
        workerSpinPhase: message.workerSpinPhase,
    };
    enterProjectExecutionPhase(framedInput, "ingress");
    acknowledgeProjectInput(message.requestId, message.sequence);
};

const acceptProjectInputConfigStart = (message) => {
    const state = requireInputFrame(message, "projectInputConfigStart");
    if (
        !hasExactKeys(message, [
            "byteLength",
            "kind",
            "path",
            "requestId",
            "sequence",
            "sha256",
        ]) ||
        state.configKind !== controlledTsConfigAuthority.configKind ||
        state.configAccepted ||
        state.configTransport !== null ||
        state.currentFile !== null ||
        state.files.length !== 0 ||
        state.packageMetadataFiles.length !== 0 ||
        state.packageTargetFiles.length !== 0 ||
        message.path !== controlledTsConfigAuthority.configPath ||
        message.byteLength !== state.configByteLength ||
        message.sha256 !== state.configSha256
    ) {
        throw new Error("Project config start frame is malformed.");
    }
    state.configTransport = {
        bytes: new Uint8Array(message.byteLength),
        byteLength: message.byteLength,
        nextChunkIndex: 0,
        offset: 0,
        path: message.path,
        sha256: message.sha256,
    };
    state.nextSequence += 1;
    acknowledgeProjectInput(message.requestId, message.sequence);
};

const acceptProjectInputConfigChunk = (message) => {
    const state = requireInputFrame(message, "projectInputConfigChunk");
    const config = state.configTransport;
    if (
        !hasExactKeys(message, [
            "chunkIndex",
            "data",
            "kind",
            "requestId",
            "sequence",
        ]) ||
        config === null ||
        state.configAccepted ||
        !Number.isSafeInteger(message.chunkIndex) ||
        message.chunkIndex !== config.nextChunkIndex
    ) {
        throw new Error("Project config chunk frame is malformed.");
    }
    const remaining = config.byteLength - config.offset;
    const expectedBytes = Math.min(projectFrameBytes, remaining);
    const bytes = decodeCanonicalBase64(message.data);
    try {
        if (expectedBytes === 0 || bytes.byteLength !== expectedBytes) {
            throw new Error("Project config chunk has an invalid raw byte length.");
        }
        config.bytes.set(bytes, config.offset);
        config.offset += bytes.byteLength;
    } finally {
        bytes.fill(0);
    }
    config.nextChunkIndex += 1;
    state.nextSequence += 1;
    acknowledgeProjectInput(message.requestId, message.sequence);
};

const acceptProjectInputConfigEnd = async (message, frameToken) => {
    const state = requireInputFrame(message, "projectInputConfigEnd");
    const config = state.configTransport;
    if (
        !hasExactKeys(message, [
            "chunkCount",
            "kind",
            "requestId",
            "sequence",
        ]) ||
        config === null ||
        state.configAccepted ||
        config.offset !== config.byteLength ||
        !Number.isSafeInteger(message.chunkCount) ||
        message.chunkCount !== config.nextChunkIndex ||
        message.chunkCount !== Math.ceil(config.byteLength / projectFrameBytes)
    ) {
        throw new Error("Project config ended before its declared bytes.");
    }

    const bytes = config.bytes;
    try {
        const actualSha256 = await bytesToSha256(bytes);
        requireActiveProjectInputFrame(frameToken);
        if (actualSha256 !== config.sha256) {
            throw new Error("Project config digest does not match its bytes.");
        }
        let text;
        try {
            text = new TextDecoder("utf-8", {
                fatal: true,
                ignoreBOM: true,
            }).decode(bytes);
        } catch (_) {
            rejectControlledTsConfig(
                controlledTsConfigPolicyCodes.encodingInvalid,
                "Controlled tsconfig is not valid UTF-8.",
            );
        }
        const parsedConfig = parseControlledTsConfig(text);
        const effectiveOptions =
            await resolveControlledTsConfigEffectiveOptions(parsedConfig);
        requireActiveProjectInputFrame(frameToken);
        // No await or externally observable operation separates these writes.
        // The config-end ACK below therefore means both immutable authorities
        // were installed, never a partially resolved config.
        state.controlledConfig = parsedConfig;
        state.controlledEffectiveOptions = effectiveOptions;
    } finally {
        bytes.fill(0);
        config.bytes = null;
    }
    requireActiveProjectInputFrame(frameToken);
    state.configTransport = null;
    state.configAccepted = true;
    state.nextSequence += 1;
    acknowledgeProjectInput(message.requestId, message.sequence);
};

const acceptProjectInputFileStart = (message) => {
    const state = requireInputFrame(message, "projectInputFileStart");
    if (
        state.configKind === controlledTsConfigAuthority.configKind &&
        !state.configAccepted
    ) {
        throw new Error("Project source frames arrived before controlled config.");
    }
    if (
        !hasExactKeys(message, [
            "byteLength",
            "fileIndex",
            "kind",
            "path",
            "requestId",
            "sequence",
            "sha256",
        ])
    ) {
        throw new Error("Project input file start shape is not supported.");
    }
    const acceptedFileCount =
        state.packageMetadataFiles.length +
        state.packageTargetFiles.length +
        state.files.length;
    const packageMetadataDescriptor =
        state.packageMetadata !== null &&
        state.packageMetadataFiles.length < state.packageMetadata.files.length
            ? state.packageMetadata.files[state.packageMetadataFiles.length]
            : null;
    const packageMetadataComplete =
        state.packageMetadata === null ||
        state.packageMetadataFiles.length === state.packageMetadata.files.length;
    const activePackageTargets =
        state.nodePackageTargets ?? state.packageTargets;
    const packageTargetDescriptor =
        packageMetadataComplete &&
        activePackageTargets !== null &&
        state.packageTargetFiles.length < activePackageTargets.files.length
            ? activePackageTargets.files[state.packageTargetFiles.length]
            : null;
    const isPackageMetadata = packageMetadataDescriptor !== null;
    const isPackageTarget = packageTargetDescriptor !== null;
    const isNodePackageTarget =
        isPackageTarget && state.nodePackageTargets !== null;
    const path = validateCanonicalVfsPath(
        message.path,
        isPackageMetadata || isPackageTarget
            ? []
            : ["/sources/", "/declarations/"],
        isPackageMetadata ? state.packageMetadata.filePaths : null,
        isPackageTarget ? activePackageTargets.filePaths : null,
        state.nodeRevisionTwoProgramMode !== null,
    );
    if (
        state.nodePackageFormatMode !== null &&
        state.nodeRevisionTwoProgramMode === null &&
        !isPackageMetadata &&
        !isPackageTarget &&
        (
            (path.startsWith("/sources/") && !path.endsWith(".ts")) ||
            (path.startsWith("/declarations/") &&
                !path.endsWith(".d.ts"))
        )
    ) {
        throw new Error(
            "Node revision 2 Worker source-kind ingress remains closed until P2-6.",
        );
    }
    if (
        state.nodeRevisionTwoProgramMode !== null &&
        !isPackageMetadata &&
        !isPackageTarget &&
        (
            (path.startsWith("/sources/") &&
                !isNodeRevisionTwoExecutableSourcePath(path) &&
                !isNodeRevisionTwoRuntimeSupportSourcePath(path) &&
                !path.endsWith(".d.ts")) ||
            (path.startsWith("/declarations/") && !path.endsWith(".d.ts"))
        )
    ) {
        throw new Error(
            "Node revision 2 Program source-kind ingress is not supported.",
        );
    }
    const foldedPath = isPackageMetadata ? null : asciiCaseFold(path);
    if (
        !isPackageMetadata &&
        !isPackageTarget &&
        state.targetProfile === provisionalRhinoProfileAuthority.targetProfile &&
        foldedPath.startsWith(declarationLayerAuthority.nodePathPrefix)
    ) {
        throw new Error("Rhino input cannot transport the Node declaration layer.");
    }
    if (
        !isPackageMetadata &&
        !isPackageTarget &&
        state.targetProfile ===
            provisionalNodeNextProfileAuthority.targetProfile &&
        foldedPath.startsWith(declarationLayerAuthority.rhinoPathPrefix)
    ) {
        throw new Error(
            "NodeNext input cannot transport the Rhino declaration layer.",
        );
    }
    if (
        state.currentFile !== null ||
        acceptedFileCount >= state.fileCount ||
        (!isPackageMetadata &&
            (state.paths.has(path) || state.foldedPaths.has(foldedPath))) ||
        !Number.isSafeInteger(message.fileIndex) ||
        message.fileIndex !== acceptedFileCount ||
        !Number.isSafeInteger(message.byteLength) ||
        message.byteLength < 0 ||
        message.byteLength >
            (isPackageMetadata
                ? controlledPackageMetadataAuthority.maximumFileBytes
                : isPackageTarget
                  ? isNodePackageTarget
                      ? nodeRevisionTwoPackageTargetAuthority.maximumFileBytes
                      : controlledPackageTargetAuthority.maximumFileBytes
                : maximumProjectFileBytes) ||
        state.declaredBytes + message.byteLength > state.totalBytes ||
        (!isPackageMetadata && !isPackageTarget &&
            state.pathChars + path.length > maximumProjectPathChars) ||
        typeof message.sha256 !== "string" ||
        !sha256Pattern.test(message.sha256) ||
        (isPackageMetadata &&
            (path !== `/${packageMetadataDescriptor.path}` ||
                message.byteLength !== packageMetadataDescriptor.byteLength ||
                message.sha256 !== packageMetadataDescriptor.sha256)) ||
        (isPackageTarget &&
            (path !== `/${packageTargetDescriptor.path}` ||
                message.byteLength !== packageTargetDescriptor.byteLength ||
                message.sha256 !== packageTargetDescriptor.sha256))
    ) {
        throw new Error("Project input file start frame is malformed.");
    }
    if (!isPackageMetadata && !isPackageTarget) {
        state.directoryIndex.addFilePath(path);
    }
    state.currentFile = {
        bytes: new Uint8Array(message.byteLength),
        byteLength: message.byteLength,
        fileIndex: message.fileIndex,
        isPackageMetadata,
        isPackageTarget,
        isNodePackageTarget,
        nextChunkIndex: 0,
        offset: 0,
        path,
        sha256: message.sha256,
    };
    state.declaredBytes += message.byteLength;
    if (!isPackageMetadata && !isPackageTarget) {
        state.pathChars += path.length;
    }
    if (!isPackageMetadata) {
        state.paths.add(path);
        state.foldedPaths.add(foldedPath);
    }
    state.nextSequence += 1;
    acknowledgeProjectInput(message.requestId, message.sequence);
};

const acceptProjectInputFileChunk = (message) => {
    const state = requireInputFrame(message, "projectInputFileChunk");
    const file = state.currentFile;
    if (file === null) {
        throw new Error("Project input chunk has no active file.");
    }
    if (
        !hasExactKeys(message, [
            "chunkIndex",
            "data",
            "fileIndex",
            "kind",
            "requestId",
            "sequence",
        ]) ||
        !Number.isSafeInteger(message.fileIndex) ||
        message.fileIndex !== file.fileIndex ||
        !Number.isSafeInteger(message.chunkIndex) ||
        message.chunkIndex !== file.nextChunkIndex
    ) {
        throw new Error("Project input chunk index is out of sequence.");
    }
    const remaining = file.byteLength - file.offset;
    const expectedBytes = Math.min(projectFrameBytes, remaining);
    const bytes = decodeCanonicalBase64(message.data);
    try {
        if (expectedBytes === 0 || bytes.byteLength !== expectedBytes) {
            throw new Error("Project input chunk has an invalid raw byte length.");
        }
        file.bytes.set(bytes, file.offset);
        file.offset += bytes.byteLength;
    } finally {
        bytes.fill(0);
    }
    file.nextChunkIndex += 1;
    state.nextSequence += 1;
    acknowledgeProjectInput(message.requestId, message.sequence);
};

const acceptProjectInputFileEnd = async (message, frameToken) => {
    const state = requireInputFrame(message, "projectInputFileEnd");
    const file = state.currentFile;
    if (
        !hasExactKeys(message, [
            "chunkCount",
            "fileIndex",
            "kind",
            "requestId",
            "sequence",
        ]) ||
        file === null ||
        file.offset !== file.byteLength ||
        !Number.isSafeInteger(message.fileIndex) ||
        message.fileIndex !== file.fileIndex ||
        !Number.isSafeInteger(message.chunkCount) ||
        message.chunkCount !== file.nextChunkIndex ||
        message.chunkCount !==
            Math.ceil(file.byteLength / projectFrameBytes)
    ) {
        throw new Error("Project input file ended before its declared bytes.");
    }
    const bytes = file.bytes;
    const actualSha256 = await bytesToSha256(bytes);
    requireActiveProjectInputFrame(frameToken);
    if (actualSha256 !== file.sha256) {
        throw new Error("Project input file digest does not match its bytes.");
    }
    let text;
    try {
        text = new TextDecoder("utf-8", {
            fatal: true,
            ignoreBOM: true,
        }).decode(bytes);
    } catch (_) {
        if (file.isPackageMetadata) {
            throw new Error(
                `${controlledPackageMetadataPolicyCodes.encodingInvalid}: ` +
                    "Package metadata is not valid UTF-8.",
            );
        }
        if (file.isPackageTarget) {
            throw new Error(
                `${controlledPackageTargetPolicyCodes.inventoryInvalid}: ` +
                    "Package target is not valid UTF-8.",
            );
        }
        throw new Error("Project input file is not valid UTF-8.");
    }
    bytes.fill(0);
    file.bytes = null;
    state.currentFile = null;
    if (file.isPackageMetadata) {
        const root = validateControlledPackageMetadataJson(text);
        state.packageMetadataFiles.push({
            byteLength: file.byteLength,
            path: file.path,
            sha256: file.sha256,
        });
        if (state.nodePackageFormatMode === "present") {
            state.nodePackageFormatRecords.push(
                createNodePackageFormatRecord(file, root),
            );
        }
        // Revision 5 is the first transport that may activate C6 resolution.
        // Earlier revisions retain the C5 raw-only behavior and discard the
        // validated package root exactly as before.
        if (
            state.packageTargets !== null ||
            state.nodeRevisionTwoProgramMode === "metadata" ||
            state.nodeRevisionTwoProgramMode === "targets"
        ) {
            const metadataPath = file.path.slice(1);
            const boundaryPath = metadataPath === "package.json"
                ? "/"
                : `/${metadataPath.slice(0, -"/package.json".length)}`;
            state.packageMetadataCatalogRecords.push(
                Object.freeze({
                    boundaryPath,
                    path: metadataPath,
                    root,
                    sha256: file.sha256,
                    size: file.byteLength,
                }),
            );
        }
        state.nextSequence += 1;
        acknowledgeProjectInput(message.requestId, message.sequence);
        return;
    }
    if (file.isPackageTarget) {
        const tokenCount = countSourceTokens(text);
        if (
            state.tokenCount + state.packageTargetTokenCount + tokenCount >
                maximumProjectTokens
        ) {
            throw new Error("Project snapshot exceeds the token limit.");
        }
        const retained = createRetainedPackageTargetRecord(file, text);
        state.packageTargetFiles.push(retained.record);
        state.packageTargetTextHolders.push(retained.textHolder);
        state.packageTargetTokenCounts.push(tokenCount);
        state.packageTargetTokenCount += tokenCount;
        state.nextSequence += 1;
        acknowledgeProjectInput(message.requestId, message.sequence);
        return;
    }
    const tokenCount = countSourceTokens(text);
    if (
        state.tokenCount + state.packageTargetTokenCount + tokenCount >
            maximumProjectTokens
    ) {
        throw new Error("Project snapshot exceeds the token limit.");
    }
    state.files.push({
        byteLength: file.byteLength,
        path: file.path,
        sha256: file.sha256,
        text,
        tokenCount,
    });
    state.tokenCount += tokenCount;
    state.nextSequence += 1;
    acknowledgeProjectInput(message.requestId, message.sequence);
};

const postProjectResultChunk = (state, sequence) => {
    const offset = sequence * projectFrameBytes;
    const bytes = state.bytes.subarray(
        offset,
        Math.min(offset + projectFrameBytes, state.bytes.byteLength),
    );
    state.awaitingAck = sequence;
    self.postMessage({
        data: encodeBase64(bytes),
        index: sequence,
        kind: "projectResultChunk",
        requestId: state.requestId,
    });
};

const beginProjectResult = async (requestId, project, frameToken) => {
    let bytes = null;
    let transferredToFramedResult = false;
    try {
        const result = await compileProject(project, frameToken);
        requireActiveProjectInputFrame(frameToken);
        bytes = new TextEncoder().encode(JSON.stringify(result));
        if (bytes.byteLength > maximumProjectResultBytes) {
            throw new Error("Project result exceeds the framed result limit.");
        }
        const digest = await bytesToSha256(bytes);
        requireActiveProjectInputFrame(frameToken);
        // Emit remains active through the bounded, stop-and-wait result transfer.
        // The final result ACK proves that the native host accepted every framed
        // byte; only then may the Worker publish emit completion and result end.
        const chunkCount = Math.ceil(bytes.byteLength / projectFrameBytes);
        framedCompilationRequestId = null;
        framedResult = {
            awaitingAck: -1,
            bytes,
            chunkCount,
            execution: project.execution,
            requestId,
            sha256: digest,
        };
        transferredToFramedResult = true;
        self.postMessage({
            byteLength: bytes.byteLength,
            chunkCount,
            kind: "projectResultStart",
            requestId,
            sha256: digest,
        });
    } finally {
        if (!transferredToFramedResult && bytes !== null) {
            bytes.fill(0);
        }
    }
};

const acceptProjectInputEnd = async (message, frameToken) => {
    const state = requireInputFrame(message, "projectInputEnd");
    const activePackageTargets =
        state.nodePackageTargets ?? state.packageTargets;
    const expectsPackageMetadataCatalog =
        state.packageTargets !== null ||
        state.nodeRevisionTwoProgramMode === "metadata" ||
        state.nodeRevisionTwoProgramMode === "targets";
    const acceptedFileCount =
        state.packageMetadataFiles.length +
        state.packageTargetFiles.length +
        state.files.length;
    if (
        !hasExactKeys(message, [
            "fileCount",
            "kind",
            "requestId",
            "sequence",
            "totalBytes",
        ]) ||
        state.currentFile !== null ||
        acceptedFileCount !== state.fileCount ||
        (state.packageMetadata !== null &&
            state.packageMetadataFiles.length !==
                state.packageMetadata.files.length) ||
        (activePackageTargets !== null &&
            (
                state.packageTargetFiles.length !== activePackageTargets.files.length ||
                state.packageTargetTokenCounts.length !==
                    state.packageTargetFiles.length ||
                state.packageTargetTokenCounts.reduce(
                    (total, tokenCount) => total + tokenCount,
                    0,
                ) !== state.packageTargetTokenCount
            )) ||
        (activePackageTargets === null &&
            (
                state.packageTargetTokenCounts.length !== 0 ||
                state.packageTargetTokenCount !== 0
            )) ||
        (expectsPackageMetadataCatalog
            ? state.packageMetadata === null ||
              state.packageMetadataCatalogRecords.length !==
                  state.packageMetadataFiles.length
            : state.packageMetadataCatalogRecords.length !== 0) ||
        (state.nodePackageTargets !== null &&
            state.nodeRevisionTwoProgramMode !== "targets") ||
        (state.nodePackageFormatMode === "present" &&
            (
                state.packageMetadata === null ||
                state.nodePackageFormatDeclaredFingerprint === null ||
                state.nodePackageFormatCatalog !== null ||
                state.nodePackageFormatRecords.length !==
                    state.packageMetadataFiles.length
            )) ||
        (state.nodePackageFormatMode === "absent" &&
            (
                state.packageMetadata !== null ||
                state.nodePackageFormatDeclaredFingerprint !== null ||
                state.nodePackageFormatCatalog !== null ||
                state.nodePackageFormatRecords.length !== 0
            )) ||
        (state.nodePackageFormatMode === null &&
            (
                state.nodePackageFormatDeclaredFingerprint !== null ||
                state.nodePackageFormatCatalog !== null ||
                state.nodePackageFormatRecords.length !== 0
            )) ||
        state.tokenCount + state.packageTargetTokenCount > maximumProjectTokens ||
        message.fileCount !== state.fileCount ||
        message.totalBytes !== state.totalBytes ||
        state.declaredBytes !== state.totalBytes
    ) {
        throw new Error("Project input end does not match its file inventory.");
    }
    const sourceRecords = state.files
        .filter((file) => file.path.startsWith("/sources/"))
        .map((file) => ({
            byteLength: file.byteLength,
            domain: "source",
            path: file.path,
            sha256: file.sha256,
        }));
    const declarationRecords = state.files
        .filter((file) => file.path.startsWith("/declarations/"))
        .map((file) => ({
            byteLength: file.byteLength,
            domain: "declaration",
            path: file.path,
            sha256: file.sha256,
        }));
    const configRecords = state.configKind === controlledTsConfigAuthority.configKind
        ? [{
              byteLength: state.configByteLength,
              domain: "config",
              path: controlledTsConfigAuthority.configPath,
              sha256: state.configSha256,
          }]
        : [];
    const packageMetadataRecords = state.packageMetadataFiles.map((file) => ({
        byteLength: file.byteLength,
        domain: "package-metadata",
        path: file.path,
        sha256: file.sha256,
    }));
    const packageTargetDomain = state.nodePackageTargets !== null
        ? "package-target-v2"
        : "package-target-v1";
    const packageTargetRecords = state.packageTargetFiles.map((file) => ({
        byteLength: file.byteLength,
        domain: packageTargetDomain,
        path: file.path,
        sha256: file.sha256,
    }));
    const callerRecords = sourceRecords.concat(
        configRecords,
        packageMetadataRecords,
        packageTargetRecords,
    );
    const retainedRecords = callerRecords.concat(declarationRecords);
    const declarationBytes = declarationRecords.reduce(
        (total, record) => total + record.byteLength,
        0,
    );
    const callerBytes = callerRecords.reduce(
        (total, record) => total + record.byteLength,
        0,
    );
    const largestCallerFileBytes = callerRecords.reduce(
        (largest, record) => Math.max(largest, record.byteLength),
        0,
    );
    const largestFileBytes = retainedRecords.reduce(
        (largest, record) => Math.max(largest, record.byteLength),
        0,
    );
    const [
        callerInventoryFingerprint,
        retainedInventoryFingerprint,
        declarationInventoryFingerprint,
    ] = await Promise.all([
        deriveInputInventoryFingerprint(callerRecords),
        deriveInputInventoryFingerprint(retainedRecords),
        deriveInputInventoryFingerprint(declarationRecords),
    ]);
    requireActiveProjectInputFrame(frameToken);
    if (
        callerInventoryFingerprint !== state.callerInventoryFingerprint ||
        retainedInventoryFingerprint !== state.retainedInventoryFingerprint ||
        declarationInventoryFingerprint !== state.declarationInventoryFingerprint ||
        retainedRecords.length !== state.retainedFileCount ||
        declarationRecords.length !== state.declarationFileCount ||
        declarationBytes !== state.declarationByteLength ||
        largestFileBytes !== state.largestFileBytes ||
        callerRecords.length > state.callerInputQuotaAuthority.maximumFiles ||
        callerBytes > state.callerInputQuotaAuthority.maximumTotalBytes ||
        largestCallerFileBytes >
            state.callerInputQuotaAuthority.maximumSingleFileBytes
    ) {
        throw new Error(
            "Project input authority does not match its exact retained inventory.",
        );
    }
    const isNodeRevisionTwoProgram =
        state.nodeRevisionTwoProgramMode !== null;
    if (state.nodePackageFormatMode !== null) {
        if (
            !matchesProvisionalNodeNextRevision2ProfileAuthority(state) ||
            state.entryPath === null ||
            !state.files.some((file) => file.path === state.entryPath) ||
            !state.files.some(
                (file) => file.path === declarationLayerAuthority.nodeEntryPath,
            )
        ) {
            throw new Error(
                "Node revision 2 transport does not match its exact profile inventory.",
            );
        }
        if (state.nodePackageFormatMode === "present") {
            const catalog = await deriveNodePackageFormatCatalog(
                state.packageMetadata,
                state.nodePackageFormatRecords,
            );
            requireActiveProjectInputFrame(frameToken);
            if (
                catalog.fingerprint !==
                state.nodePackageFormatDeclaredFingerprint
            ) {
                throw new Error(
                    "Node package-format catalog fingerprint does not match its raw metadata.",
                );
            }
            state.nodePackageFormatCatalog = catalog;
        }
        if (!isNodeRevisionTwoProgram) {
            throw new Error(
                `${nodePackageFormatPolicyCodes.programNotActivated}: ` +
                    "Node revision 2 transport is verified, but Program admission is not activated.",
            );
        }
    }
    let rootNames;
    if (isNodeRevisionTwoProgram) {
        rootNames = state.files
            .map((file) => file.path)
            .filter(isNodeRevisionTwoExecutableSourcePath)
            .sort(compareCodeUnits);
        if (
            rootNames.length === 0 ||
            !rootNames.includes(state.entryPath) ||
            !compareStrictlySortedUnique(rootNames)
        ) {
            throw new Error(
                "Node revision 2 Program roots do not match its source inventory.",
            );
        }
    } else if (state.configKind === controlledTsConfigAuthority.configKind) {
        if (
            !state.configAccepted ||
            state.controlledConfig === null ||
            state.controlledEffectiveOptions === null
        ) {
            throw new Error("Project input ended before controlled config was accepted.");
        }
        rootNames = selectControlledRootNames(
            state.controlledConfig,
            state.files,
            state.entryPath,
        );
    } else {
        rootNames = state.files
            .map((file) => file.path)
            .filter((path) => isExecutableProjectSourcePath(path))
            .sort(compareCodeUnits);
    }
    const packageTargetAuthority =
        createRetainedPackageTargetAuthority(activePackageTargets);
    const packageMetadataCatalog = !expectsPackageMetadataCatalog
        ? null
        : Object.freeze({
              dependencyRoots: state.packageMetadata.dependencyRoots,
              inventoryFingerprint: state.packageMetadata.fingerprint,
              records: Object.freeze(
                  state.packageMetadataCatalogRecords.slice(),
              ),
          });
    if (
        packageMetadataCatalog !== null &&
        validateControlledNodeMetadataCatalog(packageMetadataCatalog) === null
    ) {
        throw new Error("Controlled package metadata resolver catalog is malformed.");
    }
    const nodeResolutionAuthority = isNodeRevisionTwoProgram
        ? await deriveNodeRevisionTwoResolutionAuthority(
              state.files,
              state.nodePackageTargets,
              state.nodeResolutionDeclaredSnapshotFingerprint,
              state.nodeResolutionDeclaredTargetUniverseFingerprint,
          )
        : null;
    requireActiveProjectInputFrame(frameToken);
    const nodePackageFormatCatalog = state.nodePackageFormatCatalog;
    const nodeRevisionTwoProgramMode = state.nodeRevisionTwoProgramMode;
    const ignoredConfigProperties =
        state.controlledConfig?.ignoredProperties ?? Object.freeze([]);
    const runtimeCapabilitiesFingerprint =
        state.runtimeCapabilitiesFingerprint;
    const packageTargetFiles = state.packageTargetFiles.slice();
    const packageTargetTextHolders = state.packageTargetTextHolders.slice();
    const packageTargetTokenCount = state.packageTargetTokenCount;
    const packageTargetTokenCounts = state.packageTargetTokenCounts.slice();
    // Complete and detach only after every new rev5 resolver authority has
    // been built and revalidated. A rejection before this point is still able
    // to release the live framed state and all retained target holders.
    requireActiveProjectInputFrame(frameToken);
    state.nextSequence += 1;
    completeProjectExecutionPhase(state, "ingress");
    acknowledgeProjectInput(message.requestId, message.sequence);
    framedInput = null;
    framedCompilationRequestId = message.requestId;
    state.paths.clear();
    state.foldedPaths.clear();
    state.packageMetadataCatalogRecords.length = 0;
    state.packageMetadataFiles.length = 0;
    state.packageMetadata = null;
    state.nodePackageFormatCatalog = null;
    state.nodePackageFormatDeclaredFingerprint = null;
    state.nodePackageFormatMode = null;
    state.nodePackageFormatRecords.length = 0;
    state.nodePackageTargets?.filePaths?.clear();
    state.nodePackageTargets = null;
    state.nodeRevisionTwoProgramMode = null;
    state.nodeResolutionAuthority = null;
    state.nodeResolutionDeclaredSnapshotFingerprint = null;
    state.nodeResolutionDeclaredTargetUniverseFingerprint = null;
    state.packageTargetFiles.length = 0;
    state.packageTargetTextHolders.length = 0;
    state.packageTargetTokenCounts.length = 0;
    state.packageTargetTokenCount = 0;
    state.packageTargets?.filePaths?.clear();
    state.packageTargets = null;
    state.runtimeCapabilitiesFingerprint = null;
    state.controlledConfig = null;
    const compilerTotalBytes = state.files.reduce(
        (total, file) => total + file.byteLength,
        0,
    );
    // The end ACK is posted before TypeScript enters its synchronous program
    // construction and diagnostics/emit work.
    const compilerProject = {
        configKind: state.configKind,
        configPolicyFingerprint: state.configPolicyFingerprint,
        configPolicyRevision: state.configPolicyRevision,
        configSchemaRevision: state.configSchemaRevision,
        controlledEffectiveOptions: state.controlledEffectiveOptions,
        directoryIndex: state.directoryIndex,
        files: state.files,
        ignoredConfigProperties,
        packageTargetAuthority,
        packageTargetFiles,
        packageTargetTextHolders,
        pathChars: state.pathChars,
        profileRevision: state.profileRevision,
        profileSchemaRevision: state.profileSchemaRevision,
        rootNames: rootNames.slice(),
        targetProfile: state.targetProfile,
        tokenCount: state.tokenCount,
        totalBytes: compilerTotalBytes,
        execution: {
            activePhase: state.activePhase,
            nextPhaseIndex: state.nextPhaseIndex,
            phaseProgress: state.phaseProgress,
            requestId: state.requestId,
            workerSpinMilliseconds: state.workerSpinMilliseconds,
            workerSpinPhase: state.workerSpinPhase,
        },
    };
    if (runtimeCapabilitiesFingerprint !== null) {
        compilerProject.runtimeCapabilitiesFingerprint =
            runtimeCapabilitiesFingerprint;
    }
    if (isNodeRevisionTwoProgram) {
        compilerProject.nodePackageFormatCatalog = nodePackageFormatCatalog;
        compilerProject.nodeResolutionAuthority = nodeResolutionAuthority;
        compilerProject.nodeRevisionTwoProgram = true;
        compilerProject.nodeRevisionTwoProgramMode =
            nodeRevisionTwoProgramMode;
        compilerProject.packageMetadataCatalog = packageMetadataCatalog;
    }
    if (packageTargetAuthority !== null) {
        if (!isNodeRevisionTwoProgram) {
            compilerProject.packageMetadataCatalog = packageMetadataCatalog;
        }
        compilerProject.packageTargetTokenCount = packageTargetTokenCount;
        compilerProject.packageTargetTokenCounts = packageTargetTokenCounts;
    }
    return beginProjectResult(message.requestId, compilerProject, frameToken);
};

const acceptProjectResultAck = (message) => {
    const state = framedResult;
    if (
        state === null ||
        message.requestId !== state.requestId
    ) {
        // A late ACK from a terminated operation is harmless and must not
        // disturb a successor.
        return;
    }
    if (!hasExactKeys(message, ["kind", "requestId", "sequence"])) {
        throw new Error("Project result acknowledgement shape is not supported.");
    }
    if (
        !Number.isSafeInteger(message.sequence) ||
        message.sequence !== state.awaitingAck
    ) {
        // A late ACK from a terminated operation is harmless and must not
        // disturb a successor.
        return;
    }
    const nextSequence = message.sequence + 1;
    if (nextSequence < state.chunkCount) {
        postProjectResultChunk(state, nextSequence);
        return;
    }
    const resultByteLength = state.bytes.byteLength;
    completeProjectExecutionPhase(state.execution, "emit");
    state.bytes.fill(0);
    framedResult = null;
    self.postMessage({
        byteLength: resultByteLength,
        chunkCount: state.chunkCount,
        kind: "projectResultEnd",
        requestId: state.requestId,
        sha256: state.sha256,
    });
};

const handleProjectInputFrame = async (message) => {
    if (frameHandlerBusy) {
        if (
            pendingProjectInputFrame !== null &&
            pendingProjectInputFrame.requestId === message.requestId
        ) {
            pendingProjectInputFrame.cancelled = true;
        }
        throw new Error("Project input violated stop-and-wait backpressure.");
    }
    frameHandlerBusy = true;
    const frameToken = createProjectInputFrameToken(message);
    pendingProjectInputFrame = frameToken;
    try {
        switch (message.kind) {
            case "projectInputStart":
                await acceptProjectInputStart(message, frameToken);
                break;
            case "projectInputConfigStart":
                acceptProjectInputConfigStart(message);
                break;
            case "projectInputConfigChunk":
                acceptProjectInputConfigChunk(message);
                break;
            case "projectInputConfigEnd":
                await acceptProjectInputConfigEnd(message, frameToken);
                break;
            case "projectInputFileStart":
                acceptProjectInputFileStart(message);
                break;
            case "projectInputFileChunk":
                acceptProjectInputFileChunk(message);
                break;
            case "projectInputFileEnd":
                await acceptProjectInputFileEnd(message, frameToken);
                break;
            case "projectInputEnd":
                await acceptProjectInputEnd(message, frameToken);
                break;
            default:
                throw new Error("Unsupported project input frame.");
        }
    } catch (error) {
        if (frameToken.cancelled) {
            throw new CancelledProjectInputFrameError(frameToken);
        }
        throw error;
    } finally {
        if (pendingProjectInputFrame === frameToken) {
            pendingProjectInputFrame = null;
        }
        frameHandlerBusy = false;
    }
};

self.onmessage = async (event) => {
    const request = event.data;
    if (
        !isRecord(request) ||
        typeof request.kind !== "string" ||
        !isRequestId(request.requestId)
    ) {
        return;
    }
    try {
        if (
            request.kind === "probe" &&
            isRecord(request.vfs) &&
            !projectOperationActive()
        ) {
            self.postMessage({
                kind: "probeResult",
                requestId: request.requestId,
                result: await runProbe(request),
            });
        } else if (request.kind.startsWith("projectInput")) {
            await handleProjectInputFrame(request);
        } else if (request.kind === "projectResultAck") {
            acceptProjectResultAck(request);
        }
    } catch (error) {
        const isProjectRequest =
            request.kind.startsWith("projectInput") ||
            request.kind === "projectResultAck";
        if (isProjectRequest) {
            const cancelledPendingFrame =
                pendingProjectInputFrame !== null &&
                pendingProjectInputFrame.cancelled &&
                pendingProjectInputFrame.requestId === request.requestId
                    ? pendingProjectInputFrame
                    : null;
            if (
                framedInput?.requestId === request.requestId ||
                framedCompilationRequestId === request.requestId
            ) {
                releaseProjectInputState(framedInput);
                framedInput = null;
                framedCompilationRequestId = null;
            }
            if (framedResult?.requestId === request.requestId) {
                framedResult.bytes.fill(0);
                framedResult = null;
            }
            const terminalAlreadyPosted =
                cancelledPendingFrame?.terminalPosted === true ||
                (error instanceof CancelledProjectInputFrameError &&
                    error.frameToken.terminalPosted);
            if (!terminalAlreadyPosted) {
                if (cancelledPendingFrame !== null) {
                    cancelledPendingFrame.terminalPosted = true;
                }
                if (error instanceof ControlledTsConfigPolicyError) {
                    postProjectConfigError(request.requestId, error);
                } else {
                    postProjectError(request.requestId, error);
                }
            }
        } else {
            self.postMessage({
                kind: "probeError",
                message: boundedWorkerError(error),
                requestId: request.requestId,
            });
        }
    }
};

self.postMessage({
    kind: "ready",
    tsVersion: ts.version,
});
