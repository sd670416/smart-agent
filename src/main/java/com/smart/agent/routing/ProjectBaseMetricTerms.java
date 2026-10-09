package com.smart.agent.routing;

import java.util.List;

final class ProjectBaseMetricTerms {
    static final List<String> TERMS = List.of("项目总数", "项目数量", "各类型项目数量",
            "项目区域分布", "区域分布", "区域统计", "地区统计", "地域统计", "各省", "省份", "地区分布", "地域分布",
            "项目类型分布", "项目状态分布", "类型项目数量分布", "状态分布");

    private ProjectBaseMetricTerms() {}

    static boolean matches(String question) {
        return (question.contains("项目") || question.contains("经营看板"))
                && TERMS.stream().anyMatch(question::contains);
    }
}
