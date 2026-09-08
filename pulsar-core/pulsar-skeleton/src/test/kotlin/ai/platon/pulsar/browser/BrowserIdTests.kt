package ai.platon.pulsar.api

import ai.platon.pulsar.api.model.ProfilePaths
import ai.platon.pulsar.browser.privacy.PrivacyContext
import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.common.browser.BrowserType
import ai.platon.pulsar.common.config.CapabilityTypes.BROWSER_PROFILE_MODE
import ai.platon.pulsar.common.config.CapabilityTypes.PRIVACY_AGENT_GENERATOR_CLASS
import ai.platon.pulsar.common.printlnPro
import java.nio.file.Files
import kotlin.test.*

class BrowserIdTests {
    private val contextPath = Files.createTempDirectory("test-")

    @AfterTest
    fun tearDown() {
        Files.deleteIfExists(contextPath)
    }

    @Test
    fun testBrowserComparison() {
        val id = BrowserId(contextPath, BrowserType.PULSAR_CHROME)
        val id2 = BrowserId(contextPath, BrowserType.PLAYWRIGHT_CHROME)
        assertNotEquals(id, id2)
        assertNotEquals(id.hashCode(), id2.hashCode())
        assertTrue("id.browserType.toString() > id2.browserType.toString()") { id.browserType.toString() > id2.browserType.toString() }
        assertTrue("id.browserType < id2.browserType") { id.browserType < id2.browserType }
        assertTrue("id.fingerprint < id2.fingerprint") { id.fingerprint > id2.fingerprint }
        assertTrue("id > id2") { id > id2 }
        assertTrue("contains") { id.toString().contains(contextPath.toString()) }
        assertTrue("startsWith") { id.userDataDir.startsWith(contextPath) }
    }

    @Test
    fun testPrototypeBrowserId() {
        val id = BrowserId.createPrototype()
        printlnPro(id)
        printlnPro(id.contextDir)
        printlnPro(id.userDataDir)
        assertTrue { id.userDataDir.toString().contains("google-chrome") }
        assertEquals(id.contextDir, PrivacyContext.PROTOTYPE_CONTEXT_DIR)
        assertEquals(id.userDataDir, PrivacyContext.PROTOTYPE_DATA_DIR)
        assertTrue { id.userDataDir.startsWith(PrivacyContext.PROTOTYPE_DATA_DIR) }
    }

    @Test
    fun testDefaultBrowserId() {
        val id = BrowserId.createDefault()
        printlnPro(id)
        printlnPro(id.contextDir)
        printlnPro(id.userDataDir)
        assertTrue { id.userDataDir.toString().contains("PULSAR_CHROME") }
        assertEquals(id.contextDir, PrivacyContext.DEFAULT_CONTEXT_DIR)
        assertEquals(id.userDataDir, PrivacyContext.DEFAULT_CONTEXT_DIR.resolve(BrowserType.PULSAR_CHROME.name))
        assertTrue { id.userDataDir.startsWith(PrivacyContext.DEFAULT_CONTEXT_DIR.resolve(BrowserType.PULSAR_CHROME.name)) }
    }

    @Test
    fun testSystemDefaultBrowserId() {
        val id = BrowserId.createSystemDefault()
        printlnPro(id)
        printlnPro(id.contextDir)
        printlnPro(id.userDataDir)
        assertFalse { id.userDataDir.toString().contains("PULSAR_CHROME") }
        assertEquals(id.contextDir, AppPaths.SYSTEM_DEFAULT_BROWSER_CONTEXT_DIR_PLACEHOLDER)
        assertTrue { id.userDataDir.startsWith(AppPaths.SYSTEM_DEFAULT_BROWSER_CONTEXT_DIR_PLACEHOLDER) }
        assertEquals(id.userDataDir, AppPaths.SYSTEM_DEFAULT_BROWSER_DATA_DIR_PLACEHOLDER)
    }

    @Test
    fun testNextSequentialBrowserId() {
        IntRange(1, 20).forEach { i ->
            val id = BrowserId.createNextSequential()
            val expectedContextBaseDir = AppPaths.CONTEXT_GROUP_BASE_DIR
            printlnPro("\nRound $i")
            printlnPro("Browser Id: $id")
            printlnPro("contextDir: " + id.contextDir)
            printlnPro("userDataDir: " + id.userDataDir)
            assertTrue("Actual: ${id.userDataDir}") { id.userDataDir.toString().contains("PULSAR_CHROME") }
            assertTrue("$expectedContextBaseDir <- ${id.userDataDir}") {
                id.userDataDir.startsWith(expectedContextBaseDir)
            }
        }
    }

    // ------------------------------------------------------------------
    // External (attached) browser identities
    // ------------------------------------------------------------------

