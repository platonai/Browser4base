package ai.platon.pulsar.chrome.network

import ai.platon.cdt.kt.protocol.types.network.Response
import ai.platon.pulsar.api.BrowserProtocol

class CDPResponse(
    val browserProtocol: BrowserProtocol,
    val request: CDPRequest,
    val response: Response
) {
    fun resolveBody(body: String?) {
    }
}
