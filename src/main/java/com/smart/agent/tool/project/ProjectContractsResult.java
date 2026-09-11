package com.smart.agent.tool.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record ProjectContractsResult(String projectId,
                                     @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String contractType,
                                     int page, int pageSize,
                                     long total, boolean hasNext, BigDecimal totalAmount,
                                     List<ProjectContractItem> items) {}
