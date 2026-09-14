package caliniya.vergvoke.annotation.ecs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.annotation.Processor;
import caliniya.vergvoke.annotation.tool.AElement;
import caliniya.vergvoke.annotation.tool.AMethod;
import caliniya.vergvoke.annotation.tool.AType;
import caliniya.vergvoke.annotation.tool.AVar;
import caliniya.vergvoke.base.anno.auto.AnnoProc;
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
 *   <li><b>实体类</b>（{@code caliniya.vergvoke.base.ecs.<name>Entity}）：继承
 *       {@code caliniya.vergvoke.base.game.Entity}，字段为各组件的扁平字段；按
 *       {@code @Component(proc=...)} 分两档注入 {@code @Updata}：
 *       <ul>
 *         <li><b>proc = "main"（或留空）</b>：轻量更新（移动、生产…），方法体<b>直接铺进实体的
 *             {@code update()}</b>，顺序由 {@code @Component.index} 决定（该档必须提供非 0 的 index）；
 *         <li><b>proc = 其他系统名</b>：需要"同阶段一致"或较重的更新（开火、寻路、AI…），
 *             生成独立方法 {@code update_<组件简单名>()}，由该系统在自己阶段调用。
 *       </ul>
 *   <li><b>系统分发类</b>（{@code caliniya.vergvoke.base.ecs.ECUpdates}）：
 *       {@code update_<系统名>(<实体> e)}，按 {@code @Component.index} 顺序调用属于该系统的组件更新。
 *   <li><b>序列化</b>：实体按 {@code @Component.index} 顺序生成 {@code write(Writes)} / {@code read(Reads)}：
 *       组件内部按 {@code @Save.index} 写字段；标了 {@code @Write} / {@code @Read} 的组件则把方法体
 *       复制进 {@code write_<组件简单名>()} / {@code read_<组件简单名>()}。
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
        "caliniya.vergvoke.annotation.Annotations.Write",
        "caliniya.vergvoke.annotation.Annotations.Read",
        "caliniya.vergvoke.annotation.Annotations.SystemDef",
        "caliniya.vergvoke.annotation.Annotations.ThreadDef"
})
public class ECProcessor extends Processor {

    private static final String GENERATED_PACKAGE = "caliniya.vergvoke.base.ecs";

    /** 生成的实体继承的基类 */
    private static final String ENTITY_BASE_CLASS = "caliniya.vergvoke.base.game.Entity";

    /** 轻量档：proc 为空或等于它时，更新逻辑直接铺进实体 update() */
    private static final String MAIN_SYSTEM = "main";

    /** 保留线程名：直接表示"主线程"，不需要 @ThreadDef 声明 */
    private static final Set<String> RESERVED_THREADS = Set.of("main", "test");

    /** 序列化用的 IO 类型（arc） */
    private static final String WRITES_CLASS = "arc.util.io.Writes";
    private static final String READS_CLASS = "arc.util.io.Reads";
    private static final ClassName WRITES = ClassName.get("arc.util.io", "Writes");
    private static final ClassName READS = ClassName.get("arc.util.io", "Reads");

    /** 每种类型的实体都有哪些组件（生成成功后填充） */
    public ObjectMap<String, ObjectSet<String>> ECMap = new ObjectMap<>();

    /** 基类的 write / read 是不是已经实现好了（是的话生成的 write / read 会先调 super） */
    private Boolean baseWriteConcrete, baseReadConcrete;

    {
        maxRounds = 1;
    }

    @SuppressWarnings("unused")
    @Override
    protected void process() {
        Map<String, AType> components = componentTypes();
        Map<String, AType> systems = systemTypes();
        Set<String> threads = declaredThreads();

        boolean valid = validateImportTargets(components);
        valid &= validateUpdateTargets(components);
        valid &= validateSaveTargets(components);
        valid &= validateSerializationMethods(components);
        valid &= validateComponentNames(components);
        valid &= validateComponentIndexes(components);
        valid &= validateProcs(components, systems);
        valid &= validateSystems(systems, threads);

        // 1) 先把所有实体算成"生成计划"，过程中只报错、不写文件
        List<EntityPlan> plans = new ArrayList<>();
        for (AType entity : types(Entity.class)) {
            EntityPlan plan = planEntity(entity, components);
            if (plan == null) {
                valid = false;
                continue;
            }
            plans.add(plan);
        }

        // 2) 有任何一处校验失败就整体不生成
        if (!valid) {
            return;
        }

        // 3) 全部通过，统一生成
        for (EntityPlan plan : plans) {
            generateEntity(plan);
        }
        generateSystemDispatch(plans);
    }

