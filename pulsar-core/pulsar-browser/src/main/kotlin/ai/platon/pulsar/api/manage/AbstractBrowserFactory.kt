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
    override fun launch(profileMode: BrowserProfileMode): Browser {
        val browserId = when (profileMode) {
            BrowserProfileMode.SYSTEM_DEFAULT -> BrowserId.createSystemDefault()
            BrowserProfileMode.DEFAULT -> BrowserId.createDefault()
            BrowserProfileMode.PROTOTYPE -> BrowserId.createPrototype()
            BrowserProfileMode.TEMPORARY -> BrowserId.createRandomTemp()
            BrowserProfileMode.SEQUENTIAL -> BrowserId.createNextSequential()
        }

        return launch(browserId)
    }

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

        val launcherOptions = LauncherOptions(settings)
        if (settings.isSupervised) {
            launcherOptions.supervisorProcess = settings.supervisorProcess
            launcherOptions.supervisorProcessArgs.addAll(settings.supervisorProcessArgs)
        }

        val capabilities = mutableMapOf<String, Any>()
        val proxyURI = browserId.fingerprint.proxyURI
        if (proxyURI != null) {
            capabilities["proxy"] = proxyURI
        }

        val launchOptions = settings.createChromeOptions(capabilities)

        return launch(browserId, launcherOptions, launchOptions)
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
