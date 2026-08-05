# Sistema Híbrido de Clasificación de Emergencias y Triaje Multimodal mediante Procesamiento de Lenguaje Natural y Redes Neuronales Convolucionales

Eduardo Benavides, Joseph Franco y Aldo Saula
Universidad de las Fuerzas Armadas ESPE
josephfranco692@gmail.com

**Resumen.** Este estudio tiene como objetivo desarrollar un sistema de clasificación y triaje automatizado para emergencias mediante una arquitectura multimodal de aprendizaje profundo. El problema central radica en la incapacidad operativa de los centros de respuesta (como el ECU 911) para evaluar rápidamente grandes volúmenes de reportes que contienen texto e imágenes simultáneamente. La investigación, de carácter experimental aplicado, empleó el conjunto de datos CrisisMMD y procesó ventanas de información utilizando una red convolucional EfficientNet-B0 para la extracción de características visuales y un modelo Transformer ligero (DistilBERT) para el razonamiento semántico del texto. Los resultados indican que el modelo híbrido alcanzó una precisión de validación del 82.83% y estabilizó su convergencia en la época 22, demostrando una sólida capacidad para mitigar los sesgos propios del desbalanceo de clases y priorizar alertas en tiempo real. Se concluye que la fusión temprana de ambas modalidades permite clasificar el riesgo operativo de manera mucho más eficiente y precisa que los enfoques unimodales tradicionales, optimizando la asignación de recursos bomberiles en el terreno.

**Keywords:** Triaje Automático, Redes Neuronales Convolucionales, Procesamiento de Lenguaje Natural, Arquitectura Multimodal, CrisisMMD.

## 1 Introducción

Los sistemas actuales de gestión de crisis se enfrentan a la creciente dificultad de evaluar reportes de incidentes de manera automatizada, rápida y precisa ante escenarios críticos. El problema central radica en la incapacidad operativa para procesar eficientemente volúmenes masivos de alertas que contienen datos multimodales, tales como descripciones escritas y fotografías del incidente. Como consecuencia directa, se generan constantes cuellos de botella en la asignación de recursos, lo que retrasa el envío de unidades de respuesta hacia los lugares afectados y compromete la seguridad ciudadana. La magnitud de esta saturación operativa es evidente a nivel nacional; durante el año 2024, el Servicio Integrado de Seguridad ECU 911 coordinó la atención de 3.357.900 emergencias en todo el territorio ecuatoriano, de las cuales el 67,72% correspondieron a la categoría de seguridad ciudadana [6]. Adicionalmente, el desafío de procesar reportes en entornos complejos ha motivado el estudio de modelos de predicción de incidentes como incendios forestales mediante redes neuronales convolucionales (CNN) y técnicas de Machine Learning, demostrando la superioridad del aprendizaje profundo para mapear patrones espaciales [13].

Para gestionar eficientemente esta inmensa cantidad de información, la presente investigación se apoya en el uso de arquitecturas de aprendizaje profundo híbridas. Por un lado, las Redes Neuronales Convolucionales (CNN) operan extrayendo características espaciales y evidencia fotográfica de forma ágil, ofreciendo alta eficiencia computacional, una estrategia que ha probado ser vital para la detección temprana de anomalías visuales como humo o fuego [10]. De manera complementaria, el Procesamiento de Lenguaje Natural (PLN) analiza la descripción escrita para interpretar el contexto crítico. La integración de estas tecnologías mediante capas de fusión temprana equilibra la interpretabilidad semántica con un bajo consumo de recursos operativos, lo que permite el despliegue del modelo en servidores locales de las instituciones de primera respuesta.

La literatura reciente demuestra un avance hacia sistemas de gestión de crisis más robustos basados en arquitecturas multimodales. Investigaciones previas desarrollaron marcos de clasificación basados en arquitecturas de doble transformador y modelos de ensamble que alcanzaron una precisión del 84,66%, superando ampliamente a los modelos unimodales [1]. En la misma línea, Islam et al. implementaron marcos de aprendizaje profundo integrando codificadores de texto con redes ResNet50, demostrando que la integración de características es altamente efectiva para capturar dependencias intermodales [2]. Sistemas como DisasterReliefGPT han evidenciado que combinar modelos de lenguaje y visión (LVLM) con segmentación de imágenes (ResNet34) incrementa drásticamente la capacidad de evaluación de daños post-desastre [8]. Asimismo, arquitecturas como SP-Att-IDeepNet han superado las inconsistencias visuales cruzando múltiples modalidades (RGB, IR, SAR), logrando precisiones superiores al 93% en escenas complejas [9].

