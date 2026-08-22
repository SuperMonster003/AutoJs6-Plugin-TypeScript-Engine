package io.github.supermonster003.autojs6.plugin.typescript;

/**
 * App-private callback hosted by :typescript_compiler.
 *
 * This interface is deliberately outside plugin-api and is not a public compiler contract.
 */
interface IR2CompilerProcessEpochControl {

    boolean establishSupervisorRegistration(
        String registrationToken,
        String supervisorBootId,
        int supervisorUid,
        int supervisorPid,
        long supervisorStartTimeTicks,
        String supervisorProcessName
    );

    boolean terminatePoisonedEpoch(
        String registrationToken,
        String compilerBootId,
        int compilerUid,
        int compilerPid,
        long compilerStartTimeTicks,
        String compilerProcessName,
        String compilerNonce,
        long commandDeadlineElapsedRealtime
    );
}
