#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Интеграционный тест REST API «Память» по пунктам ТЗ:
  1. Регистрация, авторизация, JWT
  2. Три уровня доступа
  3. CRUD основной сущности (заказы)
  4. Поиск, фильтры, сортировка, пагинация
  5. Архивирование и восстановление (только админ)
  6. Комментарии (клиент — свои, админ — любые)
  7. Массовые операции (только админ)
  8. Импорт и экспорт CSV (только админ)
  9. Аналитика (только админ)
 10. Управление пользователями (только админ)
 11. Журнал аудита (только админ)

Запуск: python3 test_api.py [BASE_URL]
"""
import json
import sys
import urllib.request
import urllib.error
from urllib.parse import urlencode

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080/api"

passed = 0
failed = 0
failures = []


def check(name, condition, extra=""):
    global passed, failed
    if condition:
        passed += 1
        print(f"  PASS  {name}")
    else:
        failed += 1
        failures.append(f"{name} {extra}")
        print(f"  FAIL  {name}  {extra}")


def request(method, path, token=None, body=None, raw=None, content_type="application/json"):
    url = BASE + path
    data = raw.encode("utf-8") if raw is not None else (
        json.dumps(body).encode("utf-8") if body is not None else None)
    req = urllib.request.Request(url, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", content_type)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = resp.read().decode("utf-8")
            return resp.status, (json.loads(payload) if payload else {}), dict(resp.headers)
    except urllib.error.HTTPError as e:
        payload = e.read().decode("utf-8")
        try:
            parsed = json.loads(payload) if payload else {}
        except json.JSONDecodeError:
            parsed = {"raw": payload}
        return e.code, parsed, dict(e.headers)


def section(title):
    print(f"\n=== {title} ===")


# ==========================================================
section("1. Регистрация, авторизация, JWT")

st, health, _ = request("GET", "/health")
check("GET /api/health -> 200", st == 200)

st, resp, _ = request("POST", "/auth/register", body={
    "login": "test_client", "password": "secret1", "fullName": "Тестов Тест Тестович",
    "phone": "+79001112233", "email": "test_client@example.com"})
check("Регистрация нового клиента -> 201", st == 201, str(resp))
client_token = resp.get("token", "")
check("Токен выдан при регистрации", bool(client_token))

st, resp, _ = request("POST", "/auth/register", body={
    "login": "test_client", "password": "secret1", "fullName": "Дубль", "phone": "+79009999999"})
check("Повторный логин -> 409", st == 409, str(resp))

st, resp, _ = request("POST", "/auth/register", body={
    "login": "x", "password": "secret1", "fullName": "Плохой логин", "phone": "+79001112244"})
check("Короткий логин -> 400", st == 400)

st, resp, _ = request("POST", "/auth/register", body={
    "login": "nopass_user", "password": "123", "fullName": "Плохой пароль", "phone": "+79001112255"})
check("Короткий пароль -> 400", st == 400)

st, resp, _ = request("POST", "/auth/login", body={"login": "admin", "password": "admin123"})
check("Вход администратора (legacy SHA-256) -> 200", st == 200, str(resp))
admin_token = resp.get("token", "")

st, resp, _ = request("POST", "/auth/login", body={"login": "petrov", "password": "123456"})
check("Вход клиента -> 200", st == 200, str(resp))
petrov_token = resp.get("token", "")

st, resp, _ = request("POST", "/auth/login", body={"login": "manager", "password": "manager123"})
check("Вход менеджера -> 200", st == 200, str(resp))
manager_token = resp.get("token", "")

st, resp, _ = request("POST", "/auth/login", body={"login": "admin", "password": "wrong"})
check("Вход с неверным паролем -> 401", st == 401)

st, resp, _ = request("GET", "/auth/me", token=client_token)
check("GET /auth/me с токеном -> 200", st == 200 and resp.get("login") == "test_client", str(resp))
check("Профиль клиента в /auth/me", resp.get("client", {}).get("phone") == "+79001112233", str(resp.get("client")))

st, resp, _ = request("GET", "/auth/me")
check("/auth/me без токена -> 401", st == 401)

st, resp, _ = request("GET", "/auth/me", token="eyJhbGciOiJIUzI1NiJ9.broken.token")
check("Поддельный/битый JWT -> 401", st == 401)

st, resp, _ = request("GET", "/orders", token=client_token)
check("Авторизованный доступ к заказам -> 200", st == 200)

# ==========================================================
section("2. Три уровня доступа")

st, resp, _ = request("GET", "/auth/me", token=admin_token)
check("Роль ADMIN у админа", resp.get("role") == "ADMIN")
st, resp, _ = request("GET", "/auth/me", token=manager_token)
check("Роль MANAGER у менеджера", resp.get("role") == "MANAGER")
st, resp, _ = request("GET", "/auth/me", token=petrov_token)
check("Роль CLIENT у клиента", resp.get("role") == "CLIENT")

st, resp, _ = request("GET", "/users", token=admin_token)
check("ADMIN видит /users", st == 200)
st, resp, _ = request("GET", "/users", token=manager_token)
check("MANAGER не имеет доступа к /users -> 403", st == 403)
st, resp, _ = request("GET", "/users", token=petrov_token)
check("CLIENT не имеет доступа к /users -> 403", st == 403)

st, resp, _ = request("GET", "/analytics", token=admin_token)
check("ADMIN видит аналитику", st == 200)
st, resp, _ = request("GET", "/analytics", token=manager_token)
check("MANAGER не имеет доступа к аналитике -> 403", st == 403)

st, resp, _ = request("GET", "/audit", token=admin_token)
check("ADMIN видит журнал аудита", st == 200)
st, resp, _ = request("GET", "/audit", token=manager_token)
check("MANAGER не имеет доступа к аудиту -> 403", st == 403)

st, resp, _ = request("GET", "/services", token=client_token)
check("Каталог услуг доступен клиенту", st == 200 and resp.get("total", 0) > 0)

# ==========================================================
section("3. CRUD основной сущности (заказы)")

services = request("GET", "/services", token=client_token)[1]["data"]
svc1, svc2 = services[0], services[1]
expected_total = svc1["price"] * 2 + svc2["price"]

st, order, _ = request("POST", "/orders", token=client_token, body={
    "items": [{"serviceId": svc1["id"], "quantity": 2},
              {"serviceId": svc2["id"], "quantity": 1}]})
check("Клиент создаёт заказ -> 201", st == 201, str(order))
client_order_id = order.get("id")
check("Сумма посчитана на сервере по каталогу",
      abs(order.get("totalCost", 0) - expected_total) < 0.01,
      f"{order.get('totalCost')} != {expected_total}")
check("Статус нового заказа NEW", order.get("status") == "NEW")

st, resp, _ = request("POST", "/orders", token=petrov_token, body={
    "items": [{"serviceId": svc1["id"], "quantity": 1}]})
check("Второй клиент создаёт свой заказ -> 201", st == 201)
petrov_order_id = resp.get("id")

st, resp, _ = request("POST", "/orders", token=client_token, body={
    "items": [{"serviceId": 999999, "quantity": 1}]})
check("Заказ с несуществующей услугой -> 400", st == 400)

st, resp, _ = request("POST", "/orders", token=client_token, body={"items": []})
check("Заказ без позиций -> 400", st == 400)

st, resp, _ = request("GET", f"/orders/{client_order_id}", token=client_token)
check("Клиент видит свой заказ", st == 200 and resp.get("id") == client_order_id)
check("Позиции заказа возвращаются", len(resp.get("items", [])) == 2)

st, resp, _ = request("GET", f"/orders/{petrov_order_id}", token=client_token)
check("Клиент НЕ видит чужой заказ -> 404", st == 404)

st, resp, _ = request("GET", f"/orders/{petrov_order_id}", token=manager_token)
check("Менеджер видит любой заказ", st == 200)

st, resp, _ = request("PUT", f"/orders/{client_order_id}", token=client_token, body={
    "items": [{"serviceId": svc2["id"], "quantity": 3}]})
check("Клиент меняет состав своего заказа (NEW)", st == 200 and abs(resp.get("totalCost", 0) - svc2["price"] * 3) < 0.01, str(resp))

st, resp, _ = request("PUT", f"/orders/{client_order_id}", token=client_token, body={"status": "IN_PROGRESS"})
check("Клиент не может ставить произвольный статус -> 403", st == 403)

st, resp, _ = request("PUT", f"/orders/{petrov_order_id}", token=client_token, body={"status": "CANCELLED"})
check("Клиент не может менять чужой заказ -> 404", st == 404)

st, resp, _ = request("PUT", f"/orders/{petrov_order_id}", token=manager_token, body={
    "status": "IN_PROGRESS", "clientId": resp.get("clientId")})
check("Менеджер меняет статус заказа", st == 200 and resp.get("status") == "IN_PROGRESS")

st, resp, _ = request("POST", "/orders", token=manager_token, body={
    "clientId": 1, "items": [{"serviceId": svc1["id"], "quantity": 1}]})
check("Менеджер создаёт заказ для клиента по clientId -> 201", st == 201)
staff_created_order = resp.get("id")

st, resp, _ = request("PUT", f"/orders/{client_order_id}", token=client_token, body={
    "clientId": 3, "items": [{"serviceId": svc1["id"], "quantity": 1}]})
check("Клиент не может сменить clientId -> 403", st == 403)

st, resp, _ = request("DELETE", f"/orders/{staff_created_order}", token=client_token)
check("Клиент не может удалить чужой заказ -> 404", st == 404)

# ==========================================================
section("4. Поиск, фильтры, сортировка, пагинация")

st, resp, _ = request("GET", "/orders?page=1&limit=2", token=admin_token)
check("Пагинация: limit=2", st == 200 and len(resp.get("data", [])) == 2)
check("Пагинация: total и totalPages согласованы",
      resp.get("totalPages", 0) == -(-resp.get("total", 0) // 2), str((resp.get('total'), resp.get('totalPages'))))
st, resp2, _ = request("GET", "/orders?page=2&limit=2", token=admin_token)
check("Пагинация: вторая страница не совпадает с первой",
      resp2.get("data", []) and resp2["data"][0]["id"] != resp["data"][0]["id"])

st, resp, _ = request("GET", "/orders?search=" + urllib.parse.quote("Петров"), token=admin_token)
check("Поиск по имени клиента", st == 200 and resp.get("total", 0) >= 1, str(resp.get("total")))

st, resp, _ = request("GET", f"/orders?search={client_order_id}", token=admin_token)
check("Поиск по номеру заказа", st == 200 and any(o["id"] == client_order_id for o in resp.get("data", [])))

st, resp, _ = request("GET", "/orders?search=" + urllib.parse.quote("+79991234567"), token=admin_token)
check("Поиск по телефону", st == 200 and resp.get("total", 0) >= 1)

st, resp, _ = request("GET", "/orders?status=NEW", token=admin_token)
check("Фильтр по статусу", st == 200 and all(o["status"] == "NEW" for o in resp.get("data", [])))

st, resp, _ = request("GET", "/orders?status=WRONG", token=admin_token)
check("Некорректный статус -> 400", st == 400)

st, resp, _ = request("GET", "/orders?dateFrom=2026-09-01&dateTo=2026-09-30", token=admin_token)
check("Фильтр по периоду (сентябрь 2026)", st == 200 and resp.get("total", 0) >= 1)

st, resp, _ = request("GET", "/orders?sortBy=total_cost&sortDir=ASC", token=admin_token)
costs = [o["totalCost"] for o in resp.get("data", [])]
check("Сортировка по сумме ASC", st == 200 and costs == sorted(costs), str(costs))

st, resp, _ = request("GET", "/orders?sortBy=id&sortDir=DESC", token=admin_token)
ids = [o["id"] for o in resp.get("data", [])]
check("Сортировка по ID DESC", st == 200 and ids == sorted(ids, reverse=True), str(ids))

st, resp, _ = request("GET", "/orders", token=client_token)
check("Клиент видит только свои заказы",
      st == 200 and all(o["clientName"] == "Тестов Тест Тестович" for o in resp.get("data", [])),
      str([o.get("clientName") for o in resp.get("data", [])]))

st, resp, _ = request("GET", "/orders?clientId=2", token=manager_token)
check("Фильтр по clientId для сотрудника", st == 200 and all(o["clientId"] == 2 for o in resp.get("data", [])))

# ==========================================================
section("5. Архивирование и восстановление (только админ)")

st, resp, _ = request("POST", f"/orders/{petrov_order_id}/archive", token=client_token)
check("Клиент не может архивировать -> 403", st == 403)
st, resp, _ = request("POST", f"/orders/{petrov_order_id}/archive", token=manager_token)
check("Менеджер не может архивировать -> 403", st == 403)

st, resp, _ = request("POST", f"/orders/{petrov_order_id}/archive", token=admin_token)
check("Админ архивирует заказ -> 200", st == 200, str(resp))

st, resp, _ = request("GET", "/orders", token=manager_token)
check("Архивный заказ скрыт из общего списка",
      all(o["id"] != petrov_order_id for o in resp.get("data", [])))

st, resp, _ = request("GET", "/orders?archived=true", token=admin_token)
check("Архивный заказ виден в ?archived=true",
      any(o["id"] == petrov_order_id for o in resp.get("data", [])))

st, resp, _ = request("GET", f"/orders/{petrov_order_id}", token=client_token)
check("Архивный заказ скрыт от клиента -> 404", st == 404)

st, resp, _ = request("GET", f"/orders/{petrov_order_id}", token=manager_token)
check("Сотрудник может открыть архивный заказ", st == 200 and resp.get("archived") is True)

st, resp, _ = request("PUT", f"/orders/{petrov_order_id}", token=manager_token, body={"status": "NEW"})
check("Редактирование архивного заказа -> 409", st == 409)

st, resp, _ = request("POST", f"/orders/{petrov_order_id}/archive", token=admin_token)
check("Повторное архивирование -> 409", st == 409)

st, resp, _ = request("POST", f"/orders/{petrov_order_id}/restore", token=admin_token)
check("Восстановление из архива -> 200", st == 200)

st, resp, _ = request("GET", "/orders", token=manager_token)
check("Восстановленный заказ снова в списке",
      any(o["id"] == petrov_order_id for o in resp.get("data", [])))

# ==========================================================
section("6. Комментарии")

st, resp, _ = request("POST", f"/orders/{client_order_id}/comments", token=client_token,
                      body={"text": "Прошу перезвонить по поводу декора"})
check("Клиент добавляет комментарий к своему заказу -> 201", st == 201, str(resp))
comment_id = resp.get("id")

st, resp, _ = request("POST", f"/orders/{petrov_order_id}/comments", token=client_token,
                      body={"text": "чужой заказ"})
check("Клиент не может комментировать чужой заказ -> 404", st == 404)

st, resp, _ = request("POST", f"/orders/{petrov_order_id}/comments", token=manager_token,
                      body={"text": "Менеджер: свяжемся с клиентом до пятницы"})
check("Менеджер комментирует любой заказ -> 201", st == 201)
manager_comment_id = resp.get("id")

st, resp, _ = request("GET", f"/orders/{client_order_id}/comments", token=client_token)
check("Список комментариев заказа", st == 200 and resp.get("total", 0) >= 1)

st, resp, _ = request("GET", f"/orders/{petrov_order_id}/comments", token=client_token)
check("Комментарии чужого заказа клиенту недоступны -> 404", st == 404)

st, resp, _ = request("GET", f"/orders/{petrov_order_id}/comments", token=admin_token)
check("Админ видит комментарии любого заказа", st == 200)

st, resp, _ = request("POST", f"/orders/{client_order_id}/comments", token=client_token, body={"text": ""})
check("Пустой комментарий -> 400", st == 400)

st, resp, _ = request("DELETE", f"/orders/{petrov_order_id}/comments/{manager_comment_id}", token=petrov_token)
check("Владелец заказа не может удалить чужой комментарий -> 403", st == 403, str(resp))

st, resp, _ = request("DELETE", f"/orders/{petrov_order_id}/comments/{manager_comment_id}",
                      token=manager_token)
check("Автор удаляет свой комментарий -> 200", st == 200)

st, resp, _ = request("POST", f"/orders/{petrov_order_id}/comments", token=admin_token,
                      body={"text": "Комментарий администратора"})
admin_comment_id = resp.get("id")
st, resp, _ = request("DELETE", f"/orders/{petrov_order_id}/comments/{admin_comment_id}",
                      token=admin_token)
check("Админ удаляет любой комментарий -> 200", st == 200)

# ==========================================================
section("7. Массовые операции (только админ)")

st, resp, _ = request("POST", "/orders/bulk", token=manager_token,
                      body={"ids": [1, 2], "action": "archive"})
check("Менеджер не может выполнять массовые операции -> 403", st == 403)
st, resp, _ = request("POST", "/orders/bulk", token=client_token,
                      body={"ids": [1, 2], "action": "archive"})
check("Клиент не может выполнять массовые операции -> 403", st == 403)

st, resp, _ = request("POST", "/orders/bulk", token=admin_token,
                      body={"ids": [petrov_order_id, staff_created_order], "action": "archive"})
check("Массовое архивирование (2 заказа)", st == 200 and resp.get("successCount") == 2, str(resp))

st, resp, _ = request("GET", "/orders?archived=true", token=admin_token)
check("Оба заказа в архиве",
      all(any(o["id"] == i for o in resp.get("data", [])) for i in [petrov_order_id, staff_created_order]))

st, resp, _ = request("POST", "/orders/bulk", token=admin_token,
                      body={"ids": [petrov_order_id, staff_created_order, 999999],
                            "action": "restore"})
check("Массовое восстановление: 2 успешно + 1 ошибка",
      resp.get("successCount") == 2 and resp.get("failedCount") == 1, str(resp))

st, resp, _ = request("POST", "/orders/bulk", token=admin_token,
                      body={"ids": [staff_created_order], "action": "setStatus", "status": "COMPLETED"})
check("Массовая смена статуса", st == 200 and resp.get("successCount") == 1)

st, resp, _ = request("GET", f"/orders/{staff_created_order}", token=admin_token)
check("Статус изменился через bulk", resp.get("status") == "COMPLETED")

st, resp, _ = request("POST", "/orders/bulk", token=admin_token,
                      body={"ids": [staff_created_order], "action": "delete"})
check("Массовое удаление", st == 200 and resp.get("successCount") == 1)

st, resp, _ = request("GET", f"/orders/{staff_created_order}", token=admin_token)
check("Удалённый заказ отсутствует -> 404", st == 404)

st, resp, _ = request("POST", "/orders/bulk", token=admin_token,
                      body={"ids": [1], "action": "explode"})
check("Неизвестное действие -> 400", st == 400)

# ==========================================================
section("8. Импорт и экспорт CSV (только админ)")

req = urllib.request.Request(BASE + "/orders/export")
req.add_header("Authorization", "Bearer " + admin_token)
with urllib.request.urlopen(req, timeout=30) as r:
    csv_text = r.read().decode("utf-8")
check("Экспорт CSV -> 200", len(csv_text) > 0)
check("CSV начинается с BOM (Excel)", csv_text.startswith("\ufeff"))
check("CSV содержит заголовок", "id;client_id;client_name;created_at;status;total_cost;archived;comments_count" in csv_text)
check("CSV содержит строки данных", csv_text.count("\n") >= 6)

st, resp, _ = request("GET", "/orders/export", token=client_token)
check("Экспорт CSV клиентом -> 403", st == 403)
st, resp, _ = request("GET", "/orders/export", token=manager_token)
check("Экспорт CSV менеджером -> 403", st == 403)

import_csv = (
    "client_id;status;total_cost;created_at\n"
    "1;NEW;12500.50;2026-10-01 10:00:00\n"
    "2;IN_PROGRESS;30000;2026-10-02\n"
    "9999;NEW;1000\n"                      # несуществующий клиент — ошибка
    "1;BROKEN;1000\n"                      # плохой статус — ошибка
    "1;NEW;не число\n"                     # плохая сумма — ошибка
)
st, resp, _ = request("POST", "/orders/import", token=admin_token, raw=import_csv,
                      content_type="text/csv")
check("Импорт CSV -> 200", st == 200, str(resp))
check("Импортировано 2 строки", resp.get("imported") == 2, str(resp.get("imported")))
check("Отчёт об ошибках: 3 строки пропущены", resp.get("errorsCount") == 3, str(resp.get("errors")))

st, resp, _ = request("POST", "/orders/import", token=client_token, raw=import_csv,
                      content_type="text/csv")
check("Импорт CSV клиентом -> 403", st == 403)

st, resp, _ = request("GET", "/orders?dateFrom=2026-10-01&dateTo=2026-10-02", token=admin_token)
check("Импортированные заказы найдены фильтром по дате", resp.get("total", 0) >= 2, str(resp.get("total")))

# ==========================================================
section("9. Аналитика (только админ)")

st, resp, _ = request("GET", "/analytics", token=admin_token)
check("Аналитика -> 200", st == 200)
check("Общие показатели присутствуют",
      all(k in resp.get("overview", {}) for k in
          ["orderCount", "revenue", "avgOrderCost", "minOrderCost", "maxOrderCost", "archivedCount"]))
check("Распределение по статусам", "NEW" in resp.get("byStatus", {}))
check("Динамика по месяцам", isinstance(resp.get("byMonth"), list) and len(resp.get("byMonth")) >= 1)
check("Топ услуг", isinstance(resp.get("topServices"), list) and len(resp.get("topServices")) >= 1)
check("Пользователи по ролям", "byRole" in resp.get("users", {}))

st, resp, _ = request("GET", "/analytics?from=2026-09-01&to=2026-09-30", token=admin_token)
check("Аналитика за период", st == 200 and resp.get("period", {}).get("from") == "2026-09-01")

st, resp, _ = request("GET", "/analytics?from=2030-01-01&to=2026-01-01", token=admin_token)
check("Некорректный период -> 400", st == 400)

st, resp, _ = request("GET", "/analytics", token=client_token)
check("Аналитика клиенту -> 403", st == 403)

# ==========================================================
section("10. Управление пользователями (только админ)")

st, resp, _ = request("GET", "/users?search=test", token=admin_token)
check("Список пользователей с поиском", st == 200 and resp.get("total", 0) >= 1)
target_user = next((u for u in resp.get("data", []) if u["login"] == "test_client"), None)
check("Найден пользователь test_client", target_user is not None)
uid = target_user["id"] if target_user else 0

st, resp, _ = request("GET", "/users?page=1&limit=3", token=admin_token)
check("Пагинация списка пользователей", st == 200 and len(resp.get("data", [])) <= 3)

st, resp, _ = request("GET", "/users?role=CLIENT&blocked=false", token=admin_token)
check("Фильтр по роли и блокировке", st == 200 and all(u["role"] == "CLIENT" and not u["blocked"] for u in resp.get("data", [])))

st, resp, _ = request("PUT", f"/users/{uid}/role", token=admin_token, body={"role": "MANAGER"})
check("Смена роли на MANAGER -> 200", st == 200, str(resp))
st, resp, _ = request("GET", "/auth/me", token=client_token)
check("Роль в /auth/me обновилась (MANAGER)", resp.get("role") == "MANAGER")

st, resp, _ = request("PUT", f"/users/{uid}/role", token=admin_token, body={"role": "SUPERUSER"})
check("Недопустимая роль -> 400", st == 400)

st, resp, _ = request("PUT", f"/users/{uid}/role", token=admin_token, body={"role": "CLIENT"})
check("Возврат роли CLIENT -> 200", st == 200)

st, resp, _ = request("PUT", f"/users/{uid}/role", token=manager_token, body={"role": "CLIENT"})
check("Смена роли менеджером -> 403", st == 403)

st, resp, _ = request("POST", f"/users/{uid}/reset-password", token=admin_token, body={})
check("Сброс пароля (автогенерация) -> 200", st == 200 and resp.get("newPassword"), str(resp))
new_password = resp.get("newPassword", "")

st, resp, _ = request("POST", "/auth/login", body={"login": "test_client", "password": new_password})
check("Вход с новым паролем -> 200", st == 200)
relogin_token = resp.get("token", "")

st, resp, _ = request("POST", "/auth/login", body={"login": "test_client", "password": "secret1"})
check("Старый пароль больше не работает -> 401", st == 401)

st, resp, _ = request("POST", f"/users/{uid}/block", token=admin_token, body={})
check("Блокировка пользователя -> 200", st == 200)
st, resp, _ = request("GET", "/orders", token=relogin_token)
check("Заблокированный пользователь не проходит по старому токену -> 403", st == 403)
st, resp, _ = request("POST", "/auth/login", body={"login": "test_client", "password": new_password})
check("Вход заблокированного -> 403", st == 403)

st, resp, _ = request("POST", f"/users/{uid}/unblock", token=admin_token, body={})
check("Разблокировка -> 200", st == 200)
st, resp, _ = request("POST", "/auth/login", body={"login": "test_client", "password": new_password})
check("Вход после разблокировки -> 200", st == 200)

st, resp, _ = request("POST", "/users/1/block", token=admin_token, body={})
check("Самоблокировка админа -> 409", st == 409)

st, resp, _ = request("PUT", f"/users/{uid}", token=admin_token,
                      body={"fullName": "Тестов Тест Тестович", "phone": "+79001112233"})
check("Обновление профиля пользователя -> 200", st == 200)

st, resp, _ = request("POST", f"/users/{uid}/block", token=manager_token, body={})
check("Блокировка менеджером -> 403", st == 403)

# ==========================================================
section("11. Журнал аудита (только админ)")

st, resp, _ = request("GET", "/audit?page=1&limit=20", token=admin_token)
check("Журнал аудита -> 200", st == 200)

expected_actions = ["LOGIN", "LOGIN_FAILED", "REGISTER", "CREATE_ORDER", "UPDATE_ORDER",
                    "ARCHIVE_ORDER", "RESTORE_ORDER", "ADD_COMMENT", "DELETE_COMMENT",
                    "BULK_OPERATION", "EXPORT_ORDERS", "IMPORT_ORDERS",
                    "USER_ROLE_CHANGED", "USER_BLOCKED", "USER_UNBLOCKED",
                    "USER_PASSWORD_RESET", "ACCESS_DENIED"]
missing = []
for act in expected_actions:
    st_a, resp_a, _ = request("GET", "/audit?action=" + act + "&limit=1", token=admin_token)
    if st_a != 200 or resp_a.get("total", 0) < 1:
        missing.append(act)
check("Ключевые действия зафиксированы в журнале", not missing, f"не хватает: {missing}")

st, resp, _ = request("GET", "/audit?action=CREATE_ORDER", token=admin_token)
check("Фильтр аудита по действию", st == 200 and all(e["action"] == "CREATE_ORDER" for e in resp.get("data", [])))

st, resp, _ = request("GET", f"/audit?userId={uid}", token=admin_token)
check("Фильтр аудита по пользователю", st == 200 and all(e["userId"] == uid for e in resp.get("data", [])))

st, resp, _ = request("GET", "/audit?action=LOGIN_FAILED", token=admin_token)
check("Неудачные входы фиксируются", st == 200 and resp.get("total", 0) >= 1)

st, resp, _ = request("GET", "/audit?dateFrom=2020-01-01&dateTo=2030-01-01", token=admin_token)
check("Фильтр аудита по периоду", st == 200 and resp.get("total", 0) >= 1)

st, resp, _ = request("GET", "/audit/actions", token=admin_token)
check("Список типов действий", st == 200 and len(resp.get("data", [])) >= 10)

st, resp, _ = request("GET", "/audit", token=client_token)
check("Журнал аудита клиенту -> 403", st == 403)

st, resp, _ = request("GET", "/audit", token=manager_token)
check("Журнал аудита менеджеру -> 403", st == 403)

st, resp, _ = request("GET", "/audit", token=admin_token)
denied = any(e["action"] == "ACCESS_DENIED" for e in resp.get("data", []))
check("Попытки доступа без прав фиксируются (ACCESS_DENIED)", denied)

# ==========================================================
print(f"\n{'=' * 50}")
print(f"ИТОГО: passed={passed}, failed={failed}")
if failures:
    print("Проваленные проверки:")
    for f in failures:
        print("  - " + f)
sys.exit(1 if failed else 0)
