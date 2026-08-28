"use strict";

const childProcess = require("node:child_process");
const cryptoModule = require("node:crypto");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { pathToFileURL } = require("node:url");
const vm = require("node:vm");
const zlib = require("node:zlib");

const [
    workerArgument,
    compilerArgument,
    manifestArgument,
    archiveDirectoryArgument,
    expectedVersion,
] = process.argv.slice(2);
if (
    process.argv.slice(2).length !== 5 ||
    !workerArgument ||
    !compilerArgument ||
    !manifestArgument ||
    !archiveDirectoryArgument ||
    expectedVersion !== "6.0.3"
) {
    throw new Error(
        "Usage: node verify-t2-real-package-matrix.js <worker.js> " +
            "<typescript.js> <matrix.json> <archive-directory> 6.0.3",
    );
}

const workerPath = path.resolve(workerArgument);
const compilerPath = path.resolve(compilerArgument);
const manifestPath = path.resolve(manifestArgument);
const archiveDirectory = path.resolve(archiveDirectoryArgument);
const workerSource = fs.readFileSync(workerPath, "utf8");
const ts = require(compilerPath);
const matrix = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;
const stableJson = (value) => JSON.stringify(value);

assert(
    matrix.schema === "autojs6.typescript.t2-5.real-package-matrix" &&
        matrix.schemaRevision === 1 &&
        matrix.resolverPolicyRevision === 3 &&
        matrix.resolverPolicyFingerprint ===
            "6241375f2ea49ac2c2d2b3b79b28b7c1ee4adde75c989defc61e252c89ced2bd",
    "T2-5 matrix schema or resolver authority drifted.",
);
assert(
    Array.isArray(matrix.archives) &&
        matrix.archives.length === 6 &&
        Array.isArray(matrix.cases) &&
        stableJson(matrix.cases.map((entry) => entry.name)) ===
            stableJson(["dayjs", "lodash", "zod", "uuid", "nanoid"]),
    "T2-5 matrix must contain the five ordered Roadmap packages and one type companion.",
);

if (!global.crypto) {
    Object.defineProperty(global, "crypto", {
        configurable: true,
        value: cryptoModule.webcrypto,
    });
}
global.ts = ts;
global.importScripts = (specifier) => {
    assert(specifier === "typescript.js", "Worker imported an unexpected script.");
};
const publishedMessages = [];
global.self = {
    postMessage: (message) => publishedMessages.push(message),
};
vm.runInThisContext(workerSource, { filename: workerPath });
assert(
    publishedMessages.length === 1 &&
        publishedMessages[0].kind === "ready" &&
        publishedMessages[0].tsVersion === expectedVersion,
    "Worker bootstrap drifted from TypeScript 6.0.3.",
);
const workerBinding = (expression) => vm.runInThisContext(expression, {
    filename: `${workerPath}#t2-real-package-matrix`,
});
const createDependencyLayerResolution = workerBinding(
    "createDependencyLayerResolution",
);
const createDependencyAwareCompilerOptions = workerBinding(
    "createDependencyAwareCompilerOptions",
);
const modes = workerBinding("controlledNodeResolutionModes");

const ALLOWED_SUFFIXES = new Set([
    ".cjs",
    ".cts",
    ".js",
    ".json",
    ".jsx",
    ".mjs",
    ".mts",
    ".ts",
    ".tsx",
]);
const EXCLUDED_DIRECTORIES = new Set([
    ".bin",
    ".git",
    ".github",
    "bench",
    "benchmark",
    "benchmarks",
    "coverage",
    "doc",
    "docs",
    "example",
    "examples",
    "test",
    "tests",
]);
const MAXIMUM_FILES = 8192;
const MAXIMUM_SINGLE_FILE_BYTES = 8 * 1024 * 1024;
const MAXIMUM_TOTAL_BYTES = 64 * 1024 * 1024;
const MAXIMUM_PATH_BYTES = 1536 * 1024;
const textDecoder = new TextDecoder("utf-8", { fatal: true });

function readTarString(buffer, offset, length) {
    const end = buffer.indexOf(0, offset);
    const boundedEnd = end < 0 || end > offset + length ? offset + length : end;
    return buffer.subarray(offset, boundedEnd).toString("utf8").trim();
}

