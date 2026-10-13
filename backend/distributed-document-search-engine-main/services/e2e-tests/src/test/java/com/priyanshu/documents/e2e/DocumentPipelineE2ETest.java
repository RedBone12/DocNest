package com.priyanshu.documents.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.apache.http.HttpHost;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.priyanshu.documents.common.events.DocumentDeletedEvent;
import com.priyanshu.documents.common.events.DocumentUploadedEvent;
import com.priyanshu.documents.document_service.entity.Document;
import com.priyanshu.documents.document_service.repository.DocumentRepository;
import com.priyanshu.documents.document_service.repository.UploadRequestRepository;
import com.priyanshu.documents.document_service.service.DocumentEventProducer;
import com.priyanshu.documents.document_service.service.DocumentService;
import com.priyanshu.documents.search_service.dto.SearchResponse;
import com.priyanshu.documents.search_service.service.DocumentSearchService;
import com.priyanshu.documents.search_service.service.SearchCacheService;
import com.priyanshu.index.indexing_service.dto.AiAnalysis;
import com.priyanshu.index.indexing_service.messaging.DocumentDeleteConsumer;
import com.priyanshu.index.indexing_service.messaging.DocumentIndexConsumer;
import com.priyanshu.index.indexing_service.messaging.KafkaFailureProducer;
import com.priyanshu.index.indexing_service.service.AiDocumentAnalyzer;
import com.priyanshu.index.indexing_service.service.Constants;
import com.priyanshu.index.indexing_service.service.DocumentStatusClient;
import com.priyanshu.index.indexing_service.service.IndexInitializer;
import com.priyanshu.index.indexing_service.service.IndexingService;
import com.priyanshu.index.indexing_service.service.SearchCacheInvalidator;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;

@Testcontainers(disabledWithoutDocker = true)
class DocumentPipelineE2ETest {

    private static final String BUCKET = "documents";
    private static final String OWNER = "e2e-user";
    private static final String TEST_JWT_SECRET = "e2e-only-jwt-signing-secret-32-bytes-minimum";

