package caliniya.vergvoke.annotation.ecs;

import java.io.*;
import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import com.squareup.javapoet.*;
import arc.struct.*;
import caliniya.vergvoke.annotation.Processor;
import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.annotation.tool.*;
import caliniya.vergvoke.base.anno.auto.*;
import caliniya.vergvoke.base.tool.ObjectSet;
import caliniya.vergvoke.base.tool.Ar;

/**
 * EC 注解处理器
 *
 * <p>
 * 单轮完成：先在内存里做完全部校验，全部通过才一次性生成；校验期间不写文件，不会落半成品。
 *
 * <p>
 * 生成物：
 *
 * <ul>
 * <li><b>实体类</b>（{@code caliniya.vergvoke.base.ecs.<name>}）：继承 {@code
 *       caliniya.vergvoke.base.game.Entity}，字段为各组件的扁平字段；按
 * {@code @Component(proc=...)} 分两档注入
 * {@code @Updata}：
 * <ul>
 * <li><b>proc = "main"（或留空）</b>：轻量更新（移动、生产…），方法体<b>直接铺进实体的
 * {@code update()}</b>，顺序由
 * {@code @Component.index} 决定（该档必须提供非 0 的 index）；
 * <li><b>proc = 其他系统名</b>：需要"同阶段一致"或较重的更新（开火、寻路、AI…）， 生成独立方法 {@code
 *             update_<组件简单名>(float delta)}，由该系统在自己阶段调用。
 * </ul>
 * <li><b>组件系统</b>（{@code caliniya.vergvoke.base.ecs.<系统名>}）：继承系统基类，
 * {@code update(float)} 遍历该系统的实体、按 {@code @Component.index} 顺序直接调用属于该系统的组件更新。
 * <li><b>序列化</b>：实体按 {@code @Component.index} 顺序生成 {@code write(Writes)} /
 * {@code read(Reads)}，
 * 各组件的读写<b>直接铺进这两个方法</b>（不再生成中间方法）：组件内部按 {@code @Save.index} 写字段； 标了
 * {@code @Write} /
 * {@code @Read} 的组件则把方法体原样复制过来，外面套一层 {@code { }} 包装 （参数名不同就在包装里起个别名；方法体里有
 * {@code return}
 * 时包装里多一层守卫， 中途 return 出去直接抛异常——内联之后 return 会跳过后面所有组件）。
 * </ul>
 */
@AnnoProc
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes({
        "caliniya.vergvoke.annotation.Annotations.Component",
        "caliniya.vergvoke.annotation.Annotations.Entity",
        "caliniya.vergvoke.annotation.Annotations.Import",
        "caliniya.vergvoke.annotation.Annotations.Updata",
        "caliniya.vergvoke.annotation.Annotations.Save",
        "caliniya.vergvoke.annotation.Annotations.OverrideEntity",
        "caliniya.vergvoke.annotation.Annotations.Write",
        "caliniya.vergvoke.annotation.Annotations.Read"
})
public class ECProcessor extends Processor {

    private static final String GENERATED_PACKAGE = "caliniya.vergvoke.base.ecs";

    /** 生成的实体继承的基类 */
    private static final String ENTITY_BASE_CLASS = "caliniya.vergvoke.base.game.Entity";

    /** @Entity.type() 必须实现的接口（一种实体的类型） */
    private static final String ENTITY_TYPE_INTERFACE = "caliniya.vergvoke.base.api.EntityType";

    /** 阵营类型（生成 create(team, type, x, y) 时用） */
    private static final String TEAM_TYPES_CLASS = "caliniya.vergvoke.base.type.TeamTypes";

    /** 轻量档：proc 为空或等于它时，更新逻辑直接铺进实体 update() */
    private static final String MAIN_SYSTEM = "main";

    /** 实体 / 组件更新方法统一用的帧时间参数名：@Updata 的方法体会原样铺进生成代码，名字必须对上 */
    private static final String DELTA_NAME = "delta";

    /** 序列化用的 IO 类型（arc） */
    private static final String WRITES_CLASS = "arc.util.io.Writes";

    private static final String READS_CLASS = "arc.util.io.Reads";
    private static final ClassName WRITES = ClassName.get("arc.util.io", "Writes");
    private static final ClassName READS = ClassName.get("arc.util.io", "Reads");

    /** 每种类型的实体都有哪些组件（生成成功后填充） */
    public ObjectMap<String, ObjectSet<String>> ECMap = new ObjectMap<>();

    /** 基类的 write / read 是不是已经实现好了（是的话生成的 write / read 会先调 super） */
    private Boolean baseWriteConcrete, baseReadConcrete;

    /** 本轮校验里有问题的组件（全限定名）：用它们的实体会被跳过，错误只在组件那边报一次 */
    private final Set<String> brokenComponents = new LinkedHashSet<>();

    {
        maxRounds = 1;
    }

    @SuppressWarnings("unused")
    @Override
    protected void process() {
        Map<String, AType> components = componentTypes();

        // --- 组件级校验：只报错 + 记下"坏组件"，不再整体停摆 ---
        // 用到坏组件的实体会被跳过（错误在组件那边已经报过），其余部分照常生成，
        // 免得一处错误连累到"整个 base.ecs 包都不存在"那种一片红。
        validateImportTargets(components);
        validateUpdateTargets(components);
        validateSaveTargets(components);
        validateSerializationMethods(components);
        validateComponentNames(components);
        validateComponentIndexes(components);
        validateOverrideEntityMethods(components);

        // 1) 把没问题的实体算成"生成计划"（出错的只报错、不生成）
        List<EntityPlan> plans = new ArrayList<>();
        for (AType entity : types(Entity.class)) {
            if (usesBrokenComponent(entity)) {
                continue;
            }
            EntityPlan plan = planEntity(entity, components);
            if (plan == null) {
                continue;
            }
            plans.add(plan);
        }

        // 2) 统一生成：能站住的部分照常生成
        for (EntityPlan plan : plans) {
            generateEntity(plan);
        }
        Map<String, Map<String, List<UpdatePiece>>> bySystem = systemsByEntity(plans);
        generateEntityArs(plans);
        generateComponentSystems(bySystem);
        generateSystems(plans, bySystem);
    }

    /** 用到"坏组件"的实体本轮直接跳过（组件的错误已经报过，不再连带报一片） */
    private boolean usesBrokenComponent(AType entity) {
        for (String componentName : compsOf(entity)) {
            if (brokenComponents.contains(componentName)) {
                return true;
            }
        }
        return false;
    }

    /** 记一笔"坏组件"：它自己报过错，用到它的实体不再连带报一片 */
    private void markBroken(String componentFullName) {
        if (componentFullName != null) {
            brokenComponents.add(componentFullName);
        }
    }

    private Map<String, AType> componentTypes() {
        Map<String, AType> components = new LinkedHashMap<>();
        for (AType component : types(Component.class)) {
            components.put(component.fullName(), component);
        }
        return components;
    }

    /**
     * @Import 只能出现在 @Component 的字段上
     */
    private boolean validateImportTargets(Map<String, AType> components) {
        boolean valid = true;
        for (AVar imported : fields(Import.class)) {
            if (!components.containsKey(imported.enclosingType().fullName())) {
                error("@Import can only be used on a field declared by @Component", imported);
                valid = false;
            }
        }
        return valid;
    }

    /**
     * @Updata 只能出现在 @Component 的方法上，且一个组件最多一个
     */
    private boolean validateUpdateTargets(Map<String, AType> components) {
        boolean valid = true;

        Map<String, List<String>> byComponent = new LinkedHashMap<>();
        for (AMethod method : methods(Updata.class)) {
            if (!components.containsKey(method.enclosingType().fullName())) {
                error("@Updata can only be used on a method declared by @Component", method);
                valid = false;
                continue;
            }
            byComponent
                    .computeIfAbsent(method.enclosingType().fullName(), key -> new ArrayList<>())
                    .add(method.name());
        }

        for (Map.Entry<String, List<String>> entry : byComponent.entrySet()) {
            if (entry.getValue().size() > 1) {
                error(
                        "@Component can only declare one @Updata method, but '"
                                + simpleName(entry.getKey())
                                + "' has "
                                + entry.getValue(),
                        components.get(entry.getKey()));
                markBroken(entry.getKey());
                valid = false;
            }
        }

        return valid;
    }

    /** 组件简单名必须唯一（生成的方法名 update_<组件简单名> 基于它） */
    private boolean validateComponentNames(Map<String, AType> components) {
        boolean valid = true;
        Map<String, String> seen = new LinkedHashMap<>();
        for (AType component : components.values()) {
            String previous = seen.putIfAbsent(component.simpleName(), component.fullName());
            if (previous != null) {
                error("Duplicate component name '" + component.simpleName() + "': " + previous, component);
                markBroken(previous);
                markBroken(component.fullName());
                valid = false;
            }
        }
        return valid;
    }

