export * from './enums'

export interface Result<T = any> {
  code: number
  message: string
  data: T
  timestamp: string
}

export type CollectionType =
  | 'KNOWLEDGE_BASE'
  | 'QA_PAIRS'
  | 'DOCUMENT_SUMMARY'
  | 'TECHNICAL_DOCUMENT'
  | 'PRODUCT_MANUAL'
  | 'INTERNAL_KNOWLEDGE'

/**
 * 文档处理策略
 * MANUAL：说明书类结构化短文档，强制不切片，整篇存储，必须OCR
 * NORMAL：普通文档，使用语义分块
 */
export type DocType = 'MANUAL' | 'NORMAL'

export const DocTypeLabels: Record<DocType, string> = {
  'MANUAL': '说明书（结构化短文档）',
  'NORMAL': '普通文档'
}

export type CollectionStatus =
  | 'ACTIVE'
  | 'PAUSED'
  | 'ARCHIVED'
  | 'DELETED'
  | 'CREATING'
  | 'ERROR'
  | 'INACTIVE'

export interface RetrievalConfig {
  topK?: number
  similarityThreshold?: number
  rerankEnabled?: boolean
  rerankModel?: string
  queryExpansionEnabled?: boolean
}

export interface GenerationConfig {
  maxTokens?: number
  temperature?: number
  topP?: number
  streamEnabled?: boolean
  systemPrompt?: string
}

export type ChunkingStrategy = 'CHARACTER' | 'SENTENCE' | 'PARAGRAPH' | 'SEMANTIC' | 'FIXED' | 'NONE'

export interface ChunkingConfig {
  strategy?: ChunkingStrategy
  chunkSize?: number
  chunkOverlap?: number
  minChunkLength?: number
}

export type IndexType = 'FLAT' | 'IVF' | 'HNSW'

export interface VectorConfig {
  embeddingModel?: string
  vectorDimension?: number
  indexType?: IndexType
}

export interface CollectionConfig {
  retrieval?: RetrievalConfig
  generation?: GenerationConfig
  chunking?: ChunkingConfig
  vector?: VectorConfig
}

export interface MultiCollection extends Omit<Collection, 'status'> {
  type?: CollectionType
  status?: CollectionStatus
  config?: CollectionConfig
  /** 文档处理策略：MANUAL（说明书）/ NORMAL（普通文档） */
  docType?: DocType
  /** 字段模板配置（仅MANUAL类型使用） */
  sectionTemplate?: SectionTemplate[]
  departmentId?: string
  createdBy?: string
  deletedAt?: string
}

/**
 * 字段模板配置（用于说明书类文档的国标字段提取）
 */
export interface SectionTemplate {
  field: string      // 字段名，如 indications, dosage
  label: string      // 显示标签，如 适应症、用法用量
  required?: boolean // 是否必填
}

/**
 * 创建知识库请求
 */
export interface CollectionCreateRequest {
  name: string
  description?: string
  type?: CollectionType
  /** 文档处理策略 */
  docType?: DocType
  /** 字段模板（MANUAL类型必填） */
  sectionTemplate?: SectionTemplate[]
  config?: CollectionConfig
}

/**
 * 更新知识库请求
 */
export interface CollectionUpdateRequest {
  name?: string
  description?: string
  status?: CollectionStatus
  type?: CollectionType
  /** 文档处理策略 */
  docType?: DocType
  /** 字段模板 */
  sectionTemplate?: SectionTemplate[]
  config?: CollectionConfig
}

export interface CollectionDocument {
  id: string
  collectionId: string
  documentId: string
  documentName?: string
  documentType?: string
  departmentId?: string
  addedBy?: string
  addedByName?: string
  addedAt: string
  processingStatus: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  processingPriority?: number
  processingError?: string
  processedAt?: string
}

export interface CollectionMetrics {
  id: string
  collectionId: string
  departmentId?: string
  metricType: 'QUERY_COUNT' | 'LATENCY_P50' | 'LATENCY_P95' | 'LATENCY_P99' | 'ERROR_RATE' | 'RETRIEVAL_RECALL' | 'TOKEN_USAGE' | 'USER_COUNT'
  metricValue: number
  sampleSize?: number
  dimensions?: Record<string, any>
  recordedAt: string
}

export interface ChatSession {
  id: string
  title: string
  userId: string
  departmentId?: string
  collectionIds: string[]
  status: 'ACTIVE' | 'CLOSED'
  messageCount: number
  createdAt: string
  updatedAt: string
  lastMessageAt?: string
}

export interface ChatMessage {
  id: string
  sessionId: string
  departmentId?: string
  role: 'user' | 'assistant' | 'system'
  content: string
  reasoning?: string  // 思考过程内容
  sources?: SearchSource[]
  queryParams?: QueryParams
  modelInfo?: ModelInfo
  tokenUsage?: TokenUsage
  generationTimeMs?: number
  createdAt: string
}

