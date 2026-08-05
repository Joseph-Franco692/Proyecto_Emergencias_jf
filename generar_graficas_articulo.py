import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
import numpy as np

# Configuración de estilo científico
sns.set_theme(style="whitegrid")
plt.rcParams.update({
    "font.family": "serif",
    "font.size": 12,
    "axes.labelsize": 14,
    "axes.titlesize": 16,
    "legend.fontsize": 12,
    "xtick.labelsize": 11,
    "ytick.labelsize": 11,
    "lines.linewidth": 2,
    "figure.dpi": 300
})

# Datos de la Tabla 1 del borrador del usuario
datos = {
    "Epoca": list(range(1, 23)),
    "Train_Loss": [0.5277, 0.4870, 0.4689, 0.4513, 0.4398, 0.4328, 0.4191, 0.4151, 0.4069, 0.4052, 0.3908, 0.3900, 0.3788, 0.3810, 0.3719, 0.3682, 0.3581, 0.3580, 0.3470, 0.3440, 0.3370, 0.3387],
    "Val_Loss": [0.4619, 0.5993, 0.4232, 0.4254, 0.4303, 0.4219, 0.4134, 0.3968, 0.4031, 0.3906, 0.3997, 0.3947, 0.3878, 0.3875, 0.3979, 0.4006, 0.3954, 0.4022, 0.4015, 0.3972, 0.3907, 0.3927],
    "Train_Acc": [74.33, 76.23, 77.41, 78.78, 79.44, 79.59, 80.62, 81.02, 81.02, 81.22, 82.12, 81.96, 82.79, 82.36, 82.81, 83.08, 83.62, 83.71, 84.48, 84.67, 84.64, 84.76],
    "Val_Acc": [78.05, 69.09, 80.62, 79.87, 80.20, 80.87, 80.32, 81.37, 81.53, 82.22, 80.98, 82.33, 82.25, 82.42, 82.50, 82.06, 81.59, 81.81, 81.97, 82.08, 82.78, 82.83]
}

df = pd.DataFrame(datos)

def plot_loss():
    plt.figure(figsize=(8, 5))
    plt.plot(df["Epoca"], df["Train_Loss"], label="Pérdida Entrenamiento (Train Loss)", marker="o", markersize=4, color="#1f77b4")
    plt.plot(df["Epoca"], df["Val_Loss"], label="Pérdida Validación (Val Loss)", marker="s", markersize=4, color="#ff7f0e")
    
    plt.xlabel("Épocas")
    plt.ylabel("Pérdida (Cross-Entropy)")
    plt.title("Dinámica de Pérdida del Modelo Híbrido (EfficientNet + DistilBERT)")
    plt.xticks(np.arange(1, 23, step=2))
    plt.legend()
    plt.grid(True, linestyle="--", alpha=0.6)
    
    # Marcar el punto óptimo
    plt.axvline(x=22, color="green", linestyle="--", alpha=0.5)
    plt.text(21.5, 0.55, "Pico de \nrendimiento", color="green", ha="right")
    
    plt.tight_layout()
    plt.savefig("grafica_loss.png", dpi=300)
    plt.close()

def plot_accuracy():
    plt.figure(figsize=(8, 5))
    plt.plot(df["Epoca"], df["Train_Acc"], label="Exactitud Entrenamiento (Train Acc)", marker="o", markersize=4, color="#2ca02c")
    plt.plot(df["Epoca"], df["Val_Acc"], label="Exactitud Validación (Val Acc)", marker="s", markersize=4, color="#d62728")
    
    plt.xlabel("Épocas")
    plt.ylabel("Exactitud (%)")
    plt.title("Evolución de la Exactitud (Accuracy) del Modelo")
    plt.xticks(np.arange(1, 23, step=2))
    plt.legend()
    plt.grid(True, linestyle="--", alpha=0.6)
    
    # Marcar el punto óptimo
    opt_val_acc = df["Val_Acc"].max()
    opt_epoch = df.loc[df["Val_Acc"].idxmax(), "Epoca"]
    plt.axvline(x=opt_epoch, color="blue", linestyle="--", alpha=0.5)
    plt.scatter([opt_epoch], [opt_val_acc], color="blue", s=100, zorder=5)
    plt.text(opt_epoch - 0.5, opt_val_acc - 2, f"Óptimo:\n{opt_val_acc}%", color="blue", ha="right")
    
    plt.tight_layout()
    plt.savefig("grafica_accuracy.png", dpi=300)
    plt.close()

if __name__ == "__main__":
    print("Generando gráficas científicas...")
    plot_loss()
    plot_accuracy()
    print("¡Gráficas generadas exitosamente! (grafica_loss.png y grafica_accuracy.png)")
