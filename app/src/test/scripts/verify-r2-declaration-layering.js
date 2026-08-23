"use strict";

const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

const [distributionArgument, typeScriptLibArgument] = process.argv.slice(2);
if (!distributionArgument || !typeScriptLibArgument) {
    throw new Error(
        "Usage: node verify-r2-declaration-layering.js " +
            "<r2-declarations/v1> <typescript-6.0.3-lib-directory>",
    );
}

const distributionRoot = path.resolve(distributionArgument);
const typeScriptLibRoot = path.resolve(typeScriptLibArgument);
const manifestPath = path.join(distributionRoot, "distribution.json");
const compilerPath = path.join(typeScriptLibRoot, "typescript.js");
const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;
const exactKeys = (value, expected) =>
    value !== null &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    JSON.stringify(Object.keys(value).sort(compareCodeUnits)) ===
        JSON.stringify(expected.slice().sort(compareCodeUnits));
const sha256 = (bytes) =>
    crypto.createHash("sha256").update(bytes).digest("hex");
const sha256Pattern = /^[0-9a-f]{64}$/;
const canonicalRelativePathPattern =
    /^(?!\/)(?!.*(?:^|\/)\.\.(?:\/|$))(?!.*\\)[\x21-\x7e]+$/;
const canonicalJson = (value) => {
    if (value === null) return "null";
    if (typeof value === "string") return JSON.stringify(value);
    if (typeof value === "boolean") return value ? "true" : "false";
    if (typeof value === "number") {
        assert(Number.isSafeInteger(value), "Canonical manifest number is not an integer.");
        return String(value);
    }
    if (Array.isArray(value)) {
        return `[${value.map(canonicalJson).join(",")}]`;
    }
    assert(
        value !== null && typeof value === "object",
        "Canonical manifest value has an unsupported type.",
    );
    return `{${Object.keys(value)
        .sort(compareCodeUnits)
        .map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`)
        .join(",")}}`;
};

assert(fs.existsSync(manifestPath), `Missing declaration manifest: ${manifestPath}`);
assert(fs.existsSync(compilerPath), `Missing TypeScript compiler: ${compilerPath}`);
const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
const ts = require(compilerPath);
assert(ts.version === "6.0.3", `Expected TypeScript 6.0.3, received ${ts.version}.`);
assert(
    exactKeys(manifest, [
        "schemaVersion",
        "generator",
        "sources",
        "layers",
        "profiles",
        "files",
        "fingerprint",
    ]) &&
        manifest.schemaVersion === 1 &&
        sha256Pattern.test(manifest.fingerprint),
    "Declaration distribution top-level schema drifted.",
);
assert(
    exactKeys(manifest.layers, ["common", "node", "rhino"]) &&
        exactKeys(manifest.profiles, ["node", "rhino"]) &&
        exactKeys(manifest.sources, ["common", "node", "rhino"]) &&
        exactKeys(manifest.generator, ["id", "version", "fingerprint"]),
    "Declaration layer or profile inventory drifted.",
);
const generatorCanonical =
    "autojs6.typescript.declarations.generator.v1\n" +
    `id\0${manifest.generator.id}\n` +
    `version\0${manifest.generator.version}\n`;
assert(
    manifest.generator.id === "autojs6-plugin-typescript-engine" &&
        manifest.generator.version === 2 &&
        manifest.generator.fingerprint === sha256(generatorCanonical),
    "Declaration generator identity drifted.",
);

const listRelativeFiles = (root, relative = "") => {
    const directory = path.join(root, ...relative.split("/").filter(Boolean));
    return fs
        .readdirSync(directory, { withFileTypes: true })
        .flatMap((entry) => {
            const child = relative ? `${relative}/${entry.name}` : entry.name;
            return entry.isDirectory()
                ? listRelativeFiles(root, child)
                : [child];
        });
};
assert(Array.isArray(manifest.files), "Declaration manifest files must be an array.");
const manifestFiles = new Map();
let previousManifestPath = null;
for (const record of manifest.files) {
    assert(
        exactKeys(record, ["path", "sha256", "size"]) &&
            typeof record.path === "string" &&
            canonicalRelativePathPattern.test(record.path) &&
            Number.isSafeInteger(record.size) &&
            record.size >= 0 &&
            typeof record.sha256 === "string" &&
            sha256Pattern.test(record.sha256) &&
            (previousManifestPath === null ||
                compareCodeUnits(previousManifestPath, record.path) < 0) &&
            !manifestFiles.has(record.path),
        "Declaration manifest contains a malformed or unsorted file record.",
    );
    const absolutePath = path.join(
        distributionRoot,
        ...record.path.split("/"),
    );
    assert(fs.existsSync(absolutePath), `Manifest file is missing: ${record.path}`);
    const bytes = fs.readFileSync(absolutePath);
    assert(
        bytes.length === record.size && sha256(bytes) === record.sha256,
        `Manifest file identity drifted: ${record.path}`,
    );
    manifestFiles.set(record.path, record);
    previousManifestPath = record.path;
}
const physicalFiles = listRelativeFiles(distributionRoot)
    .filter((file) => file !== "distribution.json")
    .sort(compareCodeUnits);
assert(
    JSON.stringify(physicalFiles) ===
        JSON.stringify(Array.from(manifestFiles.keys())),
    "Declaration manifest does not exactly cover the generated distribution.",
);

const typeScriptLibFiles = fs
    .readdirSync(typeScriptLibRoot, { withFileTypes: true })
    .filter(
        (entry) =>
            entry.isFile() && /^lib(?:\..+)?\.d\.ts$/.test(entry.name),
    )
    .map((entry) => entry.name)
    .sort(compareCodeUnits);
assert(
    typeScriptLibFiles.includes("lib.d.ts"),
    "TypeScript standard-library directory has no lib.d.ts.",
);
const typeScriptStandardLibraries = typeScriptLibFiles.map((fileName) => {
    const bytes = fs.readFileSync(path.join(typeScriptLibRoot, fileName));
    return {
        bytes,
        path: `lib/${fileName}`,
        sha256: sha256(bytes),
        size: bytes.length,
    };
});
const commonFingerprintDigest = crypto.createHash("sha256");
for (const record of typeScriptStandardLibraries) {
    commonFingerprintDigest.update(record.path, "utf8");
    commonFingerprintDigest.update("\0", "ascii");
    commonFingerprintDigest.update(String(record.size), "ascii");
    commonFingerprintDigest.update("\0", "ascii");
    commonFingerprintDigest.update(record.sha256, "ascii");
    commonFingerprintDigest.update("\n", "ascii");
}
const commonFingerprint = commonFingerprintDigest.digest("hex");

const calculateEngineLayerFingerprint = (layerName, paths) => {
    const digest = crypto.createHash("sha256");
    digest.update("autojs6.typescript.declarations.layer.v1\n", "ascii");
    digest.update(`layer\0${layerName}\n`, "ascii");
    for (const filePath of paths) {
        const record = manifestFiles.get(filePath);
        digest.update("file\0", "ascii");
        digest.update(record.path, "utf8");
        digest.update("\0", "ascii");
        digest.update(String(record.size), "ascii");
        digest.update("\0", "ascii");
        digest.update(record.sha256, "ascii");
        digest.update("\n", "ascii");
    }
    return digest.digest("hex");
};

const layerKeys = [
    "sourceId",
    "entryPoint",
    "fileCount",
    "totalBytes",
    "fingerprint",
    "files",
];
const resolveLayerPath = (layerName, candidate) => {
    assert(typeof candidate === "string", `${layerName} layer path is not a string.`);
    assert(
        manifestFiles.has(candidate) &&
            candidate.startsWith(`layers/${layerName}/`),
        `${layerName} layer path is not canonical or manifested: ${candidate}`,
    );
    return candidate;
};
const layerPaths = new Map();
for (const layerName of ["common", "node", "rhino"]) {
    const layer = manifest.layers[layerName];
    assert(
        exactKeys(layer, layerKeys) &&
            typeof layer.sourceId === "string" &&
            layer.sourceId.length > 0 &&
            Array.isArray(layer.files) &&
            Number.isSafeInteger(layer.fileCount) &&
            Number.isSafeInteger(layer.totalBytes) &&
            layer.totalBytes >= 0 &&
            typeof layer.fingerprint === "string" &&
            sha256Pattern.test(layer.fingerprint) &&
            layer.sourceId === layerName,
        `${layerName} declaration layer schema drifted.`,
    );
    const paths = layer.files.map((candidate) =>
        resolveLayerPath(layerName, candidate),
    );
    assert(
        paths.every(
            (candidate, index) =>
                index === 0 || compareCodeUnits(paths[index - 1], candidate) < 0,
        ),
        `${layerName} layer file accounting drifted.`,
    );
    if (layerName === "common") {
        assert(
            layer.entryPoint === null &&
                paths.length === 0 &&
                layer.fileCount === typeScriptStandardLibraries.length &&
                layer.totalBytes ===
                    typeScriptStandardLibraries.reduce(
                        (total, record) => total + record.size,
                        0,
                    ) &&
                layer.fingerprint === commonFingerprint,
            "Common binding layer unexpectedly became a compiler declaration root.",
        );
    } else {
        const entryPoint = resolveLayerPath(layerName, layer.entryPoint);
        assert(
            paths.includes(entryPoint) &&
                entryPoint === `layers/${layerName}/index.d.ts` &&
                paths.every((candidate) => candidate.endsWith(".d.ts")) &&
                layer.fileCount === paths.length &&
                layer.totalBytes ===
                    paths.reduce(
                        (total, candidate) =>
                            total + manifestFiles.get(candidate).size,
                        0,
                    ) &&
                layer.fingerprint ===
                    calculateEngineLayerFingerprint(layerName, paths),
            `${layerName} layer has no canonical index.d.ts entry.`,
        );
    }
    layerPaths.set(layerName, paths);
}

const calculateProfileFingerprint = (profileName, layerIds, entryPoint) => {
    const digest = crypto.createHash("sha256");
    digest.update("autojs6.typescript.declarations.profile.v1\n", "ascii");
    digest.update(`profile\0${profileName}\n`, "ascii");
    for (const layerId of layerIds) {
        digest.update(
            `layer\0${layerId}\0${manifest.layers[layerId].fingerprint}\n`,
            "ascii",
        );
    }
    digest.update("entryPoint\0", "ascii");
    digest.update(entryPoint, "utf8");
    digest.update("\n", "ascii");
    return digest.digest("hex");
};
for (const profileName of ["node", "rhino"]) {
    const profile = manifest.profiles[profileName];
    const expectedLayers = ["common", profileName];
    assert(
        exactKeys(profile, ["layers", "fingerprint"]) &&
            JSON.stringify(profile.layers) ===
                JSON.stringify(expectedLayers) &&
            typeof profile.fingerprint === "string" &&
            sha256Pattern.test(profile.fingerprint) &&
            profile.fingerprint ===
                calculateProfileFingerprint(
                    profileName,
                    expectedLayers,
                    manifest.layers[profileName].entryPoint,
                ),
        `${profileName} declaration profile schema drifted.`,
    );
}

const distributionFingerprintDigest = crypto.createHash("sha256");
distributionFingerprintDigest.update(
    "autojs6.typescript.declarations.distribution.v1\n",
    "ascii",
);
distributionFingerprintDigest.update(
    `generator\0${manifest.generator.fingerprint}\n`,
    "ascii",
);
for (const sourceId of Object.keys(manifest.sources).sort(compareCodeUnits)) {
    distributionFingerprintDigest.update(`source\0${sourceId}\0`, "ascii");
    distributionFingerprintDigest.update(
        sha256(Buffer.from(canonicalJson(manifest.sources[sourceId]), "utf8")),
        "ascii",
    );
    distributionFingerprintDigest.update("\n", "ascii");
}
for (const layerId of Object.keys(manifest.layers).sort(compareCodeUnits)) {
    distributionFingerprintDigest.update(
        `layer\0${layerId}\0${manifest.layers[layerId].fingerprint}\n`,
        "ascii",
    );
}
for (const profileId of Object.keys(manifest.profiles).sort(compareCodeUnits)) {
    distributionFingerprintDigest.update(
        `profile\0${profileId}\0${manifest.profiles[profileId].fingerprint}\n`,
        "ascii",
    );
}
for (const record of manifest.files) {
    distributionFingerprintDigest.update("file\0", "ascii");
    distributionFingerprintDigest.update(record.path, "utf8");
    distributionFingerprintDigest.update("\0", "ascii");
    distributionFingerprintDigest.update(String(record.size), "ascii");
    distributionFingerprintDigest.update("\0", "ascii");
    distributionFingerprintDigest.update(record.sha256, "ascii");
    distributionFingerprintDigest.update("\n", "ascii");
}
assert(
    distributionFingerprintDigest.digest("hex") === manifest.fingerprint,
    "Declaration distribution fingerprint drifted.",
);

const decodeUtf8 = (bytes, label) => {
    try {
        return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
    } catch (_) {
        throw new Error(`Declaration source is not UTF-8: ${label}`);
    }
};
const standardLibraryEntries = typeScriptStandardLibraries.map((record) => [
    `/declarations/${record.path.slice("lib/".length)}`,
    decodeUtf8(record.bytes, record.path),
]);

const normalizeVfsPath = (candidate) => {
    const replaced = candidate.replaceAll("\\", "/");
    return path.posix.normalize(replaced.startsWith("/") ? replaced : `/${replaced}`);
};
const profileCompilerEntries = (profileName) => {
    const prefix = `layers/${profileName}/`;
    return layerPaths
        .get(profileName)
        .filter((relativePath) => relativePath.endsWith(".d.ts"))
        .map((relativePath) => {
            assert(
                relativePath.startsWith(prefix),
                `${profileName} compiler file escaped its layer: ${relativePath}`,
            );
            const bytes = fs.readFileSync(
                path.join(distributionRoot, ...relativePath.split("/")),
            );
            return [
                `/declarations/${profileName}/${relativePath.slice(prefix.length)}`,
                decodeUtf8(bytes, relativePath),
            ];
        });
};
const profileEntryPath = (profileName) => {
    const relativePath = resolveLayerPath(
        profileName,
        manifest.layers[profileName].entryPoint,
    );
    return `/declarations/${profileName}/${relativePath.slice(
        `layers/${profileName}/`.length,
    )}`;
};

const compilerOptions = Object.freeze({
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
const createVfsCompilerHost = (entries, outputs, defaultLibraryPath) => {
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
        getDefaultLibFileName: () => defaultLibraryPath,
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
                    ts.ScriptKind.TS,
                );
                sourceFile.version = "0";
                sourceFiles.set(canonical, sourceFile);
            }
            return sourceFiles.get(canonical);
        },
        readFile: (fileName) => files.get(normalizeVfsPath(fileName)),
        realpath: (fileName) => normalizeVfsPath(fileName),
        useCaseSensitiveFileNames: () => true,
        writeFile: (
            fileName,
            text,
            _writeByteOrderMark,
            _onError,
            sourceFiles,
        ) => {
            const canonical = normalizeVfsPath(fileName);
            assert(
                (canonical === "/outputs/main.js" ||
                    canonical === "/outputs/main.js.map") &&
                    !outputs.has(canonical),
                `TypeScript emitted an unauthorized output: ${canonical}`,
            );
            outputs.set(canonical, {
                sourcePaths: (sourceFiles || [])
                    .map((sourceFile) => sourceFile.fileName)
                    .sort(compareCodeUnits),
                text,
            });
        },
    };
};

const constructors = [
    {
        create: (rootNames, host) => {
            const program = ts.createProgram({
                host,
                options: compilerOptions,
                rootNames,
            });
            return { emit: () => program.emit(), program };
        },
        name: "createProgram",
    },
    {
        create: (rootNames, host) => {
            const builder = ts.createIncrementalProgram({
                host,
                options: compilerOptions,
                rootNames,
            });
            return {
                emit: () => builder.emit(),
                program: builder.getProgram(),
            };
        },
        name: "createIncrementalProgram",
    },
];
const runCase = (profileName, sourceText, expectedDiagnosticCodes, label) => {
    const userRootNames = Object.freeze(["/sources/main.ts"]);
    const entryPath = profileEntryPath(profileName);
    const profileEntries = profileCompilerEntries(profileName);
    const compilerRootNames = [...userRootNames, entryPath];
    assert(
        JSON.stringify(userRootNames) === JSON.stringify(["/sources/main.ts"]) &&
        userRootNames.every((rootName) => rootName.startsWith("/sources/")) &&
            userRootNames.every((rootName) => !rootName.startsWith("/declarations/")) &&
            entryPath.startsWith(`/declarations/${profileName}/`),
        `${label} mixed compiler-only declarations into user roots.`,
    );
    for (const constructor of constructors) {
        const outputs = new Map();
        const host = createVfsCompilerHost(
            [
                ...standardLibraryEntries,
                ...profileEntries,
                [userRootNames[0], sourceText],
            ],
            outputs,
            profileName === "rhino"
                ? "/declarations/lib.es2018.d.ts"
                : "/declarations/lib.d.ts",
        );
        const compilation = constructor.create(compilerRootNames, host);
        const program = compilation.program;
        assert(
            JSON.stringify(program.getRootFileNames()) ===
                JSON.stringify(compilerRootNames) &&
                JSON.stringify(userRootNames) ===
                    JSON.stringify(["/sources/main.ts"]),
            `${label}/${constructor.name} root authority drifted.`,
        );
        const diagnostics = ts
            .getPreEmitDiagnostics(program)
            .filter(
                (diagnostic) => diagnostic.category === ts.DiagnosticCategory.Error,
            );
        const actualCodes = diagnostics
            .map((diagnostic) => diagnostic.code)
            .sort((left, right) => left - right);
        const expectedCodes = expectedDiagnosticCodes
            .slice()
            .sort((left, right) => left - right);
        if (JSON.stringify(actualCodes) !== JSON.stringify(expectedCodes)) {
            console.error(
                diagnostics.map((diagnostic) => {
                    const location =
                        diagnostic.file && Number.isInteger(diagnostic.start)
                            ? diagnostic.file.fileName +
                              ":" +
                              (diagnostic.file.getLineAndCharacterOfPosition(
                                  diagnostic.start,
                              ).line +
                                  1)
                            : "<global>";
                    return (
                        diagnostic.code +
                        "@" +
                        location +
                        ": " +
                        ts.flattenDiagnosticMessageText(
                            diagnostic.messageText,
                            " ",
                        )
                    );
                }),
            );
        }
        assert(
            JSON.stringify(actualCodes) ===
                JSON.stringify(expectedCodes),
            `${label}/${constructor.name} diagnostics were ${JSON.stringify(
                actualCodes,
            )}, expected ${JSON.stringify(expectedDiagnosticCodes)}.`,
        );
        const sourceInventory = new Set(
            program.getSourceFiles().map((sourceFile) => sourceFile.fileName),
        );
        const actualProfileDeclarations = Array.from(sourceInventory)
            .filter((fileName) =>
                fileName.startsWith(`/declarations/${profileName}/`),
            )
            .sort(compareCodeUnits);
        const intentionallyUnreachableDeclarations =
            profileName === "node"
                ? new Set(["/declarations/node/input_observer.d.ts"])
                : new Set();
        const expectedProfileDeclarations = profileEntries
            .map(([declarationPath]) => declarationPath)
            .filter(
                (declarationPath) =>
                    !intentionallyUnreachableDeclarations.has(declarationPath),
            )
            .sort(compareCodeUnits);
        assert(
            JSON.stringify(actualProfileDeclarations) ===
                JSON.stringify(expectedProfileDeclarations) &&
                Array.from(intentionallyUnreachableDeclarations).every(
                    (declarationPath) => !sourceInventory.has(declarationPath),
                ),
            `${label}/${constructor.name} declaration reachability drifted: ` +
                `${JSON.stringify(actualProfileDeclarations)}.`,
        );
        const oppositeProfile = profileName === "rhino" ? "node" : "rhino";
        assert(
            !Array.from(sourceInventory).some((fileName) =>
                fileName.startsWith(`/declarations/${oppositeProfile}/`),
            ),
            `${label}/${constructor.name} admitted the opposite declaration layer.`,
        );
        const emitResult = compilation.emit();
        if (expectedDiagnosticCodes.length === 0) {
            assert(
                emitResult.emitSkipped === false &&
                    JSON.stringify(
                        Array.from(outputs.keys()).sort(compareCodeUnits),
                    ) ===
                        JSON.stringify([
                            "/outputs/main.js",
                            "/outputs/main.js.map",
                        ]) &&
                    Array.from(outputs.values()).every(
                        (output) =>
                            JSON.stringify(output.sourcePaths) ===
                            JSON.stringify(["/sources/main.ts"]),
                    ),
                `${label}/${constructor.name} emit inventory escaped its source root.`,
            );
        } else {
            assert(
                emitResult.emitSkipped === true && outputs.size === 0,
                `${label}/${constructor.name} emitted despite noEmitOnError.`,
            );
        }
        assert(
            JSON.stringify(userRootNames) === JSON.stringify(["/sources/main.ts"]),
            `${label}/${constructor.name} mutated the selected user roots.`,
        );
    }
};

runCase("rhino", 'toast("ready");\nsleep(1);\n', [], "Rhino globals");
runCase(
    "rhino",
    'const snapshotWidth: number = device.width;\n' +
        'const resolvedPath: string = files.path(".");\n' +
        'const storage: Internal.LocalStorage = storages.create("typescript-audit");\n' +
        'storage.put("width", snapshotWidth);\n' +
        'const containsWidth: boolean = storage.contains("width");\n' +
        'const colorText: string = colors.toString(0xff336699);\n' +
        'const topSelector: Internal.Selector = top(0);\n' +
        'Text(colorText);\n' +
        'console.log(resolvedPath, containsWidth, topSelector);\n',
    [],
    "Rhino modular API surface",
);
runCase(
    "rhino",
    "const invalidWidth: string = device.width;\n",
    [2322],
    "Rhino user-source strictness",
);
runCase(
    "rhino",
    'import profile = require("autojs6:profile");\nvoid profile;\n',
    [2307],
    "Rhino Node-profile import",
);
runCase(
    "node",
    'import profile = require("autojs6:profile");\n' +
        'import toastModule = require("toast");\n' +
        "void profile;\n" +
        'void toastModule.toast("ready");\n',
    [],
    "Node module surface",
);
runCase(
    "node",
    'toast("forbidden");\nsleep(1);\n',
    [2304, 2304],
    "Node global pollution",
);

console.log(
    "R2-M2-C8 declaration layering verified: " +
        `TypeScript=${ts.version}, APIs=${constructors.length}, ` +
        `Rhino d.ts=${profileCompilerEntries("rhino").length}, ` +
        `Node d.ts=${profileCompilerEntries("node").length}, ` +
        "profile isolation=6/6",
);
