# RAG知识库系统LLD第三部分

## 性能优化与安全设计

---

# 一、性能优化策略

## 1.1 数据库优化

### 索引优化

```sql
-- 组合索引
CREATE INDEX idx_documents_collection_status ON documents(collection_id, status);

-- 部分索引
CREATE INDEX idx_documents_pending ON documents(status) WHERE status = 'pending';

-- 表达式索引
CREATE INDEX idx_users_email_lower ON users(LOWER(email));

-- 向量检索优化
CREATE INDEX idx_chunks_doc ON document_chunks(document_id);

-- 分区表设计
CREATE TABLE audit_logs_2026 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
```

### 查询优化

```java
// 使用Stream API优化
public List<Document> searchDocuments(String keyword) {
    return documentRepository.findAll()
        .stream()
        .filter(doc -> doc.getTitle().contains(keyword))
        .sorted(Comparator.comparing(Document::getCreatedAt).reversed())
        .limit(100)
        .collect(Collectors.toList());
}

// 批量操作优化
@Transactional
public void batchProcessDocuments(List<UUID> ids) {
    // 分批处理，每批100条
    Lists.partition(ids, 100)
        .forEach(batch -> processBatch(batch));
}

// 异步处理
@Async
public Future<ProcessResult> processDocumentAsync(UUID documentId) {
    return CompletableFuture.supplyAsync(() -> processDocument(documentId));
}
```

## 1.2 缓存优化

### 多级缓存

```java
@Service
public class CacheService {
    
    // L1缓存：本地缓存
    private final Cache<String, Object> localCache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build();
    
    // L2缓存：Redis
    @Cacheable(value = "documents", key = "#id")
    public Document getDocument(UUID id) {
        // 先查本地缓存
        // 再查Redis
        // 最后查数据库
    }
    
    // 缓存预热
    @PostConstruct
    public void warmUp() {
        // 预加载热点数据
    }
}
```

### 向量缓存

```java
@Service
public class VectorCacheService {
    
    private final LoadingCache<String, float[]> vectorCache = CacheBuilder.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(24, TimeUnit.HOURS)
        .build(new CacheLoader<>() {
            @Override
            public float[] load(String key) {
                return embeddingService.getEmbedding(key);
            }
        });
    
    public float[] getEmbedding(String text) {
        String hash = md5(text);
        return vectorCache.get(hash);
    }
}
```

## 1.3 连接池优化

```java
@Configuration
public class ConnectionPoolConfig {
    
    // HikariCP配置
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(20000);
        config.setMaxLifetime(1200000);
        return new HikariDataSource(config);
    }
    
    // HTTP连接池
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        factory.setBufferRequestBody(true);
        
        PoolingHttpClientPoolingConnectionManager manager = new PoolingHttpClientPoolingConnectionManager();
        manager.setMaxTotal(100);
        manager.setDefaultMaxPerRoute(20);
        
        return new RestTemplate(factory);
    }
}
```

---

# 二、安全设计

## 2.1 认证授权

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpSecurityConverter::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(
                SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter.class
            );
        
        return http.build();
    }
}

@Component
public class JwtTokenProvider {
    
    public String createToken(UserDetails user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", user.getAuthorities());
        claims.put("userId", user.getUsername());
        
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration * 1000);
        
        return Jwts.builder()
            .setClaims(claims)
            .setSubject(user.getUsername())
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(key)
            .compact();
    }
}
```

## 2.2 数据加密

```java
@Configuration
public class EncryptionConfig {
    
    @Value("${encryption.secret}")
    private String secretKey;
    
    @Bean
    public AESEncryptor aesEncryptor() {
        return new AESEncryptor(secretKey);
    }
}

@Service
public class FileEncryptionService {
    
    private final AESEncryptor encryptor;
    
    public String encryptFileContent(byte[] content) {
        return encryptor.encrypt(Base64.encode(content));
    }
    
    public byte[] decryptFileContent(String encrypted) {
        return Base64.decode(encryptor.decrypt(encrypted));
    }
}
```

## 2.3 限流防护

```java
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${ratelimit.requests:100}")
    private int maxRequests;
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                          HttpServletResponse response, 
                          Object handler) {
        String key = "rate:" + getClientId(request);
        String count = redisTemplate.opsForValue().get(key);
        
        if (count != null && Integer.parseInt(count) > maxRequests) {
            response.setStatus(429);
            return false;
        }
        
        redisTemplate.opsForValue().increment(key);
        if (count == null) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        
        return true;
    }
}
```

---

# 三、监控告警

## 3.1 指标采集

```java
@Service
public class MetricsService {
    
