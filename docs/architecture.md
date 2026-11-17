# Ejecución y decisiones

## Flujo de una consulta

1. Se comprueba si la pregunta pide un desglose ausente, una escritura o datos personales.
2. pgvector recupera cuatro documentos del catálogo, incrustados con `nomic-embed-text`.
3. Spring AI llama al modelo con las herramientas `listTables`, `getSchema` y `runReadonlyQuery`. No se interpretan como herramientas bloques de texto que el modelo haya escrito: se utiliza la interfaz de llamadas nativa de Ollama y Spring AI.
4. El analizador acepta un único `SELECT`, incluidas CTE de lectura, sobre tablas y funciones autorizadas. Rechaza escritura anidada, `SELECT INTO`, bloqueos, esquemas internos, funciones de tabla y conversiones no autorizadas. El tamaño y el tiempo del propio análisis están limitados.
5. Las reglas del dominio revisan algunos errores de indicador, porcentajes y capturas. Una regla fallida vuelve al modelo como un error que puede corregir. Estas comprobaciones son parciales y no equivalen a una prueba de corrección semántica.
6. PostgreSQL ejecuta con un usuario que solo puede leer `analytics`, transacción de lectura, timeout de 2,5 segundos y límite exterior de 201 filas. La fila adicional detecta si la salida debe cortarse a 200.
7. La tabla y Chart.js usan el resultado de PostgreSQL. El resumen es determinista; no se solicita al modelo que invente cifras o redacte resultados no consultados.

El máximo es de cuatro rondas del modelo y dos intentos de SQL. No se permite ejecutar una tercera consulta encubierta como corrección. La concurrencia de demostración está limitada a uno.

## Permisos y sesiones

`analytics_reader` no es superusuario, no puede crear bases ni roles, no puede escribir en tablas analíticas ni acceder a `rag`. `rag_writer` solo puede gestionar la tabla de embeddings. La cuenta administradora solo inicializa el contenedor; el servicio no la usa.

La sesión utiliza una cookie HttpOnly y SameSite Strict, conserva cinco turnos válidos y no se serializa a disco. La exportación comprueba el identificador del último resultado de esa misma sesión y devuelve `409` si ha cambiado o ha caducado. El historial se borra con `DELETE /api/session`.

No hay inicio de sesión multiusuario ni aislamiento por organización: es una demostración local con estadísticas públicas. Los puertos expuestos están restringidos a localhost. Para publicarla en un servidor harían falta autenticación, HTTPS, cuotas y una revisión adicional del despliegue.

## API

| Método y ruta | Uso |
|---|---|
| `GET /api/catalog` | Tablas, columnas y grano real |
| `POST /api/chat` | JSON con `question`, hasta 700 caracteres |
| `GET /api/export/pdf?id=...` | PDF del último resultado de la sesión |
| `GET /api/export/xlsx?id=...` | Excel del mismo resultado |
| `DELETE /api/session` | Borra el contexto de conversación |
| `GET /actuator/health` | Disponibilidad de la aplicación |

Una pregunta fuera del catálogo devuelve `200` con `status=unsupported`, sin resultado. Si el modelo no obtiene un SQL válido, devuelve `status=error`. Una petición mientras el analista está ocupado recibe `429`. Un fallo del modelo o la base recibe `503`, sin detalles de conexión en la respuesta.

## Exportaciones

Excel conserva números y booleanos con sus tipos; los textos se escriben como cadenas, nunca como fórmulas. Incluye una segunda hoja con pregunta, SQL y procedencia. El PDF incrusta DejaVu Sans, repite cabeceras y divide tablas anchas en grupos de cinco columnas. Los textos de más de 400 caracteres se abrevian en PDF y se conservan completos en Excel.

## Reproducibilidad

Los CSV se cargan al crear el volumen PostgreSQL. Cambiar los CSV no modifica automáticamente un volumen existente. Para conservar datos de trabajo, crea otro proyecto Compose o exporta la base antes de realizar una reinicialización.

La evaluación publicada se ejecutó con Java 21, Spring AI 1.1.8, PostgreSQL 17 y Ollama en un Mac M2 Max. La temperatura es cero; las diferencias de versión, cuantización y runtime pueden cambiar el resultado. Las pruebas de código y SQL se ejecutan en la CI; el banco del LLM es una ejecución opcional y real.
