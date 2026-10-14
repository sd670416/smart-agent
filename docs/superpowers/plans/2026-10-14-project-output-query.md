# 工程产值独立查询实施

## 已确认范围

延续 `2026-10-12-contract-output-query-migration.md`：本次只实现工程产值列表、数量、本期完成产值合计及Agent独立域。暂不做两组比较、按ID下钻、劳务/专包产值、其他看板迁移。原业务页面及看板接口不改。

## Global Constraints

- 沿用当前检出，不切分支、不提交，保留问题记录及用户既有改动；不启动项目，不运行实际数据库写入。
- 工具 `projectOutput.query`，域 `PROJECT_OUTPUT`，中文名工程产值；与 `project.query`、`constructionContract.query`、`approval.query` 分离。
- 首批权限沿用施工合同已确认约定：`menu:project` 和可信可见项目范围，不用看板菜单授权。不新增叶子菜单权限限制。模型不得指定用户、租户、授权范围。
- 源单据和项目均未删除且审批完成2；项目状态排除1/3/10。空可信范围不得全表查询。所有页、汇总均受此限制。
- 金额来自 `bus_pm_output_report.current_period_output_value` 精确求和；累计完成产值只展示不合计。原经营看板“累计结算金额”是此指标的别名，回复须说明来源是工程产值，不能混成合同最终结算。
- 日期白名单：`projectCreateDate`、`createDate`；含开始与结束日，结束日次日零点排他上界。日期不明确走澄清；相对日期先取当前时间。
- 项目定位沿用ProjectReferenceResolver，默认精确名称/编号，重名确认；明确包含才允许contains，不因匹配失败扩大范围。项目追问继承确认ID，但每轮重新查询并重新校验权限。
- 工具返回结果交模型生成回答；不以历史结果或分页明细相加冒充实时总额。保留已有审批/合同/项目/问题记录功能。
- 不明确的单独“产值”需区分工程、劳务、专包，不能一律硬路由工程产值。明确工程产值或上下文已有工程产值才使用本域。

## Task 1: 工程产值后端和Agent完整只读链路

1. 先补行为测试并观察缺失功能失败，再写实现；测试精确金额、日期、权限、项目范围、分页、路由、Schema与上下文实时刷新。
2. smart-boot新增ProjectOutputQueryRequestVO、ProjectOutputQueryService、ProjectOutputAiQueryService、AiProjectOutputToolController，DAO新增独立queryForAi及XML。内部接口 `/internal/ai/tools/project-output-query`。
3. 请求沿用施工合同结构：projectIds（网关仅可信范围交集）、projectName、outputReportNo、mode detail/summary、dateField、startDate/endDate、page/pageSize（从1，1..100，默认20）。Agent另有projectId/projectCode/projectNameMatch并经现有Resolver定位。
4. 明细列至少id/projectId/projectName/projectCode/outputReportNo/currentPeriodQuantityTotal/currentPeriodOutputValue/cumulativeOutputValue/createDate/approvalStatus，列名中文。汇总为outputCount与currentPeriodOutputValue，空集合金额为精确0。金额不使用double。
5. Agent新增output包客户端/输入/query与日期澄清工具、PROJECT_OUTPUT contributor；接通现有域注册、工具许可、意图、权限文案、查询快照、日期/项目确认恢复、当前项目指代、实时刷新。相对日期不使用固定时间。
6. 只对必要公共扩展点作小改动；不为了新域复制整段编排，不做无关重构。检查现有constructionContract各接入点，防漏Schema/快照/日期恢复。
7. 运行后端定向测试和Agent全量测试，不启动应用；执行只读审查并修重要问题。报告真实未验证项。

## 预检

| 任务/接口 | 生产者与消费者 | 结论 |
| --- | --- | --- |
| 后端查询→内部接口 | 结构化结果ProjectQueryResultVO，可信projectIds | 沿用施工合同契约；金额字段换为本期值 |
| 内部接口→Agent客户端 | 签名上下文+JSON请求/响应 | 同已有client方式，保留租户和权限 |
| Agent→编排与确认 | 独立域、快照、日期/项目确认 | 必须覆盖追问及每轮取数，不能只注册工具 |
| 工程/劳务/专包 | 单独产值自然语言有歧义 | 使用澄清，不拓展其他域工具 |
| Task 1自洽 | 列表/汇总与日期/项目条件 | 仅白名单字段，不开放任意SQL或未知字段模型筛选 |

## 进度

- [x] Task 1 实现与测试：Agent全量391项通过，后端新旧联合18项通过，0失败/0错误。
- [ ] Task 1 规格/质量审查（正在进行）
- [x] 联合范围审查、文档、启动和人工测试说明已补齐；服务端/真实DB/模型端到端留给用户验收

## 验收话术

- 查询工程产值列表；随后“合计多少”“下一页”：保持工程产值域，每轮新查询。
- 查询项目编号20260708001的工程产值：先在可见范围精确定位项目。
- 按产值单创建日期统计2026年1月工程产值总额：createDate含整月。
- 按项目创建日期统计2026年1月的累计结算金额：工程产值sum(currentPeriodOutputValue)，说明别名。
- 2026年1月工程产值多少：日期维度不足时提供可点击确认，选择后继续原查询。
- 本月创建的工程产值单总额：先查当前时间，再查询本期金额。
- 查询产值：上下文不足需区分工程/劳务/专包。
- 查我的待办：立即切换审批域；随后明确工程产值重新进入本域。
- 无菜单、空范围、不同账号：不能经旧看板工具绕过查询权限。

实施记录、测试和限制已回填。

## 2026-10-14 自动化证据

- 实现报告：`2026-10-14-project-output-report.md`，包含RED/GREEN、文件清单和实际未验证项。
- Agent：`mvn -o test`，391 tests，0 failures/0 errors/0 skipped。
- Boot：`mvn -o -pl smart-ai,smart-business/smart-pm -am '-Dtest=ProjectOutputAiQueryServiceTest,AiProjectOutputToolControllerTest,ConstructionContractAiQueryServiceTest,AiConstructionContractToolControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，18项（新11+原7）通过。
- 两仓库`git diff --check`通过，仅Windows换行提示。没有启动应用、提交代码、切分支或改写数据库；smart-web未改动。
- 真实模型、真实数据库SQL/租户执行、金额对账与页面操作待人工验收；不能用本次单测替代端到端验收。

## 启动与验收

本次更新需重新构建并重启smart-boot、smart-agent；不需更新smart-web。本次没有新增数据库结构迁移，也没有新增菜单或角色授权。

先发送“查询工程产值列表”，确认工具是`projectOutput.query`，列含产值单号、项目、本期完成量、本期完成产值、累计完成产值及审批状态；再问“合计多少”，应再次调用同工具且mode=summary，不汇总当前页。

使用同账号、同可见项目范围和相同日期维度对账。AI只取审批完成单据；业务页面如果含审批中记录，不能直接以页面全量合计验收。迁入经营看板的“累计结算金额”按项目创建日期筛项目，再汇总关联的审批完成产值单本期值；明确按产值单创建日期查询时才用createDate。

其余日期确认、重名确认、相对日期、类型歧义和审批切换话术见上方验收表。按产值单ID下钻和两组比较继续留待后续阶段。
