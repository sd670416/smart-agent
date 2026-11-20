# C 合作历史开发交接

日期：2026-11-20。

## 当前状态

2026-11-20最新状态：A的analysis-v1已在当前checkout，C甲方与供应商业务实现、内部接口、Agent工具均已完成，Boot/Agent主代码及测试代码编译通过。状态为待H接入、待人工验收。以下“依赖缺口”保留为早期检查记录，已由A交付解除。

当前工作目录 `F:/project/gongcheng`，smart-boot分支dev、agent分支main。检查时仅有既有.gitignore和application.yml改动，以及方案文档；未切分支、未创建工作树、未修改这些既有改动。

## 实际依赖缺口

早期检查时以下内容不存在；本次已重新确认全部存在并按analysis-v1实现：

- 本目录 `A-handoff.md`。
- `smart-boot/smart-model/src/main/java/com/smart/model/business/analysis/` 公共VO目录。
- `smart-boot/smart-service/src/main/java/com/smart/service/analysis/` 公共服务接口目录，包括PartnerAccountAnalysisService。

总计划要求A合入后才能实现C，且不允许C重复创建A负责的公共类型。A交付后应重新检查实际契约和源码，不只依据方案中的拟定签名开发。

## 已核对的代码来源

| 业务 | 真实来源 | C实现注意点 |
| --- | --- | --- |
| 甲方主数据 | bus_information_first_party_unit，unit_name、unit_no | 候选限定可信项目内实际关联甲方，精确姓名/ID定位，同名澄清 |
| 甲方合同 | bus_pm_construct_contract_register，first_party_id、project_id、signing_date、final_contract_amount | 仅审批完成登记合同，一项目一有效合同；重复数据不可任意取MAX |
| 甲方回款 | bus_finance_payment_registration，contract_id、contract_type、received_amount、received_date | 沿真实施工合同关联甲方，非按项目把所有收入都算给该甲方；不计其他收入 |
| 确认产值 | bus_pm_output_report.current_period_output_value | 审批完成本期金额合计，非各期累计值相加 |
| 开票申请 | bus_finance_invoice_application.invoice_apply_amount | 仅审批完成，按合同/项目正确归属，不解释成独立实际开票状态 |
| 供应商主数据 | bus_information_supplier，supplier_name、supplier_number、supplier_type、supplier_level | 现合同party_b也可能关联自持公司，不能默默当成供应商档案或按名字合并 |
| 四类供应合同 | bus_contract_material、bus_contract_labor_subcontract、bus_contract_machine_subcontract、bus_contract_major_subcontract | 合同party_b关联供应商，签约字段sign_date；表名只能服务器白名单选择 |
| 供应商结算 | 对应合同表名加_settlement，settlement_amount | 按合同先聚合，再关联，不能多表明细连接扩大金额 |
| 供应商付款 | bus_finance_payment_request_ref.current_payment_value，ref_id关联付款主表 | 先验证主表审批完成、租户、真实合同类型及项目；各合同分别统计 |
| 供应商收票 | bus_finance_invoice_registration.current_rec_invoice_amount | 仅审批完成，合同粒度汇总 |
| 材料净额调整 | BusBoardSupplierService及BusContractMajorSubcontractDao.xml材料查询 | 区分仅退货与退货退款；原服务分别调整结算、付款、收票，不能对已经净额二次扣减 |

参考文件：

- `smart-boot/smart-business/smart-project/src/main/java/com/smart/project/service/BusBoardReceivableService.java`。
- `smart-boot/smart-business/smart-pm/src/main/resources/mapper/BusPmConstructContractRegisterDao.xml`。
- `smart-boot/smart-business/smart-project/src/main/java/com/smart/project/service/BusBoardSupplierService.java`。
- `smart-boot/smart-business/smart-contract/smart-subcontract/src/main/resources/mapper/BusContractMajorSubcontractDao.xml`。
- `smart-boot/smart-business/smart-finance/src/main/resources/mapper/BusFinancePaymentRegistrationDao.xml`。

## 不可直接照搬的旧看板行为

