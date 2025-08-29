package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 项目备注操作右键菜单组Action
 * 支持动态语言切换
 */
public class ContextMenuGroupAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // 这是一个组Action，不需要执行具体操作
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            AnnotationService service = AnnotationService.getInstance(project);
            if (service != null) {
                String currentLanguage = service.getLanguage();
                if ("en".equals(currentLanguage)) {
                    e.getPresentation().setText("Project Annotation Operations");
                } else      e.getPresentation().setText("Project Annotation Operations"); {
                    e.getPresentation().setText("项目备注操作");
                }
            }
        }
        e.getPresentation().setIcon(com.intellij.openapi.util.IconLoader.getIcon("icons/remark.svg", ContextMenuGroupAction.class));
    }
}
