#!/usr/bin/env bash
# Запуск REST-сервера из каталога проекта (Linux/macOS).
# Отредактируйте переменные под вашу базу или используйте database.properties.
set -e
cd "$(dirname "$0")"

if [ ! -d out ]; then
    javac -cp lib/postgresql-42.7.12.jar -encoding UTF-8 -d out \
      src/Main.java src/ui/*.java src/dao/*.java src/models/*.java \
      src/utils/*.java src/db/*.java src/api/*.java
fi

# MEMORY_DB_URL / MEMORY_DB_USER / MEMORY_DB_PASSWORD можно задать здесь:
# export MEMORY_DB_URL='jdbc:postgresql://localhost:5432/funeral_services'
# export MEMORY_DB_USER='postgres'
# export MEMORY_DB_PASSWORD='secret'

exec java -cp "out:lib/postgresql-42.7.12.jar" api.ApiServer