    /** 任何组件都必须提供非 0 的 index：它既决定实体的序列化顺序（组件之间）， 也决定轻量档（proc = main / 空）的更新注入顺序。 */
    private boolean validateComponentIndexes(Map<String, AType> components) {
        boolean valid = true;
        for (AType component : components.values()) {
            Component def = component.annotation(Component.class);
            if (def.index() == 0) {
                error(
                        "@Component '"
                                + component.simpleName()
                                + "' must set a non-zero index: it orders component serialization (and the main-tier update injection)",
                        component);
                markBroken(component.fullName());
                valid = false;
            }
        }
        return valid;
    }

    /**
     * @Save 只能出现在 @Component 的字段上：不能 static、不能和 @Import 混用、类型要能直接写、组件内 index 唯一
     */
    private boolean validateSaveTargets(Map<String, AType> components) {
        boolean valid = true;
        Map<String, Map<Integer, String>> byComponent = new LinkedHashMap<>();

        for (AVar field : fields(Save.class)) {
            AType owner = enclosingTypeOf(field);
            if (owner == null || !components.containsKey(owner.fullName())) {
                error("@Save can only be used on a field declared by @Component", field);
                valid = false;
                continue;
            }
            if (field.isStatic()) {
                error(
                        "@Save cannot be used on a static field: " + owner.simpleName() + "#" + field.name(),
                        field);
                markBroken(owner.fullName());
                valid = false;
                continue;
            }
            if (field.has(Import.class)) {
                error(
                        "@Save cannot be used together with @Import: "
                                + owner.simpleName()
                                + "#"
                                + field.name()
                                + " (the field it borrows is serialized by its owner component)",
                        field);
                markBroken(owner.fullName());
                valid = false;
                continue;
            }
            if (!isSaveable(field.type())) {
                error(
                        "@Save field '"
                                + owner.simpleName()
                                + "#"
                                + field.name()
                                + "' has type "
                                + field.typeName()
                                + ", which cannot be written directly: use @Write / @Read on the component",
                        field);
                markBroken(owner.fullName());
                valid = false;
                continue;
            }

            int index = field.annotation(Save.class).index();
            Map<Integer, String> indexes = byComponent.computeIfAbsent(owner.fullName(), key -> new LinkedHashMap<>());
            String previous = indexes.putIfAbsent(index, field.name());
            if (previous != null) {
                error(
                        "Duplicate @Save index "
                                + index
                                + " in component '"
                                + owner.simpleName()
                                + "': fields '"
                                + previous
                                + "' and '"
                                + field.name()
                                + "'",
                        field);
                markBroken(owner.fullName());
                valid = false;
            }
        }
        return valid;
    }

    /**
     * @Write / @Read 只能出现在 @Component 的方法上：签名要对、要成对、不能和 @Save 混用
     */
    private boolean validateSerializationMethods(Map<String, AType> components) {
        boolean valid = true;
        Map<String, String> writes = new LinkedHashMap<>();
        Map<String, String> reads = new LinkedHashMap<>();
        Set<String> withSaveFields = new LinkedHashSet<>();

        for (AVar field : fields(Save.class)) {
            AType owner = enclosingTypeOf(field);
            if (owner != null) {
                withSaveFields.add(owner.fullName());
            }
        }

        valid &= checkSerializationMethods(methods(Write.class), components, WRITES_CLASS, "@Write", writes);
        valid &= checkSerializationMethods(methods(Read.class), components, READS_CLASS, "@Read", reads);

        for (Map.Entry<String, String> entry : writes.entrySet()) {
            AType component = components.get(entry.getKey());
            if (!reads.containsKey(entry.getKey())) {
                error(
                        "@Component '"
                                + component.simpleName()
                                + "' has "
                                + entry.getValue()
                                + " but no matching @Read method: they must be used in pairs",
                        component);
                markBroken(entry.getKey());
                valid = false;
            }
            if (withSaveFields.contains(entry.getKey())) {
                error(
                        "@Component '"
                                + component.simpleName()
                                + "' cannot mix @Save fields with @Write / @Read methods: pick one style",
                        component);
                markBroken(entry.getKey());
                valid = false;
            }
        }

        for (Map.Entry<String, String> entry : reads.entrySet()) {
            if (!writes.containsKey(entry.getKey())) {
                AType component = components.get(entry.getKey());
                error(
                        "@Component '"
                                + component.simpleName()
                                + "' has "
                                + entry.getValue()
                                + " but no matching @Write method: they must be used in pairs",
                        component);
                markBroken(entry.getKey());
                valid = false;
            }
            if (withSaveFields.contains(entry.getKey()) && !writes.containsKey(entry.getKey())) {
                AType component = components.get(entry.getKey());
                error(
                        "@Component '"
                                + component.simpleName()
                                + "' cannot mix @Save fields with @Write / @Read methods: pick one style",
                        component);
                markBroken(entry.getKey());
                valid = false;
            }
        }

        return valid;
    }

    /**
     * 检查一批同方向的序列化方法（{@code @Write} 或 {@code @Read}）的合法性，并把结果记到 {@code found}。
     *
     * @param found 记录 {@code 组件全名 -> 方法名}
     */
    private boolean checkSerializationMethods(
            Ar<AMethod> candidates,
            Map<String, AType> components,
            String parameterClass,
            String annotationName,
            Map<String, String> found) {
        boolean valid = true;

        for (AMethod method : candidates) {
            AType owner = enclosingTypeOf(method);
            if (owner == null || !components.containsKey(owner.fullName())) {
                error(annotationName + " can only be used on a method declared by @Component", method);
                valid = false;
                continue;
            }

            String label = owner.simpleName() + "#" + method.name();

            // 先登记（配对校验要用），同一个组件写了两个才报错
            String previous = found.putIfAbsent(owner.fullName(), method.name());
            if (previous != null) {
                error(
                        "@Component '"
                                + owner.simpleName()
                                + "' can only declare one "
                                + annotationName
                                + " method",
                        method);
                markBroken(owner.fullName());
                valid = false;
                continue;
            }

            if (method.isStatic()) {
                error(annotationName + " method cannot be static: " + label, method);
                markBroken(owner.fullName());
                valid = false;
                continue;
            }
            if (method.isAbstract()) {
                error(annotationName + " method cannot be abstract: " + label, method);
                markBroken(owner.fullName());
                valid = false;
                continue;
            }
            if (!method.isVoid()) {
                error(annotationName + " method must return void: " + label, method);
                markBroken(owner.fullName());
                valid = false;
                continue;
            }
            if (method.parameterCount() != 1
                    || !isType(method.parameters().first().type(), parameterClass)) {
                error(
                        annotationName
                                + " method must take exactly one "
                                + parameterClass
                                + " parameter: "
                                + label
                                + "("
                                + parameterClass
                                + ")",
                        method);
                markBroken(owner.fullName());
                valid = false;
                continue;
            }
            if (bodyOf(method) == null) {
                error(annotationName + " method body is not accessible: " + label, method);
                markBroken(owner.fullName());
                valid = false;
                continue;
            }
        }

        return valid;
    }

    private boolean isMainTier(String proc) {
        return proc == null || proc.isEmpty() || MAIN_SYSTEM.equals(proc);
    }

    /** Entity 基类上的实例字段名（组件同名字段不注入实体，避免遮蔽基类）。 */
    private Set<String> entityInstanceFieldNames() {
        Set<String> names = new HashSet<>();
        TypeElement base = elementUtils.getTypeElement(ENTITY_BASE_CLASS);
        if (base == null) {
            return names;
        }
        for (Element member : base.getEnclosedElements()) {
            if (member.getKind() == ElementKind.FIELD
                    && !member.getModifiers().contains(Modifier.STATIC)) {
                names.add(member.getSimpleName().toString());
            }
        }
        return names;
    }

