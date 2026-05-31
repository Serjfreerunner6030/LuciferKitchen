package com.lucifer.kitchen.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * PermissionHelper is a static utility class that centralises all runtime-permission
 * logic for the Lucifer Kitchen app.
 *
 * <p>Behaviour by API level:
 * <ul>
 *   <li><strong>API &lt; 23 (Android 5):</strong> All permissions are granted at install
 *       time; the helper always reports them as granted.</li>
 *   <li><strong>API 23–29 (Android 6–9):</strong> {@code READ_EXTERNAL_STORAGE} and
 *       {@code WRITE_EXTERNAL_STORAGE} are requested at runtime.</li>
 *   <li><strong>API 30–32 (Android 11–12):</strong> {@code MANAGE_EXTERNAL_STORAGE}
 *       replaces the legacy permissions; the user is directed to the system "All files
 *       access" settings page.</li>
 *   <li><strong>API 33+ (Android 13+):</strong> Granular media permissions replace
 *       {@code READ_EXTERNAL_STORAGE}; no write permission exists. This helper requests
 *       only what the app needs.</li>
 * </ul>
 *
 * <p>This class has no public constructor — use the static methods directly.
 */
public final class PermissionHelper {

    private static final String TAG = "PermissionHelper";

    /**
     * Request code used internally when requesting legacy storage permissions.
     * Callers may pass their own request code to
     * {@link #requestStoragePermission(Activity, int)} or
     * {@link #checkAndRequestPermissions(Activity, int)}.
     */
    public static final int REQUEST_CODE_STORAGE = 1001;

    // Prevent instantiation
    private PermissionHelper() {
        throw new UnsupportedOperationException("PermissionHelper is a static utility class.");
    }

    // =========================================================================
    // Storage permission — legacy (API 23–29) and scoped (API 30+)
    // =========================================================================

    /**
     * Returns {@code true} if the app currently holds sufficient storage access.
     *
     * <ul>
     *   <li>On API 30+, delegates to {@link #hasManageStoragePermission(Context)}.</li>
     *   <li>On API 23–29, checks {@code READ_EXTERNAL_STORAGE} and
     *       {@code WRITE_EXTERNAL_STORAGE}.</li>
     *   <li>Below API 23, always returns {@code true} (install-time grant).</li>
     * </ul>
     *
     * @param context Any context.
     * @return {@code true} if storage access is available.
     */
    public static boolean hasStoragePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return hasManageStoragePermission(context);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean readGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;

            boolean writeGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;

            Log.d(TAG, "READ_EXTERNAL_STORAGE=" + readGranted
                    + " WRITE_EXTERNAL_STORAGE=" + writeGranted);

