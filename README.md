# Prueba técnica — Gestión de usuarios

## 1. Propósito del documento

Este documento describe la solución entregada para el servicio de gestión de usuarios de la sala 1. Está dirigido a cuatro perfiles:

- **Desarrollador:** reproducir el entorno, ejecutar la aplicación y modificar el código.
- **QA:** ejecutar las pruebas, verificar los contratos y completar la evidencia.
- **Analista:** comprobar requisitos funcionales, trazabilidad y criterios de aceptación.
- **Arquitecto:** revisar arquitectura, decisiones técnicas, límites y extensibilidad.

La fuente de requisitos original se conserva en [README-prueba-tecnica.md](README-prueba-tecnica.md).

---

## 1.1 Uso responsable de inteligencia artificial

Durante el desarrollo y la documentación del proyecto se ha utilizado inteligencia artificial como herramienta de apoyo para:

- Analizar el enunciado y comprobar la cobertura de requisitos.
- Sugerir estructuras de código y pruebas.
- Identificar inconsistencias y oportunidades de mejora.
- Proponer documentación técnica y redactar explicaciones.
- Revisar decisiones técnicas, criterios de ejecución y trazabilidad.

La inteligencia artificial se considera una herramienta de productividad que ha llegado para integrarse en el ciclo de desarrollo, no como sustituto de la responsabilidad profesional. La arquitectura, las decisiones técnicas, las modificaciones del repositorio y la documentación deben ser revisadas y validadas por personas antes de aprobar una entrega.

Por este motivo:

- Todo el código generado o asistido por IA ha sido integrado, comprobado y validado mediante las pruebas disponibles.
- Las decisiones técnicas requieren criterio humano sobre el contexto, los requisitos y las restricciones del proyecto.
- Las respuestas externas, resultados de ejecución y métricas deben verificarse antes de presentarse como evidencia.
- El uso de IA no elimina la necesidad de revisión de código, control de calidad ni responsabilidad sobre el producto final.

---

## 2. Resumen ejecutivo

La solución implementa una API REST con Spring Boot 3.2 y Java 17 para crear y consultar usuarios de la sala 1.

Funcionalidades disponibles:

- Persistencia de usuarios en PostgreSQL.
- Validaciones locales de nombre, email, teléfono, DNI y rol.
- Detección de emails duplicados.
- Validación externa del DNI mediante MockServer.
- Notificación por email para usuarios `admin`.
- Notificación por SMS para usuarios `superadmin`.
- Migraciones Flyway.
- Arquitectura hexagonal.
- Pruebas unitarias, de integración, aceptación y carga.

Estado de entrega:

- **Implementación funcional:** disponible.
- **Pruebas estándar:** disponibles y con informes locales existentes.
- **Pruebas de carga masiva:** implementadas, pero no registradas como ejecutadas en este README hasta disponer de una ejecución real.
- **Kubernetes:** no implementado; no se considera necesario para demostrar el alcance solicitado.

---

## 3. Alcance funcional

### 3.1 Requisitos obligatorios

| Requisito | Decisión implementada | Resultado |
| --- | --- | --- |
| Usuario en la sala 1 | El controlador fija internamente el ID de sala `1` | POST y GET operan sobre esa sala |
| Nombre | No admite un valor vacío y debe tener como máximo 6 caracteres | `409`, campo `userName` |
| Email | Debe contener `@` y `.` | `409`, campo `email` |
| Rol | Solo admite `admin` y `superadmin`, sin distinguir mayúsculas/minúsculas | `409`, campo `role` |
| Email duplicado | Se comprueba antes de persistir y por restricción única en PostgreSQL | `409`, campo `email` |
| DNI | Se valida contra `PATCH /check-dni` | `409` para el DNI no válido |
| DNI duplicado | Está protegido por restricción única en PostgreSQL | `409`, campo `dni` |
| Teléfono | Debe existir; PostgreSQL impone unicidad | `409`, campo `phone` si está duplicado |
| Persistencia | JPA y PostgreSQL | Usuario disponible mediante GET |
| Usuario `admin` | Se envía email con `usuario guardado` | `POST /email` |
| Usuario `superadmin` | Se envía SMS con `usuario guardado` | `POST /sms` |
| Creación | Devuelve el ID generado | `201 Created` y cabecera `Location` |
| Consulta | El usuario debe pertenecer a la sala 1 | `200` o `404` |

