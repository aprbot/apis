import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * DreamDocs API client example (Java 11+).
 *
 * Usage:
 *   java PredictExample -i <input.file> -o <output.dir>/ --project-id 1
 *
 * Options:
 *   -i, --input       File to upload (required)
 *   -o, --output      Output directory (required)
 *   --project-id      Project ID (required)
 *   --folder          Target folder (default: /)
 *   --host            Service URL (or DOCS_HOST env)
 *   -u, --username    Username (or DOCS_USER env)
 *   -p, --password    Password (or DOCS_PASS env)
 */
public class PredictExample {

    private static final int POLL_INTERVAL_SECONDS = 4;
    private static final int TIMEOUT_SECONDS = 300;

    public static void main(String[] args) throws Exception {
        Args a = parseArgs(args);
        if (a.help) {
            printUsage();
            System.exit(0);
        }

        String host = a.host != null ? a.host : System.getenv("DOCS_HOST");
        if (host == null) {
            System.err.println("Error: --host or DOCS_HOST env var is required");
            System.exit(1);
        }
        String username = a.username != null ? a.username : System.getenv("DOCS_USER");
        if (username == null) {
            System.err.println("Error: -u/--username or DOCS_USER env var is required");
            System.exit(1);
        }
        String password = a.password != null ? a.password : System.getenv("DOCS_PASS");
        if (password == null) {
            System.err.println("Error: -p/--password or DOCS_PASS env var is required");
            System.exit(1);
        }

        if (a.input == null || a.output == null || a.projectId < 0) {
            System.err.println("Error: -i, -o, and --project-id are required.");
            printUsage();
            System.exit(1);
        }

        Path filePath = Paths.get(a.input);
        if (!Files.exists(filePath)) {
            System.err.println("Error: File not found: " + filePath);
            System.exit(1);
        }

        Path outputDir = Paths.get(a.output);
        Files.createDirectories(outputDir);

        DreamDocsClient client = new DreamDocsClient(host);

        // 1. Login
        System.out.println("1. Authenticating...");
        JsonNode loginResult = client.login(username, password);
        String loggedInUser = loginResult.path("user").path("username").asText("unknown");
        System.out.println("   Logged in as: " + loggedInUser);

        // 2. Upload
        System.out.println("\n2. Uploading file to project " + a.projectId + "...");
        JsonNode uploadResult = client.uploadFile(a.projectId, filePath, a.folder);
        String fileUuid = uploadResult.get("file_uuid").asText();
        System.out.println("   File UUID: " + fileUuid);

        // 3. Wait
        System.out.println("\n3. Waiting for processing to complete...");
        JsonNode fileInfo = client.waitForFile(fileUuid, TIMEOUT_SECONDS, POLL_INTERVAL_SECONDS);
        String status = fileInfo.path("status").asText("");
        if ("Failed".equals(status)) {
            System.err.println("   Processing failed!");
            System.exit(1);
        }
        System.out.println("   Processing complete!");

        // 4. Get docs and fields
        System.out.println("\n4. Retrieving documents and fields...");
        JsonNode splitResult = client.getFileDocs(fileUuid);
        JsonNode docs = splitResult.path("docs");

        if (!docs.isArray() || docs.size() == 0) {
            System.out.println("   No documents found.");
            return;
        }

        for (int idx = 0; idx < docs.size(); idx++) {
            JsonNode doc = docs.get(idx);
            String docUuid = doc.get("doc_uuid").asText();
            JsonNode docInfo = client.getDoc(docUuid);
            String docName = docInfo.path("docname").asText("doc_" + (idx + 1));
            String docType = docInfo.path("doctype").path("name").asText("unknown");
            System.out.println("\n   Document " + (idx + 1) + ": " + docName);
            System.out.println("   Type: " + docType);

            JsonNode data = docInfo.path("data");
            if (data.isArray()) {
                for (JsonNode block : data) {
                    JsonNode fields = block.path("fields");
                    if (fields.isArray()) {
                        for (JsonNode row : fields) {
                            if (row.isArray()) {
                                for (JsonNode field : row) {
                                    String name = field.path("name").asText("");
                                    String value = field.path("value_norm").asText(field.path("value").asText(""));
                                    double score = field.path("score").asDouble(0);
                                    System.out.println("      " + name + ": " + value + " (score=" + String.format("%.2f", score) + ")");
                                }
                            }
                        }
                    }
                }
            }
        }

        // Save raw JSON
        Path resultFile = outputDir.resolve("result.json");
        ObjectMapper prettyMapper = new ObjectMapper();
        Files.writeString(resultFile, prettyMapper.writerWithDefaultPrettyPrinter().writeValueAsString(splitResult), java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("\n5. Results saved to: " + resultFile);
    }

    private static class Args {
        String input;
        String output;
        int projectId = -1;
        String folder = "/";
        String host;
        String username;
        String password;
        boolean help;
    }

    private static Args parseArgs(String[] args) {
        Args a = new Args();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-i":
                case "--input":
                    a.input = nextArg(args, i++, "input");
                    break;
                case "-o":
                case "--output":
                    a.output = nextArg(args, i++, "output");
                    break;
                case "--project-id":
                    a.projectId = Integer.parseInt(nextArg(args, i++, "project-id"));
                    break;
                case "--folder":
                    a.folder = nextArg(args, i++, "folder");
                    break;
                case "--host":
                    a.host = nextArg(args, i++, "host");
                    break;
                case "-u":
                case "--username":
                    a.username = nextArg(args, i++, "username");
                    break;
                case "-p":
                case "--password":
                    a.password = nextArg(args, i++, "password");
                    break;
                case "-h":
                case "--help":
                    a.help = true;
                    break;
                default:
                    if (arg.startsWith("-")) System.err.println("Unknown option: " + arg);
                    break;
            }
        }
        return a;
    }

    private static String nextArg(String[] args, int i, String name) {
        if (i + 1 >= args.length) {
            System.err.println("Error: " + name + " requires a value.");
            System.exit(1);
        }
        return args[i + 1];
    }

    private static void printUsage() {
        System.out.println("Usage: java DreamDocsExample -i <input.file> -o <output.dir>/ --project-id <id>");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -i, --input       File to upload (required)");
        System.out.println("  -o, --output      Output directory (required)");
        System.out.println("  --project-id      Project ID (required)");
        System.out.println("  --folder          Target folder (default: /)");
        System.out.println("  --host            Service URL (or DOCS_HOST env)");
        System.out.println("  -u, --username    Username (or DOCS_USER env)");
        System.out.println("  -p, --password    Password (or DOCS_PASS env)");
        System.out.println("  -h, --help        Show this help message");
    }
}
