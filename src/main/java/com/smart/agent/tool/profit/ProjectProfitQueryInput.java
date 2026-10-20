package com.smart.agent.tool.profit;

import java.time.LocalDate;
import java.util.Set;

public record ProjectProfitQueryInput(String projectId, String projectName, String projectCode,
        String mode, String metric, String dateField, String startDate, String endDate,
        Integer page, Integer pageSize, String projectNameMatch) {
    public ProjectProfitQueryInput {
        projectId=clean(projectId);projectName=clean(projectName);projectCode=clean(projectCode);
        mode=mode==null?"summary":mode;metric=metric==null?"both":metric;
        com.smart.agent.routing.ProjectProfitDomainContributor.permissions(metric);
        projectNameMatch=projectNameMatch==null?"exact":projectNameMatch;
        if(!Set.of("exact","contains").contains(projectNameMatch)
                || "contains".equals(projectNameMatch) && (projectName==null || projectId!=null || projectCode!=null))
            throw new IllegalArgumentException("Invalid project match");
        if(!Set.of("summary","detail").contains(mode))throw new IllegalArgumentException("Unsupported mode");
        page=page==null?1:page;pageSize=pageSize==null?20:pageSize;
        if(page<1 || pageSize<1 || pageSize>100)throw new IllegalArgumentException("Invalid pagination");
        if(dateField!=null && !"projectCreateDate".equals(dateField))throw new IllegalArgumentException("Unsupported date field");
        if(startDate!=null || endDate!=null) {
            if(dateField==null || startDate==null || endDate==null)throw new IllegalArgumentException("Confirm date dimension and range");
            if(LocalDate.parse(startDate).isAfter(LocalDate.parse(endDate)))throw new IllegalArgumentException("Reversed dates");
            LocalDate.parse(endDate).plusDays(1);
        }
    }
    public ProjectProfitQueryInput withProjectId(String id) {
        return new ProjectProfitQueryInput(id,null,null,mode,metric,dateField,startDate,endDate,page,pageSize,"exact");
    }
    private static String clean(String value){return value==null || value.isBlank()?null:value.trim();}
}
