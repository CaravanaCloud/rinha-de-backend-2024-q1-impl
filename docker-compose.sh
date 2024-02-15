#!/bin/bash

docker compose down
docker compose up 2>&1 | tee docker-compose.log.txt
