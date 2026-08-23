"use strict";

const crypto = require("node:crypto");

const authority = Object.freeze({
    generatedUriAuthority: "generated",
    mappingPolicyRevision: 1,
    maximumPathDepth: 64,
    maximumProjectIdUtf16Chars: 256,
    maximumRelativePathChars: 1024,
    maximumUriAsciiBytes: 4096,
    moduleOutputPolicyFingerprint:
        "5c2294f0ee9d6d25bd2132b4ea24866d0a5f9caaa521a63bf81ffe1d897f6ce7",
    moduleOutputPolicyRevision: 1,
    nodeProfileRevision: 2,
    outputRoot: "/outputs",
    profileSchemaRevision: 1,
    projectNamespacePrefix: "p-",
    rhinoProfileRevision: 1,
    rhinoRuntimeCapabilitiesFingerprint:
        "21dd79c85ff5b6ab04e0c2314e4e07fede635829f7abd21d1ea13451d892e749",
    nodeRevisionTwoRuntimeCapabilitiesFingerprint:
        "a19e8da1a4ab5961aada8d49e1f70eef847b84a0a8f348b6f784f43dc26e2d3a",
    schema: "autojs6.typescript.generated-module-mapping",
    schemaRevision: 1,
    sourceRoot: "/sources",
    sourceUriAuthority: "source",
    uriScheme: "autojs",
});

const profileAuthorities = Object.freeze({
    "rhino:1": Object.freeze({
        configKind: "none",
        configSchemaRevision: 1,
        effectiveOptionsFingerprint:
            "76a173383972b1bd8d4ffa3fffe5d51f5adecf7b84e7b16f58554c14f3c08e0b",
        profileSchemaRevision: 1,
        runtimeCapabilitiesFingerprint:
            authority.rhinoRuntimeCapabilitiesFingerprint,
    }),
    "node:2": Object.freeze({
        configKind: "none",
        configSchemaRevision: 1,
        effectiveOptionsFingerprint:
            "2ef130a868f27eb30869ed24fc1f8999135aeeefe92bcd30bebf94b38224419f",
        profileSchemaRevision: 1,
        runtimeCapabilitiesFingerprint:
            authority.nodeRevisionTwoRuntimeCapabilitiesFingerprint,
    }),
});

const failureCodes = Object.freeze({
    claimMismatch: "GENERATED_MAPPING_CLAIM_MISMATCH",
    generatedPathCollision: "GENERATED_MAPPING_GENERATED_PATH_COLLISION",
    generatedUriCollision: "GENERATED_MAPPING_GENERATED_URI_COLLISION",
    invalidProfileAuthority: "GENERATED_MAPPING_INVALID_PROFILE_AUTHORITY",
    invalidProjectId: "GENERATED_MAPPING_INVALID_PROJECT_ID",
    invalidSourcePath: "GENERATED_MAPPING_INVALID_SOURCE_PATH",
    moduleFormatMismatch: "GENERATED_MAPPING_MODULE_FORMAT_MISMATCH",
    mixedProjectInventory: "GENERATED_MAPPING_MIXED_PROJECT_INVENTORY",
    outputPathLimitExceeded: "GENERATED_MAPPING_OUTPUT_PATH_LIMIT_EXCEEDED",
    originalSourceUriCollision: "GENERATED_MAPPING_ORIGINAL_SOURCE_URI_COLLISION",
    packageAuthorityMismatch: "GENERATED_MAPPING_PACKAGE_AUTHORITY_MISMATCH",
    sourceCollision: "GENERATED_MAPPING_SOURCE_COLLISION",
    projectNamespaceCollision: "GENERATED_MAPPING_PROJECT_NAMESPACE_COLLISION",
    unsupportedSourceKind: "GENERATED_MAPPING_UNSUPPORTED_SOURCE_KIND",
    uriLimitExceeded: "GENERATED_MAPPING_URI_LIMIT_EXCEEDED",
});

