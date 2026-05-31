package com.lucifer.kitchen.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BinaryManager manages extraction and setup of binary tools from APK assets
 * to the app's private files directory.
 *
 * <p>Asset layout expected in APK:
 * <pre>
 *   assets/tools/bin/      — executable binaries
 *   assets/tools/scripts/  — shell scripts
 * </pre>
 *
 * <p>Extracted to:
 * <pre>
 *   &lt;filesDir&gt;/tools/bin/
 *   &lt;filesDir&gt;/tools/scripts/
 * </pre>
 */
public class BinaryManager {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final String TAG = "BinaryManager";

    /** Preference file name used to persist the last-extracted version code. */
    private static final String PREFS_NAME = "binary_manager_prefs";

    /** Key storing the APK version code at the time of the last successful extraction. */
    private static final String KEY_EXTRACTED_VERSION = "extracted_version";

    /** Root asset folder that contains all tools to be extracted. */
    private static final String ASSETS_TOOLS_ROOT = "tools";

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Synchronisation lock used to prevent concurrent extractions. */
    private static final Object sExtractionLock = new Object();

    /** Singleton executor for async script execution — single thread to avoid races. */
    private static final ExecutorService sExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "BinaryManager-worker");
                t.setDaemon(true);
                return t;
            });

    // -------------------------------------------------------------------------
    // Application context (held only as application context to avoid leaks)
    // -------------------------------------------------------------------------

    private final Context mContext;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a BinaryManager tied to the given context.
     *
     * @param context Any context — the application context is extracted internally.
     */
    public BinaryManager(Context context) {
        mContext = context.getApplicationContext();
    }

    // -------------------------------------------------------------------------
    // Public API — directory accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the absolute path to the tools base directory.
     * The directory is {@code <filesDir>/tools}.
     */
    public String getToolsDir() {
        return mContext.getFilesDir().getAbsolutePath() + "/tools";
    }

    /**
     * Returns the absolute path to the {@code bin} directory inside tools.
     */
    public String getBinDir() {
        return getToolsDir() + "/bin";
    }

    /**
     * Returns the absolute path to the {@code scripts} directory inside tools.
     */
    public String getScriptsDir() {
        return getToolsDir() + "/scripts";
    }

    /**
     * Returns the full path to a named script inside the scripts directory.
     *
     * @param scriptName Filename of the script (e.g. {@code "setup.sh"}).
     */
    public String getScriptPath(String scriptName) {
        return getScriptsDir() + "/" + scriptName;
    }

    /**
     * Returns the full path to a named binary inside the bin directory.
     *
     * @param binName Filename of the binary (e.g. {@code "ffmpeg"}).
     */
    public String getBinPath(String binName) {
        return getBinDir() + "/" + binName;
    }

    // -------------------------------------------------------------------------
    // Public API — setup lifecycle
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the tools have already been extracted for the
     * current APK version. This is a lightweight check that reads SharedPreferences
     * and verifies the tools directory exists.
     */
    public boolean isSetupComplete() {
        File toolsDir = new File(getToolsDir());
        if (!toolsDir.exists() || !toolsDir.isDirectory()) {
            return false;
        }
        int currentVersion = getApkVersionCode();
        int extractedVersion = getExtractedVersion();
        return currentVersion != -1 && currentVersion == extractedVersion;
    }

    /**
     * Extracts all files from {@code assets/tools/} into the tools directory,
     * sets executable permissions on binaries and scripts, and records the
     * current APK version so that {@link #isSetupComplete()} returns {@code true}
     * until the next APK update.
     *
     * <p>This method is thread-safe; concurrent callers block until the first
     * caller completes.
     *
     * @param context Any context — the application context is used internally.
     * @throws IOException If an asset cannot be read or written to disk.
     */
    public void extractTools(Context context) throws IOException {
        synchronized (sExtractionLock) {
            Log.i(TAG, "Starting tools extraction…");

            // Ensure directory structure exists
            ensureDirectoryExists(getToolsDir());
            ensureDirectoryExists(getBinDir());
            ensureDirectoryExists(getScriptsDir());

            // Recursively extract all assets under "tools/"
            extractAssetFolder(context, ASSETS_TOOLS_ROOT, getToolsDir());

            // Apply executable permission to bin/ and scripts/
            setExecutableRecursive(new File(getBinDir()));
            setExecutableRecursive(new File(getScriptsDir()));

            // Force chmod via shell — needed on Android 10+ where /data has noexec
            forceChmodExecutable(getBinDir());
            forceChmodExecutable(getScriptsDir());

            // Persist the version code so we don't re-extract unnecessarily
            saveExtractedVersion(getApkVersionCode());

            Log.i(TAG, "Tools extraction complete.");
        }
    }

    /**
     * Uses Runtime.exec to run chmod on all files in a directory.
     * This works around the noexec mount restriction on /data on modern Android.
     */
    private void forceChmodExecutable(String dirPath) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                "sh", "-c", "chmod -R 755 \"" + dirPath + "\" 2>/dev/null; " +
                "find \"" + dirPath + "\" -type f -exec chmod 755 {} \\; 2>/dev/null"
            });
            p.waitFor();
        } catch (Exception e) {
            Log.w(TAG, "chmod failed for " + dirPath + ": " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Public API — environment
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link Map} of environment variables suitable for passing to a
     * {@link ProcessBuilder} when executing scripts.
     *
     * <ul>
     *   <li>{@code TOOLS_DIR} — absolute path to the tools root</li>
     *   <li>{@code BIN_DIR}   — absolute path to the bin directory</li>
     *   <li>{@code PATH}      — bin directory prepended to the system {@code PATH}</li>
     *   <li>{@code LD_LIBRARY_PATH} — set to the bin directory</li>
     *   <li>{@code HOME}      — set to the tools root directory</li>
     * </ul>
     *
     * @return A mutable map of environment variable name → value entries.
     */
    public Map<String, String> setupEnvironment() {
        Map<String, String> env = new HashMap<>();
        String toolsDir = getToolsDir();
        String binDir   = getBinDir();

        env.put("TOOLS_DIR",        toolsDir);
        env.put("BIN_DIR",          binDir);
        env.put("PATH",             binDir + ":" + System.getenv("PATH"));
        env.put("LD_LIBRARY_PATH",  binDir);
        env.put("HOME",             toolsDir);

        return env;
    }

    // -------------------------------------------------------------------------
    // Public API — script execution (synchronous)
    // -------------------------------------------------------------------------

    /**
     * Executes a script synchronously and returns the combined stdout output.
     *
     * <p>The script is located via {@link #getScriptPath(String)}. It is invoked
     * with {@code /bin/sh} so that scripts do not need to carry a shebang line.
     *
     * @param scriptName Name of the script file inside the scripts directory.
     * @param args       Optional arguments forwarded to the script.
     * @return The combined standard-output of the process.
     * @throws IOException          If the process cannot be started.
     * @throws InterruptedException If the calling thread is interrupted while waiting.
     */
    public String runScript(String scriptName, String... args)
            throws IOException, InterruptedException {

        List<String> command = buildScriptCommand(scriptName, args);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(setupEnvironment());
        pb.redirectErrorStream(true); // merge stderr into stdout

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }

        int exitCode = process.waitFor();
        Log.d(TAG, "Script '" + scriptName + "' exited with code " + exitCode);

        return output.toString();
    }

    // -------------------------------------------------------------------------
    // Public API — script execution (asynchronous)
    // -------------------------------------------------------------------------

    /**
     * Executes a script on a background thread and delivers results through
     * {@link ScriptCallback}.
     *
     * <p>Callback methods are invoked on the worker thread — callers that need
     * to update the UI must dispatch back to the main thread themselves.
     *
     * @param scriptName Name of the script file inside the scripts directory.
     * @param callback   Receiver for line-by-line output, completion, and errors.
     * @param args       Optional arguments forwarded to the script.
     */
    public void runScriptAsync(String scriptName, ScriptCallback callback, String... args) {
        sExecutor.submit(() -> {
            List<String> command = buildScriptCommand(scriptName, args);
            Process process = null;
            StringBuilder fullOutput = new StringBuilder();

            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.environment().putAll(setupEnvironment());
                pb.redirectErrorStream(true);

                process = pb.start();

                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        fullOutput.append(line).append('\n');
                        if (callback != null) {
                            callback.onOutput(line);
                        }
                    }
                }

                int exitCode = process.waitFor();
                Log.d(TAG, "Async script '" + scriptName + "' exited with " + exitCode);

                if (callback != null) {
                    callback.onComplete(exitCode, fullOutput.toString());
                }

            } catch (Exception e) {
                Log.e(TAG, "Error running async script '" + scriptName + "'", e);
                if (callback != null) {
                    callback.onError(e.getMessage() != null ? e.getMessage() : e.toString());
                }
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // ScriptCallback interface
    // -------------------------------------------------------------------------

    /**
     * Callback interface for asynchronous script execution.
     */
    public interface ScriptCallback {

        /**
         * Called for each line of output produced by the script.
         *
         * @param line A single line of output (without trailing newline).
         */
        void onOutput(String line);

        /**
         * Called when the script process has finished.
         *
         * @param exitCode   The process exit code (0 typically indicates success).
         * @param fullOutput The entire output captured during execution.
         */
        void onComplete(int exitCode, String fullOutput);

        /**
         * Called when an exception prevents the script from running or completing.
         *
         * @param error A human-readable description of the error.
         */
        void onError(String error);
    }

    // -------------------------------------------------------------------------
    // Private helpers — asset extraction
    // -------------------------------------------------------------------------

    /**
     * Recursively copies all entries under {@code assetPath} in the APK's asset
     * archive to {@code destPath} on disk.
     *
     * @param context   Context used to access {@link android.content.res.AssetManager}.
     * @param assetPath Relative path inside {@code assets/} (e.g. {@code "tools/bin"}).
     * @param destPath  Absolute filesystem path to write files into.
     * @throws IOException On read or write error.
     */
    private void extractAssetFolder(Context context, String assetPath, String destPath)
            throws IOException {
        String[] children;
        try {
            children = context.getAssets().list(assetPath);
        } catch (IOException e) {
            Log.w(TAG, "Cannot list assets at '" + assetPath + "': " + e.getMessage());
            return;
        }

        if (children == null || children.length == 0) {
            // Leaf file — copy it
            extractAssetFile(context, assetPath, destPath);
            return;
        }

        // Directory — ensure it exists and recurse
        ensureDirectoryExists(destPath);
        for (String child : children) {
            extractAssetFolder(
                    context,
                    assetPath + "/" + child,
                    destPath  + "/" + child);
        }
    }

    /**
     * Copies a single asset file to a destination path on disk.
     *
     * @param context   Context for {@link android.content.res.AssetManager}.
     * @param assetPath Relative path to the asset file.
     * @param destPath  Absolute filesystem destination path (including filename).
     * @throws IOException On read or write error.
     */
    private void extractAssetFile(Context context, String assetPath, String destPath)
            throws IOException {
        File dest = new File(destPath);

        // Make sure parent directories exist
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (InputStream in  = context.getAssets().open(assetPath);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }

        Log.v(TAG, "Extracted: " + assetPath + " → " + destPath);
    }

    // -------------------------------------------------------------------------
    // Private helpers — permissions
    // -------------------------------------------------------------------------

    /**
     * Recursively sets the executable bit on every file inside {@code dir}.
     *
     * @param dir The directory to traverse.
     */
    private void setExecutableRecursive(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }

        if (dir.isFile()) {
            if (!dir.setExecutable(true, false)) {
                Log.w(TAG, "Could not set executable on " + dir.getAbsolutePath());
            }
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            setExecutableRecursive(f);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers — directory management
    // -------------------------------------------------------------------------

    /**
     * Creates the directory at {@code path} (including any missing parents) if
     * it does not already exist.
     *
     * @param path Absolute path of the directory to create.
     * @throws IOException If the directory cannot be created.
     */
    private void ensureDirectoryExists(String path) throws IOException {
        File dir = new File(path);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create directory: " + path);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers — command construction
    // -------------------------------------------------------------------------

    /**
     * Builds the command list passed to {@link ProcessBuilder} for a script.
     *
     * @param scriptName Name of the script file.
     * @param args       Additional arguments.
     * @return A list whose first element is {@code /bin/sh}, second is the full
     *         script path, followed by any supplied arguments.
     */
    private List<String> buildScriptCommand(String scriptName, String... args) {
        List<String> command = new ArrayList<>();
        command.add("/bin/sh");
        command.add(getScriptPath(scriptName));
        if (args != null) {
            for (String arg : args) {
                command.add(arg);
            }
        }
        return command;
    }

    // -------------------------------------------------------------------------
    // Private helpers — version management
    // -------------------------------------------------------------------------

    /**
     * Returns the current APK version code, or {@code -1} if it cannot be read.
     */
    private int getApkVersionCode() {
        try {
            PackageInfo info = mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0);
            // versionCode is deprecated in API 28+; use longVersionCode there,
            // but cast to int for SharedPreferences compatibility.
            return (int) info.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not read APK version code", e);
            return -1;
        }
    }

    /**
     * Returns the version code that was current during the last successful extraction,
     * or {@code -1} if no extraction has been recorded.
     */
    private int getExtractedVersion() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_EXTRACTED_VERSION, -1);
    }

    /**
     * Persists {@code versionCode} so that future calls to {@link #isSetupComplete()}
     * can detect whether re-extraction is necessary.
     *
     * @param versionCode The APK version code to save.
     */
    private void saveExtractedVersion(int versionCode) {
        mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_EXTRACTED_VERSION, versionCode)
                .apply();
    }
}
