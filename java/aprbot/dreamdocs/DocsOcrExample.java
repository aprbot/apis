import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * DreamDocs OCR example (Java 11+).
 *
 * Uploads a file via the normal document processing pipeline,
 * waits for completion, then extracts the OCR text layer and PDF.
 *
 * Usage:
 *   java DocsOcrExample -i /path/to/image.png --project-id 1 -o /tmp/ocr_out
 *
 * Options:
 *   -i, --input      Input image/PDF file (required)
 *   -o, --output     Output directory for results (required)
 *   --project-id     Project ID (default: 1)
 *   --host           DreamDocs URL (or DOCS_HOST env)
 *   -u, --user       Username (or DOCS_USER env)
 *   -p, --password   Password (or DOCS_PASS env)
 */
public class DocsOcrExample {

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
        String user = a.user != null ? a.user : System.getenv("DOCS_USER");
        if (user == null) {
            System.err.println("Error: -u/--user or DOCS_USER env var is required");
            System.exit(1);
        }
        String password = a.password != null ? a.password : System.getenv("DOCS_PASS");
        if (password == null) {
            System.err.println("Error: -p/--password or DOCS_PASS env var is required");
            System.exit(1);
        }

        if (a.input == null) {
            System.err.println("Error: --input is required.");
            printUsage();
            System.exit(1);
        }

        Path filePath = Paths.get(a.input);
        if (!filePath.toFile().exists()) {
            System.err.println("Error: File not found: " + a.input);
            System.exit(1);
        }

        Path outDir = Paths.get(a.output != null ? a.output : "/tmp/dd_ocr_java");
        Files.createDirectories(outDir);

        DreamDocsClient client = new DreamDocsClient(host);

        System.out.println("1. Logging in...");
        client.login(user, password);

        System.out.println("\n2. Uploading file to project " + a.projectId + "...");
        JsonNode uploadResult = client.uploadFile(a.projectId, filePath, "/");
        String fileUuid = uploadResult.get("file_uuid").asText();
        System.out.println("   File UUID: " + fileUuid);

        System.out.println("\n3. Waiting for processing to complete...");
        JsonNode fileInfo = client.waitForFile(fileUuid, 300, 4);
        System.out.println("   Status: " + fileInfo.path("status").asText());

        JsonNode docs = fileInfo.get("docs");
        if (docs == null || docs.size() == 0) {
            System.err.println("\nError: No documents found after processing");
            System.exit(1);
        }

        String docUuid = docs.get(0).get("doc_uuid").asText();
        System.out.println("\n4. Retrieving OCR text for doc " + docUuid + "...");
        JsonNode doc = client.getDoc(docUuid, "ocr_text");
        String ocrText = doc.path("ocr_text").asText("");
        System.out.println("   OCR text length: " + ocrText.length() + " chars");

        Path textPath = outDir.resolve("ocr_text.txt");
        Files.writeString(textPath, ocrText);
        System.out.println("   Saved text -> " + textPath);

        String pdfUrl = doc.path("pdf_url").asText(null);
        if (pdfUrl != null) {
            System.out.println("\n5. Downloading PDF...");
            Path pdfPath = outDir.resolve("result.pdf");
            client.downloadPdf(docUuid, pdfPath);
            System.out.println("   Saved PDF -> " + pdfPath + " (" + Files.size(pdfPath) + " bytes)");
        }

        System.out.println("\nDone.");
    }

    private static class Args {
        String input;
        String output;
        int projectId = 1;
        String host;
        String user;
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
                case "--host":
                    a.host = nextArg(args, i++, "host");
                    break;
                case "-u":
                case "--user":
                    a.user = nextArg(args, i++, "user");
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
        System.out.println("Usage: java DocsOcrExample -i <file> -o <dir> [--project-id <id>]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -i, --input       Input image/PDF file (required)");
        System.out.println("  -o, --output      Output directory (required)");
        System.out.println("  --project-id      Project ID (default: 1)");
        System.out.println("  --host            DreamDocs URL (or DOCS_HOST env)");
        System.out.println("  -u, --user        Username (or DOCS_USER env)");
        System.out.println("  -p, --password    Password (or DOCS_PASS env)");
        System.out.println("  -h, --help        Show this help message");
    }
}
