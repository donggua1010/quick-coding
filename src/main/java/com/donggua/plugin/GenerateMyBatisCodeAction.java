package com.donggua.plugin;

import com.intellij.database.psi.DbTable;
import com.intellij.database.view.DatabaseContextFun;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 右键一张或多张数据库表时触发的“Generate Code”动作。
 *
 * <p>整体流程：
 * <ol>
 *   <li>从事件上下文收集选中的 {@link DbTable}（数据库视图或 PSI 元素）；</li>
 *   <li>弹出 {@link GenerateCodeDialog} 让用户配置生成选项；</li>
 *   <li>用 {@link MyBatisCodeGenerator} 渲染所有选中的文件内容；</li>
 *   <li>把内容以 UTF-8 写盘（代码文件写代码目录、XML 写资源目录）；</li>
 *   <li>刷新 VFS，可选地把新文件加入版本控制（VCS）。</li>
 * </ol>
 *
 * <p>注意：所有文件操作（写盘）在非 EDT 的普通线程内执行没问题；
 * 但“加入版本控制”需要显示确认对话框，因此放到后台任务里执行
 * （详见 {@link #addToVcs(Project, java.util.List)}）。
 */
public class GenerateMyBatisCodeAction extends AnAction {

    /** 日志记录器（用于排查 VCS 等后台操作的失败原因）。 */
    private static final Logger LOG = Logger.getInstance(GenerateMyBatisCodeAction.class);

    /** 动作更新线程：EDT（访问 PSI/数据库视图需在 EDT）。 */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    /**
     * 只有当当前上下文里选中了数据库表时才启用该动作。
     *
     * @param e 事件（携带数据上下文）
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(!selectedTables(e).isEmpty());
    }

    /**
     * 执行生成流程。
     *
     * @param e 动作事件
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        List<DbTable> tables = selectedTables(e);
        if (tables.isEmpty() || project == null || project.isDisposed()) {
            return;
        }

        // 弹出配置对话框；用户取消则不做任何事
        GenerateCodeDialog dialog = new GenerateCodeDialog(project, tables);
        if (!dialog.showAndGet()) {
            return;
        }
        List<CodeGenConfig> configs = dialog.configs();
        // 表没有任何列 -> 无法生成
        if (configs.isEmpty() || !configs.get(0).hasColumns()) {
            notify(project, "表没有可用的字段信息", NotificationType.WARNING);
            return;
        }

        // 渲染所有模板（内存中完成，尚未写盘）
        List<MyBatisCodeGenerator.OutputFile> outputs = new ArrayList<>();
        try {
            for (CodeGenConfig cfg : configs) {
                outputs.addAll(new MyBatisCodeGenerator(cfg).generate());
            }
        } catch (RuntimeException ex) {
            Throwable cause = ex.getCause();
            notify(project, "模板渲染失败: " + (cause != null ? cause.getMessage() : ex.getMessage()),
                    NotificationType.ERROR);
            return;
        }

        // 写盘：代码文件 -> codeDir；资源文件（XML）-> resDir
        int written = 0;
        List<java.io.File> writtenFiles = new ArrayList<>();
        try {
            Path codeBase = Path.of(configs.get(0).codeDir);
            Path resBase = Path.of(configs.get(0).resDir);
            for (MyBatisCodeGenerator.OutputFile out : outputs) {
                boolean isCode = "CODE".equals(out.kind);
                Path base = isCode ? codeBase : resBase;
                Path target = base.resolve(out.path);
                Files.createDirectories(target.getParent());
                // UTF-8 带 BOM 写出：让中文注释能在 GBK 默认编码的系统上被正确识别为 UTF-8
                byte[] body = out.content.getBytes(StandardCharsets.UTF_8);
                byte[] withBom = new byte[body.length + 3];
                withBom[0] = (byte) 0xEF;
                withBom[1] = (byte) 0xBB;
                withBom[2] = (byte) 0xBF;
                System.arraycopy(body, 0, withBom, 3, body.length);
                Files.write(target, withBom);
                written++;
                writtenFiles.add(target.toFile());
            }
        } catch (IOException ex) {
            notify(project, "生成失败: " + ex.getMessage(), NotificationType.ERROR);
            return;
        }

        // 刷新 VFS，让 IDE 感知新建文件
        LocalFileSystem.getInstance().refresh(false);
        // 可选：把新文件加入版本控制
        if (configs.get(0).addToVcs) {
            addToVcs(project, writtenFiles);
        }
        notify(project, "成功生成 " + written + " 个文件", NotificationType.INFORMATION);
    }

    /**
     * 调用 IDE 自身的 VCS 机制把生成的文件加入版本控制（Git/SVN 等通用）。
     *
     * <p>由于 {@code com.intellij.vcsUtil.VcsFileUtil} 属于内部实现类（不在编译 classpath），
     * 这里使用反射调用其 {@code addFilesToVcsWithConfirmation(Project, Collection)}。
     * 该 API 会在确认对话框中让用户选择，然后按 VCS 类型执行添加操作。
     *
     * <p>流程：
     * <ol>
     *   <li>刷新并找到每个文件的 {@link VirtualFile}；</li>
     *   <li>探测这些文件是否位于某个已启用的 VCS 下（否则跳过，避免误导）；</li>
     *   <li>在后台线程（Backgroundable Task）中调用 VCS API——
     *       该方法内部会 {@code invokeAndWait} 显示确认对话框，不允许在 EDT 上直接调用。</li>
     * </ol>
     *
     * @param project 当前项目
     * @param files   生成的文件
     */
    private static void addToVcs(Project project, List<java.io.File> files) {
        if (files.isEmpty() || project == null || project.isDisposed()) {
            return;
        }
        // 1. 取得每个生成文件的 VirtualFile 表示
        List<VirtualFile> vfs = new ArrayList<>();
        LocalFileSystem lfs = LocalFileSystem.getInstance();
        for (java.io.File f : files) {
            VirtualFile vf = lfs.refreshAndFindFileByIoFile(f);
            if (vf != null) {
                vfs.add(vf);
            }
        }
        if (vfs.isEmpty()) {
            LOG.warn("addToVcs: no VirtualFile for generated files");
            return;
        }

        // 2. 探测 VCS 覆盖范围：若文件不在任何已启用 VCS 下则跳过
        try {
            Class<?> pvManagerClass = Class.forName("com.intellij.openapi.vcs.ProjectLevelVcsManager");
            Object pvManager = pvManagerClass.getMethod("getInstance", Project.class).invoke(null, project);
            int vcsFiles = 0;
            for (VirtualFile vf : vfs) {
                if (pvManagerClass.getMethod("getVcsFor", VirtualFile.class).invoke(pvManager, vf) != null) {
                    vcsFiles++;
                }
            }
            if (vcsFiles == 0) {
                notify(project, "未检测到生成目录所在的版本控制，已跳过加入版本控制", NotificationType.INFORMATION);
                return;
            }
        } catch (Throwable ex) {
            LOG.warn("addToVcs probe failed: " + ex, ex);
        }

        // 3. 在后台线程执行 VCS 添加（避免 EDT 阻塞 + invokeAndWait 限制）
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Adding files to VCS", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    Class<?> util = Class.forName("com.intellij.vcsUtil.VcsFileUtil");
                    // 让 VCS 意识到这些新文件（标记为“脏”/本地面刷新）
                    try {
                        util.getMethod("markFilesDirty", Project.class, java.util.Collection.class)
                                .invoke(null, project, vfs);
                    } catch (Exception ignored) {
                        // markFilesDirty 失败可忽略，addFilesToVcs 仍有重试机制
                    }
                    // 弹确认对话框并把文件加入 VCS
                    util.getMethod("addFilesToVcsWithConfirmation", Project.class, Collection.class)
                            .invoke(null, project, vfs);
                    GenerateMyBatisCodeAction.notify(project, "已把生成的文件加入版本控制", NotificationType.INFORMATION);
                } catch (Throwable ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    LOG.warn("addToVcs failed: " + cause, cause);
                    GenerateMyBatisCodeAction.notify(project, "加入版本控制失败: " + cause.getMessage(),
                            NotificationType.WARNING);
                }
            }
        });
    }

    /**
     * 从动作事件的数据上下文里提取选中的数据库表列表。
     *
     * <p>优先使用数据库视图上下文（可能一次选中多张表）；若为空，
     * 再回退到 PSI 元素（如数据库工具窗口的单表选中）。
     *
     * @param e 动作事件
     * @return 排好序（按表名）的表列表
     */
    private static List<DbTable> selectedTables(@NotNull AnActionEvent e) {
        List<DbTable> tables = new ArrayList<>();
        if (e.getDataContext() != null) {
            DatabaseContextFun.getSelectedDbElements(e.getDataContext(), DbTable.class)
                    .filter(t -> t != null)
                    .forEach(tables::add);
        }
        if (tables.isEmpty()) {
            PsiElement element = e.getData(LangDataKeys.PSI_ELEMENT);
            if (element instanceof DbTable t) {
                tables.add(t);
            }
        }
        tables.sort(Comparator.comparing(DbTable::getName));
        return tables;
    }

    /**
     * 发送一条通知（弹气球）。
     *
     * @param project 项目（决定通知所属项目）
     * @param message 消息内容
     * @param type    通知级别（信息/警告/错误）
     */
    private static void notify(Project project, String message, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("TableFields")
                .createNotification(message, type)
                .notify(project);
    }
}