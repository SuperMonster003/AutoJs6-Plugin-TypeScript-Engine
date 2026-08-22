package io.github.supermonster003.autojs6.plugin.typescript;

import io.github.supermonster003.autojs6.plugin.typescript.IR2CompilerProcessEpochControl;

/**
 * App-private registry hosted by :typescript_supervisor.
 *
 * The returned token is scoped to one exact compiler epoch and callback Binder.
 */
interface IR2CompilerProcessSupervisorRegistry {

    String registerEpoch(
        String compilerBootId,
        int compilerUid,
        int compilerPid,
        long compilerStartTimeTicks,
        String compilerProcessName,
        String compilerNonce,
        IR2CompilerProcessEpochControl control
    );

    boolean notifyPoisonedEpoch(
        String registrationToken,
        String compilerBootId,
        int compilerUid,
        int compilerPid,
        long compilerStartTimeTicks,
        String compilerProcessName,
        String compilerNonce
    );
}
