# G 工期事实与现场风险交接

日期2026-11-20。依赖契约analysis-v1，A的公共VO与接口已核对并使用，未修改公共模型、A实现、其他Owner代码、路由、profile或数据库。

## 交付状态

- 工期事实服务ProjectSiteAnalysisService：实现完成，E可依赖，待集成后人工验收。
- 进度、质量、安全ProjectRiskProvider：实现完成，待H统一接入，不代表聊天入口现已可用。
- UAT表结构与九类Mapper来源SQL：只读执行成功；不是应用启动或业务自动测试。
- G所属smart-project及其依赖主代码、测试代码最终编译通过，退出0。smart-ai及其依赖关闭本次增量缓存后复核也编译通过，退出0。不启动服务，按用户要求不执行自动测试；此结果不是全仓全部模块编译声明。

## 实际文件

Boot源码根`smart-business/smart-project/src/main/java/com/smart/project/analysis/`：

1. `site/ProjectSiteAnalysisDao.java`。
2. `site/ProjectSiteMapperConfiguration.java`。
3. `site/ProjectSiteAnalysisServiceImpl.java`。
4. `risk/site/SiteProjectRiskProvider.java`。
5. `risk/site/SiteRiskRules.java`。

XML：`smart-business/smart-project/src/main/resources/mapper/ProjectSiteAnalysisDao.xml`。遵守A核对的扫描规则放mapper根目录，通过site包专属@MapperScan注册Dao；没有更改全局扫描。

只读核对脚本：本目录`G-uat-audit.ps1`。加载application-uat实际地址/账号，不记录密码，使用START TRANSACTION READ ONLY与ROLLBACK；执行真实XML的固定来源分支和参数范围，非完整MyBatis运行时。

## 服务与输出

```java
List<SiteAnalysisSnapshotVO> ProjectSiteAnalysisService.load(AnalysisScopeVO scope, AnalysisPeriodVO period);
Set<String> SiteProjectRiskProvider.dimensions(); // PROGRESS, QUALITY, SAFETY
List<AnalysisRiskVO> SiteProjectRiskProvider.evaluate(AnalysisScopeVO scope, AnalysisPeriodVO period);
```

CURRENT默认且不能携带日期范围；合法PERIOD/AS_OF返回UNSUPPORTED指标及未评估风险，不取当前业务来源假装历史。可信租户/项目必需；空项目范围返回空；输入超过10000个项目拒绝；项目仅未删除、审批完成、状态非1/3/10；查询来源再收窄至实际有效项目。PageHelper入口清理避免意外分页；每类来源SQL取10001行检测溢出，超过10000明确失败，不返回截断统计。

每个项目返回同次查询asOf、实际开工/完工日期、最近反馈日志/质量/安全检查日期、指标状态和证据。DAY为自然日差，开工当日0天；PERCENT已乘100，BOOLEAN为0/1。

| key | 定义 |
| --- | --- |
| contractDurationDays | 一份审批完成类别1施工合同duration；没有合同NO_RECORD，重复或无效工期字段MISSING_SOURCE |
| planDurationDays | 全部有效审批任务最早计划开始至最晚计划结束的自然日差；缺日期/倒序不返回部分工期 |
| elapsedConstructionDays | 已确认实际开工至今，确认完工后截至实际完工日；非合同计划开工日 |
| actualDurationDays | 审批完成竣工验收存在，且全部审批完成任务有有效已审批反馈并进度100%、实际结束非未来时计算 |
| completedProject | 上述确认完工条件满足为1；竣工/质保项目缺完工依据时MISSING_SOURCE，不假设仍在建 |
| progressPercent | 有效顶层任务最新审批完成反馈进度平均值；无反馈不补0，缺任务数据时不冒充整体完整进度 |
| timeProgressPercent | 有效整体计划开始至今/计划起止日差×100；计划天数0不可算，超过100%保留 |
| activeConstruction | 项目状态6且没有确认完工、没有竣工/质保声明为1；7停工不套频次规则 |
| overdueTaskCount / knownOverdueTaskCount | 完整任务评估的逾期数 / 已知逾期数；前者缺数据不可算，后者保留已知事实 |
| progressSilenceDays | 最近有效审批反馈或日志至今天数；都无记录以已确认实际开工日为基点，日期缺失不以创建项目日期替代 |
| qualitySilenceDays / safetySilenceDays | 最近有效检查至今；无记录以实际开工日为基点，仅在建适用 |
| qualityOpenCount / safetyOpenCount | 检查具体问题子项未闭环数，缺明细/问题标志/期限时保留未评估 |
| qualityOverdueCount / safetyOverdueCount | 超过截止日仍未登记有效整改结果的问题数 |
| qualityKnownOverdueCount / safetyKnownOverdueCount | 资料部分缺失时仍可确认的超期问题数，不宣称其他问题正常 |

