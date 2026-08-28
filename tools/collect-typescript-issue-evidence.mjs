#!/usr/bin/env node

import { promises as fs } from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const MAX_DIAGNOSTICS_BYTES = 512 * 1024;
const MAX_LOGCAT_BYTES = 16 * 1024 * 1024;
const MAX_PERFORMANCE_LINE_CHARACTERS = 8192;
const repositoryRoot = path.resolve(
    path.dirname(fileURLToPath(import.meta.url)),
    "..",
);
const outputBase = path.join(
    repositoryRoot,
    "build",
    "reports",
    "issue-triage",
);
const performanceSchemas = Object.freeze({
    "typescript-compile-perf": {
        revision: "2",
        keys: new Set([
            "schema",
            "profile",
            "outcome",
            "error",
            "cache",
            "totalMs",
            "prepareMs",
            "cacheLookupMs",
            "bindPrewarmMs",
            "remoteRoundTripMs",
            "compilerMs",
            "compilerBeforeIngressMs",
            "compilerIngressMs",
            "compilerInterPhaseMs",
            "compilerParseMs",
            "compilerSemanticMs",
            "compilerEmitMs",
            "compilerAfterEmitMs",
            "outputMs",
            "inputBytes",
            "sourceFiles",
            "hostHeapStartKiB",
            "hostHeapEndKiB",
            "hostHeapDeltaKiB",
        ]),
    },
    "typescript-compiler-perf": {
        revision: "1",
        keys: new Set([
            "schema",
            "api",
            "profile",
            "sourceFiles",
            "inputBytes",
            "hostRoundTripMs",
            "compilerMs",
            "serviceMedianPssKiB",
            "rendererMedianPssKiB",
            "combinedMedianPssKiB",
            "activeMemoryHoldMs",
            "gate",
        ]),
    },
});
const compilerOutcomes = new Set([
    "emitted",
    "diagnostics",
    "cancelled",
    "timed_out",
    "failed",
]);
const cacheStatuses = new Set([
    "disabled",
    "not_checked",
    "unavailable",
    "miss",
    "hit",
]);
const compilerErrors = new Set([
    "none",
    "ERR_AUTOJS6_TYPESCRIPT_R0_NOT_AVAILABLE",
    "ERR_AUTOJS6_TYPESCRIPT_INVALID_REQUEST",
    "ERR_AUTOJS6_TYPESCRIPT_CONTRACT_MISMATCH",
    "ERR_AUTOJS6_TYPESCRIPT_COMPILER_BUSY",
    "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_CANCELLED",
    "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_DIAGNOSTICS",
    "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_TIMEOUT",
    "ERR_AUTOJS6_TYPESCRIPT_COMPILER_UNAVAILABLE",
    "ERR_AUTOJS6_TYPESCRIPT_OUTPUT_TRANSPORT",
    "ERR_AUTOJS6_TYPESCRIPT_ARCHIVE_FORMAT",
    "ERR_AUTOJS6_TYPESCRIPT_ARCHIVE_INTEGRITY",
    "ERR_AUTOJS6_TYPESCRIPT_ARCHIVE_PATH",
    "ERR_AUTOJS6_TYPESCRIPT_ARCHIVE_QUOTA",
]);

