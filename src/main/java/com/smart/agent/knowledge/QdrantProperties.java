package com.smart.agent.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("agent.qdrant")
public class QdrantProperties {
    private boolean enabled;
    private String host = "localhost";
    private int grpcPort = 6334;
    private String apiKey = "";
    private boolean tls;
    private String collectionName = "agent_knowledge_dev";
    private String attachmentCollectionName = "agent_conversation_attachment_dev";
    private int vectorDimension = LocalHashEmbeddingGateway.DIMENSIONS;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getGrpcPort() {
        return grpcPort;
    }

    public void setGrpcPort(int grpcPort) {
        this.grpcPort = grpcPort;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isTls() {
        return tls;
    }

    public void setTls(boolean tls) {
        this.tls = tls;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getAttachmentCollectionName() {
        return attachmentCollectionName;
    }

    public void setAttachmentCollectionName(String attachmentCollectionName) {
        this.attachmentCollectionName = attachmentCollectionName;
    }

    public int getVectorDimension() {
        return vectorDimension;
    }

    public void setVectorDimension(int vectorDimension) {
        this.vectorDimension = vectorDimension;
    }
}
