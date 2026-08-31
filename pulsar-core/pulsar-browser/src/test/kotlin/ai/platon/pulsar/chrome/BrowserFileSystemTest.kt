package ai.platon.pulsar.chrome

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserFileSystemTest {

    private fun Path.touch() {
        parent.createDirectories()
        createFile()
    }

    private fun createPrototypeUserDataDir(root: Path): Path {
        val prototype = root.resolve("prototype/google-chrome")

        // inherited content
        val defaultProfile = prototype.resolve("Default")
        defaultProfile.resolve("Preferences").apply {
            touch()
            writeText("{\"foo\":\"bar\"}")
        }
        defaultProfile.resolve("History").touch()
        defaultProfile.resolve("Extensions/extension-1/manifest.json").apply {
            touch()
            writeText("{}")
        }
        prototype.resolve("Local State").touch()
        prototype.resolve("MEIPreload/preloaded_data.pb").touch()

        // regenerable top level entries
        prototype.resolve("BrowserMetrics/BrowserMetrics-ABC.pma").touch()
        listOf(
            "Crashpad", "ShaderCache", "GrShaderCache", "GPUPersistentCache", "component_crx_cache",
            "extensions_crx_cache", "optimization_guide_model_store", "RecoveryImproved"
        ).forEach { prototype.resolve("$it/entry.bin").touch() }

        // transient runtime markers
        listOf("SingletonLock", "SingletonSocket", "SingletonCookie", "DevToolsActivePort")
            .forEach { prototype.resolve(it).touch() }

        // regenerable entries inside the Default profile
        listOf(
            "Cache", "Code Cache", "GPUCache", "DawnWebGPUCache", "DawnGraphiteCache", "GrShaderCache",
            "shared_proto_db", "Download Service", "Service Worker", "Session Storage"
        ).forEach { defaultProfile.resolve("$it/entry.bin").touch() }

        return prototype
    }

    @Test
    fun testCopyPrototypeSkipsRegenerableAndTransientEntries() {
        val root = Files.createTempDirectory("browser4-bfs-test")
        try {
            val prototype = createPrototypeUserDataDir(root)
            val target = root.resolve("target/google-chrome")

            BrowserFileSystem(target).copyPrototypeUserDataDir(prototype, target)

            // inherited content is copied with content preserved
            assertTrue(target.resolve("Default").exists())
            assertTrue(target.resolve("Default/Preferences").exists())
            assertEquals("{\"foo\":\"bar\"}", Files.readString(target.resolve("Default/Preferences")))
            assertTrue(target.resolve("Default/History").exists())
            assertTrue(target.resolve("Default/Extensions/extension-1/manifest.json").exists())
            assertTrue(target.resolve("Local State").exists())
            assertTrue(target.resolve("MEIPreload/preloaded_data.pb").exists())

            // regenerable top level entries are skipped
            listOf(
                "BrowserMetrics", "Crashpad", "ShaderCache", "GrShaderCache", "GPUPersistentCache",
                "component_crx_cache", "extensions_crx_cache", "optimization_guide_model_store", "RecoveryImproved"
            ).forEach { name -> assertFalse(target.resolve(name).exists(), "Expected $name to be skipped") }

            // transient runtime markers are skipped
            listOf("SingletonLock", "SingletonSocket", "SingletonCookie", "DevToolsActivePort")
                .forEach { name -> assertFalse(target.resolve(name).exists(), "Expected $name to be skipped") }

            // regenerable entries inside Default are skipped
            listOf(
                "Cache", "Code Cache", "GPUCache", "DawnWebGPUCache", "DawnGraphiteCache", "GrShaderCache",
                "shared_proto_db", "Download Service", "Service Worker", "Session Storage"
            ).forEach { name -> assertFalse(target.resolve("Default/$name").exists(), "Expected Default/$name to be skipped") }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun testCopyPrototypeKeepsUnknownAndNestedEntries() {
        val root = Files.createTempDirectory("browser4-bfs-test")
        try {
            val prototype = root.resolve("prototype/google-chrome")
            val defaultProfile = prototype.resolve("Default")

            // an unknown top level dir must be copied (future Chrome dirs are never lost silently)
            prototype.resolve("UnknownNewDir/entry.bin").touch()
            // a regenerable-looking name outside Default must be copied
            prototype.resolve("OtherComponent/Cache/entry.bin").touch()
            // an unknown Default subdir must be copied
            defaultProfile.resolve("SomeUnknownDir/entry.bin").touch()
            defaultProfile.resolve("Preferences").touch()

            val target = root.resolve("target/google-chrome")
            BrowserFileSystem(target).copyPrototypeUserDataDir(prototype, target)

            assertTrue(target.resolve("UnknownNewDir/entry.bin").exists())
            assertTrue(target.resolve("OtherComponent/Cache/entry.bin").exists())
            assertTrue(target.resolve("Default/SomeUnknownDir/entry.bin").exists())
            assertTrue(target.resolve("Default/Preferences").exists())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun testCopyPrototypeSkipsSymbolicLinks() {
        val root = Files.createTempDirectory("browser4-bfs-test")
        val prototype = root.resolve("prototype/google-chrome")
        try {
            prototype.resolve("Default").createDirectories()
            prototype.resolve("Default/Preferences").touch()
            prototype.resolve("real-file.bin").touch()

            val linkedFile = prototype.resolve("linked-file.bin")
            val linkedDir = prototype.resolve("linked-dir")
            val symlinksCreated = runCatching {
                Files.createSymbolicLink(linkedFile, prototype.resolve("real-file.bin"))
                Files.createSymbolicLink(linkedDir, prototype.resolve("Default"))
            }.isSuccess

            // Windows without developer mode cannot create symlinks; skip the assertions in that case
            if (!symlinksCreated) {
                return
            }

            val target = root.resolve("target/google-chrome")
            BrowserFileSystem(target).copyPrototypeUserDataDir(prototype, target)

            assertFalse(target.resolve("linked-file.bin").exists())
            assertFalse(target.resolve("linked-dir").exists())
            assertTrue(target.resolve("Default/Preferences").exists())
        } finally {
            // delete the symlinks first, deleteRecursively cannot follow them
            runCatching { Files.deleteIfExists(prototype.resolve("linked-file.bin")) }
            runCatching { Files.deleteIfExists(prototype.resolve("linked-dir")) }
            root.toFile().deleteRecursively()
        }
    }
}
