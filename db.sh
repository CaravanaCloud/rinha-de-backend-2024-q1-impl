#!/bin/bash
docker run -d \
  --network host \
  --name db \
  --hostname db \
  -e POSTGRES_PASSWORD=123 \
  -e POSTGRES_USER=rinha \
  -e POSTGRES_DB=rinha \
  -p 5400:5400 \
  -v $(pwd)/init.postgresql.sql:/docker-entrypoint-initdb.d/init.sql \
  -v $(pwd)/postgresql.conf:/etc/postgresql/postgresql.conf \
  postgres:latest \
  postgres -c config_file=/etc/postgresql/postgresql.conf