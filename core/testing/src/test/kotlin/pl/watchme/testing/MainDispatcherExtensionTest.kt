package pl.watchme.testing

import assertk.assertThat
import assertk.assertions.isTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class MainDispatcherExtensionTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `main dispatcher runs on the test dispatcher`() = runTest(mainDispatcher.dispatcher) {
        var ran = false

        launch(Dispatchers.Main) { ran = true }

        assertThat(ran).isTrue()
    }
}
