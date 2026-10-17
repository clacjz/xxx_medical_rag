# RAG知识库系统高层设计文档

## 第二部分：模块详细设计与技术实现

> **文档版本**: V1.0  
> **编制日期**: 2026-03-25  
> **前续内容**: 第一部分 - 项目概述与架构设计

---

## 七、模块详细设计

### 7.1 用户权限模块详细设计

#### 7.1.1 核心类设计

```java
// 用户实体
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String username;
    
    @Column(unique = true, nullable = false, length = 100)
    private String email;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    @Column(name = "display_name", length = 100)
    private String displayName;
    
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserStatus status = UserStatus.ACTIVE;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles;
}

public enum UserStatus {
    ACTIVE,       // 正常
    INACTIVE,     // 未激活
    LOCKED,       // 锁定
    DELETED       // 已删除
}

// JWT Token Provider
@Component
public class JwtTokenProvider {
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${jwt.expiration}")
    private long jwtExpiration; // 毫秒
    
    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;
    
    // 生成Access Token
    public String generateAccessToken(UserDetails user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);
        
        return Jwts.builder()
            .setSubject(user.getUsername())
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .claim("type", "access")
            .claim("roles", user.getAuthorities())
            .signWith(key, SignatureAlgorithm.HS512)
            .compact();
    }
    
    // 生成Refresh Token
    public String generateRefreshToken(UserDetails user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration);
        
        return Jwts.builder()
            .setSubject(user.getUsername())
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .claim("type", "refresh")
            .signWith(key, SignatureAlgorithm.HS512)
            .compact();
    }
    
    // 验证Token
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
    
    // 从Token获取用户名
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();
        return claims.getSubject();
    }
}

// 认证服务
@Service
@Slf4j
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final AuditService auditService;
    
    @Transactional
    public User register(RegisterRequest request) {
        // 验证用户名唯一性
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username already exists");
        }
        
        // 验证邮箱唯一性
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already exists");
        }
        
        // 密码强度验证
        validatePasswordStrength(request.getPassword());
        
        // 创建用户
        User user = User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .displayName(request.getDisplayName())
            .status(UserStatus.ACTIVE)
            .build();
        
        user = userRepository.save(user);
        
        // 记录审计日志
        auditService.log(user.getId(), "USER_REGISTER", "user", user.getId(), null);
        
        return user;
    }
    
    @Transactional
    public TokenResponse login(LoginRequest request) {
        // 查找用户
        User user = userRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new AuthenticationException("Invalid credentials"));
        
        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthenticationException("Invalid credentials");
        }
        
        // 验证用户状态
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationException("User account is not active");
        }
        
        // 生成Token
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(user.getPasswordHash())
            .authorities(user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                .toArray(SimpleGrantedAuthority[]::new))
            .build();
        
        String accessToken = tokenProvider.generateAccessToken(userDetails);
        String refreshToken = tokenProvider.generateRefreshToken(userDetails);
        
        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        // 记录审计日志
        auditService.log(user.getId(), "USER_LOGIN", "user", user.getId(), null);
        
        return TokenResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(tokenProvider.getJwtExpiration() / 1000)
            .tokenType("Bearer")
            .build();
    }
    
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        // 验证Refresh Token
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new AuthenticationException("Invalid refresh token");
        }
        
        // 检查Token类型
        if (!"refresh".equals(tokenProvider.getTokenType(refreshToken))) {
            throw new AuthenticationException("Invalid token type");
        }
        
        // 检查是否在黑名单
        if (redisTemplate.hasKey("jwt:blacklist:" + refreshToken)) {
            throw new AuthenticationException("Token has been revoked");
        }
        
        // 获取用户名
        String username = tokenProvider.getUsernameFromToken(refreshToken);
        
        // 生成新Token
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new AuthenticationException("User not found"));
        
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(user.getPasswordHash())
            .authorities(user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                .toArray(SimpleGrantedAuthority[]::new))
            .build();
        
        String newAccessToken = tokenProvider.generateAccessToken(userDetails);
        String newRefreshToken = tokenProvider.generateRefreshToken(userDetails);
        
        // 将旧Token加入黑名单
        redisTemplate.opsForValue().set(
            "jwt:blacklist:" + refreshToken,
            "revoked",
            tokenProvider.getExpiration(refreshToken),
            TimeUnit.MILLISECONDS
        );
        
        return TokenResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .expiresIn(tokenProvider.getJwtExpiration() / 1000)
            .tokenType("Bearer")
            .build();
    }
}

// RBAC权限服务
@Service
@Slf4j
public class RBACService {
    
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    
    // 权限检查
    public boolean hasPermission(UUID userId, String resource, String action) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found"));
        
        return user.getRoles().stream()
            .flatMap(role -> role.getPermissions().stream())
            .anyMatch(permission -> 
                permission.getResource().equals(resource) && 
                permission.getAction().equals(action));
    }
    
    // 资源级别权限检查
    public boolean canAccessResource(UUID userId, UUID resourceOwnerId, String resourceType) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found"));
        
        // 管理员可以访问所有资源
        if (user.getRoles().stream()
            .anyMatch(role -> role.getName().equals("ADMIN"))) {
            return true;
        }
        
        // 资源所有者可以访问自己的资源
        if (userId.equals(resourceOwnerId)) {
            return true;
        }
        
        return false;
    }
    
    // 分配角色
    @Transactional
    public void assignRole(UUID userId, UUID roleId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found"));
        
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new BusinessException("Role not found"));
        
        user.getRoles().add(role);
        userRepository.save(user);
        
        // 记录审计日志
        auditService.log(userId, "ROLE_ASSIGN", "role", roleId, null);
    }
}
```

#### 7.1.2 安全配置

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Autowired
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF（使用JWT不需要）
            .csrf(csrf -> csrf.disable())
            
            // 配置异常处理
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
            )
            
            // 配置会话管理
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // 配置请求授权
            .authorizeHttpRequests(auth -> auth
                // 公开接口
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // 管理员接口
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                
                // 其他接口需要认证
                .anyRequest().authenticated()
            )
            
            // 添加JWT过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            
            // 禁用headers
            .headers(headers -> headers
                .frameOptions(frame -> frame.disable())
            );
        
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}

// JWT认证过滤器
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private JwtTokenProvider tokenProvider;
    
    @Autowired
    private UserDetailsService userDetailsService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) 
            throws ServletException, IOException {
        
        try {
            String jwt = getJwtFromRequest(request);
            
            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String username = tokenProvider.getUsernameFromToken(jwt);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(
                        userDetails, 
                        null, 
                        userDetails.getAuthorities()
                    );
                
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }
        
        filterChain.doFilter(request, response);
    }
}
```

### 7.2 文档处理模块详细设计

#### 7.2.1 文档处理器策略模式

```java
// 文档处理器接口
public interface DocumentProcessor {
    