    /** 校验 {@code @OverrideEntity}：必须在 @Component 上、签名对齐 Entity、不与更新/序列化注解混用。 */
    private void validateOverrideEntityMethods(Map<String, AType> components) {
        TypeElement base = elementUtils.getTypeElement(ENTITY_BASE_CLASS);
        for (AMethod method : methods(OverrideEntity.class)) {
            AType owner = enclosingTypeOf(method);
            if (owner == null || !components.containsKey(owner.fullName())) {
                error("@OverrideEntity can only be used on a method declared by @Component", method);
                if (owner != null) {
                    markBroken(owner.fullName());
                }
                continue;
            }

            String label = owner.simpleName() + "#" + method.name();

            if (method.isStatic() || method.isAbstract()) {
                error("@OverrideEntity method cannot be static/abstract: " + label, method);
                markBroken(owner.fullName());
                continue;
            }
            if (method.has(Updata.class) || method.has(Write.class) || method.has(Read.class)) {
                error(
                        "@OverrideEntity cannot mix with @Updata/@Write/@Read: "
                                + label
                                + "（覆写方法体本身就会进实体）",
                        method);
                markBroken(owner.fullName());
                continue;
            }
            if (bodyOf(method) == null) {
                error("@OverrideEntity method body is not accessible: " + label, method);
                markBroken(owner.fullName());
                continue;
            }
            if (base != null && findBaseMethod(base, method) == null) {
                error(
                        "@OverrideEntity has no matching method on "
                                + ENTITY_BASE_CLASS
                                + ": "
                                + method.fullSignature()
                                + " ("
                                + label
                                + ")",
                        method);
                markBroken(owner.fullName());
            }
        }
    }

    /** 校验一个实体并算出它的生成计划；返回 null 表示这个实体没通过校验（错误已经报出）。 */
    private EntityPlan planEntity(AType entity, Map<String, AType> components) {
        // 实体类名就是 @Entity(name = ...) 给的名字本身，不加后缀
        String entityName = entity.annotation(Entity.class).name();
        if (!SourceVersion.isName(entityName)) {
            error("Invalid generated entity name: " + entityName, entity);
            return null;
        }

        boolean valid = true;

        // --- @Entity.type()：类型目标 class，必须实现 EntityType ---
        TypeMirror typeMirror = entityTypeOf(entity);
        ClassName typeClassName = null;
        if (typeMirror == null) {
            error(
                    "@Entity must declare type() = <EntityType实现类>.class, e.g. type = UnitType.class",
                    entity);
            return null;
        } else {
            Element typeEl = typeUtils.asElement(typeMirror);
            if (!(typeEl instanceof TypeElement)) {
                error("@Entity.type is not a class: " + typeMirror, entity);
                return null;
            }
            TypeElement entityIface = elementUtils.getTypeElement(ENTITY_TYPE_INTERFACE);
            if (entityIface == null) {
                error("Cannot resolve " + ENTITY_TYPE_INTERFACE + " (is core on the compile classpath?)", entity);
                return null;
            }
            if (!typeUtils.isSubtype(typeUtils.erasure(typeMirror), typeUtils.erasure(entityIface.asType()))) {
                error(
                        "@Entity.type must implement "
                                + ENTITY_TYPE_INTERFACE
                                + ": "
                                + ((TypeElement) typeEl).getQualifiedName(),
                        entity);
                valid = false;
            }
            typeClassName = ClassName.get((TypeElement) typeEl);
        }

        // --- 解析实体包含的组件 ---
        List<AType> entityComponents = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String componentName : compsOf(entity)) {
            AType component = components.get(componentName);
            if (component == null) {
                error("Entity component is not annotated with @Component: " + componentName, entity);
                valid = false;
                continue;
            }
            if (!seen.add(component.fullName())) {
                error("Duplicate component in entity: " + component.simpleName(), entity);
                valid = false;
                continue;
            }
            entityComponents.add(component);
        }

        // --- 收集字段 + 判重名（@Import 不注入实体；与 Entity 基类同名的字段只留在组件源码里，不注入） ---
        Set<String> baseFieldNames = entityInstanceFieldNames();
        Map<String, VariableElement> injectedFields = new LinkedHashMap<>();
        List<VariableElement> imports = new ArrayList<>();

        for (AType component : entityComponents) {
            for (AVar field : component.fields()) {
                VariableElement variable = field.e;

                if (field.has(Import.class)) {
                    if (field.isStatic()) {
                        error("@Import cannot be used on a static field", variable);
                        valid = false;
                    } else {
                        imports.add(variable);
                    }
                    continue;
                }

                if (field.isStatic()) {
                    continue;
                }

                // Entity 基类已有同名字段（x/y/size/type/…）：组件侧声明仅供源码编译，实体用基类那一份
                if (baseFieldNames.contains(field.name())) {
                    continue;
                }

                VariableElement previous = injectedFields.putIfAbsent(field.name(), variable);
                if (previous != null) {
                    error(
                            "Duplicate entity field '"
                                    + field.name()
                                    + "' from components "
                                    + previous.getEnclosingElement()
                                    + " and "
                                    + component.fullName(),
                            variable);
                    valid = false;
                }
                if (field.name().equals("type")) {
                    error(
                            "Component field name 'type' is reserved for @Entity.type (基类 Entity.type)",
                            variable);
                    valid = false;
                }
            }
        }

        // --- @Import 必须在别的组件里有同名同类型的字段 ---
        for (VariableElement imported : imports) {
            VariableElement source = injectedFields.get(imported.getSimpleName().toString());
            if (source == null) {
                error(
                        "@Import field '"
                                + imported.getSimpleName()
                                + "' has no matching field in another component",
                        imported);
                valid = false;
                continue;
            }
            if (!typeUtils.isSameType(imported.asType(), source.asType())) {
                error(
                        "@Import field '"
                                + imported.getSimpleName()
                                + "' has type "
                                + imported.asType()
                                + ", but the entity field has type "
                                + source.asType(),
                        imported);
                valid = false;
            }
        }

        // --- 收集 @Updata 片段：按 @Component.index 排序（相同则按组件顺序） ---
        List<UpdatePiece> pieces = new ArrayList<>();
        int order = 0;
        for (AType component : entityComponents) {
            List<AMethod> updateMethods = methodsOf(component);
            if (updateMethods.isEmpty()) {
                continue;
            }

            Component componentDef = component.annotation(Component.class);
            int componentIndex = componentDef.index();
            boolean mainTier = isMainTier(componentDef.proc());
            int seq = order++;

            for (AMethod method : updateMethods) {
                String label = component.simpleName() + "#" + method.name();

                if (method.isStatic()) {
                    error("@Updata method cannot be static: " + label, method);
                    valid = false;
                    continue;
                }
                if (method.isAbstract()) {
                    error("@Updata method cannot be abstract: " + label, method);
                    valid = false;
                    continue;
                }
                if (!method.isVoid()) {
                    error("@Updata method must return void: " + label, method);
                    valid = false;
                    continue;
                }
                if (method.parameterCount() != 1
                        || !isType(method.parameters().first().type(), "float")
                        || !method.parameters().first().name().equals(DELTA_NAME)) {
                    error(
                            "@Updata method must take exactly one float parameter named '"
                                    + DELTA_NAME
                                    + "': "
                                    + label
                                    + "(float "
                                    + DELTA_NAME
                                    + ")",
                            method);
                    valid = false;
                    continue;
                }

                String body = bodyOf(method);
                if (body == null) {
                    error("@Updata method body is not accessible: " + label, method);
                    valid = false;
                    continue;
                }

                pieces.add(
                        new UpdatePiece(
                                componentIndex,
                                seq,
                                label,
                                "update_" + component.simpleName(),
                                body,
                                componentDef.proc(),
                                entityName,
                                mainTier));

                // 一个组件只允许一个 @Updata 方法（校验阶段已保证），这里只取第一个
                break;
            }
        }
        pieces.sort(
                Comparator.comparingInt((UpdatePiece piece) -> piece.index)
                        .thenComparingInt(piece -> piece.order));

        // --- 收集序列化：组件之间按 @Component.index 排（同 index 直接报错，顺序会不确定） ---
        Map<Integer, String> indexOwners = new LinkedHashMap<>();
        List<SerialPiece> serials = new ArrayList<>();
        for (AType component : entityComponents) {
            int componentIndex = component.annotation(Component.class).index();
            String previous = indexOwners.putIfAbsent(componentIndex, component.simpleName());
            if (previous != null) {
                error(
                        "Duplicate @Component.index "
                                + componentIndex
                                + " in entity '"
                                + safeName(entity)
                                + "': components '"
                                + previous
                                + "' and '"
                                + component.simpleName()
                                + "' (serialization order would be undefined)",
                        entity);
                valid = false;
            }
        }
        for (int i = 0; i < entityComponents.size(); i++) {
            SerialPiece piece = serialPieceOf(entityComponents.get(i), i);
            if (piece != null) {
                serials.add(piece);
            }
        }
        serials.sort(
                Comparator.comparingInt((SerialPiece piece) -> piece.index)
                        .thenComparingInt(piece -> piece.order));

