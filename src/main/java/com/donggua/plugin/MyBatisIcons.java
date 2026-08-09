package com.donggua.plugin;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * 插件图标的集中定义。
 *
 * <p>所有 marker 或 UI 使用的 {@link Icon} 都在这里持有，避免在多处写魔法路径。
 * 图标资源位于 {@code src/main/resources/icons/} 下。
 */
public final class MyBatisIcons {

    /** MyBatis 官方小鸟图标（SVG，18x18），用于 mapper 方法/XML 语句的 gutter 导航。 */
    public static final Icon MAPPER = IconLoader.getIcon("/icons/mybatis.svg", MyBatisIcons.class);

    /** 工具类：不允许实例化。 */
    private MyBatisIcons() {
    }
}