    /**
     * 判断是否支持该文件类型
     */
    boolean supports(String fileType);
    
    /**
     * 提取文本内容
     */
    Document extractText(InputStream inputStream, String fileName) throws Exception;
    
    /**
     * 提取元数据
     */
    DocumentMetadata extractMetadata(InputStream inputStream) throws Exception;
    
    /**
     * 获取支持的文档类型
     */
    default List<String> getSupportedTypes() {
        return Collections.singletonList(getType());
    }
    
    /**
     * 返回处理器类型
     */
    String getType();
}

// PDF文档处理器
@Component
public class PDFDocumentProcessor implements DocumentProcessor {
    
    private static final Logger log = LoggerFactory.getLogger(PDFDocumentProcessor.class);
    
    @Override
    public boolean supports(String fileType) {
        return "pdf".equalsIgnoreCase(fileType);
    }
    
    @Override
    public String getType() {
        return "pdf";
    }
    
    @Override
    public Document extractText(InputStream inputStream, String fileName) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        
        try (PDDocument document = PDDocument.load(inputStream)) {
            // 按页提取文本
            List<String> pages = new ArrayList<>();
            for (int i = 1; i <= document.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document);
                pages.add(pageText);
            }
            
            // 提取目录结构
            List<OutlineItem> outline = extractOutline(document);
            
            // 提取表格
            List<Table> tables = extractTables(document);
            
            return Document.builder()
                .pages(pages)
                .outline(outline)
                .tables(tables)
                .metadata(extractMetadata(document))
                .build();
        }
    }
    
    @Override
    public DocumentMetadata extractMetadata(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            DocumentMetadata metadata = new DocumentMetadata();
            
            PDDocumentInformation info = document.getDocumentInformation();
            metadata.setTitle(info.getTitle());
            metadata.setAuthor(info.getAuthor());
            metadata.setSubject(info.getSubject());
            metadata.setKeywords(info.getKeywords());
            metadata.setCreator(info.getCreator());
            metadata.setProducer(info.getProducer());
            metadata.setCreationDate(info.getCreationDate());
            metadata.setModificationDate(info.getModDate());
            metadata.setPageCount(document.getNumberOfPages());
            
            return metadata;
        }
    }
    
    // 提取大纲目录
    private List<OutlineItem> extractOutline(PDDocument document) throws IOException {
        List<OutlineItem> outline = new ArrayList<>();
        
        PDDocumentCatalog catalog = document.getDocumentCatalog();
        PDActionsFactory actionsFactory = catalog.getActions();
        
        // 提取PDF大纲（如果有）
        PDOutlineNode outlineRoot = catalog.getDocumentOutline();
        if (outlineRoot != null) {
            extractOutlineItems(outlineRoot, outline, 0);
        }
        
        return outline;
    }
    
    private void extractOutlineItems(PDOutlineNode parent, List<OutlineItem> result, int level) {
        PDOutlineItem current = parent.getFirstChild();
        while (current != null) {
            result.add(new OutlineItem(
                current.getTitle(),
                level,
                current.getDestination()
            ));
            if (current.hasChildren()) {
                extractOutlineItems(current, current, level + 1);
            }
            current = current.getNextSibling();
        }
    }
    
    // 提取表格
    private List<Table> extractTables(PDDocument document) {
        List<Table> tables = new ArrayList<>();
        
        // 使用pdfplumber提取表格
        try (PDFParser parser = new PDFParser(inputStream)) {
            parser.parse();
            PDDocument pdDocument = parser.getPDDocument();
            
            try (PDFTextStripper stripper = new PDFTextStripper()) {
                for (int i = 1; i <= pdDocument.getNumberOfPages(); i++) {
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    String pageText = stripper.getText(pdDocument);
                    
                    // 简化：按空格和换行识别表格
                    // 实际实现中可以使用pdfplumber或iText
                    List<List<String>> tableData = parseTableFromText(pageText);
                    if (!tableData.isEmpty()) {
                        tables.add(new Table(i, tableData));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract tables from PDF", e);
        }
        
        return tables;
    }
}

// Word文档处理器
@Component
public class WordDocumentProcessor implements DocumentProcessor {
    
    @Override
    public boolean supports(String fileType) {
        return "docx".equalsIgnoreCase(fileType);
    }
    
    @Override
    public String getType() {
        return "docx";
    }
    
    @Override
    public Document extractText(InputStream inputStream, String fileName) throws Exception {
        XWPFDocument document = new XWPFDocument(inputStream);
        List<String> paragraphs = new ArrayList<>();
        List<Table> tables = new ArrayList<>();
        List<OutlineItem> outline = new ArrayList<>();
        
        // 提取段落
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String text = paragraph.getText();
            if (StringUtils.isNotBlank(text)) {
                paragraphs.add(text);
                
                // 提取标题级别
                if (paragraph.getStyleID() != null && 
                    paragraph.getStyleID().startsWith("Heading")) {
                    String levelStr = paragraph.getStyleID().replace("Heading", "");
                    int level = StringUtils.isNumeric(levelStr) ? Integer.parseInt(levelStr) : 1;
                    outline.add(new OutlineItem(text, level, null));
                }
            }
        }
        
        // 提取表格
        for (XWPFTable table : document.getTables()) {
            List<List<String>> tableData = new ArrayList<>();
            for (XWPFTableRow row : table.getRows()) {
                List<String> rowData = new ArrayList<>();
                for (XWPFTableCell cell : row.getCells()) {
                    rowData.add(cell.getText());
                }
                tableData.add(rowData);
            }
            tables.add(new Table(paragraphs.size(), tableData));
        }
        
        document.close();
        
        // 合并所有段落为完整文本
        String fullText = String.join("\n\n", paragraphs);
        
        return Document.builder()
            .fullText(fullText)
            .pages(Collections.singletonList(fullText))
            .paragraphs(paragraphs)
            .tables(tables)
            .outline(outline)
            .build();
    }
    
    @Override
    public DocumentMetadata extractMetadata(InputStream inputStream) throws Exception {
        XWPFDocument document = new XWPFDocument(inputStream);
        DocumentMetadata metadata = new DocumentMetadata();
        
        // 提取核心属性
        metadata.setTitle(document.getProperties().getTitle());
        metadata.setAuthor(document.getProperties().getCoreProperties().getCreator());
        metadata.setSubject(document.getProperties().getCoreProperties().getSubject());
        metadata.setKeywords(document.getProperties().getCoreProperties().getKeywords());
        
        // 提取创建和修改时间
        if (document.getProperties().getCoreProperties().getCreated() != null) {
            metadata.setCreationDate(
                document.getProperties().getCoreProperties().getCreated().toInstant()
            );
        }
        if (document.getProperties().getCoreProperties().getModified() != null) {
            metadata.setModificationDate(
                document.getProperties().getCoreProperties().getModified().toInstant()
            );
        }
        
        metadata.setPageCount(document.getParagraphs().size()); // 近似
        
        document.close();
        return metadata;
    }
}

// Markdown处理器
@Component
public class MarkdownDocumentProcessor implements DocumentProcessor {
    
    private final MarkdigEngine markdig = new MarkdigEngine.Builder()
        .build();
    
    @Override
    public boolean supports(String fileType) {
        return "md".equalsIgnoreCase(fileType) || 
               "markdown".equalsIgnoreCase(fileType);
    }
    
    @Override
    public String getType() {
        return "markdown";
    }
    
    @Override
    public Document extractText(InputStream inputStream, String fileName) throws Exception {
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        
        // 解析Markdown
        Document document = markdig.parse(content);
        
        // 提取大纲
        List<OutlineItem> outline = extractOutline(document);
        
        // 提取代码块
        List<CodeBlock> codeBlocks = extractCodeBlocks(document);
        
        return Document.builder()
            .fullText(content)
            .pages(Collections.singletonList(content))
            .outline(outline)
            .codeBlocks(codeBlocks)
            .build();
    }
    
    private List<OutlineItem> extractOutline(MarkdownDocument document) {
        List<OutlineItem> outline = new ArrayList<>();
        
        document.getBlocks().forEach(block -> {
            if (block instanceof HeadingBlock) {
                HeadingBlock heading = (HeadingBlock) block;
                int level = heading.getLevel();
                String text = extractText(heading);
                outline.add(new OutlineItem(text, level, null));
            }
        });
        
        return outline;
    }
}
```

#### 7.2.2 文档分块器

```java
// 分块策略接口
public interface ChunkingStrategy {
    
    /**
     * 执行分块
     */
    List<TextChunk> chunk(String text, ChunkingConfig config);
    
    /**
     * 获取策略名称
     */
    String getName();
}

// 固定长度分块策略
@Component
public class FixedSizeChunkingStrategy implements ChunkingStrategy {
    
    @Override
    public List<TextChunk> chunk(String text, ChunkingConfig config) {
        List<TextChunk> chunks = new ArrayList<>();
        
        int chunkSize = config.getChunkSize();
        int overlap = config.getOverlap();
        int minChunkSize = config.getMinChunkSize();
        
        if (chunkSize <= 0) {
            chunkSize = 512; // 默认512字符
        }
        
        if (overlap >= chunkSize) {
            overlap = chunkSize / 4; // overlap不能超过chunkSize
        }
        
        int textLength = text.length();
        int step = chunkSize - overlap;
        
        for (int i = 0; i < textLength; i += step) {
            int end = Math.min(i + chunkSize, textLength);
            String chunkText = text.substring(i, end);
            
            // 跳过太小的块（除非是最后一个）
            if (chunkText.length() < minChunkSize && (i + step) < textLength) {
                continue;
            }
            
            // 尽量在句子边界分割
            if (end < textLength) {
                int lastSentenceEnd = findLastSentenceEnd(chunkText);
                if (lastSentenceEnd > minChunkSize) {
                    end = i + lastSentenceEnd;
                    chunkText = text.substring(i, end);
                }
            }
            
            chunks.add(TextChunk.builder()
                .content(chunkText)
                .chunkIndex(chunks.size())
                .chunkSize(chunkText.length())
                .startPosition(i)
                .endPosition(end)
                .build());
            
            if (end >= textLength) {
                break;
            }
        }
        
        return chunks;
    }
    
    @Override
    public String getName() {
        return "fixed_size";
    }
    
    private int findLastSentenceEnd(String text) {
        // 句子结束标记：。！？.!?
        int lastEnd = text.length();
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if ("。！？.!?".indexOf(c) >= 0) {
                lastEnd = i + 1;
                break;
            }
        }
        return lastEnd;
    }
}