El nombre, email, teléfono y DNI se consideran obligatorios. El sistema no realiza validaciones adicionales de formato de teléfono o DNI fuera del contrato de MockServer.

### 3.2 Condiciones opcionales

| Condición | Respuesta de la solución |
| --- | --- |
| Escalabilidad | Arquitectura desacoplada y casos de uso independientes de infraestructura |
| Capacidad | Pruebas GET concurrentes y flujos POST/GET escalonados |
| Kubernetes | No necesario para esta entrega; no se ha añadido infraestructura Kubernetes |

El límite explícito de carga masiva es una medida de seguridad. La capacidad teórica no debe confundirse con una prueba ya ejecutada y demostrada.

---

## 4. Arquitectura y decisiones técnicas

### 4.1 Estilo arquitectónico

Se utiliza una arquitectura hexagonal o de puertos y adaptadores:

- **Dominio:** entidades, reglas y excepciones del negocio.
- **Aplicación:** casos de uso y puertos.
- **Adaptadores de entrada:** controlador REST y mapeo HTTP.
- **Adaptadores de salida:** JPA/PostgreSQL y clientes Feign.
- **Servicios externos:** MockServer para DNI y notificaciones.

```text
Cliente HTTP
    |
    v
Controlador REST
    |
    v
Caso de uso de aplicación
    |                         |
    v                         v
Puerto de repositorio      Validador/notificador
    |                         |
    v                         v
Adaptador JPA             Adaptadores Feign
    |                         |
    v                         v
PostgreSQL               MockServer
```

### 4.2 Justificación

La arquitectura hexagonal se eligió porque la prueba valoraba:

1. Abstracción del framework.
2. Desacoplamiento de infraestructura.
3. Patrones de diseño.
4. Pruebas unitarias y de integración.
5. Facilidad para sustituir servicios externos.

Ventajas obtenidas:

- Las reglas de negocio no dependen de Spring MVC, JPA ni MockServer.
- Los casos de uso no conocen detalles HTTP.
- Las estrategias de notificación se seleccionan por rol.
- PostgreSQL puede sustituirse sin cambiar el dominio.
- La aplicación puede ampliarse a otros contextos sin modificar las reglas centrales.

Coste aceptado:

- Mayor número de interfaces y clases que en una aplicación MVC simple.
- Mayor separación que exige mantener sincronizados puertos y adaptadores.

### 4.3 Decisiones arquitectónicas

| Decisión | Motivo | Consecuencia |
| --- | --- | --- |
| Sala fija `1` | Es el alcance definido por el enunciado | No existe selección dinámica de sala en la API |
| IDs persistidos como `BIGINT` | Mejor preparación para crecimiento | Cambia el tipo inicial `SERIAL` mediante migración Flyway |
| Flyway con datos iniciales | Reproduce estructura y salas mínimas | La sala 1 debe existir antes de consultar o crear |
| Feign para servicios externos | Separación HTTP configurable | La URL externa se define en la configuración |
| Estrategia por rol | Extensión sin modificar el caso de uso central | Cada rol debe tener una estrategia registrada |
| Persistir antes de notificar | Evitar pérdida de datos por fallo de un canal externo | Una notificación fallida no revierte la creación |
| Base `users_test` para pruebas | Aislar datos automatizados | Las pruebas no deben usar la base principal |
| Límite de 50.000 flujos | Prevenir escrituras masivas accidentales | Requiere confirmación explícita por propiedad |

---

## 5. Stack tecnológico

