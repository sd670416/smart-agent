# 查询分析功能并行开发总计划

**Goal:** 将已确认的场景2、3、6、7、8拆成可分配给独立聊天的开发任务，减少冲突并保持统一计算规则。

**Architecture:** 公共指标基础先合入，业务任务使用固定服务契约独立开发。统一接入任务独占公共路由、上下文、权限映射及系统提示词，按业务任务逐批接入。模型理解与解释，后端负责实际取数、计算和风险判定。

**Tech Stack:** smart-boot Java8/Spring/MyBatis，smart-agent Java21/Spring，现有聊天前端与Markdown表格。

**Spec:** [已确认业务方案](../2026-11-20-business-analysis-scenarios-proposal.md)。本目录是实施拆分，业务定义冲突时以原方案中最新已确认事项为准。

日期2026-11-20。最新进度：A至G业务代码已交接，H已完成B/C/D/E/F/G统一接入，主代码与测试源码编译通过，整体待UAT人工验收。具体证据和测试问题见[H交接](H-handoff.md)。下方依赖顺序保留供开发维护参考，不表示任务尚未开始。场景1、4保留已完成功能。

## 先读这些约束

- UAT是用户后续开发及人工验证环境。先检查真实配置，不自行切profile、分支、数据库或更改地址密码。
- 用户要求以编译检查为主，业务由用户验证；不启动服务，不擅自部署、合并、提交或修改数据库。
- 当前目录中已有多个任务的未提交改动。执行前查看各仓库git status和diff，只修改所属文件，保留其他改动。
- 不让多个聊天同时在同一个checkout切换分支。推荐每个任务独立worktree或独立checkout；由用户在创建聊天时选择隔离目录。不能隔离时遵守文件所有权，并且编译串行执行，避免共享target目录互相清理。
- 本文档不授权创建聊天、派发代理、切分支或新建worktree；这里只提供用户可自行使用的任务材料。
- 业务金额仅计审批完成，普通项目数据排除关闭、立项中、立项未通过；指定待办审批查询沿用原规则。
- 权限沿用现有菜单/可信项目范围/权限总开关/租户规则。客户端传入项目ID、人员或租户不能扩大可信范围。
- 每轮事实问题实时调用工具；历史结果只用于继承条件、真实ID及用户选择，不作为当前金额依据。
- 显示业务中文名称，不向客户输出表名、字段编码、SQL、接口名或内部统计口径术语；技术信息留在AI处理信息。
- 系统提示词、路由和公共鉴权属于H任务独占区。功能聊天将接入需求写到本任务交接文件，不能自行改共享文件。

## 任务与依赖

| 聊天 | 任务 | 依赖 | 拆分文档 |
| --- | --- | --- | --- |
| A | 公共指标基础和UAT来源核对 | 无，首先执行 | [A](A-common-foundation.md) |
| B | 场景2产值与付款分析 | A合入 | [B](B-output-payment.md) |
| C | 场景3甲方和供应商合作历史 | A合入 | [C](C-partner-history.md) |
| D | 场景7盈利与利润率排名 | A合入 | [D](D-profit-ranking.md) |
| E | 场景6多项目经营比较 | A、B、G的契约和实现合入；复用A利润指标 | [E](E-project-comparison.md) |
| F | 场景8资金与成本风险五维 | A、B、C合入 | [F](F-financial-risk.md) |
| G | 场景8现场风险三维及工期事实 | A合入 | [G](G-site-risk.md) |
| H | 统一路由、权限映射、上下文及风险总览 | A后可先检查接入点；随B至G逐批合入 | [H](H-integration.md) |

推荐第一轮只开A；A交接后开B、C、D、G；再开E、F；H负责每个已合入功能的聊天接入及最终收尾。H最后合并金融与现场风险输出统一八维总览。

独立分支的文件不会自动传到其他任务。依赖“合入”表示用户已将该任务变更合并/cherry-pick到下游起点，并确认契约版本一致；看到接口文档不等于实现已可用。

## 公共文件所有权

| 区域 | 唯一修改者 |
| --- | --- |
| smart-model/.../business/analysis 下公共VO；smart-service/.../analysis 下公共服务接口 | A |
| 公共基础指标实现及Mapper | A |
| 新增业务包及各自Controller、Tool、Client | B/C/D/E/F/G各自对应任务 |
| QueryIntentResolver、ChatOrchestrator、SystemInstructionCatalog、公共字段/澄清/上下文目录 | H |
| AuthenticatedAgentContextFactory及其他公共权限映射/内部API鉴权配置 | H |
| 原有利润服务、输入和Mapper | 原则不改；D确需修改时独占，先在交接声明 |
| pom.xml、application*.yml、数据库迁移、sys_config写入 | 不由功能聊天自行修改，提交H评估；数据库执行需用户授权 |
| 风险总览Controller、Tool和Provider编排 | H |
| 原始Word、原业务方案、此总计划 | 功能聊天只读；进度写各自交接文件 |

路径中的`...`仅在此表表示既有包路径；各任务正文列出了完整源码根目录。

## A需要先交付的契约

所有名称均为本计划拟新增，并非声称代码已有。完整定义见[A](A-common-foundation.md)。

公共包根：`smart-boot/smart-model/src/main/java/com/smart/model/business/analysis/`。

