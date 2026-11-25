# UAT 利润查询工具参数兼容修复

- runId：964cdc49-e9d5-47ae-9e2a-746c443b9084。
- 问题：“目前哪个项目利润最高”未执行工具即失败。
- UAT 记录：意图正确切换 PROJECT_PROFIT，业务模型阶段 RequestRejectedException / Model tool schema is invalid，无工具执行记录。
- 原因：ProjectProfitQueryTool 的参数含标准 enum 约束，OpenAiCompatibleModelGateway 的受支持字段白名单未包含 enum。任一允许工具无法解析导致整轮请求构建失败，与排名 SQL、数据权限或轮数上限无关。
- 修改：网关支持基本类型 enum，保留原始 schema 发送模型；校验枚举非空、值类型一致、值不重复，并校验模型返回参数属于枚举范围。对象及数组不扩大支持范围。
- 验证：按用户要求仅编译，不启动服务或执行业务验收。
- 用户测试：重启 smart-agent；原会话重新问“目前哪个项目利润最高”，应执行 projectProfit.rank，并说明默认排序指标。再问“查询各项目预算利润”“比较两个指定项目利润”，确认利润域工具可用。
- smart-boot、smart-web 和数据库无需更新。状态：待 UAT 人工验收。
