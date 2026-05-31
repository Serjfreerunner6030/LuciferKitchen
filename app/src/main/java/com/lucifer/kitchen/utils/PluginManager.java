package com.lucifer.kitchen.utils;

import android.content.Context;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * DNA-compatible Plugin Manager.
 *
 * Supports DNA .zip2 plugin format and .mpk format.
 * Both are standard ZIP archives extracted to module directory.
 *
 * DNA plugin structure:
 *   module/plugin-name/
 *       index.xml    - kr-script XML UI definition (groups, actions, params)
 *       scripts      - shell scripts referenced by index.xml
 *
 * Workflow (identical to DNA-Android):
 *   1. User selects .zip2 or .mpk file
 *   2. Archive extracted to module dir
 *   3. Scanner finds all index.xml in module subdirectories
 *   4. Each index.xml parsed into native UI actions
 *   5. Removal deletes plugin directory
 *
 * Environment variables passed to plugin scripts:
 *   START_DIR, DNA_DIR, DNA_PRO, DNA_DRO, DNA_TMP, TMPDIR, MODULE_DIR, APP_USER_ID
 */
public class PluginManager {

    private static final String MODULE_DIR = "module";
    private final String baseDir;
    private final Context context;

    public PluginManager(Context context) {
        this.context = context;
        ProjectManager pm = new ProjectManager(context);
        this.baseDir = pm.getBaseDir();
        File moduleDir = new File(getModuleDir());
        if (!moduleDir.exists()) {
            moduleDir.mkdirs();
        }
    }

    public String getModuleDir() {
        return baseDir + "/" + MODULE_DIR;
    }

    public String getBaseDir() {
        return baseDir;
    }

    /**
     * Import a plugin file (.zip2 or .mpk).
     * Extracts ZIP contents into module/ directory, exactly like DNA's:
     *   dna unzip $file $START_DIR/module
     */
    public String importPlugin(File pluginFile) throws Exception {
        if (!pluginFile.exists()) {
            throw new Exception("File not found");
        }

        String fileName = pluginFile.getName().toLowerCase();
        if (!fileName.endsWith(".zip2") && !fileName.endsWith(".mpk") && !fileName.endsWith(".zip")) {
            throw new Exception("Unsupported format. Use .zip2 or .mpk");
        }

        // Probe zip structure to determine plugin name
        String pluginName = null;
        Set<String> topLevelDirs = new LinkedHashSet<>();
        boolean hasTopLevelFiles = false;

        ZipInputStream probe = new ZipInputStream(new FileInputStream(pluginFile));
        ZipEntry entry;
        while ((entry = probe.getNextEntry()) != null) {
            String name = entry.getName();
            int slash = name.indexOf('/');
            if (slash > 0) {
                topLevelDirs.add(name.substring(0, slash));
            } else if (!entry.isDirectory()) {
                hasTopLevelFiles = true;
            }
            probe.closeEntry();
        }
        probe.close();

        if (topLevelDirs.size() == 1 && !hasTopLevelFiles) {
            // Single directory at root — extract directly to module/
            // This is the standard DNA format: plugin.zip2 contains pluginname/index.xml
            pluginName = topLevelDirs.iterator().next();
            unzip(pluginFile, new File(getModuleDir()));
        } else {
            // Multiple items or loose files — wrap in directory named after the file
            pluginName = pluginFile.getName().replaceAll("\\.(zip2|mpk|zip)$", "");
            File pluginDir = new File(getModuleDir(), pluginName);
            pluginDir.mkdirs();
            unzip(pluginFile, pluginDir);
        }

        // Set permissions (like DNA's chmod -R 755)
        File pluginDir = new File(getModuleDir(), pluginName);
        if (pluginDir.exists()) {
            makeExecutable(pluginDir);
            // Also set via shell for proper Linux permissions
            ShellExecutor.execute("chmod -R 755 '" + pluginDir.getAbsolutePath() + "'");
        }

        return pluginName;
    }

