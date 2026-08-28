#!/usr/bin/env node

import { createHash } from "node:crypto";
import { promises as fs } from "node:fs";
import path from "node:path";
import process from "node:process";

const REGISTRY_METADATA_URL = "https://registry.npmjs.org/typescript";
const MAX_METADATA_BYTES = 64 * 1024 * 1024;
const MAX_TARBALL_BYTES = 32 * 1024 * 1024;

const fail = (message) => {
    throw new Error(message);
};
const parseArguments = (arguments_) => {
    const result = {
        currentLock: "compiler-distribution.lock.json",
        major: 6,
        outputDirectory: "build/reports/typescript-upgrade/preflight",
    };
    for (let index = 0; index < arguments_.length; index += 1) {
        const argument = arguments_[index];
        if (argument === "--help") {
            process.stdout.write(
                "Usage: node tools/prepare-typescript-upgrade.mjs " +
                    "[--major 6] [--current-lock <path>] [--output-dir <path>]\n",
            );
            process.exit(0);
        }
        const value = arguments_[index + 1];
        if (!value || value.startsWith("--")) fail(`Missing value for ${argument}.`);
        if (argument === "--major") result.major = Number(value);
        else if (argument === "--current-lock") result.currentLock = value;
        else if (argument === "--output-dir") result.outputDirectory = value;
        else fail(`Unknown argument: ${argument}`);
        index += 1;
    }
    if (!Number.isInteger(result.major) || result.major <= 0) {
        fail("--major must be a positive integer.");
    }
    if (result.major !== 6) {
        fail(
            "This project can only preflight the JavaScript-based stable TypeScript 6.x line; " +
                "TypeScript 7.x requires a separate compiler-host architecture decision.",
        );
    }
    return result;
};
const fetchBytes = async (url, maximumBytes, accept) => {
    const response = await fetch(url, {
        headers: {
            Accept: accept,
            "User-Agent": "autojs6-typescript-engine-upgrade-preflight/1",
        },
        redirect: "error",
    });
    if (!response.ok) fail(`HTTP ${response.status} while reading ${url}.`);
    const declaredLength = Number(response.headers.get("content-length"));
    if (Number.isFinite(declaredLength) && declaredLength > maximumBytes) {
        fail(`${url} declares ${declaredLength} bytes; limit is ${maximumBytes}.`);
    }
    const bytes = Buffer.from(await response.arrayBuffer());
    if (bytes.length > maximumBytes) {
        fail(`${url} returned ${bytes.length} bytes; limit is ${maximumBytes}.`);
    }
    return bytes;
};
const digestHex = (algorithm, bytes) =>
    createHash(algorithm).update(bytes).digest("hex");
const digestSriSha512 = (bytes) =>
    `sha512-${createHash("sha512").update(bytes).digest("base64")}`;
const stableVersion = (major, version) =>
    new RegExp(`^${major}\\.(\\d+)\\.(\\d+)$`, "u").exec(version);
const compareStableVersions = (left, right) => {
    const leftParts = left.split(".").map(Number);
    const rightParts = right.split(".").map(Number);
    for (let index = 0; index < 3; index += 1) {
        if (leftParts[index] !== rightParts[index]) {
            return leftParts[index] - rightParts[index];
        }
    }
    return 0;
};
const requireLowerHex = (value, length, owner) => {
    if (typeof value !== "string" || !new RegExp(`^[0-9a-f]{${length}}$`, "u").test(value)) {
        fail(`${owner} is not a lowercase ${length}-digit hex digest.`);
    }
    return value;
};

const options = parseArguments(process.argv.slice(2));
const repositoryRoot = process.cwd();
const currentLockPath = path.resolve(repositoryRoot, options.currentLock);
const outputDirectory = path.resolve(repositoryRoot, options.outputDirectory);
const currentLock = JSON.parse(await fs.readFile(currentLockPath, "utf8"));
if (currentLock.package !== "typescript" || currentLock.schemaVersion !== 2) {
    fail("The current compiler distribution lock is not TypeScript schema revision 2.");
}

const metadataBytes = await fetchBytes(
    REGISTRY_METADATA_URL,
    MAX_METADATA_BYTES,
    "application/json",
);
const metadata = JSON.parse(metadataBytes.toString("utf8"));
const stableVersions = Object.keys(metadata.versions ?? {})
    .filter((version) => stableVersion(options.major, version))
    .sort(compareStableVersions);
