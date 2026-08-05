package ai.platon.pulsar.api.manage

import ai.platon.pulsar.api.Browser
import ai.platon.pulsar.api.BrowserId
import ai.platon.pulsar.api.ChromeOptions
import ai.platon.pulsar.api.LauncherOptions
import ai.platon.pulsar.api.model.BrowserSettings
import ai.platon.pulsar.common.browser.BrowserProfileMode
import ai.platon.pulsar.common.browser.BrowserType
import ai.platon.pulsar.common.config.ImmutableConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class AbstractBrowserFactoryTest {

    private class RecordingFactory(
        conf: ImmutableConfig = ImmutableConfig(),
        settings: BrowserSettings = BrowserSettings(),
    ) : AbstractBrowserFactory(conf, settings) {
        val launched = mutableListOf<Triple<BrowserId, LauncherOptions, ChromeOptions>>()

        override fun connect(browserType: BrowserType, port: Int, settings: BrowserSettings): Browser = mock()

        override fun launch(
            browserId: BrowserId,
            launcherOptions: LauncherOptions,
            launchOptions: ChromeOptions,
        ): Browser {
            launched += Triple(browserId, launcherOptions, launchOptions)
            return mock()
        }
    }

    @Test
    @DisplayName("profile mode maps to the expected browser id")
    fun profileModeMapsToExpectedBrowserId() {
        val factory = RecordingFactory()

        factory.launch(BrowserProfileMode.SYSTEM_DEFAULT)
        assertEquals(BrowserId.SYSTEM_DEFAULT, factory.launched.last().first)

        factory.launch(BrowserProfileMode.DEFAULT)
        assertEquals(BrowserId.DEFAULT, factory.launched.last().first)

        factory.launch(BrowserProfileMode.PROTOTYPE)
        assertEquals(BrowserId.PROTOTYPE, factory.launched.last().first)

        factory.launch(BrowserProfileMode.TEMPORARY)
        assertNotEquals(BrowserId.PROTOTYPE, factory.launched.last().first)
        assertEquals(BrowserType.PULSAR_CHROME, factory.launched.last().first.browserType)

        factory.launch(BrowserProfileMode.SEQUENTIAL)
        assertNotEquals(BrowserId.PROTOTYPE, factory.launched.last().first)
        assertEquals(BrowserType.PULSAR_CHROME, factory.launched.last().first.browserType)
    }

    @Test
    @DisplayName("convenience launch methods target the expected browser id")
    fun convenienceLaunchMethodsTargetExpectedBrowserId() {
        val factory = RecordingFactory()

        factory.launchSystemDefaultBrowser()
        assertEquals(BrowserId.SYSTEM_DEFAULT, factory.launched.last().first)

        factory.launchDefaultBrowser()
        assertEquals(BrowserId.DEFAULT, factory.launched.last().first)

        factory.launchPrototypeBrowser()
        assertEquals(BrowserId.PROTOTYPE, factory.launched.last().first)

        factory.launchNextSequentialBrowser()
        assertNotEquals(BrowserId.PROTOTYPE, factory.launched.last().first)

        factory.launchRandomTempBrowser()
        assertNotEquals(BrowserId.PROTOTYPE, factory.launched.last().first)
    }

    @Test
    @DisplayName("launch by browser id delegates with factory settings")
    fun launchByBrowserIdDelegatesWithFactorySettings() {
        val factory = RecordingFactory(settings = BrowserSettings())

        factory.launch(BrowserId.PROTOTYPE)

        assertEquals(BrowserId.PROTOTYPE, factory.launched.single().first)
        org.junit.jupiter.api.Assertions.assertSame(factory.settings, factory.launched.single().second.settings)
    }
}
