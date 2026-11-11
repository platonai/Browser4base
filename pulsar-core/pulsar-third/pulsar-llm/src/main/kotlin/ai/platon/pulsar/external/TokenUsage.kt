package ai.platon.pulsar.external

import dev.langchain4j.model.anthropic.AnthropicTokenUsage
import dev.langchain4j.model.openai.OpenAiTokenUsage

/**
 * Provider-reported token usage of one LLM request, together with the
 * prompt-cache breakdown when the provider discloses it.
 *
 * ## Cache accounting differs by protocol — this type keeps both honest
 *
 * Providers count prompt-cache tokens differently:
 *
 * * **OpenAI-compatible APIs** (OpenAI, DeepSeek, OpenRouter, Volcengine, …)
 *   report `usage.prompt_tokens` as the *whole* prompt and break the cached
 *   part out in `prompt_tokens_details.cached_tokens`:
 *   `prompt_tokens = cache_hit + cache_miss`. The cache counters are therefore
 *   **inside** [inputTokenCount] ([cacheCountedInInputTokenCount] = `true`).
 * * **Anthropic-style APIs** report `usage.input_tokens` as the *uncached
 *   remainder only*: total prompt size = `input_tokens + cache_read_input_tokens
 *   + cache_creation_input_tokens`. The cache counters are **outside**
 *   [inputTokenCount] ([cacheCountedInInputTokenCount] = `false`).
 *
 * Callers that need a uniform "how many prompt tokens did the model actually
 * process" number use [effectiveInputTokenCount] / [effectiveTotalTokenCount], or
 * normalize once with [normalized] and then treat [inputTokenCount] as
 * cache-inclusive. Getting this wrong double counts cache reads on OpenAI
 * providers and drops them entirely on Anthropic ones.
 *
 * @property inputTokenCount prompt tokens as the provider reported them, verbatim
 * @property outputTokenCount completion tokens of the request
 * @property totalTokenCount total tokens as the provider reported them (a
 *   provider that omits the total is accounted as input + output)
 * @property cachedInputTokenCount prompt tokens served from the provider's prompt
 *   cache ("cache read" / "cache hit"), or `null` when the provider — or the
 *   model client in front of it — reported no cache details. `null` is *not* `0`:
 *   zero means "the provider said nothing was cached", null means "unknown".
 * @property cacheWriteInputTokenCount prompt tokens written to the provider's
 *   prompt cache (Anthropic's `cache_creation_input_tokens`), or `null` when
 *   unreported. Cache writes are billed above the base input price, which is why
 *   they are kept apart from the reads.
 * @property cacheCountedInInputTokenCount whether the cache counters above are
 *   already part of [inputTokenCount] (OpenAI style) or additional prompt tokens
 *   (Anthropic style)
 */