MISSING_SOURCE/NOT_AUTHORIZED/UNSUPPORTED/NO_RECORD/NOT_APPLICABLE的value=null；有效0是OK。风险命中与未评估可以同时存在，H必须保留两种信息，不能用未评估覆盖已知高风险，也不能按已知命中推断全部维度已评估。

## 实际取数与保守边界

- 不直接相信计划表可变的actual_start_time/progress状态；实际状态取同租户/同项目/同任务的最新审批完成进度反馈（按创建时间、ID降序）。原因是反馈回调会更新计划任务；以审批完成反馈作为可追溯的依据。没有反馈时保留数据缺口，合同工期仍可独立展示。
- 计划根/子任务全部用于检查逾期及工期日期范围，实际进度平均只算没有有效父节点的顶层任务，避免重复平均父子进度。
- 质量/安全主检查审批完成，子项无独立审批状态，按父检查及tenant关联；子表在UAT无is_deleted，不添加不存在字段。整改必须同检查主单/子项/项目/租户，未删除且审批完成。
- 子项有问题标志为1才判断整改。相同子项多条整改取最新创建记录；完成日期合法、不早于检查、不晚于今天，且有结果说明，视为登记完成。没有独立整改验收字段，不宣称有额外验收完成状态；主检查is_complete不用于代替所有子项闭环。
- UAT未发现重大安全隐患分级字段，因此不从文本推断重大等级，没有实现重大隐患自由文本识别。现有超期/未闭环/缺检查规则完整保留。
- 检查缺失只提示管理记录缺口，不认定工程有质量事故或安全事故；未开工、停工、竣工项目不套在建频次。
- 竣工验收表只有登记创建日期，无独立竣工实际日期；因此实际完工取全部有效任务结束日期，前置要求存在已审批竣工验收。未完工不拿最晚任务日期当项目整体竣工。

## 给H的权限接入清单

G只消费可信scope，不改公共映射。权限开关关闭时跳过来源菜单，但租户、项目范围、审批和删除条件不变。启用时需menu:project；细粒度权限如下，均为本功能需要H映射至真实菜单的key，不能当作已经映射完成。

| 来源 | permission |
| --- | --- |
| 施工合同工期 | menu:project |
| 进度计划 | menu:scene:schedule |
| 进度反馈 | menu:scene:feedback |
| 施工日志 | menu:scene:construction-log |
| 竣工验收 | menu:scene:acceptance |
| 质量检查及子项 | menu:scene:quality-inspection |
| 质量整改 | menu:scene:quality-improvement |
| 安全检查及子项 | menu:scene:security-check |
| 安全整改 | menu:scene:safety-rectification |

缺检查或整改权限不查询相应问题来源，返回NOT_AUTHORIZED，不泄漏证据。不同维度按自身可用来源评估，不能因为少安全权限而把质量金额或进度全部补0。当前Provider接口没有dimensions参数，一次返回三维，H按用户请求选择输出；不会越权查询未授权来源。

证据businessType：CONSTRUCTION_CONTRACT、SCENE_SCHEDULE、SCENE_FEEDBACK、SCENE_CONSTRUCTION_LOG、SCENE_ACCEPTANCE、QUALITY_INSPECTION、QUALITY_ISSUE、QUALITY_RECTIFICATION、SAFETY_CHECK、SAFETY_ISSUE、SAFETY_RECTIFICATION。问题证据ID为真实子项ID，非检查主单ID；H下钻需据类型关联父记录再验权，不把子项ID直接塞主表详情接口。

## 风险规则与配置

