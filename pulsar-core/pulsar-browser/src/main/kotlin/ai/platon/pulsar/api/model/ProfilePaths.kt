package ai.platon.pulsar.api.model

import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.common.browser.BrowserFiles
import ai.platon.pulsar.common.browser.BrowserType
import ai.platon.pulsar.common.browser.fingerprint.Fingerprint
import java.nio.file.Path

object ProfilePaths {
    // The prefix for all temporary privacy contexts. System context, prototype context and default context are not
    // required to start with the prefix.
    const val CONTEXT_DIR_PREFIX = "cx."

    // An external key becomes a directory name, see requireLegalExternalKey.
    private const val EXTERNAL_KEY_MAX_LENGTH = 64
    private val EXTERNAL_KEY_PATTERN = Regex("[A-Za-z0-9._-]+")

    // NOTE: Chrome DevTools remote debugging requires a non-default data directory. Specify this using --user-data-dir.
    val SYSTEM_DEFAULT_BROWSER_CONTEXT_DIR_PLACEHOLDER: Path = AppPaths.SYSTEM_DEFAULT_BROWSER_CONTEXT_DIR_PLACEHOLDER

    // The default context directory, if you need a permanent and isolate context, use this one.
    // NOTE: the user-default context is not a default context.
    val DEFAULT_CONTEXT_DIR: Path = AppPaths.CONTEXT_DEFAULT_DIR

    // The prototype context directory, all privacy contexts copies browser data from the prototype.
    // A typical prototype data dir is: ~/.browser4/browser/chrome/prototype/google-chrome/
    val PROTOTYPE_DATA_DIR: Path = AppPaths.CHROME_DATA_DIR_PROTOTYPE
    // A context dir is the dir which contains the browser data dir, and supports different browsers.
    // For example: ~/.browser4/browser/chrome/prototype/
    val PROTOTYPE_CONTEXT_DIR: Path = AppPaths.CHROME_DATA_DIR_PROTOTYPE.parent

    // A random context directory, if you need a random temporary context, use this one
    val NEXT_SEQUENTIAL_CONTEXT_DIR: Path get() = BrowserFiles.computeNextSequentialContextDir()
    // A random context directory, if you need a random temporary context, use this one
    val RANDOM_TEMP_CONTEXT_DIR: Path get() = BrowserFiles.computeRandomTmpContextDir(browserType = BrowserType.PULSAR_CHROME)

    // The virtual context root of externally-attached browsers (CDP attach, browser extension
    // relay). Purely logical — never created on disk; the physical browser owns its own user
    // data dir on its own machine. Context dirs rooted here are classified as external
    // (ProfileId.isExternal) and must never be passed to a browser launcher.
    val EXTERNAL_CONTEXT_DIR: Path = AppPaths.CONTEXT_EXTERNAL_DIR

    /**
     * Compute the virtual context dir of an externally-attached browser.
     *
     * The dir name is `cx.ext.<externalKey>`: the key is used verbatim as the LAST path
     * segment, which is why it has to pass [requireLegalExternalKey]. The name is never
     * created on disk, but it is still parsed into a [Path] on every platform — an illegal
     * character is rejected by the platform's path parser right here, not at creation time.
     *
     * @param externalKey A stable, caller-chosen key that identifies the external browser
     * (e.g. a session id or a CDP endpoint). The same key always yields the same context
     * dir, hence the same BrowserId/profile identity across reconnects and restarts.
     * @throws IllegalArgumentException if the key cannot be a portable file name, see
     * [requireLegalExternalKey].
     * */
    fun externalContextDir(externalKey: String): Path {
        val safeKey = requireLegalExternalKey(externalKey)
        return EXTERNAL_CONTEXT_DIR.resolve("${CONTEXT_DIR_PREFIX}ext.$safeKey")
    }

    /**
     * Validate an external key, which is used verbatim as a directory name (`cx.ext.<key>`).
     *
     * The accepted grammar is deliberately the COMMON SUBSET of the Windows, macOS and Linux
     * file-name grammars, so a key accepted here can neither make a path a platform refuses to
     * parse nor make two distinct keys silently share one directory:
     *
     * - **ASCII letters, digits, `_`, `-`, `.` only.** `:` in particular is rejected: it is
     *   illegal in Windows file names (a `host:port` key throws `InvalidPathException` here,
     *   long before anything is created) and Win32 would read it as an NTFS alternate data
     *   stream. Non-ASCII is rejected as well: macOS normalizes HFS+/APFS names to NFD, so two
     *   spellings of one key would resolve to the same directory, and multi-byte characters eat
     *   into the OS name-length budget.
     * - **No trailing `.`.** Win32 strips trailing dots, so `key` and `key.` would alias.
     * - **At most [EXTERNAL_KEY_MAX_LENGTH] characters**, which keeps the composed name
     *   (`cx.ext.` + key) far below the 255-byte `NAME_MAX` of every supported file system.
     *
     * Case is not folded: on Windows and macOS `Session-1` and `session-1` denote the same
     * directory anyway. Callers that need an identity stable across platforms must pick keys
     * that are unique case-insensitively.
     *
     * Windows reserved device names (`CON`, `NUL`, `COM1`, ...) need no special case — the
     * composed name always starts with the `cx.ext.` prefix, so it can never be one of them.
     * */
    private fun requireLegalExternalKey(externalKey: String): String {
        require(externalKey.isNotBlank()) { "The external key must not be blank: '$externalKey'" }
        require(externalKey.length <= EXTERNAL_KEY_MAX_LENGTH) {
            "The external key is too long (>$EXTERNAL_KEY_MAX_LENGTH): '$externalKey'"
        }
        require(EXTERNAL_KEY_PATTERN.matches(externalKey)) {
            "The external key may contain only ASCII letters, digits, '_', '-' and '.' because it becomes " +
                "the directory name '${CONTEXT_DIR_PREFIX}ext.$externalKey', which must be a legal file name on " +
                "every OS; got '$externalKey'. Use '.' where a path separator is meant — e.g. " +
                "'attach.ws.127.0.0.1.9222' rather than 'attach.ws.127.0.0.1:9222'."
        }
        require(!externalKey.endsWith(".")) {
            "The external key must not end with '.', because Windows strips trailing dots and " +
                "'$externalKey' would then name the same directory as '${externalKey.dropLast(1)}': '$externalKey'"
        }
        return externalKey
    }

    fun createNextSequential(fingerprint: Fingerprint): Path {
        return BrowserFiles.computeNextSequentialContextDir(fingerprint = fingerprint)
    }

    fun createRandom(browserType: BrowserType): Path {
        return BrowserFiles.computeRandomTmpContextDir(browserType = browserType)
    }
}
