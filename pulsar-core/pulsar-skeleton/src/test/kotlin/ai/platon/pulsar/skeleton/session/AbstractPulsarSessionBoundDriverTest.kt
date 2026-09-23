package ai.platon.pulsar.skeleton.session

import ai.platon.pulsar.api.Browser
import ai.platon.pulsar.api.BrowserId
import ai.platon.pulsar.api.BrowserManager
import ai.platon.pulsar.api.ChromeOptions
import ai.platon.pulsar.api.WebDriver
import ai.platon.pulsar.common.browser.BrowserProfileMode
import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.common.config.VolatileConfig
import ai.platon.pulsar.skeleton.context.support.AbstractPulsarContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertSame

/**
 * A downstream caller must be able to create a driver for a session whose browser starts with a
 * customized Chrome command line.
 *
 * The interesting part is the wiring: the extra options must reach the browser manager, the
 * browser must be launched for the profile mode of the session, and the created driver must be
 * bound to the session.
 * */
@Tag("Unit")
@Tag("Fast")
@DisplayName("PulsarSession creates a bound driver with extra Chrome options")
class AbstractPulsarSessionBoundDriverTest {

    private lateinit var context: AbstractPulsarContext
    private lateinit var browserManager: BrowserManager
    private lateinit var browser: Browser
    private lateinit var driver: WebDriver
    private lateinit var sessionConfig: VolatileConfig
    private lateinit var session: BasicPulsarSession

    @BeforeEach
    fun setUp() {
        context = mockk()
        browserManager = mockk()
        browser = mockk()
        driver = mockk()

        sessionConfig = VolatileConfig().apply {
            this[CapabilityTypes.BROWSER_PROFILE_MODE] = BrowserProfileMode.DEFAULT.name
        }

        every { context.isActive } returns true
        every { context.browserManager } returns browserManager
        every { browserManager.settings } returns mockk(relaxed = true)
        every { browser.newDriver() } returns driver
        every { driver.browser } returns browser

        session = BasicPulsarSession(context, sessionConfig)
    }

    @Test
    @DisplayName("the extra Chrome options reach the browser manager and the driver is bound")
    fun extraChromeOptionsReachTheBrowserManagerAndTheDriverIsBound() {
        val extraChromeOptions = ChromeOptions().addArguments("--lang=zh-CN")
        every {
            browserManager.launchWithExtraOptions(any(), extraChromeOptions)
        } returns browser

        val createdDriver = session.createBoundDriver(extraChromeOptions)

        assertSame(driver, createdDriver)
        // The browser is launched for the profile mode of the session: an equal browser id is
        // enough, since BrowserId equality is profile based.
        verify { browserManager.launchWithExtraOptions(BrowserId.create(BrowserProfileMode.DEFAULT), extraChromeOptions) }
        assertSame(driver, session.boundDriver, "the created driver must be bound to the session")
        assertSame(browser, session.boundBrowser, "the launched browser must be bound to the session")
    }

    @Test
    @DisplayName("the profile mode of the session config selects the browser id")
    fun theProfileModeOfTheSessionConfigSelectsTheBrowserId() {
        sessionConfig[CapabilityTypes.BROWSER_PROFILE_MODE] = BrowserProfileMode.PROTOTYPE.name
        val extraChromeOptions = ChromeOptions().addArguments("--lang=zh-CN")
        every { browserManager.launchWithExtraOptions(any(), any()) } returns browser

        session.createBoundDriver(extraChromeOptions)

        verify { browserManager.launchWithExtraOptions(BrowserId.createPrototype(), extraChromeOptions) }
    }
}
