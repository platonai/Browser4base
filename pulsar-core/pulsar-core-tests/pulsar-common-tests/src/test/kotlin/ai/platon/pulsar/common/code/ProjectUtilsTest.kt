package ai.platon.pulsar.common.code

import ai.platon.pulsar.common.code.ProjectUtils.isInJar
import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.common.printlnPro
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Tests for [ProjectUtils].
 *
 * Covers both automatic beacon-file detection (within the Browser4 project tree)
 * and explicit [CapabilityTypes.PROJECT_BASE_DIR_KEY] override (for library consumers).
 *
 * All tests are skipped when running from a JAR since the project source tree is
 * not accessible and the tests create temporary directory structures on disk.
 */
class ProjectUtilsTest {

    @BeforeEach
    fun setUp() {
        // Skip tests if running in a JAR environment
        Assumptions.assumeFalse(isInJar(), "Tests are skipped when running in a JAR environment")
    }

    /**
     * Clears [CapabilityTypes.PROJECT_BASE_DIR_KEY] between tests to prevent
     * cross-test contamination when a test sets the system property.
     */
    @AfterEach
    fun clearSystemProperty() {
        System.clearProperty(CapabilityTypes.PROJECT_BASE_DIR_KEY)
    }

    @Test
    fun testFindProjectRootDirFromCurrentDir() {
        // Assuming the current working directory is the project root
        val projectRootDir = ProjectUtils.findProjectRootDir()
        assertNotNull(projectRootDir)
        assertTrue(Files.exists(projectRootDir!!.resolve("VERSION")))
    }

    @Test
    fun testFindProjectRootDirFromStartDir(@TempDir tempDir: Path) {
        // Create a mock project structure
        val versionFile = tempDir.resolve("VERSION")
        Files.createFile(versionFile)

        val subdirectory = tempDir.resolve("subdirectory")
        Files.createDirectory(subdirectory)

        val projectRootDir = ProjectUtils.findProjectRootDir(subdirectory)
        assertNotNull(projectRootDir) { "Project root directory should be found" }
        assertEquals(tempDir, projectRootDir)
    }

    @Test
    fun testWalkToFindFiles(@TempDir tempDir: Path) {
        // Create a mock file
        val targetFile = tempDir.resolve("testFile.txt")
        Files.createFile(targetFile)

        val foundFiles = ProjectUtils.walkToFindFiles("testFile.txt", tempDir)
        assertEquals(foundFiles.size, 1)
        assertEquals(targetFile, foundFiles.first())
    }

    @Test
    fun testFindFiles() {
        printlnPro("Project root dir:")
        printlnPro(ProjectUtils.findProjectRootDir())
        printlnPro("Current dir:")
        printlnPro(Paths.get(".").toAbsolutePath().normalize())
        val foundFiles = ProjectUtils.findFiles("pulsar-core", "WebDriver.kt")
        assertEquals(foundFiles.size, 1)
        assertEquals("WebDriver.kt", foundFiles.first().fileName?.toString())
        assertEquals("PulsarSession.kt", ProjectUtils.findFiles("pulsar-core", "PulsarSession.kt").first().fileName?.toString())
    }

    @Test
    fun testFindFilesNotFound(@TempDir tempDir: Path) {
        // Create a mock project structure
        val versionFile = tempDir.resolve("VERSION")
        Files.createFile(versionFile)

        val foundFiles = ProjectUtils.findFiles("nonExistentFile.txt")
        assertTrue { foundFiles.isEmpty() }
    }

    /**
     * Verifies that setting the system property [CapabilityTypes.PROJECT_BASE_DIR_KEY]
     * causes [ProjectUtils.findProjectRootDir] to return that directory directly,
     * bypassing beacon-file detection.
     */
    @Test
    fun testFindProjectRootDirFromSystemProperty(@TempDir tempDir: Path) {
        val previous = System.getProperty(CapabilityTypes.PROJECT_BASE_DIR_KEY)
        try {
            System.setProperty(CapabilityTypes.PROJECT_BASE_DIR_KEY, tempDir.toString())
            val result = ProjectUtils.findProjectRootDir()
            assertNotNull(result)
            assertEquals(tempDir.toAbsolutePath().normalize(), result)
        } finally {
            if (previous != null) {
                System.setProperty(CapabilityTypes.PROJECT_BASE_DIR_KEY, previous)
            }
        }
    }

    /**
     * Verifies that the system property override takes precedence over beacon-file
     * detection. Even when the target directory has no `VERSION` beacon file,
     * the property value is returned — proving the override works for library consumers.
     */
    @Test
    fun testFindProjectRootDirFromPropertyTakesPrecedence(@TempDir tempDir: Path) {
        // tempDir does NOT contain a VERSION file — property must take precedence
        val previous = System.getProperty(CapabilityTypes.PROJECT_BASE_DIR_KEY)
        try {
            System.setProperty(CapabilityTypes.PROJECT_BASE_DIR_KEY, tempDir.toString())
            val result = ProjectUtils.findProjectRootDir()
            assertNotNull(result) { "Property override should return directory even without VERSION beacon" }
            assertEquals(tempDir.toAbsolutePath().normalize(), result)
        } finally {
            if (previous != null) {
                System.setProperty(CapabilityTypes.PROJECT_BASE_DIR_KEY, previous)
            }
        }
    }

    /**
     * Verifies graceful fallback when [CapabilityTypes.PROJECT_BASE_DIR_KEY] points to
     * a non-existent directory: the property is ignored, and beacon-file detection is used instead.
     */
    @Test
    fun testFindProjectRootDirIgnoresNonExistentProperty(@TempDir tempDir: Path) {
        // Create a VERSION file so beacon-file detection can find the root
        val versionFile = tempDir.resolve("VERSION")
        Files.createFile(versionFile)

        val previous = System.getProperty(CapabilityTypes.PROJECT_BASE_DIR_KEY)
        try {
            // Point property to a non-existent directory
            System.setProperty(CapabilityTypes.PROJECT_BASE_DIR_KEY,
                tempDir.resolve("non-existent").toString())

            // Should fall through to beacon-file detection
            val result = ProjectUtils.findProjectRootDir(tempDir)
            assertNotNull(result)
            assertEquals(tempDir, result)
        } finally {
            if (previous != null) {
                System.setProperty(CapabilityTypes.PROJECT_BASE_DIR_KEY, previous)
            }
        }
    }
}