const reject = (policyCode, message) => {
    const error = new Error(message);
    error.policyCode = policyCode;
    throw error;
};
const sha256 = (bytes) =>
    crypto.createHash("sha256").update(bytes).digest("hex");
const asciiCaseFold = (value) =>
    Array.from(value, (character) => {
        const code = character.charCodeAt(0);
        return code >= 0x41 && code <= 0x5a
            ? String.fromCharCode(code + 0x20)
            : character;
    }).join("");
const isLowerSha256 = (value) =>
    typeof value === "string" && /^[0-9a-f]{64}$/.test(value);
const exactKeys = (value, keys) =>
    value !== null &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    [Object.prototype, null].includes(Object.getPrototypeOf(value)) &&
    JSON.stringify(Object.keys(value).sort()) === JSON.stringify(keys.slice().sort());

const strictUtf8 = (value, policyCode, label) => {
    if (typeof value !== "string") reject(policyCode, `${label} is not a string.`);
    for (let index = 0; index < value.length; index += 1) {
        const code = value.charCodeAt(index);
        if (code >= 0xd800 && code <= 0xdbff) {
            const next = value.charCodeAt(index + 1);
            if (next < 0xdc00 || next > 0xdfff) {
                reject(policyCode, `${label} contains an unpaired high surrogate.`);
            }
            index += 1;
        } else if (code >= 0xdc00 && code <= 0xdfff) {
            reject(policyCode, `${label} contains an unpaired low surrogate.`);
        }
    }
    return Buffer.from(value, "utf8");
};

const projectNamespace = (projectId) => {
    if (
        typeof projectId !== "string" ||
        projectId.length === 0 ||
        projectId.length > authority.maximumProjectIdUtf16Chars
    ) {
        reject(
            failureCodes.invalidProjectId,
            "Generated mapping project ID is empty or exceeds its UTF-16 limit.",
        );
    }
    return authority.projectNamespacePrefix +
        sha256(strictUtf8(projectId, failureCodes.invalidProjectId, "Project ID"));
};

const validateSourcePath = (sourcePath) => {
    const prefix = `${authority.sourceRoot}/`;
    if (
        typeof sourcePath !== "string" ||
        !sourcePath.startsWith(prefix) ||
        sourcePath.includes("\\") ||
        Array.from(sourcePath).some((character) => {
            const code = character.charCodeAt(0);
            return code < 0x20 || code > 0x7e;
        })
    ) {
        reject(
            failureCodes.invalidSourcePath,
            "Generated mapping source path is not canonical printable ASCII.",
        );
    }
    const relative = sourcePath.slice(prefix.length);
    const segments = relative.split("/");
    const transportRelative = `sources/${relative}`;
    if (
        relative.length === 0 ||
        transportRelative.length > authority.maximumRelativePathChars ||
        segments.length + 1 > authority.maximumPathDepth ||
        segments.some(
            (segment) =>
                segment.length === 0 || segment === "." || segment === "..",
        )
    ) {
        reject(
            failureCodes.invalidSourcePath,
            "Generated mapping source path has a non-canonical segment or limit.",
        );
    }
    return relative;
};

const sourceExtension = (sourcePath) => {
    if (
        sourcePath.endsWith(".d.ts") ||
        sourcePath.endsWith(".d.mts") ||
        sourcePath.endsWith(".d.cts")
    ) {
        reject(
            failureCodes.unsupportedSourceKind,
            "Declaration files cannot establish generated mappings.",
        );
    }
    if (sourcePath.endsWith(".mts")) return ".mts";
    if (sourcePath.endsWith(".cts")) return ".cts";
    if (sourcePath.endsWith(".ts")) return ".ts";
    reject(failureCodes.unsupportedSourceKind, "Unsupported source kind.");
};

