import com.fasterxml.jackson.databind.JsonNode;

/**
 * DreamDocs setup example (Java 11+).
 *
 * Lists workspaces, docsets (projects), and doctypes.
 * Optionally creates a new docset or doctype.
 *
 * Usage:
 *   java SetupExample [--create-docset "My Project"] [--create-doctype "My Type"]
 *
 * Options:
 *   --create-docset   Name of docset to create (optional)
 *   --create-doctype  Name of doctype to create (optional)
 *   --host            DreamDocs URL (or DOCS_HOST env)
 *   -u, --user        Username (or DOCS_USER env)
 *   -p, --password    Password (or DOCS_PASS env)
 */
public class SetupExample {

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

        System.out.println("\n2. Workspaces:");
        JsonNode workspaces = client.listWorkspaces();
        for (int i = 0; i < workspaces.size(); i++) {
            JsonNode ws = workspaces.get(i);
            System.out.println("   - " + ws.path("name").asText("") + " (slug: " + ws.path("slug").asText("") + ")");
        }

        System.out.println("\n3. Projects (docsets):");
        JsonNode docsets = client.listDocsets();
        for (int i = 0; i < docsets.size(); i++) {
            JsonNode ds = docsets.get(i);
            System.out.println("   - " + ds.path("name").asText("") + " (ID: " + ds.path("id").asInt() + ")");
        }

        if (a.createDocset != null) {
            System.out.println("\n4. Creating docset: " + a.createDocset);
            try {
                JsonNode newDs = client.createDocset(a.createDocset, "");
                System.out.println("   Created: " + newDs.path("name").asText("") + " (ID: " + newDs.path("id").asInt() + ")");
            } catch (Exception e) {
                System.out.println("   Error: " + e.getMessage());
            }
        }

        System.out.println("\n5. Doctypes:");
        JsonNode doctypes = client.listDoctypes();
        for (int i = 0; i < doctypes.size(); i++) {
            JsonNode dt = doctypes.get(i);
            System.out.println("   - " + dt.path("name").asText("") + " (ID: " + dt.path("id").asInt() + ")");
        }

        if (a.createDoctype != null) {
            System.out.println("\n6. Creating doctype: " + a.createDoctype);
            try {
                JsonNode newDt = client.createDoctype(a.createDoctype, "", "ru");
                System.out.println("   Created: " + newDt.path("name").asText("") + " (ID: " + newDt.path("id").asInt() + ")");
            } catch (Exception e) {
                System.out.println("   Error: " + e.getMessage());
            }
        }

        System.out.println("\nDone.");
    }

    private static class Args {
        String createDocset;
        String createDoctype;
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
                case "--create-docset":
                    a.createDocset = nextArg(args, i++, "create-docset");
                    break;
                case "--create-doctype":
                    a.createDoctype = nextArg(args, i++, "create-doctype");
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
        System.out.println("Usage: java SetupExample [--create-docset <name>] [--create-doctype <name>]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --create-docset   Name of docset to create (optional)");
        System.out.println("  --create-doctype  Name of doctype to create (optional)");
        System.out.println("  --host            DreamDocs URL (or DOCS_HOST env)");
        System.out.println("  -u, --user        Username (or DOCS_USER env)");
        System.out.println("  -p, --password    Password (or DOCS_PASS env)");
        System.out.println("  -h, --help        Show this help message");
    }
}