    private final MeterRegistry meterRegistry;
    
    public void recordDocumentUpload(long bytes, long ms) {
        meterRegistry.counter("documents.upload.total").increment();
        meterRegistry.timer("documents.upload.duration").record(ms, TimeUnit.MILLISECONDS);
        meterRegistry.summary("documents.upload.size").record(bytes);
    }
    
    public void recordEmbeddingLatency(long ms) {
        meterRegistry.timer("embedding.latency").record(ms, TimeUnit.MILLISECONDS);
    }
    
    public void recordLLMTokenUsage(int promptTokens, int completionTokens) {
        meterRegistry.counter("llm.tokens.prompt").increment(promptTokens);
        meterRegistry.counter("llm.tokens.completion").increment(completionTokens);
    }
    
    @Scheduled(fixedRate = 60000)
    public void reportMetrics() {
        log.info("Metrics: {} requests/min", 
            meterRegistry.counter("api.requests").count() / 60);
    }
}
```

## 3.2 健康检查

```java
@Component
public class HealthIndicator implements HealthIndicator {
    
    private final DataSource dataSource;
    private final RedisTemplate redisTemplate;
    private final QdrantClient qdrantClient;
    
    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        
        try {
            // 数据库连接
            dataSource.getConnection().createStatement().execute("SELECT 1");
            details.put("database", "UP");
        } catch (Exception e) {
            details.put("database", "DOWN");
        }
        
        try {
            // Redis连接
            redisTemplate.getConnectionFactory().getConnection().ping();
            details.put("redis", "UP");
        } catch (Exception e) {
            details.put("redis", "DOWN");
        }
        
        boolean allUp = "UP".equals(details.get("database"))
            && "UP".equals(details.get("redis"));
        
        return allUp ? Health.up().withDetails(details) : Health.down()
            .withDetails(details)
            .withException(new RuntimeException("Service degraded"));
    }
}
```

---

# 四、日志规范

## 4.1 日志级别

```java
public class LoggerLevels {
    // FATAL: 系统不可用
    // ERROR: 系统错误
    // WARN: 警告信息
    // INFO: 重要业务操作
    // DEBUG: 开发调试
    // TRACE: 跟踪详情
}

@Slf4j
@Service
public class BusinessService {
    
    public void processOrder(Order order) {
        log.info("开始处理订单: orderId={}, userId={}", 
            order.getId(), order.getUserId());
        
        try {
            // 业务逻辑
            log.debug("订单详情: {}", order);
            
        } catch (BusinessException e) {
            log.warn("业务异常: code={}, message={}", 
                e.getCode(), e.getMessage());
            throw e;
            
        } catch (Exception e) {
            log.error("系统异常: orderId={}", order.getId(), e);
            throw new SystemException("PROCESS_ORDER_FAILED", e);
        }
    }
}
```

## 4.2 日志格式

```xml
<PatternLayout>
    <Pattern>
        %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
    </Pattern>
</PatternLayout>
```

---

# 五、部署配置

## 5.1 应用配置

```yaml
spring:
  application:
    name: rag-knowledge-base
  
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
  
  cache:
    type: redis
    redis:
      time-to-live: 3600000
  
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB

server:
  port: ${APP_PORT:8080}
  compression:
    enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
```

## 5.2 Docker配置

```dockerfile
FROM maven:3.9-openjdk-17-slim

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=0 /app/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms512m -Xmx2g"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

# 六、监控告警机制

## 6.1 核心监控指标

### 6.1.1 基础设施指标

| 指标名称 | 类型 | 单位 | 描述 | 采集方式 |
|---------|------|------|------|----------|
| cpu_usage | Gauge | % | CPU使用率 | JMX/Sigar |
| memory_used | Gauge | bytes | 已使用内存 | JMX |
| memory_free | Gauge | bytes | 可用内存 | JMX |
| disk_used | Gauge | bytes | 磁盘使用量 | OS Probe |
| disk_io_read | Counter | bytes | 磁盘读取量 | OS Probe |
| disk_io_write | Counter | bytes | 磁盘写入量 | OS Probe |
| network_in | Counter | bytes | 网络入流量 | OS Probe |
| network_out | Counter | bytes | 网络出流量 | OS Probe |

