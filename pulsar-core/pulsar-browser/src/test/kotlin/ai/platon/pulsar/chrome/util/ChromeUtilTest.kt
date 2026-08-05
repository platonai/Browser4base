package ai.platon.pulsar.chrome.util

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ChromeUtilTest {

    private interface Greeter {
        suspend fun greet(name: String): String
    }

    private interface Doubler {
        fun twice(x: Int): Int
    }

    private interface Missing {
        fun missing(): String
    }

    class TestImpl : Greeter, Doubler {
        override suspend fun greet(name: String) = "hi $name"
        override fun twice(x: Int) = x * 2
    }

    private fun newProxy(): Greeter = Proxy.newProxyInstance(
        TestImpl::class.java.classLoader,
        arrayOf(Greeter::class.java, Doubler::class.java, Missing::class.java),
        SuspendAwareHandler(TestImpl()),
    ) as Greeter

    @Test
    @DisplayName("suspend aware handler delegates suspend calls")
    fun suspendAwareHandlerDelegatesSuspendCalls() {
        val handler = SuspendAwareHandler(TestImpl())
        val proxy = Proxy.newProxyInstance(TestImpl::class.java.classLoader, arrayOf(Greeter::class.java), handler)
        val latch = CountDownLatch(1)
        val result = AtomicReference<String?>()
        val continuation = object : Continuation<String> {
            override val context: CoroutineContext = EmptyCoroutineContext
            override fun resumeWith(resumed: Result<String>) {
                result.set(resumed.getOrNull())
                latch.countDown()
            }
        }
        val method = Greeter::class.java.getMethod("greet", String::class.java, Continuation::class.java)

        val returned = handler.invoke(proxy, method, arrayOf("codex", continuation))

        assertEquals(COROUTINE_SUSPENDED, returned)
        assertTrue(latch.await(5, TimeUnit.SECONDS), "suspend call continuation was not resumed")
        assertEquals("hi codex", result.get())
    }

    @Test
    @DisplayName("suspend aware handler delegates plain calls")
    fun suspendAwareHandlerDelegatesPlainCalls() {
        assertEquals(42, (newProxy() as Doubler).twice(21))
    }

    @Test
    @DisplayName("suspend aware handler returns null for unknown methods")
    fun suspendAwareHandlerReturnsNullForUnknownMethods() {
        assertNull((newProxy() as Missing).missing())
    }

    @Test
    @DisplayName("getJavaClass resolves invariant generic arguments")
    fun getJavaClassResolvesInvariantGenericArguments() {
        val method = Holder::class.java.getDeclaredMethod("first", Box::class.java)

        assertEquals(String::class.java, ReflectUtils.getJavaClass(method))
    }

    @Test
    @DisplayName("getJavaClass returns Any for covariant out parameters")
    fun getJavaClassReturnsAnyForCovariantParameters() {
        val method = Holder::class.java.getDeclaredMethod("second", List::class.java)

        assertEquals(Any::class.java, ReflectUtils.getJavaClass(method))
    }

    @Test
    @DisplayName("credentials data class exposes fields and copy")
    fun credentialsDataClass() {
        val credentials = Credentials("user", "pass")

        assertEquals("user", credentials.username)
        assertEquals("pass", credentials.password)
        assertEquals(Credentials("user", null), credentials.copy(password = null))
    }

    @Test
    @DisplayName("chrome rpc exception tracks code and url")
    fun chromeRpcExceptionTracksCodeAndUrl() {
        val exception = ChromeRPCException(123L, "boom")
        exception.url = "https://example.com"

        assertEquals(123L, exception.code)
        assertEquals("https://example.com", exception.url)
        assertEquals("boom", exception.message)
    }

    class Box<T>(val value: T)

    class Holder {
        fun first(box: Box<String>) {}
        fun second(list: List<String>) {}
    }
}
