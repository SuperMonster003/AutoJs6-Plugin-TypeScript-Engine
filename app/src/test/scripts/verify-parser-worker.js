"use strict";

// TypeScript parser contract v1 desktop gate: runs the packaged parser Worker against the locked
// TypeScript distribution and the parser page's relay against a fake Worker.

const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const [workerArgument, pageArgument, compilerArgument, expectedVersion] = process.argv.slice(2);
if (!workerArgument || !pageArgument || !compilerArgument || !expectedVersion) {
    throw new Error("Usage: node verify-parser-worker.js <parser-worker.js> <parser-host.js> <typescript.js> <version>");
}
const assert = (condition, message) => {
    if (!condition) throw new Error(message);
};

// --- Worker -----------------------------------------------------------------------------------

// Same realm as TypeScript (as in the browser Worker), so its RangeError is the Worker's RangeError.
const workerPath = path.resolve(workerArgument);
const workerContext = { posted: [] };
global.ts = require(path.resolve(compilerArgument));
global.importScripts = (specifier) => assert(specifier === "typescript.js", `Worker imported ${specifier}.`);
global.self = { postMessage: (message) => workerContext.posted.push(message) };
workerContext.self = global.self;
vm.runInThisContext(fs.readFileSync(workerPath, "utf8"), { filename: workerPath });
assert(
    workerContext.posted.length === 1 &&
        workerContext.posted[0].kind === "ready" &&
        workerContext.posted[0].tsVersion === expectedVersion,
    "Parser Worker bootstrap drifted.",
);

const limits = { maxNodes: 250000, maxDiagnostics: 1000, maxTreeChars: 32 * 1024 * 1024 };
const parse = (fileName, sourceText, requestLimits = limits) => {
    workerContext.posted.length = 0;
    workerContext.self.onmessage({ data: { kind: "parse", requestId: "r", fileName, sourceText, limits: requestLimits } });
    assert(workerContext.posted.length === 1, "A parse must post exactly one message.");
    return workerContext.posted[0];
};

const counter = [
    "let count = compose.state(0);",
    "var offset = -2;",
    "function Counter(): any {",
    "    return <compose.Column padding={16}>",
    "        <compose.Text text={'Clicked ' + count.value} />",
    "        <compose.Button onClick={() => { count.value++; }}>Add 😀</compose.Button>",
    "    </compose.Column>;",
    "}",
    "compose.mount(Counter);",
    "",
].join("\n");
const result = parse("counter.tsx", counter);
assert(result.kind === "parseResult" && result.requestId === "r", `Parse failed: ${JSON.stringify(result).slice(0, 300)}`);
const tree = JSON.parse(result.tree);
assert(tree.schemaRevision === 1 && tree.compilerVersion === expectedVersion, "Tree header drifted.");
assert(tree.fileName === "counter.tsx" && tree.languageVariant === "jsx", "Tree language drifted.");
assert(tree.diagnostics.length === 0 && result.diagnosticCount === 0, "A valid source produced diagnostics.");

let counted = 0;
const kinds = new Map();
const walk = (node, visit) => {
    counted += 1;
    visit(node);
    for (const [key, value] of Object.entries(node)) {
        if (Array.isArray(value)) value.forEach((child) => walk(child, visit));
        else if (value !== null && typeof value === "object" && key !== "root") walk(value, visit);
    }
};
walk(tree.root, (node) => {
    assert(typeof node.kind === "string" && Number.isInteger(node.start) && Number.isInteger(node.end), "Node shape drifted.");
    assert(node.start <= node.end, `${node.kind} starts after its end.`);
    assert(!node.kind.startsWith("First") && !node.kind.startsWith("Last"), `Marker name ${node.kind} leaked.`);
    kinds.set(node.kind, (kinds.get(node.kind) || 0) + 1);
    if (node.kind === "Identifier") {
        assert(counter.slice(node.start, node.end) === node.text, `Identifier ${node.text} is misplaced.`);
    }
    if (node.kind === "JsxText") {
        assert(counter.slice(node.start, node.end) === node.text, "JsxText must start at its own position.");
    }
});
assert(counted === tree.nodeCount && counted === result.nodeCount, "nodeCount does not match the tree.");
for (const kind of ["JsxElement", "JsxSelfClosingElement", "JsxAttribute", "JsxExpression", "JsxText", "PropertyAccessExpression", "ArrowFunction"]) {
    assert(kinds.has(kind), `The tree has no ${kind}.`);
}

const statements = tree.root.statements;
assert(statements[0].declarationList.declarationKind === "let", "let declaration kind drifted.");
assert(statements[1].declarationList.declarationKind === "var", "var declaration kind drifted.");
const minus = statements[1].declarationList.declarations[0].initializer;
assert(minus.kind === "PrefixUnaryExpression" && minus.operator === "MinusToken" && minus.operand.text === "2", "Prefix operator drifted.");
const column = statements[2].body.statements[0].expression;
assert(column.kind === "JsxElement", "The returned element drifted.");
const tag = column.openingElement.tagName;
assert(tag.kind === "PropertyAccessExpression" && tag.expression.text === "compose" && tag.name.text === "Column", "Tag name drifted.");
const padding = column.openingElement.attributes.properties[0];
assert(padding.name.text === "padding" && padding.initializer.expression.text === "16", "Attribute drifted.");
const button = column.children.find((child) => child.kind === "JsxElement");
const label = button.children.find((child) => child.kind === "JsxText");
assert(label.text === "Add 😀" && label.triviaOnly === false, "JsxText with a surrogate pair drifted.");
assert(column.children.some((child) => child.kind === "JsxText" && child.triviaOnly === true), "White-space JsxText lost triviaOnly.");