// 语义分块策略
@Component
public class SemanticChunkingStrategy implements ChunkingStrategy {
    
    @Override
    public List<TextChunk> chunk(String text, ChunkingConfig config) {
        List<TextChunk> chunks = new ArrayList<>();
        
        // 1. 按段落分割
        List<String> paragraphs = splitByParagraphs(text);
        
        // 2. 合并段落形成语义块
        StringBuilder currentChunk = new StringBuilder();
        int currentSize = 0;
        int chunkIndex = 0;
        
        for (String paragraph : paragraphs) {
            int paragraphSize = paragraph.length();
            
            // 如果单个段落就超过chunkSize，需要进一步分割
            if (paragraphSize > config.getChunkSize()) {
                // 先保存当前块
                if (currentSize > 0) {
                    chunks.add(createChunk(currentChunk.toString(), chunkIndex++, 0, currentSize));
                    currentChunk.setLength(0);
                    currentSize = 0;
                }
                
                // 递归分割大段落
                List<TextChunk> subChunks = splitLargeParagraph(paragraph, config, chunkIndex);
                chunks.addAll(subChunks);
                chunkIndex += subChunks.size();
            } else if (currentSize + paragraphSize > config.getChunkSize()) {
                // 当前块已满，保存并开始新块
                chunks.add(createChunk(currentChunk.toString(), chunkIndex++, 0, currentSize));
                currentChunk.setLength(0);
                currentChunk.append(paragraph);
                currentSize = paragraphSize;
            } else {
                // 添加到当前块
                if (currentSize > 0) {
                    currentChunk.append("\n\n");
                    currentSize += 2;
                }
                currentChunk.append(paragraph);
                currentSize += paragraphSize;
            }
        }
        
        // 保存最后一个块
        if (currentSize > 0) {
            chunks.add(createChunk(currentChunk.toString(), chunkIndex++, 0, currentSize));
        }
        
        return chunks;
    }
    
    @Override
    public String getName() {
        return "semantic";
    }
    
