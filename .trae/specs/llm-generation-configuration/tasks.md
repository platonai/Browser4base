# LLM 生成与思考配置任务

- [x] 1. 在 `CapabilityTypes` 声明新增全局配置键。
- [x] 2. 在 `ChatModelSettings.kt` 实现 effort 枚举、生成配置模型、relaxed 解析、Provider 覆盖、校验和缓存键。
- [x] 3. 在 `ChatModelFactory` 中把生成配置传入 OpenAI、Anthropic、Gemini Builder，并覆盖全部创建入口。
- [x] 4. 把完整生成配置加入各模型缓存键。
- [x] 5. 扩充 `LLMConfigTest`，覆盖默认值、解析、覆盖和非法输入。
- [x] 6. 扩充 `ChatModelFactoryTest`，覆盖 Builder 参数、thinking 映射、不支持组合和缓存隔离。
- [x] 7. 更新 `application.properties`、LLM 配置指南和模型工厂缓存文档。
- [x] 8. 编译并精准运行离线测试，修复问题。
- [x] 9. 完成独立审查、意见修复和复审。
