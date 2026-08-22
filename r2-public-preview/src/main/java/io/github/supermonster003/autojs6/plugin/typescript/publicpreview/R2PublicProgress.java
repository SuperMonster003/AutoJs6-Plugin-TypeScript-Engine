package io.github.supermonster003.autojs6.plugin.typescript.publicpreview;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract;
import org.autojs.plugin.typescript.api.TypeScriptR2Contract;

/** Strict parser and frozen-order verifier for additive public R2 progress Bundles. */
final class R2PublicProgress {

    private static final Set<String> PHASES =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    TypeScriptR2Contract.PHASE_INGRESS,
                                    TypeScriptR2Contract.PHASE_PARSE,
                                    TypeScriptR2Contract.PHASE_SEMANTIC,
                                    TypeScriptR2Contract.PHASE_EMIT)));

    private R2PublicProgress() {}

    static List<Event> parse(List<Bundle> bundles, String requestId) {
        require(bundles != null, "Public progress list is null.");
        require(
                bundles.size()
                        <= TypeScriptR2Contract.MAX_PROGRESS_EVENTS_PER_COMPILATION,
                "Public progress exceeded its per-compilation event bound.");
        List<Event> events = new ArrayList<>(bundles.size());
        long previousElapsedNanos = -1L;
        for (Bundle bundle : bundles) {
            require(bundle != null, "Public progress callback delivered a null Bundle.");
            require(
                    requestId.equals(
                            bundle.getString(
                                    TypeScriptCompilerContract.KEY_REQUEST_ID)),
                    "Public progress request ID drifted.");
            require(
                    bundle.getInt(
                                    TypeScriptR2Contract
                                            .KEY_PROGRESS_SCHEMA_REVISION,
                                    -1)
                            == TypeScriptR2Contract.PROGRESS_SCHEMA_REVISION,
                    "Public progress schema revision drifted.");
            String kind =
                    bundle.getString(TypeScriptR2Contract.KEY_PROGRESS_KIND);
            String phase = bundle.getString(TypeScriptR2Contract.KEY_PHASE);
            require(PHASES.contains(phase), "Public progress phase is invalid.");
            long elapsedNanos =
                    bundle.getLong(
                            TypeScriptR2Contract
                                    .KEY_ELAPSED_REALTIME_NANOS_SINCE_REQUEST_START,
                            -1L);
            require(
                    elapsedNanos >= 0L && elapsedNanos >= previousElapsedNanos,
                    "Public progress monotonic time regressed.");
            require(
                    bundle.getLong(
                                    TypeScriptCompilerContract.KEY_ELAPSED_MS,
                                    -1L)
                            == TimeUnit.NANOSECONDS.toMillis(elapsedNanos),
                    "Public progress millisecond projection drifted.");
            previousElapsedNanos = elapsedNanos;

            if (TypeScriptR2Contract.PROGRESS_KIND_PHASE.equals(kind)) {
                requireExactKeys(bundle, phaseKeys());
                String state =
                        bundle.getString(TypeScriptR2Contract.KEY_PHASE_STATE);
                require(
                        TypeScriptR2Contract.PHASE_STATE_STARTED.equals(state)
                                || TypeScriptR2Contract.PHASE_STATE_COMPLETED
                                        .equals(state),
                        "Public phase progress state is invalid.");
                events.add(Event.phase(phase, state, elapsedNanos));
            } else if (TypeScriptR2Contract.PROGRESS_KIND_WORK.equals(kind)) {
                requireExactKeys(bundle, workKeys());
                int sequence =
                        bundle.getInt(
                                TypeScriptR2Contract.KEY_PROGRESS_SEQUENCE,
                                -1);
                int completedUnits =
                        bundle.getInt(
                                TypeScriptR2Contract.KEY_COMPLETED_UNITS,
                                -1);
                int totalUnits =
                        bundle.getInt(
                                TypeScriptR2Contract.KEY_TOTAL_UNITS,
                                -1);
                require(
                        sequence >= 1
                                && sequence <= 2
                                && totalUnits >= 8
                                && completedUnits > 0
                                && completedUnits < totalUnits,
                        "Public work progress values are invalid.");
                int divisor = sequence == 1 ? 8 : 4;
                require(
                        completedUnits == (totalUnits - 1) / divisor + 1,
                        "Public work progress is not a frozen real-work threshold.");
                events.add(
                        Event.work(
                                phase,
                                sequence,
                                completedUnits,
                                totalUnits,
                                elapsedNanos));
            } else {
                throw new AssertionError("Public progress kind is invalid.");
            }
        }
        return Collections.unmodifiableList(events);
    }

    static ParseWindow requireSuccessfulNaturalParse(
            List<Event> events,
            int rootCount,
            int outputCount,
            boolean successor) {
        List<Event> expected = new ArrayList<>();
        expected.add(phase(TypeScriptR2Contract.PHASE_INGRESS, true));
        expected.add(phase(TypeScriptR2Contract.PHASE_INGRESS, false));
        expected.add(phase(TypeScriptR2Contract.PHASE_PARSE, true));
        if (!successor) {
            expected.add(work(TypeScriptR2Contract.PHASE_PARSE, 1, rootCount));
            expected.add(work(TypeScriptR2Contract.PHASE_PARSE, 2, rootCount));
        }
        expected.add(phase(TypeScriptR2Contract.PHASE_PARSE, false));
        expected.add(phase(TypeScriptR2Contract.PHASE_SEMANTIC, true));
        if (!successor) {
            expected.add(work(TypeScriptR2Contract.PHASE_SEMANTIC, 1, rootCount));
            expected.add(work(TypeScriptR2Contract.PHASE_SEMANTIC, 2, rootCount));
        }
        expected.add(phase(TypeScriptR2Contract.PHASE_SEMANTIC, false));
        expected.add(phase(TypeScriptR2Contract.PHASE_EMIT, true));
        if (!successor) {
            expected.add(work(TypeScriptR2Contract.PHASE_EMIT, 1, outputCount));
            expected.add(work(TypeScriptR2Contract.PHASE_EMIT, 2, outputCount));
        }
        expected.add(phase(TypeScriptR2Contract.PHASE_EMIT, false));
        requireShape(events, expected, "successful natural parse");
        if (successor) {
            require(
                    events.size() == 8,
                    "Small successor must publish only eight phase transitions.");
            return null;
        }
        Event sequenceTwo = events.get(4);
        Event completed = events.get(5);
        require(
                sequenceTwo.elapsedNanos < completed.elapsedNanos,
                "Natural parse public timing window is empty.");
        return new ParseWindow(
                sequenceTwo.elapsedNanos,
                completed.elapsedNanos);
    }

    static Event requireTimedOutNaturalParse(
            List<Event> events,
            int rootCount) {
        List<Event> expected =
                Arrays.asList(
                        phase(TypeScriptR2Contract.PHASE_INGRESS, true),
                        phase(TypeScriptR2Contract.PHASE_INGRESS, false),
                        phase(TypeScriptR2Contract.PHASE_PARSE, true),
                        work(TypeScriptR2Contract.PHASE_PARSE, 1, rootCount),
                        work(TypeScriptR2Contract.PHASE_PARSE, 2, rootCount));
        requireShape(events, expected, "timed-out natural parse");
        return events.get(events.size() - 1);
    }

    private static void requireShape(
            List<Event> actual, List<Event> expected, String label) {
        require(
                actual.size() == expected.size(),
                "Unexpected "
                        + label
                        + " public progress count: "
                        + actual);
        for (int index = 0; index < expected.size(); index++) {
            require(
                    actual.get(index).sameShape(expected.get(index)),
                    "Unexpected "
                            + label
                            + " public progress at index "
                            + index
                            + ": "
                            + actual.get(index));
        }
    }

    private static Event phase(String phase, boolean started) {
        return Event.phase(
                phase,
                started
                        ? TypeScriptR2Contract.PHASE_STATE_STARTED
                        : TypeScriptR2Contract.PHASE_STATE_COMPLETED,
                -1L);
    }

    private static Event work(String phase, int sequence, int totalUnits) {
        int divisor = sequence == 1 ? 8 : 4;
        return Event.work(
                phase,
                sequence,
                (totalUnits - 1) / divisor + 1,
                totalUnits,
                -1L);
    }

    private static Set<String> phaseKeys() {
        return new HashSet<>(
                Arrays.asList(
                        TypeScriptCompilerContract.KEY_REQUEST_ID,
                        TypeScriptCompilerContract.KEY_ELAPSED_MS,
                        TypeScriptR2Contract.KEY_PROGRESS_SCHEMA_REVISION,
                        TypeScriptR2Contract.KEY_PROGRESS_KIND,
                        TypeScriptR2Contract.KEY_PHASE,
                        TypeScriptR2Contract.KEY_PHASE_STATE,
                        TypeScriptR2Contract
                                .KEY_ELAPSED_REALTIME_NANOS_SINCE_REQUEST_START));
    }

    private static Set<String> workKeys() {
        return new HashSet<>(
                Arrays.asList(
                        TypeScriptCompilerContract.KEY_REQUEST_ID,
                        TypeScriptCompilerContract.KEY_ELAPSED_MS,
                        TypeScriptR2Contract.KEY_PROGRESS_SCHEMA_REVISION,
                        TypeScriptR2Contract.KEY_PROGRESS_KIND,
                        TypeScriptR2Contract.KEY_PHASE,
                        TypeScriptR2Contract.KEY_PROGRESS_SEQUENCE,
                        TypeScriptR2Contract.KEY_COMPLETED_UNITS,
                        TypeScriptR2Contract.KEY_TOTAL_UNITS,
                        TypeScriptR2Contract
                                .KEY_ELAPSED_REALTIME_NANOS_SINCE_REQUEST_START));
    }

    private static void requireExactKeys(Bundle bundle, Set<String> expected) {
        require(
                new HashSet<>(bundle.keySet()).equals(expected),
                "Public progress Bundle keys drifted: " + bundle.keySet());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static final class ParseWindow {

        final long sequenceTwoElapsedNanos;
        final long completedElapsedNanos;

        ParseWindow(long sequenceTwoElapsedNanos, long completedElapsedNanos) {
            this.sequenceTwoElapsedNanos = sequenceTwoElapsedNanos;
            this.completedElapsedNanos = completedElapsedNanos;
        }
    }

    static final class Event {

        final String kind;
        final String phase;
        final String state;
        final int sequence;
        final int completedUnits;
        final int totalUnits;
        final long elapsedNanos;

        private Event(
                String kind,
                String phase,
                String state,
                int sequence,
                int completedUnits,
                int totalUnits,
                long elapsedNanos) {
            this.kind = kind;
            this.phase = phase;
            this.state = state;
            this.sequence = sequence;
            this.completedUnits = completedUnits;
            this.totalUnits = totalUnits;
            this.elapsedNanos = elapsedNanos;
        }

        static Event phase(String phase, String state, long elapsedNanos) {
            return new Event(
                    TypeScriptR2Contract.PROGRESS_KIND_PHASE,
                    phase,
                    state,
                    0,
                    0,
                    0,
                    elapsedNanos);
        }

        static Event work(
                String phase,
                int sequence,
                int completedUnits,
                int totalUnits,
                long elapsedNanos) {
            return new Event(
                    TypeScriptR2Contract.PROGRESS_KIND_WORK,
                    phase,
                    null,
                    sequence,
                    completedUnits,
                    totalUnits,
                    elapsedNanos);
        }

        boolean sameShape(Event other) {
            return kind.equals(other.kind)
                    && phase.equals(other.phase)
                    && (state == null ? other.state == null : state.equals(other.state))
                    && sequence == other.sequence
                    && completedUnits == other.completedUnits
                    && totalUnits == other.totalUnits;
        }

        @Override
        public String toString() {
            return kind
                    + ":"
                    + phase
                    + ":"
                    + (state == null ? sequence + ":" + completedUnits + "/" + totalUnits : state)
                    + "@"
                    + elapsedNanos;
        }
    }
}