    /**
     * List all installed plugins.
     * Scans module/ for directories, reads index.xml from each.
     * Equivalent to DNA's modun.sh: find $START_DIR/module/ -name index.xml
     */
    public List<PluginInfo> listPlugins() {
        List<PluginInfo> plugins = new ArrayList<>();
        File dir = new File(getModuleDir());
        File[] children = dir.listFiles();
        if (children == null) return plugins;

        for (File child : children) {
            if (!child.isDirectory()) continue;

            PluginInfo info = new PluginInfo();
            info.name = child.getName();
            info.path = child.getAbsolutePath();

            // Check for index.xml
            File indexXml = new File(child, "index.xml");
            info.hasIndexXml = indexXml.exists();

            // Find entry script
            info.entryScript = findEntryScript(child);

            // Parse index.xml for actions
            if (info.hasIndexXml) {
                info.actions = parseIndexXml(indexXml);
                // Use first group title or action title as description
                if (!info.actions.isEmpty()) {
                    info.description = info.actions.get(0).groupTitle;
                    if (info.description == null || info.description.isEmpty()) {
                        info.description = info.actions.get(0).title;
                    }
                }
            }
            if (info.description == null || info.description.isEmpty()) {
                info.description = info.name;
            }

            plugins.add(info);
        }

        plugins.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return plugins;
    }

    /**
     * Get plugin names for deletion dialog.
     * Equivalent to DNA's: ls -F $START_DIR/module | sed 's/\/$//g'
     */
    public String[] getPluginNames() {
        File dir = new File(getModuleDir());
        String[] names = dir.list((d, n) -> new File(d, n).isDirectory());
        return names != null ? names : new String[0];
    }

    /**
     * Delete plugins by name.
     * Equivalent to DNA's project.sh sub:
     *   rm -rf $START_DIR/module/$i
     */
    public void deletePlugins(String[] names) {
        for (String name : names) {
            File dir = new File(getModuleDir(), name);
            if (dir.exists() && dir.isDirectory()) {
                deleteRecursive(dir);
            }
        }
    }

    /**
     * Run a plugin action's <set> command with DNA environment variables.
     */
    public String runAction(PluginInfo plugin, PluginAction action, String projectPath, Map<String, String> params) {
        StringBuilder env = new StringBuilder();
        // DNA-compatible environment variables
        env.append("export START_DIR='").append(baseDir).append("' && ");
        env.append("export DNA_DIR='").append(baseDir).append("' && ");
        env.append("export DNA_PRO='").append(projectPath != null ? projectPath : baseDir).append("' && ");
        env.append("export DNA_DRO='/data/lucifer_tmp' && ");
        env.append("export DNA_TMP='/data/lucifer_tmp' && ");
        env.append("export TMPDIR='").append(context.getCacheDir().getAbsolutePath()).append("' && ");
        env.append("export MODULE_DIR='").append(plugin.path).append("' && ");
        env.append("export PATH='").append(plugin.path).append(":$PATH' && ");

        // Set param variables
        if (params != null) {
            for (Map.Entry<String, String> p : params.entrySet()) {
                env.append("export ").append(p.getKey()).append("='").append(p.getValue()).append("' && ");
            }
        }

        // cd to plugin dir and execute
        String command = action.setCommand;
        if (command == null || command.isEmpty()) {
            return "No command defined for this action";
        }

        // If command starts with "samples/" — prefix with plugin path
        if (command.startsWith("samples/") || command.startsWith("scripts/")) {
            command = plugin.path + "/" + command;
        }

        return ShellExecutor.executeAsRoot(env.toString() + "cd '" + plugin.path + "' && " + command);
    }

