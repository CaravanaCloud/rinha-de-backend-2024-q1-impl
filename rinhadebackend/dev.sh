#!/bin/bash
# download datadog agent if not exists
if [ ! -f dd-java-agent.jar ]; then
  wget -O dd-java-agent.jar https://dtdg.co/latest-java-tracer
fi

quarkus dev -Djvm.args="-Djavaagent:dd-java-agent.jar  -Ddd.profiling.enabled=true -XX:FlightRecorderOptions=stackdepth=256 -Ddd.logs.injection=true -Ddd.service=my-app -Ddd.env=staging -Ddd.version=1.0"
