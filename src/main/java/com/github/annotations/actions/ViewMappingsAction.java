package com.github.annotations.actions;

import com.github.annotations.ui.MappingLibraryDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;


public class ViewMappingsAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        MappingLibraryDialog dialog = new MappingLibraryDialog(project);
        dialog.show();
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
                    e.getPresentation().setText("View Open Source Mapping Library");
                    e.getPresentation().setDescription("View currently loaded mapping library content");
                } else {
                    e.getPresentation().setText("查看开源映射库");
                    e.getPresentation().setDescription("查看当前加载的映射库内容");
                }
            } else {
                e.getPresentation().setText("查看开源映射库");
                e.getPresentation().setDescription("查看当前加载的映射库内容");
            }
        } catch (Exception ex) {
            e.getPresentation().setText("查看开源映射库");
            e.getPresentation().setDescription("查看当前加载的映射库内容");
        }
    }
}
