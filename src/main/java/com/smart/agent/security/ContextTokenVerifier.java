package com.smart.agent.security;

public interface ContextTokenVerifier {
    AgentUserContext verify(String token);
}