function readTarNumber(buffer, offset, length) {
    const value = readTarString(buffer, offset, length).replace(/^0+/, "");
    return value.length === 0 ? 0 : Number.parseInt(value, 8);
}

function parsePax(body) {
    const fields = Object.create(null);
    let offset = 0;
    while (offset < body.length) {
        const space = body.indexOf(0x20, offset);
        assert(space > offset, "Malformed PAX record length.");
        const recordLength = Number.parseInt(
            body.subarray(offset, space).toString("ascii"),
            10,
        );
        assert(Number.isSafeInteger(recordLength) && recordLength > 0, "Invalid PAX record length.");
        const recordEnd = offset + recordLength;
        assert(recordEnd <= body.length, "PAX record exceeds its tar entry.");
        const record = body.subarray(space + 1, recordEnd - 1).toString("utf8");
        const separator = record.indexOf("=");
        if (separator > 0) fields[record.slice(0, separator)] = record.slice(separator + 1);
        offset = recordEnd;
    }
    return fields;
}

function parseTarGzip(archiveBytes) {
    const tar = zlib.gunzipSync(archiveBytes);
    const files = [];
    let offset = 0;
    let nextPax = Object.create(null);
    while (offset + 512 <= tar.length) {
        const header = tar.subarray(offset, offset + 512);
        if (header.every((value) => value === 0)) break;
        const size = readTarNumber(header, 124, 12);
        assert(Number.isSafeInteger(size) && size >= 0, "Invalid tar entry size.");
        const bodyStart = offset + 512;
        const bodyEnd = bodyStart + size;
        assert(bodyEnd <= tar.length, "Tar entry exceeds the archive.");
        const type = String.fromCharCode(header[156] || 0);
        const prefix = readTarString(header, 345, 155);
        const headerName = readTarString(header, 0, 100);
        const headerPath = prefix ? `${prefix}/${headerName}` : headerName;
        const body = tar.subarray(bodyStart, bodyEnd);
        if (type === "x") {
            nextPax = parsePax(body);
        } else if (type === "g") {
            parsePax(body);
        } else {
            const entryPath = nextPax.path || headerPath;
            nextPax = Object.create(null);
            if (type === "" || type === "\u0000" || type === "0") {
                files.push({ path: entryPath.replace(/\\/g, "/"), bytes: Buffer.from(body) });
            } else {
                assert(
                    type === "5",
                    `Unsupported non-file npm tar entry '${entryPath}' of type '${type}'.`,
                );
            }
        }
        offset = bodyStart + Math.ceil(size / 512) * 512;
    }
    return files;
}

function integrityDigest(archiveBytes, integrity) {
    const separator = integrity.indexOf("-");
    assert(separator > 0, `Invalid npm integrity '${integrity}'.`);
    const algorithm = integrity.slice(0, separator);
    assert(algorithm === "sha512", `Unsupported npm integrity algorithm '${algorithm}'.`);
    return `${algorithm}-${cryptoModule
        .createHash(algorithm)
        .update(archiveBytes)
        .digest("base64")}`;
}

function admissiblePackagePath(relativePath) {
    if (!relativePath || relativePath.startsWith("/") || relativePath.includes("\u0000")) {
        return false;
    }
    const segments = relativePath.split("/");
    if (
        segments.some(
            (segment) =>
                !segment ||
                segment === "." ||
                segment === ".." ||
                EXCLUDED_DIRECTORIES.has(segment.toLowerCase()),
        )
    ) {
        return false;
    }
    const lower = relativePath.toLowerCase();
    return Array.from(ALLOWED_SUFFIXES).some((suffix) => lower.endsWith(suffix));
}

