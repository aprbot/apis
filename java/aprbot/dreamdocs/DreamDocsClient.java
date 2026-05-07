import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DreamDocs API client library (Java 11+).
 *
 * Requires Jackson Databind for JSON parsing.
 */
public class DreamDocsClient {

    private final String host;
    private String accessToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DreamDocsClient(String host) {
        this.host = host.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.objectMapper = new ObjectMapper();
    }

    private String url(String path) {
        return host + path;
    }

    private Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("Content-Type", "application/json");
        if (accessToken != null) {
            h.put("Authorization", "Bearer " + accessToken);
        }
        return h;
    }

    private HttpRequest.Builder requestBuilder(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(url(path)));
        for (Map.Entry<String, String> e : headers().entrySet()) {
            b.header(e.getKey(), e.getValue());
        }
        return b;
    }

    public JsonNode login(String username, String password) throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url("/api/v2/user/login/")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode result = parseResponse(response);
        JsonNode tokenNode = result.get("access_token");
        if (tokenNode == null || tokenNode.isNull()) {
            String bearer = result.get("access").asText("");
            tokenNode = objectMapper.valueToTree(bearer.replace("Bearer ", ""));
        }
        this.accessToken = tokenNode.asText();
        return result;
    }

    // --- File & Document ---

    public JsonNode uploadFile(int projectId, Path filePath, String folder) throws IOException, InterruptedException {
        byte[] fileBytes = Files.readAllBytes(filePath);
        String boundary = UUID.randomUUID().toString();
        byte[] body = buildMultipartBodyBytes(boundary,
                Map.of("docset_id", String.valueOf(projectId), "folder", folder),
                Map.of(filePath.getFileName().toString(), fileBytes));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url("/api/v2/file/")))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response);
    }

    public JsonNode getFile(String fileUuid) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder("/api/v2/file/" + fileUuid + "/").GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response);
    }

    public JsonNode getFileDocs(String fileUuid) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder("/api/v2/file/" + fileUuid + "/split/").GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response);
    }

    public JsonNode getDoc(String docUuid) throws IOException, InterruptedException {
        return getDoc(docUuid, null);
    }

    public JsonNode getDoc(String docUuid, String include) throws IOException, InterruptedException {
        String path = "/api/v2/doc/" + docUuid + "/";
        if (include != null && !include.isEmpty()) {
            path += "?include=" + include;
        }
        HttpRequest request = requestBuilder(path).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response);
    }

    public JsonNode listDocuments(Map<String, Object> filters, Integer limit, Integer offset) throws IOException, InterruptedException {
        StringBuilder path = new StringBuilder("/api/v2/doc/");
        if (limit != null || offset != null) {
            path.append("?");
            if (limit != null) path.append("limit=").append(limit);
            if (offset != null) {
                if (limit != null) path.append("&");
                path.append("offset=").append(offset);
            }
        }
        String body = filters != null ? objectMapper.writeValueAsString(filters) : "{}";
        HttpRequest request = requestBuilder(path.toString())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response);
    }

    public void downloadPdf(String docUuid, Path outputPath) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder("/api/v2/doc/" + docUuid + "/download/").GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + new String(response.body(), StandardCharsets.UTF_8));
        }
        Files.write(outputPath, response.body());
    }

    // --- Compare ---

    public JsonNode submitCompare(String file1Uuid, String file2Uuid, Map<String, Object> compareParams) throws IOException, InterruptedException {
        String taskUuid = UUID.randomUUID().toString();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("task_uuid", taskUuid);
        body.put("file1_uuid", file1Uuid);
        body.put("file2_uuid", file2Uuid);
        if (compareParams != null) {
            body.set("compare_params", objectMapper.valueToTree(compareParams));
        }

        HttpRequest request = requestBuilder("/api/v2/run/compare/")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response);
    }

    public JsonNode getCompareResult(String taskUuid, int wait) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder("/api/v2/run/compare/" + taskUuid + "/")
                .uri(URI.create(url("/api/v2/run/compare/" + taskUuid + "/?wait=" + wait)))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response);
    }

    // --- Setup ---

    public JsonNode listWorkspaces() throws IOException, InterruptedException {
        HttpRequest request = requestBuilder("/api/v2/workspace/").GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response);
    }

    public JsonNode listDocsets() throws IOException, InterruptedException {
        HttpRequest request = requestBuilder("/api/v2/docset/").GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response);
    }

    public JsonNode createDocset(String name, String description) throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(Map.of("name", name, "description", description));
        HttpRequest request = requestBuilder("/api/v2/docset/")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response);
    }

    public JsonNode listDoctypes() throws IOException, InterruptedException {
        HttpRequest request = requestBuilder("/api/v2/doctype/").GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response);
    }

    public JsonNode createDoctype(String name, String description, String lang) throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(Map.of("name", name, "description", description, "lang", lang));
        HttpRequest request = requestBuilder("/api/v2/doctype/")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response);
    }

    // --- Wait helpers ---

    public JsonNode waitForFile(String fileUuid, int timeoutSeconds, int pollIntervalSeconds) throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutSeconds * 1000L) {
            JsonNode fileInfo = getFile(fileUuid);
            String status = fileInfo.path("status").asText("");
            if ("Ready".equals(status) || "HasDocs".equals(status) || "Failed".equals(status)) {
                return fileInfo;
            }
            System.out.println("   File status: " + status + " (waiting)...");
            Thread.sleep(pollIntervalSeconds * 1000L);
        }
        throw new RuntimeException("File " + fileUuid + " did not finish processing within " + timeoutSeconds + " seconds");
    }

    public JsonNode waitForCompare(String taskUuid, int timeoutSeconds, int pollIntervalSeconds) throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutSeconds * 1000L) {
            JsonNode result = getCompareResult(taskUuid, 0);
            String status = result.path("status").asText("");
            if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
                return result;
            }
            System.out.println("   Compare status: " + status + " (waiting)...");
            Thread.sleep(pollIntervalSeconds * 1000L);
        }
        throw new RuntimeException("Compare task " + taskUuid + " did not finish within " + timeoutSeconds + " seconds");
    }

    // --- Helpers ---

    private JsonNode parseResponse(HttpResponse<String> response) throws IOException {
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readTree(response.body());
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
