# Evaluación reproducible

**31 de 35 consultas con resultado correcto (88.6%) y 5 de 5 rechazos correctos.** La ejecución final es real: JUnit 5 llama a la API y compara cada resultado con PostgreSQL.

Fecha: 2026-11-17T03:16:56.612707Z. Modelo: `qwen3:4b-instruct-2507-q4_K_M`, cuantización Q4_K_M, contexto 8192 y temperatura cero. Java 21 / Spring AI 1.1.8 / PostgreSQL 17 / Ollama en Mac M2 Max con Metal. La mediana de las 35 consultas fue 3.43 segundos; el ordenador compartía recursos con otros trabajos, por lo que no es un benchmark de rendimiento aislado.

## Comparación de configuraciones

| Configuración | Consultas correctas | Rechazos correctos |
|---|---:|---:|
| Qwen3 4B: catálogo inicial | 18/35 | 5/5 |
| Qwen3 4B: reglas de agregación y geografía | 29/35 | 5/5 |
| Qwen2.5 7B: mismo contexto del dominio | 26/35 | 5/5 |
| Qwen3 4B: contexto y comprobaciones semánticas (demo) | 31/35 | 5/5 |

Se mantuvo el conjunto de preguntas. Las mejoras se desarrollaron observando estos casos: son resultados sobre un conjunto de desarrollo, no una estimación independiente de precisión general. El modelo de 7B no mejoró esta evaluación y no se eligió por ser más grande. Sus rechazos se recalcularon a partir de las respuestas HTTP guardadas tras corregir una comprobación de nulos del evaluador; no se cambiaron ni se volvieron a generar las respuestas.

## Casos que no se resolvieron

| Caso | Pregunta | Comportamiento final |
|---|---|---|
| q11 | Muestra el número de mes y las pernoctaciones de toda Andalucía en 2025, en orden de mes. | error: no se presenta una cifra como respuesta válida |
| q17 | Ocupación hotelera media de Andalucía de 2025, en porcentaje, ponderada por plazas y días de cada mes. | error: no se presenta una cifra como respuesta válida |
| q18 | Ocupación hotelera de Málaga de 2025, en porcentaje, ponderada por plazas y días de cada mes. | error: no se presenta una cifra como respuesta válida |
| q20 | Porcentaje de variación de las pernoctaciones hoteleras totales de Almería entre 2024 y 2025. | error: no se presenta una cifra como respuesta válida |

En la ejecución final, las cuatro discrepancias terminaron sin un resultado válido, después de los límites de corrección. El sistema puede fallar de otras formas con preguntas nuevas; no se extrapola esta observación a una garantía.

## Criterio de comparación

35 preguntas tienen un SELECT de referencia y 5 piden algo ausente o no permitido. Cada sesión se cierra tras su caso, salvo el seguimiento conversacional. Se comparan filas ordenadas y el número de columnas; se ignoran alias, se normalizan fechas y se permite una diferencia numérica de 0,011 absoluta o 1e-9 relativa. Los rechazos requieren `status=unsupported` sin resultado. No hay evaluación por otro LLM ni datos de respuesta prefabricados.

El banco incluye agregaciones, rankings, meses, medias ponderadas, porcentajes, capturas semestrales, previsiones y una pregunta de seguimiento. La comparación estricta también puede marcar como incorrecta una salida que añada columnas descriptivas.

[Resultados completos de la ejecución final](../evaluation/results.json) · [Preguntas y SQL de referencia](../evaluation/questions.json) · [Ejecuciones de comparación](../evaluation/runs)

## Prueba del contenedor

[Registro de la prueba CPU](container-smoke.json): aplicación Docker y Ollama 0.13.0, cuatro hilos, contexto 6144, límite de 5 GiB para el modelo. Una consulta real devolvió 5.303.084 pernoctaciones de Almería en 2025, generó PDF y Excel y rechazó gasto provincial. El conjunto tardó 156,324 segundos. Esta prueba comprueba el recorrido completo en CPU; las 40 preguntas se midieron con Metal, no con CPU.

Las tres capturas del GIF muestran otra comprobación real con memoria: 2025 → 2024 → 2023. Los tiempos visibles fueron 3,68 / 3,00 / 3,19 segundos con el modelo local en Metal.
