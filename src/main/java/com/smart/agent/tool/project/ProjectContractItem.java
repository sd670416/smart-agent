package com.smart.agent.tool.project;

import java.math.BigDecimal;

public record ProjectContractItem(String id, String type, String contractNo, String contractName,
                                  String approvalStatus, BigDecimal amount) {}
