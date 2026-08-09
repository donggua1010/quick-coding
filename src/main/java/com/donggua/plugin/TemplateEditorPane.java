package com.donggua.plugin;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * 可嵌入的 IntelliJ 代码编辑器面板，专门用于编辑 FreeMarker 模板（.ftl）。
 *
 * <p>与直接在 JTextArea 里编辑相比，本组件复用了 IDE 的编辑器组件，因此可以获得
 * FreeMarker（或回退的纯文本）语法高亮、行号等体验。实现方式：
 * <ol>
 *   <li>用 {@link LightVirtualFile} 构造一个"内存中的 .ftl 文件"，从而让文件类型系统
 *       识别出 FreeMarker 文件类型；</li>
 *   <li>用 {@link EditorFactory} 为该文档创建编辑器组件并放入本面板；</li>
 *   <li>切换模板时先 {@link #release()} 释放旧编辑器再创建新编辑器，避免资源泄漏。</li>
 * </ol>
 */
final class TemplateEditorPane extends JPanel {

    /** 用于创建编辑器 / 执行写命令的 Project。 */
    private final Project project;
    /** 当前显示的编辑器实例（可能为 null）。 */
    private Editor editor;
    /** 是否处于只读（viewer）模式——内置默认模板组为只读。 */
    private boolean readOnly;

    /**
     * @param project 编辑器所属 Project（用于 WriteCommandAction）
     */
    TemplateEditorPane(Project project) {
        super(new BorderLayout());
        this.project = project;
    }

    /**
     * 读取当前编辑器的完整文本。
     *
     * @return 编辑器文本；未创建编辑器时返回空串
     */
    @NotNull
    String getText() {
        return editor == null ? "" : editor.getDocument().getText();
    }

    /**
     * 替换显示内容：释放旧编辑器，创建绑定到给定文本的新编辑器。
     *
     * <p>用 {@code LightVirtualFile} 让 IDE 按 .ftl 类型渲染语法高亮；
     * 文本写入文档需要在写命令（WriteCommandAction）中执行。
     *
     * @param content 新的模板文本
     */
    void setContent(String content) {
        release();
        // 内存虚拟文件：文件名 Template.ftl 会命中 FreeMarker 文件类型
        VirtualFile vf = new LightVirtualFile("Template.ftl",
                FileTypeManager.getInstance().getFileTypeByFileName("Template.ftl"), content);
        Document doc = EditorFactory.getInstance().createDocument(content);
        // 文档内容修改必须包裹在写命令里（IDE 的线程/事件模型要求）
        WriteCommandAction.runWriteCommandAction(project, () -> doc.setText(content));
        editor = EditorFactory.getInstance().createEditor(doc, project, vf, false);
        // 若当前应处于只读模式，则新编辑器也立即设为只读
        if (readOnly) {
            applyReadOnly();
        }
        removeAll();
        add(editor.getComponent(), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /**
     * 设置/取消只读模式（只读时编辑器变成 viewer，不可输入）。
     *
     * @param readOnly true 表示只读
     */
    void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        applyReadOnly();
    }

    /**
     * 把当前编辑器切换为 viewer（只读）或编辑器（可写）模式。
     */
    private void applyReadOnly() {
        if (editor instanceof EditorEx ex) {
            // EditorEx 提供 setViewer：true 时编辑器只读
            ex.setViewer(readOnly);
        }
    }

    /**
     * 释放当前编辑器实例并清空面板，防止编辑器资源泄漏。
     */
    private void release() {
        if (editor != null) {
            EditorFactory.getInstance().releaseEditor(editor);
            editor = null;
        }
        removeAll();
    }

    /**
     * 释放所有资源（在 dispose 或组件移除时调用）。
     */
    void dispose() {
        release();
    }

    /**
     * 面板从 UI 层级移除时自动释放编辑器资源。
     */
    @Override
    public void removeNotify() {
        dispose();
        super.removeNotify();
    }
}