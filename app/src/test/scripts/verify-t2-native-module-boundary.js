"use strict";

const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const zlib = require("node:zlib");

const [manifestArgument, archiveDirectoryArgument, layerArgument, resolverArgument,
    workerArgument, contractArgument, compilerArgument] = process.argv.slice(2);
if (process.argv.slice(2).length !== 7) {
    throw new Error(
        "Usage: node verify-t2-native-module-boundary.js <manifest.json> " +
            "<archive-directory> <layer.kt> <resolver.kt> <worker.js> <contract.kt> " +
            "<typescript.js>",
    );
}

const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const manifest = JSON.parse(fs.readFileSync(path.resolve(manifestArgument), "utf8"));
const layerSource = fs.readFileSync(path.resolve(layerArgument), "utf8");
const resolverSource = fs.readFileSync(path.resolve(resolverArgument), "utf8");
const workerSource = fs.readFileSync(path.resolve(workerArgument), "utf8");
const contractSource = fs.readFileSync(path.resolve(contractArgument), "utf8");

const EXPECTED_LAYER_FINGERPRINT =
    "c159ae179de24307c456c69ae4e456ed9d7dba71dc965c22e6114f632af3834a";
const EXPECTED_RESOLVER_FINGERPRINT =
    "d8207539237d2a08a6b97b530c5e6215f4b73311fdd6954ce9f8004717ebd635";
const NATIVE_CODE = "ERR_AUTOJS6_TYPESCRIPT_NATIVE_DEPENDENCY_UNSUPPORTED";
const INSTALL_CODE = "ERR_AUTOJS6_TYPESCRIPT_DEPENDENCY_INSTALL_SCRIPT_UNSUPPORTED";
const INSTALL_KEYS = ["preinstall", "install", "postinstall"];

assert(
    manifest.schema === "autojs6.typescript.t2-6.native-module-boundary" &&
        manifest.schemaRevision === 1 &&
        manifest.dependencyLayerPolicyRevision === 2 &&
        manifest.dependencyLayerPolicyFingerprint === EXPECTED_LAYER_FINGERPRINT &&
        manifest.resolverPolicyRevision === 4 &&
        manifest.resolverPolicyFingerprint === EXPECTED_RESOLVER_FINGERPRINT,
    "T2-6 manifest schema or policy authority drifted.",
);
assert(
    JSON.stringify(manifest.boundaryPriority) ===
        JSON.stringify(["native-binary", "binding-gyp", "gypfile", "install-lifecycle"]),
    "T2-6 deterministic boundary priority drifted.",
);
assert(
    manifest.errors.nativeDependency === NATIVE_CODE &&
        manifest.errors.installLifecycle === INSTALL_CODE,
    "T2-6 stable public error codes drifted.",
);

function tarString(buffer, offset, length) {
    const terminator = buffer.indexOf(0, offset);
    const end = terminator < 0 || terminator > offset + length
        ? offset + length
        : terminator;
    return buffer.subarray(offset, end).toString("utf8").trim();
}

function tarNumber(buffer, offset, length) {
    const value = tarString(buffer, offset, length).replace(/^0+/, "");
    return value ? Number.parseInt(value, 8) : 0;
}

function parsePax(body) {
    const fields = Object.create(null);
    let offset = 0;
    while (offset < body.length) {
        const space = body.indexOf(0x20, offset);
        assert(space > offset, "Malformed PAX length.");
        const length = Number.parseInt(body.subarray(offset, space).toString("ascii"), 10);
        const end = offset + length;
        assert(Number.isSafeInteger(length) && length > 0 && end <= body.length,
            "Malformed PAX record.");
        const record = body.subarray(space + 1, end - 1).toString("utf8");
        const separator = record.indexOf("=");
        if (separator > 0) fields[record.slice(0, separator)] = record.slice(separator + 1);
        offset = end;
    }
    return fields;
}