function loadArchive(archive) {
    const archivePath = path.join(archiveDirectory, archive.archive);
    const archiveBytes = fs.readFileSync(archivePath);
    assert(
        integrityDigest(archiveBytes, archive.integrity) === archive.integrity,
        `${archive.packageName}@${archive.version} npm integrity mismatch.`,
    );
    const accepted = new Map();
    for (const file of parseTarGzip(archiveBytes)) {
        if (!file.path.startsWith(archive.archiveRoot)) continue;
        const relativePath = file.path.slice(archive.archiveRoot.length);
        if (!admissiblePackagePath(relativePath)) continue;
        assert(
            file.bytes.length <= MAXIMUM_SINGLE_FILE_BYTES,
            `${archive.packageName}/${relativePath} exceeds the T2-1 single-file quota.`,
        );
        const vfsPath = `/node_modules/${archive.installName}/${relativePath}`;
        assert(!accepted.has(vfsPath), `Duplicate npm archive path '${vfsPath}'.`);
        let text;
        try {
            text = textDecoder.decode(file.bytes);
        } catch (error) {
            throw new Error(`${vfsPath} is not valid UTF-8.`, { cause: error });
        }
        accepted.set(vfsPath, text);
    }
    const packageJsonPath = `/node_modules/${archive.installName}/package.json`;
    assert(accepted.has(packageJsonPath), `${archive.packageName} package.json was not admitted.`);
    const packageJson = JSON.parse(accepted.get(packageJsonPath));
    assert(
        packageJson.name === archive.packageName &&
            packageJson.version === archive.version &&
            packageJson.license === archive.license,
        `${archive.packageName} package identity/license drifted from the pinned npm release.`,
    );
    return accepted;
}

const packageFiles = new Map();
for (const archive of matrix.archives) {
    for (const [filePath, text] of loadArchive(archive)) {
        assert(!packageFiles.has(filePath), `Duplicate matrix VFS path '${filePath}'.`);
        packageFiles.set(filePath, text);
    }
}
const packagePaths = Array.from(packageFiles.keys()).sort(compareCodeUnits);
const packageTotalBytes = packagePaths.reduce(
    (total, filePath) => total + Buffer.byteLength(packageFiles.get(filePath), "utf8"),
    0,
);
const packagePathBytes = packagePaths.reduce(
    (total, filePath) => total + Buffer.byteLength(`dependencies${filePath}`, "utf8"),
    0,
);
assert(
    packagePaths.length <= MAXIMUM_FILES &&
        packageTotalBytes <= MAXIMUM_TOTAL_BYTES &&
        packagePathBytes <= MAXIMUM_PATH_BYTES,
    `Real package matrix exceeds T2-1 quotas: files=${packagePaths.length}, ` +
        `bytes=${packageTotalBytes}, pathBytes=${packagePathBytes}.`,
);

function createVirtualFileSystem(sourcePath, sourceText, includeLodashTypes = true) {
    const files = new Map(
        packagePaths
            .filter(
                (filePath) =>
                    includeLodashTypes || !filePath.startsWith("/node_modules/@types/lodash/"),
            )
            .map((filePath) => [filePath, packageFiles.get(filePath)]),
    );
    files.set(sourcePath, sourceText);
    const directories = new Set(["/", "/node_modules", "/node_modules/@types", "/sources"]);
    for (const filePath of files.keys()) {
        let directory = filePath.slice(0, filePath.lastIndexOf("/"));
        while (directory.length > 0) {
            directories.add(directory);
            if (directory === "/") break;
            directory = directory.slice(0, directory.lastIndexOf("/")) || "/";
        }
    }
    const dependencyTypeNames = includeLodashTypes ? Object.freeze(["lodash"]) : Object.freeze([]);
    return Object.freeze({
        directoryExists: (candidate) => directories.has(candidate.replace(/\\/g, "/")),
        fileExists: (candidate) => files.has(candidate.replace(/\\/g, "/")),
        getDependencyLayer: () => Object.freeze({
            fingerprint: cryptoModule
                .createHash("sha256")
                .update(matrix.archives.map((archive) => archive.integrity).join("\n"), "utf8")
                .digest("hex"),
        }),
        getDependencyTypeNames: () => dependencyTypeNames,
        getDirectories: (candidate) => {
            const normalized = candidate.replace(/\\/g, "/");
            const prefix = normalized === "/" ? "/" : `${normalized}/`;
            return Array.from(directories)
                .filter(
                    (directory) =>
                        directory.startsWith(prefix) &&
                        !directory.slice(prefix.length).includes("/"),
                )
                .map((directory) => directory.slice(prefix.length))
                .filter(Boolean)
                .sort(compareCodeUnits);
        },
        readFile: (candidate) => files.get(candidate.replace(/\\/g, "/")),
        readRecord: (candidate) => {
            const normalized = candidate.replace(/\\/g, "/");
            const text = files.get(normalized);
            return text === undefined ? undefined : { path: normalized, text };
        },
        files,
    });
}