export interface SearchSource {
  documentId: string
  title: string
  snippet: string
  score?: number
  similarity?: number
  collectionId?: string
  collectionName?: string
  pageNumber?: number
  chunkIndex?: number
  startPage?: number  // 起始页
  endPage?: number    // 结束页
}

export interface QueryParams {
  topK?: number
  similarityThreshold?: number
  noExpansion?: boolean
  collectionIds?: string[]
}

export interface ModelInfo {
  modelName: string
  provider: string
  version?: string
}

export interface TokenUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

export interface StreamEvent {
  type: 'token' | 'reasoning' | 'source' | 'rewrite' | 'done' | 'error'
  content?: string
  reasoning?: string  // 思考过程内容（type为reasoning时使用）
  rewrittenQuery?: string  // 重写后的query（type为rewrite时使用）
  documentId?: string
  title?: string
  snippet?: string
  score?: number
  collectionId?: string
  messageId?: string
  tokensUsed?: number
  generationTimeMs?: number
  message?: string
  startPage?: number    // 起始页
  endPage?: number      // 结束页
}

export interface ChatQueryRequest {
  sessionId?: string
  query: string
  collectionIds: string[]
  noExpansion?: boolean
  topK?: number
  similarityThreshold?: number
  // LLM 控制参数
  temperature?: number
  topP?: number
  maxTokens?: number
  repetitionPenalty?: number
  presencePenalty?: number
  thinkingEnabled?: boolean
  /** 思考过程最大token预算，默认使用服务端配置 */
  thinkingMaxTokens?: number
  // 是否启用会话历史
  enableHistory?: boolean
}

export interface ChatQueryResponse {
  answer: string
  sources?: SearchSource[]
  tokenUsage?: TokenUsage
  generationTimeMs?: number
  usedExpansion?: boolean
  tokensUsed?: number
  messageId?: string
}

export interface CreateSessionRequest {
  title?: string
  collectionIds: string[]
}

export interface CollectionSelectorOption {
  id: string
  name: string
  type: CollectionType
  documentCount: number
  status: CollectionStatus
  hasPermission: boolean
  description?: string
}

export const CollectionTypeLabels: Record<CollectionType, string> = {
  'KNOWLEDGE_BASE': '知识库',
  'QA_PAIRS': '问答对',
  'DOCUMENT_SUMMARY': '文档摘要',
  'TECHNICAL_DOCUMENT': '技术文档',
  'PRODUCT_MANUAL': '产品手册',
  'INTERNAL_KNOWLEDGE': '内部知识'
}

export const CollectionStatusLabels: Record<CollectionStatus, string> = {
  'ACTIVE': '活跃',
  'PAUSED': '暂停',
  'ARCHIVED': '归档',
  'DELETED': '已删除',
  'CREATING': '创建中',
  'ERROR': '异常',
  'INACTIVE': '禁用'
}

export const ChunkingStrategyLabels: Record<ChunkingStrategy, string> = {
  'CHARACTER': '字符分块',
  'SENTENCE': '句子分块',
  'PARAGRAPH': '段落分块',
  'SEMANTIC': '语义分块',
  'FIXED': '固定长度分块',
  'NONE': '不分块'
}

export const IndexTypeLabels: Record<IndexType, string> = {
  'FLAT': '暴力检索',
  'IVF': '倒排索引',
  'HNSW': '分层可导航小世界图'
}

export interface User {
  id: string
  username: string
  email: string
  role: 'SYSTEM_ADMIN' | 'DEPT_ADMIN' | 'DEPT_UPLOADER' | 'DEPT_USER'
  status: 'ACTIVE' | 'DISABLED' | 'LOCKED'
  createdAt: string
  updatedAt: string
  roles?: UserRole[]
}

export interface UserRole {
  id: string
  code: string
  name: string
  level: number
  isPrimary: boolean
  roleId?: string
  assignedAt?: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  email: string
}

export interface Collection {
  id: string
  name: string
  description: string
  embeddingModel?: string
  vectorDimension?: number
  documentCount: number
  chunkCount: number
  totalSize?: number
  createdAt: string
  updatedAt: string
  status: 'ACTIVE' | 'INACTIVE'
}

export interface Document {
  id: string
  name: string
  type: 'PDF' | 'WORD' | 'MARKDOWN' | 'TXT'
  size: number
  status: 'UPLOADING' | 'PENDING' | 'PARSING' | 'CHUNKING' | 'VECTORIZING' | 'COMPLETED' | 'FAILED'
  progress: number
  collectionId: string
  collectionName: string
  /** 实际存储的向量数量（向量化成功后更新） */
  chunkCount: number
  /** 解析阶段预期的分块数量（parse成功后设置，与实际存储数量可能不同） */
  expectedChunkCount?: number
  errorMessage?: string
  createdAt: string
  updatedAt: string
}