const validatePackageAuthority = (packageAuthority) => {
    if (
        !exactKeys(packageAuthority, [
            "typeState",
            "packageMetadataInventoryFingerprint",
            "boundaryPath",
        ]) ||
        !["no-boundary", "missing", "commonjs", "module"].includes(
            packageAuthority.typeState,
        ) ||
        (packageAuthority.typeState === "no-boundary"
            ? packageAuthority.packageMetadataInventoryFingerprint !== null ||
              packageAuthority.boundaryPath !== null
            : !isLowerSha256(packageAuthority.packageMetadataInventoryFingerprint) ||
              packageAuthority.boundaryPath !== "/")
    ) {
        reject(
            failureCodes.packageAuthorityMismatch,
            "Generated package-format authority is malformed.",
        );
    }
    return packageAuthority;
};

const requireMatrixAuthority = (request, extension) => {
    const packageAuthority = request.packageFormatAuthority === null
        ? null
        : validatePackageAuthority(request.packageFormatAuthority);
    if (request.profile === "rhino") {
        if (
            request.profileRevision !== authority.rhinoProfileRevision ||
            extension !== ".ts"
        ) {
            reject(
                failureCodes.invalidProfileAuthority,
                "Rhino mapping requires revision 1 and .ts.",
            );
        }
        if (packageAuthority !== null) {
            reject(failureCodes.packageAuthorityMismatch, "Rhino package authority.");
        }
        if (request.impliedModuleFormat !== "commonjs") {
            reject(failureCodes.moduleFormatMismatch, "Rhino format mismatch.");
        }
        return ".js";
    }
    if (request.profile !== "node" || request.profileRevision !== 2) {
        reject(
            failureCodes.invalidProfileAuthority,
            "Node mapping requires exact revision 2.",
        );
    }
    if (extension === ".mts") {
        if (packageAuthority !== null) {
            reject(failureCodes.packageAuthorityMismatch, "Node .mts package authority.");
        }
        if (request.impliedModuleFormat !== "esm") {
            reject(failureCodes.moduleFormatMismatch, "Node .mts format mismatch.");
        }
        return ".mjs";
    }
    if (extension === ".cts") {
        if (packageAuthority !== null) {
            reject(failureCodes.packageAuthorityMismatch, "Node .cts package authority.");
        }
        if (request.impliedModuleFormat !== "commonjs") {
            reject(failureCodes.moduleFormatMismatch, "Node .cts format mismatch.");
        }
        return ".cjs";
    }
    if (packageAuthority === null) {
        reject(failureCodes.packageAuthorityMismatch, "Node .ts package authority missing.");
    }
    const expectedFormat = packageAuthority.typeState === "module"
        ? "esm"
        : "commonjs";
    if (request.impliedModuleFormat !== expectedFormat) {
        reject(failureCodes.moduleFormatMismatch, "Node .ts format mismatch.");
    }
    return ".js";
};

const percentEncodeSegment = (segment) => {
    const bytes = strictUtf8(
        segment,
        failureCodes.invalidSourcePath,
        "Generated URI segment",
    );
    let result = "";
    for (const value of bytes) {
        if (
            (value >= 0x61 && value <= 0x7a) ||
            (value >= 0x41 && value <= 0x5a) ||
            (value >= 0x30 && value <= 0x39) ||
            value === 0x2d ||
            value === 0x2e ||
            value === 0x5f ||
            value === 0x7e
        ) {
            result += String.fromCharCode(value);
        } else {
            result += `%${value.toString(16).toUpperCase().padStart(2, "0")}`;
        }
    }
    return result;
};

const buildUri = (uriAuthority, namespace, rootedRelativePath) => {
    const path = rootedRelativePath.split("/").map(percentEncodeSegment).join("/");
    const uri = `${authority.uriScheme}://${uriAuthority}/${namespace}/${path}`;
    if (uri.length > authority.maximumUriAsciiBytes) {
        reject(failureCodes.uriLimitExceeded, "Generated mapping URI limit exceeded.");
    }
    return uri;
};

