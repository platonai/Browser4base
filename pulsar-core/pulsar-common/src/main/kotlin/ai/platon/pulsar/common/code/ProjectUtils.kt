package ai.platon.pulsar.common.code

import ai.platon.pulsar.common.config.CapabilityTypes
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.notExists

/**
 * A utility class for project-related operations, such as locating the project root directory
 * or finding specific files within the project structure.
 *
 * ## Project root resolution strategy
 *
 * [findProjectRootDir] resolves the project root using a priority-ordered strategy:
 *
 * 1. **Explicit override** — system property or environment variable `project.base.dir`
 *    (see [CapabilityTypes.PROJECT_BASE_DIR_KEY]). This is the primary mechanism for
 *    consuming projects that use Browser4 as a library dependency.
 * 2. **JAR guard** — when running from a JAR with no explicit override, returns `null`
 *    since the project source tree is not accessible.
 * 3. **Beacon file walk-up** — walks up from the current working directory looking for
 *    a file named by [PROJECT_ROOT_BEACON_FILE_NAME] (default: `VERSION`).
 * 4. **Module deep-search** — walks down from the start directory to find a module
 *    directory named [PROJECT_BEACON_MODULE_NAME] (default: `pulsar-common`), then
 *    walks up from that module to find the project root.
 *
 * The beacon file name and module name are mutable [var]s so that forks or specialized
 * builds can customize them.
 */
object ProjectUtils {
    private val logger = LoggerFactory.getLogger(ProjectUtils::class.java)

    /** Name of the code-mirror resource subdirectory. */
    const val CODE_MIRROR_DIR = "code-mirror"

    /**
     * The file name used as a beacon to identify the project root directory.
     * Defaults to `VERSION`. Change this if your project uses a different marker file.
     */
    var PROJECT_ROOT_BEACON_FILE_NAME = "VERSION"

    /**
     * The module directory name used as a fallback beacon during deep search.
     * When the beacon-file walk-up fails, the method walks down from the start directory
     * looking for a directory whose name ends with this value.
     */
    var PROJECT_BEACON_MODULE_NAME = "pulsar-common"

    /**
     * Path relative to the project root where code-mirror resources are stored.
     * Used by [copySourceFileAsCodeResource].
     */
    var CODE_RESOURCE_DIR = "pulsar-core/pulsar-resources/src/main/resources/$CODE_MIRROR_DIR"

    /**
     * Checks whether the current class is loaded from a JAR (as opposed to a filesystem
     * classpath, e.g. `target/classes`). When running from a JAR the project source tree
     * is not accessible, so filesystem-based detection is skipped.
     */
    fun isInJar(): Boolean {
        val location = this::class.java.protectionDomain.codeSource.location
        return location.protocol == "jar" || location.path.endsWith(".jar")
    }

    /**
     * Resolves the project root directory from an explicit override.
     *
     * Checks, in order:
     * 1. System property [CapabilityTypes.PROJECT_BASE_DIR_KEY] (`project.base.dir`)
     * 2. Environment variable `project.base.dir`
     *
     * If the resolved path does not exist on disk, a warning is logged and this method
     * returns `null` so that automatic detection can take over.
     *
     * This is the escape hatch for projects that consume Browser4 as a library dependency:
     * set `-Dproject.base.dir=/path/to/your/project` (or the equivalent env var) and
     * [findProjectRootDir] will return that directory regardless of beacon files or JAR status.
     *
     * @return the explicitly configured project root, or `null` if not set or the path is invalid.
     * @see CapabilityTypes.PROJECT_BASE_DIR_KEY
     */
    private fun findProjectRootDirFromProperty(): Path? {
        val propertyValue = System.getProperty(CapabilityTypes.PROJECT_BASE_DIR_KEY)
            ?: System.getenv(CapabilityTypes.PROJECT_BASE_DIR_KEY)
            ?: return null

        val path = Paths.get(propertyValue).toAbsolutePath().normalize()
        if (!path.exists()) {
            logger.warn("Project root directory from property/env is set but does not exist: {}", path)
            return null
        }
        return path
    }

    /**
     * Finds the project root directory starting from the current working directory.
     *
     * See the [class-level KDoc][ProjectUtils] for the full resolution strategy.
     *
     * @return The project root directory if found, otherwise `null`.
     */
    fun findProjectRootDir(): Path? = findProjectRootDir(Paths.get(".").toAbsolutePath().normalize())

