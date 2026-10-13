"use strict";

// TypeScript parser contract v1: one source file in, its syntax tree (schema revision 1) out.
// Only ts.createSourceFile runs here; nothing is bound, type-checked, emitted or executed, and the
// Worker keeps no state between requests.
importScripts("typescript.js");

const SCHEMA_REVISION = 1;
const ERROR_INVALID_REQUEST = "ERR_AUTOJS6_TYPESCRIPT_PARSER_INVALID_REQUEST";
const ERROR_TREE_LIMIT = "ERR_AUTOJS6_TYPESCRIPT_PARSER_TREE_LIMIT";
const ERROR_UNAVAILABLE = "ERR_AUTOJS6_TYPESCRIPT_PARSER_UNAVAILABLE";

// ts.SyntaxKind maps a value to the last member declared with it ("FirstAssignment" rather than
// "EqualsToken"); the first declared member is the node kind's own name.
const kindNames = (() => {
    const names = [];
    for (const name of Object.keys(ts.SyntaxKind)) {
        const value = ts.SyntaxKind[name];
        if (typeof value === "number" && names[value] === undefined) {
            names[value] = name;
        }
    }
    return names;
})();

// Node properties that are not children: links to other nodes, binder and emitter state, JSDoc.
const NON_CHILD_PROPERTIES = new Set([
    "parent",
    "original",
    "emitNode",
    "symbol",
    "localSymbol",
    "locals",
    "nextContainer",
    "flowNode",
    "endFlowNode",
    "returnFlowNode",
    "jsDoc",
    "jsDocCache",
    "externalModuleIndicator",
    "commonJsModuleIndicator",
    "localJsxNamespace",
    "localJsxFactory",
    "localJsxFragmentNamespace",
    "localJsxFragmentFactory",
]);

const DIAGNOSTIC_CATEGORIES = ["warning", "error", "suggestion", "message"];

class ParseLimitError extends Error {}

const isNode = (value) =>
    value !== null &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    typeof value.kind === "number" &&
    typeof value.pos === "number" &&
    typeof value.end === "number";

// A NodeArray carries its own text range; plain arrays on a node (references, imports) are not children.
const isNodeArray = (value) =>
    Array.isArray(value) &&
    typeof value.pos === "number" &&
    typeof value.end === "number" &&
    value.every(isNode);

const scriptKindOf = (fileName) => {
    const lower = fileName.toLowerCase();
    if (lower.endsWith(".tsx")) return ts.ScriptKind.TSX;
    if (lower.endsWith(".jsx")) return ts.ScriptKind.JSX;
    if (lower.endsWith(".js")) return ts.ScriptKind.JS;
    if (lower.endsWith(".ts") || lower.endsWith(".mts") || lower.endsWith(".cts")) return ts.ScriptKind.TS;
    return null;
};

const declarationKindOf = (flags) => {
    if ((flags & ts.NodeFlags.AwaitUsing) === ts.NodeFlags.AwaitUsing) return "await using";
    if (flags & ts.NodeFlags.Using) return "using";
    if (flags & ts.NodeFlags.Const) return "const";
    if (flags & ts.NodeFlags.Let) return "let";
    return "var";
};

const startOf = (node, sourceFile) => {
    if (node.kind === ts.SyntaxKind.SourceFile) return 0;
    // JsxText has no leading trivia: its white space is its text.
    if (node.kind === ts.SyntaxKind.JsxText) return node.pos;
    try {
        return node.getStart(sourceFile, false);
    } catch (_) {
        return node.pos;
    }
};

