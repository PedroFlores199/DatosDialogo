# Fuentes y dependencias

DatosDialogo utiliza una captura de estadísticas públicas del INE preparada por TurismoLakehouse. [data/provenance.json](data/provenance.json) identifica los ficheros derivados y la revisión de origen; `data/ine-source-manifest.json` conserva las URL y huellas de las respuestas originales. Las previsiones son resultados del modelo de TurismoLakehouse, no datos oficiales del INE. No existe afiliación ni respaldo del INE.

Java / Spring Boot / Spring AI / Apache POI / Apache PDFBox se utilizan conforme a sus licencias. Las dependencias se resuelven mediante el `pom.xml`. PostgreSQL utiliza su licencia propia y pgvector su licencia PostgreSQL.

Chart.js 4.5.1 se distribuye localmente bajo MIT. Se conserva su licencia en `src/main/resources/static/vendor/Chart.js.LICENSE.md`. El archivo se verificó mediante la integridad SHA-512 publicada en npm.

DejaVu Sans se incrusta en los informes para representar texto en español. Su licencia original se conserva en `src/main/resources/fonts/LICENSE_DEJAVU`.

Los pesos de Qwen3-4B-Instruct-2507 y nomic-embed-text no se incluyen en Git. Ollama los descarga de sus repositorios; deben respetarse sus licencias originales. Qwen2.5-7B-Instruct se utilizó como comparación de desarrollo y no es el modelo predeterminado.

Las capturas y el GIF son comprobaciones de la aplicación ejecutada sobre los datos públicos incluidos. No contienen sesiones de cuentas externas ni fotografías o documentos personales.
