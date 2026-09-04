package com.smart.agent.knowledge;

import com.smart.agent.ingestion.ParsedTextBlock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@ConditionalOnBean({KnowledgeRepository.class, EmbeddingGateway.class, VectorIndex.class})
public class KnowledgeIngestionService {
    static final int MAX_TEXT_BYTES = 1024 * 1024;
    static final int MAX_CHUNK_CODE_POINTS = 1_200;
    static final int CHUNK_OVERLAP_CODE_POINTS = 150;
    private static final int MIN_PREFERRED_BOUNDARY_CODE_POINTS = 600;
    private static final String PARSER_VERSION = "plain-text-v1";

    private final KnowledgeRepository repository;
    private final EmbeddingGateway embeddingGateway;
    private final VectorIndex vectorIndex;

    public KnowledgeIngestionService(
            KnowledgeRepository repository, EmbeddingGateway embeddingGateway, VectorIndex vectorIndex) {
        this.repository = repository;
        this.embeddingGateway = embeddingGateway;
        this.vectorIndex = vectorIndex;
    }

    @Transactional
    public String ingestText(IngestTextCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        String normalizedText = normalizeAndValidate(command.text());
        requireTransaction();

        String sourceChecksum = sha256(normalizedText);
        String documentId = deterministicId(String.join("|", command.tenantId(), command.spaceId(),
                nullToEmpty(command.projectId()), command.title(), sourceChecksum));
        String versionId = deterministicId(documentId + "|" + sourceChecksum);
        List<ChunkDraft> drafts = chunk(normalizedText);
        List<KnowledgeChunk> chunks = new ArrayList<>(drafts.size());
        List<IndexedChunk> indexedChunks = new ArrayList<>(drafts.size());
        for (int ordinal = 0; ordinal < drafts.size(); ordinal++) {
            ChunkDraft draft = drafts.get(ordinal);
            String chunkChecksum = sha256(draft.content());
            String chunkId = deterministicId(versionId + "|" + ordinal + "|" + chunkChecksum);
            KnowledgeChunk chunk = new KnowledgeChunk(
                    chunkId, documentId, versionId, command.tenantId(), ordinal, draft.content(), chunkChecksum,
                    chunkId, null, draft.sectionTitle());
            chunks.add(chunk);
            indexedChunks.add(new IndexedChunk(
                    chunk.vectorPointId(), documentId, command.tenantId(), command.spaceId(),
                    command.projectId() == null ? IndexedChunk.GLOBAL_PROJECT_ID : command.projectId(),
                    command.status(), chunk.content(), embeddingGateway.embed(chunk.content())));
        }
        KnowledgeDocument document = new KnowledgeDocument(
                documentId, versionId, command.tenantId(), command.spaceId(), command.organizationId(),
                command.projectId(), command.title(), command.status(), normalizedText, sourceChecksum,
                PARSER_VERSION, embeddingGateway.modelKey(), command.actorId(), chunks);
        repository.save(document);
        registerAfterCommit(documentId, VectorNamespace.KNOWLEDGE, indexedChunks, true);
        return documentId;
    }

    @Transactional
    public String ingestDocument(IngestDocumentCommand command, List<ParsedTextBlock> parsedBlocks,
            VectorNamespace namespace) {
        if (command == null || parsedBlocks == null || namespace == null) {
            throw new IllegalArgumentException("command, parsedBlocks and namespace are required");
        }
        List<ParsedTextBlock> nonBlankBlocks = parsedBlocks.stream()
                .filter(Objects::nonNull)
                .filter(block -> !block.text().isBlank())
                .toList();
        if (nonBlankBlocks.isEmpty()) {
            throw new IllegalArgumentException("parsedBlocks must contain text");
        }
        String sourceText = normalizeAndValidate(nonBlankBlocks.stream()
                .map(ParsedTextBlock::text)
                .collect(java.util.stream.Collectors.joining("\n\n")));
        requireTransaction();

        List<KnowledgeChunk> chunks = new ArrayList<>();
        List<IndexedChunk> indexedChunks = new ArrayList<>();
        int ordinal = 0;
        for (ParsedTextBlock block : nonBlankBlocks) {
            String normalizedBlock = block.text().replace("\r\n", "\n").replace('\r', '\n');
            for (ChunkDraft draft : chunk(normalizedBlock)) {
                String checksum = sha256(draft.content());
                String chunkId = deterministicId(command.documentVersionId() + "|" + ordinal + "|" + checksum);
                String sectionTitle = firstNonBlank(block.sectionTitle(), block.sheetName(), draft.sectionTitle());
                KnowledgeChunk chunk = new KnowledgeChunk(
                        chunkId, command.documentId(), command.documentVersionId(), command.tenantId(), ordinal,
                        draft.content(), checksum, chunkId, block.pageNumber(), sectionTitle);
                chunks.add(chunk);
                indexedChunks.add(new IndexedChunk(
                        chunkId, command.documentId(), command.documentVersionId(), command.tenantId(),
                        command.spaceId(), command.projectId() == null ? IndexedChunk.GLOBAL_PROJECT_ID : command.projectId(),
                        command.status(), command.attachmentId(), command.expiresAt(), block.pageNumber(),
                        block.sheetName(), sectionTitle, chunk.content(), embeddingGateway.embed(chunk.content())));
                ordinal++;
            }
        }
        KnowledgeDocument document = new KnowledgeDocument(
                command.documentId(), command.documentVersionId(), command.attachmentId(), command.tenantId(),
                command.spaceId(), command.organizationId(), command.projectId(), command.title(), command.status(),
                sourceText, sha256(sourceText), command.parserVersion(), embeddingGateway.modelKey(),
                command.actorId(), chunks);
        boolean authoritativeKnowledge = namespace == VectorNamespace.KNOWLEDGE;
        if (authoritativeKnowledge) {
            repository.saveManagedDocument(document);
        }
        registerAfterCommit(command.documentId(), namespace, indexedChunks, authoritativeKnowledge);
        return command.documentId();
    }

