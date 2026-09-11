package com.smart.agent.tool.project;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AccessibleProjectItem(String projectId, String projectName, String projectCode,
                                    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String status,
                                    String statusName, String personInChargeName, String projectBudget,
                                    String overview) {}
