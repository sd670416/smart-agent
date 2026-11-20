# H 统一接入与风险总览实施计划

**Goal:** 将B至G接入真实智能助手链路，统一权限、澄清、上下文、实时查询和八维风险总览。
**Architecture:** 读取各任务handoff及已合入实现，分批更新公共接入点；不重写业务公式。通过ProjectRiskProvider列表编排F/G，按维度/来源权限过滤并标记未评估。
**Tech Stack:** Agent Java21、Boot Java8、既有BusinessDomainContributor/Clarification/ToolContext。
**Spec:** [总计划](00-start-here.md)、[已确认业务方案](../2026-11-20-business-analysis-scenarios-proposal.md)、A至G-handoff.md。

## 独占共享文件

- Agent `src/main/java/com/smart/agent/routing/QueryIntentResolver.java`、必要公共domain/context/clarification目录。
- Agent `src/main/java/com/smart/agent/chat/ChatOrchestrator.java`、`src/main/java/com/smart/agent/model/SystemInstructionCatalog.java`及系统使用的公共字段注册。
- Boot找到实际AuthenticatedAgentContextFactory路径、内部API安全配置、权限映射和公共异常映射后修改；先rg定位，不能按不存在路径创建第二个工厂。
- 新建Agent `ProjectOperationDomainContributor`、`PartnerHistoryDomainContributor`、`ProjectRiskDomainContributor`；既有ProjectProfitDomainContributor只追加rank，不注册重复CODE。
- 风险Boot：`smart-service/.../analysis/risk/ProjectRiskQueryService.java`、`smart-model/.../analysis/risk/ProjectRiskQueryRequestVO.java`、`smart-business/smart-project/.../analysis/risk/ProjectRiskQueryServiceImpl.java`、`smart-ai/.../controller/AiProjectRiskToolController.java`。
- 风险Agent：`src/main/java/com/smart/agent/tool/projectrisk/` 下Input、Tool、Client、Configuration。

不改B至G独占源码；集成发现公式错误写回任务handoff并由原Owner修复。H可以做公共适配，不将业务逻辑塞回ChatOrchestrator。

## 任务顺序

- [ ] 读取当前脏改动及所有已合入handoff，确认服务实现可注入、工具key唯一、内部路径唯一、包扫描和Mapper扫描有效。
- [ ] 对照UAT实际菜单多层级与权限总开关映射真实业务权限；缺权限反馈对应内容，不能把合同无权限说成项目无权限。system按既有账号规则，不依赖角色。
- [ ] 先接B：产值＋甲方回款组合进入PROJECT_OPERATION；普通产值单仍原域。再接C、D、E，逐批提供用户验收问题。
- [ ] 新增问题理解依赖模型和域描述；相似字段由当前域目录解析，指代/范围不清先澄清。普通项目、审批、材料价既有链路保持可用。
- [ ] 预算利润排名可默认，答复明确指标/方向/数量；有明确用户指标不覆盖。C同名合作方先选类型/真实ID；E支持多项目和基准切换。
- [ ] 上下文持久化完整有效参数和真实ID，后续“下一页/加一个/改指标/只看高风险”必须实时重查。显式切换到其他模块覆盖旧域，不强行锁定。
- [ ] 日期未指定CURRENT。期间发生/截至累计不清追问；来源不支持明确说明，不能自己改成项目创建日期或CURRENT。
- [ ] 审查每个新增工具schema只用当前适配器支持关键字；针对此前minItems/maxItems错误逐个检查，避免在模型请求前失败。
- [ ] 风险总览工具`projectRisk.query`，POST`/internal/ai/tools/project-risk-query`。参数projectId/Code/Name、dimensions、level、mode(summary/detail/evidence)、dateMode、page/pageSize。
- [ ] 只调用请求的已注册Provider维度。未注册/无权限/缺来源输出NOT_EVALUATED，不生成虚假的正常或0；空可信项目集合返回空。
- [ ] 查询全量评估后筛选风险等级及分页，风险项目数按projectId去重，风险条数单算；项目最高等级与未评估维度同时显示。
- [ ] evidence下钻重新核对当前项目/来源权限，不信任历史ID；默认只读，不自动催款、发消息、办审批或改业务状态。
- [ ] 规则sys_config初始SQL如需写库，先生成幂等脚本与目标库清单，得到用户授权后执行；本任务默认只有安全默认值和只读配置支持。
- [ ] 检查不同业务的错误消息、模型返回内容、表格中文列、金额元/万元单位及百分点；技术细节仅AI处理信息。
- [ ] Agent与Boot覆盖业务实现编译通过，用户未要求不启动服务。编译结果不等于工具运行通过，明确需人工验证。
- [ ] 写H-handoff，汇总已开发/已接入/待人工验收项目，更新交接索引；最终合并由用户执行。

## 必查交互例子

1. “查看项目”→“A项目产值和回款”→“它的预算利润率”→“做个排名”→“改查我的待办”：每轮到正确域并实时查。
2. “某甲方合作历史”→同名确认→“只看去年签约”→“看第一份合同”：日期选择合同，详情按真实ID。
3. “比较A、B、C”→“以B为基准”→“删掉A”：保留项目次序和真实ID，重新取数。
4. “哪些项目经营有风险”→“只看高风险”→“A为什么有质量风险”→“看对应记录”：保留规则证据，重新检查权限。
5. 少来源权限账号、system、权限开关关闭/打开、项目成员范围变化后追问：不能仅依赖历史范围。
6. 无合同、无预算、零分母、负利润、退款、缺检查、未开工、停工、未完工：不把缺失变0，不误报最终延期或税务违规。

## 交付节奏

H可在A之后先整理接入点，但不要引用尚未合入的类导致主分支编译失败。每次只接已交付任务，给用户对应重启/问题/预期。前端默认继续已有聊天Markdown，无新页面；确需页面改动另列任务，不插入本次并行范围。
