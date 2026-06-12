package ai.platon.pulsar.persist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestProtocolStatus {

    @Test
    fun testRetry() {
        val status = ProtocolStatus.retry(RetryScope.PRIVACY, Exception())
        assertTrue(status.isRetry(RetryScope.PRIVACY, Exception()))
        assertTrue(status.isRetry(RetryScope.PRIVACY, Exception::class.java))

        val e = Exception()
//        printlnPro(e.javaClass.name)
//        printlnPro(Exception::class.java.toString())
        assertEquals("java.lang.Exception", e.javaClass.name)
        assertEquals("class java.lang.Exception", Exception::class.java.toString())
    }
}