function parseTarGzip(bytes) {
    const tar = zlib.gunzipSync(bytes);
    const files = new Map();
    let offset = 0;
    let pax = Object.create(null);
    while (offset + 512 <= tar.length) {
        const header = tar.subarray(offset, offset + 512);
        if (header.every((value) => value === 0)) break;
        const size = tarNumber(header, 124, 12);
        const start = offset + 512;
        const end = start + size;
        assert(Number.isSafeInteger(size) && size >= 0 && end <= tar.length,
            "Malformed npm tar entry.");
        const type = String.fromCharCode(header[156] || 0);
        const prefix = tarString(header, 345, 155);
        const name = tarString(header, 0, 100);
        const headerPath = prefix ? `${prefix}/${name}` : name;
        const body = tar.subarray(start, end);
        if (type === "x") {
            pax = parsePax(body);
        } else if (type !== "g") {
            const entryPath = (pax.path || headerPath).replace(/\\/g, "/");
            pax = Object.create(null);
            if (type === "" || type === "\u0000" || type === "0") {
                assert(!files.has(entryPath), `Duplicate npm tar path '${entryPath}'.`);
                files.set(entryPath, Buffer.from(body));
            } else {
                assert(type === "5", `Unsupported npm tar entry type '${type}'.`);
            }
        }
        offset = start + Math.ceil(size / 512) * 512;
    }
    return files;
}

function metadataBoundary(packageJson) {
    if (packageJson.gypfile === true) {
        return { code: NATIVE_CODE, signal: "gypfile", priority: 2 };
    }
    const scripts = packageJson.scripts && typeof packageJson.scripts === "object"
        ? packageJson.scripts
        : {};
    const lifecycle = INSTALL_KEYS.find((key) => Object.hasOwn(scripts, key));
    return lifecycle
        ? { code: INSTALL_CODE, signal: `scripts.${lifecycle}`, priority: 3 }
        : null;
}

const archive = manifest.package;
const archivePath = path.join(path.resolve(archiveDirectoryArgument), archive.archive);
const archiveBytes = fs.readFileSync(archivePath);
const actualIntegrity = `sha512-${crypto.createHash("sha512").update(archiveBytes).digest("base64")}`;
assert(actualIntegrity === archive.integrity, "bcrypt npm tarball integrity mismatch.");
const files = parseTarGzip(archiveBytes);
const packagePath = `${archive.archiveRoot}package.json`;
assert(files.has(packagePath), "bcrypt package.json is absent from the pinned npm tarball.");
const packageJson = JSON.parse(files.get(packagePath).toString("utf8"));
assert(
    packageJson.name === archive.name &&
        packageJson.version === archive.version &&
        packageJson.license === archive.license,
    "bcrypt package identity drifted.",
);
assert(
    packageJson.scripts?.[archive.requiredSignals.installLifecycleKey] ===
        archive.requiredSignals.installLifecycleCommand,
    "bcrypt install lifecycle command drifted.",
);

const relativeFiles = Array.from(files.keys())
    .filter((entryPath) => entryPath.startsWith(archive.archiveRoot))
    .map((entryPath) => entryPath.slice(archive.archiveRoot.length))
    .filter(Boolean)
    .sort();
const candidates = [];
for (const relativePath of relativeFiles) {
    const folded = relativePath.toLowerCase();
    if (folded.endsWith(archive.requiredSignals.nativeBinarySuffix)) {
        candidates.push({ code: NATIVE_CODE, signal: "native-binary", path: relativePath, priority: 0 });
    } else if (folded === archive.requiredSignals.bindingGyp) {
        candidates.push({ code: NATIVE_CODE, signal: "binding-gyp", path: relativePath, priority: 1 });
    }
}
const packageBoundary = metadataBoundary(packageJson);
if (packageBoundary) candidates.push({ ...packageBoundary, path: "package.json" });
candidates.sort((left, right) =>
    left.priority - right.priority || left.path.localeCompare(right.path, "en") ||
        left.signal.localeCompare(right.signal, "en"));
const selected = candidates[0];
assert(selected, "bcrypt did not expose a native or install-lifecycle signal.");
assert(
    selected.code === archive.expectedFailure.code &&
        selected.signal === archive.expectedFailure.signal &&
        selected.path === archive.expectedFailure.path,
    `bcrypt boundary was not deterministic: ${JSON.stringify(selected)}.`,
);
assert(candidates.some((entry) => entry.signal === "binding-gyp"),
    "bcrypt binding.gyp signal is absent.");
assert(candidates.some((entry) => entry.signal === "scripts.install"),
    "bcrypt install lifecycle signal is absent.");
assert(!relativeFiles.some((entry) => /(^|\/)android([^/]*)(\/|$)/i.test(entry)),
    "The fixture unexpectedly contains an Android native build; revisit the documented boundary.");

