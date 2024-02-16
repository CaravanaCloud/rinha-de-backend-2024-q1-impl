#!/bin/bash

cp ./rinhadebackend/src/main/resources/db/init.postgresql.sql .
cp docker-compose.yml nginx.conf postgresql.conf init.postgresql.sql ./rinha-de-backend-2024-q1/participantes/caravanacloud/
rm -f ./rinha-de-backend-2024-q1/participantes/caravanacloud/testada
rm -f ./rinha-de-backend-2024-q1/participantes/caravanacloud/*.log

echo done