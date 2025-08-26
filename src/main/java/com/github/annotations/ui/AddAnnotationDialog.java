package com.github.annotations.ui;

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

/**
 * 添加备注对话框 - 支持颜色选择
 * 类似 macOS 访达风格的标签颜色
 */
public class AddAnnotationDialog extends DialogWrapper {
    
    private final Project project;
    private final String fileName;
    private final String initialAnnotation;
    private final String initialColor;
    
    private JBTextField annotationField;
    private ButtonGroup colorButtonGroup;
    private String selectedColor = "#BBBBBB"; // 默认灰色
    
    // macOS 访达风格的颜色
    private static final ColorOption[] COLOR_OPTIONS = {
        new ColorOption("灰色", "#BBBBBB", true),   // 默认
        new ColorOption("蓝色", "#265DE8", false),
        new ColorOption("绿色", "#51B433", false),
        new ColorOption("红色", "#E93225", false),
        new ColorOption("橙色", "#E57B2D", false),
        new ColorOption("黄色", "#E6B33C", false),
        new ColorOption("紫色", "#9F43C7", false),
        new ColorOption("粉色", "#C76299", false)
    };
    
    public AddAnnotationDialog(@NotNull Project project, @NotNull String fileName, @Nullable String initialAnnotation, @Nullable String initialColor) {
        super(project);
        this.project = project;
        this.fileName = fileName;
        this.initialAnnotation = initialAnnotation != null ? initialAnnotation : "";
        this.initialColor = initialColor != null && isValidColor(initialColor) ? initialColor : "#BBBBBB";
        
        setTitle(I18nUtils.getText(project, "添加备注", "Add Annotation"));
        setSize(480, 180);
        init();
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(15));
        
        // 创建表单
        JPanel formPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(I18nUtils.getText(project, "为 \"" + fileName + "\" 添加备注:", "Add annotation for \"" + fileName + "\":"), createAnnotationField())
                .addVerticalGap(15)
                .addLabeledComponent(I18nUtils.getText(project, "备注颜色:", "Annotation Color:"), createColorPanel())
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        
        mainPanel.add(formPanel, BorderLayout.CENTER);
        return mainPanel;
    }
    
    private JComponent createAnnotationField() {
        annotationField = new JBTextField(initialAnnotation);
        annotationField.setPreferredSize(new Dimension(350, 30));
        annotationField.setToolTipText(I18nUtils.getText(project, "输入备注内容", "Enter annotation content"));
        
        // 添加文档监听器用于验证
        annotationField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
        });
        
        return annotationField;
    }
    
    private JComponent createColorPanel() {
        JPanel colorPanel = new JPanel();
        
        // 使用GridLayout精确控制间距，1行8列
        colorPanel.setLayout(new GridLayout(1, 8, 6, 0));
        colorPanel.setBorder(JBUI.Borders.empty(5));
        
        colorButtonGroup = new ButtonGroup();
        
        for (ColorOption colorOption : COLOR_OPTIONS) {
            JRadioButton colorButton = createColorButton(colorOption);
            colorButtonGroup.add(colorButton);
            colorPanel.add(colorButton);
            
            // 根据初始颜色或默认颜色设置选中状态
            if ((initialColor != null && colorOption.colorCode.equals(initialColor)) || 
                (initialColor == null && colorOption.isDefault)) {
                colorButton.setSelected(true);
                selectedColor = colorOption.colorCode;
            }
        }
        
        return colorPanel;
    }
    
    private JRadioButton createColorButton(ColorOption colorOption) {
        JRadioButton button = new JRadioButton();
        button.setPreferredSize(new Dimension(40, 28));
        button.setToolTipText(getColorName(colorOption.name) + " " + colorOption.colorCode);
        
        // 自定义渲染颜色圆点
        button.setUI(new javax.swing.plaf.basic.BasicRadioButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // 计算圆点位置
                int centerX = c.getWidth() / 2;
                int centerY = c.getHeight() / 2;
                int radius = 8;
                
                // 绘制外圈（选中状态）
                if (button.isSelected()) {
                    g2.setColor(new Color(37, 93, 232)); // 蓝色外圈
                    g2.setStroke(new BasicStroke(2));
                    g2.drawOval(centerX - radius - 2, centerY - radius - 2, (radius + 2) * 2, (radius + 2) * 2);
                }
                
                // 绘制颜色圆点
                Color color = Color.decode(colorOption.colorCode);
                g2.setColor(color);
                g2.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
                
                // 绘制边框
                g2.setColor(new Color(200, 200, 200));
                g2.setStroke(new BasicStroke(1));
                g2.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
                
                g2.dispose();
            }
        });
        
        // 添加选择监听器
        button.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                selectedColor = colorOption.colorCode;
            }
        });
        
        return button;
    }
    
    private void validateInput() {
        setOKActionEnabled(!annotationField.getText().trim().isEmpty());
    }
    
    @Override
    protected void doOKAction() {
        super.doOKAction();
    }
    
    @Override
    protected ValidationInfo doValidate() {
        String annotation = annotationField.getText().trim();
        
        if (annotation.isEmpty()) {
            return new ValidationInfo(I18nUtils.getText(project, "请输入备注内容", "Please enter annotation content"), annotationField);
        }
        
        return null;
    }
    
    /**
     * 获取输入的备注
     */
    public String getAnnotation() {
        return annotationField.getText().trim();
    }
    
    /**
     * 获取选择的颜色
     */
    public String getSelectedColor() {
        return selectedColor;
    }
    
    /**
     * 是否使用默认颜色（灰色）
     */
    public boolean isDefaultColor() {
        return "#BBBBBB".equals(selectedColor);
    }
    
    /**
     * 验证颜色是否在预定义颜色列表中
     */
    private boolean isValidColor(String colorCode) {
        if (colorCode == null) return false;
        for (ColorOption option : COLOR_OPTIONS) {
            if (option.colorCode.equals(colorCode)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取颜色的国际化名称
     */
    private String getColorName(String chineseName) {
        switch (chineseName) {
            case "灰色": return I18nUtils.getText(project, "灰色", "Gray");
            case "蓝色": return I18nUtils.getText(project, "蓝色", "Blue");
            case "绿色": return I18nUtils.getText(project, "绿色", "Green");
            case "红色": return I18nUtils.getText(project, "红色", "Red");
            case "橙色": return I18nUtils.getText(project, "橙色", "Orange");
            case "黄色": return I18nUtils.getText(project, "黄色", "Yellow");
            case "紫色": return I18nUtils.getText(project, "紫色", "Purple");
            case "粉色": return I18nUtils.getText(project, "粉色", "Pink");
            default: return chineseName;
        }
    }
    
    /**
     * 颜色选项类
     */
    private static class ColorOption {
        final String name;
        final String colorCode;
        final boolean isDefault;
        
        ColorOption(String name, String colorCode, boolean isDefault) {
            this.name = name;
            this.colorCode = colorCode;
            this.isDefault = isDefault;
        }
    }
}
