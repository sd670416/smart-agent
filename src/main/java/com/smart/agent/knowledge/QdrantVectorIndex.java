package com.smart.agent.knowledge;

import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.ConditionFactory.matchKeywords;
import static io.qdrant.client.ValueFactory.value;

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public final class QdrantVectorIndex implements VectorIndex {
    private static final List<String> INDEXED_PAYLOAD_FIELDS =
            List.of("tenant_id", "space_id", "project_id", "status");

    private final QdrantClient client;
    private final String collectionName;
    private final int vectorDimension;

    public QdrantVectorIndex(QdrantClient client, String collectionName, int vectorDimension) {
        if (client == null) {
            throw new IllegalArgumentException("client must not be null");
        }
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("collectionName must not be blank");
        }
        if (vectorDimension < 1) {
            throw new IllegalArgumentException("vectorDimension must be positive");
        }
        this.client = client;
        this.collectionName = collectionName;
        this.vectorDimension = vectorDimension;
        initializeCollection();
    }

    @Override
    public void upsert(List<IndexedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        List<Points.PointStruct> points = chunks.stream().map(this::toPoint).toList();
        await(client.upsertAsync(collectionName, points), "VECTOR_INDEX_UPSERT_FAILED", "Vector upsert failed");
    }

    @Override
    public List<VectorHit> search(VectorSearchQuery query) {
        requireDimension(query.vector());
        Points.SearchPoints request = Points.SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllVector(query.vector())
                .setFilter(buildFilter(query))
                .setLimit(query.topK())
                .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true))
                .build();
        return await(client.searchAsync(request), "VECTOR_INDEX_SEARCH_FAILED", "Vector search failed").stream()
                .map(QdrantVectorIndex::toHit)
                .toList();
    }

    @Override
    public void deleteDocument(String tenantId, String documentId) {
        Common.Filter filter = Common.Filter.newBuilder()
                .addMust(matchKeyword("tenant_id", requireText(tenantId, "tenantId")))
                .addMust(matchKeyword("document_id", requireText(documentId, "documentId")))
                .build();
        await(client.deleteAsync(collectionName, filter), "VECTOR_INDEX_DELETE_FAILED", "Vector delete failed");
    }

    static Common.Filter buildFilter(VectorSearchQuery query) {
        Common.Filter.Builder filter = Common.Filter.newBuilder()
                .addMust(matchKeyword("tenant_id", query.tenantId()))
                .addMust(matchKeyword("status", "published"));
        if (!query.allowedSpaceIds().isEmpty()) {
            filter.addMust(matchKeywords("space_id", sorted(query.allowedSpaceIds())));
        }
        if (!query.allowedProjectIds().isEmpty()) {
            filter.addMust(matchKeywords("project_id", sorted(query.allowedProjectIds())));
        }
        return filter.build();
    }

    private void initializeCollection() {
        boolean exists = await(client.collectionExistsAsync(collectionName),
                "VECTOR_INDEX_INIT_FAILED", "Vector collection lookup failed");
        if (!exists) {
            Collections.VectorParams params = Collections.VectorParams.newBuilder()
                    .setSize(vectorDimension)
                    .setDistance(Collections.Distance.Cosine)
                    .build();
            try {
                await(client.createCollectionAsync(collectionName, params),
                        "VECTOR_INDEX_INIT_FAILED", "Vector collection creation failed");
            } catch (VectorIndexException exception) {
                if (!hasGrpcStatus(exception, Status.Code.ALREADY_EXISTS)) {
                    throw exception;
                }
            }
        }
        Collections.CollectionInfo info = await(client.getCollectionInfoAsync(collectionName),
                "VECTOR_INDEX_INIT_FAILED", "Vector collection validation failed");
        Collections.VectorParams actual = info.getConfig().getParams().getVectorsConfig().getParams();
        if (actual.getSize() != vectorDimension || actual.getDistance() != Collections.Distance.Cosine) {
            throw new VectorIndexException(
                    "VECTOR_INDEX_SCHEMA_MISMATCH", "Vector collection schema does not match configured dimension and distance");
        }
        for (String field : INDEXED_PAYLOAD_FIELDS) {
            if (!info.getPayloadSchemaMap().containsKey(field)) {
                createPayloadIndex(field);
            }
        }
    }

    private void createPayloadIndex(String field) {
        try {
            await(client.createPayloadIndexAsync(collectionName, field, Collections.PayloadSchemaType.Keyword,
                            null, true, null, null),
                    "VECTOR_INDEX_INIT_FAILED", "Vector payload index creation failed");
        } catch (VectorIndexException exception) {
            if (!hasGrpcStatus(exception, Status.Code.ALREADY_EXISTS)) {
                throw exception;
            }
        }
    }

    private Points.PointStruct toPoint(IndexedChunk chunk) {
        requireDimension(chunk.vector());
        Map<String, JsonWithInt.Value> payload = new LinkedHashMap<>();
        payload.put("chunk_id", value(chunk.chunkId()));
        payload.put("document_id", value(chunk.documentId()));
        payload.put("tenant_id", value(chunk.tenantId()));
        payload.put("space_id", value(chunk.spaceId()));
        payload.put("project_id", value(projectPayloadValue(chunk.projectId())));
        payload.put("status", value(chunk.status()));
        payload.put("content", value(chunk.content()));
        return Points.PointStruct.newBuilder()
                .setId(io.qdrant.client.PointIdFactory.id(pointUuid(chunk.chunkId())))
                .putAllPayload(payload)
                .setVectors(io.qdrant.client.VectorsFactory.vectors(chunk.vector()))
                .build();
    }

    static String projectPayloadValue(String projectId) {
        return projectId == null ? IndexedChunk.GLOBAL_PROJECT_ID : projectId;
    }

    private void requireDimension(List<Float> vector) {
        if (vector.size() != vectorDimension) {
            throw new IllegalArgumentException(
                    "vector dimension must be " + vectorDimension + " but was " + vector.size());
        }
    }

    private static VectorHit toHit(Points.ScoredPoint point) {
        Map<String, String> metadata = new LinkedHashMap<>();
        point.getPayloadMap().forEach((key, payloadValue) -> {
            if (payloadValue.getKindCase() == JsonWithInt.Value.KindCase.STRING_VALUE) {
                metadata.put(key, payloadValue.getStringValue());
            }
        });
        return new VectorHit(metadata.get("chunk_id"), metadata.get("document_id"), point.getScore(),
                metadata.get("content"), metadata);
    }

    private static UUID pointUuid(String chunkId) {
        try {
            return UUID.fromString(chunkId);
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(chunkId.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static List<String> sorted(java.util.Set<String> values) {
        return values.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static <T> T await(ListenableFuture<T> future, String code, String message) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new VectorIndexException(code, message, exception);
        } catch (ExecutionException exception) {
            throw new VectorIndexException(code, message, exception.getCause());
        } catch (RuntimeException exception) {
            throw new VectorIndexException(code, message, exception);
        }
    }

    private static boolean hasGrpcStatus(Throwable throwable, Status.Code code) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof StatusRuntimeException statusException
                    && statusException.getStatus().getCode() == code) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
