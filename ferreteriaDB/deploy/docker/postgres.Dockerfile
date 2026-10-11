# postgres:17-alpine + pgBackRest para PITR (DB-ESC-008).
# Sin este binario, el archive_command de postgresql.conf no existe y el WAL
# nunca se archiva (RPO≈0 imposible). Todo lo demás idéntico al upstream.
FROM postgres:17-alpine
RUN apk add --no-cache pgbackrest && pgbackrest version