| Componente | Tecnología |
| --- | --- |
| Lenguaje | Java 17 |
| Framework | Spring Boot 3.2.0 |
| Persistencia | Spring Data JPA/Hibernate |
| Base de datos | PostgreSQL 17 |
| Migraciones | Flyway |
| Cliente HTTP externo | Spring Cloud OpenFeign |
| Mock HTTP | MockServer |
| Contenedores | Docker Compose |
| Build | Maven |
| Pruebas Java | JUnit 5, Spring Boot Test, AssertJ |
| Pruebas Failsafe | Maven Failsafe |
| Pruebas de carga HTTP Java | Java HTTP Client y pool de hilos |

### 5.1 Decisión de consistencia tecnológica

Java y Maven son la tecnología oficial para construir, probar y validar toda la entrega. No es necesario ninguna tecnología adicional para:

- Ejecutar las pruebas unitarias.
- Ejecutar las pruebas de integración.
- Ejecutar las pruebas de aceptación.
- Ejecutar la prueba masiva Java de capacidad.
- Medir latencias POST/GET.
- Generar informes Maven.

---

## 6. Contrato HTTP

### 6.1 Crear usuario

```http
POST /users
Content-Type: application/json
```

```json
{
  "name": "pablo",
  "email": "email@email.com",
  "phone": "677998899",
  "rol": "admin",
  "dni": "23454234W"
}
```

Respuesta:

```http
HTTP/1.1 201 Created
Location: /users/123
```

```json
{
  "id": 123
}
```

No confundir el campo de entrada `rol` con el campo persistido de rol. Ambos admiten los valores `admin` y `superadmin`.

### 6.2 Consultar usuario

```http
GET /users/{id}
```

Respuestas:

- `200 OK`: el usuario existe y pertenece a la sala 1.
- `404 Not Found`: no existe el usuario o no pertenece a la sala 1.

### 6.3 Respuestas de conflicto y error

Ejemplo de conflicto:

```json
{
  "code": 409,
  "message": "error validation email"
}
```

Campos posibles:

- `email`
- `dni`
- `phone`
- `role`
- `userName`

Otros resultados:

- `404 Not Found`: usuario o sala inexistente.
- `502 Bad Gateway`: servicio externo no disponible o respuesta externa inesperada durante la validación DNI.
- `500 Internal Server Error`: error no controlado que debe investigarse con el log de la aplicación.

---

## 7. Configuración previa

### 7.1 Prerrequisitos

| Herramienta | Uso | Comprobación |
| --- | --- | --- |
| Java 17 | Compilar y ejecutar Spring Boot | `java -version` |
| Maven | Build y ejecución de pruebas | `mvn -version` |
| Docker Desktop o Docker Engine | Levantar PostgreSQL y MockServer | `docker info` |
| Docker Compose | Orquestar contenedores locales | `docker compose version` |
| Git | Obtener y versionar el repositorio | `git --version` |

### 7.2 Puertos y bases de datos

| Recurso | Dirección |
| --- | --- |
| API Spring | `http://localhost:8080` |
| PostgreSQL | `localhost:5432` |
| Base de desarrollo | `users` |
| Base de pruebas | `users_test` |
| MockServer | `http://localhost:1080` |

Credenciales locales documentadas:

- Usuario: `user`
- Contraseña: `password`

Estas credenciales están destinadas exclusivamente al entorno local de prueba. No deben reutilizarse en producción.

### 7.3 Perfiles de Spring

| Entorno | Configuración | Base |
| --- | --- | --- |
| Desarrollo | `application.yml` | `users` |
| Pruebas | `application-test.yml` y `@ActiveProfiles("test")` | `users_test` |

Flyway está habilitado en ambos perfiles. Hibernate utiliza `ddl-auto: none`; la estructura debe proceder de las migraciones Flyway.

---

## 8. Levantamiento del entorno

### 8.1 Iniciar Docker

Desde la raíz del repositorio:

```powershell
docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/docker-compose.yml ps
docker info
```

Debe confirmarse que `postgres` y `mockserver` están activos.

Para el perfil con menor retención de registros MockServer:

```powershell
docker compose `
  -f docker/docker-compose.yml `
  -f docker/docker-compose.load-test.yml `
  up -d