const validateGeneratedPath = (generatedPath) => {
    const transportRelative = generatedPath.slice(1);
    if (
        transportRelative.length > authority.maximumRelativePathChars ||
        transportRelative.split("/").length > authority.maximumPathDepth
    ) {
        reject(
            failureCodes.outputPathLimitExceeded,
            "Generated output or source-map path exceeds an archive path limit.",
        );
    }
};

const map = (request) => {
    if (
        !exactKeys(request, [
            "projectId",
            "profile",
            "profileRevision",
            "sourcePath",
            "impliedModuleFormat",
            "packageFormatAuthority",
        ])
    ) {
        reject(failureCodes.invalidProfileAuthority, "Mapping request shape drifted.");
    }
    const namespace = projectNamespace(request.projectId);
    const relativeSource = validateSourcePath(request.sourcePath);
    const extension = sourceExtension(request.sourcePath);
    const outputExtension = requireMatrixAuthority(request, extension);
    const profileAuthority = profileAuthorities[
        `${request.profile}:${request.profileRevision}`
    ];
    if (profileAuthority === undefined) {
        reject(failureCodes.invalidProfileAuthority, "Profile authority is not exact.");
    }
    const generatedRelative = relativeSource.slice(0, -extension.length) +
        outputExtension;
    const generatedPath = `${authority.outputRoot}/${generatedRelative}`;
    const sourceMapPath = `${generatedPath}.map`;
    validateGeneratedPath(generatedPath);
    validateGeneratedPath(sourceMapPath);
    const originalSourceUri = buildUri(
        authority.sourceUriAuthority,
        namespace,
        `sources/${relativeSource}`,
    );
    const generatedUri = buildUri(
        authority.generatedUriAuthority,
        namespace,
        `outputs/${generatedRelative}`,
    );
    const packageState = request.packageFormatAuthority?.typeState ?? "none";
    const packageFingerprint =
        request.packageFormatAuthority?.packageMetadataInventoryFingerprint ?? "none";
    const packageBoundary = request.packageFormatAuthority?.boundaryPath ?? "none";
    const canonicalEvidence =
        `schema=${authority.schema}` +
        `|schemaRevision=${authority.schemaRevision}` +
        `|mappingPolicyRevision=${authority.mappingPolicyRevision}` +
        `|moduleOutputPolicyRevision=${authority.moduleOutputPolicyRevision}` +
        `|moduleOutputPolicyFingerprint=${authority.moduleOutputPolicyFingerprint}` +
        `|profile=${percentEncodeSegment(request.profile)}` +
        `|profileSchemaRevision=${profileAuthority.profileSchemaRevision}` +
        `|profileRevision=${request.profileRevision}` +
        `|configSchemaRevision=${profileAuthority.configSchemaRevision}` +
        `|configKind=${percentEncodeSegment(profileAuthority.configKind)}` +
        `|effectiveOptionsFingerprint=${profileAuthority.effectiveOptionsFingerprint}` +
        `|runtimeCapabilitiesFingerprint=${profileAuthority.runtimeCapabilitiesFingerprint}` +
        `|projectNamespace=${percentEncodeSegment(namespace)}` +
        `|sourcePath=${percentEncodeSegment(request.sourcePath)}` +
        `|originalSourceUri=${percentEncodeSegment(originalSourceUri)}` +
        `|moduleFormat=${percentEncodeSegment(request.impliedModuleFormat)}` +
        `|packageTypeState=${percentEncodeSegment(packageState)}` +
        `|packageMetadataInventoryFingerprint=${percentEncodeSegment(packageFingerprint)}` +
        `|packageBoundary=${percentEncodeSegment(packageBoundary)}` +
        `|outputExtension=${percentEncodeSegment(outputExtension)}` +
        `|generatedPath=${percentEncodeSegment(generatedPath)}` +
        `|sourceMapPath=${percentEncodeSegment(sourceMapPath)}` +
        `|generatedUri=${percentEncodeSegment(generatedUri)}`;
    return Object.freeze({
        sourcePath: request.sourcePath,
        projectNamespace: namespace,
        originalSourceUri,
        moduleFormat: request.impliedModuleFormat,
        outputExtension,
        generatedPath,
        sourceMapPath,
        generatedUri,
        canonicalEvidence,
        fingerprint: sha256(Buffer.from(canonicalEvidence, "ascii")),
    });
};

