package ai.platon.pulsar.common.config

import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.code.ProjectUtils
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import kotlin.io.path.notExists
import kotlin.io.path.reader

/**
 * Loads properties from standard locations on the local filesystem.
 *
 * Properties are loaded from:
 * 1. The working directory (`./`) and `./config/` subdirectory
 * 2. The project root directory and its `config/` subdirectory
 * 3. The `${PULSAR_DATA_HOME}/config/conf-enabled` directory
 *
 * Within each directory, `application.properties` and `application-private.properties`
 * are loaded if present (consistent with Spring Boot conventions).
 */
class LocalResourceProperties(
    private val loadDefaults: Boolean,
) {
    private val logger = getLogger(this)

    val properties = Properties()

    private val loadedPropertiesFiles = mutableSetOf<Path>()

    @Synchronized
    fun load() {
        if (!loadDefaults) {
            return
        }

        // Search for properties files in the ${project.baseDir} and ${project.baseDir}/config,
        // keep consistent with Spring's behavior, so even when we are not running a full
        // Spring Boot application (e.g., CLI tool, unit test, or native launch),
        // we can still load properties from these locations.
        // https://github.com/platonai/browser4/issues/110
        val pwd = Paths.get(".")
        val projectRoot = ProjectUtils.findProjectRootDir()
        listOfNotNull(pwd, projectRoot).forEach {
            loadExternalProperties(it)
            loadExternalProperties(it.resolve("config"))
        }

        if (Files.isDirectory(AppPaths.CONFIG_ENABLED_DIR)) {
            loadExternalProperties(AppPaths.CONFIG_ENABLED_DIR)
        }
    }

    fun loadExternalProperties(baseDir: Path) {
        if (baseDir.notExists()) {
            return
        }

        val externalResources = listOf(
            baseDir.resolve("application.properties"),
            baseDir.resolve("application-private.properties"),
        )
        externalResources.forEach { loadFromPropertyFile(it) }
    }

    private fun loadFromPropertyFile(path: Path) {
        if (loadedPropertiesFiles.contains(path)) {
            return
        }

        if (path.notExists()) {
            return
        }

        logger.info("Loading properties: {}", path)
        try {
            properties.load(path.reader())
            loadedPropertiesFiles.add(path)
        } catch (_: IOException) {
            logger.warn("Failed to load properties | {}", path)
        }
    }

    operator fun get(name: String): Any? = properties[name]

    /**
     * Sets a property value. Enforces use of strings for property keys and values.
     *
     * @param name  the property name
     * @param value the property value
     * @return the previous value of the specified key, or `null` if it did not have one
     */
    operator fun set(name: String, value: String): Any? {
        return properties.setProperty(name, value)
    }

    fun remove(name: String) {
        properties.remove(name)
    }

    fun size() = properties.size

    override fun toString(): String {
        return loadedPropertiesFiles.joinToString(", ", "[", "]") {
            it.fileName.toString()
        }
    }
}