const fail = (message) => {
    throw new Error(message);
};
const parseArguments = (arguments_) => {
    const result = {
        diagnostics: null,
        logcat: null,
        outputDirectory: null,
    };
    for (let index = 0; index < arguments_.length; index += 1) {
        const argument = arguments_[index];
        if (argument === "--help") {
            process.stdout.write(
                "Usage: node tools/collect-typescript-issue-evidence.mjs " +
                    "[--diagnostics <diagnostics.json>] [--logcat <logcat.txt>] " +
                    "[--output-dir <build/reports/issue-triage/name>]\n",
            );
            process.exit(0);
        }
        const value = arguments_[index + 1];
        if (!value || value.startsWith("--")) {
            fail("Missing value for " + argument + ".");
        }
        if (argument === "--diagnostics") result.diagnostics = value;
        else if (argument === "--logcat") result.logcat = value;
        else if (argument === "--output-dir") result.outputDirectory = value;
        else fail("Unknown argument: " + argument);
        index += 1;
    }
    if (!result.diagnostics && !result.logcat) {
        fail("Provide --diagnostics, --logcat, or both.");
    }
    return result;
};
const resolveInput = (value) => path.resolve(process.cwd(), value);
const resolveOutput = (value) => {
    if (value) return path.resolve(repositoryRoot, value);
    const timestamp = new Date().toISOString().replaceAll(":", "-");
    return path.join(outputBase, timestamp);
};
const assertOutputLocation = (target) => {
    const relative = path.relative(outputBase, target);
    if (
        !relative ||
        relative.startsWith("..") ||
        path.isAbsolute(relative)
    ) {
        fail(
            "Output must be a child of the ignored build/reports/issue-triage directory.",
        );
    }
};
const readBoundedRegularFile = async (target, maximumBytes, label) => {
    const stat = await fs.lstat(target);
    if (!stat.isFile() || stat.isSymbolicLink()) {
        fail(label + " input must be a non-symlink regular file.");
    }
    if (stat.size > maximumBytes) {
        fail(label + " input exceeds " + maximumBytes + " bytes.");
    }
    return fs.readFile(target);
};
const prepareOutputDirectory = async (target) => {
    await fs.mkdir(outputBase, { recursive: true });
    try {
        const stat = await fs.lstat(target);
        if (!stat.isDirectory() || stat.isSymbolicLink()) {
            fail("Output exists and is not a non-symlink directory.");
        }
        const entries = await fs.readdir(target);
        if (entries.length !== 0) {
            fail("Output directory must be new or empty.");
        }
    } catch (error) {
        if (error?.code !== "ENOENT") throw error;
        await fs.mkdir(target, { recursive: false });
    }
};
const requireInteger = (value, owner) => {
    if (!Number.isInteger(value)) fail(owner + " must be an integer.");
    return value;
};
const requireString = (value, owner) => {
    if (typeof value !== "string") fail(owner + " must be a string.");
    return value;
};
const safeRange = (range, owner) => {
    if (range === null) return null;
    if (!range || typeof range !== "object" || Array.isArray(range)) {
        fail(owner + " range must be an object or null.");
    }
    return {
        start: requireInteger(range.start, owner + ".start"),
        length: requireInteger(range.length, owner + ".length"),
        line: requireInteger(range.line, owner + ".line"),
        column: requireInteger(range.column, owner + ".column"),
    };
};
const safeUriDomain = (uri, owner) => {
    if (uri === null) return null;
    const parsed = new URL(requireString(uri, owner + ".uri"));
    const segments = parsed.pathname.split("/").filter(Boolean);
    if (
        parsed.protocol !== "autojs:" ||
        parsed.hostname !== "source" ||
        segments.length < 2 ||
        !["sources", "declarations", "packages"].includes(segments[1])
    ) {
        fail(owner + " URI is not a canonical project-scoped source URI.");
    }
    return segments[1];
};
const safeMessageMetadata = (message, owner) => {
    requireString(message, owner + ".message");
    return { messageRedacted: true };
};
const safeRelated = (related, owner) => {
    if (!related || typeof related !== "object" || Array.isArray(related)) {
        fail(owner + " must be an object.");
    }
    return {
        sourceDomain: safeUriDomain(related.uri, owner),
        range: safeRange(related.range, owner),
        category: requireInteger(related.category, owner + ".category"),
        code: requireInteger(related.code, owner + ".code"),
        ...safeMessageMetadata(related.message, owner),
    };
};
const safeDiagnostic = (diagnostic, index) => {
    const owner = "diagnostics[" + index + "]";
    if (!diagnostic || typeof diagnostic !== "object" || Array.isArray(diagnostic)) {
        fail(owner + " must be an object.");
    }
    const related = diagnostic.relatedInformation;
    if (!Array.isArray(related)) fail(owner + ".relatedInformation must be an array.");
    const result = {
        phase: requireString(diagnostic.phase, owner + ".phase"),
        sourceDomain: safeUriDomain(diagnostic.uri, owner),
        range: safeRange(diagnostic.range, owner),
        category: requireInteger(diagnostic.category, owner + ".category"),
        code: requireInteger(diagnostic.code, owner + ".code"),
        ...safeMessageMetadata(diagnostic.message, owner),
        relatedInformation: related.map((item, relatedIndex) =>
            safeRelated(item, owner + ".relatedInformation[" + relatedIndex + "]"),
        ),
    };
    if (Object.hasOwn(diagnostic, "nodeResolution")) {
        const policyCode = diagnostic.nodeResolution?.policyCode;
        if (
            typeof policyCode !== "string" ||
            !/^[A-Z][A-Z0-9_]{2,127}$/u.test(policyCode)
        ) {
            fail(owner + ".nodeResolution.policyCode is not a stable policy code.");
        }
        result.nodeResolution = { policyCode };
    }
    return result;
};
const redactDiagnostics = (document) => {
    if (
        !document ||
        typeof document !== "object" ||
        Array.isArray(document) ||
        document.schema !== "autojs6.typescript.diagnostics" ||
        document.revision !== 1 ||
        !Array.isArray(document.diagnostics)
    ) {
        fail("Diagnostics input is not formal autojs6.typescript.diagnostics revision 1.");
    }
    const diagnostics = document.diagnostics.map(safeDiagnostic);
    const relatedCount = diagnostics.reduce(
        (count, diagnostic) => count + diagnostic.relatedInformation.length,
        0,
    );
    if (
        document.diagnosticCount !== diagnostics.length ||
        document.relatedInformationCount !== relatedCount
    ) {
        fail("Diagnostics input counts do not match its arrays.");
    }
    return {
        schema: "autojs6.typescript.issue-evidence.diagnostics",
        revision: 1,
        sourceSchema: document.schema,
        sourceRevision: document.revision,
        diagnosticsQuotaRevision: requireInteger(
            document.diagnosticsQuotaRevision,
            "diagnosticsQuotaRevision",
        ),
        targetProfile: requireString(document.targetProfile, "targetProfile"),
        profileSchemaRevision: requireInteger(
            document.profileSchemaRevision,
            "profileSchemaRevision",
        ),
        profileRevision: requireInteger(
            document.profileRevision,
            "profileRevision",
        ),
        configSchemaRevision: requireInteger(
            document.configSchemaRevision,
            "configSchemaRevision",
        ),
        configKind: requireString(document.configKind, "configKind"),
        positionEncoding: requireString(
            document.positionEncoding,
            "positionEncoding",
        ),
        lineBase: requireInteger(document.lineBase, "lineBase"),
        columnBase: requireInteger(document.columnBase, "columnBase"),
        requestIdentityRedacted: Object.hasOwn(document, "requestId"),
        projectIdentityRedacted: Object.hasOwn(document, "projectId"),
        uriPathsRedacted: true,
        messagesRedacted: true,
        diagnosticCount: diagnostics.length,
        relatedInformationCount: relatedCount,
        diagnostics,
    };
};
const isSafePerformanceValue = (marker, key, value) => {
    if (key === "schema") return value === performanceSchemas[marker].revision;
    if (key === "profile") return value === "rhino" || value === "node";
    if (key === "outcome") return compilerOutcomes.has(value);
    if (key === "error") return compilerErrors.has(value);
    if (key === "cache") return cacheStatuses.has(value);
    if (key === "gate") return value === "none";
    return /^(?:-?[0-9]+|na)$/u.test(value);
};
const sanitizePerformanceLine = (rawLine) => {
    let selected = null;
    for (const marker of Object.keys(performanceSchemas)) {
        const index = rawLine.indexOf(marker + " ");
        if (index >= 0 && (!selected || index < selected.index)) {
            selected = { marker, index };
        }
    }
    if (!selected) return { candidate: false, line: null };
    const candidate = rawLine.slice(selected.index).trim();
    if (candidate.length > MAX_PERFORMANCE_LINE_CHARACTERS) {
        return { candidate: true, line: null };
    }
    const tokens = candidate.split(/\s+/u);
    const marker = tokens.shift();
    const schema = performanceSchemas[marker];
    const seen = new Set();
    for (const token of tokens) {
        const separator = token.indexOf("=");
        if (separator <= 0 || separator === token.length - 1) {
            return { candidate: true, line: null };
        }
        const key = token.slice(0, separator);
        const value = token.slice(separator + 1);
        if (
            !schema.keys.has(key) ||
            seen.has(key) ||
            !isSafePerformanceValue(marker, key, value)
        ) {
            return { candidate: true, line: null };
        }
        seen.add(key);
    }
    if (
        seen.size !== schema.keys.size ||
        !seen.has("schema") ||
        !tokens.includes("schema=" + schema.revision)
    ) {
        return { candidate: true, line: null };
    }
    return { candidate: true, line: marker + " " + tokens.join(" ") };
};
const extractPerformance = (text) => {
    const lines = [];
    let candidates = 0;
    let omitted = 0;
    for (const rawLine of text.split(/\r?\n/u)) {
        const result = sanitizePerformanceLine(rawLine);
        if (!result.candidate) continue;
        candidates += 1;
        if (result.line) lines.push(result.line);
        else omitted += 1;
    }
    return {
        lines: [...new Set(lines)],
        candidates,
        omitted,
    };
};
const writeJson = (target, value) =>
    fs.writeFile(target, JSON.stringify(value, null, 2) + "\n", "utf8");