function extensionFor(resolvedFileName) {
    if (resolvedFileName.endsWith(".d.mts")) return ts.Extension.Dmts;
    if (resolvedFileName.endsWith(".d.cts")) return ts.Extension.Dcts;
    if (resolvedFileName.endsWith(".d.ts")) return ts.Extension.Dts;
    if (resolvedFileName.endsWith(".mts")) return ts.Extension.Mts;
    if (resolvedFileName.endsWith(".cts")) return ts.Extension.Cts;
    if (resolvedFileName.endsWith(".mjs")) return ts.Extension.Mjs;
    if (resolvedFileName.endsWith(".cjs")) return ts.Extension.Cjs;
    if (resolvedFileName.endsWith(".json")) return ts.Extension.Json;
    if (resolvedFileName.endsWith(".jsx")) return ts.Extension.Jsx;
    if (resolvedFileName.endsWith(".js")) return ts.Extension.Js;
    if (resolvedFileName.endsWith(".tsx")) return ts.Extension.Tsx;
    return ts.Extension.Ts;
}

function formatDiagnostics(diagnostics) {
    return diagnostics
        .map((diagnostic) =>
            `${diagnostic.code}: ${ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n")}`,
        )
        .join(" | ");
}

function compileCase(matrixCase, profile, includeLodashTypes = true) {
    const sourcePath = `/sources/${matrixCase.name}.${profile === "node" ? "mts" : "ts"}`;
    const sourceText = `${matrixCase.sourceLines.join("\n")}\n`;
    const vfs = createVirtualFileSystem(sourcePath, sourceText, includeLodashTypes);
    const baseOptions = Object.freeze({
        allowJs: false,
        allowSyntheticDefaultImports: true,
        alwaysStrict: true,
        esModuleInterop: true,
        forceConsistentCasingInFileNames: true,
        ignoreDeprecations: "6.0",
        module: profile === "node" ? ts.ModuleKind.NodeNext : ts.ModuleKind.CommonJS,
        moduleResolution: profile === "node"
            ? ts.ModuleResolutionKind.NodeNext
            : ts.ModuleResolutionKind.Node10,
        noEmitOnError: true,
        outDir: "/outputs",
        resolveJsonModule: true,
        ...(profile === "node"
            ? {
                  resolvePackageJsonExports: true,
                  resolvePackageJsonImports: true,
              }
            : {}),
        skipLibCheck: true,
        strict: true,
        target: profile === "node" ? ts.ScriptTarget.ES2022 : ts.ScriptTarget.ES2018,
        types: [],
    });
    const options = createDependencyAwareCompilerOptions(vfs, baseOptions);
    const resolver = createDependencyLayerResolution(vfs, options);
    assert(resolver !== null, `${matrixCase.name}/${profile} dependency resolver did not activate.`);
    const baseHost = ts.createCompilerHost(options, true);
    const outputs = new Map();
    const host = {
        ...baseHost,
        createDirectory: () => {},
        directoryExists: (candidate) =>
            vfs.directoryExists(candidate) || baseHost.directoryExists(candidate),
        fileExists: (candidate) => vfs.fileExists(candidate) || baseHost.fileExists(candidate),
        getDirectories: (candidate) => {
            const virtual = vfs.getDirectories(candidate);
            return virtual.length > 0 ? virtual : baseHost.getDirectories(candidate);
        },
        getSourceFile: (fileName, languageVersion) => {
            const normalized = fileName.replace(/\\/g, "/");
            const text = vfs.readFile(normalized);
            if (text === undefined) return baseHost.getSourceFile(fileName, languageVersion);
            const impliedFormat = normalized.endsWith(".mts") || normalized.endsWith(".mjs") ||
                normalized.startsWith("/node_modules/") &&
                    resolver.impliedModuleFormatFor(normalized) === "esm"
                ? ts.ModuleKind.ESNext
                : ts.ModuleKind.CommonJS;
            return ts.createSourceFile(
                normalized,
                text,
                { languageVersion, impliedNodeFormat: impliedFormat },
                true,
            );
        },
        readFile: (candidate) => vfs.readFile(candidate) ?? baseHost.readFile(candidate),
        realpath: (candidate) =>
            candidate.startsWith("/") ? candidate : baseHost.realpath(candidate),
        resolveModuleNameLiterals: (
            moduleLiterals,
            containingFile,
            _redirectedReference,
            _compilerOptions,
            containingSourceFile,
        ) => moduleLiterals.map((literal) => {
            const usageMode = profile === "node" &&
                ts.getModeForUsageLocation(containingSourceFile, literal, options) ===
                    ts.ModuleKind.ESNext
                ? modes.import
                : modes.require;
            const result = resolver.resolveModule(literal.text, containingFile, usageMode);
            if (result.kind === "resolved") {
                return {
                    resolvedModule: {
                        extension: extensionFor(result.target.path),
                        isExternalLibraryImport: result.target.path.startsWith("/node_modules/"),
                        resolvedFileName: result.target.path,
                    },
                };
            }
            return {
                resolvedModule: ts.resolveModuleName(
                    literal.text,
                    containingFile,
                    options,
                    host,
                ).resolvedModule,
            };
        }),
        resolveTypeReferenceDirectiveReferences: (references, containingFile) =>
            references.map((reference) => {
                const name = typeof reference === "string" ? reference : reference.fileName;
                const result = resolver.resolveTypeReference(name, containingFile);
                if (result.kind === "resolved") {
                    return {
                        resolvedTypeReferenceDirective: {
                            isExternalLibraryImport: true,
                            primary: true,
                            resolvedFileName: result.target.path,
                        },
                    };
                }
                return {
                    resolvedTypeReferenceDirective: ts.resolveTypeReferenceDirective(
                        name,
                        containingFile,
                        options,
                        host,
                    ).resolvedTypeReferenceDirective,
                };
            }),
        writeFile: (fileName, text) => outputs.set(fileName.replace(/\\/g, "/"), text),
    };
    const program = ts.createProgram({ host, options, rootNames: [sourcePath] });
    const diagnostics = ts.getPreEmitDiagnostics(program);
    const emit = diagnostics.some((diagnostic) => diagnostic.category === ts.DiagnosticCategory.Error)
        ? { emitSkipped: true }
        : program.emit();
    const outputExtension = profile === "node" ? ".mjs" : ".js";
    const output = Array.from(outputs.entries()).find(([fileName]) =>
        fileName.endsWith(`/${matrixCase.name}${outputExtension}`),
    );
    const sourceFiles = program.getSourceFiles().map((sourceFile) => sourceFile.fileName);
    resolver.release();
    return { diagnostics, emit, output, sourceFiles };
}