    @Container
    private static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Container
    private static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.11.1"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node")
            .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    @Container
    private static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse(System.getProperty("docnest.minio.image", "quay.io/minio/minio:latest")))
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server", "/data")
            .withExposedPorts(9000);

    private static final Map<UUID, Document> DOCUMENTS = new ConcurrentHashMap<>();

    private static DocumentRepository documentRepository;
    private static UploadRequestRepository uploadRequestRepository;
    private static RecordingStatusClient statusClient;
    private static AiDocumentAnalyzer aiDocumentAnalyzer;
    private static final AtomicInteger aiAnalysisCalls = new AtomicInteger();

    private static DocumentService documentService;
    private static DocumentSearchService searchService;
    private static SearchCacheInvalidator cacheInvalidator;
    private static KafkaTemplate<String, DocumentUploadedEvent> uploadedTemplate;
    private static KafkaTemplate<String, DocumentDeletedEvent> deletedTemplate;
    private static ElasticsearchClient elasticsearchClient;
    private static RestClient restClient;
    private static LettuceConnectionFactory redisConnectionFactory;
    private static StringRedisTemplate redis;

    private static ConcurrentMessageListenerContainer<String, DocumentUploadedEvent> uploadContainer;
    private static ConcurrentMessageListenerContainer<String, DocumentUploadedEvent> retryContainer;
    private static ConcurrentMessageListenerContainer<String, DocumentDeletedEvent> deleteContainer;

    @BeforeAll
    static void startPipeline() throws Exception {
        createKafkaTopics();

        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();

        restClient = RestClient.builder(HttpHost.create(ELASTICSEARCH.getHttpHostAddress())).build();
        elasticsearchClient = new ElasticsearchClient(
                new RestClientTransport(restClient, new JacksonJsonpMapper(mapper)));
        new IndexInitializer(elasticsearchClient).createIndex();

        redisConnectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379)));
        redisConnectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(redisConnectionFactory);
        redis.afterPropertiesSet();

        MinioClient minio = MinioClient.builder()
                .endpoint("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000))
                .credentials("minioadmin", "minioadmin")
                .build();
        if (!minio.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build())) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
        }

        uploadedTemplate = uploadedTemplate();
        deletedTemplate = deletedTemplate();
        DocumentEventProducer eventProducer = new DocumentEventProducer(uploadedTemplate, deletedTemplate);

        IndexingService indexingService = new IndexingService(minio, elasticsearchClient, TEST_JWT_SECRET);
        ReflectionTestUtils.setField(indexingService, "bucket", BUCKET);
        documentRepository = inMemoryDocumentRepository();
        uploadRequestRepository = emptyUploadRequestRepository();
        statusClient = new RecordingStatusClient(indexingService);
        aiDocumentAnalyzer = new AiDocumentAnalyzer() {
            @Override
            public AiAnalysis analyze(String title, String content) {
                aiAnalysisCalls.incrementAndGet();
                return AiAnalysis.completed("E2E AI summary", List.of("e2e-ai"));
            }
        };

        documentService = new DocumentService(
                documentRepository, minio, eventProducer, uploadRequestRepository);
        ReflectionTestUtils.setField(documentService, "bucket", BUCKET);

        cacheInvalidator = new SearchCacheInvalidator(redis);
        KafkaFailureProducer failureProducer = new KafkaFailureProducer(uploadedTemplate);
        DocumentIndexConsumer indexConsumer = new DocumentIndexConsumer(
                statusClient,
                indexingService,
                failureProducer,
                cacheInvalidator,
                aiDocumentAnalyzer);
        DocumentDeleteConsumer deleteConsumer = new DocumentDeleteConsumer(
                elasticsearchClient, cacheInvalidator);

        SearchCacheService cacheService = new SearchCacheService(redis, mapper);
        searchService = new DocumentSearchService(elasticsearchClient, cacheService);

        uploadContainer = uploadedListener("document_uploaded", "e2e-index-upload", indexConsumer);
        retryContainer = uploadedListener("document_uploaded_retry", "e2e-index-retry", indexConsumer);
        deleteContainer = deletedListener(deleteConsumer);
        startAndAwait(uploadContainer);
        startAndAwait(retryContainer);
        startAndAwait(deleteContainer);
    }

    @BeforeEach
    void resetTestState() {
        DOCUMENTS.clear();
        statusClient.clear();
        aiAnalysisCalls.set(0);
    }

    @Test
    void uploadTravelsThroughKafkaAndElasticsearchAndBecomesSearchable() throws Exception {
        String marker = "pipeline-" + UUID.randomUUID();

        UUID documentId = upload(marker);

        SearchResponse response = awaitSearch(marker, 1);
        assertThat(response.results()).singleElement().satisfies(result -> {
            assertThat(result.documentId()).isEqualTo(documentId.toString());
            assertThat(result.title()).contains(marker);
            assertThat(result.contentHighlights()).isNotEmpty();
            assertThat(result.aiStatus()).isEqualTo("DISABLED");
            assertThat(result.aiMessage()).isEqualTo("AI analysis was not requested");
        });
        awaitCondition(Duration.ofSeconds(20), () ->
                statusClient.hasStatus(documentId.toString(), "READY"));
        assertThat(aiAnalysisCalls.get()).isZero();
    }

    @Test
    void uploadWithConsentRunsAiAnalysisAndIndexesResult() throws Exception {
        String marker = "consented-ai-" + UUID.randomUUID();

        UUID documentId = upload(marker, true);

        SearchResponse response = awaitSearch(marker, 1);
        assertThat(response.results()).singleElement().satisfies(result -> {
            assertThat(result.documentId()).isEqualTo(documentId.toString());
            assertThat(result.aiStatus()).isEqualTo("COMPLETED");
            assertThat(result.aiSummary()).isEqualTo("E2E AI summary");
            assertThat(result.aiTags()).contains("e2e-ai");
        });
        assertThat(aiAnalysisCalls.get()).isEqualTo(1);
    }

    @Test
    void manualAiRequestAfterUploadWithoutConsentRunsAnalysis() throws Exception {
        String marker = "manual-ai-" + UUID.randomUUID();
        UUID documentId = upload(marker);
        assertThat(awaitSearch(marker, 1).results()).singleElement()
                .satisfies(result -> assertThat(result.aiStatus()).isEqualTo("DISABLED"));
        assertThat(aiAnalysisCalls.get()).isZero();

        documentService.requestAiAnalysis(documentId, OWNER);

        awaitCondition(Duration.ofSeconds(30), () -> {
            try {
                return search(marker).results().stream()
                        .anyMatch(result -> "COMPLETED".equals(result.aiStatus())
                                && "E2E AI summary".equals(result.aiSummary()));
            } catch (Exception ignored) {
                return false;
            }
        });
        assertThat(aiAnalysisCalls.get()).isEqualTo(1);
    }

    @Test
    void cachedSearchSurvivesAnIndexChangeUntilVersionInvalidation() throws Exception {
        String marker = "cache-" + UUID.randomUUID();
        UUID documentId = upload(marker);
        awaitSearch(marker, 1);

        assertThat(redis.keys("docnest:search:v2:*")).isNotEmpty();
        elasticsearchClient.delete(delete -> delete
                .index(Constants.INDEX_NAME)
                .id(documentId.toString())
                .refresh(Refresh.True));

        assertThat(search(marker).total()).isEqualTo(1);

        cacheInvalidator.invalidate();
        assertThat(awaitSearch(marker, 0).results()).isEmpty();
    }

    @Test
    void deleteEventRemovesTheSearchResultAndInvalidatesCachedResults() throws Exception {
        String marker = "delete-" + UUID.randomUUID();
        UUID documentId = upload(marker);
        awaitSearch(marker, 1);

        documentService.deleteDocument(documentId, OWNER);

        SearchResponse response = awaitSearch(marker, 0);
        assertThat(response.results()).isEmpty();
        assertThat(elasticsearchClient.exists(exists -> exists
                .index(Constants.INDEX_NAME)
                .id(documentId.toString())).value()).isFalse();
    }

    @Test
    void failedIndexingIsRetriedThreeTimesAndThenPublishedToDlq() throws Exception {
        String documentId = UUID.randomUUID().toString();
        DocumentUploadedEvent poisonEvent = new DocumentUploadedEvent(
                documentId,
                "missing-object-" + documentId,
                "Broken upload",
                "This object does not exist in MinIO",
                OWNER,
                42L,
                "UPLOADED",
                0,
                false);

        try (Consumer<String, DocumentUploadedEvent> dlqConsumer = dlqConsumer()) {
            dlqConsumer.subscribe(List.of("document_uploaded_dlq"));
            dlqConsumer.poll(Duration.ofMillis(500));

            uploadedTemplate.send("document_uploaded", documentId, poisonEvent).get();

            DocumentUploadedEvent dlqEvent = awaitDlqEvent(dlqConsumer, documentId);
            assertThat(dlqEvent.retryCount()).isEqualTo(3);
            awaitCondition(Duration.ofSeconds(30), () ->
                    statusClient.hasStatus(documentId, "FAILED"));
        }
    }

    private static DocumentRepository inMemoryDocumentRepository() {
        return (DocumentRepository) Proxy.newProxyInstance(
                DocumentRepository.class.getClassLoader(),
                new Class<?>[] { DocumentRepository.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        Document document = (Document) args[0];
                        DOCUMENTS.put(document.getId(), document);
                        yield document;
                    }
                    case "findById" -> Optional.ofNullable(DOCUMENTS.get((UUID) args[0]));
                    case "findByIdAndOwnerId" -> Optional.ofNullable(DOCUMENTS.get((UUID) args[0]))
                            .filter(document -> document.getOwnerId().equals(args[1]));
                    case "delete" -> {
                        DOCUMENTS.remove(((Document) args[0]).getId());
                        yield null;
                    }
                    case "updateStatus" -> 1;
                    case "toString" -> "InMemoryDocumentRepository";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(
                            "Repository method is not used by this E2E test: " + method.getName());
                });
    }

    private static UploadRequestRepository emptyUploadRequestRepository() {
        return (UploadRequestRepository) Proxy.newProxyInstance(
                UploadRequestRepository.class.getClassLoader(),
                new Class<?>[] { UploadRequestRepository.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.empty();
                    case "save" -> args[0];
                    case "toString" -> "EmptyUploadRequestRepository";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(
                            "Repository method is not used by this E2E test: " + method.getName());
                });
    }

    private static final class RecordingStatusClient extends DocumentStatusClient {
        private final List<String> updates = new CopyOnWriteArrayList<>();

        private RecordingStatusClient(IndexingService indexingService) {
            super(indexingService);
        }

        @Override
        public void updateStatus(String documentId, String status) {
            updates.add(documentId + ":" + status);
        }

        @Override
        public void updateAiMetadata(String documentId, AiAnalysis analysis) {
            // Metadata persistence is outside the asynchronous indexing contract under test.
        }

        @Override
        public void updateFailure(String documentId, String code, String message, int failureCount) {
            updateStatus(documentId, "FAILED");
        }

        private boolean hasStatus(String documentId, String status) {
            return updates.contains(documentId + ":" + status);
        }

        private void clear() {
            updates.clear();
        }
    }

    private UUID upload(String marker) {
        return upload(marker, false);
    }

    private UUID upload(String marker, boolean enableAiAnalysis) {
        byte[] content = ("DocNest end to end content " + marker).getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", marker + ".txt", "text/plain", content);

        return documentService.upload(
                "idempotency-" + marker,
                file,
                "Document " + marker,
                "End-to-end verification",
                List.of("e2e"),
                OWNER,
                enableAiAnalysis);
    }

    private static SearchResponse search(String query) throws Exception {
        return searchService.search(query, List.of(), null, null, 0, 10, OWNER);
    }

    private static SearchResponse awaitSearch(String query, long expectedTotal) throws Exception {
        final SearchResponse[] latest = new SearchResponse[1];
        awaitCondition(Duration.ofSeconds(30), () -> {
            try {
                latest[0] = search(query);
                return latest[0].total() == expectedTotal;
            } catch (Exception ignored) {
                return false;
            }
        });
        return latest[0];
    }

    private static void awaitCondition(Duration timeout, BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Condition was not satisfied within " + timeout);
    }

    private static DocumentUploadedEvent awaitDlqEvent(
            Consumer<String, DocumentUploadedEvent> consumer,
            String expectedDocumentId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(40).toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, DocumentUploadedEvent> record
                    : consumer.poll(Duration.ofMillis(500))) {
                if (expectedDocumentId.equals(record.value().documentId())) {
                    return record.value();
                }
            }
        }
        throw new AssertionError("No matching event arrived on document_uploaded_dlq");
    }

    private static void createKafkaTopics() throws Exception {
        Map<String, Object> config = Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(config)) {
            admin.createTopics(List.of(
                    new NewTopic("document_uploaded", 1, (short) 1),
                    new NewTopic("document_uploaded_retry", 1, (short) 1),
                    new NewTopic("document_uploaded_dlq", 1, (short) 1),
                    new NewTopic("document_deleted", 1, (short) 1)))
                    .all()
                    .get();
        }
    }

    private static KafkaTemplate<String, DocumentUploadedEvent> uploadedTemplate() {
        Map<String, Object> config = producerProperties();
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    private static KafkaTemplate<String, DocumentDeletedEvent> deletedTemplate() {
        Map<String, Object> config = producerProperties();
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    private static Map<String, Object> producerProperties() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        return config;
    }

    private static ConcurrentMessageListenerContainer<String, DocumentUploadedEvent> uploadedListener(
            String topic,
            String groupId,
            DocumentIndexConsumer consumer) {
        Map<String, Object> properties = consumerProperties(groupId);
        JsonDeserializer<DocumentUploadedEvent> deserializer =
                new JsonDeserializer<>(DocumentUploadedEvent.class, false);
        deserializer.addTrustedPackages("com.priyanshu.documents.common.events");
        DefaultKafkaConsumerFactory<String, DocumentUploadedEvent> factory =
                new DefaultKafkaConsumerFactory<>(properties, new StringDeserializer(), deserializer);
        ContainerProperties containerProperties = new ContainerProperties(topic);
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        ConcurrentMessageListenerContainer<String, DocumentUploadedEvent> container =
                new ConcurrentMessageListenerContainer<>(factory, containerProperties);
        container.setupMessageListener((AcknowledgingMessageListener<String, DocumentUploadedEvent>)
                (record, acknowledgment) -> {
                    try {
                        if ("document_uploaded_retry".equals(record.topic())) {
                            consumer.retry(record.value(), acknowledgment);
                        } else {
                            consumer.consume(record.value(), acknowledgment);
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
        return container;
    }

    private static ConcurrentMessageListenerContainer<String, DocumentDeletedEvent> deletedListener(
            DocumentDeleteConsumer consumer) {
        Map<String, Object> properties = consumerProperties("e2e-index-delete");
        JsonDeserializer<DocumentDeletedEvent> deserializer =
                new JsonDeserializer<>(DocumentDeletedEvent.class, false);
        deserializer.addTrustedPackages("com.priyanshu.documents.common.events");
        DefaultKafkaConsumerFactory<String, DocumentDeletedEvent> factory =
                new DefaultKafkaConsumerFactory<>(properties, new StringDeserializer(), deserializer);
        ContainerProperties containerProperties = new ContainerProperties("document_deleted");
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        ConcurrentMessageListenerContainer<String, DocumentDeletedEvent> container =
                new ConcurrentMessageListenerContainer<>(factory, containerProperties);
        container.setupMessageListener((AcknowledgingMessageListener<String, DocumentDeletedEvent>)
                (record, acknowledgment) -> {
                    try {
                        consumer.consume(record.value(), acknowledgment);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
        return container;
    }

    private static Map<String, Object> consumerProperties(String groupId) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return properties;
    }

    private static Consumer<String, DocumentUploadedEvent> dlqConsumer() {
        Map<String, Object> properties = consumerProperties("e2e-dlq-" + UUID.randomUUID());
        JsonDeserializer<DocumentUploadedEvent> deserializer =
                new JsonDeserializer<>(DocumentUploadedEvent.class, false);
        deserializer.addTrustedPackages("com.priyanshu.documents.common.events");
        return new DefaultKafkaConsumerFactory<>(
                properties, new StringDeserializer(), deserializer).createConsumer();
    }

    private static void startAndAwait(ConcurrentMessageListenerContainer<?, ?> container) {
        container.start();
        ContainerTestUtils.waitForAssignment(container, 1);
    }

    @AfterAll
    static void stopPipeline() throws Exception {
        if (uploadContainer != null) uploadContainer.stop();
        if (retryContainer != null) retryContainer.stop();
        if (deleteContainer != null) deleteContainer.stop();
        if (uploadedTemplate != null) uploadedTemplate.destroy();
        if (deletedTemplate != null) deletedTemplate.destroy();
        if (redisConnectionFactory != null) redisConnectionFactory.destroy();
        if (restClient != null) restClient.close();
    }
}
