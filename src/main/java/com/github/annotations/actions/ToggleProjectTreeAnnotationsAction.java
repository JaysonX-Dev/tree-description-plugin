package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;


public class ToggleProjectTreeAnnotationsAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        boolean isEnabled = annotationService.isProjectTreeAnnotationsEnabled();
        
        if (isEnabled) {
            int result = Messages.showYesNoDialog(
                project,
                "确定要禁用项目树备注显示吗？\n\n禁用后，项目树中将不再显示备注信息。",
                "禁用项目树备注",
                "禁用",
                "取消",
                Messages.getWarningIcon()
            );
            
            if (result == Messages.YES) {
                annotationService.setProjectTreeAnnotationsEnabled(false);
            }
        } else {
            int result = Messages.showYesNoDialog(
                project,
                "确定要启用项目树备注显示吗？\n\n启用后，项目树中将显示备注信息。",
                "启用项目树备注",
                "启用",
                "取消",
                Messages.getInformationIcon()
            );
            
            if (result == Messages.YES) {
                annotationService.setProjectTreeAnnotationsEnabled(true);
            }
        }
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        boolean isEnabled = annotationService.isProjectTreeAnnotationsEnabled();
        
        // 根据当前语言动态显示菜单文本
        String currentLanguage = annotationService.getLanguage();
        if ("en".equals(currentLanguage)) {
            if (isEnabled) {
                e.getPresentation().setText("Disable Project Tree Annotations");
                e.getPresentation().setDescription("Disable annotation display in project tree");
            } else {
                e.getPresentation().setText("Enable Project Tree Annotations");
                e.getPresentation().setDescription("Enable annotation display in project tree");
            }
        } else {
            if (isEnabled) {
                e.getPresentation().setText("禁用项目树备注");
                e.getPresentation().setDescription("禁用项目树中的备注显示");
            } else {
                e.getPresentation().setText("启用项目树备注");
                e.getPresentation().setDescription("启用项目树中的备注显示");
            }
        }
        
        e.getPresentation().setVisible(true);
        e.getPresentation().setEnabled(true);
    }
}