    @Test
    fun testExternalBrowserIdIsDeterministic() {
        val id1 = BrowserId.external("session-1")
        val id2 = BrowserId.external("session-1")

        assertEquals(id1, id2)
        assertEquals(id1.hashCode(), id2.hashCode())
        assertEquals(0, id1.compareTo(id2))
        assertEquals(id1.contextDir, id2.contextDir)
    }

    @Test
    fun testExternalBrowserIdIsDistinctPerKey() {
        val id1 = BrowserId.external("session-1")
        val id2 = BrowserId.external("session-2")

        assertNotEquals(id1, id2)
        assertTrue { id1.contextDir != id2.contextDir }
    }

    @Test
    fun testExternalBrowserIdIsClassifiedExternal() {
        val id = BrowserId.external("session-1")

        assertTrue { id.isExternal }
        assertTrue { id.profile.isExternal }
        assertTrue { id.profile.id.isExternal }
        assertFalse { id.profile.isSystemDefault }
        assertFalse { id.profile.isDefault }
        assertFalse { id.profile.isPrototype }
        assertFalse { id.profile.isGroup }
        assertFalse { id.profile.isTemporary }
        assertFalse { id.profile.isPermanent }
    }

    @Test
    fun testExternalBrowserIdNeverTouchesDisk() {
        val id = BrowserId.external("session-no-disk")

        // The context dir of an external browser is purely virtual: nothing may be created.
        assertFalse { Files.exists(id.contextDir) }
        assertFalse { Files.exists(id.userDataDir) }
        assertFalse { Files.exists(id.profile.contextDir) }
    }

    @Test
    fun testExternalBrowserIdDoesNotMutateRuntimeConfiguration() {
        val profileModeBefore = System.getProperty(BROWSER_PROFILE_MODE)
        val generatorClassBefore = System.getProperty(PRIVACY_AGENT_GENERATOR_CLASS)

        val id = BrowserId.external("session-pure")
        printlnPro(id)

        assertEquals(profileModeBefore, System.getProperty(BROWSER_PROFILE_MODE))
        assertEquals(generatorClassBefore, System.getProperty(PRIVACY_AGENT_GENERATOR_CLASS))
    }

    @Test
    fun testExternalBrowserIdDisplay() {
        val id = BrowserId.external("session-abc")

        assertTrue { id.display.contains("ext.session-abc") }
        assertTrue { id.contextDir.startsWith(ProfilePaths.EXTERNAL_CONTEXT_DIR) }
        assertTrue { id.contextDir.last().toString().startsWith(ProfilePaths.CONTEXT_DIR_PREFIX) }
    }

    @Test
    fun testExternalBrowserIdCarriesBrowserType() {
        assertEquals(BrowserType.PULSAR_CHROME, BrowserId.external("s1").browserType)
        assertEquals(BrowserType.PLAYWRIGHT_CHROME, BrowserId.external("s1", BrowserType.PLAYWRIGHT_CHROME).browserType)
        // The same key with different browser types are different identities.
        assertNotEquals(
            BrowserId.external("s1", BrowserType.PULSAR_CHROME),
            BrowserId.external("s1", BrowserType.PLAYWRIGHT_CHROME)
        )
    }

    @Test
    fun testExternalBrowserIdRejectsInvalidKeys() {
        assertFailsWith<IllegalArgumentException> { BrowserId.external("") }
        assertFailsWith<IllegalArgumentException> { BrowserId.external("  ") }
        assertFailsWith<IllegalArgumentException> { BrowserId.external("a/b") }
        assertFailsWith<IllegalArgumentException> { BrowserId.external("a\\b") }
        assertFailsWith<IllegalArgumentException> { BrowserId.external("a b") }
        assertFailsWith<IllegalArgumentException> { BrowserId.external("x".repeat(65)) }
    }

    @Test
    fun testExternalBrowserIdProfileIsBuiltWithoutFingerprintFile() {
        // BrowserProfile.create() loads/generates a fingerprint file in the context dir.
        // External profiles must skip that step entirely.
        val id = BrowserId.external("session-no-fingerprint")

        assertFalse { Files.exists(id.profile.contextDir.resolve("fingerprint.json")) }
        assertEquals(BrowserType.PULSAR_CHROME, id.profile.fingerprint.browserType)
    }

    @Test
    fun testExternalProfileFactoryProducesEqualIds() {
        // A BrowserProfile built for an external key must keep the identity stable as well.
        val profile = BrowserProfile(ProfilePaths.externalContextDir("session-p1"), BrowserType.PULSAR_CHROME)
        val id = BrowserId(profile)

        assertEquals(BrowserId.external("session-p1"), id)
    }
}