    /**
     * Finds the project root directory using a priority-ordered strategy:
     *
     * 1. **Explicit override** — system property / env var `project.base.dir`
     * 2. **JAR guard** — returns `null` when running from a JAR (no source tree accessible)
     * 3. **Beacon file walk-up** — walks up from [startDir] looking for [PROJECT_ROOT_BEACON_FILE_NAME]
     * 4. **Module deep-search** — when [deepSearch] is `true`, walks down from [startDir]
     *    to find a module directory matching [PROJECT_BEACON_MODULE_NAME], then walks up from there
     *
     * @param startDir   The directory to start the search from.
     * @param deepSearch Whether to attempt the module-based deep-search fallback.
     * @return The project root directory if found, otherwise `null`.
     */
    fun findProjectRootDir(startDir: Path, deepSearch: Boolean = true): Path? {
        // Priority 1: explicit system property / env var override
        val fromProperty = findProjectRootDirFromProperty()
        if (fromProperty != null) {
            return fromProperty
        }

        // Priority 2: automatic detection is not possible when running from a JAR
        if (isInJar()) {
            return null
        }

        // Priority 3: beacon-file walk-up
        var projectRootDir: Path? = startDir

        while (projectRootDir != null && projectRootDir.resolve(PROJECT_ROOT_BEACON_FILE_NAME).notExists()) {
            projectRootDir = projectRootDir.parent
        }

        // Priority 4: module deep-search fallback
        if (projectRootDir == null && deepSearch) {
            // The working directory may not be the project root, try to find the module directory first and then search for the project root.
            val moduleDir = Files.walk(startDir)
                .filter { it.fileName.toString().endsWith(PROJECT_BEACON_MODULE_NAME) }
                .findFirst().orElse(null)?.toAbsolutePath()

            if (moduleDir != null) {
                return findProjectRootDir(moduleDir, false)
            }
        }

        if (projectRootDir == null) {
            logger.warn("Project root directory not found. Please ensure you are running within a project structure containing a $PROJECT_ROOT_BEACON_FILE_NAME file.")
        }

        return projectRootDir
    }

    /**
     * Copies a source file into the code-mirror resource directory, appending `.txt` to
     * its file name. The destination is resolved as `<projectRoot>/[CODE_RESOURCE_DIR]/<filename>.txt`.
     *
     * @param source The source file to copy.
     * @return `true` if the copy succeeded, `false` if the project root could not be found.
     */
    fun copySourceFileAsCodeResource(source: Path): Boolean {
        val rootDir = findProjectRootDir() ?: return false
        val destPath = rootDir.resolve(CODE_RESOURCE_DIR)

        val filename = source.fileName.toString() + ".txt"
        val target = destPath.resolve(filename)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)

        return true
    }

    /**
     * Walks through the directory tree starting from the specified base directory to find
     * a file with the given name.
     *
     * Excludes any files located in `target`, `build`, and `test` directories to avoid
     * unnecessary processing of build artifacts.
     *
     * This method works only when running in an environment where the project structure
     * is accessible (i.e., not in a JAR environment).
     *
     * @param fileName     The name of the file to find.
     * @param baseDir      The directory to start the search from.
     * @param excludePaths Path fragments to exclude from the search (default: `/target/`, `/build/`, `/test/`).
     * @return The list of paths to the files that match the specified name.
     */
    fun walkToFindFiles(
        fileName: String, baseDir: Path,
        excludePaths: List<String> = listOf("/target/", "/build/", "/test/")
    ): List<Path> {
        return Files.walk(baseDir)
            .filter { it.fileName.toString() == fileName }
            .filter { path -> excludePaths.none { path.toString().contains(it) } }
            .toList()
    }

    /**
     * Finds the project root directory and then searches for a file with the specified
     * name within the entire project structure.
     *
     * Excludes `target`, `build`, and `test` directories.
     *
     * @param fileName The name of the file to find.
     * @return The list of matching file paths, or an empty list if the project root cannot be found.
     */
    fun findFiles(fileName: String): List<Path> {
        val projectRootDir = findProjectRootDir()
        return if (projectRootDir != null) {
            walkToFindFiles(fileName, projectRootDir)
        } else emptyList()
    }

    /**
     * Finds the project root directory, then locates the specified module within it,
     * and searches for a file with the given name inside that module.
     *
     * Excludes `target`, `build`, and `test` directories.
     *
     * @param moduleName The name of the module directory to search within.
     * @param fileName   The name of the file to find.
     * @return The list of matching file paths, or an empty list if the project root or module cannot be found.
     */
    fun findFiles(moduleName: String, fileName: String): List<Path> {
        val projectRootDir = findProjectRootDir() ?: return emptyList()
        val moduleRootDir = Files.walk(projectRootDir).filter { it.fileName.toString() == moduleName }.toList()
        return if (moduleRootDir.isNotEmpty()) {
            walkToFindFiles(fileName, moduleRootDir.first())
        } else emptyList()
    }
}
