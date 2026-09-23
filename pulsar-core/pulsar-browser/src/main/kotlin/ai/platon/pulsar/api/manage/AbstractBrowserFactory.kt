package ai.platon.pulsar.api.manage

import ai.platon.pulsar.api.ChromeOptions
import ai.platon.pulsar.api.LauncherOptions
import ai.platon.pulsar.api.Browser
import ai.platon.pulsar.api.BrowserId
import ai.platon.pulsar.api.model.BrowserLaunchException
import ai.platon.pulsar.api.model.BrowserSettings
import ai.platon.pulsar.common.browser.BrowserProfileMode
import ai.platon.pulsar.common.config.ImmutableConfig

abstract class AbstractBrowserFactory(
    override val conf: ImmutableConfig,
    override val settings: BrowserSettings = BrowserSettings(conf),
) : BrowserFactory {

    @Throws(BrowserLaunchException::class)
    override fun launch(profileMode: BrowserProfileMode): Browser = launch(BrowserId.create(profileMode))

    /**
     * Launch a browser with the given browser id, the browser id is used to identify the browser instance.
     * */
    override fun launch(browserId: BrowserId) = launch(browserId, settings)

    /**
     * Launch the system default browser, the system default browser is your daily used browser.
     * */
    @Throws(BrowserLaunchException::class)
    override fun launch(browserId: BrowserId, settings: BrowserSettings): Browser {
        require(!browserId.isExternal) {
            "Cannot launch an external browser id '$browserId' — it names a browser attached " +
                "from another process (CDP attach or Chrome extension relay). " +
                "Connect to the running browser instead of launching a new one."
        }

        return launch(browserId, createLauncherOptions(settings), createStandardLaunchOptions(browserId, settings))
    }

    /**
     * Launch a browser with the standard launch line of [browserId] plus the extra Chrome
     * command line options in [extraChromeOptions].
     *
     * This is the programmatic counterpart of the `browser.launch.chrome.args` config key: the
     * browser still gets everything the standard launch line sets (headless, window size, user
     * agent, ...), and the extra options are applied on top of it following the [ChromeOptions]
     * priority rules:
     *
     * 1. an extra **raw** argument ([ChromeOptions.rawArguments], i.e. what
     *    [ChromeOptions.addArguments] adds) takes effect only when the key is not already
     *    effectively set by the standard launch line, so it can not accidentally break the
     *    session-forced flags;
     * 2. an extra **additional** argument ([ChromeOptions.addArgument]) overrides the standard
     *    value of the same key, so a caller can deliberately change e.g. the window size.
     *
     * Callers that need full control over the whole command line (and are ready to build every
     * needed switch themselves) should call [launch] with an explicit [ChromeOptions] instead.
     *
     * @param browserId The browser id, it identifies the browser instance and its user data dir
     * @param extraChromeOptions The extra Chrome command line options
     * */
    @Throws(BrowserLaunchException::class)
    override fun launchWithExtraOptions(browserId: BrowserId, extraChromeOptions: ChromeOptions): Browser {
        require(!browserId.isExternal) {
            "Cannot launch an external browser id '$browserId' — it names a browser attached " +
                "from another process (CDP attach or Chrome extension relay). " +
                "Connect to the running browser instead of launching a new one."
        }

        val launchOptions = createStandardLaunchOptions(browserId, settings)
        // Raw arguments have the lowest priority: they fill in keys the program did not set.
        launchOptions.addArguments(extraChromeOptions.rawArguments)
        // Additional arguments are program-level: they deliberately override the standard value.
        extraChromeOptions.additionalArguments.forEach { (key, value) ->
            launchOptions.addArgument(key, value?.toString())
        }

        return launch(browserId, createLauncherOptions(settings), launchOptions)
    }

    /**
     * Create the launcher options for [settings], including the supervisor process when the
     * browser runs in supervised mode.
     * */
    protected fun createLauncherOptions(settings: BrowserSettings): LauncherOptions {
        val launcherOptions = LauncherOptions(settings)
        if (settings.isSupervised) {
            launcherOptions.supervisorProcess = settings.supervisorProcess
            launcherOptions.supervisorProcessArgs.addAll(settings.supervisorProcessArgs)
        }
        return launcherOptions
    }

    /**
     * Create the standard Chrome command line options for [browserId]: everything
     * [BrowserSettings.createChromeOptions] sets, plus the capabilities derived from the browser
     * id (currently the proxy of its fingerprint).
     * */
    protected fun createStandardLaunchOptions(browserId: BrowserId, settings: BrowserSettings): ChromeOptions {
        val capabilities = mutableMapOf<String, Any>()
        val proxyURI = browserId.fingerprint.proxyURI
        if (proxyURI != null) {
            capabilities["proxy"] = proxyURI
        }

        return settings.createChromeOptions(capabilities)
    }

    /**
     * Launch the system default browser, the system default browser is your daily used browser.
     * */
    @Throws(BrowserLaunchException::class)
    override fun launchSystemDefaultBrowser(): Browser =
        launch(BrowserId.createSystemDefault(), LauncherOptions(settings), ChromeOptions())

    /**
     * Launch the default browser, notice, the default browser is not the one you used daily.
     * */
    @Throws(BrowserLaunchException::class)
    override fun launchDefaultBrowser(): Browser = launch(BrowserId.createDefault(), LauncherOptions(settings), ChromeOptions())

    /**
     * Launch the prototype browser, the prototype browser is a browser instance with default settings.
     * */
    @Throws(BrowserLaunchException::class)
    override fun launchPrototypeBrowser(): Browser =
        launch(BrowserId.createPrototype(), LauncherOptions(settings), ChromeOptions())

    /**
     * Launch the next sequential browser, the browser's user data dir rotates between a group of dirs.
     * */
    @Throws(BrowserLaunchException::class)
    override fun launchNextSequentialBrowser(): Browser =
        launch(BrowserId.createNextSequential(), LauncherOptions(settings), ChromeOptions())

    /**
     * Launch a random temporary browser, the browser's user data dir is a random temporary dir.
     * */
    @Throws(BrowserLaunchException::class)
    override fun launchRandomTempBrowser(): Browser =
        launch(BrowserId.createRandomTemp(), LauncherOptions(settings), ChromeOptions())
}
