package com.smart.agent.routing;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BoardDomainContributor implements BusinessDomainContributor {
    private static final BusinessDomainDescriptor DESCRIPTOR = new BusinessDomainDescriptor(
            "BOARD", "看板", List.of("board.query", "board.compare", "board.detail"),
            List.of(new DataScopeDescriptor("BOARD_MENU", "看板菜单权限", List.of("MENU"))),
            "REALTIME", List.of("经营看板", "预算看板", "应收看板", "收款看板", "供应商看板",
                    "投标看板", "库存看板", "项目看板", "甘特图", "建设合同总金额", "累计结算金额",
                    "已收项目款", "当前项目垫资", "实际项目利润", "各类型项目合同金额",
                    "项目完成进度", "项目各项成本费用", "各自持公司经营数据",
                    "项目垫资分析", "项目利润对比分析", "项目垫资明细", "项目利润对比明细",
                    "类型项目合同金额分布", "完成进度",
                    "成本费用占比", "自持公司经营数据", "垫资分析", "利润对比分析",
                    "垫资明细", "利润对比明细"));

    @Override
    public BusinessDomainDescriptor descriptor() { return DESCRIPTOR; }

}