- AnalysisScopeVO：可信租户、可见项目ID、可信权限、权限开关状态。
- AnalysisPeriodVO：CURRENT/PERIOD/AS_OF、明确日期维度、开始/结束日期；CURRENT第一版必须可用，其他模式不得静默转CURRENT。
- AnalysisMetricVO：key、label、unit、value、status、reason、sampleCount。
- ProjectAnalysisSnapshotVO：项目属性、指标Map、证据、统计时刻。
- AnalysisEvidenceVO：业务类型、业务单据ID、编号、日期及业务标签，不含任意SQL或原始敏感信息。
- AnalysisRiskVO：项目、维度、规则编号/版本、等级、评估状态、原因、数值与阈值、证据、建议。
- SiteAnalysisSnapshotVO：工期与进度事实、数据完整性和证据。

服务包根：`smart-boot/smart-service/src/main/java/com/smart/service/analysis/`。

```java
public interface ProjectAnalysisMetricService {
    List<ProjectAnalysisSnapshotVO> load(AnalysisScopeVO scope, AnalysisPeriodVO period,
                                        Set<String> metricKeys);
}
public interface ProjectOutputPaymentAnalysisService {
    List<ProjectAnalysisSnapshotVO> load(AnalysisScopeVO scope, AnalysisPeriodVO period);
}
public interface PartnerAccountAnalysisService {
    List<ProjectAnalysisSnapshotVO> loadSupplierAccounts(AnalysisScopeVO scope,
                                                        AnalysisPeriodVO period);
}
public interface ProjectSiteAnalysisService {
    List<SiteAnalysisSnapshotVO> load(AnalysisScopeVO scope, AnalysisPeriodVO period);
}
public interface ProjectRiskProvider {
    Set<String> dimensions();
    List<AnalysisRiskVO> evaluate(AnalysisScopeVO scope, AnalysisPeriodVO period);
}
```

所有集合按可信范围查询；空projectIds返回空，不代表全量。`load`不是页面方法，不能偷偷截断为第一页；大范围不能完整查询时显式失败，不能返回伪全量。供应商账款快照的主键属性另含supplierId、contractType、contractId或明确的聚合粒度，禁止以projectId覆盖同项目多供应商记录。

期间选择和可信scope只能从后端校验构造；公共VO不可直接作为对外HTTP请求不经校验地执行。

## 对外工具和接口预留

| Owner | Tool Key | POST /internal/ai/tools 下路径 | Agent域 |
| --- | --- | --- | --- |
| B | projectOperation.query | /project-operation-query | PROJECT_OPERATION |
| C | partnerHistory.query | /partner-history-query | PARTNER_HISTORY |
| D | projectProfit.rank | /project-profit-ranking | 既有PROJECT_PROFIT，由H增加工具列表 |
| E | projectOperation.compare | /project-operation-compare | PROJECT_OPERATION，与B共域由H注册 |
| F/G | 无独立公共工具，提供ProjectRiskProvider实现 | 无公共工具入口 | 由H统一 |
| H | projectRisk.query | /project-risk-query | PROJECT_RISK |

工具返回优先使用既有`ProjectQueryResultVO`/Agent `ProjectQueryResult`，columns定义业务标签。风险使用同结构表示列表/汇总，证据与未评估原因应可由模型读取。服务内部使用公共强类型VO。

新工具schema只用当前适配器支持的type/description/properties/required/additionalProperties/items；不要使用enum/minItems/maxItems/format/oneOf等当前未支持关键字。允许值和长度由服务端验证，说明写description；不能把Java编译通过当成工具schema一定可调用。

## 每个聊天的统一交接内容

在本目录新增 `<任务字母>-handoff.md`，仅写自己的文件：

1. 实际新增/修改文件、已保留的既有功能及依赖契约版本。
2. 接口、工具、业务域、请求字段/默认值、真实响应样例。
3. 指标的真实取数与金额、日期、缺失、权限处理，不能把拟定规则写成已验证事实。
4. H需要修改的公共接入点与建议片段（文档即可），功能聊天不直接改公共源码。
5. 编译命令、退出结果；是否执行额外验证、是否尚有启动/运行风险。
6. 需要重启组件、用户可直接发送的测试问题、预期行为。
7. 是否仍需UAT样本核对、已知限制、下一步；不得把未运行标成通过。

每个聊天只维护自己的进度清单：待开始/开发中/编译通过/待接入/待人工验收/验收通过。用户才可确认人工验收通过。

## 编译约定

Agent：JAVA_HOME=`F:/java/jdk_21/jdk-21.0.6`，`mvn -o -q -DskipTests test-compile`。

Boot：JAVA_HOME=`F:/java/jdk/1.8`，`mvn -o -q -pl smart-ai,smart-business/smart-project -am -DskipTests test-compile`。任务修改其他业务模块时把该模块显式加入-pl；smart-ai只依赖服务接口，单独编译smart-ai可能不覆盖业务实现。

构建输出写任务专属日志。共享checkout内不能同时运行Maven clean或编译；独立worktree可并行，但注意共用本地Maven仓库，默认不执行install。失败先检查旧产物/依赖，不无依据修改公共pom。

## 新聊天的通用启动指令

将以下文字中的文件名替换为相应任务文档：

> 请执行 F:\project\gongcheng\agent\docs\superpowers\plans\parallel-business-analysis\B-output-payment.md。先阅读同目录00-start-here.md及所链接的已确认业务方案，严格遵守文件所有权和依赖。使用本聊天所附的独立checkout；不要自行切换分支或改其他任务文件。环境以UAT实际配置为准，不启动服务、不部署、不修改数据库；以编译通过为代码检查目标，业务由我验证。已确认的规则不用再次询问。完成后写任务专属handoff，告诉我重启哪些服务、如何测试和下一步。公共接入文件的改动建议交给H，不直接修改。

仅执行方案不意味着自动创建新聊天；由用户自行拆分与启动。