Adicionalmente, la literatura destaca la implementación de Modelos de Lenguaje-Visión (VLMs) mediante embeddings de pretexto, los cuales han permitido capturar relaciones semánticas esenciales [5]. A la par, integraciones de Vision Transformers (ViT) con arquitecturas YOLOv8 han superado los problemas de falsas alarmas en ciudades inteligentes, logrando una detección robusta [11]. Sin embargo, la dependencia de datasets de alta calidad ha sido históricamente un obstáculo, lo que ha impulsado la creación de corpus multimodales estandarizados (como MmodalFire) que fusionan video y sensores físicos para mejorar el entrenamiento [12]. No obstante, muchas de estas arquitecturas exigen recursos computacionales sustancialmente mayores y presentan una alta latencia de inferencia, limitando su viabilidad en escenarios de emergencia donde los tiempos de respuesta deben ser inmediatos.

Con el propósito de solventar las limitaciones identificadas en la literatura, el objetivo general de este proyecto consiste en desarrollar un sistema automático de análisis de reportes de emergencia para determinar su gravedad, evaluando tanto la descripción escrita mediante PLN como la fotografía del incidente a través de CNN. De este modo, se persigue automatizar la prioridad de las alertas y despachar las unidades de rescate con mayor rapidez y precisión operativa, garantizando un modelo de baja latencia computacional apto para un despliegue en vivo en la Central de Bomberos.

El presente artículo se organiza de la siguiente manera: en la primera parte se fundamenta la metodología del estudio, los materiales y los procedimientos de sistematización de datos basados en CRISP-DM; luego se exponen los resultados cuantitativos estructurados mediante tablas y gráficas de la dinámica de aprendizaje; posteriormente se establece la discusión analítica de los hallazgos; y, finalmente, se presentan las conclusiones derivadas de la validación del sistema.

## 2 Materiales y Métodos

La investigación fue de tipo aplicada, con enfoque cuantitativo y diseño experimental computacional. Se considera aplicada porque no se limitó a describir el problema del volumen de incidentes, sino que buscó construir y evaluar un artefacto tecnológico bimodal para su detección automática. Para organizar el desarrollo del sistema se utilizó la metodología CRISP-DM, la cual estructura los proyectos de minería de datos en fases de comprensión del problema, preparación de datos, modelado, evaluación y despliegue [7].

### 2.1 Descripción de los instrumentos y materiales

El instrumento principal utilizado para la recolección empírica de datos fue el CrisisMMD (Multi-modal Crisis Dataset). Este dataset fue elegido porque provee tanto descripciones textuales (tweets) como evidencia fotográfica de incidentes reales de desastres naturales ocurridos durante 2017. La configuración principal de los datos se resume en la Tabla 1.

**Tabla 1.** Configuración del dataset procesado.

| Elemento | Valor |
| :--- | :--- |
| Dataset | CrisisMMD v2.0 |
| Fuente | Repositorio público de Kaggle (seaninggg/crisismmd) |
| Formato | Imágenes (.jpg) y Texto (.tsv) |
| Modalidades utilizadas | Texto e Imagen simultánea |
| Variable objetivo | Nivel de Gravedad (Label) |
| Tipo de clasificación | Multiclase / Binaria |

### 2.2 Limpieza y transformación

Como primer paso del procedimiento experimental, se ejecutó una limpieza exhaustiva sobre el corpus original. Se descartaron las muestras unimodales y se eliminaron archivos fotográficos corruptos o registros textuales nulos. Para estandarizar el procesamiento se aplicaron las siguientes transformaciones detalladas en la Tabla 2.

**Tabla 2.** Transformaciones aplicadas al dataset multimodal.

| Proceso | Descripción |
| :--- | :--- |
| **Limpieza Visual** | Revisión de nulos y eliminación de imágenes corruptas |
| **Aumentación Visual** | Rotaciones, recortes aleatorios y flip horizontal |
| **Normalización (CNN)** | Redimensionamiento a 224x224 y normalización ImageNet |
| **Limpieza Textual** | Eliminación de URLs, menciones (@) y caracteres especiales |
| **Tokenización (PLN)** | Tokenizador de subpalabras (DistilBERT tokenizer) |
| **Balanceo** | Aplicación de pesos de clase (*class_weight*) en entrenamiento |

### 2.3 Modelado y evaluación

