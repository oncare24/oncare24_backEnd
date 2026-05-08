package com.oncare.oncare24.security.kms;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OpenBaoKvClient {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baoAddr;
    private final String baoToken;
    private final String kvMount;

    public OpenBaoKvClient(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = objectMapper;
        this.baoAddr = envOrDefault("BAO_ADDR", "http://127.0.0.1:8200");
        this.baoToken = System.getenv("BAO_TOKEN");
        this.kvMount = envOrDefault("BAO_KV_MOUNT", "secret");
    }

    public void put(String path, Map<String, Object> data) {
        requireToken();
        Map<String, Object> body = Map.of("data", data);
        HttpRequest request = requestBuilder(path)
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(body)))
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OpenBao KV put failed: status=" + response.statusCode()
                    + ", path=" + path + ", body=" + response.body());
        }
    }

    public Map<String, Object> get(String path) {
        requireToken();
        HttpRequest request = requestBuilder(path).GET().build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            throw new IllegalStateException("OpenBao KV secret not found: " + path);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OpenBao KV get failed: status=" + response.statusCode()
                    + ", path=" + path + ", body=" + response.body());
        }

        Map<String, Object> root = readJsonMap(response.body());
        Object dataNode = root.get("data");
        if (!(dataNode instanceof Map<?, ?> dataMap)) {
            throw new IllegalStateException("OpenBao KV response missing data object: " + path);
        }
        Object secretNode = dataMap.get("data");
        if (!(secretNode instanceof Map<?, ?> secretMap)) {
            throw new IllegalStateException("OpenBao KV response missing data.data object: " + path);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        secretMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    public boolean exists(String path) {
        requireToken();
        HttpRequest request = requestBuilder(path).GET().build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            return false;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OpenBao KV exists check failed: status=" + response.statusCode()
                    + ", path=" + path + ", body=" + response.body());
        }
        return true;
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder(kvDataUri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Vault-Token", baoToken);
    }

    private URI kvDataUri(String path) {
        String base = baoAddr.endsWith("/") ? baoAddr.substring(0, baoAddr.length() - 1) : baoAddr;
        return URI.create(base + "/v1/" + encodePath(kvMount) + "/data/" + encodePath(path));
    }

    private String encodePath(String path) {
        return java.util.Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "/" + right)
                .orElse("");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException error) {
            throw new IllegalStateException("failed to serialize OpenBao request JSON", error);
        }
    }

    private Map<String, Object> readJsonMap(String value) {
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (IOException error) {
            throw new IllegalStateException("failed to parse OpenBao response JSON", error);
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalStateException("OpenBao request failed. uri=" + request.uri(), error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenBao request interrupted. uri=" + request.uri(), error);
        }
    }

    private void requireToken() {
        if (baoToken == null || baoToken.isBlank()) {
            throw new IllegalStateException("BAO_TOKEN is required for OpenBao KV access");
        }
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
