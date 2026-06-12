package ai.platon.pulsar.driver.examples

import ai.platon.cdt.kt.protocol.events.overlay.ScreenshotRequested
import ai.platon.cdt.kt.protocol.types.dom.RGBA
import ai.platon.cdt.kt.protocol.types.overlay.HighlightConfig
import ai.platon.pulsar.common.serialize.json.prettyPulsarObjectMapper

class OverlayExample : BrowserExampleBase() {

    override val testUrl: String = "https://www.amazon.com/dp/B08PP5MSVB"

    override suspend fun run() {
        page.enable()
        dom.enable()
        overlay.enable()

        overlay.onScreenshotRequested { screenshot: ScreenshotRequested ->
            val v = screenshot.viewport
            overlay.highlightRect(v.x.toInt(), v.y.toInt(), v.width.toInt(), v.height.toInt())
        }

        page.onFrameAttached {
            page.captureSnapshot()
            highlight("#productTitle")
        }

        page.navigate(testUrl)
    }

    private suspend fun highlight(selector: String) {
        val documentId = dom.getDocument().nodeId
        val nodeId = dom.querySelector(documentId, selector)
        val highlightConfig = HighlightConfig(
            showInfo = true,
            showRulers = true,
            showStyles = true,
            showExtensionLines = true,
            shapeColor = RGBA(255, 0, 0, 1.0)
        )

        overlay.highlightRect(300, 400, 500, 500)
//        Thread.sleep(5000)
        overlay.highlightNode(highlightConfig, nodeId, null, null, selector)
//        Thread.sleep(5000)
        val obj = overlay.getHighlightObjectForTest(nodeId)
        val json = prettyPulsarObjectMapper().writeValueAsString(obj)
        println(json)
    }
}

suspend fun main() {
    OverlayExample().use { it.run() }
}
