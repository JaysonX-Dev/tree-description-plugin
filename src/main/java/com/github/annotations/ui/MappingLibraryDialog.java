package com.github.annotations.ui;

import com.github.annotations.services.GitHubMappingService;
import com.github.annotations.services.GitHubMappingService.GitHubMappingFile;
import com.github.annotations.services.GitHubMappingService.GitHubMappingLibrary;
import com.github.annotations.services.VFSRefreshService;
import com.github.annotations.utils.I18nUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.util.List;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 开源映射库管理对话框
 * 显示GitHub上的开源映射库内容
 */
public class MappingLibraryDialog extends DialogWrapper {
    
    private final Project project;
    private JBTable mappingTable;
    private JComboBox<GitHubMappingFile> libraryComboBox;
    private JButton downloadButton;
    private final GitHubMappingService gitHubService;
    private final VFSRefreshService vfsRefreshService;
    
    public MappingLibraryDialog(@Nullable Project project) {
        super(project);
        this.project = project;
        this.gitHubService = new GitHubMappingService();
        this.vfsRefreshService = new VFSRefreshService(project);
        setTitle(I18nUtils.getText(project, "查看GitHub开源映射库", "View GitHub Open Source Mapping Library"));
        setSize(900, 700);
        init();
    }
    
    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        JPanel topPanel = createLibrarySelectionPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        JPanel centerPanel = createMappingTablePanel();
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        JPanel bottomPanel = createInfoPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        loadGitHubLibraries();
        
