package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 备注工具菜单组Action
 * 支持动态语言切换
 */
public class ToolsMenuAction extends ActionGroup {
    
    public ToolsMenuAction() {
        super("备注工具", true);
        // 图标通过plugin.xml设置
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            AnnotationService service = AnnotationService.getInstance(project);
            if (service != null) {
                String currentLanguage = service.getLanguage();
                if ("en".equals(currentLanguage)) {
                    e.getPresentation().setText("Annotation Tools");
                } else {
                    e.getPresentation().setText("备注工具");
                }
            }
        }
    }
    
    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
        // 直接返回Action数组，避免使用Override-only API
        return new AnAction[]{
            new LanguageSubMenuAction(),
            Separator.getInstance(),  // 分隔符
            new ToggleProjectTreeAnnotationsAction(),
            /*
            // 临时注释掉极速搜索，后期修改
            com.intellij.openapi.actionSystem.ActionManager.getInstance()
                .getAction("ChineseAnnotations.SearchAnnotations"),
            */
            Separator.getInstance(),  // 分隔符
            new ViewMappingsAction(),
            Separator.getInstance(),  // 分隔符
            new BuyMeACoffeeAction()
        };
    }
}
