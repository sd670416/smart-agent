package com.smart.agent.audit;
import java.util.List;import java.util.concurrent.CopyOnWriteArrayList;
public class AuditService { private final List<AuditEvent> events=new CopyOnWriteArrayList<>(); public void record(AuditEvent e){events.add(e);} public List<AuditEvent> events(){return List.copyOf(events);} }