const options = parseArguments(process.argv.slice(2));
const outputDirectory = resolveOutput(options.outputDirectory);
assertOutputLocation(outputDirectory);
await prepareOutputDirectory(outputDirectory);

let redactedDiagnostics = null;
let performance = null;
if (options.diagnostics) {
    const bytes = await readBoundedRegularFile(
        resolveInput(options.diagnostics),
        MAX_DIAGNOSTICS_BYTES,
        "Diagnostics",
    );
    let document;
    try {
        document = JSON.parse(bytes.toString("utf8"));
    } catch (error) {
        fail("Diagnostics input is not valid UTF-8 JSON: " + error.message);
    }
    redactedDiagnostics = redactDiagnostics(document);
    await writeJson(
        path.join(outputDirectory, "diagnostics.redacted.json"),
        redactedDiagnostics,
    );
}
if (options.logcat) {
    const bytes = await readBoundedRegularFile(
        resolveInput(options.logcat),
        MAX_LOGCAT_BYTES,
        "Logcat",
    );
    performance = extractPerformance(bytes.toString("utf8"));
    await fs.writeFile(
        path.join(outputDirectory, "typescript-performance.log"),
        performance.lines.length === 0
            ? ""
            : performance.lines.join("\n") + "\n",
        "utf8",
    );
}

