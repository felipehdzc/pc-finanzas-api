# API de finanzas personales: gastos e ingresos

API REST para gestionar ingresos y gastos personales en euros. Proyecto Maven con
Java 21, Spring Boot 3.5.16, Spring Data JPA/Hibernate y H2 en memoria.

## Requisitos

- JDK 21, con `JAVA_HOME` apuntando al JDK.
- Maven 3.6.3 o posterior.
- Acceso a Maven Central para descargar las dependencias en la primera compilación.
- `curl` para ejecutar los ejemplos, si se desea.

Comprueba las versiones con `java -version` y `mvn -version`.

## Compilar, probar y ejecutar

Desde la raíz del proyecto:

```bash
# Ejecutar las pruebas unitarias
mvn test

# Ejecutar las pruebas y generar el JAR ejecutable con sus dependencias
mvn package

# Iniciar el JAR
java -jar target/finanzas-api-0.0.1-SNAPSHOT.jar
```

También puedes iniciar la aplicación durante el desarrollo con:

```bash
mvn spring-boot:run
```

Usa una sola de las dos formas de ejecución a la vez. La API escucha en
`http://localhost:8080`. Detén la aplicación con `Ctrl+C`.

La configuración está en `src/main/resources/application.properties`. La base de
datos H2 utiliza `jdbc:h2:mem:finanzas`: **los datos se pierden al detener la
aplicación**. Cada arranque comienza sin movimientos.

## Estructura

```text
pom.xml
src/main/java/com/example/finanzas/
├── FinanzasApiApplication.java
├── config/       # Configuración del reloj
├── domain/       # Entidad JPA FinancialTransaction y enumeración del tipo
├── dto/          # Peticiones y respuestas de la API
├── repository/   # Repositorio Spring Data JPA
├── service/      # Operaciones y reglas de negocio
├── controller/   # Endpoints REST
└── exception/    # Excepciones y manejo centralizado de errores
src/main/resources/application.properties
src/test/java/com/example/finanzas/
```

El controlador recibe y devuelve DTOs; la entidad JPA no se expone directamente.
El servicio utiliza un repositorio JPA y un `Clock` inyectado. El reloj de la
aplicación usa la zona horaria de la JVM; en las pruebas se utiliza un reloj fijo.

## Reglas de negocio

| Campo | Regla |
| --- | --- |
| `id` | Generado automáticamente; se conserva al actualizar. |
| `concept` | Obligatorio, no vacío ni compuesto solo por espacios, máximo 100 caracteres. |
| `description` | Opcional, máximo 500 caracteres. |
| `amount` | Obligatorio, estrictamente positivo, máximo 12 dígitos enteros y 2 decimales. |
| `type` | Obligatorio: `INCOME` o `EXPENSE`. Se persiste como texto. |
| `date` | Obligatoria, formato `AAAA-MM-DD`; debe ser hoy o una fecha anterior. |

Los importes se representan con `BigDecimal` y se almacenan con precisión 14 y
escala 2. El máximo admitido es `999999999999.99`; no se redondean peticiones con
más de dos decimales para hacerlas válidas. Todos los importes están en euros y
son positivos: `INCOME` suma y `EXPENSE` resta. Se permite un balance negativo.

La creación y la actualización aplican las mismas reglas. Bean Validation valida
las peticiones; el servicio comprueba también las reglas de importe y fecha
cuando se invoca directamente.

`PUT` reemplaza todos los datos del movimiento, excepto su identificador. Para
borrar una descripción, envía `"description": null` o no incluyas ese campo; los
campos obligatorios deben aparecer en cada actualización.

## Endpoints

| Método | Ruta | Respuesta satisfactoria |
| --- | --- | --- |
| `POST` | `/api/transactions` | `201 Created`, movimiento creado y cabecera `Location`. |
| `GET` | `/api/transactions` | `200 OK`, lista de movimientos con filtros opcionales `from` y `to`; `[]` si no hay coincidencias. |
| `GET` | `/api/transactions/{id}` | `200 OK`, movimiento solicitado. |
| `PUT` | `/api/transactions/{id}` | `200 OK`, movimiento actualizado. |
| `DELETE` | `/api/transactions/{id}` | `204 No Content`, sin cuerpo. |
| `GET` | `/api/transactions/balance` | `200 OK`, totales de ingresos y gastos y balance. |

### Filtrar movimientos por fecha

El listado admite `from` y `to` en formato `AAAA-MM-DD`. Ambos límites son
inclusivos y los resultados conservan el orden ascendente por identificador.

| Parámetros | Movimientos devueltos |
| --- | --- |
| Ninguno | Todos los movimientos. |
| Solo `from` | Fecha igual o posterior a `from`. |
| Solo `to` | Fecha igual o anterior a `to`. |
| `from` y `to` | Fecha comprendida entre ambos extremos, incluidos. |

Si ambas fechas coinciden, se consultan los movimientos de ese día. Se pueden
utilizar límites futuros, por ejemplo el último día del mes en curso. Si no hay
coincidencias, se devuelve `[]` con estado `200 OK`.

Un intervalo invertido devuelve `400 Bad Request` con el mensaje
`La fecha 'from' no puede ser posterior a 'to'.` y un detalle en
`fieldErrors.from`. Las fechas con formato inválido también devuelven `400`, con
un detalle sobre el parámetro afectado.

