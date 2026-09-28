package pl.watchme.feature.settings

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.usecase.ObserveSessionUseCase
import pl.watchme.domain.usecase.SignOutUseCase
import pl.watchme.testing.FakeAuthRepository
import pl.watchme.testing.FakeLineupRepository
import pl.watchme.testing.MainDispatcherExtension
import pl.watchme.testing.TestData

class SettingsViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val auth = FakeAuthRepository().apply { session.value = TestData.session }
    private val lineups = FakeLineupRepository(ChannelLineup(setOf(TestData.tvp.id), null, Instant.EPOCH))
    private val viewModel = SettingsViewModel(ObserveSessionUseCase(auth), SignOutUseCase(auth, lineups))

    @Test
    fun `shows the signed in email`() {
        assertThat(viewModel.state.value.email).isEqualTo("jan@example.com")
    }

    @Test
    fun `signing out clears the account and reports it`() = runTest {
        viewModel.events.test {
            viewModel.onSignOut()

            assertThat(awaitItem()).isEqualTo(SettingsEvent.SignedOut)
        }
        assertThat(auth.session.value).isNull()
        assertThat(lineups.lineup.value).isNull()
    }
}