if (stableVersions.length === 0) {
    fail(`The official registry exposes no stable TypeScript ${options.major}.x version.`);
}
const latestVersion = stableVersions.at(-1);
const release = metadata.versions[latestVersion];
if (release?.version !== latestVersion) {
    fail(`Registry metadata for TypeScript ${latestVersion} is inconsistent.`);
}
const expectedTarballUrl =
    `https://registry.npmjs.org/typescript/-/typescript-${latestVersion}.tgz`;
if (release.dist?.tarball !== expectedTarballUrl) {
    fail(`TypeScript ${latestVersion} tarball URL is not the exact official npm URL.`);
}
const registrySha1 = requireLowerHex(
    release.dist.shasum,
    40,
    `TypeScript ${latestVersion} registry SHA-1`,
);
if (
    typeof release.dist.integrity !== "string" ||
    !/^sha512-[A-Za-z0-9+/]+={0,2}$/u.test(release.dist.integrity)
) {
    fail(`TypeScript ${latestVersion} registry integrity is not canonical SHA-512 SRI.`);
}

const tarballBytes = await fetchBytes(
    expectedTarballUrl,
    MAX_TARBALL_BYTES,
    "application/octet-stream",
);
const computed = {
    sha1: digestHex("sha1", tarballBytes),
    sha256: digestHex("sha256", tarballBytes),
    integrity: digestSriSha512(tarballBytes),
};
if (computed.sha1 !== registrySha1 || computed.integrity !== release.dist.integrity) {
    fail(`TypeScript ${latestVersion} tarball does not match official registry digests.`);
}

const candidateLock = {
    ...currentLock,
    version: latestVersion,
    source: expectedTarballUrl,
    sha1: computed.sha1,
    sha256: computed.sha256,
    integrity: computed.integrity,
};
const comparableFields = ["version", "source", "sha1", "sha256", "integrity"];
const currentLockMatchesLatest = comparableFields.every(
    (field) => currentLock[field] === candidateLock[field],
);
const registryLatest = metadata["dist-tags"]?.latest ?? null;
const registryLatestMajor =
    typeof registryLatest === "string" ? Number(registryLatest.split(".")[0]) : null;
const report = {
    schemaVersion: 1,
    checkedAt: new Date().toISOString(),
    registry: REGISTRY_METADATA_URL,
    requestedStableMajor: options.major,
    stableVersions,
    latestStableVersion: latestVersion,
    registryLatest,
    currentVersion: currentLock.version,
    status: currentLockMatchesLatest ? "already-current" : "upgrade-available",
    currentLockMatchesLatest,
    release: {
        tarball: expectedTarballUrl,
        tarballBytes: tarballBytes.length,
        sha1: computed.sha1,
        sha256: computed.sha256,
        integrity: computed.integrity,
    },
    majorBoundary: {
        latestOverallMajor: registryLatestMajor,
        acceptedMajor: options.major,
        decision:
            registryLatestMajor === options.major
                ? "same-major"
                : "out-of-scope-separate-architecture-review",
    },
    sourceMutation: "none",
};

await fs.mkdir(outputDirectory, { recursive: true });
const candidatePath = path.join(
    outputDirectory,
    `candidate-compiler-distribution-${latestVersion}.lock.json`,
);
const reportPath = path.join(
    outputDirectory,
    `preflight-typescript-${options.major}.x.json`,
);
const tarballPath = path.join(outputDirectory, `typescript-${latestVersion}.tgz`);
await Promise.all([
    fs.writeFile(candidatePath, `${JSON.stringify(candidateLock, null, 2)}\n`, "utf8"),
    fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8"),
    fs.writeFile(tarballPath, tarballBytes),
]);

process.stdout.write(
    `TypeScript ${options.major}.x preflight: current=${currentLock.version}, ` +
        `latest=${latestVersion}, overallLatest=${registryLatest}, ` +
        `status=${report.status}, sha256=${computed.sha256}.\n` +
        `Report: ${reportPath}\nCandidate lock: ${candidatePath}\nTarball: ${tarballPath}\n`,
);
