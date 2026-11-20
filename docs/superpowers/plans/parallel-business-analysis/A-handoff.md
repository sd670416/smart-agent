# A 公共指标基础交接

日期：2026-11-20。契约版本：**analysis-v1**。Owner：A。

## 进度与交付范围

| 项目 | 状态 |
| --- | --- |
| 公共VO、状态枚举、5个服务接口 | 已实现 |
| 16项基础指标目录与当前累计计算 | 已实现 |
| 可信项目范围、租户、来源权限、审批条件 | 已实现，运行账号权限由H接入后人工验收 |
| Mapper资源与接口注册 | 已实现；使用已有扫描目录与A包内配置 |
| UAT表结构及4个项目只读取数核对 | 已完成，未写数据库 |
| Boot主代码及测试代码编译 | 最终编译通过，退出0 |
| Agent路由/工具/新业务页面 | A不实现，由各任务和H接入 |
| 业务人工验收 | 待B/H接入后由用户验收 |

工作已写入当前checkout，未自动提交、切换分支、合并或部署。没有启动应用，没有修改profile、pom、旧业务查询或数据库。

## 实际文件

公共模型目录：`smart-boot/smart-model/src/main/java/com/smart/model/business/analysis/`。

- AnalysisScopeVO、AnalysisPeriodVO、AnalysisMetricVO、AnalysisEvidenceVO。
- ProjectAnalysisSnapshotVO、SiteAnalysisSnapshotVO、AnalysisRiskVO。
- AnalysisPeriodMode：CURRENT、PERIOD、AS_OF。
- AnalysisMetricStatus：OK、NO_RECORD、MISSING_SOURCE、NOT_AUTHORIZED、NOT_APPLICABLE、UNSUPPORTED。

公共服务目录：`smart-boot/smart-service/src/main/java/com/smart/service/analysis/`。

- ProjectAnalysisMetricService：A已实现。
- ProjectOutputPaymentAnalysisService：B实现。
- PartnerAccountAnalysisService：C实现。
- ProjectSiteAnalysisService：G实现。
- ProjectRiskProvider：F/G分别实现，H编排。
- AnalysisMetricCatalog：新增稳定指标key/中文标签/单位/依赖/来源权限目录，可直接复用。

实现目录：`smart-boot/smart-business/smart-project/src/main/java/com/smart/project/analysis/core/`。

- ProjectAnalysisMetricServiceImpl。
- ProjectAnalysisMetricDao。
- ProjectAnalysisMapperConfiguration。

Mapper实际位置：`smart-boot/smart-business/smart-project/src/main/resources/mapper/ProjectAnalysisMetricDao.xml`。

