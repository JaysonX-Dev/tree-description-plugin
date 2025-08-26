package com.github.annotations.ui;

import com.github.annotations.services.AnnotationService;
import com.github.annotations.utils.I18nUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.awt.Font;

/**
 * 添加文件匹配模式对话框
 * 允许用户添加文件匹配模式规则
 */
public class AddFileMatchDialog extends DialogWrapper {
    
    private final Project project;
    private final AnnotationService annotationService;
    
    private JBTextField patternField;
    private JBTextField descriptionField;
    
    public AddFileMatchDialog(@NotNull Project project) {
        super(project);
        this.project = project;
        this.annotationService = AnnotationService.getInstance(project);
        
        setTitle(I18nUtils.getText(project, "添加文件匹配映射", "Add File Match Mapping"));
        setSize(540, 200);
        init();
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(10));
        
        // 创建表单
        JPanel formPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(I18nUtils.getText(project, "匹配文件:", "Match File:"), createPatternField())
                .addLabeledComponent(I18nUtils.getText(project, "备注描述:", "Annotation Description:"), createDescriptionField())
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        
        // 添加使用说明
        JPanel helpPanel = createHelpPanel();
        mainPanel.add(helpPanel, BorderLayout.SOUTH);
        
        mainPanel.add(formPanel, BorderLayout.CENTER);
        return mainPanel;
    }
    
    private JComponent createPatternField() {
        patternField = new JBTextField();
        patternField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
        });
        return patternField;
    }
    
    private JComponent createDescriptionField() {
        descriptionField = new JBTextField();
        descriptionField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
        });
        return descriptionField;
    }
    

    
    private JPanel createHelpPanel() {
        JPanel helpPanel = new JPanel(new BorderLayout());
        helpPanel.setBorder(BorderFactory.createTitledBorder(I18nUtils.getText(project, "使用说明", "Usage Instructions")));
        
        JTextArea helpText = new JTextArea();
        helpText.setText(I18nUtils.getText(project, 
            "• 会匹配映射所对应的文件上\n" +
            "• 映射保存位置：项目根目录/mappings/local-description.json\n" +
            "• pom.xml 会匹配所有以 pom.xml 命名的文件",
            "• Will match the corresponding files\n" +
            "• Mapping save location: project root/mappings/local-description.json\n" +
            "• pom.xml will match all files named pom.xml"));
        helpText.setEditable(false);
        helpText.setLineWrap(true);
        helpText.setWrapStyleWord(true);
        helpText.setBackground(UIManager.getColor("Panel.background"));
        helpText.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        helpText.setFont(helpText.getFont().deriveFont(Font.PLAIN, 11));
        
        helpPanel.add(helpText, BorderLayout.CENTER);
        return helpPanel;
    }
    
    private void validateInput() {
        setOKActionEnabled(!patternField.getText().trim().isEmpty() && 
                          !descriptionField.getText().trim().isEmpty());
    }
    
    @Override
    protected void doOKAction() {
        String pattern = patternField.getText().trim();
        String description = descriptionField.getText().trim();
        
        if (pattern.isEmpty() || description.isEmpty()) {
            return;
        }
        
        // 检查是否已存在相同的模式
        Map<String, String> existingPatterns = annotationService.getAllFileMatchAnnotations();
        if (existingPatterns.containsKey(pattern)) {
            // 如果已存在，询问是否覆盖
            int result = JOptionPane.showConfirmDialog(
                getContentPane(),
                I18nUtils.getText(project, 
                    "文件匹配模式 \"" + pattern + "\" 已存在，是否覆盖？",
                    "File match pattern \"" + pattern + "\" already exists, do you want to overwrite it?"),
                I18nUtils.getText(project, "确认覆盖", "Confirm Overwrite"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE
            );
            
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
        
        // 保存到AnnotationService
        annotationService.setFileMatchAnnotation(pattern, description);
        
        super.doOKAction();
    }
    
    @Override
    protected ValidationInfo doValidate() {
        String pattern = patternField.getText().trim();
        String description = descriptionField.getText().trim();
        
        if (pattern.isEmpty()) {
            return new ValidationInfo(I18nUtils.getText(project, "请输入匹配模式", "Please enter match pattern"), patternField);
        }
        
        if (description.isEmpty()) {
            return new ValidationInfo(I18nUtils.getText(project, "请输入备注描述", "Please enter annotation description"), descriptionField);
        }
        
        // 检查模式是否包含特殊字符
        if (pattern.contains("/") || pattern.contains("\\") || pattern.contains(":")) {
            return new ValidationInfo(I18nUtils.getText(project, "匹配模式不能包含路径分隔符或特殊字符", "Match pattern cannot contain path separators or special characters"), patternField);
        }
        
        return null;
    }
}