const serialize = (sourceFile, maxNodes) => {
    let count = 0;
    const seen = new WeakSet();
    const visit = (node) => {
        count += 1;
        if (count > maxNodes) {
            throw new ParseLimitError(`The tree has more than ${maxNodes} nodes.`);
        }
        seen.add(node);
        const out = {
            kind: kindNames[node.kind] || String(node.kind),
            start: startOf(node, sourceFile),
            end: node.end,
        };
        if (node.kind === ts.SyntaxKind.Identifier || node.kind === ts.SyntaxKind.PrivateIdentifier) {
            out.text = ts.idText(node);
        } else if (node.kind !== ts.SyntaxKind.SourceFile && typeof node.text === "string") {
            out.text = node.text;
        }
        if (node.kind === ts.SyntaxKind.JsxText) {
            out.triviaOnly = node.containsOnlyTriviaWhiteSpaces === true;
        }
        if (node.kind === ts.SyntaxKind.VariableDeclarationList) {
            out.declarationKind = declarationKindOf(node.flags);
        }
        if (
            (node.kind === ts.SyntaxKind.PrefixUnaryExpression || node.kind === ts.SyntaxKind.PostfixUnaryExpression) &&
            typeof node.operator === "number"
        ) {
            out.operator = kindNames[node.operator];
        }
        for (const key of Object.keys(node)) {
            if (NON_CHILD_PROPERTIES.has(key) || key in out) continue;
            const value = node[key];
            if (isNode(value)) {
                if (!seen.has(value)) out[key] = visit(value);
            } else if (isNodeArray(value)) {
                if (!value.some((item) => seen.has(item))) out[key] = value.map(visit);
            }
        }
        return out;
    };
    const root = visit(sourceFile);
    return { root, nodeCount: count };
};

const diagnosticsOf = (sourceFile, maxDiagnostics) => {
    const diagnostics = sourceFile.parseDiagnostics || [];
    if (diagnostics.length > maxDiagnostics) {
        throw new ParseLimitError(`The source has more than ${maxDiagnostics} syntax errors.`);
    }
    return diagnostics.map((diagnostic) => ({
        start: diagnostic.start === undefined ? 0 : diagnostic.start,
        length: diagnostic.length === undefined ? 0 : diagnostic.length,
        code: diagnostic.code,
        category: DIAGNOSTIC_CATEGORIES[diagnostic.category] || "error",
        message: ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n").slice(0, 4096),
    }));
};

const parse = (message) => {
    const startedAt = Date.now();
    const limits = message.limits;
    const scriptKind = typeof message.fileName === "string" ? scriptKindOf(message.fileName) : null;
    if (
        scriptKind === null ||
        typeof message.sourceText !== "string" ||
        limits === null ||
        typeof limits !== "object" ||
        !Number.isInteger(limits.maxNodes) ||
        !Number.isInteger(limits.maxDiagnostics) ||
        !Number.isInteger(limits.maxTreeChars)
    ) {
        return { kind: "parseError", requestId: message.requestId, code: ERROR_INVALID_REQUEST, message: "Malformed parse request." };
    }
    try {
        const sourceFile = ts.createSourceFile(
            message.fileName,
            message.sourceText,
            { languageVersion: ts.ScriptTarget.Latest, jsDocParsingMode: ts.JSDocParsingMode.ParseNone },
            false,
            scriptKind,
        );
        const { root, nodeCount } = serialize(sourceFile, limits.maxNodes);
        const diagnostics = diagnosticsOf(sourceFile, limits.maxDiagnostics);
        const tree = JSON.stringify({
            schemaRevision: SCHEMA_REVISION,
            compilerVersion: ts.version,
            fileName: message.fileName,
            languageVariant: sourceFile.languageVariant === ts.LanguageVariant.JSX ? "jsx" : "standard",
            nodeCount,
            diagnostics,
            root,
        });
        if (tree.length > limits.maxTreeChars) {
            throw new ParseLimitError(`The tree has more than ${limits.maxTreeChars} characters.`);
        }
        return {
            kind: "parseResult",
            requestId: message.requestId,
            tree,
            nodeCount,
            diagnosticCount: diagnostics.length,
            elapsedMs: Date.now() - startedAt,
        };
    } catch (error) {
        if (error instanceof ParseLimitError) {
            return { kind: "parseError", requestId: message.requestId, code: ERROR_TREE_LIMIT, message: error.message };
        }
        if (error instanceof RangeError) {
            return { kind: "parseError", requestId: message.requestId, code: ERROR_TREE_LIMIT, message: "The source is nested too deeply." };
        }
        const text = error instanceof Error ? error.message : String(error);
        return { kind: "parseError", requestId: message.requestId, code: ERROR_UNAVAILABLE, message: (text || "Parse failed.").slice(0, 4096) };
    }
};

self.onmessage = (event) => {
    const message = event.data;
    if (message === null || typeof message !== "object" || message.kind !== "parse" || typeof message.requestId !== "string") {
        return;
    }
    self.postMessage(parse(message));
};

self.postMessage({ kind: "ready", tsVersion: ts.version });
