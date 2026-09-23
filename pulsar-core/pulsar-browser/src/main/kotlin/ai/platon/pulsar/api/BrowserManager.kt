package ai.platon.pulsar.api

import ai.platon.pulsar.api.model.BrowserLaunchException
import ai.platon.pulsar.api.model.BrowserSettings
import ai.platon.pulsar.api.manage.BrowserFactory
import ai.platon.pulsar.common.browser.BrowserProfileMode

interface BrowserManager : AutoCloseable {
    val browserFactory: BrowserFactory

    val settings: BrowserSettings

    val browsers: Map<BrowserId, Browser>

    @Throws(BrowserLaunchException::class)
    fun launch(profileMode: BrowserProfileMode): Browser

    /**
     * Launch the system default browser, the system default browser is your daily used browser.
     * */
    @Throws(BrowserLaunchException::class)
    fun launch(browserId: BrowserId, settings: BrowserSettings): Browser

    /**
     * Launch a browser with an explicit [LauncherOptions] and an explicit Chrome command line,
     * and let this manager own the launched browser.
     *
     * This is the escape hatch for callers that need full control over the browser command line:
     * [launchOptions] is passed to the browser process exactly as given, nothing is added to it.
     * A caller that only wants to add a few switches on top of the standard launch line should
     * call [launchWithExtraOptions] instead.
     *
     * A browser process can only be configured when it starts, so the options never affect a
     * browser that is already running — always launch a new [BrowserId] for a new command line.
     *
     * @param browserId The browser id, it identifies the browser instance and its user data dir
     * @param launcherOptions The launcher options, e.g. the supervisor process
     * @param launchOptions The Chrome command line options, see [ChromeOptions]
     * */
    @Throws(BrowserLaunchException::class)
    fun launch(
        browserId: BrowserId, launcherOptions: LauncherOptions, launchOptions: ChromeOptions
    ): Browser = browserFactory.launch(browserId, launcherOptions, launchOptions)

    /**
     * Launch a browser with the standard launch line of [browserId] plus the extra Chrome
     * command line options in [extraChromeOptions], and let this manager own the launched browser.
     *
     * The browser still gets everything the standard launch line sets (headless, window size,
     * user agent, the proxy of the browser fingerprint, ...), and the extra options are applied
     * on top of it following the [ChromeOptions] priority rules: raw arguments (what
     * [ChromeOptions.addArguments] adds) take effect only for keys the program did not set, while
     * additional arguments ([ChromeOptions.addArgument]) override the standard value of the same
     * key.
     *
     * A Chrome process is only configured when it starts: when a Chrome process is already running
     * for the same user data directory, the launcher attaches to it and the extra options have no
     * effect — use a fresh browser profile when the command line must change.
     *
     * ```kotlin
     * val browser = context.browserManager.launchWithExtraOptions(
     *     BrowserId.createDefault(),
     *     ChromeOptions().addArguments("--lang=zh-CN"),
     * )
     * val driver = browser.newDriver()
     * ```
     *
     * @param browserId The browser id, it identifies the browser instance and its user data dir
     * @param extraChromeOptions The extra Chrome command line options
     * */
    @Throws(BrowserLaunchException::class)
    fun launchWithExtraOptions(browserId: BrowserId, extraChromeOptions: ChromeOptions): Browser =
        browserFactory.launchWithExtraOptions(browserId, extraChromeOptions)

    /**
     * Launch the system default browser, the system default browser is your daily used browser.
     * */
    @Throws(BrowserLaunchException::class)
    fun launchSystemDefaultBrowser(): Browser

    /**
     * Launch the default browser, notice, the default browser is not the one you used daily.
     * */
    @Throws(BrowserLaunchException::class)
    fun launchDefaultBrowser(): Browser

    /**
     * Launch the prototype browser, the prototype browser is a browser instance with default settings.
     * */
    @Throws(BrowserLaunchException::class)
    fun launchPrototypeBrowser(): Browser

    /**
     * Launch a random temporary browser, the browser's user data dir is a random temporary dir.
     * */
    @Throws(BrowserLaunchException::class)
    fun launchRandomTempBrowser(): Browser

    fun findBrowserOrNull(browserId: BrowserId): Browser?

    fun closeBrowser(browserId: BrowserId)

    fun closeBrowser(browser: Browser)

    fun closeDriver(driver: WebDriver)
}
