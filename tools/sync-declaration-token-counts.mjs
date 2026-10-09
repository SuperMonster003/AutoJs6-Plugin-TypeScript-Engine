#!/usr/bin/env node
"use strict";

// Startup-latency L2-7: locks the token count of every plugin-owned declaration record so the
// compiler Worker can admit its preloaded declaration layers without re-scanning about 4.7 MB of
// text on every cold process. The count uses exactly the Worker's scanner configuration
// (`countSourceTokens` in compiler-worker.js: ts.createScanner(ES2018, skipTrivia, Standard),
// counting every token before EndOfFileToken), and the committed lock is what
// GenerateR2DeclarationWorkerBundles embeds as `tokenCount` into each bundle record.
//
// Usage:
//   node tools/sync-declaration-token-counts.mjs --typescript-dir <generated typescript/<ver>>
//       --declarations-dir <generated r2-declarations/v2> --worker <compiler-worker.js>
//       --lock <r2-declaration-token-counts.lock.json> [--check]
//
// Without --check the lock is rewritten. With --check the lock must already be byte-identical
// to the regenerated content (the `check` gate), and in both modes every count must respect the
// Worker's per-file and per-profile token budgets.

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";

const LOCK_SCHEMA_VERSION = 1;
const SCANNER = Object.freeze({
    languageVariant: "Standard",
    scriptTarget: "ES2018",
    skipTrivia: true,
});

const argv = process.argv.slice(2);
const options = { check: false };
for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === "--check") {
        options.check = true;
        continue;
    }
    const value = argv[index + 1];
    if (value === undefined) {
        throw new Error(`Missing value for ${argument}.`);
    }
    switch (argument) {
        case "--typescript-dir":
            options.typescriptDir = path.resolve(value);
            break;
        case "--declarations-dir":
            options.declarationsDir = path.resolve(value);
            break;
        case "--worker":
            options.worker = path.resolve(value);
            break;
        case "--lock":
            options.lock = path.resolve(value);
            break;
        default:
            throw new Error(`Unknown argument ${argument}.`);
    }
    index += 1;
}
for (const key of ["typescriptDir", "declarationsDir", "worker", "lock"]) {
    if (!options[key]) {
        throw new Error(`--${key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)} is required.`);
    }
}

const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};
const sha256Hex = (bytes) => crypto.createHash("sha256").update(bytes).digest("hex");
const readJson = (file) => JSON.parse(fs.readFileSync(file, "utf8"));
const compareCodeUnits = (left, right) => (left < right ? -1 : left > right ? 1 : 0);

const workerSource = fs.readFileSync(options.worker, "utf8");
const workerConstant = (name) => {
    const match = workerSource.match(new RegExp(`const ${name} = ([0-9_]+);`));
    assert(match, `compiler-worker.js must declare ${name}.`);
    return Number.parseInt(match[1].replace(/_/g, ""), 10);
};
const maximumTokensPerFile = workerConstant("maximumTokensPerFile");
const maximumProjectTokens = workerConstant("maximumProjectTokens");
assert(
    /ts\.createScanner\(\s*ts\.ScriptTarget\.ES2018,\s*true,\s*ts\.LanguageVariant\.Standard,\s*text,\s*\)/.test(
        workerSource,
    ),
    "compiler-worker.js countSourceTokens scanner configuration drifted from this tool.",
);

const typescriptManifest = readJson(path.join(options.typescriptDir, "distribution.json"));
assert(typescriptManifest.schemaVersion === 1, "TypeScript distribution manifest schema must be 1.");
const typescriptVersion = typescriptManifest.package.version;
const ts = createRequire(import.meta.url)(path.join(options.typescriptDir, "lib", "typescript.js"));
assert(ts.version === typescriptVersion, "typescript.js version does not match its manifest.");

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
    }
    return tokenCount;
};

