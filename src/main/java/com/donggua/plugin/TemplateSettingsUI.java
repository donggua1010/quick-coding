package com.donggua.plugin;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 代码模板设置的设置页面（IDE Settings -&gt; Code Templates）。
 *
 * <p>页面布局：
 * <pre>
 *   +--------------------------------------------------------------+
 *   | 模板组: [下拉框] [+] [-]                                      |   topBar
 *   +---------------------------------+----------------------------+
 *   | 模板文件                         | 编辑器（.ftl 语法高亮）    |
 *   |   entity.ftl                     |                            |
 *   |   mapper.ftl                     |                            |
 *   |   ...                            |                            |
 *   +---------------------------------+----------------------------+
 * </pre>
 *
 * <p>功能要点：
 * <ul>
 *   <li>左侧列出当前模板组下的全部模板文件（固定六个文件）；</li>
 *   <li>右侧用 {@link TemplateEditorPane} 编辑选中的模板内容；</li>
 *   <li>默认模板组“只读”，自定义组可编辑，并支持新建/删除模板组；</li>
 *   <li>所有编辑先在内存缓冲区（{@link #buffers}）里进行，点击 Apply 才一次性写回
 *       {@link TemplateSettings}。</li>
 * </ul>
 */
public final class TemplateSettingsUI implements Configurable {

    /** 内存编辑缓冲：组名 -&gt; (模板文件名 -&gt; 内容)。点击 Apply 后写回持久化。 */
    private final Map<String, Map<String, String>> buffers = new HashMap<>();
    /** 显示名到组键的映射（当前实现二者相同）。 */
    private final Map<String, String> groupNames = new HashMap<>();
    /** 组下拉框中正在显示的名称。 */

    private JPanel panel;
    private JComboBox<String> groupCombo;
    private JBList<String> fileList;
    private TemplateEditorPane editor;
    private JButton deleteButton;
    /** 是否正在程序化地更新下拉框（避免事件循环触发 switchGroup 二次切换）。 */
    private boolean updatingCombo;

    /** 当前选中的组名。 */
    private String currentGroup;
    /** 当前选中的模板文件名。 */
    private String currentFile;

    /** 设置页显示名称。 */
    @Override
    public String getDisplayName() {
        return "Code Templates";
    }

    /**
     * 创建设置页的主组件。
     *
     * @return 页面根面板
     */
    @Override
    public @Nullable JComponent createComponent() {
        resetBuffers();

        // 模板组下拉框（数据项为组显示名）
        DefaultComboBoxModel<String> comboModel = new DefaultComboBoxModel<>();
        for (String key : groupNames.keySet()) {
            comboModel.addElement(key);
        }
        groupCombo = new JComboBox<String>(comboModel);
        groupCombo.setMaximumRowCount(20);
        groupCombo.setPreferredSize(new java.awt.Dimension(150, 26));

        // 模板文件列表（固定 6 个模板文件）
        fileList = new JBList<String>(TemplateSettings.TEMPLATE_NAMES);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setFixedCellWidth(150);

        // 模板内容编辑器（.ftl 高亮）
        editor = new TemplateEditorPane(com.intellij.openapi.project.ProjectManager.getInstance().getDefaultProject());
        editor.setPreferredSize(new java.awt.Dimension(520, 420));

        // 切换模板组
        groupCombo.addActionListener(e -> {
            if (updatingCombo) {
                return;
            }
            switchGroup((String) groupCombo.getSelectedItem());
        });
        // 切换选中的模板文件
        fileList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            switchFile(fileList.getSelectedValue());
        });

        // 新增/删除模板组按钮
        JButton addButton = iconButton(com.intellij.icons.AllIcons.General.Add,
                "新增模板组：创建一个可编辑的模板组（以默认模板为起点）");
        addButton.addActionListener(e -> addGroup());
        deleteButton = iconButton(com.intellij.icons.AllIcons.General.Remove,
                "删除当前选中的自定义模板组（默认模板组不可删除）");
        deleteButton.addActionListener(e -> deleteGroup());
        updateDeleteButton();
        size25(addButton);
        size25(deleteButton);

        // 顶部工具条: 模板组下拉 + 增删按钮
        JPanel topBar = new JPanel(new GridBagLayout());
        topBar.setPreferredSize(new Dimension(10, 32));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(2, 4, 2, 4);
        gc.gridy = 0;

        gc.gridx = 0;
        gc.weightx = 0;
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.NONE;
        JLabel comboLabel = new JLabel("模板组:");
        topBar.add(comboLabel, gc.clone());

        gc.gridx = 1;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        topBar.add(groupCombo, gc);

        gc.gridx = 2;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        gc.anchor = GridBagConstraints.WEST;
        topBar.add(addButton, gc);

        gc.gridx = 3;
        gc.weightx = 0;
        gc.anchor = GridBagConstraints.WEST;
        topBar.add(deleteButton, gc);

        // 尾部空白，占满剩余宽度
        gc.gridx = 4;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        topBar.add(new JPanel(), gc);

        // 左侧：模板文件列表
        JPanel filePanel = new JPanel(new BorderLayout());
        filePanel.add(new JLabel("模板文件"), BorderLayout.NORTH);
        filePanel.add(new JBScrollPane(fileList), BorderLayout.CENTER);

        // 右侧：模板内容编辑器
        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.add(editor, BorderLayout.CENTER);

        // 左右分隔条（文件列表占 28% 宽度）
        JSplitPane body = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, filePanel, editorPanel);
        body.setResizeWeight(0.28);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(topBar, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(920, 540));

        // 初始选中持久化的活动模板组
        updatingCombo = true;
        groupCombo.setSelectedItem(validGroup(TemplateSettings.getInstance().getActiveGroup()));
        updatingCombo = false;
        switchGroup((String) groupCombo.getSelectedItem());

        return panel;
    }

    /** 统一把按钮设为 25x25 小尺寸（图标按钮）。 */
    private static void size25(JButton b) {
        b.setPreferredSize(new Dimension(25, 25));
        b.setMinimumSize(new Dimension(25, 25));
        b.setMaximumSize(new Dimension(25, 25));
    }

    /**
     * 返回真实存在的模板组名；若传入的组不存在则回退到默认组。
     *
     * @param group 期望的组名
     * @return 有效组名
     */
    private String validGroup(String group) {
        if (group != null && groupNames.containsKey(group)) {
            return group;
        }
        return TemplateSettings.DEFAULT_GROUP;
    }

    // ------------------------------------------------------------------ switching
    // 切换逻辑：把当前组/文件的编辑内容先保存进缓冲区，再加载目标内容。

    /** 保存当前模板文件的内容到缓冲区（若存在当前文件）。 */
    private void saveCurrent() {
        if (currentGroup != null && currentFile != null) {
            buffers.computeIfAbsent(currentGroup, k -> new HashMap<>()).put(currentFile, editor.getText());
        }
    }

    /**
     * 切换到另一个模板组：保存当前编辑，加载该组第一个模板文件，刷新编辑状态与删除按钮。
     *
     * @param group 目标组名
     */
    private void switchGroup(String group) {
        if (group == null || group.equals(currentGroup)) {
            return;
        }
        saveCurrent();
        currentGroup = group;
        currentFile = null;
        fileList.setSelectedIndex(0);
        showFile(fileList.getSelectedValue());
        updateEditorState();
        updateDeleteButton();
    }

    /**
     * 切换到另一个模板文件：保存当前编辑，加载新文件内容。
     *
     * @param file 目标模板文件名
     */
    private void switchFile(String file) {
        if (file == null || file.equals(currentFile)) {
            return;
        }
        saveCurrent();
        currentFile = file;
        showFile(file);
    }

    /**
     * 把指定模板文件内容显示到编辑器；文件为 null 时清空编辑器。
     *
     * @param file 模板文件名
     */
    private void showFile(String file) {
        currentFile = file == null ? null : file;
        if (currentFile == null) {
            editor.setContent("");
            updateEditorState();
            return;
        }
        // 读取缓冲区中该组该文件的内容显示
        editor.setContent(buffers.get(currentGroup).get(currentFile));
        updateEditorState();
    }

    /** 根据当前组是否为默认组，更新编辑器的只读状态与 tooltip。 */
    private void updateEditorState() {
        boolean isDefault = TemplateSettings.DEFAULT_GROUP.equals(currentGroup);
        editor.setReadOnly(isDefault);
        editor.setToolTipText(isDefault ? "内置默认模板，只读" : null);
    }

    /** 判断当前组是否为内置默认模板组。 */
    private boolean isDefault() {
        return TemplateSettings.DEFAULT_GROUP.equals(currentGroup);
    }

    /**
     * 创建一个紧凑的无边框图标按钮并附加 tooltip。
     *
     * @param icon    按钮图标
     * @param tooltip 悬浮提示
     * @return 配置好的按钮
     */
    private static JButton iconButton(javax.swing.Icon icon, String tooltip) {
        JButton b = new JButton(icon);
        b.setToolTipText(tooltip);
        b.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        b.setContentAreaFilled(false);
        b.setFocusable(false);
        return b;
    }

    // ------------------------------------------------------------------ group management
    /**
     * 新增模板组：弹出输入框输入组名，创建以默认模板为初始内容的新组并切换过去。
     */
    @SuppressWarnings("unchecked")
    private void addGroup() {
        String name = JOptionPane.showInputDialog(panel, "模板组名称:");
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        name = name.trim();
        // 不允许与现有组或默认组重名
        if (groupNames.containsKey(name) || TemplateSettings.DEFAULT_GROUP.equals(name)) {
            JOptionPane.showMessageDialog(panel, "已存在同名模板组");
            return;
        }
        // 初始内容 = 内置默认模板
        Map<String, String> files = new HashMap<>();
        for (String t : TemplateSettings.TEMPLATE_NAMES) {
            files.put(t, TemplateRenderer.defaultTemplateText(t));
        }
        buffers.put(name, files);
        groupNames.put(name, name);
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) groupCombo.getModel();
        model.addElement(name);
        groupCombo.setSelectedItem(name);
        updateDeleteButton();
    }

    /**
     * 删除当前自定义模板组（默认组不可删除，需二次确认）。
     */
    private void deleteGroup() {
        if (currentGroup == null || isDefault()) {
            return;
        }
        // 二次确认
        if (JOptionPane.showConfirmDialog(panel, "确定删除模板组 \"" + currentGroup + "\"?",
                "删除模板组", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) groupCombo.getModel();
        model.removeElement(currentGroup);
        buffers.remove(currentGroup);
        groupNames.remove(currentGroup);
        // 删除后切回默认组
        model.setSelectedItem(TemplateSettings.DEFAULT_GROUP);
        updateDeleteButton();
    }

    /**
     * 更新删除按钮可用性：默认组不可删除。
     */
    private void updateDeleteButton() {
        if (deleteButton != null) {
            deleteButton.setEnabled(!isDefault());
        }
    }

    // ------------------------------------------------------------------ data load
    /**
     * 重置内存缓冲区：载入默认组 + 全部自定义组的模板内容。
     */
    private void resetBuffers() {
        buffers.clear();
        groupNames.clear();
        groupNames.put(TemplateSettings.DEFAULT_GROUP, TemplateSettings.DEFAULT_GROUP);
        // 默认组内容 = 内置模板
        Map<String, String> def = new HashMap<>();
        for (String t : TemplateSettings.TEMPLATE_NAMES) {
            def.put(t, currentText(TemplateSettings.DEFAULT_GROUP, t));
        }
        buffers.put(TemplateSettings.DEFAULT_GROUP, def);
        // 自定义组内容（缺某个文件时用默认模板补齐）
        for (Map.Entry<String, Map<String, String>> e : TemplateSettings.getInstance().getGroups().entrySet()) {
            String key = e.getKey();
            Map<String, String> files = new HashMap<>();
            for (String t : TemplateSettings.TEMPLATE_NAMES) {
                String stored = e.getValue().get(t);
                files.put(t, stored != null ? stored : currentText(TemplateSettings.DEFAULT_GROUP, t));
            }
            buffers.put(key, files);
            groupNames.put(key, key);
        }
        currentGroup = null;
        currentFile = null;
    }

    /**
     * 读取某组中某模板文件的当前内容；自定义组缺失时回退内置默认模板。
     *
     * @param group 模板组
     * @param name  模板文件名
     * @return 模板文本
     */
    private static String currentText(String group, String name) {
        if (!TemplateSettings.DEFAULT_GROUP.equals(group)) {
            Map<String, String> g = TemplateSettings.getInstance().getGroup(group);
            if (g != null && g.get(name) != null) {
                return g.get(name);
            }
        }
        return TemplateRenderer.defaultTemplateText(name);
    }

    /** 判断设置是否被修改（用于 IDE 决定是否显示“Apply”按钮）。 */
    @Override
    public boolean isModified() {
        saveCurrent();
        TemplateSettings settings = TemplateSettings.getInstance();
        // 活动模板组变化
        if (!settings.getActiveGroup().equals(currentGroup)) {
            return true;
        }
        // 逐个自定义组对比缓冲区与持久化
        Map<String, Map<String, String>> stored = settings.getGroups();
        for (Map.Entry<String, Map<String, String>> e : buffers.entrySet()) {
            String key = e.getKey();
            if (TemplateSettings.DEFAULT_GROUP.equals(key)) {
                continue;
            }
            Map<String, String> storedGroup = stored.get(key);
            if (storedGroup == null) {
                return true;
            }
            // 模板内容逐文件对比
            for (String t : TemplateSettings.TEMPLATE_NAMES) {
                if (!e.getValue().get(t).equals(storedGroup.getOrDefault(t, ""))) {
                    return true;
                }
            }
            // 文件数目不同
            if (storedGroup.size() != e.getValue().size()) {
                return true;
            }
        }
        // 自定义组数目不同（缓冲中比持久化的多/少）
        return stored.size() != (buffers.size() - 1);
    }

    /** 应用修改：把缓冲区写入持久化并切换活动模板组。 */
    @Override
    public void apply() {
        saveCurrent();
        Map<String, Map<String, String>> out = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> e : buffers.entrySet()) {
            if (TemplateSettings.DEFAULT_GROUP.equals(e.getKey())) {
                continue;
            }
            out.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        TemplateSettings.getInstance().replaceGroups(out, currentGroup);
        updateDeleteButton();
    }

    /**
     * 重置界面为持久化状态（丢弃未应用的编辑）。
     */
    @Override
    public void reset() {
        resetBuffers();
        if (groupCombo == null) {
            return;
        }
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) groupCombo.getModel();
        updatingCombo = true;
        model.removeAllElements();
        for (String key : groupNames.keySet()) {
            model.addElement(key);
        }
        updatingCombo = false;
        groupCombo.setSelectedItem(validGroup(TemplateSettings.getInstance().getActiveGroup()));
        switchGroup((String) groupCombo.getSelectedItem());
    }

    /**
     * 释放界面资源（编辑器等）。
     */
    @Override
    public void disposeUIResources() {
        buffers.clear();
        groupNames.clear();
        groupCombo = null;
        fileList = null;
        if (editor != null) {
            editor.dispose();
            editor = null;
        }
        currentGroup = null;
        currentFile = null;
    }
}