const broken = parse("broken.tsx", "const a = <compose.Text text={1} ;\n");
const brokenTree = JSON.parse(broken.tree);
assert(broken.kind === "parseResult" && brokenTree.diagnostics.length > 0, "Syntax errors must be diagnostics, not failures.");
assert(brokenTree.diagnostics.every((item) => item.category === "error" && typeof item.message === "string"), "Diagnostic shape drifted.");

const typed = parse("typed.ts", "const n: number = 1 as const satisfies number;\n");
assert(typed.kind === "parseResult" && JSON.parse(typed.tree).languageVariant === "standard", ".ts must parse as standard TypeScript.");

assert(parse("x.vue", "").code === "ERR_AUTOJS6_TYPESCRIPT_PARSER_INVALID_REQUEST", "Unsupported extensions must be rejected.");
assert(parse("x.tsx", counter, Object.assign({}, limits, { maxNodes: 10 })).code === "ERR_AUTOJS6_TYPESCRIPT_PARSER_TREE_LIMIT", "maxNodes is not enforced.");
assert(parse("x.tsx", "let a = <;<;<;\n".repeat(20), Object.assign({}, limits, { maxDiagnostics: 2 })).code === "ERR_AUTOJS6_TYPESCRIPT_PARSER_TREE_LIMIT", "maxDiagnostics is not enforced.");
assert(parse("x.tsx", counter, Object.assign({}, limits, { maxTreeChars: 100 })).code === "ERR_AUTOJS6_TYPESCRIPT_PARSER_TREE_LIMIT", "maxTreeChars is not enforced.");
const deep = parse("deep.ts", "let x = " + "(".repeat(200000) + "1" + ")".repeat(200000) + ";\n");
assert(deep.kind === "parseError" && deep.code === "ERR_AUTOJS6_TYPESCRIPT_PARSER_TREE_LIMIT", "Deep nesting must fail closed.");

// --- Page relay -------------------------------------------------------------------------------

const pagePath = path.resolve(pageArgument);
const published = [];
let pageListener = null;
let fakeWorker = null;
class FakeWorker {
    constructor(script) {
        assert(script === "parser-worker.js", `The page created ${script}.`);
        this.received = [];
        fakeWorker = this;
    }
    postMessage(message) { this.received.push(message); }
    terminate() { this.terminated = true; }
}
const pageContext = {
    Worker: FakeWorker,
    JSON,
    Math,
    Number,
    String,
    Error,
    Map,
    self: { addEventListener: (type, listener) => { if (type === "message") pageListener = listener; } },
};
vm.createContext(pageContext);
vm.runInContext(fs.readFileSync(pagePath, "utf8"), pageContext, { filename: pagePath });
const port = {
    postMessage: (text) => published.push(JSON.parse(text)),
    start: () => undefined,
    close: () => undefined,
};
pageListener({ data: "__autojs_typescript_parser_port_v1__", ports: [port] });
assert(fakeWorker !== null, "The page did not start the parser Worker.");
fakeWorker.onmessage({ data: { kind: "ready", tsVersion: expectedVersion } });
assert(published.length === 1 && published[0].kind === "ready" && published[0].tsVersion === expectedVersion, "The page did not relay ready.");

const native = (message) => port.onmessage({ data: JSON.stringify(message) });
native({ kind: "parseStart", requestId: "p1", fileName: "a.tsx", chunkCount: 2, limits });
native({ kind: "parseChunk", requestId: "p1", index: 0, data: "let a" });
native({ kind: "parseChunk", requestId: "p1", index: 1, data: " = 1;" });
native({ kind: "parseEnd", requestId: "p1" });
const forwarded = fakeWorker.received[0];
assert(forwarded.kind === "parse" && forwarded.sourceText === "let a = 1;" && forwarded.fileName === "a.tsx", "The page did not join the request frames.");

// A tree whose 16 KiB frame boundary falls inside a surrogate pair.
const bigTree = "x".repeat(16 * 1024 - 1) + "😀" + "y".repeat(40000);
published.length = 0;
fakeWorker.onmessage({ data: { kind: "parseResult", requestId: "p1", tree: bigTree, nodeCount: 3, diagnosticCount: 0, elapsedMs: 1 } });
const start = published[0];
const chunks = published.filter((message) => message.kind === "parseResultChunk");
assert(start.kind === "parseResultStart" && start.chunkCount === chunks.length && start.treeChars === bigTree.length, "Result framing drifted.");
assert(published[published.length - 1].kind === "parseResultEnd", "The result was not closed.");
assert(chunks.map((chunk) => chunk.data).join("") === bigTree, "Result frames do not rebuild the tree.");
chunks.slice(0, -1).forEach((chunk) => {
    const last = chunk.data.charCodeAt(chunk.data.length - 1);
    assert(!(last >= 0xd800 && last <= 0xdbff), "A result frame ends inside a surrogate pair.");
});

native({ kind: "parseChunk", requestId: "nobody", index: 0, data: "x" });
native({ kind: "parseStart", requestId: "p2", fileName: "b.tsx", chunkCount: 2, limits });
published.length = 0;
native({ kind: "parseChunk", requestId: "p2", index: 1, data: "out of order" });
assert(published.length === 1 && published[0].kind === "parseError" && published[0].requestId === "p2", "Out-of-order frames must fail the request.");

console.log(`Parser worker verified: TypeScript ${expectedVersion}, ${counted} nodes, ${chunks.length} result frames.`);
