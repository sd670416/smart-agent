# H 接入交接

2026-11-20，本批仅接入D盈利排名；B/C/E/F/G不在本批接入范围。

## D状态

代码接入完成、Agent主代码及测试源码编译通过，待UAT人工验收。未启动应用、未改数据库和Web，未切profile/分支，保留其他聊天改动。

修改文件：
- Agent routing/ProjectProfitDomainContributor.java：在原PROJECT_PROFIT域增加projectProfit.rank，加入盈利/亏损词，排名metric可映射预算/实际来源权限，兼容可信权限关闭标记。
- Agent routing/QueryIntentResolver.java：默认盈利排名按预算来源判定，保留普通利润/项目/审批分流。
- Agent chat/ChatOrchestrator.java：排名业务提示、可信历史工具类型和参数持久化、排名分页与参数继承、序号定位支持及结果展示说明；旧排名指标可恢复成预算/实际权限类别，不因budgetProfitRate未知而失败。
- Agent model/SystemInstructionCatalog.java：默认预算利润率降序前20、回复说明排序依据、全量数量与TopN区别、缺数据说明、CURRENT限制与技术ID隐藏。

排名分页按历史页码加减；其他受支持的改排序/亏损筛选等追问继承模型未明确提供的原条件并回第1页。模型明确提供的新值优先；不对每个新问题无条件继承旧榜单项目条件。上下文只用于条件和真实ID，不使用历史金额代替本轮结果。

