package ai.platon.pulsar.driver.examples.site

import ai.platon.pulsar.driver.examples.BrowserExampleBase

class Crawler: BrowserExampleBase() {

    override val testUrl = "https://ly.simuwang.com/"

    override suspend fun run() {
        network.setBlockedURLs(listOf("*fireyejs*"))
        network.enable()

        page.addScriptToEvaluateOnNewDocument(preloadJs)
        page.enable()

        page.navigate(testUrl)
    }
}

suspend fun main() {
    Crawler().use { it.run() }
}