const manifest = {
    schema: "autojs6.typescript.issue-evidence",
    revision: 1,
    createdAt: new Date().toISOString(),
    networkUsed: false,
    sourceMutation: "none",
    outputPolicy: "ignored-build-reports-only",
    diagnostics: {
        provided: redactedDiagnostics !== null,
        diagnosticCount: redactedDiagnostics?.diagnosticCount ?? 0,
        relatedInformationCount:
            redactedDiagnostics?.relatedInformationCount ?? 0,
        messagesRedacted: redactedDiagnostics?.messagesRedacted ?? true,
        uriPathsRedacted: redactedDiagnostics?.uriPathsRedacted ?? true,
        requestAndProjectIdentityRedacted: true,
    },
    performance: {
        provided: performance !== null,
        safeLineCount: performance?.lines.length ?? 0,
        candidateLineCount: performance?.candidates ?? 0,
        unsafeOrUnknownLinesOmitted: performance?.omitted ?? 0,
        logcatPrefixesRemoved: true,
    },
};
await writeJson(path.join(outputDirectory, "manifest.json"), manifest);
await fs.writeFile(
    path.join(outputDirectory, "README.md"),
    [
        "# TypeScript issue evidence",
        "",
        "Review every generated file before sharing it.",
        "diagnostics.redacted.json removes request/project identity, URI paths, and messages.",
        "typescript-performance.log keeps only exact schema 2/schema 1 key-value records.",
        "The collector does not include source files, Source Maps, arbitrary logcat, or input paths.",
        "",
    ].join("\n"),
    "utf8",
);

process.stdout.write(
    "TypeScript issue evidence prepared: diagnostics=" +
        (redactedDiagnostics?.diagnosticCount ?? 0) +
        ", performanceLines=" +
        (performance?.lines.length ?? 0) +
        ", omittedPerformanceCandidates=" +
        (performance?.omitted ?? 0) +
        ".\nOutput: " +
        outputDirectory +
        "\n",
);
