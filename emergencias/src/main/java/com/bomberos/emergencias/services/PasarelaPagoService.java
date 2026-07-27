package com.bomberos.emergencias.services;

import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PasarelaPagoService {

    private final PaymentCredentials credentials;
    public String paypalClientId() {
        return credentials.required("PAYPAL_CLIENT_ID");
    }

    public String crearOrdenPaypal(String codigo, int centavos, String moneda) {
        RestClient client = paypalClient();
        String token = tokenPaypal(client);
        String valor = BigDecimal.valueOf(centavos, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        Map<String, Object> body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of(
                        "custom_id", codigo,
                        "description", "Plan Premium de Prevención IoT",
                        "amount", Map.of("currency_code", moneda, "value", valor)
                ))
        );
        JsonNode response = client.post()
                .uri("/v2/checkout/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        String id = text(response, "id");
        if (id.isBlank()) throw new IllegalStateException("PayPal no devolvió el identificador de la orden");
        return id;
    }

    public JsonNode capturarPaypal(String paypalOrderId) {
        RestClient client = paypalClient();
        return client.post()
                .uri("/v2/checkout/orders/{id}/capture", paypalOrderId)
                .header("Authorization", "Bearer " + tokenPaypal(client))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode consultarOrdenPaypal(String paypalOrderId) {
        RestClient client = paypalClient();
        return client.get()
                .uri("/v2/checkout/orders/{id}", paypalOrderId)
                .header("Authorization", "Bearer " + tokenPaypal(client))
                .retrieve()
                .body(JsonNode.class);
    }

    public boolean validarCapturaPaypal(JsonNode capture, String codigo, int centavos, String moneda) {
        if (capture == null || !"COMPLETED".equalsIgnoreCase(text(capture, "status"))) return false;
        JsonNode unit = capture.path("purchase_units").path(0);
        String customId = unit.path("custom_id").asText("");
        if (!customId.isBlank() && !codigo.equals(customId)) return false;
        JsonNode amount = unit.path("payments").path("captures").path(0).path("amount");
        if (!moneda.equalsIgnoreCase(amount.path("currency_code").asText())) return false;
        try {
            BigDecimal expected = BigDecimal.valueOf(centavos, 2);
            return expected.compareTo(new BigDecimal(amount.path("value").asText())) == 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public String paypalTransactionId(JsonNode capture) {
        return capture.path("purchase_units").path(0).path("payments").path("captures").path(0).path("id").asText();
    }

    private String tokenPaypal(RestClient client) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        JsonNode response = client.post()
                .uri("/v1/oauth2/token")
                .headers(headers -> headers.setBasicAuth(
                        credentials.required("PAYPAL_CLIENT_ID"),
                        credentials.required("PAYPAL_CLIENT_SECRET")))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        String token = text(response, "access_token");
        if (token.isBlank()) throw new IllegalStateException("No fue posible autenticar con PayPal");
        return token;
    }

    private RestClient paypalClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(8_000);
        requestFactory.setReadTimeout(12_000);
        return RestClient.builder()
                .baseUrl(credentials.optional("PAYPAL_BASE_URL", "https://api-m.sandbox.paypal.com"))
                .requestFactory(requestFactory)
                .build();
    }

    private String text(JsonNode node, String field) {
        return node == null ? "" : node.path(field).asText("");
    }
}
