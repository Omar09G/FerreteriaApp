# postgres:17.11-alpine3.24 + pgBackRest para PITR (DB-ESC-008).
# Sin este binario, el archive_command de postgresql.conf no existe y el WAL
# nunca se archiva (RPO≈0 imposible). Todo lo demás idéntico al upstream.
FROM postgres:17.11-alpine3.24@sha256:e5d2edd28b1f8d40a0a6c0c9cdc545b19b56a0d5d88f94bc11a97d970d4700d0
RUN apk add --no-cache pgbackrest && pgbackrest version
