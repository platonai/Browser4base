package ai.platon.pulsar.api

import ai.platon.pulsar.api.model.BrowserSettings

interface BrowserLauncher {
    fun connect(port: Int, settings: BrowserSettings = BrowserSettings()): Browser
    fun launch(
        browserId: BrowserId,
        launcherOptions: LauncherOptions,
        launchOptions: ChromeOptions
    ): Browser
}
