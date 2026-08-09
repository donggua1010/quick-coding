package com.donggua.plugin;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Mapper 接口方法 与 MyBatis XML 语句之间的方法级双向导航提供器。
 *
 * <p>工作方式（双向）：
 * <ul>
 *   <li><b>XML → Java</b>：在 {@code <mapper>} XML 文件的每个语句标签
 *       （{@code select/insert/update/delete}）图标命，通过 {@code id} 找到对应的 Java
 *       接口方法跳到方法定义；</li>
 *   <li><b>Java → XML</b>：在 Java Mapper 接口的每个方法上，通过
 *       namespace + 方法名找到对应的 XML 语句标签跳到标签位置。</li>
 * </ul>
 *
 * <p>本类实现 {@link LineMarkerProvider} 的 {@code collectSlowLineMarkers}（慢收集）
 * 两处都先在人工收集（{@code collectLineMarkers} 返回 null），全部走慢速路径，
 * 因此允许做索引查询（{@link FilenameIndex}）。
 *
 * <p>导航用 {@link com.intellij.openapi.fileEditor.OpenFileDescriptor}，在目标文件
 * 的偏移处打开并聚焦。
 */
public final class MapperLineMarkerProvider implements LineMarkerProvider {

    /** MyBatis 语句标签名称集合。 */
    private static final Set<String> STATEMENTS = Set.of("select", "insert", "update", "delete");