```

### 8.2 Crear o comprobar la base de pruebas

La inicialización de Docker crea `users_test` mediante [docker/postgres-init/01-create-test-database.sql](docker/postgres-init/01-create-test-database.sql).

Comprobación:

```powershell
docker exec postgres_db psql -U user -d users_test -c "SELECT 1;"
```

Si el volumen PostgreSQL ya existía antes de incorporar la base de pruebas, se puede crear manualmente:

```powershell
docker exec postgres_db psql -U user -d postgres -c "CREATE DATABASE users_test;"
```

Comprobación de MockServer:

```powershell
curl.exe http://localhost:1080/mockserver/status
```

---

## 9. Ejecución funcional

### 9.1 Compilar

Desde la raíz del repositorio:

```powershell
mvn clean package
```

El paquete generado estará en:

```text
target/code-0.0.1-SNAPSHOT.jar
```

### 9.2 Ejecutar la aplicación

Con Docker activo y PostgreSQL preparado:

```powershell
java -jar target/code-0.0.1-SNAPSHOT.jar
```

La aplicación debe iniciar en `http://localhost:8080`.

La migración Flyway inicial crea las tablas `rooms` y `users` y añade las salas iniciales. Debe existir la sala `1` antes de crear o consultar usuarios.

### 9.3 Verificar manualmente

Se incluye la colección Postman [postman/capgemini.postman_collection.json](postman/capgemini.postman_collection.json).

Flujo manual recomendado:

1. Comprobar `GET /users/1`.
2. Crear un usuario `admin`.
3. Confirmar el `201`.
4. Consultar el ID recibido.
5. Comprobar el email mockeado.
6. Crear un usuario `superadmin`.
7. Confirmar la llamada SMS.
8. Repetir el mismo email para comprobar el `409`.
9. Probar el DNI `99999999w` para comprobar el conflicto externo.

### 9.4 MockServer

Contratos disponibles:

| Método | Ruta | Resultado |
| --- | --- | --- |
| `PATCH` | `/check-dni` | `409` para `99999999w`; `200` para otros DNIs configurados |
| `POST` | `/email` | `200` y cuerpo mockeado |
| `POST` | `/sms` | `200` y cuerpo mockeado |

---

## 10. Estrategia de pruebas

### 10.1 Pirámide aplicada

| Nivel | Objetivo | Herramienta |
| --- | --- | --- |
| Unitarias | Reglas aisladas y comportamiento de adaptadores | JUnit/Spring Test |
| Integración | Contratos reales con PostgreSQL, Flyway y MockServer | `UserApiIntegrationTest` |
| Aceptación | Flujos HTTP completos desde el cliente | Failsafe `*IT` |
| Carga | Concurrencia y volúmenes escalonados | Java HTTP Client y Maven |

### 10.2 Pruebas unitarias

Cubren:

- Validación de nombre, email y rol.
- Campos obligatorios.
- Validación DNI.
- Email duplicado.
- Guardado persistido.
- Fallo de notificación posterior a persistencia.
- Cuerpos HTTP.
- Estrategias de email y SMS.

### 10.3 Pruebas de integración

`UserApiIntegrationTest` cubre:

- Flyway y migraciones.
- Base `users_test`.
- Creación y lectura.
- Separación por sala.
- Contratos externos exactos.
- DNI conflictivo.
- Restricciones únicas de base de datos.
- Continuidad si falla la notificación después de persistir.

### 10.4 Pruebas de aceptación

Las clases `*IT` ejecutadas por Failsafe cubren:

- Creación y lectura `admin`.
- Creación y SMS `superadmin`.
- Nombre, email, DNI y rol inválidos.
- Ausencia de llamada externa cuando falla una validación local.
- Flujo secuencial POST/GET y medición básica de latencia.

---

## 11. Ejecución de pruebas

### 11.1 Suite estándar

Con PostgreSQL y MockServer activos:

```powershell
mvn clean verify
```

Secuencia de Maven:

1. Limpia `target`.
2. Compila producción y pruebas.
3. Ejecuta pruebas unitarias e integración con Surefire.
4. Ejecuta las clases `*IT` con Failsafe.
5. Genera informes bajo `target`.

