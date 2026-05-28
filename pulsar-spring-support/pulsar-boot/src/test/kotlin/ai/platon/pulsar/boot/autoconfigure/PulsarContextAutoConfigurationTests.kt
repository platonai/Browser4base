package ai.platon.pulsar.boot.autoconfigure

import ai.platon.pulsar.ql.context.SQLContext
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PulsarContextAutoConfigurationTests {
    @Test
    fun `registers converted pulsar bean definitions`() {
        AnnotationConfigApplicationContext().use { context ->
            context.register(PulsarContextConfiguration::class.java)
            context.refresh()

            listOf(
                "conf",
                "webDb",
                "metricsSystem",
                "messageWriter",
                "coreMetrics",
                "appStatusTracker",
                "protocolFactory",
                "globalCacheFactory",
                "globalCache",
                "taskLoop",
                "taskLoops",
                "proxyLoaderFactory",
                "proxyLoader",
                "proxyPool",
                "proxyPoolManagerFactory",
                "proxyPoolManager",
                "browserSettings",
                "browserFactory",
                "browserManager",
                "driverPoolManager",
                "privacyManager",
                "browserResponseHandlerFactory",
                "browserResponseHandler",
                "browserEmulator",
                "browserFetcher",
                "fetchSchedule",
                "parseFilters",
                "htmlParser",
                "tikaParser",
                "parserFactory",
                "pageParser",
                "privacyContextMonitor",
                "driverPoolMonitor",
                "browserMonitor",
                "fetchComponent",
                "parseComponent",
                "updateComponent",
                "loadComponent",
                "pulsarContext",
                "getPulsarSession",
            ).forEach { beanName ->
                assertTrue(context.containsBeanDefinition(beanName), "Missing bean definition: $beanName")
            }

            assertTrue(context.isPrototype("getPulsarSession"), "getPulsarSession should stay prototype")
            assertNotNull(context.getBean(SQLContext::class.java))
        }
    }
}

