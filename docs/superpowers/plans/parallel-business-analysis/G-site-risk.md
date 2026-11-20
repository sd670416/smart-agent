# G 工期事实与现场风险实施计划

**Goal:** 提供E所需工期事实，以及场景8进度、质量、安全三维Provider。
**Architecture:** 一个ProjectSiteAnalysisService只读事实实现，一个ProjectRiskProvider规则实现，复用同一事实查询；不改原进度查询/看板。
**Tech Stack:** Boot Java8/MyBatis，Asia/Shanghai日期。
**Spec:** [总计划](00-start-here.md)、[已确认进度与质量安全规则](../2026-11-20-business-analysis-scenarios-proposal.md)、A-handoff.md。

2026-11-20执行状态：工期事实与三维风险实现完成，UAT来源SQL只读核对通过，所属业务模块及smart-ai依赖编译通过；待H接入及用户人工验收。实际文件、Mapper根目录调整、权限key和边界见[G交接](G-handoff.md)。

## 依赖与文件

A合入后即可与B/C/D并行。独占：
- Boot `smart-business/smart-project/src/main/java/com/smart/project/analysis/site/ProjectSiteAnalysisServiceImpl.java`、`ProjectSiteAnalysisDao.java`。
- Boot `smart-business/smart-project/src/main/resources/mapper/analysis/ProjectSiteAnalysisDao.xml`。
- Boot `smart-business/smart-project/src/main/java/com/smart/project/analysis/risk/site/SiteProjectRiskProvider.java`及规则配置类。

原有ProjectProgressAiDao、现场业务实体/服务只读参考；不自行修改公共VO、路由、配置或数据库。只读规则配置拟用`ai_project_site_risk_rules`，缺省用已确认默认，写库不在本任务范围。

## 服务输出

ProjectSiteAnalysisService.load遵循A签名，metrics：contractDurationDays、planDurationDays、elapsedConstructionDays、actualDurationDays、completedProject、progressPercent、timeProgressPercent。completedProject用0/1且unit=BOOLEAN，仍保留无法判断状态；日期事实使用A的SiteAnalysisSnapshotVO专用日期字段及证据businessDate，不把日期硬塞BigDecimal，禁止另建同名公共类。

未完工时actualDurationDays=NOT_APPLICABLE，显示已历工期；任务日期的最晚结束不自动视为整个项目已竣工。项目竣工结合有效竣工验收与全项目任务状态。工期日差统一明确采用自然日差，开工当日0天，与合同天数展示保持定义清楚。

Provider维度PROGRESS、QUALITY、SAFETY，规则号SITE_前缀。CURRENT完整支持，历史场景不能拿今天未整改状态冒充历史状态。

## 执行步骤

- [ ] 核对计划根子任务、实际时间、反馈/日志、质量检查子项与整改单、安全检查子项与整改关联、竣工验收字段和完成标志。
- [ ] 实现ProjectSiteAnalysisService，项目＋租户＋审批完成过滤；所有人员/责任信息只在原可见范围内输出。
- [ ] 进度：计划结束已过仍未完成高；工期已过半且实际落后时间进度≥20个百分点中；在建超7天无反馈/日志低。缺日期或计划0天不可算时间比例。
- [ ] 质量：超整改截止未闭环高，未超期未闭环中；开工超30天且近30天无检查低。按照具体问题子项闭环，不因一张整改单结束就清空所有问题。
- [ ] 安全同质量；仅存在真实重大隐患等级时追加高风险规则，不从自由文本猜测重大等级。
- [ ] 停工/未开工/完工不套在建检查频次；无记录与无权限分开，检查缺记录只提示管理缺口。
- [ ] 整改状态依可信审批完成业务事实；不要统计草稿/未通过。若实际关闭需专门状态字段，核对字段而非仅用非空完成日期。
- [ ] 同维度多规则保留证据，项目最高等级由H汇总；规则版本/统计时刻统一可追溯。
- [ ] 编译并写G-handoff，优先交付工期事实契约实现供E合入，再交完整Provider。

## 验收

H接入后重启Boot/Agent。“A工期多少，施工多久了”“有哪些项目进度延期”“为什么A有质量风险”“只看安全隐患未关闭的”。核对合同100天开工60天未完工只显示已施工60天；过期任务单独提示；质量安全记录缺失不直接说工程存在质量或安全事故。