    private List<String> splitByParagraphs(String text) {
        return Arrays.stream(text.split("\\n\\s*\\n"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }
    
    private List<TextChunk> splitLargeParagraph(String paragraph, ChunkingConfig config, int startIndex) {
        List<TextChunk> chunks = new FixedSizeChunkingStrategy()
            .chunk(paragraph, config);
        
        // 更新chunkIndex
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setChunkIndex(startIndex + i);
        }
        
        return chunks;
    }
    
    private TextChunk createChunk(String content, int index, int start, int size) {
        return TextChunk.builder()
            .content(content)
            .chunkIndex(index)
            .chunkSize(size)
            .startPosition(start)
            .endPosition(start + size)
            .build();
    }
}

// 混合分块策略
@Component
public class HybridChunkingStrategy implements ChunkingStrategy {
    
    private final FixedSizeChunkingStrategy fixedStrategy;
    private final SemanticChunkingStrategy semanticStrategy;
    
    @Override
    public List<TextChunk> chunk(String text, ChunkingConfig config) {
        List<TextChunk> chunks;
        
        // 1. 先进行语义分块
        List<String> paragraphs = splitByParagraphs(text);
        
        // 2. 计算段落长度分布
        double avgLength = paragraphs.stream()
            .mapToInt(String::length)
            .average()
            .orElse(100);
        
        // 3. 根据段落长度选择策略
        if (avgLength > config.getChunkSize() * 0.8) {
            // 段落普遍较长，使用固定长度分块
            chunks = fixedStrategy.chunk(text, config);
        } else {
            // 段落长度适中，使用语义分块
            chunks = semanticStrategy.chunk(text, config);
        }
        
        // 4. 如果块数量过多，进行二次合并
        if (chunks.size() > config.getMaxChunks()) {
            chunks = mergeChunks(chunks, config);
        }
        
        return chunks;
    }
    
    @Override
    public String getName() {
        return "hybrid";
    }
    
    private List<String> splitByParagraphs(String text) {
        return Arrays.stream(text.split("\\n\\s*\\n"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }
    
    private List<TextChunk> mergeChunks(List<TextChunk> chunks, ChunkingConfig config) {
        List<TextChunk> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int index = 0;
        
        for (TextChunk chunk : chunks) {
            if (current.length() + chunk.getContent().length() <= config.getChunkSize() * 2) {
                if (current.length() > 0) {
                    current.append("\n\n");
                }
                current.append(chunk.getContent());
            } else {
                if (current.length() > 0) {
                    merged.add(TextChunk.builder()
                        .content(current.toString())
                        .chunkIndex(index++)
                        .chunkSize(current.length())
                        .build());
                    current.setLength(0);
                }
                current.append(chunk.getContent());
            }
        }
        
        if (current.length() > 0) {
            merged.add(TextChunk.builder()
                .content(current.toString())
                .chunkIndex(index)
                .chunkSize(current.length())
                .build());
        }
        
        return merged;
    }
}

// 文档分块器
@Service
@Slf4j
public class DocumentChunker {
    
    private final Map<String, ChunkingStrategy> strategies;
    private final ChunkingConfig defaultConfig;
    
    public DocumentChunker(
            List<ChunkingStrategy> strategyList,
            @Value("${rag.chunking.chunk-size:512}") int defaultChunkSize,
            @Value("${rag.chunking.overlap:64}") int defaultOverlap) {
        
        this.strategies = strategyList.stream()
            .collect(Collectors.toMap(ChunkingStrategy::getName, s -> s));
        
        this.defaultConfig = ChunkingConfig.builder()
            .chunkSize(defaultChunkSize)
            .overlap(defaultOverlap)
            .minChunkSize(50)
            .maxChunks(1000)
            .build();
    }
    
    public List<TextChunk> chunk(String text, String strategy, ChunkingConfig config) {
        ChunkingStrategy chunkingStrategy = strategies.getOrDefault(
            strategy, 
            strategies.get("fixed_size")
        );
        
        if (chunkingStrategy == null) {
            throw new BusinessException("Unknown chunking strategy: " + strategy);
        }
        
        ChunkingConfig finalConfig = config != null ? config : defaultConfig;
        
        log.info("Chunking text with strategy: {}, chunkSize: {}", 
            chunkingStrategy.getName(), finalConfig.getChunkSize());
        
        return chunkingStrategy.chunk(text, finalConfig);
    }
    
    public List<TextChunk> chunk(String text) {
        return chunk(text, "fixed_size", null);
    }
}

// 分块配置
@Data
@Builder
public class ChunkingConfig {
    private int chunkSize;        // 块大小（字符数）
    private int overlap;          // 重叠大小
    private int minChunkSize;      // 最小块大小
    private int maxChunks;         // 最大块数量
    private boolean preserveStructure; // 尽量保持结构完整性
}
```

### 7.3 RAG引擎模块详细设计

#### 7.3.1 Embedding服务客户端

```java
// Embedding服务接口
public interface EmbeddingService {
    
    /**
     * 嵌入单条文本
     */
    float[] embed(String text);
    
    /**
     * 批量嵌入文本
     */
    List<float[]> embedBatch(List<String> texts);
    
    /**
     * 嵌入查询文本
     */
    float[] embedQuery(String query);
    
    /**
     * 获取模型信息
     */
    EmbeddingModel getModel();
    
    /**
     * 获取向量维度
     */
    int getDimension();
}

// 本地Embedding服务实现
@Service
@Slf4j
public class LocalEmbeddingService implements EmbeddingService {
    
    private final RestTemplate restTemplate;
    private final String embeddingServiceUrl;
    private final String modelName;
    private final int dimension;
    
    // Embedding结果缓存
    private final LoadingCache<String, float[]> embeddingCache;
    
    public LocalEmbeddingService(
            @Value("${rag.embedding.url}") String serviceUrl,
            @Value("${rag.embedding.model}") String model,
            @Value("${rag.embedding.dimension:1024}") int dim) {
        
        this.restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(30))
            .build();
        
        this.embeddingServiceUrl = serviceUrl;
        this.modelName = model;
        this.dimension = dim;
        
        // 初始化缓存（LRU缓存，最多10000条）
        this.embeddingCache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(Duration.ofHours(24))
            .build(key -> null); // 不使用自动加载
    }
    
    @Override
    public float[] embed(String text) {
        // 先检查缓存
        String cacheKey = generateCacheKey(text);
        float[] cached = embeddingCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // 调用Embedding服务
        EmbedRequest request = new EmbedRequest(text, true);
        EmbedResponse response = restTemplate.postForObject(
            embeddingServiceUrl + "/embed",
            request,
            EmbedResponse.class
        );
        
        float[] embedding = response.getEmbedding();
        
        // 存入缓存
        embeddingCache.put(cacheKey, embedding);
        
        return embedding;
    }
    
    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 检查缓存并分离需要处理的文本
        List<String> toProcess = new ArrayList<>();
        List<float[]> results = new ArrayList<>();
        Map<String, Integer> textIndexMap = new HashMap<>();
        
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            String cacheKey = generateCacheKey(text);
            float[] cached = embeddingCache.getIfPresent(cacheKey);
            
            if (cached != null) {
                results.add(cached);
            } else {
                results.add(null);
                toProcess.add(text);
                textIndexMap.put(text, i);
            }
        }
        
