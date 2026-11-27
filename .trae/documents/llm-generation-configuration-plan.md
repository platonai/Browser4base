# LLM 生成与思考配置优化计划

## Context

当前 LLM 客户端只配置 API Key、模型名、Base URL、重试和超时；`ChatModelSettings` 也仅暴露输入 token 上限。用户无法配置 temperature、topP、输出长度、停止词及思考强度，且模型缓存键不含生成参数。此次优化要补齐常用生成配置，让思考参数真实传入对应客户端，同时保持未配置时的现有行为，不重构 Provider Registry 或公共聊天接口。

已核对项目版本为 `4.11.34-SNAPSHOT`，现有 LangChain4j 1.15.0 API 足够实现，无需升级依赖。

## 配置设计

新增内部 `ChatGenerationSettings`，所有字段默认 `null`，未配置时不调用对应 Builder 方法：

| 全局配置键 | 类型 | 客户端映射 |
| --- | --- | --- |
| `llm.temperature` | Double | OpenAI / Anthropic / Gemini `temperature` |
| `llm.top.p` | Double | OpenAI / Anthropic / Gemini `topP` |
| `llm.max.output.tokens` | 正整数 | OpenAI / Anthropic `maxTokens`；Gemini `maxOutputTokens` |
| `llm.stop.sequences` | 逗号分隔列表 | 各协议的 stop sequences |
| `llm.reasoning.effort` | `minimal/low/medium/high` | OpenAI `reasoningEffort`；Gemini `thinkingLevel` |
| `llm.reasoning.budget.tokens` | 正整数 | Anthropic `thinkingType=enabled` + `thinkingBudgetTokens`；Gemini `thinkingBudget` |

支持 canonical Provider 覆盖，Provider 值优先于全局值，例如：

```properties
llm.reasoning.effort=medium
llm.provider.openai.reasoning.effort=high
llm.provider.anthropic.reasoning.budget.tokens=4096
llm.provider.gemini.temperature=0.2
```

沿用现有 relaxed configuration，因此环境变量形式如 `LLM_REASONING_EFFORT`、`LLM_PROVIDER_OPENAI_REASONING_EFFORT` 也可使用。非法数字、非正 token 数、非法 effort 应报告带配置键的错误。

OpenAI + token budget、Anthropic + effort 属于当前客户端 API 明确不支持的组合，应在创建模型时快速失败并提示替代配置，不静默忽略。服务端模型是否支持已透传参数仍由 Provider 校验，不新增模型能力矩阵。`none/off/xhigh`、显式关闭思考、返回思维内容及 `max_completion_tokens` 等 Provider 专项能力不在本次范围内。

## 实施步骤

1. **按项目功能开发流程建立规格**
   - 在 `.trae/specs/llm-generation-configuration/` 编写 `spec.md` 与 `tasks.md`，记录上述配置语义、支持矩阵、兼容性和验收标准。

2. **增加配置常量与纯配置模型**
   - 修改 `pulsar-core/pulsar-common/src/main/java/ai/platon/pulsar/common/config/CapabilityTypes.java`，声明六个全局 LLM 配置键。
   - 修改 `pulsar-core/pulsar-third/pulsar-llm/src/main/kotlin/ai/platon/pulsar/external/ChatModelSettings.kt`，增加内部 effort 枚举、`ChatGenerationSettings`、可空配置解析、Provider 覆盖、校验及稳定缓存键。
   - 保留现有 `llm.max.input.token.length`，不把输入与输出 token 混为一项。

3. **将配置接入所有模型创建路径**
   - 修改 `pulsar-core/pulsar-third/pulsar-llm/src/main/kotlin/ai/platon/pulsar/external/ChatModelFactory.kt`。
   - 复用 `createModel`、`getOrCreateModel0`、`dispatchProtocol` 和各协议创建函数；以私有重载/内部辅助函数传递 effective settings，不改变现有公开方法签名。
   - 为 OpenAI、Anthropic、Gemini 分别增加小型 Builder 配置函数；原生 Anthropic 与 Anthropic-compatible 共用同一函数。
   - 自动选择、显式 Provider、通用 OpenAI/Anthropic-compatible、Gemini、MiniMax 等入口都使用相同解析逻辑；无 Provider 身份的通用入口只应用全局配置。

4. **修正缓存隔离**
   - 将完整生成配置缓存键加入 `getOrCreateModel0` 及各专用 `getOrCreate...` 方法的现有缓存键。
   - 配置相同继续复用客户端，仅 temperature、effort 等任一参数不同则不得复用；不修改响应缓存逻辑或 `CachedBrowserChatModel` 调用接口。

5. **补充离线单元测试**
   - 扩充 `LLMConfigTest.kt`：默认空值、全局/环境变量风格解析、Provider 覆盖、大小写归一化、停止词顺序、非法值和非正 token 校验。
   - 扩充 `ChatModelFactoryTest.kt`：读取 `CachedBrowserChatModel.langchainModel` 的公开默认请求参数，验证三个协议的生成参数；用内部纯映射函数验证 Anthropic/Gemini thinking 配置；验证不支持组合快速失败及缓存隔离。
   - 测试使用 `MutableConfig(false)` 和唯一模型名，避免环境变量与既有缓存污染；不发起网络请求，测试方法保持 camelCase 并添加 `@DisplayName`。

6. **更新现有配置说明**
   - 在 `application.properties` 增加默认注释掉的全局及 Provider 覆盖示例。
   - 更新 `docs/config/llm/llm-config.md`：参数表、默认值、覆盖顺序、协议支持矩阵和缓存说明。
   - 更新 `docs/config/llm/llm-chat-model-factory-howto.md` 的缓存键说明。
   - 不修改 `providers.json`、`ProviderConfigLoader`、POM 或公共 `BrowserChatModel` API。

7. **独立审查与修复**
   - 实现和测试完成后提交独立子代理审查，按审查意见修复并复审通过，重点检查所有创建入口、缓存隔离、默认行为兼容和协议映射。

## 验证

- 运行 `./mvnw.cmd compile -q`，确认跨模块编译通过。
- 精准运行 `pulsar-core/pulsar-third/pulsar-llm` 中新增/修改的离线测试；不直接运行包含真实 Doubao 请求的整个既有 `ChatModelFactoryTest`。
- 运行 `./mvnw.cmd test -pl pulsar-core/pulsar-common`，并运行 `pulsar-llm` 其余可离线执行的测试；明确排除已确认会发出真实请求的既有测试，不用外部服务结果冒充离线验证。
- 核对未配置时请求默认参数保持原状；配置全局值、Provider 覆盖和不同思考参数后，Builder 默认值与缓存实例身份符合预期。