### 6.1.2 应用业务指标

| 指标名称 | 类型 | 单位 | 描述 | 采集方式 |
|---------|------|------|------|----------|
| api_requests_total | Counter | count | API请求总数 | 代码埋点 |
| api_requests_success | Counter | count | 成功请求数 | 代码埋点 |
| api_requests_failed | Counter | count | 失败请求数 | 代码埋点 |
| api_latency_seconds | Histogram | seconds | API响应延迟 | 代码埋点 |
| active_users | Gauge | count | 在线用户数 | Redis |
| concurrent_requests | Gauge | count | 并发请求数 | 代码埋点 |

### 6.1.3 RAG专项指标

| 指标名称 | 类型 | 单位 | 描述 | 采集方式 |
|---------|------|------|------|----------|
| rag_queries_total | Counter | count | RAG查询总数 | 代码埋点 |
| rag_query_latency | Histogram | seconds | RAG查询延迟 | 代码埋点 |
| embedding_latency | Histogram | seconds | Embedding延迟 | 代码埋点 |
| llm_latency | Histogram | seconds | LLM生成延迟 | 代码埋点 |
| retrieval_latency | Histogram | seconds | 向量检索延迟 | 代码埋点 |
| llm_tokens_total | Counter | count | Token消耗总量 | 代码埋点 |
| llm_tokens_prompt | Counter | count | Prompt Token数 | 代码埋点 |
| llm_tokens_completion | Counter | count | Completion Token数 | 代码埋点 |
| retrieval_results_count | Histogram | count | 检索结果数量 | 代码埋点 |
| retrieval_score_avg | Gauge | score | 平均检索得分 | 代码埋点 |

### 6.1.4 文档处理指标

| 指标名称 | 类型 | 单位 | 描述 | 采集方式 |
|---------|------|------|------|----------|
| documents_uploaded_total | Counter | count | 上传文档总数 | 代码埋点 |
| documents_processing | Gauge | count | 处理中文档数 | 代码埋点 |
| documents_completed | Counter | count | 处理完成文档数 | 代码埋点 |
| documents_failed | Counter | count | 处理失败文档数 | 代码埋点 |
| document_processing_time | Histogram | seconds | 文档处理耗时 | 代码埋点 |
| document_file_size | Histogram | bytes | 文档文件大小 | 代码埋点 |
| document_chunks_count | Histogram | count | 分块数量 | 代码埋点 |

## 6.2 告警阈值配置

### 6.2.1 告警级别定义

| 级别 | 名称 | 说明 | 通知方式 | 响应时间 |
|------|------|------|----------|----------|
| P1 | 紧急 | 服务不可用，数据丢失风险 | 电话+短信+钉钉 | 5分钟 |
| P2 | 重要 | 功能受损，影响部分用户 | 短信+钉钉 | 15分钟 |
| P3 | 警告 | 性能下降，可能影响服务 | 钉钉 | 1小时 |
| P4 | 提示 | 容量预警，趋势警告 | 邮件 | 4小时 |

### 6.2.2 告警规则配置

```yaml
alerts:
  # P1 - 紧急告警
  - name: service_down
    level: P1
    condition: |
      api_requests_success_rate < 0.5
      for: 2m
    summary: "API成功率低于50%，服务可能宕机"
    action: auto_restart + phone_call
  
  - name: database_unavailable
    level: P1
    condition: |
      postgresql_active_connections > 100
      for: 1m
    summary: "数据库连接数异常"
    action: phone_call + escalation
  
  - name: vector_store_unavailable
    level: P1
    condition: |
      vector_search_error_rate > 0.1
      for: 30s
    summary: "向量数据库不可用"
    action: phone_call
  
  # P2 - 重要告警
  - name: high_error_rate
    level: P2
    condition: |
      api_error_rate > 0.05
      for: 5m
    summary: "API错误率超过5%"
    action: sms + auto_scale
  
  - name: high_latency
    level: P2
    condition: |
      api_latency_p99 > 5s
      for: 5m
    summary: "API响应延迟P99超过5秒"
    action: sms
  
  - name: llm_service_slow
    level: P2
    condition: |
      llm_latency_p95 > 30s
      for: 5m
    summary: "LLM生成延迟过高"
    action: sms
  
  # P3 - 警告告警
  - name: disk_space_warning
    level: P3
    condition: |
      disk_usage_percent > 80
      for: 10m
    summary: "磁盘使用率超过80%"
    action: dingtalk
  
  - name: memory_pressure
    level: P3
    condition: |
      memory_usage_percent > 85
      for: 10m
    summary: "内存使用率超过85%"
    action: dingtalk
  
  - name: document_queue_backlog
    level: P3
    condition: |
      documents_processing > 100
      for: 15m
    summary: "文档处理队列积压"
    action: dingtalk
  
  # P4 - 提示告警
  - name: token_usage_warning
    level: P4
    condition: |
      llm_tokens_daily > 1000000
      for: 1h
    summary: "Token消耗接近日限额"
    action: email
  
  - name: storage_usage_trend
    level: P4
    condition: |
      storage_growth_rate > 10GB/day
      for: 24h
    summary: "存储增长趋势异常"
    action: email
```

