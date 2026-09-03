package ai.platon.pulsar.chrome

import ai.platon.pulsar.WebDriverTestBase
import ai.platon.pulsar.api.WebDriver
import ai.platon.pulsar.api.model.FrameInfo
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Frame-tree invariants and frame switching on REAL public web pages that embed
 * iframes. These tests are intentionally tolerant of site changes: they assert
 * structural invariants of [ai.platon.pulsar.api.WebDriver.frameList] (tree
 * order, active marking, parent linkage) and that switching into a real child
 * frame works, but they do not depend on any specific element on those sites.
 *
 * Pages under test:
 * - https://www.w3schools.com/tryit/tryit.asp?filename=tryhtml_iframe (live code editor embedding its result in a same-origin iframe)
 * - https://www.w3schools.com/html/html_iframe.asp (tutorial page embedding several iframes)
 */
@Tag("Slow")
@Tag("E2ETest")
@DisplayName("Frame switching against real websites")
open class FrameSwitchRealSitesE2ETest : WebDriverTestBase() {

    /**
     * Asserts the structural invariants of the frame tree and returns it:
     * main frame first & active, exactly one active frame, depth-first order,
     * and consistent parent linkage.
     */
    private suspend fun assertFrameTreeInvariants(driver: WebDriver): List<FrameInfo> {
        val frames = driver.frameList()

        assertTrue(frames.isNotEmpty())
        val main = frames.first()
        assertTrue(main.isMainFrame, "first frame must be the main frame")
        assertNull(main.parentId)
        assertEquals(0, main.depth)
        assertTrue(main.active, "main frame must be active by default")
        assertEquals(1, frames.count { it.active }, "exactly one frame must be active")

        val indexById = frames.mapIndexed { index, frame -> frame.id to index }.toMap()
        frames.filter { !it.isMainFrame }.forEach { child ->
            val parentIndex = indexById[child.parentId]
            assertNotNull(parentIndex, "parent ${child.parentId} of frame ${child.id} must be listed before it")
            assertTrue(indexById.getValue(child.id) > parentIndex, "frame ${child.id} must be listed after its parent")
            assertEquals(frames[parentIndex].depth + 1, child.depth, "depth of ${child.id}")
        }
        return frames
    }

    /** Skips the test when the page does not embed any iframe. */
    private fun assumeHasChildFrames(frames: List<FrameInfo>) {
        assumeTrue(frames.size > 1, "the page has no child frames: ${frames.map { it.url }}")
    }

    @Test
    @DisplayName("frame tree invariants and switching on the w3schools tryit iframe editor")
    fun frameSwitchingOnW3SchoolsTryitEditor() = runEnhancedWebDriverTest(
        "https://www.w3schools.com/tryit/tryit.asp?filename=tryhtml_iframe"
    ) { driver ->
        val frames = assertFrameTreeInvariants(driver)
        assumeHasChildFrames(frames)

        val child = frames.first { !it.isMainFrame }

        // switch by exact frame id
        val switched = driver.frameSwitch(child.id)
        assertEquals(child.id, switched.id)
        assertTrue(switched.active)
        assertEquals(1, driver.frameList().count { it.active })

        // switch by url substring when available (tolerant: the result iframe may
        // be srcdoc/about:blank without a usable url)
        driver.frameMain()
        val byUrl = child.url.ifBlank { null }?.let { url ->
            runCatching { driver.frameSwitch(url.take(80)) }.getOrNull()
        }
        if (byUrl != null) {
            assertEquals(child.id, byUrl.id)
        }
        driver.frameMain()

        // back on the main frame
        val listed = driver.frameList()
        assertEquals(1, listed.count { it.active })
        assertTrue(listed.first { it.active }.isMainFrame)
    }

    @Test
    @DisplayName("frame tree invariants and switching on the w3schools iframe tutorial")
    fun frameSwitchingOnW3SchoolsIframeTutorial() = runEnhancedWebDriverTest(
        "https://www.w3schools.com/html/html_iframe.asp"
    ) { driver ->
        val frames = assertFrameTreeInvariants(driver)
        assumeHasChildFrames(frames)

        val child = frames.first { !it.isMainFrame }
        val switched = driver.frameSwitch(child.id)
        assertEquals(child.id, switched.id)
        assertTrue(switched.active)
        assertEquals(1, driver.frameList().count { it.active })

        // switching back out works even when the child frame is cross-origin/unreachable
        driver.frameMain()
        val listed = driver.frameList()
        assertEquals(1, listed.count { it.active })
        assertTrue(listed.first { it.active }.isMainFrame)
    }
}