for (const testCase of manifest.syntheticMetadataCases) {
    const boundary = metadataBoundary(testCase.packageJson);
    assert(
        boundary && boundary.signal === testCase.signal && boundary.code === testCase.expectedCode,
        `Synthetic metadata boundary '${testCase.signal}' drifted.`,
    );
}
for (const ordinaryScript of manifest.ordinaryScripts) {
    assert(
        metadataBoundary({ scripts: { [ordinaryScript]: "ignored" } }) === null,
        `Ordinary dependency script '${ordinaryScript}' must remain inert and admissible.`,
    );
}

if (!global.crypto) {
    Object.defineProperty(global, "crypto", {
        configurable: true,
        value: crypto.webcrypto,
    });
}
global.ts = require(path.resolve(compilerArgument));
global.importScripts = (specifier) => {
    assert(specifier === "typescript.js", "Worker imported an unexpected script.");
};
const publishedMessages = [];
global.self = { postMessage: (message) => publishedMessages.push(message) };
vm.runInThisContext(workerSource, { filename: path.resolve(workerArgument) });
assert(
    publishedMessages.length === 1 &&
        publishedMessages[0].kind === "ready" &&
        publishedMessages[0].tsVersion === "6.0.3",
    "Worker did not bootstrap with the pinned TypeScript 6.0.3 runtime.",
);
const validateWorkerBoundary = vm.runInThisContext("validateDependencyBoundaryRecord");
function requireWorkerFailure(workerPath, text, expectedCode) {
    let failure = null;
    try {
        validateWorkerBoundary(workerPath, text);
    } catch (error) {
        failure = error;
    }
    assert(
        failure && String(failure.message).startsWith(`${expectedCode}:`),
        `Worker did not reject '${workerPath}' with ${expectedCode}.`,
    );
    return failure;
}
requireWorkerFailure("/node_modules/bcrypt/prebuilds/linux-arm64/bcrypt.node", "", NATIVE_CODE);
requireWorkerFailure("/node_modules/bcrypt/binding.gyp", "{}", NATIVE_CODE);
requireWorkerFailure(
    "/node_modules/native-source/package.json",
    JSON.stringify({ name: "native-source", gypfile: true }),
    NATIVE_CODE,
);
for (const lifecycle of INSTALL_KEYS) {
    requireWorkerFailure(
        `/node_modules/${lifecycle}-package/package.json`,
        JSON.stringify({ scripts: { [lifecycle]: "prepare-native" } }),
        INSTALL_CODE,
    );
}
const combinedLifecycleFailure = requireWorkerFailure(
    "/node_modules/all-lifecycle/package.json",
    JSON.stringify({
        scripts: {
            preinstall: "first-in-json",
            postinstall: "second-in-json",
            install: "last-in-json",
        },
    }),
    INSTALL_CODE,
);
assert(
    String(combinedLifecycleFailure.message).includes("scripts.install"),
    "Worker lifecycle signal ordering drifted from canonical signal ordering.",
);
validateWorkerBoundary(
    "/node_modules/ordinary/package.json",
    JSON.stringify({ scripts: { build: "ignored", test: "ignored" } }),
);

for (const marker of [
    "dependency-layer-policy.v2",
    "native-binary",
    "binding.gyp",
    "gypfile",
    "preinstall",
    "postinstall",
]) {
    assert(layerSource.includes(marker), `Dependency-layer source is missing '${marker}'.`);
}
for (const marker of ["check(POLICY_REVISION == 4)",
    "native-addon-and-install-lifecycle-rejected-t2-6"]) {
    assert(resolverSource.includes(marker), `Resolver source is missing '${marker}'.`);
}
for (const marker of ["dependencyBoundaryAuthority", "validateDependencyBoundaryRecord",
    NATIVE_CODE, INSTALL_CODE, "postinstall", "scripts.${lifecycle}",
    EXPECTED_LAYER_FINGERPRINT, EXPECTED_RESOLVER_FINGERPRINT]) {
    assert(workerSource.includes(marker), `Worker boundary defense is missing '${marker}'.`);
}
for (const marker of [NATIVE_CODE, INSTALL_CODE,
    "DEPENDENCY_LAYER_POLICY_REVISION = 2", "DEPENDENCY_RESOLVER_POLICY_REVISION = 4",
    EXPECTED_RESOLVER_FINGERPRINT]) {
    assert(contractSource.includes(marker), `Public R2 contract is missing '${marker}'.`);
}

process.stdout.write(
    `T2-6 native dependency boundary verified: ${archive.name}@${archive.version} -> ` +
        `${selected.code} (${selected.signal}, ${selected.path}); ${candidates.length} signals.\n`,
);
