package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.github.annotations.actions.RemoveAnnotationAction;
import com.github.annotations.actions.ClearAllAnnotationsAction;
import com.github.annotations.actions.AddFileMatchAction;
import com.github.annotations.actions.AddPackageMatchAction;

/**
 * 项目备注操作右键菜单组Action
 * 支持动态语言切换
 */
public class ContextMenuAction extends ActionGroup {
    
    public ContextMenuAction() {
        super("项目备注操作", true);
        getTemplatePresentation().setIcon(com.intellij.openapi.util.IconLoader.getIcon("icons/remark.svg", ContextMenuAction.class));
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
                } else {
                    e.getPresentation().setText("项目备注操作");
                }
            }
        }
    }
    
    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
        java.util.List<AnAction> actions = new java.util.ArrayList<>();
        
        // 引用已注册的AddAnnotationAction（保留快捷键配置）
        AnAction addAction = com.intellij.openapi.actionSystem.ActionManager.getInstance()
            .getAction("ChineseAnnotations.AddAnnotation");
        if (addAction != null) {
            actions.add(addAction);
        }
        actions.add(new RemoveAnnotationAction());
        actions.add(null); // 分隔符
        
        // 引用已注册的ExtractCommentAnnotationAction（保留快捷键配置）
        AnAction extractAction = com.intellij.openapi.actionSystem.ActionManager.getInstance()
            .getAction("ChineseAnnotations.ExtractComment");
        if (extractAction != null) {
            actions.add(extractAction);
        }
        actions.add(new ClearAllAnnotationsAction());
        actions.add(null); // 分隔符
        actions.add(new AddFileMatchAction());
        actions.add(new AddPackageMatchAction());
        
        return actions.toArray(new AnAction[0]);
    }
}
