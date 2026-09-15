package caliniya.vergvoke.annotation.ecs;

import java.io.IOException;
import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import com.squareup.javapoet.*;
import caliniya.vergvoke.annotation.Processor;
import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.annotation.tool.*;
import caliniya.vergvoke.base.anno.auto.*;

/**
 * 系统与线程侧的注解处理器：
 *
 * <ul>
 *   <li>收集并校验 {@code @SystemDef} / {@code @ThreadDef}（含与 {@code @Component.proc} 的交叉校验）；
 *   <li>生成调度蓝图 {@code Systems}：线程 → 按 index 升序的系统清单（Class 字面量，编译期安全）。
 * </ul>
 *
 * <p>组件 / 实体 / 序列化由 {@code ECProcessor} 负责；本处理器只管"系统与线程"。
 */
@AnnoProc
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes({
        "caliniya.vergvoke.annotation.Annotations.SystemDef",
        "caliniya.vergvoke.annotation.Annotations.ThreadDef",
        "caliniya.vergvoke.annotation.Annotations.Component"
})
public class SystemProcessor extends Processor {

    private static final String GENERATED_PACKAGE = "caliniya.vergvoke.base.ecs";

    /** 保留线程名：直接表示"主线程"，不需要 @ThreadDef 声明 */
    private static final Set<String> RESERVED_THREADS = Set.of("main", "test");

    /** 轻量档：proc 为空或等于它时，更新逻辑铺进实体 update()，不需要系统 */
    private static final String MAIN_SYSTEM = "main";

    /** 本轮校验是否通过（收集/校验过程中置 false） */
    private boolean valid;

    {
        maxRounds = 1;
    }

    @Override
    protected void process() {
        valid = true;

        Map<String, AType> systems = systemTypes();
        Map<String, AType> threads = threadTypes();

        valid &= validateSystems(systems, threads);
        valid &= validateSystemBases(systems);
        valid &= validateSystemIndexes(systems);
        valid &= validateProcs(componentTypes(), systems);

        if (!valid) {
            return;
        }

        generateSchedules(systems);
    }

    /** 收集 @SystemDef（重名报错，但仍然收集到最后一刻——让本轮能报出更多错误） */
    private Map<String, AType> systemTypes() {
        Map<String, AType> systems = new LinkedHashMap<>();
        for (AType system : types(SystemDef.class)) {
            String name = system.annotation(SystemDef.class).name();
            AType previous = systems.putIfAbsent(name, system);
            if (previous != null) {
                error("Duplicate system name '" + name + "': " + previous.fullName(), system);
                valid = false;
            }
        }
        return systems;
    }

    /** 收集 @ThreadDef（重名报错，但仍然收集到最后一刻） */
    private Map<String, AType> threadTypes() {
        Map<String, AType> threads = new LinkedHashMap<>();
        for (AType thread : types(ThreadDef.class)) {
            String name = thread.annotation(ThreadDef.class).name();
            AType previous = threads.putIfAbsent(name, thread);
            if (previous != null) {
                error("Duplicate thread name '" + name + "': " + previous.fullName(), thread);
                valid = false;
            }
        }
        return threads;
    }

