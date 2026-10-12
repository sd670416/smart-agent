package com.smart.agent.tool.contract;

public record ConstructionContractQueryInput(String projectId, String projectName, String contractName,
        String mode, String dateField, String startDate, String endDate, Integer page, Integer pageSize) {
    public ConstructionContractQueryInput {
        mode = mode == null ? "detail" : mode;
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
        if (!java.util.List.of("detail", "summary").contains(mode)) throw new IllegalArgumentException("Unsupported mode");
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid pagination");
        if (dateField != null && !java.util.List.of("projectCreateDate", "signingDate", "createDate").contains(dateField))
            throw new IllegalArgumentException("Unsupported date field");
        if (startDate != null || endDate != null) {
            if (dateField == null || startDate == null || endDate == null)
                throw new IllegalArgumentException("Confirm the date field and provide both dates");
            if (java.time.LocalDate.parse(startDate).isAfter(java.time.LocalDate.parse(endDate)))
                throw new IllegalArgumentException("Reversed date range");
        }
    }
}
