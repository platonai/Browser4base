package ai.platon.pulsar.driver.examples

import ai.platon.pulsar.browser.common.BrowserSettings
import ai.platon.pulsar.browser.impl.DevToolsConfig
import ai.platon.pulsar.chrome.ChromeLauncher
import ai.platon.pulsar.chrome.util.ChromeOptions
import ai.platon.pulsar.common.browser.BrowserFiles
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

abstract class BrowserExampleBase(val headless: Boolean = false): AutoCloseable {
    val logger = LoggerFactory.getLogger(BrowserExampleBase::class.java)

    open val testUrl = "https://gitee.com/"

    val browserSettings = BrowserSettings()
    val preloadJs = browserSettings.scriptLoader.getPreloadJs()
    val launchOptions = ChromeOptions()
            .addArgument("window-size", formatViewPort())
            .also { it.headless = headless }
    val userDataDir = BrowserFiles.computeTestContextDir()
    val launcher = ChromeLauncher(userDataDir)

    val chrome = launcher.launch(launchOptions)
    val tab = chrome.createTab()
    val devTools = chrome.createDevTools(tab, DevToolsConfig())

    val browser get() = devTools.browser
    val network get() = devTools.network
    val page get() = devTools.page
    val mainFrame get() = runBlocking { page.getFrameTree().frame }
    val runtime get() = devTools.runtime
    val emulation get() = devTools.emulation
    val dom get() = devTools.dom
    val overlay get() = devTools.overlay

    abstract suspend fun run()

    val pageSource: String
        get() {
            val evaluation = runBlocking { runtime.evaluate("document.documentElement.outerHTML") }
            return evaluation.result.value.toString()
        }

    private fun formatViewPort(delimiter: String = ","): String {
        val vp = BrowserSettings.VIEWPORT
        return "${vp.width}$delimiter${vp.height}"
    }

    override fun close() {
        devTools.awaitTermination()
    }
}
