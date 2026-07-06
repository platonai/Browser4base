package ai.platon.pulsar.driver.examples

import ai.platon.cdt.kt.protocol.events.tracing.DataCollected
import ai.platon.pulsar.chrome.ChromeLauncher
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths

suspend fun main() {
    // Create chrome launcher.
    val launcher = ChromeLauncher()

    // Launch chrome either as headless (true) or regular (false).
    val chromeService = launcher.launch(false)

    // Create empty tab ie about:blank.
    val tab = chromeService.createTab()

    // Get DevTools service to this tab
    val devToolsService = chromeService.createDevToolsService(tab)

    // Get individual commands
    val page = devToolsService.page
    val tracing = devToolsService.tracing

    val dataCollectedList = mutableListOf<Any>()

    // Add tracing data to dataCollectedList
    tracing.onDataCollected { event: DataCollected ->
        dataCollectedList.addAll(event.value)
    }

    // When tracing is complete, dump dataCollectedList to JSON file.
    tracing.onTracingComplete {
        // Dump tracing to file.
        val path = Paths.get("/tmp/tracing.json")
        println("Tracing completed! Dumping to $path")

        val om = ObjectMapper()
        om.writeValue(path.toFile(), dataCollectedList)

        devToolsService.close()
    }

    page.onLoadEventFired { tracing.end() }

    page.enable()
    tracing.start()
    page.navigate("https://github.com")
}
