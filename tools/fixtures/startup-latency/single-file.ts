// Startup-latency fixture: one Rhino-profile TypeScript file with no side effects.
// tools/observe-startup-latency.ps1 rewrites RUN_MARKER before every cache-miss run and
// pushes this file to the device; the first statements persist a wall-clock stamp next to the
// fixture for the stopwatch cross-check described in startup-latency-roadmap.md (L0-7).
const firstStatementAt = Date.now();
const STAMP_PATH = "first-statement.txt";
files.write(STAMP_PATH, String(firstStatementAt));
console.log(`startup-latency first-statement ${firstStatementAt}`);

const RUN_MARKER = "run-00000000";

interface Sample {
    readonly name: string;
    readonly value: number;
}

type Summary = {
    count: number;
    total: number;
    mean: number;
};

enum Phase {
    Ingress = "ingress",
    Parse = "parse",
    Semantic = "semantic",
    Emit = "emit",
}

// AutoJs6's Rhino rejects the ES2015 `class` keyword at parse time, so the fixture models the
// accumulator as a closure-backed interface instead of a class declaration.
interface Accumulator<T extends Sample> {
    add(item: T): Accumulator<T>;
    summarize(): Summary;
}

function createAccumulator<T extends Sample>(): Accumulator<T> {
    const items: T[] = [];
    const accumulator: Accumulator<T> = {
        add(item: T): Accumulator<T> {
            items.push(item);
            return accumulator;
        },
        summarize(): Summary {
            const total = items.reduce((sum, item) => sum + item.value, 0);
            const count = items.length;
            return { count, total, mean: count === 0 ? 0 : total / count };
        },
    };
    return accumulator;
}

function describeDevice(): string {
    const api: number = device.sdkInt;
    const size = `${device.width}x${device.height}`;
    return `api=${api} size=${size}`;
}

const phases = Object.values(Phase) as readonly Phase[];
const accumulator = createAccumulator<Sample>();
phases.forEach((phase, index) => accumulator.add({ name: phase, value: (index + 1) * 10 }));
const summary = accumulator.summarize();
const lookup = new Map<string, number>(phases.map((phase, index) => [phase, index] as const));
const maybe: Summary | undefined = summary.count > 0 ? summary : undefined;
const mean = maybe?.mean ?? -1;

console.log(`${RUN_MARKER} ${describeDevice()} host=${app.autojs.versionName}`);
console.log(`phases=${lookup.size} mean=${mean.toFixed(1)} cwd=${files.cwd()}`);
console.log("startup-latency done");