1. 原应收统计包含其他收入，C的甲方回款只取回款登记。
2. 原应收公共where按合同create_date、产值create_date、回款received_date分别套同一日期。C的合作时间筛选应限定合同signing_date/sign_date，再看所选合同当前累计账款，不把合同筛选日期同步裁剪回款。
3. 原供应商materialMap存在仅按projectId索引的处理，C快照必须保留supplierId＋projectId＋contractType＋contractId，不因同项目多合同覆盖记录。
4. 原Mapper有动态表名和多种隐式范围，C新入口必须固定类型白名单并显式增加可信租户/项目/审批完成/删除过滤，不能直接把普通客户端参数送入原看板方法。
5. 旧聚合COALESCE会把无记录和关键金额为空都变成0，C依A契约区分真实0、无记录、缺数据、缺权限。

## 等A交付后的执行顺序

- [x] 阅读C任务、总计划及已确认业务方案。
- [x] 核对仓库状态和甲方/供应商主要取数来源。
- [ ] 核对A-handoff版本及实际VO、PartnerAccountAnalysisService、基础金额服务与权限目录。
- [ ] 实现甲方候选、合作合同列表、全量合计及下钻。
- [ ] 实现供应商四合同来源和公共净账款快照。
- [ ] 实现C专属Controller及Agent Input/Tool/签名Client，公共路由由H处理。
- [ ] 编译Boot业务实现和Agent，补充真实接口请求/响应、日期/权限矩阵及H接入说明。
- [ ] H接入后交用户人工验证。

## 对H/F的说明

本段早期结论已更新：C已实现partnerHistory.query和/internal/ai/tools/partner-history-query，F可在源码同步后调用PartnerAccountAnalysisService，但仍需H权限映射及实际账号人工验收，不能把编译通过当作自然语言已接入。

早期准备轮次只扫描代码。本次已开发并编译，UAT只读核对见下文；未写数据库、未启动服务。下一步交H接入。

## C实际新增文件

- Boot smart-model：`com/smart/model/business/analysis/partner/PartnerHistoryQueryRequestVO.java`。
- Boot smart-service：`com/smart/service/analysis/partner/PartnerHistoryQueryService.java`（私有子包，不修改A接口）。
- Boot smart-project：`com/smart/project/analysis/partner/PartnerHistoryAnalysisService.java`、`PartnerHistoryAnalysisDao.java`、`PartnerHistoryMapperConfiguration.java`。
- Mapper：`smart-business/smart-project/src/main/resources/mapper/PartnerHistoryAnalysisDao.xml`。根目录符合当前扫描规则，专属MapperScan注册C包接口。
- Boot smart-ai：`AiPartnerHistoryToolController.java`、`AiPartnerHistoryToolExceptionHandler.java`。
- Agent：`src/main/java/com/smart/agent/tool/partnerhistory/` 下QueryInput、QueryTool、BusinessClient、SmartBootBusinessClient、BusinessClientConfiguration，共5文件。

公共VO、A接口、B/D源码、旧看板Mapper、公共路由/提示词/profile/pom及权限总开关均未修改。当前工作区其他聊天新增文件继续保留。

## 已实现能力

- CUSTOMER甲方、SUPPLIER供应商两种历史；名称精确定位，同名先返回候选，Agent工具给出带编号的澄清选择。
- 甲方按真实施工合同ID关联回款、开票申请，产值按项目汇总本期完成值；不把其他收入归到甲方。重复有效施工合同标记金额来源不完整，不随意MAX或求和。
- 供应商四合同类型1材料、2劳务、3机械、4专包；每项快照保留supplierId/projectId/contractType/contractId。party_b为自持公司但没有供应商档案的合同不混入供应商历史。
- 结算、付款、收票、入库、仅退货、退货退款分别读取，按合同计算。材料退款分别从结算/已付/收票各自来源扣一次；材料入库净额=入库−仅退货−退货退款。F不要再次扣退款。
- detail/contracts合同分页，summary基于全量匹配合同重算合计与整体比率；项目数按ID去重。
- payments：甲方回款登记或供应商付款明细；invoices：甲方开票申请或供应商收票；settlements仅供应商。带recordId可过滤对应主单，contractId可限定合同。提供记录ID/编号/金额/登记或到账日期，不是完整审批表单或附件。
- 签约日期支持contractSigningDate/startDate/endDate，结束日含当天；只选择合同，不裁剪所选合同的当前累计流水。
- 过滤支持项目ID列表、精确项目名称/编号、项目性质字典值、经理用户ID、所属组织ID。项目状态遵守既定审批/状态范围，未开放任意状态参数。
- 项目性质、供应商类型/等级在对外响应中转换为字典名称，原码仅作内部定位；金额缺失、来源权限不足保留null和中文数据说明。
- 来源与结果最多10000条/项，取10001检测溢出；超过显式拒绝，不把截断结果当全部。PageHelper入口清理。

