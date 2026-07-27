package com.bomberos.emergencias.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentCredentials {

    private final Map<String, String> fileValues;

    public PaymentCredentials(@Value("${payments.credentials-file:}") String credentialsFile) {
        this.fileValues = loadFile(credentialsFile);
    }

    public String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = fileValues.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Falta configurar la credencial de pagos " + name);
        }
        return value.trim();
    }

    public String optional(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = fileValues.get(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private Map<String, String> loadFile(String file) {
        Map<String, String> values = new HashMap<>();
        if (file == null || file.isBlank()) return values;
        Path path = Path.of(file);
        if (!Files.isRegularFile(path)) return values;
        try {
            List<String> lines = Files.readAllLines(path);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isBlank() || line.startsWith("#") || !line.contains("=")) continue;
                int separator = line.indexOf('=');
                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException ignored) {
            // La validación required() dará un mensaje seguro si falta alguna clave.
        }
        return values;
    }
}
