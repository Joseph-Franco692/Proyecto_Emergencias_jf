import gradio as gr
import torch
import sys
import os
from PIL import Image
from torchvision import transforms
from transformers import DistilBertTokenizer

from crisis_multimodal_model import MultimodalCrisisClassifier

# Configuración del dispositivo
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# Instanciar el modelo y cargar los pesos si existen
model = MultimodalCrisisClassifier(num_classes=2).to(device)
model_path = "mejor_modelo_crisis_avanzado.pth"

if os.path.exists(model_path):
    print(f"Cargando modelo avanzado desde: {model_path}")
    try:
        model.load_state_dict(torch.load(model_path, map_location=device))
        print("¡Pesos cargados exitosamente!")
    except Exception as e:
        print(f"Error cargando pesos: {e}. Asegúrate de haber ejecutado 'python train.py' primero.")
else:
    print(f"ADVERTENCIA: No se encontró '{model_path}'. El modelo usará pesos aleatorios (Ejecuta 'python train.py' primero).")

model.eval()

# Transformaciones de imagen (iguales a dataset.py pero sin augmentation)
img_transform = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
])

# Tokenizador
tokenizer = DistilBertTokenizer.from_pretrained('distilbert-base-uncased')

def predict_crisis(image, text):
    if image is None or text == "":
        return "Por favor ingresa tanto una imagen como el texto del tweet."
    
    # Preprocesar la imagen
    img_tensor = img_transform(image).unsqueeze(0).to(device) # Añadir dimensión de batch
    
    # Preprocesar el texto
    encoding = tokenizer(
        text,
        add_special_tokens=True,
        max_length=128,
        return_token_type_ids=False,
        padding='max_length',
        truncation=True,
        return_attention_mask=True,
        return_tensors='pt',
    )
    
    input_ids = encoding['input_ids'].to(device)
    attention_mask = encoding['attention_mask'].to(device)
    
    # Inferencia
    with torch.no_grad():
        outputs = model(img_tensor, input_ids, attention_mask)
        probabilities = torch.nn.functional.softmax(outputs[0], dim=0)
        
    prob_not_info = probabilities[0].item()
    prob_info = probabilities[1].item()
    
    return {
        "Informativo (Alerta de Crisis)": prob_info,
        "No Informativo (Irrelevante)": prob_not_info
    }

# Crear la interfaz con Gradio
interfaz = gr.Interface(
    fn=predict_crisis,
    inputs=[
        gr.Image(type="pil", label="Sube la fotografía del incidente"),
        gr.Textbox(lines=3, placeholder="Escribe el texto del tweet o reporte aquí...", label="Texto del Reporte")
    ],
    outputs=gr.Label(num_top_classes=2, label="Predicción de Gravedad"),
    title="Sistema Híbrido de Clasificación de Emergencias",
    description="Interfaz Gráfica Multimodal (PLN + CNN) para el proyecto de Minería de Datos. Sube una imagen y escribe el texto asociado para clasificar la alerta.",
    theme="default"
)

if __name__ == "__main__":
    print("Iniciando la Interfaz Gráfica GUI...")
    interfaz.launch(inbrowser=True)
