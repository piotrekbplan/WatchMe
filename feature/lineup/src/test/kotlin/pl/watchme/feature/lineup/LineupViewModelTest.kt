package pl.watchme.feature.lineup

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.watchme.domain.DomainError
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.Catalog
import pl.watchme.domain.model.Channel
import pl.watchme.domain.model.ChannelId
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.model.PackageRef
import pl.watchme.domain.usecase.GetCatalogUseCase
import pl.watchme.domain.usecase.ObserveLineupUseCase
import pl.watchme.domain.usecase.SaveLineupUseCase
import pl.watchme.testing.FakeCatalogRepository
import pl.watchme.testing.FakeLineupRepository
import pl.watchme.testing.MainDispatcherExtension
import pl.watchme.testing.MutableClock
import pl.watchme.testing.TestData

class LineupViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val now = Instant.parse("2026-09-28T19:00:00Z")
    private val lodz = Channel(ChannelId("tvp-3-lodz"), "TVP 3 Łódź", "regionalne", "Regionalne", null)
    private val catalog = Catalog(TestData.catalog.channels + lodz, TestData.catalog.operators)
    private val catalogs = FakeCatalogRepository(Outcome.Success(catalog))
    private val lineups = FakeLineupRepository()

    private fun viewModel() = LineupViewModel(
        GetCatalogUseCase(catalogs),
        ObserveLineupUseCase(lineups),
        SaveLineupUseCase(lineups),
        MutableClock(now),
    )

    private fun LineupViewModel.content() = state.value as LineupUiState.Content

    @Test
    fun `existing lineup opens operator step with its package marked`() {
        lineups.lineup.value = ChannelLineup(setOf(TestData.tvp.id), PackageRef("play", "play-max"), now)

        val content = viewModel().content()

        assertThat(content.step).isEqualTo(LineupStep.OPERATOR)
        assertThat(content.expandedOperatorId).isEqualTo("play")
        assertThat(content.operators.single().packages.filter { it.selected }.map { it.id }).containsExactly("play-max")
        assertThat(content.selectedCount).isEqualTo(1)
    }

    @Test
    fun `choosing a package moves to channels with its channels selected`() {
        val viewModel = viewModel()

        viewModel.onPackageSelected("play", "play-start")

        val content = viewModel.content()
        assertThat(content.step).isEqualTo(LineupStep.CHANNELS)
        assertThat(content.selectedIds()).containsExactlyInAnyOrder("tvp-1", "tvn")
        assertThat(content.groups.map { it.name }).containsExactly("Ogólne", "Filmowe", "Regionalne")
        assertThat(content.canSave).isTrue()
    }

    @Test
    fun `skipping keeps the current selection and shows channels`() {
        lineups.lineup.value = ChannelLineup(setOf(TestData.hbo.id), null, now)
        val viewModel = viewModel()

        viewModel.onSkipPackage()

        assertThat(viewModel.content().step).isEqualTo(LineupStep.CHANNELS)
        assertThat(viewModel.content().selectedIds()).containsExactly("hbo")
    }

    @Test
    fun `going back returns to operators`() {
        val viewModel = viewModel()
        viewModel.onSkipPackage()

        viewModel.onBackToOperators()

        assertThat(viewModel.content().step).isEqualTo(LineupStep.OPERATOR)
    }

    @Test
    fun `toggling a channel updates the selection`() {
        val viewModel = viewModel()
        viewModel.onPackageSelected("play", "play-start")

        viewModel.onChannelToggled("hbo")
        viewModel.onChannelToggled("tvn")

        assertThat(viewModel.content().selectedIds()).containsExactlyInAnyOrder("tvp-1", "hbo")
        assertThat(viewModel.content().selectedCount).isEqualTo(2)
    }

    @Test
    fun `nothing selected cannot be saved`() {
        val viewModel = viewModel()
        viewModel.onSkipPackage()

        assertThat(viewModel.content().canSave).isFalse()
    }

    @Test
    fun `search ignores case and polish diacritics`() {
        val viewModel = viewModel()
        viewModel.onSkipPackage()

        viewModel.onQueryChanged("LODZ")

        val groups = viewModel.content().groups
        assertThat(groups.map { it.name }).containsExactly("Regionalne")
        assertThat(groups.single().channels.map { it.name }).containsExactly("TVP 3 Łódź")
    }

    @Test
    fun `saving stores the draft and reports success`() = runTest {
        val viewModel = viewModel()
        viewModel.onPackageSelected("play", "play-start")

        viewModel.events.test {
            viewModel.onSave()

            assertThat(awaitItem()).isEqualTo(LineupEvent.Saved)
        }
        assertThat(lineups.saved.single().source).isEqualTo(PackageRef("play", "play-start"))
        assertThat(lineups.saved.single().channelIds.map { it.value }).containsExactlyInAnyOrder("tvp-1", "tvn")
    }

    @Test
    fun `catalog failure shows error and retry loads again`() {
        catalogs.outcome = Outcome.Failure(DomainError.Network)
        val viewModel = viewModel()

        assertThat(viewModel.state.value).isInstanceOf<LineupUiState.Error>()

        catalogs.outcome = Outcome.Success(catalog)
        viewModel.onRetry()

        assertThat(viewModel.state.value).isInstanceOf<LineupUiState.Content>()
    }

    private fun LineupUiState.Content.selectedIds() =
        groups.flatMap { it.channels }.filter { it.selected }.map { it.id }
}
