package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.github.annotations.actions.LanguageSubMenuAction;
import com.github.annotations.actions.ToggleProjectTreeAnnotationsAction;
import com.github.annotations.actions.ViewMappingsAction;
import com.github.annotations.actions.BuyMeACoffeeAction;

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
        // 创建语言子菜单
        LanguageSubMenuAction languageGroup = new LanguageSubMenuAction();
        
        // 创建主菜单组
        DefaultActionGroup mainGroup = new DefaultActionGroup();
        mainGroup.add(languageGroup);
        mainGroup.addSeparator();
        mainGroup.add(new ToggleProjectTreeAnnotationsAction());
        // 在禁用项目树备注下方添加搜索备注（引用已注册的Action）
        AnAction searchAction = com.intellij.openapi.actionSystem.ActionManager.getInstance()
            .getAction("ChineseAnnotations.SearchAnnotations");
        if (searchAction != null) {
            mainGroup.add(searchAction);
        }
        mainGroup.addSeparator();
        mainGroup.add(new ViewMappingsAction());
        mainGroup.addSeparator();
        mainGroup.add(new BuyMeACoffeeAction());
        
        return mainGroup.getChildren(e);
    }
}
