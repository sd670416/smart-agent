# C 合作历史明细实施计划

**Goal:** 完成场景3的甲方及供应商合作历史，支持明细、合计与业务单据下钻。
**Architecture:** 统一partnerHistory入口，按甲方/供应商分支；实现公共PartnerAccountAnalysisService供F复用供应商净账款。
**Tech Stack:** Boot Java8/MyBatis，Agent Java21，既有响应和项目定位。
**Spec:** [总计划](00-start-here.md)、[业务规则](../2026-11-20-business-analysis-scenarios-proposal.md)、A-handoff.md。

## 依赖与文件

A合入后可与B/D/G并行。独占：
- Boot `smart-business/smart-project/src/main/java/com/smart/project/analysis/partner/` 下ServiceImpl、Dao。
- Boot `smart-business/smart-project/src/main/resources/mapper/analysis/PartnerHistoryAnalysisDao.xml`。
- Boot `smart-model/src/main/java/com/smart/model/business/analysis/partner/PartnerHistoryQueryRequestVO.java`。
- Boot `smart-ai/src/main/java/com/smart/ai/controller/AiPartnerHistoryToolController.java`。
- Agent `src/main/java/com/smart/agent/tool/partnerhistory/` 下本域Input、Tool、Client、签名Client及Configuration。

原有BusBoardReceivableService、BusBoardSupplierService和合同Mapper只读参考，不自行修改看板统计。公共文件交H。

## 固定接口与内部输出

Tool `partnerHistory.query`，POST `/internal/ai/tools/partner-history-query`，使用ProjectQueryResultVO。

请求：partnerType(CUSTOMER/SUPPLIER)、partnerId/partnerName、contractType、projectId/projectCode/projectName、mode(partners/detail/summary/contracts/settlements/payments/invoices)、dateField=contractSigningDate、startDate/endDate、page/pageSize；用服务端允许值验证，不支持的mode显式说明。partners仅返回有权查看的定位候选，不泄漏全量人员/合作方档案。

供应商内部快照按供应商ID＋项目ID＋合同类型＋合同ID粒度输出，汇总需显式去重。metricKeys固定：supplierContractAmount、supplierSettlementAmount、supplierPaidAmount、supplierUnpaidAmount、supplierInvoiceAmount、materialInboundAmount、materialReturnAmount、supplierPaymentRate、supplierInvoiceCoverageRate。结算/付款/收票采用既有有效净额来源，退款每一来源只调整一次；F仅复用结算、净付款、未付、收票事实，不能再次扣退款。

## 执行步骤

- [ ] 先实现甲方候选定位，按真实ID关联施工合同与项目；同名/类型不明确返回候选，不按名称模糊拼金额。
- [ ] 实现甲方历史：合同额、签约时间、产值、开票申请、甲方回款、未收、两类比例；收入只取对应甲方回款。证据来自真实单据。
- [ ] 甲方合同签约日期筛选选择合同后，展示其项目当前累计；不得把该日期偷偷用于过滤回款单发生时间。响应说明选择范围。
- [ ] 实现供应商四种合同来源及PartnerAccountAnalysisService，材料入退库/退货退款净额对照原服务，缺来源返回缺失而非0。
- [ ] 实现全量汇总后分页，合作项目数按项目ID去重；跨合同合计不能把一个项目数成多项目。
- [ ] 实现已存在单据的明细下钻，重新按可信项目＋合作方＋真实单据ID校验，不信任历史页面ID。
- [ ] 实现Agent签名调用、中文columns、参数范围校验；权限来源清单交H映射。
- [ ] 编译覆盖业务实现及Agent，分别记录甲方与供应商完成状态到C-handoff。

## 验收

H接入后重启Boot/Agent。问题：“查某甲方合作过的项目和回款”“只看2025年签约的合同”“某供应商合作过哪些项目，还欠多少，票收了多少”“查看第一份合同”。核对跨项目权限、四合同类型、材料退款、汇总与分页一致、同名确认、零分母和真实0。

先交甲方，再交供应商，允许H分两次接入；F必须等供应商内部服务合入。合作未付为0只称账款结清，不断言合作终止。
