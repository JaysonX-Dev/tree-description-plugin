package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 语言子菜单组Action
 * 支持动态语言切换
 */
public class LanguageSubMenuAction extends ActionGroup {
    
    public LanguageSubMenuAction() {
        super("语言", true);
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            AnnotationService service = AnnotationService.getInstance(project);
            if (service != null) {
                String currentLanguage = service.getLanguage();
                if ("en".equals(currentLanguage)) {
                    e.getPresentation().setText("Language");
                } else {
                    e.getPresentation().setText("语言");
                }
            }
        }
    }
    
    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
        return new AnAction[]{new SwitchToEnglishAction(), new SwitchToChineseAction()};
    }
}