export interface Conversation {
  id: string
  title: string
  collectionId: string
  collectionName: string
  messageCount: number
  lastMessageAt: string
  createdAt: string
}

export interface Message {
  id: string
  conversationId: string
  role: 'user' | 'assistant'
  content: string
  citations?: Citation[]
  createdAt: string
}

export interface Citation {
  documentId: string
  documentName: string
  chunkContent: string
  similarity: number
  pageNumber?: number
}

export interface AuditLog {
  id: string
  userId: string
  username: string
  displayName?: string
  action: string
  actionDescription?: string
  level?: string
  levelDescription?: string
  resourceType?: string
  resourceId?: string
  description?: string
  ipAddress: string
  userAgent?: string
  details?: string
  status: 'success' | 'failed'
  timestamp: string
}

export interface HealthStatus {
  status: 'UP' | 'DOWN' | 'DEGRADED'
  services: {
    [key: string]: {
      status: string
      message: string
      responseTime: number
    }
  }
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export interface QueryRequest {
  question: string
  collectionId: string
  conversationId?: string
  topK?: number
  similarityThreshold?: number
  stream?: boolean
}

export type PermissionType = 'COMPANY_WIDE' | 'DEPARTMENT_WIDE' | 'RESTRICTED' | 'DEPARTMENT_WIDE_RESTRICTED'
export type PermissionLevel = 'READ' | 'COMMENT' | 'EDIT' | 'FULL'
export type OperationType = 'GRANT' | 'REVOKE' | 'UPDATE'
export type TargetType = 'USER' | 'ROLE' | 'DEPARTMENT' | 'DOCUMENT'

export type CollectionPermissionType = 'PUBLIC' | 'DEPARTMENT_WIDE' | 'RESTRICTED' | 'DEPARTMENT_WIDE_RESTRICTED'
export type CollectionPermissionLevel = 'NONE' | 'READ' | 'EDIT' | 'MANAGE' | 'ADMIN'

export interface CollectionPermission {
  id: string
  collectionId: string
  collectionName: string
  ownerId: string
  ownerName: string
  permissionType: CollectionPermissionType
  permissionTypeDescription?: string
  grantedBy: string
  granterName?: string
  grantedAt?: string
  updatedAt?: string
  allowedUsers?: CollectionAllowedUser[]
}

export interface CollectionAllowedUser {
  userId: string
  username: string
  displayName: string
  permissionLevel: CollectionPermissionLevel
  expiresAt?: string
}

export interface CollectionAccessCheckResult {
  hasAccess: boolean
  permissionLevel?: CollectionPermissionLevel
  reason?: string
}

export const CollectionPermissionTypeLabels: Record<CollectionPermissionType, string> = {
  'PUBLIC': '公开访问',
  'DEPARTMENT_WIDE': '部门内公开',
  'RESTRICTED': '指定人员开放',
  'DEPARTMENT_WIDE_RESTRICTED': '部门全员+指定人员'
}

export const CollectionPermissionLevelLabels: Record<CollectionPermissionLevel, string> = {
  'NONE': '无权限',
  'READ': '查看',
  'EDIT': '编辑',
  'MANAGE': '管理',
  'ADMIN': '管理员'
}

export interface Department {
  id: string
  code: string
  name: string
  parentId: string | null
  managerId?: string
  managerName?: string
  description?: string
  status: 'ACTIVE' | 'INACTIVE'
  userCount?: number
  children?: Department[]
  createdAt: string
  updatedAt: string
}

export interface CollectionStats {
  name: string
  vectorsCount: number
  pointsCount: number
  indexedVectorsCount: number
  status: string
  createdAt: number
}

export interface VectorSearchRequest {
  vector: number[]
  limit?: number
  scoreThreshold?: number
  ef?: number
}

export interface VectorSearchResult {
  id: string
  score: number
  payload: Record<string, unknown>
}

export interface VectorBatchItem {
  vector: number[]
  payload?: Record<string, unknown>
  pointId?: string
}

export interface BatchInsertResult {
  totalCount: number
  successCount: number
  failedCount: number
  failedPointIds: string[]
  durationMs: number
}

export type BatchStatus = 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

export interface ResumeState {
  batchId: string
  collectionName: string
  currentBatchIndex: number
  totalBatches: number
  processedItems: number
  totalItems: number
  status: BatchStatus
  startTimeMs: number
  lastUpdatedMs: number
  processedPointIds: string[]
  errorMessage?: string
}

export const BatchStatusLabels: Record<BatchStatus, string> = {
  'RUNNING': '运行中',
  'PAUSED': '已暂停',
  'COMPLETED': '已完成',
  'FAILED': '失败',
  'CANCELLED': '已取消'
}

export const BatchStatusTypeMap: Record<BatchStatus, string> = {
  'RUNNING': 'primary',
  'PAUSED': 'warning',
  'COMPLETED': 'success',
  'FAILED': 'danger',
  'CANCELLED': 'info'
}

export interface UserDepartment {
  id: string
  userId: string
  username: string
  email: string
  departmentId: string
  departmentName: string
  isPrimary: boolean
  position?: string
  joinedAt: string
}

export interface DocumentPermission {
  id: string
  documentId: string
  documentName: string
  ownerId: string
  ownerName: string
  permissionType: PermissionType
  allowedUsers?: AllowedUser[]
  createdAt: string
  updatedAt: string
}

export interface AllowedUser {
  userId: string
  username: string
  email: string
  departmentName: string
  permissionLevel: PermissionLevel
  expiresAt?: string
  grantedAt: string
}

export interface AccessCheckResult {
  hasAccess: boolean
  permissionLevel?: PermissionLevel
  reason?: string
}

export interface BatchAccessCheckResult {
  results: Array<{
    documentId: string
    hasAccess: boolean
  }>
}

export interface PermissionAuditLog {
  id: string
  operatorId: string
  operatorName: string
  operatorUsername: string
  operationType: OperationType
  targetType: TargetType
  targetId: string
  targetName: string
  oldValue?: Record<string, any>
  newValue?: Record<string, any>
  ipAddress: string
  userAgent?: string
  createdAt: string
}

export interface Role {
  id: string
  code: string
  name: string
  level: number
  permissions: string
  description?: string
  active: boolean
  userCount?: number
  createdAt: string
  updatedAt: string
}

export type RoleLevel = 1 | 2 | 3 | 4

export interface RolePermission {
  id: string
  code: string
  name: string
  description: string
  category: string
}

export interface EffectivePermission {
  hasReadAccess: boolean
  hasCommentAccess: boolean
  hasEditAccess: boolean
  hasDeleteAccess: boolean
  hasManageAccess: boolean
}

export interface ProcessStatusDTO {
  documentId: string
  uploadStatus: string
  parseStatus: string
  chunkStatus: string
  vectorStatus?: string
  progress: number
  currentStep: string
  startTime?: string
  estimatedEndTime?: string
  errorMessage?: string
}

export interface DocumentProcessDTO {
  documentId: string
  documentName?: string
  fileType?: string
  fileSize?: number
  parseResult?: ParseResultDTO
  chunkPreview?: ChunkPreviewDTO
  originalText?: string
  tempFilePath?: string
}

export interface ParseResultDTO {
  status: string
  pageCount?: number
  parsedPageCount?: number
  charCount?: number
  originalText?: string
  parsedPages?: PageDTO[]
  warnings?: WarningDTO[]
  errorMessage?: string
  parsedAt?: string
}

export interface PageDTO {
  pageNum: number
  text: string
  charCount: number
}

export interface WarningDTO {
  page?: number
  message: string
}

export interface ChunkPreviewDTO {
  totalChars?: number
  strategy?: string
  config?: ChunkConfigDTO
  chunkCount?: number
  chunks?: ChunkDTO[]
  previewAt?: string
}

export interface ChunkConfigDTO {
  chunkSize?: number
  overlap?: number
}

export interface ChunkDTO {
  index: number
  text: string
  startPos: number
  endPos: number
  sourcePages?: number[]
}

export interface DocumentTextDTO {
  documentId: string
  text?: string
  charCount?: number
  truncated?: boolean
  originalLength?: number
  message?: string
}

export type DocumentChunkingStrategy = 'fixed' | 'paragraph' | 'semantic' | 'character' | 'none'

export const DocumentChunkingStrategyLabels: Record<DocumentChunkingStrategy, string> = {
  'fixed': '固定长度分块',
  'paragraph': '段落分块',
  'semantic': '语义分块',
  'character': '字符分块',
  'none': '不分块'
}

export const ProcessStatusLabels: Record<string, string> = {
  'pending': '待处理',
  'uploading': '上传中',
  'upload_completed': '上传完成',
  'parsing': '解析中',
  'parse_completed': '解析完成',
  'ready_to_chunk': '准备分块',
  'chunking': '分块中',
  'chunk_completed': '分块完成',
  'completed': '处理完成',
  'failed': '处理失败',
  'parse_failed': '解析失败',
  'chunk_failed': '分块失败'
}

export const ProcessingStepLabels: Record<string, string> = {
  'pending': '等待处理',
  'uploading': '文件上传中',
  'parsing': '文档解析中',
  'ready_to_chunk': '准备分块',
  'chunking': '文本分块中',
  'completed': '处理完成',
  'failed': '处理失败',
  'parse_failed': '解析失败',
  'chunk_failed': '分块失败'
}
