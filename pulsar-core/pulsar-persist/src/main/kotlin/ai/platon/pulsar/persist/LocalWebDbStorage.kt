package ai.platon.pulsar.persist

import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.persist.model.WebPageRecord
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.concurrent.ConcurrentHashMap

/**
 * Local filesystem storage backend for [WebPageRecord] persistence.
 *
 * File organization:
 * - Each page gets two files under `{storageDir}/{shard}/`:
 *   - `{md5hex}.wpm` — Avro container file with page metadata (content field is null)
 *   - `{md5hex}.wpc` — Raw bytes of the page content (only if content is non-null)
 * - Sharding: first 2 chars of the MD5 hex digest (256 subdirectories)
 *
 * Thread safety: per-file [synchronized] blocks via a [ConcurrentHashMap] of lock monitors.
 */
class LocalWebDbStorage(private val conf: ImmutableConfig) : WebDbStorage {

    companion object {
        private const val METADATA_SUFFIX = ".wpm"
        private const val CONTENT_SUFFIX = ".wpc"
        private const val TEMP_SUFFIX = ".tmp"
        private const val CONFIG_KEY_STORAGE_DIR = "storage.local.dir"
    }

    private val logger = LoggerFactory.getLogger(LocalWebDbStorage::class.java)

    /** Resolved storage directory, created on first access. */
    private val storageDir: Path = resolveStorageDir()

    /** Per-file lock monitors for thread-safe I/O. Keyed by absolute path string. */
    private val lockMap = ConcurrentHashMap<String, Any>()

    // --- Public API ---

    /**
     * Reads the [WebPageRecord] for the given key.
     *
     * @param key the reversed-url key identifying the page
     * @return the deserialized record, or null if the page does not exist
     */
    override fun read(key: String): WebPageRecord? {
        val path = metadataPath(key)
        val lock = fileLock(path)
        return synchronized(lock) {
            if (!Files.exists(path)) {
                null
            } else {
                try {
                    WebPageAvroSerDe.read(path.toFile())
                } catch (e: Exception) {
                    logger.error("Failed to read page from {}", path, e)
                    null
                }
            }
        }
    }

    /**
     * Reads the raw content bytes for the given key.
     *
     * @param key the reversed-url key identifying the page
     * @return the content bytes wrapped in a [ByteBuffer], or null if no content file exists
     */
    override fun readContent(key: String): ByteBuffer? {
        val contentFile = contentPath(key)
        val lock = fileLock(contentFile)
        return synchronized(lock) {
            if (!Files.exists(contentFile)) {
                null
            } else {
                try {
                    ByteBuffer.wrap(Files.readAllBytes(contentFile))
                } catch (e: Exception) {
                    logger.error("Failed to read content from {}", contentFile, e)
                    null
                }
            }
        }
    }

    /**
     * Writes a [WebPageRecord] to storage.
     *
     * @param record the record to persist (content field should be non-null if page has content)
     * @param key the reversed-url key identifying the page
     * @param replaceIfExists if false and the page already exists, no write occurs
     * @return true if the write succeeded, false if [replaceIfExists] is false and the page already exists
     */
    override fun write(record: WebPageRecord, key: String, replaceIfExists: Boolean): Boolean {
        val metaPath = metadataPath(key)
        val lock = fileLock(metaPath)

        return synchronized(lock) {
            if (!replaceIfExists && Files.exists(metaPath)) {
                logger.debug("Page already exists and replaceIfExists is false: {}", key)
                false
            } else {
                doWrite(record, key, metaPath)
            }
        }
    }

    /**
     * Checks whether a page exists for the given key.
     */
    override fun exists(key: String): Boolean {
        return Files.exists(metadataPath(key))
    }

    /**
     * Deletes both the metadata and content files for the given key.
     *
     * @return true if the page existed and was deleted
     */
    override fun delete(key: String): Boolean {
        val metaPath = metadataPath(key)
        val contentPath = contentPath(key)
        val lock = fileLock(metaPath)

        return synchronized(lock) {
            val metaExisted = Files.deleteIfExists(metaPath)
            Files.deleteIfExists(contentPath)
            metaExisted
        }
    }

