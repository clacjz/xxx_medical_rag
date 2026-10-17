#!/bin/bash

# ============================================
# RAG Medical 构建脚本
# 用于构建前后端Docker镜像
# ============================================

set -e

# 项目根目录与构建产物目录（可通过环境变量覆盖）
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="${BUILD_DIR:-${PROJECT_ROOT}/temp/rag-medical-builds}"

# 服务器地址（部署时通过环境变量传入）
SERVER_IP="${SERVER_IP:-your-server-host}"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

echo_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

echo_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 生成版本号 YYMMDDNN（当日序号，自动递增）
generate_version() {
    local date_prefix=$(date +%y%m%d)
    local version_file="${BUILD_DIR}/.last_version"
    local tar_dir="${BUILD_DIR}"

    mkdir -p "$tar_dir"

    if [ -f "$version_file" ]; then
        local last_version=$(cat "$version_file")
        local last_date=$(echo "$last_version" | cut -c1-6)
        local last_seq=$(echo "$last_version" | cut -c7-8)

        if [ "$last_date" == "$date_prefix" ]; then
            local new_seq=$(printf "%02d" $((10#$last_seq + 1)))
            echo "${date_prefix}${new_seq}"
        else
            echo "${date_prefix}01"
        fi
    else
        echo "${date_prefix}01"
    fi
}

# 保存版本号
save_version() {
    local version=$1
    local version_file="${BUILD_DIR}/.last_version"
    mkdir -p "${BUILD_DIR}"
    echo "$version" > "$version_file"
}

# 构建后端镜像
build_backend() {
    local version=$1
    echo_info "构建后端镜像... 版本: $version"
    docker build --network=host --add-host=maven.aliyun.com:8.8.8.8 \
        -t rag-medical-backend:${version} -t rag-medical-backend:latest \
        -f backend/Dockerfile backend/
    echo_info "后端镜像构建完成"
}

# 构建前端镜像
build_frontend() {
    local version=$1
    echo_info "构建前端镜像... 版本: $version"
    docker build -t rag-medical-frontend:${version} -t rag-medical-frontend:latest -f frontend/Dockerfile frontend/
    echo_info "前端镜像构建完成"
}

# 保存镜像到tar文件
save_images() {
    set -e
    local version=$1
    local tar_dir="${BUILD_DIR}"
    local temp_dir="/tmp/rag-medical-builds"
    mkdir -p "$tar_dir"
    mkdir -p "$temp_dir"

    echo_info "导出镜像到tar文件..."

    # docker save在挂载的NTFS文件系统上会静默失败，先保存到/tmp再复制
    docker save -o ${temp_dir}/rag-medical-backend-${version}.tar rag-medical-backend:${version} rag-medical-backend:latest
    cp ${temp_dir}/rag-medical-backend-${version}.tar "$tar_dir/"
    echo_info "已保存: rag-medical-backend-${version}.tar"

    docker save -o ${temp_dir}/rag-medical-frontend-${version}.tar rag-medical-frontend:${version} rag-medical-frontend:latest
    cp ${temp_dir}/rag-medical-frontend-${version}.tar "$tar_dir/"
    echo_info "已保存: rag-medical-frontend-${version}.tar"

    # 清理临时文件
    rm -f ${temp_dir}/rag-medical-backend-${version}.tar ${temp_dir}/rag-medical-frontend-${version}.tar

    echo ""
    echo_info "所有tar文件已保存到: ${tar_dir}/"
    ls -lh ${tar_dir}/*.tar 2>/dev/null || true
}

# 列出可用的tar包
list_tars() {
    echo "=========================================="
    echo "RAG Medical 可用版本tar包:"
    echo "=========================================="
    ls -lh ${BUILD_DIR}/*.tar 2>/dev/null || echo "暂无tar包，请先执行 $0 build"
}

# 构建所有镜像
build_all() {
    local version=$(generate_version)
    echo_info "=========================================="
    echo_info "开始构建所有镜像... 版本: $version"
    echo_info "=========================================="

    # 同时打版本号标签和latest标签
    build_backend "$version" &
    build_frontend "$version" &
    wait

    # 保存版本号
    save_version "$version"

    # 导出tar
    save_images "$version"

    echo_info "所有镜像构建完成!"
    echo ""
    echo "=========================================="
    echo "版本: $version"
    echo "镜像列表:"
    docker images | grep rag-medical
    echo "=========================================="
    echo ""
    echo "下一步: 传输tar包到服务器"
    echo "  scp ${BUILD_DIR}/rag-medical-*.tar user@${SERVER_IP}:/path/to/"
    echo ""
    echo "在服务器上加载镜像:"
    echo "  docker load -i rag-medical-backend-${version}.tar"
    echo "  docker load -i rag-medical-frontend-${version}.tar"
}

# 仅构建后端
build_backend_only() {
    local version=$(generate_version)
    echo_info "构建后端镜像... 版本: $version"
    build_backend "$version"
    save_version "$version"
    save_images "$version"
}

# 仅构建前端
build_frontend_only() {
    local version=$(generate_version)
    echo_info "构建前端镜像... 版本: $version"
    build_frontend "$version"
    save_version "$version"
    save_images "$version"
}

# 部署到服务器
deploy_server() {
    echo_warn "请先执行以下命令上传tar包到服务器:"
    echo ""
    echo "  scp ${BUILD_DIR}/rag-medical-*.tar user@${SERVER_IP}:/path/to/"
    echo ""
    echo_warn "然后在服务器上执行:"
    echo "  docker load -i rag-medical-backend-<version>.tar"
    echo "  docker load -i rag-medical-frontend-<version>.tar"
    echo "  cd /path/to/project/deploy/prod"
    echo "  docker-compose --env-file .env.prod -f docker-compose.yml up -d"
}

# 显示使用帮助
show_help() {
    echo "=========================================="
    echo "RAG Medical 构建脚本"
    echo "=========================================="
    echo ""
    echo "用法: $0 <command>"
    echo ""
    echo "命令:"
    echo "  build        构建所有镜像 (后端+前端)"
    echo "  build-be     仅构建后端镜像"
    echo "  build-fe     仅构建前端镜像"
    echo "  deploy       显示部署到服务器的步骤"
    echo "  list         列出已构建的镜像"
    echo "  tars         列出可用的tar包"
    echo ""
    echo "版本号格式: YYMMDDNN (如 26042301)"
    echo "构建后会自动生成tar包保存在 ${BUILD_DIR}/"
    echo ""
    echo "示例:"
    echo "  $0 build"
    echo "  $0 deploy"
    echo ""
}

# 列出镜像
list_images() {
    echo "=========================================="
    echo "RAG Medical 镜像列表:"
    echo "=========================================="
    docker images | grep rag-medical || echo "暂无镜像，请先执行 $0 build"
}

# 主逻辑
case "${1:-}" in
    build)
        build_all
        ;;
    build-be)
        build_backend_only
        ;;
    build-fe)
        build_frontend_only
        ;;
    deploy)
        deploy_server
        ;;
    list)
        list_images
        ;;
    tars)
        list_tars
        ;;
    *)
        show_help
        ;;
esac
