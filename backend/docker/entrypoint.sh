#!/bin/bash

set -e

# Default values
PROFILE=${SPRING_PROFILES_ACTIVE:-prod}
JAVA_OPTS=${JAVA_OPTS:-"-Xms512m -Xmx1024m -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"}

echo "=========================================="
echo "Starting RAG Backend"
echo "Profile: $PROFILE"
echo "Java Options: $JAVA_OPTS"
echo "=========================================="

# Set timezone
export TZ=Asia/Shanghai

# Execute the application
exec java $JAVA_OPTS -jar app.jar --spring.profiles.active=$PROFILE