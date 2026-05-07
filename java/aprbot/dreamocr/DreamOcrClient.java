import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DreamOCR API client library (Java 11+).
 *
 * Requires Jackson Databind for JSON parsing:
 *   Maven:
 *     <dependency>
 *       <groupId>com.fasterxml.jackson.core</groupId>
 *       <artifactId>jackson-databind</artifactId>
 *       <version>2.15.2</version>
 *     </dependency>
 */
public class DreamOcrClient {

    public static final int STATUS_LOADING = 10;
    public static final int STATUS_WORKING = 20;
    public static final int STATUS_FAILED = 30;
    public static final int STATUS_READY = 40;
    public static final int STATUS_ARCHIVED = 50;

    private final String host;
    private final String key;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DreamOcrClient(String host, String key) {
        this.host = host.replaceAll("/+$", "");
        this.key = key;
        this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.objectMapper = new ObjectMapper();
    }

    public JsonNode upload(Path filePath) throws IOException, InterruptedException {
        byte[] fileBytes = Files.readAllBytes(filePath);
        String boundary = UUID.randomUUID().toString();
        byte[] body = buildMultipartBodyBytes(boundary, Map.of("key", key), Map.of(filePath.getFileName().toString(), fileBytes));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(host + "/api/v1/upload/"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response);
    }

    public JsonNode recognize(String uploadId, int priority, String paramsJson) throws IOException, InterruptedException {
        Map<String, String> form = new HashMap<>();
        form.put("key", key);
        form.put("upload_id", uploadId);
        form.put("priority", String.valueOf(priority));
        form.put("params", paramsJson);

        String body = encodeForm(form);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(host + "/api/v1/recognize/"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response);
    }

    public JsonNode status(int[] taskIds) throws IOException, InterruptedException {
        String idsJson = objectMapper.writeValueAsString(taskIds);
        Map<String, String> form = new HashMap<>();
        form.put("key", key);
        form.put("ids", idsJson);

        String body = encodeForm(form);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(host + "/api/v1/status/"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode result = parseResponse(response);
        return result.path("data");
    }

    public boolean download(String path, Path outputPath) throws IOException, InterruptedException {
        String urlPath = path.replaceFirst("^\\.+", "");
        if (!urlPath.startsWith("/")) urlPath = "/" + urlPath;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(host + urlPath + "?key=" + urlEncode(key)))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 200) {
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, response.body(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        }
        return false;
    }

    public JsonNode waitForTask(int taskId, int timeoutSeconds, int pollIntervalSeconds) throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutSeconds * 1000L) {
            JsonNode data = status(new int[]{taskId});
            if (!data.isArray() || data.size() == 0) {
                throw new RuntimeException("Task " + taskId + " not found in status response");
            }

            JsonNode taskStatus = null;
            for (JsonNode item : data) {
                if (item.path("task_id").asInt() == taskId) {
                    taskStatus = item;
                    break;
                }
            }
            if (taskStatus == null) {
                throw new RuntimeException("Task " + taskId + " not found in status response");
            }

            int statusCode = taskStatus.path("status").asInt(0);
            if (statusCode == STATUS_READY) return taskStatus;
            if (statusCode == STATUS_FAILED) {
                String error = taskStatus.path("error").asText("Unknown error");
                throw new RuntimeException("OCR task failed: " + error);
            }
            if (statusCode >= STATUS_FAILED) {
                String error = taskStatus.path("error").asText("Unknown error");
                throw new RuntimeException("OCR task failed with status " + statusCode + ": " + error);
            }

            System.out.println("   Status: " + statusCode + " (waiting)...");
            Thread.sleep(pollIntervalSeconds * 1000L);
        }
        throw new RuntimeException("OCR task " + taskId + " did not complete within " + timeoutSeconds + " seconds");
    }

    private JsonNode parseResponse(HttpResponse<String> response) throws IOException {
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode node = objectMapper.readTree(response.body());
        if (node.has("error")) {
            throw new RuntimeException("API error: " + node.get("error").asText());
        }
        return node;
    }

    private static String encodeForm(Map<String, String> form) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(urlEncode(entry.getKey())).append("=").append(urlEncode(entry.getValue()));
        }
        return sb.toString();
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static byte[] buildMultipartBodyBytes(String boundary, Map<String, String> fields, Map<String, byte[]> files) {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        byte[] boundaryBytes = (twoHyphens + boundary + lineEnd).getBytes(StandardCharsets.UTF_8);
        byte[] lineEndBytes = lineEnd.getBytes(StandardCharsets.UTF_8);

        try {
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                baos.write(boundaryBytes);
                String disp = "Content-Disposition: form-data; name=\"" + entry.getKey() + "\"" + lineEnd;
                baos.write(disp.getBytes(StandardCharsets.UTF_8));
                baos.write(lineEndBytes);
                baos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                baos.write(lineEndBytes);
            }
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                baos.write(boundaryBytes);
                String disp = "Content-Disposition: form-data; name=\"file\"; filename=\"" + entry.getKey() + "\"" + lineEnd;
                baos.write(disp.getBytes(StandardCharsets.UTF_8));
                baos.write(("Content-Type: application/octet-stream" + lineEnd).getBytes(StandardCharsets.UTF_8));
                baos.write(lineEndBytes);
                baos.write(entry.getValue());
                baos.write(lineEndBytes);
            }
            baos.write((twoHyphens + boundary + twoHyphens + lineEnd).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }
}
