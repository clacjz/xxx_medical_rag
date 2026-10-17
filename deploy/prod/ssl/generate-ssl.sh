#!/bin/bash
# SSL证书生成脚本 - 用于内网生产环境 HTTPS 部署
# 使用方式: ./generate-ssl.sh [域名或IP]
# 示例: ./generate-ssl.sh localhost

set -e

HOST="${1:-localhost}"
SSL_DIR="$(dirname "$0")"
KEY_FILE="$SSL_DIR/nginx.key"
CERT_FILE="$SSL_DIR/nginx.crt"

echo "Generating self-signed SSL certificate for $HOST..."

openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
    -keyout "$KEY_FILE" \
    -out "$CERT_FILE" \
    -subj "/CN=$HOST" \
    -addext "subjectAltName=IP:$HOST"

echo "SSL certificate generated successfully!"
echo "  Key: $KEY_FILE"
echo "  Cert: $CERT_FILE"
echo ""
echo "Certificate details:"
openssl x509 -in "$CERT_FILE" -noout -subject -dates