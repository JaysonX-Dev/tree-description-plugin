package com.github.annotations.ui;

import com.github.annotations.utils.I18nUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * 提取注释为备注对话框 - 支持颜色选择和内容预览
 * 复用AddAnnotationDialog的颜色选择功能
 */
public class ExtractCommentDialog extends DialogWrapper {
    
    private final Project project;
    private final List<String> extractedComments;
    private final int fileCount;
    
    private ButtonGroup colorButtonGroup;
    private String selectedColor = "#BBBBBB"; // 默认灰色
    
    // macOS 访达风格的颜色
    private static final ColorOption[] COLOR_OPTIONS = {
        new ColorOption("灰色", "#BBBBBB", true),   // 默认
        new ColorOption("蓝色", "#265DE8", false),
        new ColorOption("绿色", "#51B433", false),
        new ColorOption("红色", "#E93225", false),
        new ColorOption("橙色", "#DE6F36", false),
        new ColorOption("黄色", "#E6B33C", false),
        new ColorOption("紫色", "#AD8EF8", false),
        new ColorOption("粉色", "#E95573", false)
    };
    
    public ExtractCommentDialog(@NotNull Project project, @NotNull List<String> extractedComments, int fileCount) {
        super(project);
        this.project = project;
        this.extractedComments = extractedComments;
        this.fileCount = fileCount;
        
        setTitle(I18nUtils.getText(project, "提取注释为备注", "Extract Comment as Remark"));
        setSize(400, 200);
        init();
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(10));
        
        // 创建表单
        JPanel formPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(createInfoLabel(), createContentPreview())
                .addVerticalGap(8)
                .addLabeledComponent(I18nUtils.getText(project, "备注颜色:", "Annotation Color:"), createColorPanel())
                .getPanel();
        
        mainPanel.add(formPanel, BorderLayout.CENTER);
        return mainPanel;
    }
    
    private JComponent createInfoLabel() {
        String labelText;
        if (fileCount == 1) {
            labelText = I18nUtils.getText(project, "提取类文件备注:", "Extract Class Annotation:");
        } else {
            labelText = I18nUtils.getText(project, 
                String.format("%d 个备注:", fileCount),
                String.format("Annotation for %d files:", fileCount));
        }
        return new JBLabel(labelText);
    }
    
    private JComponent createContentPreview() {
        JBTextArea textArea = new JBTextArea();
        textArea.setEditable(false);
        textArea.setBackground(UIManager.getColor("Panel.background"));
        textArea.setFont(UIManager.getFont("Label.font"));
        textArea.setBorder(JBUI.Borders.empty(6));
        
        StringBuilder content = new StringBuilder();
        if (extractedComments.size() == 1) {
            // 单个文件，显示提取的注释内容
            content.append(extractedComments.get(0));
        } else {
            // 多个文件，显示文件数量和部分内容预览
            content.append(I18nUtils.getText(project, 
                String.format("共找到 %d 个文件的注释:\n\n", extractedComments.size()),
                String.format("Found comments in %d files:\n\n", extractedComments.size())));
            
            int previewCount = Math.min(3, extractedComments.size());
            for (int i = 0; i < previewCount; i++) {
                content.append("• ").append(extractedComments.get(i)).append("\n");
            }
            
            if (extractedComments.size() > 3) {
                content.append(I18nUtils.getText(project, 
                    String.format("... 还有 %d 个文件", extractedComments.size() - 3),
                    String.format("... and %d more files", extractedComments.size() - 3)));
            }
        }
        
        textArea.setText(content.toString());
        textArea.setCaretPosition(0);
        
        JBScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(450, 35));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        return scrollPane;
    }
    
    private JComponent createColorPanel() {
        JPanel colorPanel = new JPanel();
        
        // 使用GridLayout精确控制间距，1行8列
        colorPanel.setLayout(new GridLayout(1, 8, 4, 0));
        colorPanel.setBorder(JBUI.Borders.empty(3));
        
        colorButtonGroup = new ButtonGroup();
        
        for (ColorOption colorOption : COLOR_OPTIONS) {
            JRadioButton colorButton = createColorButton(colorOption);
            colorButtonGroup.add(colorButton);
            colorPanel.add(colorButton);
            
            // 默认选中灰色
            if (colorOption.isDefault) {
                colorButton.setSelected(true);
                selectedColor = colorOption.colorCode;
            }
        }
        
        return colorPanel;
    }
    
    private JRadioButton createColorButton(ColorOption colorOption) {
        JRadioButton button = new JRadioButton();
        button.setPreferredSize(new Dimension(36, 24));
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
    
    @Override
    protected ValidationInfo doValidate() {
        if (extractedComments.isEmpty()) {
            return new ValidationInfo(I18nUtils.getText(project, "没有找到可提取的注释", "No extractable comments found"));
        }
        return null;
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