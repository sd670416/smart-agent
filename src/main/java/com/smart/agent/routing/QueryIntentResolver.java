package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Deterministic final裁决 after model or keyword candidate generation. */
@Component
public class QueryIntentResolver {
    private final BusinessDomainRegistry registry;

    public QueryIntentResolver(BusinessDomainRegistry registry) {
        this.registry = registry;
    }

    public IntentResolution resolve(String question, Map<String, ?> pageContext,
            String activeDomain, AgentUserContext userContext) {
        String normalized = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return IntentResolution.unknown(question);
        boolean workflow = ConstructionContractDomainContributor.isWorkflowQuery(normalized);
        if (!workflow && registry.findDomain(CompanyOperatingDomainContributor.CODE).isPresent()
                && (CompanyOperatingDomainContributor.matches(normalized) || CompanyOperatingDomainContributor.CODE.equals(activeDomain)
                        && CompanyOperatingDomainContributor.isCompanyReference(normalized))) {
            boolean inheritedMetric = CompanyOperatingDomainContributor.CODE.equals(activeDomain)
                    && !CompanyOperatingDomainContributor.hasExplicitMetric(normalized);
            if (userContext == null || !(inheritedMetric ? userContext.permissions().contains("menu:project")
                    : userContext.permissions().containsAll(CompanyOperatingDomainContributor.permissions(CompanyOperatingDomainContributor.metric(normalized)))))
                return IntentResolution.unknown(question);
            return IntentResolution.resolved(new QueryIntentCandidate(registry.findDomain(CompanyOperatingDomainContributor.CODE).orElseThrow(), "自持公司经营", 100));
        }
        if (!workflow && registry.findDomain(CostContractDomainContributor.CODE).isPresent()
                && CostContractDomainContributor.matches(normalized)) {
            if (userContext == null || !userContext.permissions().containsAll(
                    CostContractDomainContributor.permissions(CostContractDomainContributor.source(normalized))))
                return IntentResolution.unknown(question);
            return IntentResolution.resolved(new QueryIntentCandidate(
                    registry.findDomain(CostContractDomainContributor.CODE).orElseThrow(), "成本合同", 100));
        }
        if (!workflow && registry.findDomain(ProjectProgressDomainContributor.CODE).isPresent()
                && ProjectProgressDomainContributor.matches(normalized)) {
            if (userContext == null || !userContext.permissions().contains("menu:project")) return IntentResolution.unknown(question);
            return IntentResolution.resolved(new QueryIntentCandidate(
                    registry.findDomain(ProjectProgressDomainContributor.CODE).orElseThrow(), "施工进度", 100));
        }
        if (!workflow && registry.findDomain(InvoiceApplicationDomainContributor.CODE).isPresent()
                && InvoiceApplicationDomainContributor.matches(normalized)
                && (userContext == null || !userContext.permissions().contains(InvoiceApplicationDomainContributor.PERMISSION)))
            return IntentResolution.unknown(question);
        if (!workflow && registry.findDomain(ProjectProfitDomainContributor.CODE).isPresent() && ProjectProfitDomainContributor.matches(normalized)) {
            boolean inheritedMetric = ProjectProfitDomainContributor.CODE.equals(activeDomain)
                    && !ProjectProfitDomainContributor.hasExplicitMetric(normalized);
            if (userContext == null || !(inheritedMetric
                    ? userContext.permissions().containsAll(ProjectProfitDomainContributor.permissions("budget"))
                            || userContext.permissions().containsAll(ProjectProfitDomainContributor.permissions("actual"))
                    : userContext.permissions().containsAll(ProjectProfitDomainContributor.permissions(ProjectProfitDomainContributor.metric(normalized)))))
                return IntentResolution.unknown(question);
            return IntentResolution.resolved(new QueryIntentCandidate(registry.findDomain(ProjectProfitDomainContributor.CODE).orElseThrow(), "项目利润", 100));
        }
        if (!workflow && registry.findDomain(BudgetCostDomainContributor.CODE).isPresent()
                && BudgetCostDomainContributor.matches(normalized)) {
            if (userContext == null || !userContext.permissions().contains(BudgetCostDomainContributor.PERMISSION))
                return IntentResolution.unknown(question);
            return IntentResolution.resolved(new QueryIntentCandidate(
                    registry.findDomain(BudgetCostDomainContributor.CODE).orElseThrow(), "预算成本", 100));
        }
        if (!workflow && registry.findDomain(ProjectFundingDomainContributor.CODE).isPresent()
                && ProjectFundingDomainContributor.matches(normalized)
                && (userContext == null || !userContext.permissions().containsAll(
                        ProjectFundingDomainContributor.permissions(ProjectFundingDomainContributor.metric(normalized)))))
            return IntentResolution.unknown(question);
        if (!workflow && registry.findDomain(ProjectFundingDomainContributor.CODE).isPresent()
                && ProjectFundingDomainContributor.matches(normalized))
            return IntentResolution.resolved(new QueryIntentCandidate(
                    registry.findDomain(ProjectFundingDomainContributor.CODE).orElseThrow(), "组合财务指标", 100));
        if (!workflow && registry.findDomain(MaterialRefundDomainContributor.CODE).isPresent()
                && MaterialRefundDomainContributor.matches(normalized)
                && (userContext == null || !userContext.permissions().contains(MaterialRefundDomainContributor.PERMISSION)))
            return IntentResolution.unknown(question);
        if (!workflow && registry.findDomain(ProjectReimburseDomainContributor.CODE).isPresent()
                && ProjectReimburseDomainContributor.matches(normalized)
                && (userContext == null || !userContext.permissions().contains(ProjectReimburseDomainContributor.PERMISSION)))
            return IntentResolution.unknown(question);
        if (!workflow && registry.findDomain(OtherPaymentRequestDomainContributor.CODE).isPresent()
                && OtherPaymentRequestDomainContributor.matches(normalized)
                && (userContext == null || !userContext.permissions().contains(OtherPaymentRequestDomainContributor.PERMISSION)))
            return IntentResolution.unknown(question);
        if (!workflow && registry.findDomain(PaymentRequestDomainContributor.CODE).isPresent()
                && PaymentRequestDomainContributor.matches(normalized)
                && (userContext == null || !userContext.permissions().contains(PaymentRequestDomainContributor.PERMISSION)))
            return IntentResolution.unknown(question);
        boolean receipts = registry.findDomain(ProjectReceiptsDomainContributor.CODE).isPresent()
                && ProjectReceiptsDomainContributor.matches(normalized);
        if (!workflow && receipts && (userContext == null || !ProjectReceiptsDomainContributor.canAccess(userContext.permissions())))
            return IntentResolution.unknown(question);
        if (!workflow && registry.findDomain(OtherIncomeRegistrationDomainContributor.CODE).isPresent()
                && !receipts
                && OtherIncomeRegistrationDomainContributor.matches(normalized)
                && (userContext == null || !userContext.permissions().contains(OtherIncomeRegistrationDomainContributor.PERMISSION)))
            return IntentResolution.unknown(question);
        if (!workflow && registry.findDomain(PaymentRegistrationDomainContributor.CODE).isPresent()
                && !receipts
                && !OtherIncomeRegistrationDomainContributor.matches(normalized)
                && PaymentRegistrationDomainContributor.matches(normalized)
                && (userContext == null || !userContext.permissions().contains(PaymentRegistrationDomainContributor.PERMISSION)))
            return IntentResolution.unknown(question);
        if (!workflow && ProjectOutputDomainContributor.isOtherOutput(normalized)) return IntentResolution.unknown(question);
        if (!workflow && ProjectOutputDomainContributor.isAmbiguousOutput(normalized)
                && !ProjectOutputDomainContributor.CODE.equals(activeDomain)) return IntentResolution.unknown(question);
        boolean outputQuery = registry.findDomain(ProjectOutputDomainContributor.CODE).isPresent()
                && ProjectOutputDomainContributor.matches(normalized)
                && !ConstructionContractDomainContributor.isWorkflowQuery(normalized);
        if (outputQuery && (userContext == null || !userContext.permissions().contains("menu:project")))
            return IntentResolution.unknown(question);
        boolean contractQuery = registry.findDomain(ConstructionContractDomainContributor.CODE).isPresent()
                && ConstructionContractDomainContributor.matches(normalized)
                && !ConstructionContractDomainContributor.isWorkflowQuery(normalized);
        if (contractQuery && (userContext == null || !userContext.permissions().contains("menu:project"))) {
            return IntentResolution.unknown(question);
        }
        if (ProjectBaseMetricTerms.matches(normalized)
                && (userContext == null || !userContext.permissions().contains("menu:project"))) {
            return IntentResolution.unknown(question);
        }
        List<QueryIntentCandidate> candidates = new ArrayList<>();
        for (BusinessDomainDescriptor domain : registry.availableDomains(userContext)) {
            int score = score(domain, normalized, pageContext, activeDomain);
            if (score > 0) candidates.add(new QueryIntentCandidate(domain, reason(domain, score), score));
        }
        candidates.sort(Comparator.comparingInt(QueryIntentCandidate::score).reversed());
        if (candidates.isEmpty()) return IntentResolution.unknown(question);
        if (candidates.size() == 1 || candidates.get(0).score() > candidates.get(1).score()) {
            return IntentResolution.resolved(candidates.get(0));
        }
        return IntentResolution.needsClarification(question, candidates);
    }

