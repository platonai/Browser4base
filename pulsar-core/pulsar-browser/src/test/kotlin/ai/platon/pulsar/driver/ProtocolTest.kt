package ai.platon.pulsar.driver

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProtocolTest {
    @Test
    fun ensureProtocol() {
        val entries = ai.platon.cdt.kt.protocol.types.network.IPAddressSpace.entries.map { it.name }
        arrayOf("LOCAL", "LOOPBACK").forEach {
            assertTrue(entries.contains(it)) { "$it should be supported by IPAddressSpace" }
        }
    }
}