    /**
     * 快速路径：本实现不提供廉价 marker，始终返回 null（全部走慢速收集）。
     *
     * @param element 待检查元素
     * @return 恒为 null（不耗时路径无 marker）
     */
    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        return null;
    }

    /**
     * 慢速收集：根据元素所在文件类型分发到两个方向的收集逻辑。
     *
     * @param elements 待检查的元素列表（同一文件）
     * @param result   收集结果容器
     */
    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements,
                                       @NotNull Collection<? super LineMarkerInfo<?>> result) {
        if (elements.isEmpty()) {
            return;
        }
        PsiFile file = elements.get(0).getContainingFile();
        if (file == null) {
            return;
        }
        if (file instanceof XmlFile xf && mapperRoot(xf) != null) {
            // XML 文件且是 mapper 根 -> XML -> Java
            collectXmlToJava(xf, elements, result);
        } else if (file.getName() != null && file.getName().endsWith(".java")) {
            // Java 文件 -> Java -> XML
            collectJavaToXml(file, elements, result);
        }
    }

    /**
     * XML 语句标签 -&gt; 匹配的 Java Mapper 接口方法。
     *
     * <p>通过 XML 根标签的 {@code namespace}（Mapper 接口全限定名）解析接口，
     * 再按语句标签的 {@code id} 找到同名方法，在该方法名位置挂 marker。
     *
     * @param xmlFile  mapper XML 文件
     * @param elements 该文件参与检查的元素
     * @param result   收集结果
     */
    private void collectXmlToJava(XmlFile xmlFile, List<? extends PsiElement> elements,
                                  Collection<? super LineMarkerInfo<?>> result) {
        Project project = xmlFile.getProject();
        PsiClass mapper = resolveMapperClass(project, xmlFile);
        if (mapper == null) {
            return;
        }
        for (PsiElement el : elements) {
            if (!(el instanceof XmlTag tag) || !STATEMENTS.contains(tag.getName())) {
                continue; // 只处理语句标签
            }
            String id = tag.getAttributeValue("id");
            if (id == null) {
                continue;
            }
            PsiMethod method = findMethod(mapper, id);
            if (method == null) {
                continue;
            }
            PsiElement anchor = xmlTagNameLeaf(tag);
            if (anchor == null) {
                continue;
            }
            result.add(markerOn(anchor,
                    MyBatisIcons.MAPPER, (e, elt) -> navigate(method)));
        }
    }

    /**
     * Java Mapper 接口方法名 -&gt; 匹配的 XML 语句标签。
     *
     * <p>先用本文件中的“Mapper 结尾接口”找到 XML（按 namespace 优先、base-name 回退），
     * 再为每个方法挂 marker 跳转到对应语句标签。
     *
     * @param javaFile Mapper 接口所在 Java 文件
     * @param elements 参与检查的元素
     * @param result   收集结果
     */
    private void collectJavaToXml(PsiFile javaFile, List<? extends PsiElement> elements,
                                  Collection<? super LineMarkerInfo<?>> result) {
        PsiClass mapper = findMapperClassInFile(javaFile);
        if (mapper == null || mapper.getQualifiedName() == null) {
            return;
        }
        Project project = javaFile.getProject();
        XmlTag root = findMapperXml(project, javaFile.getName(), mapper.getQualifiedName());
        if (root == null) {
            return;
        }
        for (PsiElement el : elements) {
            if (!(el instanceof PsiMethod method) || method.getContainingClass() != mapper) {
                continue; // 只处理该接口的方法
            }
            String name = method.getName();
            XmlTag stmt = findStatement(root, name);
            if (stmt == null) {
                continue;
            }
            PsiElement anchor = method.getNameIdentifier();
            if (anchor == null) {
                continue;
            }
            result.add(markerOn(anchor,
                    MyBatisIcons.MAPPER, (e, t) -> navigate(stmt)));
        }
    }

    /**
     * 根据 Java 文件名（base）与接口全限定名定位映射 XML 文件的根标签。
     *
     * <p>先按 filename（{@code <base>.xml}）全局扫描，优先匹配 XML 的
     * {@code namespace} 与接口全限定名一致；若无 namespace 则回退到 base-name 匹配。
     *
     * @param project       项目
     * @param javaFileName  Java 文件名（含 .java 后缀）
     * @param qualifiedName Mapper 接口全限定名
     * @return 匹配到的 {@code <mapper>} 根标签，或 null
     */
    private static XmlTag findMapperXml(Project project, String javaFileName, String qualifiedName) {
        // 去掉 ".java" 后缀得到基础名 UserMapper
        String base = javaFileName;
        int dot = base.lastIndexOf('.');
        if (dot >= 0) {
            base = base.substring(0, dot);
        }
        XmlTag namespaceMatch = null;
        XmlTag baseMatch = null;
        for (PsiFile f : FilenameIndex.getFilesByName(project, base + ".xml", GlobalSearchScope.allScope(project))) {
            if (f instanceof XmlFile xf) {
                XmlTag root = mapperRoot(xf);
                if (root == null) {
                    continue;
                }
                String ns = root.getAttributeValue("namespace");
                if (qualifiedName.equals(ns)) {
                    // namespace 精确匹配 -> 优先
                    namespaceMatch = root;
                } else if (ns == null || ns.isEmpty()) {
                    // 无 namespace 的兜底匹配
                    baseMatch = root;
                }
            }
        }
        return namespaceMatch != null ? namespaceMatch : baseMatch;
    }

    /**
     * 从 XML 文件的 {@code <mapper namespace="...">} 解析出对应的 Java 接口。
     *
     * @param project 项目
     * @param xmlFile mapper XML 文件
     * @return 差异的 mapper 接口，或 null
     */
    private static PsiClass resolveMapperClass(Project project, XmlFile xmlFile) {
        XmlTag root = mapperRoot(xmlFile);
        if (root == null) {
            return null;
        }
        String ns = root.getAttributeValue("namespace");
        if (ns == null || ns.isEmpty()) {
            return null;
        }
        return JavaPsiFacade.getInstance(project).findClass(ns, GlobalSearchScope.allScope(project));
    }

    /**
     * 在指定根标签下查找 id 匹配的语句标签。
     *
     * @param root  {@code <mapper>} 根
     * @param id    语句 id（即 Java 方法名）
     * @return 语句标签，或 null
     */
    private static XmlTag findStatement(XmlTag root, String id) {
        for (XmlTag child : root.getSubTags()) {
            if (STATEMENTS.contains(child.getName()) && id.equals(child.getAttributeValue("id"))) {
                return child;
            }
        }
        return null;
    }

    /**
     * 在指定类中按方法名查找方法（不包含继承进来的方法）。
     *
     * @param cls  目标类型
     * @param name 方法名
     * @return 匹配的方法，或 null
     */
    private static PsiMethod findMethod(PsiClass cls, String name) {
        for (PsiMethod m : cls.findMethodsByName(name, false)) {
            if (name.equals(m.getName())) {
                return m;
            }
        }
        return null;
    }

    /**
     * 返回 Java 文件中第一个名为 *Mapper 的接口。
     *
     * @param javaFile Java 文件
     * @return Mapper 接口，找不到返回 null
     */
    private static PsiClass findMapperClassInFile(PsiFile javaFile) {
        for (PsiClass cls : PsiTreeUtil.getChildrenOfTypeAsList(javaFile, PsiClass.class)) {
            if (cls.getName() != null && cls.getName().endsWith("Mapper")) {
                return cls;
            }
        }
        return null;
    }

    /**
     * 判断 XML 文件是否是 MyBatis mapper 文件（根标签为 name="mapper"）。
     *
     * @param xmlFile XML 文件
     * @return 根标签，或 null
     */
    @Nullable
    private static XmlTag mapperRoot(XmlFile xmlFile) {
        if (xmlFile.getDocument() == null) {
            return null;
        }
        XmlTag root = xmlFile.getDocument().getRootTag();
        return (root != null && "mapper".equals(root.getName())) ? root : null;
    }

    /**
     * 返回 XML 语句标签名称的叶子 token（如 {@code <select>} 里的 {@code select}）。
     *
     * <p>LineMarker 要求挂在叶子元素上，不能直接挂 {@link XmlTag}，否则会触发
     * "LineMarker is supposed to be registered for leaf elements only" 性能警告。
     *
     * @param tag 语句标签
     * @return 标签名的 {@link XmlToken}，找不到返回 null
     */
    @Nullable
    private static XmlToken xmlTagNameLeaf(XmlTag tag) {
        XmlToken token = PsiTreeUtil.findChildOfType(tag, XmlToken.class);
        if (token != null && token.getTokenType() == XmlTokenType.XML_NAME) {
            return token;
        }
        // 容错：带前缀/复杂标签名时，退回到标签内任一并叶子 token
        for (PsiElement child : tag.getChildren()) {
            if (child instanceof XmlToken t && t.getTextRange().getLength() > 0) {
                return t;
            }
        }
        return null;
    }

    /**
     * 构造一个 gutter 图标 marker（无 tooltip，点击执行 handler）。
     *
     * @param anchor  图标挂载的元素
     * @param icon    显示的图标
     * @param handler 点击时执行的导航 handler
     * @return LineMarkerInfo
     */
    private static LineMarkerInfo<PsiElement> markerOn(PsiElement anchor, Icon icon,
                                                       GutterIconNavigationHandler<PsiElement> handler) {
        return new LineMarkerInfo<>(anchor, anchor.getTextRange(), icon,
                (Function<PsiElement, String>) unused -> null, handler, GutterIconRenderer.Alignment.LEFT);
    }

    /**
     * 打开目标元素所在文件并跳转到其文本起始偏移。
     *
     * @param target 要导航到的元素（方法/标签等）
     */
    private static void navigate(PsiElement target) {
        int offset = target.getTextRange() != null ? target.getTextRange().getStartOffset() : 0;
        new com.intellij.openapi.fileEditor.OpenFileDescriptor(target.getProject(),
                target.getContainingFile().getVirtualFile(), offset).navigate(true);
    }
}