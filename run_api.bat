@echo off
rem Запуск REST-сервера из каталога проекта (Windows).
rem Отредактируйте переменные под вашу базу или используйте database.properties.

if not exist out (
    javac -cp lib\postgresql-42.7.12.jar -encoding UTF-8 -d out src\Main.java src\ui\*.java src\dao\*.java src\models\*.java src\utils\*.java src\db\*.java src\api\*.java || exit /b 1
)

rem MEMORY_DB_URL / MEMORY_DB_USER / MEMORY_DB_PASSWORD можно задать здесь:
rem set MEMORY_DB_URL=jdbc:postgresql://localhost:5432/funeral_services
rem set MEMORY_DB_USER=postgres
rem set MEMORY_DB_PASSWORD=secret

java -cp "out;lib\postgresql-42.7.12.jar" api.ApiServer
