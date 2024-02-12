#!/bin/bash

echo "# Rebuilding the project"
rm -rf ./rinhadebackend/target
source ./rinhadebackend/publish-image.sh

echo "# Starting containers"
docker compose up -d

echo "# Waiting for the application to start"
sleep 15

echo "# Running tests"
pushd ../rinha-de-backend-2024-q1/
source ./executar-teste-local.sh
popd

echo "# Stopping containers"
docker compose down

echo "# Done"