## 6.3 告警通知配置

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertNotificationService {
    
    private final SmsService smsService;
    private final EmailService emailService;
    private final DingTalkService dingTalkService;
    private final AlertRepository alertRepository;
    
    public void sendAlert(Alert alert) {
        // 1. 记录告警日志
        alertRepository.save(alert);
        
        // 2. 根据级别选择通知渠道
        switch (alert.getLevel()) {
            case P1:
                sendP1Alert(alert);
                break;
            case P2:
                sendP2Alert(alert);
                break;
            case P3:
                sendP3Alert(alert);
                break;
            case P4:
                sendP4Alert(alert);
                break;
        }
        
        // 3. 记录告警发送历史
        recordAlertHistory(alert);
    }
    
    private void sendP1Alert(Alert alert) {
        // 电话通知
        smsService.sendPhoneCall(getOncallPhoneNumbers(), 
            formatAlertMessage(alert));
        
        // 短信通知
        smsService.sendSms(getAdminPhoneNumbers(), 
            formatAlertMessage(alert));
        
        // 钉钉通知
        dingTalkService.sendCriticalAlert(formatDingTalkMessage(alert));
    }
    
    private void sendP2Alert(Alert alert) {
        // 短信通知
        smsService.sendSms(getOncallPhoneNumbers(), 
            formatAlertMessage(alert));
        
        // 钉钉通知
        dingTalkService.sendAlert(formatDingTalkMessage(alert));
    }
    
    private void sendP3Alert(Alert alert) {
        dingTalkService.sendWarning(formatDingTalkMessage(alert));
    }
    
    private void sendP4Alert(Alert alert) {
        emailService.sendAlertEmail(getAdminEmails(), alert);
    }
}
```

---

# 七、安全防护策略

## 7.1 输入安全防护

### 7.1.1 SQL注入防护

```java
@Configuration
public class SqlInjectionPreventionConfig {
    
    @Bean
    public FilterRegistrationBean<SqlInjectionFilter> sqlInjectionFilter() {
        FilterRegistrationBean<SqlInjectionFilter> registration = 
            new FilterRegistrationBean<>();
        registration.setFilter(new SqlInjectionFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        return registration;
    }
}

public class SqlInjectionFilter implements Filter {
    
    private static final List<String> SQL_KEYWORDS = Arrays.asList(
        "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "CREATE",
        "ALTER", "EXEC", "UNION", "--", ";", "/*", "*/", "xp_"
    );
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                         FilterChain chain) throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        // 检查所有请求参数
        Enumeration<String> params = httpRequest.getParameterNames();
        while (params.hasMoreElements()) {
            String param = params.nextElement();
            String value = httpRequest.getParameter(param);
            
            if (containsSqlInjection(value)) {
                ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"code\":\"SYS_008\",\"message\":\"请求包含非法字符\"}");
                return;
            }
        }
        
        chain.doFilter(request, response);
    }
    
    private boolean containsSqlInjection(String value) {
        if (value == null) return false;
        String upperValue = value.toUpperCase();
        return SQL_KEYWORDS.stream().anyMatch(upperValue::contains);
    }
}
```

### 7.1.2 XSS防护

```java
@Configuration
public class XssPreventionConfig {
    
    @Bean
    public FilterRegistrationBean<XssFilter> xssFilter() {
        FilterRegistrationBean<XssFilter> registration = 
            new FilterRegistrationBean<>();
        registration.setFilter(new XssFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(2);
        return registration;
    }
}

public class XssFilter implements Filter {
    
