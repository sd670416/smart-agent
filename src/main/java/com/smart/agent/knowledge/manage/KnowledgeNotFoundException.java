package com.smart.agent.knowledge.manage;

public class KnowledgeNotFoundException extends RuntimeException {
    public KnowledgeNotFoundException() { super("Knowledge resource was not found"); }
}
