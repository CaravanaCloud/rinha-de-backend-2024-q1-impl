#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

export QUARKUS_DATASOURCE_JDBC_URL="jdbc:postgresql://localhost:5432/postgres"
export QUARKUS_DATASOURCE_USERNAME="gitpod"
export QUARKUS_DATASOURCE_PASSWORD="gitpod"

if [ ! -f "target/quarkus-app/quarkus-run.jar" ]; then
  ${DIR}/mvnw package
fi

export JAVA_XOPTS=""
java -jar "${DIR}/target/quarkus-app/quarkus-run.jar" $JAVA_XOPTS