```bash
# Todos los movimientos
curl -i 'http://localhost:8080/api/transactions'

# Movimientos de septiembre de 2026
curl -i 'http://localhost:8080/api/transactions?from=2026-09-01&to=2026-09-30'

# Desde el 1 de septiembre, incluido
curl -i 'http://localhost:8080/api/transactions?from=2026-09-01'

# Hasta el 30 de septiembre, incluido
curl -i 'http://localhost:8080/api/transactions?to=2026-09-30'

# Intervalo invertido: 400 Bad Request
curl -i 'http://localhost:8080/api/transactions?from=2026-09-30&to=2026-09-01'
```

### Ejemplos con curl

Con la aplicación iniciada, crea un ingreso:

```bash
curl -i -X POST http://localhost:8080/api/transactions \
  -H 'Content-Type: application/json' \
  -d '{
    "concept": "Nómina",
    "description": "Ingreso de enero",
    "amount": 2500.00,
    "type": "INCOME",
    "date": "2025-01-15"
  }'
```

La respuesta contiene el identificador generado y una cabecera `Location` que
apunta al recurso creado. El cuerpo tiene esta forma:

```json
{
  "id": 1,
  "concept": "Nómina",
  "description": "Ingreso de enero",
  "amount": 2500.00,
  "type": "INCOME",
  "date": "2025-01-15"
}
```

Crea un gasto:

```bash
curl -i -X POST http://localhost:8080/api/transactions \
  -H 'Content-Type: application/json' \
  -d '{
    "concept": "Supermercado",
    "description": "Compra semanal",
    "amount": 45.50,
    "type": "EXPENSE",
    "date": "2025-01-15"
  }'
```

Los siguientes ejemplos suponen que la base de datos estaba vacía y que los
identificadores devueltos son `1` y `2`. Sustitúyelos por los que recibas al crear
los movimientos.

Lista los movimientos y consulta el ingreso:

```bash
curl -i http://localhost:8080/api/transactions

curl -i http://localhost:8080/api/transactions/1
```

Actualiza completamente el gasto, cambiando su importe y borrando la descripción:

```bash
curl -i -X PUT http://localhost:8080/api/transactions/2 \
  -H 'Content-Type: application/json' \
  -d '{
    "concept": "Supermercado",
    "description": null,
    "amount": 50.00,
    "type": "EXPENSE",
    "date": "2025-01-15"
  }'
```

Consulta el balance:

```bash
curl -i http://localhost:8080/api/transactions/balance
```

Con los dos movimientos anteriores, después de la actualización, el resultado es:

```json
{
  "totalIncome": 2500.00,
  "totalExpense": 50.00,
  "balance": 2450.00
}
```

Sin movimientos, los tres importes son cero. La cantidad de ceros decimales en la
representación JSON puede variar sin cambiar el valor.

Elimina el gasto:

```bash
curl -i -X DELETE http://localhost:8080/api/transactions/2
```

## Errores

Un `@RestControllerAdvice` devuelve errores JSON uniformes, sin trazas de
excepciones. Consultar, actualizar o eliminar un identificador inexistente
devuelve `404 Not Found`. Las peticiones inválidas y las infracciones de las
reglas de negocio devuelven `400 Bad Request`.

Los errores contienen `timestamp`, `status`, `error`, `message`, `path` y
`fieldErrors`. Este último es un objeto que relaciona cada campo inválido con su
mensaje; puede estar vacío cuando el error no corresponde a un campo concreto.

Para comprobar el rechazo de un importe cero:

```bash
curl -i -X POST http://localhost:8080/api/transactions \
  -H 'Content-Type: application/json' \
  -d '{
    "concept": "Importe inválido",
    "amount": 0,
    "type": "EXPENSE",
    "date": "2025-01-15"
  }'
```

Para comprobar un recurso inexistente, consulta el gasto después de borrarlo:

```bash
curl -i http://localhost:8080/api/transactions/2
```

## Pruebas unitarias

Las pruebas utilizan JUnit 5 y Mockito, con el repositorio simulado y un reloj
fijo. Se ejecutan sin iniciar Spring ni ninguna base de datos. Las comprobaciones
de Bean Validation crean el validador directamente.

Cubren el rechazo de importes cero, negativos y con demasiados decimales; fechas
futuras; aceptación de la fecha de hoy; actualizaciones inválidas; recursos
inexistentes al consultar, actualizar y eliminar; y los balances con ingresos,
gastos, ausencia de movimientos y resultado negativo. Se comprueban valores,
excepciones e interacciones con el repositorio, incluido que los datos inválidos
no se guarden.

Para los filtros de fecha, comprueban el rechazo del intervalo invertido sin
consultar el repositorio, la selección de la consulta para cada combinación de
parámetros, los intervalos de un solo día y las listas vacías. Al simular el
repositorio con Mockito, estas pruebas verifican la lógica del servicio y la
consulta solicitada; no demuestran que la consulta real filtre correctamente en
la base de datos.

`mvn test` ejecuta las pruebas, y `mvn package` las ejecuta antes de empaquetar el
JAR. Los informes se generan en `target/surefire-reports/`.