function rhinoEntry(packageJson) {
    const exportsValue = packageJson.exports;
    if (exportsValue !== undefined) {
        const root = exportsValue && typeof exportsValue === "object" &&
            !Array.isArray(exportsValue) && Object.prototype.hasOwnProperty.call(exportsValue, ".")
            ? exportsValue["."]
            : exportsValue;
        if (typeof root === "string") return { entry: root.replace(/^\.\//, "") };
        if (root && typeof root === "object" && !Array.isArray(root)) {
            const target = root.require ?? root.node ?? root.default;
            if (typeof target === "string") {
                const entry = target.replace(/^\.\//, "");
                const esm = packageJson.type === "module" && !entry.endsWith(".cjs") ||
                    entry.endsWith(".mjs") || entry.endsWith(".mts");
                return esm ? { errorCode: "ERR_AUTOJS6_TYPESCRIPT_RHINO_ESM_ONLY_DEPENDENCY" } : { entry };
            }
            if (Object.prototype.hasOwnProperty.call(root, "import")) {
                return { errorCode: "ERR_AUTOJS6_TYPESCRIPT_RHINO_ESM_ONLY_DEPENDENCY" };
            }
        }
    }
    const entry = typeof packageJson.main === "string" ? packageJson.main.replace(/^\.\//, "") : "index.js";
    return packageJson.type === "module" && !entry.endsWith(".cjs")
        ? { errorCode: "ERR_AUTOJS6_TYPESCRIPT_RHINO_ESM_ONLY_DEPENDENCY" }
        : { entry };
}

const packageJsonByName = new Map(
    matrix.archives
        .filter((archive) => archive.role === "runtime")
        .map((archive) => [
            archive.installName,
            JSON.parse(packageFiles.get(`/node_modules/${archive.installName}/package.json`)),
        ]),
);

for (const matrixCase of matrix.cases) {
    const boundary = rhinoEntry(packageJsonByName.get(matrixCase.name));
    if (matrixCase.rhino.runtime === "execute") {
        assert(
            boundary.entry === matrixCase.rhino.entry,
            `${matrixCase.name} Rhino entry drifted: ${stableJson(boundary)}.`,
        );
    } else {
        assert(
            boundary.errorCode === matrixCase.rhino.errorCode,
            `${matrixCase.name} Rhino stable boundary drifted: ${stableJson(boundary)}.`,
        );
    }
}

const runtimeRoot = fs.mkdtempSync(path.join(os.tmpdir(), "autojs6-t2-5-matrix-"));
const resolvedTempRoot = path.resolve(os.tmpdir()) + path.sep;
assert(
    path.resolve(runtimeRoot).startsWith(resolvedTempRoot),
    "T2-5 runtime materialization escaped the operating-system temporary directory.",
);
try {
    for (const [filePath, text] of packageFiles) {
        const destination = path.join(runtimeRoot, filePath.replace(/^\//, ""));
        fs.mkdirSync(path.dirname(destination), { recursive: true });
        fs.writeFileSync(destination, text, "utf8");
    }

    const results = [];
    for (const matrixCase of matrix.cases) {
        for (const profile of ["rhino", "node"]) {
            const compiled = compileCase(matrixCase, profile);
            assert(
                compiled.diagnostics.length === 0 &&
                    !compiled.emit.emitSkipped &&
                    compiled.output,
                `${matrixCase.name}/${profile} real package compilation failed: ` +
                    formatDiagnostics(compiled.diagnostics),
            );
            assert(
                compiled.sourceFiles.some((fileName) => fileName.startsWith("/node_modules/")),
                `${matrixCase.name}/${profile} Program did not include real package declarations.`,
            );
            const profileExpectation = matrixCase[profile];
            if (profileExpectation.runtime === "stable-error") {
                results.push({
                    package: matrixCase.name,
                    profile,
                    result: profileExpectation.errorCode,
                });
                continue;
            }
            const outputExtension = profile === "node" ? "mjs" : "cjs";
            const outputPath = path.join(
                runtimeRoot,
                `t2-5-${matrixCase.name}-${profile}.${outputExtension}`,
            );
            fs.writeFileSync(outputPath, compiled.output[1], "utf8");
            const runner = profile === "node"
                ? `import(${JSON.stringify(pathToFileURL(outputPath).href)})` +
                    `.then((value) => process.stdout.write(JSON.stringify(value.result)))`
                : `process.stdout.write(JSON.stringify(require(${JSON.stringify(outputPath)}).result))`;
            const runnerArguments = profile === "node"
                ? ["--input-type=module", "--eval", runner]
                : ["--input-type=commonjs", "--eval", runner];
            const execution = childProcess.spawnSync(
                process.execPath,
                runnerArguments,
                { cwd: runtimeRoot, encoding: "utf8", timeout: 30_000 },
            );
            assert(
                execution.status === 0 && !execution.error,
                `${matrixCase.name}/${profile} execution failed: ` +
                    `${execution.error?.message || ""}${execution.stderr}`,
            );
            const actual = JSON.parse(execution.stdout);
            assert(
                stableJson(actual) === stableJson(matrixCase.expected),
                `${matrixCase.name}/${profile} returned ${stableJson(actual)} instead of ` +
                    `${stableJson(matrixCase.expected)}.`,
            );
            results.push({ package: matrixCase.name, profile, result: actual });
        }
    }

    const lodashWithoutTypes = compileCase(
        matrix.cases.find((matrixCase) => matrixCase.name === "lodash"),
        "node",
        false,
    );
    assert(
        lodashWithoutTypes.diagnostics.some((diagnostic) => diagnostic.code === 7016),
        "lodash without @types/lodash did not produce the documented TS7016 boundary.",
    );
    console.log(JSON.stringify({
        schema: matrix.schema,
        packageFiles: packagePaths.length,
        packageBytes: packageTotalBytes,
        packagePathBytes,
        results,
        lodashWithoutTypes: 7016,
    }, null, 2));
    console.log(
        "T2-5 real package matrix verified: five pinned npm tarballs, dual-profile " +
            "strict compilation, applicable CJS/ESM execution, two Rhino ESM-only stable " +
            "boundaries, and the lodash @types workaround.",
    );
} finally {
    fs.rmSync(runtimeRoot, { recursive: true, force: true });
}
