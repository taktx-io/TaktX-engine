# Takt BPMN Engine

## Building image

```
docker build -t ghcr.io/qunit/bpmnmeister:1.0.0 .
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
