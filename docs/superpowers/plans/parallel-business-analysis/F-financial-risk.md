# F 资金与成本风险实施计划

**Goal:** 实现场景8的成本、付款、回款、开票、收票五维规则Provider，交H统一总览。
**Architecture:** 实现A的ProjectRiskProvider；复用A基础指标、B产值回款、C供应商账款，在服务端判断规则，输出事实与单据证据。
**Tech Stack:** Boot Java8，版本化配置默认值，BigDecimal。
**Spec:** [总计划](00-start-here.md)、[已确认六维及成本规则](../2026-11-20-business-analysis-scenarios-proposal.md)、A/B/C-handoff.md。

实施进度（2026-11-20）：Provider、科目事实与配置解析已实现，主代码及测试源码编译通过。未启动服务、未写数据库、未执行自动测试；公共工具文案/路由交H接入，UAT业务待用户验收。完整状态、规则限制、文件与验证问题见[F交接](F-handoff.md)。

## 依赖与文件

A/B/C的实现合入后正式开发。独占：
- Boot `smart-business/smart-project/src/main/java/com/smart/project/analysis/risk/financial/FinancialProjectRiskProvider.java`。
- 同包必要的规则定义与只读事实Dao。
- Boot `smart-business/smart-project/src/main/resources/mapper/analysis/FinancialProjectRiskDao.xml`（需要一级科目付款等新增事实时）。

不建独立Agent工具/公共Controller，不修改公共配置/数据库。规则版本和默认配置放本包类中，可通过现有配置服务只读读取`ai_project_financial_risk_rules`；key为拟新增，不假定存在。写库由用户单独授权，缺配置用已确认默认，格式非法不能悄悄视为无风险。

## 固定Provider输出

dimensions()返回COST、PAYMENT、RECEIVABLE、INVOICE、SUPPLIER_INVOICE。evaluate返回每个项目各维度评估状态；多个规则证据可多行，但至少能区分未触发和未评估。规则ID前缀FIN_，与G的SITE_不撞。

完整条件以原方案已确认表为准，不能自行调阈值。边界：成本超预算高，未超且≥90%中，≥75%且<90%低；结算未付率≥50%高，≥30%且<50%中，>0且<30%低。其余回款、开票和收票规则严格继承已确认原文。

## 执行步骤

- [ ] 建立带version的默认规则及配置解析，区间和优先级明确定义，所有阈值可由合法配置覆盖。
- [ ] 加载可信全量事实，检查所需来源权限；无权/缺结算/零分母标NOT_EVALUATED，不计入无风险项目数。
- [ ] 成本按一级科目判断再汇总最高等级；若A只有项目总目标成本，新增本任务只读科目事实，不能用总额覆盖科目风险。
- [ ] 付款按相同项目/供应商/合同范围的有效结算净额与净付款算未付，不用合同额替代结算分母；无到期依据称未付压力。
- [ ] 回款只用回款登记，保持确认产值和工期前置条件。超过90天无回款要有应收起算/到期事实，缺这些不得改用项目创建日。
- [ ] 开票使用审批完成申请金额，标签明确申请；不能声称税款损失或已实际开票。收票按C提供有效净额，只调退款一次。
- [ ] 证据带实际单据ID和金额，缺失要说明，建议用“核对账期/跟进回款/核对票据”等业务动作，不自动办审批。
- [ ] 第一版不实现审批中付款积压：它是可选独立审批信号，普通金融金额仅审批完成。H后续要接时再按独立流程权限处理，不能绕过用户要求。
- [ ] 编译覆盖Provider实现，写F-handoff：维度、规则号、配置格式、样例命中/未命中/未评估及证据来源。

## 验收

H总览接入后重启Boot/Agent。问“有哪些项目有成本或回款风险”“只看高风险”“A为什么提示收票风险”“看对应单据”。核对预算100万付105万为高、95万中、80万低；无预算未评估。无票缺口提示金额，不直接认定税务违规。没有账期不能说已经逾期拖欠。
