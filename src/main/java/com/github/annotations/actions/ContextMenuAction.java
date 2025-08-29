package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.github.annotations.actions.AddAnnotationAction;
import com.github.annotations.actions.RemoveAnnotationAction;
import com.github.annotations.actions.ExtractCommentAnnotationAction;
import com.github.annotations.actions.ClearAllAnnotationsAction;
import com.github.annotations.actions.AddFileMatchAction;
import com.github.annotations.actions.AddPackageMatchAction;
import com.github.annotations.actions.ClearAllMappingsAction;

/**
 * 项目备注操作右键菜单组Action
 * 支持动态语言切换
 */
public class ContextMenuAction extends DefaultActionGroup {
    
    private static final Logger LOG = Logger.getInstance(ContextMenuAction.class);
    
    public ContextMenuAction() {
        super("项目备注操作", true);
        getTemplatePresentation().setIcon(com.intellij.openapi.util.IconLoader.getIcon("icons/remark.svg", ContextMenuAction.class));
        
        // 使用ActionManager获取已注册的Action实例，保持快捷键配置
        ActionManager actionManager = ActionManager.getInstance();
        
        AnAction addAction = actionManager.getAction("ChineseAnnotations.AddAnnotation");
        if (addAction != null) {
            add(addAction);
        } else {
            add(new AddAnnotationAction());
        }
        
        AnAction removeAction = actionManager.getAction("ChineseAnnotations.RemoveAnnotation");
        if (removeAction != null) {
            add(removeAction);
        } else {
            add(new RemoveAnnotationAction());
        }
        
        AnAction extractAction = actionManager.getAction("ChineseAnnotations.ExtractComment");
        if (extractAction != null) {
            add(extractAction);
        } else {
            add(new ExtractCommentAnnotationAction());
        }
        
        add(new ClearAllAnnotationsAction());
        addSeparator();
        add(new AddFileMatchAction());
        add(new AddPackageMatchAction());
        add(new ClearAllMappingsAction());
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
    

}
