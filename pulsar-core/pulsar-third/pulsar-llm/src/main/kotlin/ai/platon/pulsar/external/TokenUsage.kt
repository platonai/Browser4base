package ai.platon.pulsar.external

data class TokenUsage(
    val inputTokenCount: Int = 0,
    val outputTokenCount: Int = 0,
    val totalTokenCount: Int = 0,
) {
    override fun toString(): String {
        return "in: ${formatTokenCount(inputTokenCount)} out: ${formatTokenCount(outputTokenCount)} total: ${formatTokenCount(totalTokenCount)}"
    }

    companion object {
        private fun formatTokenCount(count: Int): String {
            return when {
                count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
                count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
                else -> count.toString()
            }
        }
    }
}
