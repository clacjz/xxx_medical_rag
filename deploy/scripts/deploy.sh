#!/bin/bash

# ============================================
# RAG Medical 统一部署脚本
# 支持三种场景: local-docker, dev, prod
# ============================================

set -e

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 场景配置
declare -A SCENARIOS=(
    ["local-docker"]="backend/docker:本地Docker完整环境(前后端+中间件都在本地)"
    ["dev"]="deploy/dev:开发环境(后端在服务器，前端在本地npm)"
    ["prod"]="deploy/prod:生产环境(全部在服务器Docker)"
)

declare -A ENV_FILES=(
    ["local-docker"]="backend/docker/.env.local"
    ["dev"]="deploy/dev/.env.dev"
    ["prod"]="deploy/prod/.env.prod"
)

declare -A COMPOSE_FILES=(
    ["local-docker"]="backend/docker/docker-compose.yml"
    ["dev"]="deploy/dev/docker-compose.yml"
    ["prod"]="deploy/prod/docker-compose.yml"
)

declare -A BACKEND_URLS=(
    ["local-docker"]="http://localhost:18080"
    ["dev"]="http://localhost:18080"
    ["prod"]="http://localhost:18080"
)

declare -A FRONTEND_URLS=(
    ["local-docker"]="http://localhost:18030"
    ["dev"]="http://localhost:5173"
    ["prod"]="http://localhost:18030"
)

echo_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

echo_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

echo_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

echo_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# 显示使用帮助
show_help() {
    echo "=========================================="
    echo "RAG Medical 统一部署脚本"
    echo "=========================================="
    echo ""
    echo "用法: $0 <scenario> <command>"
    echo ""
    echo "场景 (必须指定):"
    for key in "${!SCENARIOS[@]}"; do
        echo "  ${key}  - ${SCENARIOS[$key]}"
    done
    echo ""
    echo "命令:"
    echo "  start         启动服务"
    echo "  stop          停止服务"
    echo "  restart       重启服务"
    echo "  logs [svc]    查看日志 (可选: 指定服务名)"
    echo "  ps            查看服务状态"
    echo "  status        检查服务健康状态"
    echo "  build         构建镜像 (仅 prod/dev)"
    echo "  clean         清理未使用的镜像"
    echo ""
    echo "示例:"
    echo "  $0 local-docker start"
    echo "  $0 dev logs backend"
    echo "  $0 prod status"
    echo ""
}

# 检查场景是否有效
validate_scenario() {
    local scenario=$1
    if [[ -z "${SCENARIOS[$scenario]}" ]]; then
        echo_error "无效的场景: $scenario"
        echo "可选场景: ${!SCENARIOS[@]}"
        exit 1
    fi
}

# 检查前置条件
check_prerequisites() {
    local scenario=$1

    if ! command -v docker &> /dev/null; then
        echo_error "Docker 未安装"
        exit 1
    fi

    if ! docker info &> /dev/null; then
        echo_error "Docker 未运行"
        exit 1
    fi

    local env_file="${ENV_FILES[$scenario]}"
    if [[ ! -f "$env_file" ]]; then
        echo_error "环境配置文件不存在: $env_file"
        exit 1
    fi
}

# 获取 docker-compose 命令
get_compose_cmd() {
    local scenario=$1
    local compose_file="${COMPOSE_FILES[$scenario]}"
    echo "docker-compose --env-file ${ENV_FILES[$scenario]} -f $compose_file"
}

# 启动服务
do_start() {
    local scenario=$1
    validate_scenario $scenario

    echo_step "启动场景: $scenario - ${SCENARIOS[$scenario]}"

    # 本地docker场景需要先启动基础设施
    if [[ "$scenario" == "local-docker" ]]; then
        echo_step "第1步: 启动基础设施服务 (PostgreSQL, Redis, Qdrant, MinIO, etcd, ES)..."
        local infra_cmd=$(get_compose_cmd $scenario)
        $infra_cmd up -d postgres redis qdrant minio etcd elasticsearch
        echo_info "等待基础设施服务启动..."
        sleep 10
    fi

    echo_step "第2步: 启动应用服务..."
    local compose_cmd=$(get_compose_cmd $scenario)
    $compose_cmd up -d

    echo_info "服务启动完成!"
    echo ""
    echo "=========================================="
    echo "访问地址:"
    echo "  后端API: ${BACKEND_URLS[$scenario]}"
    echo "  前端:    ${FRONTEND_URLS[$scenario]}"
    echo "  Prometheus: http://localhost:19090"
    echo "  Grafana:    http://localhost:13000"
    echo "=========================================="
}

