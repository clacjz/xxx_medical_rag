package com.clacjz.medical.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "rag")
public class RAGProperties {

    private EmbeddingConfig embedding = new EmbeddingConfig();
    private LLMConfig llm = new LLMConfig();
    private QdrantConfig qdrant = new QdrantConfig();
    private MinIOConfig minio = new MinIOConfig();
    private DocumentConfig document = new DocumentConfig();
    private EtcdConfig etcd = new EtcdConfig();
    private ElasticsearchConfig elasticsearch = new ElasticsearchConfig();
    private NerConfig ner = new NerConfig();
    private QueryRewriteConfig queryRewrite = new QueryRewriteConfig();
    private HybridSearchConfig hybridSearch = new HybridSearchConfig();

    @Data
    public static class HybridSearchConfig {
        /**
         * ES-only 结果的分数补偿系数，用于 RRF 融合时降低 ES-only 结果的权重
         * 默认 0.5 意味着 ES-only 文档的 RRF 分数乘以 0.5
         */
        private double esOnlyScoreFactor = 0.5;
    }

    @Data
    public static class EmbeddingConfig {
        @NotBlank(message = "Embedding服务地址不能为空")
        private String host = "localhost";

        @Positive(message = "Embedding服务端口必须为正数")
        private int port = 28347;

        private String url;
        private String model = "BGE-M3";
        private int dimension = 1024;
        private int maxSequenceLength = 8192;
        private int timeout = 10000;
        /**
         * BGE-M3 推荐 chunk 配置（基于 8192 max sequence length）：
         * - chunk size: 4000 字符（约 2000 tokens，保留安全边界）
         * - overlap: 400 字符（约 200 tokens）
         * 实际使用建议不超过 maxSequenceLength * 0.5 以确保检索质量
         */
        private int defaultChunkSize = 4000;
        private int defaultChunkOverlap = 400;

        public String getUrl() {
            if (this.url != null && !this.url.isEmpty()) {
                return this.url;
            }
            return String.format("http://%s:%d", this.host, this.port);
        }
    }

    @Data
    public static class LLMConfig {
        @NotBlank(message = "LLM服务地址不能为空")
        private String host = "localhost";

        @Positive(message = "LLM服务端口必须为正数")
        private int port = 28347;

        private String baseUrl;
        private String model = "Qwen3.6-27B";
        private double temperature = 0.7;
        private int maxTokens = 4096;  // 128K上下文，最大输出4K
        private double topP = 0.8;        // 小数格式 (0-1)
        private int topK = 20;
        private int minP = 0;
        private double presencePenalty = 1.5;
        private double repetitionPenalty = 1.0;
        private int timeout = 60000;
        /** 思考模式最大token限制，防止思考过程消耗过多token */
        private int thinkingMaxTokens = 4096;

        public String getBaseUrl() {
            if (this.baseUrl != null && !this.baseUrl.isEmpty()) {
                return this.baseUrl;
            }
            return String.format("http://%s:%d", this.host, this.port);
        }
    }

    @Data
    public static class QdrantConfig {
        @NotBlank(message = "Qdrant服务地址不能为空")
        private String host = "localhost";

        @Positive(message = "Qdrant服务端口必须为正数")
        private int port = 6333;

        @Positive(message = "Qdrant gRPC端口必须为正数")
        private int grpcPort = 6334;

        private String apiKey;

        @Positive(message = "超时时间必须为正数")
        private int timeout = 5;

        @Positive(message = "最大连接数必须为正数")
        private int maxConnections = 32;

        @Positive(message = "空闲超时时间必须为正数")
        private int idleTimeout = 300;

        private RetryConfig retry = new RetryConfig();

        private CollectionConfig collection = new CollectionConfig();

        @Data
        public static class RetryConfig {
            @Positive(message = "最大重试次数必须为正数")
            private int maxAttempts = 3;

