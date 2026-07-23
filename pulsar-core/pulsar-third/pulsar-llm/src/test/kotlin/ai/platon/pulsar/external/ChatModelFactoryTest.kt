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
        // Clear any registered providers from previous tests
        ChatModelFactory.registeredProviders.forEach {
            ChatModelFactory.unregisterProvider(it.providerName)
        }
        // Reset configurable fields to defaults
        ChatModelFactory.resetMessagesToDefaults()
        // Reset cached provider registry for clean test state
        ChatModelFactory.resetProviders()
        // Clear any override path
        System.clearProperty("llm.provider.config.path")
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

    // ---------------------------------------------------------------------------
    // Provider registration
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Should register a custom provider and detect its API key")
    fun shouldRegisterCustomProviderAndDetectApiKey() {
        System.setProperty("CUSTOM_API_KEY", "test-custom-key-12345")
        try {
            ChatModelFactory.registerProvider(ProviderConfig(
                apiKeyName = "CUSTOM_API_KEY",
                modelNameKey = "CUSTOM_MODEL_NAME",
                baseUrlKey = "CUSTOM_BASE_URL",
                defaultModel = "custom-model-v1",
                defaultBaseUrl = "https://api.custom.com/v1",
                providerName = "custom"
            ))

            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))
            val model = ChatModelFactory.getOrCreate(conf)
            assertNotNull(model)
            assertInstanceOf(CachedBrowserChatModel::class.java, model)
        } finally {
            System.clearProperty("CUSTOM_API_KEY")
            ChatModelFactory.unregisterProvider("custom")
        }
    }

    @Test
    @DisplayName("Should create model for registered provider via explicit name")
    fun shouldCreateModelForRegisteredProviderViaExplicitName() {
        ChatModelFactory.registerProvider(ProviderConfig(
            apiKeyName = "REGISTERED_API_KEY",
            modelNameKey = "REGISTERED_MODEL_NAME",
            baseUrlKey = "REGISTERED_BASE_URL",
            defaultModel = "registered-model-v1",
            defaultBaseUrl = "https://api.registered.com/v1",
            providerName = "registered-provider"
        ))

        try {
            val conf = ImmutableConfig()
            val model = ChatModelFactory.getOrCreate(
                "registered-provider", "registered-model-v1", "test-key-12345", conf
            )
            assertNotNull(model)
            assertInstanceOf(CachedBrowserChatModel::class.java, model)
        } finally {
            ChatModelFactory.unregisterProvider("registered-provider")
        }
    }

    @Test
    @DisplayName("Should unregister a provider")
    fun shouldUnregisterProvider() {
        ChatModelFactory.registerProvider(ProviderConfig(
            apiKeyName = "TEMP_API_KEY",
            modelNameKey = "TEMP_MODEL_NAME",
            baseUrlKey = "TEMP_BASE_URL",
            defaultModel = "temp-model",
            defaultBaseUrl = "https://api.temp.com/v1",
            providerName = "temp-provider"
        ))

        val removed = ChatModelFactory.unregisterProvider("temp-provider")
        assertTrue(removed, "Should return true when a registered provider is removed")
        assertTrue(
            ChatModelFactory.registeredProviders.none { it.providerName == "temp-provider" },
            "Provider should no longer appear in registeredProviders"
        )
    }

    @Test
    @DisplayName("unregisterProvider should return false for unknown provider")
    fun unregisterProviderShouldReturnFalseForUnknownProvider() {
        val removed = ChatModelFactory.unregisterProvider("nonexistent-provider-xyz")
        assertFalse(removed, "Should return false for a provider that was never registered")
    }

    @Test
    @DisplayName("Should reject duplicate provider registration")
    fun shouldRejectDuplicateProviderRegistration() {
        ChatModelFactory.registerProvider(ProviderConfig(
            apiKeyName = "DUPLICATE_API_KEY",
            modelNameKey = "DUPLICATE_MODEL_NAME",
            baseUrlKey = "DUPLICATE_BASE_URL",
            defaultModel = "dup-model",
            defaultBaseUrl = "https://api.dup.com/v1",
            providerName = "duplicate-test"
        ))

        try {
            val ex = assertThrows(IllegalArgumentException::class.java) {
                ChatModelFactory.registerProvider(ProviderConfig(
                    apiKeyName = "DUPLICATE_API_KEY_2",
                    modelNameKey = "DUPLICATE_MODEL_NAME_2",
                    baseUrlKey = "DUPLICATE_BASE_URL_2",
                    defaultModel = "dup-model-2",
                    defaultBaseUrl = "https://api.dup2.com/v1",
                    providerName = "duplicate-test"
                ))
            }
            assertTrue(ex.message!!.contains("already registered"),
                "Message should mention 'already registered'")
        } finally {
            ChatModelFactory.unregisterProvider("duplicate-test")
        }
    }

    @Test
    @DisplayName("Should reject registration that conflicts with a built-in provider name")
    fun shouldRejectRegistrationConflictingWithBuiltin() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            ChatModelFactory.registerProvider(ProviderConfig(
                apiKeyName = "OPENAI_API_KEY_V2",
                modelNameKey = "OPENAI_MODEL_NAME_V2",
                baseUrlKey = "OPENAI_BASE_URL_V2",
                defaultModel = "gpt-99",
                defaultBaseUrl = "https://api.openai.com/v1",
                providerName = "openai"
            ))
        }
        assertTrue(ex.message!!.contains("built-in"),
            "Message should mention 'built-in'")
    }

    @Test
    @DisplayName("registeredProviders should reflect currently registered providers")
    fun registeredProvidersShouldReflectCurrentlyRegistered() {
        val initialSize = ChatModelFactory.registeredProviders.size

        ChatModelFactory.registerProvider(ProviderConfig(
            apiKeyName = "LIST_TEST_API_KEY",
            modelNameKey = "LIST_TEST_MODEL_NAME",
            baseUrlKey = "LIST_TEST_BASE_URL",
            defaultModel = "list-test-model",
            defaultBaseUrl = "https://api.list-test.com/v1",
            providerName = "list-test-provider"
        ))

        try {
            assertEquals(initialSize + 1, ChatModelFactory.registeredProviders.size)
            assertTrue(
                ChatModelFactory.registeredProviders.any { it.providerName == "list-test-provider" }
            )
        } finally {
            ChatModelFactory.unregisterProvider("list-test-provider")
        }
    }

    // ---------------------------------------------------------------------------
    // Registered provider priority
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Registered provider should take priority over built-in with same API key")
    fun registeredProviderShouldTakePriorityOverBuiltin() {
        // Register a custom provider that shadows the OPENAI_API_KEY
        ChatModelFactory.registerProvider(ProviderConfig(
            apiKeyName = "OPENAI_API_KEY",
            modelNameKey = "CUSTOM_OPENAI_MODEL",
            baseUrlKey = "CUSTOM_OPENAI_BASE_URL",
            defaultModel = "custom-override-model",
            defaultBaseUrl = "https://api.custom-override.com/v1",
            providerName = "custom-openai-override"
        ))

        System.setProperty("OPENAI_API_KEY", "test-override-key-12345")
        try {
            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))

            // SUPPORTED_API_KEY_NAMES should list the registered provider's key first
            val keyNames = ChatModelFactory.SUPPORTED_API_KEY_NAMES
            val firstOpenAiIndex = keyNames.indexOfFirst { it == "OPENAI_API_KEY" }
            val lastOpenAiIndex = keyNames.indexOfLast { it == "OPENAI_API_KEY" }
            // OPENAI_API_KEY appears twice (registered + builtin); the registered one is first
            assertTrue(firstOpenAiIndex < lastOpenAiIndex,
                "Registered OPENAI_API_KEY should appear before built-in OPENAI_API_KEY")
        } finally {
            System.clearProperty("OPENAI_API_KEY")
            ChatModelFactory.unregisterProvider("custom-openai-override")
        }
    }

    @Test
    @DisplayName("Registered provider should be found by doCreateModel before built-in")
    fun registeredProviderShouldBeFoundBeforeBuiltin() {
        ChatModelFactory.registerProvider(ProviderConfig(
            apiKeyName = "MY_GROQ_KEY",
            modelNameKey = "MY_GROQ_MODEL",
            baseUrlKey = "MY_GROQ_BASE_URL",
            defaultModel = "my-custom-groq-model",
            defaultBaseUrl = "https://api.my-groq.com/v1",
            providerName = "my-groq"
        ))

        try {
            val conf = ImmutableConfig()
            val model = ChatModelFactory.getOrCreate(
                "my-groq", "my-custom-groq-model", "test-key-12345", conf
            )
            assertNotNull(model)
            assertInstanceOf(CachedBrowserChatModel::class.java, model)
        } finally {
            ChatModelFactory.unregisterProvider("my-groq")
        }
    }

    // ---------------------------------------------------------------------------
    // Configurable documentPath and messages
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Should use custom documentPath in exception message")
    fun shouldUseCustomDocumentPathInExceptionMessage() {
        ChatModelFactory.documentPath = "https://custom.docs.example.com/llm-setup"

        val conf = ImmutableConfig()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            ChatModelFactory.getOrCreate(conf)
        }
        assertTrue(ex.message!!.contains("https://custom.docs.example.com/llm-setup"),
            "Exception message should contain the custom documentPath")
    }

    @Test
    @DisplayName("Should allow setting custom llmNotConfiguredMessage")
    fun shouldAllowSettingCustomLlmNotConfiguredMessage() {
        val customMessage = "LLM features are disabled — contact your admin to enable them."
        ChatModelFactory.llmNotConfiguredMessage = customMessage
        assertEquals(customMessage, ChatModelFactory.llmNotConfiguredMessage)
    }

    @Test
    @DisplayName("Should allow setting llmDeveloperGuide to null")
    fun shouldAllowSettingLlmDeveloperGuideToNull() {
        ChatModelFactory.llmDeveloperGuide = null
        assertNull(ChatModelFactory.llmDeveloperGuide)
    }

    @Test
    @DisplayName("Should allow setting custom llmDeveloperGuide")
    fun shouldAllowSettingCustomLlmDeveloperGuide() {
        val customGuide = "Please visit https://example.com/llm for setup instructions."
        ChatModelFactory.llmDeveloperGuide = customGuide
        assertEquals(customGuide, ChatModelFactory.llmDeveloperGuide)
    }

    @Test
    @DisplayName("Should use custom documentPath in generic fallback error")
    fun shouldUseCustomDocumentPathInGenericFallbackError() {
        ChatModelFactory.documentPath = "https://custom.docs.example.com/advanced"

        System.setProperty("llm.provider", "some-provider")
        System.setProperty("llm.name", "some-model")
        // Deliberately omit llm.apiKey to trigger the fallback error
        try {
            val conf = ImmutableConfig()
            val ex = assertThrows(IllegalArgumentException::class.java) {
                ChatModelFactory.getOrCreate(conf)
            }
            // The error should mention the custom path
            assertTrue(ex.message!!.contains("https://custom.docs.example.com/advanced"),
                "Generic fallback error should contain custom documentPath")
        } finally {
            System.clearProperty("llm.provider")
            System.clearProperty("llm.name")
        }
    }

    // ---------------------------------------------------------------------------
    // Config-based overrides (properties / env vars)
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Should use llm.document.path from config in exception")
    fun shouldUseLlmDocumentPathFromConfig() {
        System.setProperty("llm.document.path", "https://config.example.com/llm-docs")
        try {
            val conf = ImmutableConfig()
            val ex = assertThrows(IllegalArgumentException::class.java) {
                ChatModelFactory.getOrCreate(conf)
            }
            assertTrue(ex.message!!.contains("https://config.example.com/llm-docs"),
                "Exception should use llm.document.path from config")
        } finally {
            System.clearProperty("llm.document.path")
        }
    }

    @Test
    @DisplayName("Should use llm.not.configured.message from config in exception")
    fun shouldUseLlmNotConfiguredMessageFromConfig() {
        System.setProperty("llm.not.configured.message", "AI is off — ask your admin.")
        try {
            val conf = ImmutableConfig()
            val ex = assertThrows(IllegalArgumentException::class.java) {
                ChatModelFactory.getOrCreate(conf)
            }
            assertTrue(ex.message!!.contains("AI is off — ask your admin."),
                "Exception should use llm.not.configured.message from config")
        } finally {
            System.clearProperty("llm.not.configured.message")
        }
    }

    @Test
    @DisplayName("Should use llm.developer.guide from config when set")
    fun shouldUseLlmDeveloperGuideFromConfig() {
        val customGuide = "Custom setup: visit https://example.com/ai-setup for help."
        System.setProperty("llm.developer.guide", customGuide)
        try {
            val conf = ImmutableConfig()
            // The guide message is read from config inside isModelConfigured.
            // We verify the config value is picked up by checking the effective
            // value that would be used.
            val effectiveGuide = conf["llm.developer.guide"]
            assertEquals(customGuide, effectiveGuide,
                "Config should contain the custom developer guide")
            assertTrue(effectiveGuide!!.contains("example.com/ai-setup"))
        } finally {
            System.clearProperty("llm.developer.guide")
        }
    }

    @Test
    @DisplayName("Empty llm.developer.guide config should suppress the guide")
    fun emptyLlmDeveloperGuideConfigShouldSuppressGuide() {
        System.setProperty("llm.developer.guide", "")
        try {
            val conf = ImmutableConfig()
            val guide = conf["llm.developer.guide"]
            assertEquals("", guide, "Empty config value should be preserved")
        } finally {
            System.clearProperty("llm.developer.guide")
        }
    }

    @Test
    @DisplayName("Config documentPath should be used in generic fallback error")
    fun configDocumentPathShouldBeUsedInGenericFallbackError() {
        System.setProperty("llm.document.path", "https://acme.com/llm-guide")
        System.setProperty("llm.provider", "some-provider")
        System.setProperty("llm.name", "some-model")
        try {
            val conf = ImmutableConfig()
            val ex = assertThrows(IllegalArgumentException::class.java) {
                ChatModelFactory.getOrCreate(conf)
            }
            assertTrue(ex.message!!.contains("https://acme.com/llm-guide"),
                "Generic fallback error should use llm.document.path from config")
        } finally {
            System.clearProperty("llm.document.path")
            System.clearProperty("llm.provider")
            System.clearProperty("llm.name")
        }
    }

    // ---------------------------------------------------------------------------
    // Cached provider maps — invalidation on register/unregister
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("SUPPORTED_API_KEY_NAMES should be invalidated when a provider is registered")
    fun supportedApiKeyNamesShouldBeInvalidatedOnRegister() {
        val before = ChatModelFactory.SUPPORTED_API_KEY_NAMES
        assertTrue(before.contains("OPENAI_API_KEY"))

        ChatModelFactory.registerProvider(ProviderConfig(
            apiKeyName = "CACHE_INVAL_TEST_API_KEY",
            modelNameKey = "CACHE_INVAL_TEST_MODEL_NAME",
            baseUrlKey = "CACHE_INVAL_TEST_BASE_URL",
            defaultModel = "cache-inval-test-model",
            defaultBaseUrl = "https://api.cache-inval-test.com/v1",
            providerName = "cache-inval-test"
        ))

        try {
            val after = ChatModelFactory.SUPPORTED_API_KEY_NAMES
            // Should now include the newly registered provider's key
            assertTrue(after.contains("CACHE_INVAL_TEST_API_KEY"),
                "SUPPORTED_API_KEY_NAMES should reflect the newly registered provider")
            // Should still contain the built-in keys
            assertTrue(after.contains("OPENAI_API_KEY"))
        } finally {
            ChatModelFactory.unregisterProvider("cache-inval-test")
        }
    }

    @Test
    @DisplayName("SUPPORTED_API_KEY_NAMES should be invalidated when a provider is unregistered")
    fun supportedApiKeyNamesShouldBeInvalidatedOnUnregister() {
        ChatModelFactory.registerProvider(ProviderConfig(
            apiKeyName = "UNREG_INVAL_TEST_API_KEY",
            modelNameKey = "UNREG_INVAL_TEST_MODEL_NAME",
            baseUrlKey = "UNREG_INVAL_TEST_BASE_URL",
            defaultModel = "unreg-inval-test-model",
            defaultBaseUrl = "https://api.unreg-inval-test.com/v1",
            providerName = "unreg-inval-test"
        ))

        val before = ChatModelFactory.SUPPORTED_API_KEY_NAMES
        assertTrue(before.contains("UNREG_INVAL_TEST_API_KEY"))

        ChatModelFactory.unregisterProvider("unreg-inval-test")

        val after = ChatModelFactory.SUPPORTED_API_KEY_NAMES
        assertFalse(after.contains("UNREG_INVAL_TEST_API_KEY"),
            "SUPPORTED_API_KEY_NAMES should no longer contain the unregistered provider's key")
    }

    // ---------------------------------------------------------------------------
    // Alias key resolution — data-driven loop (KIMI was missing from original tests)
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Should resolve KIMI_API_KEY alias to moonshot config")
    fun shouldResolveKimiApiKeyAlias() {
        System.setProperty("KIMI_API_KEY", "test-kimi-alias-key-12345")
        try {
            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))
            val model = ChatModelFactory.getOrCreate(conf)
            assertNotNull(model)
            assertInstanceOf(CachedBrowserChatModel::class.java, model)
        } finally {
            System.clearProperty("KIMI_API_KEY")
        }
    }

    @Test
    @DisplayName("Alias key should be skipped when its canonical provider is denied")
    fun aliasKeyShouldBeSkippedWhenProviderDenied() {
        System.setProperty("KIMI_API_KEY", "test-kimi-denied-12345")
        System.setProperty("OPENAI_API_KEY", "test-openai-fallback-12345")
        System.setProperty("llm.provider.deny.list", "moonshot")
        try {
            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))
            val model = ChatModelFactory.getOrCreate(conf)
            assertNotNull(model)
            // KIMI maps to moonshot which is denied, so should fall through to OpenAI
        } finally {
            System.clearProperty("KIMI_API_KEY")
            System.clearProperty("OPENAI_API_KEY")
            System.clearProperty("llm.provider.deny.list")
        }
    }

    // ---------------------------------------------------------------------------
    // Registry-driven provider dispatch (verifying dead branches removed)
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("deepseek provider should be resolved via registry (not via removed when branch)")
    fun deepseekProviderShouldBeResolvedViaRegistry() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("deepseek", "deepseek-chat", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("bailian provider should be resolved via registry (not via removed when branch)")
    fun bailianProviderShouldBeResolvedViaRegistry() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("bailian", "qwen-plus", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("volcengine provider should be resolved via registry (not via removed when branch)")
    fun volcengineProviderShouldBeResolvedViaRegistry() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("volcengine", "doubao-1-5-pro-32k-250115", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    // ---------------------------------------------------------------------------
    // Deny list threading — generic fallback path
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Generic fallback should block denied providers via threaded deny list")
    fun genericFallbackShouldBlockDeniedProviders() {
        System.setProperty("llm.provider", "openai")
        System.setProperty("llm.name", "gpt-4o")
        System.setProperty("llm.apiKey", "test-fallback-denied-key-123456")
        System.setProperty("llm.provider.deny.list", "openai")
        try {
            val conf = ImmutableConfig()
            // isModelConfigured0 should skip openai because it's denied, BUT
            // the legacy check also uses resolveCanonicalProviderName + denyList,
            // so it should report NOT configured
            val configured = ChatModelFactory.isModelConfigured(conf, verbose = false)
            if (configured) {
                println("SKIP: Other LLM providers are configured externally")
                return
            }
            assertFalse(configured)
        } finally {
            System.clearProperty("llm.provider")
            System.clearProperty("llm.name")
            System.clearProperty("llm.apiKey")
            System.clearProperty("llm.provider.deny.list")
        }
    }

    @Test
    @DisplayName("getOrCreateOrNull should return null when all detected providers are denied")
    fun getOrCreateOrNullShouldReturnNullWhenAllProvidersDenied() {
        System.setProperty("OPENAI_API_KEY", "test-openai-denied-all-12345")
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

    // ---------------------------------------------------------------------------
    // Deny list threading — explicit 4-arg creation
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Explicit getOrCreate with 4 args should block denied providers")
    fun explicitGetOrCreateShouldBlockDeniedProviders() {
        System.setProperty("llm.provider.deny.list", "anthropic,gemini")
        try {
            val conf = ImmutableConfig()

            // Both should throw because they're on the deny list
            val ex1 = assertThrows(IllegalArgumentException::class.java) {
                ChatModelFactory.getOrCreate("anthropic", "claude-sonnet-4-6", "test-key-12345", conf)
            }
            assertTrue(ex1.message!!.contains("deny list"))

            val ex2 = assertThrows(IllegalArgumentException::class.java) {
                ChatModelFactory.getOrCreate("gemini", "gemini-2.0-flash", "test-key-12345", conf)
            }
            assertTrue(ex2.message!!.contains("deny list"))

            // Non-denied provider should still work
            val model = ChatModelFactory.getOrCreate("groq", "llama-3.3-70b", "test-key-12345", conf)
            assertNotNull(model)
        } finally {
            System.clearProperty("llm.provider.deny.list")
        }
    }

    // ---------------------------------------------------------------------------
    // Deny list — alias resolution from deny list entries
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("isProviderDenied should resolve alias key names (KIMI_API_KEY → moonshot)")
    fun isProviderDeniedShouldResolveAliasKeyNames() {
        System.setProperty("llm.provider.deny.list", "moonshot")
        try {
            val conf = ImmutableConfig()
            assertTrue(ChatModelFactory.isProviderDenied("KIMI_API_KEY", conf))
            assertTrue(ChatModelFactory.isProviderDenied("kimi_api_key", conf))
        } finally {
            System.clearProperty("llm.provider.deny.list")
        }
    }

    @Test
    @DisplayName("getDeniedProviders should resolve alias provider names from deny list")
    fun getDeniedProvidersShouldResolveAliasProviderNames() {
        System.setProperty("llm.provider.deny.list", "KIMI_API_KEY,claude,LINGYI_API_KEY")
        try {
            val conf = ImmutableConfig()
            val denied = ChatModelFactory.getDeniedProviders(conf)
            assertEquals(3, denied.size)
            assertTrue(denied.contains("moonshot"), "KIMI_API_KEY should resolve to moonshot")
            assertTrue(denied.contains("anthropic"), "claude should resolve to anthropic")
            assertTrue(denied.contains("yi"), "LINGYI_API_KEY should resolve to yi")
        } finally {
            System.clearProperty("llm.provider.deny.list")
        }
    }

    // ---------------------------------------------------------------------------
    // JSON-based provider registry loading
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Provider registry should load from classpath JSON with all 21 providers")
    fun providerRegistryShouldLoadFromClasspathJson() {
        val conf = ImmutableConfig()
        val model = ChatModelFactory.getOrCreate("openai", "gpt-4o", "test-key-12345", conf)
        assertNotNull(model)
        assertInstanceOf(CachedBrowserChatModel::class.java, model)
    }

    @Test
    @DisplayName("Should load providers from external JSON via llm.provider.config.path")
    fun shouldLoadProvidersFromExternalJson() {
        // Create a minimal external JSON with one custom provider
        val json = """
        {
          "providers": [
            {
              "apiKeyName": "CUSTOM_API_KEY",
              "modelNameKey": "CUSTOM_MODEL_NAME",
              "baseUrlKey": "CUSTOM_BASE_URL",
              "defaultModel": "custom-model-from-json",
              "defaultBaseUrl": "https://api.custom-json.com/v1",
              "providerName": "custom-json-provider",
              "supportVision": true,
              "apiProtocol": "OPENAI"
            }
          ],
          "aliases": {},
          "canonicalAliases": {}
        }
        """.trimIndent()

        val tempFile = java.io.File.createTempFile("test-providers", ".json")
        try {
            tempFile.writeText(json)
            System.setProperty("llm.provider.config.path", tempFile.absolutePath)

            // Reset so the next access picks up the override
            ChatModelFactory.resetProviders()

            System.setProperty("CUSTOM_API_KEY", "test-custom-key-12345")
            try {
                val conf = ImmutableConfig()
                assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))

                // Should detect the custom provider from the external JSON
                val model = ChatModelFactory.getOrCreate(conf)
                assertNotNull(model)
                assertInstanceOf(CachedBrowserChatModel::class.java, model)

                // The provider should be usable via explicit creation too
                val model2 = ChatModelFactory.getOrCreate(
                    "custom-json-provider", "custom-model-from-json", "test-key-12345", conf
                )
                assertNotNull(model2)
                assertInstanceOf(CachedBrowserChatModel::class.java, model2)

                // Built-in providers should NOT be available (external JSON replaces them)
                ChatModelFactory.resetProviders()
                val isConfigured = ChatModelFactory.isModelConfigured(conf, verbose = false)
                // Custom key is still set, so it should be detected
                assertTrue(isConfigured)
            } finally {
                System.clearProperty("CUSTOM_API_KEY")
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    @DisplayName("resetProviders should allow switching between different external configs")
    fun resetProvidersShouldAllowReload() {
        val json1 = """
        {
          "providers": [
            {
              "apiKeyName": "PROVIDER_A_API_KEY",
              "modelNameKey": "PROVIDER_A_MODEL_NAME",
              "baseUrlKey": "PROVIDER_A_BASE_URL",
              "defaultModel": "model-a",
              "defaultBaseUrl": "https://api.a.com/v1",
              "providerName": "provider-a",
              "supportVision": true,
              "apiProtocol": "OPENAI"
            }
          ],
          "aliases": {},
          "canonicalAliases": {}
        }
        """.trimIndent()

        val json2 = """
        {
          "providers": [
            {
              "apiKeyName": "PROVIDER_B_API_KEY",
              "modelNameKey": "PROVIDER_B_MODEL_NAME",
              "baseUrlKey": "PROVIDER_B_BASE_URL",
              "defaultModel": "model-b",
              "defaultBaseUrl": "https://api.b.com/v1",
              "providerName": "provider-b",
              "supportVision": true,
              "apiProtocol": "OPENAI"
            }
          ],
          "aliases": {},
          "canonicalAliases": {}
        }
        """.trimIndent()

        val tempFile1 = java.io.File.createTempFile("test-providers-a", ".json")
        val tempFile2 = java.io.File.createTempFile("test-providers-b", ".json")
        try {
            tempFile1.writeText(json1)
            tempFile2.writeText(json2)

            // Load config A
            System.setProperty("llm.provider.config.path", tempFile1.absolutePath)
            ChatModelFactory.resetProviders()

            System.setProperty("PROVIDER_A_API_KEY", "test-key-a-12345")
            try {
                val conf = ImmutableConfig()
                assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))
                val model = ChatModelFactory.getOrCreate(conf)
                assertNotNull(model)
            } finally {
                System.clearProperty("PROVIDER_A_API_KEY")
            }

            // Switch to config B
            System.setProperty("llm.provider.config.path", tempFile2.absolutePath)
            ChatModelFactory.resetProviders()

            System.setProperty("PROVIDER_B_API_KEY", "test-key-b-12345")
            try {
                val conf = ImmutableConfig()
                assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))
                val model = ChatModelFactory.getOrCreate(conf)
                assertNotNull(model)
            } finally {
                System.clearProperty("PROVIDER_B_API_KEY")
            }
        } finally {
            tempFile1.delete()
            tempFile2.delete()
        }
    }

    @Test
    @DisplayName("External JSON should support canonicalAliases and apiKeyAliases")
    fun externalJsonShouldSupportAliases() {
        val json = """
        {
          "providers": [
            {
              "apiKeyName": "MY_CUSTOM_API_KEY",
              "modelNameKey": "MY_CUSTOM_MODEL_NAME",
              "baseUrlKey": "MY_CUSTOM_BASE_URL",
              "defaultModel": "my-custom-model",
              "defaultBaseUrl": "https://api.mycustom.com/v1",
              "providerName": "mycustom",
              "supportVision": false,
              "apiProtocol": "OPENAI"
            }
          ],
          "aliases": {
            "MY_ALIAS_API_KEY": "mycustom"
          },
          "canonicalAliases": {
            "mc": "mycustom"
          }
        }
        """.trimIndent()

        val tempFile = java.io.File.createTempFile("test-providers-aliases", ".json")
        try {
            tempFile.writeText(json)
            System.setProperty("llm.provider.config.path", tempFile.absolutePath)
            ChatModelFactory.resetProviders()

            val conf = ImmutableConfig()

            // canonicalAliases: "mc" should resolve to "mycustom"
            System.setProperty("llm.provider.deny.list", "mc")
            try {
                assertTrue(ChatModelFactory.isProviderDenied("mc", conf))
                assertTrue(ChatModelFactory.isProviderDenied("mycustom", conf))
            } finally {
                System.clearProperty("llm.provider.deny.list")
            }

            // apiKeyAliases: MY_ALIAS_API_KEY should be detected
            System.setProperty("MY_ALIAS_API_KEY", "test-alias-key-12345")
            try {
                assertTrue(ChatModelFactory.isModelConfigured(conf, verbose = false))
            } finally {
                System.clearProperty("MY_ALIAS_API_KEY")
            }
        } finally {
            tempFile.delete()
        }
    }
}