    /**
     * Run a plugin's standalone entry script
     */
    public String runPlugin(PluginInfo plugin, String projectPath) {
        if (plugin.entryScript == null) {
            return "No executable script found in plugin";
        }

        StringBuilder env = new StringBuilder();
        env.append("export START_DIR='").append(baseDir).append("' && ");
        env.append("export DNA_DIR='").append(baseDir).append("' && ");
        env.append("export DNA_PRO='").append(projectPath != null ? projectPath : baseDir).append("' && ");
        env.append("export DNA_TMP='/data/lucifer_tmp' && ");
        env.append("export TMPDIR='").append(context.getCacheDir().getAbsolutePath()).append("' && ");
        env.append("export MODULE_DIR='").append(plugin.path).append("' && ");
        env.append("export PATH='").append(plugin.path).append(":$PATH' && ");

        String scriptPath = plugin.path + "/" + plugin.entryScript;
        return ShellExecutor.executeAsRoot(env.toString() + "cd '" + plugin.path + "' && sh '" + scriptPath + "'");
    }

    /**
     * Read raw index.xml content
     */
    public String readIndexXml(PluginInfo plugin) {
        File indexXml = new File(plugin.path, "index.xml");
        if (!indexXml.exists()) return null;
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(indexXml));
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== index.xml Parser ====================

    /**
     * Parse DNA kr-script index.xml into list of PluginAction objects.
     * Supports: <group title="">, <action><title/><set/><desc/></action>, <param name="" .../>
     */
    public List<PluginAction> parseIndexXml(File indexXml) {
        List<PluginAction> actions = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(indexXml));
            String line;
            String currentGroupTitle = null;
            PluginAction currentAction = null;
            boolean inAction = false;
            StringBuilder setContent = new StringBuilder();
            boolean inSet = false;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                // <group title="...">
                if (trimmed.startsWith("<group")) {
                    String title = extractAttr(trimmed, "title");
                    if (title != null) currentGroupTitle = title;
                }

                // <action ...>
                if (trimmed.startsWith("<action")) {
                    currentAction = new PluginAction();
                    currentAction.groupTitle = currentGroupTitle;
                    currentAction.interruptible = !"false".equals(extractAttr(trimmed, "interruptible"));
                    currentAction.params = new ArrayList<>();
                    inAction = true;
                }

                if (inAction && currentAction != null) {
                    // <title>...</title>
                    if (trimmed.startsWith("<title>")) {
                        String t = extractTag(trimmed, "title");
                        if (t != null) currentAction.title = t;
                    }

                    // <desc>...</desc>
                    if (trimmed.startsWith("<desc>")) {
                        String d = extractTag(trimmed, "desc");
                        if (d != null) currentAction.description = d;
                    }

                    // <summary>...</summary> or summary="..."
                    if (trimmed.startsWith("<summary")) {
                        String s = extractTag(trimmed, "summary");
                        if (s != null && currentAction.description == null) {
                            currentAction.description = s;
                        }
                    }

                    // <set>...</set> (can be multiline)
                    if (trimmed.startsWith("<set>")) {
                        if (trimmed.contains("</set>")) {
                            currentAction.setCommand = extractTag(trimmed, "set");
                        } else {
                            inSet = true;
                            setContent.setLength(0);
                            String after = trimmed.substring(5);
                            setContent.append(after);
                        }
                    } else if (inSet) {
                        if (trimmed.contains("</set>")) {
                            int idx = trimmed.indexOf("</set>");
                            setContent.append(trimmed, 0, idx);
                            currentAction.setCommand = setContent.toString().trim();
                            inSet = false;
                        } else {
                            setContent.append(trimmed).append(" ");
                        }
                    }

                    // <param name="..." .../>
                    if (trimmed.startsWith("<param ")) {
                        PluginParam param = new PluginParam();
                        param.name = extractAttr(trimmed, "name");
                        param.title = extractAttr(trimmed, "title");
                        param.label = extractAttr(trimmed, "label");
                        param.desc = extractAttr(trimmed, "desc");
                        param.type = extractAttr(trimmed, "type");
                        param.placeholder = extractAttr(trimmed, "placeholder");
                        param.value = extractAttr(trimmed, "value");
                        param.required = "true".equals(extractAttr(trimmed, "required"));
                        param.multiple = "true".equals(extractAttr(trimmed, "multiple"));
                        param.optionsSh = extractAttr(trimmed, "options-sh");
                        param.separator = extractAttr(trimmed, "separator");
                        // Parse max/min for seekbar
                        String maxStr = extractAttr(trimmed, "max");
                        String minStr = extractAttr(trimmed, "min");
                        if (maxStr != null) try { param.max = Integer.parseInt(maxStr); } catch (Exception e) {}
                        if (minStr != null) try { param.min = Integer.parseInt(minStr); } catch (Exception e) {}

                        if (param.name != null) {
                            currentAction.params.add(param);
                        }
                    }
                }

