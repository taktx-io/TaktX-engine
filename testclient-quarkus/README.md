# Takt BPMN Engine

## Building image

```
docker build -f Dockerfile.jvm -t ghcr.io/taktx-io/test-client:1.0.0 .
docker build --platform linux/amd64 -f Dockerfile.jvm -t ghcr.io/taktx-io/test-client:1.0.0 .
docker run -it -v ./config:/app/config -e TAKTX_PROPERTIES_FILE=/app/config/takt.properties -e tenant=tenant -e namespace=namespace -p 8081:8081 ghcr.io/taktx-io/test-client:1.0.0
docker push ghcr.io/taktx-io/test-client:1.0.0
docker pull ghcr.io/taktx-io/test-client:1.0.0

```
## Typical payload 

```json
    {
      "benchmark_starter_id": "200",
      "var1": "value1",
      "var2": true,
      "var3": 15,
      "var4": {
        "var4-1": "value4-1-${RANDOM_UUID}",
        "var4-2": false,
        "var4-3": 111
      },
      "var5": "736d9100-0155-4af5-be14-b09c42de8417",
      "var6": "b2959d57-d091-42d4-b18c-9e2145b45074",
      "var7": "572c74fa-fb3d-4711-bb76-21d66b87fa86 ",
      "var8": "d091-42d4-b18c-9e2145b45074-b2959d57",
      "var9": "b18c-9e2145b45074-b2959d57-d091-42d4",
      "var10": "b2959d5742d4-b18c-d091-9e2145b45074",
      "var11": "b18c-9e2145b45074-b2959d57-d091-42d4",
      "var12": 7458,
      "var13": false,
      "list": [
        "a",
        "b",
        "c",
        "d",
        "e",
        "f",
        "g",
        "h",
        "i",
        "j"
      ]
    }
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
