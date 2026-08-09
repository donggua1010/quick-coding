package com.donggua.plugin;

import java.util.Locale;

/**
 * 命名工具类：用于把数据库字段名、表名等转换为 Java 命名风格。
 *
 * <p>数据库中的命名通常是下划线分隔（snake_case），例如 {@code user_name}、{@code t_order_info}，
 * 而 Java 代码更习惯使用驼峰命名（camelCase / PascalCase）。本类集中提供两种最常用的转换：
 * <ul>
 *   <li>PascalCase（大驼峰）：{@code user_name} -&gt; {@code UserName}，常用于类名（实体名）；</li>
 *   <li>camelCase（小驼峰）：{@code user_name} -&gt; {@code userName}，常用于变量名、字段名。</li>
 * </ul>
 *
 * 该类为纯函数工具类，无状态、可安全地被多处并发调用。
 */
public final class Naming {

    /** 工具类不允许实例化。 */
    private Naming() {
    }

    /**
     * 把输入串转换为 PascalCase（首字母大写驼峰）格式。
     *
     * 规则：
     * <ol>
     *   <li>按 {@code _} 下划线拆分；</li>
     *   <li>对每一段调用 {@link #capitalize(String)}（首字母大写、其余字母视情况小写）；</li>
     *   <li>把所有段拼接成一个单词。</li>
     * </ol>
     *
     * @param input 原始名称，例如 {@code user_name}
     * @return 转换结果，例如 {@code UserName}；输入为 null 或空时返回空串
     */
    public static String toPascal(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String[] parts = input.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append(capitalize(part));
        }
        return sb.toString();
    }

    /**
     * 将输入串转换为 camelCase（小驼峰）格式。
     *
     * <p>实现上是先把字符串转成 {@link #toPascal(String)}，再把结果的首字符小写。
     * 例如 {@code user_name} -&gt; {@code UserName} -&gt; {@code userName}。
     *
     * @param input 原始名称，例如 {@code user_name}
     * @return 小驼峰结果，例如 {@code userName}；输入为 null 或空时返回空串
     */
    public static String toCamel(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        // 先整体转 PascalCase，再把第一个字符转为小写即得到小驼峰
        String pampered = toPascal(input);
        return Character.toLowerCase(pampered.charAt(0)) + pampered.substring(1);
    }

    /**
     * 把单个单词段首字母大写，同时处理全大写缩写（all-caps）的情况。
     *
     * <p>例子：
     * <ul>
     *   <li>{@code name} -&gt; {@code Name}</li>
     *   <li>{@code id} -&gt; {@code Id}</li>
     *   <li>{@code ID} -&gt; {@code Id}（全大写缩写统一转成首字母大写 + 其余小写，避免生成 {@code ID} 之类的字段名）</li>
     * </ul>
     *
     * @param part 被下划线分隔后的单个词段（可为空）
     * @return 规范化后的单词；空段返回空串
     */
    private static String capitalize(String part) {
        if (part == null || part.isEmpty()) {
            return "";
        }
        String first = part.substring(0, 1).toUpperCase(Locale.ROOT);
        String rest = part.substring(1);
        // 如果其余部分是纯大写（缩写形式，如 "ID"、"URL"），统一转成小写，
        // 保证 "ID" -> "Id" 而不是 "ID"，避免出现 ALL_CAPS 字段名
        if (!rest.isEmpty() && rest.equals(rest.toUpperCase(Locale.ROOT))) {
            rest = rest.toLowerCase(Locale.ROOT);
        }
        return first + rest;
    }
}