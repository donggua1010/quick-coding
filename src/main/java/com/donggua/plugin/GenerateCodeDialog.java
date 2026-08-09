package com.donggua.plugin;

import com.intellij.database.psi.DbTable;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * “Generate Code”对话框：收集一次代码生成所需的全部选项。
 *
 * <p>展示的信息/控件：
 * <ul>
 *   <li>模板组下拉框（用于选择使用的模板组）；</li>
 *   <li>表前缀输入框（可移除的表名前缀，如 {@code t_}，用于推导实体名）；</li>
 *   <li>实体名输入框（随前缀联动，可手动覆盖）；</li>
 *   <li>代码目录 / 资源目录（带浏览按钮的文件选择器）；</li>
 *   <li>一组“是否生成”复选框（entity/mapper/XML/service/controller）；</li>
 *   <li>附加选项（Swagger 注解、SQL 携带 schema、生成后加入版本控制）。</li>
 * </ul>
 *
 * <p>用户点击 OK 后可通过 {@link #configs()} 获取每张选中表对应的
 * {@link CodeGenConfig}。
 */
public class GenerateCodeDialog extends DialogWrapper {

    /** 实体名输入框。 */
    private final JTextField entityField = new JTextField();
    /** 表名前缀输入框。 */
    private final JTextField prefixField = new JTextField();
    /** 项目模块下拉框。 */
    private final JComboBox<String> moduleBox = new JComboBox<>();
    /** 模板组下拉框。 */
    private final JComboBox<String> templateGroupBox = new JComboBox<>();
    /** 代码目录（带浏览）。 */
    private final TextFieldWithBrowseButton codeDir;
    /** 资源目录（带浏览）。 */
    private final TextFieldWithBrowseButton resDir;
    /** 是否生成 entity。 */
    private final JCheckBox chkEntity = new JCheckBox("生成 entity", true);
    /** 是否生成 mapper 接口。 */
    private final JCheckBox chkMapper = new JCheckBox("生成 mapper 接口", true);
    /** 是否生成 mapper XML。 */
    private final JCheckBox chkXml = new JCheckBox("生成 mapper XML", true);
    /** 是否生成 service。 */
    private final JCheckBox chkService = new JCheckBox("生成 service", true);
    /** 是否生成 controller。 */
    private final JCheckBox chkController = new JCheckBox("生成 controller", true);
    /** 是否附加 Swagger 注解。 */
    private final JCheckBox chkSwagger = new JCheckBox("生成 Swagger 注解");
    /** SQL 中是否包含 schema 前缀。 */
    private final JCheckBox chkSchema = new JCheckBox("SQL 包含 schema");
    /** 生成后是否加入版本控制。 */
    private final JCheckBox chkVcs = new JCheckBox("生成后加入版本控制");

    /** 当前项目。 */
    private final Project project;
    /** 本次要生成代码的所有选中表。 */
    private final List<DbTable> tables;
    /** 项目里的全部模块（与 moduleBox 顺序一致）。 */
    private final List<com.intellij.openapi.module.Module> modules = new ArrayList<>();
    /** 是否正在程序化更新模块下拉框（避免在选择时重复刷新目录）。 */
    private boolean updatingModuleBox;

    /**
     * 构造对话框。
     *
     * @param project 当前项目
     * @param tables  选中的数据库表（至少一张）
     */
    public GenerateCodeDialog(Project project, List<DbTable> tables) {
        super(project, true); // modal 对话框
        this.project = project;
        this.tables = tables;
        DbTable first = tables.get(0);
        setTitle("Generate Code - " + first.getName() + (tables.size() > 1 ? " (+" + (tables.size() - 1) + " more)" : ""));
        // 默认前缀与实体名（用第一张表推导）
        prefixField.setText("t_");
        entityField.setText(entityForTable("t_"));
        // 监听前缀输入变化，实时刷新实体名
        prefixField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                refreshEntityName();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                refreshEntityName();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                refreshEntityName();
            }
        });

        // 目录选择控件初始化
        codeDir = new TextFieldWithBrowseButton();
        resDir = new TextFieldWithBrowseButton();

        // 模块下拉框：列出项目全部模块（按名称排序），切换时自动更新源码/资源目录
        List<com.intellij.openapi.module.Module> allModules = new ArrayList<>(
                java.util.Arrays.asList(
                        com.intellij.openapi.module.ModuleManager.getInstance(project).getModules()));
        allModules.sort(Comparator.comparing(com.intellij.openapi.module.Module::getName));
        for (com.intellij.openapi.module.Module m : allModules) {
            modules.add(m);
            moduleBox.addItem(m.getName());
        }
        if (modules.isEmpty()) {
            modules.add(null);
            moduleBox.addItem("");
        }
        moduleBox.addActionListener(e -> {
            if (updatingModuleBox) {
                return;
            }
            refreshDirsForModule();
        });
        // 初始默认目录：优先取第一个模块
        updatingModuleBox = true;
        moduleBox.setSelectedIndex(0);
        updatingModuleBox = false;
        configureDirField(codeDir, defaultCodeDir());
        configureDirField(resDir, defaultResDir());

        // 模板组下拉框：默认组 + 全部自定义组
        templateGroupBox.addItem(TemplateSettings.DEFAULT_GROUP);
        for (String g : TemplateSettings.getInstance().getGroups().keySet()) {
            templateGroupBox.addItem(g);
        }
        templateGroupBox.setSelectedItem(TemplateSettings.getInstance().getActiveGroup());
        templateGroupBox.setPreferredSize(new java.awt.Dimension(360, 28));

        init();
    }

    /** 根据当前前缀刷新实体名输入框。 */
    private void refreshEntityName() {
        entityField.setText(entityForTable(prefixField.getText()));
    }

    /** 使用第一张表 + 给定前缀推导实体名。 */
    private String entityForTable(String prefix) {
        return entityForTable(tables.get(0), prefix);
    }

    /**
     * 表名去掉前缀并转 PascalCase 作为实体名。
     *
     * @param t      数据库表
     * @param prefix 可移除的前缀（如 "t_"）
     * @return 实体名，例如 "t_user" + "t_" -> "User"
     */
    private static String entityForTable(DbTable t, String prefix) {
        String name = t.getName();
        if (prefix != null && !prefix.isEmpty() && name.startsWith(prefix)) {
            name = name.substring(prefix.length());
        }
        return Naming.toPascal(name);
    }

    /**
     * 配置一个目录选择框：设置默认文本并绑定浏览文件夹的监听器。
     *
     * @param field 目录输入框
     * @param def   默认目录
     */
    private static void configureDirField(TextFieldWithBrowseButton field, String def) {
        field.setText(def);
        field.addBrowseFolderListener("选择目录", "", null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor().withShowFileSystemRoots(true));
    }

    /**
     * 计算默认代码目录：优先主源码根（src/main/java），否则任意源码根。
     *
     * @return 目录路径字符串；当前模块无源码根时返回空串
     */
    private String defaultCodeDir() {
        List<VirtualFile> roots = collectRoots(true);
        return roots.isEmpty() ? "" : pickPreferred(roots);
    }

    /**
     * 计算默认资源目录：优先主资源根（src/main/resources），否则任意资源根。
     *
     * @return 目录路径字符串；当前模块无资源根时返回空串
     */
    private String defaultResDir() {
        List<VirtualFile> roots = collectRoots(false);
        return roots.isEmpty() ? "" : pickPreferred(roots);
    }

    /**
     * 切换模块时刷新源码/资源目录输入框（依据所选模块的源码根/资源根）。
     */
    private void refreshDirsForModule() {
        codeDir.setText(defaultCodeDir());
        resDir.setText(defaultResDir());
    }

    /**
     * 当前下拉框选中的模块。
     *
     * @return 选中的模块；无模块或未选择时返回 null
     */
    private Module selectedModule() {
        int idx = moduleBox.getSelectedIndex();
        if (idx < 0 || idx >= modules.size()) {
            return null;
        }
        return modules.get(idx);
    }

    /**
     * 收集当前所选模块的源码根（java=true）或资源根（java=false）。
     *
     * @param java true 表示收集 Java 源码根；false 表示资源根
     * @return 目录列表
     */
    private List<VirtualFile> collectRoots(boolean java) {
        Module module = selectedModule();
        if (module == null) {
            return new ArrayList<>();
        }
        List<VirtualFile> roots = new ArrayList<>();
        for (VirtualFile file : ModuleRootManager.getInstance(module).getSourceRoots(false)) {
            if (file == null || !file.isDirectory()) {
                continue;
            }
            boolean isResource = isResourceFolder(file);
            if (java && !isResource) {            // 源码根（非资源目录）
                roots.add(file);
            } else if (!java && isResource) {     // 资源根
                roots.add(file);
            }
        }
        return roots;
    }

    /**
     * 判断目录是否为资源目录：路径中包含 "resources" 视为资源根。
     *
     * @param file 目录
     * @return true 表示资源目录
     */
    private static boolean isResourceFolder(VirtualFile file) {
        String path = file.getPath().toLowerCase();
        return path.contains("resources");
    }

    /**
     * 从多个根目录中选择“main”目录（如 src/test 优先排除）。
     *
     * @param roots 候选目录
     * @return 最合适的目录路径
     */
    private static String pickPreferred(List<VirtualFile> roots) {
        VirtualFile best = null;
        for (VirtualFile root : roots) {
            String p = root.getPath().toLowerCase();
            if (p.contains("/test/")) {
                continue;
            }
            // 优先包含 "main" 的目录（src/main），否则保留首个
            if (best == null || p.contains("main")) {
                best = root;
            }
        }
        if (best == null) {
            roots.sort(Comparator.comparing(VirtualFile::getPath));
            best = roots.get(0);
        }
        return best.getPath();
    }

    /**
     * 构建对话框中心面板（GridBagLayout 布局）。
     *
     * <p>上半部分为输入行（模板组/前缀/实体名/目录），下半部分为复选框组。
     *
     * @return 中心面板
     */
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new java.awt.Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        g.weightx = 0;
        g.gridy = 0;

        addRow(panel, g, "模板组:", templateGroupBox, 1.0);
        addRow(panel, g, "模块:", moduleBox, 1.0);
        addRow(panel, g, "前缀(可移除):", prefixField, 1.0);
        addRow(panel, g, "实体名:", entityField, 1.0);
        addRow(panel, g, "代码目录(包路径):", codeDir, 1.0);
        addRow(panel, g, "资源目录:", resDir, 1.0);

        // “生成的模块”标签独占一行，复选框另起一行（避免与标签重叠）
        g.gridx = 1;
        g.weightx = 0;
        panel.add(new JLabel("生成的模块:"), g);
        g.weightx = 1.0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridy++;
        panel.add(chkEntity, g.clone());
        g.gridy++;
        panel.add(chkMapper, g.clone());
        g.gridy++;
        panel.add(chkXml, g.clone());
        g.gridy++;
        panel.add(chkService, g.clone());
        g.gridy++;
        panel.add(chkController, g.clone());
        g.gridy++;
        panel.add(chkSwagger, g.clone());
        g.gridy++;
        panel.add(chkSchema, g.clone());
        g.gridy++;
        panel.add(chkVcs, g.clone());

        return panel;
    }

    /**
     * 在对话框中添加一行标准输入组件。
     *
     * @param panel 目标面板
     * @param g     布局约束（会在内部递增 gridy）
     * @param label 行的标签文字
     * @param field 行的输入组件
     * @param wx    权重
     */
    private static void addRow(JPanel panel, GridBagConstraints g, String label, JComponent field, double wx) {
        g.gridx = 0;
        g.weightx = 0;
        g.anchor = GridBagConstraints.EAST;
        g.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), g);
        field.setPreferredSize(new java.awt.Dimension(360, 28));
        g.gridx = 1;
        g.weightx = 1.0;
        g.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, g);
        g.gridy++;
    }

    /**
     * OK 按钮点击：校验实体名与包路径合法后关闭对话框。
     */
    @Override
    protected void doOKAction() {
        if (entityField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(getContentPanel(), "实体名不能为空");
            return;
        }
        if (derivePackage(codeDir.getText()).trim().isEmpty()) {
            JOptionPane.showMessageDialog(getContentPanel(), "代码目录需要位于 Sources(源码根) 目录下以推导包名");
            return;
        }
        super.doOKAction();
    }

    /**
     * 读取当前所有选项，为每一张表生成一个 {@link CodeGenConfig}。
     *
     * <p>调用时机：对话框关闭后（且用户点击了 OK）。
     *
     * @return 与选中表数量一致的配置列表
     */
    public List<CodeGenConfig> configs() {
        Object group = templateGroupBox.getSelectedItem();
        String selectedGroup = group == null ? TemplateSettings.DEFAULT_GROUP : group.toString();
        List<CodeGenConfig> list = new ArrayList<>();
        for (DbTable t : tables) {
            list.add(new CodeGenConfig(derivePackage(codeDir.getText()), entityForTable(t, prefixField.getText()), t,
                    codeDir.getText(), resDir.getText(), selectedGroup,
                    chkEntity.isSelected(), chkMapper.isSelected(), chkXml.isSelected(),
                    chkService.isSelected(), chkController.isSelected(),
                    chkSwagger.isSelected(), chkSchema.isSelected(), chkVcs.isSelected()));
        }
        return list;
    }

    /**
     * 根据选中的代码目录推导 Java 包名。
     *
     * <p>规则：该目录必须是某个模块的源码根或其子目录；所得相对路径以 {@code .}
     * 作包分隔符。若目录不在任何源码根下，返回空串（表示不能推导包名）。
     *
     * @param codeDir 代码目录
     * @return 包名，例如 {@code com.example.user}
     */
    private String derivePackage(String codeDir) {
        if (codeDir == null || codeDir.isEmpty()) {
            return "";
        }
        VirtualFile selected = LocalFileSystem.getInstance().findFileByIoFile(new java.io.File(codeDir));
        if (selected == null) {
            return "";
        }
        String selectedPath = selected.getPath().replace('\\', '/');
        for (Module module : com.intellij.openapi.module.ModuleManager.getInstance(project).getModules()) {
            for (VirtualFile root : ModuleRootManager.getInstance(module).getSourceRoots(false)) {
                String rootPath = root.getPath().replace('\\', '/');
                // 目录就是源码根本身 -> 包名为空（默认包）
                if (selectedPath.equals(rootPath)) {
                    return "";
                }
                // 目录是源码根的子目录 -> 相对路径转包名
                if (selectedPath.startsWith(rootPath + "/")) {
                    String rel = selectedPath.substring(rootPath.length() + 1);
                    return rel.replace('/', '.').replace('\\', '.');
                }
            }
        }
        return "";
    }
}