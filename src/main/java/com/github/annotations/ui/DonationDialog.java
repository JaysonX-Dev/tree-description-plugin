package com.github.annotations.ui;

import com.github.annotations.utils.I18nUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * 捐赠对话框 - 支持更大的尺寸和更好的布局
 */
public class DonationDialog extends DialogWrapper {
    
    private final Project project;
    
    public DonationDialog(@NotNull Project project) {
        super(project);
        this.project = project;
        
        setTitle(I18nUtils.getText(project, "请我喝咖啡 ☕", "Buy me a coffee ☕"));
        setSize(650, 450);
        setResizable(false);
        init();
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(20));
        
        // 创建主内容面板
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        
        // 标题
        JLabel titleLabel = new JLabel(I18nUtils.getText(project, "感谢您使用 Tree Description !!！", "Thank you for using Tree Description!!"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setBorder(JBUI.Borders.emptyBottom(15));
        
        // 感谢内容
        JTextArea thankText = new JTextArea();
        thankText.setText(I18nUtils.getText(project,
                "如果这个插件对您有帮助，欢迎支持一下开发者。\n\n" +
                "您的支持将帮助我们：\n" +
                "• 持续改进插件功能\n" +
                "• 修复Bug和兼容性问题\n" +
                "• 添加更多实用特性\n",
                "If this plugin helps you, welcome to support the developer.\n\n" +
                "Your support will help us:\n" +
                "• Continuously improve plugin functionality\n" +
                "• Fix bugs and compatibility issues\n" +
                "• Add more useful features\n"));
        thankText.setEditable(false);
        thankText.setLineWrap(true);
        thankText.setWrapStyleWord(true);
        thankText.setBackground(UIManager.getColor("Panel.background"));
        thankText.setBorder(JBUI.Borders.empty(10));
        thankText.setFont(thankText.getFont().deriveFont(Font.PLAIN, 14));
        
        // 开源信息
        JPanel openSourcePanel = new JPanel(new BorderLayout());
        openSourcePanel.setBorder(BorderFactory.createTitledBorder(I18nUtils.getText(project, "项目信息", "Project Information")));
        
        JTextPane openSourceText = new JTextPane();
        openSourceText.setEditable(false);
        openSourceText.setBackground(openSourcePanel.getBackground());
        openSourceText.setFont(openSourceText.getFont().deriveFont(Font.PLAIN, 13));
        
        // 创建样式文档
        StyledDocument doc = openSourceText.getStyledDocument();
        SimpleAttributeSet normalStyle = new SimpleAttributeSet();
        SimpleAttributeSet linkStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(linkStyle, new Color(0, 102, 204));
        StyleConstants.setUnderline(linkStyle, true);
        
        try {
            // 添加纯文本内容
            doc.insertString(doc.getLength(), I18nUtils.getText(project, "项目已开源，欢迎参与贡献 ！\n\n", "Project is open source, welcome to contribute!\n\n"), normalStyle);
            doc.insertString(doc.getLength(), I18nUtils.getText(project, "主项目地址：\n", "Main project address:\n"), normalStyle);
            doc.insertString(doc.getLength(), "GitHub: ", normalStyle);
            doc.insertString(doc.getLength(), "https://github.com/JaysonX-Tech/tree-description-plugin", linkStyle);
            doc.insertString(doc.getLength(), "\n\n", normalStyle);
            doc.insertString(doc.getLength(), I18nUtils.getText(project, "开源映射库地址：\n", "Open source mapping library address:\n"), normalStyle);
            doc.insertString(doc.getLength(), "GitHub: ", normalStyle);
            doc.insertString(doc.getLength(), "https://github.com/JaysonX-Tech/tree-description-repository", linkStyle);
            doc.insertString(doc.getLength(), "\n\n", normalStyle);
            doc.insertString(doc.getLength(), I18nUtils.getText(project, "让我们一起打造丰富的开源映射库，\n", "Let's build a rich open source mapping library together,\n"), normalStyle);
            doc.insertString(doc.getLength(), I18nUtils.getText(project, "为开发者提供更好的项目理解工具 ！\n\n", "providing better project understanding tools for developers!\n\n"), normalStyle);
            doc.insertString(doc.getLength(), I18nUtils.getText(project, "如果觉得有用，欢迎给个Star支持 !!! \n", "If you find it useful, welcome to give a Star support!!!\n"), normalStyle);
        } catch (BadLocationException e) {
            // 忽略异常
        }
        
        // 添加超链接监听器
        openSourceText.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openGitHubPage();
            }
        });
        
        openSourcePanel.add(openSourceText, BorderLayout.CENTER);
        
        // 按钮区域
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setBorder(JBUI.Borders.emptyTop(20));
        
        // 访问GitHub主页按钮
        JButton githubButton = new JButton(I18nUtils.getText(project, "🌐 访问GitHub主页", "🌐 Visit GitHub Homepage"));
        githubButton.addActionListener(e -> openGitHubPage());
        
        // 给个Star按钮
        JButton starButton = new JButton(I18nUtils.getText(project, "⭐ 给个Star", "⭐ Give a Star"));
        starButton.addActionListener(e -> openGitHubPage());
        
        // 支持开发者按钮
        JButton donationButton = new JButton(I18nUtils.getText(project, "☕ 支持开发者", "☕ Support Developer"));
        donationButton.addActionListener(e -> openDonationPage());
        
        buttonPanel.add(githubButton);
        buttonPanel.add(starButton);
        buttonPanel.add(donationButton);
        
        // 组装面板
        contentPanel.add(titleLabel);
        contentPanel.add(thankText);
        contentPanel.add(Box.createVerticalStrut(15));
        contentPanel.add(openSourcePanel);
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(buttonPanel);
        
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        return mainPanel;
    }
    
    /**
     * 打开捐赠页面
     */
    private void openDonationPage() {
        try {
            // 这里可以替换为您的实际捐赠链接
            String donationUrl = "https://jaysonx-tech.github.io/TreeRemarkPage/TreeRemark.html";
            
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(donationUrl));
            } else {
                JOptionPane.showMessageDialog(
                    getContentPane(),
                    I18nUtils.getText(project, "请手动访问以下链接：\n\n", "Please manually visit the following link:\n\n") + donationUrl,
                    I18nUtils.getText(project, "捐赠链接", "Donation Link"),
                    JOptionPane.INFORMATION_MESSAGE
                );
            }
        } catch (IOException | UnsupportedOperationException e) {
            JOptionPane.showMessageDialog(
                getContentPane(),
                I18nUtils.getText(project, "无法打开捐赠页面，请手动访问：\n\n", "Unable to open donation page, please visit manually:\n\n") +
                "https://jaysonx-tech.github.io/TreeRemarkPage/TreeRemark.html",
                I18nUtils.getText(project, "打开失败", "Open Failed"),
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
    
    /**
     * 打开GitHub页面
     */
    private void openGitHubPage() {
        try {
            String githubUrl = "https://github.com/JaysonX-Tech/tree-description-repository";
            
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(githubUrl));
            } else {
                JOptionPane.showMessageDialog(
                    getContentPane(),
                    I18nUtils.getText(project, "请手动访问以下链接：\n\n", "Please manually visit the following link:\n\n") + githubUrl,
                    I18nUtils.getText(project, "GitHub链接", "GitHub Link"),
                    JOptionPane.ERROR_MESSAGE
                );
            }
        } catch (IOException | UnsupportedOperationException e) {
            JOptionPane.showMessageDialog(
                getContentPane(),
                I18nUtils.getText(project, "无法打开GitHub页面，请手动访问：\n\n", "Unable to open GitHub page, please visit manually:\n\n") +
                "https://github.com/JaysonX-Tech/tree-description-repository",
                I18nUtils.getText(project, "打开失败", "Open Failed"),
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
