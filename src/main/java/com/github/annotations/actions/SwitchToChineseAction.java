package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

/**
 * 切换到中文Action
 */
public class SwitchToChineseAction extends AnAction {
    
    public SwitchToChineseAction() {
        super("中文");
        getTemplatePresentation().setDescription("切换到中文");
    }
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            Messages.showErrorDialog("无法获取项目信息", "错误");
            return;
        }
        
        AnnotationService service = AnnotationService.getInstance(project);
        if (service == null) {
            Messages.showErrorDialog("无法获取备注服务", "错误");
            return;
        }
        
        try {
            // 设置语言为中文
            service.setLanguage("zh");
            
            // 显示成功消息
            Messages.showInfoMessage(project, "语言已切换到中文", "语言切换");
            
        } catch (Exception ex) {
            Messages.showErrorDialog(project, "语言切换失败：" + ex.getMessage(), "错误");
        }
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabled(false);
            return;
        }
        
        AnnotationService service = AnnotationService.getInstance(project);
        if (service == null) {
            e.getPresentation().setEnabled(false);
            return;
        }
        
        // 检查当前语言，如果是中文则禁用
        String currentLanguage = service.getLanguage();
        boolean isCurrentLanguage = "zh".equals(currentLanguage);
        e.getPresentation().setEnabled(!isCurrentLanguage);
        
        // 根据当前语言显示不同的文本
        if (isCurrentLanguage) {
            // 当前是中文，显示选中状态
            e.getPresentation().setText("✓ 中文");
        } else {
            // 当前是英文，显示中文选项
            e.getPresentation().setText("Chinese");
        }
    }
}