        if (!valid) {
            return null;
        }

        ObjectSet<String> componentNames = new ObjectSet<>();
        for (AType component : entityComponents) {
            componentNames.add(component.simpleName());
        }

        return new EntityPlan(
                entity,
                entityName,
                typeClassName,
                entityComponents,
                componentNames,
                injectedFields,
                pieces,
                serials);
    }

    private String safeName(AType entity) {
        Entity def = entity.annotation(Entity.class);
        return def == null ? entity.simpleName() : def.name();
    }

    /**
     * 算出一个组件要写/读什么：{@code @Save} 字段（按 index 排好）或 {@code @Write} / {@code @Read}
     * 方法体。
     * 两者都没有（这个组件不参与序列化）时返回 null。
     */
    private SerialPiece serialPieceOf(AType component, int order) {
        Map<Integer, VariableElement> saveFields = new TreeMap<>();
        for (AVar field : component.fields()) {
            Save save = field.annotation(Save.class);
            if (save == null) {
                continue;
            }
            saveFields.put(save.index(), field.e);
        }

        List<VariableElement> ordered = new ArrayList<>(saveFields.values());

        String writeBody = null, readBody = null, writeParam = null, readParam = null;
        String writeLabel = null, readLabel = null;
        boolean writeHasReturn = false, readHasReturn = false;
        for (AMethod method : component.methods()) {
            if (method.has(Write.class)) {
                writeBody = bodyOf(method);
                writeParam = method.parameters().first().name();
                writeLabel = component.simpleName() + "#" + method.name();
                writeHasReturn = countReturns(method.e) > 0;
            } else if (method.has(Read.class)) {
                readBody = bodyOf(method);
                readParam = method.parameters().first().name();
                readLabel = component.simpleName() + "#" + method.name();
                readHasReturn = countReturns(method.e) > 0;
            }
        }

        if (ordered.isEmpty() && writeBody == null) {
            return null;
        }

        return new SerialPiece(
                component.simpleName(),
                component.annotation(Component.class).index(),
                order,
                ordered,
                writeBody,
                writeParam,
                writeLabel,
                writeHasReturn,
                readBody,
                readParam,
                readLabel,
                readHasReturn);
    }

    /** 取出某个组件里带 @Updata 的方法（正常只会有一个；多于一个在校验阶段已报错） */
    private List<AMethod> methodsOf(AType component) {
        List<AMethod> result = new ArrayList<>();
        for (AMethod method : component.methods()) {
            if (method.has(Updata.class)) {
                result.add(method);
            }
        }
        return result;
    }

    /** 取方法体的源码文本（含大括号）；拿不到（非 javac 等）返回 null */
    private String bodyOf(AMethod method) {
        if (trees == null) {
            return null;
        }
        Tree tree = trees.getTree(method.e);
        if (!(tree instanceof MethodTree)) {
            return null;
        }
        BlockTree body = ((MethodTree) tree).getBody();
        return body == null ? null : body.toString();
    }

    /**
     * 数一数方法体里有多少个 {@code return}（lambda / 匿名类里的不算——那些 return 只结束它们自己）。
     *
     * <p>
     * 序列化方法的方法体会被内联进实体的 {@code write()} / {@code read()}，那里的 return 会直接跳过后面所有组件，
     * 所以生成时会把它换成一个抛异常；这里数出来的个数要和文本里能改写的个数对得上，否则报错让人工确认。
     */
    private int countReturns(ExecutableElement method) {
        if (trees == null) {
            return 0;
        }
        Tree tree = trees.getTree(method);
        if (!(tree instanceof MethodTree)) {
            return 0;
        }
        BlockTree body = ((MethodTree) tree).getBody();
        if (body == null) {
            return 0;
        }

        final int[] count = { 0 };
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitReturn(ReturnTree node, Void unused) {
                count[0]++;
                return null;
            }

            @Override
            public Void visitLambdaExpression(LambdaExpressionTree node, Void unused) {
                return null; // lambda 里的 return 只结束 lambda
            }

            @Override
            public Void visitClass(ClassTree node, Void unused) {
                return null; // 局部 / 内部类里的 return 只结束它自己的方法
            }

            @Override
            public Void visitNewClass(NewClassTree node, Void unused) {
                if (node.getClassBody() != null) {
                    return null; // 匿名类同理
                }
                return super.visitNewClass(node, unused);
            }
        }.scan(body, null);

        return count[0];
    }

    /** 按生成计划写出实体类 */
    private void generateEntity(EntityPlan plan) {
        // extends Entity<Type>：基类已有 public T type，由 @Entity.type 泛型实例化
        // JavaPoet 1.12 无 ClassName.parameterizedBy，用 ParameterizedTypeName.get
        TypeName entityBase = ClassName.bestGuess(ENTITY_BASE_CLASS);
        if (plan.typeClass != null) {
            entityBase = ParameterizedTypeName.get((ClassName) entityBase, plan.typeClass);
        }

        TypeSpec.Builder entityType = TypeSpec.classBuilder(plan.entityName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(entityBase);

        // 按类型目标生成工厂：create(type) / create(team, type, x, y)
        if (plan.typeClass != null) {
            addTypeFactories(entityType, plan);
        }

        for (VariableElement field : plan.injectedFields.values()) {
            entityType.addField(
                    FieldSpec.builder(
                            TypeName.get(field.asType()), field.getSimpleName().toString(), Modifier.PUBLIC)
                            .build());
        }

        // 独立更新方法：proc 指定了处理系统的片段
        for (UpdatePiece piece : plan.pieces) {
            if (piece.mainTier) {
                continue;
            }
            MethodSpec.Builder method = MethodSpec.methodBuilder(piece.methodName)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(float.class, DELTA_NAME)
                    .addJavadoc("来自 {@code @Updata}：$L（由 $L 处理，自动生成）。\n", piece.label, piece.proc);
            method.addCode("$L\n", piece.body);
            entityType.addMethod(method.build());
        }

        // @OverrideEntity：组件方法覆写实体基类同名方法
        injectOverrideEntityMethods(entityType, plan);

        // 组件上的其他 public 实例方法（非 @Updata/@Write/@Read/@OverrideEntity）：方法体原样铺进实体
        injectComponentHelpers(entityType, plan);

        // 实体 update()：轻量档片段按 @Component.index 顺序直接铺进来
        boolean hasMain = false;
        for (UpdatePiece piece : plan.pieces) {
            if (piece.mainTier) {
                hasMain = true;
                break;
            }
        }

        if (hasMain) {
            MethodSpec.Builder update = MethodSpec.methodBuilder("update")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(float.class, DELTA_NAME)
                    .addJavadoc("proc = \"main\"（或未指定）的组件更新，按 @Component.index 顺序直接注入（自动生成）。\n");
            for (UpdatePiece piece : plan.pieces) {
                if (!piece.mainTier) {
                    continue;
                }
                update.addCode("// $L\n", piece.label);
                update.addCode("$L\n", piece.body);
            }
            entityType.addMethod(update.build());
        }

        // 序列化：按 @Component.index 排好的 write()/read()（各组件的读写直接铺进去）
        boolean baseWrite = isBaseMethodConcrete("write", WRITES_CLASS);
        boolean baseRead = isBaseMethodConcrete("read", READS_CLASS);
        boolean generateWrite = baseWrite || !plan.serials.isEmpty();
        boolean generateRead = baseRead || !plan.serials.isEmpty();
        if (generateWrite) {
            entityType.addMethod(serializationOverride(true, plan, baseWrite));
        }
        if (generateRead) {
            entityType.addMethod(serializationOverride(false, plan, baseRead));
        }

        // 补齐基类里仍然抽象的方法，保证生成类一定能编译
        addAbstractStubs(entityType, plan, generateWrite, generateRead, hasMain);

        try {
            // 组件的 import 原样搬进生成文件：@Updata / @Write / @Read 的方法体里就能写短名（Mathf、Time……）
            JavaFile javaFile = JavaFile.builder(GENERATED_PACKAGE, entityType.build()).build();
            String source = injectImports(javaFile.toString(), componentImports(plan));

            // 自己落盘：JavaPoet 的 writeTo 没给"外来 import"留位置，只能渲染完手动插
            List<Element> origins = new ArrayList<>();
            origins.add(plan.entity.e);
            for (AType component : plan.components) {
                origins.add(component.e);
            }
            Writer writer =
                    createSourceFile(GENERATED_PACKAGE + "." + plan.entityName, origins.toArray(new Element[0]));
            writer.write(source);
            writer.close();

            ECMap.put(plan.entityName, plan.componentNames);
        } catch (IOException e) {
            error(
                    "Failed to generate entity "
                            + GENERATED_PACKAGE
                            + "."
                            + plan.entityName
                            + ": "
                            + e.getMessage(),
                    plan.entity);
        }
    }

    /**
     * 组件上 {@code @OverrideEntity} 方法：铺进实体并打 {@code @Override}，替代基类默认实现。
     *
     * <p>签名必须与 {@code Entity} 已有方法一致；同一实体内同签名只允许一个组件覆写。
     */
    private void injectOverrideEntityMethods(TypeSpec.Builder entityType, EntityPlan plan) {
        if (trees == null) {
            return;
        }
        TypeElement base = elementUtils.getTypeElement(ENTITY_BASE_CLASS);
        if (base == null) {
            return;
        }

        Set<String> seen = new HashSet<>();
        for (AType component : plan.components) {
            for (AMethod method : component.methods()) {
                if (!method.has(OverrideEntity.class)) {
                    continue;
                }
                String label = component.simpleName() + "#" + method.name();

                if (method.isStatic() || method.isAbstract()) {
                    error("@OverrideEntity method cannot be static/abstract: " + label, method);
                    continue;
                }
                if (method.has(Updata.class) || method.has(Write.class) || method.has(Read.class)) {
                    error(
                            "@OverrideEntity cannot mix with @Updata/@Write/@Read: "
                                    + label
                                    + "（覆写方法体本身就会进实体，别再当更新/序列化片段）",
                            method);
                    continue;
                }

                String body = bodyOf(method);
                if (body == null) {
                    error("@OverrideEntity method body is not accessible: " + label, method);
                    continue;
                }

                ExecutableElement baseMethod = findBaseMethod(base, method);
                if (baseMethod == null) {
                    error(
                            "@OverrideEntity has no matching method on "
                                    + ENTITY_BASE_CLASS
                                    + ": "
                                    + method.fullSignature()
                                    + " ("
                                    + label
                                    + ")",
                            method);
                    continue;
                }

                String signature = method.name() + "/" + method.parameterCount();
                if (!seen.add(signature)) {
                    error(
                            "Duplicate @OverrideEntity for "
                                    + method.fullSignature()
                                    + " in entity '"
                                    + plan.entityName
                                    + "' ("
                                    + label
                                    + ")",
                            method);
                    continue;
                }

                MethodSpec.Builder out = MethodSpec.methodBuilder(method.name())
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC);
                for (AVar param : method.parameters()) {
                    out.addParameter(TypeName.get(param.e.asType()), param.name());
                }
                if (!method.isVoid()) {
                    out.returns(TypeName.get(method.e.getReturnType()));
                }
                out.addJavadoc(
                        "覆写基类 $L（来自组件 $L，@OverrideEntity，自动生成）。\n",
                        method.fullSignature(),
                        component.simpleName());
                out.addCode("$L\n", body);
                entityType.addMethod(out.build());
            }
        }
    }

    /** 在 Entity 基类上找同名同参方法；找不到返回 null。 */
    private ExecutableElement findBaseMethod(TypeElement base, AMethod method) {
        Ar<AVar> params = method.parameters();
        for (Element member : elementUtils.getAllMembers(base)) {
            if (member.getKind() != ElementKind.METHOD) {
                continue;
            }
            ExecutableElement m = (ExecutableElement) member;
            if (!m.getSimpleName().contentEquals(method.name())) {
                continue;
            }
            if (m.getParameters().size() != params.size) {
                continue;
            }
            boolean sameParams = true;
            for (int i = 0; i < params.size; i++) {
                if (!typeUtils.isSameType(
                        typeUtils.erasure(m.getParameters().get(i).asType()),
                        typeUtils.erasure(params.get(i).e.asType()))) {
                    sameParams = false;
                    break;
                }
            }
            if (sameParams) {
                return m;
            }
        }
        return null;
    }

    /**
     * 把组件上除生命周期外的 public 实例方法铺进实体（字段已扁平化，方法体里用短名即可）。
     *
     * <p>跳过：构造、static、abstract、@Updata、@Write、@Read、@OverrideEntity。
     */
    private void injectComponentHelpers(TypeSpec.Builder entityType, EntityPlan plan) {
        if (trees == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (AType component : plan.components) {
            for (AMethod method : component.methods()) {
                if (method.isStatic() || method.isAbstract()) {
                    continue;
                }
                if (method.has(Updata.class)
                        || method.has(Write.class)
                        || method.has(Read.class)
                        || method.has(OverrideEntity.class)) {
                    continue;
                }
                String body = bodyOf(method);
                if (body == null) {
                    continue;
                }
                String name = method.name();
                String signature = name + "/" + method.parameterCount();
                if (!seen.add(signature)) {
                    continue;
                }

                MethodSpec.Builder out = MethodSpec.methodBuilder(name).addModifiers(Modifier.PUBLIC);
                for (AVar param : method.parameters()) {
                    out.addParameter(TypeName.get(param.e.asType()), param.name());
                }
                if (!method.isVoid()) {
                    out.returns(TypeName.get(method.e.getReturnType()));
                }
                out.addJavadoc(
                        "来自组件 $L#$L（自动生成，方法体字段已扁平到实体）。\n",
                        component.simpleName(),
                        name);
                out.addCode("$L\n", body);
                entityType.addMethod(out.build());
            }
        }
    }

    /**
     * 生成按 {@code @Entity.type} 创建实例的工厂：
     * {@code create(type)} 委托 {@code type.create(e)} 填配置；{@code create(team, type, x, y)} 再写阵营坐标。
     */
    private void addTypeFactories(TypeSpec.Builder entityType, EntityPlan plan) {
        ClassName self = ClassName.bestGuess(plan.entityName);
        TypeName type = plan.typeClass;

        entityType.addMethod(
                MethodSpec.methodBuilder("create")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(self)
                        .addParameter(type, "type")
                        .addJavadoc(
                                "按 {@code @Entity.type = $T} 创建实例，并委托类型填充配置（自动生成）。\n",
                                type)
                        .addStatement("$L e = new $L()", plan.entityName, plan.entityName)
                        .addStatement("e.type = type")
                        .beginControlFlow("if (type != null)")
                        .addStatement("type.create(e)")
                        .endControlFlow()
                        .addStatement("return e")
                        .build());

        entityType.addMethod(
                MethodSpec.methodBuilder("create")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(self)
                        .addParameter(ClassName.bestGuess(TEAM_TYPES_CLASS), "team")
                        .addParameter(type, "type")
                        .addParameter(float.class, "x")
                        .addParameter(float.class, "y")
                        .addJavadoc("按类型创建并设置阵营与坐标（自动生成）。\n")
                        .addStatement("$L e = create(type)", plan.entityName)
                        .addStatement("e.team = team")
                        .addStatement("e.x = x")
                        .addStatement("e.y = y")
                        .addStatement("return e")
                        .build());
    }

    /** 实体各组件的源文件里有那些 import（原样搬到生成实体，方法体里才能用短名） */
    private Set<String> componentImports(EntityPlan plan) {
        Set<String> imports = new LinkedHashSet<>();
        if (trees == null) {
            return imports;
        }
        for (AType component : plan.components) {
            TreePath path = trees.getPath(component.e);
            if (path == null || path.getCompilationUnit() == null) {
                continue;
            }
            for (ImportTree importTree : path.getCompilationUnit().getImports()) {
                imports.add(
                        "import "
                                + (importTree.isStatic() ? "static " : "")
                                + importTree.getQualifiedIdentifier()
                                + ";");
            }
        }
        return imports;
    }

    /**
     * 把外来的 import 插到生成源码的 import 块末尾（JavaPoet 只给它自己 {@code $T} 用到的类型生成 import，
     * 手动补的 import 只能自己插）；已有的不重复插。
     */
    private String injectImports(String source, Set<String> extra) {
        if (extra.isEmpty()) {
            return source;
        }

        String[] lines = source.split("\n", -1);
        Set<String> existing = new LinkedHashSet<>();
        int insertAt = 1; // 一行 import 都没有时，至少插在 package 后面
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("import ")) {
                existing.add(lines[i].trim());
                insertAt = i + 1;
            }
        }

        List<String> fresh = new ArrayList<>();
        for (String line : extra) {
            if (!existing.contains(line)) {
                fresh.add(line);
            }
        }
        if (fresh.isEmpty()) {
            return source;
        }

        StringBuilder out = new StringBuilder(source.length() + fresh.size() * 32);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(lines[i]);
            if (i == insertAt - 1) {
                for (String line : fresh) {
                    out.append('\n').append(line);
                }
            }
        }
        return out.toString();
    }

    /**
     * 生成实体的 {@code write(Writes)} / {@code read(Reads)}：按 @Component.index
     * 顺序，把各组件要写/读的东西
     * <b>直接铺进方法体</b>（不生成 write_&lt;组件&gt; / read_&lt;组件&gt; 这种中间方法）。
     *
     * <ul>
     * <li>{@code @Save} 字段 → 直接展开成 {@code w.i(x)} / {@code x = r.i()}；
     * <li>{@code @Write} / {@code @Read} → 方法体原样复制过来，外面套一层花括号当作用域；参数名和实体这边不一样时，
     * 先来一句 {@code Writes
     *       out = w;} 起别名（校验阶段已经拦住 return）。
     * </ul>
     */
    private MethodSpec serializationOverride(boolean writing, EntityPlan plan, boolean callSuper) {
        String param = writing ? "w" : "r";
        String io = writing ? "Writes" : "Reads";
        String name = writing ? "write" : "read";
        ClassName ioClass = writing ? WRITES : READS;

        MethodSpec.Builder method = MethodSpec.methodBuilder(name)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ioClass, param);

        if (callSuper) {
            method.addJavadoc("先让基类写出公共字段（位置 / 血量 / 阵营……），再按 @Component.index 顺序把各组件的读写铺进来（自动生成）。\n");
            method.addStatement("super.$L($L)", name, param);
        } else {
            method.addJavadoc(
                    "按 @Component.index 顺序把各组件的读写直接铺进来（基类 $L 的 $L($L) 还是抽象方法，所以这里不调用 super，自动生成）。\n",
                    simpleName(ENTITY_BASE_CLASS),
                    name,
                    io);
        }

        for (SerialPiece piece : plan.serials) {
            if (piece.methodStyle()) {
                String label = writing ? piece.writeLabel : piece.readLabel;
                String source = writing ? piece.writeParam : piece.readParam;
                String alias = param.equals(source) ? null : ioClass.simpleName() + " " + source + " = " + param + ";";
                boolean guard = writing ? piece.writeHasReturn : piece.readHasReturn;
                method.addCode("// $L（@Component.index = $L）\n", label, piece.index);
                method.addCode(
                        "$L\n", wrappedBody(writing ? piece.writeBody : piece.readBody, alias, label, guard));
            } else {
                method.addCode("// $L（@Component.index = $L，@Save 字段）\n", piece.componentName, piece.index);
                for (VariableElement field : piece.saveFields) {
                    method.addStatement("$L", writing ? writeStatement(field) : readStatement(field));
                }
            }
        }
        return method.build();
    }

    /**
     * 给一个复制过来的方法体套上包装（{@code @Write} / {@code @Read} 都是这个形状）：
     *
     * <pre>{@code
     * {
     * Writes out = w; // 参数名和 w/r 不一样时的别名（没有就不生成）
     * ...方法体原样，内部代码块照旧...
     * }
     * }</pre>
     *
     * <p>方法体里写了 {@code return} 时，包装里还会多一层守卫：正常走完才算完成，中途 return 出去就直接抛异常 （内联之后
     * return
     * 会跳过后面所有组件，所以它在这里等于"出问题了"）。 方法体自己抛异常的话守卫不会插嘴，原异常照常往外抛。
     */
    private String wrappedBody(String body, String aliasLine, String label, boolean guard) {
        List<String> lines = statementLines(body);

        StringBuilder text = new StringBuilder("{\n");
        if (aliasLine != null) {
            text.append("    ").append(aliasLine).append('\n');
        }

        if (guard) {
            text.append("    boolean done = false;\n");
            text.append("    try {\n");
            appendLines(text, lines, "        ");
            text.append("        done = true;\n");
            text.append("    } catch (RuntimeException | Error e) {\n");
            text.append("        done = true;\n");
            text.append("        throw e;\n");
            text.append("    } finally {\n");
            text.append("        if (!done) {\n");
            text.append("            throw new IllegalStateException(\"")
                    .append(label)
                    .append(" IO process return occurred , Shoud stop\");\n");
            text.append("        }\n");
            text.append("    }\n");
        } else {
            appendLines(text, lines, "    ");
        }

        text.append("}");
        return text.toString();
    }

    private void appendLines(StringBuilder text, List<String> lines, String indent) {
        for (String line : lines) {
            if (line.isEmpty()) {
                text.append('\n');
            } else {
                text.append(indent).append(line).append('\n');
            }
        }
    }

    /** 复制来的方法体：去掉最外层大括号、去掉公共缩进和首尾空行；方法体内部的代码块原样保留 */
    private List<String> statementLines(String body) {
        String text = body.trim();
        if (text.startsWith("{") && text.endsWith("}")) {
            text = text.substring(1, text.length() - 1);
        }

        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        while (!lines.isEmpty() && lines.get(0).trim().isEmpty()) {
            lines.remove(0);
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).trim().isEmpty()) {
            lines.remove(lines.size() - 1);
        }

        int indent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            int spaces = 0;
            while (spaces < line.length() && Character.isWhitespace(line.charAt(spaces))) {
                spaces++;
            }
            indent = Math.min(indent, spaces);
        }
        if (indent == Integer.MAX_VALUE) {
            indent = 0;
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) {
                lines.set(i, "");
            } else if (line.length() >= indent) {
                lines.set(i, line.substring(indent));
            } else {
                lines.set(i, line.trim());
            }
        }
        return lines;
    }

    /**
     * @Save 字段的写入语句
     */
    private String writeStatement(VariableElement field) {
        String name = field.getSimpleName().toString();
        switch (field.asType().getKind()) {
            case BOOLEAN:
                return "w.bool(" + name + ")";
            case BYTE:
                return "w.b(" + name + ")";
            case SHORT:
            case CHAR:
                return "w.s(" + name + ")";
            case INT:
                return "w.i(" + name + ")";
            case LONG:
                return "w.l(" + name + ")";
            case FLOAT:
                return "w.f(" + name + ")";
            case DOUBLE:
                return "w.d(" + name + ")";
            default:
                return "w.str(" + name + " == null ? \"\" : " + name + ")";
        }
    }

    /**
     * @Save 字段的读取语句
     */
    private String readStatement(VariableElement field) {
        String name = field.getSimpleName().toString();
        switch (field.asType().getKind()) {
            case BOOLEAN:
                return name + " = r.bool()";
            case BYTE:
                return name + " = r.b()";
            case SHORT:
                return name + " = r.s()";
            case CHAR:
                return name + " = (char)r.s()";
            case INT:
                return name + " = r.i()";
            case LONG:
                return name + " = r.l()";
            case FLOAT:
                return name + " = r.f()";
            case DOUBLE:
                return name + " = r.d()";
            default:
                return name + " = r.str()";
        }
    }

    /** 这个类型能不能直接用 @Save 写出去 */
    private boolean isSaveable(TypeMirror type) {
        switch (type.getKind()) {
            case BOOLEAN:
            case BYTE:
            case SHORT:
            case CHAR:
            case INT:
            case LONG:
            case FLOAT:
            case DOUBLE:
                return true;
            default:
                return isType(type, "java.lang.String");
        }
    }

    /** 类型是否就是指定的那个类（取不到 TypeElement 时按全限定名比） */
    private boolean isType(TypeMirror type, String className) {
        TypeElement element = elementUtils.getTypeElement(className);
        if (element != null) {
            return typeUtils.isSameType(typeUtils.erasure(type), typeUtils.erasure(element.asType()));
        }
        return typeUtils.erasure(type).toString().equals(className);
    }

    /** 基类里的某个方法是不是已经实现好了（不是 abstract） */
    private boolean isBaseMethodConcrete(String name, String parameterClass) {
        if (name.equals("write") && baseWriteConcrete != null) {
            return baseWriteConcrete;
        }
        if (name.equals("read") && baseReadConcrete != null) {
            return baseReadConcrete;
        }

        boolean concrete = false;
        TypeElement base = elementUtils.getTypeElement(ENTITY_BASE_CLASS);
        if (base != null) {
            for (Element member : elementUtils.getAllMembers(base)) {
                if (member.getKind() != ElementKind.METHOD) {
                    continue;
                }
                ExecutableElement method = (ExecutableElement) member;
                if (!method.getSimpleName().contentEquals(name) || method.getParameters().size() != 1) {
                    continue;
                }
                if (!isType(method.getParameters().get(0).asType(), parameterClass)) {
                    continue;
                }
                concrete = !method.getModifiers().contains(Modifier.ABSTRACT);
                break;
            }
        }

        if (name.equals("write")) {
            baseWriteConcrete = concrete;
        } else {
            baseReadConcrete = concrete;
        }
        return concrete;
    }

    /** 元素所在的类型（不是类型里的元素时返回 null） */
    private AType enclosingTypeOf(AElement<?> element) {
        Element parent = element.up();
        return parent instanceof TypeElement ? new AType((TypeElement) parent) : null;
    }

    /** 基类 {@code Entity} 里还没实现的方法，这里生成空实现（带 TODO）， 这样即使以后基类增删抽象方法，生成类也始终能编译。 */
    private void addAbstractStubs(
            TypeSpec.Builder entityType,
            EntityPlan plan,
            boolean generateWrite,
            boolean generateRead,
            boolean updateGenerated) {
        TypeElement base = elementUtils.getTypeElement(ENTITY_BASE_CLASS);
        if (base == null) {
            return;
        }

        Set<String> generated = new HashSet<>();
        for (UpdatePiece piece : plan.pieces) {
            if (!piece.mainTier) {
                generated.add(piece.methodName + "/1");
            }
        }
        generated.add("update/1"); // 轻量档会生成 update(float delta)；没生成时这里补空实现

        Set<String> done = new HashSet<>();
        for (Element member : elementUtils.getAllMembers(base)) {
            if (member.getKind() != ElementKind.METHOD
                    || !member.getModifiers().contains(Modifier.ABSTRACT)) {
                continue;
            }

            ExecutableElement method = (ExecutableElement) member;
            String name = method.getSimpleName().toString();

            StringBuilder signature = new StringBuilder(name).append('/').append(method.getParameters().size());
            for (VariableElement param : method.getParameters()) {
                signature.append('|').append(param.asType().toString());
            }
            if (!done.add(signature.toString())) {
                continue;
            }

            // update(float) 已被轻量档注入生成过，别再补空实现盖掉
            if (updateGenerated && name.equals("update") && method.getParameters().size() == 1) {
                continue;
            }

            // write / read 已经由序列化那一步生成了，别再补空实现盖掉
            if (method.getParameters().size() == 1) {
                TypeMirror only = method.getParameters().get(0).asType();
                if (generateWrite && name.equals("write") && isType(only, WRITES_CLASS)) {
                    continue;
                }
                if (generateRead && name.equals("read") && isType(only, READS_CLASS)) {
                    continue;
                }
            }

            MethodSpec.Builder stub = MethodSpec.methodBuilder(name)
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addJavadoc("TODO 基类尚未提供实现，先留空（自动生成）。\n");

            for (TypeParameterElement tp : method.getTypeParameters()) {
                stub.addTypeVariable(TypeVariableName.get(tp));
            }

            for (VariableElement param : method.getParameters()) {
                stub.addParameter(TypeName.get(param.asType()), param.getSimpleName().toString());
            }

            TypeMirror returnType = method.getReturnType();
            if (returnType.getKind() != TypeKind.VOID) {
                stub.returns(TypeName.get(returnType));
                stub.addStatement("return $L", defaultValue(returnType));
            }

            entityType.addMethod(stub.build());
        }
    }

    private String defaultValue(TypeMirror type) {
        switch (type.getKind()) {
            case BOOLEAN:
                return "false";
            case BYTE:
            case CHAR:
            case SHORT:
            case INT:
            case LONG:
                return "0";
            case FLOAT:
            case DOUBLE:
                return "0f";
            default:
                return "null";
        }
    }

    /** 按系统分组：系统名 -> 实体名 -> 更新片段（组件系统 / Systems 生成共用） */
    private Map<String, Map<String, List<UpdatePiece>>> systemsByEntity(List<EntityPlan> plans) {
        Map<String, Map<String, List<UpdatePiece>>> bySystem = new LinkedHashMap<>();

        for (EntityPlan plan : plans) {
            for (UpdatePiece piece : plan.pieces) {
                if (piece.mainTier) {
                    continue; // 轻量档不生成独立入口，走实体的 update()
                }
                bySystem
                        .computeIfAbsent(piece.proc, key -> new LinkedHashMap<>())
                        .computeIfAbsent(piece.entityName, key -> new ArrayList<>())
                        .add(piece);
            }
        }
        return bySystem;
    }

    /** 生成 EntityArs：为每个实体生成一个 EntityAr（实体集合），供系统遍历 */
    private void generateEntityArs(List<EntityPlan> plans) {
        if (plans.isEmpty()) {
            return;
        }

        ClassName entityAr = ClassName.get("caliniya.vergvoke.base.game", "EntityAr");

        TypeSpec.Builder ars = TypeSpec.classBuilder("EntityArs")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("由注解处理器生成：各实体的集合（EntityAr），系统遍历它来更新实体。\n");

        ars.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

        for (EntityPlan plan : plans) {
            ars.addField(
                    FieldSpec.builder(
                            ParameterizedTypeName.get(
                                    entityAr, ClassName.get(GENERATED_PACKAGE, plan.entityName)),
                            plan.entityName,
                            Modifier.PUBLIC,
                            Modifier.STATIC,
                            Modifier.FINAL)
                            .addJavadoc("$L 实体的集合。\n", plan.entityName)
                            .initializer("new $T<>()", entityAr)
                            .build());
        }

        try {
            JavaFile.builder(GENERATED_PACKAGE, ars.build()).build().writeTo(filer);
        } catch (IOException e) {
            error("Failed to generate " + GENERATED_PACKAGE + ".EntityArs: " + e.getMessage());
        }
    }

    /** 生成"组件系统"：每个有独立档组件的系统一个类——继承 System，update 遍历实体、直接调用该系统的组件更新 */
    private void generateComponentSystems(Map<String, Map<String, List<UpdatePiece>>> bySystem) {
        if (bySystem.isEmpty()) {
            return;
        }

        ClassName systemBase = ClassName.get("caliniya.vergvoke.system", "System");
        ClassName entityArs = ClassName.get(GENERATED_PACKAGE, "EntityArs");

        for (String systemName : bySystem.keySet()) {
            TypeSpec.Builder componentSystem = TypeSpec.classBuilder(systemName)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .superclass(
                            ParameterizedTypeName.get(
                                    systemBase, ClassName.get(GENERATED_PACKAGE, systemName)))
                    .addJavadoc("由注解处理器生成：$L 的组件系统——遍历实体，直接调用该系统的组件更新。\n", systemName);

            MethodSpec.Builder update = MethodSpec.methodBuilder("update")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(float.class, "delta")
                    .addJavadoc("遍历该系统的实体集合，逐个调用组件更新（自动生成）。\n");

            for (String entityName : bySystem.get(systemName).keySet()) {
                List<UpdatePiece> pieces = new ArrayList<>(bySystem.get(systemName).get(entityName));
                pieces.sort(
                        Comparator.comparingInt((UpdatePiece piece) -> piece.index)
                                .thenComparingInt(piece -> piece.order));

                update.beginControlFlow(
                        "for ($T e : $T.$L)",
                        ClassName.get(GENERATED_PACKAGE, entityName),
                        entityArs,
                        entityName);
                for (UpdatePiece piece : pieces) {
                    update.addStatement("e.$L($L)", piece.methodName, DELTA_NAME);
                }
                update.endControlFlow();
            }

            componentSystem.addMethod(update.build());

            try {
                JavaFile.builder(GENERATED_PACKAGE, componentSystem.build()).build().writeTo(filer);
            } catch (IOException e) {
                error("Failed to generate " + GENERATED_PACKAGE + "." + systemName + ": " + e.getMessage());
            }
        }
    }

    /** 生成 Systems：系统总览——@SystemDef 线程视图 + 组件系统数组 + 统一更新入口（update / updateAll） */
    private void generateSystems(
            List<EntityPlan> plans, Map<String, Map<String, List<UpdatePiece>>> bySystem) {
        ClassName systemBase = ClassName.get("caliniya.vergvoke.system", "System");
        ClassName ar = ClassName.get("caliniya.vergvoke.base.tool", "Ar");
        ClassName entityArs = ClassName.get(GENERATED_PACKAGE, "EntityArs");

        TypeSpec.Builder systems = TypeSpec.classBuilder("Systems")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("由注解处理器生成：系统总览。\n")
                .addJavadoc("@SystemDef 的线程视图（Class 清单）＋ 组件系统数组与统一更新入口（update / updateAll）。\n");

        systems.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

        List<AType> systemDefs = new ArrayList<>();
        for (AType system : types(SystemDef.class)) {
            systemDefs.add(system);
        }

        // ---- 线程视图：按线程分组（Class 清单），main 优先、组内按 index 升序 ----
        Map<String, List<AType>> byThread = new LinkedHashMap<>();
        for (AType system : systemDefs) {
            byThread
                    .computeIfAbsent(system.annotation(SystemDef.class).thread(), key -> new ArrayList<>())
                    .add(system);
        }

        if (!byThread.isEmpty()) {
            List<String> threadNames = new ArrayList<>(byThread.keySet());
            threadNames.sort(
                    (a, b) -> {
                        if (a.equals(MAIN_SYSTEM)) {
                            return b.equals(MAIN_SYSTEM) ? 0 : -1;
                        }
                        if (b.equals(MAIN_SYSTEM)) {
                            return 1;
                        }
                        return a.compareTo(b);
                    });

            TypeName classOfWildcard = ParameterizedTypeName.get(
                    ClassName.get(Class.class), WildcardTypeName.subtypeOf(TypeName.OBJECT));

            for (String thread : threadNames) {
                List<AType> group = new ArrayList<>(byThread.get(thread));
                group.sort(
                        Comparator.comparingInt(
                                (AType system) -> system.annotation(SystemDef.class).index()));

                CodeBlock.Builder array = CodeBlock.builder().add("{ ");
                for (int i = 0; i < group.size(); i++) {
                    if (i > 0) {
                        array.add(", ");
                    }
                    array.add("$T.class", ClassName.bestGuess(group.get(i).fullName()));
                }
                array.add(" }");

                systems.addField(
                        FieldSpec.builder(ArrayTypeName.of(classOfWildcard), thread)
                                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                .addJavadoc("线程 {@code $L} 的 @SystemDef 类（按 index 升序）。\n", thread)
                                .initializer(array.build())
                                .build());
            }

            CodeBlock.Builder threadsArray = CodeBlock.builder().add("{ ");
            for (int i = 0; i < threadNames.size(); i++) {
                if (i > 0) {
                    threadsArray.add(", ");
                }
                threadsArray.add("$S", threadNames.get(i));
            }
            threadsArray.add(" }");

            systems.addField(
                    FieldSpec.builder(ArrayTypeName.of(ClassName.get(String.class)), "THREADS")
                            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                            .addJavadoc("线程清单（与上面的字段一一对应）。\n")
                            .initializer(threadsArray.build())
                            .build());
        }

        // ---- 组件系统数组：按 @SystemDef.index 升序（找不到声明的排最后，按名字兜底）----
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (AType system : systemDefs) {
            SystemDef def = system.annotation(SystemDef.class);
            indexes.putIfAbsent(def.name(), def.index());
        }

        List<String> ordered = new ArrayList<>(bySystem.keySet());
        ordered.sort(
                (a, b) -> {
                    int ia = indexes.getOrDefault(a, Integer.MAX_VALUE);
                    int ib = indexes.getOrDefault(b, Integer.MAX_VALUE);
                    return ia != ib ? Integer.compare(ia, ib) : a.compareTo(b);
                });

        TypeName systemWildcard = ParameterizedTypeName.get(
                systemBase, WildcardTypeName.subtypeOf(TypeName.OBJECT));

        systems.addField(
                FieldSpec.builder(ParameterizedTypeName.get(ar, systemWildcard), "systems",
                        Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addJavadoc("全部组件系统（按 @SystemDef.index 升序）。\n")
                        .initializer("new $T<>()", ar)
                        .build());

        if (!ordered.isEmpty()) {
            CodeBlock.Builder init = CodeBlock.builder();
            for (String name : ordered) {
                init.addStatement("systems.add(new $T())", ClassName.get(GENERATED_PACKAGE, name));
            }
            systems.addStaticBlock(init.build());
        }

        systems.addMethod(
                MethodSpec.methodBuilder("update")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addParameter(float.class, "delta")
                        .addJavadoc("按 @SystemDef.index 依次调用所有组件系统。\n")
                        .addCode("for ($T<?> sys : systems) {\n$>sys.update(delta);\n$<}\n", systemBase)
                        .build());

        MethodSpec.Builder updateAll = MethodSpec.methodBuilder("updateAll")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(float.class, "delta")
                .addJavadoc("按划定的帧顺序总驱动：基础更新 → 组件系统 → 实体更新（帧时间倍率统一传入）。\n");

        if (!plans.isEmpty()) {
            updateAll.addCode("// 基础更新（热量 / 能量 + 能力 / 模组）\n");
            for (EntityPlan plan : plans) {
                updateAll.beginControlFlow(
                        "for ($T e : $T.$L)",
                        ClassName.get(GENERATED_PACKAGE, plan.entityName),
                        entityArs,
                        plan.entityName);
                updateAll.addStatement("e.sync(delta)");
                updateAll.endControlFlow();
            }
        }

        updateAll.addCode("// 组件系统（按 @SystemDef.index）\nupdate(delta);\n");

        if (!plans.isEmpty()) {
            updateAll.addCode("// 实体自身更新（轻量档注入在 update(float) 里）\n");
            for (EntityPlan plan : plans) {
                updateAll.beginControlFlow(
                        "for ($T e : $T.$L)",
                        ClassName.get(GENERATED_PACKAGE, plan.entityName),
                        entityArs,
                        plan.entityName);
                updateAll.addStatement("e.update(delta)");
                updateAll.endControlFlow();
            }
        }

        systems.addMethod(updateAll.build());

        try {
            JavaFile.builder(GENERATED_PACKAGE, systems.build()).build().writeTo(filer);
        } catch (IOException e) {
            error("Failed to generate " + GENERATED_PACKAGE + ".Systems: " + e.getMessage());
        }
    }

    /** 一个实体校验通过后算好的生成计划 */
    private static final class EntityPlan {
        final AType entity;
        final String entityName;

        /** @Entity.type()：类型目标 class（必须实现 EntityType） */
        final ClassName typeClass;

        final List<AType> components;
        final ObjectSet<String> componentNames;
        final Map<String, VariableElement> injectedFields;
        final List<UpdatePiece> pieces;
        final List<SerialPiece> serials;

        EntityPlan(
                AType entity,
                String entityName,
                ClassName typeClass,
                List<AType> components,
                ObjectSet<String> componentNames,
                Map<String, VariableElement> injectedFields,
                List<UpdatePiece> pieces,
                List<SerialPiece> serials) {
            this.entity = entity;
            this.entityName = entityName;
            this.typeClass = typeClass;
            this.components = components;
            this.componentNames = componentNames;
            this.injectedFields = injectedFields;
            this.pieces = pieces;
            this.serials = serials;
        }
    }

    /**
     * 一个组件的序列化片段：{@code @Save} 字段（已按 index 排好）或 {@code @Write} / {@code @Read} 方法体
     */
    private static final class SerialPiece {
        final String componentName;
        final int index;
        final int order;
        final List<VariableElement> saveFields;
        final String writeBody;
        final String writeParam;
        final String writeLabel;

        /** true = {@code @Write} 方法体里有 return，需要套一层"中途 return 就抛异常"的守卫 */
        final boolean writeHasReturn;

        final String readBody;
        final String readParam;
        final String readLabel;

        /** true = {@code @Read} 方法体里有 return */
        final boolean readHasReturn;

        SerialPiece(
                String componentName,
                int index,
                int order,
                List<VariableElement> saveFields,
                String writeBody,
                String writeParam,
                String writeLabel,
                boolean writeHasReturn,
                String readBody,
                String readParam,
                String readLabel,
                boolean readHasReturn) {
            this.componentName = componentName;
            this.index = index;
            this.order = order;
            this.saveFields = saveFields;
            this.writeBody = writeBody;
            this.writeParam = writeParam;
            this.writeLabel = writeLabel;
            this.writeHasReturn = writeHasReturn;
            this.readBody = readBody;
            this.readParam = readParam;
            this.readLabel = readLabel;
            this.readHasReturn = readHasReturn;
        }

        /** true = 走 {@code @Write} / {@code @Read} 方法体（否则是 {@code @Save} 字段） */
        boolean methodStyle() {
            return writeBody != null;
        }
    }

    /** 一条要注入实体的 @Updata 片段 */
    private static final class UpdatePiece {
        final int index;
        final int order;
        final String label;
        final String methodName;
        final String body;
        final String proc;
        final String entityName;

        /** true = 轻量档（proc = "main" 或未指定）：方法体直接铺进实体 update() */
        final boolean mainTier;

        UpdatePiece(
                int index,
                int order,
                String label,
                String methodName,
                String body,
                String proc,
                String entityName,
                boolean mainTier) {
            this.index = index;
            this.order = order;
            this.label = label;
            this.methodName = methodName;
            this.body = body;
            this.proc = proc;
            this.entityName = entityName;
            this.mainTier = mainTier;
        }
    }
}