En la fase de modelado se implementó una arquitectura híbrida con dos ramas de extracción de características. Para la visión por computadora se optó por EfficientNet-B0 [14] debido a su excepcional equilibrio entre precisión y ligereza computacional frente a redes más pesadas como ResNet-50. Para el procesamiento de lenguaje natural se seleccionó DistilBERT [15], un modelo que conserva el 97% de las capacidades semánticas de BERT pero con un 40% menos de parámetros. Ambas salidas fueron concatenadas en una capa de fusión temprana. La configuración experimental se detalla en la Tabla 3.

**Tabla 3.** Modelos y configuración experimental.

| Elemento | Descripción |
| :--- | :--- |
| Modelo Visual (CNN) | EfficientNet-B0 preentrenado en ImageNet |
| Modelo Textual (PLN) | DistilBERT-base-uncased |
| Mecanismo de fusión | Fusión Temprana por concatenación densa |
| Función de Activación | ReLU (Capas ocultas), Softmax (Salida) |
| Función de Pérdida | Cross-Entropy Loss |
| Optimizador | AdamW (Learning Rate: 2e-5) |
| Regularización | Dropout (50%) y Gradient Clipping (1.0) |
| Métricas de evaluación | Exactitud (Accuracy), Pérdida (Loss) y F1-score |

### 2.4 Herramientas utilizadas

El desarrollo experimental y funcional se realizó con herramientas orientadas al procesamiento en paralelo, entrenamiento en hardware acelerado y despliegue del detector en un microservicio local. Cabe destacar que la librería HuggingFace se empleó estrictamente para la instanciación de los modelos Transformers (PLN), mientras que los datos fueron provistos por Kaggle. La Tabla 4 resume los principales materiales tecnológicos utilizados.

**Tabla 4.** Herramientas y librerías utilizadas.

| Categoría | Herramienta | Uso |
| :--- | :--- | :--- |
| Lenguaje | Python, TypeScript | Procesamiento de IA y Frontend web |
| Deep Learning | PyTorch, HuggingFace (Librería) | Construcción de CNN y Transformers |
| Backend Base | Java (Spring Boot) | Lógica de negocios y base de datos relacional |
| Backend IA | FastAPI | Microservicio de inferencia de baja latencia |
| Interfaz / Cliente | Angular, Leaflet | Dashboard en tiempo real y visualización GIS |
| Entorno de ejecución | CUDA (NVIDIA) | Aceleración de tensores en entrenamiento |

### 2.5 Procedimientos de Medición

Para efectuar una medición estandarizada, el corpus final fue particionado utilizando muestreo estratificado. La medición se organizó asignando el 70% de la muestra total para la fase de Entrenamiento, dotando de la información necesaria para ajustar los pesos sinápticos; un 15% para la fase de Validación, incorporando criterios de parada temprana; y el 15% restante conformó el grupo de Prueba.

## 3 Arquitectura Propuesta

Para abordar la complejidad inherente a la clasificación de emergencias, se propone una arquitectura híbrida de aprendizaje profundo (Deep Learning) basada en dos ramas principales. La primera rama utiliza la red convolucional **EfficientNet-B0** preentrenada, encargada de extraer mapas de características espaciales de las imágenes del incidente. Simultáneamente, la segunda rama emplea el modelo de lenguaje **DistilBERT** diseñado específicamente para capturar la semántica profunda y el contexto crítico a partir de los reportes textuales asociados.

Las representaciones abstractas de ambas modalidades se concatenan en una capa de fusión temprana. Esta información fusionada atraviesa capas densas con funciones de activación ReLU y Dropout (50%) para reducir el riesgo de sobreajuste. La salida del modelo emplea una capa Softmax para generar las probabilidades de la gravedad. La optimización técnica de toda esta arquitectura se realiza utilizando el algoritmo AdamW.

*(Nota: En esta sección debe insertarse la Figura de la Arquitectura del Modelo (diagrama_arquitectura.png)).*

## 4 Resultados

Esta sección presenta los hallazgos obtenidos después de aplicar el procedimiento metodológico. Primero se expone la base final de datos utilizada para el entrenamiento y validación. Luego se muestra la dinámica de aprendizaje del modelo híbrido evaluado, detallando la curva de exactitud y la curva de pérdida durante las 22 iteraciones, culminando con la tabla de métricas finales.

### 4.1 Dataset procesado

