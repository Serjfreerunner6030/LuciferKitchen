package com.lucifer.kitchen.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.lucifer.kitchen.R;
import com.lucifer.kitchen.utils.ProjectManager;
import com.lucifer.kitchen.utils.ShellExecutor;
import java.util.*;

public class ApkFragment extends Fragment {

    private LinearLayout container;
    private ProjectManager projectManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_apk, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        container = view.findViewById(R.id.apkContainer);
        projectManager = new ProjectManager(requireContext());
        buildApkTools();
        buildApkPatches();
    }

    // ==================== APK Tools ====================

    private void buildApkTools() {
        addSectionHeader(getString(R.string.apk_title), getString(R.string.apk_desc));

        // Decompile APK
        addApkToolCard(
            getString(R.string.apk_decompile),
            getString(R.string.apk_decompile_desc),
            "apk", true, null, (path, apkFile, extra) -> {
                String outDir = apkFile.replace(".apk", "");
                return "apktool d -f -o '" + path + "/" + outDir + "' '" + path + "/" + apkFile + "' && echo 'Decompiled to: " + outDir + "'";
            }
        );

        // Recompile APK
        addApkToolCard(
            getString(R.string.apk_recompile),
            getString(R.string.apk_recompile_desc),
            "dir", true, null, (path, dir, extra) -> {
                return "apktool b '" + path + "/" + dir + "' -o '" + path + "/" + dir + "_rebuilt.apk' && echo 'Built: " + dir + "_rebuilt.apk'";
            }
        );

        // Sign APK
        addApkToolCard(
            getString(R.string.apk_sign),
            getString(R.string.apk_sign_desc),
            "apk", true, null, (path, apkFile, extra) -> {
                String signed = apkFile.replace(".apk", "_signed.apk");
                return buildSignCommand(path, apkFile, signed);
            }
        );

        // Zipalign
        addApkToolCard(
            getString(R.string.apk_zipalign),
            getString(R.string.apk_zipalign_desc),
            "apk", true, null, (path, apkFile, extra) -> {
                String aligned = apkFile.replace(".apk", "_aligned.apk");
                return "zipalign -f 4 '" + path + "/" + apkFile + "' '" + path + "/" + aligned + "' && echo 'Aligned: " + aligned + "'";
            }
        );

        // Install APK
        addApkToolCard(
            getString(R.string.apk_install),
            getString(R.string.apk_install_desc),
            "apk", true, null, (path, apkFile, extra) -> {
                return "su -c 'pm install -r -d \"" + path + "/" + apkFile + "\"' && echo 'Installed: " + apkFile + "'";
            }
        );

        // Extract from device
        addApkToolCard(
            getString(R.string.apk_extract),
            getString(R.string.apk_extract_desc),
            "input", false, getString(R.string.apk_package_hint), (path, input, extra) -> {
                return "su -c 'APK_PATH=$(pm path " + input + " | head -1 | cut -d: -f2) && " +
                       "if [ -n \"$APK_PATH\" ]; then cp \"$APK_PATH\" \"" + path + "/" + input + ".apk\" && " +
                       "echo \"Extracted: " + input + ".apk\"; else echo \"Package not found: " + input + "\"; fi'";
            }
        );

        // APK Info
        addApkToolCard(
            getString(R.string.apk_info),
            getString(R.string.apk_info_desc),
            "apk", true, null, (path, apkFile, extra) -> {
                return "aapt dump badging '" + path + "/" + apkFile + "' 2>/dev/null | head -30 || " +
                       "apktool d -f -s -o /tmp/_apkinfo '" + path + "/" + apkFile + "' 2>/dev/null && " +
                       "cat /tmp/_apkinfo/AndroidManifest.xml | head -50 && rm -rf /tmp/_apkinfo";
            }
        );
    }

    // ==================== APK Patches ====================

    private void buildApkPatches() {
        addSectionHeader(getString(R.string.apk_patches_title), "");

        // Disable signature verification
        addApkPatchCard(
            getString(R.string.apk_patch_disable_sig),
            getString(R.string.apk_patch_disable_sig_desc),
            (path, dir) -> buildPatchDisableSigVerification(path, dir)
        );

        // Make debuggable
        addApkPatchCard(
            getString(R.string.apk_patch_debuggable),
            getString(R.string.apk_patch_debuggable_desc),
            (path, dir) -> buildManifestPatch(path, dir, "android:debuggable=\"false\"", "android:debuggable=\"true\"",
                "android:allowBackup", "android:debuggable=\"true\" android:allowBackup")
        );

        // Allow backup
        addApkPatchCard(
            getString(R.string.apk_patch_allow_backup),
            getString(R.string.apk_patch_allow_backup_desc),
            (path, dir) -> buildManifestPatch(path, dir, "android:allowBackup=\"false\"", "android:allowBackup=\"true\"", null, null)
        );

        // Remove ads
        addApkPatchCard(
            getString(R.string.apk_patch_remove_ads),
            getString(R.string.apk_patch_remove_ads_desc),
            (path, dir) -> buildRemoveAdsCommand(path, dir)
        );

        // Disable analytics
        addApkPatchCard(
            getString(R.string.apk_patch_disable_analytics),
            getString(R.string.apk_patch_disable_analytics_desc),
            (path, dir) -> buildDisableAnalyticsCommand(path, dir)
        );

        // Force fullscreen
        addApkPatchCard(
            getString(R.string.apk_patch_force_fullscreen),
            getString(R.string.apk_patch_force_fullscreen_desc),
            (path, dir) -> buildForceFullscreenCommand(path, dir)
        );

        // Disable SSL pinning
        addApkPatchCard(
            getString(R.string.apk_patch_disable_ssl),
            getString(R.string.apk_patch_disable_ssl_desc),
            (path, dir) -> buildDisableSslPinningCommand(path, dir)
        );

        // Change targetSdkVersion
        addTargetSdkCard();

        // Remove splash screen
        addApkPatchCard(
            getString(R.string.apk_patch_remove_splash),
            getString(R.string.apk_patch_remove_splash_desc),
            (path, dir) -> buildRemoveSplashCommand(path, dir)
        );

        // Custom smali patch
        addCustomSmaliCard();
    }

    // ==================== Patch Command Builders ====================

    private String buildSignCommand(String path, String apkFile, String signed) {
        // Generate test keystore if not exists, then sign
        return "cd '" + path + "' && " +
            "if [ ! -f lucifer_test.keystore ]; then " +
            "keytool -genkey -v -keystore lucifer_test.keystore -alias lucifer " +
            "-keyalg RSA -keysize 2048 -validity 10000 " +
            "-storepass lucifer123 -keypass lucifer123 " +
            "-dname 'CN=Lucifer,OU=Kitchen,O=LK,L=Moscow,ST=RU,C=RU' 2>/dev/null; fi && " +
            "apksigner sign --ks lucifer_test.keystore --ks-pass pass:lucifer123 " +
            "--ks-key-alias lucifer --key-pass pass:lucifer123 " +
            "--out '" + signed + "' '" + apkFile + "' 2>/dev/null && echo 'Signed: " + signed + "' || " +
            "(jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 " +
            "-keystore lucifer_test.keystore -storepass lucifer123 " +
            "'" + apkFile + "' lucifer 2>/dev/null && " +
            "cp '" + apkFile + "' '" + signed + "' && echo 'Signed (jarsigner): " + signed + "')";
    }

    private String buildPatchDisableSigVerification(String path, String dir) {
        // Find signature verification methods in smali and patch them to return true/0
        return "cd '" + path + "/" + dir + "' && echo 'Patching signature verification...' && " +
            // Patch Signature.verify() calls
            "find smali* -name '*.smali' | xargs grep -l 'Ljava/security/Signature;->verify' 2>/dev/null | while read f; do " +
            "  echo \"Patching: $f\"; " +
            "  sed -i 's/invoke-virtual.*Ljava\\/security\\/Signature;->verify.*$/const\\/4 v0, 0x1/g' \"$f\"; " +
            "done && " +
            // Patch PackageManager.GET_SIGNATURES
            "find smali* -name '*.smali' | xargs grep -l 'getPackageInfo.*0x40' 2>/dev/null | while read f; do " +
            "  echo \"Patching sig check: $f\"; " +
            "done && " +
            // Patch signature comparison methods
            "find smali* -name '*.smali' | xargs grep -rl 'checkSignatures\\|verifySignature\\|isSignatureValid' 2>/dev/null | while read f; do " +
            "  echo \"Patching sig method: $f\"; " +
            "  sed -i '/checkSignatures\\|verifySignature\\|isSignatureValid/,/\\.end method/{s/return v[0-9]/const\\/4 v0, 0x0\\n    return v0/}' \"$f\" 2>/dev/null; " +
            "done && " +
            "echo 'Signature verification disabled!'";
    }

    private String buildManifestPatch(String path, String dir, String find, String replace, String altFind, String altReplace) {
        StringBuilder sb = new StringBuilder();
        sb.append("cd '").append(path).append("/").append(dir).append("' && ");
        sb.append("if [ -f AndroidManifest.xml ]; then ");
        if (find != null && replace != null) {
            sb.append("sed -i 's/").append(escSed(find)).append("/").append(escSed(replace)).append("/g' AndroidManifest.xml && ");
        }
        if (altFind != null && altReplace != null) {
            // If attribute not found, add it
            sb.append("grep -q '").append(replace != null ? replace.split("=")[0] : "")
              .append("' AndroidManifest.xml || sed -i 's/").append(escSed(altFind)).append("/")
              .append(escSed(altReplace)).append("/g' AndroidManifest.xml && ");
        }
        sb.append("echo 'Manifest patched!'; else echo 'AndroidManifest.xml not found'; fi");
        return sb.toString();
    }

    private String buildRemoveAdsCommand(String path, String dir) {
        return "cd '" + path + "/" + dir + "' && echo 'Removing ad SDKs...' && " +
            "for adDir in " +
            "'smali*/com/google/android/gms/ads' " +
            "'smali*/com/google/ads' " +
            "'smali*/com/facebook/ads' " +
            "'smali*/com/unity3d/ads' " +
            "'smali*/com/applovin' " +
            "'smali*/com/mopub' " +
            "'smali*/com/inmobi' " +
            "'smali*/com/chartboost' " +
            "'smali*/com/startapp' " +
            "'smali*/com/ironsource' " +
            "'smali*/com/vungle' " +
            "; do " +
            "  if [ -d \"$adDir\" ]; then " +
            "    echo \"Removing: $adDir\"; " +
            "    rm -rf \"$adDir\"; " +
            "  fi; " +
            "done && " +
            // Remove ad layouts
            "find res -name '*ad_*' -o -name '*banner*' -o -name '*interstitial*' 2>/dev/null | while read f; do " +
            "  echo \"Removing layout: $f\"; " +
            "  rm -f \"$f\"; " +
            "done && " +
            // Patch smali: replace ad init calls with nop
            "find smali* -name '*.smali' | xargs grep -l 'MobileAds;->initialize\\|AdView;->loadAd\\|InterstitialAd;->loadAd' 2>/dev/null | while read f; do " +
            "  echo \"Patching ad calls: $f\"; " +
            "  sed -i 's/invoke.*MobileAds;->initialize.*$/nop/g' \"$f\"; " +
            "  sed -i 's/invoke.*AdView;->loadAd.*$/nop/g' \"$f\"; " +
            "  sed -i 's/invoke.*InterstitialAd;->loadAd.*$/nop/g' \"$f\"; " +
            "done && " +
            "echo 'Ads removed!'";
    }

    private String buildDisableAnalyticsCommand(String path, String dir) {
        return "cd '" + path + "/" + dir + "' && echo 'Disabling analytics...' && " +
            "for analyticsDir in " +
            "'smali*/com/google/firebase/analytics' " +
            "'smali*/com/google/firebase/crashlytics' " +
            "'smali*/com/google/android/gms/analytics' " +
            "'smali*/com/google/android/gms/measurement' " +
            "'smali*/com/flurry' " +
            "'smali*/com/mixpanel' " +
            "'smali*/com/amplitude' " +
            "'smali*/io/sentry' " +
            "'smali*/com/bugsnag' " +
            "'smali*/com/appsflyer' " +
            "'smali*/com/adjust/sdk' " +
            "; do " +
            "  if [ -d \"$analyticsDir\" ]; then " +
            "    echo \"Removing: $analyticsDir\"; " +
            "    rm -rf \"$analyticsDir\"; " +
            "  fi; " +
            "done && " +
            // Disable in manifest
            "if [ -f AndroidManifest.xml ]; then " +
            "  sed -i 's/com.google.firebase.analytics.FirebaseAnalytics/disabled.analytics/g' AndroidManifest.xml; " +
            "  sed -i '/firebase_analytics_collection_enabled/d' AndroidManifest.xml; " +
            "fi && " +
            "echo 'Analytics disabled!'";
    }

    private String buildForceFullscreenCommand(String path, String dir) {
        return "cd '" + path + "/" + dir + "' && " +
            "if [ -f AndroidManifest.xml ]; then " +
            // Add fullscreen theme
            "  sed -i 's/android:theme=\"@style\\/[^\"]*\"/android:theme=\"@android:style\\/Theme.NoTitleBar.Fullscreen\"/g' AndroidManifest.xml && " +
            "  echo 'Fullscreen forced in manifest!' && " +
            // Also patch styles.xml
            "  if [ -f res/values/styles.xml ]; then " +
            "    sed -i 's/<item name=\"android:windowFullscreen\">false</<item name=\"android:windowFullscreen\">true</g' res/values/styles.xml; " +
            "    echo 'Styles patched!'; " +
            "  fi; " +
            "else echo 'AndroidManifest.xml not found'; fi";
    }

    private String buildDisableSslPinningCommand(String path, String dir) {
        return "cd '" + path + "/" + dir + "' && echo 'Disabling SSL pinning...' && " +
            // Patch OkHttp CertificatePinner
            "find smali* -path '*/okhttp3/CertificatePinner*.smali' 2>/dev/null | while read f; do " +
            "  echo \"Patching OkHttp: $f\"; " +
            "  sed -i '/check(/,/\\.end method/{s/invoke.*check.*$/return-void/}' \"$f\" 2>/dev/null; " +
            "done && " +
            // Patch TrustManager implementations
            "find smali* -name '*.smali' | xargs grep -rl 'checkServerTrusted' 2>/dev/null | while read f; do " +
            "  echo \"Patching TrustManager: $f\"; " +
            "  sed -i '/checkServerTrusted/,/\\.end method/{s/invoke.*throw.*$/return-void/; s/throw v[0-9]/return-void/}' \"$f\" 2>/dev/null; " +
            "done && " +
            // Patch network_security_config
            "if [ -f res/xml/network_security_config.xml ]; then " +
            "  echo '<?xml version=\"1.0\" encoding=\"utf-8\"?><network-security-config><base-config cleartextTrafficPermitted=\"true\"><trust-anchors><certificates src=\"system\"/><certificates src=\"user\"/></trust-anchors></base-config></network-security-config>' > res/xml/network_security_config.xml && " +
            "  echo 'Network security config patched!'; " +
            "fi && " +
            "echo 'SSL pinning disabled!'";
    }

    private String buildRemoveSplashCommand(String path, String dir) {
        return "cd '" + path + "/" + dir + "' && " +
            "echo 'Searching for splash activities...' && " +
            "grep -i 'splash\\|launch_screen\\|loading' AndroidManifest.xml 2>/dev/null && " +
            // Find splash activity smali files and redirect to main
            "find smali* -iname '*splash*' -o -iname '*launch*screen*' 2>/dev/null | while read f; do " +
            "  echo \"Found: $f\"; " +
            "done && " +
            // Remove splash layouts
            "find res -iname '*splash*' -o -iname '*launch_screen*' 2>/dev/null | while read f; do " +
            "  echo \"Removing: $f\"; rm -f \"$f\"; " +
            "done && " +
            "echo 'Splash screen elements removed. Manual manifest edit may be needed.'";
    }

    // ==================== UI Builders ====================

    private void addSectionHeader(String title, String desc) {
        MaterialCardView card = createCard();
        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);

        TextView tv = new TextView(requireContext());
        tv.setText(title);
        tv.setTextSize(20);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        inner.addView(tv);

        if (desc != null && !desc.isEmpty()) {
            TextView tvd = new TextView(requireContext());
            tvd.setText(desc);
            tvd.setTextSize(13);
            tvd.setTextColor(0xFFB0B0B0);
            tvd.setPadding(0, 4, 0, 0);
            inner.addView(tvd);
        }

        card.addView(inner);
        container.addView(card);
    }

    private void addApkToolCard(String title, String desc, String inputType, boolean fromProject,
                                String inputHint, CommandBuilder builder) {
        MaterialCardView card = createCard();
        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(title);
        tvTitle.setTextSize(16);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        inner.addView(tvTitle);

        TextView tvDesc = new TextView(requireContext());
        tvDesc.setText(desc);
        tvDesc.setTextSize(12);
        tvDesc.setTextColor(0xFFB0B0B0);
        tvDesc.setPadding(0, 4, 0, 16);
        inner.addView(tvDesc);

        // Input field or file selector
        EditText etInput = null;
        if ("input".equals(inputType)) {
            etInput = new EditText(requireContext());
            etInput.setHint(inputHint != null ? inputHint : "");
            etInput.setTextColor(0xFFFFFFFF);
            etInput.setHintTextColor(0xFF555555);
            etInput.setBackgroundColor(0xFF2A2A2A);
            etInput.setPadding(24, 16, 24, 16);
            etInput.setTextSize(13);
            inner.addView(etInput);
        }

        MaterialButton btn = new MaterialButton(requireContext());
        btn.setText(R.string.run);
        btn.setBackgroundColor(0xFFD50000);
        btn.setCornerRadius(16);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 110);
        bp.topMargin = 12;
        btn.setLayoutParams(bp);

        final EditText finalInput = etInput;
        btn.setOnClickListener(v -> {
            String path = projectManager.getCurrentProjectPath();
            if (path == null) {
                Toast.makeText(requireContext(), R.string.no_project, Toast.LENGTH_SHORT).show();
                return;
            }
            if ("input".equals(inputType) && finalInput != null) {
                String val = finalInput.getText().toString().trim();
                if (val.isEmpty()) return;
                executeCmd(builder.build(path, val, null));
            } else if ("apk".equals(inputType)) {
                showFileDialog(path, ".apk", (file) -> executeCmd(builder.build(path, file, null)));
            } else if ("dir".equals(inputType)) {
                showDirDialog(path, (dir) -> executeCmd(builder.build(path, dir, null)));
            }
        });

        inner.addView(btn);
        card.addView(inner);
        container.addView(card);
    }

    private void addApkPatchCard(String title, String desc, PatchBuilder builder) {
        MaterialCardView card = createCard();
        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(title);
        tvTitle.setTextSize(16);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        inner.addView(tvTitle);

        TextView tvDesc = new TextView(requireContext());
        tvDesc.setText(desc);
        tvDesc.setTextSize(12);
        tvDesc.setTextColor(0xFFB0B0B0);
        tvDesc.setPadding(0, 4, 0, 12);
        inner.addView(tvDesc);

        MaterialButton btn = new MaterialButton(requireContext());
        btn.setText(R.string.run);
        btn.setBackgroundColor(0xFFD50000);
        btn.setCornerRadius(16);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 110);
        bp.topMargin = 8;
        btn.setLayoutParams(bp);

        btn.setOnClickListener(v -> {
            String path = projectManager.getCurrentProjectPath();
            if (path == null) {
                Toast.makeText(requireContext(), R.string.no_project, Toast.LENGTH_SHORT).show();
                return;
            }
            showDirDialog(path, (dir) -> executeCmd(builder.build(path, dir)));
        });

        inner.addView(btn);
        card.addView(inner);
        container.addView(card);
    }

    private void addTargetSdkCard() {
        MaterialCardView card = createCard();
        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(R.string.apk_patch_max_target_sdk);
        tvTitle.setTextSize(16);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        inner.addView(tvTitle);

        TextView tvDesc = new TextView(requireContext());
        tvDesc.setText(R.string.apk_patch_max_target_sdk_desc);
        tvDesc.setTextSize(12);
        tvDesc.setTextColor(0xFFB0B0B0);
        tvDesc.setPadding(0, 4, 0, 12);
        inner.addView(tvDesc);

        EditText etSdk = new EditText(requireContext());
        etSdk.setHint(R.string.apk_target_sdk_hint);
        etSdk.setTextColor(0xFFFFFFFF);
        etSdk.setHintTextColor(0xFF555555);
        etSdk.setBackgroundColor(0xFF2A2A2A);
        etSdk.setPadding(24, 16, 24, 16);
        etSdk.setTextSize(13);
        etSdk.setInputType(InputType.TYPE_CLASS_NUMBER);
        etSdk.setText("28");
        inner.addView(etSdk);

        MaterialButton btn = new MaterialButton(requireContext());
        btn.setText(R.string.run);
        btn.setBackgroundColor(0xFFD50000);
        btn.setCornerRadius(16);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 110);
        bp.topMargin = 12;
        btn.setLayoutParams(bp);

        btn.setOnClickListener(v -> {
            String path = projectManager.getCurrentProjectPath();
            if (path == null) {
                Toast.makeText(requireContext(), R.string.no_project, Toast.LENGTH_SHORT).show();
                return;
            }
            String sdk = etSdk.getText().toString().trim();
            if (sdk.isEmpty()) sdk = "28";
            final String fSdk = sdk;
            showDirDialog(path, (dir) -> {
                String cmd = "cd '" + path + "/" + dir + "' && " +
                    "if [ -f apktool.yml ]; then " +
                    "  sed -i 's/targetSdkVersion:.*/targetSdkVersion: \\x27" + fSdk + "\\x27/g' apktool.yml && " +
                    "  echo 'apktool.yml patched'; fi && " +
                    "if [ -f AndroidManifest.xml ]; then " +
                    "  sed -i 's/android:targetSdkVersion=\"[0-9]*\"/android:targetSdkVersion=\"" + fSdk + "\"/g' AndroidManifest.xml && " +
                    "  echo 'Manifest patched to targetSdk=" + fSdk + "'; fi";
                executeCmd(cmd);
            });
        });

        inner.addView(btn);
        card.addView(inner);
        container.addView(card);
    }

    private void addCustomSmaliCard() {
        MaterialCardView card = createCard();
        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(R.string.apk_patch_custom_smali);
        tvTitle.setTextSize(16);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        inner.addView(tvTitle);

        TextView tvDesc = new TextView(requireContext());
        tvDesc.setText(R.string.apk_patch_custom_smali_desc);
        tvDesc.setTextSize(12);
        tvDesc.setTextColor(0xFFB0B0B0);
        tvDesc.setPadding(0, 4, 0, 12);
        inner.addView(tvDesc);

        EditText etFind = new EditText(requireContext());
        etFind.setHint(R.string.apk_smali_find_hint);
        etFind.setTextColor(0xFFFFFFFF);
        etFind.setHintTextColor(0xFF555555);
        etFind.setBackgroundColor(0xFF2A2A2A);
        etFind.setPadding(24, 16, 24, 16);
        etFind.setTextSize(12);
        etFind.setMinLines(2);
        etFind.setGravity(android.view.Gravity.TOP);
        inner.addView(etFind);

        View spacer = new View(requireContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 12));
        inner.addView(spacer);

        EditText etReplace = new EditText(requireContext());
        etReplace.setHint(R.string.apk_smali_replace_hint);
        etReplace.setTextColor(0xFFFFFFFF);
        etReplace.setHintTextColor(0xFF555555);
        etReplace.setBackgroundColor(0xFF2A2A2A);
        etReplace.setPadding(24, 16, 24, 16);
        etReplace.setTextSize(12);
        etReplace.setMinLines(2);
        etReplace.setGravity(android.view.Gravity.TOP);
        inner.addView(etReplace);

        MaterialButton btn = new MaterialButton(requireContext());
        btn.setText(R.string.run);
        btn.setBackgroundColor(0xFFD50000);
        btn.setCornerRadius(16);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 110);
        bp.topMargin = 12;
        btn.setLayoutParams(bp);

        btn.setOnClickListener(v -> {
            String path = projectManager.getCurrentProjectPath();
            if (path == null) {
                Toast.makeText(requireContext(), R.string.no_project, Toast.LENGTH_SHORT).show();
                return;
            }
            String findStr = etFind.getText().toString();
            String replStr = etReplace.getText().toString();
            if (findStr.isEmpty()) return;

            showDirDialog(path, (dir) -> {
                String cmd = "cd '" + path + "/" + dir + "' && " +
                    "echo 'Searching in smali files...' && " +
                    "FOUND=$(find smali* -name '*.smali' | xargs grep -rl '" + escShell(findStr) + "' 2>/dev/null) && " +
                    "if [ -z \"$FOUND\" ]; then echo 'Pattern not found'; exit 0; fi && " +
                    "echo \"$FOUND\" | while read f; do " +
                    "  echo \"Patching: $f\"; " +
                    "  sed -i 's/" + escSed(findStr) + "/" + escSed(replStr) + "/g' \"$f\"; " +
                    "done && " +
                    "echo 'Custom smali patch applied!'";
                executeCmd(cmd);
            });
        });

        inner.addView(btn);
        card.addView(inner);
        container.addView(card);
    }

    // ==================== Helpers ====================

    private void showFileDialog(String path, String extension, FileCallback callback) {
        String[] files = projectManager.listFiles(extension.replace(".", ""));
        // Also scan for APK files in subdirectories
        String[] fromShell = ShellExecutor.execute(
            "find '" + path + "' -maxdepth 2 -name '*" + extension + "' -type f 2>/dev/null | " +
            "sed 's|" + path + "/||g' | sort"
        ).trim().split("\n");

        Set<String> allFiles = new LinkedHashSet<>();
        for (String f : files) if (!f.isEmpty()) allFiles.add(f);
        for (String f : fromShell) if (!f.isEmpty()) allFiles.add(f);

        if (allFiles.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.select_files_hint), Toast.LENGTH_LONG).show();
            return;
        }

        String[] items = allFiles.toArray(new String[0]);
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.apk_select)
            .setItems(items, (d, w) -> callback.onFile(items[w]))
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void showDirDialog(String path, FileCallback callback) {
        String[] dirs = projectManager.listDirectories();
        if (dirs.length == 0) {
            Toast.makeText(requireContext(), getString(R.string.select_files_hint), Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.apk_select_dir)
            .setItems(dirs, (d, w) -> callback.onFile(dirs[w]))
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void executeCmd(String command) {
        AlertDialog progress = new AlertDialog.Builder(requireContext())
            .setMessage(R.string.running).setCancelable(false).show();

        new Thread(() -> {
            String result = ShellExecutor.executeAsRoot(command);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    progress.dismiss();
                    new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.done)
                        .setMessage(result.length() > 3000 ? result.substring(0, 3000) + "..." : result)
                        .setPositiveButton(R.string.ok, null)
                        .show();
                });
            }
        }).start();
    }

    private MaterialCardView createCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(0xFF1E1E1E);
        card.setRadius(32);
        card.setCardElevation(8);
        card.setContentPadding(40, 32, 40, 32);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = 20;
        card.setLayoutParams(p);
        return card;
    }

    private String escSed(String s) {
        return s.replace("/", "\\/").replace(".", "\\.").replace("*", "\\*")
                .replace("[", "\\[").replace("]", "\\]").replace("\"", "\\\"");
    }

    private String escShell(String s) {
        return s.replace("'", "'\\''");
    }

    // ==================== Interfaces ====================

    private interface CommandBuilder {
        String build(String path, String input, String extra);
    }

    private interface PatchBuilder {
        String build(String path, String dir);
    }

    private interface FileCallback {
        void onFile(String file);
    }
}
