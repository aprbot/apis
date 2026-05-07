import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * DreamDocs document compare example (Java 11+).
 *
 * Usage:
 *   java CompareExample --file1 <path1> --file2 <path2> --project-id 1
 *
 * Options:
 *   --file1          First file path (required)
 *   --file2          Second file path (required)
 *   --project-id     Project ID for upload (default: 1)
 *   --level          Compare level: text or fields (default: text)
 *   --mode           Compare mode: all, differences, same (default: all)
 *   --host           DreamDocs URL (or DOCS_HOST env)
 *   -u, --user       Username (or DOCS_USER env)
 *   -p, --password   Password (or DOCS_PASS env)
 */
public class CompareExample {


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

        if (a.file1 == null || a.file2 == null) {
            System.err.println("Error: --file1 and --file2 are required.");
            printUsage();
            System.exit(1);
        }

        Path file1Path = Paths.get(a.file1);
        Path file2Path = Paths.get(a.file2);
        if (!file1Path.toFile().exists()) {
            System.err.println("Error: file1 not found: " + a.file1);
            System.exit(1);
        }
        if (!file2Path.toFile().exists()) {
            System.err.println("Error: file2 not found: " + a.file2);
            System.exit(1);
        }

        DreamDocsClient client = new DreamDocsClient(host);

        System.out.println("1. Logging in...");
        client.login(user, password);

        System.out.println("\n2. Uploading files to project " + a.projectId + "...");
        JsonNode result1 = client.uploadFile(a.projectId, file1Path, "/");
        String file1Uuid = result1.get("file_uuid").asText();
        System.out.println("   File 1 uploaded: " + file1Uuid + " (" + file1Path.getFileName() + ")");

        JsonNode result2 = client.uploadFile(a.projectId, file2Path, "/");
        String file2Uuid = result2.get("file_uuid").asText();
        System.out.println("   File 2 uploaded: " + file2Uuid + " (" + file2Path.getFileName() + ")");

        System.out.println("\n3. Waiting for files to be ready...");
        client.waitForFile(file1Uuid, 300, 4);
        System.out.println("   File 1 ready");
        client.waitForFile(file2Uuid, 300, 4);
        System.out.println("   File 2 ready");

        System.out.println("\n4. Comparing files (level=" + a.level + ", mode=" + a.mode + ")...");
        Map<String, Object> compareParams = new HashMap<>();
        compareParams.put("level", a.level);
        compareParams.put("mode", a.mode);
        JsonNode result = client.submitCompare(file1Uuid, file2Uuid, compareParams);
        String taskUuid = result.get("task_uuid").asText();
        System.out.println("   Task UUID: " + taskUuid);

        System.out.println("\n5. Getting comparison result...");
        JsonNode finalResult = client.getCompareResult(taskUuid, 1);
        String status = finalResult.path("status").asText("");
        System.out.println("   Status: " + status);

        if ("SUCCESS".equals(status)) {
            JsonNode comparison = finalResult.path("comparison");
            JsonNode items = comparison.path("items");
            System.out.println("   Differences found: " + items.size());
            for (int i = 0; i < items.size(); i++) {
                JsonNode item = items.get(i);
                System.out.println("\n   Difference " + (i + 1) + ":");
                JsonNode diff = item.path("diff");
                for (int j = 0; j < diff.size(); j++) {
                    System.out.println("      " + diff.get(j).asText().trim());
                }
            }
        } else if (finalResult.has("error_message")) {
            System.out.println("   Error: " + finalResult.get("error_message").asText());
        }

        System.out.println("\nDone.");
    }

    private static class Args {
        String file1;
        String file2;
        int projectId = 1;
        String level = "text";
        String mode = "all";
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
                case "--file1":
                    a.file1 = nextArg(args, i++, "file1");
                    break;
                case "--file2":
                    a.file2 = nextArg(args, i++, "file2");
                    break;
                case "--project-id":
                    a.projectId = Integer.parseInt(nextArg(args, i++, "project-id"));
                    break;
                case "--level":
                    a.level = nextArg(args, i++, "level");
                    break;
                case "--mode":
                    a.mode = nextArg(args, i++, "mode");
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
        System.out.println("Usage: java CompareExample --file1 <path> --file2 <path>");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --file1           First file path (required)");
        System.out.println("  --file2           Second file path (required)");
        System.out.println("  --project-id      Project ID for upload (default: 1)");
        System.out.println("  --level           Compare level: text or fields (default: text)");
        System.out.println("  --mode            Compare mode: all, differences, same (default: all)");
        System.out.println("  --host            DreamDocs URL (or DOCS_HOST env)");
        System.out.println("  -u, --user        Username (or DOCS_USER env)");
        System.out.println("  -p, --password    Password (or DOCS_PASS env)");
        System.out.println("  -h, --help        Show this help message");
    }
}
