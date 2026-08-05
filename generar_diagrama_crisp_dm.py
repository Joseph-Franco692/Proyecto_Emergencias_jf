import matplotlib.pyplot as plt
import matplotlib.patches as patches

# Crear la figura
fig, ax = plt.subplots(figsize=(10, 10))
ax.set_xlim(0, 10)
ax.set_ylim(0, 10)
ax.axis('off')

# Colores
box_color = "#3d5afe"
text_color = "white"

def draw_node(x, y, title, subtitle):
    w = 3.4
    h = 1.4
    box = patches.FancyBboxPatch((x - w/2, y - h/2), w, h, boxstyle="round,pad=0.1,rounding_size=0.2", 
                                 linewidth=1.5, edgecolor='#1a237e', facecolor=box_color)
    ax.add_patch(box)
    ax.text(x, y + 0.2, title, ha='center', va='center', color=text_color, fontsize=12, fontweight='bold')
    ax.text(x, y - 0.25, subtitle, ha='center', va='center', color=text_color, fontsize=9, linespacing=1.3)

def draw_db(x, y):
    w = 2.0
    h = 2.0
    rect = patches.Rectangle((x - w/2, y - h/2), w, h, facecolor='#e0e0e0', edgecolor='gray', lw=2)
    top = patches.Ellipse((x, y + h/2), w, 0.5, facecolor='#f5f5f5', edgecolor='gray', lw=2)
    bottom = patches.Ellipse((x, y - h/2), w, 0.5, facecolor='#e0e0e0', edgecolor='gray', lw=2)
    
    ax.add_patch(bottom)
    ax.add_patch(rect)
    for i in range(1, 3):
        ax.add_patch(patches.Ellipse((x, y - h/2 + i*(h/3)), w, 0.5, facecolor='none', edgecolor='gray', lw=1.5))
    ax.add_patch(top)
    
    ax.text(x, y - h/2 - 0.7, "Base de Datos:\nCrisisMMD", ha='center', va='center', fontsize=14, fontweight='bold', color='black')

# Coordenadas
pos = {
    'A': (3.0, 8.2), 'B': (7.0, 8.2), 'C': (8.5, 5.5), 
    'D': (7.5, 2.8), 'E': (4.5, 1.2), 'F': (1.8, 4.5), 
    'DB': (5.0, 5.0)
}

# Nodos
draw_node(*pos['A'], "Comprensión\ndel problema", "Clasificar la gravedad de emergencias\n(Triaje Multimodal)")
draw_node(*pos['B'], "Comprensión\nde los datos", "Dataset CrisisMMD\n(Pares de Texto e Imágenes)")
draw_node(*pos['C'], "Preparación\nde los datos", "Limpieza NLP, resize 224x224\ny data augmentation")
draw_node(*pos['D'], "Modelado", "EfficientNet-B0 y DistilBERT\ncon fusión temprana")
draw_node(*pos['E'], "Evaluación", "Accuracy, F1-Macro\ny baja latencia de inferencia")
draw_node(*pos['F'], "Despliegue", "Sistema automático de priorización\ny despacho de alertas")
draw_db(*pos['DB'])

# Flechas
def draw_arrow(start, end, rad="0.2", style="->", ls="solid"):
    arrow = patches.FancyArrowPatch(start, end, connectionstyle=f"arc3,rad={rad}", 
                                    arrowstyle=f"{style},head_length=8,head_width=6", 
                                    color='#757575', lw=3.5, linestyle=ls)
    ax.add_patch(arrow)

draw_arrow((4.8, 8.3), (5.2, 8.3), rad="0.0", style="<->")
draw_arrow((4.8, 8.1), (5.2, 8.1), rad="0.0", style="<->") # Doble flecha
draw_arrow((7.5, 7.4), (8.3, 6.3), rad="0.1")
draw_arrow((8.0, 4.7), (7.6, 3.6), rad="0.0", style="<->")
draw_arrow((6.0, 2.6), (5.0, 1.9), rad="0.1")
draw_arrow((2.9, 1.6), (2.0, 3.7), rad="0.2", ls="dashed")
draw_arrow((1.8, 5.3), (2.4, 7.4), rad="0.2")

plt.tight_layout()
plt.savefig("figura1_crisp_dm_corregida.png", dpi=300, bbox_inches='tight')
plt.close()
