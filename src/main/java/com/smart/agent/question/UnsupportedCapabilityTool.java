package com.smart.agent.question;

import com.smart.agent.tool.*;
import org.springframework.stereotype.Component;

@Component
public class UnsupportedCapabilityTool implements AgentTool<UnsupportedCapabilityTool.Input,String> {
    public record Input(String reason) {}
    public String key() { return "system.unsupported_capability"; }
    public Class<Input> inputType() { return Input.class; }
    public String requiredPermission() { return null; }
    public ToolRisk risk() { return ToolRisk.L0; }
    public String description() { return "Mark a capability or field as unsupported only after available queries cannot fulfill it. Never use for no data, denied permission, clarification or a successful answer. This grants no query permissions. Explain the unsupported capability in the final answer."; }
    public String argumentsSchemaJson() { return "{\"type\":\"object\",\"properties\":{\"reason\":{\"type\":\"string\",\"description\":\"Unsupported capability reason, 1 to 500 characters\"}},\"required\":[\"reason\"],\"additionalProperties\":false}"; }
    public String execute(Input input,ToolContext context) { return "Capability marker received"; }
}
