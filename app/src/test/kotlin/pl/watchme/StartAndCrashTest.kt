package pl.watchme

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.usecase.ObserveLineupUseCase
import pl.watchme.domain.usecase.ObserveSessionUseCase
import pl.watchme.domain.usecase.SyncLineupUseCase
import pl.watchme.logging.CrashLogger
import pl.watchme.navigation.StartDestination
import pl.watchme.navigation.StartViewModel
import pl.watchme.testing.FakeAuthRepository
import pl.watchme.testing.FakeLineupRepository
import pl.watchme.testing.MainDispatcherExtension
import pl.watchme.testing.TestData

class StartAndCrashTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val now = Instant.parse("2026-09-28T19:00:00Z")
    private val auth = FakeAuthRepository()
    private val lineups = FakeLineupRepository()

    private fun viewModel() = StartViewModel(
        ObserveSessionUseCase(auth),
        ObserveLineupUseCase(lineups),
        SyncLineupUseCase(lineups),
    )

    @Test
    fun `signed out user starts with sign in`() {
        assertThat(viewModel().destination.value).isEqualTo(StartDestination.LOGIN)
        assertThat(lineups.syncCalls).isEqualTo(0)
    }

    @Test
    fun `signed in user without channels starts with channel selection`() {
        auth.session.value = TestData.session
        lineups.lineup.value = ChannelLineup.empty(now)

        assertThat(viewModel().destination.value).isEqualTo(StartDestination.LINEUP)
    }

    @Test
    fun `signed in user with channels starts with the ranking and syncs`() {
        auth.session.value = TestData.session
        lineups.lineup.value = ChannelLineup(setOf(TestData.tvp.id), null, now)

        assertThat(viewModel().destination.value).isEqualTo(StartDestination.RANKING)
        assertThat(lineups.syncCalls).isEqualTo(1)
    }

    @Test
    fun `losing the session is reported once`() = runTest {
        auth.session.value = TestData.session
        val viewModel = viewModel()

        viewModel.sessionEnded.test {
            auth.session.value = null

            assertThat(awaitItem()).isEqualTo(Unit)
            expectNoEvents()
        }
    }

    @Test
    fun `starting signed out does not report a lost session`() = runTest {
        viewModel().sessionEnded.test {
            expectNoEvents()
        }
    }

    @Test
    fun `crash is logged and passed to the previous handler`() {
        val logged = mutableListOf<Throwable>()
        val delegated = mutableListOf<Throwable>()
        val previous = Thread.UncaughtExceptionHandler { _, error -> delegated += error }
        val crash = IllegalStateException("boom")

        CrashLogger(log = { _, error -> logged += error }, previous = previous)
            .uncaughtException(Thread.currentThread(), crash)

        assertThat(logged).containsExactly(crash)
        assertThat(delegated.single()).isSameInstanceAs(crash)
    }
}