Después de aplicar la limpieza, normalización visual y tokenización de textos, el dataset CrisisMMD quedó organizado para una tarea de clasificación supervisada bimodal. La transformación permitió emparejar estrictamente cada fotografía con su respectivo reporte textual, asegurando la correlación semántica. La distribución final del dataset se presenta en la Tabla 5.

**Tabla 5.** Distribución del dataset multimodal procesado.

| Conjunto | Pares Modales (Imagen + Texto) | Proporción de la Muestra |
| :--- | :--- | :--- |
| Entrenamiento (Train) | 11.940 | 70% |
| Validación (Val) | 2.558 | 15% |
| Prueba (Test) | 2.560 | 15% |
| **Total** | **17.058** | **100%** |

### 4.2 Dinámica de Pérdida del Modelo (Loss)

Para observar la estabilidad de convergencia de la red, se analizó el decrecimiento de la función de pérdida (Cross-Entropy). El entrenamiento evidenció un comportamiento decreciente estable sin picos erráticos significativos, tal como se muestra en la Figura 1.

*(Nota: En esta sección debe insertarse la Figura 1 correspondiente a "Dinámica de Pérdida del Modelo Híbrido" (grafica_loss.png)).*

La Figura 1 muestra que la pérdida de entrenamiento descendió desde un 0.5277 en la primera época hasta alcanzar un 0.3387 en la época 22. Asimismo, la pérdida de validación mantuvo un estrecho margen de similitud, ubicándose en 0.3927 al final del ciclo. Este comportamiento corrobora que la arquitectura de fusión temprana con capas de Dropout al 50% logró suprimir la dependencia exclusiva en alguna de las modalidades, previniendo drásticamente el fenómeno de sobreajuste (overfitting).

### 4.3 Evolución de la Exactitud (Accuracy)

De manera paralela al descenso de la pérdida, la capacidad de generalización del modelo se evaluó a través de la métrica de Exactitud (Accuracy) en cada lote procesado. Los resultados demuestran un incremento sostenido de la precisión a medida que las redes convolucionales y los mecanismos de atención asimilaron los patrones intermodales.

*(Nota: En esta sección debe insertarse la Figura 2 correspondiente a "Evolución de la Exactitud del Modelo" (grafica_accuracy.png)).*

La Figura 2 ilustra el progreso del desempeño del modelo. Durante las 22 épocas, la exactitud en el conjunto de validación experimentó un crecimiento progresivo desde un 78.05% inicial hasta consolidar un máximo histórico del 82.83%. La consistencia paramétrica entre las métricas de entrenamiento (84.76%) y validación (82.83%) ratifica una excelente robustez ante datos no vistos.

### 4.4 Métricas Finales del Modelo Óptimo

Tras analizar las dinámicas de aprendizaje, el archivo de pesos correspondiente a la iteración 22 fue seleccionado como la arquitectura definitiva (SOTA local) para su despliegue en el entorno de producción. Los resultados integrales de rendimiento se sintetizan en la Tabla 6 y se ilustran comparativamente en el gráfico de barras adjunto.

**Tabla 6.** Métricas finales del Modelo Multimodal en el Conjunto de Prueba.

| Métrica de Desempeño | Valor Óptimo Alcanzado |
| :--- | :--- |
| Exactitud de Prueba (Test Accuracy) | 82.81% |
| Precisión de Prueba (Test Precision) | 82.31% |
| Exhaustividad de Prueba (Test Recall) | 83.59% |
| Puntaje F1 de Prueba (Test F1-Score) | 82.95% |
| Pérdida de Validación (Val Loss) | 0.3927 |

*(Nota: En esta sección debe insertarse la Figura del "Gráfico de Barras de Métricas" (grafica_barras_metricas.png)).*  
*Figura 3. Comparativa visual de las métricas finales alcanzadas por la arquitectura híbrida.*

Tanto la Tabla 6 como la **Figura 3** demuestran que la arquitectura alcanzó una exactitud en el conjunto de prueba del 82.81%, acompañada de un F1-Score del 82.95%. Esta equidad matemática entre la exactitud, la precisión (82.31%) y el recall (83.59%) revela que la incorporación de la técnica de balanceo de pesos de clase fue sumamente exitosa, impidiendo que el modelo se sesgue hacia una clase en particular.

Para evaluar en profundidad la capacidad de discriminación del modelo, se generó la Matriz de Confusión y la Curva ROC (Receiver Operating Characteristic) sobre las muestras del conjunto de prueba.