data class TokenUsage(
    val inputTokenCount: Int = 0,
    val outputTokenCount: Int = 0,
    val totalTokenCount: Int = 0,
    val cachedInputTokenCount: Int? = null,
    val cacheWriteInputTokenCount: Int? = null,
    val cacheCountedInInputTokenCount: Boolean = true,
) {
    /** True when the provider reported any prompt-cache detail. */
    val hasCacheDetails: Boolean
        get() = cachedInputTokenCount != null || cacheWriteInputTokenCount != null

    /**
     * Prompt tokens the model actually processed, cache reads and writes
     * included. Equals [inputTokenCount] for OpenAI-style providers; adds the
     * cache counters for Anthropic-style ones.
     */
    val effectiveInputTokenCount: Int
        get() = if (cacheCountedInInputTokenCount) {
            inputTokenCount
        } else {
            inputTokenCount + (cachedInputTokenCount ?: 0) + (cacheWriteInputTokenCount ?: 0)
        }

    /**
     * Total tokens the request consumed, cache included: [effectiveInputTokenCount]
     * plus [outputTokenCount].
     */
    val effectiveTotalTokenCount: Int
        get() = effectiveInputTokenCount + outputTokenCount

    /**
     * Share of the prompt served from the provider cache, `0.0..1.0`, or `null`
     * when the provider reported no cache details (or the prompt was empty).
     */
    val cacheHitRatio: Double?
        get() {
            val cached = cachedInputTokenCount ?: return null
            val prompt = effectiveInputTokenCount
            return if (prompt > 0) cached.toDouble() / prompt else null
        }

    /**
     * The same usage with the cache counters folded into [inputTokenCount] and
     * [totalTokenCount].
     *
     * After normalization [cachedInputTokenCount] and [cacheWriteInputTokenCount]
     * are subsets of [inputTokenCount] for every provider, so consumers (budget
     * accounting, run statistics, cache-hit ratios) no longer need to know which
     * protocol produced the numbers. Returns this instance unchanged when it is
     * already normalized.
     */
    fun normalized(): TokenUsage {
        if (cacheCountedInInputTokenCount) return this
        val prompt = effectiveInputTokenCount
        return copy(
            inputTokenCount = prompt,
            totalTokenCount = prompt + outputTokenCount,
            cacheCountedInInputTokenCount = true,
        )
    }

    /**
     * Add two usages (e.g. the extraction call plus its advisory metadata call).
     *
     * Cache counters stay `null` when neither side reported them, and the
     * protocol flag is the AND of both sides — a default (all-zero) [TokenUsage]
     * carries `true`, so adding it is a no-op.
     *
     * **Both sides must share a protocol.** Summing one OpenAI-style usage with
     * one Anthropic-style usage mixes "cache inside input" with "cache outside
     * input", and the result cannot be fixed afterwards — normalize each side
     * with [normalized] first when they may come from different providers.
     */
    operator fun plus(other: TokenUsage): TokenUsage = TokenUsage(
        inputTokenCount = inputTokenCount + other.inputTokenCount,
        outputTokenCount = outputTokenCount + other.outputTokenCount,
        totalTokenCount = totalTokenCount + other.totalTokenCount,
        cachedInputTokenCount = sumNullable(cachedInputTokenCount, other.cachedInputTokenCount),
        cacheWriteInputTokenCount = sumNullable(cacheWriteInputTokenCount, other.cacheWriteInputTokenCount),
        cacheCountedInInputTokenCount = cacheCountedInInputTokenCount && other.cacheCountedInInputTokenCount,
    )

    override fun toString(): String {
        val base = "in: ${formatTokenCount(inputTokenCount)} out: ${formatTokenCount(outputTokenCount)} " +
            "total: ${formatTokenCount(totalTokenCount)}"
        if (!hasCacheDetails) return base
        val cached = cachedInputTokenCount?.let { formatTokenCount(it) } ?: "n/a"
        val written = cacheWriteInputTokenCount?.let { formatTokenCount(it) } ?: "n/a"
        return "$base cache read: $cached write: $written"
    }

    companion object {
        private fun formatTokenCount(count: Int): String {
            return when {
                count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
                count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
                else -> count.toString()
            }
        }

        private fun sumNullable(first: Int?, second: Int?): Int? = when {
            first == null -> second
            second == null -> first
            else -> first + second
        }

        /**
         * Build from a LangChain4j usage, keeping the provider's cache breakdown.
         *
         * LangChain4j exposes the cache details on provider-specific subclasses:
         *
         * * [OpenAiTokenUsage.inputTokensDetails]`.cachedTokens()` — what every
         *   OpenAI-compatible API (DeepSeek included) reports as
         *   `prompt_tokens_details.cached_tokens`;
         * * [AnthropicTokenUsage.cacheReadInputTokens] /
         *   [AnthropicTokenUsage.cacheCreationInputTokens].
         *
         * Providers whose client drops the details (Gemini in the LangChain4j
         * versions this library targets, some gateways) yield a usage without
         * cache counters — reported as "unknown", never as zero.
         *
         * Both provider modules are compile dependencies of this artifact, so the
         * subclass checks are safe wherever a chat model exists at all.
         */
        fun from(usage: dev.langchain4j.model.output.TokenUsage?): TokenUsage {
            if (usage == null) return TokenUsage()
            val input = usage.inputTokenCount() ?: 0
            val output = usage.outputTokenCount() ?: 0
            val total = usage.totalTokenCount() ?: (input + output)
            return when (usage) {
                is OpenAiTokenUsage -> TokenUsage(
                    inputTokenCount = input,
                    outputTokenCount = output,
                    totalTokenCount = total,
                    cachedInputTokenCount = usage.inputTokensDetails()?.cachedTokens(),
                    // No cache-write counter for the OpenAI protocol in the
                    // LangChain4j versions this library targets; it arrives later.
                    cacheWriteInputTokenCount = null,
                    cacheCountedInInputTokenCount = true,
                )

                is AnthropicTokenUsage -> TokenUsage(
                    inputTokenCount = input,
                    outputTokenCount = output,
                    totalTokenCount = total,
                    cachedInputTokenCount = usage.cacheReadInputTokens(),
                    cacheWriteInputTokenCount = usage.cacheCreationInputTokens(),
                    cacheCountedInInputTokenCount = false,
                )

                else -> TokenUsage(
                    inputTokenCount = input,
                    outputTokenCount = output,
                    totalTokenCount = total,
                )
            }
        }

        /** Null-safe [plus]: either side may be absent. */
        fun sum(first: TokenUsage?, second: TokenUsage?): TokenUsage = when {
            first == null -> second ?: TokenUsage()
            second == null -> first
            else -> first + second
        }
    }
}
