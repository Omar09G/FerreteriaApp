#!/bin/bash
# Se ejecuta AL FINAL del init (después de los .sql que crean ferreteria_app)
set -e
if [ -n "$POSTGRES_APP_PASSWORD" ]; then
    echo ">> [init] Aplicando POSTGRES_APP_PASSWORD al rol de aplicación..."
    psql -v ON_ERROR_STOP=1 -U postgres -c \
        "ALTER ROLE ferreteria_app WITH PASSWORD '${POSTGRES_APP_PASSWORD}';"
fi