    /** Returns the registered domain for a tool without exposing the registry internals. */
    public String domainCodeForTool(String toolKey) {
        return registry.findByTool(toolKey).map(BusinessDomainDescriptor::code).orElse(null);
    }

    public java.util.Optional<BusinessDomainDescriptor> domain(String code) {
        return registry.findDomain(code);
    }

    public IntentResolution resolveSelectedDomain(
            String domainCode, String originalQuestion, AgentUserContext userContext) {
        return registry.availableDomains(userContext).stream()
                .filter(domain -> domain.code().equals(domainCode))
                .findFirst()
                .map(domain -> IntentResolution.resolved(new QueryIntentCandidate(
                        domain, "用户确认业务域", Integer.MAX_VALUE)))
                .orElseThrow(() -> new IllegalArgumentException("Selected business domain is unavailable"));
    }

    private int score(BusinessDomainDescriptor domain, String question,
            Map<String, ?> pageContext, String activeDomain) {
        int score = 0;
        if (registry.findDomain(CompanyOperatingDomainContributor.CODE).isPresent()
                && (CompanyOperatingDomainContributor.matches(question) || question.contains("自持公司") && ConstructionContractDomainContributor.isWorkflowQuery(question))) {
            if (ConstructionContractDomainContributor.isWorkflowQuery(question)) return "APPROVAL".equals(domain.code()) ? 100 : 0;
            return CompanyOperatingDomainContributor.CODE.equals(domain.code()) ? 100 : 0;
        }
        if (registry.findDomain(CostContractDomainContributor.CODE).isPresent() && CostContractDomainContributor.matches(question)) {
            if (ConstructionContractDomainContributor.isWorkflowQuery(question)) return "APPROVAL".equals(domain.code()) ? 100 : 0;
            return CostContractDomainContributor.CODE.equals(domain.code()) ? 100 : 0;
        }
        if (registry.findDomain(InvoiceApplicationDomainContributor.CODE).isPresent() && InvoiceApplicationDomainContributor.matches(question)) {
            if (ConstructionContractDomainContributor.isWorkflowQuery(question)) return "APPROVAL".equals(domain.code()) ? 100 : 0;
            return InvoiceApplicationDomainContributor.CODE.equals(domain.code()) ? 100 : 0;
        }
        if (registry.findDomain(ProjectProgressDomainContributor.CODE).isPresent() && ProjectProgressDomainContributor.matches(question)) {
            if (ConstructionContractDomainContributor.isWorkflowQuery(question)) return "APPROVAL".equals(domain.code()) ? 100 : 0;
            return ProjectProgressDomainContributor.CODE.equals(domain.code()) ? 100 : 0;
        }
        if (registry.findDomain(ProjectProfitDomainContributor.CODE).isPresent() && ProjectProfitDomainContributor.matches(question)) {
            if (ConstructionContractDomainContributor.isWorkflowQuery(question)) return "APPROVAL".equals(domain.code()) ? 100 : 0;
            return ProjectProfitDomainContributor.CODE.equals(domain.code()) ? 100 : 0;
        }
        if (registry.findDomain(BudgetCostDomainContributor.CODE).isPresent() && BudgetCostDomainContributor.matches(question)) {
            if (ConstructionContractDomainContributor.isWorkflowQuery(question)) return "APPROVAL".equals(domain.code()) ? 100 : 0;
            return BudgetCostDomainContributor.CODE.equals(domain.code()) ? 100 : 0;
        }
        if (registry.findDomain(ProjectFundingDomainContributor.CODE).isPresent()
                && ProjectFundingDomainContributor.matches(question)) {
            if (ConstructionContractDomainContributor.isWorkflowQuery(question))
                return "APPROVAL".equals(domain.code()) ? 100 : 0;
            return ProjectFundingDomainContributor.CODE.equals(domain.code()) ? 100 : 0;
        }
        if (registry.findDomain(MaterialRefundDomainContributor.CODE).isPresent()
                && MaterialRefundDomainContributor.matches(question)) {
            if (ConstructionContractDomainContributor.isWorkflowQuery(question))
                return "APPROVAL".equals(domain.code()) ? 100 : 0;
            return MaterialRefundDomainContributor.CODE.equals(domain.code()) ? 100 : 0;
        }
        if (registry.findDomain(ProjectReimburseDomainContributor.CODE).isPresent()
                && ProjectReimburseDomainContributor.matches(question)) {
            if (ConstructionContractDomainContributor.isWorkflowQuery(question))
                return "APPROVAL".equals(domain.code()) ? 100 : 0;
            return ProjectReimburseDomainContributor.CODE.equals(domain.code()) ? 100 : 0;
        }
        if (registry.findDomain(OtherPaymentRequestDomainContributor.CODE).isPresent()
                && OtherPaymentRequestDomainContributor.matches(question)) {
            if (ConstructionContractDomainContributor.isWorkflowQuery(question))
                return "APPROVAL".equals(domain.code()) ? 100 : 0;
            return OtherPaymentRequestDomainContributor.CODE.equals(domain.code()) ? 100 : 0;
        }
        if (PaymentRequestDomainContributor.CODE.equals(domain.code()) && question.contains("其他付款")) return 0;
        if (registry.findDomain(PaymentRequestDomainContributor.CODE).isPresent()
                && PaymentRequestDomainContributor.matches(question)) {
            if (ConstructionContractDomainContributor.isWorkflowQuery(question))
                return "APPROVAL".equals(domain.code()) ? 100 : 0;
            return PaymentRequestDomainContributor.CODE.equals(domain.code()) ? 100 : 0;
        }
        if (registry.findDomain(ProjectReceiptsDomainContributor.CODE).isPresent()
                && ProjectReceiptsDomainContributor.matches(question)) {
            if (ConstructionContractDomainContributor.isWorkflowQuery(question))
                return "APPROVAL".equals(domain.code()) ? 100 : 0;
            return ProjectReceiptsDomainContributor.CODE.equals(domain.code()) ? 100 : 0;
        }
        if (registry.findDomain(OtherIncomeRegistrationDomainContributor.CODE).isPresent()
                && OtherIncomeRegistrationDomainContributor.matches(question)) {
            if (ConstructionContractDomainContributor.isWorkflowQuery(question))
                return "APPROVAL".equals(domain.code()) ? 100 : 0;
            return OtherIncomeRegistrationDomainContributor.CODE.equals(domain.code()) ? 100 : 0;
        }
        if (registry.findDomain(PaymentRegistrationDomainContributor.CODE).isPresent()
                && PaymentRegistrationDomainContributor.matches(question)) {
            if (ConstructionContractDomainContributor.isWorkflowQuery(question))
                return "APPROVAL".equals(domain.code()) ? 100 : 0;
            return PaymentRegistrationDomainContributor.CODE.equals(domain.code()) ? 100 : 0;
        }
        if (registry.findDomain(ProjectOutputDomainContributor.CODE).isPresent()
                && ProjectOutputDomainContributor.matches(question)) {
            if (ConstructionContractDomainContributor.isWorkflowQuery(question))
                return "APPROVAL".equals(domain.code()) ? 100 : 0;
            if (ProjectOutputDomainContributor.CODE.equals(domain.code())) score += 50;
        }
        if (registry.findDomain(ConstructionContractDomainContributor.CODE).isPresent()
                && ConstructionContractDomainContributor.matches(question)) {
            if (ConstructionContractDomainContributor.isWorkflowQuery(question))
                return "APPROVAL".equals(domain.code()) ? 100 : 0;
            if (ConstructionContractDomainContributor.CODE.equals(domain.code())) score += 50;
        }
        for (String term : domain.triggerTerms()) {
            if (term != null && !term.isBlank() && question.contains(term.toLowerCase(Locale.ROOT))) score += 10;
        }
        if ("PROJECT".equals(domain.code()) && ProjectBaseMetricTerms.matches(question)) score += 10;
        if ("BOARD".equals(domain.code()) && domain.triggerTerms().stream()
                .anyMatch(term -> term != null && term.length() >= 2
                        && !term.endsWith("看板") && !"甘特图".equals(term)
                        && question.contains(term.toLowerCase(Locale.ROOT)))) {
            score += 15;
        }
        if (domain.code().equalsIgnoreCase(activeDomain) && isContextualFollowUp(question)) score += 3;
        if (ProjectProgressDomainContributor.CODE.equals(activeDomain) && ProjectProgressDomainContributor.CODE.equals(domain.code())
                && (question.matches(".*第[一二三四五六七八九十百零两0-9]+[个条项].*") || question.contains("任务"))
                && !ConstructionContractDomainContributor.isWorkflowQuery(question)
                && !question.matches(".*(档案|项目信息|改查|切换|转查).*")) score += 20;
        // A project ordinal in the current profit list does not switch to project archives.
        if (ProjectProfitDomainContributor.CODE.equals(activeDomain) && ProjectProfitDomainContributor.CODE.equals(domain.code())
                && question.matches(".*第[一二三四五六七八九十百零两0-9]+[个条项].*")
                && !question.matches(".*(档案|项目信息|改查|切换|转查).*")) score += 20;
        if (pageContext != null) {
            String page = String.valueOf(pageContext.get("routeName")) + " "
                    + String.valueOf(pageContext.get("routePath"));
            page = page.toLowerCase(Locale.ROOT);
            for (String term : domain.triggerTerms()) {
                if (term != null && page.contains(term)) score += 2;
            }
        }
        return score;
    }

    private boolean isContextualFollowUp(String question) {
        return question.matches(".*(查|看|多少|数量|金额|总额|合计|总计|签订|创建|日期|第|上一页|下一页|明细|详情|筛选|状态|比较|对比|继续|项目|审批|看板|这[份张笔单]|该合同).*" );
    }

    private String reason(BusinessDomainDescriptor domain, int score) {
        return "匹配业务域关键词或当前会话上下文，评分=" + score;
    }
}
