// Type definitions for AutoJs6 internal module debugging
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Nov 15, 2026

/// <reference path="./index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/modules/debugging/DebuggingModule.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/modules/debugging/Debugging.kt
 */

declare namespace Internal {

    /**
     * The global `debugging` object (also `$debugging`), available since AutoJs6 6.8.1: what a script knows about the
     * debugger of its own execution. A debugger is the debugger of the code editor or one connected through the
     * external 3-Probe Debugger plugin (such as VS Code). The module reads the debugging state and never controls it:
     * a script cannot read breakpoints or variables, or step its own session.
     *
     * A script is attachable only when debugging was on when it started (the editor's "Debug", or the "Debug service"
     * of the plugin). Otherwise, and always in packaged apps, it is never attached: {@link waitForAttach} returns
     * `false` when its timeout ends (at once without a timeout), {@link breakpoint} does nothing and the listeners never
     * run. Rhino scripts only; Node.js scripts use the `inspector` module of Node.js.
     *
     * @see https://docs.autojs6.com/#/debugging
     */
    interface Debugging {

        /**
         * Whether a debugger is attached to this script.
         * @since 6.8.1
         * @example
         * if (debugging.isAttached()) console.log('debugged');
         */
        isAttached(): boolean;

        /**
         * Waits until a debugger is attached and has ended its configuration (its breakpoints are set), at most `timeout`
         * milliseconds, and tells whether it is attached. Without `timeout` (or with `null`) it waits without a limit,
         * unless the script cannot be attached, when it returns `false` at once. Blocks the calling thread (avoid the UI
         * thread) and stops at once when the script is stopped. Throws a `RangeError` for a negative `timeout`.
         * @param timeout milliseconds to wait, an integer of 0 or more; no limit when omitted
         * @since 6.8.1
         * @example
         * if (debugging.waitForAttach(30000)) {
         *     debugging.breakpoint();
         * }
         */
        waitForAttach(timeout?: number | null): boolean;

        /**
         * Pauses here like a `debugger` statement while a debugger is attached (the stop reason is
         * `debugger_statement`); does nothing otherwise.
         * @since 6.8.1
         */
        breakpoint(): void;

        /**
         * Listens to `event`. The listener runs on the thread that added it, through its event loop; while that thread is
         * paused by the debugger the call waits until it goes on. Listeners do not keep the script running. Throws a
         * `RangeError` for an unknown event and a `TypeError` when `listener` is not a function.
         * @since 6.8.1
         * @example
         * debugging.on('pause', e => console.log(`paused on ${e.threadName}: ${e.reason}`));
         */
        on<E extends Debugging.EventName>(event: E, listener: Debugging.Listener<E>): Debugging.Subscription;

        /** Like {@link on}, removed after the first call. @since 6.8.1 */
        once<E extends Debugging.EventName>(event: E, listener: Debugging.Listener<E>): Debugging.Subscription;

        /** Removes every debugging listener of this script. @since 6.8.1 */
        off(): void;
        /** Removes the listeners of `event`. @since 6.8.1 */
        off(event: Debugging.EventName): void;
        /** Removes the listeners calling `listener`, whatever their event. @since 6.8.1 */
        off(listener: (...args: any[]) => void): void;
        /** Removes the listeners of `event` calling `listener`. @since 6.8.1 */
        off(event: Debugging.EventName, listener: (...args: any[]) => void): void;

    }

    namespace Debugging {

        /**
         * `attach`: a debugger attached; `detach`: it left (also when debugging is turned off); `pause`: a thread of the
         * script stopped (a breakpoint, a step, a pause request, an exception, `debugger` or {@link Debugging.breakpoint});
         * `resume`: the script runs again.
         */
        type EventName = 'attach' | 'detach' | 'pause' | 'resume';

        /** The listener of `event`. */
        type Listener<E extends EventName> =
            E extends 'attach' ? (info: AttachInfo) => void :
                E extends 'pause' ? (event: PauseEvent) => void :
                    () => void;

        interface AttachInfo {
            /** `editor` for the debugger of the code editor, `remote` for a debugger connected through 3-Probe Debugger. */
            readonly frontend: 'editor' | 'remote';
        }

        interface PauseEvent {
            /** Why the thread stopped. */
            readonly reason: 'breakpoint' | 'step' | 'pause' | 'entry' | 'exception' | 'debugger_statement';
            /** The name of the stopped thread, as the debugger shows it. */
            readonly threadName: string;
        }

        interface Subscription {
            readonly event: EventName;
            /** Removes this listener; repeated calls do nothing. */
            off(): void;
        }

    }

}

// Globals of the debugging module, from aj6-int-init.d.ts of the same source.
/** Whether the script is being debugged, waiting for a debugger, breakpoints in code and the attach / detach / pause / resume events, Rhino scripts only. Available since AutoJs6 6.8.1. */
declare let debugging: Internal.Debugging;
declare let $debugging: Internal.Debugging;