    /**
     * Recursively deletes all files in the storage directory.
     *
     * @return true if the operation completed without errors
     */
    override fun truncate(): Boolean {
        return try {
            if (Files.exists(storageDir)) {
                Files.walk(storageDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            }
            Files.createDirectories(storageDir)
            logger.info("Storage truncated: {}", storageDir)
            true
        } catch (e: Exception) {
            logger.error("Failed to truncate storage: {}", storageDir, e)
            false
        }
    }

    // --- Private implementation ---

    /**
     * Performs the actual write of metadata (Avro) and content (raw bytes) to disk.
     * Called from [write] while holding the per-key lock.
     */
    private fun doWrite(record: WebPageRecord, key: String, metaPath: Path): Boolean {
        return try {
            // Ensure shard directory exists
            Files.createDirectories(metaPath.parent)

            // Extract and null out content so it's not serialized in the Avro file
            val content = record.content
            if (content != null) {
                record.content = null
            }

            // Write Avro metadata atomically via temp file
            val tmpMetaPath = metaPath.resolveSibling("${metaPath.fileName}$TEMP_SUFFIX")
            try {
                WebPageAvroSerDe.write(record, tmpMetaPath.toFile())
                Files.move(tmpMetaPath, metaPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                // Clean up temp file if it still exists (e.g. write succeeded but move failed)
                Files.deleteIfExists(tmpMetaPath)
                // Restore content reference in the record
                record.content = content
            }

            // Write content file (or delete if no content)
            val contentPath = contentPath(key)
            if (content != null) {
                // Copy the buffer to a fresh array for safe writing
                val bytes = ByteArray(content.remaining())
                val pos = content.position()
                content.get(bytes)
                content.position(pos)

                val tmpContentPath = contentPath.resolveSibling("${contentPath.fileName}$TEMP_SUFFIX")
                try {
                    Files.write(tmpContentPath, bytes)
                    Files.move(tmpContentPath, contentPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } finally {
                    Files.deleteIfExists(tmpContentPath)
                }
            } else {
                // Remove stale content file if content is now null
                Files.deleteIfExists(contentPath)
            }

            logger.debug("Written page: {}", key)
            true
        } catch (e: Exception) {
            logger.error("Failed to write page: {}", key, e)
            throw WebDBException("Failed to write page: $key", e)
        }
    }

    // --- Path resolution ---

    /**
     * Resolves the metadata (.wpm) path for a given key.
     * Format: `{storageDir}/{shard}/{md5hex}.wpm`
     */
    private fun metadataPath(key: String): Path {
        val hex = AppPaths.md5Hex(key)
        val shard = hex.substring(0, 2)
        return storageDir.resolve(shard).resolve("$hex$METADATA_SUFFIX")
    }

    /**
     * Resolves the content (.wpc) path for a given key.
     * Format: `{storageDir}/{shard}/{md5hex}.wpc`
     */
    private fun contentPath(key: String): Path {
        val hex = AppPaths.md5Hex(key)
        val shard = hex.substring(0, 2)
        return storageDir.resolve(shard).resolve("$hex$CONTENT_SUFFIX")
    }

    // --- Lock management ---

    /**
     * Returns (and caches) a lock monitor for the given path.
     * All I/O for the same file is synchronized on the same monitor.
     */
    private fun fileLock(path: Path): Any {
        return lockMap.computeIfAbsent(path.toAbsolutePath().toString()) { Any() }
    }

    // --- Storage directory resolution ---

    private fun resolveStorageDir(): Path {
        val customDir = conf.get(CONFIG_KEY_STORAGE_DIR)
        val dir = if (customDir != null) {
            Path.of(customDir)
        } else {
            AppPaths.LOCAL_STORAGE_DIR
        }
        Files.createDirectories(dir)
        logger.info("WebDb storage directory: {}", dir.toAbsolutePath())
        return dir
    }
}