    private static final Pattern[] XSS_PATTERNS = {
        Pattern.compile("<script>(.*?)</script>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("src[\r\n]*=[\r\n]*\\\'(.*?)\\\'", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("src[\r\n]*=[\r\n]*\\\"(.*?)\\\"", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("<iframe(.*?)>(.*?)</iframe>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("eval\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("expression\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
    };
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                         FilterChain chain) throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        XssHttpServletRequestWrapper xssRequest = new XssHttpServletRequestWrapper(httpRequest);
        chain.doFilter(xssRequest, response);
    }
}

public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {
    
    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }
    
    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return cleanXss(value);
    }
    
    private String cleanXss(String value) {
        if (value == null) return null;
        
        String cleanValue = value;
        for (Pattern pattern : XSS_PATTERNS) {
            cleanValue = pattern.matcher(cleanValue).replaceAll("");
        }
        
        // HTML实体编码
        cleanValue = cleanValue.replaceAll("<", "&lt;")
                             .replaceAll(">", "&gt;")
                             .replaceAll("\"", "&quot;")
                             .replaceAll("'", "&#x27;");
        
        return cleanValue;
    }
}
```

### 7.1.3 CSRF防护

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/api/auth/**", "/api/public/**")
            )
            .addFilterBefore(corsFilter, CsrfFilter.class)
            .addFilterBefore(requestValidationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

## 7.2 文件上传安全

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadSecurityService {
    
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword",
        "text/plain",
        "text/markdown",
        "text/html",
        "text/csv",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint"
    );
    
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final long MAX_STORAGE_SIZE = 10L * 1024 * 1024 * 1024; // 10GB
    
    private final FileStorageService storageService;
    private final MalwareScanService malwareScanService;
    
    public UploadResult uploadFile(MultipartFile file, UUID userId) {
        // 1. 文件大小验证
        validateFileSize(file);
        
        // 2. 文件类型验证
        validateContentType(file);
        
        // 3. 文件名安全检查
        validateFileName(file.getOriginalFilename());
        
        // 4. 文件内容扫描
        scanForMalware(file);
        
        // 5. 用户存储配额检查
        checkStorageQuota(userId);
        
        // 6. 计算文件哈希
        String fileHash = calculateFileHash(file);
        
        // 7. 检查重复文件
        if (storageService.fileExists(fileHash)) {
            return UploadResult.duplicate(fileHash);
        }
        
        // 8. 安全上传
        return storageService.storeSecurely(file, userId, fileHash);
    }
    
    private void validateFileSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException("DOC_003", 
                "文件大小不能超过" + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
        }
        
        if (file.getSize() == 0) {
            throw new ValidationException("DOC_011", "上传文件不能为空");
        }
    }
    
    private void validateContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ValidationException("DOC_002", 
                "不支持的文件类型，仅支持: " + String.join(", ", ALLOWED_CONTENT_TYPES));
        }
    }
    
    private void validateFileName(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new ValidationException("DOC_012", "文件名不能为空");
        }
        
        // 禁止路径遍历
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new ValidationException("SYS_008", "文件名包含非法字符");
        }
        
        // 限制文件名长度
        if (filename.length() > 255) {
            throw new ValidationException("DOC_012", "文件名过长");
        }
    }
    
    private void scanForMalware(MultipartFile file) {
        try {
            boolean isSafe = malwareScanService.scan(file.getBytes());
            if (!isSafe) {
                log.warn("文件安全扫描未通过: {}", file.getOriginalFilename());
                throw new ValidationException("DOC_013", "文件安全检查未通过");
            }
        } catch (Exception e) {
            log.error("安全扫描失败", e);
            throw new ValidationException("DOC_014", "文件安全扫描失败");
        }
    }
    
    private void checkStorageQuota(UUID userId) {
        long usedStorage = storageService.getUserStorageUsed(userId);
        if (usedStorage >= MAX_STORAGE_SIZE) {
            throw new ValidationException("DOC_015", "存储配额已用完");
        }
    }
    
    private String calculateFileHash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("计算文件哈希失败", e);
        }
    }
}
```

## 7.3 数据加密策略