        // 批量调用Embedding服务
        if (!toProcess.isEmpty()) {
            BatchEmbedRequest request = new BatchEmbedRequest(toProcess, true);
            BatchEmbedResponse response = restTemplate.postForObject(
                embeddingServiceUrl + "/embed/batch",
                request,
                BatchEmbedResponse.class
            );
            
            List<float[]> embeddings = response.getEmbeddings();
            
            // 更新结果
            for (int i = 0; i < toProcess.size(); i++) {
                float[] embedding = embeddings.get(i);
                String text = toProcess.get(i);
                int originalIndex = textIndexMap.get(text);
                
                // 存入缓存
                String cacheKey = generateCacheKey(text);
                embeddingCache.put(cacheKey, embedding);
                
                results.set(originalIndex, embedding);
            }
        }
        
        return results;
    }
    
    @Override
    public float[] embedQuery(String query) {
        // 查询文本通常较短，使用单独接口
        QueryEmbedRequest request = new QueryEmbedRequest(query);
        QueryEmbedResponse response = restTemplate.postForObject(
            embeddingServiceUrl + "/embed/query",
            request,
            QueryEmbedResponse.class
        );
        
        return response.getEmbedding();
    }
    
    @Override
    public EmbeddingModel getModel() {
        return EmbeddingModel.builder()
            .name(modelName)
            .type("sentence-transformers")
            .dimension(dimension)
            .normalize(true)
            .build();
    }
    
    @Override
    public int getDimension() {
        return dimension;
    }
    
    private String generateCacheKey(String text) {
        // 使用文本的MD5作为缓存键
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5总是存在
            return text.hashCode() + "_" + text.length();
        }
    }
}

// DTO类
@Data
public class EmbedRequest {
    private String text;
    private boolean normalize;
}

@Data
public class EmbedResponse {
    private float[] embedding;
    private String model;
    private int dimension;
    private long processingTimeMs;
}

@Data
public class BatchEmbedRequest {
    private List<String> texts;
    private boolean normalize;
}

@Data
public class BatchEmbedResponse {
    private List<float[]> embeddings;
    private String model;
    private int dimension;
    private int batchSize;
    private long processingTimeMs;
}
```

#### 7.3.2 LLM服务客户端

```java
// LLM服务接口
public interface LLMService {
    
    /**
     * 同步生成
     */
    String generate(String prompt);
    
    /**
     * 同步生成（带参数）
     */
    String generate(String prompt, LLMConfig config);
    
    /**
     * 流式生成
     */
    Flux<String> generateStream(String prompt);
    
    /**
     * 流式生成（带参数）
     */
    Flux<String> generateStream(String prompt, LLMConfig config);
    
    /**
     * 获取提供商类型
     */
    LLMProvider getProvider();
    
    /**
     * 获取当前模型
     */
    String getModel();
}

// LLM配置
@Data
@Builder
public class LLMConfig {
    @Builder.Default
    private double temperature = 0.7;
    
    @Builder.Default
    private int maxTokens = 2048;
    
    @Builder.Default
    private double topP = 0.9;
    
    @Builder.Default
    private List<String> stop = null;
    
    private boolean stream;
}

// vLLM服务实现
@Service
@Slf4j
public class VLLMService implements LLMService {
    
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String defaultModel;
    
    public VLLMService(
            @Value("${rag.llm.vllm.base-url}") String baseUrl,
            @Value("${rag.llm.vllm.model}") String model) {
        this.restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(120))
            .build();
        
        this.baseUrl = baseUrl;
        this.defaultModel = model;
    }
    
    @Override
    public String generate(String prompt) {
        return generate(prompt, LLMConfig.builder().build());
    }
    
    @Override
    public String generate(String prompt, LLMConfig config) {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
            .model(defaultModel)
            .messages(Collections.singletonList(
                ChatMessage.builder()
                    .role("user")
                    .content(prompt)
                    .build()
            ))
            .temperature(config.getTemperature())
            .maxTokens(config.getMaxTokens())
            .topP(config.getTopP())
            .stop(config.getStop())
            .stream(false)
            .build();
        
        ChatCompletionResponse response = restTemplate.postForObject(
            baseUrl + "/v1/chat/completions",
            request,
            ChatCompletionResponse.class
        );
        
        return response.getChoices().get(0).getMessage().getContent();
    }
    
    @Override
    public Flux<String> generateStream(String prompt) {
        return generateStream(prompt, LLMConfig.builder().build());
    }
    
    @Override
    public Flux<String> generateStream(String prompt, LLMConfig config) {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
            .model(defaultModel)
            .messages(Collections.singletonList(
                ChatMessage.builder()
                    .role("user")
                    .content(prompt)
                    .build()
            ))
            .temperature(config.getTemperature())
            .maxTokens(config.getMaxTokens())
            .topP(config.getTopP())
            .stop(config.getStop())
            .stream(true)
            .build();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<ChatCompletionRequest> entity = new HttpEntity<>(request, headers);
        
        return restTemplate.postForEntity(
            baseUrl + "/v1/chat/completions",
            entity,
            ParameterizedTypeReference.get(String.class)
        ).getBody();
    }
    
    @Override
    public LLMProvider getProvider() {
        return LLMProvider.VLLM;
    }
    
    @Override
    public String getModel() {
        return defaultModel;
    }
}

// Ollama服务实现
@Service
@Slf4j
public class OllamaService implements LLMService {
    
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String defaultModel;
    
    public OllamaService(
            @Value("${rag.llm.ollama.base-url}") String baseUrl,
            @Value("${rag.llm.ollama.model}") String model) {
        this.restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(120))
            .build();
        
