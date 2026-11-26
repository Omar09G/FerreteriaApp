# postgres:17.11-alpine3.24 + pgBackRest para PITR (DB-ESC-008).
# Sin este binario, el archive_command de postgresql.conf no existe y el WAL
# nunca se archiva (RPO≈0 imposible). Todo lo demás idéntico al upstream.
FROM postgres:19.0-alpine3.24@sha256:cac45fb483caee3668946088a1c0532db89d5285c28dbfeac889b4068296e06f
RUN apk add --no-cache pgbackrest && pgbackrest version
