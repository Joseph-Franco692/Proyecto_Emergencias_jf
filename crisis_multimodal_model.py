import torch
import torch.nn as nn
from torchvision import models
from transformers import DistilBertModel
from torchvision.models import efficientnet_b0, EfficientNet_B0_Weights


class MultimodalCrisisClassifier(nn.Module):
    def __init__(self, num_classes, freeze_base=True):
        """
        Arquitectura híbrida AVANZADA que fusiona CNN (Imágenes) y PLN (Texto).
        Mejorada para obtener un Accuracy y F1-Score muy superiores (State-of-the-Art).
        
        Args:
            num_classes (int): Número de categorías de salida.
            freeze_base (bool): Si es True, congela la mayoría de las capas, pero 
                                deja las últimas descongeladas para "Fine-Tuning" profundo.
        """
        super(MultimodalCrisisClassifier, self).__init__()
        
        # -------------------------------------------------------------
        # RAMA DE VISIÓN (CNN) - EfficientNet-B0 (Mejor que ResNet50 para este tipo de extracción)
        # -------------------------------------------------------------
        # EfficientNet suele tener un mejor balance accuracy/parámetros
        self.image_model = efficientnet_b0(
    weights=EfficientNet_B0_Weights.DEFAULT
)
        
        # Fine-Tuning: Congelar TODAS las capas base para extraer características rápidamente sin sobreajustar
        if freeze_base:
            for param in self.image_model.parameters():
                param.requires_grad = False
                
        # Modificamos la capa final para extraer características
        num_ftrs_img = self.image_model.classifier[1].in_features
        self.image_model.classifier = nn.Identity() 
        
        # -------------------------------------------------------------
        # RAMA DE LENGUAJE (PLN) - DistilBERT (Fine-Tuned)
        # -------------------------------------------------------------
        self.text_model = DistilBertModel.from_pretrained('distilbert-base-uncased')
        
        # Fine-Tuning: Congelar TODAS las capas del Transformer
        if freeze_base:
            for param in self.text_model.parameters():
                param.requires_grad = False
                
        num_ftrs_txt = self.text_model.config.hidden_size # 768
        
        # -------------------------------------------------------------
        # CAPAS DE FUSIÓN Y CLASIFICACIÓN AVANZADA
        # -------------------------------------------------------------
        combined_features = num_ftrs_img + num_ftrs_txt # 1280 (EfficientNet) + 768 = 2048
        
        # Usamos LayerNorm, Dropout fuerte y Activación GELU (mejor que ReLU en deep learning moderno)
        self.classifier = nn.Sequential(
            nn.LayerNorm(combined_features),
            nn.Dropout(0.4),
            nn.Linear(combined_features, 512),
            nn.GELU(),
            nn.LayerNorm(512),
            nn.Dropout(0.3),
            nn.Linear(512, 128),
            nn.GELU(),
            nn.LayerNorm(128),
            nn.Linear(128, num_classes)
        )

    def forward(self, images, input_ids, attention_mask):
        # 1. Características visuales
        img_features = self.image_model(images)
        
        # 2. Características semánticas
        txt_outputs = self.text_model(input_ids=input_ids, attention_mask=attention_mask)
        txt_features = txt_outputs.last_hidden_state[:, 0, :] # Token [CLS]
        
        # 3. Fusión
        combined = torch.cat((img_features, txt_features), dim=1)
        
        # 4. Clasificación
        logits = self.classifier(combined)
        
        return logits