        this.baseUrl = baseUrl;
        this.defaultModel = model;
    }
    
    @Override
    public String generate(String prompt) {
        return generate(prompt, LLMConfig.builder().build());
    }
    
    @Override
    public String generate(String prompt, LLMConfig config) {
        OllamaGenerateRequest request = OllamaGenerateRequest.builder()
            .model(defaultModel)
            .prompt(prompt)
            .temperature(config.getTemperature())
            .options(Map.of("num_predict", config.getMaxTokens()))
            .stream(false)
            .build();
        
        OllamaGenerateResponse response = restTemplate.postForObject(
            baseUrl + "/api/generate",
            request,
            OllamaGenerateResponse.class
        );
        
        return response.getResponse();
    }
    
    @Override
    public Flux<String> generateStream(String prompt) {
        return generateStream(prompt, LLMConfig.builder().build());
    }
    
    @Override
    public Flux<String> generateStream(String prompt, LLMConfig config) {
        OllamaGenerateRequest request = OllamaGenerateRequest.builder()
            .model(defaultModel)
            .prompt(prompt)
            .temperature(config.getTemperature())
            .options(Map.of("num_predict", config.getMaxTokens()))
            .stream(true)
            .build();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<OllamaGenerateRequest> entity = new HttpEntity<>(request, headers);
        
        return restTemplate.postForEntity(
            baseUrl + "/api/generate",
            entity,
            ParameterizedTypeReference.get(String.class)
        ).getBody();
    }
    
    @Override
    public LLMProvider getProvider() {
        return LLMProvider.OLLAMA;
    }
    
    @Override
    public String getModel() {
        return defaultModel;
    }
}
```

#### 7.3.3 RAG编排器

```java
// RAG编排器
@Service
@Slf4j
public class RAGOrchestrator {
    
    private final EmbeddingService embeddingService;
    private final LLMService llmService;
    private final VectorStoreService vectorStoreService;
    private final RetrievalService retrievalService;
    private final PromptTemplateManager promptManager;
    
    public RAGOrchestrator(
            EmbeddingService embeddingService,
            @Qualifier("vllmService") LLMService llmService,
            VectorStoreService vectorStoreService,
            RetrievalService retrievalService,
            PromptTemplateManager promptManager) {
        
        this.embeddingService = embeddingService;
        this.llmService = llmService;
        this.vectorStoreService = vectorStoreService;
        this.retrievalService = retrievalService;
        this.promptManager = promptManager;
    }
    
    /**
     * 问答查询
     */
    public QueryResponse query(QueryRequest request) {
        long startTime = System.currentTimeMillis();
        
        // 1. 查询向量化
        float[] queryVector = embeddingService.embedQuery(request.getQuestion());
        
        // 2. 检索相关文档
        RetrievalConfig retrievalConfig = request.getSearchConfig() != null 
            ? request.getSearchConfig() 
            : RetrievalConfig.DEFAULT;
        
        List<SearchResult> searchResults = retrievalService.search(
            request.getCollectionId(),
            queryVector,
            retrievalConfig
        );
        
        // 3. 构建上下文
        String context = buildContext(searchResults);
        
        // 4. 构建Prompt
        String prompt = promptManager.buildPrompt(
            request.getQuestion(),
            context,
            request.getConversationHistory()
        );
        
        // 5. 生成回答
        LLMConfig llmConfig = request.getLlMConfig() != null 
            ? request.getLlmConfig() 
            : LLMConfig.builder().build();
        
        String answer = llmService.generate(prompt, llmConfig);
        
        // 6. 构建响应
        long processingTime = System.currentTimeMillis() - startTime;
        
        return QueryResponse.builder()
            .answer(answer)
            .sources(searchResults.stream()
                .map(this::convertToSource)
                .collect(Collectors.toList()))
            .usage(UsageInfo.builder()
                .promptTokens(estimateTokenCount(prompt))
                .completionTokens(estimateTokenCount(answer))
                .totalTokens(estimateTokenCount(prompt) + estimateTokenCount(answer))
                .build())
            .processingTimeMs(processingTime)
            .build();
    }
    
    /**
     * 流式问答
     */
    public Flux<StreamResponse> queryStream(QueryRequest request) {
        // 1. 查询向量化
        float[] queryVector = embeddingService.embedQuery(request.getQuestion());
        
        // 2. 检索相关文档
        RetrievalConfig retrievalConfig = request.getSearchConfig() != null 
            ? request.getSearchConfig() 
            : RetrievalConfig.DEFAULT;
        
        List<SearchResult> searchResults = retrievalService.search(
            request.getCollectionId(),
            queryVector,
            retrievalConfig
        );
        
        // 3. 构建上下文
        String context = buildContext(searchResults);
        
        // 4. 构建Prompt
        String prompt = promptManager.buildPrompt(
            request.getQuestion(),
            context,
            request.getConversationHistory()
        );
        
        // 5. 流式生成
        LLMConfig llmConfig = request.getLlmConfig() != null 
            ? request.getLlmConfig() 
            : LLMConfig.builder().build();
        
        // 先发送source信息
        Flux<StreamResponse> sourceFlux = Flux.just(
            searchResults.stream()
                .map(this::convertToStreamSource)
                .collect(Collectors.toList())
        ).map(StreamResponse::source);
        
        // 再发送生成的内容
        Flux<StreamResponse> contentFlux = llmService.generateStream(prompt, llmConfig)
            .map(content -> StreamResponse.builder()
                .type("token")
                .content(content)
                .build());
        
        return Flux.concat(sourceFlux, contentFlux);
    }
    
    /**
     * 构建上下文
     */
    private String buildContext(List<SearchResult> searchResults) {
        if (searchResults.isEmpty()) {
            return "没有找到相关的文档信息。";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("参考文档：\n\n");
        
        for (int i = 0; i < searchResults.size(); i++) {
            SearchResult result = searchResults.get(i);
            context.append(String.format("[文档%d]\n", i + 1));
            context.append(result.getContent());
            context.append("\n\n");
        }
        
        return context.toString();
    }
    
    /**
     * 转换Source
     */
    private Source convertToSource(SearchResult result) {
        return Source.builder()
            .documentId(result.getDocumentId())
            .documentTitle(result.getDocumentTitle())
            .chunkContent(result.getContent())
            .score(result.getScore())
            .metadata(result.getMetadata())
            .build();
    }
    
    /**
     * 转换流式Source
     */
    private List<StreamSource> convertToStreamSource(SearchResult result) {
        return Collections.singletonList(
            StreamSource.builder()
                .documentId(result.getDocumentId())
                .title(result.getDocumentTitle())
                .score(result.getScore())
                .build()
        );
    }
    
    /**
     * 估算Token数量（简单实现）
     */
    private int estimateTokenCount(String text) {
        // 中文约1.5字符=1token，英文约4字符=1token
        // 这里使用简化估算
        return (int) Math.ceil(text.length() / 2.0);
    }
}

// Prompt模板管理
@Service
@Slf4j
public class PromptTemplateManager {
    
    private final Map<String, String> templates;
    
    public PromptTemplateManager(
            @Value("${rag.prompt.template:default}") String templateName) {
        this.templates = loadTemplates();
    }
    
