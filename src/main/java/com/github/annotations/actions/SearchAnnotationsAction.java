package com.github.annotations.actions;

import com.github.annotations.ui.SearchAnnotationsDialog;
import com.github.annotations.utils.I18nUtils;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;


public class SearchAnnotationsAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        
        if (project == null) {
            Project[] openProjects = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects();
                    if (openProjects.length > 0) {
            project = openProjects[0];
        } else {
            com.intellij.openapi.ui.Messages.showInfoMessage(
                I18nUtils.getText(project, "请先打开一个项目才能使用搜索功能", "Please open a project first to use the search function"), 
                I18nUtils.getText(project, "搜索项目树备注", "Search Project Tree Annotations")
            );
            return;
        }
        }
        
        SearchAnnotationsDialog dialog = new SearchAnnotationsDialog(project);
        dialog.show();
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(true);
        e.getPresentation().setVisible(true);
        
        // 根据当前语言动态显示菜单文本
        Project project = e.getProject();
        if (project != null) {
            try {
                com.github.annotations.services.AnnotationService service = com.github.annotations.services.AnnotationService.getInstance(project);
                if (service != null) {
                    String currentLanguage = service.getLanguage();
                    if ("en".equals(currentLanguage)) {
                        e.getPresentation().setText("Search Project Tree Annotations");
                        e.getPresentation().setDescription("Search all annotations in project");
                    } else {
                        e.getPresentation().setText("搜索项目树备注");
                        e.getPresentation().setDescription("搜索项目中的所有备注内容");
                    }
                } else {
                    e.getPresentation().setText("搜索项目树备注");
                    e.getPresentation().setDescription("搜索项目中的所有备注内容");
                }
            } catch (Exception ex) {
                e.getPresentation().setText("搜索项目树备注");
                e.getPresentation().setDescription("搜索项目中的所有备注内容");
            }
        } else {
            e.getPresentation().setText("搜索项目树备注");
            e.getPresentation().setDescription("搜索项目中的所有备注内容");
        }
    }
    
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
