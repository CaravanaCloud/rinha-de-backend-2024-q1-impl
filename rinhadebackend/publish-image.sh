#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# Remember to docker login
echo "## Building Default image"
docker build -f "$DIR/src/main/docker/Dockerfile" --no-cache --progress=plain -t caravanacloud/rinhadebackend:latest "$DIR"
docker push caravanacloud/rinhadebackend:latest

echo "## Building JVM image"
docker build -f "$DIR/src/main/docker/Dockerfile.jvm" --no-cache --progress=plain -t caravanacloud/rinhadebackend-jvm:latest "$DIR"
docker push caravanacloud/rinhadebackend-jvm:latest

echo "## Building Native image"
docker build -f "$DIR/src/main/docker/Dockerfile.native" --no-cache --progress=plain -t caravanacloud/rinhadebackend-native:latest "$DIR"
docker push caravanacloud/rinhadebackend-native:latest