## 固定调用契约与示例

内部POST：`/internal/ai/tools/partner-history-query`。普通JSON不接受租户、权限和权限开关；Controller从验签后的AiTrustedUserContext构造AnalysisScopeVO。projectIds与可信范围求交，空范围不代表全部。

示例请求（已知UAT真实甲方ID，账号仍需具有该项目范围）：

```json
{"partnerType":"CUSTOMER","partnerId":"2016776040635150338","mode":"detail","page":1,"pageSize":20}
```

Agent输入同请求，增加projectId/projectCode/projectName定位字段，Client将projectId转换成可信projectIds。Tool key=`partnerHistory.query`，拟注册domain=`PARTNER_HISTORY`。contractType仅供应商传1至4，默认查全部四类。page默认1、pageSize默认20且最多100。仅partners模式可不指定合作方；确定partnerId后不再同时传partnerName。

响应采用既有ProjectQueryResultVO/ProjectQueryResult。以下为契约示意，不声称运行过完整HTTP链路：

```json
{"mode":"detail","page":1,"pageSize":20,"total":1,"hasNext":false,
 "columns":[{"key":"contractAmount","label":"施工合同最终金额（元）","type":"number"}],
 "rows":[{"partnerId":"2016776040635150338","partnerName":"YYY甲方单位",
          "contractId":"实际合同ID","projectId":"2016779723397640193","projectCode":"A001",
          "contractAmount":10000000.00,"confirmedOutputAmount":null,
          "dataNotes":"完成产值：对应数据说明","metricStatuses":{},"evidence":[]}],
 "appliedFilters":[{"partnerType":"CUSTOMER","dateMeaning":"签约日期只筛选合同，金额为所选合同当前累计，不是该期间发生额"}]}
```

rows包含真实定位ID、中文列数据、metricStatuses与evidence。模型按columns输出业务表格，不直接展示内部status/key/权限编码。分母0比率null，负余额/超过100%保留。单据明细为来源原额，合同账款汇总为材料退款调整后的净额，不能直接拿原额明细合计替换净额。

## 给F的公共服务

已实现A接口：`PartnerAccountAnalysisService.loadSupplierAccounts(AnalysisScopeVO, AnalysisPeriodVO)`，Spring实现为PartnerHistoryAnalysisService。

返回每份可见供应合同快照，metricKeys：supplierContractAmount、supplierSettlementAmount、supplierPaidAmount、supplierUnpaidAmount、supplierInvoiceAmount、materialInboundAmount、materialReturnAmount、supplierPaymentRate、supplierInvoiceCoverageRate。付款进度分母为合同金额；F付款风险结算分母使用supplierSettlementAmount另算，不能误用supplierPaymentRate。

CURRENT可用；PERIOD/AS_OF合法请求返回UNSUPPORTED而非今天金额。CURRENT带日期拒绝。部分合同来源缺权限时返回该项目/合同类型NOT_AUTHORIZED占位（supplierId/contractId为空），表示此类未完整评估，不是真实合同/供应商或0余额；F应传递未评估提示。无审批完成记录返回NO_RECORD且流水值为0；存在来源金额空值返回MISSING_SOURCE且null。

## H必须完成的接入

