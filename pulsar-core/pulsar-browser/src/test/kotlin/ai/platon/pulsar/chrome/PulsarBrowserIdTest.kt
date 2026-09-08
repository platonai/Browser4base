package ai.platon.pulsar.chrome

import ai.platon.pulsar.api.BrowserId
import ai.platon.pulsar.api.model.ProfilePaths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PulsarBrowserIdTest {

    @Test
    @DisplayName("PulsarBrowser(port) carries a stable external identity keyed by the port")
    fun connectByPortGetsExternalIdentity() {
        val browser = PulsarBrowser(port = 9333)
        try {
            val id = browser.id
            assertTrue(id.isExternal, "The port-connect wrapper must have an external identity")
            assertEquals(BrowserId.external("attach.port.9333"), id)
            assertTrue(id.contextDir.startsWith(ProfilePaths.EXTERNAL_CONTEXT_DIR))
            assertTrue(!java.nio.file.Files.exists(id.contextDir), "No local data dir may ever exist for an external identity")
        } finally {
            browser.close()
        }
    }

    @Test
    @DisplayName("different ports get different external identities")
    fun differentPortsGetDifferentIdentities() {
        val browser1 = PulsarBrowser(port = 9333)
        val browser2 = PulsarBrowser(port = 9334)
        try {
            assertNotEquals(browser1.id, browser2.id)
        } finally {
            browser1.close()
            browser2.close()
        }
    }

    @Test
    @DisplayName("an explicit external id is honored by the port-connect constructor")
    fun explicitExternalIdIsHonored() {
        val explicitId = BrowserId.external("cdp-session-42")
        val browser = PulsarBrowser(port = 9335, id = explicitId)
        try {
            assertEquals(explicitId, browser.id)
            assertTrue(browser.id.isExternal)
        } finally {
            browser.close()
        }
    }
}
