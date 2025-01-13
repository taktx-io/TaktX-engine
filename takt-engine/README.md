# Takt BPMN Engine

## Building image

```
docker build -f Dockerfile.jvm -t ghcr.io/qunit/takt-engine:1.0.0 .
docker build --platform linux/amd64 -f Dockerfile.jvm -t ghcr.io/qunit/takt-engine:1.0.0 .
docker push ghcr.io/qunit/takt-engine:1.0.0 
docker run -it -v ./config:/app/config -e quarkus.config.locations=file:/app/config/kafka-dockerlocal.properties -e tenant=tenant -e namespace=namespace  ghcr.io/qunit/takt-engine:1.0.0
```

## Running container

```bash
docker run -d \
    -e "injectedhost=host.docker.internal" \
    -e "injectedport=8081" \
    -e "quarkus.profile=dockerlocal" \
    -e "tenant=[tenant]" \
    -e "namespace=[namespace]" \
    -p 8081:8080 \
    -it ghcr.io/qunit/bpmnmeister:1.0.0
```