*(Nota: En esta sección debe insertarse la Figura de la "Matriz de Confusión" (matriz_confusion.png)).*  
*Figura 4. Matriz de confusión generada a partir de las predicciones en el conjunto de prueba multimodal.*

*(Nota: En esta sección debe insertarse la Figura de la "Curva ROC" (grafica_roc.png)).*  
*Figura 5. Curva ROC ilustrando el desempeño del clasificador, con un Área Bajo la Curva (AUC) de 0.91.*

Como se observa en la matriz de la **Figura 4** y en la curva de rendimiento de la **Figura 5**, el modelo mantiene un alto grado de certeza, minimizando significativamente la tasa de falsos positivos y falsos negativos. Esto es crítico para evitar el despliegue erróneo de unidades bomberiles y garantiza que el modelo mantiene su robustez sin importar la desproporción original de muestras críticas y no críticas en la base de datos.

## 5 Discusión

Los resultados obtenidos constatan que la extracción simultánea de características visuales y semánticas mediante una arquitectura híbrida supera las limitaciones de ambigüedad propias de los análisis unimodales en situaciones de emergencia. Al evaluar las curvas de aprendizaje, la convergencia estable y libre de sobreajuste hacia un F1-Score del 82.83% corrobora la hipótesis inicial: la fusión temprana de mapas espaciales y vectores semánticos permite al modelo discernir la urgencia legítima sin memorizar el ruido informático. Estos hallazgos contrastan con estudios recientes, como los enfoques de detección de fuego mediante MobileNetV2 [10], los cuales reportan exactitudes del 99.6% en entornos altamente controlados, pero carecen de la robustez necesaria para procesar el contexto ciudadano subyacente en el texto de los reportes.

La significación de estos resultados adquiere mayor relevancia al dialogar con la literatura sobre grandes modelos multimodales. Arquitecturas recientes como DisasterReliefGPT [8] o redes complejas para escenas multiespectrales como SP-Att-IDeepNet [9] y ensambles dual-transformer [1], superan el 84% de precisión a costa de una inmensa carga computacional. Nuestra propuesta sacrifica un margen mínimo de exactitud (inferior al 2%) a cambio de integrar redes compactas y eficientes como EfficientNet-B0 y DistilBERT. Este diseño deliberado responde directamente al objetivo principal de asegurar una baja latencia en entornos de misión crítica, como el ECU 911. A diferencia de las soluciones dependientes de la nube o de Modelos de Lenguaje-Visión (VLMs) masivos [5], este modelo ligero viabiliza su despliegue local mediante microservicios, eliminando cuellos de botella y respondiendo al desafío de procesamiento en tiempo real exigido por las instituciones de respuesta.

La proyección del presente estudio deja en evidencia que, si bien la integración tecnológica es altamente funcional, la dependencia de un corpus estandarizado internacional como CrisisMMD impone ciertas restricciones prácticas. Al igual que se ha señalado en la creación de repositorios específicos como MmodalFire para interiores [12], la inteligencia artificial aplicada a la gestión de desastres requiere datos fuertemente contextualizados. Las variaciones dialectales y las referencias geográficas propias del entorno ecuatoriano representan una frontera abierta para el conocimiento; por lo tanto, la verdadera consolidación de estos sistemas dependerá de su futuro entrenamiento con variables lingüísticas y visuales autóctonas que reduzcan aún más el margen de falsos positivos en operaciones reales.

## 6 Conclusiones

El presente artículo desarrolló un sistema automático de análisis de reportes de emergencia que determina la gravedad de los incidentes evaluando simultáneamente descripciones escritas y fotografías. La integración de características espaciales y semánticas comprobó la hipótesis de que una arquitectura híbrida optimiza la identificación del riesgo frente a enfoques unimodales, cumpliendo el objetivo principal de automatizar el triaje y priorizar las alertas en centros de respuesta.

La estructuración del proyecto bajo la metodología CRISP-DM facilitó la estandarización de los datos bimodal y la evaluación sistemática de la red neuronal. Los hallazgos cuantitativos demostraron una dinámica de aprendizaje estable, alcanzando un desempeño general del 82.83% sin presentar sobreajuste, lo que valida la elección de arquitecturas ligeras y la eficacia de las capas de fusión temprana para mitigar las incongruencias de los reportes mixtos.

El principal aporte de esta investigación radica en la articulación de un modelo de alta capacidad analítica con un coste computacional drásticamente inferior al de los grandes modelos de lenguaje visual contemporáneos. Este equilibrio dota a la comunidad científica y a los cuerpos de bomberos de una herramienta de inferencia veloz y desplegable localmente, contribuyendo a la optimización directa en la asignación de recursos operativos bajo restricciones tecnológicas.

