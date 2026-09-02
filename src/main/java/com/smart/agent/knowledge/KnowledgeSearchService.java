package com.smart.agent.knowledge;

import com.smart.agent.security.AgentUserContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean({KnowledgeRepository.class, EmbeddingGateway.class, VectorIndex.class})
public class KnowledgeSearchService {
    private final KnowledgeRepository repository;
    private final EmbeddingGateway embeddingGateway;
    private final VectorIndex vectorIndex;

    public KnowledgeSearchService(
            KnowledgeRepository repository, EmbeddingGateway embeddingGateway, VectorIndex vectorIndex) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.embeddingGateway = Objects.requireNonNull(embeddingGateway, "embeddingGateway must not be null");
        this.vectorIndex = Objects.requireNonNull(vectorIndex, "vectorIndex must not be null");
    }

    public List<KnowledgeCitation> search(KnowledgeSearchQuery query, AgentUserContext context) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (!context.canAccessProject(query.projectId())) {
            throw new IllegalArgumentException("project is not accessible to this user");
        }

        List<Float> vector = embeddingGateway.embed(query.query());
        VectorSearchQuery vectorQuery = new VectorSearchQuery(
                context.tenantId(), query.allowedSpaceIds(), Set.of(query.projectId()), vector, query.topK());
        List<VectorHit> hits = vectorIndex.search(vectorQuery).stream().limit(query.topK()).toList();
        if (hits.isEmpty()) {
            return List.of();
        }

        Map<String, KnowledgeChunkMetadata> metadataByChunkId = metadataByChunkId(
                context.tenantId(), hits.stream().map(VectorHit::chunkId).filter(Objects::nonNull).toList());
        return hits.stream()
                .map(hit -> toCitation(hit, metadataByChunkId.get(hit.chunkId()), query, context))
                .filter(Objects::nonNull)
                .limit(query.topK())
                .toList();
    }

    private Map<String, KnowledgeChunkMetadata> metadataByChunkId(String tenantId, Collection<String> chunkIds) {
        if (chunkIds.isEmpty()) {
            return Map.of();
        }
        Map<String, KnowledgeChunkMetadata> metadata = new LinkedHashMap<>();
        for (KnowledgeChunkMetadata chunk : repository.findPublishedChunks(tenantId, Set.copyOf(chunkIds))) {
            if (chunk != null) {
                metadata.putIfAbsent(chunk.chunkId(), chunk);
            }
        }
        return metadata;
    }

    private static KnowledgeCitation toCitation(
            VectorHit hit, KnowledgeChunkMetadata chunk, KnowledgeSearchQuery query, AgentUserContext context) {
        if (hit == null || chunk == null || !Double.isFinite(hit.score())) {
            return null;
        }
        if (!context.tenantId().equals(chunk.tenantId())
                || !chunk.documentId().equals(hit.documentId())
                || !query.allowedSpaceIds().contains(chunk.spaceId())
                || (chunk.projectId() != null && !query.projectId().equals(chunk.projectId()))
                || !chunk.published()
                || chunk.deleted()) {
            return null;
        }
        return new KnowledgeCitation(
                chunk.documentId(), chunk.versionId(), chunk.title(), chunk.pageNumber(), chunk.sectionTitle(),
                chunk.content(), hit.score(), citationToken(context.tenantId(), chunk));
    }

    private static String citationToken(String tenantId, KnowledgeChunkMetadata chunk) {
        return sha256(String.join("|", tenantId, chunk.documentId(), chunk.versionId(), chunk.chunkId()));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