const mapInventory = (requests) => {
    const exactSources = new Set();
    const foldedSources = new Set();
    const exactPaths = new Set();
    const foldedPaths = new Set();
    const exactUris = new Set();
    const foldedUris = new Set();
    const exactOriginalUris = new Set();
    const foldedOriginalUris = new Set();
    const namespaceOwners = new Map();
    let inventoryProjectId;
    const mappings = requests.map((request) => {
        const mapping = map(request);
        const namespaceOwner = namespaceOwners.get(mapping.projectNamespace);
        if (namespaceOwner !== undefined && namespaceOwner !== request.projectId) {
            reject(failureCodes.projectNamespaceCollision, "Project namespace collision.");
        }
        namespaceOwners.set(mapping.projectNamespace, request.projectId);
        if (inventoryProjectId === undefined) {
            inventoryProjectId = request.projectId;
        } else if (inventoryProjectId !== request.projectId) {
            reject(
                failureCodes.mixedProjectInventory,
                "A generated mapping inventory cannot combine different projects.",
            );
        }
        if (
            exactSources.has(mapping.sourcePath) ||
            foldedSources.has(asciiCaseFold(mapping.sourcePath))
        ) {
            reject(failureCodes.sourceCollision, "Source collision.");
        }
        exactSources.add(mapping.sourcePath);
        foldedSources.add(asciiCaseFold(mapping.sourcePath));
        if (
            exactOriginalUris.has(mapping.originalSourceUri) ||
            foldedOriginalUris.has(asciiCaseFold(mapping.originalSourceUri))
        ) {
            reject(failureCodes.originalSourceUriCollision, "Original URI collision.");
        }
        exactOriginalUris.add(mapping.originalSourceUri);
        foldedOriginalUris.add(asciiCaseFold(mapping.originalSourceUri));
        if (
            exactPaths.has(mapping.generatedPath) ||
            foldedPaths.has(asciiCaseFold(mapping.generatedPath))
        ) {
            reject(failureCodes.generatedPathCollision, "Generated path collision.");
        }
        exactPaths.add(mapping.generatedPath);
        foldedPaths.add(asciiCaseFold(mapping.generatedPath));
        if (
            exactUris.has(mapping.generatedUri) ||
            foldedUris.has(asciiCaseFold(mapping.generatedUri))
        ) {
            reject(failureCodes.generatedUriCollision, "Generated URI collision.");
        }
        exactUris.add(mapping.generatedUri);
        foldedUris.add(asciiCaseFold(mapping.generatedUri));
        return mapping;
    });
    return Object.freeze(mappings.sort((left, right) =>
        left.sourcePath < right.sourcePath
            ? -1
            : left.sourcePath > right.sourcePath
              ? 1
              : 0,
    ));
};

const mappingKeys = Object.freeze([
    "sourcePath",
    "projectNamespace",
    "originalSourceUri",
    "moduleFormat",
    "outputExtension",
    "generatedPath",
    "sourceMapPath",
    "generatedUri",
    "canonicalEvidence",
    "fingerprint",
]);

const requireExact = (request, claimed) => {
    const expected = map(request);
    if (
        !exactKeys(claimed, mappingKeys) ||
        mappingKeys.some((key) => claimed[key] !== expected[key])
    ) {
        reject(failureCodes.claimMismatch, "Generated mapping claim drifted.");
    }
    return expected;
};

module.exports = Object.freeze({
    authority,
    failureCodes,
    map,
    mapInventory,
    requireExact,
});
