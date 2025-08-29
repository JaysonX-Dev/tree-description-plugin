package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.github.annotations.utils.I18nUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.util.Map;


public class ClearAllAnnotationsAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        
        Map<String, String> fileAnnotations = annotationService.getAllAnnotations();
        Map<String, String> packageAnnotations = annotationService.getAllPackageAnnotations();
        Map<String, String> fileMatchAnnotations = annotationService.getAllFileMatchAnnotations();
        Map<String, String> packageMatchAnnotations = annotationService.getAllPackageMatchAnnotations();
        
        int totalCount = fileAnnotations.size() + packageAnnotations.size() + fileMatchAnnotations.size() + packageMatchAnnotations.size();
        
        if (totalCount == 0) {
            Messages.showInfoMessage(
                project,
                I18nUtils.getText(project, "当前项目没有任何用户备注。", "Current project has no user annotations."),
                I18nUtils.getText(project, "清空用户备注", "Clear User Annotations")
            );
            return;
        }
        
        StringBuilder details = new StringBuilder();
        details.append(I18nUtils.getText(project, "当前共有 ", "Current total: ")).append(totalCount).append(I18nUtils.getText(project, " 个用户备注：\n", " user annotations:\n"));
        if (!fileAnnotations.isEmpty()) {
            details.append("• ").append(I18nUtils.getText(project, "文件备注：", "File annotations: ")).append(fileAnnotations.size()).append(I18nUtils.getText(project, " 个\n", " items\n"));
        }
        if (!packageAnnotations.isEmpty()) {
            details.append("• ").append(I18nUtils.getText(project, "包备注：", "Package annotations: ")).append(packageAnnotations.size()).append(I18nUtils.getText(project, " 个\n", " items\n"));
        }
        if (!fileMatchAnnotations.isEmpty()) {
            details.append("• ").append(I18nUtils.getText(project, "文件匹配模式备注：", "File match pattern annotations: ")).append(fileMatchAnnotations.size()).append(I18nUtils.getText(project, " 个\n", " items\n"));
        }
        if (!packageMatchAnnotations.isEmpty()) {
            details.append("• ").append(I18nUtils.getText(project, "包匹配模式备注：", "Package match pattern annotations: ")).append(packageMatchAnnotations.size()).append(I18nUtils.getText(project, " 个\n", " items\n"));
        }
        details.append("\n").append(I18nUtils.getText(project, "此操作不可撤销！", "This operation cannot be undone!"));
        
        int result = Messages.showYesNoDialog(
            project,
            I18nUtils.getText(project, 
                "确定要清空当前项目的所有备注吗？\n\n" + details.toString(),
                "Are you sure you want to clear all annotations in the current project?\n\n" + details.toString()),
            I18nUtils.getText(project, "确认清空所有备注", "Confirm Clear All Annotations"),
            I18nUtils.getText(project, "清空", "Clear"),
            I18nUtils.getText(project, "取消", "Cancel"),
            Messages.getWarningIcon()
        );
        
        if (result == Messages.YES) {
            annotationService.clearAllAnnotations();
            // clearAllAnnotations方法已包含完整的UI和编辑器刷新机制
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
                // 检查是否有任何类型的备注
                Map<String, String> fileAnnotations = annotationService.getAllAnnotations();
                Map<String, String> packageAnnotations = annotationService.getAllPackageAnnotations();
                Map<String, String> fileMatchAnnotations = annotationService.getAllFileMatchAnnotations();
                Map<String, String> packageMatchAnnotations = annotationService.getAllPackageMatchAnnotations();
                
                boolean hasAnnotations = !fileAnnotations.isEmpty() || 
                                       !packageAnnotations.isEmpty() || 
                                       !fileMatchAnnotations.isEmpty() || 
                                       !packageMatchAnnotations.isEmpty();
                
                e.getPresentation().setVisible(true);
                e.getPresentation().setEnabled(hasAnnotations);
                
                // 根据当前语言动态显示菜单文本
                String currentLanguage = annotationService.getLanguage();
                if ("en".equals(currentLanguage)) {
                    e.getPresentation().setText("Clear All Annotations");
                    if (!hasAnnotations) {
                        e.getPresentation().setDescription("No annotations to clear in current project");
                    } else {
                        e.getPresentation().setDescription("Clear all annotations in current project");
                    }
                } else {
                    e.getPresentation().setText("清空所有备注");
                    if (!hasAnnotations) {
                        e.getPresentation().setDescription("当前项目没有任何备注可清空");
                    } else {
                        e.getPresentation().setDescription("清空当前项目的所有备注");
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

