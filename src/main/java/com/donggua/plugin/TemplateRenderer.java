package com.donggua.plugin;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.StringTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 封装 FreeMarker 模板渲染。
 *
 * <p>模板来源有两层，采用“自定义优先、内置兜底”的策略：
 * <ol>
 *   <li><b>用户自定义模板组</b>：存于 {@link TemplateSettings}（IDE 持久化 XML），
 *       通过 {@link StringTemplateLoader} 以字符串形式作为模板源；</li>
 *   <li><b>内置默认模板</b>：打包在插件 jar 的 {@code /templates} 目录下，通过
 *       {@link ClassTemplateLoader} 加载。</li>
 * </ol>
 * 两者通过 {@link MultiTemplateLoader} 组合，FreeMarker 会依次查找同名模板，
 * 因此用户自定义模板优先，找不到再回退到内置默认模板。
 */
final class TemplateRenderer {

    /** 工具类，不允许实例化。 */
    private TemplateRenderer() {
    }

    /**
     * 使用默认模板组渲染。
     *
     * @param templateName 模板文件名（不含目录），例如 {@code entity.ftl}
     * @param model        模板数据模型
     * @return 渲染结果
     */
    static String render(String templateName, Map<String, Object> model) {
        return render(templateName, model, null);
    }

    /**
     * 渲染指定模板。
     *
     * @param templateName 模板文件名
     * @param model        模板数据模型
     * @param group        模板组名；为 null 或默认组时使用内置模板
     * @return 渲染结果文本
     * @throws RuntimeException 模板不存在或渲染出错时抛出（包装 IOException/TemplateException）
     */
    static String render(String templateName, Map<String, Object> model, String group) {
        try {
            Template template = createConfiguration(group).getTemplate(templateName);
            StringWriter out = new StringWriter();
            // FreeMarker 的 process 会把渲染结果写入 StringWriter
            template.process(model, out);
            return out.toString();
        } catch (IOException | TemplateException e) {
            throw new RuntimeException("模板渲染失败: " + templateName, e);
        }
    }

    /**
     * 从插件 jar 读取内置（默认）模板的原始文本。
     *
     * <p>用于“设置界面”中展示默认模板、以及新自定义模板组初始内容。
     *
     * @param name 内置模板文件名，例如 {@code entity.ftl}
     * @return 模板文本；找不到时返回空串
     */
    static String defaultTemplateText(String name) {
        try (InputStream in = TemplateRenderer.class.getResourceAsStream("/templates/" + name)) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 创建 FreeMarker {@link Configuration}（线程安全，可重复使用）。
     *
     * <p>配置要点：
     * <ul>
     *   <li>UTF-8 编码；</li>
     *   <li>关闭模板异常日志（避免模板在 IDE 日志里刷屏）；</li>
     *   <li>模板加载器按“自定义在前、内置在后”组合。</li>
     * </ul>
     *
     * @param group 模板组名（自定义组）
     * @return 配置好的 FreeMarker Configuration
     */
    private static Configuration createConfiguration(String group) {
        Configuration config = new Configuration(Configuration.VERSION_2_3_32);
        config.setDefaultEncoding("UTF-8");
        config.setLogTemplateExceptions(false);

        // 自定义模板：把用户保存的每个模板文本注册为具名模板
        StringTemplateLoader custom = new StringTemplateLoader();
        Map<String, String> templates = effectiveTemplates(group);
        if (templates != null) {
            for (Map.Entry<String, String> e : templates.entrySet()) {
                if (e.getValue() != null) {
                    custom.putTemplate(e.getKey(), e.getValue());
                }
            }
        }
        // 内置模板：从 classpath /templates 目录加载
        ClassTemplateLoader bundled = new ClassTemplateLoader(TemplateRenderer.class, "/templates");
        // 组合加载器：查找顺序 custom -> bundled
        config.setTemplateLoader(new MultiTemplateLoader(new TemplateLoader[]{custom, bundled}));
        return config;
    }

    /**
     * 返回指定自定义模板组的内容；默认组或组不存在时返回 null——此时只使用内置模板。
     *
     * @param group 模板组名
     * @return 该组的所有模板文本（模板名 -&gt; 内容），或 null
     */
    private static Map<String, String> effectiveTemplates(String group) {
        if (group == null || TemplateSettings.DEFAULT_GROUP.equals(group)) {
            return null;
        }
        Map<String, String> templates = TemplateSettings.getInstance().getGroup(group);
        if (templates == null) {
            // 组已被用户删除但仍有代码引用 -> 回退到内置默认
            return null;
        }
        return templates;
    }
}