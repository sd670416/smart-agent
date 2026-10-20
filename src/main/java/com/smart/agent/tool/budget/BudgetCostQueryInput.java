package com.smart.agent.tool.budget;

import java.time.LocalDate;
import java.util.Set;

public record BudgetCostQueryInput(String projectId,String projectName,String projectCode,String mode,
        String dateField,String startDate,String endDate,Integer page,Integer pageSize,String projectNameMatch) {
    public BudgetCostQueryInput {
        projectId=clean(projectId);projectName=clean(projectName);projectCode=clean(projectCode);
        mode=mode==null?"summary":mode;
        projectNameMatch=projectNameMatch==null?"exact":projectNameMatch;
        if(!Set.of("exact","contains").contains(projectNameMatch)
                || "contains".equals(projectNameMatch) && (projectName==null || projectId!=null || projectCode!=null))
            throw new IllegalArgumentException("Invalid project match");
        if(!Set.of("summary","detail","items").contains(mode))throw new IllegalArgumentException("Unsupported mode");
        if("items".equals(mode) && (projectId==null && projectName==null && projectCode==null || "contains".equals(projectNameMatch)))
            throw new IllegalArgumentException("Cost details require one project");
        page=page==null?1:page;pageSize=pageSize==null?20:pageSize;
        if(page<1 || pageSize<1 || pageSize>100)throw new IllegalArgumentException("Invalid pagination");
        if(dateField!=null && !"projectCreateDate".equals(dateField))throw new IllegalArgumentException("Unsupported date field");
        if(startDate!=null || endDate!=null){
            if(dateField==null || startDate==null || endDate==null)throw new IllegalArgumentException("Confirm date dimension and range");
            if(LocalDate.parse(startDate).isAfter(LocalDate.parse(endDate)))throw new IllegalArgumentException("Reversed dates");
            LocalDate.parse(endDate).plusDays(1);
        }
    }
    public BudgetCostQueryInput withProjectId(String id){return new BudgetCostQueryInput(id,null,null,mode,dateField,startDate,endDate,page,pageSize,"exact");}
    private static String clean(String v){return v==null || v.isBlank()?null:v.trim();}
}
