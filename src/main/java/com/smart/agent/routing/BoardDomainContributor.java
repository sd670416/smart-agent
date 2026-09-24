package com.smart.agent.routing;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BoardDomainContributor implements BusinessDomainContributor {
    private static final BusinessDomainDescriptor DESCRIPTOR = new BusinessDomainDescriptor(
            "BOARD", "看板", List.of("board.query", "board.compare", "board.detail"),
            List.of(new DataScopeDescriptor("BOARD_MENU", "看板菜单权限", List.of("MENU"))),
            "REALTIME", List.of("看板", "经营看板", "预算看板", "收款看板"));

    @Override
    public BusinessDomainDescriptor descriptor() { return DESCRIPTOR; }
}
