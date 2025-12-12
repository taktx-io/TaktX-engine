# Takt BPMN Engine

![Coverage](../badges/taktx-engine-coverage.svg)

The TaktX Engine is the core BPMN process automation engine with **87% test coverage**, including comprehensive integration tests with Testcontainers.

## Building image

```
docker build -f Dockerfile.jvm -t ghcr.io/qunit/taktx-engine:1.0.0 .
docker build --platform linux/amd64 -f Dockerfile.jvm -t ghcr.io/qunit/taktx-engine:1.0.0 .
docker push ghcr.io/qunit/taktx-engine:1.0.0 
docker pull ghcr.io/qunit/taktx-engine:1.0.0 
docker run -it -v ./config:/app/config -e quarkus.config.locations=file:/app/config/kafka-dockerlocal.properties -e namespace=namespace  ghcr.io/qunit/taktx-engine:1.0.0
keytool -import -alias testalias -file ca-certificate.crt -keypass keypass -keystore truststore.jks 
 ```


## Build native executable on mac for mac
QUARKUS_NATIVE_ADDITIONAL_BUILD_ARGS="--initialize-at-run-time=scala.util.Random$" quarkus build --native --no-tests
## Build native executable on mac for Linux
QUARKUS_NATIVE_ADDITIONAL_BUILD_ARGS="--initialize-at-run-time=scala.util.Random$" quarkus build --native --no-tests -Dquarkus.native.container-build=true

## Running container

```bash
docker run -d \
    -e "injectedhost=host.docker.internal" \
    -e "injectedport=8081" \
    -e "quarkus.profile=dockerlocal" \
    -e "namespace=[namespace]" \
    -p 8081:8080 \
    -it ghcr.io/qunit/bpmnmeister:1.0.0
```