**重要调整：** 原计划建议mapper/analysis子目录，但当前MyBatis配置为classpath*:mapper/*.xml，只加载根目录。A因此使用根目录XML，未更改公共配置。当前Application扫描com.smart.**.*.dao，A接口位于analysis.core，所以通过A专属@Configuration/@MapperScan注册。B至G新增Dao/XML时也应选择已扫描的包与资源目录，或由自己的配置显式注册，不能仅因Java编译通过就认为Mapper会加载。

## 固定服务签名

```java
List<ProjectAnalysisSnapshotVO> ProjectAnalysisMetricService.load(
    AnalysisScopeVO scope, AnalysisPeriodVO period, Set<String> metricKeys);
List<ProjectAnalysisSnapshotVO> ProjectOutputPaymentAnalysisService.load(
    AnalysisScopeVO scope, AnalysisPeriodVO period);
List<ProjectAnalysisSnapshotVO> PartnerAccountAnalysisService.loadSupplierAccounts(
    AnalysisScopeVO scope, AnalysisPeriodVO period);
List<SiteAnalysisSnapshotVO> ProjectSiteAnalysisService.load(
    AnalysisScopeVO scope, AnalysisPeriodVO period);
Set<String> ProjectRiskProvider.dimensions();
List<AnalysisRiskVO> ProjectRiskProvider.evaluate(
    AnalysisScopeVO scope, AnalysisPeriodVO period);
```

公共VO字段与A计划一致，使用Java8 POJO和Lombok@Data。日期字段保留String/date precision定义，metric.value统一BigDecimal；不要把日期硬塞数值。SiteAnalysisSnapshotVO已包含actualStartDate/actualEndDate及三种latest日期字段，G无需追加公共字段。

`metricKeys`必须非空，未知key显式拒绝。返回所有符合范围及项目状态条件的项目，且metrics仅包含请求项，依赖指标内部计算，不强行塞入客户结果。evidence包含实际使用的已授权来源单据，可有派生项依赖的单据。

## 可信范围接入示例

以下片段供B/H在内部签名接口完成上下文核验后使用，不是面向客户的请求模型：

```java
AiTrustedUserContext context = AiTrustedUserContextHolder.require();
AnalysisScopeVO scope = new AnalysisScopeVO();
scope.setTenantId(context.getTenantId());
Set<String> allowed = new LinkedHashSet<String>(context.getProjectIds());
if (requestedProjectIds != null) allowed.retainAll(requestedProjectIds);
scope.setProjectIds(allowed);
scope.setPermissions(new LinkedHashSet<String>(context.getPermissions()));
scope.setQueryPermissionsEnforced(context.isQueryPermissionsEnforced());
List<ProjectAnalysisSnapshotVO> snapshots = metricService.load(scope, period,
    new LinkedHashSet<String>(Arrays.asList("finalContractAmount", "targetCostAmount", "budgetProfitRate")));
```

不要从@RequestBody直接绑定并信任AnalysisScopeVO；权限开关值必须来自已有经过核验的可信上下文，不接受客户传false。scope为空或租户缺失拒绝；空projectIds返回空，不代表全部。scope对象和集合内部复制，调用者集合不会被修改。

项目查询强制同租户、未删除、审批完成、状态不在1/3/10，并将后续来源查询进一步收窄到实际找到的项目。权限启用时至少要求menu:project；各指标再校验其依赖的全部来源菜单。已签名权限关闭状态下跳过菜单校验，但不移除租户、可信项目、审批和删除条件。

## 指标、真实来源与权限

所有业务主表均要求tenant_id、is_deleted='0'、approval_status='2'；明细按主单ID与tenant_id关联。项目报销按明细project_id归属，不能从主表取不存在的project_id。

| key | 中文含义 | 来源 | 额外权限（均另需menu:project） |
| --- | --- | --- | --- |
| finalContractAmount | 施工合同最终金额 | bus_pm_construct_contract_register.final_contract_amount，contract_category=1，恰一份有效主单 | 无 |
| targetCostAmount | 有效目标成本 | 复用BudgetCostAiDao.projectRows，已审批编制＋新增变更＋有效修改合并 | menu:budget:cost |
| paymentReceivedAmount | 甲方回款登记金额 | bus_finance_payment_registration.received_amount | menu:finance:payment-registration |
| otherIncomeAmount | 其他收入 | bus_finance_other_income_registration.received_amount | menu:finance:other-income-registration |
| paymentAmount | 付款申请金额 | bus_finance_payment_request_ref.current_payment_value，按主单汇总 | menu:finance:payment-request |
| otherPaymentAmount | 其他付款金额 | bus_finance_other_payment_request_ref.current_payment_value | menu:finance:other-payment-request |
| reimburseAmount | 项目报销金额 | bus_finance_project_reimburse_ref.reimburse_amount，按明细项目归属 | menu:finance:project-reimburse |
| refundAmount | 材料退货退款调整金额 | bus_contract_material_return_ref.return_amount，主单type=2、purchase_type=1 | menu:material:refund |
| invoiceApplicationAmount | 开票申请金额 | bus_finance_invoice_application.invoice_apply_amount | menu:finance:invoice-application |
| netPaymentAmount | 净付款 | paymentAmount＋otherPaymentAmount＋reimburseAmount−refundAmount | 对应四项来源全部权限 |
| totalIncomeAmount | 总收入 | paymentReceivedAmount＋otherIncomeAmount | 对应两项来源全部权限 |
| budgetProfit | 预算利润 | finalContractAmount−targetCostAmount | 合同与目标成本来源权限 |
| budgetProfitRate | 预算利润率 | budgetProfit/finalContractAmount×100 | 同上 |
| actualProfit | 实际收支利润 | totalIncomeAmount−netPaymentAmount | 对应六项资金来源全部权限 |
| actualProfitRate | 实际收支利润率 | actualProfit/totalIncomeAmount×100 | 同上 |
| costRate | 目标成本占比 | targetCostAmount/finalContractAmount×100 | 合同与目标成本来源权限 |

以上是技术交接字段，不向业务回复暴露表名、key或权限编码。labels可使用AnalysisMetricCatalog.label(key)，金额单位CNY，比例单位PERCENT且值已乘100；不得再次乘100。率保留4位小数，金额保持原十进制精度。

targetCostAmount复用的XML片段在smart-budget资源中，主应用smart-app已依赖smart-budget和smart-project；单独装载新Mapper的隔离程序需要同时加载BudgetCostAiDao.xml，不要误认为XML引用自动下载其他模块。

预算合并算法沿用现有预算明细页面的最新修改匹配方向，分区另含租户和项目以防跨项目匹配。没有修改旧预算、利润和看板算法；这些旧入口未自动改用A，H接入时勿声称全部旧模块已经统一迁移。

## 状态、缺失及公式规则

- OK：有效来源完整，可包含真实0。
- NO_RECORD：没有审批完成的来源记录。金融流水此时value=0，可参与金额加减；没有施工合同/目标成本时value=null，不得用于算100%利润率。
- MISSING_SOURCE：有合同但最终金额为空、存在多份有效合同、目标成本有效明细缺失、金融主单明细金额缺失等；value=null，不返回部分金额冒充全量。
- NOT_AUTHORIZED：缺该指标任一必要来源权限；value=null，不查询该指标来源，不泄漏证据。其他已授权请求项可独立返回。
- NOT_APPLICABLE：比率分母0；value=null。
- UNSUPPORTED：请求合法但尚未实现的日期模式；value=null，保留原因。

派生项遇到依赖value=null时传递对应不可计算状态及中文原因。NO_RECORD金融流水的明确0可参与计算，但不能把其他状态的null补成0。金额、退款、亏损及超过100%的率保留真实符号。summary的全量汇总/整体比率由B/C/D等调用方按范围金额重算，不能平均项目率；本服务不返回误导性的分页合计。

单次最多10000个输入项目、10000个有效项目、每来源10000条主单记录。SQL取10001条检测溢出，超过明确失败，不用前10000冒充全部。无新增分页截断。PageHelper遗留上下文在入口清理，以免外层分页干扰全量统计。

sampleCount：来源指标为审批完成主单数，目标成本为编制与变更主单数；不是明细行数。派生项无统一样本粒度，sampleCount=null。

## 日期能力矩阵

| 指标组 | CURRENT | PERIOD/AS_OF |
| --- | --- | --- |
| 合同/有效目标成本/预算利润与率 | 支持 | 当前返回UNSUPPORTED；尚无统一的合同、成本历史有效版本及审批时点重建 |
| 回款与其他收入 | 支持 | received_date字段已核对，但A v1尚未实现期间查询，明确返回UNSUPPORTED |
| 付款、其他付款、报销、退款、开票申请及实际收支利润 | 支持 | 日期维度并不统一，A v1明确返回UNSUPPORTED，不用create_date冒充实际发生日 |

period=null或mode为空默认CURRENT；CURRENT携带日期字段会被拒绝，防止静默忽略。PERIOD必须明确dateField与起止日；AS_OF必须明确dateField及结束日且不接收开始日。合法非当前请求只返回不支持状态，不取当前来源金额，更不会把今天审批完成记录冒充当时已审批。

以上是已实现边界，不把“预留枚举”描述成“期间/历史功能已完成”。B/C对合同签约日期选择项目的筛选可在自身范围解析中实现，然后以CURRENT调用A；应告诉用户该条件选择合同/项目，不是期间资金发生额。

## 证据与来源定位

每条证据包含businessType、recordId、recordNo、businessDate、label。金额不附在evidence中，完整金额在对应metric，单据详情由各业务任务重新验权查询。重复请求依赖通过缓存只读取一次来源，缓存仅本次方法内，下一轮重新查询。

businessType：CONSTRUCTION_CONTRACT、TARGET_COST_PREPARATION、TARGET_COST_CHANGE、PAYMENT_REGISTRATION、OTHER_INCOME_REGISTRATION、PAYMENT_REQUEST、OTHER_PAYMENT_REQUEST、PROJECT_REIMBURSE、MATERIAL_REFUND、INVOICE_APPLICATION。

证据日期：回款/其他收入用received_date，报销用reimburse_date，其余为来源单据create_date；后者只是登记日期，不可在回复中宣称为银行支付日/实际开票日。Date格式化明确Asia/Shanghai；所有项目asOf取本次查询同一时刻。

## UAT只读核对记录

连接读取smart-boot/smart-app/src/main/resources/application-uat.yml，目标192.168.1.200:3305/gongcheng，MySQL8.0.39。密码不写交接文档或日志。未修改UAT数据。

1. tenant=000000：未删除项目59个；按本方案项目审批与状态条件可参与35个。此数是核对时的全库样本，不表示某个账号可见35个。
2. 对未删除审批完成、类别1施工合同按项目检查，没有发现重复主单。全租户此类已审批合同24份；另有草稿/审批中/其他未完成状态，基础SQL明确不计。
3. 报销主表不含project_id，明细含project_id。已审批报销明细中未分配项目的数量为0；仍不臆造无明细单据属于哪个项目。
4. 三项目调用新增Mapper实际SELECT和复用的预算片段核对如下，金额单位元：

| 指标 | XM260130001 | 254101 | 004 |
| --- | ---: | ---: | ---: |
| 最终合同额 | 1000000.00 | 600000.00 | 90000000.00 |
| 有效目标成本 | 60600.00 | 502200.00 | 15017000.00 |
| 甲方回款登记 | 10000.00 | 5000.00 | 2000000.00 |
| 其他收入 | 无审批完成记录 | 2000.00 | 1000000.00 |
| 付款申请 | 1300.00 | 116700.00 | 无审批完成记录 |
| 其他付款 | 200.00 | 1100.00 | 无审批完成记录 |
| 项目报销 | 10.00 | 50.00 | 无审批完成记录 |
| 材料退款调整 | 400.00 | 10000.00 | 无审批完成记录 |
| 开票申请 | 100000.00 | 20000.00 | 2000000.00 |

预算证据分别含6/5/4张审批完成编制与变更主单，合并有效明细行数7/9/8。

据以上实测源值按已确认公式人工推算（非声称已运行应用服务）：净付款分别1110.00、107850.00、0；总收入10000.00、7000.00、3000000.00；实际收支利润8890.00、-100850.00、3000000.00。

5. 无有效预算项目006（2017523307537190914）补查：预算源金额NULL、有效明细0，无合同与资金来源返回记录。服务应返回合同/预算NO_RECORD且value=null，预算利润不可计算，而非利润率100%。

可重复执行本目录A-uat-audit.ps1。脚本用结构化XML展开当前使用的Mapper片段，只在START TRANSACTION READ ONLY中SELECT并ROLLBACK，不是完整MyBatis引擎或自动化业务测试。首次核对发现报销来源字段误用，修正为明细归属后重新执行成功。没有借核对脚本写库或验证模型聊天。

## 验证与启动说明

编译命令（smart-boot目录）：

```powershell
$env:JAVA_HOME='F:/java/jdk/1.8'
mvn -o -q -pl smart-ai,smart-business/smart-project -am -DskipTests test-compile
```

日志：smart-boot/analysis-foundation-A-compile.log。自动测试按用户要求未执行；未启动Spring应用或聊天模型。初次编译与SQL修正后的最终编译均退出0，主代码及测试代码编译通过；最终Mapper资源已复制到target/classes/mapper。git diff --check通过；未提交新文件另经源码与XML核对。

A本身无需立即重启验证聊天，因为没有新工具入口。B/H接入后重启smart-boot及包含新工具的smart-agent，Web与数据库本批无需更新。

人工问题（B/H接入对应能力后）：
- 查询XM260130001合同、目标成本、预算利润及实际收支利润，核对上述源值和退款只扣一次。
- 查询254101实际收支利润，保留负数；不能把其他收入算入甲方付款比例。
- 查询006预算利润率，应说明缺少有效合同/目标成本，不能按0成本推算。
- 少预算权限账号只查回款应正常按授权范围取数；查预算利润率应说明来源权限不足。
- 切换账号或收窄项目成员权限后追问同一项目，应重新按当前可信项目范围查询。

## 给下游的下一步

用户将A变更同步/合入各任务起点后，B/C/D/G可并行开始，H可读取本交接梳理公共接入。A没有替他们实现具体场景，不能因此把场景2/3/6/7/8标记完成。E等待B/G，F等待B/C。所有开发继续遵守文件所有权。

尚需人工验收的限制：服务真实Spring注入、签名请求与各账号权限链路；期间/历史金额尚不支持；旧利润/预算工具没有自动迁移至A。上述事项已明确交H/对应业务任务，不要求用户再次确认已定业务规则。