    private Map<String, AType> componentTypes() {
        Map<String, AType> components = new LinkedHashMap<>();
        for (AType component : types(Component.class)) {
            components.put(component.fullName(), component);
        }
        return components;
    }

    private Map<String, AType> systemTypes() {
        Map<String, AType> systems = new LinkedHashMap<>();
        for (AType system : types(SystemDef.class)) {
            String name = system.annotation(SystemDef.class).name();
            AType previous = systems.putIfAbsent(name, system);
            if (previous != null) {
                error("Duplicate system name '" + name + "': " + previous.fullName(), system);
            }
        }
        return systems;
    }

    /** 合法的线程名 = 声明的 @ThreadDef + 保留名（main / test） */
    private Set<String> declaredThreads() {
        Set<String> threads = new LinkedHashSet<>(RESERVED_THREADS);
        for (AType thread : types(ThreadDef.class)) {
            threads.add(thread.annotation(ThreadDef.class).name());
        }
        return threads;
    }

    /** @Import 只能出现在 @Component 的字段上 */
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

    /** @Updata 只能出现在 @Component 的方法上，且一个组件最多一个 */
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
                valid = false;
            }
        }
        return valid;
    }

    /**
     * 任何组件都必须提供非 0 的 index：它既决定实体的序列化顺序（组件之间），
     * 也决定轻量档（proc = main / 空）的更新注入顺序。
     */
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
                valid = false;
            }
        }
        return valid;
    }

    /** @Save 只能出现在 @Component 的字段上：不能 static、不能和 @Import 混用、类型要能直接写、组件内 index 唯一 */
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
                error("@Save cannot be used on a static field: " + owner.simpleName() + "#" + field.name(), field);
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
                valid = false;
                continue;
            }

            int index = field.annotation(Save.class).index();
            Map<Integer, String> indexes = byComponent.computeIfAbsent(
                    owner.fullName(), key -> new LinkedHashMap<>());
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
                valid = false;
            }
        }
        return valid;
    }

    /** @Write / @Read 只能出现在 @Component 的方法上：签名要对、要成对、不能和 @Save 混用 */
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
                        "@Component '" + component.simpleName() + "' has " + entry.getValue()
                                + " but no matching @Read method: they must be used in pairs",
                        component);
                valid = false;
            }
            if (withSaveFields.contains(entry.getKey())) {
                error(
                        "@Component '"
                                + component.simpleName()
                                + "' cannot mix @Save fields with @Write / @Read methods: pick one style",
                        component);
                valid = false;
            }
        }

        for (Map.Entry<String, String> entry : reads.entrySet()) {
            if (!writes.containsKey(entry.getKey())) {
                AType component = components.get(entry.getKey());
                error(
                        "@Component '" + component.simpleName() + "' has " + entry.getValue()
                                + " but no matching @Write method: they must be used in pairs",
                        component);
                valid = false;
            }
            if (withSaveFields.contains(entry.getKey()) && !writes.containsKey(entry.getKey())) {
                AType component = components.get(entry.getKey());
                error(
                        "@Component '"
                                + component.simpleName()
                                + "' cannot mix @Save fields with @Write / @Read methods: pick one style",
                        component);
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

            if (method.isStatic()) {
                error(annotationName + " method cannot be static: " + label, method);
                valid = false;
                continue;
            }
            if (method.isAbstract()) {
                error(annotationName + " method cannot be abstract: " + label, method);
                valid = false;
                continue;
            }
            if (!method.isVoid()) {
                error(annotationName + " method must return void: " + label, method);
                valid = false;
                continue;
            }
            if (method.parameterCount() != 1 || !isType(method.parameters().first().type(), parameterClass)) {
                error(
                        annotationName + " method must take exactly one "
                                + parameterClass
                                + " parameter: "
                                + label + "(" + parameterClass + ")",
                        method);
                valid = false;
                continue;
            }
            if (bodyOf(method) == null) {
                error(annotationName + " method body is not accessible: " + label, method);
                valid = false;
                continue;
            }

            String previous = found.putIfAbsent(owner.fullName(), method.name());
            if (previous != null) {
                error(
                        "@Component '" + owner.simpleName() + "' can only declare one "
                                + annotationName + " method",
                        method);
                valid = false;
            }
        }

        return valid;
    }

    /** 非轻量档组件的 proc 必须对应一个真实存在的 @SystemDef（否则没人会调用它） */
    private boolean validateProcs(Map<String, AType> components, Map<String, AType> systems) {
        boolean valid = true;
        for (AType component : components.values()) {
            String proc = component.annotation(Component.class).proc();
            if (isMainTier(proc) || systems.containsKey(proc)) {
                continue;
            }
            error(
                    "@Component '"
                            + component.simpleName()
                            + "' declares proc = '"
                            + proc
                            + "', but no @SystemDef with that name exists",
                    component);
            valid = false;
        }
        return valid;
    }

    /** @SystemDef.thread 必须是已声明的 @ThreadDef 名字或保留名（main / test） */
    private boolean validateSystems(Map<String, AType> systems, Set<String> threads) {
        boolean valid = true;
        for (AType system : systems.values()) {
            SystemDef def = system.annotation(SystemDef.class);
            if (!threads.contains(def.thread())) {
                error(
                        "@SystemDef '"
                                + def.name()
                                + "' uses unknown thread '"
                                + def.thread()
                                + "': declare it with @ThreadDef, or use 'main' / 'test' for the main thread",
                        system);
                valid = false;
            }
        }
        return valid;
    }

    private boolean isMainTier(String proc) {
        return proc == null || proc.isEmpty() || MAIN_SYSTEM.equals(proc);
    }

    /**
     * 校验一个实体并算出它的生成计划；返回 null 表示这个实体没通过校验（错误已经报出）。
     */
    private EntityPlan planEntity(AType entity, Map<String, AType> components) {
        String entityName = entity.annotation(Entity.class).name() + "Entity";
        if (!SourceVersion.isName(entityName)) {
            error("Invalid generated entity name: " + entityName, entity);
            return null;
        }

        boolean valid = true;

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

        // --- 收集字段 + 判重名（@Import 不注入实体，单独记下来校验） ---
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
                if (method.parameterCount() != 0) {
                    error("@Updata method must not have parameters: " + label, method);
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

        return new EntityPlan(entity, entityName, componentNames, injectedFields, pieces, serials);
    }

    private String safeName(AType entity) {
        Entity def = entity.annotation(Entity.class);
        return def == null ? entity.simpleName() : def.name();
    }

    /**
     * 算出一个组件要写/读什么：{@code @Save} 字段（按 index 排好）或 {@code @Write} / {@code @Read} 方法体。
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
        for (AMethod method : component.methods()) {
            if (method.has(Write.class)) {
                writeBody = bodyOf(method);
                writeParam = method.parameters().first().name();
                writeLabel = component.simpleName() + "#" + method.name();
            } else if (method.has(Read.class)) {
                readBody = bodyOf(method);
                readParam = method.parameters().first().name();
                readLabel = component.simpleName() + "#" + method.name();
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
                readBody,
                readParam,
                readLabel);
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

    /** 按生成计划写出实体类 */
    private void generateEntity(EntityPlan plan) {
        TypeSpec.Builder entityType =
                TypeSpec.classBuilder(plan.entityName)
                        .addModifiers(Modifier.PUBLIC)
                        .superclass(ClassName.bestGuess(ENTITY_BASE_CLASS));

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
            MethodSpec.Builder method =
                    MethodSpec.methodBuilder(piece.methodName)
                            .addModifiers(Modifier.PUBLIC)
                            .addJavadoc(
                                    "来自 {@code @Updata}：$L（由 $L 处理，自动生成）。\n",
                                    piece.label,
                                    piece.proc);
            method.addCode("$L\n", piece.body);
            entityType.addMethod(method.build());
        }

        // 实体 update()：轻量档片段按 @Component.index 顺序直接铺进来
        boolean hasMain = false;
        for (UpdatePiece piece : plan.pieces) {
            if (piece.mainTier) {
                hasMain = true;
                break;
            }
        }

        if (hasMain) {
            MethodSpec.Builder update =
                    MethodSpec.methodBuilder("update")
                            .addModifiers(Modifier.PUBLIC)
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

        // 序列化：write_<组件简单名>() / read_<组件简单名>() + 按 @Component.index 排好的 write()/read()
        addSerializationMethods(entityType, plan);
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
        addAbstractStubs(entityType, plan, generateWrite, generateRead);

        try {
            JavaFile.builder(GENERATED_PACKAGE, entityType.build()).build().writeTo(filer);
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
     * 生成每个组件的序列化方法：{@code @Save} 字段直接展开成 {@code w.i(x)} / {@code x = r.i()}，
     * {@code @Write} / {@code @Read} 的方法体原样复制过来（参数名保持组件里写的样子）。
     */
    private void addSerializationMethods(TypeSpec.Builder entityType, EntityPlan plan) {
        for (SerialPiece piece : plan.serials) {
            if (piece.methodStyle()) {
                entityType.addMethod(
                        MethodSpec.methodBuilder("write_" + piece.componentName)
                                .addModifiers(Modifier.PUBLIC)
                                .addJavadoc(
                                        "来自 {@code @Write}：$L（@Component.index = $L，自动生成）。\n",
                                        piece.writeLabel,
                                        piece.index)
                                .addParameter(WRITES, piece.writeParam)
                                .addCode("$L\n", piece.writeBody)
                                .build());
                entityType.addMethod(
                        MethodSpec.methodBuilder("read_" + piece.componentName)
                                .addModifiers(Modifier.PUBLIC)
                                .addJavadoc(
                                        "来自 {@code @Read}：$L（@Component.index = $L，自动生成）。\n",
                                        piece.readLabel,
                                        piece.index)
                                .addParameter(READS, piece.readParam)
                                .addCode("$L\n", piece.readBody)
                                .build());
            } else {
                MethodSpec.Builder write =
                        MethodSpec.methodBuilder("write_" + piece.componentName)
                                .addModifiers(Modifier.PUBLIC)
                                .addJavadoc(
                                        "来自 {@code @Save} 字段（@Component.index = $L，自动生成）。\n",
                                        piece.index)
                                .addParameter(WRITES, "w");
                MethodSpec.Builder read =
                        MethodSpec.methodBuilder("read_" + piece.componentName)
                                .addModifiers(Modifier.PUBLIC)
                                .addJavadoc(
                                        "来自 {@code @Save} 字段（@Component.index = $L，自动生成）。\n",
                                        piece.index)
                                .addParameter(READS, "r");
                for (VariableElement field : piece.saveFields) {
                    write.addStatement("$L", writeStatement(field));
                    read.addStatement("$L", readStatement(field));
                }
                entityType.addMethod(write.build());
                entityType.addMethod(read.build());
            }
        }
    }

    /** 生成实体的 {@code write(Writes)} / {@code read(Reads)}：按 @Component.index 顺序调用各组件的序列化方法 */
    private MethodSpec serializationOverride(boolean writing, EntityPlan plan, boolean callSuper) {
        String param = writing ? "w" : "r";
        String io = writing ? "Writes" : "Reads";
        String name = writing ? "write" : "read";

        MethodSpec.Builder method =
                MethodSpec.methodBuilder(name)
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(writing ? WRITES : READS, param);

        if (callSuper) {
            method.addJavadoc(
                    "先让基类写出公共字段（位置 / 血量 / 阵营……），再按 @Component.index 顺序处理各组件（自动生成）。\n");
            method.addStatement("super.$L($L)", name, param);
        } else {
            method.addJavadoc(
                    "按 @Component.index 顺序处理各组件（基类 $L 的 $L($L) 还是抽象方法，所以这里不调用 super，自动生成）。\n",
                    simpleName(ENTITY_BASE_CLASS),
                    name,
                    io);
        }

        for (SerialPiece piece : plan.serials) {
            method.addStatement("$L_$L($L)", name, piece.componentName, param);
        }
        return method.build();
    }

    /** @Save 字段的写入语句 */
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

    /** @Save 字段的读取语句 */
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

    /**
     * 基类 {@code Entity} 里还没实现的方法，这里生成空实现（带 TODO），
     * 这样即使以后基类增删抽象方法，生成类也始终能编译。
     */
    private void addAbstractStubs(
            TypeSpec.Builder entityType, EntityPlan plan, boolean generateWrite, boolean generateRead) {
        TypeElement base = elementUtils.getTypeElement(ENTITY_BASE_CLASS);
        if (base == null) {
            return;
        }

        Set<String> generated = new HashSet<>();
        for (UpdatePiece piece : plan.pieces) {
            if (!piece.mainTier) {
                generated.add(piece.methodName + "/0");
            }
        }
        generated.add("update/1");   // update(float) 依旧要补（生成的是无参 update()）

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

            boolean isGeneratedUpdate = name.equals("update") && method.getParameters().isEmpty();
            if (isGeneratedUpdate) {
                continue;   // 生成的无参 update() 不覆盖 update(float)，但也不会重复生成
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

            MethodSpec.Builder stub =
                    MethodSpec.methodBuilder(name)
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

    /** 生成 ECUpdates：系统名 -> 该系统的组件更新入口 */
    private void generateSystemDispatch(List<EntityPlan> plans) {
        Map<String, Map<String, List<UpdatePiece>>> bySystem = new LinkedHashMap<>();

        for (EntityPlan plan : plans) {
            for (UpdatePiece piece : plan.pieces) {
                if (piece.mainTier) {
                    continue;   // 轻量档不生成独立入口，走实体的 update()
                }
                bySystem
                        .computeIfAbsent(piece.proc, key -> new LinkedHashMap<>())
                        .computeIfAbsent(piece.entityName, key -> new ArrayList<>())
                        .add(piece);
            }
        }

        if (bySystem.isEmpty()) {
            return;
        }

        TypeSpec.Builder registry =
                TypeSpec.classBuilder("ECUpdates")
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                        .addJavadoc("由注解处理器生成：系统 -&gt; 它负责的组件更新入口。\n");

        registry.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

        for (Map.Entry<String, Map<String, List<UpdatePiece>>> systemEntry : bySystem.entrySet()) {
            String systemName = systemEntry.getKey();

            for (Map.Entry<String, List<UpdatePiece>> entityEntry : systemEntry.getValue().entrySet()) {
                String entityName = entityEntry.getKey();
                List<UpdatePiece> pieces = new ArrayList<>(entityEntry.getValue());
                pieces.sort(
                        Comparator.comparingInt((UpdatePiece piece) -> piece.index)
                                .thenComparingInt(piece -> piece.order));

                MethodSpec.Builder method =
                        MethodSpec.methodBuilder("update_" + systemName)
                                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                                .addJavadoc("proc = $L 的组件更新（按 @Component.index 排序，自动生成）。\n", systemName)
                                .addParameter(ClassName.get(GENERATED_PACKAGE, entityName), "e");

                for (UpdatePiece piece : pieces) {
                    method.addStatement("e.$L()", piece.methodName);
                }

                registry.addMethod(method.build());
            }
        }

        try {
            JavaFile.builder(GENERATED_PACKAGE, registry.build()).build().writeTo(filer);
        } catch (IOException e) {
            error("Failed to generate " + GENERATED_PACKAGE + ".ECUpdates: " + e.getMessage());
        }
    }

    /** 一个实体校验通过后算好的生成计划 */
    private static final class EntityPlan {
        final AType entity;
        final String entityName;
        final ObjectSet<String> componentNames;
        final Map<String, VariableElement> injectedFields;
        final List<UpdatePiece> pieces;
        final List<SerialPiece> serials;

        EntityPlan(
                AType entity,
                String entityName,
                ObjectSet<String> componentNames,
                Map<String, VariableElement> injectedFields,
                List<UpdatePiece> pieces,
                List<SerialPiece> serials) {
            this.entity = entity;
            this.entityName = entityName;
            this.componentNames = componentNames;
            this.injectedFields = injectedFields;
            this.pieces = pieces;
            this.serials = serials;
        }
    }

    /** 一个组件的序列化片段：{@code @Save} 字段（已按 index 排好）或 {@code @Write} / {@code @Read} 方法体 */
    private static final class SerialPiece {
        final String componentName;
        final int index;
        final int order;
        final List<VariableElement> saveFields;
        final String writeBody;
        final String writeParam;
        final String writeLabel;
        final String readBody;
        final String readParam;
        final String readLabel;

        SerialPiece(
                String componentName,
                int index,
                int order,
                List<VariableElement> saveFields,
                String writeBody,
                String writeParam,
                String writeLabel,
                String readBody,
                String readParam,
                String readLabel) {
            this.componentName = componentName;
            this.index = index;
            this.order = order;
            this.saveFields = saveFields;
            this.writeBody = writeBody;
            this.writeParam = writeParam;
            this.writeLabel = writeLabel;
            this.readBody = readBody;
            this.readParam = readParam;
            this.readLabel = readLabel;
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
