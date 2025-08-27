package com.github.annotations.actions;

import com.github.annotations.ui.AddFileMatchDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class AddFileMatchAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        // 获取当前选中的文件名，并添加*.前缀用于界面显示
        String currentFileName = null;
        VirtualFile selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (selectedFile != null && !selectedFile.isDirectory()) {
            currentFileName = "*." + selectedFile.getName();
        }

        AddFileMatchDialog dialog = new AddFileMatchDialog(project, currentFileName);
        if (dialog.showAndGet()) {
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        e.getPresentation().setEnabledAndVisible(true);

        // 根据当前语言动态显示菜单文本
        try {
            com.github.annotations.services.AnnotationService service = com.github.annotations.services.AnnotationService
                    .getInstance(project);
            if (service != null) {
                String currentLanguage = service.getLanguage();
                if ("en".equals(currentLanguage)) {
                    e.getPresentation().setText("Add File Match");
                    e.getPresentation().setDescription("Add filename matching pattern for automatic annotations");
                } else {
                    e.getPresentation().setText("添加文件匹配映射");
                    e.getPresentation().setDescription("添加文件名匹配模式，为匹配的文件自动添加备注");
                }
            } else {
                e.getPresentation().setText("添加文件匹配映射");
                e.getPresentation().setDescription("添加文件名匹配模式，为匹配的文件自动添加备注");
            }
        } catch (Exception ex) {
            e.getPresentation().setText("添加文件匹配映射");
            e.getPresentation().setDescription("添加文件名匹配模式，为匹配的文件自动添加备注");
        }
    }
}
