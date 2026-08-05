import graphviz
import os

def crear_diagrama():
    # Configuración principal del grafo
    dot = graphviz.Digraph('Arquitectura', format='png', filename='diagrama_arquitectura')
    dot.attr(rankdir='TB', splines='spline', nodesep='0.6', ranksep='0.6', pad='0.5')
    dot.attr('node', shape='box', style='filled,rounded', fontname='Helvetica', fontsize='11', margin='0.2')
    dot.attr('edge', fontname='Helvetica', fontsize='10', color='#555555', penwidth='1.2')

    # Paleta de colores
    color_input = '#e3f2fd'    # Azul muy claro
    color_vision = '#e8f5e9'   # Azul claro (rama CNN)
    color_texto = '#fff3e0'    # Verde claro (rama PLN)
    color_fusion = '#f3e5f5'   # Naranja claro (fusión)
    color_output = '#ffebee'   # Morado claro (salida)

    # 1. Nivel de Entrada
    with dot.subgraph(name='cluster_entrada') as c:
        c.attr(style='dashed', color='#aaaaaa', label='Fase de Entrada de Datos', fontname='Helvetica-Bold', fontsize='12')
        c.node('Input', 'Reporte Multimodal de Emergencia\n(CrisisMMD)', fillcolor=color_input, shape='folder')

    # 2. División en dos ramas
    with dot.subgraph(name='cluster_ramas') as ramas:
        ramas.attr(style='invis') # Contenedor invisible para mantenerlas alineadas
        
        # Rama Visión
        with ramas.subgraph(name='cluster_vision') as vision:
            vision.attr(style='rounded,dashed', color='#4caf50', label='Procesamiento de Visión (CNN)', fontname='Helvetica-Bold', fontsize='10')
            vision.node('V1', 'Imagen Fotográfica\n(Redimensionada a 224x224)', fillcolor=color_vision)
            vision.node('V2', 'EfficientNet-B0\n(Red Convolucional Preentrenada)', fillcolor=color_vision, fontname='Helvetica-Bold')
            vision.node('V3', 'Extracción de Mapas\nde Características Espaciales', fillcolor=color_vision)
            
            vision.edge('V1', 'V2')
            vision.edge('V2', 'V3')

        # Rama Texto
        with ramas.subgraph(name='cluster_texto') as texto:
            texto.attr(style='rounded,dashed', color='#ff9800', label='Procesamiento de Lenguaje (PLN)', fontname='Helvetica-Bold', fontsize='10')
            texto.node('T1', 'Descripción Escrita\n(Depuración de Ruido)', fillcolor=color_texto)
            texto.node('T2', 'Tokenización\n(DistilBERT Tokenizer)', fillcolor=color_texto)
            texto.node('T3', 'DistilBERT\n(Modelo de Lenguaje Ligero)', fillcolor=color_texto, fontname='Helvetica-Bold')
            texto.node('T4', 'Extracción de\nCaracterísticas Semánticas', fillcolor=color_texto)
            
            texto.edge('T1', 'T2')
            texto.edge('T2', 'T3')
            texto.edge('T3', 'T4')

    # 3. Nivel de Fusión
    with dot.subgraph(name='cluster_fusion') as fusion:
        fusion.attr(style='rounded,dashed', color='#9c27b0', label='Fusión y Clasificación', fontname='Helvetica-Bold', fontsize='10')
        fusion.node('F1', 'Capa de Fusión Temprana\n(Concatenación de Vectores)', fillcolor=color_fusion, shape='hexagon')
        fusion.node('F2', 'Capas Densas (Fully Connected)\nActivación ReLU', fillcolor=color_fusion)
        fusion.node('F3', 'Regularización\nDropout (50%)', fillcolor=color_fusion)
        fusion.node('F4', 'Capa de Salida (Softmax)', fillcolor=color_output, shape='component')
        fusion.node('Output', 'Clasificación de la Gravedad\n(Priorización / Triaje)', fillcolor='#ffcdd2', style='filled,rounded,bold', fontname='Helvetica-Bold')

        fusion.edge('F1', 'F2')
        fusion.edge('F2', 'F3')
        fusion.edge('F3', 'F4')
        fusion.edge('F4', 'Output')

    # Conectar Entrada con Ramas
    dot.edge('Input', 'V1', label=' Evidencia visual')
    dot.edge('Input', 'T1', label=' Reporte textual')

    # Conectar Ramas con Fusión
    dot.edge('V3', 'F1', label=' Vector Espacial')
    dot.edge('T4', 'F1', label=' Vector Semántico')

    # Renderizar el gráfico
    dot.render(cleanup=True)
    print("El diagrama de arquitectura se generó exitosamente como 'diagrama_arquitectura.png'")

if __name__ == '__main__':
    crear_diagrama()
