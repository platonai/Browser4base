package ai.platon.pulsar.api.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * An external key is used verbatim as the last segment of the virtual context dir name
 * (`cx.ext.<key>`), so it has to be a legal file name on every OS. Accepting a `host:port`
 * key used to blow up inside the platform path parser on Windows (`InvalidPathException:
 * Illegal char <:>`) while passing silently on Linux — the validator has to reject it
 * everywhere, before the key ever reaches a [java.nio.file.Path].
 * */
@Tag("Unit")
@Tag("Fast")
@DisplayName("an external key is accepted as a portable file name or rejected loudly")
class ProfilePathsTest {

    private val legalKeys = listOf(
        "session-1",
        "attach.port.9333",
        "attach.ws.127.0.0.1.9222",
        "attach.ws.--1.9222",       // the flattened IPv6 loopback host
        "cdp-session-42",
        "A_b-c.d",
        "9",
        ".leading-dot",             // only a TRAILING dot aliases on Win32
        "a..b",
        "x".repeat(64)              // exactly the limit
    )

    private val illegalKeys = listOf(
        "" to "blank",
        "   " to "blank",
        "a/b" to "a path separator",
        "a\\b" to "a path separator",
        "../escape" to "a path traversal attempt",
        "..\\escape" to "a path traversal attempt",
        "a b" to "whitespace",
        "attach.ws.127.0.0.1:9222" to "the ':' of the host:port form",
        "a:b" to "':' (an NTFS alternate data stream on Windows)",
        "a?b" to "a Windows-illegal character",
        "a*b" to "a Windows-illegal character",
        "a\"b" to "a Windows-illegal character",
        "a<b" to "a Windows-illegal character",
        "a>b" to "a Windows-illegal character",
        "a|b" to "a Windows-illegal character",
        "a\tb" to "a control character",
        "a\nb" to "a control character",
        "a\u0000b" to "a control character",
        "sessie-één" to "a non-ASCII character (macOS normalizes NFD, so spellings would alias)",
        "会话-1" to "a non-ASCII character",
        "key." to "a trailing dot (Win32 strips it)",
        "key.." to "a trailing dot (Win32 strips it)",
        "." to "a path component",
        ".." to "a path component",
        "x".repeat(65) to "more than the length limit"
    )

    @Test
    @DisplayName("every legal key becomes the last segment of the virtual context dir")
    fun legalKeysBecomeTheLastSegmentOfTheContextDir() {
        for (key in legalKeys) {
            val dir = ProfilePaths.externalContextDir(key)
            assertEquals("${ProfilePaths.CONTEXT_DIR_PREFIX}ext.$key", dir.fileName.toString(), "key: '$key'")
            assertEquals(ProfilePaths.EXTERNAL_CONTEXT_DIR, dir.parent, "key: '$key'")
        }
    }

    @Test
    @DisplayName("the composed dir name is a file name every OS accepts")
    fun theComposedDirNameIsALegalFileNameOnEveryOs() {
        val portableFileName = Regex("[A-Za-z0-9._-]+")

        for (key in legalKeys) {
            val name = ProfilePaths.externalContextDir(key).fileName.toString()
            assertTrue(portableFileName.matches(name), "'$name' must stay inside the portable file-name grammar")
            assertTrue(
                name.startsWith("${ProfilePaths.CONTEXT_DIR_PREFIX}ext."),
                "'$name' must keep the prefix: it is what rules out traversal and Windows device names"
            )
            assertFalse(name.endsWith("."), "'$name' must not end with a dot: Win32 strips it")
            assertFalse(name.endsWith(" "), "'$name' must not end with a space: Win32 strips it")
            assertTrue(name.length <= 255, "'$name' must fit NAME_MAX")
            assertTrue(name.all { it.code < 128 }, "'$name' must stay ASCII: macOS normalizes non-ASCII names")
        }
    }

    @Test
    @DisplayName("an illegal key is rejected with the offending value in the message")
    fun illegalKeysAreRejectedWithTheOffendingValue() {
        for ((key, why) in illegalKeys) {
            val failure = assertThrows<IllegalArgumentException>("$why must be rejected: '$key'") {
                ProfilePaths.externalContextDir(key)
            }
            assertTrue(failure.message?.contains(key) == true, "the message must name the key '$key'")
        }
    }

    @Test
    @DisplayName("a host:port key is rejected here instead of inside the platform path parser")
    fun aHostPortKeyIsRejectedInsteadOfInsideThePathParser() {
        // The CDP endpoint form must never reach Path.resolve(): Windows parses the context dir
        // eagerly and throws 'Invalid char <:>', while Linux would accept a colon directory.
        val failure = assertThrows<IllegalArgumentException> {
            ProfilePaths.externalContextDir("attach.ws.127.0.0.1:9222")
        }

        assertTrue(
            failure.message?.contains("attach.ws.127.0.0.1.9222") == true,
            "the message must show the dot-joined form, was: ${failure.message}"
        )
    }

    @Test
    @DisplayName("a trailing dot is rejected because Win32 would strip it and alias the key")
    fun aTrailingDotIsRejectedBecauseWin32WouldStripIt() {
        assertThrows<IllegalArgumentException> { ProfilePaths.externalContextDir("session-1.") }

        // Without the rule, 'session-1.' and 'session-1' would name one directory on Windows.
        assertEquals("cx.ext.session-1", ProfilePaths.externalContextDir("session-1").fileName.toString())
    }

    @Test
    @DisplayName("a key is used verbatim, so the identity stays deterministic")
    fun aKeyIsUsedVerbatim() {
        assertEquals(ProfilePaths.externalContextDir("session-1"), ProfilePaths.externalContextDir("session-1"))

        // Case is preserved rather than folded. On a case-insensitive file system the two
        // spellings are one directory anyway, so callers must pick case-unique keys.
        assertEquals("cx.ext.Session-1", ProfilePaths.externalContextDir("Session-1").fileName.toString())
    }

    @Test
    @DisplayName("keys that differ only in a separator keep their own directory")
    fun keysDifferingInASeparatorKeepTheirOwnDirectory() {
        val names = listOf("a.b", "a-b", "a_b", "ab").map { ProfilePaths.externalContextDir(it).fileName.toString() }

        assertEquals(names.size, names.toSet().size, "every key must keep its own directory: $names")
    }
}
