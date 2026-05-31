package com.lucifer.kitchen.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import android.widget.TextView;
import android.widget.CheckBox;
import com.lucifer.kitchen.R;
import com.lucifer.kitchen.utils.BinaryManager;
import com.lucifer.kitchen.utils.ProjectManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ActionListFragment extends Fragment {

    private static final String ARG_TYPE = "type";
    private LinearLayout container;
    private ProjectManager projectManager;
    private BinaryManager binaryManager;

    private static final String[] ANDROID_VERSIONS = {
        "Android 10 (API 29)", "Android 11 (API 30)", "Android 12 (API 31)",
        "Android 12L (API 32)", "Android 13 (API 33)", "Android 14 (API 34)",
        "Android 15 (API 35)", "Android 16 (API 36)"
    };
    private static final int[] ANDROID_APIS = {29, 30, 31, 32, 33, 34, 35, 36};
    private static final String[] ROM_TYPES = {"AOSP", "MIUI", "HyperOS"};

    private static final String[] PARTITION_NAMES = {
        "system", "vendor", "product", "system_ext", "odm", "boot", "recovery",
        "vbmeta", "dtbo", "super", "modem", "vendor_boot"
    };

    private static final String[] FS_TYPES = {"ext4", "erofs", "f2fs"};
    private static final String[] FORMAT_TYPES = {"sparse", "raw"};

    public static ActionListFragment newInstance(String type) {
        ActionListFragment f = new ActionListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_action_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        container = view.findViewById(R.id.actionContainer);
        projectManager = new ProjectManager(requireContext());
        binaryManager = new BinaryManager(requireContext());

        String type = getArguments() != null ? getArguments().getString(ARG_TYPE, "unpack") : "unpack";
        switch (type) {
            case "unpack": buildUnpackMenu(); break;
            case "repack": buildRepackMenu(); break;
            case "patch": buildPatchMenu(); break;
            case "boot": buildBootMenu(); break;
            case "tools": buildToolsMenu(); break;
        }
    }

    // ==================== UNPACK ====================
    private void buildUnpackMenu() {
        addScriptCard(getString(R.string.unpack_payload),
            "Unpack payload.bin into individual partition images",
            "payload_dump.sh", new String[]{"%PROJECT%"});
        addScriptCard(getString(R.string.unpack_br),
            "Decompress .dat.br brotli files",
            "unpack_br.sh", new String[]{"%PROJECT%"});
        addScriptCard(getString(R.string.unpack_dat),
            "Convert .dat files to raw images",
            "unpack_dat.sh", new String[]{"%PROJECT%"});
        addScriptCard(getString(R.string.unpack_img),
            "Unpack .img partition images (ext4/erofs/f2fs)",
            "unpack_img.sh", new String[]{"%PROJECT%"});
        addScriptCard(getString(R.string.unpack_super),
            "Extract logical partitions from super.img",
            "unpack_super.sh", new String[]{"%PROJECT%"});
        addScriptCard(getString(R.string.unpack_zstd),
            "Decompress .zst compressed images",
            "unpack_zst.sh", new String[]{"%PROJECT%"});
    }

    // ==================== REPACK ====================
    private void buildRepackMenu() {
        // Repack single — needs partition name, fs type, format selectors
        addRepackSingleCard();
        // Repack super
        addScriptCard(getString(R.string.repack_super),
            "Rebuild super.img from individual partitions",
            "repack_super.sh", new String[]{"%PROJECT%"});
        addScriptCard(getString(R.string.convert_img_simg),
            "Convert raw IMG to sparse IMG format",
            "convert_img2simg.sh", new String[]{"%PROJECT%"});
        addScriptCard(getString(R.string.convert_img_dat),
            "Convert IMG to DAT+BR format",
            "convert_img2dat.sh", new String[]{"%PROJECT%"});
        addScriptCard(getString(R.string.convert_zst),
            "ZST/IMG format conversion",
            "convert_zst.sh", new String[]{"%PROJECT%"});
        addScriptCard(getString(R.string.merge_sparse),
            "Merge sparse image segments into one",
            "merge_sparse.sh", new String[]{"%PROJECT%"});
    }

    // ==================== PATCH ====================
    private void buildPatchMenu() {
        addScriptCard(getString(R.string.patch_vbmeta),
            "Disable AVB verification by patching vbmeta",
            "patch_vbmeta.sh", new String[]{"%PROJECT%"});
        addScriptCard(getString(R.string.patch_integrity),
            "Fix Play Integrity fingerprint",
            "patch_integrity.sh", new String[]{"%PROJECT%"});
        addScriptCard(getString(R.string.patch_privapp),
            "Skip priv-app permissions enforcement",
            "patch_privapp.sh", new String[]{"%PROJECT%"});
        addScriptCard(getString(R.string.patch_ota),
            "Disable automatic OTA updates",
            "patch_ota.sh", new String[]{"%PROJECT%"});
        addScriptCard(getString(R.string.patch_debloat),
            "Remove bloatware from MIUI/HyperOS ROMs",
            "patch_debloat.sh", new String[]{"%PROJECT%"});
        addScriptCard(getString(R.string.patch_rw),
            "Make ROM partitions full read-write",
            "patch_rw.sh", new String[]{"%PROJECT%"});
        addScriptCard(getString(R.string.patch_decrypt),
            "Remove encryption from fstab for dirty flash",
            "patch_decrypt.sh", new String[]{"%PROJECT%"});
        addScriptCard(getString(R.string.patch_remove_oat),
            "Remove OAT/ODEX files to free space",
            "patch_remove_oat.sh", new String[]{"%PROJECT%"});

        // Jar patches with version selectors
        addJarPatchCard(getString(R.string.patch_core_dsv),
            getString(R.string.patch_core_dsv_desc), "core_dsv");
        addJarPatchCard(getString(R.string.patch_remove_apk_prot),
            getString(R.string.patch_remove_apk_prot_desc), "remove_apk_prot");
        addJarPatchCard(getString(R.string.patch_disable_secure_ss),
            getString(R.string.patch_disable_secure_ss_desc), "disable_secure_ss");
    }

    // ==================== BOOT ====================
    private void buildBootMenu() {
        addBootFileCard(getString(R.string.boot_unpack),
            "Unpack boot/recovery/vendor_boot.img (kernel + ramdisk + dtb)",
            "boot_unpack.sh", "boot.img");
        addBootFileCard(getString(R.string.boot_repack),
            "Repack boot image from unpacked components",
            "boot_repack.sh", "boot_unpacked");
        addBootFileCard(getString(R.string.boot_patch_magisk),
            "Patch boot.img for Magisk root (remove verity + skip_initramfs)",
            "boot_patch_magisk.sh", "boot.img");
        addBootInfoCard();
        addExtractKernelCard();
        addFlashBootCard();
    }

    // ==================== TOOLS ====================
    private void buildToolsMenu() {
        addExtractPartitionCard();
        addFlashPartitionCard();
        addActionCard(getString(R.string.script_executor),
            "Execute custom shell scripts",
            null); // handled specially
    }

    // ========================================================================
    // CARD BUILDERS
    // ========================================================================

    // --- Repack single with partition/fs/format selectors ---
    private void addRepackSingleCard() {
        MaterialCardView card = createCard();
        LinearLayout inner = createInnerLayout();

        addTitle(inner, getString(R.string.repack_single));
        addDesc(inner, "Repack a single partition to ext4/erofs/f2fs image");

        // Partition name spinner
        addLabel(inner, getString(R.string.select_partition));
        Spinner spinPart = createSpinner(PARTITION_NAMES);
        inner.addView(spinPart);

        // FS type spinner
        addLabel(inner, getString(R.string.repack_type));
        Spinner spinFs = createSpinner(FS_TYPES);
        inner.addView(spinFs);

        // Format spinner
        addLabel(inner, getString(R.string.partition_type));
        Spinner spinFmt = createSpinner(FORMAT_TYPES);
        inner.addView(spinFmt);

        // R/W checkbox
        CheckBox cbRw = new CheckBox(requireContext());
        cbRw.setText(R.string.rw_mode);
        cbRw.setTextColor(0xFFB0B0B0);
        cbRw.setTextSize(13);
        LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cbParams.topMargin = 12;
        cbRw.setLayoutParams(cbParams);
        inner.addView(cbRw);

        // Auto-size checkbox
        CheckBox cbAutoSize = new CheckBox(requireContext());
        cbAutoSize.setText(R.string.auto_size);
        cbAutoSize.setTextColor(0xFFB0B0B0);
        cbAutoSize.setTextSize(13);
        cbAutoSize.setChecked(true);
        inner.addView(cbAutoSize);

        MaterialButton btn = createRunButton();
        btn.setOnClickListener(v -> {
            String projectPath = requireProjectPath();
            if (projectPath == null) return;
            String part = PARTITION_NAMES[spinPart.getSelectedItemPosition()];
            String fs = FS_TYPES[spinFs.getSelectedItemPosition()];
            String fmt = FORMAT_TYPES[spinFmt.getSelectedItemPosition()];
            String rw = cbRw.isChecked() ? "1" : "0";
            String autoSz = cbAutoSize.isChecked() ? "1" : "0";
            executeScript("repack_single.sh", projectPath, part, fs, fmt, rw, autoSz);
        });
        inner.addView(btn);
        card.addView(inner);
        container.addView(card);
    }

    // --- Boot file card — auto-detects files in project ---
    private void addBootFileCard(String title, String desc, String scriptName, String defaultFile) {
        MaterialCardView card = createCard();
        LinearLayout inner = createInnerLayout();
        addTitle(inner, title);
        addDesc(inner, desc);

        MaterialButton btn = createRunButton();
        btn.setOnClickListener(v -> {
            String projectPath = requireProjectPath();
            if (projectPath == null) return;

            // Find matching files in project
            List<String> found = findBootFiles(projectPath, defaultFile);
            if (found.isEmpty()) {
                // Let user type a path
                showInputDialog(title, "File path (relative to project):", defaultFile, input -> {
                    String filePath = projectPath + "/" + input;
                    executeScript(scriptName, filePath);
                });
            } else if (found.size() == 1) {
                executeScript(scriptName, found.get(0));
            } else {
                // Show selection dialog
                String[] items = found.toArray(new String[0]);
                String[] names = new String[items.length];
                for (int i = 0; i < items.length; i++) {
                    names[i] = items[i].replace(projectPath + "/", "");
                }
                new AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.select_file))
                    .setItems(names, (d, w) -> executeScript(scriptName, items[w]))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            }
        });
        inner.addView(btn);
        card.addView(inner);
        container.addView(card);
    }

    private List<String> findBootFiles(String projectPath, String pattern) {
        List<String> result = new ArrayList<>();
        File dir = new File(projectPath);
        if (!dir.exists()) return result;
        File[] files = dir.listFiles();
        if (files == null) return result;

        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (pattern.equals("boot.img")) {
                if (name.endsWith(".img") && (name.contains("boot") || name.contains("recovery") || name.contains("vendor_boot"))) {
                    result.add(f.getAbsolutePath());
                }
            } else if (pattern.equals("boot_unpacked")) {
                if (f.isDirectory() && (name.contains("boot") && name.contains("unpack"))) {
                    result.add(f.getAbsolutePath());
                }
            }
        }
        return result;
    }

    // --- Boot Info card ---
    private void addBootInfoCard() {
        MaterialCardView card = createCard();
        LinearLayout inner = createInnerLayout();
        addTitle(inner, getString(R.string.boot_info));
        addDesc(inner, "Show boot image header info (cmdline, offsets, version)");

        MaterialButton btn = createRunButton();
        btn.setOnClickListener(v -> {
            String projectPath = requireProjectPath();
            if (projectPath == null) return;
            List<String> found = findBootFiles(projectPath, "boot.img");
            if (found.isEmpty()) {
                showInputDialog(getString(R.string.boot_info), "Path to boot.img:", "boot.img", input -> {
                    String path = projectPath + "/" + input;
                    executeScript("boot_unpack.sh", path, projectPath + "/boot_info");
                });
            } else {
                String[] names = new String[found.size()];
                for (int i = 0; i < found.size(); i++)
                    names[i] = found.get(i).replace(projectPath + "/", "");
                new AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.select_file))
                    .setItems(names, (d, w) -> executeScript("boot_unpack.sh", found.get(w), projectPath + "/boot_info"))
                    .show();
            }
        });
        inner.addView(btn);
        card.addView(inner);
        container.addView(card);
    }

    // --- Extract Kernel card ---
    private void addExtractKernelCard() {
        MaterialCardView card = createCard();
        LinearLayout inner = createInnerLayout();
        addTitle(inner, getString(R.string.boot_extract_kernel));
        addDesc(inner, "Extract kernel from device boot partition");

        CheckBox cbDelete = new CheckBox(requireContext());
        cbDelete.setText(R.string.delete_after);
        cbDelete.setTextColor(0xFFB0B0B0);
        inner.addView(cbDelete);

        MaterialButton btn = createRunButton();
        btn.setOnClickListener(v -> {
            String projectPath = requireProjectPath();
            if (projectPath == null) return;
            String cmd = "su -c 'dd if=$(readlink -f /dev/block/by-name/boot) of=\"" +
                projectPath + "/boot.img\" bs=4096' && echo 'Boot extracted to boot.img'";
            executeRawCommand(cmd);
        });
        inner.addView(btn);
        card.addView(inner);
        container.addView(card);
    }

    // --- Flash Boot card ---
    private void addFlashBootCard() {
        MaterialCardView card = createCard();
        LinearLayout inner = createInnerLayout();
        addTitle(inner, getString(R.string.flash_partition));
        addDesc(inner, "Flash patched boot image to device");

        MaterialButton btn = createRunButton();
        btn.setOnClickListener(v -> {
            String projectPath = requireProjectPath();
            if (projectPath == null) return;
            // Find patched boot images
            List<String> candidates = new ArrayList<>();
            File dir = new File(projectPath);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    String n = f.getName().toLowerCase();
                    if (n.endsWith(".img") && (n.contains("patched") || n.contains("magisk") || n.contains("new_boot"))) {
                        candidates.add(f.getAbsolutePath());
                    }
                }
            }
            // Also check output dir
            File outDir = new File(projectPath + "/output");
            if (outDir.exists()) {
                File[] outFiles = outDir.listFiles();
                if (outFiles != null) {
                    for (File f : outFiles) {
                        if (f.getName().toLowerCase().contains("boot") && f.getName().endsWith(".img")) {
                            candidates.add(f.getAbsolutePath());
                        }
                    }
                }
            }

            if (candidates.isEmpty()) {
                showInputDialog(getString(R.string.flash_partition), "Path to boot image:", "boot-patched.img", input -> {
                    String path = input.startsWith("/") ? input : projectPath + "/" + input;
                    confirmAndFlashBoot(path);
                });
            } else {
                String[] names = new String[candidates.size()];
                for (int i = 0; i < candidates.size(); i++)
                    names[i] = new File(candidates.get(i)).getName();
                final List<String> cands = candidates;
                new AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.select_file))
                    .setItems(names, (d, w) -> confirmAndFlashBoot(cands.get(w)))
                    .show();
            }
        });
        inner.addView(btn);
        card.addView(inner);
        container.addView(card);
    }

    private void confirmAndFlashBoot(String imgPath) {
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.warning)
            .setMessage("Flash " + new File(imgPath).getName() + " to boot partition?\nThis can brick your device!")
            .setPositiveButton(R.string.ok, (d, w) -> {
                String cmd = "su -c 'dd if=\"" + imgPath + "\" of=$(readlink -f /dev/block/by-name/boot) bs=4096 && echo \"Boot flashed successfully!\"'";
                executeRawCommand(cmd);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    // --- Extract Partition (Tools tab) ---
    private void addExtractPartitionCard() {
        MaterialCardView card = createCard();
        LinearLayout inner = createInnerLayout();
        addTitle(inner, getString(R.string.extract_partition));
        addDesc(inner, "Extract partition image from device");

        addLabel(inner, getString(R.string.select_partition));
        Spinner spinPart = createSpinner(PARTITION_NAMES);
        inner.addView(spinPart);

        MaterialButton btn = createRunButton();
        btn.setOnClickListener(v -> {
            String projectPath = requireProjectPath();
            if (projectPath == null) return;
            String part = PARTITION_NAMES[spinPart.getSelectedItemPosition()];
            executeScript("extract_partition.sh", part, projectPath);
        });
        inner.addView(btn);
        card.addView(inner);
        container.addView(card);
    }

    // --- Flash Partition (Tools tab) ---
    private void addFlashPartitionCard() {
        MaterialCardView card = createCard();
        LinearLayout inner = createInnerLayout();
        addTitle(inner, getString(R.string.flash_partition));
        addDesc(inner, "Flash image to device partition");

        addLabel(inner, getString(R.string.select_partition));
        Spinner spinPart = createSpinner(PARTITION_NAMES);
        inner.addView(spinPart);

        MaterialButton btn = createRunButton();
        btn.setOnClickListener(v -> {
            String projectPath = requireProjectPath();
            if (projectPath == null) return;
            String part = PARTITION_NAMES[spinPart.getSelectedItemPosition()];
            String imgPath = projectPath + "/output/" + part + ".img";
            File imgFile = new File(imgPath);
            if (!imgFile.exists()) {
                imgPath = projectPath + "/" + part + ".img";
                imgFile = new File(imgPath);
            }
            if (!imgFile.exists()) {
                showInputDialog(getString(R.string.flash_partition),
                    "Path to " + part + ".img:", part + ".img", input -> {
                        String path = input.startsWith("/") ? input : projectPath + "/" + input;
                        executeScript("flash_partition.sh", PARTITION_NAMES[spinPart.getSelectedItemPosition()], path);
                    });
            } else {
                final String finalPath = imgPath;
                new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.warning)
                    .setMessage("Flash " + imgFile.getName() + " to " + part + "?\nThis can brick your device!")
                    .setPositiveButton(R.string.ok, (d, w) ->
                        executeScript("flash_partition.sh", part, finalPath))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            }
        });
        inner.addView(btn);
        card.addView(inner);
        container.addView(card);
    }

    // ==================== Jar Patch Card ====================
    private void addJarPatchCard(String title, String description, String patchType) {
        MaterialCardView card = createCard();
        LinearLayout inner = createInnerLayout();
        addTitle(inner, title);
        addDesc(inner, description);

        addLabel(inner, getString(R.string.select_rom_type));
        Spinner spinnerRom = createSpinner(ROM_TYPES);
        inner.addView(spinnerRom);

        addLabel(inner, getString(R.string.select_android_version));
        Spinner spinnerAndroid = createSpinner(ANDROID_VERSIONS);
        spinnerAndroid.setSelection(5); // Default Android 14
        inner.addView(spinnerAndroid);

        MaterialButton btn = createRunButton();
        btn.setOnClickListener(v -> {
            String projectPath = requireProjectPath();
            if (projectPath == null) return;
            int apiLevel = ANDROID_APIS[spinnerAndroid.getSelectedItemPosition()];
            String romType = ROM_TYPES[spinnerRom.getSelectedItemPosition()].toLowerCase();
            String scriptName;
            switch (patchType) {
                case "core_dsv": scriptName = "jar_patch_dsv.sh"; break;
                case "remove_apk_prot": scriptName = "jar_patch_apk_prot.sh"; break;
                case "disable_secure_ss": scriptName = "jar_patch_secure_ss.sh"; break;
                default: return;
            }
            executeScript(scriptName, projectPath, String.valueOf(apiLevel), romType);
        });
        inner.addView(btn);
        card.addView(inner);
        container.addView(card);
    }

    // ==================== Script-based Action Card ====================
    private void addScriptCard(String title, String description, String scriptName, String[] args) {
        MaterialCardView card = createCard();
        LinearLayout inner = createInnerLayout();
        addTitle(inner, title);
        addDesc(inner, description);

        MaterialButton btn = createRunButton();
        btn.setOnClickListener(v -> {
            String projectPath = requireProjectPath();
            if (projectPath == null) return;
            String[] finalArgs = new String[args.length];
            for (int i = 0; i < args.length; i++) {
                finalArgs[i] = args[i].replace("%PROJECT%", projectPath);
            }
            executeScript(scriptName, finalArgs);
        });
        inner.addView(btn);
        card.addView(inner);
        container.addView(card);
    }

    // ==================== Standard Action Card ====================
    private void addActionCard(String title, String description, String command) {
        MaterialCardView card = createCard();
        LinearLayout inner = createInnerLayout();
        addTitle(inner, title);
        addDesc(inner, description);

        if (command == null) {
            // Script executor — custom command input
            EditText etCmd = new EditText(requireContext());
            etCmd.setHint(getString(R.string.custom_command));
            etCmd.setTextColor(0xFFFFFFFF);
            etCmd.setHintTextColor(0xFF666666);
            etCmd.setBackgroundColor(0xFF2A2A2A);
            etCmd.setPadding(24, 16, 24, 16);
            etCmd.setTextSize(13);
            LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            etParams.topMargin = 12;
            etCmd.setLayoutParams(etParams);
            inner.addView(etCmd);

            MaterialButton btn = createRunButton();
            btn.setOnClickListener(v -> {
                String cmd = etCmd.getText().toString().trim();
                if (cmd.isEmpty()) {
                    Toast.makeText(requireContext(), "Enter a command", Toast.LENGTH_SHORT).show();
                    return;
                }
                executeRawCommand(cmd);
            });
            inner.addView(btn);
        } else {
            MaterialButton btn = createRunButton();
            btn.setOnClickListener(v -> {
                String projectPath = requireProjectPath();
                if (projectPath == null) return;
                String finalCmd = command.replace("%PROJECT%", projectPath);
                executeRawCommand(finalCmd);
            });
            inner.addView(btn);
        }

        card.addView(inner);
        container.addView(card);
    }

    // ========================================================================
    // EXECUTION
    // ========================================================================

    private void executeScript(String scriptName, String... args) {
        String scriptPath = binaryManager.getScriptPath(scriptName);

        // Verify script exists
        File scriptFile = new File(scriptPath);
        if (!scriptFile.exists()) {
            new AlertDialog.Builder(requireContext())
                .setTitle(R.string.error)
                .setMessage("Script not found: " + scriptName + "\n\nPath: " + scriptPath +
                    "\n\nTry restarting the app to re-extract tools.")
                .setPositiveButton(R.string.ok, null)
                .show();
            return;
        }

        // Build command with proper environment
        StringBuilder cmd = new StringBuilder();
        Map<String, String> env = binaryManager.setupEnvironment();
        String binDir = env.get("BIN_DIR");
        String scriptsDir = binaryManager.getScriptsDir();

        // Force chmod before execution (Android noexec workaround)
        cmd.append("chmod -R 755 \"").append(binDir).append("\" 2>/dev/null; ");
        cmd.append("chmod 755 \"").append(scriptPath).append("\" 2>/dev/null; ");

        // Export environment variables
        cmd.append("export TOOLS_DIR=\"").append(env.get("TOOLS_DIR")).append("\"; ");
        cmd.append("export BIN_DIR=\"").append(binDir).append("\"; ");
        cmd.append("export PATH=\"").append(env.get("PATH")).append("\"; ");
        cmd.append("export LD_LIBRARY_PATH=\"").append(env.get("LD_LIBRARY_PATH")).append("\"; ");
        cmd.append("export HOME=\"").append(env.get("HOME")).append("\"; ");

        // Run script
        cmd.append("sh \"").append(scriptPath).append("\"");
        for (String arg : args) {
            cmd.append(" \"").append(arg).append("\"");
        }

        executeWithProgress(cmd.toString());
    }

    private void executeRawCommand(String command) {
        executeWithProgress(command);
    }

    private void executeWithProgress(String command) {
        AlertDialog progress = new AlertDialog.Builder(requireContext())
            .setMessage(R.string.running)
            .setCancelable(false)
            .show();

        new Thread(() -> {
            StringBuilder output = new StringBuilder();
            try {
                // Try running via su first (rooted device — bypasses noexec)
                // Fall back to sh if su is not available
                String[] shellCmd;
                if (hasRoot()) {
                    shellCmd = new String[]{"su", "-c", command};
                } else {
                    shellCmd = new String[]{"sh", "-c", command};
                }
                Process process = Runtime.getRuntime().exec(shellCmd);
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                while ((line = errReader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                process.waitFor();
                reader.close();
                errReader.close();
            } catch (Exception e) {
                output.append("Error: ").append(e.getMessage()).append("\n");
            }

            final String result = output.toString();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    progress.dismiss();
                    new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.done)
                        .setMessage(result.length() > 2000 ? result.substring(0, 2000) + "..." : result)
                        .setPositiveButton(R.string.ok, null)
                        .show();
                });
            }
        }).start();
    }

    private boolean hasRoot() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            r.close();
            p.waitFor();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    // ========================================================================
    // UI HELPERS
    // ========================================================================

    private String requireProjectPath() {
        String path = projectManager.getCurrentProjectPath();
        if (path == null) {
            Toast.makeText(requireContext(), R.string.no_project, Toast.LENGTH_SHORT).show();
        }
        return path;
    }

    private MaterialCardView createCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(0xFF1E1E1E);
        card.setRadius(32);
        card.setCardElevation(8);
        card.setContentPadding(40, 32, 40, 32);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = 24;
        card.setLayoutParams(p);
        return card;
    }

    private LinearLayout createInnerLayout() {
        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        return inner;
    }

    private void addTitle(LinearLayout parent, String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(0, 0, 0, 8);
        parent.addView(tv);
    }

    private void addDesc(LinearLayout parent, String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(0xFFB0B0B0);
        tv.setPadding(0, 0, 0, 16);
        parent.addView(tv);
    }

    private void addLabel(LinearLayout parent, String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(0xFF909090);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = 12;
        p.bottomMargin = 4;
        tv.setLayoutParams(p);
        parent.addView(tv);
    }

    private Spinner createSpinner(String[] items) {
        Spinner spinner = new Spinner(requireContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, items);
        spinner.setAdapter(adapter);
        spinner.setBackgroundColor(0xFF2A2A2A);
        return spinner;
    }

    private MaterialButton createRunButton() {
        MaterialButton btn = new MaterialButton(requireContext());
        btn.setText(R.string.run);
        btn.setBackgroundColor(0xFFD50000);
        btn.setCornerRadius(16);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 120);
        p.topMargin = 24;
        btn.setLayoutParams(p);
        return btn;
    }

    private interface InputCallback {
        void onInput(String value);
    }

    private void showInputDialog(String title, String hint, String defaultValue, InputCallback callback) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setText(defaultValue);
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF666666);
        input.setPadding(40, 24, 40, 24);

        new AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setPositiveButton(R.string.ok, (d, w) -> {
                String val = input.getText().toString().trim();
                if (!val.isEmpty()) callback.onInput(val);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }
}