    public String buildPrompt(String question, String context, List<Message> history) {
        StringBuilder prompt = new StringBuilder();
        
        // 添加系统提示
        prompt.append("你是一个专业的知识库助手，请根据提供的参考文档回答用户的问题。\n\n");
        
        // 添加对话历史
        if (history != null && !history.isEmpty()) {
            prompt.append("对话历史：\n");
            for (Message msg : history) {
                prompt.append(String.format("%s: %s\n", 
                    msg.getRole().equals("user") ? "用户" : "助手",
                    msg.getContent()
                ));
            }
            prompt.append("\n");
        }
        
        // 添加上下文
        prompt.append(context).append("\n\n");
        
        // 添加问题
        prompt.append("请根据上述参考文档回答以下问题：\n");
        prompt.append(question).append("\n\n");
        
        // 添加回答要求
        prompt.append("回答要求：\n");
        prompt.append("1. 如果参考文档中有答案，请基于文档内容回答\n");
        prompt.append("2. 如果文档中没有相关信息，请说明\"我没有找到相关信息\"\n");
        prompt.append("3. 回答要简洁明了，有条理\n");
        prompt.append("4. 如果需要，可以在回答中引用相关文档\n");
        
        return prompt.toString();
    }
    
    private Map<String, String> loadTemplates() {
        Map<String, String> templates = new HashMap<>();
        
        // 默认模板
        templates.put("default", 
            "你是一个专业的知识库助手，请根据提供的参考文档回答用户的问题。\n\n" +
            "{context}\n\n" +
            "请根据上述参考文档回答以下问题：\n" +
            "{question}\n\n" +
            "回答要求：\n" +
            "1. 基于文档内容回答\n" +
            "2. 简洁明了\n" +
            "3. 如无相关信息，说明不知道"
        );
        
        // 详细回答模板
        templates.put("detailed",
            "你是一个专业的知识库助手，擅长从文档中提取信息并详细解答。\n\n" +
            "参考文档：\n{context}\n\n" +
            "用户问题：{question}\n\n" +
            "请详细回答用户的问题，并标注信息来源。"
        );
        
        // 简洁回答模板
        templates.put("concise",
            "基于以下文档：\n{context}\n\n" +
            "问题：{question}\n\n" +
            "简短回答："
        );
        
        return templates;
    }
}
```

---

## 八、部署方案设计

### 8.1 基础设施配置

#### 8.1.1 Docker Compose配置

```yaml
version: '3.8'

services:
  # PostgreSQL数据库
  postgres:
    image: postgres:15-alpine
    container_name: rag-postgres
    environment:
      POSTGRES_DB: ${DB_NAME:-rag_knowledge_base}
      POSTGRES_USER: ${DB_USERNAME:-postgres}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./init-db.sql:/docker-entrypoint-initdb.d/init-db.sql
    networks:
      - rag-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Redis缓存
  redis:
    image: redis:7-alpine
    container_name: rag-redis
    command: redis-server --appendonly yes
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - rag-network
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # etcd配置中心
  etcd:
    image: quay.io/coreos/etcd:v3.5
    container_name: rag-etcd
    environment:
      ETCD_NAME: etcd
      ETCD_ADVERTISE_CLIENT_URLS: http://0.0.0.0:2379
      ETCD_LISTEN_CLIENT_URLS: http://0.0.0.0:2379
      ETCD_INITIAL_CLUSTER_STATE: new
    ports:
      - "2379:2379"
    volumes:
      - etcd-data:/etcd
    networks:
      - rag-network
    healthcheck:
      test: ["CMD", "etcdctl", "endpoint", "health"]
      interval: 10s
      timeout: 5s
      retries: 5

  # MinIO对象存储
  minio:
    image: minio/minio:latest
    container_name: rag-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ACCESS_KEY}
      MINIO_ROOT_PASSWORD: ${MINIO_SECRET_KEY}
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio-data:/data
    networks:
      - rag-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3

  # Qdrant向量数据库
  qdrant:
    image: qdrant/qdrant:v1.7
    container_name: rag-qdrant
    ports:
      - "6333:6333"
      - "6334:6334"
    volumes:
      - qdrant-data:/qdrant/storage
    networks:
      - rag-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:6333/health"]
      interval: 10s
      timeout: 5s
      retries: 5

  # 后端API服务
  rag-api:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: rag-api
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: ${DB_NAME:-rag_knowledge_base}
      DB_USERNAME: ${DB_USERNAME:-postgres}
      DB_PASSWORD: ${DB_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      ETCD_HOST: etcd
      ETCD_PORT: 2379
      QDRANT_HOST: qdrant
      QDRANT_PORT: 6333
      MINIO_ENDPOINT: http://minio:9000
      MINIO_ACCESS_KEY: ${MINIO_ACCESS_KEY}
      MINIO_SECRET_KEY: ${MINIO_SECRET_KEY}
      EMBEDDING_SERVICE_URL: ${EMBEDDING_SERVICE_URL:-http://embedding-service:8081}
      LLM_BASE_URL: ${LLM_BASE_URL:-http://llm-service:8082}
      LLM_MODEL: ${LLM_MODEL:-qwen2.5-7b-instruct}
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      etcd:
        condition: service_healthy
      qdrant:
        condition: service_healthy
      minio:
        condition: service_healthy
    networks:
      - rag-network
    restart: unless-stopped

  # Embedding服务
  embedding-service:
    build:
      context: ./embedding-service
      dockerfile: Dockerfile
    container_name: embedding-service
    environment:
      MODEL_NAME: ${EMBEDDING_MODEL:-BAAI/bge-large-zh-v1.5}
      DEVICE: ${EMBEDDING_DEVICE:-cuda}
      MAX_BATCH_SIZE: ${EMBEDDING_BATCH_SIZE:-32}
      MAX_SEQ_LENGTH: ${EMBEDDING_MAX_SEQ_LENGTH:-512}
    ports:
      - "8081:8081"
    volumes:
      - embedding-models:/app/models
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu]
    networks:
      - rag-network
    restart: unless-stopped

  # LLM服务（vLLM）
  llm-service:
    image: vllm/vllm-openai:latest
    container_name: llm-service
    command: >
      python -m vllm.entrypoints.openai.api_server
      --model ${LLM_MODEL:-Qwen/Qwen2.5-7B-Instruct-GPTQ}
      --tensor-parallel-size 1
      --port 8082
      --gpu-memory-utilization 0.9
    environment:
      MODEL_NAME: ${LLM_MODEL:-Qwen/Qwen2.5-7B-Instruct-GPTQ}
    ports:
      - "8082:8082"
    volumes:
      - vllm-models:/root/.cache/huggingface
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu]
    networks:
      - rag-network
    restart: unless-stopped

  # Nginx反向代理
  nginx:
    image: nginx:alpine
    container_name: rag-nginx
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/ssl:/etc/nginx/ssl:ro
    depends_on:
      - rag-api
    networks:
      - rag-network
    restart: unless-stopped

  # Prometheus监控
  prometheus:
    image: prom/prometheus:latest
    container_name: rag-prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    networks:
      - rag-network
    restart: unless-stopped

  # Grafana监控面板
  grafana:
    image: grafana/grafana:latest
    container_name: rag-grafana
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:-admin}
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
    depends_on:
      - prometheus
    networks:
      - rag-network
    restart: unless-stopped