```java
@Configuration
public class DataEncryptionConfig {
    
    @Value("${encryption.algorithm:AES/GCM/NoPadding}")
    private String algorithm;
    
    @Value("${encryption.key-size:256}")
    private int keySize;
    
    @Bean
    public EncryptionService encryptionService() {
        return new AESEncryptionService(algorithm, keySize);
    }
}

@Service
@RequiredArgsConstructor
@Slf4j
public class AESEncryptionService implements EncryptionService {
    
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    
    private final SecretKey secretKey;
    
    @Override
    public String encrypt(String plainText) {
        try {
            // 生成随机IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterParameterSpec gcmSpec = new GCMParameterParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
            
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // 拼接IV和密文
            byte[] encrypted = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, encrypted, 0, iv.length);
            System.arraycopy(cipherText, 0, encrypted, iv.length, cipherText.length);
            
            return Base64.getEncoder().encodeToString(encrypted);
            
        } catch (Exception e) {
            log.error("加密失败", e);
            throw new RuntimeException("数据加密失败");
        }
    }
    
    @Override
    public String decrypt(String encryptedText) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            
            // 分离IV和密文
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[decoded.length - GCM_IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, iv.length);
            System.arraycopy(decoded, iv.length, cipherText, 0, cipherText.length);
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterParameterSpec gcmSpec = new GCMParameterParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
            
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("解密失败", e);
            throw new RuntimeException("数据解密失败");
        }
    }
}
```

---

# 八、容灾备份方案

## 8.1 数据库备份策略

### 8.1.1 PostgreSQL备份

```bash
#!/bin/bash
# backup_postgres.sh

BACKUP_DIR="/backup/postgres"
DATE=$(date +%Y%m%d_%H%M%S)
DB_HOST="${DB_HOST}"
DB_NAME="${DB_NAME}"
DB_USER="${DB_USER}"

# 全量备份
pg_dump -h $DB_HOST -U $DB_USER -d $DB_NAME -Fc -f "$BACKUP_DIR/full_${DATE}.dump"

# 压缩备份
gzip "$BACKUP_DIR/full_${DATE}.dump"

# 保留最近30天的备份
find $BACKUP_DIR -name "full_*.dump.gz" -mtime +30 -delete

# 上传到对象存储
aws s3 cp "$BACKUP_DIR/full_${DATE}.dump.gz" s3://backup-bucket/postgres/
```

### 8.1.2 增量备份（WAL归档）

```bash
#!/bin/bash
# wal_archive.sh

WAL_DIR="/backup/wal"
S3_BUCKET="s3://backup-bucket/wal"

# 归档WAL段
ARCHIVE_NAME=$(date +%Y%m%d_%H%M%S)
cp $PGDATA/pg_wal/* $WAL_DIR/

# 压缩并上传
tar -czf "$WAL_DIR/wal_${ARCHIVE_NAME}.tar.gz" $WAL_DIR/*.wal
aws s3 cp "$WAL_DIR/wal_${ARCHIVE_NAME}.tar.gz" $S3_BUCKET/

# 清理本地WAL
rm -f $WAL_DIR/*.wal
```

