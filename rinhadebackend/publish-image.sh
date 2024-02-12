#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

docker build -f "$DIR/src/main/docker/Dockerfile" --no-cache --progress=plain -t caravanacloud/rinhadebackend:latest "$DIR"
# Remember to docker login
docker push caravanacloud/rinhadebackend:latest
