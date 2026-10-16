# 其他回款查询实施记录

## 已确认范围

其他回款归财务模块，实际来源为其他收入登记页面 `/finance/bus-finance-other-income-registration`，原业务接口 `/finance/busFinanceOtherIncomeRegistration/page`、`list`、`get`。对照经营看板代码，金额来源为 `bus_finance_other_income_registration.received_amount`，不是其他付款申请，也不是回款登记。

本批支持独立列表、数量、到账金额合计、项目筛选、到账日期/登记创建日期/项目创建日期范围筛选。日期不明确追问。结束日期包含全天。只查询审批完成记录，项目审批完成并排除状态 1/3/10；菜单及可信可见项目范围由服务端控制。每轮实时查询并把结果交回模型，面向用户只输出中文业务表达。

原表单也支持不关联项目的其他收入。本批按已确认的可见项目范围，仅查询关联可见项目的记录，未关联项目的公司级记录不在本批数据范围内；金额对账必须采用相同范围。

不合并回款登记，不接管全部已收项目款，不开发详情下钻或两组比较。不切分支、不提交、不启动应用、不连接数据库。

## 进度

| 任务 | 状态 |
| --- | --- |
| 页面、实体、原看板来源核对 | 已完成代码核对 |
| 后端独立查询、菜单和可信项目权限 | 已开发，定向测试通过，待验收 |
| Agent 业务域、工具、日期确认及实时追问 | 已开发，全量测试通过，待验收 |
| 自动回归及安全复核 | 全量449项、定向复跑60项及后端43项通过；独立只读审查未发现可确认缺陷 |
| 真实模型、页面金额及权限验收 | 待验收 |

## 验收话术

查询其他回款列表；查询其他收入登记；追问数量、到账金额合计、下一页；按到账日期查询2026年1月的其他回款金额；查询2026年1月其他回款（应先确认日期）；查询指定项目的其他回款；随后查我的待办；随后查询回款登记，确认两个业务域不混淆。

使用没有其他收入登记菜单、仅财务父菜单、仅回款登记菜单、仅看板菜单、空项目范围和 system 账号分别测试。金额与同账号、同审批及日期条件的其他收入登记对账，不与完整已收项目款直接比较。

## 预计重启范围

smart-boot、smart-agent；smart-web 不修改。无需数据库结构变更。最终测试结果在开发结束后回填，不将自动测试等同于端到端验收。

## 实现与验证记录

- 新增独立业务域 `OTHER_INCOME_REGISTRATION`、`otherIncomeRegistration.query` 和日期确认工具，内部接口 `/internal/ai/tools/other-income-registration-query`。使用现有结果契约，中文列名、分页明细、精确数量及到账金额合计，不改变前端渲染链路。
- “其他回款”“其他收入登记”均路由到新域；显式“回款登记”切回原回款登记域，相关待办/已办/发起流程仍走审批。只有原回款登记权限时不能查询其他回款，更不能回退到看板绕过授权。
- 服务端递归识别其他收入登记叶子菜单，生成独立权限 `menu:finance:other-income-registration`。财务父菜单、回款登记菜单、看板菜单不能授予此权限；system 账号直接授予，不依赖角色。继续采用现有可信项目范围，请求项目再次取交集，空范围不执行查询。
- 同名项目先确认，确认后保存真实 ID；上下文帮助理解，不替代每轮查询。日期选择恢复原查询条件，相对日期先取当前时间，结束日采用次日零点前的范围。
- 先添加测试并观察到缺少新业务实现的编译失败，再添加实现。Agent `mvn -o test` 全量449项通过；清理新测试中的旧产值命名后，聊天/路由/输入/签名客户端/模型网关定向60项复跑通过。
- 后端命令：`mvn -o -pl smart-ai,smart-business/smart-finance -am '-Dtest=OtherIncomeRegistrationAiQueryServiceTest,AiOtherIncomeRegistrationToolControllerTest,OtherIncomeRegistrationMenuResolverTest,AgentContextFactoryTest,PaymentRegistrationAiQueryServiceTest,AiPaymentRegistrationToolControllerTest,PaymentRegistrationMenuResolverTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`。两轮通过，最后一次共43项（smart-ai 29项、smart-finance 14项），0失败/错误/跳过。
- Agent 定向命令：`mvn -o '-Dtest=OtherIncomeRegistrationChatTest,OtherIncomeRegistrationRoutingTest,OtherIncomeRegistrationQueryInputTest,SmartBootOtherIncomeRegistrationBusinessClientTest,ModelGatewayContractTest' test`。
- 未连接真实数据库、未启动应用或调用真实模型，因此页面菜单配置、真实模型自然语言表现及页面金额对账仍需用户验收。本批状态为待验收，不代表已上线。
- 独立只读代码审查已完成，未发现可确认缺陷。两个仓库 `git diff --check` 通过，仅有换行符提示；未切分支、提交或改动前端。

后续再开发回款登记与其他回款的完整已收项目款组合统计，不能把当前任一来源单独标为完整总额。
