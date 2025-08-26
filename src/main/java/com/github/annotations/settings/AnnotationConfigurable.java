package com.github.annotations.settings;

import com.github.annotations.services.AnnotationService;
import com.github.annotations.utils.LanguageManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * 插件设置页面配置
 */
public class AnnotationConfigurable implements Configurable {
    
    private final Project project;
    private JComboBox<LanguageManager.Language> languageComboBox;
    private JCheckBox projectTreeAnnotationsCheckBox;
    private JCheckBox builtinMappingsCheckBox;
    
    public AnnotationConfigurable(Project project) {
        this.project = project;
    }
    
    @Nls
    @Override
    public String getDisplayName() {
        return "路径备注";
    }
    
    @Nullable
    @Override
    public JComponent createComponent() {
        // 创建语言选择下拉框
        languageComboBox = new JComboBox<>(LanguageManager.Language.values());
        languageComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof LanguageManager.Language) {
                    setText(((LanguageManager.Language) value).getDisplayName());
                }
                return this;
            }
        });
        
        // 创建项目树备注开关
        projectTreeAnnotationsCheckBox = new JCheckBox("启用项目树备注");
        
        // 创建内置映射库开关
        builtinMappingsCheckBox = new JCheckBox("启用内置映射库");
        
        // 使用FormBuilder创建布局
        JPanel panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("语言:", languageComboBox)
            .addComponent(projectTreeAnnotationsCheckBox)
            .addComponent(builtinMappingsCheckBox)
            .addComponentFillVertically(new JPanel(), 0)
            .getPanel();
        
        panel.setPreferredSize(new Dimension(500, 200));
        
        // 设置当前值
        reset();
        
        return panel;
    }
    
    @Override
    public void reset() {
        AnnotationService service = AnnotationService.getInstance(project);
        if (service == null) return;
        
        // 设置语言选择
        String currentLanguage = service.getLanguage();
        for (LanguageManager.Language lang : LanguageManager.Language.values()) {
            if (lang.getCode().equals(currentLanguage)) {
                languageComboBox.setSelectedItem(lang);
                break;
            }
        }
        
        // 设置项目树备注开关
        projectTreeAnnotationsCheckBox.setSelected(service.isProjectTreeAnnotationsEnabled());
        
        // 设置内置映射库开关
        builtinMappingsCheckBox.setSelected(service.isBuiltinMappingsEnabled());
    }
    
    @Override
    public boolean isModified() {
        AnnotationService service = AnnotationService.getInstance(project);
        if (service == null) return false;
        
        // 检查语言设置是否改变
        LanguageManager.Language selectedLanguage = (LanguageManager.Language) languageComboBox.getSelectedItem();
        if (selectedLanguage != null && !selectedLanguage.getCode().equals(service.getLanguage())) {
            return true;
        }
        
        // 检查项目树备注开关是否改变
        if (projectTreeAnnotationsCheckBox.isSelected() != service.isProjectTreeAnnotationsEnabled()) {
            return true;
        }
        
        // 检查内置映射库开关是否改变
        if (builtinMappingsCheckBox.isSelected() != service.isBuiltinMappingsEnabled()) {
            return true;
        }
        
        return false;
    }
    
    @Override
    public void apply() {
        AnnotationService service = AnnotationService.getInstance(project);
        if (service == null) return;
        
        // 应用语言设置
        LanguageManager.Language selectedLanguage = (LanguageManager.Language) languageComboBox.getSelectedItem();
        if (selectedLanguage != null) {
            service.setLanguage(selectedLanguage.getCode());
        }
        
        // 应用项目树备注开关
        service.setProjectTreeAnnotationsEnabled(projectTreeAnnotationsCheckBox.isSelected());
        
        // 应用内置映射库开关
        service.setBuiltinMappingsEnabled(builtinMappingsCheckBox.isSelected());
    }
}

