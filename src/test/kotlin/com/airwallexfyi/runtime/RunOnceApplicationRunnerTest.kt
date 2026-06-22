package com.airwallexfyi.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments

class RunOnceApplicationRunnerTest {
    @Test
    fun `does nothing when run once argument is absent`() {
        val command = FakeMonitorRunOnceCommand()
        val exit = FakeApplicationExit()
        val runner = RunOnceApplicationRunner(
            MonitorRunMode(DefaultApplicationArguments()),
            command,
            exit,
        )

        runner.run(DefaultApplicationArguments())

        assertThat(command.calls).isZero()
        assertThat(exit.codes).isEmpty()
    }

    @Test
    fun `runs command and exits with returned code when run once argument is present`() {
        val command = FakeMonitorRunOnceCommand(exitCode = 1)
        val exit = FakeApplicationExit()
        val runner = RunOnceApplicationRunner(
            MonitorRunMode(DefaultApplicationArguments("--run-once")),
            command,
            exit,
        )

        runner.run(DefaultApplicationArguments("--run-once"))

        assertThat(command.calls).isEqualTo(1)
        assertThat(exit.codes).containsExactly(1)
    }

    private class FakeMonitorRunOnceCommand(
        private val exitCode: Int = 0,
    ) : MonitorRunOnceCommand {
        var calls = 0

        override fun execute(): Int {
            calls += 1
            return exitCode
        }
    }

    private class FakeApplicationExit : ApplicationExit {
        val codes = mutableListOf<Int>()

        override fun exit(code: Int) {
            codes += code
        }
    }
}
