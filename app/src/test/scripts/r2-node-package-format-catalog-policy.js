"use strict";

const crypto = require("node:crypto");

const authority = Object.freeze({
    canonicalDomain: "autojs6.typescript.node-package-format.catalog.v1",
    catalogRevision: 1,
    schema: "autojs6.typescript.node-package-format-catalog",
    schemaRevision: 1,
    profileSchemaRevision: 1,
    profileRevision: 2,
    nodeModuleOutputPolicySchemaRevision: 1,
    nodeModuleOutputPolicyRevision: 1,
    nodeModuleOutputPolicyFingerprint:
        "5c2294f0ee9d6d25bd2132b4ea24866d0a5f9caaa521a63bf81ffe1d897f6ce7",
    packageMetadataSchemaRevision: 1,
    packageMetadataPolicyRevision: 1,
    packageMetadataPolicyFingerprint:
        "fab8fec29ed0f0a1de65594620e233c9a9bef2fa514dac16c3079d5570325e97",
    packageMetadataInventoryDomain:
        "autojs6.typescript.package-metadata.inventory.v1",
});

const failureCodes = Object.freeze({
    packageTypeInvalid: "NODE_PACKAGE_TYPE_INVALID",
});

const sha256 = (bytes) =>
    crypto.createHash("sha256").update(bytes).digest("hex");

const reject = (policyCode, message) => {
    const error = new Error(message);
    error.policyCode = policyCode;
    throw error;
};

const parseTypeState = (root) => {
    if (!Object.prototype.hasOwnProperty.call(root, "type")) return "missing";
    if (root.type === "commonjs") return "commonjs";
    if (root.type === "module") return "module";
    reject(
        failureCodes.packageTypeInvalid,
        "Accepted package metadata contains an unsupported top-level type value.",
    );
};

const boundaryPath = (metadataPath) => metadataPath === "package.json"
    ? "/"
    : `/${metadataPath.slice(0, -"/package.json".length)}`;

// This mirror starts from an inventory already accepted by the frozen C5 policy. Full C5
// UTF-8/duplicate/lifecycle/path rejection remains covered by the C5 Kotlin/Worker gates; this
// oracle independently checks the P2 catalog derivation and identity layered on that authority.
const deriveCatalog = (dependencyRoots, files) => {
    if (!Array.isArray(dependencyRoots) || !Array.isArray(files) || files.length === 0) {
        throw new Error("P2-3A package metadata inventory is malformed.");
    }
    for (let index = 1; index < dependencyRoots.length; index += 1) {
        if (dependencyRoots[index - 1] >= dependencyRoots[index]) {
            throw new Error("P2-3A dependency roots are not strictly sorted.");
        }
    }
    const records = files.map((file, index) => {
        if (
            index > 0 && files[index - 1].path >= file.path ||
            file.path !== "package.json" &&
                !dependencyRoots.some((root) =>
                    file.path.startsWith(`${root}/`) &&
                    file.path.endsWith("/package.json"),
                )
        ) {
            throw new Error("P2-3A package metadata path inventory is malformed.");
        }
        const bytes = Buffer.from(file.json, "utf8");
        if (bytes.length !== file.size || sha256(bytes) !== file.sha256) {
            throw new Error("P2-3A package metadata bytes drifted from their descriptor.");
        }
        const root = JSON.parse(file.json);
        if (root === null || typeof root !== "object" || Array.isArray(root)) {
            throw new Error("P2-3A package metadata root must be an object.");
        }
        return Object.freeze({
            packageMetadataPath: file.path,
            packageBoundaryPath: boundaryPath(file.path),
            packageMetadataSize: file.size,
            packageMetadataSha256: file.sha256,
            typeState: parseTypeState(root),
        });
    });
    if (!records.some((record) => record.packageMetadataPath === "package.json")) {
        throw new Error("P2-3A package metadata inventory has no project boundary.");
    }
    for (const root of dependencyRoots) {
        if (!records.some((record) => record.packageMetadataPath === `${root}/package.json`)) {
            throw new Error("P2-3A dependency root has no exact package boundary.");
        }
    }
    const inventoryCanonicalInput = authority.packageMetadataInventoryDomain +
        dependencyRoots.map((root) => `|root=${root}`).join("") +
        records.map((record) =>
            `|file=${record.packageMetadataPath},${record.packageMetadataSize},` +
                record.packageMetadataSha256,
        ).join("");
    const packageMetadataInventoryFingerprint = sha256(
        Buffer.from(inventoryCanonicalInput, "ascii"),
    );
    const catalogCanonicalInput = authority.canonicalDomain +
        `|schemaRevision=${authority.schemaRevision}` +
        `|catalogRevision=${authority.catalogRevision}` +
        `|profileSchemaRevision=${authority.profileSchemaRevision}` +
        `|profileRevision=${authority.profileRevision}` +
        `|nodeModuleOutputPolicySchemaRevision=` +
            authority.nodeModuleOutputPolicySchemaRevision +
        `|nodeModuleOutputPolicyRevision=${authority.nodeModuleOutputPolicyRevision}` +
        `|nodeModuleOutputPolicyFingerprint=` +
            authority.nodeModuleOutputPolicyFingerprint +
        `|packageMetadataSchemaRevision=${authority.packageMetadataSchemaRevision}` +
        `|packageMetadataPolicyRevision=${authority.packageMetadataPolicyRevision}` +
        `|packageMetadataPolicyFingerprint=${authority.packageMetadataPolicyFingerprint}` +
        `|packageMetadataInventoryFingerprint=${packageMetadataInventoryFingerprint}` +
        records.map((record) =>
            `|record=${record.packageMetadataPath},${record.packageBoundaryPath},` +
                `${record.packageMetadataSize},${record.packageMetadataSha256},` +
                record.typeState,
        ).join("");
    return Object.freeze({
        records: Object.freeze(records),
        inventoryCanonicalInput,
        packageMetadataInventoryFingerprint,
        catalogCanonicalInput,
        catalogFingerprint: sha256(Buffer.from(catalogCanonicalInput, "ascii")),
    });
};

const requireCanonicalContainingPath = (containingPath) => {
    if (
        typeof containingPath !== "string" ||
        !containingPath.startsWith("/") ||
        containingPath.length <= 1 ||
        containingPath.length > 4096 ||
        containingPath.includes("\\") ||
        Array.from(containingPath).some((character) => {
            const code = character.charCodeAt(0);
            return code < 0x20 || code > 0x7e;
        })
    ) {
        throw new Error("Node package-format lookup path is not bounded printable ASCII.");
    }
    const segments = containingPath.slice(1).split("/");
    if (
        segments.length > 64 ||
        segments.some((segment) =>
            segment.length === 0 || segment === "." || segment === "..",
        )
    ) {
        throw new Error("Node package-format lookup path is not canonical.");
    }
};

const nearestRecord = (catalog, containingPath) => {
    requireCanonicalContainingPath(containingPath);
    return catalog.records
        .filter((record) =>
            record.packageBoundaryPath === "/" ||
            containingPath.startsWith(`${record.packageBoundaryPath}/`),
        )
        .sort((left, right) =>
            right.packageBoundaryPath.length - left.packageBoundaryPath.length,
        )[0] || null;
};

module.exports = Object.freeze({
    authority,
    deriveCatalog,
    failureCodes,
    nearestRecord,
});
