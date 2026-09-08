package ai.platon.pulsar.api.manage

import ai.platon.pulsar.api.BrowserId
import ai.platon.pulsar.api.ChromeOptions
import ai.platon.pulsar.api.LauncherOptions
import ai.platon.pulsar.api.Browser
import ai.platon.pulsar.api.model.BrowserSettings
import ai.platon.pulsar.common.browser.BrowserType
import ai.platon.pulsar.common.config.ImmutableConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class AbstractBrowserFactoryExternalIdTest {

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
    @DisplayName("external browser ids are rejected by the launch funnel")
    fun launchRejectsExternalBrowserId() {
        val factory = RecordingFactory()

        val e = assertThrows(IllegalArgumentException::class.java) {
            factory.launch(BrowserId.external("session-guard"))
        }
        assertTrue(e.message!!.contains("external browser id"))
        assertTrue(factory.launched.isEmpty(), "Nothing may be launched for an external browser id")
    }

    @Test
    @DisplayName("launching by profile mode still works with local ids")
    fun launchByProfileModeIsUnaffected() {
        val factory = RecordingFactory()

        factory.launchDefaultBrowser()

        assertEquals(BrowserId.createDefault(), factory.launched.single().first)
    }
}