后端只读检查：AiInternalWebConfig对/internal/ai/tools/**注册可信签名拦截器，包含/project-profit-ranking；D Controller根据可信上下文构造AnalysisScopeVO，A指标目录包含各利润来源权限。无需新增路径白名单或修改数据库菜单。最终指标来源权限和数据范围仍由Boot执行。

## 编译

JAVA_HOME=F:/java/jdk_21/jdk-21.0.6；`mvn -o -q -DskipTests test-compile`退出0，日志agent/profit-ranking-H-compile.log。本批只修改Agent，Boot编译沿用D交接记录，未声称重新执行。未运行自动测试或真实模型调用。

## 用户验证

部署/启动D与A后端代码需重启smart-boot；本批Agent修改需重启smart-agent。若Boot已运行D最新版本，只重启Agent即可。Web/数据库无需更新。

1. “做个盈利排名”：调用projectProfit.rank，默认预算利润率降序前20，答复明确排序依据。
2. “改按预算利润金额排序”：budgetProfit、page1，原其他筛选保留。
3. “实际收支利润最低的50个项目，每页10条”→“下一页”：actualProfit、asc、topN50、pageSize10、page2，名次连续保留。
4. “只看亏损项目”：相应利润负数筛选，实时调用，不删负值；核对不可排名原因。
5. “按去年利润排名”：说明只支持当前累计，不以去年创建项目代替去年利润。
6. “查询项目按合同额排序”以及“查我的待办”：分别进入原项目与审批工具，不强制排名。
7. 只有预算来源菜单账号查询预算榜，不要求实际收支全部菜单；关闭权限开关时沿用可信标记，Boot再校验。组织/经理仅有名称时不能编造ID。

待人工确认真实模型对“改按金额”等省略语、同名项目澄清和域切换的效果，不将编译通过当作业务验收。

下一步：用户验收D；按用户指定接入其他已完成任务，H继续维护公共文件。

## B C G 接入批次

2026-11-20：按用户追加要求接入B、C、G。D原接入保留。本批代码完成，待UAT人工验收，E比较和F金融风险不声明已完成。

### 本批公共改动

- 新增PROJECT_OPERATION域，注册B的projectOperation.query；产值与甲方回款联合问题优先进入该域，普通产值单仍原入口。
- 新增PARTNER_HISTORY域，注册C的partnerHistory.query；类型选择PARTNER_TYPE和同名合作方PARTNER_REFERENCE接入现有持久化澄清流程。确认ID后清除旧合作方名称和单据范围。
- 新增PROJECT_RISK域与projectRisk.query工具；H增加专属请求、服务接口/编排实现、签名HTTP Controller/异常处理和Agent Client/Tool配置。复用G的ProjectSiteAnalysisService及ProjectRiskProvider，不修改G规则或数据来源。
- 工期mode=siteFacts；风险detail/summary/evidence。当前G提供进度、质量、安全三维；默认综合查询中其余维度生成未评估项，不能当无风险。将来F实现ProjectRiskProvider合入后可被列表注入发现，仍需核对F交接，不声明已验证金融规则。
- 编排按可信租户和项目范围及当前有效项目收窄；只支持CURRENT，历史参数明确拒绝。全量评估后过滤等级和分页，风险项目数去重，风险条数与含未评估项目数分别返回。
- 工期事实使用G返回的自然日、确认完工状态与缺失原因；不拿未完工项目已历工期当最终总工期。
- C/G新增来源菜单通过AnalysisSourceMenuResolver递归匹配真实叶子路由或组件，增加结算四类、收票、材料入库、计划、反馈、日志、验收、质量安全及整改权限。更新READ_PERMISSIONS用于已有权限关闭流程，system按账号授予这些读取权限，不依赖角色。
- 源码路径匹配来自当前页面组件路径，UAT实际角色菜单授予及签名调用由用户人工验证。本批未写菜单或配置表，默认风险规则无需先建sys_config。
- 三域沿用通用查询快照并补充真实项目/合同/单据定位提示；C从后端appliedFilters持久化确定的partnerId。事实追问仍调用工具重查，明确切换模块不强制留旧域。
- 系统提示要求中文业务表格、隐藏技术ID/原始状态、说明缺失原因。风险evidence只返回本轮重新评估的单据定位与依据；没有实现质量安全主单完整表单打开，不能宣称已提供完整原业务记录。

### 文件归属

Agent新增routing/ProjectOperationDomainContributor.java、PartnerHistoryDomainContributor.java、ProjectRiskDomainContributor.java；tool/projectrisk下Input、Tool、Client、签名Client与Configuration；修改H拥有的QueryIntentResolver、ChatOrchestrator、SystemInstructionCatalog。

Boot新增smart-model/business/analysis/risk/ProjectRiskQueryRequestVO、smart-service/analysis/risk/ProjectRiskQueryService、smart-project/analysis/risk/ProjectRiskQueryServiceImpl、smart-ai/controller/AiProjectRiskToolController与AiProjectRiskToolExceptionHandler、security/AnalysisSourceMenuResolver；修改AgentContextFactory及AiQueryAccessPolicy。B/C/G原业务实现与共享A契约未修改。

### 编译记录

Boot：JAVA_HOME=F:/java/jdk/1.8，`mvn -o -q -pl smart-ai,smart-business/smart-project -am -DskipTests '-Dmaven.compiler.useIncrementalCompilation=false' test-compile`，退出0，analysis-BCG-H-boot-compile.log。

Agent：JAVA_HOME=F:/java/jdk_21/jdk-21.0.6，`mvn -o -q -DskipTests test-compile`，最终退出0，analysis-BCG-H-agent-compile.log。按用户约定不执行自动测试，不启动Spring或模型链路，不把编译当作运行验证。

### 人工验证

重启smart-boot和smart-agent；Web、数据库无需变更。

| 功能 | 提问 | 重点 |
| --- | --- | --- |
| B | 004项目完成多少产值、上报多少、甲方回款多少？ | 调projectOperation.query，回款不含其他收入 |
| B | 两种回款比例分别多少？然后汇总所有可见项目 | 比率已乘100，汇总清除单项目条件 |
| C | 查询YYY甲方单位的合作历史及累计回款 | 同名确认，按真实合作方ID查询 |
| C | 只看2026年签约的合同，再看第一份合同付款明细 | 签约日期选合同，真实contractId重查，不用序号做ID |
| C | 查询中天材料供应商的材料合同合作历史及收票 | 核对结算/收票独立菜单权限及退款净额 |
| G | A项目合同工期多少，已施工多少天？ | siteFacts，未完工不算最终总工期 |
| G | 哪些项目有质量或安全风险？只看高风险 | 维度过滤、后端规则、证据和未评估保留 |
| G | 目前项目有哪些经营风险？ | 未接入金融维度说明，不返回全面无风险结论 |
| 回归 | 做个盈利排名；改查我的待办；再查项目信息 | D和原项目/审批切换保持实时查询 |

来源权限不足时，可能只有某些指标或维度不可评估；在AI回复中不能按0或正常填补。实际菜单字段、模型指代和SQL完整链路仍需UAT人工验收。下一步根据验收结果修复，或按用户指示接入E/F。

## E F 最终接入批次

2026-11-20：用户先授权接E，开发中追加F。已读取两份交接，保留A/B/C/D/G实现，本批只修改H公共接入及H拥有的风险总览，不重写E/F公式。

### E接入

- PROJECT_OPERATION更名为项目经营分析，在同域追加projectOperation.compare，未新建重复域。多项目经营/横向跨指标比较及增删/基准追问进入该域，普通单利润比较、产值单比较和审批查询继续原工具。
- 新增H专属OperationComparisonContextSupport，保存比较结果appliedFilters的有序真实projects、baselineProjectId、metricKeys和工具类型。结果rows为指标，不当成项目；意图模型摘要也使用项目列映射，而非指标行序号。
- 同名PROJECT_REFERENCE持久化完整输入、比较工具key及目标下标，只替换该位置的projectId，不在根节点写入单项目ID覆盖整组。缺少或无效下标拒绝，选择后重新校验当前项目范围并重查。
- 追加/移除/更换指标或基准时继承模型未明确修改的指标和日期条件；默认首项目基准第一次由E计算并告知。旧基准仍在列表则继续保留；旧基准移出且未提供新基准时，具有完整真实ID列表可返回OPERATION_BASELINE_REFERENCE持久化选择。尚有项目待准确定位时提示先明确新基准，不任选新基准。
- 多项目专用提示与渲染约束：按columns展示矩阵、说明基准、各项目减基准、率差百分点、缺失显示原因，隐藏ID及状态。total只表示指标行数，不是项目数；仅CURRENT。
- E的/internal/ai/tools/project-operation-compare已处于既有签名拦截路径，Controller以可信上下文构建scope；未新增绕过鉴权白名单。

### F接入

- 核对FinancialProjectRiskProvider为Spring组件，提供COST/PAYMENT/RECEIVABLE/INVOICE/SUPPLIER_INVOICE，现有H编排的List<ProjectRiskProvider>自动收集。结合G后八维均有Provider，无需复制业务规则或新建第二个风险工具。
- 更新projectRisk.query工具描述及系统提示，列出八维对应参数。普通付款/开票单查询仍原域，风险问题选相应维度。
- H风险总览增加中文判断依据factSummary及相关单据evidenceSummary，保留金额、比例/阈值、来源编号与原因，技术规则key和ID仍留内部元数据。evidence模式重新评估并返回依据，不打开完整业务表单。
- 只读编排采用REPEATABLE_READ；风险项目最高等级取所选维度完整已知命中，等级筛选后的项目数/风险条数另算，未评估数量仍说明所选范围的数据缺口，不因只看HIGH宣称全部已评估。
- 缺到期节点的长期未回款规则仍未评估；同项目已知回款高风险不被该缺口覆盖。付款只称未付压力，开票只用申请，收票净额不重复扣退款，不认定税务违规。
- 复用C/G批次已添加的来源菜单和可信权限总开关，无新增权限key，无数据库配置写入；F及G默认规则在未配置sys_config时可用，错误配置按各Provider既定规则失败，不能当无风险。

### 文件与验证

Agent修改：routing/ProjectOperationDomainContributor、QueryIntentResolver、ModelIntentRouter；chat/ChatOrchestrator、新增OperationComparisonContextSupport；model/SystemInstructionCatalog；tool/projectrisk/ProjectRiskQueryTool。

Boot修改：仅H拥有的smart-project/analysis/risk/ProjectRiskQueryServiceImpl。E/F专属业务源码、A共享契约、应用配置、Web和数据库未修改。

Agent最终：JAVA_HOME=F:/java/jdk_21/jdk-21.0.6，`mvn -o -q -DskipTests test-compile`退出0，analysis-EF-H-agent-compile.log。

Boot最终：JAVA_HOME=F:/java/jdk/1.8，`mvn -o -q -pl smart-ai,smart-business/smart-project -am -DskipTests '-Dmaven.compiler.useIncrementalCompilation=false' test-compile`退出0，analysis-EF-H-boot-compile.log。

主代码和测试源码编译通过；按用户约定没有执行自动测试、启动服务、模型调用、UAT写入或部署。工具运行、菜单范围、SQL/业务对账仍待人工验收。

### 用户测试

重启smart-boot与smart-agent，无需更新Web或数据库。

1. “比较004和254101项目的合同额、目标成本、预算利润率、甲方回款和工期”：projectOperation.compare，默认第一个项目基准，矩阵差额方向明确。
2. “再加XM260130001，以254101为基准”：三项目完整重查，率差百分点；“去掉004”保留剩余项目与有效基准。
3. “去掉254101”：若其为基准且未指定替代，先选择新基准；不足两个项目先补项目，不做假比较。
4. 同名项目选择只替换提示的第N个元素；“看第二个项目产值回款”从项目列映射定位，不从第二个指标行定位。
5. “有哪些项目有成本风险？”“只看高风险”“汇总所有可见项目”：后端全量评估，项目去重，不相加当前页；有未评估项仍说明。
6. “A项目为什么有回款风险？”“看收票风险的依据”：展示金额、比例/阈值及单据编号；没有到期节点不能声称超过90天逾期或到期拖欠。
7. “按去年经营比较/风险”：当前状态限制明确，不套创建日期；“改查我的待办”应回原审批域。

截至本批，A至G业务代码均有交接，H已完成B/C/D/E/F/G接入，整体状态为待UAT人工验收。下一步优先用户集中验证及按实际runId修复，不再将代码完成当作验收通过。
