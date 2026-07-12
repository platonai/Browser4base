package ai.platon.pulsar.common.logging

import org.junit.jupiter.api.*
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.*
import org.slf4j.Logger
import org.slf4j.helpers.MessageFormatter
import java.time.Duration
import org.junit.jupiter.api.DisplayName

class ThrottlingLoggerTest {

    private lateinit var mockLogger: Logger
    private lateinit var logger: ThrottlingLogger

    @BeforeEach
    fun setUp() {
        mockLogger = mock<Logger>()
        // Use a generous TTL to avoid flaky test failures. Only the
        // shouldAllowLoggingAfterTtlExpires test needs a short TTL.
        logger = ThrottlingLogger(mockLogger, Duration.ofSeconds(10), enableSuppressedCount = true)
    }

    @Test
        @DisplayName("should log message only once within TTL")
    fun shouldLogMessageOnlyOnceWithinTtl() {
        val format = "User %s logged in"
        val args = arrayOf("Alice")

        logger.info(format, *args)
        logger.info(format, *args)

        verify(mockLogger, times(1)).info(anyString())
    }

    @Test
        @DisplayName("should allow logging after TTL expires")
    fun shouldAllowLoggingAfterTtlExpires() {
        val shortTtlLogger = ThrottlingLogger(mockLogger, Duration.ofMillis(100))
        val format = "System warning: %s"
        val args = arrayOf("low memory")

        shortTtlLogger.info(format, *args)

        Thread.sleep(200) // Wait past the 100ms TTL with comfortable margin

        shortTtlLogger.info(format, *args)

        verify(mockLogger, times(2)).info(anyString())
    }

    @Test
        @DisplayName("should count suppressed messages when enabled")
    fun shouldCountSuppressedMessagesWhenEnabled() {
        val format = "Configuration reload failed: {}"
        val args = arrayOf("timeout")

        logger.info(format, *args)
        logger.info(format, *args)
        logger.info(format, *args)

        val counts = logger.getSuppressedCounts()
        val key = MessageFormatter.arrayFormat(format, args).message

        Assertions.assertTrue(counts?.containsKey(key) == true)
        Assertions.assertEquals(2, counts?.get(key))
    }

    @Test
        @DisplayName("should reset suppression count and cache")
    fun shouldResetSuppressionCountAndCache() {
        val format = "Error processing request: {}"
        val args = arrayOf("404")

        logger.info(format, *args)
        logger.reset()

        logger.info(format, *args)

        verify(mockLogger, times(2)).info(anyString()) // First before reset, second after reset
    }

    @Test
        @DisplayName("should log error with exception and throttle correctly")
    fun shouldLogErrorWithExceptionAndThrottleCorrectly() {
        val throwable = RuntimeException("Database connection failed")
        val format = "Failed to connect to {}"
        val args = arrayOf("main-db")

        logger.error(throwable, format, *args)
        logger.error(throwable, format, *args)

        verify(mockLogger, times(1)).error(anyString(), eq(throwable))
    }

    @Test
        @DisplayName("should handle multiple different messages independently")
    fun shouldHandleMultipleDifferentMessagesIndependently() {
        logger.info("Message A")
        logger.info("Message B")
        logger.info("Message A")

        verify(mockLogger, times(1)).info(eq("Message A"))
        verify(mockLogger, times(1)).info(eq("Message B"))
    }

    @Test
        @DisplayName("should not track suppressed count if disabled")
    fun shouldNotTrackSuppressedCountIfDisabled() {
        val simpleLogger = ThrottlingLogger(mockLogger, Duration.ofMinutes(1), enableSuppressedCount = false)

        simpleLogger.info("Repeated message")
        simpleLogger.info("Repeated message")

        Assertions.assertNull(simpleLogger.getSuppressedCounts())
    }
}