## 8.2 向量数据库备份

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorStoreBackupService {
    
    private final QdrantClient qdrantClient;
    private final S3Service s3Service;
    
    @Value("${rag.qdrant.collection}")
    private String collectionName;
    
    private static final String BACKUP_BUCKET = "rag-vector-backup";
    
    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点
    public void backupVectorStore() {
        try {
            log.info("开始备份向量数据库: {}", collectionName);
            
            // 1. 获取所有点
            List<PointStruct> allPoints = exportAllPoints();
            
            // 2. 导出到临时文件
            String backupFile = exportToJson(allPoints);
            
            // 3. 上传到对象存储
            String backupKey = generateBackupKey();
            s3Service.upload(backupFile, BACKUP_BUCKET, backupKey);
            
            // 4. 记录备份元数据
            saveBackupMetadata(backupKey, allPoints.size());
            
            // 5. 清理过期备份
            cleanupOldBackups();
            
            log.info("向量数据库备份完成，备份文件: {}, 点数: {}", 
                backupKey, allPoints.size());
            
        } catch (Exception e) {
            log.error("向量数据库备份失败", e);
            alertService.sendCriticalAlert("VectorBackup", "备份失败: " + e.getMessage());
        }
    }
    
    private List<PointStruct> exportAllPoints() {
        List<PointStruct> allPoints = new ArrayList<>();
        int offset = 0;
        int batchSize = 1000;
        
        while (true) {
            ScrollPoints scrollResult = qdrantClient.scroll(collectionName, 
                ScrollRequest.builder()
                    .offset(offset)
                    .limit(batchSize)
                    .build());
            
            allPoints.addAll(scrollResult.getResult().getPoints());
            
            if (scrollResult.getResult().getPoints().size() < batchSize) {
                break;
            }
            
            offset += batchSize;
        }
        
        return allPoints;
    }
    
    private void cleanupOldBackups() {
        Instant threshold = Instant.now().minus(30, ChronoUnit.DAYS);
        List<String> oldBackups = s3Service.listObjects(BACKUP_BUCKET, threshold);
        
        for (String key : oldBackups) {
            s3Service.deleteObject(BACKUP_BUCKET, key);
            log.info("清理过期备份: {}", key);
        }
    }
}
```

## 8.3 文件存储备份

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageBackupService {
    
    private final MinioClient minioClient;
    private final S3Service s3Service;
    
    private static final String SOURCE_BUCKET = "rag-documents";
    private static final String BACKUP_BUCKET = "rag-documents-backup";
    
    @Scheduled(cron = "0 0 4 * * ?") // 每天凌晨4点
    public void incrementalBackup() {
        try {
            log.info("开始文件存储增量备份");
            
            // 1. 获取昨天修改的文件列表
            List<String> modifiedFiles = getModifiedFiles(
                LocalDate.now().minusDays(1));
            
            // 2. 复制到备份存储
            for (String file : modifiedFiles) {
                copyToBackup(file);
            }
            
            log.info("文件存储备份完成，共备份 {} 个文件", modifiedFiles.size());
            
        } catch (Exception e) {
            log.error("文件存储备份失败", e);
        }
    }
    
    @Scheduled(cron = "0 0 5 * * SUN") // 每周日凌晨5点
    public void fullBackup() {
        try {
            log.info("开始文件存储全量备份");
            
            // 每周执行一次全量备份
            List<String> allFiles = listAllFiles();
            
            for (String file : allFiles) {
                copyToBackup(file);
            }
            
            log.info("文件存储全量备份完成，共备份 {} 个文件", allFiles.size());
            
        } catch (Exception e) {
            log.error("文件存储全量备份失败", e);
        }
    }
    
    private void copyToBackup(String sourceKey) {
        try {
            // 从MinIO复制到S3
            InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(SOURCE_BUCKET)
                    .object(sourceKey)
                    .build());
            
            s3Service.upload(stream, BACKUP_BUCKET, sourceKey);
            
        } catch (Exception e) {
            log.error("文件复制失败: {}", sourceKey, e);
        }
    }
}
```

## 8.4 灾难恢复流程

```markdown
## 灾难恢复手册

### 恢复场景

#### 场景1：数据库完全故障

1. 评估影响范围
   - 确定故障时间点
   - 评估数据丢失量
   
2. 执行恢复
   ```bash
   # 停止应用
   kubectl scale deployment rag-api --replicas=0
   
   # 删除损坏的数据目录
   rm -rf $PGDATA/*
   
   # 恢复最新全量备份
   pg_restore -h $DB_HOST -U $DB_USER -d $DB_NAME -c latest_full.dump.gz
   
   # 应用WAL恢复
   pg_restore -h $DB_HOST -U $DB_USER -d $DB_NAME -c latest_wal.tar.gz
   
   # 启动应用
   kubectl scale deployment rag-api --replicas=3
   ```
   
3. 验证
   - 检查数据完整性
   - 验证业务功能

#### 场景2：向量数据库故障

1. 停止向量写入
2. 从备份恢复
   ```bash
   # 下载最新备份
   aws s3 cp s3://backup-bucket/vector/latest.tar.gz /tmp/
   
   # 导入数据
   curl -X POST http://qdrant:6333/collections/{collection}/points/delete -d '{"filter": {}}'
   
   # 批量导入
   curl -X PUT http://qdrant:6333/collections/{collection}/points -d @backup.json
   ```
3. 验证检索功能

#### 场景3：文件存储故障

1. 切换到备份存储
2. 恢复文件
   ```bash
   # 从S3恢复
   aws s3 sync s3://backup-bucket/ s3://rag-documents/
   ```
3. 验证文件访问
```

---

*第三部分完成*