    /** @SystemDef.thread 必须是已声明的 @ThreadDef 名字或保留名（main / test） */
    private boolean validateSystems(Map<String, AType> systems, Map<String, AType> threads) {
        boolean valid = true;
        for (AType system : systems.values()) {
            SystemDef def = system.annotation(SystemDef.class);
            if (!RESERVED_THREADS.contains(def.thread()) && !threads.containsKey(def.thread())) {
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

    /** @SystemDef 类必须继承系统基类（caliniya.vergvoke.system.System），行为通过 update 注入 */
    private boolean validateSystemBases(Map<String, AType> systems) {
        TypeElement base = elementUtils.getTypeElement("caliniya.vergvoke.system.System");
        if (base == null) {
            return true; // 运行时基类不在类路径（不该发生）：跳过这项校验
        }
        boolean valid = true;
        for (AType system : systems.values()) {
            if (!typeUtils.isSubtype(typeUtils.erasure(system.e.asType()), typeUtils.erasure(base.asType()))) {
                error(
                        "@SystemDef '"
                                + system.annotation(SystemDef.class).name()
                                + "' must extend caliniya.vergvoke.system.System (and override update())",
                        system);
                valid = false;
            }
        }
        return valid;
    }

    /** @SystemDef.index 必须非 0，且同一线程内唯一（不同线程之间可以重复） */
    private boolean validateSystemIndexes(Map<String, AType> systems) {
        boolean valid = true;
        Map<String, Map<Integer, AType>> byThread = new LinkedHashMap<>();
        for (AType system : systems.values()) {
            SystemDef def = system.annotation(SystemDef.class);
            if (def.index() == 0) {
                error(
                        "@SystemDef '"
                                + def.name()
                                + "' must set a non-zero index: it orders systems within a thread",
                        system);
                valid = false;
                continue;
            }
            AType previous = byThread
                    .computeIfAbsent(def.thread(), key -> new LinkedHashMap<>())
                    .putIfAbsent(def.index(), system);
            if (previous != null) {
                error(
                        "@SystemDef '"
                                + def.name()
                                + "' reuses index "
                                + def.index()
                                + " in thread '"
                                + def.thread()
                                + "' (already used by "
                                + previous.simpleName()
                                + "): indexes must be unique within a thread",
                        system);
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

    private Map<String, AType> componentTypes() {
        Map<String, AType> components = new LinkedHashMap<>();
        for (AType component : types(Component.class)) {
            components.put(component.fullName(), component);
        }
        return components;
    }

    private boolean isMainTier(String proc) {
        return proc == null || proc.isEmpty() || MAIN_SYSTEM.equals(proc);
    }

    /** 生成 Systems：线程 → 按 index 排序的系统清单（Class 字面量，编译期安全） */
    private void generateSchedules(Map<String, AType> systems) {
        Map<String, List<AType>> byThread = new LinkedHashMap<>();
        for (AType system : systems.values()) {
            SystemDef def = system.annotation(SystemDef.class);
            byThread.computeIfAbsent(def.thread(), key -> new ArrayList<>()).add(system);
        }

        if (byThread.isEmpty()) {
            return;
        }

        // 线程顺序：main 优先，其余按名字升序（保证同一份源码每次生成结果一致）
        List<String> threadNames = new ArrayList<>(byThread.keySet());
        threadNames.sort((a, b) -> {
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

        TypeSpec.Builder schedule = TypeSpec.classBuilder("Systems")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("由注解处理器生成：系统调度蓝图（按线程分组，组内按 index 升序）。\n");

        schedule.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

        for (String thread : threadNames) {
            List<AType> group = new ArrayList<>(byThread.get(thread));
            group.sort(
                    Comparator.comparingInt((AType system) -> system.annotation(SystemDef.class).index()));

            CodeBlock.Builder array = CodeBlock.builder().add("{ ");
            for (int i = 0; i < group.size(); i++) {
                if (i > 0) {
                    array.add(", ");
                }
                array.add("$T.class", ClassName.bestGuess(group.get(i).fullName()));
            }
            array.add(" }");

            schedule.addField(
                    FieldSpec.builder(ArrayTypeName.of(classOfWildcard), thread)
                            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                            .addJavadoc("线程 {@code $L}：$L 个系统，按 index 升序\n", thread, group.size())
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

        schedule.addField(
                FieldSpec.builder(ArrayTypeName.of(ClassName.get(String.class)), "THREADS")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addJavadoc("线程清单（与上面的字段一一对应）\n")
                        .initializer(threadsArray.build())
                        .build());

        try {
            JavaFile.builder(GENERATED_PACKAGE, schedule.build()).build().writeTo(filer);
        } catch (IOException e) {
            error("Failed to generate " + GENERATED_PACKAGE + ".Systems: " + e.getMessage());
        }
    }
}