默认version=SITE-20261120-v1；PROGRESS逾期高、工期过半落后至少20百分点中、在建超7天无有效反馈/日志低；QUALITY/SAFETY超期未整改高、未超期未闭环中、在建开工超30天且近30天无检查低。同一维度多条风险保留各规则证据，H汇总最高等级并附未评估情况。

规则key `ai_project_site_risk_rules`，同租户sys_config只读查询，不用会在缺key时抛异常的getConfig。无配置采用默认；重复配置或格式非法明确失败，不静默当无风险。未写数据库，用户无需为默认规则先建配置。

```json
{"version":"SITE-20261120-v2","progressSilenceDays":7,"inspectionSilenceDays":30,"progressLagPoints":20}
```

允许key仅上述4项，修改阈值必须携带version；天数必须为1至3650的整数，进度百分点须大于0且不大于100。建议H变更规则时同步更改版本；当前代码不能强制版本唯一性或记录历史配置，不宣称已做历史版本库。

规则ID：SITE_PROGRESS_OVERDUE、SITE_PROGRESS_LAG、SITE_PROGRESS_SILENCE；SITE_QUALITY/SAFETY_OVERDUE、SITE_QUALITY/SAFETY_OPEN、SITE_QUALITY/SAFETY_SILENCE。未触发以SITE_<DIM>_CLEAR、EVALUATED/NONE表示；资料不足以SITE_<DIM>_INCOMPLETE、NOT_EVALUATED/level=null表示。

## UAT核对证据

2026-11-20读取application-uat对应数据库192.168.1.200:3305/gongcheng，未写库。初次字典查询误用dict_label字段报错，已按真实dict_name修正重查。

原始Mapper来源固定分支全部SELECT成功：有效施工合同24条、计划17条、反馈20条、日志1条、竣工验收3条、质量检查6条、安全检查5条、质量问题关联8行、安全问题关联5行。仅用于SQL字段与关联有效性核对，不能说这些数量是当前账号可见或实际风险数。

字典核对：6在建、7停工、8竣工、9质保，项目排除1立项中/3未通过/10关闭。查询只读事务rollback。未启动Spring，未调用模型，未执行自动测试，尚未核对真实账号的权限映射与聊天显示。

## 编译与人工验收

编译命令Boot目录：JAVA_HOME=F:/java/jdk/1.8，`mvn -o -q -pl smart-ai,smart-business/smart-project -am -DskipTests test-compile`，日志site-risk-G-compile.log。本批不改Agent/Web，无需为G单独编译前端。

最终验证：`mvn -o -q -pl smart-business/smart-project -am -DskipTests test-compile`退出0（site-risk-G-project-compile.log）；`mvn -o -q -pl smart-ai -am -DskipTests '-Dmaven.compiler.useIncrementalCompilation=false' test-compile`退出0（site-risk-G-ai-compile.log）。包含smart-ai的增量复核曾在测试编译插件出现空指针，关闭本次增量缓存后通过，未修改pom或测试源码；此前未加引号的PowerShell参数尝试被Maven识别为错误生命周期，已正确引用并重跑通过。最终Mapper XML已只读核对，规则配置查询UAT无记录，使用默认值。

G本身没有聊天Tool或Controller，不能直接问聊天验证；H接入或E比较编排接入后再启动验证。届时重启smart-boot，H若增加工具则同时重启smart-agent；Web/数据库无需更新。

人工问题及预期：

1. “A项目合同工期多少，已施工多少天？”合同100天、实际开工60天但未完工应展示100/60，最终总工期不可算。
2. “哪些项目进度延期？”仅能根据有效已审批计划/反馈判断；没有反馈应说明未评估，不按0进度猜测。
3. “A为什么有质量风险？看问题记录。”按具体子项及整改关联核对超期，其他已完成子项不重复报风险。
4. “只看安全问题未关闭的项目。”同样需有效安全检查/整改权限，不输出无权限单据证据。
5. 停工项目无检查不应仅因30天规则报缺检查；在建超过30天无记录才提示管理缺口。
6. 合法历史日期应说明暂不支持历史状态重建；关键来源不足时仍保留已知高风险及未评估说明。

下一步：E可复用工期服务；H映射菜单权限并将三维Provider接入统一风险总览，完成实际签名/账号链路人工验收。G不自行修改H公共文件。
