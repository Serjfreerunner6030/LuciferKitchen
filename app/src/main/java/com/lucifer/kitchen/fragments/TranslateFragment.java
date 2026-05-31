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
import java.io.File;
import java.util.List;

public class TranslateFragment extends Fragment {

    private ProjectManager projectManager;
    private TextView tvFilesFound, tvProgress, tvLog;
    private ProgressBar progressBar;
    private MaterialCardView cardProgress, cardLog;
    private MaterialButton btnScan, btnStart;
    private CheckBox cbStrings, cbArrays;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<File> foundFiles;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_translate, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        projectManager = new ProjectManager(requireContext());

        tvFilesFound = view.findViewById(R.id.tvFilesFound);
        tvProgress = view.findViewById(R.id.tvProgress);
        tvLog = view.findViewById(R.id.tvLog);
        progressBar = view.findViewById(R.id.progressBar);
        cardProgress = view.findViewById(R.id.cardProgress);
        cardLog = view.findViewById(R.id.cardLog);
        btnScan = view.findViewById(R.id.btnScanFiles);
        btnStart = view.findViewById(R.id.btnStartTranslate);
        cbStrings = view.findViewById(R.id.cbStrings);
        cbArrays = view.findViewById(R.id.cbArrays);

        btnScan.setOnClickListener(v -> scanFiles());
        btnStart.setOnClickListener(v -> startTranslation());
    }

    private void scanFiles() {
        String path = projectManager.getCurrentProjectPath();
        if (path == null) {
            Toast.makeText(requireContext(), R.string.no_project, Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            RomTranslator translator = new RomTranslator(path);
            foundFiles = translator.scanTranslatableFiles();
            handler.post(() -> {
                tvFilesFound.setVisibility(View.VISIBLE);
                tvFilesFound.setText(String.format(getString(R.string.translate_files_found), foundFiles.size()));
            });
        }).start();
    }

    private void startTranslation() {
        String path = projectManager.getCurrentProjectPath();
        if (path == null) {
            Toast.makeText(requireContext(), R.string.no_project, Toast.LENGTH_SHORT).show();
            return;
        }

        if (foundFiles == null || foundFiles.isEmpty()) {
            scanFiles();
            Toast.makeText(requireContext(), R.string.translate_scan, Toast.LENGTH_SHORT).show();
            return;
        }

        cardProgress.setVisibility(View.VISIBLE);
        cardLog.setVisibility(View.VISIBLE);
        btnStart.setEnabled(false);
        tvLog.setText("");
        final StringBuilder logBuffer = new StringBuilder();

        new Thread(() -> {
            RomTranslator translator = new RomTranslator(path);
            translator.translateAll(new RomTranslator.TranslationCallback() {
                @Override
                public void onProgress(int current, int total, String fileName) {
                    handler.post(() -> {
                        progressBar.setMax(total);
                        progressBar.setProgress(current);
                        tvProgress.setText(current + " / " + total + " - " + fileName);
                    });
                }

                @Override
                public void onLog(String message) {
                    logBuffer.append(message).append("\n");
                    handler.post(() -> tvLog.setText(logBuffer.toString()));
                }

                @Override
                public void onComplete(int translated) {
                    handler.post(() -> {
                        btnStart.setEnabled(true);
                        tvProgress.setText(getString(R.string.translate_complete) + " (" + translated + " strings)");
                        Toast.makeText(requireContext(), R.string.translate_complete, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }).start();
    }
}
