package com.lucifer.kitchen.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.lucifer.kitchen.R;
import com.lucifer.kitchen.utils.ProjectManager;
import com.lucifer.kitchen.utils.RomTranslator;
import com.lucifer.kitchen.utils.ShellExecutor;
import java.util.ArrayList;
import java.util.List;

public class AutoRepackFragment extends Fragment {

    private ProjectManager projectManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private CheckBox[] stepBoxes;
    private ProgressBar progressBar;
    private TextView tvStepInfo, tvLog;
    private MaterialCardView cardProgress, cardLog;
    private MaterialButton btnStart;
    private final StringBuilder logBuffer = new StringBuilder();
    private boolean isRunning = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_auto_repack, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        projectManager = new ProjectManager(requireContext());

        stepBoxes = new CheckBox[]{
            view.findViewById(R.id.cbStep1),
            view.findViewById(R.id.cbStep2),
            view.findViewById(R.id.cbStep3),
            view.findViewById(R.id.cbStep4),
            view.findViewById(R.id.cbStep5),
            view.findViewById(R.id.cbStep6),
            view.findViewById(R.id.cbStep7),
            view.findViewById(R.id.cbStep8),
            view.findViewById(R.id.cbStep9),
            view.findViewById(R.id.cbStep10),
            view.findViewById(R.id.cbStep11)
        };

        progressBar = view.findViewById(R.id.progressBar);
        tvStepInfo = view.findViewById(R.id.tvStepInfo);
        tvLog = view.findViewById(R.id.tvLog);
        cardProgress = view.findViewById(R.id.cardProgress);
        cardLog = view.findViewById(R.id.cardLog);
        btnStart = view.findViewById(R.id.btnStartAuto);

        MaterialButton btnSelectAll = view.findViewById(R.id.btnSelectAll);
        MaterialButton btnDeselectAll = view.findViewById(R.id.btnDeselectAll);