volumes:
  postgres-data:
  redis-data:
  etcd-data:
  minio-data:
  qdrant-data:
  embedding-models:
  vllm-models:
  prometheus-data:
  grafana-data:

networks:
  rag-network:
    driver: bridge
```

#### 8.1.2 Nginx配置

```nginx
# nginx.conf
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /var/run/nginx.pid;

events {
    worker_connections 1024;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;
    
    # 日志格式
    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent" "$http_x_forwarded_for" '
                    'rt=$request_time uct="$upstream_connect_time" '
                    'uht="$upstream_header_time" urt="$upstream_response_time"';
    
    access_log /var/log/nginx/access.log main;
    
    # 性能优化
    sendfile on;
    tcp_nopush on;
    tcp_nodelay on;
    keepalive_timeout 65;
    types_hash_max_size 2048;
    
    # Gzip压缩
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml;
    
    # 上传大小限制
    client_max_body_size 100m;
    
    # 限流
    limit_req_zone $binary_remote_addr zone=api:10m rate=100r/s;
    limit_req_zone $binary_remote_addr zone=upload:10m rate=10r/m;
    
    upstream rag_api {
        server rag-api:8080;
        keepalive 32;
    }
    
    server {
        listen 80;
        server_name _;
        
        # 重定向到HTTPS（生产环境）
        # return 301 https://$host$request_uri;
        
        location / {
            root /usr/share/nginx/html;
            index index.html;
            try_files $uri $uri/ /index.html;
        }
        
        # API代理
        location /api/ {
            limit_req zone=api burst=20 nodelay;
            
            proxy_pass http://rag_api;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            
            # 超时设置
            proxy_connect_timeout 60s;
            proxy_send_timeout 60s;
            proxy_read_timeout 60s;
            
            # SSE支持
            proxy_buffering off;
            chunked_transfer_encoding on;
        }
        
        # 文件上传
        location /api/documents/upload {
            limit_req zone=upload burst=5 nodelay;
            
            client_max_body_size 100m;
            proxy_pass http://rag_api;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_read_timeout 300s;
        }
        
        # SSE流式响应
        location /api/rag/query/stream {
            proxy_pass http://rag_api;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            
            proxy_buffering off;
            chunked_transfer_encoding on;
            proxy_cache off;
            
            proxy_read_timeout 300s;
        }
        
        # 健康检查
        location /health {
            proxy_pass http://rag_api;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
        }
        
        # 监控端点
        location /actuator/prometheus {
            proxy_pass http://rag_api;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
        }
        
        # Swagger文档
        location /swagger-ui/ {
            proxy_pass http://rag_api;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
        }
        
        location /v3/api-docs/ {
            proxy_pass http://rag_api;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
        }
    }
}
```

### 8.2 监控配置

#### 8.2.1 Prometheus配置

```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

alerting:
  alertmanagers:
    - static_configs:
        - targets: []

rule_files: []

scrape_configs:
  # Prometheus自身
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  # RAG API服务
  - job_name: 'rag-api'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['rag-api:8080']
    relabel_configs:
      - source_labels: [__address__]
        target_label: instance
        regex: '([^:]+):\d+'
        replacement: '${1}'

  # Embedding服务
  - job_name: 'embedding-service'
    static_configs:
      - targets: ['embedding-service:8081']

  # LLM服务
  - job_name: 'llm-service'
    static_configs:
      - targets: ['llm-service:8082']

  # Qdrant
  - job_name: 'qdrant'
    static_configs:
      - targets: ['qdrant:6333']

  # Redis
  - job_name: 'redis'
    static_configs:
      - targets: ['redis:6379']

  # PostgreSQL（需要Exporter）
  - job_name: 'postgres'
    static_configs:
      - targets: ['postgres-exporter:9187']
```

#### 8.2.2 告警规则

```yaml
# alert_rules.yml
groups:
  - name: rag_system
    rules:
      # API服务告警
      - alert: RAGAPI Down
        expr: up{job="rag-api"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "RAG API服务不可用"
          description: "RAG API服务已停止运行超过1分钟"
      
      - alert: RAGAPI HighLatency
        expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{job="rag-api"}[5m])) > 2
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "RAG API响应延迟过高"
          description: "API响应时间P95超过2秒，当前值: {{ $value }}秒"
      
      # Embedding服务告警
      - alert: EmbeddingService Down
        expr: up{job="embedding-service"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Embedding服务不可用"
      
      - alert: EmbeddingLatencyHigh
        expr: histogram_quantile(0.95, rate(embedding_request_duration_seconds_bucket[5m])) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Embedding延迟过高"
          description: "Embedding服务P95延迟超过100ms"
      
      # LLM服务告警
      - alert: LLMService Down
        expr: up{job="llm-service"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "LLM服务不可用"
      
      - alert: LLMService HighErrorRate
        expr: rate(llm_request_errors_total[5m]) / rate(llm_requests_total[5m]) > 0.01
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "LLM服务错误率过高"
          description: "LLM服务5分钟错误率超过1%"
      
      # 资源告警
      - alert: HighCPUUsage
        expr: 100 - (avg by(instance)(rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100) > 80
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "CPU使用率过高"
          description: "服务器CPU使用率超过80%"
      
      - alert: HighMemoryUsage
        expr: (1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100 > 85
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "内存使用率过高"
          description: "服务器内存使用率超过85%"
      
      - alert: HighDiskUsage
        expr: (1 - (node_filesystem_avail_bytes / node_filesystem_size_bytes)) * 100 > 90
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "磁盘使用率过高"
          description: "磁盘使用率超过90%"
```

---

## 第三部分内容预告

HLD文档第三部分将包含：
- 实施计划（按工作日详细规划）
- 风险评估与缓解措施
- 验收标准
- 成本估算
- 项目管理流程
- 团队配置建议
- 附录（技术文档链接、术语表等）

完整文档将在最终合并版本中提供。
