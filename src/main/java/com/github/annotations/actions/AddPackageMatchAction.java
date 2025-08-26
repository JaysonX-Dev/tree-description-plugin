package com.github.annotations.actions;

import com.github.annotations.ui.AddPackageMatchDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;


public class AddPackageMatchAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        AddPackageMatchDialog dialog = new AddPackageMatchDialog(project);
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
            com.github.annotations.services.AnnotationService service = com.github.annotations.services.AnnotationService.getInstance(project);
            if (service != null) {
                String currentLanguage = service.getLanguage();
                if ("en".equals(currentLanguage)) {
                    e.getPresentation().setText("Add Package Match");
                    e.getPresentation().setDescription("Add package path matching pattern for automatic annotations");
                } else {
                    e.getPresentation().setText("添加路径匹配映射");
                    e.getPresentation().setDescription("添加路径匹配模式，为匹配的路径自动添加备注");
                }
            } else {
                e.getPresentation().setText("添加路径匹配映射");
                e.getPresentation().setDescription("添加路径匹配模式，为匹配的路径自动添加备注");
            }
        } catch (Exception ex) {
            e.getPresentation().setText("添加路径匹配映射");
            e.getPresentation().setDescription("添加路径匹配模式，为匹配的路径自动添加备注");
        }
    }
}
