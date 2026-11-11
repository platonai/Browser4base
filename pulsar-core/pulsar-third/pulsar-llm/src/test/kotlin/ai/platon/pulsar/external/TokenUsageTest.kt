package ai.platon.pulsar.external

import dev.langchain4j.model.anthropic.AnthropicTokenUsage
import dev.langchain4j.model.openai.OpenAiTokenUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for the prompt-cache breakdown carried by [TokenUsage].
 *
 * The two protocols disagree on what the input count means (OpenAI: cache reads
 * are inside `prompt_tokens`; Anthropic: `input_tokens` is the uncached
 * remainder), so the extraction and the normalization are what stop the numbers
 * from being double counted or silently dropped.
 */
class TokenUsageTest {

    @Test
    @DisplayName("A usage without cache details reports unknown, not zero")
    fun usageWithoutCacheDetailsReportsUnknown() {
        val usage = TokenUsage(inputTokenCount = 120, outputTokenCount = 30, totalTokenCount = 150)

        assertFalse(usage.hasCacheDetails)
        assertNull(usage.cachedInputTokenCount)
        assertNull(usage.cacheWriteInputTokenCount)
        assertNull(usage.cacheHitRatio)
        assertEquals(120, usage.effectiveInputTokenCount)
        assertEquals(150, usage.effectiveTotalTokenCount)
    }

    @Test
    @DisplayName("OpenAI-style cached tokens are a subset of the input count")
    fun openAiCachedTokensAreSubsetOfInput() {
        val lc4jUsage = OpenAiTokenUsage.builder()
            .inputTokenCount(1_000)
            .outputTokenCount(200)
            .totalTokenCount(1_200)
            .inputTokensDetails(
                OpenAiTokenUsage.InputTokensDetails.builder().cachedTokens(800).build()
            )
            .build()

        val usage = TokenUsage.from(lc4jUsage)

        assertTrue(usage.hasCacheDetails)
        assertEquals(1_000, usage.inputTokenCount)
        assertEquals(800, usage.cachedInputTokenCount)
        assertNull(usage.cacheWriteInputTokenCount)
        // The prompt is 1,000 tokens — the 800 cached ones are already inside it.
        assertEquals(1_000, usage.effectiveInputTokenCount)
        assertEquals(1_200, usage.effectiveTotalTokenCount)
        assertEquals(0.8, usage.cacheHitRatio!!, 1e-9)
        // Already normalized: same instance, no double counting.
        assertSame(usage, usage.normalized())
    }

    @Test
    @DisplayName("Anthropic-style cache tokens are outside the input count and get folded in by normalized()")
    fun anthropicCachedTokensAreOutsideInput() {
        val lc4jUsage = AnthropicTokenUsage.builder()
            .inputTokenCount(12)
            .outputTokenCount(40)
            .cacheReadInputTokens(900)
            .cacheCreationInputTokens(88)
            .build()

        val usage = TokenUsage.from(lc4jUsage)

        assertTrue(usage.hasCacheDetails)
        assertEquals(12, usage.inputTokenCount)
        assertEquals(900, usage.cachedInputTokenCount)
        assertEquals(88, usage.cacheWriteInputTokenCount)
        // Anthropic counts only the uncached remainder as input.
        assertEquals(1_000, usage.effectiveInputTokenCount)
        assertEquals(1_040, usage.effectiveTotalTokenCount)
        assertEquals(0.9, usage.cacheHitRatio!!, 1e-9)

        val normalized = usage.normalized()

        assertEquals(1_000, normalized.inputTokenCount)
        assertEquals(1_040, normalized.totalTokenCount)
        assertTrue(normalized.cacheCountedInInputTokenCount)
        assertEquals(900, normalized.cachedInputTokenCount)
        assertEquals(88, normalized.cacheWriteInputTokenCount)
        // Idempotent.
        assertSame(normalized, normalized.normalized())
    }

    @Test
    @DisplayName("A reported zero is different from an unreported cache count")
    fun reportedZeroIsNotUnknown() {
        val lc4jUsage = OpenAiTokenUsage.builder()
            .inputTokenCount(500)
            .outputTokenCount(50)
            .totalTokenCount(550)
            .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder().cachedTokens(0).build())
            .build()

        val usage = TokenUsage.from(lc4jUsage)

        assertTrue(usage.hasCacheDetails)
        assertEquals(0, usage.cachedInputTokenCount)
        assertEquals(0.0, usage.cacheHitRatio!!, 1e-9)
    }

    @Test
    @DisplayName("Summing usages keeps unknown cache counts unknown and mixes protocols correctly")
    fun summingUsagesKeepsUnknownUnknown() {
        val openAi = TokenUsage(
            inputTokenCount = 100,
            outputTokenCount = 10,
            totalTokenCount = 110,
            cachedInputTokenCount = 60,
        )
        val anthropic = TokenUsage.from(
            AnthropicTokenUsage.builder()
                .inputTokenCount(5)
                .outputTokenCount(5)
                .cacheReadInputTokens(45)
                .build()
        )

        val sum = openAi + anthropic

        assertEquals(105, sum.inputTokenCount)
        assertEquals(15, sum.outputTokenCount)
        // Totals are summed verbatim: 110 (OpenAI-reported) + 10 (Anthropic input+output).
        assertEquals(120, sum.totalTokenCount)
        assertEquals(105, sum.cachedInputTokenCount)
        // Mixing protocols: the AND of the flags keeps the Anthropic semantics.
        assertFalse(sum.cacheCountedInInputTokenCount)

        val unknown = TokenUsage(inputTokenCount = 10, outputTokenCount = 1, totalTokenCount = 11)
        assertNull(TokenUsage.sum(unknown, unknown).cachedInputTokenCount)
        assertNull(TokenUsage.sum(null, unknown).cachedInputTokenCount)
        assertEquals(10, TokenUsage.sum(null, unknown).inputTokenCount)
        assertEquals(0, TokenUsage.sum(null, null).inputTokenCount)
    }

    @Test
    @DisplayName("A null LangChain4j usage maps to an empty usage")
    fun nullUsageMapsToEmpty() {
        val usage = TokenUsage.from(null)

        assertEquals(0, usage.inputTokenCount)
        assertEquals(0, usage.outputTokenCount)
        assertEquals(0, usage.totalTokenCount)
        assertFalse(usage.hasCacheDetails)
        assertNull(usage.cacheHitRatio)
    }

    @Test
    @DisplayName("Cached tokens are rendered in toString when reported")
    fun toStringRendersCache() {
        val usage = TokenUsage(
            inputTokenCount = 1_000,
            outputTokenCount = 200,
            totalTokenCount = 1_200,
            cachedInputTokenCount = 800,
            cacheWriteInputTokenCount = 0,
        )

        val text = usage.toString()

        assertTrue(text.contains("cache read: 800"), text)
        assertTrue(text.contains("write: 0"), text)
    }
}
