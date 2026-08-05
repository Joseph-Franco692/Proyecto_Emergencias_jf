import uvicorn
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import torch
from torchvision import transforms
from transformers import DistilBertTokenizer
from PIL import Image
import io
import os
import sys

# Importar la clase del modelo
from crisis_multimodal_model import MultimodalCrisisClassifier

app = FastAPI(title="API Híbrida de Clasificación de Emergencias")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Usando dispositivo: {device}")

# Configuración y carga del modelo
NUM_CLASSES = 2
model = MultimodalCrisisClassifier(num_classes=NUM_CLASSES, freeze_base=True)
model_path = "mejor_modelo_crisis_avanzado.pth"

try:
    if os.path.exists(model_path):
        model.load_state_dict(torch.load(model_path, map_location=device))
        print("Modelo PyTorch cargado exitosamente.")
    else:
        print(f"ATENCIÓN: No se encontró {model_path}. Asegúrate de que el archivo exista en la misma ruta.")
except Exception as e:
    print(f"Error al cargar el modelo: {e}")

model.to(device)
model.eval()

# Tokenizador de DistilBERT
tokenizer = DistilBertTokenizer.from_pretrained('distilbert-base-uncased')

# Transformaciones de imagen (para EfficientNet)
image_transforms = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
])

classes = ["No Informativo (Irrelevante)", "Informativo (Alerta de Crisis)"]

@app.post("/predict")
async def predict_crisis(
    text: str = Form(""),
    image: UploadFile = File(None)
):
    try:
        # Procesar texto
        if not text.strip():
            text = "unknown" # Texto por defecto si viene vacío
            
        inputs = tokenizer(
            text,
            return_tensors="pt",
            max_length=128,
            padding="max_length",
            truncation=True
        )
        input_ids = inputs["input_ids"].to(device)
        attention_mask = inputs["attention_mask"].to(device)
        
        # Procesar imagen
        if image and image.filename:
            image_bytes = await image.read()
            img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        else:
            # Si no hay imagen, creamos un tensor de ceros (imagen en negro)
            # Esto asume que el modelo puede tolerar imágenes negras
            img = Image.new('RGB', (224, 224), color = 'black')
            
        img_tensor = image_transforms(img).unsqueeze(0).to(device)
        
        # Predicción
        with torch.no_grad():
            outputs = model(img_tensor, input_ids, attention_mask)
            probabilities = torch.nn.functional.softmax(outputs, dim=1)
            
            confidence, predicted_idx = torch.max(probabilities, 1)
            
            label_idx = predicted_idx.item()
            label = classes[label_idx]
            score = confidence.item() * 100
            
        return {
            "label": label,
            "confidence": round(score, 2),
            "percentage": int(score),
            "raw_text": text
        }
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    # Para ejecutar: python app_ia.py
    uvicorn.run(app, host="0.0.0.0", port=8000)
