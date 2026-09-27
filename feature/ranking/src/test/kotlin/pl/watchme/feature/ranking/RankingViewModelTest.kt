package pl.watchme.feature.ranking

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.watchme.domain.DomainError
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.usecase.ObserveRankingUseCase
import pl.watchme.domain.usecase.RefreshGuideUseCase
import pl.watchme.testing.FakeCatalogRepository
import pl.watchme.testing.FakeGuideRepository
import pl.watchme.testing.FakeLineupRepository
import pl.watchme.testing.MainDispatcherExtension
import pl.watchme.testing.MutableClock
import pl.watchme.testing.TestData

class RankingViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val now = Instant.parse("2026-09-28T19:00:00Z")
    private val clock = MutableClock(now)
    private val lineups = FakeLineupRepository(ChannelLineup(setOf(TestData.tvp.id, TestData.tvn.id), null, now))
    private val guides = FakeGuideRepository().apply {
        programmes.value = listOf(
            TestData.programme("Pianista", rating = 8.5, votes = 900),
            TestData.programme("Ranczo", channel = TestData.tvn, rating = 8.6, year = null),
            TestData.programme("Wiadomości filmowe", channel = TestData.tvn),
            TestData.programme(
                "Wieczorny film",
                start = Instant.parse("2026-09-28T21:00:00Z"),
                stop = Instant.parse("2026-09-28T23:00:00Z"),
                rating = 7.0,
            ),
        )
    }
    private val catalogs = FakeCatalogRepository(Outcome.Success(TestData.catalog))
    private val subscriptions = mutableListOf<Job>()

    @AfterEach
    fun unsubscribe() {
        subscriptions.forEach { it.cancel() }
    }

    private fun viewModel(zone: ZoneId = ZoneOffset.UTC) = RankingViewModel(
        ObserveRankingUseCase(lineups, guides, catalogs),
        RefreshGuideUseCase(lineups, guides, catalogs, clock),
        clock,
        zone,
    ).also { it.subscribe() }

    private fun RankingViewModel.subscribe(): Job =
        CoroutineScope(mainDispatcher.dispatcher).launch { state.collect {} }.also { subscriptions += it }

    private fun advanceMinutes(minutes: Long) {
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(minutes * 60_000)
        mainDispatcher.dispatcher.scheduler.runCurrent()
    }

    private fun RankingViewModel.content() = state.value as RankingUiState.Content

    @Test
    fun `no channels asks the user to pick some without downloading`() {
        lineups.lineup.value = null

        val viewModel = viewModel()

        assertThat(viewModel.state.value).isEqualTo(RankingUiState.NoChannels)
        assertThat(guides.refreshed).hasSize(0)
    }

    @Test
    fun `opening refreshes stale data and ranks what airs now`() {
        val viewModel = viewModel()

        assertThat(guides.refreshed).hasSize(1)
        val content = viewModel.content()
        assertThat(content.rated.map { it.title }).containsExactly("Ranczo", "Pianista")
        assertThat(content.rated.map { it.position }).containsExactly(1, 2)
        assertThat(content.isRefreshing).isFalse()
        assertThat(content.selectedTime).isNull()
    }

    @Test
    fun `now ranking moves forward with the clock`() {
        val viewModel = viewModel()

        clock.now = Instant.parse("2026-09-28T22:00:00Z")
        advanceMinutes(1)

        assertThat(viewModel.content().rated.map { it.title }).containsExactly("Wieczorny film")
    }

    @Test
    fun `coming back to the screen checks freshness again`() {
        val viewModel = viewModel()
        assertThat(guides.refreshed).hasSize(1)

        subscriptions.forEach { it.cancel() }
        advanceMinutes(1)
        viewModel.subscribe()

        assertThat(guides.refreshed).hasSize(2)
    }

    @Test
    fun `rated item shows rating channel time and progress`() {
        val pianista = viewModel().content().rated.single { it.title == "Pianista" }

        assertThat(pianista.rating).isEqualTo("8.5")
        assertThat(pianista.channelName).isEqualTo("TVP 1")
        assertThat(pianista.channelLogoUrl).isEqualTo("https://logo.example/tvp1.png")
        assertThat(pianista.timeRange).isEqualTo("18:00–20:00")
        assertThat(pianista.subtitle).isEqualTo("film · 2002")
        assertThat(pianista.progress).isEqualTo(0.5f)
    }

    @Test
    fun `unrated programmes have no position nor rating`() {
        val unrated = viewModel().content().unrated.single()

        assertThat(unrated.title).isEqualTo("Wiadomości filmowe")
        assertThat(unrated.position).isNull()
        assertThat(unrated.rating).isNull()
    }

    @Test
    fun `chosen time ranks what airs then and back to now`() {
        val viewModel = viewModel()

        viewModel.onTimeSelected(Instant.parse("2026-09-28T22:00:00Z"))

        assertThat(viewModel.content().rated.map { it.title }).containsExactly("Wieczorny film")
        assertThat(viewModel.content().selectedTime).isEqualTo(Instant.parse("2026-09-28T22:00:00Z"))
        assertThat(viewModel.content().selectedLabel).isEqualTo("28.09 22:00")

        viewModel.onNowSelected()

        assertThat(viewModel.content().selectedTime).isNull()
        assertThat(viewModel.content().rated.map { it.title }).containsExactly("Ranczo", "Pianista")
    }

    @Test
    fun `pull to refresh forces a download`() {
        guides.lastRefreshAt = now
        val viewModel = viewModel()
        assertThat(guides.refreshed).hasSize(0)

        viewModel.onRefresh()

        assertThat(guides.refreshed).hasSize(1)
        assertThat(viewModel.content().isRefreshing).isFalse()
    }

    @Test
    fun `failed refresh with cached data shows offline banner`() {
        guides.refreshOutcome = Outcome.Failure(DomainError.Network)
        guides.lastRefreshAt = Instant.parse("2026-09-28T11:30:00Z")

        val content = viewModel().content()

        assertThat(content.isOffline).isTrue()
        assertThat(content.offlineSince).isEqualTo("11:30")
        assertThat(content.rated.map { it.title }).containsExactly("Ranczo", "Pianista")
    }

    @Test
    fun `offline with cache but nothing airing keeps the ranking screen`() {
        guides.refreshOutcome = Outcome.Failure(DomainError.Network)
        guides.lastRefreshAt = Instant.parse("2026-09-28T11:30:00Z")
        val viewModel = viewModel()

        viewModel.onTimeSelected(Instant.parse("2026-09-29T06:00:00Z"))

        val content = viewModel.content()
        assertThat(content.isEmpty).isTrue()
        assertThat(content.isOffline).isTrue()
    }

    @Test
    fun `failed refresh without any cached data shows error until a retry succeeds`() {
        guides.programmes.value = emptyList()
        guides.refreshOutcome = Outcome.Failure(DomainError.Network)
        val viewModel = viewModel()

        assertThat(viewModel.state.value).isEqualTo(RankingUiState.Error)

        guides.refreshOutcome = Outcome.Success(Unit)
        guides.programmes.value = listOf(TestData.programme("Pianista", rating = 8.5))
        viewModel.onRefresh()

        assertThat(viewModel.state.value).isInstanceOf<RankingUiState.Content>()
    }

    @Test
    fun `times are shown in the device zone`() {
        val pianista = viewModel(ZoneId.of("Europe/Warsaw")).content().rated.single { it.title == "Pianista" }

        assertThat(pianista.timeRange).isEqualTo("20:00–22:00")
    }
}
