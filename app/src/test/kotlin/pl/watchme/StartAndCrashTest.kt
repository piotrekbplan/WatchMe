package pl.watchme

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.usecase.ObserveLineupUseCase
import pl.watchme.logging.CrashLogger
import pl.watchme.navigation.StartDestination
import pl.watchme.navigation.StartViewModel
import pl.watchme.testing.FakeLineupRepository
import pl.watchme.testing.MainDispatcherExtension
import pl.watchme.testing.TestData

class StartAndCrashTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val now = Instant.parse("2026-09-28T19:00:00Z")

    @Test
    fun `first launch starts with channel selection`() {
        val viewModel = StartViewModel(ObserveLineupUseCase(FakeLineupRepository()))

        assertThat(viewModel.destination.value).isEqualTo(StartDestination.LINEUP)
    }

    @Test
    fun `empty lineup starts with channel selection`() {
        val viewModel = StartViewModel(ObserveLineupUseCase(FakeLineupRepository(ChannelLineup.empty(now))))

        assertThat(viewModel.destination.value).isEqualTo(StartDestination.LINEUP)
    }

    @Test
    fun `saved channels start with the ranking`() {
        val lineup = ChannelLineup(setOf(TestData.tvp.id), null, now)

        val viewModel = StartViewModel(ObserveLineupUseCase(FakeLineupRepository(lineup)))

        assertThat(viewModel.destination.value).isEqualTo(StartDestination.RANKING)
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
