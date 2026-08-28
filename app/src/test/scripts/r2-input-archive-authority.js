"use strict";

const crypto = require("node:crypto");

const INPUT_ARCHIVE_SCHEMA_REVISION = 1;
const ORDINARY_INPUT_QUOTA_REVISION = 1;
const CURRENT_ORDINARY_INPUT_QUOTA_REVISION = 3;
const TEST_ONLY_EXPANDED_INPUT_QUOTA_REVISION = 2_147_483_647;
const INVENTORY_CANONICAL_PREFIX = "autojs6.typescript.input-inventory.v1\n";
const DOMAIN_ORDER = Object.freeze({
    source: 0,
    declaration: 1,
    config: 2,
    "package-metadata": 3,
    "package-target-v1": 4,
    "package-target-v2": 5,
});
const SHA256_PATTERN = /^[0-9a-f]{64}$/;

const compareCodeUnits = (left, right) =>
    left < right ? -1 : left > right ? 1 : 0;

const sha256 = (bytes) =>
    crypto.createHash("sha256").update(bytes).digest("hex");

const bytes = (value) =>
    Buffer.isBuffer(value) ? value : Buffer.from(value, "utf8");

const record = (domain, path, value) => {
    const content = bytes(value);
    return Object.freeze({
        byteLength: content.byteLength,
        domain,
        path,
        sha256: sha256(content),
    });
};

const descriptorRecord = ({ domain, path, byteLength, sha256: digest }) => {
    if (
        !Number.isSafeInteger(byteLength) ||
        byteLength < 0 ||
        typeof digest !== "string" ||
        !SHA256_PATTERN.test(digest)
    ) {
        throw new Error("Input archive descriptor is not canonical.");
    }
    return Object.freeze({
        byteLength,
        domain,
        path,
        sha256: digest,
    });
};

const requireRecord = (value) => {
    if (
        value === null ||
        typeof value !== "object" ||
        !Object.prototype.hasOwnProperty.call(DOMAIN_ORDER, value.domain) ||
        typeof value.path !== "string" ||
        value.path.length === 0 ||
        value.path.includes("\u0000") ||
        value.path.includes("\n") ||
        !Number.isSafeInteger(value.byteLength) ||
        value.byteLength < 0 ||
        typeof value.sha256 !== "string" ||
        !SHA256_PATTERN.test(value.sha256)
    ) {
        throw new Error("Input archive record is not canonical.");
    }
    return value;
};

const inventoryFingerprint = (records) => {
    const ordered = records.map(requireRecord).slice().sort((left, right) => {
        const domainOrder = DOMAIN_ORDER[left.domain] - DOMAIN_ORDER[right.domain];
        return domainOrder !== 0
            ? domainOrder
            : compareCodeUnits(left.path, right.path);
    });
    const canonical = ordered.reduce(
        (value, item) =>
            value +
            `${item.domain}\u0000${item.path}\u0000${item.byteLength}` +
            `\u0000${item.sha256}\n`,
        INVENTORY_CANONICAL_PREFIX,
    );
    return sha256(Buffer.from(canonical, "utf8"));
};

const projectInputAuthority = ({
    configRecord = null,
    declarationProfileFingerprint = null,
    fileRecords,
    inputQuotaRevision = ORDINARY_INPUT_QUOTA_REVISION,
}) => {
    if (!Array.isArray(fileRecords) || fileRecords.length === 0) {
        throw new Error("Project input authority requires exact file records.");
    }
    const files = fileRecords.map(requireRecord);
    const config = configRecord === null ? null : requireRecord(configRecord);
    if (config !== null && config.domain !== "config") {
        throw new Error("Project input config record uses the wrong domain.");
    }
    const declarations = files.filter((item) => item.domain === "declaration");
    if (declarations.length === 0) {
        throw new Error("Project input authority requires a retained declaration.");
    }
    const caller = files.filter((item) => item.domain !== "declaration");
    if (config !== null) caller.push(config);
    const retained = caller.concat(declarations);
    const declarationInventoryFingerprint = inventoryFingerprint(declarations);
    const profileFingerprint =
        declarationProfileFingerprint ?? declarationInventoryFingerprint;
    if (typeof profileFingerprint !== "string" || !SHA256_PATTERN.test(profileFingerprint)) {
        throw new Error("Declaration profile fingerprint is not canonical.");
    }
    return Object.freeze({
        callerInventoryFingerprint: inventoryFingerprint(caller),
        declarationByteLength: declarations.reduce(
            (total, item) => total + item.byteLength,
            0,
        ),
        declarationFileCount: declarations.length,
        declarationInventoryFingerprint,
        declarationProfileFingerprint: profileFingerprint,
        fileCount: files.length,
        inputArchiveSchemaRevision: INPUT_ARCHIVE_SCHEMA_REVISION,
        inputQuotaRevision,
        largestFileBytes: retained.reduce(
            (largest, item) => Math.max(largest, item.byteLength),
            0,
        ),
        retainedFileCount: retained.length,
        retainedInventoryFingerprint: inventoryFingerprint(retained),
        totalBytes: retained.reduce((total, item) => total + item.byteLength, 0),
    });
};

module.exports = Object.freeze({
    CURRENT_ORDINARY_INPUT_QUOTA_REVISION,
    INPUT_ARCHIVE_SCHEMA_REVISION,
    INVENTORY_CANONICAL_PREFIX,
    ORDINARY_INPUT_QUOTA_REVISION,
    TEST_ONLY_EXPANDED_INPUT_QUOTA_REVISION,
    descriptorRecord,
    inventoryFingerprint,
    projectInputAuthority,
    record,
    sha256,
});
