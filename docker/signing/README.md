# Docker Compose signing files

This directory is bind-mounted into containers when using `docker/docker-compose-full.yaml`.

## Engine

Create these files before starting Compose:

- `docker/signing/engine/key-id`
- `docker/signing/engine/private-key.b64`
- `docker/signing/engine/public-key.b64`

## Workers

For each worker container, create a separate directory with the same filenames. Example:

- `docker/signing/worker-example/key-id`
- `docker/signing/worker-example/private-key.b64`
- `docker/signing/worker-example/public-key.b64`

Write updates atomically for live rotation:

1. write new contents to a temporary file
2. rename/move into place

Optional:

- set `TAKTX_SIGNING_FILE_REFRESH_INTERVAL_MS` to control how often the process re-checks the files
- default refresh interval is `1000` ms

