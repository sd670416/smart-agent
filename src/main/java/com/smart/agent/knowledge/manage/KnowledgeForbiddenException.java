package com.smart.agent.knowledge.manage;

public class KnowledgeForbiddenException extends RuntimeException {
    public KnowledgeForbiddenException() { super("Knowledge resource is not permitted"); }
}
