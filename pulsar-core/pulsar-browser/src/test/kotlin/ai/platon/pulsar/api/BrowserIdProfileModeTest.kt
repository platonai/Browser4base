package ai.platon.pulsar.api

import ai.platon.pulsar.common.browser.BrowserProfileMode
import ai.platon.pulsar.common.browser.BrowserType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * [BrowserId.create] is the single mapping from a [BrowserProfileMode] to the corresponding
 * explicit browser id factory, so callers that resolve a profile mode themselves (e.g. a session
 * launching a browser with custom Chrome launch options) do not have to duplicate it.
 * */
@Tag("Unit")
@Tag("Fast")
@DisplayName("BrowserId.create resolves a profile mode to a browser id")
class BrowserIdProfileModeTest {

    @Test
    @DisplayName("stable profile modes map to the corresponding browser id factory")
    fun stableProfileModesMapToTheCorrespondingFactory() {
        assertEquals(BrowserId.createSystemDefault(), BrowserId.create(BrowserProfileMode.SYSTEM_DEFAULT))
        assertEquals(BrowserId.createDefault(), BrowserId.create(BrowserProfileMode.DEFAULT))
        assertEquals(BrowserId.createPrototype(), BrowserId.create(BrowserProfileMode.PROTOTYPE))
    }

    @Test
    @DisplayName("allocating profile modes create a fresh, non prototype browser id")
    fun allocatingProfileModesCreateAFreshBrowserId() {
        val temporary = BrowserId.create(BrowserProfileMode.TEMPORARY)
        assertNotEquals(BrowserId.createPrototype(), temporary)
        assertEquals(BrowserType.PULSAR_CHROME, temporary.browserType)

        val sequential = BrowserId.create(BrowserProfileMode.SEQUENTIAL)
        assertNotEquals(BrowserId.createPrototype(), sequential)
        assertEquals(BrowserType.PULSAR_CHROME, sequential.browserType)
    }

    @Test
    @DisplayName("an empty context mode resolves to the default browser id, as fromString does")
    fun emptyContextModeResolvesToTheDefaultBrowserId() {
        assertEquals(BrowserId.createDefault(), BrowserId.create(BrowserProfileMode.fromString(null)))
        assertEquals(BrowserId.createDefault(), BrowserId.create(BrowserProfileMode.fromString("")))
    }
}
