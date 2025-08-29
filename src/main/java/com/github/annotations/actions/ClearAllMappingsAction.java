package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.github.annotations.utils.I18nUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ClearAllMappingsAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        
        Map<String, String> fileMatchAnnotations = annotationService.getAllFileMatchAnnotations();
        Map<String, String> packageMatchAnnotations = annotationService.getAllPackageMatchAnnotations();
        
        int totalCount = fileMatchAnnotations.size() + packageMatchAnnotations.size();
        
        if (totalCount == 0) {
            Messages.showInfoMessage(
                project,
                I18nUtils.getText(project, "当前项目没有任何路径匹配映射。", "Current project has no path matching mappings."),
                I18nUtils.getText(project, "清空所有映射", "Clear All Mappings")
            );
            return;
        }
        
        StringBuilder details = new StringBuilder();
        details.append(I18nUtils.getText(project, "当前共有 ", "Current total: ")).append(totalCount).append(I18nUtils.getText(project, " 个路径匹配映射：\n", " path matching mappings:\n"));
        if (!fileMatchAnnotations.isEmpty()) {
            details.append("• ").append(I18nUtils.getText(project, "文件匹配模式：", "File match patterns: ")).append(fileMatchAnnotations.size()).append(I18nUtils.getText(project, " 个\n", " items\n"));
        }
        if (!packageMatchAnnotations.isEmpty()) {
            details.append("• ").append(I18nUtils.getText(project, "包匹配模式：", "Package match patterns: ")).append(packageMatchAnnotations.size()).append(I18nUtils.getText(project, " 个\n", " items\n"));
        }
        details.append("\n").append(I18nUtils.getText(project, "此操作不可撤销！", "This operation cannot be undone!"));
        
        int result = Messages.showYesNoDialog(
            project,
            I18nUtils.getText(project, 
                "确定要清空当前项目的所有路径匹配映射吗？\n\n" + details.toString(),
                "Are you sure you want to clear all path matching mappings in the current project?\n\n" + details.toString()),
            I18nUtils.getText(project, "确认清空所有映射", "Confirm Clear All Mappings"),
            I18nUtils.getText(project, "清空", "Clear"),
            I18nUtils.getText(project, "取消", "Cancel"),
            Messages.getWarningIcon()
        );
        
        if (result == Messages.YES) {
            // 清空文件匹配映射
            for (String pattern : fileMatchAnnotations.keySet()) {
                annotationService.setFileMatchAnnotation(pattern, "");
            }
            
            // 清空包匹配映射
            for (String pattern : packageMatchAnnotations.keySet()) {
                annotationService.setPackageMatchAnnotation(pattern, "");
            }
            
            // 调用刷新机制，确保JSON文件和UI同步更新
            try {
                java.lang.reflect.Method refreshMethod = annotationService.getClass().getDeclaredMethod("refreshAfterSave");
                refreshMethod.setAccessible(true);
                refreshMethod.invoke(annotationService);
            } catch (Exception ex) {
                // 如果反射调用失败，回退到基本的项目视图刷新
                com.intellij.ide.projectView.ProjectView.getInstance(project).refresh();
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
        
        try {
            AnnotationService annotationService = AnnotationService.getInstance(project);
            if (annotationService != null) {
                Map<String, String> fileMatchAnnotations = annotationService.getAllFileMatchAnnotations();
                Map<String, String> packageMatchAnnotations = annotationService.getAllPackageMatchAnnotations();
                boolean hasMappings = !fileMatchAnnotations.isEmpty() || !packageMatchAnnotations.isEmpty();
                
                e.getPresentation().setEnabledAndVisible(true);
                
                // 根据当前语言动态显示菜单文本
                String currentLanguage = annotationService.getLanguage();
                if ("en".equals(currentLanguage)) {
                    e.getPresentation().setText("Clear All Mappings");
                    if (!hasMappings) {
                        e.getPresentation().setDescription("No path matching mappings to clear in current project");
                    } else {
                        e.getPresentation().setDescription("Clear all path matching mappings in current project");
                    }
                } else {
                    e.getPresentation().setText("清空所有映射");
                    if (!hasMappings) {
                        e.getPresentation().setDescription("当前项目没有任何路径匹配映射可清空");
                    } else {
                        e.getPresentation().setDescription("清空当前项目的所有路径匹配映射");
                    }
                }
            } else {
                e.getPresentation().setEnabledAndVisible(false);
            }
        } catch (Exception ex) {
            e.getPresentation().setEnabledAndVisible(false);
        }
    }
}