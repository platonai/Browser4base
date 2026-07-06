package ai.platon.pulsar.driver.examples

class EventsExample: BrowserExampleBase() {
    override suspend fun run() {
        val page = devTools.page
        val network = devTools.network

        network.enable()
        page.enable()
        page.navigate(testUrl)
    }
}

suspend fun main() {
    EventsExample().use { it.run() }
}
