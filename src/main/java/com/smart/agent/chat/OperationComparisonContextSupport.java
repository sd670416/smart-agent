package com.smart.agent.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.tool.project.ProjectQueryResult;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/** Keeps matrix project references separate from metric rows and single-project queries. */
final class OperationComparisonContextSupport {
    static final String TOOL = "projectOperation.compare";
    private static final Pattern POSITION = Pattern.compile("第(\\d+)个比较项目");

    private OperationComparisonContextSupport() { }

    static int clarificationIndex(ClarificationPrompt prompt, JsonNode arguments) {
        var match = POSITION.matcher(prompt.question());
        if (!arguments.path("projects").isArray() || !match.find())
            throw new IllegalArgumentException("无法确认需要选择的比较项目，请重新指定准确编号");
        int index = Integer.parseInt(match.group(1)) - 1;
        if (index < 0 || index >= arguments.path("projects").size())
            throw new IllegalArgumentException("比较项目定位失效，请重新查询");
        return index;
    }

    static ObjectNode confirmProject(JsonNode intent, String id, Set<String> allowed) {
        if (!TOOL.equals(intent.path("queryToolKey").asText()) || !allowed.contains(id))
            throw new IllegalArgumentException("比较项目选择已失效，请重新查询");
        ObjectNode arguments = (ObjectNode) intent.path("queryArguments").deepCopy();
        int index = intent.path("comparisonProjectIndex").asInt(-1);
        JsonNode projects = arguments.path("projects");
        if (!projects.isArray() || index < 0 || index >= projects.size() || !projects.path(index).isObject())
            throw new IllegalArgumentException("比较项目选择已失效，请重新查询");
        ObjectNode selected = (ObjectNode) projects.path(index);
        selected.put("projectId", id);
        selected.remove(List.of("projectCode", "projectName"));
        return arguments;
    }

    static ObjectNode inheritArguments(JsonNode input, JsonNode previous) {
        ObjectNode arguments = input.deepCopy();
        for (String field : List.of("metricKeys", "dateMode", "dateField", "startDate", "endDate")) {
            if (!arguments.hasNonNull(field) && previous.hasNonNull(field)) arguments.set(field, previous.get(field));
        }
        if (!arguments.hasNonNull("baselineProjectId")) {
            String baseline = previous.path("baselineProjectId").asText();
            for (JsonNode project : arguments.path("projects")) {
                if (!baseline.isBlank() && baseline.equals(project.path("projectId").asText())) {
                    arguments.put("baselineProjectId", baseline);
                    break;
                }
            }
        }
        return arguments;
    }

    static ClarificationPrompt baselineChoice(JsonNode arguments, JsonNode previous, Set<String> allowed) {
        if (arguments.hasNonNull("baselineProjectId") || previous.path("baselineProjectId").asText().isBlank()) return null;
        JsonNode projects = arguments.path("projects");
        if (!projects.isArray() || projects.size() < 2 || projects.size() > 5) return null;
        var options = new ArrayList<ClarificationPrompt.Option>();
        Set<String> seen = new HashSet<>();
        int index = 0;
        for (JsonNode project : projects) {
            index++;
            String id = project.path("projectId").asText();
            if (!allowed.contains(id) || !seen.add(id))
                throw new AgentException("AGENT_QUERY_VALUE_INVALID", HttpStatus.BAD_REQUEST,
                        "原比较基准已不在列表中，请明确新的基准项目后重新比较");
            String label = project.path("projectName").asText();
            if (label.isBlank()) label = "第" + index + "个比较项目";
            options.add(new ClarificationPrompt.Option("baseline:" + id, label, "PROJECT_OPERATION"));
        }
        return new ClarificationPrompt("原基准项目已移出比较列表，请选择新的基准项目：",
                "OPERATION_BASELINE_REFERENCE", options);
    }

    static ObjectNode confirmBaseline(JsonNode intent, String id, Set<String> allowed) {
        if (!TOOL.equals(intent.path("queryToolKey").asText()) || !allowed.contains(id))
            throw new IllegalArgumentException("比较基准选择已失效，请重新查询");
        ObjectNode arguments = (ObjectNode) intent.path("queryArguments").deepCopy();
        for (JsonNode project : arguments.path("projects")) {
            if (id.equals(project.path("projectId").asText())) {
                arguments.put("baselineProjectId", id);
                return arguments;
            }
        }
        throw new IllegalArgumentException("基准项目必须属于本次比较列表");
    }

    static ObjectNode canonicalArguments(ObjectMapper mapper, JsonNode input,
                                         ProjectQueryResult result, Set<String> allowed) {
        ObjectNode arguments = input.deepCopy();
        for (var filter : result.appliedFilters()) {
            JsonNode metadata = mapper.valueToTree(filter);
            if (!metadata.path("projects").isArray()) continue;
            JsonNode projects = metadata.path("projects");
            if (projects.size() != input.path("projects").size() || projects.size() < 2 || projects.size() > 5)
                throw new IllegalStateException("比较结果的项目定位信息不完整");
            var canonical = mapper.createArrayNode();
            Set<String> ids = new HashSet<>();
            for (JsonNode project : projects) {
                String id = project.path("projectId").asText();
                if (!allowed.contains(id) || !ids.add(id))
                    throw new IllegalStateException("比较结果包含无效的项目定位信息");
                canonical.addObject().put("projectId", id);
            }
            String baseline = metadata.path("baselineProjectId").asText();
            if (!ids.contains(baseline)) throw new IllegalStateException("比较基准信息不完整");
            arguments.set("projects", canonical);
            arguments.put("baselineProjectId", baseline);
            if (metadata.path("metricKeys").isArray()) arguments.set("metricKeys", metadata.path("metricKeys"));
            arguments.put("dateMode", "CURRENT");
            return arguments;
        }
        throw new IllegalStateException("比较结果缺少真实项目定位信息");
    }
}
