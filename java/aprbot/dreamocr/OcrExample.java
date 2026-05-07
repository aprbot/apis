import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * DreamOCR API client example (Java 11+).
 *
 * Usage:
 *   java OcrExample -i <input.file> -o <output.dir>/ [-p '{"lang":"rus"}']
 *
 * Options:
 *   -i, --input   File to recognize (required)
 *   -o, --output  Output directory for results (required)
 *   -p, --params  OCR parameters as JSON string (default: {})
 *   --host        DreamOCR service URL (or OCR_HOST env)
 *   -k, --key     API key (or OCR_KEY env)
 */
public class OcrExample {

    private static final String DEFAULT_PARAMS = "{}";
    private static final int POLL_INTERVAL_SECONDS = 4;
    private static final int TIMEOUT_SECONDS = 300;

    public static void main(String[] args) throws Exception {
        Args a = parseArgs(args);
        if (a.help) {
            printUsage();
            System.exit(0);
        }

        String host = a.host != null ? a.host : System.getenv("OCR_HOST");
        if (host == null) {
            System.err.println("Error: --host or OCR_HOST env var is required");
            System.exit(1);
        }
        String key = a.key != null ? a.key : System.getenv("OCR_KEY");
        if (key == null) {
            System.err.println("Error: -k/--key or OCR_KEY env var is required");
            System.exit(1);
        }

        if (a.input == null || a.output == null) {
            System.err.println("Error: -i/--input and -o/--output are required.");
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

        // Validate params JSON
        try {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(a.params);
        } catch (IOException e) {
            System.err.println("Error: Invalid JSON in params: " + e.getMessage());
            System.exit(1);
        }

        DreamOcrClient client = new DreamOcrClient(host, key);

        System.out.println("Service: " + host);
        System.out.println("File: " + filePath);
        System.out.println("Output: " + outputDir);
        System.out.println("Params: " + a.params);

        // 1. Upload
        System.out.println("\n1. Uploading file...");
        JsonNode uploadResult = client.upload(filePath);
        String uploadId = uploadResult.get("upload_id").asText();
        System.out.println("   Upload ID: " + uploadId);

        // 2. Recognize
        System.out.println("\n2. Starting recognition...");
        JsonNode recognizeResult = client.recognize(uploadId, 0, a.params);
        int taskId = recognizeResult.get("task_id").asInt();
        System.out.println("   Task ID: " + taskId);

        // 3. Wait for completion
        System.out.println("\n3. Waiting for recognition to complete...");
        JsonNode finalStatus = client.waitForTask(taskId, TIMEOUT_SECONDS, POLL_INTERVAL_SECONDS);
        System.out.println("   Recognition complete!");

        // 4. Download results
        System.out.println("\n4. Downloading results...");
        JsonNode docs = finalStatus.path("docs");
        if (docs.isObject()) {
            var iter = docs.fields();
            while (iter.hasNext()) {
                var entry = iter.next();
                String fmt = entry.getKey();
                String path = entry.getValue().asText();
                if (path == null || path.isEmpty()) continue;

                String ext = fmt.equals("txt") ? "txt" : fmt;
                Path outFile = outputDir.resolve("result." + ext);
                try {
                    boolean ok = client.download(path, outFile);
                    if (ok) {
                        long size = Files.size(outFile);
                        System.out.println("   [" + fmt + "] -> " + outFile + " (" + size + " bytes)");
                    } else {
                        System.out.println("   [" + fmt + "] -> download failed");
                    }
                } catch (IOException | InterruptedException e) {
                    System.out.println("   [" + fmt + "] -> error: " + e.getMessage());
                }
            }
        }

        System.out.println("\nDone. Results saved to: " + outputDir.toAbsolutePath());
    }

    private static class Args {
        String input;
        String output;
        String params = DEFAULT_PARAMS;
        String host;
        String key;
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
                case "-p":
                case "--params":
                    a.params = nextArg(args, i++, "params");
                    break;
                case "--host":
                    a.host = nextArg(args, i++, "host");
                    break;
                case "-k":
                case "--key":
                    a.key = nextArg(args, i++, "key");
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
        System.out.println("Usage: java DreamOcrExample -i <input.file> -o <output.dir>/ [-p '{\"lang\":\"rus\"}']");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -i, --input    File to recognize (required)");
        System.out.println("  -o, --output   Output directory for results (required)");
        System.out.println("  -p, --params   OCR parameters as JSON string (default: " + DEFAULT_PARAMS + ")");
        System.out.println("  --host         DreamOCR service URL (or OCR_HOST env)");
        System.out.println("  -k, --key      API key (or OCR_KEY env)");
        System.out.println("  -h, --help     Show this help message");
    }
}