1. 注册PARTNER_HISTORY域工具与触发词、可见性；公共路由继承当前域，显式改查项目/审批等切域。不要与C工具创建重复key或Client Bean。
2. 同名/类型澄清选项：partnerType:CUSTOMER/SUPPLIER更新partnerType；partner:<真实ID>写partnerId并清除partnerName；project:<真实ID>限定projectId。清除过期recordId/contractId，避免换人后带旧单据。
3. 上下文持久化有效partnerType/partnerId/contractType/contractId、精确项目与签约日期/模式/分页；每次追问重新调用。序号定位取真实合同ID或recordId，不能用序号替代。
4. 项目合同和产值沿用menu:project；甲方回款menu:finance:payment-registration；开票申请menu:finance:invoice-application；供应商合同menu:cost-contract:material/labor/machine/major，均已存在。
5. 新增来源权限需H映射真实菜单及扩展权限开关的可信READ_PERMISSIONS：结算menu:cost-settlement:material/labor/machine/major；收票menu:finance:invoice-registration；入库menu:material:warehousing。C使用这些明确隔离key，当前工厂尚未映射，启用权限时相关项会NOT_AUTHORIZED，绝不能临时用合同菜单代替来源授权。退款沿用menu:material:refund。
6. 用户只查具体合同类型时只需该类合同权限；未指定类型默认四类，需要四类合同权限，少权限应引导选择已授权类型，不悄悄返回部分当全部。服务内部供F复用支持未授权状态。
7. 错误码PARTNER_HISTORY_FORBIDDEN/INVALID已在C专属ControllerAdvice，Agent转为AGENT_TOOL_FORBIDDEN/AGENT_QUERY_VALUE_INVALID；H应保留业务原因与“合作历史/结算/收票”对应说法，不能泛化成项目无权限。
8. 核对公共内部API校验策略是否需要添加新路径，避免仅Controller存在但拦截不匹配。Tool schema只用现支持关键字，不含enum/minItems/maxItems。

## UAT只读核对与编译

UAT连接由application-uat.yml读取，192.168.1.200:3305/gongcheng；未写库。核对四类合同所需contract_code/name、party_b、sign_date、tenant_id、approval_status字段均存在，结算、付款、收票、入退库相关字段存在。

可供人工定位的实际候选：甲方YYY甲方单位，ID2016776040635150338，对应项目A001、合同M1、最终金额10000000.00；供应商中天材料供应商，ID2016780623516250114，对应002项目合同006、金额1005000.00，以及001项目合同CL20260130001、金额20000.00。这里只核对基础候选与字段，不表示某账号一定能看这些项目，也不表示完整统计接口已运行。

- Agent JAVA_HOME=F:/java/jdk_21/jdk-21.0.6，`mvn -o -q -DskipTests test-compile`，partner-history-C-compile.log，退出0。
- Boot JAVA_HOME=F:/java/jdk/1.8，`mvn -o -q -pl smart-ai,smart-business/smart-project -am -DskipTests test-compile`，partner-history-C-compile.log，首次与最终补字典映射后均退出0。
- 按用户约定未运行自动测试，未启动服务，未验证真实模型调用、Spring注入、Mapper实际完整SQL执行及权限菜单链路。下一步H接入及用户UAT验收。

## 启动与验收

H接入后重启smart-boot与smart-agent；Web和数据库无需变更。

1. “查询YYY甲方单位的合作历史及累计回款。”仅看有权项目，回款不含其他收入。
2. “只看2026年签约的合同。”日期限定合同，金额仍是这些合同当前累计。
3. “查询中天材料供应商的材料合同合作历史。”合同按项目分行，金额与材料退款净额核对。
4. “看第一份合同的结算明细/付款明细/收票明细。”H应以真实合同ID重查，来源权限分别校验。
5. “合计合作过多少项目、合同额多少、还欠多少。”总数全量且项目去重，不按一页计算。
6. 普通账号缺收票权限时显示相应数据不可查看，不按0计算票据覆盖率；关闭权限总开关后保持租户和项目边界。
7. 同名甲方/供应商先选择编号；切换人员或项目范围后追问实时重查，不能沿用旧金额。

后续限制：未增加完整合同表单/变更历史/附件查询；供应商自持公司乙方不并入供应商档案；不支持模糊名称自动扩大范围、任意状态、历史账款快照；组织和经理需真实ID而非模型猜值。以上未实现能力不可对客户宣称已支持。
