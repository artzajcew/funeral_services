#!/usr/bin/env sh
set -eu
mkdir -p out
# Консольный smoke-тест не требует JavaFX SDK. JavaFX-часть проверяется Maven-сборкой.
find src/main/java src/test/java -name '*.java' ! -path '*/ru/memory/fx/*' -print0 | xargs -0 javac -encoding UTF-8 -d out
java -ea -cp out ru.memory.FrontendUiTest
