# LLM 生成与思考配置规格

## 背景

当前模型工厂只设置连接相关参数，无法通过统一配置控制生成行为和思考强度。本功能补齐常用生成参数，并确保配置真实传入 LangChain4j 客户端、参与模型缓存身份。

## 需求

1. 支持全局配置：`llm.temperature`、`llm.top.p`、`llm.max.output.tokens`、`llm.stop.sequences`、`llm.reasoning.effort`、`llm.reasoning.budget.tokens`。
2. 支持 `llm.provider.<canonical-provider>.<parameter>` 覆盖对应全局值，沿用 relaxed configuration。
3. 所有新增值默认未设置；未配置时不调用对应 Builder 方法，保持现有客户端默认行为。
4. 数值和枚举解析失败时给出包含配置键的错误；输出 token 数和思考 token 预算必须为正整数。
5. `llm.reasoning.effort` 支持 `minimal`、`low`、`medium`、`high`，大小写不敏感。
6. `llm.stop.sequences` 使用逗号分隔，去除元素两端空白并保持顺序。
7. 生成参数必须覆盖自动选择、显式 Provider 和各专用模型创建入口；无 Provider 身份的通用入口只读取全局值。
8. 模型缓存键包含完整生成配置：相同配置复用实例，不同配置隔离实例。

## 协议映射

| 参数 | OpenAI-compatible | Anthropic-compatible | Gemini |
| --- | --- | --- | --- |
| temperature | temperature | temperature | temperature |
| topP | topP | topP | topP |
| maxOutputTokens | maxTokens | maxTokens | maxOutputTokens |
| stopSequences | stop | stopSequences | stopSequences |
| reasoningEffort | reasoningEffort | 不支持，快速失败 | thinkingLevel |
| reasoningBudgetTokens | 不支持，快速失败 | thinkingType=enabled + thinkingBudgetTokens | thinkingBudget |

对明确不兼容的协议/参数组合，在模型创建阶段抛出 `IllegalArgumentException`，提示 Provider、配置键及替代项。模型端能力仍由服务端校验，不维护额外模型能力矩阵。

## 非目标

- 不修改 Provider Registry、`providers.json`、公共 `BrowserChatModel` 接口或响应缓存。
- 不支持 `none/off/xhigh`、显式关闭思考、返回思维内容或 OpenAI 专项 `max_completion_tokens`。
- 不为生成参数设置新的业务默认值。

## 验收标准

- 空配置下新增字段均为 `null`，现有创建方式与公开签名不变。
- 全局和 Provider 配置均正确解析，Provider 覆盖仅影响指定 canonical Provider。
- 三个协议 Builder 获得各自支持的参数，thinking 映射符合上表。
- 非法配置和已知不支持组合快速失败。
- 缓存对相同配置返回同一实例，对 generation settings 不同的配置返回不同实例。
- 新增测试不需要浏览器、网络或外部服务；相关模块编译通过。
- 配置示例、参数文档与缓存说明同步更新。
- 完成后经独立审查，问题修复并复审通过。
