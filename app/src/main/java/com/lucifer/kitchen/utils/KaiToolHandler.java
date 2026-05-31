package com.lucifer.kitchen.utils;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.util.concurrent.TimeUnit;

public class KaiToolHandler {

    private final Context context;
    private final ProjectManager projectManager;
    private final BinaryManager binaryManager;

    public KaiToolHandler(Context context) {
        this.context = context;
        this.projectManager = new ProjectManager(context);
        this.binaryManager = new BinaryManager(context);
    }

    /**
     * Returns tool definitions in OpenAI function calling format
     */
    public JSONArray getToolDefinitions() throws JSONException {
        JSONArray tools = new JSONArray();

        // read_file
        tools.put(buildTool("read_file", "Read the contents of a file in the project",
            new String[]{"path"}, new String[]{"string"},
            new String[]{"Relative or absolute file path"},
            new String[]{"path"}));

        // write_file
        tools.put(buildTool("write_file", "Write content to a file in the project (creates dirs if needed)",
            new String[]{"path", "content"}, new String[]{"string", "string"},
            new String[]{"Relative or absolute file path", "Content to write"},
            new String[]{"path", "content"}));

        // list_files
        tools.put(buildTool("list_files", "List files and directories in a path",
            new String[]{"path", "recursive"}, new String[]{"string", "boolean"},
            new String[]{"Directory path (default: project root)", "List recursively (default: false)"},
            new String[]{})); // none required

        // execute_command
        tools.put(buildTool("execute_command", "Execute a shell command in the project directory",
            new String[]{"command"}, new String[]{"string"},
            new String[]{"Shell command to execute"},
            new String[]{"command"}));

        // search_files
        tools.put(buildTool("search_files", "Search for text pattern in project files using grep",
            new String[]{"pattern", "path", "file_pattern"}, new String[]{"string", "string", "string"},
            new String[]{"Text pattern to search for", "Directory to search in (default: project root)", "File glob pattern e.g. *.xml (default: all)"},
            new String[]{"pattern"}));

        // file_info
        tools.put(buildTool("file_info", "Get information about a file (size, permissions, type)",
            new String[]{"path"}, new String[]{"string"},
            new String[]{"File path"},
            new String[]{"path"}));

        return tools;
    }

    private JSONObject buildTool(String name, String description,
            String[] paramNames, String[] paramTypes, String[] paramDescs, String[] required) throws JSONException {
        JSONObject tool = new JSONObject();
        tool.put("type", "function");

        JSONObject function = new JSONObject();
        function.put("name", name);
        function.put("description", description);

        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");

        JSONObject properties = new JSONObject();
        for (int i = 0; i < paramNames.length; i++) {
            JSONObject prop = new JSONObject();
            prop.put("type", paramTypes[i]);
            prop.put("description", paramDescs[i]);
            properties.put(paramNames[i], prop);
        }
        parameters.put("properties", properties);

        JSONArray req = new JSONArray();
        for (String r : required) req.put(r);
        parameters.put("required", req);

        function.put("parameters", parameters);
        tool.put("function", function);
        return tool;
    }

    /**
     * Execute a tool call and return the result as a string
     */
    public String executeTool(String name, JSONObject arguments) {
        try {
            switch (name) {
                case "read_file": return readFile(arguments);
                case "write_file": return writeFile(arguments);
                case "list_files": return listFiles(arguments);
                case "execute_command": return executeCommand(arguments);
                case "search_files": return searchFiles(arguments);
                case "file_info": return fileInfo(arguments);
                default: return "Unknown tool: " + name;
            }
        } catch (Exception e) {
            return "Error executing " + name + ": " + e.getMessage();
        }
    }

    private String resolvePath(String path) {
        if (path == null || path.isEmpty()) {
            String pp = projectManager.getCurrentProjectPath();
            return pp != null ? pp : "/sdcard/LuciferKitchen";
        }
        if (path.startsWith("/")) return path;
        String pp = projectManager.getCurrentProjectPath();
        if (pp != null) return pp + "/" + path;
        return "/sdcard/LuciferKitchen/" + path;
    }

