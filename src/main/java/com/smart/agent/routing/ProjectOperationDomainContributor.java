package com.smart.agent.routing;
import java.util.List;
import org.springframework.stereotype.Component;
@Component
public class ProjectOperationDomainContributor implements BusinessDomainContributor {
    public static boolean matches(String q){return q!=null && (q.contains("产值与付款") || q.contains("产值与回款")
            || q.contains("产值回款") || q.contains("产值付款") || q.contains("合同回款比例") || q.contains("产值回款比例")
            || q.contains("两种回款比例") || isOperatingComparison(q) || q.matches(".*产值.*(甲方|回款|收款|付了).*"));}
    public static boolean isOperatingComparison(String q) {
        return q != null && q.matches(".*(比较|对比|相比).*" )
                && (q.contains("经营") || q.contains("横向")
                || q.contains("工期") && (q.contains("利润") || q.contains("合同额") || q.contains("回款"))
                || q.contains("合同额") && (q.contains("目标成本") || q.contains("利润") || q.contains("回款")));
    }
    @Override public BusinessDomainDescriptor descriptor(){return new BusinessDomainDescriptor("PROJECT_OPERATION","项目经营分析",List.of("projectOperation.query","projectOperation.compare"),
        List.of(new DataScopeDescriptor("OPERATION_SOURCES","项目及对应金额来源权限",List.of("MENU","PROJECT"))),"REALTIME",List.of("产值与回款","产值与付款","产值回款","两种回款比例","经营对比","经营比较","横向对比"));}
}