    private void registerAfterCommit(String documentId, VectorNamespace namespace,
            List<IndexedChunk> indexedChunks, boolean updateRepositoryStatus) {
        List<IndexedChunk> immutableChunks = List.copyOf(indexedChunks);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    vectorIndex.upsert(namespace, immutableChunks);
                    if (updateRepositoryStatus) {
                        repository.markIndexingSucceeded(documentId);
                    }
                } catch (RuntimeException exception) {
                    String code = exception instanceof VectorIndexException vectorException
                            ? vectorException.code() : "VECTOR_INDEX_UPSERT_FAILED";
                    if (updateRepositoryStatus) {
                        try {
                            repository.markIndexingFailed(documentId, code);
                        } catch (RuntimeException statusException) {
                            exception.addSuppressed(statusException);
                        }
                    }
                    throw exception;
                }
            }
        });
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("An active synchronized transaction is required for knowledge ingestion");
        }
    }

    private static String normalizeAndValidate(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (normalized.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("text must not exceed 1 MB in UTF-8");
        }
        return normalized;
    }

    static List<ChunkDraft> chunk(String text) {
        int[] codePoints = text.codePoints().toArray();
        List<ChunkDraft> result = new ArrayList<>();
        int start = 0;
        while (start < codePoints.length) {
            int end = Math.min(start + MAX_CHUNK_CODE_POINTS, codePoints.length);
            if (end < codePoints.length) {
                end = preferredBoundary(codePoints, start, end);
            }
            String content = new String(codePoints, start, end - start);
            result.add(new ChunkDraft(content, sectionTitleAt(codePoints, start)));
            if (end == codePoints.length) {
                break;
            }
            start = end - CHUNK_OVERLAP_CODE_POINTS;
        }
        return List.copyOf(result);
    }

    private static int preferredBoundary(int[] codePoints, int start, int maximumEnd) {
        int minimum = Math.min(maximumEnd, start + MIN_PREFERRED_BOUNDARY_CODE_POINTS);
        for (int index = maximumEnd; index > minimum; index--) {
            if (isParagraphOrHeadingBoundary(codePoints, index)) {
                return index;
            }
        }
        return maximumEnd;
    }

    private static boolean isParagraphOrHeadingBoundary(int[] codePoints, int index) {
        if (codePoints[index - 1] != '\n') {
            return false;
        }
        return index < codePoints.length && (codePoints[index] == '\n' || codePoints[index] == '#');
    }

    private static String sectionTitleAt(int[] codePoints, int start) {
        String section = null;
        int lineStart = 0;
        if (start == 0) {
            int firstLineEnd = 0;
            while (firstLineEnd < codePoints.length && codePoints[firstLineEnd] != '\n') {
                firstLineEnd++;
            }
            section = heading(codePoints, 0, firstLineEnd);
        }
        for (int index = 0; index <= start && index < codePoints.length; index++) {
            if (index == start || codePoints[index] == '\n') {
                int lineEnd = index == start ? index : index;
                String heading = heading(codePoints, lineStart, lineEnd);
                if (heading != null) {
                    section = heading;
                }
                lineStart = index + 1;
            }
        }
        return section;
    }

    private static String heading(int[] codePoints, int lineStart, int lineEnd) {
        if (lineStart >= lineEnd || codePoints[lineStart] != '#') {
            return null;
        }
        String value = new String(codePoints, lineStart, lineEnd - lineStart)
                .replaceFirst("^#+\\s*", "")
                .trim();
        return value.isEmpty() ? null : value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String deterministicId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    record ChunkDraft(String content, String sectionTitle) {
    }
}
