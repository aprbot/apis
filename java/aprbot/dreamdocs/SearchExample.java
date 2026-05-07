import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

/**
 * DreamDocs document search example (Java 11+).
 *
 * Usage:
 *   java SearchExample --project-id 1
 *
 * Options:
 *   --project-id      Filter by project (docset) ID
 *   --status          Filter by status name
 *   --doctype-id      Filter by doctype ID
 *   --folder          Filter by folder path
 *   --host            DreamDocs URL (or DOCS_HOST env)
 *   -u, --user        Username (or DOCS_USER env)
 *   -p, --password    Password (or DOCS_PASS env)
 */
public class SearchExample {

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

        DreamDocsClient client = new DreamDocsClient(host);

        System.out.println("1. Logging in...");
        client.login(user, password);

        Map<String, Object> filters = new HashMap<>();
        if (a.projectId != null) {
            filters.put("docset_id", a.projectId);
        }
        if (a.status != null) {
            filters.put("statuses", new String[]{a.status});
        }
        if (a.doctypeId != null) {
            filters.put("doctype_id", a.doctypeId);
        }
        if (a.folder != null) {
            filters.put("folder", a.folder);
        }

        System.out.println("\n2. Searching documents...");
        JsonNode results = client.listDocuments(filters, 20, null);
        JsonNode items = results.path("results");
        int count = items.size();
        System.out.println("   Found " + count + " documents (showing first 20)");
        for (int i = 0; i < count; i++) {
            JsonNode doc = items.get(i);
            String name = doc.path("docname").asText("");
            String status = doc.path("status").asText("");
            String type = doc.path("doctype").path("name").asText("");
            System.out.println("   - " + name + " (status: " + status + ", type: " + type + ")");
        }

        System.out.println("\n3. Available projects (docsets):");
        JsonNode docsets = client.listDocsets();
        for (int i = 0; i < docsets.size(); i++) {
            JsonNode ds = docsets.get(i);
            System.out.println("   - " + ds.path("name").asText("") + " (ID: " + ds.path("id").asInt() + ")");
        }

        System.out.println("\nDone.");
    }

    private static class Args {
        Integer projectId;
        String status;
        Integer doctypeId;
        String folder;
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
                case "--project-id":
                    a.projectId = Integer.parseInt(nextArg(args, i++, "project-id"));
                    break;
                case "--status":
                    a.status = nextArg(args, i++, "status");
                    break;
                case "--doctype-id":
                    a.doctypeId = Integer.parseInt(nextArg(args, i++, "doctype-id"));
                    break;
                case "--folder":
                    a.folder = nextArg(args, i++, "folder");
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
        System.out.println("Usage: java SearchExample [--project-id <id>] [--status <name>] [--doctype-id <id>] [--folder <path>]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --project-id      Filter by project (docset) ID");
        System.out.println("  --status          Filter by status name");
        System.out.println("  --doctype-id      Filter by doctype ID");
        System.out.println("  --folder          Filter by folder path");
        System.out.println("  --host            DreamDocs URL (or DOCS_HOST env)");
        System.out.println("  -u, --user        Username (or DOCS_USER env)");
        System.out.println("  -p, --password    Password (or DOCS_PASS env)");
        System.out.println("  -h, --help        Show this help message");
    }
}