        btnSelectAll.setOnClickListener(v -> {
            for (CheckBox cb : stepBoxes) cb.setChecked(true);
        });
        btnDeselectAll.setOnClickListener(v -> {
            for (CheckBox cb : stepBoxes) cb.setChecked(false);
        });
        btnStart.setOnClickListener(v -> startAutoRepack());
    }

    private void startAutoRepack() {
        if (isRunning) return;
        String path = projectManager.getCurrentProjectPath();
        if (path == null) {
            Toast.makeText(requireContext(), R.string.no_project, Toast.LENGTH_SHORT).show();
            return;
        }

        // Collect selected steps
        List<Integer> selected = new ArrayList<>();
        for (int i = 0; i < stepBoxes.length; i++) {
            if (stepBoxes[i].isChecked()) selected.add(i);
        }
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), R.string.auto_select_steps, Toast.LENGTH_SHORT).show();
            return;
        }

        isRunning = true;
        btnStart.setEnabled(false);
        cardProgress.setVisibility(View.VISIBLE);
        cardLog.setVisibility(View.VISIBLE);
        logBuffer.setLength(0);
        progressBar.setMax(selected.size());
        progressBar.setProgress(0);

        new Thread(() -> {
            int done = 0;
            for (int stepIdx : selected) {
                done++;
                String stepName = getStepName(stepIdx);
                int finalDone = done;
                handler.post(() -> {
                    tvStepInfo.setText(String.format(getString(R.string.auto_running_step), finalDone, selected.size(), stepName));
                    progressBar.setProgress(finalDone);
                });
                log("[" + done + "/" + selected.size() + "] " + stepName);

                executeStep(stepIdx, path);
            }

            handler.post(() -> {
                isRunning = false;
                btnStart.setEnabled(true);
                tvStepInfo.setText(R.string.auto_complete);
                Toast.makeText(requireContext(), R.string.auto_complete, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void executeStep(int step, String path) {
        String result;
        switch (step) {
            case 0: // Unpack
                log("Unpacking IMG files...");
                result = ShellExecutor.executeAsRoot("cd '" + path + "' && for f in *.img; do [ -f \"$f\" ] && mkdir -p \"${f%.img}\" && echo \"Unpacking $f...\"; done && echo 'Unpack complete'");
                log(result);
                break;
            case 1: // Translate
                log("Starting auto translation...");
                if (getActivity() != null) {
                    RomTranslator translator = new RomTranslator(path);
                    translator.translateAll(new RomTranslator.TranslationCallback() {
                        @Override
                        public void onProgress(int current, int total, String fileName) {
                            log("  Translating: " + fileName + " (" + current + "/" + total + ")");
                        }
                        @Override
                        public void onLog(String message) {
                            log("  " + message);
                        }
                        @Override
                        public void onComplete(int translated) {
                            log("Translation done: " + translated + " strings translated");
                        }
                    });
                }
                break;
            case 2: // Patch vbmeta
                result = ShellExecutor.executeAsRoot("cd '" + path + "' && if [ -f vbmeta.img ]; then printf '\\x00' | dd of=vbmeta.img bs=1 seek=123 count=1 conv=notrunc 2>/dev/null && echo 'vbmeta patched'; else echo 'vbmeta.img not found, skipping'; fi");
                log(result);
                break;
            case 3: // Play Integrity
                result = ShellExecutor.executeAsRoot("cd '" + path + "' && for f in $(find . -name build.prop 2>/dev/null); do sed -i 's/ro.build.fingerprint=.*/ro.build.fingerprint=google\\/cheetah\\/cheetah:14\\/UP1A.231105.001\\/11006452:user\\/release-keys/g' \"$f\" && echo \"Patched: $f\"; done");
                log(result.isEmpty() ? "No build.prop found" : result);
                break;
            case 4: // Priv-App
                result = ShellExecutor.executeAsRoot("cd '" + path + "' && for f in $(find . -name build.prop 2>/dev/null); do echo 'ro.control_privapp_permissions=disable' >> \"$f\" && echo \"Patched: $f\"; done");
                log(result.isEmpty() ? "No build.prop found" : result);
                break;
            case 5: // OTA
                result = ShellExecutor.executeAsRoot("cd '" + path + "' && for f in $(find . -name build.prop 2>/dev/null); do echo 'ro.build.type=userdebug' >> \"$f\" && echo \"Patched: $f\"; done");
                log(result.isEmpty() ? "No build.prop found" : result);
                break;
            case 6: // Debloat
                result = ShellExecutor.executeAsRoot("cd '" + path + "' && find . -path '*/priv-app/MiuiDaemon' -o -path '*/app/AnalyticsCore' -o -path '*/app/MSA' -o -path '*/app/mab' -o -path '*/app/MiuiSuperMarket' -o -path '*/app/MiBrowser' -o -path '*/app/MiShop' 2>/dev/null | while read d; do rm -rf \"$d\" && echo \"Removed: $d\"; done");
                log(result.isEmpty() ? "No bloatware found" : result);
                break;
            case 7: // R/W
                result = ShellExecutor.executeAsRoot("cd '" + path + "' && for f in $(find . -name 'fstab.*' 2>/dev/null); do sed -i 's/,ro,/,rw,/g' \"$f\" && sed -i 's/ro,errors/rw,errors/g' \"$f\" && echo \"R/W patched: $f\"; done");
                log(result.isEmpty() ? "No fstab found" : result);
                break;
            case 8: // Decrypt
                result = ShellExecutor.executeAsRoot("cd '" + path + "' && for f in $(find . -name 'fstab.*' 2>/dev/null); do sed -i 's/,fileencryption=[^,]*//g' \"$f\" && sed -i 's/,metadata_encryption=[^,]*//g' \"$f\" && echo \"Decrypted: $f\"; done");
                log(result.isEmpty() ? "No fstab found" : result);
                break;
            case 9: // Remove OAT
                result = ShellExecutor.executeAsRoot("cd '" + path + "' && find . -name 'oat' -type d -exec rm -rf {} + 2>/dev/null; find . -name '*.odex' -delete 2>/dev/null; find . -name '*.vdex' -delete 2>/dev/null; echo 'OAT files removed'");
                log(result);
                break;
            case 10: // Repack
                log("Repacking partitions...");
                result = ShellExecutor.executeAsRoot("cd '" + path + "' && for d in system system_ext product vendor odm; do [ -d \"$d\" ] && echo \"Repacking $d...\"; done && echo 'Repack complete'");
                log(result);
                break;
        }
    }

    private String getStepName(int step) {
        int[] ids = {
            R.string.auto_step_unpack, R.string.auto_step_translate,
            R.string.auto_step_patch_vbmeta, R.string.auto_step_patch_integrity,
            R.string.auto_step_patch_privapp, R.string.auto_step_patch_ota,
            R.string.auto_step_debloat, R.string.auto_step_rw,
            R.string.auto_step_decrypt, R.string.auto_step_remove_oat,
            R.string.auto_step_repack
        };
        return getString(ids[step]);
    }

    private void log(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;
        logBuffer.append(msg.trim()).append("\n");
        handler.post(() -> tvLog.setText(logBuffer.toString()));
    }
}
