
quarkus dev -Djvm.args="-Djavaagent:dd-java-agent.jar  -Ddd.profiling.enabled=true -XX:FlightRecorderOptions=stackdepth=256 -Ddd.logs.injection=true -Ddd.service=my-app -Ddd.env=staging -Ddd.version=1.0"
