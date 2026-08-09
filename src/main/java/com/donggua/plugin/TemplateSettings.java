package com.donggua.plugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 模板组的应用级持久化状态（application-level PersistentStateComponent）。
 *
 * <p>模板分为两类：
 * <ul>
 *   <li><b>内置默认模板组</b>（{@link #DEFAULT_GROUP}）：打包在插件 jar 里，只读，不允许修改。</li>
 *   <li><b>用户自定义模板组</b>：在 IDE 设置界面（{@link TemplateSettingsUI}）中创建/编辑，
 *       以 XML（{@code tableFieldsTemplates.xml}）形式持久化到 IDE 的配置目录。</li>
 * </ul>
 *
 * <p>存储结构（{@link State}）：
 * <pre>
 *   activeGroup: 当前选中的模板组名
 *   groups:       组名 -&gt; {模板文件名 -&gt; 模板文本}
 * </pre>
 */
@State(name = "TableFieldsTemplates", storages = @Storage("tableFieldsTemplates.xml"))
public final class TemplateSettings implements PersistentStateComponent<TemplateSettings.State> {

    /** 生成器用到的全部模板文件名。 */
    public static final String[] TEMPLATE_NAMES = {
            "entity.ftl", "mapper.ftl", "mapper.xml.ftl",
            "service.ftl", "serviceImpl.ftl", "controller.ftl"
    };

    /** 内置只读默认模板组的显示名称。 */
    public static final String DEFAULT_GROUP = "默认模板";

    /** 持久化状态。 */
    public static final class State {
        /** 当前选中的模板组。 */
        public String activeGroup = DEFAULT_GROUP;
        /** 组名 -&gt; （模板文件名 -&gt; 模板文本）。 */
        public Map<String, Map<String, String>> groups = new HashMap<>();
    }

    private State state = new State();

    /**
     * 获取全局唯一的 TemplateSettings 实例（IntelliJ Service）。
     */
    public static TemplateSettings getInstance() {
        return ApplicationManager.getApplication().getService(TemplateSettings.class);
    }

    /** 返回持久化状态对象（IntelliJ 会序列化它）。 */
    @Override
    public @Nullable State getState() {
        return state;
    }

    /** 从磁盘状态还原（IntelliJ 在启动时调用）。 */
    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    /**
     * 当前选中的模板组名（可能为空，此时回退到默认组）。
     *
     * @return 活动模板组名，保证非空
     */
    @NotNull
    public String getActiveGroup() {
        String g = state.activeGroup;
        return g == null || g.isEmpty() ? DEFAULT_GROUP : g;
    }

    /**
     * 返回某个自定义模板组的所有模板；组不存在时返回 null。
     *
     * @param name 模板组名
     * @return 模板名 -&gt; 文本 的映射，或 null
     */
    @Nullable
    public Map<String, String> getGroup(String name) {
        return state.groups.get(name);
    }

    /**
     * 全部自定义模板组（只读视角，若要变更请使用 {@link #replaceGroups}）。
     *
     * @return 组名 -&gt; 模板映射
     */
    public Map<String, Map<String, String>> getGroups() {
        return state.groups;
    }

    /**
     * 用新的模板组集合整体替换当前状态，并同时设置活动模板组。
     *
     * <p>在设置界面点击“Apply”时调用，把用户编辑的自定义组一次性写回。
     *
     * @param groups      新的自定义模板组集合
     * @param activeGroup 新的活动模板组名（null 或空回退默认组）
     */
    public void replaceGroups(Map<String, Map<String, String>> groups, String activeGroup) {
        State s = new State();
        s.groups.putAll(groups);
        s.activeGroup = activeGroup == null ? DEFAULT_GROUP : activeGroup;
        this.state = s;
    }
}