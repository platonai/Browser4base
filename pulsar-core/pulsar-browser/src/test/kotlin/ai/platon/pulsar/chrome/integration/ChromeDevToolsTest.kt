package ai.platon.pulsar.chrome.integration

import ai.platon.pulsar.api.ChromeOptions
import ai.platon.pulsar.api.LauncherOptions
import ai.platon.pulsar.api.BrowserProtocol
import ai.platon.pulsar.chrome.ChromeLauncher
import ai.platon.pulsar.chrome.RemoteChrome
import ai.platon.pulsar.chrome.RemoteDevTools
import ai.platon.pulsar.common.browser.BrowserFiles
import ai.platon.pulsar.common.serialize.json.Pson
import ai.platon.pulsar.common.sleepSeconds
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChromeDevToolsTest {

    private lateinit var launcher: ChromeLauncher
    private lateinit var chrome: RemoteChrome
    private lateinit var devTools: RemoteDevTools
    private lateinit var browserProtocol: BrowserProtocol

    @BeforeTest
    fun createDevTools() {
        val userDataDir = BrowserFiles.computeRandomTmpContextDir()

        launcher = ChromeLauncher(userDataDir, options = LauncherOptions())
        val options = ChromeOptions().also { it.noSandbox = true }
        chrome = launcher.launch(options)

        val tab = chrome.createTab()
        val versionString = Pson.toJson(chrome.version)
        assertTrue(!chrome.version.browser.isNullOrBlank())
        assertTrue(versionString.contains("Mozilla"))

        devTools = chrome.createDevTools(tab)
        browserProtocol = BrowserProtocol.create(devTools)

        runBlocking { browserProtocol.pageEnable() }
    }

    @AfterTest
    fun closeBrowser() {
        chrome.close()
        launcher.close()
    }

    @Test
    fun testDevTools() {
        runBlocking {
            browserProtocol.navigate("https://vercel.com/")
            val navigate = browserProtocol.navigate("https://www.example.com/")
            assertNotNull(navigate)
        }

        sleepSeconds(2)
    }
}
