package com.bomberos.emergencias.controllers;

import com.bomberos.emergencias.services.OllamaIaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class OllamaController {

    @Autowired
    private OllamaIaService ollamaIaService;

    public static class ChatRequest {
        public String pregunta;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody ChatRequest request) {
        String pregunta = (request != null && request.pregunta != null) ? request.pregunta.trim() : "";
        if (pregunta.isEmpty()) {
            Map<String, String> err = new HashMap<>();
            err.put("respuesta", "Por favor ingresa una pregunta para el asistente de IA.");
            return ResponseEntity.badRequest().body(err);
        }

        String respuesta = ollamaIaService.consultarIaConContexto(pregunta);
        Map<String, String> result = new HashMap<>();
        result.put("respuesta", respuesta);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> res = new HashMap<>();
        res.put("status", "OK");
        res.put("modelo", "llama3.2");
        res.put("proveedor", "Ollama local + datos verificados de PostgreSQL");
        return ResponseEntity.ok(res);
    }
}
