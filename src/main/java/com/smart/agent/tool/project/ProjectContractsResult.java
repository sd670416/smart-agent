package com.smart.agent.tool.project;

import java.math.BigDecimal;
import java.util.List;

public record ProjectContractsResult(String projectId, String contractType, int page, int pageSize,
                                     long total, boolean hasNext, BigDecimal totalAmount,
                                     List<ProjectContractItem> items) {}
