package com.smart.agent.tool.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record ProjectContractItem(String id,
                                  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String type,
                                  String contractNo, String contractName,
                                  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String approvalStatus,
                                  BigDecimal amount) {}