                // </action>
                if (trimmed.startsWith("</action>") && currentAction != null) {
                    inAction = false;
                    if (currentAction.title != null) {
                        actions.add(currentAction);
                    }
                    currentAction = null;
                }
            }
            reader.close();
        } catch (Exception e) {
            // Skip unparseable
        }
        return actions;
    }

    private String extractAttr(String line, String attr) {
        String search = attr + "=\"";
        int start = line.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = line.indexOf("\"", start);
        if (end < 0) return null;
        return line.substring(start, end);
    }

    private String extractTag(String line, String tag) {
        String openTag = "<" + tag + ">";
        String closeTag = "</" + tag + ">";
        int start = line.indexOf(openTag);
        if (start < 0) return null;
        start += openTag.length();
        int end = line.indexOf(closeTag, start);
        if (end < 0) return null;
        return line.substring(start, end).trim();
    }

    // ==================== Helpers ====================

    private String findEntryScript(File dir) {
        String[] priorities = {"main.sh", "run.sh", "index.sh", "entry.sh", "install.sh"};
        for (String name : priorities) {
            if (new File(dir, name).exists()) return name;
        }
        File[] files = dir.listFiles((d, n) -> n.endsWith(".sh"));
        if (files != null && files.length > 0) return files[0].getName();
        return null;
    }

    private void unzip(File zipFile, File destDir) throws Exception {
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
        ZipEntry entry;
        byte[] buffer = new byte[8192];
        while ((entry = zis.getNextEntry()) != null) {
            File outFile = new File(destDir, entry.getName());
            if (!outFile.getCanonicalPath().startsWith(destDir.getCanonicalPath())) {
                throw new Exception("Zip slip detected");
            }
            if (entry.isDirectory()) {
                outFile.mkdirs();
            } else {
                outFile.getParentFile().mkdirs();
                FileOutputStream fos = new FileOutputStream(outFile);
                int len;
                while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                fos.close();
            }
            zis.closeEntry();
        }
        zis.close();
    }

    private void makeExecutable(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                makeExecutable(f);
            } else if (f.getName().endsWith(".sh")) {
                f.setExecutable(true, false);
            }
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        file.delete();
    }

    // ==================== Data Classes ====================

    public static class PluginInfo {
        public String name;
        public String path;
        public String description;
        public String entryScript;
        public boolean hasIndexXml;
        public List<PluginAction> actions;
    }

    public static class PluginAction {
        public String groupTitle;
        public String title;
        public String description;
        public String setCommand;
        public boolean interruptible = true;
        public List<PluginParam> params;
    }

    public static class PluginParam {
        public String name;
        public String title;
        public String label;
        public String desc;
        public String type;        // checkbox, switch, seekbar, file, float, text (default)
        public String placeholder;
        public String value;        // default value
        public boolean required;
        public boolean multiple;
        public String optionsSh;   // shell command for dynamic options
        public String separator;   // separator for multiple selections
        public int max = 100;
        public int min = 0;
    }
}