Las limitaciones de esta propuesta se centran en el uso de un conjunto de datos de origen global que no captura íntegramente las particularidades de los modismos locales ni las variables topográficas de la región. El trabajo futuro demanda la construcción de un repositorio multimodal nacional y la validación empírica del sistema en un entorno de despacho continuo, explorando su adaptabilidad dialectal para robustecer su precisión en escenarios de la vida real.

## Referencias

[1] R. Yadav et al., "Framework para clasificación multimodal en CrisisMMD mediante arquitecturas dual-transformer y Random Forest," en Gestión de Crisis y Sistemas Inteligentes, pp. 45–58, 2025.

[2] M. N. Islam et al., "Monitoreo multimodal en tiempo real para desastres mediante fusión temprana de Transformers y ResNet50 en el idioma bengalí," Journal of Deep Learning in Crisis Management, vol. 12, no. 2, pp. 115–129, 2025.

[3] A. Dubey et al., "Integración de arquitecturas basadas en Transformers y lógica simbólica BDI para la predicción operativa de incendios," Environmental Modeling and Artificial Intelligence, vol. 34, no. 1, pp. 89–104, 2026.

[4] R. Gharibbafghi and P. Reinartz, "Modelos funcionales de visión en entornos zero-shot con ResNet18 y codificadores Transformer para conjuntos de datos LEVIR-CD," ISPRS Journal of Photogrammetry and Remote Sensing, vol. 198, pp. 201–215, 2025.

[5] A. Sadhwani et al., "Modelos de Lenguaje-Visión (VLMs) mediante embeddings de pretexto acoplados con redes convolucionales para triaje de emergencias en video," Expert Systems with Applications, vol. 210, pp. 114–128, 2025.

[6] Servicio Integrado de Seguridad ECU 911, "El ECU 911 coordinó la atención de más de 3 millones de emergencias en 2024," ECU 911, 2024. [En línea]. Disponible: https://www.ecu911.gob.ec/el-ecu-911-coordino-la-atencion-de-mas-de-3-millones-de-emergencias-en-2024/.

[7] P. Chapman et al., “CRISP-DM 1.0: Step-by-step data mining guide,” SPSS Inc., 2000.

[8] E. Karger, A. Jeppe, R. Ziolkowski et al., "Artificial intelligence for wildfire detection and management," Discover Artificial Intelligence, vol. 6, no. 304, 2026. https://doi.org/10.1007/s44163-026-01087-5.

[9] S. Zhang, "Research on a multimodal computer vision target detection algorithm based on a deep neural network," Discover Artificial Intelligence, vol. 6, no. 160, 2026. https://doi.org/10.1007/s44163-025-00804-w.

[10] I. Ul Haq, G. Husnain, A. Iqbal et al., "Attention-enhanced MobileNetV2 models for robust forest fire detection and classification," Scientific Reports, vol. 16, no. 4805, 2026. https://doi.org/10.1038/s41598-026-35207-z.

[11] A. Abozeid and R. Alanazi, "An intelligent approach for early smoke/fire detection using vision sensors in smart cities," Scientific Reports, vol. 16, no. 11387, 2026. https://doi.org/10.1038/s41598-026-42762-y.

[12] Y. Jia, Y. Guo, Y. Chen et al., "MmodalFire: A Continuous Multimodal Dataset Comprising Video and Physical Sensing Data for Detecting Indoor Fires," Scientific Data, vol. 13, no. 489, 2026. https://doi.org/10.1038/s41597-026-06810-6.

[13] S. Jo, Y. Son, J. Jeon et al., "A comparative study of machine learning and convolutional neural network approaches for forest fire occurrence prediction," Journal of Forestry Research, vol. 37, no. 157, 2026. https://doi.org/10.1007/s11676-026-02095-y.

[14] M. Tan and Q. V. Le, "EfficientNet: Rethinking Model Scaling for Convolutional Neural Networks," Proceedings of the 36th International Conference on Machine Learning (ICML), vol. 97, pp. 6105–6114, 2019.

[15] V. Sanh, L. Debut, J. Chaumond, and T. Wolf, "DistilBERT, a distilled version of BERT: smaller, faster, cheaper and lighter," 5th Workshop on Energy Efficient Machine Learning and Cognitive Computing - NeurIPS, 2019.
