#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

echo "## Checking registry authentication"
docker login


echo "## Building Cached Native image"
docker build \
    -f "$DIR/src/main/docker/Dockerfile.native" \
    --no-cache \
    --progress=plain \
    -t caravanacloud/rinhadebackend-native:0.0.3-serial "$DIR"
docker push caravanacloud/rinhadebackend-native:0.0.3-serial

# echo "## Building Default image"
# docker build -f "$DIR/src/main/docker/Dockerfile" --no-cache --progress=plain -t caravanacloud/rinhadebackend:latest "$DIR"
# docker push caravanacloud/rinhadebackend:latest

# echo "## Building JVM image"
# docker build -f "$DIR/src/main/docker/Dockerfile.jvm" --no-cache --progress=plain -t caravanacloud/rinhadebackend-jvm:latest "$DIR"
# docker push caravanacloud/rinhadebackend-jvm:latest

echo "## done"