        return mainPanel;
    }
    
    /**
     * 创建映射库选择面板
     */
    private JPanel createLibrarySelectionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        panel.add(new JLabel(I18nUtils.getText(project, "选择映射库：", "Select Mapping Library:")));
        
        libraryComboBox = new JComboBox<>();
        libraryComboBox.addActionListener(e -> loadSelectedLibraryMappings());
        panel.add(libraryComboBox);
        
        downloadButton = new JButton(I18nUtils.getText(project, "下载到本地", "Download to Local"));
        downloadButton.addActionListener(e -> downloadSelectedLibraryToLocal());
        panel.add(downloadButton);
        
        JButton refreshButton = new JButton(I18nUtils.getText(project, "刷新", "Refresh"));
        refreshButton.addActionListener(e -> loadGitHubLibraries());
        panel.add(refreshButton);
        
        return panel;
    }
    
    /**
     * 创建映射表格面板
     */
    private JPanel createMappingTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        String[] columnNames = {
            I18nUtils.getText(project, "类型", "Type"),
            I18nUtils.getText(project, "名称", "Name"),
            I18nUtils.getText(project, "映射", "Mapping")
        };
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        mappingTable = new JBTable(tableModel);
        mappingTable.getTableHeader().setReorderingAllowed(false);
        mappingTable.setRowHeight(25);
        
        mappingTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        mappingTable.getColumnModel().getColumn(1).setPreferredWidth(250);
        mappingTable.getColumnModel().getColumn(2).setPreferredWidth(250);
        
        panel.add(new JBScrollPane(mappingTable), BorderLayout.CENTER);
        return panel;
    }
    
    /**
     * 创建说明信息面板
     */
    private JPanel createInfoPanel() {
        JPanel infoPanel = new JPanel(new BorderLayout());
        
        JTextPane infoArea = new JTextPane();
        infoArea.setEditable(false);
        infoArea.setBackground(infoPanel.getBackground());
        infoArea.setBorder(JBUI.Borders.empty(10));
        infoArea.setFont(infoArea.getFont().deriveFont(Font.PLAIN, 13));
        
        // 创建样式文档
        StyledDocument doc = infoArea.getStyledDocument();
        SimpleAttributeSet normalStyle = new SimpleAttributeSet();
        SimpleAttributeSet linkStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(linkStyle, new Color(0, 102, 204));
        StyleConstants.setUnderline(linkStyle, true);
        
        try {
            // 添加纯文本内容
            doc.insertString(doc.getLength(), I18nUtils.getText(project, "映射库来源于 GitHub 开源仓库：", "Mapping library comes from GitHub open source repository: "), normalStyle);
            doc.insertString(doc.getLength(), "https://github.com/JaysonX-Tech/tree-description-repository", linkStyle);
            doc.insertString(doc.getLength(), " \n" + I18nUtils.getText(project, "如果遇到下载问题，请前往 Github 手动下载", "If you encounter download issues, please go to Github to download manually."), normalStyle);
            doc.insertString(doc.getLength(), " \n" + I18nUtils.getText(project, "欢迎提交 PR 参与贡献 !!！", "Welcome to submit PR to contribute!!"), normalStyle);
        } catch (BadLocationException e) {
            // 忽略异常
        }
        
        // 添加超链接监听器
        infoArea.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openGitHubPage();
            }
        });
        
        infoPanel.add(infoArea, BorderLayout.CENTER);
 
        return infoPanel;
    }
    
    /**
     * 加载GitHub上的映射库
     */
    private void loadGitHubLibraries() {
        SwingUtilities.invokeLater(() -> {
            libraryComboBox.removeAllItems();
            libraryComboBox.addItem(new GitHubMappingFile("", I18nUtils.getText(project, "正在加载...", "Loading..."), false));
            
            new Thread(() -> {
                try {
                    List<GitHubMappingFile> files = gitHubService.getRepositoryContents();
                    
                    SwingUtilities.invokeLater(() -> {
                        libraryComboBox.removeAllItems();
                        
                        if (files.isEmpty()) {
                            libraryComboBox.addItem(new GitHubMappingFile("", "(没有可用的映射库)", false));
                            clearMappingTable();
                        } else {
                            for (GitHubMappingFile file : files) {
                                libraryComboBox.addItem(file);
                            }
                            
                            if (libraryComboBox.getItemCount() > 0) {
                                libraryComboBox.setSelectedIndex(0);
                                loadSelectedLibraryMappings();
                            }
                        }
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        libraryComboBox.removeAllItems();
                        String errorMessage;
                        if (e.getMessage() != null && e.getMessage().contains("速率限制")) {
                            errorMessage = "(GitHub API速率限制，请稍后再试)";
                        } else if (e.getMessage() != null && e.getMessage().contains("访问被拒绝")) {
                            errorMessage = "(GitHub API访问被拒绝)";
                        } else {
                            errorMessage = "(加载失败，请检查网络连接)";
                        }
                        libraryComboBox.addItem(new GitHubMappingFile("", errorMessage, false));
                        clearMappingTable();
                    });
                }
            }).start();
        });
    }
    
    /**
     * 加载选中映射库的映射关系
     */
    private void loadSelectedLibraryMappings() {
        GitHubMappingFile selectedFile = (GitHubMappingFile) libraryComboBox.getSelectedItem();
        if (selectedFile == null || selectedFile.path.isEmpty() || 
            selectedFile.path.contains("正在加载") || selectedFile.path.contains("没有可用的") || 
            selectedFile.path.contains("加载失败")) {
            clearMappingTable();
            return;
        }
        
        clearMappingTable();
        DefaultTableModel model = (DefaultTableModel) mappingTable.getModel();
        model.addRow(new Object[]{"加载中", selectedFile.path, "正在下载..."});
        
        new Thread(() -> {
            try {
                String jsonContent = gitHubService.downloadMappingFile(selectedFile.path);
                if (jsonContent != null) {
                    GitHubMappingLibrary library = gitHubService.parseMappingFile(jsonContent);
                    if (library != null) {
                        SwingUtilities.invokeLater(() -> loadLibraryMappings(library));
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            clearMappingTable();
                            model.addRow(new Object[]{"错误", selectedFile.path, "解析失败"});
                        });
                    }
                } else {
                    SwingUtilities.invokeLater(() -> {
                        clearMappingTable();
                        model.addRow(new Object[]{"错误", selectedFile.path, "下载失败"});
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    clearMappingTable();
                    model.addRow(new Object[]{"错误", selectedFile.path, "加载失败"});
                });
            }
        }).start();
    }
    
    /**
     * 加载指定映射库的数据到表格
     */
    private void loadLibraryMappings(GitHubMappingLibrary library) {
        DefaultTableModel model = (DefaultTableModel) mappingTable.getModel();
        model.setRowCount(0);
        
        if (library.mappings == null) {
            model.addRow(new Object[]{"信息", library.name != null ? library.name : "未知", "无映射数据"});
            return;
        }
        
        if (library.mappings.packages != null) {
            for (java.util.Map.Entry<String, String> entry : library.mappings.packages.entrySet()) {
                model.addRow(new Object[]{
                    "包映射",
                    entry.getKey(),
                    entry.getValue()
                });
            }
        }
        
        if (library.mappings.files != null) {
            for (java.util.Map.Entry<String, String> entry : library.mappings.files.entrySet()) {
                model.addRow(new Object[]{
                    "文件映射",
                    entry.getKey(),
                    entry.getValue()
                });
            }
        }
        
        if (library.mappings.packageMatch != null) {
            for (java.util.Map.Entry<String, String> entry : library.mappings.packageMatch.entrySet()) {
                model.addRow(new Object[]{
                    "包匹配",
                    entry.getKey(),
                    entry.getValue()
                });
            }
        }
        
        if (library.mappings.fileMatch != null) {
            for (java.util.Map.Entry<String, String> entry : library.mappings.fileMatch.entrySet()) {
                model.addRow(new Object[]{
                    "文件匹配",
                    entry.getKey(),
                    entry.getValue()
                });
            }
        }
        
        if (model.getRowCount() == 0) {
            model.addRow(new Object[]{"信息", library.name != null ? library.name : "未知", "无映射数据"});
        }
    }
    
    /**
     * 清空映射表格
     */
    private void clearMappingTable() {
        DefaultTableModel model = (DefaultTableModel) mappingTable.getModel();
        model.setRowCount(0);
    }
    
    @Override
    protected Action[] createActions() {
        return new Action[]{getOKAction()};
    }

    /**
     * 下载选中的映射库到本地
     */
    private void downloadSelectedLibraryToLocal() {
        GitHubMappingFile selectedFile = (GitHubMappingFile) libraryComboBox.getSelectedItem();
        if (selectedFile == null || selectedFile.path.isEmpty() || 
            selectedFile.path.contains("正在加载") || selectedFile.path.contains("没有可用的") || 
            selectedFile.path.contains("加载失败")) {
            JOptionPane.showMessageDialog(getContentPane(), 
                I18nUtils.getText(project, "请先选择一个有效的映射库文件", "Please select a valid mapping library file first"), 
                I18nUtils.getText(project, "提示", "Tip"), JOptionPane.PLAIN_MESSAGE);
            return;
        }
        
        // 显示确认对话框
        int result = JOptionPane.showConfirmDialog(getContentPane(), 
            I18nUtils.getText(project,
                "确定要将 \"" + selectedFile.displayName + "\" 下载到本地 .td-maps 文件夹吗？\n\n" +
                "文件将保存为: " + selectedFile.displayName + "\n" +
                "保存位置: 项目根目录/.td-maps/",
            "Are you sure you want to download \"" + selectedFile.displayName + "\" to the local .td-maps folder?\n\n" +
                "File will be saved as: " + selectedFile.displayName + "\n" +
                "Save location: project root/.td-maps/"), 
            I18nUtils.getText(project, "下载确认", "Download Confirmation"), JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE);
        
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        
        if (downloadButton != null) {
            downloadButton.setEnabled(false);
            downloadButton.setText(I18nUtils.getText(project, "下载中...", "Downloading..."));
        }
        
        new Thread(() -> {
            try {
                String jsonContent = gitHubService.downloadMappingFile(selectedFile.path);
                if (jsonContent != null) {
                    GitHubMappingLibrary library = gitHubService.parseMappingFile(jsonContent);
                    String fileName = selectedFile.displayName;
                    
                    String formattedJsonContent = formatJsonContent(jsonContent);
                    
                    boolean success = saveToLocalMappings(fileName, formattedJsonContent);
                    
                    SwingUtilities.invokeLater(() -> {
                        if (downloadButton != null) {
                            downloadButton.setEnabled(true);
                            downloadButton.setText("下载到本地");
                        }
                        
                        if (success) {
                            // 刷新项目视图以显示新下载的文件
                            vfsRefreshService.refreshMappingsDirectory();
                            
                            JOptionPane.showMessageDialog(getContentPane(), 
                                I18nUtils.getText(project,
                                    "下载成功！\n\n" +
                                    "文件已保存到: .td-maps/" + fileName + "\n",
                                    "Download successful!\n\n" +
                                    "File saved to: .td-maps/" + fileName + "\n"), 
                                I18nUtils.getText(project, "下载成功！", "Download Successful!"), JOptionPane.PLAIN_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(getContentPane(), 
                                I18nUtils.getText(project, "下载失败！\n\n", "Download failed!\n\n"), 
                                I18nUtils.getText(project, "下载失败", "Download Failed"), JOptionPane.PLAIN_MESSAGE);
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        if (downloadButton != null) {
                            downloadButton.setEnabled(true);
                            downloadButton.setText("下载到本地");
                        }
                        JOptionPane.showMessageDialog(getContentPane(), 
                            I18nUtils.getText(project,
                                "下载失败！\n\n" +
                                "无法获取文件内容，请检查网络连接",
                                "Download failed!\n\n" +
                                "Unable to get file content, please check network connection"), 
                            I18nUtils.getText(project, "下载失败", "Download Failed"), JOptionPane.PLAIN_MESSAGE);
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (downloadButton != null) {
                        downloadButton.setEnabled(true);
                        downloadButton.setText("下载到本地");
                    }
                                            JOptionPane.showMessageDialog(getContentPane(), 
                            I18nUtils.getText(project,
                                "下载失败！\n\n" +
                                "错误信息: " + e.getMessage(),
                                "Download failed!\n\n" +
                                "Error message: " + e.getMessage()), 
                            I18nUtils.getText(project, "下载失败", "Download Failed"), JOptionPane.PLAIN_MESSAGE);
                });
            }
        }).start();
    }
    
    /**
     * 保存映射库到本地.td-maps文件夹
     */
    private boolean saveToLocalMappings(String fileName, String jsonContent) {
        try {
            if (project == null || project.getBasePath() == null) {
                return false;
            }
            
            java.io.File mappingsDir = new java.io.File(project.getBasePath(), ".td-maps");
            if (!mappingsDir.exists()) {
                if (!mappingsDir.mkdirs()) {
                    return false;
                }
            }
            
            java.io.File targetFile = new java.io.File(mappingsDir, fileName);
            try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(targetFile), java.nio.charset.StandardCharsets.UTF_8)) {
                writer.write(jsonContent);
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 格式化JSON内容，使其更易读
     */
    private String formatJsonContent(String jsonContent) {
        try {
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                .setPrettyPrinting()
                .create();
            
            Object jsonObject = gson.fromJson(jsonContent, Object.class);
            
            return gson.toJson(jsonObject);
            
        } catch (Exception e) {
            return jsonContent;
        }
    }

    /**
     * 打开GitHub主页
     */
    private void openGitHubPage() {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com/JaysonX-Tech/tree-description-repository"));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(getContentPane(), "无法打开GitHub链接: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
}