    private String readFile(JSONObject args) throws Exception {
        String path = resolvePath(args.getString("path"));
        File file = new File(path);
        if (!file.exists()) return "File not found: " + path;
        if (file.length() > 1024 * 1024) return "File too large (" + file.length() + " bytes). Max 1MB.";

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                sb.append(lineNum++).append(": ").append(line).append("\n");
                if (lineNum > 2000) {
                    sb.append("... (truncated at 2000 lines)\n");
                    break;
                }
            }
        }
        return sb.toString();
    }

    private String writeFile(JSONObject args) throws Exception {
        String path = resolvePath(args.getString("path"));
        String content = args.getString("content");
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return "File written: " + path + " (" + content.length() + " bytes)";
    }

    private String listFiles(JSONObject args) throws Exception {
        String path = resolvePath(args.optString("path", ""));
        boolean recursive = args.optBoolean("recursive", false);
        File dir = new File(path);
        if (!dir.exists()) return "Directory not found: " + path;
        if (!dir.isDirectory()) return path + " is not a directory";

        StringBuilder sb = new StringBuilder();
        listFilesRecursive(dir, "", recursive, sb, 0, 500);
        return sb.toString();
    }

    private int listFilesRecursive(File dir, String prefix, boolean recursive, StringBuilder sb, int count, int max) {
        File[] files = dir.listFiles();
        if (files == null) return count;
        java.util.Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareTo(b.getName());
        });
        for (File f : files) {
            if (count >= max) {
                sb.append(prefix).append("... (truncated)\n");
                return count;
            }
            if (f.isDirectory()) {
                sb.append(prefix).append("[").append(f.getName()).append("/]\n");
                if (recursive) {
                    count = listFilesRecursive(f, prefix + "  ", true, sb, count + 1, max);
                } else {
                    count++;
                }
            } else {
                sb.append(prefix).append(f.getName());
                if (f.length() > 0) {
                    sb.append(" (").append(formatSize(f.length())).append(")");
                }
                sb.append("\n");
                count++;
            }
        }
        return count;
    }

    private String executeCommand(JSONObject args) throws Exception {
        String command = args.getString("command");
        // Security: block dangerous commands
        String lower = command.toLowerCase().trim();
        if (lower.startsWith("rm -rf /") || lower.equals("rm -rf *") || lower.contains("mkfs") ||
            lower.contains("dd if=") && lower.contains("of=/dev")) {
            return "Blocked: dangerous command";
        }

        String projectPath = projectManager.getCurrentProjectPath();
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
        if (projectPath != null) pb.directory(new File(projectPath));
        pb.redirectErrorStream(true);

        // Setup environment with tools
        java.util.Map<String, String> env = pb.environment();
        java.util.Map<String, String> toolEnv = binaryManager.setupEnvironment();
        env.putAll(toolEnv);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            int lines = 0;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                lines++;
                if (lines > 500) {
                    output.append("... (output truncated at 500 lines)\n");
                    break;
                }
            }
        }
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            output.append("\n[Command timed out after 60s]");
        } else {
            int exit = process.exitValue();
            if (exit != 0) output.append("\n[Exit code: ").append(exit).append("]");
        }
        return output.toString();
    }

    private String searchFiles(JSONObject args) throws Exception {
        String pattern = args.getString("pattern");
        String path = resolvePath(args.optString("path", ""));
        String filePattern = args.optString("file_pattern", "");

        StringBuilder cmd = new StringBuilder("grep -rn --include='");
        if (!filePattern.isEmpty()) {
            cmd.append(filePattern);
        } else {
            cmd.append("*");
        }
        cmd.append("' '").append(pattern.replace("'", "'\\''")).append("' '")
           .append(path.replace("'", "'\\''")).append("' 2>/dev/null | head -100");

        ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        process.waitFor(30, TimeUnit.SECONDS);

        if (output.length() == 0) return "No matches found for: " + pattern;
        return output.toString();
    }

    private String fileInfo(JSONObject args) throws Exception {
        String path = resolvePath(args.getString("path"));
        File file = new File(path);
        if (!file.exists()) return "File not found: " + path;

        StringBuilder sb = new StringBuilder();
        sb.append("Path: ").append(file.getAbsolutePath()).append("\n");
        sb.append("Type: ").append(file.isDirectory() ? "Directory" : "File").append("\n");
        sb.append("Size: ").append(formatSize(file.length())).append("\n");
        sb.append("Readable: ").append(file.canRead()).append("\n");
        sb.append("Writable: ").append(file.canWrite()).append("\n");
        sb.append("Executable: ").append(file.canExecute()).append("\n");
        sb.append("Last modified: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(file.lastModified())).append("\n");

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            sb.append("Children: ").append(children != null ? children.length : 0).append("\n");
        }
        return sb.toString();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