const readRecord = (root, relativePath, vfsPath, expectedSize, expectedSha256) => {
    const file = path.join(root, relativePath);
    const bytes = fs.readFileSync(file);
    assert(bytes.length === expectedSize, `Declaration size drifted: ${relativePath}`);
    const sha256 = sha256Hex(bytes);
    assert(sha256 === expectedSha256, `Declaration digest drifted: ${relativePath}`);
    const text = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
    return { path: vfsPath, sha256, tokenCount: countSourceTokens(text) };
};

const layers = new Map();
layers.set(
    "common",
    typescriptManifest.files
        .filter(
            (file) =>
                file.path.startsWith("lib/lib") &&
                file.path.endsWith(".d.ts") &&
                !file.path.slice("lib/".length).includes("/"),
        )
        .map((file) =>
            readRecord(
                options.typescriptDir,
                file.path,
                `/declarations/${file.path.slice("lib/".length)}`,
                file.size,
                file.sha256,
            ),
        ),
);
const declarationManifest = readJson(path.join(options.declarationsDir, "distribution.json"));
assert(declarationManifest.schemaVersion === 2, "Declaration distribution manifest schema must be 2.");
const declarationFiles = new Map(declarationManifest.files.map((file) => [file.path, file]));
for (const layer of ["rhino", "node"]) {
    layers.set(
        layer,
        declarationManifest.layers[layer].files.map((relativePath) => {
            const descriptor = declarationFiles.get(relativePath);
            assert(descriptor, `Declaration layer ${layer} references an unlisted file ${relativePath}`);
            assert(
                relativePath.startsWith(`layers/${layer}/`) && relativePath.endsWith(".d.ts"),
                `Declaration layer ${layer} file is outside its layer: ${relativePath}`,
            );
            return readRecord(
                options.declarationsDir,
                relativePath,
                `/declarations/${relativePath.slice("layers/".length)}`,
                descriptor.size,
                descriptor.sha256,
            );
        }),
    );
}

const files = {};
const totals = {};
for (const [layer, records] of layers) {
    let total = 0;
    for (const record of records) {
        assert(!(record.path in files), `Duplicate declaration path ${record.path}`);
        assert(
            record.tokenCount <= maximumTokensPerFile,
            `${record.path} has ${record.tokenCount} tokens, above maximumTokensPerFile ${maximumTokensPerFile}.`,
        );
        files[record.path] = { sha256: record.sha256, tokenCount: record.tokenCount };
        total += record.tokenCount;
    }
    totals[layer] = total;
}
for (const profile of ["rhino", "node"]) {
    const total = totals.common + totals[profile];
    assert(
        total < maximumProjectTokens,
        `Declaration profile ${profile} alone uses ${total} tokens, not below maximumProjectTokens ${maximumProjectTokens}.`,
    );
}

const lock = {
    files: Object.fromEntries(
        Object.keys(files)
            .sort(compareCodeUnits)
            .map((key) => [key, files[key]]),
    ),
    scanner: { ...SCANNER, typescript: typescriptVersion },
    schemaVersion: LOCK_SCHEMA_VERSION,
};
const rendered = `${JSON.stringify(lock, null, 2)}\n`;
const summary =
    `declaration token counts: ${Object.keys(files).length} files, ` +
    `common ${totals.common}, rhino ${totals.rhino}, node ${totals.node} tokens ` +
    `(TypeScript ${typescriptVersion}, per-file limit ${maximumTokensPerFile}, ` +
    `project limit ${maximumProjectTokens})`;

if (options.check) {
    const existing = fs.existsSync(options.lock) ? fs.readFileSync(options.lock, "utf8") : null;
    assert(
        existing === rendered,
        `${path.basename(options.lock)} is stale; run tools/sync-declaration-token-counts.mjs ` +
            "without --check and commit the result.",
    );
    process.stdout.write(`${summary}: lock verified\n`);
} else {
    fs.writeFileSync(options.lock, rendered, "utf8");
    process.stdout.write(`${summary}: lock written to ${options.lock}\n`);
}