# 停止服务
do_stop() {
    local scenario=$1
    validate_scenario $scenario

    echo_step "停止场景: $scenario"
    local compose_cmd=$(get_compose_cmd $scenario)
    $compose_cmd down

    echo_info "服务已停止"
}

# 重启服务
do_restart() {
    local scenario=$1
    validate_scenario $scenario
    do_stop $scenario
    sleep 2
    do_start $scenario
}

# 查看日志
do_logs() {
    local scenario=$1
    local service=$2
    validate_scenario $scenario

    local compose_cmd=$(get_compose_cmd $scenario)
    if [[ -n "$service" ]]; then
        $compose_cmd logs -f $service
    else
        $compose_cmd logs -f
    fi
}

# 查看服务状态
do_ps() {
    local scenario=$1
    validate_scenario $scenario

    local compose_cmd=$(get_compose_cmd $scenario)
    $compose_cmd ps
}

# 检查服务健康状态
do_status() {
    local scenario=$1
    validate_scenario $scenario

    echo_step "检查服务健康状态..."

    local backend_url="${BACKEND_URLS[$scenario]}"
    local health_url="$backend_url/health"

    echo -n "后端健康检查: "
    if curl -s -f "$health_url" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ OK${NC}"
    else
        echo -e "${RED}✗ FAIL${NC}"
    fi

    # 检查各服务状态
    local services=("postgres:PostgreSQL" "redis:Redis" "qdrant:Qdrant" "minio:MinIO" "elasticsearch:Elasticsearch")
    for entry in "${services[@]}"; do
        IFS=':' read -r svc display_name <<< "$entry"
        printf "%-15s " "$display_name:"
        if docker ps --filter "name=rag-$svc" --filter "status=running" 2>/dev/null | grep -q "rag-$svc"; then
            echo -e "${GREEN}✓ OK${NC}"
        else
            echo -e "${RED}✗ FAIL${NC}"
        fi
    done
}

# 构建镜像
do_build() {
    local scenario=$1
    validate_scenario $scenario

    if [[ "$scenario" == "local-docker" ]]; then
        echo_warn "local-docker 场景使用本地构建，不需要单独构建"
        return
    fi

    echo_step "构建镜像..."

    if [[ "$scenario" == "dev" ]] || [[ "$scenario" == "prod" ]]; then
        echo_info "并行构建前后端镜像..."
        docker build -t rag-medical-backend:latest -f backend/Dockerfile backend/ &
        docker build -t rag-medical-frontend:latest -f frontend/Dockerfile frontend/ &
        wait
        echo_info "镜像构建完成:"
        docker images | grep rag-medical
    fi
}

# 清理未使用的镜像
do_clean() {
    echo_step "清理未使用的镜像..."
    docker image prune -f --filter "reference=rag-*:latest" 2>/dev/null || true
    echo_info "清理完成"
}

# 主逻辑
main() {
    if [[ $# -lt 2 ]]; then
        show_help
        exit 1
    fi

    local scenario=$1
    local command=$2
    local param=$3

    validate_scenario $scenario
    check_prerequisites $scenario

    case "$command" in
        start)
            do_start $scenario
            ;;
        stop)
            do_stop $scenario
            ;;
        restart)
            do_restart $scenario
            ;;
        logs)
            do_logs $scenario $param
            ;;
        ps)
            do_ps $scenario
            ;;
        status)
            do_status $scenario
            ;;
        build)
            do_build $scenario
            ;;
        clean)
            do_clean
            ;;
        *)
            echo_error "未知命令: $command"
            show_help
            exit 1
            ;;
    esac
}

main "$@"
