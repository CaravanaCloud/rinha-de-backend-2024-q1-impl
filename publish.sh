#!/bin/bash

cp docker-compose.yml nginx.conf postgresql.conf init.postgresql.1table.fn.sql ./rinha-de-backend-2024-q1/participantes/caravanacloud/
rm -f ./rinha-de-backend-2024-q1/participantes/caravanacloud/testada
rm -f ./rinha-de-backend-2024-q1/participantes/caravanacloud/*.log

echo done