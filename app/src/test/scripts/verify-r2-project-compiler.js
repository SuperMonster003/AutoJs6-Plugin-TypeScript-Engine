"use strict";

const crypto = require("node:crypto");
const childProcess = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");
const v8 = require("node:v8");
const vm = require("node:vm");
const {
    CURRENT_ORDINARY_INPUT_QUOTA_REVISION,
    TEST_ONLY_EXPANDED_INPUT_QUOTA_REVISION,
    descriptorRecord: inputArchiveDescriptorRecord,
    projectInputAuthority,
    record: inputArchiveRecord,
} = require("./r2-input-archive-authority.js");

const [workerArgument, compilerArgument, fixtureArgument, expectedVersion] =
    process.argv.slice(2);
const nodeMajor = Number.parseInt(process.versions.node.split(".")[0], 10);
if (!Number.isInteger(nodeMajor) || nodeMajor < 18) {
    throw new Error("The R2 desktop golden requires Node.js 18 or newer.");
}
if (
    !workerArgument ||
    !compilerArgument ||
    !fixtureArgument ||
    !expectedVersion
) {
    throw new Error(
        "Usage: node verify-r2-project-compiler.js " +
            "<worker.js> <typescript.js> <fixture.json> <expected-version>",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const fixturePath = path.resolve(fixtureArgument);
const fixture = JSON.parse(fs.readFileSync(fixturePath, "utf8"));
const x2FixturePath = path.resolve(
    path.dirname(fixturePath),
    "..",
    "r2-compiler-diagnostics",
    "x2-v1.json",
);
const x2Fixture = JSON.parse(fs.readFileSync(x2FixturePath, "utf8"));
const workerSource = fs.readFileSync(workerPath, "utf8");

if (!global.crypto) {
    Object.defineProperty(global, "crypto", {
        value: crypto.webcrypto,
    });
}
global.ts = require(compilerPath);
global.importScripts = (specifier) => {
    if (specifier !== "typescript.js") {
        throw new Error(`Worker imported an unexpected script: ${specifier}`);
    }
};
const messages = [];
const messageObservations = [];
let lastDispatchMessageObservations = [];
global.self = {
    postMessage: (message) => {
        messages.push(message);
        messageObservations.push({
            message,
            observedAtElapsedRealtimeNanos: process.hrtime.bigint(),
        });
    },
};
vm.runInThisContext(workerSource, {
    filename: workerPath,
});
{
    const readyMessage = messages.find((message) => message.kind === "ready");
    if (!readyMessage || readyMessage.tsVersion !== expectedVersion) {
        throw new Error("Compiler Worker did not publish its exact ready version.");
    }
    messages.length = 0;
    messageObservations.length = 0;
}

const assert = (condition, message) => {
    if (!condition) {
        throw new Error(message);
    }
};

const expectThrows = (operation, pattern, message) => {
    let failure = null;
    try {
        operation();
    } catch (error) {
        failure = error;
    }
    assert(failure !== null, message);
    assert(
        pattern.test(failure instanceof Error ? failure.message : String(failure)),
        `${message} Unexpected error: ${
            failure instanceof Error ? failure.message : String(failure)
        }`,
    );
};

const workerBinding = (name) =>
    vm.runInThisContext(name, {
        filename: `${workerPath}#desktop-binding-${name}`,
    });

const verifyCompleteFixedWorkerPolicy = () => {
    const optionsBlock = workerSource.match(
        /const options = Object\.freeze\(\{\r?\n([\s\S]*?)\r?\n\s*\}\);/,
    );
    assert(optionsBlock, "Worker fixed compiler options block is missing.");
    const actual = optionsBlock[1]
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter(Boolean);
    const expected = [
        "allowJs: false,",
        "checkJs: false,",
        'ignoreDeprecations: "6.0",',
        "incremental: false,",
        "inlineSourceMap: false,",
        "inlineSources: false,",
        "module: ts.ModuleKind.CommonJS,",
        "moduleResolution: ts.ModuleResolutionKind.Node10,",
        "newLine: ts.NewLineKind.LineFeed,",
        "noEmitOnError: true,",
        "noLib: false,",
        'outDir: "/outputs",',
        'rootDir: "/sources",',
        "skipLibCheck: false,",
        "sourceMap: true,",
        "strict: true,",
        "target: ts.ScriptTarget.ES2018,",
        "types: [],",
    ];
    assert(
        JSON.stringify(actual) === JSON.stringify(expected),
        "Worker fixed compiler policy drifted from the runtime-capability authority.",
    );
};

const p3bWorkerLimits = Object.freeze({
    maximumDirectoryChars: 4 * 1024 * 1024,
    maximumDirectoryNodes: 65_536,
    maximumPathChars: 8 * 1024 * 1024,
    maximumProjectBytes: 72 * 1024 * 1024,
    maximumProjectFiles: 4_608,
    maximumProjectTokens: 262_144,
    maximumTokensPerFile: 65_536,
});

const verifyP3bWorkerSourcePolicy = () => {
    for (const [name, expected] of Object.entries({
        maximumProjectFiles: p3bWorkerLimits.maximumProjectFiles,
        maximumProjectBytes: p3bWorkerLimits.maximumProjectBytes,
        maximumTokensPerFile: p3bWorkerLimits.maximumTokensPerFile,
        maximumProjectTokens: p3bWorkerLimits.maximumProjectTokens,
        maximumProjectPathChars: p3bWorkerLimits.maximumPathChars,
        maximumProjectDirectoryNodes: p3bWorkerLimits.maximumDirectoryNodes,
        maximumProjectDirectoryChars: p3bWorkerLimits.maximumDirectoryChars,
    })) {
        assert(
            workerBinding(name) === expected,
            `Worker ${name} does not match the P3b-a capacity authority.`,
        );
    }

    const vfsSource = workerBinding("createReadonlyProjectVfs").toString();
    assert(
        !/\bTextEncoder\b|\bTextDecoder\b/.test(vfsSource),
        "Worker VFS re-encodes or re-decodes already validated source text.",
    );
    assert(
        typeof workerBinding("createDirectoryIndex") === "function",
        "Worker does not expose its bounded segment-trie constructor.",
    );

    const compilerHostSource = workerBinding(
        "createProjectCompilerHost",
    ).toString();
    assert(
        /sourceFileCache/.test(compilerHostSource),
        "Worker compiler host does not expose its per-job SourceFile cache.",
    );
    assert(
        /sourceFile\.version\s*=\s*record\.sha256/.test(compilerHostSource),
        "Worker cached SourceFiles do not carry the accepted content version.",
    );

    const compileSource = workerBinding("compileProject").toString();
    assert(
        /\bcreateDiagnosticCollector\b/.test(compileSource),
        "Worker compile path does not use the incremental diagnostic collector.",
    );
    assert(
        !/const\s+diagnosticGroups\s*=\s*\[/.test(compileSource),
        "Worker still retains every diagnostic phase before applying limits.",
    );
};

const snapshot = (mainSource) => ({
    files: fixture.commonFiles.concat([
        {
            path: "/sources/main.ts",
            text: mainSource,
        },
    ]),
    rootNames: fixture.rootNames,
    targetProfile: fixture.targetProfile,
});

const typeOnlyRootImportSnapshot = () => ({
    files: fixture.commonFiles
        .filter((file) => file.path.startsWith("/declarations/"))
        .concat(fixture.typeOnlyRootImport.files),
    rootNames: fixture.typeOnlyRootImport.rootNames,
    targetProfile: fixture.targetProfile,
});

const declarationErrorSnapshot = () => ({
    files: [
        {
            path: "/declarations/lib.d.ts",
            text: "",
        },
        {
            path: "/declarations/lib.es2018.d.ts",
            text: fixture.declarationError.libraryText,
        },
        {
            path: "/sources/main.ts",
            text: fixture.declarationError.mainSource,
        },
    ],
    rootNames: fixture.declarationError.rootNames,
    targetProfile: fixture.targetProfile,
});

const sourceDeclarationErrorSnapshot = () => ({
    files: fixture.commonFiles
        .filter((file) => file.path.startsWith("/declarations/"))
        .concat(fixture.sourceDeclarationError.files),
    rootNames: fixture.sourceDeclarationError.rootNames,
    targetProfile: fixture.targetProfile,
});

const fullStandardLibrarySnapshot = () => {
    const compilerDirectory = path.dirname(compilerPath);
    const declarationNames = fs.readdirSync(compilerDirectory)
        .filter((name) => /^lib(?:\..+)?\.d\.ts$/.test(name))
        .sort();
    assert(
        declarationNames.length === 108,
        `Expected 108 generated standard libraries, received ${declarationNames.length}.`,
    );
    const declarationFiles = declarationNames.map((name) => ({
        path: `/declarations/${name}`,
        text: fs.readFileSync(path.join(compilerDirectory, name), "utf8"),
    }));
    const source = {
        path: "/sources/main.ts",
        text:
            "export const framedValue: Promise<number> = " +
            "Promise.resolve(1);\n",
    };
    return {
        files: declarationFiles.concat([source]),
        rootNames: [source.path],
        targetProfile: fixture.targetProfile,
    };
};

const t1LargeProjectSnapshot = () => {
    const declarationFileCount = 1_024;
    const declarationFileBytes = 32 * 1024;
    const sharedPadding = "x".repeat(declarationFileBytes);
    const declarations = Array.from(
        { length: declarationFileCount },
        (_, index) => {
            const suffix = index.toString().padStart(4, "0");
            const tail = `*/\ndeclare const t1Bulk${suffix}: number;\n`;
            const prefix = "/*";
            const paddingBytes =
                declarationFileBytes -
                Buffer.byteLength(prefix + tail, "utf8");
            const text = prefix + sharedPadding.slice(0, paddingBytes) + tail;
            assert(
                Buffer.byteLength(text, "utf8") === declarationFileBytes,
                "T1-1 large-project declaration size drifted.",
            );
            return {
                path: `/sources/bulk/${suffix}.d.ts`,
                text,
            };
        },
    );
    const source = {
        path: "/sources/main.ts",
        text:
            declarations
                .map((file) =>
                    `/// <reference path="./bulk/${path.posix.basename(file.path)}" />`,
                )
                .join("\n") +
            "\nexport const t1LargeProjectValue: number = t1Bulk0000;\n",
    };
    const callerBytes = declarations.reduce(
        (total, file) => total + Buffer.byteLength(file.text, "utf8"),
        Buffer.byteLength(source.text, "utf8"),
    );
    assert(
        declarations.length + 1 > 1_000 && callerBytes > 30 * 1024 * 1024,
        "T1-1 large-project acceptance fixture is below its public threshold.",
    );
    return {
        callerBytes,
        callerFiles: declarations.length + 1,
        files: fixture.commonFiles
            .filter((file) => file.path.startsWith("/declarations/"))
            .concat(declarations, [source]),
        rootNames: [source.path],
        targetProfile: fixture.targetProfile,
    };
};

const naturalPhaseWorkloadAuthority = Object.freeze({
    declarationBytes: 3_784_758,
    declarationFiles: 108,
    declarationTokens: 86_329,
    javascriptOutputBytes: 290_497,
    maximumRootTokens: 760,
    outputAggregateSha256:
        "2f4dc949984424bb6eff1d806efcac2fd21f4ae8ad87a0c44f03f2c086d1444d",
    outputBytes: 562_945,
    outputFiles: 384,
    rootBytes: 810_241,
    rootFiles: 192,
    rootTokens: 145_920,
    runtimeValuesPerRoot: 16,
    sourceMapOutputBytes: 272_448,
    totalBytes: 4_594_999,
    totalFiles: 300,
    totalTokens: 232_249,
    typeAliasesPerRoot: 46,
});

const naturalPhaseSourceText = (index) => {
    const prefix = index.toString().padStart(3, "0");
    let text =
        `export interface NaturalRecord${prefix}<T> { ` +
        "readonly id: number; readonly value: T; }\n";
    for (
        let valueIndex = 0;
        valueIndex < naturalPhaseWorkloadAuthority.runtimeValuesPerRoot;
        valueIndex += 1
    ) {
        text +=
            `export const naturalValue${prefix}_` +
            `${valueIndex.toString().padStart(2, "0")}: ` +
            `NaturalRecord${prefix}<number> = { id: ${valueIndex}, ` +
            `value: ${index + valueIndex} };\n`;
    }
    for (
        let aliasIndex = 0;
        aliasIndex < naturalPhaseWorkloadAuthority.typeAliasesPerRoot;
        aliasIndex += 1
    ) {
        text +=
            `export type NaturalAlias${prefix}_` +
            `${aliasIndex.toString().padStart(2, "0")} = ` +
            `NaturalRecord${prefix}<number>;\n`;
    }
    return (
        text +
        `export function select${prefix}(item: ` +
        `NaturalRecord${prefix}<number>): number { ` +
        "return item.id + item.value; }\n"
    );
};

const naturalPhaseWorkloadSnapshot = () => {
    const compilerDirectory = path.dirname(compilerPath);
    const declarations = fs.readdirSync(compilerDirectory)
        .filter((name) => /^lib(?:\..+)?\.d\.ts$/.test(name))
        .sort()
        .map((name) => ({
            path: `/declarations/${name}`,
            text: fs.readFileSync(path.join(compilerDirectory, name), "utf8"),
        }));
    const roots = Array.from(
        { length: naturalPhaseWorkloadAuthority.rootFiles },
        (_, index) => ({
            path:
                `/sources/natural-phase-` +
                `${index.toString().padStart(3, "0")}.ts`,
            text: naturalPhaseSourceText(index),
        }),
    );
    return {
        files: declarations.concat(roots),
        rootNames: roots.map((root) => root.path),
        targetProfile: fixture.targetProfile,
    };
};

const naturalPhaseTimeoutWorkloadAuthority = Object.freeze({
    declarationBytes: 3_784_758,
    declarationFiles: 108,
    declarationTokens: 86_329,
    heavyPrivateFieldsPerRoot: 8,
    helperAtoms: 72,
    helperBytes: 3_763_492,
    helperFiles: 148,
    helperTokens: 28_268,
    helperWhitespaceBytes: 24 * 1024,
    javascriptOutputBytes: 535_202,
    lightRootFiles: 64,
    maximumHelperTokens: 191,
    maximumRootTokens: 169,
    outputAggregateSha256:
        "4b5651d65dc07b0fbaadcb7b31db3908a3de331829f21ce32fba56ac944b4cc1",
    outputBytes: 720_866,
    outputFiles: 512,
    rootBytes: 178_722,
    rootFiles: 256,
    rootTokens: 36_928,
    sourceMapOutputBytes: 185_664,
    totalBytes: 7_726_972,
    totalFiles: 512,
    totalTokens: 151_525,
});

const naturalPhaseTimeoutHelperIndex = (rootIndex) => {
    assert(
        rootIndex >= naturalPhaseTimeoutWorkloadAuthority.lightRootFiles &&
            rootIndex < naturalPhaseTimeoutWorkloadAuthority.rootFiles,
        "Natural-phase timeout helper index received a light or invalid root.",
    );
    return (
        (rootIndex -
            naturalPhaseTimeoutWorkloadAuthority.lightRootFiles) %
        naturalPhaseTimeoutWorkloadAuthority.helperFiles
    );
};

const naturalPhaseTimeoutSourcePath = (index) =>
    `/sources/natural-timeout-phase-${index
        .toString()
        .padStart(3, "0")}.ts`;

const naturalPhaseTimeoutHelperPath = (index) =>
    `/sources/natural-timeout-helper-${index
        .toString()
        .padStart(3, "0")}.d.ts`;

const naturalPhaseTimeoutSourceText = (index) => {
    const authority = naturalPhaseTimeoutWorkloadAuthority;
    const prefix = index.toString().padStart(3, "0");
    const lightRoot = index < authority.lightRootFiles;
    let text = "";
    let helperPrefix = null;
    if (!lightRoot) {
        helperPrefix = naturalPhaseTimeoutHelperIndex(index)
            .toString()
            .padStart(3, "0");
        text +=
            `import type { NaturalTimeoutMatrix${helperPrefix} } from ` +
            `"./natural-timeout-helper-${helperPrefix}";\n`;
    }
    text +=
        `export interface NaturalTimeoutRecord${prefix}<T> { ` +
        "readonly id: number; readonly value: T; }\n";
    text +=
        `export const naturalTimeoutValue${prefix}: ` +
        `NaturalTimeoutRecord${prefix}<number> = { id: ${index}, ` +
        `value: ${index + 1} };\n`;
    if (lightRoot) {
        text +=
            `export type NaturalTimeoutAlias${prefix} = ` +
            `NaturalTimeoutRecord${prefix}<number>;\n`;
    } else {
        text +=
            `const naturalTimeoutProof${prefix} = ["a00", "a00"] ` +
            `satisfies NaturalTimeoutMatrix${helperPrefix};\n`;
        text += `class NaturalTimeoutEmitter${prefix} {\n`;
        for (
            let fieldIndex = 0;
            fieldIndex < authority.heavyPrivateFieldsPerRoot;
            fieldIndex += 1
        ) {
            text +=
                `  #v${fieldIndex.toString().padStart(2, "0")} = ` +
                `${index + fieldIndex};\n`;
        }
        text += "  value(): number { return ";
        for (
            let fieldIndex = 0;
            fieldIndex < authority.heavyPrivateFieldsPerRoot;
            fieldIndex += 1
        ) {
            if (fieldIndex !== 0) {
                text += " + ";
            }
            text += `this.#v${fieldIndex.toString().padStart(2, "0")}`;
        }
        text += "; }\n}\n";
        text +=
            `export const naturalTimeoutEmitterValue${prefix} = ` +
            `new NaturalTimeoutEmitter${prefix}().value();\n`;
    }
    return (
        text +
        `export function selectTimeout${prefix}(item: ` +
        `NaturalTimeoutRecord${prefix}<number>): number { ` +
        "return item.id + item.value; }\n"
    );
};

const naturalPhaseTimeoutHelperText = (index) => {
    const authority = naturalPhaseTimeoutWorkloadAuthority;
    const prefix = index.toString().padStart(3, "0");
    let text = `export type NaturalTimeoutAtom${prefix} = `;
    for (
        let atomIndex = 0;
        atomIndex < authority.helperAtoms;
        atomIndex += 1
    ) {
        if (atomIndex !== 0) {
            text += " | ";
        }
        text += `"a${atomIndex.toString().padStart(2, "0")}"`;
    }
    text += ";\n";
    text +=
        `export type NaturalTimeoutCross${prefix}` +
        "<A extends string, B extends string> = " +
        "A extends A ? B extends B ? readonly [A, B] : never : never;\n";
    text +=
        `export type NaturalTimeoutMatrix${prefix} = ` +
        `NaturalTimeoutCross${prefix}<NaturalTimeoutAtom${prefix}, ` +
        `NaturalTimeoutAtom${prefix}>;\n`;
    return text + " ".repeat(authority.helperWhitespaceBytes);
};

const naturalPhaseTimeoutWorkloadSnapshot = () => {
    const authority = naturalPhaseTimeoutWorkloadAuthority;
    const compilerDirectory = path.dirname(compilerPath);
    const declarations = fs.readdirSync(compilerDirectory)
        .filter((name) => /^lib(?:\..+)?\.d\.ts$/.test(name))
        .sort()
        .map((name) => ({
            path: `/declarations/${name}`,
            text: fs.readFileSync(path.join(compilerDirectory, name), "utf8"),
        }));
    const roots = Array.from(
        { length: authority.rootFiles },
        (_, index) => ({
            path: naturalPhaseTimeoutSourcePath(index),
            text: naturalPhaseTimeoutSourceText(index),
        }),
    );
    const helpers = Array.from(
        { length: authority.helperFiles },
        (_, index) => ({
            path: naturalPhaseTimeoutHelperPath(index),
            text: naturalPhaseTimeoutHelperText(index),
        }),
    );
    return {
        files: roots.concat(helpers, declarations),
        rootNames: roots.map((root) => root.path),
        targetProfile: fixture.targetProfile,
    };
};

const naturalParseTimeoutWorkloadAuthority = Object.freeze({
    ambientDeclarationBytes: 61,
    ambientDeclarationFiles: 1,
    ambientDeclarationTokens: 11,
    aggregatePathCharacters: 4_434,
    commonDirectoryDepth: 8,
    commonDirectorySegmentPrefix: "p",
    declarationBytes: 3_784_758,
    declarationFiles: 108,
    declarationTokens: 86_329,
    firstImportRootIndex: 3,
    inputTransportFrameCount: 418,
    importRootFiles: 3,
    importsPerRoot: 5_700,
    javascriptOutputBytes: 1_524,
    maximumPathCharacters: 65,
    maximumModuleIndex: 17_099,
    maximumRootBytes: 364_832,
    maximumRootTokens: 57_006,
    outputAggregateSha256:
        "c16cf1e19354655321bc7bd13bea9840900f2f2b2bf67793b4d1b926ee42e6b3",
    outputBytes: 3_338,
    outputFiles: 16,
    referenceRootIndex: 2,
    rootBytes: 1_094_708,
    rootFiles: 8,
    rootTokens: 171_048,
    sourcePathDepth: 10,
    sourceMapOutputBytes: 1_814,
    totalBytes: 4_879_527,
    totalFiles: 117,
    totalTokens: 257_388,
});

const naturalParseTimeoutCommonDirectorySegments = Array.from(
    { length: naturalParseTimeoutWorkloadAuthority.commonDirectoryDepth },
    (_, index) =>
        `${naturalParseTimeoutWorkloadAuthority.commonDirectorySegmentPrefix}` +
        index.toString().padStart(2, "0"),
);

const naturalParseTimeoutCommonDirectory =
    `/sources/${naturalParseTimeoutCommonDirectorySegments.join("/")}`;

const naturalParseTimeoutSourcePath = (index) =>
    `${naturalParseTimeoutCommonDirectory}/probe-parse-root-` +
    `${index.toString().padStart(3, "0")}.ts`;

const naturalParseTimeoutSourceText = (index) => {
    const prefix = index.toString().padStart(3, "0");
    let text = "";
    if (
        index === naturalParseTimeoutWorkloadAuthority.referenceRootIndex
    ) {
        text +=
            '/// <reference path="./probe-parse-ambient.d.ts" />\n';
    }
    if (
        index >= naturalParseTimeoutWorkloadAuthority.firstImportRootIndex &&
        index <
            naturalParseTimeoutWorkloadAuthority.firstImportRootIndex +
                naturalParseTimeoutWorkloadAuthority.importRootFiles
    ) {
        for (
            let importIndex = 0;
            importIndex <
            naturalParseTimeoutWorkloadAuthority.importsPerRoot;
            importIndex += 1
        ) {
            const moduleIndex =
                (index -
                    naturalParseTimeoutWorkloadAuthority
                        .firstImportRootIndex) *
                    naturalParseTimeoutWorkloadAuthority.importsPerRoot +
                importIndex;
            assert(
                moduleIndex <=
                    naturalParseTimeoutWorkloadAuthority.maximumModuleIndex,
                "Natural parse-timeout module index escaped authority.",
            );
            const moduleSuffix = moduleIndex
                .toString()
                .padStart(5, "0");
            text +=
                `import type { ProbeType as ProbeType${moduleSuffix} } ` +
                `from "probe-${moduleSuffix}"; `;
        }
    }
    return text + `export const probeValue${prefix} = ${index}; `;
};

const naturalParseTimeoutAmbientDeclarationPath =
    `${naturalParseTimeoutCommonDirectory}/probe-parse-ambient.d.ts`;

const naturalParseTimeoutAmbientDeclarationText =
    'declare module "probe-*" { export type ProbeType = number; } ';

const naturalParseTimeoutWorkloadSnapshot = () => {
    const authority = naturalParseTimeoutWorkloadAuthority;
    const compilerDirectory = path.dirname(compilerPath);
    const declarations = fs.readdirSync(compilerDirectory)
        .filter((name) => /^lib(?:\..+)?\.d\.ts$/.test(name))
        .sort()
        .map((name) => ({
            path: `/declarations/${name}`,
            text: fs.readFileSync(path.join(compilerDirectory, name), "utf8"),
        }));
    const roots = Array.from(
        { length: authority.rootFiles },
        (_, index) => ({
            path: naturalParseTimeoutSourcePath(index),
            text: naturalParseTimeoutSourceText(index),
        }),
    );
    const ambientDeclaration = {
        path: naturalParseTimeoutAmbientDeclarationPath,
        text: naturalParseTimeoutAmbientDeclarationText,
    };
    return {
        files: roots.concat([ambientDeclaration], declarations),
        rootNames: roots.map((root) => root.path),
        targetProfile: fixture.targetProfile,
    };
};

const externalNaturalParseTimeoutWorkloadAuthority = Object.freeze({
    ambientDeclarationBytes: 61,
    ambientDeclarationFiles: 1,
    ambientDeclarationTokens: 11,
    callerBytes: 4_392_959,
    callerFiles: 404,
    callerInventoryFingerprint:
        "334b1a3a940a741105c5222ca6c189c72dfb6c0ea994a72b86013df1fdbf72ef",
    callerPathCharacters: 25_486,
    callerTokens: 150_203,
    commonDirectoryDepth: 8,
    declarationBytes: 3_784_758,
    declarationFiles: 108,
    declarationPathCharacters: 3_857,
    declarationTokens: 86_329,
    firstImportRootIndex: 2,
    graphBytes: 3_431_750,
    graphEdges: 68_635,
    graphEntryIndex: 370,
    graphFiles: 371,
    graphReferenceLineBytes: 50,
    graphTokens: 0,
    graphTriggerRootIndex: 8,
    importRootFiles: 3,
    importsPerRoot: 3_000,
    postSequenceImportRootIndex: 8,
    postSequenceImports: 6_000,
    preSequenceMaximumModuleIndex: 8_999,
    inputTransportFrameCount: 1_598,
    javascriptOutputBytes: 5_962,
    maximumModuleIndex: 14_999,
    maximumPathCharacters: 65,
    maximumRootBytes: 384_082,
    maximumRootTokens: 60_006,
    outputAggregateSha256:
        "18f79093d9a09c3111740e282a3ad642576230c142d6b7d7c61bb9c7950ca038",
    outputBytes: 13_155,
    outputFiles: 64,
    rootBytes: 961_148,
    rootFiles: 32,
    rootTokens: 150_192,
    sequenceTwoCompletedRoots: 8,
    sourceMapOutputBytes: 7_193,
    sourcePathDepth: 10,
    totalBytes: 8_177_717,
    totalFiles: 512,
    totalPathCharacters: 29_343,
    totalTokens: 236_532,
});

const externalNaturalParseTimeoutCommonDirectory =
    `/sources/${Array.from(
        {
            length:
                externalNaturalParseTimeoutWorkloadAuthority
                    .commonDirectoryDepth,
        },
        (_, index) => `p${index.toString().padStart(2, "0")}`,
    ).join("/")}`;

const externalNaturalParseTimeoutSourcePath = (index) =>
    `${externalNaturalParseTimeoutCommonDirectory}/probe-parse-root-` +
    `${index.toString().padStart(3, "0")}.ts`;

const externalNaturalParseTimeoutGraphPath = (index) =>
    `${externalNaturalParseTimeoutCommonDirectory}/probe-parse-xx` +
    `${index.toString().padStart(3, "0")}.d.ts`;

const externalNaturalParseTimeoutGraphReferenceLine = (index) =>
    `/// <reference path="./probe-parse-xx` +
    `${index.toString().padStart(3, "0")}.d.ts" />\n`;

const externalNaturalParseTimeoutAmbientDeclarationPath =
    `${externalNaturalParseTimeoutCommonDirectory}/probe-parse-ambient.d.ts`;

const externalNaturalParseTimeoutAmbientDeclarationText =
    'declare module "probe-*" { export type ProbeType = number; } ';

const externalNaturalParseTimeoutSourceText = (index) => {
    const authority = externalNaturalParseTimeoutWorkloadAuthority;
    const prefix = index.toString().padStart(3, "0");
    let text = "";
    if (index === 0) {
        text +=
            '/// <reference path="./probe-parse-ambient.d.ts" />\n';
    }
    if (index === authority.graphTriggerRootIndex) {
        text += externalNaturalParseTimeoutGraphReferenceLine(
            authority.graphEntryIndex,
        );
    }
    let firstModuleIndex = -1;
    let importCount = 0;
    if (
        index >= authority.firstImportRootIndex &&
        index <
            authority.firstImportRootIndex + authority.importRootFiles
    ) {
        firstModuleIndex =
            (index - authority.firstImportRootIndex) *
            authority.importsPerRoot;
        importCount = authority.importsPerRoot;
    } else if (index === authority.postSequenceImportRootIndex) {
        firstModuleIndex = authority.preSequenceMaximumModuleIndex + 1;
        importCount = authority.postSequenceImports;
    }
    for (
        let importIndex = 0;
        importIndex < importCount;
        importIndex += 1
    ) {
        const moduleIndex = firstModuleIndex + importIndex;
        assert(
            moduleIndex <= authority.maximumModuleIndex,
            "External natural parse module index escaped authority.",
        );
        const moduleSuffix = moduleIndex.toString().padStart(5, "0");
        text +=
            `import type { ProbeType as ProbeType${moduleSuffix} } ` +
            `from "probe-${moduleSuffix}"; `;
    }
    return text + `export const probeValue${prefix} = ${index}; `;
};

const externalNaturalParseTimeoutGraphText = (index) => {
    const authority = externalNaturalParseTimeoutWorkloadAuthority;
    let text = "";
    for (let target = 0; target < index; target += 1) {
        text += externalNaturalParseTimeoutGraphReferenceLine(target);
    }
    return text;
};

const externalNaturalParseTimeoutWorkloadSnapshot = () => {
    const authority = externalNaturalParseTimeoutWorkloadAuthority;
    const compilerDirectory = path.dirname(compilerPath);
    const declarations = fs
        .readdirSync(compilerDirectory)
        .filter((name) => /^lib(?:\..+)?\.d\.ts$/.test(name))
        .sort()
        .map((name) => ({
            path: `/declarations/${name}`,
            text: fs.readFileSync(path.join(compilerDirectory, name), "utf8"),
        }));
    const roots = Array.from(
        { length: authority.rootFiles },
        (_, index) => ({
            path: externalNaturalParseTimeoutSourcePath(index),
            text: externalNaturalParseTimeoutSourceText(index),
        }),
    );
    const ambientDeclaration = {
        path: externalNaturalParseTimeoutAmbientDeclarationPath,
        text: externalNaturalParseTimeoutAmbientDeclarationText,
    };
    const graph = Array.from(
        { length: authority.graphFiles },
        (_, index) => ({
            path: externalNaturalParseTimeoutGraphPath(index),
            text: externalNaturalParseTimeoutGraphText(index),
        }),
    );
    return {
        files: roots.concat([ambientDeclaration], graph, declarations),
        rootNames: roots.map((root) => root.path),
        targetProfile: fixture.targetProfile,
    };
};

const naturalEmitTimeoutWorkloadAuthority = Object.freeze({
    aggregatePathCharacters: 4_145,
    declarationBytes: 3_784_758,
    declarationFiles: 108,
    declarationTokens: 86_329,
    heavyRootFiles: 6,
    inputTransportFrameCount: 400,
    javascriptOutputBytes: 769_510,
    lightRootFiles: 2,
    maximumPathCharacters: 46,
    maximumRootBytes: 9_502,
    maximumRootTokens: 4_049,
    optionalChainsPerHeavyRoot: 18,
    optionalChainDepth: 110,
    outputAggregateSha256:
        "e7eac5f110b4dff7bbb732ea47c3f21c083b44fa215b740895a8e8bac9ba4fa6",
    outputBytes: 965_714,
    outputFiles: 16,
    rootBytes: 57_074,
    rootFiles: 8,
    rootTokens: 24_310,
    sourceMapOutputBytes: 196_204,
    sourcePathDepth: 2,
    totalBytes: 3_841_832,
    totalFiles: 116,
    totalTokens: 110_639,
});

const naturalEmitTimeoutSourcePath = (index) =>
    `/sources/natural-emit-timeout-${index
        .toString()
        .padStart(3, "0")}.ts`;

const naturalEmitTimeoutSourceText = (index) => {
    const authority = naturalEmitTimeoutWorkloadAuthority;
    const prefix = index.toString().padStart(3, "0");
    if (index < authority.lightRootFiles) {
        return `export const e${prefix}: number = ${index};\n`;
    }
    let text = "declare const q: any;\n";
    text += `export const e${prefix}: number = [\n`;
    for (
        let chainIndex = 0;
        chainIndex < authority.optionalChainsPerHeavyRoot;
        chainIndex += 1
    ) {
        text += "  q";
        for (
            let depthIndex = 0;
            depthIndex < authority.optionalChainDepth;
            depthIndex += 1
        ) {
            text += `?.p${depthIndex.toString(36)}`;
        }
        text += ` ?? ${chainIndex},\n`;
    }
    text += "].length;\n";
    return text;
};

const naturalEmitTimeoutWorkloadSnapshot = () => {
    const authority = naturalEmitTimeoutWorkloadAuthority;
    const compilerDirectory = path.dirname(compilerPath);
    const declarations = fs.readdirSync(compilerDirectory)
        .filter((name) => /^lib(?:\..+)?\.d\.ts$/.test(name))
        .sort()
        .map((name) => ({
            path: `/declarations/${name}`,
            text: fs.readFileSync(path.join(compilerDirectory, name), "utf8"),
        }));
    const roots = Array.from(
        { length: authority.rootFiles },
        (_, index) => ({
            path: naturalEmitTimeoutSourcePath(index),
            text: naturalEmitTimeoutSourceText(index),
        }),
    );
    return {
        files: roots.concat(declarations),
        rootNames: roots.map((root) => root.path),
        targetProfile: fixture.targetProfile,
    };
};

const frameBytes = 64 * 1024;
const profileAuthorityFields = Object.freeze({
    configKind: fixture.profileAuthority.configKind,
    configSchemaRevision: fixture.profileAuthority.configSchemaRevision,
    profileRevision: fixture.profileAuthority.profileRevision,
    profileSchemaRevision: fixture.profileAuthority.profileSchemaRevision,
});
const rhinoRevision2ProfileAuthorityFields = Object.freeze({
    configKind: "none",
    configSchemaRevision: 1,
    profileRevision: 2,
    profileSchemaRevision: 1,
    runtimeCapabilitiesFingerprint:
        "49374b84060ce634472aa2db34d960892501adebb7a6b7ba3a767018a81b3d25",
});
const rhinoRevision2EffectiveOptions = Object.freeze({
    allowJs: false,
    checkJs: false,
    ignoreDeprecations: "6.0",
    incremental: false,
    inlineSourceMap: false,
    inlineSources: false,
    jsx: 2,
    jsxFactory: "__autojs6Tsx",
    jsxFragmentFactory: "__autojs6TsxFragment",
    module: 1,
    moduleResolution: 2,
    newLine: 1,
    noEmitOnError: true,
    noLib: false,
    outDir: "/outputs",
    rootDir: "/sources",
    skipLibCheck: false,
    sourceMap: true,
    strict: true,
    target: 5,
    types: [],
});

const tsxDeclarationText =
    "declare namespace UI { interface View { setText(text: string): void; } }\n" +
    "declare namespace AutoJs6Tsx {\n" +
    "  type Scalar = string | number | boolean;\n" +
    "  type TextChild = string | number | boolean | null | undefined;\n" +
    "  type ClickHandler = (view: UI.View) => void;\n" +
    "  interface BaseAttributes { id?: string; padding?: Scalar; gravity?: Scalar; onClick?: ClickHandler; }\n" +
    "  interface Element { readonly __autojs6TsxElement: unique symbol; }\n" +
    "  type Child = Element | readonly Child[] | TextChild;\n" +
    "  interface ContainerAttributes extends BaseAttributes { children?: Child | readonly Child[]; }\n" +
    "  interface TextAttributes extends BaseAttributes { children?: TextChild | readonly TextChild[]; text?: Scalar; textSize?: Scalar; }\n" +
    "}\n" +
    "declare const __autojs6TsxFragment: unique symbol;\n" +
    "declare function __autojs6Tsx(type: string | typeof __autojs6TsxFragment, props: AutoJs6Tsx.BaseAttributes | null, ...children: AutoJs6Tsx.Child[]): AutoJs6Tsx.Element;\n" +
    "declare namespace JSX {\n" +
    "  type Element = AutoJs6Tsx.Element;\n" +
    "  interface ElementChildrenAttribute { children: {}; }\n" +
    "  interface IntrinsicElements { vertical: AutoJs6Tsx.ContainerAttributes; text: AutoJs6Tsx.TextAttributes; button: AutoJs6Tsx.TextAttributes; }\n" +
    "}\n";

const tsxSnapshot = (
    mainSource =
        "const view = (<>\n" +
        "  <vertical id=\"root\">\n" +
        "    <text id=\"title\" text={\"ready\"} />\n" +
        "    <button id=\"action\" text={7} onClick={() => \"done\"} />\n" +
        "  </vertical>\n" +
        "</>);\n" +
        "void view;\n",
) => ({
    effectiveOptions: rhinoRevision2EffectiveOptions,
    entryPath: "/sources/main.tsx",
    files: fixture.commonFiles
        .filter((file) => file.path.startsWith("/declarations/"))
        .concat([
            {
                path: "/declarations/rhino/index.d.ts",
                text: tsxDeclarationText,
            },
            {
                path: "/sources/main.tsx",
                text: mainSource,
            },
        ]),
    profileAuthority: rhinoRevision2ProfileAuthorityFields,
    rootNames: ["/sources/main.tsx"],
    targetProfile: "rhino",
});

const digestBytes = (bytes) =>
    crypto.createHash("sha256").update(bytes).digest("hex");

const inputArchiveDomain = (filePath) =>
    filePath.startsWith("/declarations/") ? "declaration" : "source";
const inputArchiveAuthorityForFiles = (
    files,
    inputQuotaRevision = undefined,
) =>
    projectInputAuthority({
        fileRecords: files.map((file) =>
            inputArchiveRecord(
                inputArchiveDomain(file.path),
                file.path,
                Buffer.isBuffer(file.bytes)
                    ? file.bytes
                    : Buffer.from(file.text, "utf8"),
            ),
        ),
        ...(inputQuotaRevision === undefined ? {} : { inputQuotaRevision }),
    });

const verifyBoundProfile = (
    result,
    expectedAuthority = profileAuthorityFields,
    expectedOptions = fixture.effectiveOptions,
) => {
    assert(
        result.targetProfile === fixture.targetProfile &&
            result.profileSchemaRevision ===
                expectedAuthority.profileSchemaRevision &&
            result.profileRevision === expectedAuthority.profileRevision &&
            result.configSchemaRevision ===
                expectedAuthority.configSchemaRevision &&
            result.configKind === expectedAuthority.configKind,
        "Worker result profile authority does not match the golden.",
    );
    assert(
        JSON.stringify(result.options) === JSON.stringify(expectedOptions),
        "Worker result effective options do not match the complete golden.",
    );
};

const dispatchWorkerMessage = async (data) => {
    assert(
        messages.length === 0,
        "Desktop Worker response queue retained an earlier frame.",
    );
    assert(
        messageObservations.length === 0,
        "Desktop Worker timing queue retained an earlier frame.",
    );
    await self.onmessage({ data });
    const responses = messages.splice(0);
    lastDispatchMessageObservations = messageObservations.splice(0);
    assert(
        responses.length === lastDispatchMessageObservations.length,
        "Desktop Worker response/timing queues diverged.",
    );
    return responses;
};

const expectInputAck = (responses, requestId, sequence) => {
    const failure = responses.find(
        (message) =>
            message.kind === "projectError" &&
            message.requestId === requestId,
    );
    assert(
        !failure,
        `Worker rejected input sequence ${sequence}: ${failure?.message}`,
    );
    assert(
        responses.some(
            (message) =>
                message.kind === "projectInputAck" &&
                message.requestId === requestId &&
                message.sequence === sequence,
        ),
        `Worker did not ACK input sequence ${sequence}.`,
    );
};

const compile = async (
    requestId,
    project,
    {
        inputQuotaRevision = null,
        projectSessionKey = null,
        workerSpinMilliseconds = 0,
        workerSpinPhase = null,
    } = {},
) => {
    const requestStartedAtElapsedRealtimeNanos = process.hrtime.bigint();
    const encodedFiles = project.files.map((file) => ({
        byteLength: Buffer.byteLength(file.text, "utf8"),
        path: file.path,
        text: file.text,
    }));
    const totalBytes = encodedFiles.reduce(
        (total, file) => total + file.byteLength,
        0,
    );
    let sequence = 0;
    const selectedWorkerSpinPhase =
        workerSpinPhase ??
        (workerSpinMilliseconds === 0 ? "none" : "parse");
    const usesHistoricalExpandedCapacity =
        encodedFiles.length > 512 ||
        totalBytes > 8 * 1024 * 1024 ||
        encodedFiles.some((file) => file.byteLength > 8 * 1024 * 1024);
    const authority = inputArchiveAuthorityForFiles(
        encodedFiles,
        inputQuotaRevision ??
            (usesHistoricalExpandedCapacity
                ? TEST_ONLY_EXPANDED_INPUT_QUOTA_REVISION
                : undefined),
    );
    const expectedProfileAuthority =
        project.profileAuthority ?? profileAuthorityFields;
    const revisionTwoStartAuthority = Object.prototype.hasOwnProperty.call(
        expectedProfileAuthority,
        "runtimeCapabilitiesFingerprint",
    )
        ? {
              entryPath: project.entryPath,
              runtimeCapabilitiesFingerprint:
                  expectedProfileAuthority.runtimeCapabilitiesFingerprint,
          }
        : {};
    const startResponses = await dispatchWorkerMessage({
            ...authority,
            fileCount: encodedFiles.length,
            kind: "projectInputStart",
            requestId,
            sequence,
            configKind: expectedProfileAuthority.configKind,
            configSchemaRevision: expectedProfileAuthority.configSchemaRevision,
            profileRevision: expectedProfileAuthority.profileRevision,
            profileSchemaRevision: expectedProfileAuthority.profileSchemaRevision,
            ...revisionTwoStartAuthority,
            ...(projectSessionKey === null
                ? {}
                : {
                      projectSessionKey,
                      projectSessionRevision: 1,
                  }),
            targetProfile: project.targetProfile,
            totalBytes,
            workerSpinMilliseconds,
            workerSpinPhase: selectedWorkerSpinPhase,
        });
    expectInputAck(
        startResponses,
        requestId,
        sequence,
    );
    sequence += 1;

    for (let fileIndex = 0; fileIndex < encodedFiles.length; fileIndex += 1) {
        const file = encodedFiles[fileIndex];
        let fileBytes = Buffer.from(file.text, "utf8");
        assert(
            fileBytes.length === file.byteLength,
            `Desktop fixture byte length changed for ${file.path}.`,
        );
        expectInputAck(
            await dispatchWorkerMessage({
                byteLength: file.byteLength,
                fileIndex,
                kind: "projectInputFileStart",
                path: file.path,
                requestId,
                sequence,
                sha256: digestBytes(fileBytes),
            }),
            requestId,
            sequence,
        );
        sequence += 1;

        let chunkIndex = 0;
        for (
            let offset = 0;
            offset < fileBytes.length;
            offset += frameBytes
        ) {
            const chunk = fileBytes.subarray(
                offset,
                Math.min(offset + frameBytes, fileBytes.length),
            );
            expectInputAck(
                await dispatchWorkerMessage({
                    chunkIndex,
                    data: chunk.toString("base64"),
                    fileIndex,
                    kind: "projectInputFileChunk",
                    requestId,
                    sequence,
                }),
                requestId,
                sequence,
            );
            sequence += 1;
            chunkIndex += 1;
        }
        expectInputAck(
            await dispatchWorkerMessage({
                chunkCount: chunkIndex,
                fileIndex,
                kind: "projectInputFileEnd",
                requestId,
                sequence,
            }),
            requestId,
            sequence,
        );
        sequence += 1;
        fileBytes.fill(0);
        fileBytes = null;
    }

    const endStartedAt = Date.now();
    const endResponses = await dispatchWorkerMessage({
        fileCount: encodedFiles.length,
        kind: "projectInputEnd",
        requestId,
        sequence,
        totalBytes,
    });
    const endMessageObservations = lastDispatchMessageObservations.slice();
    const endElapsedMilliseconds = Date.now() - endStartedAt;
    expectInputAck(endResponses, requestId, sequence);
    const phaseEventsBeforeResult = [...startResponses, ...endResponses]
        .filter(
            (message) =>
                message.kind === "projectPhase" &&
                message.requestId === requestId,
        )
        .map((message) => `${message.phase}:${message.state}`);
    assert(
        JSON.stringify(phaseEventsBeforeResult) === JSON.stringify([
            "ingress:started",
            "ingress:completed",
            "parse:started",
            "parse:completed",
            "semantic:started",
            "semantic:completed",
            "emit:started",
        ]),
        `Worker reported an invalid pre-result phase sequence: ${phaseEventsBeforeResult}.`,
    );
    const phaseProgressEvents = [...startResponses, ...endResponses]
        .filter(
            (message) =>
                message.kind === "projectPhaseProgress" &&
                message.requestId === requestId,
        )
        .map((message) => ({
            completedUnits: message.completedUnits,
            phase: message.phase,
            sequence: message.sequence,
            totalUnits: message.totalUnits,
        }));
    const start = endResponses.find(
        (message) =>
            message.kind === "projectResultStart" &&
            message.requestId === requestId,
    );
    assert(start, "Worker did not begin the framed project result.");
    assert(
        start.chunkCount === Math.ceil(start.byteLength / frameBytes),
        "Worker result start has an inconsistent chunk count.",
    );
    assert(
        endResponses.every(
            (message) => message.kind !== "projectResultChunk",
        ),
        "Worker published result bytes before the initial native ACK.",
    );
    const chunks = [];
    const resultResponses = [];
    const resultMessageObservations = [];
    for (let index = -1; index < start.chunkCount; index += 1) {
        const responses = await dispatchWorkerMessage({
            kind: "projectResultAck",
            requestId,
            sequence: index,
        });
        resultResponses.push(...responses);
        resultMessageObservations.push(...lastDispatchMessageObservations);
        const failure = responses.find(
            (message) =>
                message.kind === "projectError" &&
                message.requestId === requestId,
        );
        assert(!failure, `Worker result framing failed: ${failure?.message}`);
        if (index + 1 < start.chunkCount) {
            const chunk = responses.find(
                (message) =>
                    message.kind === "projectResultChunk" &&
                    message.requestId === requestId,
            );
            assert(chunk, `Worker did not publish result chunk ${index + 1}.`);
            assert(
                chunk.index === index + 1,
                "Worker result chunk index is out of sequence.",
            );
            assert(
                responses.filter(
                    (message) => message.kind === "projectResultChunk",
                ).length === 1,
                "Worker published more than one chunk for one ACK.",
            );
            assert(
                responses.every(
                    (message) =>
                        message.kind !== "projectPhase" ||
                        message.phase !== "emit" ||
                        message.state !== "completed",
                ),
                "Worker completed emit before the final result ACK.",
            );
            chunks.push(Buffer.from(chunk.data, "base64"));
        } else {
            const end = responses.find(
                (message) =>
                    message.kind === "projectResultEnd" &&
                    message.requestId === requestId,
            );
            assert(end, "Worker did not end the framed project result.");
            assert(end.byteLength === start.byteLength, "Result size changed.");
            assert(end.sha256 === start.sha256, "Result digest changed.");
            assert(end.chunkCount === start.chunkCount, "Result count changed.");
            const terminalEvents = responses
                .filter(
                    (message) =>
                        message.requestId === requestId &&
                        (
                            message.kind === "projectResultEnd" ||
                            (
                                message.kind === "projectPhase" &&
                                message.phase === "emit" &&
                                message.state === "completed"
                            )
                        ),
                )
                .map((message) =>
                    message.kind === "projectResultEnd"
                        ? message.kind
                        : `${message.phase}:${message.state}`,
                );
            assert(
                JSON.stringify(terminalEvents) ===
                    JSON.stringify(["emit:completed", "projectResultEnd"]),
                `Worker ended result transport out of sequence: ${terminalEvents}.`,
            );
        }
    }

    assert(
        (
            await dispatchWorkerMessage({
                kind: "projectResultAck",
                requestId,
                sequence: 0,
            })
        ).length === 0,
        "Worker did not ignore a late result ACK.",
    );

    const phaseEvents = [
        ...startResponses,
        ...endResponses,
        ...resultResponses,
    ]
        .filter(
            (message) =>
                message.kind === "projectPhase" &&
                message.requestId === requestId,
        )
        .map((message) => `${message.phase}:${message.state}`);
    assert(
        JSON.stringify(phaseEvents) === JSON.stringify([
            "ingress:started",
            "ingress:completed",
            "parse:started",
            "parse:completed",
            "semantic:started",
            "semantic:completed",
            "emit:started",
            "emit:completed",
        ]),
        `Worker reported an invalid project phase sequence: ${phaseEvents}.`,
    );
    const phaseTimingObservations = [
        ...endMessageObservations,
        ...resultMessageObservations,
    ];
    const phaseTimingEvidence = ["parse", "semantic", "emit"].map((phase) => {
        const sequenceTwo = phaseTimingObservations.find(
            (observation) =>
                observation.message.kind === "projectPhaseProgress" &&
                observation.message.requestId === requestId &&
                observation.message.phase === phase &&
                observation.message.sequence === 2,
        );
        const completed = phaseTimingObservations.find(
            (observation) =>
                observation.message.kind === "projectPhase" &&
                observation.message.requestId === requestId &&
                observation.message.phase === phase &&
                observation.message.state === "completed",
        );
        if (!sequenceTwo || !completed) {
            return null;
        }
        const sequenceTwoElapsedRealtimeNanosSinceProjectStart =
            sequenceTwo.observedAtElapsedRealtimeNanos -
            requestStartedAtElapsedRealtimeNanos;
        const completedElapsedRealtimeNanosSinceProjectStart =
            completed.observedAtElapsedRealtimeNanos -
            requestStartedAtElapsedRealtimeNanos;
        assert(
            sequenceTwoElapsedRealtimeNanosSinceProjectStart > 0n &&
                completedElapsedRealtimeNanosSinceProjectStart >
                    sequenceTwoElapsedRealtimeNanosSinceProjectStart,
            `Worker exposed an invalid ${phase} deadline window.`,
        );
        return {
            completedElapsedRealtimeNanosSinceProjectStart,
            phase,
            sequenceTwoElapsedRealtimeNanosSinceProjectStart,
        };
    }).filter((timing) => timing !== null);

    const resultBytes = Buffer.concat(chunks);
    assert(
        resultBytes.length === start.byteLength,
        "Framed result byte length mismatch.",
    );
    assert(
        digestBytes(resultBytes) === start.sha256,
        "Framed result digest mismatch.",
    );
    const result = JSON.parse(
        new TextDecoder("utf-8", { fatal: true }).decode(resultBytes),
    );
    verifyBoundProfile(
        result,
        expectedProfileAuthority,
        project.effectiveOptions ?? fixture.effectiveOptions,
    );
    return {
        endElapsedMilliseconds,
        phaseProgressEvents,
        phaseTimingEvidence,
        result,
    };
};

const verifySuccess = (result) => {
    assert(result.tsVersion === expectedVersion, "TypeScript version mismatch.");
    assert(
        result.compilerApi === "createIncrementalProgram",
        "Worker did not use createIncrementalProgram.",
    );
    assert(
        !result.emitSkipped,
        `Valid project emit was skipped: ${JSON.stringify(result.diagnostics)}`,
    );
    assert(
        result.diagnostics.length === 0,
        `Valid project has diagnostics: ${JSON.stringify(result.diagnostics)}`,
    );
    const expected = new Map(
        fixture.success.outputs.map((output) => [output.path, output]),
    );
    assert(
        result.outputs.length === expected.size,
        "Valid project output count mismatch.",
    );
    for (const output of result.outputs) {
        const golden = expected.get(output.path);
        assert(golden, `Unexpected output: ${output.path}`);
        assert(
            Buffer.byteLength(output.text, "utf8") === golden.bytes,
            `Output size mismatch: ${output.path}`,
        );
        assert(
            output.sha256 === golden.sha256,
            `Output digest mismatch: ${output.path}`,
        );
        if (output.path.endsWith(".map")) {
            const sourceMap = JSON.parse(output.text);
            assert(sourceMap.version === 3, "Source Map version mismatch.");
            assert(sourceMap.mappings.length > 0, "Source Map mappings are empty.");
            assert(
                !Object.prototype.hasOwnProperty.call(
                    sourceMap,
                    "sourcesContent",
                ),
                "Source Map contains inline sources.",
            );
        }
    }
};

const verifyTsxSuccess = (result) => {
    assert(result.tsVersion === expectedVersion, "TSX TypeScript version mismatch.");
    assert(
        result.compilerApi === "createIncrementalProgram",
        "TSX project did not use createIncrementalProgram.",
    );
    assert(
        !result.emitSkipped && result.diagnostics.length === 0,
        `TSX project did not emit cleanly: ${JSON.stringify(result.diagnostics)}`,
    );
    const outputs = new Map(result.outputs.map((output) => [output.path, output]));
    assert(
        outputs.size === 2 &&
            outputs.has("/outputs/main.js") &&
            outputs.has("/outputs/main.js.map"),
        `TSX project emitted an unexpected output inventory: ${Array.from(outputs.keys())}.`,
    );
    const javaScript = outputs.get("/outputs/main.js").text;
    assert(
        javaScript.includes("__autojs6Tsx(__autojs6TsxFragment, null") &&
            javaScript.includes('__autojs6Tsx("vertical"') &&
            javaScript.includes('__autojs6Tsx("text"') &&
            javaScript.includes('__autojs6Tsx("button"') &&
            javaScript.includes("onClick: () => \"done\"") &&
            !javaScript.includes("<vertical"),
        `TSX lowering did not use the controlled AutoJs6 factories: ${javaScript}`,
    );
    const sourceMap = JSON.parse(outputs.get("/outputs/main.js.map").text);
    assert(sourceMap.version === 3, "TSX Source Map version mismatch.");
    assert(sourceMap.mappings.length > 0, "TSX Source Map mappings are empty.");
    assert(
        sourceMap.sources.length === 1 &&
            sourceMap.sources[0].endsWith("/sources/main.tsx"),
        `TSX Source Map lost its .tsx source identity: ${sourceMap.sources}.`,
    );
    assert(
        !Object.prototype.hasOwnProperty.call(sourceMap, "sourcesContent"),
        "TSX Source Map unexpectedly contains inline source text.",
    );
};

const verifyTsxAttributeDiagnostic = (result) => {
    assert(result.emitSkipped, "Invalid TSX attribute unexpectedly emitted JavaScript.");
    assert(result.outputs.length === 0, "Invalid TSX attribute retained executable output.");
    assert(
        result.diagnostics.length === 1,
        `Invalid TSX attribute produced an unstable diagnostic set: ${JSON.stringify(result.diagnostics)}.`,
    );
    const diagnostic = result.diagnostics[0];
    assert(
        diagnostic.category === ts.DiagnosticCategory.Error &&
            diagnostic.code === 2322 &&
            diagnostic.phase === "semantic" &&
            diagnostic.sourcePath === "/sources/main.tsx" &&
            diagnostic.line === 1 &&
            diagnostic.message.includes("texxt"),
        `Invalid TSX attribute diagnostic drifted: ${JSON.stringify(diagnostic)}.`,
    );
};

const countSourceTokens = (text) => {
    const scanner = global.ts.createScanner(
        global.ts.ScriptTarget.ES2018,
        true,
        global.ts.LanguageVariant.Standard,
        text,
    );
    let count = 0;
    while (scanner.scan() !== global.ts.SyntaxKind.EndOfFileToken) {
        count += 1;
    }
    return count;
};

const verifyP3bB2gNaturalPhaseProgressWorkload = async () => {
    const authority = naturalPhaseWorkloadAuthority;
    const project = naturalPhaseWorkloadSnapshot();
    const declarations = project.files.filter((file) =>
        file.path.startsWith("/declarations/"),
    );
    const roots = project.files.filter((file) =>
        file.path.startsWith("/sources/"),
    );
    const byteLength = (file) => Buffer.byteLength(file.text, "utf8");
    const tokenCount = (file) => countSourceTokens(file.text);
    const declarationBytes = declarations.reduce(
        (total, file) => total + byteLength(file),
        0,
    );
    const declarationTokens = declarations.reduce(
        (total, file) => total + tokenCount(file),
        0,
    );
    const rootBytes = roots.reduce(
        (total, file) => total + byteLength(file),
        0,
    );
    const rootTokens = roots.reduce(
        (total, file) => total + tokenCount(file),
        0,
    );
    const maximumRootTokens = Math.max(...roots.map(tokenCount));

    assert(
        declarations.length === authority.declarationFiles &&
            declarationBytes === authority.declarationBytes &&
            declarationTokens === authority.declarationTokens,
        "Natural-phase trusted declaration inventory drifted.",
    );
    assert(
        roots.length === authority.rootFiles &&
            rootBytes === authority.rootBytes &&
            rootTokens === authority.rootTokens &&
            maximumRootTokens === authority.maximumRootTokens,
        "Natural-phase source generator drifted.",
    );
    assert(
        project.files.length === authority.totalFiles &&
            declarationBytes + rootBytes === authority.totalBytes &&
            declarationTokens + rootTokens === authority.totalTokens,
        "Natural-phase aggregate input accounting drifted.",
    );
    assert(
        authority.totalFiles <= 512 &&
            authority.totalBytes <= 8 * 1024 * 1024 &&
            Math.max(...project.files.map(byteLength)) <= 8 * 1024 * 1024,
        "Natural-phase workload escaped ordinary production admission.",
    );
    assert(
        authority.totalTokens <= Math.floor(
            p3bWorkerLimits.maximumProjectTokens * 0.9,
        ),
        "Natural-phase workload lost its frozen aggregate token headroom.",
    );

    const { phaseProgressEvents, phaseTimingEvidence, result } = await compile(
        "desktop-p3b-b2g-natural-phase-progress",
        project,
    );
    const expectedProgress = [
        {
            completedUnits: 24,
            phase: "parse",
            sequence: 1,
            totalUnits: 192,
        },
        {
            completedUnits: 48,
            phase: "parse",
            sequence: 2,
            totalUnits: 192,
        },
        {
            completedUnits: 24,
            phase: "semantic",
            sequence: 1,
            totalUnits: 192,
        },
        {
            completedUnits: 48,
            phase: "semantic",
            sequence: 2,
            totalUnits: 192,
        },
        {
            completedUnits: 48,
            phase: "emit",
            sequence: 1,
            totalUnits: 384,
        },
        {
            completedUnits: 96,
            phase: "emit",
            sequence: 2,
            totalUnits: 384,
        },
    ];
    assert(
        JSON.stringify(phaseProgressEvents) ===
            JSON.stringify(expectedProgress),
        "Natural-phase Worker progress did not come from the frozen real work units.",
    );
    assert(
        !result.emitSkipped && result.diagnostics.length === 0,
        "Natural-phase ordinary baseline did not compile cleanly.",
    );
    assert(
        result.outputs.length === authority.outputFiles,
        "Natural-phase ordinary baseline output count drifted.",
    );

    const sortedOutputs = result.outputs
        .slice()
        .sort((left, right) =>
            left.path < right.path ? -1 : left.path > right.path ? 1 : 0,
        );
    const javascriptOutputs = sortedOutputs.filter((output) =>
        output.path.endsWith(".js"),
    );
    const sourceMapOutputs = sortedOutputs.filter((output) =>
        output.path.endsWith(".map"),
    );
    const outputBytes = (outputs) =>
        outputs.reduce(
            (total, output) =>
                total + Buffer.byteLength(output.text, "utf8"),
            0,
        );
    const aggregate = crypto.createHash("sha256");
    for (const output of sortedOutputs) {
        aggregate.update(Buffer.from(output.path, "utf8"));
        aggregate.update(Buffer.from([0]));
        aggregate.update(Buffer.from(output.text, "utf8"));
    }
    assert(
        javascriptOutputs.length === authority.rootFiles &&
            sourceMapOutputs.length === authority.rootFiles &&
            outputBytes(javascriptOutputs) ===
                authority.javascriptOutputBytes &&
            outputBytes(sourceMapOutputs) ===
                authority.sourceMapOutputBytes &&
            outputBytes(sortedOutputs) === authority.outputBytes,
        "Natural-phase ordinary baseline output accounting drifted.",
    );
    assert(
        authority.outputBytes <= Math.floor(1024 * 1024 * 0.8),
        "Natural-phase output lost its frozen ordinary-budget headroom.",
    );
    assert(
        aggregate.digest("hex") === authority.outputAggregateSha256,
        "Natural-phase ordinary baseline aggregate output digest drifted.",
    );
    return {
        expectedProgress,
        phaseProgressEvents,
        phaseTimingEvidence,
        result,
    };
};

const p3bB2hNaturalPhaseCalibrationBaselineRuns = 3;
const p3bB2hNaturalPhaseCalibrationMarginMilliseconds = 100;
const p3bB2hNaturalPhaseFaultAttempts = 1;
const nanosecondsPerMillisecond = 1_000_000n;

const naturalPhaseOutputAggregateSha256 = (outputs) => {
    const aggregate = crypto.createHash("sha256");
    for (const output of outputs
        .slice()
        .sort((left, right) =>
            left.path < right.path ? -1 : left.path > right.path ? 1 : 0,
        )) {
        aggregate.update(Buffer.from(output.path, "utf8"));
        aggregate.update(Buffer.from([0]));
        aggregate.update(Buffer.from(output.text, "utf8"));
    }
    return aggregate.digest("hex");
};

const calibrateP3bB2hNaturalSemanticRequestStartDeadline = (
    timingRuns,
    marginMilliseconds,
) => {
    assert(
        timingRuns.length === p3bB2hNaturalPhaseCalibrationBaselineRuns,
        "Natural-phase calibration did not use exactly three successful baselines.",
    );
    const expectedPhases = ["semantic"];
    const marginNanos =
        BigInt(marginMilliseconds) * nanosecondsPerMillisecond;
    return expectedPhases.map((phase) => {
        const phaseRuns = timingRuns.map((run) => {
            const timing = run.find((candidate) => candidate.phase === phase);
            assert(
                timing,
                `Natural-phase calibration run omitted ${phase}.`,
            );
            return timing;
        });
        const rawLowerNanos = phaseRuns.reduce(
            (maximum, timing) =>
                timing.sequenceTwoElapsedRealtimeNanosSinceProjectStart >
                maximum
                    ? timing.sequenceTwoElapsedRealtimeNanosSinceProjectStart
                    : maximum,
            0n,
        );
        const rawUpperNanos = phaseRuns.reduce(
            (minimum, timing) =>
                timing.completedElapsedRealtimeNanosSinceProjectStart < minimum
                    ? timing.completedElapsedRealtimeNanosSinceProjectStart
                    : minimum,
            phaseRuns[0].completedElapsedRealtimeNanosSinceProjectStart,
        );
        assert(
            rawUpperNanos - rawLowerNanos >= marginNanos * 2n,
            `Natural ${phase} three-baseline intersection has less than ` +
                `${marginMilliseconds} ms on each side of a fixed ` +
                "request-start deadline.",
        );
        const minimumTimeoutMilliseconds =
            Number(
                (rawLowerNanos +
                    marginNanos +
                    nanosecondsPerMillisecond -
                    1n) /
                    nanosecondsPerMillisecond,
            );
        const maximumTimeoutMilliseconds = Number(
            (rawUpperNanos - marginNanos) / nanosecondsPerMillisecond,
        );
        assert(
            minimumTimeoutMilliseconds <= maximumTimeoutMilliseconds,
            `Natural ${phase} has no whole-millisecond calibrated deadline.`,
        );
        const timeoutMilliseconds = Math.floor(
            (minimumTimeoutMilliseconds + maximumTimeoutMilliseconds) / 2,
        );
        const timeoutNanos =
            BigInt(timeoutMilliseconds) * nanosecondsPerMillisecond;
        assert(
            timeoutNanos - rawLowerNanos >= marginNanos &&
                rawUpperNanos - timeoutNanos >= marginNanos,
            `Natural ${phase} midpoint lost its two ` +
                `${marginMilliseconds} ms margins.`,
        );
        return {
            maximumTimeoutMilliseconds,
            minimumTimeoutMilliseconds,
            phase,
            timeoutMilliseconds,
        };
    });
};

const verifyP3bB2hNaturalSemanticDeadlineCalibration = async () => {
    const authority = naturalPhaseTimeoutWorkloadAuthority;
    const project = naturalPhaseTimeoutWorkloadSnapshot();
    const declarations = project.files.filter((file) =>
        file.path.startsWith("/declarations/"),
    );
    const roots = project.files.filter(
        (file) =>
            file.path.startsWith("/sources/natural-timeout-phase-") &&
            file.path.endsWith(".ts"),
    );
    const helpers = project.files.filter((file) =>
        file.path.startsWith("/sources/natural-timeout-helper-"),
    );
    const byteLength = (file) => Buffer.byteLength(file.text, "utf8");
    const tokenCount = (file) => countSourceTokens(file.text);
    const aggregateBytes = (files) =>
        files.reduce((total, file) => total + byteLength(file), 0);
    const aggregateTokens = (files) =>
        files.reduce((total, file) => total + tokenCount(file), 0);
    assert(
        declarations.length === authority.declarationFiles &&
            aggregateBytes(declarations) === authority.declarationBytes &&
            aggregateTokens(declarations) === authority.declarationTokens,
        "Natural semantic-timeout declaration authority drifted.",
    );
    assert(
        roots.length === authority.rootFiles &&
            aggregateBytes(roots) === authority.rootBytes &&
            aggregateTokens(roots) === authority.rootTokens &&
            Math.max(...roots.map(tokenCount)) ===
                authority.maximumRootTokens,
        "Natural semantic-timeout root authority drifted.",
    );
    assert(
        helpers.length === authority.helperFiles &&
            aggregateBytes(helpers) === authority.helperBytes &&
            aggregateTokens(helpers) === authority.helperTokens &&
            Math.max(...helpers.map(tokenCount)) ===
                authority.maximumHelperTokens,
        "Natural semantic-timeout helper authority drifted.",
    );
    assert(
        project.files.length === authority.totalFiles &&
            aggregateBytes(project.files) === authority.totalBytes &&
            aggregateTokens(project.files) === authority.totalTokens,
        "Natural semantic-timeout aggregate input authority drifted.",
    );
    assert(
        authority.totalFiles <= 512 &&
            authority.totalBytes <= 8 * 1024 * 1024 &&
            authority.totalTokens <= p3bWorkerLimits.maximumProjectTokens &&
            Math.max(...project.files.map(byteLength)) <= 8 * 1024 * 1024 &&
            Math.max(...project.files.map(tokenCount)) <=
                p3bWorkerLimits.maximumTokensPerFile,
        "Natural semantic-timeout fixture escaped ordinary Worker admission.",
    );
    const timingRuns = [];
    const expectedProgress = [
        {
            completedUnits: Math.ceil(authority.rootFiles / 8),
            phase: "parse",
            sequence: 1,
            totalUnits: authority.rootFiles,
        },
        {
            completedUnits: Math.ceil(authority.rootFiles / 4),
            phase: "parse",
            sequence: 2,
            totalUnits: authority.rootFiles,
        },
        {
            completedUnits: Math.ceil(authority.rootFiles / 8),
            phase: "semantic",
            sequence: 1,
            totalUnits: authority.rootFiles,
        },
        {
            completedUnits: Math.ceil(authority.rootFiles / 4),
            phase: "semantic",
            sequence: 2,
            totalUnits: authority.rootFiles,
        },
        {
            completedUnits: Math.ceil(authority.outputFiles / 8),
            phase: "emit",
            sequence: 1,
            totalUnits: authority.outputFiles,
        },
        {
            completedUnits: Math.ceil(authority.outputFiles / 4),
            phase: "emit",
            sequence: 2,
            totalUnits: authority.outputFiles,
        },
    ];
    for (
        let runIndex = 0;
        runIndex < p3bB2hNaturalPhaseCalibrationBaselineRuns;
        runIndex += 1
    ) {
        const baseline = await compile(
            `desktop-p3b-b2h-natural-phase-calibration-${runIndex + 1}`,
            project,
        );
        assert(
            JSON.stringify(baseline.phaseProgressEvents) ===
                JSON.stringify(expectedProgress),
            "P3b-b2h calibration baseline lost the six real-work receipts.",
        );
        assert(
            !baseline.result.emitSkipped &&
                baseline.result.diagnostics.length === 0 &&
                baseline.result.outputs.length === authority.outputFiles,
            "P3b-b2h calibration baseline did not compile cleanly.",
        );
        const javascriptOutputs = baseline.result.outputs.filter((output) =>
            output.path.endsWith(".js"),
        );
        const sourceMapOutputs = baseline.result.outputs.filter((output) =>
            output.path.endsWith(".map"),
        );
        const outputBytes = (outputs) =>
            outputs.reduce(
                (total, output) =>
                    total + Buffer.byteLength(output.text, "utf8"),
                0,
            );
        assert(
            javascriptOutputs.length === authority.rootFiles &&
                sourceMapOutputs.length === authority.rootFiles &&
                outputBytes(javascriptOutputs) ===
                    authority.javascriptOutputBytes &&
                outputBytes(sourceMapOutputs) ===
                    authority.sourceMapOutputBytes &&
                outputBytes(baseline.result.outputs) ===
                    authority.outputBytes &&
                authority.outputFiles <= 512 &&
                authority.outputBytes <= 1024 * 1024,
            "P3b-b2h calibration baseline output accounting drifted.",
        );
        assert(
            naturalPhaseOutputAggregateSha256(baseline.result.outputs) ===
                authority.outputAggregateSha256,
            "P3b-b2h calibration baseline output authority drifted.",
        );
        timingRuns.push(baseline.phaseTimingEvidence);
    }
    const observedPhaseRuns = timingRuns.map((run) =>
        run.map((timing) => {
            assert(
                timing.completedElapsedRealtimeNanosSinceProjectStart >
                    timing.sequenceTwoElapsedRealtimeNanosSinceProjectStart,
                `P3b-b2h observed an invalid real ${timing.phase} window.`,
            );
            return timing.phase;
        }),
    );
    const marginCalibrations =
        calibrateP3bB2hNaturalSemanticRequestStartDeadline(
            timingRuns,
            p3bB2hNaturalPhaseCalibrationMarginMilliseconds,
        );
    assert(
        p3bB2hNaturalPhaseFaultAttempts === 1,
        "P3b-b2h must use one fixed request-start timeout attempt per phase.",
    );
    assert(
        JSON.stringify(
            observedPhaseRuns,
        ) ===
            JSON.stringify(
                Array.from(
                    { length: p3bB2hNaturalPhaseCalibrationBaselineRuns },
                    () => ["parse", "semantic", "emit"],
                ),
            ) &&
            JSON.stringify(
                marginCalibrations.map((calibration) => calibration.phase),
            ) ===
            JSON.stringify(["semantic"]),
        "P3b-b2h did not calibrate exactly semantic.",
    );
};

const p3bB2iNaturalParseCalibrationBaselineRuns = 3;
const p3bB2iNaturalParseCalibrationMarginMilliseconds = 100;
const p3bB2iNaturalParsePreconditionRuns = 2;
const p3bB2iNaturalParseFaultAttempts = 1;

const calibrateP3bB2iNaturalParseRequestStartDeadline = (
    timingRuns,
    marginMilliseconds,
) => {
    assert(
        timingRuns.length === p3bB2iNaturalParseCalibrationBaselineRuns,
        "Natural parse calibration did not use exactly three baselines.",
    );
    const phase = "parse";
    const phaseRuns = timingRuns.map((run) => {
        const timing = run.find((candidate) => candidate.phase === phase);
        assert(timing, "Natural parse calibration run omitted parse.");
        return timing;
    });
    const rawLowerNanos = phaseRuns.reduce(
        (maximum, timing) =>
            timing.sequenceTwoElapsedRealtimeNanosSinceProjectStart > maximum
                ? timing.sequenceTwoElapsedRealtimeNanosSinceProjectStart
                : maximum,
        0n,
    );
    const rawUpperNanos = phaseRuns.reduce(
        (minimum, timing) =>
            timing.completedElapsedRealtimeNanosSinceProjectStart < minimum
                ? timing.completedElapsedRealtimeNanosSinceProjectStart
                : minimum,
        phaseRuns[0].completedElapsedRealtimeNanosSinceProjectStart,
    );
    const marginNanos =
        BigInt(marginMilliseconds) * nanosecondsPerMillisecond;
    assert(
        rawUpperNanos - rawLowerNanos >= marginNanos * 2n,
        "Natural parse three-baseline intersection has less than " +
            `${marginMilliseconds} ms on each side of a fixed ` +
            "request-start deadline.",
    );
    const minimumTimeoutMilliseconds = Number(
        (
            rawLowerNanos +
            marginNanos +
            nanosecondsPerMillisecond -
            1n
        ) / nanosecondsPerMillisecond,
    );
    const maximumTimeoutMilliseconds = Number(
        (rawUpperNanos - marginNanos) / nanosecondsPerMillisecond,
    );
    assert(
        minimumTimeoutMilliseconds <= maximumTimeoutMilliseconds,
        "Natural parse has no whole-millisecond calibrated deadline.",
    );
    const timeoutMilliseconds = Math.floor(
        (minimumTimeoutMilliseconds + maximumTimeoutMilliseconds) / 2,
    );
    const timeoutNanos =
        BigInt(timeoutMilliseconds) * nanosecondsPerMillisecond;
    assert(
        timeoutNanos - rawLowerNanos >= marginNanos &&
            rawUpperNanos - timeoutNanos >= marginNanos,
        `Natural parse midpoint lost its two ${marginMilliseconds} ms margins.`,
    );
    return {
        maximumTimeoutMilliseconds,
        minimumTimeoutMilliseconds,
        phase,
        timeoutMilliseconds,
    };
};

const verifyP3bB2iNaturalParseDeadlineCalibration = async () => {
    const authority = naturalParseTimeoutWorkloadAuthority;
    const project = naturalParseTimeoutWorkloadSnapshot();
    const roots = project.files.filter((file) =>
        file.path.startsWith(
            `${naturalParseTimeoutCommonDirectory}/probe-parse-root-`,
        ),
    );
    const ambientDeclarations = project.files.filter(
        (file) =>
            file.path === naturalParseTimeoutAmbientDeclarationPath,
    );
    const declarations = project.files.filter((file) =>
        file.path.startsWith("/declarations/"),
    );
    const byteLength = (file) => Buffer.byteLength(file.text, "utf8");
    const tokenCount = (file) => countSourceTokens(file.text);
    const aggregateBytes = (files) =>
        files.reduce((total, file) => total + byteLength(file), 0);
    const aggregatePathCharacters = (files) =>
        files.reduce((total, file) => total + file.path.length, 0);
    const aggregateTokens = (files) =>
        files.reduce((total, file) => total + tokenCount(file), 0);
    const maximumBytes = (files) => Math.max(...files.map(byteLength));
    const maximumPathCharacters = (files) =>
        Math.max(...files.map((file) => file.path.length));
    const maximumTokens = (files) => Math.max(...files.map(tokenCount));
    const inputTransportFrameCount =
        2 +
        project.files.reduce(
            (total, file) =>
                total + 2 + Math.ceil(byteLength(file) / frameBytes),
            0,
        );

    assert(
        authority.commonDirectoryDepth === 8 &&
            authority.commonDirectorySegmentPrefix === "p" &&
            JSON.stringify(naturalParseTimeoutCommonDirectorySegments) ===
                JSON.stringify(
                    Array.from(
                        { length: 8 },
                        (_, index) =>
                            `p${index.toString().padStart(2, "0")}`,
                    ),
                ) &&
            naturalParseTimeoutCommonDirectory ===
                `/sources/${Array.from(
                    { length: 8 },
                    (_, index) =>
                        `p${index.toString().padStart(2, "0")}`,
                ).join("/")}` &&
            roots.length === authority.rootFiles &&
            roots.every(
                (root, index) =>
                    root.path === naturalParseTimeoutSourcePath(index) &&
                    path.dirname(root.path) ===
                        naturalParseTimeoutCommonDirectory &&
                    root.path
                        .split("/")
                        .filter(Boolean).length ===
                        authority.sourcePathDepth,
            ) &&
            JSON.stringify(project.rootNames) ===
                JSON.stringify(project.rootNames.slice().sort()) &&
            aggregateBytes(roots) === authority.rootBytes &&
            aggregateTokens(roots) === authority.rootTokens &&
            maximumBytes(roots) === authority.maximumRootBytes &&
            maximumTokens(roots) === authority.maximumRootTokens,
        "Natural parse-timeout eight-level common-directory root authority drifted.",
    );
    assert(
        ambientDeclarations.length ===
                authority.ambientDeclarationFiles &&
            path.dirname(ambientDeclarations[0].path) ===
                naturalParseTimeoutCommonDirectory &&
            aggregateBytes(ambientDeclarations) ===
                authority.ambientDeclarationBytes &&
            aggregateTokens(ambientDeclarations) ===
                authority.ambientDeclarationTokens &&
            ambientDeclarations[0].text ===
                naturalParseTimeoutAmbientDeclarationText,
        "Natural parse-timeout ambient declaration authority drifted.",
    );
    assert(
        declarations.length === authority.declarationFiles &&
            aggregateBytes(declarations) === authority.declarationBytes &&
            aggregateTokens(declarations) === authority.declarationTokens,
        "Natural parse-timeout declaration authority drifted.",
    );
    assert(
        project.files.length === authority.totalFiles &&
            aggregateBytes(project.files) === authority.totalBytes &&
            aggregatePathCharacters(project.files) ===
                authority.aggregatePathCharacters &&
            maximumPathCharacters(project.files) ===
                authority.maximumPathCharacters &&
            inputTransportFrameCount ===
                authority.inputTransportFrameCount &&
            aggregateTokens(project.files) === authority.totalTokens,
        "Natural parse-timeout aggregate input or path authority drifted.",
    );
    assert(
        authority.totalFiles <= 512 &&
            authority.totalBytes < 8 * 1024 * 1024 &&
            authority.totalTokens < p3bWorkerLimits.maximumProjectTokens &&
            maximumBytes(project.files) < 8 * 1024 * 1024 &&
            maximumTokens(project.files) <
                p3bWorkerLimits.maximumTokensPerFile,
        "Natural parse-timeout fixture escaped ordinary Worker admission.",
    );
    const referenceRoots = roots.filter((root) =>
        root.text.includes(
            '/// <reference path="./probe-parse-ambient.d.ts" />\n',
        ),
    );
    assert(
        referenceRoots.length === 1 &&
            authority.referenceRootIndex === 2 &&
            authority.referenceRootIndex ===
                Math.ceil(authority.rootFiles / 4) &&
            referenceRoots[0].path ===
                naturalParseTimeoutSourcePath(
                    authority.referenceRootIndex,
                ) &&
            referenceRoots[0].text ===
                naturalParseTimeoutSourceText(
                    authority.referenceRootIndex,
                ),
        "Natural parse-timeout ambient reference is not confined to root 2 immediately after parse sequence two.",
    );
    const importRoots = roots.filter((root) =>
        root.text.includes("import type { ProbeType as ProbeType"),
    );
    assert(
        importRoots.length === authority.importRootFiles &&
            authority.firstImportRootIndex === 3 &&
            authority.firstImportRootIndex ===
                authority.referenceRootIndex + 1 &&
            importRoots.every(
                (root, index) =>
                    root.path ===
                        naturalParseTimeoutSourcePath(
                            authority.firstImportRootIndex + index,
                        ) &&
                    root.text ===
                        naturalParseTimeoutSourceText(
                            authority.firstImportRootIndex + index,
                        ),
            ),
        "Natural parse-timeout imports are not confined to roots 3 through 5.",
    );
    const moduleIndexes = importRoots.flatMap((root) =>
        Array.from(
            root.text.matchAll(/from "probe-([0-9]{5})"; /g),
            (match) => Number.parseInt(match[1], 10),
        ),
    );
    assert(
        moduleIndexes.length ===
                authority.importRootFiles * authority.importsPerRoot &&
            authority.maximumModuleIndex ===
                authority.importRootFiles * authority.importsPerRoot - 1 &&
            moduleIndexes.every(
                (moduleIndex, index) => moduleIndex === index,
            ),
        "Natural parse-timeout module imports lost their unique 0..17099 authority.",
    );

    const expectedProgress = [
        {
            completedUnits: Math.ceil(authority.rootFiles / 8),
            phase: "parse",
            sequence: 1,
            totalUnits: authority.rootFiles,
        },
        {
            completedUnits: Math.ceil(authority.rootFiles / 4),
            phase: "parse",
            sequence: 2,
            totalUnits: authority.rootFiles,
        },
        {
            completedUnits: Math.ceil(authority.rootFiles / 8),
            phase: "semantic",
            sequence: 1,
            totalUnits: authority.rootFiles,
        },
        {
            completedUnits: Math.ceil(authority.rootFiles / 4),
            phase: "semantic",
            sequence: 2,
            totalUnits: authority.rootFiles,
        },
        {
            completedUnits: Math.ceil(authority.outputFiles / 8),
            phase: "emit",
            sequence: 1,
            totalUnits: authority.outputFiles,
        },
        {
            completedUnits: Math.ceil(authority.outputFiles / 4),
            phase: "emit",
            sequence: 2,
            totalUnits: authority.outputFiles,
        },
    ];
    assert(
        p3bB2iNaturalParsePreconditionRuns === 2,
        "P3b-b2i must use exactly two successful workload preconditions.",
    );
    for (
        let preconditionIndex = 0;
        preconditionIndex < p3bB2iNaturalParsePreconditionRuns;
        preconditionIndex += 1
    ) {
        const precondition = await compile(
            `desktop-p3b-b2i-natural-parse-precondition-${
                preconditionIndex + 1
            }`,
            project,
        );
        assert(
            JSON.stringify(precondition.phaseProgressEvents) ===
                JSON.stringify(expectedProgress),
            "P3b-b2i precondition lost the six real-work receipts.",
        );
        assert(
            !precondition.result.emitSkipped &&
                precondition.result.diagnostics.length === 0 &&
                precondition.result.outputs.length === authority.outputFiles,
            "P3b-b2i precondition did not compile cleanly.",
        );
        const preconditionJavascriptOutputs =
            precondition.result.outputs.filter((output) =>
                output.path.endsWith(".js"),
            );
        const preconditionSourceMapOutputs =
            precondition.result.outputs.filter((output) =>
                output.path.endsWith(".map"),
            );
        const preconditionOutputBytes = (outputs) =>
            outputs.reduce(
                (total, output) =>
                    total + Buffer.byteLength(output.text, "utf8"),
                0,
            );
        assert(
            preconditionJavascriptOutputs.length === authority.rootFiles &&
                preconditionSourceMapOutputs.length === authority.rootFiles &&
                preconditionOutputBytes(preconditionJavascriptOutputs) ===
                    authority.javascriptOutputBytes &&
                preconditionOutputBytes(preconditionSourceMapOutputs) ===
                    authority.sourceMapOutputBytes &&
                preconditionOutputBytes(precondition.result.outputs) ===
                    authority.outputBytes &&
                naturalPhaseOutputAggregateSha256(
                    precondition.result.outputs,
                ) === authority.outputAggregateSha256 &&
                JSON.stringify(
                    precondition.phaseTimingEvidence.map(
                        (timing) => timing.phase,
                    ),
                ) === JSON.stringify(["parse", "semantic", "emit"]),
            "P3b-b2i natural parse precondition authority drifted.",
        );
    }
    const timingRuns = [];
    for (
        let runIndex = 0;
        runIndex < p3bB2iNaturalParseCalibrationBaselineRuns;
        runIndex += 1
    ) {
        const baseline = await compile(
            `desktop-p3b-b2i-natural-parse-calibration-${runIndex + 1}`,
            project,
        );
        assert(
            JSON.stringify(baseline.phaseProgressEvents) ===
                JSON.stringify(expectedProgress),
            "P3b-b2i calibration baseline lost the six real-work receipts.",
        );
        assert(
            !baseline.result.emitSkipped &&
                baseline.result.diagnostics.length === 0 &&
                baseline.result.outputs.length === authority.outputFiles,
            "P3b-b2i calibration baseline did not compile cleanly.",
        );
        const javascriptOutputs = baseline.result.outputs.filter((output) =>
            output.path.endsWith(".js"),
        );
        const sourceMapOutputs = baseline.result.outputs.filter((output) =>
            output.path.endsWith(".map"),
        );
        const outputBytes = (outputs) =>
            outputs.reduce(
                (total, output) =>
                    total + Buffer.byteLength(output.text, "utf8"),
                0,
            );
        assert(
            javascriptOutputs.length === authority.rootFiles &&
                sourceMapOutputs.length === authority.rootFiles &&
                outputBytes(javascriptOutputs) ===
                    authority.javascriptOutputBytes &&
                outputBytes(sourceMapOutputs) ===
                    authority.sourceMapOutputBytes &&
                outputBytes(baseline.result.outputs) ===
                    authority.outputBytes &&
                authority.outputFiles <= 512 &&
                authority.outputBytes <= 1024 * 1024,
            "P3b-b2i calibration baseline output accounting drifted.",
        );
        assert(
            naturalPhaseOutputAggregateSha256(baseline.result.outputs) ===
                authority.outputAggregateSha256,
            "P3b-b2i calibration baseline output authority drifted.",
        );
        assert(
            JSON.stringify(
                baseline.phaseTimingEvidence.map((timing) => timing.phase),
            ) === JSON.stringify(["parse", "semantic", "emit"]),
            "P3b-b2i baseline lost real phase timing evidence.",
        );
        timingRuns.push(baseline.phaseTimingEvidence);
    }
    const calibration = calibrateP3bB2iNaturalParseRequestStartDeadline(
        timingRuns,
        p3bB2iNaturalParseCalibrationMarginMilliseconds,
    );
    assert(
        p3bB2iNaturalParseFaultAttempts === 1 &&
            calibration.phase === "parse",
        "P3b-b2i did not freeze exactly one parse timeout attempt.",
    );
};

const externalNaturalParseTimeoutCallerInventoryFingerprint = (files) => {
    const aggregate = crypto.createHash("sha256");
    for (const file of files
        .slice()
        .sort((left, right) =>
            left.path < right.path ? -1 : left.path > right.path ? 1 : 0,
        )) {
        const bytes = Buffer.from(file.text, "utf8");
        const archivePath = file.path.replace(/^\//, "");
        aggregate.update(Buffer.from(archivePath, "ascii"));
        aggregate.update(Buffer.from([0]));
        aggregate.update(Buffer.from(String(bytes.byteLength), "ascii"));
        aggregate.update(Buffer.from([0]));
        aggregate.update(
            Buffer.from(
                crypto.createHash("sha256").update(bytes).digest("hex"),
                "ascii",
            ),
        );
        aggregate.update(Buffer.from("\n", "ascii"));
    }
    return aggregate.digest("hex");
};

const verifyExternalNaturalParseTimeoutWorkload = async () => {
    const authority = externalNaturalParseTimeoutWorkloadAuthority;
    assert(
        process.execArgv.includes("--max-old-space-size=512") &&
            v8.getHeapStatistics().heap_size_limit <= 768 * 1024 * 1024,
        "External natural parse golden is not running under the 512 MiB old-space authority.",
    );
    if (typeof global.gc === "function") {
        global.gc();
    }
    const project = externalNaturalParseTimeoutWorkloadSnapshot();
    const callerFiles = project.files.filter((file) =>
        file.path.startsWith("/sources/"),
    );
    const roots = callerFiles.filter(
        (file) => file.path.endsWith(".ts") && !file.path.endsWith(".d.ts"),
    );
    const graph = callerFiles.filter((file) =>
        /\/probe-parse-xx[0-9]{3}\.d\.ts$/.test(file.path),
    );
    const ambientDeclarations = callerFiles.filter(
        (file) =>
            file.path === externalNaturalParseTimeoutAmbientDeclarationPath,
    );
    const declarations = project.files.filter((file) =>
        file.path.startsWith("/declarations/"),
    );
    const byteLength = (file) => Buffer.byteLength(file.text, "utf8");
    const tokenCount = (file) => countSourceTokens(file.text);
    const aggregateBytes = (files) =>
        files.reduce((total, file) => total + byteLength(file), 0);
    const aggregateTokens = (files) =>
        files.reduce((total, file) => total + tokenCount(file), 0);
    const aggregatePathCharacters = (files) =>
        files.reduce((total, file) => total + file.path.length, 0);
    const maximumBytes = (files) => Math.max(...files.map(byteLength));
    const maximumTokens = (files) => Math.max(...files.map(tokenCount));
    const maximumPathCharacters = (files) =>
        Math.max(...files.map((file) => file.path.length));
    const inputTransportFrameCount =
        2 +
        project.files.reduce(
            (total, file) =>
                total + 2 + Math.ceil(byteLength(file) / frameBytes),
            0,
        );

    assert(
        authority.rootFiles === 32 &&
            authority.sequenceTwoCompletedRoots ===
                Math.ceil(authority.rootFiles / 4) &&
            authority.commonDirectoryDepth === 8 &&
            externalNaturalParseTimeoutCommonDirectory ===
                "/sources/p00/p01/p02/p03/p04/p05/p06/p07" &&
            roots.length === authority.rootFiles &&
            roots.every(
                (root, index) =>
                    root.path ===
                        externalNaturalParseTimeoutSourcePath(index) &&
                    root.path.split("/").filter(Boolean).length ===
                        authority.sourcePathDepth &&
                    root.text ===
                        externalNaturalParseTimeoutSourceText(index),
            ) &&
            JSON.stringify(project.rootNames) ===
                JSON.stringify(roots.map((root) => root.path)) &&
            aggregateBytes(roots) === authority.rootBytes &&
            aggregateTokens(roots) === authority.rootTokens &&
            maximumBytes(roots) === authority.maximumRootBytes &&
            maximumTokens(roots) === authority.maximumRootTokens,
        "External natural parse root authority drifted.",
    );
    assert(
        ambientDeclarations.length === authority.ambientDeclarationFiles &&
            ambientDeclarations[0].text ===
                externalNaturalParseTimeoutAmbientDeclarationText &&
            byteLength(ambientDeclarations[0]) ===
                authority.ambientDeclarationBytes &&
            tokenCount(ambientDeclarations[0]) ===
                authority.ambientDeclarationTokens &&
            roots[0].text.startsWith(
                '/// <reference path="./probe-parse-ambient.d.ts" />\n',
            ) &&
            roots
                .slice(1)
                .every(
                    (root) =>
                        !root.text.includes("probe-parse-ambient.d.ts"),
                ),
        "External natural parse ambient reference escaped root zero.",
    );

    const importRoots = roots.filter((root) =>
        root.text.includes("import type { ProbeType as ProbeType"),
    );
    const preSequenceImportRoots = importRoots.filter(
        (root) =>
            roots.indexOf(root) < authority.sequenceTwoCompletedRoots,
    );
    const postSequenceImportRoot =
        roots[authority.postSequenceImportRootIndex];
    assert(
        authority.firstImportRootIndex === 2 &&
            authority.importRootFiles === 3 &&
            authority.preSequenceMaximumModuleIndex ===
                authority.importRootFiles * authority.importsPerRoot - 1 &&
            preSequenceImportRoots.length === authority.importRootFiles &&
            preSequenceImportRoots.every(
                (root, index) =>
                    root.path ===
                        externalNaturalParseTimeoutSourcePath(
                            authority.firstImportRootIndex + index,
                        ) &&
                    authority.firstImportRootIndex + index <
                        authority.sequenceTwoCompletedRoots,
            ) &&
            authority.postSequenceImportRootIndex ===
                authority.sequenceTwoCompletedRoots &&
            authority.postSequenceImportRootIndex ===
                authority.graphTriggerRootIndex &&
            authority.postSequenceImports === 6_000 &&
            authority.graphEntryIndex === authority.graphFiles - 1 &&
            importRoots.length === authority.importRootFiles + 1 &&
            postSequenceImportRoot.text.startsWith(
                externalNaturalParseTimeoutGraphReferenceLine(
                    authority.graphEntryIndex,
                ),
            ) &&
            postSequenceImportRoot.text.includes(
                'from "probe-09000"; ',
            ),
        "External natural parse pre/post-sequence import topology drifted.",
    );
    let expectedModuleIndex = 0;
    for (const importRoot of importRoots) {
        for (const match of importRoot.text.matchAll(
            /from "probe-([0-9]{5})"; /g,
        )) {
            assert(
                Number.parseInt(match[1], 10) === expectedModuleIndex,
                "External natural parse module inventory is not contiguous.",
            );
            expectedModuleIndex += 1;
        }
    }
    assert(
        expectedModuleIndex ===
                authority.importRootFiles * authority.importsPerRoot +
                    authority.postSequenceImports &&
            authority.maximumModuleIndex ===
                authority.preSequenceMaximumModuleIndex +
                    authority.postSequenceImports &&
            expectedModuleIndex - 1 === authority.maximumModuleIndex,
        "External natural parse module inventory lost its contiguous authority.",
    );

    const referenceLine = externalNaturalParseTimeoutGraphReferenceLine(
        authority.graphEntryIndex,
    );
    assert(
        Buffer.byteLength(referenceLine, "ascii") ===
                authority.graphReferenceLineBytes &&
            authority.graphTriggerRootIndex ===
                authority.sequenceTwoCompletedRoots &&
            roots[authority.graphTriggerRootIndex].text.startsWith(
                referenceLine,
            ) &&
            roots.every(
                (root, index) =>
                    index === authority.graphTriggerRootIndex ||
                    !root.text.includes("probe-parse-xx"),
            ),
        "External natural parse graph is not triggered by the first post-sequence-two root.",
    );
    let graphEdges = 0;
    for (let index = 0; index < graph.length; index += 1) {
        const file = graph[index];
        assert(
            file.path === externalNaturalParseTimeoutGraphPath(index) &&
                file.text === externalNaturalParseTimeoutGraphText(index) &&
                tokenCount(file) === authority.graphTokens,
            "External natural parse graph file authority drifted.",
        );
        let expectedTarget = 0;
        for (const match of file.text.matchAll(
            /\/\/\/ <reference path="\.\/probe-parse-xx([0-9]{3})\.d\.ts" \/>\n/g,
        )) {
            assert(
                Number.parseInt(match[1], 10) === expectedTarget &&
                    Buffer.byteLength(match[0], "ascii") ===
                        authority.graphReferenceLineBytes,
                "External natural parse graph edge is malformed or out of order.",
            );
            expectedTarget += 1;
            graphEdges += 1;
        }
        assert(
            expectedTarget === index,
            "External natural parse graph is not the frozen reverse-index DAG.",
        );
    }
    assert(
        graph.length === authority.graphFiles &&
            graphEdges === authority.graphEdges &&
            aggregateBytes(graph) === authority.graphBytes &&
            aggregateTokens(graph) === authority.graphTokens,
        "External natural parse graph aggregate authority drifted.",
    );

    assert(
        declarations.length === authority.declarationFiles &&
            aggregateBytes(declarations) === authority.declarationBytes &&
            aggregateTokens(declarations) === authority.declarationTokens &&
            aggregatePathCharacters(declarations) ===
                authority.declarationPathCharacters,
        "External natural parse declarations authority drifted.",
    );
    assert(
        callerFiles.length === authority.callerFiles &&
            aggregateBytes(callerFiles) === authority.callerBytes &&
            aggregateTokens(callerFiles) === authority.callerTokens &&
            aggregatePathCharacters(callerFiles) ===
                authority.callerPathCharacters &&
            externalNaturalParseTimeoutCallerInventoryFingerprint(
                callerFiles,
            ) === authority.callerInventoryFingerprint &&
            project.files.length === authority.totalFiles &&
            aggregateBytes(project.files) === authority.totalBytes &&
            aggregateTokens(project.files) === authority.totalTokens &&
            aggregatePathCharacters(project.files) ===
                authority.totalPathCharacters &&
            maximumPathCharacters(project.files) ===
                authority.maximumPathCharacters &&
            inputTransportFrameCount === authority.inputTransportFrameCount,
        "External natural parse aggregate input authority drifted.",
    );
    assert(
        authority.totalFiles === 512 &&
            authority.totalFiles <= 512 &&
            authority.totalBytes < 8 * 1024 * 1024 &&
            authority.totalTokens < p3bWorkerLimits.maximumProjectTokens &&
            maximumBytes(project.files) < 8 * 1024 * 1024 &&
            maximumTokens(project.files) <
                p3bWorkerLimits.maximumTokensPerFile,
        "External natural parse workload escaped ordinary Worker admission.",
    );

    const expectedProgress = [
        {
            completedUnits: 4,
            phase: "parse",
            sequence: 1,
            totalUnits: 32,
        },
        {
            completedUnits: 8,
            phase: "parse",
            sequence: 2,
            totalUnits: 32,
        },
        {
            completedUnits: 4,
            phase: "semantic",
            sequence: 1,
            totalUnits: 32,
        },
        {
            completedUnits: 8,
            phase: "semantic",
            sequence: 2,
            totalUnits: 32,
        },
        {
            completedUnits: 8,
            phase: "emit",
            sequence: 1,
            totalUnits: 64,
        },
        {
            completedUnits: 16,
            phase: "emit",
            sequence: 2,
            totalUnits: 64,
        },
    ];
    const success = await compile(
        "desktop-external-natural-parse-timeout-success",
        project,
    );
    assert(
        JSON.stringify(success.phaseProgressEvents) ===
            JSON.stringify(expectedProgress),
        "External natural parse success lost its six real-work receipts.",
    );
    assert(
        !success.result.emitSkipped &&
            success.result.diagnostics.length === 0 &&
            success.result.outputs.length === authority.outputFiles &&
            JSON.stringify(
                success.phaseTimingEvidence.map((timing) => timing.phase),
            ) === JSON.stringify(["parse", "semantic", "emit"]),
        "External natural parse workload did not complete cleanly under 512 MiB old-space.",
    );
    const javascriptOutputs = success.result.outputs.filter((output) =>
        output.path.endsWith(".js"),
    );
    const sourceMapOutputs = success.result.outputs.filter((output) =>
        output.path.endsWith(".map"),
    );
    const outputBytes = (outputs) =>
        outputs.reduce(
            (total, output) =>
                total + Buffer.byteLength(output.text, "utf8"),
            0,
        );
    assert(
        javascriptOutputs.length === authority.rootFiles &&
            sourceMapOutputs.length === authority.rootFiles &&
            outputBytes(javascriptOutputs) ===
                authority.javascriptOutputBytes &&
            outputBytes(sourceMapOutputs) ===
                authority.sourceMapOutputBytes &&
            outputBytes(success.result.outputs) === authority.outputBytes &&
            naturalPhaseOutputAggregateSha256(success.result.outputs) ===
                authority.outputAggregateSha256,
        "External natural parse successful output authority drifted.",
    );
};

const p3bB2jNaturalEmitCalibrationBaselineRuns = 3;
const p3bB2jNaturalEmitCalibrationMarginMilliseconds = 100;
const p3bB2jNaturalEmitPreconditionRuns = 2;
const p3bB2jNaturalEmitFaultAttempts = 1;

const calibrateP3bB2jNaturalEmitRequestStartDeadline = (
    timingRuns,
    marginMilliseconds,
) => {
    assert(
        timingRuns.length === p3bB2jNaturalEmitCalibrationBaselineRuns,
        "Natural emit calibration did not use exactly three baselines.",
    );
    const phase = "emit";
    const phaseRuns = timingRuns.map((run) => {
        const timing = run.find((candidate) => candidate.phase === phase);
        assert(timing, "Natural emit calibration run omitted emit.");
        return timing;
    });
    const rawLowerNanos = phaseRuns.reduce(
        (maximum, timing) =>
            timing.sequenceTwoElapsedRealtimeNanosSinceProjectStart > maximum
                ? timing.sequenceTwoElapsedRealtimeNanosSinceProjectStart
                : maximum,
        0n,
    );
    const rawUpperNanos = phaseRuns.reduce(
        (minimum, timing) =>
            timing.completedElapsedRealtimeNanosSinceProjectStart < minimum
                ? timing.completedElapsedRealtimeNanosSinceProjectStart
                : minimum,
        phaseRuns[0].completedElapsedRealtimeNanosSinceProjectStart,
    );
    const marginNanos =
        BigInt(marginMilliseconds) * nanosecondsPerMillisecond;
    assert(
        rawUpperNanos - rawLowerNanos >= marginNanos * 2n,
        "Natural emit three-baseline intersection has less than " +
            `${marginMilliseconds} ms on each side of a fixed ` +
            "request-start deadline; " +
            `intervals=${phaseRuns
                .map(
                    (timing) =>
                        `${(
                            Number(
                                timing.sequenceTwoElapsedRealtimeNanosSinceProjectStart,
                            ) / 1_000_000
                        ).toFixed(3)}..${(
                            Number(
                                timing.completedElapsedRealtimeNanosSinceProjectStart,
                            ) / 1_000_000
                        ).toFixed(3)} ms`,
                )
                .join(", ")}; ` +
            `intersection=${(
                Number(rawUpperNanos - rawLowerNanos) / 1_000_000
            ).toFixed(3)} ms.`,
    );
    const minimumTimeoutMilliseconds = Number(
        (
            rawLowerNanos +
            marginNanos +
            nanosecondsPerMillisecond -
            1n
        ) / nanosecondsPerMillisecond,
    );
    const maximumTimeoutMilliseconds = Number(
        (rawUpperNanos - marginNanos) / nanosecondsPerMillisecond,
    );
    assert(
        minimumTimeoutMilliseconds <= maximumTimeoutMilliseconds,
        "Natural emit has no whole-millisecond calibrated deadline.",
    );
    const timeoutMilliseconds = Math.floor(
        (minimumTimeoutMilliseconds + maximumTimeoutMilliseconds) / 2,
    );
    const timeoutNanos =
        BigInt(timeoutMilliseconds) * nanosecondsPerMillisecond;
    assert(
        timeoutNanos - rawLowerNanos >= marginNanos &&
            rawUpperNanos - timeoutNanos >= marginNanos,
        `Natural emit midpoint lost its two ${marginMilliseconds} ms margins.`,
    );
    phaseRuns.forEach((timing) => {
        assert(
            timing.sequenceTwoElapsedRealtimeNanosSinceProjectStart <
                timeoutNanos &&
                timeoutNanos <
                    timing.completedElapsedRealtimeNanosSinceProjectStart,
            "Natural emit midpoint escaped an observed strict interval.",
        );
    });
    return {
        maximumTimeoutMilliseconds,
        minimumTimeoutMilliseconds,
        phase,
        timeoutMilliseconds,
    };
};

const verifyP3bB2jNaturalEmitDeadlineCalibration = async () => {
    const authority = naturalEmitTimeoutWorkloadAuthority;
    const project = naturalEmitTimeoutWorkloadSnapshot();
    const roots = project.files.filter((file) =>
        file.path.startsWith("/sources/natural-emit-timeout-"),
    );
    const declarations = project.files.filter((file) =>
        file.path.startsWith("/declarations/"),
    );
    const lightRoots = roots.slice(0, authority.lightRootFiles);
    const heavyRoots = roots.slice(authority.lightRootFiles);
    const byteLength = (file) => Buffer.byteLength(file.text, "utf8");
    const tokenCount = (file) => countSourceTokens(file.text);
    const aggregateBytes = (files) =>
        files.reduce((total, file) => total + byteLength(file), 0);
    const aggregateTokens = (files) =>
        files.reduce((total, file) => total + tokenCount(file), 0);
    const aggregatePathCharacters = (files) =>
        files.reduce((total, file) => total + file.path.length, 0);
    const maximumBytes = (files) => Math.max(...files.map(byteLength));
    const maximumTokens = (files) => Math.max(...files.map(tokenCount));
    const maximumPathCharacters = (files) =>
        Math.max(...files.map((file) => file.path.length));
    const inputTransportFrameCount =
        2 +
        project.files.reduce(
            (total, file) =>
                total + 2 + Math.ceil(byteLength(file) / frameBytes),
            0,
        );

    assert(
        declarations.length === authority.declarationFiles &&
            aggregateBytes(declarations) === authority.declarationBytes &&
            aggregateTokens(declarations) === authority.declarationTokens,
        "Natural emit-timeout declaration authority drifted.",
    );
    assert(
        roots.length === authority.rootFiles &&
            lightRoots.length === authority.lightRootFiles &&
            heavyRoots.length === authority.heavyRootFiles &&
            authority.lightRootFiles + authority.heavyRootFiles ===
                authority.rootFiles &&
            aggregateBytes(roots) === authority.rootBytes &&
            aggregateTokens(roots) === authority.rootTokens &&
            maximumBytes(roots) === authority.maximumRootBytes &&
            maximumTokens(roots) === authority.maximumRootTokens &&
            JSON.stringify(project.rootNames) ===
                JSON.stringify(project.rootNames.slice().sort()) &&
            roots.every(
                (root, index) =>
                    root.path === naturalEmitTimeoutSourcePath(index) &&
                    root.text === naturalEmitTimeoutSourceText(index) &&
                    root.path.split("/").filter(Boolean).length ===
                        authority.sourcePathDepth,
            ),
        "Natural emit-timeout root authority drifted.",
    );
    assert(
        lightRoots.every(
            (root, index) =>
                root.text ===
                `export const e${index
                    .toString()
                    .padStart(3, "0")}: number = ${index};\n` &&
                !root.text.includes("?."),
        ) &&
            heavyRoots.every(
                (root) =>
                    (root.text.match(/\?\./g) || []).length ===
                        authority.optionalChainsPerHeavyRoot *
                            authority.optionalChainDepth &&
                    (root.text.match(/ \?\? [0-9]+,/g) || []).length ===
                        authority.optionalChainsPerHeavyRoot &&
                    root.text.includes("?.p0?.p1") &&
                    root.text.includes("?.p31 ?? 17,"),
            ) &&
            authority.optionalChainsPerHeavyRoot === 18 &&
            authority.optionalChainDepth === 110 &&
            authority.lightRootFiles * 2 ===
                Math.ceil(authority.outputFiles / 4),
        "Natural emit-timeout heavy work does not begin after emit sequence two.",
    );
    assert(
        project.files.length === authority.totalFiles &&
            aggregateBytes(project.files) === authority.totalBytes &&
            aggregateTokens(project.files) === authority.totalTokens &&
            aggregatePathCharacters(project.files) ===
                authority.aggregatePathCharacters &&
            maximumPathCharacters(project.files) ===
                authority.maximumPathCharacters &&
            inputTransportFrameCount ===
                authority.inputTransportFrameCount,
        "Natural emit-timeout aggregate input, path, or framing authority drifted.",
    );
    assert(
        authority.totalFiles <= 512 &&
            authority.totalBytes <= 8 * 1024 * 1024 &&
            authority.totalTokens <= p3bWorkerLimits.maximumProjectTokens &&
            maximumBytes(project.files) <= 8 * 1024 * 1024 &&
            maximumTokens(project.files) <=
                p3bWorkerLimits.maximumTokensPerFile &&
            8 * 1024 * 1024 - authority.totalBytes >= 4 * 1024 * 1024,
        "Natural emit-timeout fixture escaped ordinary Worker admission.",
    );

    const expectedProgress = [
        {
            completedUnits: Math.ceil(authority.rootFiles / 8),
            phase: "parse",
            sequence: 1,
            totalUnits: authority.rootFiles,
        },
        {
            completedUnits: Math.ceil(authority.rootFiles / 4),
            phase: "parse",
            sequence: 2,
            totalUnits: authority.rootFiles,
        },
        {
            completedUnits: Math.ceil(authority.rootFiles / 8),
            phase: "semantic",
            sequence: 1,
            totalUnits: authority.rootFiles,
        },
        {
            completedUnits: Math.ceil(authority.rootFiles / 4),
            phase: "semantic",
            sequence: 2,
            totalUnits: authority.rootFiles,
        },
        {
            completedUnits: Math.ceil(authority.outputFiles / 8),
            phase: "emit",
            sequence: 1,
            totalUnits: authority.outputFiles,
        },
        {
            completedUnits: Math.ceil(authority.outputFiles / 4),
            phase: "emit",
            sequence: 2,
            totalUnits: authority.outputFiles,
        },
    ];

    const verifySuccessfulRun = async (requestId) => {
        const run = await compile(requestId, project);
        assert(
            JSON.stringify(run.phaseProgressEvents) ===
                JSON.stringify(expectedProgress),
            "P3b-b2j successful run lost the six real-work receipts.",
        );
        assert(
            !run.result.emitSkipped &&
                run.result.diagnostics.length === 0 &&
                run.result.outputs.length === authority.outputFiles,
            "P3b-b2j successful run did not compile cleanly.",
        );
        const javascriptOutputs = run.result.outputs.filter((output) =>
            output.path.endsWith(".js"),
        );
        const sourceMapOutputs = run.result.outputs.filter((output) =>
            output.path.endsWith(".map"),
        );
        const outputBytes = (outputs) =>
            outputs.reduce(
                (total, output) =>
                    total + Buffer.byteLength(output.text, "utf8"),
                0,
            );
        const actualJavascriptOutputBytes = outputBytes(javascriptOutputs);
        const actualSourceMapOutputBytes = outputBytes(sourceMapOutputs);
        const actualOutputBytes = outputBytes(run.result.outputs);
        assert(
            javascriptOutputs.length === authority.rootFiles &&
                sourceMapOutputs.length === authority.rootFiles &&
                actualJavascriptOutputBytes ===
                    authority.javascriptOutputBytes &&
                actualSourceMapOutputBytes ===
                    authority.sourceMapOutputBytes &&
                actualOutputBytes === authority.outputBytes &&
                authority.outputFiles <= 512 &&
                authority.outputBytes <= 1024 * 1024 &&
                1024 * 1024 - authority.outputBytes >= 64 * 1024,
            "P3b-b2j successful output accounting drifted: " +
                `javascript=${actualJavascriptOutputBytes}, ` +
                `sourceMap=${actualSourceMapOutputBytes}, ` +
                `total=${actualOutputBytes}.`,
        );
        const actualOutputAggregateSha256 =
            naturalPhaseOutputAggregateSha256(run.result.outputs);
        assert(
            actualOutputAggregateSha256 ===
                authority.outputAggregateSha256,
            "P3b-b2j successful output authority drifted: " +
                actualOutputAggregateSha256,
        );
        assert(
            JSON.stringify(
                run.phaseTimingEvidence.map((timing) => timing.phase),
            ) === JSON.stringify(["parse", "semantic", "emit"]),
            "P3b-b2j successful run lost real phase timing evidence.",
        );
        return run.phaseTimingEvidence;
    };

    assert(
        p3bB2jNaturalEmitPreconditionRuns === 2,
        "P3b-b2j must use exactly two successful workload preconditions.",
    );
    for (
        let preconditionIndex = 0;
        preconditionIndex < p3bB2jNaturalEmitPreconditionRuns;
        preconditionIndex += 1
    ) {
        await verifySuccessfulRun(
            `desktop-p3b-b2j-natural-emit-precondition-${
                preconditionIndex + 1
            }`,
        );
    }
    const timingRuns = [];
    for (
        let runIndex = 0;
        runIndex < p3bB2jNaturalEmitCalibrationBaselineRuns;
        runIndex += 1
    ) {
        timingRuns.push(
            await verifySuccessfulRun(
                `desktop-p3b-b2j-natural-emit-calibration-${runIndex + 1}`,
            ),
        );
    }
    const calibration =
        calibrateP3bB2jNaturalEmitRequestStartDeadline(
            timingRuns,
            p3bB2jNaturalEmitCalibrationMarginMilliseconds,
        );
    assert(
        p3bB2jNaturalEmitFaultAttempts === 1 &&
            calibration.phase === "emit",
        "P3b-b2j did not freeze exactly one emit timeout attempt.",
    );
};

const verifySemanticError = (result) => {
    assert(result.emitSkipped, "Type-error project was emitted.");
    assert(result.outputs.length === 0, "Type-error project returned output.");
    assert(
        result.diagnostics.length === 1,
        "Type-error project must return exactly one diagnostic; " +
            `received ${result.diagnostics
                .map((diagnostic) => `${diagnostic.phase}:${diagnostic.code}`)
                .join(", ")}.`,
    );
    const [actual] = result.diagnostics;
    assert(
        actual.phase === "semantic",
        `Type-error diagnostic phase must be semantic, received ${actual.phase}.`,
    );
    const expected = fixture.semanticError.diagnostic;
    for (const key of [
        "phase",
        "category",
        "code",
        "message",
        "sourcePath",
        "start",
        "length",
        "line",
        "column",
    ]) {
        assert(
            actual[key] === expected[key],
            `Semantic diagnostic mismatch for ${key}.`,
        );
    }
};

const verifyDeclarationError = (result) => {
    assert(result.emitSkipped, "Declaration-error project was emitted.");
    assert(
        result.outputs.length === 0,
        "Declaration-error project returned output.",
    );
    assert(
        result.diagnostics.length === 1,
        "Declaration-error project must return exactly one diagnostic; " +
            `received ${result.diagnostics
                .map((diagnostic) => `${diagnostic.phase}:${diagnostic.code}`)
                .join(", ")}.`,
    );
    const [actual] = result.diagnostics;
    const expected = fixture.declarationError.diagnostic;
    assert(
        actual.sourcePath === expected.sourcePath,
        "Declaration diagnostic must retain the exact controlled declaration path; " +
            `received ${actual.sourcePath}.`,
    );
    for (const key of [
        "phase",
        "category",
        "code",
        "message",
        "sourcePath",
        "start",
        "length",
        "line",
        "column",
    ]) {
        assert(
            actual[key] === expected[key],
            `Declaration diagnostic mismatch for ${key}.`,
        );
    }
};

const verifySourceDeclarationError = (result) => {
    assert(result.emitSkipped, "Source-declaration-error project was emitted.");
    assert(
        result.outputs.length === 0,
        "Source-declaration-error project returned output.",
    );
    assert(
        result.diagnostics.length === 1,
        "Source-declaration-error project must return exactly one diagnostic; " +
            `received ${result.diagnostics
                .map((diagnostic) => `${diagnostic.phase}:${diagnostic.code}`)
                .join(", ")}.`,
    );
    const [actual] = result.diagnostics;
    const expected = fixture.sourceDeclarationError.diagnostic;
    assert(
        actual.sourcePath === "/sources/types.d.ts",
        "Imported source declaration diagnostic must retain its source path.",
    );
    for (const key of [
        "phase",
        "category",
        "code",
        "message",
        "sourcePath",
        "start",
        "length",
        "line",
        "column",
    ]) {
        assert(
            actual[key] === expected[key],
            `Source declaration diagnostic mismatch for ${key}.`,
        );
    }
};

const verifyTypeOnlyRootImport = (result) => {
    assert(result.tsVersion === expectedVersion, "TypeScript version mismatch.");
    assert(
        result.compilerApi === "createIncrementalProgram",
        "Worker did not use createIncrementalProgram.",
    );
    assert(!result.emitSkipped, "Type-only root/import project emit was skipped.");
    assert(
        result.diagnostics.length === 0,
        "Type-only root/import project has diagnostics.",
    );

    const expected = new Map(
        fixture.typeOnlyRootImport.outputs.map((output) => [
            output.path,
            output,
        ]),
    );
    assert(
        result.outputs.length === expected.size,
        "Type-only root/import output count mismatch.",
    );
    for (const output of result.outputs) {
        const golden = expected.get(output.path);
        assert(golden, `Unexpected type-only output: ${output.path}`);
        assert(
            Buffer.byteLength(output.text, "utf8") === golden.bytes,
            `Type-only output size mismatch: ${output.path}`,
        );
        assert(
            output.sha256 === golden.sha256,
            `Type-only output digest mismatch: ${output.path}`,
        );
        assert(
            JSON.stringify(output.sourcePaths) ===
                JSON.stringify(golden.sourcePaths),
            `Type-only output sourcePaths mismatch: ${output.path}`,
        );

        if (output.path.endsWith(".map")) {
            const sourceMap = JSON.parse(output.text);
            const expectedMap = golden.sourceMap;
            assert(expectedMap, `Missing Source Map golden: ${output.path}`);
            assert(sourceMap.version === 3, "Source Map version mismatch.");
            assert(
                sourceMap.file === expectedMap.file,
                `Source Map file mismatch: ${output.path}`,
            );
            assert(
                sourceMap.sourceRoot === expectedMap.sourceRoot,
                `Source Map sourceRoot mismatch: ${output.path}`,
            );
            assert(
                JSON.stringify(sourceMap.sources) ===
                    JSON.stringify(expectedMap.sources),
                `Source Map sources mismatch: ${output.path}`,
            );
            assert(
                typeof sourceMap.mappings === "string",
                `Source Map mappings must be a string: ${output.path}`,
            );
            assert(
                (sourceMap.mappings.length === 0) ===
                    expectedMap.mappingsEmpty,
                `Source Map mappings emptiness mismatch: ${output.path}`,
            );
            assert(
                !Object.prototype.hasOwnProperty.call(
                    sourceMap,
                    "sourcesContent",
                ),
                "Source Map contains inline sources.",
            );
        }
    }
};

const runGolden = async (label, verification, failures) => {
    try {
        await verification();
    } catch (error) {
        failures.push(
            `${label}: ${error instanceof Error ? error.message : String(error)}`,
        );
    }
};

const expectProjectError = (responses, requestId, pattern) => {
    const error = responses.find(
        (message) =>
            message.kind === "projectError" &&
            message.requestId === requestId,
    );
    assert(error, `Worker did not reject malformed project ${requestId}.`);
    assert(
        pattern.test(error.message),
        `Unexpected project error for ${requestId}: ${error.message}`,
    );
};

const projectErrorFrom = (responses, requestId) =>
    responses.find(
        (message) =>
            message.kind === "projectError" &&
            message.requestId === requestId,
    );

const p3bCapacityBoundaryAuthority = (() => {
    const maximumSingleFileBytes = 8 * 1024 * 1024;
    const nonEmptyFiles =
        p3bWorkerLimits.maximumProjectBytes / maximumSingleFileBytes;
    assert(
        Number.isInteger(nonEmptyFiles),
        "P3b capacity boundary is not divisible into exact maximum files.",
    );
    const records = Array.from(
        { length: p3bWorkerLimits.maximumProjectFiles },
        (_, index) =>
            inputArchiveDescriptorRecord({
                byteLength: index < nonEmptyFiles ? maximumSingleFileBytes : 0,
                domain: index === 0 ? "declaration" : "source",
                path:
                    index === 0
                        ? "/declarations/capacity-boundary.d.ts"
                        : `/sources/capacity-boundary-${index
                              .toString()
                              .padStart(4, "0")}.ts`,
                sha256: "0".repeat(64),
            }),
    );
    return projectInputAuthority({
        fileRecords: records,
        inputQuotaRevision: TEST_ONLY_EXPANDED_INPUT_QUOTA_REVISION,
    });
})();

const verifyP3bAdmissionBoundaries = async () => {
    const acceptedRequestId = "desktop-p3b-capacity-boundary";
    const validStart = {
        ...p3bCapacityBoundaryAuthority,
        fileCount: p3bWorkerLimits.maximumProjectFiles,
        kind: "projectInputStart",
        requestId: acceptedRequestId,
        sequence: 0,
        ...profileAuthorityFields,
        targetProfile: "rhino",
        totalBytes: p3bWorkerLimits.maximumProjectBytes,
        workerSpinMilliseconds: 0,
        workerSpinPhase: "none",
    };
    expectInputAck(
        await dispatchWorkerMessage(validStart),
        acceptedRequestId,
        0,
    );
    expectProjectError(
        await dispatchWorkerMessage({
            fileCount: p3bWorkerLimits.maximumProjectFiles,
            kind: "projectInputEnd",
            requestId: acceptedRequestId,
            sequence: 1,
            totalBytes: p3bWorkerLimits.maximumProjectBytes,
        }),
        acceptedRequestId,
        /inventory|ended before|does not match/i,
    );

    const fileOverflowRequestId = "desktop-p3b-file-count-overflow";
    expectProjectError(
        await dispatchWorkerMessage({
            ...validStart,
            fileCount: validStart.fileCount + 1,
            requestId: fileOverflowRequestId,
        }),
        fileOverflowRequestId,
        /malformed|limit|capacity/i,
    );

    const byteOverflowRequestId = "desktop-p3b-byte-count-overflow";
    expectProjectError(
        await dispatchWorkerMessage({
            ...validStart,
            requestId: byteOverflowRequestId,
            totalBytes: validStart.totalBytes + 1,
        }),
        byteOverflowRequestId,
        /malformed|limit|capacity/i,
    );
};

const verifyP3bMaximumFileIngressRelease = async () => {
    const requestId = "desktop-p3b-maximum-file-ingress";
    const emptySha256 = digestBytes(Buffer.alloc(0));
    const startedAt = Date.now();
    const files = Array.from(
        { length: p3bWorkerLimits.maximumProjectFiles },
        (_, fileIndex) => ({
            bytes: Buffer.alloc(0),
            path:
                fileIndex === 0
                    ? "/declarations/lib.d.ts"
                    : fileIndex === 1
                      ? "/sources/root.ts"
                      : `/declarations/p3b-cardinality-${(fileIndex - 2)
                            .toString()
                            .padStart(4, "0")}.d.ts`,
        }),
    );
    const authority = inputArchiveAuthorityForFiles(
        files,
        TEST_ONLY_EXPANDED_INPUT_QUOTA_REVISION,
    );
    let sequence = 0;
    expectInputAck(
        await dispatchWorkerMessage({
            ...authority,
            fileCount: p3bWorkerLimits.maximumProjectFiles,
            kind: "projectInputStart",
            requestId,
            sequence,
            ...profileAuthorityFields,
            targetProfile: "rhino",
            totalBytes: 0,
            workerSpinMilliseconds: 0,
            workerSpinPhase: "none",
        }),
        requestId,
        sequence,
    );
    sequence += 1;

    for (
        let fileIndex = 0;
        fileIndex < p3bWorkerLimits.maximumProjectFiles;
        fileIndex += 1
    ) {
        const filePath = files[fileIndex].path;
        expectInputAck(
            await dispatchWorkerMessage({
                byteLength: 0,
                fileIndex,
                kind: "projectInputFileStart",
                path: filePath,
                requestId,
                sequence,
                sha256: emptySha256,
            }),
            requestId,
            sequence,
        );
        sequence += 1;
        expectInputAck(
            await dispatchWorkerMessage({
                chunkCount: 0,
                fileIndex,
                kind: "projectInputFileEnd",
                requestId,
                sequence,
            }),
            requestId,
            sequence,
        );
        sequence += 1;
    }

    const retainedState = workerBinding("framedInput");
    assert(
        retainedState !== null &&
            retainedState.files.length ===
                p3bWorkerLimits.maximumProjectFiles &&
            retainedState.paths.size ===
                p3bWorkerLimits.maximumProjectFiles &&
            retainedState.foldedPaths.size ===
                p3bWorkerLimits.maximumProjectFiles &&
            retainedState.files[0].path === "/declarations/lib.d.ts" &&
            retainedState.files[1].path === "/sources/root.ts" &&
            retainedState.directoryIndex.pathChars() > 0,
        "Worker did not retain the exact maximum-cardinality ingress state.",
    );

    expectProjectError(
        await dispatchWorkerMessage({
            fileCount: p3bWorkerLimits.maximumProjectFiles - 1,
            kind: "projectInputEnd",
            requestId,
            sequence,
            totalBytes: 0,
        }),
        requestId,
        /input end.*inventory|inventory.*does not match/i,
    );
    assert(
        workerBinding("framedInput") === null &&
            workerBinding("framedCompilationRequestId") === null &&
            workerBinding("framedResult") === null &&
            workerBinding("projectOperationActive")() === false,
        "Worker retained an active operation after maximum-cardinality release.",
    );
    assert(
        retainedState.currentFile === null &&
            retainedState.files.length === 0 &&
            retainedState.paths.size === 0 &&
            retainedState.foldedPaths.size === 0 &&
            retainedState.directoryIndex.pathChars() === 0 &&
            retainedState.directoryIndex.directoryExists("/") === false,
        "Worker did not clear every high-cardinality ingress collection.",
    );

    const { result } = await compile(
        "desktop-p3b-maximum-file-successor",
        snapshot(fixture.success.mainSource),
    );
    verifySuccess(result);
    const elapsedMilliseconds = Date.now() - startedAt;
    assert(
        elapsedMilliseconds < 30_000,
        "Maximum-cardinality ingress/release fixture exceeded 30 seconds.",
    );
    process.stdout.write(
        `P3b ${p3bWorkerLimits.maximumProjectFiles}-file ingress/release passed in ` +
            `${elapsedMilliseconds} ms.\n`,
    );
};

const expectProjectRejectedDuringTransfer = async (
    requestId,
    files,
    pattern,
) => {
    const transferFiles = files.some((file) =>
        file.path.startsWith("/declarations/"),
    )
        ? files
        : [
              {
                  path: "/declarations/transfer-boundary.d.ts",
                  text: "",
              },
              ...files,
          ];
    const totalBytes = transferFiles.reduce(
        (total, file) => total + Buffer.byteLength(file.text, "utf8"),
        0,
    );
    const authority = inputArchiveAuthorityForFiles(transferFiles);
    let sequence = 0;
    expectInputAck(
        await dispatchWorkerMessage({
            ...authority,
            fileCount: transferFiles.length,
            kind: "projectInputStart",
            requestId,
            sequence,
            ...profileAuthorityFields,
            targetProfile: "rhino",
            totalBytes,
            workerSpinMilliseconds: 0,
            workerSpinPhase: "none",
        }),
        requestId,
        sequence,
    );
    sequence += 1;

    for (
        let fileIndex = 0;
        fileIndex < transferFiles.length;
        fileIndex += 1
    ) {
        const file = transferFiles[fileIndex];
        let bytes = Buffer.from(file.text, "utf8");
        let responses = await dispatchWorkerMessage({
            byteLength: bytes.length,
            fileIndex,
            kind: "projectInputFileStart",
            path: file.path,
            requestId,
            sequence,
            sha256: digestBytes(bytes),
        });
        let failure = projectErrorFrom(responses, requestId);
        if (failure) {
            bytes.fill(0);
            assert(
                pattern.test(failure.message),
                `Unexpected project error for ${requestId}: ${failure.message}`,
            );
            return failure;
        }
        expectInputAck(responses, requestId, sequence);
        sequence += 1;

        let chunkIndex = 0;
        for (
            let offset = 0;
            offset < bytes.length;
            offset += frameBytes
        ) {
            const chunk = bytes.subarray(
                offset,
                Math.min(offset + frameBytes, bytes.length),
            );
            responses = await dispatchWorkerMessage({
                chunkIndex,
                data: chunk.toString("base64"),
                fileIndex,
                kind: "projectInputFileChunk",
                requestId,
                sequence,
            });
            failure = projectErrorFrom(responses, requestId);
            if (failure) {
                bytes.fill(0);
                assert(
                    pattern.test(failure.message),
                    `Unexpected project error for ${requestId}: ${failure.message}`,
                );
                return failure;
            }
            expectInputAck(responses, requestId, sequence);
            sequence += 1;
            chunkIndex += 1;
        }

        responses = await dispatchWorkerMessage({
            chunkCount: chunkIndex,
            fileIndex,
            kind: "projectInputFileEnd",
            requestId,
            sequence,
        });
        failure = projectErrorFrom(responses, requestId);
        bytes.fill(0);
        bytes = null;
        if (failure) {
            assert(
                pattern.test(failure.message),
                `Unexpected project error for ${requestId}: ${failure.message}`,
            );
            return failure;
        }
        expectInputAck(responses, requestId, sequence);
        sequence += 1;
    }

    const responses = await dispatchWorkerMessage({
        fileCount: transferFiles.length,
        kind: "projectInputEnd",
        requestId,
        sequence,
        totalBytes,
    });
    const failure = projectErrorFrom(responses, requestId);
    assert(failure, `Worker accepted over-limit project ${requestId}.`);
    assert(
        pattern.test(failure.message),
        `Unexpected project error for ${requestId}: ${failure.message}`,
    );
    return failure;
};

const verifyWorkerRejectsUnboundProfileAuthority = async () => {
    const requestId = "desktop-bad-profile-authority";
    const validAuthority = inputArchiveAuthorityForFiles([
        {
            bytes: Buffer.from([0]),
            path: "/declarations/bad.d.ts",
        },
    ]);
    expectProjectError(
        await dispatchWorkerMessage({
            ...validAuthority,
            fileCount: 1,
            kind: "projectInputStart",
            requestId,
            sequence: 0,
            ...profileAuthorityFields,
            profileRevision: profileAuthorityFields.profileRevision + 1,
            targetProfile: "rhino",
            totalBytes: 1,
            workerSpinMilliseconds: 0,
            workerSpinPhase: "none",
        }),
        requestId,
        /malformed/i,
    );
    const unknownRequestId = "desktop-unknown-profile-field";
    expectProjectError(
        await dispatchWorkerMessage({
            ...validAuthority,
            fileCount: 1,
            kind: "projectInputStart",
            requestId: unknownRequestId,
            sequence: 0,
            ...profileAuthorityFields,
            targetProfile: "rhino",
            totalBytes: 1,
            unknown: true,
            workerSpinMilliseconds: 0,
            workerSpinPhase: "none",
        }),
        unknownRequestId,
        /malformed/i,
    );
};

const dispatchCompleteProjectInput = async (
    requestId,
    files,
    authorityOverrides,
) => {
    const encodedFiles = files.map((file) => ({
        bytes: Buffer.isBuffer(file.bytes)
            ? Buffer.from(file.bytes)
            : Buffer.from(file.text, "utf8"),
        path: file.path,
    }));
    const totalBytes = encodedFiles.reduce(
        (total, file) => total + file.bytes.length,
        0,
    );
    const authority = inputArchiveAuthorityForFiles(encodedFiles);
    let sequence = 0;
    expectInputAck(
        await dispatchWorkerMessage({
            ...authority,
            ...authorityOverrides,
            fileCount: encodedFiles.length,
            kind: "projectInputStart",
            requestId,
            sequence,
            ...profileAuthorityFields,
            targetProfile: "rhino",
            totalBytes,
            workerSpinMilliseconds: 0,
            workerSpinPhase: "none",
        }),
        requestId,
        sequence,
    );
    sequence += 1;

    for (let fileIndex = 0; fileIndex < encodedFiles.length; fileIndex += 1) {
        const file = encodedFiles[fileIndex];
        expectInputAck(
            await dispatchWorkerMessage({
                byteLength: file.bytes.length,
                fileIndex,
                kind: "projectInputFileStart",
                path: file.path,
                requestId,
                sequence,
                sha256: digestBytes(file.bytes),
            }),
            requestId,
            sequence,
        );
        sequence += 1;
        expectInputAck(
            await dispatchWorkerMessage({
                chunkIndex: 0,
                data: file.bytes.toString("base64"),
                fileIndex,
                kind: "projectInputFileChunk",
                requestId,
                sequence,
            }),
            requestId,
            sequence,
        );
        sequence += 1;
        expectInputAck(
            await dispatchWorkerMessage({
                chunkCount: 1,
                fileIndex,
                kind: "projectInputFileEnd",
                requestId,
                sequence,
            }),
            requestId,
            sequence,
        );
        sequence += 1;
        file.bytes.fill(0);
    }

    return dispatchWorkerMessage({
        fileCount: encodedFiles.length,
        kind: "projectInputEnd",
        requestId,
        sequence,
        totalBytes,
    });
};

const verifyWorkerRejectsEveryTamperedAcceptedInputAuthorityField = async () => {
    const originalTs = global.ts;
    const originalCreateIncrementalProgram = originalTs.createIncrementalProgram;
    let programInvocations = 0;
    const fields = [
        ["callerInventoryFingerprint", "0".repeat(64)],
        ["retainedInventoryFingerprint", "0".repeat(64)],
        ["declarationInventoryFingerprint", "0".repeat(64)],
        ["declarationFileCount", 2],
        ["declarationByteLength", 2],
        ["largestFileBytes", 2],
    ];
    const files = [
        {
            bytes: Buffer.from("d", "utf8"),
            path: "/declarations/s1-authority.d.ts",
        },
        {
            bytes: Buffer.from("s", "utf8"),
            path: "/sources/s1-authority.ts",
        },
    ];
    global.ts = new Proxy(originalTs, {
        get(target, property, receiver) {
            if (property === "createIncrementalProgram") {
                return (...args) => {
                    programInvocations += 1;
                    return originalCreateIncrementalProgram(...args);
                };
            }
            return Reflect.get(target, property, receiver);
        },
    });
    try {
        for (const [field, value] of fields) {
            const requestId = `desktop-s1-tampered-${field}`;
            const programsBeforeFailure = programInvocations;
            expectProjectError(
                await dispatchCompleteProjectInput(
                    requestId,
                    files,
                    { [field]: value },
                ),
                requestId,
                /authority.*retained inventory|retained inventory.*authority/i,
            );
            assert(
                programInvocations === programsBeforeFailure,
                `Worker created a Program for tampered ${field}.`,
            );
            assert(
                workerBinding("framedInput") === null &&
                    workerBinding("projectOperationActive")() === false,
                `Worker retained ingress state after tampered ${field}.`,
            );
            const { result } = await compile(
                `desktop-s1-tampered-${field}-successor`,
                snapshot(fixture.success.mainSource),
            );
            verifySuccess(result);
            assert(
                programInvocations === programsBeforeFailure + 1,
                `Worker did not run exactly one clean successor after tampered ${field}.`,
            );
        }
    } finally {
        global.ts = originalTs;
    }
    process.stdout.write(
        "S1 accepted-input authority tamper rejection passed: " +
            "fields=6, clean successors=6, failure Program=0.\n",
    );
};

const beginOneFileTransfer = async (
    requestId,
    bytes,
    declaredSha256 = digestBytes(bytes),
) => {
    const authority = inputArchiveAuthorityForFiles([
        {
            bytes,
            path: "/declarations/bad.d.ts",
        },
    ]);
    expectInputAck(
        await dispatchWorkerMessage({
            ...authority,
            fileCount: 1,
            kind: "projectInputStart",
            requestId,
            sequence: 0,
            ...profileAuthorityFields,
            targetProfile: "rhino",
            totalBytes: bytes.length,
            workerSpinMilliseconds: 0,
            workerSpinPhase: "none",
        }),
        requestId,
        0,
    );
    expectInputAck(
        await dispatchWorkerMessage({
            byteLength: bytes.length,
            fileIndex: 0,
            kind: "projectInputFileStart",
            path: "/declarations/bad.d.ts",
            requestId,
            sequence: 1,
            sha256: declaredSha256,
        }),
        requestId,
        1,
    );
};

const verifyWorkerRejectsBadDigest = async () => {
    const requestId = "desktop-bad-digest";
    const bytes = Buffer.from("export const value = 1;\n", "utf8");
    await beginOneFileTransfer(requestId, bytes, "0".repeat(64));
    expectInputAck(
        await dispatchWorkerMessage({
            chunkIndex: 0,
            data: bytes.toString("base64"),
            fileIndex: 0,
            kind: "projectInputFileChunk",
            requestId,
            sequence: 2,
        }),
        requestId,
        2,
    );
    expectProjectError(
        await dispatchWorkerMessage({
            chunkCount: 1,
            fileIndex: 0,
            kind: "projectInputFileEnd",
            requestId,
            sequence: 3,
        }),
        requestId,
        /digest/i,
    );
};

const verifyWorkerRejectsInvalidUtf8 = async () => {
    const requestId = "desktop-bad-utf8";
    const bytes = Buffer.from([0xc3, 0x28]);
    await beginOneFileTransfer(requestId, bytes);
    expectInputAck(
        await dispatchWorkerMessage({
            chunkIndex: 0,
            data: bytes.toString("base64"),
            fileIndex: 0,
            kind: "projectInputFileChunk",
            requestId,
            sequence: 2,
        }),
        requestId,
        2,
    );
    expectProjectError(
        await dispatchWorkerMessage({
            chunkCount: 1,
            fileIndex: 0,
            kind: "projectInputFileEnd",
            requestId,
            sequence: 3,
        }),
        requestId,
        /UTF-8/i,
    );
};

const verifyWorkerRejectsNonCanonicalBase64 = async () => {
    const requestId = "desktop-bad-base64";
    const bytes = Buffer.from([0]);
    await beginOneFileTransfer(requestId, bytes);
    expectProjectError(
        await dispatchWorkerMessage({
            chunkIndex: 0,
            data: "A===",
            fileIndex: 0,
            kind: "projectInputFileChunk",
            requestId,
            sequence: 2,
        }),
        requestId,
        /base64/i,
    );
};

const verifyP3bUniqueFatalDecodeAndNoVfsReencode = async () => {
    const NativeTextDecoder = global.TextDecoder;
    const NativeTextEncoder = global.TextEncoder;
    const decoderRecords = [];
    const encodedInputs = [];
    class TrackingTextDecoder extends NativeTextDecoder {
        constructor(label, options) {
            super(label, options);
            this.p3bRecord = {
                decodeCalls: 0,
                fatal: options?.fatal === true,
                ignoreBOM: options?.ignoreBOM === true,
            };
            decoderRecords.push(this.p3bRecord);
        }

        decode(input, options) {
            this.p3bRecord.decodeCalls += 1;
            return super.decode(input, options);
        }
    }
    class TrackingTextEncoder extends NativeTextEncoder {
        encode(input) {
            encodedInputs.push(input);
            return super.encode(input);
        }
    }

    const markerSource = fixture.success.mainSource;
    const project = snapshot(markerSource);
    let result;
    global.TextDecoder = TrackingTextDecoder;
    global.TextEncoder = TrackingTextEncoder;
    try {
        ({ result } = await compile("desktop-p3b-single-decode", project));
    } finally {
        global.TextDecoder = NativeTextDecoder;
        global.TextEncoder = NativeTextEncoder;
    }
    verifySuccess(result);
    const inputDecoders = decoderRecords.filter(
        (record) => record.fatal && record.ignoreBOM,
    );
    assert(
        inputDecoders.length === project.files.length,
        "Worker did not create exactly one fatal UTF-8 decoder per input file.",
    );
    assert(
        inputDecoders.every((record) => record.decodeCalls === 1),
        "Worker did not decode each input file exactly once.",
    );
    assert(
        !encodedInputs.some((input) => input === markerSource),
        "Worker re-encoded source text after framed UTF-8 validation.",
    );
};

const verifyP3bFatalDecodeAcrossFrameBoundary = async () => {
    const requestId = "desktop-p3b-split-invalid-utf8";
    const bytes = Buffer.alloc(frameBytes + 1, 0x61);
    bytes[frameBytes - 1] = 0xc3;
    bytes[frameBytes] = 0x28;
    await beginOneFileTransfer(requestId, bytes);
    expectInputAck(
        await dispatchWorkerMessage({
            chunkIndex: 0,
            data: bytes.subarray(0, frameBytes).toString("base64"),
            fileIndex: 0,
            kind: "projectInputFileChunk",
            requestId,
            sequence: 2,
        }),
        requestId,
        2,
    );
    expectInputAck(
        await dispatchWorkerMessage({
            chunkIndex: 1,
            data: bytes.subarray(frameBytes).toString("base64"),
            fileIndex: 0,
            kind: "projectInputFileChunk",
            requestId,
            sequence: 3,
        }),
        requestId,
        3,
    );
    expectProjectError(
        await dispatchWorkerMessage({
            chunkCount: 2,
            fileIndex: 0,
            kind: "projectInputFileEnd",
            requestId,
            sequence: 4,
        }),
        requestId,
        /UTF-8/i,
    );
    bytes.fill(0);
};

const verifyP3bScannerLimits = async () => {
    const countSourceTokens = workerBinding("countSourceTokens");
    assert(
        countSourceTokens("export const value = 1;\n") > 0,
        "Worker scanner did not count a valid source.",
    );
    await expectProjectRejectedDuringTransfer(
        "desktop-p3b-file-token-overflow",
        [
            {
                path: "/sources/token-overflow.ts",
                text: ";".repeat(p3bWorkerLimits.maximumTokensPerFile + 1),
            },
        ],
        /token.*file|file.*token/i,
    );

    const projectTokenFiles = Array.from({ length: 5 }, (_, index) => ({
        path: `/sources/project-token-${index}.ts`,
        text: ";".repeat(60_000),
    }));
    assert(
        projectTokenFiles.every(
            (file) =>
                countSourceTokens(file.text) <=
                p3bWorkerLimits.maximumTokensPerFile,
        ),
        "Project-token fixture accidentally exceeds the per-file token limit.",
    );
    assert(
        projectTokenFiles.reduce(
            (total, file) => total + countSourceTokens(file.text),
            0,
        ) > p3bWorkerLimits.maximumProjectTokens,
        "Project-token fixture does not exceed the aggregate token limit.",
    );
    await expectProjectRejectedDuringTransfer(
        "desktop-p3b-project-token-overflow",
        projectTokenFiles,
        /token.*project|project.*token/i,
    );
};

const verifyP3bAggregatePathAndDirectoryGates = () => {
    const createDirectoryIndex = workerBinding("createDirectoryIndex");

    const longFileStem = "f".repeat(4_070);
    const pathOverflow = Array.from({ length: 2_060 }, (_, index) => {
        const suffix = index.toString(16).padStart(4, "0");
        return `/sources/${longFileStem.slice(0, -suffix.length)}${suffix}.ts`;
    });
    assert(
        pathOverflow.reduce((total, entry) => total + entry.length, 0) >
            p3bWorkerLimits.maximumPathChars,
        "Path-overflow fixture does not exceed the aggregate path limit.",
    );
    expectThrows(
        () => createDirectoryIndex(pathOverflow),
        /path.*limit|aggregate.*path/i,
        "Worker accepted an aggregate VFS path-character overflow.",
    );

    const directoryCharOverflow = Array.from({ length: 1_040 }, (_, index) => {
        const unique = index.toString(16).padStart(4, "0");
        const segment = `${unique}${"d".repeat(4_060 - unique.length)}`;
        return `/sources/${segment}/f.ts`;
    });
    expectThrows(
        () => createDirectoryIndex(directoryCharOverflow),
        /director.*character|director.*char/i,
        "Worker accepted an aggregate directory-character overflow.",
    );

    const directoryNodeOverflow = Array.from({ length: 1_060 }, (_, index) => {
        const segments = Array.from(
            { length: 62 },
            (_unused, depth) => `d${index.toString(36)}_${depth.toString(36)}`,
        );
        return `/sources/${segments.join("/")}/f.ts`;
    });
    expectThrows(
        () => createDirectoryIndex(directoryNodeOverflow),
        /director.*node/i,
        "Worker accepted an aggregate directory-node overflow.",
    );
};

const verifyP3bLazySourceFileIdentityAndVersion = async () => {
    const originalTs = global.ts;
    const originalCreateSourceFile = originalTs.createSourceFile;
    const originalCreateIncrementalProgram =
        originalTs.createIncrementalProgram;
    let sourceFileCreations = 0;
    let inspectedCompilerHost = false;
    global.ts = new Proxy(originalTs, {
        get(target, property, receiver) {
            if (property === "createSourceFile") {
                return (...args) => {
                    sourceFileCreations += 1;
                    return originalCreateSourceFile(...args);
                };
            }
            if (property === "createIncrementalProgram") {
                return (configuration) => {
                    assert(
                        sourceFileCreations === 0,
                        "Worker eagerly parsed a SourceFile before Program construction.",
                    );
                    const rootName = configuration.rootNames[0];
                    const first = configuration.host.getSourceFile(
                        rootName,
                        configuration.options.target,
                    );
                    const creationsAfterFirstRead = sourceFileCreations;
                    const second = configuration.host.getSourceFile(
                        rootName,
                        configuration.options.target,
                    );
                    assert(
                        first === second,
                        "Compiler host returned different SourceFiles for one canonical path.",
                    );
                    assert(
                        typeof first?.version === "string" &&
                            /^[0-9a-f]{64}$/.test(first.version),
                        "Compiler host cached SourceFile has no accepted content version.",
                    );
                    assert(
                        sourceFileCreations === creationsAfterFirstRead,
                        "Compiler host reparsed a cached SourceFile.",
                    );
                    inspectedCompilerHost = true;
                    return originalCreateIncrementalProgram(configuration);
                };
            }
            return Reflect.get(target, property, receiver);
        },
    });

    let result;
    try {
        ({ result } = await compile(
            "desktop-p3b-lazy-source-file",
            snapshot(fixture.success.mainSource),
        ));
    } finally {
        global.ts = originalTs;
    }
    assert(inspectedCompilerHost, "Desktop golden did not inspect the compiler host.");
    verifySuccess(result);
};

const t3TwentyModuleSnapshot = (lastValue) => {
    const declarations = fixture.commonFiles.filter((file) =>
        file.path.startsWith("/declarations/"),
    );
    const sources = Array.from({ length: 20 }, (_, index) => {
        const name = index.toString().padStart(2, "0");
        const previous = (index - 1).toString().padStart(2, "0");
        const typeBallast = Array.from(
            { length: 600 },
            (_unused, typeIndex) =>
                `interface Shape${name}_${typeIndex} { ` +
                `readonly key: "${name}-${typeIndex}"; ` +
                "readonly tuple: readonly [number, string, boolean]; }",
        ).join("\n");
        const executable =
            index === 0
                ? "export const value00 = 1;\n"
                : index === 19
                  ? `import { value${previous} } from "./module-${previous}";\n` +
                    `export const value${name} = value${previous} + ${lastValue};\n`
                  : `import { value${previous} } from "./module-${previous}";\n` +
                    `export const value${name} = value${previous} + 1;\n`;
        return {
            path: `/sources/module-${name}.ts`,
            text: `${typeBallast}\n${executable}`,
        };
    });
    return {
        files: declarations.concat(sources),
        rootNames: sources.map((source) => source.path),
        targetProfile: fixture.targetProfile,
    };
};

const verifyT3ResidentProjectSessionIncrementality = async () => {
    const originalTs = global.ts;
    const originalCreateIncrementalProgram =
        originalTs.createIncrementalProgram;
    const runs = [];
    global.ts = new Proxy(originalTs, {
        get(target, property, receiver) {
            if (property === "createIncrementalProgram") {
                return (configuration) => {
                    const run = {
                        emittedOutputs: 0,
                        reusedBuilder:
                            typeof configuration.createProgram === "function",
                    };
                    runs.push(run);
                    const writeFile = configuration.host.writeFile;
                    configuration.host.writeFile = (...args) => {
                        run.emittedOutputs += 1;
                        return writeFile(...args);
                    };
                    return originalCreateIncrementalProgram(configuration);
                };
            }
            return Reflect.get(target, property, receiver);
        },
    });

    const projectSessionKey = digestBytes(
        Buffer.from("t3-resident-project-a", "utf8"),
    );
    const otherProjectSessionKey = digestBytes(
        Buffer.from("t3-resident-project-b", "utf8"),
    );
    const thirdProjectSessionKey = digestBytes(
        Buffer.from("t3-resident-project-c", "utf8"),
    );
    const fourthProjectSessionKey = digestBytes(
        Buffer.from("t3-resident-project-d", "utf8"),
    );
    let cold;
    let warm;
    let isolated;
    let thirdProject;
    let fourthProject;
    let evictedReplay;
    let legacyFirst;
    let legacySecond;
    try {
        cold = await compile(
            "desktop-t3-resident-cold",
            t3TwentyModuleSnapshot(1),
            { projectSessionKey },
        );
        warm = await compile(
            "desktop-t3-resident-warm",
            t3TwentyModuleSnapshot(2),
            { projectSessionKey },
        );
        isolated = await compile(
            "desktop-t3-resident-isolated-project",
            t3TwentyModuleSnapshot(2),
            { projectSessionKey: otherProjectSessionKey },
        );
        thirdProject = await compile(
            "desktop-t3-resident-third-project",
            t3TwentyModuleSnapshot(2),
            { projectSessionKey: thirdProjectSessionKey },
        );
        fourthProject = await compile(
            "desktop-t3-resident-fourth-project",
            t3TwentyModuleSnapshot(2),
            { projectSessionKey: fourthProjectSessionKey },
        );
        evictedReplay = await compile(
            "desktop-t3-resident-evicted-replay",
            t3TwentyModuleSnapshot(2),
            { projectSessionKey },
        );
        legacyFirst = await compile(
            "desktop-t3-legacy-cold-one",
            t3TwentyModuleSnapshot(2),
        );
        legacySecond = await compile(
            "desktop-t3-legacy-cold-two",
            t3TwentyModuleSnapshot(3),
        );
    } finally {
        global.ts = originalTs;
    }

    assert(runs.length === 8, "T3 verifier observed an unexpected Program count.");
    const expectedOutputCount = 40;
    assert(
        runs[0].emittedOutputs === expectedOutputCount &&
            !runs[0].reusedBuilder,
        "T3 cold session did not execute one full cold emit.",
    );
    assert(
        runs[1].reusedBuilder &&
            runs[1].emittedOutputs <= expectedOutputCount * 0.3,
        "T3 warm session did not reuse the builder and affected-module emit.",
    );
    assert(
        !runs[2].reusedBuilder &&
            runs[2].emittedOutputs === expectedOutputCount,
        "T3 leaked resident state across project session identities.",
    );
    assert(
        !runs[3].reusedBuilder &&
            !runs[4].reusedBuilder &&
            !runs[5].reusedBuilder &&
            runs[3].emittedOutputs === expectedOutputCount &&
            runs[4].emittedOutputs === expectedOutputCount &&
            runs[5].emittedOutputs === expectedOutputCount,
        "T3 did not evict the oldest project after the three-session bound.",
    );
    assert(
        !runs[6].reusedBuilder &&
            !runs[7].reusedBuilder &&
            runs[6].emittedOutputs === expectedOutputCount &&
            runs[7].emittedOutputs === expectedOutputCount,
        "T3 changed the legacy fully cold compilation path.",
    );
    for (const compilation of [
        cold,
        warm,
        isolated,
        thirdProject,
        fourthProject,
        evictedReplay,
        legacyFirst,
        legacySecond,
    ]) {
        assert(
            compilation.result.diagnostics.length === 0 &&
                !compilation.result.emitSkipped &&
                compilation.result.outputs.length === expectedOutputCount,
            "T3 compilation lost the complete output inventory.",
        );
    }
    const warmModule = warm.result.outputs.find(
        (output) => output.path === "/outputs/module-19.js",
    );
    assert(
        warmModule?.text.includes("value18 + 2"),
        "T3 warm compilation returned a stale changed-module output.",
    );
    const reduction =
        cold.endElapsedMilliseconds === 0
            ? 0
            : 1 - warm.endElapsedMilliseconds / cold.endElapsedMilliseconds;
    process.stdout.write(
        `T3 resident session: cold=${cold.endElapsedMilliseconds} ms, ` +
            `warm=${warm.endElapsedMilliseconds} ms, ` +
            `warm-emits=${runs[1].emittedOutputs}/${expectedOutputCount}, ` +
            `compile-reduction=${(reduction * 100).toFixed(1)}%.\n`,
    );
};

const verifyP3bIncrementalDiagnosticCap = async () => {
    const originalTs = global.ts;
    let emitCalls = 0;
    const diagnostics = Array.from({ length: 1_001 }, (_, index) => ({
            category: originalTs.DiagnosticCategory.Error,
            code: 90_000 + index,
            file: undefined,
            length: undefined,
            messageText: "x",
            start: undefined,
        }));
    Object.defineProperty(diagnostics[1_000], "messageText", {
        configurable: true,
        get() {
            throw new Error(
                "Diagnostic collector materialized an item beyond its count budget.",
            );
        },
    });
    const program = {
        getGlobalDiagnostics: () => [],
        getOptionsDiagnostics: () => diagnostics,
        getSemanticDiagnostics: () => [],
        getSourceFiles: () => [],
        getSyntacticDiagnostics: () => [],
    };
    const builder = {
        emit: () => {
            emitCalls += 1;
            return {
                diagnostics: [],
                emitSkipped: true,
            };
        },
        getGlobalDiagnostics: program.getGlobalDiagnostics,
        getOptionsDiagnostics: program.getOptionsDiagnostics,
        getProgram: () => program,
        getSemanticDiagnostics: program.getSemanticDiagnostics,
        getSyntacticDiagnostics: program.getSyntacticDiagnostics,
    };
    global.ts = new Proxy(originalTs, {
        get(target, property, receiver) {
            if (property === "createIncrementalProgram") {
                return () => builder;
            }
            return Reflect.get(target, property, receiver);
        },
    });
    try {
        await expectProjectRejectedDuringTransfer(
            "desktop-p3b-diagnostic-cap",
            snapshot(fixture.success.mainSource).files,
            /diagnostic.*limit|diagnostics exceed/i,
        );
    } finally {
        global.ts = originalTs;
    }
    assert(
        emitCalls === 0,
        "Worker emitted after the incremental diagnostic collector exceeded its limit.",
    );
};

const verifyP3bSkipsEmitOnPreEmitErrors = async () => {
    const originalTs = global.ts;
    const originalCreateIncrementalProgram =
        originalTs.createIncrementalProgram;
    let emitCalls = 0;
    global.ts = new Proxy(originalTs, {
        get(target, property, receiver) {
            if (property === "createIncrementalProgram") {
                return (configuration) => {
                    const builder =
                        originalCreateIncrementalProgram(configuration);
                    return new Proxy(builder, {
                        get(builderTarget, builderProperty, builderReceiver) {
                            if (builderProperty === "emit") {
                                return (...args) => {
                                    emitCalls += 1;
                                    return Reflect.apply(
                                        builderTarget.emit,
                                        builderTarget,
                                        args,
                                    );
                                };
                            }
                            return Reflect.get(
                                builderTarget,
                                builderProperty,
                                builderReceiver,
                            );
                        },
                    });
                };
            }
            return Reflect.get(target, property, receiver);
        },
    });

    let result;
    try {
        ({ result } = await compile(
            "desktop-p3b-no-emit-on-error",
            snapshot(fixture.semanticError.mainSource),
        ));
    } finally {
        global.ts = originalTs;
    }
    verifySemanticError(result);
    assert(
        emitCalls === 0,
        "Worker called builder.emit() after pre-emit diagnostics contained errors.",
    );
};

const x2DiagnosticMethodByPhase = Object.freeze({
    global: "getGlobalDiagnostics",
    options: "getOptionsDiagnostics",
    semantic: "getSemanticDiagnostics",
    syntactic: "getSyntacticDiagnostics",
});

const createX2InMemoryCompilerHost = (tsApi, options, files = new Map()) => ({
    directoryExists: () => false,
    fileExists: (fileName) => files.has(fileName),
    getCanonicalFileName: (fileName) => fileName,
    getCurrentDirectory: () => "/",
    getDefaultLibFileName: () => "/declarations/lib.d.ts",
    getDirectories: () => [],
    getNewLine: () => "\n",
    getSourceFile: (fileName, languageVersion) => {
        const text = files.get(fileName);
        return text === undefined
            ? undefined
            : tsApi.createSourceFile(
                  fileName,
                  text,
                  languageVersion,
                  true,
              );
    },
    readFile: (fileName) => files.get(fileName),
    realpath: (fileName) => fileName,
    useCaseSensitiveFileNames: () => true,
    writeFile: () => {},
});

const createX2ActualOptionsDiagnostic = (tsApi) => {
    const options = {
        declarationMap: false,
        mapRoot: "/x2-maps",
        noLib: true,
        sourceMap: false,
    };
    const program = tsApi.createProgram({
        host: createX2InMemoryCompilerHost(tsApi, options),
        options,
        rootNames: [],
    });
    const diagnostics = program.getOptionsDiagnostics();
    assert(
        diagnostics.length === 1 &&
            diagnostics[0].category === tsApi.DiagnosticCategory.Error &&
            diagnostics[0].code === 5069 &&
            diagnostics[0].file === undefined &&
            diagnostics[0].start === undefined &&
            diagnostics[0].length === undefined,
        "X2 TS 6.0.3 invalid-options Program diagnostic drifted.",
    );
    return diagnostics[0];
};

const createX2ActualGlobalDiagnostic = (tsApi) => {
    const sourcePath = "/sources/x2-global-oracle.ts";
    const files = new Map([
        [
            sourcePath,
            [
                "interface Array<T> { readonly length: number; [n: number]: T; }",
                "interface Boolean {}",
                "interface CallableFunction extends Function {}",
                "interface Function {}",
                "interface IArguments { length: number; [n: number]: any; }",
                "interface NewableFunction extends Function {}",
                "interface Number {}",
                "interface Object {}",
                "interface RegExp {}",
                "",
            ].join("\n"),
        ],
    ]);
    const options = {
        noLib: true,
        noResolve: true,
        target: tsApi.ScriptTarget.ES2018,
    };
    const program = tsApi.createProgram({
        host: createX2InMemoryCompilerHost(tsApi, options, files),
        options,
        rootNames: [sourcePath],
    });
    const diagnostics = program.getGlobalDiagnostics();
    assert(
        diagnostics.length === 1 &&
            diagnostics[0].category === tsApi.DiagnosticCategory.Error &&
            diagnostics[0].code === 2318 &&
            diagnostics[0].file === undefined &&
            diagnostics[0].start === undefined &&
            diagnostics[0].length === undefined,
        "X2 TS 6.0.3 missing-global Program diagnostic drifted.",
    );
    return diagnostics[0];
};

// Desktop-only defensive seam: real diagnostics obtained from independent
// TypeScript Programs are exposed by a Proxy around the otherwise valid
// production-shaped Program. No request field or shipping Worker hook exists.
const withX2CompilerDiagnosticProbe = async (
    injectedDiagnosticsByPhase,
    operation,
) => {
    const originalTs = global.ts;
    const originalCreateIncrementalProgram =
        originalTs.createIncrementalProgram;
    let builderCreations = 0;
    let builderEmitCalls = 0;
    global.ts = new Proxy(originalTs, {
        get(target, property, receiver) {
            if (property !== "createIncrementalProgram") {
                return Reflect.get(target, property, receiver);
            }
            return (configuration) => {
                builderCreations += 1;
                const builder = originalCreateIncrementalProgram(configuration);
                const program = builder.getProgram();
                const programProxy = new Proxy(program, {
                    get(programTarget, programProperty, programReceiver) {
                        const phase = Object.entries(
                            x2DiagnosticMethodByPhase,
                        ).find(
                            ([_phase, method]) => method === programProperty,
                        )?.[0];
                        if (phase !== undefined) {
                            return (...args) => {
                                const originalDiagnostics = Reflect.apply(
                                    programTarget[programProperty],
                                    programTarget,
                                    args,
                                );
                                const injected =
                                    injectedDiagnosticsByPhase[phase] || [];
                                return originalDiagnostics.concat(injected);
                            };
                        }
                        return Reflect.get(
                            programTarget,
                            programProperty,
                            programReceiver,
                        );
                    },
                });
                return new Proxy(builder, {
                    get(builderTarget, builderProperty, builderReceiver) {
                        if (builderProperty === "emit") {
                            return (...args) => {
                                builderEmitCalls += 1;
                                return Reflect.apply(
                                    builderTarget.emit,
                                    builderTarget,
                                    args,
                                );
                            };
                        }
                        if (builderProperty === "getProgram") {
                            return () => programProxy;
                        }
                        return Reflect.get(
                            builderTarget,
                            builderProperty,
                            builderReceiver,
                        );
                    },
                });
            };
        },
    });

    let value;
    try {
        value = await operation();
    } finally {
        global.ts = originalTs;
    }
    assert(
        builderCreations === 1,
        `X2 diagnostic probe expected one builder; received ${builderCreations}.`,
    );
    return { builderEmitCalls, value };
};

const verifyX2Diagnostic = (actual, expected, label) => {
    const fields = x2Fixture.diagnosticFields;
    assert(
        Object.keys(actual).length === fields.length &&
            fields.every((field) =>
                Object.prototype.hasOwnProperty.call(actual, field),
            ),
        `${label} did not use the unified nine-field diagnostic model.`,
    );
    for (const field of fields) {
        assert(
            actual[field] === expected[field],
            `${label} diagnostic mismatch for ${field}: ` +
                `${JSON.stringify(actual[field])} !== ` +
                `${JSON.stringify(expected[field])}.`,
        );
    }
};

const verifyX2StoppedEmit = (probe, expected, label) => {
    const result = probe.value.result;
    assert(
        probe.builderEmitCalls === expected.builderEmitCalls,
        `${label} builder.emit() call count drifted: ` +
            `${probe.builderEmitCalls}.`,
    );
    assert(
        result.emitSkipped === true &&
            result.emitSkipped === expected.emitSkipped,
        `${label} did not return emitSkipped === true.`,
    );
    assert(
        Array.isArray(result.outputs) &&
            result.outputs.length === 0 &&
            JSON.stringify(result.outputs) === JSON.stringify(expected.outputs),
        `${label} did not return outputs.length === 0.`,
    );
    assert(
        result.diagnostics.length === 1,
        `${label} did not return exactly one diagnostic.`,
    );
    return result.diagnostics[0];
};

const verifyX2CleanSuccessor = async (requestId) => {
    const probe = await withX2CompilerDiagnosticProbe({}, () =>
        compile(requestId, snapshot(fixture.success.mainSource)),
    );
    assert(
        probe.builderEmitCalls ===
            x2Fixture.execution.expectedCleanBuilderEmitCalls,
        `${requestId} did not call builder.emit() exactly once.`,
    );
    verifySuccess(probe.value.result);
};

const verifyX2UnifiedNoEmitDiagnostics = async () => {
    assert(
        x2Fixture.schema ===
            "autojs6.typescript.r2-m2-x2.unified-no-emit-diagnostics" &&
            x2Fixture.revision === 1 &&
            x2Fixture.typescriptVersion === expectedVersion &&
            x2Fixture.scope === "desktop-worker-only/no-production-hook" &&
            JSON.stringify(x2Fixture.phasePrecedence) ===
                JSON.stringify(["options", "syntactic", "global", "semantic"]),
        "X2 unified diagnostic fixture authority drifted.",
    );
    const originalTs = global.ts;
    const optionsDiagnostic = createX2ActualOptionsDiagnostic(originalTs);
    const globalDiagnostic = createX2ActualGlobalDiagnostic(originalTs);
    const actualDiagnosticByInjectedPhase = Object.freeze({
        global: globalDiagnostic,
        options: optionsDiagnostic,
    });

    for (const fixtureCase of x2Fixture.errorCases) {
        const injectedDiagnosticsByPhase = {};
        if (fixtureCase.origin === "desktop-only-proxy-injection") {
            injectedDiagnosticsByPhase[fixtureCase.phase] = [
                actualDiagnosticByInjectedPhase[fixtureCase.phase],
            ];
        } else {
            assert(
                fixtureCase.origin === "real-typescript-source" &&
                    (fixtureCase.phase === "syntactic" ||
                        fixtureCase.phase === "semantic"),
                `${fixtureCase.id} uses an unsupported X2 diagnostic origin.`,
            );
        }
        const probe = await withX2CompilerDiagnosticProbe(
            injectedDiagnosticsByPhase,
            () =>
                compile(
                    `desktop-x2-${fixtureCase.id}`,
                    snapshot(fixtureCase.mainSource),
                ),
        );
        const actual = verifyX2StoppedEmit(
            probe,
            fixtureCase.expected,
            fixtureCase.id,
        );
        verifyX2Diagnostic(actual, fixtureCase.diagnostic, fixtureCase.id);
        if (
            fixtureCase.phase === "options" ||
            fixtureCase.phase === "global"
        ) {
            assert(
                actual.sourcePath === null &&
                    actual.start === null &&
                    actual.length === null &&
                    actual.line === null &&
                    actual.column === null,
                `${fixtureCase.id} did not preserve explicit null source/range fields.`,
            );
        }
        await verifyX2CleanSuccessor(fixtureCase.cleanSuccessorRequestId);
    }

    const duplicateCase = x2Fixture.duplicatePrecedenceCase;
    const identicalByPhase = Object.fromEntries(
        duplicateCase.injectedPhases.map((phase) => [
            phase,
            [optionsDiagnostic],
        ]),
    );
    const duplicateProbe = await withX2CompilerDiagnosticProbe(
        identicalByPhase,
        () =>
            compile(
                `desktop-x2-${duplicateCase.id}`,
                snapshot(duplicateCase.mainSource),
            ),
    );
    const duplicateDiagnostic = verifyX2StoppedEmit(
        duplicateProbe,
        duplicateCase.expected,
        duplicateCase.id,
    );
    verifyX2Diagnostic(
        duplicateDiagnostic,
        duplicateCase.diagnostic,
        duplicateCase.id,
    );
    assert(
        duplicateDiagnostic.phase === x2Fixture.phasePrecedence[0],
        "X2 phase-less diagnostic identity did not keep first-phase precedence.",
    );
    await verifyX2CleanSuccessor(duplicateCase.cleanSuccessorRequestId);

    const warningCase = x2Fixture.warningCase;
    const warningDiagnostic = Object.freeze({
        ...optionsDiagnostic,
        category: originalTs.DiagnosticCategory.Warning,
    });
    const warningProbe = await withX2CompilerDiagnosticProbe(
        { options: [warningDiagnostic] },
        () =>
            compile(
                `desktop-x2-${warningCase.id}`,
                snapshot(warningCase.mainSource),
            ),
    );
    const warningResult = warningProbe.value.result;
    assert(
        warningProbe.builderEmitCalls ===
            warningCase.expected.builderEmitCalls &&
            warningResult.emitSkipped === false &&
            warningResult.emitSkipped === warningCase.expected.emitSkipped &&
            warningResult.outputs.length === warningCase.expected.outputCount,
        "X2 Warning diagnostic blocked builder.emit() or discarded outputs.",
    );
    assert(
        warningResult.diagnostics.length === 1,
        "X2 Warning pass-through did not return exactly one diagnostic.",
    );
    verifyX2Diagnostic(
        warningResult.diagnostics[0],
        warningCase.diagnostic,
        warningCase.id,
    );
    verifySuccess({ ...warningResult, diagnostics: [] });
    await verifyX2CleanSuccessor(warningCase.cleanSuccessorRequestId);
};

const exactAsciiFile = (body, byteLength) => {
    const fixedBytes = Buffer.byteLength(body, "utf8");
    assert(
        fixedBytes <= byteLength,
        "P3b boundary fixture body exceeds its requested byte length.",
    );
    const text = body + " ".repeat(byteLength - fixedBytes);
    assert(
        Buffer.byteLength(text, "utf8") === byteLength,
        "P3b boundary fixture has an inexact UTF-8 byte length.",
    );
    return text;
};

const p3bBoundaryProject = () => {
    const perFileBytes = 8 * 1024 * 1024;
    const baseLibrary = fixture.commonFiles.find(
        (file) => file.path === "/declarations/lib.es2018.d.ts",
    );
    assert(baseLibrary, "P3b boundary fixture cannot find controlled lib.es2018.d.ts.");
    const declarations = [
        {
            path: "/declarations/lib.d.ts",
            text: "",
        },
        {
            path: "/declarations/lib.es2018.d.ts",
            text: exactAsciiFile(baseLibrary.text, perFileBytes),
        },
    ];
    const sources = Array.from({ length: 8 }, (_, index) => ({
        path: `/sources/p3b-boundary-${index}.ts`,
        text: exactAsciiFile(
            `export const p3bBoundary${index}: number = ${index};\n`,
            perFileBytes,
        ),
    }));
    const callerBytes = sources.reduce(
        (total, file) => total + Buffer.byteLength(file.text, "utf8"),
        0,
    );
    const trustedBytes = declarations.reduce(
        (total, file) => total + Buffer.byteLength(file.text, "utf8"),
        0,
    );
    assert(
        callerBytes === 64 * 1024 * 1024,
        "P3b boundary fixture is not exactly 64 MiB of caller source.",
    );
    assert(
        trustedBytes === 8 * 1024 * 1024,
        "P3b boundary fixture is not exactly 8 MiB of trusted declarations.",
    );
    return {
        files: declarations.concat(sources),
        rootNames: sources.map((file) => file.path),
        targetProfile: fixture.targetProfile,
    };
};

const collectRetainedMemory = async () => {
    assert(
        typeof global.gc === "function",
        "P3b retained-heap golden requires Node --expose-gc.",
    );
    for (let attempt = 0; attempt < 4; attempt += 1) {
        global.gc();
        await new Promise((resolve) => setImmediate(resolve));
    }
    const usage = process.memoryUsage();
    return {
        arrayBuffers: usage.arrayBuffers,
        external: usage.external,
        heapUsed: usage.heapUsed,
        rss: usage.rss,
    };
};

const compileAndDiscard = async (requestId, project, verification) => {
    const { result } = await compile(requestId, project);
    verification(result);
};

const formatMiB = (bytes) => (bytes / (1024 * 1024)).toFixed(1);

const runP3bRetainedHeapChild = async () => {
    assert(
        typeof global.gc === "function",
        "P3b retained-heap child was not launched with --expose-gc.",
    );
    const heapLimit = v8.getHeapStatistics().heap_size_limit;
    assert(
        process.execArgv.includes("--max-old-space-size=512") &&
            heapLimit <= 768 * 1024 * 1024,
        "P3b retained-heap child is not running with the exact 512 MiB old-space flag.",
    );

    await compileAndDiscard(
        "desktop-p3b-memory-warmup",
        snapshot(fixture.success.mainSource),
        verifySuccess,
    );
    const baseline = await collectRetainedMemory();

    await compileAndDiscard(
        "desktop-p3b-memory-64-plus-8",
        p3bBoundaryProject(),
        (result) => {
            assert(
                !result.emitSkipped && result.diagnostics.length === 0,
                "64 MiB caller + 8 MiB trusted boundary project did not emit cleanly.",
            );
            assert(
                result.outputs.length === 16,
                "Boundary project did not emit one module and map per caller source.",
            );
        },
    );
    const afterBoundary = await collectRetainedMemory();

    await compileAndDiscard(
        "desktop-p3b-memory-small-replay",
        snapshot(fixture.success.mainSource),
        verifySuccess,
    );
    const afterReplay = await collectRetainedMemory();

    const retainedAllowance = 32 * 1024 * 1024;
    assert(
        afterBoundary.heapUsed <= baseline.heapUsed + retainedAllowance,
        "Boundary project retained more than 32 MiB of additional V8 heap after forced GC.",
    );
    assert(
        afterReplay.heapUsed <= afterBoundary.heapUsed + 8 * 1024 * 1024,
        "Small replay caused an obvious retained V8 heap accumulation.",
    );
    assert(
        afterReplay.arrayBuffers <=
            baseline.arrayBuffers + 16 * 1024 * 1024,
        "Boundary/replay sequence retained aggregate input ArrayBuffers.",
    );
    process.stdout.write(
        "P3b retained-heap child passed under 512 MiB old-space " +
            `(heapUsed MiB baseline=${formatMiB(baseline.heapUsed)}, ` +
            `boundary=${formatMiB(afterBoundary.heapUsed)}, ` +
            `replay=${formatMiB(afterReplay.heapUsed)}; one 64+8 MiB ` +
            "boundary compile plus small replay).\n",
    );
};

const runP3bRetainedHeapSubprocess = () => {
    const child = childProcess.spawnSync(
        process.execPath,
        [
            "--expose-gc",
            "--max-old-space-size=512",
            __filename,
            ...process.argv.slice(2),
        ],
        {
            env: {
                ...process.env,
                R2_P3B_RETAINED_HEAP_CHILD: "1",
            },
            stdio: "inherit",
            timeout: 10 * 60 * 1000,
        },
    );
    assert(
        !child.error,
        `P3b retained-heap child could not run: ${child.error?.message}`,
    );
    assert(
        child.status === 0,
        `P3b retained-heap child exited with status ${child.status}.`,
    );
};

const verifyCompilerPageStateMachine = () => {
    const hostPath = path.join(path.dirname(workerPath), "compiler-host.js");
    const nativeMessages = [];
    const workers = [];
    let transferredPortListener = null;
    let rejectNextWorkerCreation = false;

    class FakeWorker {
        constructor(specifier) {
            assert(
                specifier === "compiler-worker.js",
                `Compiler page created an unexpected Worker: ${specifier}`,
            );
            if (rejectNextWorkerCreation) {
                rejectNextWorkerCreation = false;
                throw new Error("Injected replacement Worker construction failure.");
            }
            this.onmessage = null;
            this.onerror = null;
            this.onmessageerror = null;
            this.posted = [];
            this.terminated = false;
            workers.push(this);
        }

        postMessage(message) {
            assert(!this.terminated, "Compiler page posted to a terminated Worker.");
            this.posted.push(message);
        }

        terminate() {
            this.terminated = true;
        }

        emit(message) {
            this.onmessage?.({ data: message });
        }

        fail(message) {
            this.onerror?.({
                message,
                preventDefault() {},
            });
        }
    }

    const port = {
        closed: false,
        onmessage: null,
        close() {
            this.closed = true;
        },
        postMessage(encoded) {
            assert(
                Buffer.byteLength(encoded, "utf8") <= 128 * 1024,
                "Compiler page published an oversized JSON frame.",
            );
            nativeMessages.push(JSON.parse(encoded));
        },
        start() {},
    };
    const pageSelf = {
        addEventListener(type, listener) {
            if (type === "message") {
                transferredPortListener = listener;
            }
        },
    };
    const context = vm.createContext({
        JSON,
        TextEncoder,
        Worker: FakeWorker,
        self: pageSelf,
    });
    vm.runInContext(fs.readFileSync(hostPath, "utf8"), context, {
        filename: hostPath,
    });
    assert(transferredPortListener, "Compiler page did not install its port listener.");
    transferredPortListener({
        data: "__autojs_typescript_compiler_port_v1__",
        ports: [port],
    });

    const deliver = (message) => {
        const encoded = JSON.stringify(message);
        assert(
            Buffer.byteLength(encoded, "utf8") <= 128 * 1024,
            "Test attempted to send an oversized native frame.",
        );
        port.onmessage({ data: encoded });
    };
    const pageAuthority = inputArchiveAuthorityForFiles([
        {
            bytes: Buffer.from([0]),
            path: "/declarations/page.d.ts",
        },
    ]);
    const inputStart = (requestId) => ({
        ...pageAuthority,
        fileCount: 1,
        kind: "projectInputStart",
        requestId,
        sequence: 0,
        ...profileAuthorityFields,
        targetProfile: "rhino",
        totalBytes: 1,
        workerSpinMilliseconds: 0,
        workerSpinPhase: "none",
    });

    const first = workers[0];
    first.emit({ kind: "ready", tsVersion: expectedVersion });
    const beforeLegacyProject = first.posted.length;
    deliver({
        kind: "compileProject",
        project: {},
        requestId: "legacy-project-bypass",
    });
    assert(
        first.posted.length === beforeLegacyProject,
        "Compiler page retained the legacy single-message project bypass.",
    );
    deliver(inputStart("page-a"));
    assert(
        first.posted.at(-1).requestId === "page-a",
        "Compiler page did not forward the first project.",
    );
    deliver({ kind: "restartWorker" });
    assert(
        !first.terminated,
        "Unkeyed probe recovery terminated a keyed project.",
    );
    first.emit({
        kind: "projectPhase",
        phase: "ingress",
        requestId: "page-a",
        state: "started",
    });
    first.emit({
        kind: "projectInputAck",
        requestId: "page-a",
        sequence: 0,
    });
    const inputBatch = {
        frames: [
            {
                byteLength: 1,
                fileIndex: 0,
                kind: "projectInputFileStart",
                path: "/declarations/page.d.ts",
                requestId: "page-a",
                sequence: 1,
                sha256: sha256(Buffer.from([0])),
            },
            {
                chunkIndex: 0,
                data: "AA==",
                fileIndex: 0,
                kind: "projectInputFileChunk",
                requestId: "page-a",
                sequence: 2,
            },
            {
                chunkCount: 1,
                fileIndex: 0,
                kind: "projectInputFileEnd",
                requestId: "page-a",
                sequence: 3,
            },
        ],
        kind: "projectInputBatch",
        requestId: "page-a",
        sequence: 3,
    };
    deliver(inputBatch);
    assert(
        first.posted.at(-1).kind === "projectInputBatch" &&
            first.posted.at(-1).frames.length === 3,
        "Compiler page did not forward the bounded project-input batch.",
    );
    first.emit({
        kind: "projectInputAck",
        requestId: "page-a",
        sequence: 3,
    });
    deliver({
        fileCount: 1,
        kind: "projectInputEnd",
        requestId: "page-a",
        sequence: 4,
        totalBytes: 1,
    });
    first.emit({
        kind: "projectPhase",
        phase: "ingress",
        requestId: "page-a",
        state: "completed",
    });
    first.emit({
        kind: "projectInputAck",
        requestId: "page-a",
        sequence: 4,
    });
    first.emit({
        kind: "projectPhase",
        phase: "parse",
        requestId: "page-a",
        state: "started",
    });
    first.emit({
        completedUnits: 24,
        kind: "projectPhaseProgress",
        phase: "parse",
        requestId: "page-a",
        sequence: 1,
        totalUnits: 192,
    });
    first.emit({
        completedUnits: 48,
        kind: "projectPhaseProgress",
        phase: "parse",
        requestId: "page-a",
        sequence: 2,
        totalUnits: 192,
    });
    assert(
        nativeMessages.filter(
            (message) =>
                message.kind === "projectPhaseProgress" &&
                message.requestId === "page-a",
        ).length === 2,
        "Compiler page did not forward both bounded natural progress receipts.",
    );
    deliver({
        expectedGeneration: 2,
        kind: "terminateWorker",
        requestId: "page-a",
    });
    assert(!first.terminated, "Wrong generation terminated the active Worker.");
    deliver({
        expectedGeneration: 1,
        kind: "terminateWorker",
        requestId: "page-a",
    });
    assert(first.terminated, "Exact keyed termination did not stop the Worker.");
    const second = workers[1];
    assert(
        !nativeMessages.some(
            (message) =>
                message.kind === "workerTerminated" &&
                message.requestId === "page-a",
        ),
        "Compiler page acknowledged termination before replacement readiness.",
    );
    second.emit({ kind: "ready", tsVersion: expectedVersion });
    const terminatedA = nativeMessages.find(
        (message) =>
            message.kind === "workerTerminated" &&
            message.requestId === "page-a",
    );
    assert(terminatedA, "Compiler page did not acknowledge keyed termination.");
    assert(
        terminatedA.terminatedGeneration === 1 &&
            terminatedA.replacementGeneration === 2 &&
            terminatedA.generation === 2,
        "Compiler page reported incorrect termination generations.",
    );
    assert(
        terminatedA.phaseAtTermination === "parse" &&
            terminatedA.phaseProgressSequenceAtTermination === 2 &&
            terminatedA.phaseProgressCompletedUnitsAtTermination === 48 &&
            terminatedA.phaseProgressTotalUnitsAtTermination === 192,
        "Compiler page did not atomically bind natural progress to keyed teardown.",
    );
    const beforeLateA = nativeMessages.length;
    first.emit({
        kind: "projectInputAck",
        requestId: "page-a",
        sequence: 0,
    });
    assert(
        nativeMessages.length === beforeLateA,
        "Terminated Worker published a stale acknowledgement.",
    );

    deliver(inputStart("page-b"));
    second.emit({
        kind: "projectPhase",
        phase: "ingress",
        requestId: "page-b",
        state: "started",
    });
    second.emit({
        kind: "projectInputAck",
        requestId: "page-b",
        sequence: 0,
    });
    deliver({
        fileCount: 1,
        kind: "projectInputEnd",
        requestId: "page-b",
        sequence: 1,
        totalBytes: 1,
    });
    second.emit({
        kind: "projectPhase",
        phase: "ingress",
        requestId: "page-b",
        state: "completed",
    });
    second.emit({
        kind: "projectInputAck",
        requestId: "page-b",
        sequence: 1,
    });
    for (const phase of ["parse", "semantic"]) {
        second.emit({
            kind: "projectPhase",
            phase,
            requestId: "page-b",
            state: "started",
        });
        second.emit({
            kind: "projectPhase",
            phase,
            requestId: "page-b",
            state: "completed",
        });
    }
    second.emit({
        kind: "projectPhase",
        phase: "emit",
        requestId: "page-b",
        state: "started",
    });
    second.emit({
        completedUnits: 1,
        kind: "projectPhaseProgress",
        phase: "emit",
        requestId: "page-b",
        sequence: 1,
        totalUnits: 8,
    });
    second.emit({
        completedUnits: 2,
        kind: "projectPhaseProgress",
        phase: "emit",
        requestId: "page-b",
        sequence: 2,
        totalUnits: 8,
    });
    const resultEgressPayload = Buffer.from("{}", "utf8");
    second.emit({
        byteLength: resultEgressPayload.length,
        chunkCount: 1,
        kind: "projectResultStart",
        requestId: "page-b",
        sha256: digestBytes(resultEgressPayload),
    });
    deliver({
        expectedGeneration: 2,
        kind: "projectResultAck",
        requestId: "page-b",
        sequence: -1,
    });
    second.emit({
        data: resultEgressPayload.toString("base64"),
        index: 0,
        kind: "projectResultChunk",
        requestId: "page-b",
    });
    deliver({
        expectedGeneration: 1,
        kind: "terminateWorker",
        requestId: "page-a",
    });
    assert(!second.terminated, "Stale keyed termination stopped a successor.");
    deliver({
        expectedGeneration: 2,
        kind: "terminateWorker",
        requestId: "page-b",
    });
    assert(second.terminated, "Successor did not honor its exact termination key.");

    const third = workers[2];
    third.emit({ kind: "ready", tsVersion: expectedVersion });
    const terminatedB = nativeMessages.find(
        (message) =>
            message.kind === "workerTerminated" &&
            message.requestId === "page-b",
    );
    assert(
        terminatedB?.phaseAtTermination === "emit" &&
            terminatedB.phaseProgressSequenceAtTermination === 2 &&
            terminatedB.phaseProgressCompletedUnitsAtTermination === 2 &&
            terminatedB.phaseProgressTotalUnitsAtTermination === 8,
        "Result-egress termination lost the active emit progress receipt.",
    );
    deliver(inputStart("page-backpressure"));
    deliver({
        byteLength: 1,
        fileIndex: 0,
        kind: "projectInputFileStart",
        path: "/sources/a.ts",
        requestId: "page-backpressure",
        sequence: 1,
        sha256: "0".repeat(64),
    });
    assert(
        third.terminated,
        "Input sent before its ACK did not replace the protocol-violating Worker.",
    );
    assert(
        nativeMessages.some(
            (message) =>
                message.kind === "projectError" &&
                message.requestId === "page-backpressure",
        ),
        "Backpressure violation did not produce a keyed project error.",
    );

    const fourth = workers[3];
    fourth.emit({ kind: "ready", tsVersion: expectedVersion });
    deliver(inputStart("page-result"));
    fourth.emit({
        kind: "projectPhase",
        phase: "ingress",
        requestId: "page-result",
        state: "started",
    });
    fourth.emit({
        kind: "projectInputAck",
        requestId: "page-result",
        sequence: 0,
    });
    deliver({
        fileCount: 1,
        kind: "projectInputEnd",
        requestId: "page-result",
        sequence: 1,
        totalBytes: 1,
    });
    fourth.emit({
        kind: "projectPhase",
        phase: "ingress",
        requestId: "page-result",
        state: "completed",
    });
    fourth.emit({
        kind: "projectInputAck",
        requestId: "page-result",
        sequence: 1,
    });
    for (const phase of ["parse", "semantic"]) {
        fourth.emit({
            kind: "projectPhase",
            phase,
            requestId: "page-result",
            state: "started",
        });
        fourth.emit({
            kind: "projectPhase",
            phase,
            requestId: "page-result",
            state: "completed",
        });
    }
    fourth.emit({
        kind: "projectPhase",
        phase: "emit",
        requestId: "page-result",
        state: "started",
    });
    const resultPayload = Buffer.alloc(frameBytes + 1, 0x61);
    const resultSha256 = digestBytes(resultPayload);
    fourth.emit({
        byteLength: resultPayload.length,
        chunkCount: 2,
        kind: "projectResultStart",
        requestId: "page-result",
        sha256: resultSha256,
    });
    deliver({
        expectedGeneration: 4,
        kind: "projectResultAck",
        requestId: "page-result",
        sequence: -1,
    });
    assert(
        fourth.posted.at(-1).kind === "projectResultAck" &&
            fourth.posted.at(-1).sequence === -1 &&
            !Object.prototype.hasOwnProperty.call(
                fourth.posted.at(-1),
                "expectedGeneration",
            ),
        "Compiler page did not forward the initial result ACK exactly once.",
    );
    fourth.emit({
        data: resultPayload.subarray(0, frameBytes).toString("base64"),
        index: 0,
        kind: "projectResultChunk",
        requestId: "page-result",
    });
    deliver({
        expectedGeneration: 4,
        kind: "projectResultAck",
        requestId: "page-result",
        sequence: 0,
    });
    fourth.emit({
        data: resultPayload.subarray(frameBytes).toString("base64"),
        index: 1,
        kind: "projectResultChunk",
        requestId: "page-result",
    });
    deliver({
        expectedGeneration: 4,
        kind: "projectResultAck",
        requestId: "page-result",
        sequence: 1,
    });
    fourth.emit({
        kind: "projectPhase",
        phase: "emit",
        requestId: "page-result",
        state: "completed",
    });
    fourth.emit({
        byteLength: resultPayload.length,
        chunkCount: 2,
        kind: "projectResultEnd",
        requestId: "page-result",
        sha256: resultSha256,
    });
    const forwardedResultEvents = nativeMessages
        .filter((message) => message.requestId === "page-result")
        .filter(
            (message) =>
                message.kind === "projectResultChunk" ||
                message.kind === "projectResultEnd" ||
                (
                    message.kind === "projectPhase" &&
                    message.phase === "emit" &&
                    message.state === "completed"
                ),
        )
        .map((message) =>
            message.kind === "projectResultChunk"
                ? `chunk:${message.index}`
                : message.kind === "projectResultEnd"
                  ? message.kind
                  : `${message.phase}:${message.state}`,
        );
    assert(
        JSON.stringify(forwardedResultEvents) === JSON.stringify([
            "chunk:0",
            "chunk:1",
            "emit:completed",
            "projectResultEnd",
        ]),
        `Compiler page forwarded result egress out of sequence: ${forwardedResultEvents}.`,
    );
    assert(
        nativeMessages.some(
            (message) =>
                message.kind === "projectResultEnd" &&
                message.requestId === "page-result",
        ),
        "Compiler page did not finish stop-and-wait result forwarding.",
    );

    deliver(inputStart("page-forged-page-kind"));
    const forgedTerminationAcks = nativeMessages.filter(
        (message) =>
            message.kind === "workerTerminated" &&
            message.requestId === "page-forged-page-kind",
    ).length;
    fourth.emit({
        kind: "workerTerminated",
        replacementGeneration: 5,
        requestId: "page-forged-page-kind",
        terminatedGeneration: 4,
    });
    assert(
        fourth.terminated,
        "Unsupported Worker-owned message kind did not replace the Worker.",
    );
    assert(
        nativeMessages.filter(
            (message) =>
                message.kind === "workerTerminated" &&
                message.requestId === "page-forged-page-kind",
        ).length === forgedTerminationAcks,
        "Worker forged a page-owned termination acknowledgement.",
    );
    assert(
        nativeMessages.some(
            (message) =>
                message.kind === "projectError" &&
                message.requestId === "page-forged-page-kind",
        ),
        "Unsupported Worker message did not fail its exact active project.",
    );

    const fifth = workers[4];
    fifth.emit({ kind: "ready", tsVersion: expectedVersion });
    deliver(inputStart("page-replacement-load-failure"));
    const loadFailureAcksBefore = nativeMessages.filter(
        (message) =>
            message.kind === "workerTerminated" &&
            message.requestId === "page-replacement-load-failure",
    ).length;
    deliver({
        expectedGeneration: 5,
        kind: "terminateWorker",
        requestId: "page-replacement-load-failure",
    });
    const sixth = workers[5];
    sixth.fail("Injected replacement Worker load failure.");
    assert(
        fifth.terminated,
        "Replacement-load fixture did not terminate the exact Worker.",
    );
    assert(
        nativeMessages.filter(
            (message) =>
                message.kind === "workerTerminated" &&
                message.requestId === "page-replacement-load-failure",
        ).length === loadFailureAcksBefore,
        "Compiler page acknowledged termination before replacement readiness.",
    );
    assert(
        nativeMessages.some(
            (message) =>
                message.kind === "workerError" &&
                message.generation === 6 &&
                /load failure/i.test(message.message),
        ),
        "Replacement Worker asynchronous load failure was not reported.",
    );

    deliver({ kind: "restartWorker" });
    const seventh = workers[6];
    seventh.emit({ kind: "ready", tsVersion: expectedVersion });
    deliver(inputStart("page-replacement-construction-failure"));
    rejectNextWorkerCreation = true;
    const constructionFailureAcksBefore = nativeMessages.filter(
        (message) =>
            message.kind === "workerTerminated" &&
            message.requestId === "page-replacement-construction-failure",
    ).length;
    deliver({
        expectedGeneration: 7,
        kind: "terminateWorker",
        requestId: "page-replacement-construction-failure",
    });
    assert(
        seventh.terminated,
        "Replacement-construction fixture did not terminate the exact Worker.",
    );
    assert(
        nativeMessages.filter(
            (message) =>
                message.kind === "workerTerminated" &&
                message.requestId === "page-replacement-construction-failure",
        ).length === constructionFailureAcksBefore,
        "Compiler page acknowledged termination before constructing its replacement.",
    );
    assert(
        nativeMessages.some(
            (message) =>
                message.kind === "workerError" &&
                message.generation === 8 &&
                /construction failure/i.test(message.message),
        ),
        "Replacement Worker construction failure was not reported.",
    );
};

(async () => {
    if (process.env.R2_S1_SMOKE_ONLY === "1") {
        const { result } = await compile(
            "s1-rhino-smoke",
            snapshot(fixture.success.mainSource),
        );
        verifySuccess(result);
        process.stdout.write(
            `S1 Rhino Worker smoke passed (TypeScript ${expectedVersion}; ` +
                "2 modules + paired source maps).\n",
        );
        return;
    }
    if (process.env.R2_S1_AUTHORITY_ONLY === "1") {
        await verifyWorkerRejectsEveryTamperedAcceptedInputAuthorityField();
        return;
    }
    if (process.env.R2_T3_ONLY === "1") {
        await verifyT3ResidentProjectSessionIncrementality();
        return;
    }
    if (process.env.R2_X2_ONLY === "1") {
        await verifyX2UnifiedNoEmitDiagnostics();
        process.stdout.write(
            `R2-M2-X2 desktop diagnostic stop-emit golden passed ` +
                `(TypeScript ${expectedVersion}; 4 error phases, ` +
                `1 precedence case, 1 Warning case, 6 clean successors).\n`,
        );
        return;
    }
    if (process.env.R2_P3B_RETAINED_HEAP_CHILD === "1") {
        verifyP3bWorkerSourcePolicy();
        await runP3bRetainedHeapChild();
        return;
    }
    const failures = [];
    await runGolden(
        "complete fixed Worker compiler policy",
        async () => {
            verifyCompleteFixedWorkerPolicy();
        },
        failures,
    );
    await runGolden(
        "P3b-a Worker representation and capacity policy",
        async () => {
            verifyP3bWorkerSourcePolicy();
            await verifyP3bAdmissionBoundaries();
        },
        failures,
    );
    await runGolden(
        "P3b-a maximum-file ingress release and successor",
        async () => {
            await verifyP3bMaximumFileIngressRelease();
        },
        failures,
    );
    await runGolden(
        "P3b-a unique fatal decode without VFS re-encode",
        async () => {
            await verifyP3bUniqueFatalDecodeAndNoVfsReencode();
            await verifyP3bFatalDecodeAcrossFrameBoundary();
        },
        failures,
    );
    await runGolden(
        "P3b-a scanner token and directory aggregate limits",
        async () => {
            await verifyP3bScannerLimits();
            verifyP3bAggregatePathAndDirectoryGates();
        },
        failures,
    );
    await runGolden(
        "P3b-a lazy SourceFile cache identity and version",
        async () => {
            await verifyP3bLazySourceFileIdentityAndVersion();
        },
        failures,
    );
    await runGolden(
        "T3 project-scoped resident session and affected-module emit",
        async () => {
            await verifyT3ResidentProjectSessionIncrementality();
        },
        failures,
    );
    await runGolden(
        "P3b-a incremental diagnostic cap and pre-emit short circuit",
        async () => {
            await verifyP3bIncrementalDiagnosticCap();
            await verifyP3bSkipsEmitOnPreEmitErrors();
        },
        failures,
    );
    await runGolden(
        "R2-M2-X2 unified no-emit diagnostics and clean successors",
        async () => {
            await verifyX2UnifiedNoEmitDiagnostics();
        },
        failures,
    );
    await runGolden(
        "Rhino revision 2 native TSX lowering and Source Map",
        async () => {
            const project = tsxSnapshot();
            const { result } = await compile("desktop-rhino-revision2-tsx", project);
            verifyTsxSuccess(result);

            const invalid = await compile(
                "desktop-rhino-revision2-tsx-invalid-attribute",
                tsxSnapshot('const view = <text texxt="typo" />;\nvoid view;\n'),
            );
            verifyTsxAttributeDiagnostic(invalid.result);
        },
        failures,
    );
    await runGolden(
        "runtime-valued project",
        async () => {
            const { result: success } = await compile(
                "desktop-success",
                snapshot(fixture.success.mainSource),
            );
            verifySuccess(success);
        },
        failures,
    );
    await runGolden(
        "semantic-error project",
        async () => {
            const { result: semanticError } = await compile(
                "desktop-semantic-error",
                snapshot(fixture.semanticError.mainSource),
            );
            verifySemanticError(semanticError);
        },
        failures,
    );
    await runGolden(
        "controlled declaration-error project",
        async () => {
            const { result: declarationError } = await compile(
                "desktop-controlled-declaration-error",
                declarationErrorSnapshot(),
            );
            verifyDeclarationError(declarationError);
        },
        failures,
    );
    await runGolden(
        "imported source-declaration-error project",
        async () => {
            const { result: sourceDeclarationError } = await compile(
                "desktop-imported-source-declaration-error",
                sourceDeclarationErrorSnapshot(),
            );
            verifySourceDeclarationError(sourceDeclarationError);
        },
        failures,
    );
    await runGolden(
        "type-only root/import project",
        async () => {
            const { result: typeOnlyRootImport } = await compile(
                "desktop-type-only-root-import",
                typeOnlyRootImportSnapshot(),
            );
            verifyTypeOnlyRootImport(typeOnlyRootImport);
        },
        failures,
    );
    await runGolden(
        "full generated standard-library framed project",
        async () => {
            const project = fullStandardLibrarySnapshot();
            const totalBytes = project.files.reduce(
                (total, file) =>
                    total + Buffer.byteLength(file.text, "utf8"),
                0,
            );
            assert(
                totalBytes > 3 * 1024 * 1024,
                "Full declaration framing fixture is unexpectedly small.",
            );
            const { result } = await compile(
                "desktop-full-standard-library",
                project,
            );
            assert(
                !result.emitSkipped && result.diagnostics.length === 0,
                "Full declaration project did not emit cleanly.",
            );
            assert(
                result.outputs.length === 2,
                "Full declaration project did not emit one module and map.",
            );
        },
        failures,
    );
    await runGolden(
        "T1-1 revision 3 compiles 1000+ caller files and 30+ MiB",
        async () => {
            const project = t1LargeProjectSnapshot();
            const { result } = await compile(
                "desktop-t1-large-project",
                project,
                {
                    inputQuotaRevision:
                        CURRENT_ORDINARY_INPUT_QUOTA_REVISION,
                },
            );
            assert(
                project.callerFiles === 1_025 &&
                    project.callerBytes > 30 * 1024 * 1024,
                "T1-1 large-project caller authority drifted.",
            );
            assert(
                !result.emitSkipped &&
                    result.diagnostics.length === 0 &&
                    result.outputs.length === 2,
                "T1-1 large project did not compile and emit one clean module: " +
                    JSON.stringify({
                        diagnostics: result.diagnostics.slice(0, 3),
                        emitSkipped: result.emitSkipped,
                        outputPaths: result.outputs.map((output) => output.path),
                    }),
            );
        },
        failures,
    );
    await runGolden(
        "P3b-b2g natural parse/semantic/emit progress workload",
        async () => {
            await verifyP3bB2gNaturalPhaseProgressWorkload();
        },
        failures,
    );
    await runGolden(
        "P3b-b2h three-baseline semantic request-start deadline calibration",
        async () => {
            await verifyP3bB2hNaturalSemanticDeadlineCalibration();
        },
        failures,
    );
    await runGolden(
        "P3b-b2i three-baseline parse request-start deadline calibration",
        async () => {
            await verifyP3bB2iNaturalParseDeadlineCalibration();
        },
        failures,
    );
    await runGolden(
        "external natural parse ordinary-admission workload",
        async () => {
            await verifyExternalNaturalParseTimeoutWorkload();
        },
        failures,
    );
    await runGolden(
        "P3b-b2j three-baseline emit request-start deadline calibration",
        async () => {
            await verifyP3bB2jNaturalEmitDeadlineCalibration();
        },
        failures,
    );
    await runGolden(
        "S1 accepted-input authority tamper rejection and clean successors",
        async () => {
            await verifyWorkerRejectsEveryTamperedAcceptedInputAuthorityField();
        },
        failures,
    );
    await runGolden(
        "framed authority, digest, UTF-8, and base64 rejection",
        async () => {
            await verifyWorkerRejectsUnboundProfileAuthority();
            await verifyWorkerRejectsBadDigest();
            await verifyWorkerRejectsInvalidUtf8();
            await verifyWorkerRejectsNonCanonicalBase64();
        },
        failures,
    );
    await runGolden(
        "bounded synchronous Worker cancellation hook",
        async () => {
            const spinMilliseconds = 60;
            const spun = await compile(
                "desktop-worker-spin",
                snapshot(fixture.success.mainSource),
                { workerSpinMilliseconds: spinMilliseconds },
            );
            assert(
                spun.endElapsedMilliseconds >= spinMilliseconds - 5,
                "Worker did not execute its bounded synchronous spin hook.",
            );
            verifySuccess(spun.result);
        },
        failures,
    );
    await runGolden(
        "compiler page backpressure and keyed teardown",
        async () => {
            verifyCompilerPageStateMachine();
        },
        failures,
    );
    await runGolden(
        "P3b-a retained heap under constrained Node old-space",
        async () => {
            runP3bRetainedHeapSubprocess();
        },
        failures,
    );
    assert(
        failures.length === 0,
        `R2 provisional Rhino desktop golden failed:\n- ${failures.join("\n- ")}`,
    );
    process.stdout.write(
        `R2 provisional Rhino desktop golden passed (TypeScript ${expectedVersion}).\n`,
    );
})().catch((error) => {
    process.stderr.write(`${error.stack || error}\n`);
    process.exitCode = 1;
});
