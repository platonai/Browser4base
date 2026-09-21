package ai.platon.pulsar.api.model

import ai.platon.pulsar.common.browser.Browsers
import org.apache.commons.lang3.SystemUtils
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Builds a "reduced" desktop User-Agent that matches the installed Chrome version.
 *
 * Headless Chrome advertises itself as `HeadlessChrome/<version>`. That token is a deterministic
 * one-line bot verdict, and it contradicts the Client Hints (`Sec-CH-UA*`) the very same browser
 * sends, because those report `Google Chrome`. Measured against detector suites, the token is
 * what fails "User Agent (Old)" / `HEADCHR_UA` checks and gets search engines to refuse or
 * poison results.
 *
 * A launch-time `--user-agent` switch is the only mechanism that reaches **every** JavaScript
 * scope of a session — page, iframes, dedicated workers, shared workers and service workers —
 * while leaving `Sec-CH-UA*` intact. A page-world (`Page.addScriptToEvaluateOnNewDocument`)
 * patch reaches only the page world, and `Emulation.setUserAgentOverride` silently misses
 * shared/service worker scopes and wipes the Client Hints unless `userAgentMetadata` is supplied.
 *
 * Chrome's reduced User-Agent is always `<major>.0.0.0`, so only the major version is needed, and
 * that can be derived *before* launch from the installed browser. See issue #11 section 1.
 */
object ReducedUserAgent {
    /** Chromium's reduced user agent freezes the build components to zeros. */
    const val REDUCED_VERSION_SUFFIX = ".0.0.0"

    private val VERSION_DIR_PATTERN = Regex("""^\d+(\.\d+){2,3}$""")

    /**
     * The major version of the installed Chrome, or `null` when it cannot be determined.
     *
     * @param binary the Chrome binary; defaults to the one [Browsers] resolves
     */
    fun chromeMajorVersion(binary: Path? = Browsers.searchChromeBinaryOrNull()): Int? {
        if (binary == null) return null

        return majorVersionFromInstallLayout(binary) ?: majorVersionFromProcess(binary)
    }

    /**
     * Derives the major version from Chrome's install layout.
     *
     * Chrome keeps the real binary in a version-named directory next to the launcher stub, e.g.
     * `…/Google/Chrome/Application/153.0.8010.52/chrome.exe`, so the version is a sibling
     * directory of the binary on Windows and Linux. Returns `null` when no such directory exists.
     */
    fun majorVersionFromInstallLayout(binary: Path): Int? {
        val dir = binary.parent ?: return null
        if (!Files.isDirectory(dir)) return null

        return runCatching {
            val versionDirs = Files.newDirectoryStream(dir).use { stream ->
                stream.filter { Files.isDirectory(it) }
                    .map { it.fileName.toString() }
                    .filter { VERSION_DIR_PATTERN.matches(it) }
            }
            versionDirs.mapNotNull { it.substringBefore('.').toIntOrNull() }.maxOrNull()
        }.getOrNull()
    }

    /**
     * Derives the major version by running `<binary> --version`, which prints e.g.
     * `Google Chrome 153.0.8010.52`. Returns `null` on any failure (missing binary, timeout,
     * unexpected output), so callers can fall back to a plain user agent.
     */
    fun majorVersionFromProcess(binary: Path, timeoutMillis: Long = 5_000): Int? = runCatching {
        val process = ProcessBuilder(binary.toString(), "--version")
            .redirectErrorStream(true)
            .start()

        try {
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                return null
            }
            parseMajorVersion(output)
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }.getOrNull()

    /**
     * Extracts the major version from any string containing a Chrome version, e.g.
     * `Google Chrome 153.0.8010.52` or `HeadlessChrome/153.0.0.0` → `153`.
     */
    fun parseMajorVersion(text: String): Int? =
        Regex("""(\d+)\.\d+\.\d+\.\d+""").find(text)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Builds the reduced user agent for [chromeMajor] on the host platform.
     */
    fun build(chromeMajor: Int, platform: Platform = Platform.current()): String =
        "${platform.userAgentPrefix} Chrome/$chromeMajor$REDUCED_VERSION_SUFFIX Safari/537.36"

    /**
     * The best reduced user agent for the installed Chrome, or `null` when the version is
     * unknown and no [configuredUserAgent] was supplied.
     */
    fun buildOrNull(configuredUserAgent: String? = null, binary: Path? = null): String? {
        configuredUserAgent?.trim()?.takeIf { it.isNotEmpty() }?.let { return reduce(it) }

        val major = chromeMajorVersion(binary) ?: return null
        return build(major)
    }

    /**
     * Replaces the `Headless` token in [userAgent] with a plain `Chrome` token, so a user agent
     * observed from a running headless browser can be made consistent with its Client Hints.
     *
     * The version is left as-is: `Chrome/153.0.0.0` is already the reduced form, and a full
     * `Chrome/153.0.8010.52` would not match what a real browser reports either.
     */
    fun reduce(userAgent: String): String {
        val trimmed = userAgent.trim()
        if (trimmed.isEmpty()) return trimmed
        return trimmed
            .replace("HeadlessChrome/", "Chrome/")
            .replace("Headless ", "")
    }

    /**
     * Whether [userAgent] still advertises the headless token, i.e. the stealth fix did not apply.
     */
    fun isHeadless(userAgent: String?): Boolean =
        userAgent?.contains("Headless", ignoreCase = true) == true

    /**
     * The platform-specific prefix of a desktop Chrome user agent.
     */
    enum class Platform(val userAgentPrefix: String) {
        WINDOWS("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"),
        MAC("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)"),
        LINUX("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko)"),
        UNKNOWN("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko)");

        companion object {
            fun current(): Platform = when {
                SystemUtils.IS_OS_WINDOWS -> WINDOWS
                SystemUtils.IS_OS_MAC -> MAC
                SystemUtils.IS_OS_LINUX -> LINUX
                else -> UNKNOWN
            }
        }
    }
}