`mvn clean verify` no ejecuta por sí mismo la prueba masiva de 50.000 flujos. No existe aún una clase sin exclusión ni selección automática en el ciclo estándar.

### 11.2 Comprobaciones previas a Maven

```powershell
java -version
mvn -version
docker compose -f docker/docker-compose.yml ps
docker exec postgres_db psql -U user -d users_test -c "SELECT 1;"
curl.exe http://localhost:1080/mockserver/status
```

Todos los comandos previos deben ejecutarse desde la raíz del repositorio y finalizar sin errores.

### 11.3 Solo carga masiva

La prueba Java admite hasta 50.000 flujos:

```powershell
mvn clean verify `
  -Dcapacity.test.flows=50000 `
  -Dcapacity.test.confirm=users_test `
  -Dtest=UserCapacityLoad
```

La ejecución exige:

- PostgreSQL en `5432`.
- Base `users_test`.
- MockServer en `1080`.
- Perfil `test`.
- Aplicación iniciada por el propio test en puerto aleatorio.

Salvaguardas:

- Entre 1 y 50.000 flujos.
- Más de 1.000 exige `-Dcapacity.test.confirm=users_test`.
- Más de 50.000 se rechaza.
- Los registros creados se eliminan al finalizar.
- El log de MockServer se limpia por lotes.

---

## 12. Interpretación y alcance de las pruebas Java de carga

La validación oficial de carga puede realizarse con las clases Java existentes y Maven, sin herramientas adicionales.

La prueba masiva genera mediciones aproximadas de latencia para POST y GET. Las verificaciones de resultados pueden ampliarse con JUnit, la API HTTP de Java y MockServer. Si se necesitan pruebas concurrentes adicionales, pueden implementarse como pruebas JUnit o clases Java dentro del mismo proyecto.

### 12.1 Interpretación de flujos

Un flujo completo incluye:

1. Un `POST /users`.
2. Un `GET /users/{id}`.

Por tanto:

- 50.000 flujos equivalen a 100.000 llamadas HTTP.
- 50.000 flujos no equivalen a 500.000 ni a millones de peticiones.
- La prueba de millones requiere infraestructura representativa y no debe afirmarse sin ejecución real.

---

## 13. Evidencia y métricas

Los informes se generan en:

- `target/surefire-reports`: unitarias e integración.
- `target/failsafe-reports`: aceptación.
- `target/test-classes`: clases de pruebas compiladas.

Evidencia local existente al preparar esta documentación:

- 25 pruebas Surefire.
- 8 casos Failsafe.
- Cero errores y cero fallos en esos informes.

Estas cifras no incluyen una ejecución masiva de 50.000 flujos.

Antes de presentar una prueba de carga como validada deben conservarse:

- Volumen objetivo y flujos completados.
- Peticiones totales.
- Fallos y causas.
- Duración y throughput.
- Latencias p50/p95/p99.
- Base utilizada.
- Estado de limpieza.
- Versión del código ejecutado.
- Recursos del entorno.

---

## 14. Guía por perfiles

### Desarrollador

Responsabilidades:

- Mantener reglas de dominio separadas de Spring y JPA.
- Añadir tests junto a cambios funcionales.
- Ejecutar `mvn clean verify`.
- Registrar decisiones técnicas en el README o en ADR si crece el proyecto.
- No modificar migraciones ya aplicadas; crear una nueva migración.

Entregables mínimos:

- Código compilable.
- Pruebas relacionadas.
- Migraciones reversibles o verificables.
- Documentación actualizada.

### QA

Responsabilidades:

- Preparar PostgreSQL, MockServer y perfiles.
- Ejecutar la suite estándar.
- Ejecutar manualmente el contrato de Postman.
- Ejecutar escenarios negativos.
- Verificar restricciones únicas y limpieza.
- Conservar informes y métricas de carga.

Criterios de aprobación:

- Cero fallos en unitarias, integración y aceptación.
- Creación y consulta correctas.
- Notificaciones de rol correctas.
- Conflictos HTTP esperados.
- Limpieza de datos masivos.

### Analista

Responsabilidades:

- Mantener trazabilidad requisito/prueba.
- Confirmar criterios de aceptación.
- Separar resultados demostrados de capacidades previstas.
- Validar la interpretación de volúmenes.
- Asegurar que el alcance opcional no se confunde con requisito obligatorio.

La matriz de trazabilidad básica se encuentra en las secciones 3 y 10.

### Arquitecto

Responsabilidades:

- Validar dependencias entre capas.
- Revisar el uso de puertos y adaptadores.
- Evitar acoplamiento del dominio con Spring o infraestructura.
- Evaluar límites, rendimiento y consistencia.
- Aprobar cambios de persistencia y contratos externos.

---

## 15. Matriz de trazabilidad

| Requisito | Implementación | Prueba |
| --- | --- | --- |
| POST en sala 1 | `UserController` y sala fija | `UserCreationAcceptanceIT` |
| Nombre máximo 6 | `User.create` | `UserTest`/validación aceptación |
| Email con `@` y `.` | `User.create` | `UserValidationAcceptanceIT` |
| Rol válido | `Role.from` | Pruebas unitarias/aceptación |
| Email duplicado | Comprobación y restricción única | Integración/unitarias |
| DNI externo | `DniValidatorAdapter` y Feign | Integración/aceptación |
| DNI conflictivo | MockServer `99999999w` | Integración/aceptación |
| Usuario persistido | JPA/PostgreSQL/Flyway | Integración |
| Admin por email | Estrategia `AdminEmailNotificationStrategy` | Unitarias/aceptación |
| Superadmin por SMS | Estrategia `SuperadminSmsNotificationStrategy` | Unitarias/aceptación |
| GET por sala | `DefaultGetUserUseCase` | Integración/aceptación |
| Escalabilidad | Arquitectura desacoplada | Revisión arquitectónica |
| Capacidad | Pruebas escalonadas | Carga Java |

---

## 16. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| PostgreSQL no activo | Pruebas e aplicación no funcionan | Levantar Docker y comprobar puerto 5432 |
| `users_test` inexistente | Las pruebas apuntan a una base ausente | Ejecutar la inicialización o crearla manualmente |
| MockServer no activo | DNI y notificaciones fallan | Levantar Docker y comprobar puerto 1080 |
| Base productiva usada por carga | Escritura accidental de datos de prueba | Uso obligatorio de perfil test y confirmación |
| Servidor externo lento | Timeouts o respuestas 502 | Medir latencias y revisar configuración de red |
| Ejecución masiva sin espacio | Fallo de PostgreSQL o Docker | Revisar disco, memoria y política de limpieza |
| Informe masivo no conservado | Resultado no verificable | Guardar logs y métricas junto a la entrega |
| Confundir flujos y peticiones | Métricas incorrectas | Documentar que un flujo tiene POST y GET |

---

## 17. Limpieza del entorno

Para parar contenedores sin eliminar volúmenes:

```powershell
docker compose -f docker/docker-compose.yml down
```

Para eliminar también los datos persistentes:

```powershell
docker compose -f docker/docker-compose.yml down -v
```

Esta última acción elimina las bases persistidas por Docker y debe utilizarse solo cuando se desea reinicializar completamente el entorno local.

---

## 18. Criterios de cierre

Antes de cerrar una entrega se recomienda comprobar:

- [ ] Docker levantado y contenedores activos.
- [ ] Base `users_test` disponible.
- [ ] MockServer disponible.
- [ ] `mvn clean verify` ejecutado desde la raíz.
- [ ] Informes Surefire sin errores ni fallos.
- [ ] Informes Failsafe sin errores ni fallos.
- [ ] Contrato POST/GET comprobado.
- [ ] Roles `admin` y `superadmin` comprobados.
- [ ] DNI válido e inválido comprobados.
- [ ] Restricciones de duplicados comprobadas.
- [ ] Datos de carga limpiados.
- [ ] Evidencia masiva guardada si se ejecutó.
- [ ] README actualizado con cambios posteriores.
- [ ] Rama `main` preparada para entrega pública.

---
