package com.lucifer.kitchen.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.lucifer.kitchen.R;
import com.lucifer.kitchen.utils.ProjectManager;
import com.lucifer.kitchen.utils.ShellExecutor;

public class HomeFragment extends Fragment {

    private ProjectManager projectManager;
    private TextView tvCurrentProject;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        projectManager = new ProjectManager(requireContext());
        tvCurrentProject = view.findViewById(R.id.tvCurrentProject);

        MaterialButton btnSelect = view.findViewById(R.id.btnSelectProject);
        MaterialButton btnNew = view.findViewById(R.id.btnNewProject);
        MaterialButton btnDelete = view.findViewById(R.id.btnDeleteProject);
        CheckBox cbDelete = view.findViewById(R.id.cbDeleteSource);
        MaterialButton btnUnzip = view.findViewById(R.id.btnUnzipRom);

        updateProjectDisplay();

        btnSelect.setOnClickListener(v -> showSelectProjectDialog());
        btnNew.setOnClickListener(v -> showNewProjectDialog());
        btnDelete.setOnClickListener(v -> showDeleteProjectDialog());
        btnUnzip.setOnClickListener(v -> {
            String path = projectManager.getCurrentProjectPath();
            if (path == null) {
                Toast.makeText(requireContext(), R.string.no_project, Toast.LENGTH_SHORT).show();
                return;
            }
            String[] zips = projectManager.listFiles("zip");
            if (zips.length == 0) {
                Toast.makeText(requireContext(), getString(R.string.select_files_hint), Toast.LENGTH_LONG).show();
                return;
            }
            boolean deleteSource = cbDelete.isChecked();
            showFileSelectDialog(zips, path, deleteSource);
        });
    }

    private void updateProjectDisplay() {
        String current = projectManager.getCurrentProject();
        if (current != null) {
            tvCurrentProject.setText(current);
        } else {
            tvCurrentProject.setText(R.string.project_none);
        }
    }

    private void showSelectProjectDialog() {
        String[] projects = projectManager.listProjects();
        if (projects.length == 0) {
            Toast.makeText(requireContext(), R.string.no_project, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.project_select)
            .setItems(projects, (dialog, which) -> {
                projectManager.setCurrentProject(projects[which]);
                updateProjectDisplay();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void showNewProjectDialog() {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.project_name_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setPadding(48, 24, 48, 24);

        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.project_new)
            .setView(input)
            .setPositiveButton(R.string.ok, (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) {
                    if (projectManager.createProject(name)) {
                        projectManager.setCurrentProject(name);
                        updateProjectDisplay();
                        Toast.makeText(requireContext(), R.string.success, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), R.string.error, Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void showDeleteProjectDialog() {
        String[] projects = projectManager.listProjects();
        if (projects.length == 0) {
            Toast.makeText(requireContext(), R.string.no_project, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.project_delete)
            .setItems(projects, (dialog, which) -> {
                new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.warning)
                    .setMessage(R.string.confirm_delete)
                    .setPositiveButton(R.string.ok, (d, w) -> {
                        projectManager.deleteProject(projects[which]);
                        updateProjectDisplay();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void showFileSelectDialog(String[] files, String path, boolean deleteSource) {
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_zip)
            .setItems(files, (dialog, which) -> {
                String zipPath = path + "/" + files[which];
                String cmd = "cd " + path + " && unzip -o '" + zipPath + "'";
                if (deleteSource) {
                    cmd += " && rm -f '" + zipPath + "'";
                }
                executeWithProgress(cmd);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void executeWithProgress(String command) {
        AlertDialog progress = new AlertDialog.Builder(requireContext())
            .setMessage(R.string.running)
            .setCancelable(false)
            .show();

        new Thread(() -> {
            String result = ShellExecutor.execute(command);
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
}