            return readGranted && writeGranted;
        }

        // Below API 23 — permissions were granted at install time
        return true;
    }

    /**
     * Requests storage permission(s) appropriate for the running Android version.
     *
     * <ul>
     *   <li>API 30+: Calls {@link #requestManageStoragePermission(Activity)} and ignores
     *       {@code requestCode} (the system handles the settings intent).</li>
     *   <li>API 23–29: Calls {@link ActivityCompat#requestPermissions} with
     *       {@code READ_EXTERNAL_STORAGE} and {@code WRITE_EXTERNAL_STORAGE}.</li>
     *   <li>Below API 23: No-op.</li>
     * </ul>
     *
     * @param activity    The foreground activity used to show the permission dialogue.
     * @param requestCode The request code forwarded to
     *                    {@link Activity#onRequestPermissionsResult} (API 23–29 only).
     */
    public static void requestStoragePermission(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestManageStoragePermission(activity);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d(TAG, "Requesting READ/WRITE_EXTERNAL_STORAGE (requestCode=" + requestCode + ")");
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    requestCode);
        }
        // Below API 23 — nothing to request
    }

    // =========================================================================
    // MANAGE_EXTERNAL_STORAGE — Android 11+ (API 30+)
    // =========================================================================

    /**
     * Returns {@code true} if the app holds the
     * {@link android.Manifest.permission#MANAGE_EXTERNAL_STORAGE} permission.
     *
     * <p>On devices below Android 11, this method always returns {@code true} because
     * {@code MANAGE_EXTERNAL_STORAGE} does not exist on those versions.
     *
     * @param context Any context.
     * @return {@code true} if the "All files access" permission is granted (or not needed).
     */
    public static boolean hasManageStoragePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean granted = Environment.isExternalStorageManager();
            Log.d(TAG, "MANAGE_EXTERNAL_STORAGE=" + granted);
            return granted;
        }
        return true;
    }

    /**
     * Opens the system "All files access" settings page for this app so the user
     * can grant {@link android.Manifest.permission#MANAGE_EXTERNAL_STORAGE}.
     *
     * <p>On devices below Android 11 this method is a no-op.
     *
     * @param activity The foreground activity used to start the settings intent.
     */
    public static void requestManageStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Log.d(TAG, "Opening MANAGE_APP_ALL_FILES_ACCESS_PERMISSION settings…");
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            } catch (Exception e) {
                // Fallback: open the generic "All files access" list page
                Log.w(TAG, "Specific intent failed, falling back to generic settings", e);
                try {
                    Intent fallback = new Intent(
                            Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    activity.startActivity(fallback);
                } catch (Exception ex) {
                    Log.e(TAG, "Unable to open manage-storage settings", ex);
                }
            }
        }
    }

    // =========================================================================
    // Convenience — check-and-request all required permissions at once
    // =========================================================================

    /**
     * Checks all permissions required by the app and, if any are missing, initiates
     * the appropriate permission request flow.
     *
     * <p>Returns {@code true} immediately when every required permission is already
     * granted so the caller can proceed without waiting for a callback.
     *
     * @param activity    The foreground activity.
     * @param requestCode The request code used for
     *                    {@link ActivityCompat#requestPermissions} (API 23–29 only).
     * @return {@code true} if all required permissions are already granted;
     *         {@code false} if the request flow has been initiated and the result
     *         will arrive in {@link Activity#onRequestPermissionsResult}.
     */
    public static boolean checkAndRequestPermissions(Activity activity, int requestCode) {
        if (hasStoragePermission(activity)) {
            Log.d(TAG, "All permissions already granted.");
            return true;
        }

        Log.d(TAG, "Permissions missing — initiating request flow.");
        requestStoragePermission(activity, requestCode);
        return false;
    }

    // =========================================================================
    // Permission result helper
    // =========================================================================

    /**
     * Convenience method to evaluate the arrays delivered to
     * {@link Activity#onRequestPermissionsResult}.
     *
     * <p>Returns {@code true} only when every entry in {@code grantResults} is
     * {@link PackageManager#PERMISSION_GRANTED} and the arrays are non-empty.
     *
     * <p>Usage in your Activity:
     * <pre>{@code
     * @Override
     * public void onRequestPermissionsResult(int requestCode,
     *         @NonNull String[] permissions, @NonNull int[] grantResults) {
     *     super.onRequestPermissionsResult(requestCode, permissions, grantResults);
     *     if (requestCode == MY_REQUEST_CODE) {
     *         boolean allGranted = PermissionHelper.onRequestPermissionsResult(
     *                 requestCode, permissions, grantResults);
     *         if (allGranted) { // proceed } else { // explain why }
     *     }
     * }
     * }</pre>
     *
     * @param requestCode  The request code (not evaluated here; present for symmetry).
     * @param permissions  The permission names returned by the system.
     * @param grantResults The grant results returned by the system.
     * @return {@code true} if every permission in the result was granted.
     */
    public static boolean onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        if (grantResults == null || grantResults.length == 0) {
            Log.w(TAG, "onRequestPermissionsResult: empty grantResults (requestCode="
                    + requestCode + ")");
            return false;
        }

        for (int i = 0; i < grantResults.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                String name = (permissions != null && i < permissions.length)
                        ? permissions[i] : "unknown";
                Log.w(TAG, "Permission denied: " + name);
                return false;
            }
        }

        Log.d(TAG, "All " + grantResults.length + " permission(s) granted "
                + "(requestCode=" + requestCode + ")");
        return true;
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Checks whether a single named permission is granted.
     *
     * @param context    Any context.
     * @param permission A permission string from {@link android.Manifest.permission}.
     * @return {@code true} if granted.
     */
    private static boolean isPermissionGranted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }
}