            @Positive(message = "初始延迟时间必须为正数")
            private long initialDelay = 1000;

            @Positive(message = "最大延迟时间必须为正数")
            private long maxDelay = 10000;

            private double multiplier = 2.0;
        }

        @Data
        public static class CollectionConfig {
            private String defaultName = "medical_documents";
            private int vectorSize = 1024;
            private String distance = "Cosine";
        }
    }

    @Data
    public static class MinIOConfig {
        @NotBlank(message = "MinIO服务地址不能为空")
        private String host = "localhost";

        @Positive(message = "MinIO服务端口必须为正数")
        private int port = 9000;

        private String endpoint;
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private BucketConfig bucket = new BucketConfig();

        public String getEndpoint() {
            if (this.endpoint != null && !this.endpoint.isEmpty()) {
                return this.endpoint;
            }
            return String.format("http://%s:%d", this.host, this.port);
        }

        @Data
        public static class BucketConfig {
            private String documents = "rag-documents";
            private String temp = "rag-temp";
        }
    }

    @Data
    public static class DocumentConfig {
        private ChunkConfig chunk = new ChunkConfig();
        private String[] supportedTypes = {"pdf", "docx", "md", "txt"};

        @Data
        public static class ChunkConfig {
            private int size = 500;
            private int overlap = 50;
        }
    }

    @Data
    public static class EtcdConfig {
        private String host = "localhost";
        private int port = 2379;
        private String uris = "http://localhost:2379";
        private int timeout = 5000;
        private String username;
        private String password;

        public String getEndpoint() {
            return String.format("http://%s:%d", this.host, this.port);
        }
    }

    @Data
    public static class ElasticsearchConfig {
        private String host = "localhost";
        private int port = 9200;
        private String uris = "http://localhost:9200";
        private int connectionTimeout = 5000;
        private int socketTimeout = 30000;
        private String username;
        private String password;

        public String getEndpoint() {
            return String.format("http://%s:%d", this.host, this.port);
        }
    }

    @Data
    public static class NerConfig {
        /**
         * 是否启用NER实体提取（文档入库时和查询时）
         */
        private boolean enabled = false;

        /**
         * NER提取使用的模型
         */
        private String model = "Qwen3.6-27B";

        /**
         * NER提取温度（低温度确保稳定性）
         */
        private double temperature = 0.1;

        /**
         * 查询端NER缓存过期时间（秒），0表示不缓存
         */
        private int queryCacheTtl = 300;
    }

    @Data
    public static class QueryRewriteConfig {
        /**
         * 是否启用Query改写（全局开关）
         */
        private boolean enabled = false;

        /**
         * 改写使用的模型
         */
        private String model = "Qwen3.6-27B";

        /**
         * 改写温度
         */
        private double temperature = 0.3;

        /**
         * 是否启用混合分类（语义型/精确匹配型/混合型），仅在 enabled=true 时生效
         * 关闭时所有检索类问题视为 HYBRID_QUERY（向后兼容）
         */
        private boolean hybridClassificationEnabled = true;

        /**
         * 是否启用多查询扩展（多角度 query 变体检索）
         */
        private boolean multiQueryEnabled = false;

        /**
         * 多查询扩展个数（2-3），仅在 multiQueryEnabled=true 时生效
         */
        private int multiQueryCount = 3;

        /**
         * 多查询结果合并策略：MAX_SCORE / AVG_SCORE / FIRST_RANK
         */
        private String multiQueryMergeStrategy = "MAX_SCORE";

        /**
         * 是否启用子查询分解（复合问题拆解）
         */
        private boolean subQueryDecompositionEnabled = false;

        /**
         * 多查询扩展 + 子查询分解同时启用时的总 query 数上限
         */
        private int maxTotalSearchQueries = 6;
    }

    public NerConfig getNer() {
        return ner;
    }

    public QueryRewriteConfig getQueryRewrite() {
        return queryRewrite;
    }
}
