package com.donggua.plugin;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * 编辑器右键菜单动作：把当前选中的文本转换为小驼峰（lower camelCase）。
 *
 * <p>用法示例（选中后右键 → “转换为小驼峰 (camelCase)”）：
 * <ul>
 *   <li>{@code user_name} -&gt; {@code userName}</li>
 *   <li>{@code user name} -&gt; {@code userName}</li>
 *   <li>{@code UserName} -&gt; {@code userName}</li>
 *   <li>多行选中时逐行转换，保留换行符</li>
 * </ul>
 *
 * <p>仅在编辑器存在且已选中非空文本时启用（见 {@link #update(AnActionEvent)}）。
 * 修改文档必须在 {@link WriteCommandAction} 中执行，以便支持撤销/重做。
 */
public class ToCamelCaseAction extends AnAction {

    /**
     * 更新线程：EDT（读取/修改编辑器必须在该线程）。
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    /**
     * 设置动作可见性：只有存在编辑器且选中了非空文本时才可用。
     *
     * @param e 动作事件（携带编辑器数据）
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        boolean enabled = editor != null && hasSelection(editor);
        e.getPresentation().setEnabledAndVisible(enabled);
    }

    /**
     * 当前编辑器是否选中了非空文本。
     *
     * @param editor 编辑器
     * @return true 表示有选中文本
     */
    private static boolean hasSelection(Editor editor) {
        SelectionModel selection = editor.getSelectionModel();
        return selection.hasSelection() && !selection.getSelectedText().isEmpty();
    }

    /**
     * 执行转换：读取选中文本，逐行转小驼峰，替换回文档。
     *
     * @param e 动作事件
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            return;
        }
        SelectionModel selection = editor.getSelectionModel();
        if (!selection.hasSelection()) {
            return;
        }
        int start = selection.getSelectionStart();
        int end = selection.getSelectionEnd();
        String original = selection.getSelectedText();
        if (original == null || original.isEmpty()) {
            return;
        }

        // 逐行转换，保留换行符，避免多行选中被合并成一行
        String[] lines = original.split("\n", -1);
        StringBuilder converted = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                converted.append("\n");
            }
            converted.append(toCamelCase(words(lines[i])));
        }
        final String text = converted.toString();

        // 在 EDT 中以写命令替换选中区域（可撤销）
        Project project = e.getProject();
        WriteCommandAction.runWriteCommandAction(project, () -> editor.getDocument().replaceString(start, end, text));
        selection.removeSelection();
    }

    /**
     * 把一行内的文本按非字母数字字符切分为单词段。
     *
     * @param line 一行文本
     * @return 单词数组
     */
    private static String[] words(String line) {
        return line.split("[^A-Za-z0-9]+", -1);
    }

    /**
     * 把单词序列拼成小驼峰：第一个单词首字符小写，其后每个单词首字符大写。
     *
     * <p>例如 {@code user}, {@code name} -&gt; {@code userName}。
     *
     * @param parts 单词数组
     * @return 小驼峰结果
     */
    private static String toCamelCase(String[] parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part == null || part.isEmpty()) {
                continue; // 跳过空段
            }
            String first = part.substring(0, 1).toUpperCase(Locale.ROOT);
            String rest = part.substring(1);
            if (i == 0) {
                // 第一个单词首字符小写（小驼峰的标志）
                sb.append(part.substring(0, 1).toLowerCase(Locale.ROOT)).append(rest);
            } else {
                // 后续单词首字符大写
                sb.append(first).append(rest);
            }
        }
        return sb.toString();
    }
}