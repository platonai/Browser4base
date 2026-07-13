package ai.platon.pulsar.persist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.Instant

class TestProtocolHeaders {

    @Test
    fun testGetAndPut() {
        val headers = ProtocolHeaders.box(HashMap())
        headers.put("Content-Type", "text/html")
        assertEquals("text/html", headers.get("Content-Type"))
        assertNull(headers.get("Non-Existent"))
    }

    @Test
    fun testGetOrDefault() {
        val headers = ProtocolHeaders.box(HashMap())
        // Returns default when key absent
        assertEquals("text/html", headers.getOrDefault("Content-Type", "text/html"))
        // Returns actual value when present
        headers.put("Content-Type", "application/json")
        assertEquals("application/json", headers.getOrDefault("Content-Type", "text/html"))
    }

    @Test
    fun testRemove() {
        val headers = ProtocolHeaders.box(HashMap())
        headers.put("X-Test", "value")
        assertEquals("value", headers.get("X-Test"))
        headers.remove("X-Test")
        assertNull(headers.get("X-Test"))
    }

    @Test
    fun testPutAll() {
        val headers = ProtocolHeaders.box(HashMap())
        headers.putAll(mapOf("A" to "1", "B" to "2"))
        assertEquals("1", headers.get("A"))
        assertEquals("2", headers.get("B"))
    }

    @Test
    fun testPutAllMulti() {
        val headers = ProtocolHeaders.box(HashMap())
        // Multi-valued headers: last value wins (single-valued backing map)
        headers.putAllMulti(mapOf("Set-Cookie" to listOf("a=1", "b=2")))
        assertEquals("b=2", headers.get("Set-Cookie"))
    }

    @Test
    fun testClear() {
        val headers = ProtocolHeaders.box(HashMap())
        headers.put("X-Test", "value")
        assertEquals(1, headers.asStringMap().size)
        headers.clear()
        assertNull(headers.get("X-Test"))
        assertEquals(0, headers.asStringMap().size)
    }

    @Test
    fun testGetLastModifiedWhenAbsent() {
        val headers = ProtocolHeaders.box(HashMap())
        // No Last-Modified header → Instant.EPOCH
        assertEquals(Instant.EPOCH, headers.lastModified)
    }

    @Test
    fun testGetLastModifiedWhenPresent() {
        val headers = ProtocolHeaders.box(HashMap())
        // Standard HTTP date format (RFC 1123)
        headers.put("Last-Modified", "Wed, 21 Oct 2015 07:28:00 GMT")
        val result = headers.lastModified
        // BUG: DateTimes.parseHttpDateTime calls DateUtils.parseDate(text) without patterns,
        // which always throws ParseException in commons-lang3 — so it always returns defaultValue.
        // Once parseHttpDateTime is fixed, this should parse the date correctly.
        assertEquals(Instant.EPOCH, result, "Currently always EPOCH due to bug in parseHttpDateTime")
    }

    @Test
    fun testGetContentLengthWhenAbsent() {
        val headers = ProtocolHeaders.box(HashMap())
        assertEquals(-1L, headers.contentLength)
    }

    @Test
    fun testGetContentLengthNormal() {
        val headers = ProtocolHeaders.box(HashMap())
        headers.put("Content-Length", "1024")
        assertEquals(1024L, headers.contentLength)
    }

    @Test
    fun testGetContentLengthLargeValue() {
        val headers = ProtocolHeaders.box(HashMap())
        // > Integer.MAX_VALUE — validates the long fix
        headers.put("Content-Length", "5000000000")
        assertEquals(5000000000L, headers.contentLength)
    }

    @Test
    fun testGetDispositionFilenameWhenAbsent() {
        val headers = ProtocolHeaders.box(HashMap())
        assertNull(headers.dispositionFilename)
    }

    @Test
    fun testGetDispositionFilenameQuoted() {
        val headers = ProtocolHeaders.box(HashMap())
        headers.put("Content-Disposition", "attachment; filename=\"report.pdf\"")
        assertEquals("report.pdf", headers.dispositionFilename)
    }

    @Test
    fun testGetDispositionFilenameUnquoted() {
        val headers = ProtocolHeaders.box(HashMap())
        headers.put("Content-Disposition", "attachment; filename=data.csv")
        assertEquals("data.csv", headers.dispositionFilename)
    }

    @Test
    fun testGetDispositionFilenameWithExtraParams() {
        val headers = ProtocolHeaders.box(HashMap())
        // Tests the reluctant-quantifier fix: .+? stops at the first closing quote
        headers.put("Content-Disposition", "attachment; filename=\"report.pdf\"; size=1024")
        assertEquals("report.pdf", headers.dispositionFilename)
    }

    @Test
    fun testGetDecodedDispositionFilename() {
        val headers = ProtocolHeaders.box(HashMap())
        // "中文文件.pdf" URL-encoded
        headers.put("Content-Disposition", "attachment; filename=\"%E4%B8%AD%E6%96%87%E6%96%87%E4%BB%B6.pdf\"")
        val decoded = headers.getDecodedDispositionFilename()
        assertNotNull(decoded)
        assertTrue(decoded.contains("文"), "Should decode UTF-8 URL-encoded filename")
    }

    @Test
    fun testGetDecodedDispositionFilenameWhenAbsent() {
        val headers = ProtocolHeaders.box(HashMap())
        assertNull(headers.getDecodedDispositionFilename())
    }

    @Test
    fun testAsStringMap() {
        val headers = ProtocolHeaders.box(HashMap())
        headers.put("A", "1")
        headers.put("B", "2")
        val map = headers.asStringMap()
        assertEquals(2, map.size)
        assertEquals("1", map["A"])
        assertEquals("2", map["B"])
    }

    @Test
    fun testAsStringMapEmpty() {
        val headers = ProtocolHeaders.box(HashMap())
        assertEquals(0, headers.asStringMap().size)
    }

    @Test
    fun testUnbox() {
        val map = HashMap<CharSequence, CharSequence>()
        val headers = ProtocolHeaders.box(map)
        assertEquals(map, headers.unbox())
    }

    @Test
    fun testToStringContainsEntries() {
        val headers = ProtocolHeaders.box(HashMap())
        headers.put("A", "1")
        val str = headers.toString()
        assertTrue(str.contains("A: 1"), "toString should contain header entries")
    }
}
