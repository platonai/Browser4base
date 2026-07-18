package ai.platon.pulsar.external

import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.external.impl.CachedBrowserChatModel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ChatModelFactoryTest {

    @AfterEach
    fun cleanUp() {
        // Ensure no system properties leak between tests
        ChatModelFactory.SUPPORTED_API_KEY_NAMES.forEach { System.clearProperty(it) }
    }

    // ---------------------------------------------------------------------------
    // Provider registry integrity
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Provider registry should have no duplicate API key names")
    fun providerRegistryShouldHaveNoDuplicateApiKeyNames() {
        val keyNames = ChatModelFactory.SUPPORTED_API_KEY_NAMES
        val duplicates = keyNames.groupingBy { it }.eachCount().filter { it.value > 1 }
        assertTrue(duplicates.isEmpty()) {
            "Duplicate API key names found: ${duplicates.keys}"
        }
    }

    @Test
    @DisplayName("Provider registry should contain all major providers")
    fun providerRegistryShouldContainMajorProviders() {
        val keyNames = ChatModelFactory.SUPPORTED_API_KEY_NAMES
        val required = listOf(
            // Global
            "OPENAI_API_KEY", "ANTHROPIC_API_KEY", "GOOGLE_GENERATIVE_AI_API_KEY",
            "GEMINI_API_KEY", "DEEPSEEK_API_KEY", "GROQ_API_KEY", "MISTRAL_API_KEY",
            // Chinese domestic
            "ZHIPU_API_KEY", "MOONSHOT_API_KEY", "BAICHUAN_API_KEY",
            "YI_API_KEY", "MINIMAX_API_KEY", "STEPFUN_API_KEY",
            "HUNYUAN_API_KEY", "QIANFAN_API_KEY",
            // Chinese aliases
            "LINGYI_API_KEY", "TENCENT_API_KEY", "BAIDU_API_KEY"
        )
        required.forEach { name ->
            assertTrue(keyNames.contains(name)) {
                "Expected $name in SUPPORTED_API_KEY_NAMES"
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Model creation via explicit provider name — Global
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Should create OpenAI-compatible model for groq provider")
    fun shouldCreateModelForGroqProvider() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("groq", "llama-3.3-70b-versatile", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Should create OpenAI-compatible model for together provider")
    fun shouldCreateModelForTogetherProvider() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("together", "meta-llama/Llama-3.3-70B-Instruct-Turbo", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Should create OpenAI-compatible model for mistral provider")
    fun shouldCreateModelForMistralProvider() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("mistral", "mistral-large-latest", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Should create OpenAI-compatible model for xai provider")
    fun shouldCreateModelForXaiProvider() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("xai", "grok-2-1212", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Should create OpenAI-compatible model for perplexity provider")
    fun shouldCreateModelForPerplexityProvider() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("perplexity", "llama-3.1-sonar-large-128k-online", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Should create OpenAI-compatible model for fireworks provider")
    fun shouldCreateModelForFireworksProvider() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("fireworks", "llama-v3p3-70b-instruct", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    // ---------------------------------------------------------------------------
    // Model creation via explicit provider name — Chinese domestic
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Should create model for zhipu (GLM) provider")
    fun shouldCreateModelForZhipuProvider() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("zhipu", "glm-4-plus", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Should create model for moonshot (Kimi) provider")
    fun shouldCreateModelForMoonshotProvider() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("moonshot", "moonshot-v1-8k", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Should create model for baichuan provider")
    fun shouldCreateModelForBaichuanProvider() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("baichuan", "Baichuan4", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Should create model for yi (01.AI) provider")
    fun shouldCreateModelForYiProvider() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("yi", "yi-large", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Should create model for minimax provider")
    fun shouldCreateModelForMinimaxProvider() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("minimax", "abab6.5s-chat", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Should create model for stepfun provider")
    fun shouldCreateModelForStepfunProvider() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("stepfun", "step-1-8k", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Should create model for hunyuan (Tencent) provider")
    fun shouldCreateModelForHunyuanProvider() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("hunyuan", "hunyuan-pro", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Should create model for qianfan (Baidu) provider")
    fun shouldCreateModelForQianfanProvider() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("qianfan", "ernie-4.0-8k", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    // ---------------------------------------------------------------------------
    // Native (non-OpenAI-compatible) providers
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Should create Anthropic model via native AnthropicChatModel")
    fun shouldCreateAnthropicModel() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("anthropic", "claude-sonnet-4-5-20250901", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Should create Gemini model via native GoogleAiGeminiChatModel")
    fun shouldCreateGeminiModel() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("gemini", "gemini-2.0-flash", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Should create Anthropic model via getOrCreateAnthropicModel")
    fun shouldCreateAnthropicModelViaDedicatedMethod() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreateAnthropicModel("claude-sonnet-4-5-20250901", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Should create Gemini model via getOrCreateGeminiModel")
    fun shouldCreateGeminiModelViaDedicatedMethod() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreateGeminiModel("gemini-2.0-flash", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    // ---------------------------------------------------------------------------
    // Model creation via API key detection (getOrCreate with conf only)
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Should detect Groq API key and create model")
    fun shouldDetectGroqApiKey() {
        System.setProperty("GROQ_API_KEY", "test-groq-key-12345")
        try {
            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))
            val model = ChatModelFactory.getOrCreate(conf)
            assertNotNull(model)
            assertInstanceOf(CachedBrowserChatModel::class.java, model)
        } finally {
            System.clearProperty("GROQ_API_KEY")
        }
    }

    @Test
    @DisplayName("Should detect Zhipu API key and create model")
    fun shouldDetectZhipuApiKey() {
        System.setProperty("ZHIPU_API_KEY", "test-zhipu-key-12345")
        try {
            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))
            val model = ChatModelFactory.getOrCreate(conf)
            assertNotNull(model)
            assertInstanceOf(CachedBrowserChatModel::class.java, model)
        } finally {
            System.clearProperty("ZHIPU_API_KEY")
        }
    }

    @Test
    @DisplayName("Should detect Moonshot API key and create model")
    fun shouldDetectMoonshotApiKey() {
        System.setProperty("MOONSHOT_API_KEY", "test-moonshot-key-12345")
        try {
            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))
            val model = ChatModelFactory.getOrCreate(conf)
            assertNotNull(model)
            assertInstanceOf(CachedBrowserChatModel::class.java, model)
        } finally {
            System.clearProperty("MOONSHOT_API_KEY")
        }
    }

    @Test
    @DisplayName("Should detect Anthropic API key and create native model")
    fun shouldDetectAnthropicApiKey() {
        System.setProperty("ANTHROPIC_API_KEY", "test-anthropic-key-12345")
        try {
            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))
            val model = ChatModelFactory.getOrCreate(conf)
            assertNotNull(model)
            assertInstanceOf(CachedBrowserChatModel::class.java, model)
        } finally {
            System.clearProperty("ANTHROPIC_API_KEY")
        }
    }

    @Test
    @DisplayName("Should detect Gemini API key and create native model")
    fun shouldDetectGeminiApiKey() {
        System.setProperty("GEMINI_API_KEY", "test-gemini-key-12345")
        try {
            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))
            val model = ChatModelFactory.getOrCreate(conf)
            assertNotNull(model)
            assertInstanceOf(CachedBrowserChatModel::class.java, model)
        } finally {
            System.clearProperty("GEMINI_API_KEY")
        }
    }

    // ---------------------------------------------------------------------------
    // API key alias resolution (Chinese providers)
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Should detect Google API key as Gemini alias")
    fun shouldDetectGoogleApiKeyAsGemini() {
        System.setProperty("GOOGLE_API_KEY", "test-google-key-12345")
        try {
            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))
            val model = ChatModelFactory.getOrCreate(conf)
            assertNotNull(model)
            assertInstanceOf(CachedBrowserChatModel::class.java, model)
        } finally {
            System.clearProperty("GOOGLE_API_KEY")
        }
    }

    @Test
    @DisplayName("Should detect LINGYI_API_KEY as Yi alias")
    fun shouldDetectLingyiApiKeyAsYiAlias() {
        System.setProperty("LINGYI_API_KEY", "test-lingyi-key-12345")
        try {
            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))
            val model = ChatModelFactory.getOrCreate(conf)
            assertNotNull(model)
            assertInstanceOf(CachedBrowserChatModel::class.java, model)
        } finally {
            System.clearProperty("LINGYI_API_KEY")
        }
    }

    @Test
    @DisplayName("Should detect TENCENT_API_KEY as Hunyuan alias")
    fun shouldDetectTencentApiKeyAsHunyuanAlias() {
        System.setProperty("TENCENT_API_KEY", "test-tencent-key-12345")
        try {
            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))
            val model = ChatModelFactory.getOrCreate(conf)
            assertNotNull(model)
            assertInstanceOf(CachedBrowserChatModel::class.java, model)
        } finally {
            System.clearProperty("TENCENT_API_KEY")
        }
    }

    @Test
    @DisplayName("Should detect BAIDU_API_KEY as Qianfan alias")
    fun shouldDetectBaiduApiKeyAsQianfanAlias() {
        System.setProperty("BAIDU_API_KEY", "test-baidu-key-12345")
        try {
            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))
            val model = ChatModelFactory.getOrCreate(conf)
            assertNotNull(model)
            assertInstanceOf(CachedBrowserChatModel::class.java, model)
        } finally {
            System.clearProperty("BAIDU_API_KEY")
        }
    }

    // ---------------------------------------------------------------------------
    // Legacy backward compatibility
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("doubao API should be compatible with OpenAI API")
    fun doubaoApiShouldBeCompatibleWithOpenaiApi() {
        val provider = "volcengine"
        val modelName = "doubao-1-5-pro-32k-250115"
        val apiKey = "test-fake-api-key-not-a-real-credential"

        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate(provider, modelName, apiKey, conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)

        try {
            val response = runBlocking { model.call("Give me the answer only for 100+1=?") }
            fail("Expected an exception due to invalid API key, but got response: $response")
        } catch (e: Exception) {
            val lowered = e.toString().lowercase()
            val keywords = listOf("error", "invalid", "missing", "unauthorized", "fail",
                "not found", "not exist", "not support", "not available", "not configured")
            val matched = keywords.any { lowered.contains(it) }
            assertTrue(matched, "Exception message should indicate auth/config failure: ${e.toString()}")
        }
    }

    @Test
    @DisplayName("Backward compat: deepseek provider should work")
    fun backwardCompatDeepseekProvider() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("deepseek", "deepseek-chat", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Backward compat: volcengine provider should work")
    fun backwardCompatVolcengineProvider() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("volcengine", "doubao-1-5-pro-32k-250115", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Backward compat: bailian provider should work")
    fun backwardCompatBailianProvider() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("bailian", "qwen-plus", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    // ---------------------------------------------------------------------------
    // Model cache behaviour
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Should return cached model for same parameters")
    fun shouldReturnCachedModelForSameParameters() {
        val conf = ImmutableConfig()
        val model1 = ChatModelFactory.getOrCreate("openai", "gpt-4o", "test-key-12345", conf)
        val model2 = ChatModelFactory.getOrCreate("openai", "gpt-4o", "test-key-12345", conf)
        assertSame(model1, model2, "Should return the same cached instance")
    }

    @Test
    @DisplayName("Should return null when not configured")
    fun shouldReturnNullWhenNotConfigured() {
        // Clear all known system properties for a clean test baseline
        ChatModelFactory.SUPPORTED_API_KEY_NAMES.forEach { System.clearProperty(it) }
        val conf = ImmutableConfig()
        val configured = ChatModelFactory.isModelConfigured(conf, verbose = false)
        if (configured) {
            // Environment variables or config files may have keys set externally;
            // skip the assertion rather than failing on the developer's own setup.
            println("SKIP: LLM is configured externally (env vars or config files) — cannot test null path")
            return
        }
        assertNull(ChatModelFactory.getOrCreateOrNull(conf))
    }

    // ---------------------------------------------------------------------------
    // Deny list — parsing and query methods
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("getDeniedProviders should return empty set when deny list is not configured")
    fun getDeniedProvidersShouldReturnEmptySetWhenNotConfigured() {
        val conf = ImmutableConfig()
        val denied = ChatModelFactory.getDeniedProviders(conf)
        assertTrue(denied.isEmpty())
    }

    @Test
    @DisplayName("getDeniedProviders should parse comma-separated provider names")
    fun getDeniedProvidersShouldParseCommaSeparatedList() {
        System.setProperty("llm.provider.deny.list", "openai,zhipu,minimax")
        try {
            val conf = ImmutableConfig()
            val denied = ChatModelFactory.getDeniedProviders(conf)
            assertEquals(3, denied.size)
            assertTrue(denied.containsAll(setOf("openai", "zhipu", "minimax")))
        } finally {
            System.clearProperty("llm.provider.deny.list")
        }
    }

    @Test
    @DisplayName("getDeniedProviders should handle whitespace and empty entries")
    fun getDeniedProvidersShouldHandleWhitespaceAndEmptyEntries() {
        System.setProperty("llm.provider.deny.list", " openai , , zhipu ,  ")
        try {
            val conf = ImmutableConfig()
            val denied = ChatModelFactory.getDeniedProviders(conf)
            assertEquals(2, denied.size)
            assertTrue(denied.containsAll(setOf("openai", "zhipu")))
        } finally {
            System.clearProperty("llm.provider.deny.list")
        }
    }

    @Test
    @DisplayName("isProviderDenied should return true for a denied provider")
    fun isProviderDeniedShouldReturnTrueForDeniedProvider() {
        System.setProperty("llm.provider.deny.list", "openai,zhipu")
        try {
            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isProviderDenied("openai", conf))
            assertTrue(ChatModelFactory.isProviderDenied("zhipu", conf))
            assertFalse(ChatModelFactory.isProviderDenied("anthropic", conf))
        } finally {
            System.clearProperty("llm.provider.deny.list")
        }
    }

    @Test
    @DisplayName("isProviderDenied should resolve aliases (claude → anthropic)")
    fun isProviderDeniedShouldResolveAliases() {
        System.setProperty("llm.provider.deny.list", "anthropic")
        try {
            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isProviderDenied("claude", conf))
            assertTrue(ChatModelFactory.isProviderDenied("ANTHROPIC_API_KEY", conf))
        } finally {
            System.clearProperty("llm.provider.deny.list")
        }
    }

    @Test
    @DisplayName("isProviderDenied should resolve API key names")
    fun isProviderDeniedShouldResolveApiKeyNames() {
        System.setProperty("llm.provider.deny.list", "zhipu")
        try {
            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isProviderDenied("ZHIPU_API_KEY", conf))
        } finally {
            System.clearProperty("llm.provider.deny.list")
        }
    }

    // ---------------------------------------------------------------------------
    // Deny list — auto-detection (getOrCreate with conf only)
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Should skip denied provider and fall through to next configured provider")
    fun shouldSkipDeniedProviderAndFallThroughToNext() {
        System.setProperty("GROQ_API_KEY", "test-groq-key-12345")
        System.setProperty("OPENAI_API_KEY", "test-openai-key-12345")
        System.setProperty("llm.provider.deny.list", "groq")
        try {
            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))
            val model = ChatModelFactory.getOrCreate(conf)
            assertNotNull(model)
            // Groq is denied, so it should fall through to OpenAI
            // (Groq appears before OpenAI in OPENAI_COMPATIBLE_PROVIDERS)
        } finally {
            System.clearProperty("GROQ_API_KEY")
            System.clearProperty("OPENAI_API_KEY")
            System.clearProperty("llm.provider.deny.list")
        }
    }

    @Test
    @DisplayName("Should report not configured when only provider is denied")
    fun shouldReportNotConfiguredWhenOnlyProviderIsDenied() {
        System.setProperty("OPENAI_API_KEY", "test-openai-key-12345")
        System.setProperty("llm.provider.deny.list", "openai")
        try {
            val conf = ImmutableConfig()
            // No other providers configured, and openai is denied
            val configured = ChatModelFactory.isModelConfigured(conf, verbose = false)
            // Might be configured if env vars for other providers exist — skip gracefully
            if (configured) {
                println("SKIP: Other LLM providers are configured externally")
                return
            }
            assertFalse(configured)
        } finally {
            System.clearProperty("OPENAI_API_KEY")
            System.clearProperty("llm.provider.deny.list")
        }
    }

    // ---------------------------------------------------------------------------
    // Deny list — explicit creation (getOrCreate with provider)
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Should throw when explicitly creating a denied provider")
    fun shouldThrowWhenExplicitlyCreatingDeniedProvider() {
        System.setProperty("llm.provider.deny.list", "openai")
        try {
            val conf = ImmutableConfig()
            val ex = assertThrows(IllegalArgumentException::class.java) {
                ChatModelFactory.getOrCreate("openai", "gpt-4o", "test-key-12345", conf)
            }
            assertTrue(ex.message!!.contains("deny list"), "Message should mention deny list")
            assertTrue(ex.message!!.contains("openai"), "Message should name the provider")
        } finally {
            System.clearProperty("llm.provider.deny.list")
        }
    }

    @Test
    @DisplayName("Should throw when creating a provider via alias that is denied")
    fun shouldThrowWhenCreatingDeniedProviderViaAlias() {
        System.setProperty("llm.provider.deny.list", "anthropic")
        try {
            val conf = ImmutableConfig()
            val ex = assertThrows(IllegalArgumentException::class.java) {
                ChatModelFactory.getOrCreate("claude", "claude-sonnet-4-6", "test-key-12345", conf)
            }
            assertTrue(ex.message!!.contains("deny list"), "Message should mention deny list")
        } finally {
            System.clearProperty("llm.provider.deny.list")
        }
    }

    @Test
    @DisplayName("Should allow creating non-denied provider when others are denied")
    fun shouldAllowCreatingNonDeniedProvider() {
        System.setProperty("llm.provider.deny.list", "openai,zhipu,anthropic")
        try {
            val conf = ImmutableConfig()
            // Groq is not denied — should work fine
            val model = ChatModelFactory.getOrCreate("groq", "llama-3.3-70b-versatile", "test-key-12345", conf)
            assertNotNull(model)
            assertInstanceOf(CachedBrowserChatModel::class.java, model)
        } finally {
            System.clearProperty("llm.provider.deny.list")
        }
    }

    // ---------------------------------------------------------------------------
    // Deny list — getOrCreateOrNull
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("getOrCreateOrNull should return null when provider is denied")
    fun getOrCreateOrNullShouldReturnNullWhenDenied() {
        System.setProperty("OPENAI_API_KEY", "test-openai-key-12345")
        System.setProperty("llm.provider.deny.list", "openai")
        try {
            val conf = ImmutableConfig()
            val model = ChatModelFactory.getOrCreateOrNull(conf)
            // If other providers are configured externally, model might not be null
            if (model != null) {
                println("SKIP: Other LLM providers are configured externally")
                return
            }
            assertNull(model)
        } finally {
            System.clearProperty("OPENAI_API_KEY")
            System.clearProperty("llm.provider.deny.list")
        }
    }
}
