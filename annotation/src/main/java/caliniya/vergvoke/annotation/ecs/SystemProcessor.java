package caliniya.vergvoke.annotation.ecs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import caliniya.vergvoke.annotation.Annotations.SystemDef;
import caliniya.vergvoke.annotation.Annotations.ThreadDef;
import caliniya.vergvoke.annotation.Processor;
import caliniya.vergvoke.annotation.tool.AType;
import caliniya.vergvoke.base.anno.auto.AnnoProc;

/**
 * 系统 / 线程注册表处理器（原型）。
 *
 * <p>只覆盖 {@link ThreadDef} / {@link SystemDef}：校验线程名、系统名唯一，
 * 系统必须挂在已声明（或保留）线程上；全部通过后生成
 * {@code caliniya.vergvoke.base.ecs.ECSchedule}。线程启停仍由运行时 System 框架负责。
 */
@AnnoProc
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes({
        "caliniya.vergvoke.annotation.Annotations.SystemDef",
        "caliniya.vergvoke.annotation.Annotations.ThreadDef"
})
public class SystemProcessor extends Processor {

    private static final String GENERATED_PACKAGE = "caliniya.vergvoke.base.ecs";
    private static final ClassName SYS = ClassName.get(GENERATED_PACKAGE, "ECSchedule", "Sys");

    /** 保留线程名：主线程，不需要 @ThreadDef */
    private static final Set<String> RESERVED_THREADS = Set.of("main", "test");

    {
        maxRounds = 1;
    }

    @Override
    protected void process() {
        Map<String, AType> systems = systemTypes();
        Set<String> threads = declaredThreads();
        List<AType> threadDefs = threadTypes();

        boolean valid = validateThreadNames(threadDefs);
        valid &= validateSystems(systems, threads);

        if (!valid) {
            return;
        }

        // 只有保留名、没有任何自定义声明时跳过，避免空壳
        if (systems.isEmpty() && threads.equals(RESERVED_THREADS)) {
            return;
        }

        generateSchedule(systems, threads);
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

    private List<AType> threadTypes() {
        List<AType> result = new ArrayList<>();
        for (AType t : types(ThreadDef.class)) {
            result.add(t);
        }
        return result;
    }

    private Set<String> declaredThreads() {
        Set<String> threads = new LinkedHashSet<>(RESERVED_THREADS);
        for (AType thread : types(ThreadDef.class)) {
            threads.add(thread.annotation(ThreadDef.class).name());
        }
        return threads;
    }

    private boolean validateThreadNames(List<AType> threadDefs) {
        boolean valid = true;
        Map<String, AType> seen = new LinkedHashMap<>();
        for (AType thread : threadDefs) {
            String name = thread.annotation(ThreadDef.class).name();
            AType previous = seen.putIfAbsent(name, thread);
            if (previous != null) {
                error("Duplicate thread name '" + name + "': " + previous.fullName(), thread);
                valid = false;
            }
        }
        return valid;
    }

    private boolean validateSystems(Map<String, AType> systems, Set<String> threads) {
        boolean valid = true;
        for (AType system : systems.values()) {
            SystemDef def = system.annotation(SystemDef.class);
            if (!threads.contains(def.thread())) {
                error(
                        "@SystemDef '" + def.name() + "' uses unknown thread '" + def.thread()
                                + "': declare it with @ThreadDef, or use 'main' / 'test'",
                        system);
                valid = false;
            }
        }
        return valid;
    }

    private void generateSchedule(Map<String, AType> systems, Set<String> threads) {
        TypeSpec.Builder type = TypeSpec.classBuilder("ECSchedule")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("由注解处理器生成：线程与系统注册表（原型）。线程启停由运行时 System 框架负责。\n");
        type.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

        for (String thread : threads) {
            type.addField(
                    FieldSpec.builder(String.class, "THREAD_" + thread.toUpperCase(),
                            Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                            .initializer("$S", thread)
                            .build());
        }

        type.addType(
                TypeSpec.classBuilder("Sys")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addJavadoc("一条系统注册信息。\n")
                        .addField(FieldSpec.builder(String.class, "name", Modifier.PUBLIC, Modifier.FINAL).build())
                        .addField(FieldSpec.builder(String.class, "thread", Modifier.PUBLIC, Modifier.FINAL).build())
                        .addField(FieldSpec.builder(int.class, "index", Modifier.PUBLIC, Modifier.FINAL).build())
                        .addMethod(
                                MethodSpec.constructorBuilder()
                                        .addModifiers(Modifier.PUBLIC)
                                        .addParameter(String.class, "name")
                                        .addParameter(String.class, "thread")
                                        .addParameter(int.class, "index")
                                        .addStatement("this.name = name")
                                        .addStatement("this.thread = thread")
                                        .addStatement("this.index = index")
                                        .build())
                        .build());

        // 按线程声明顺序分组，组内按 @SystemDef.index 升序
        Map<String, List<AType>> byThread = new LinkedHashMap<>();
        for (String thread : threads) {
            byThread.put(thread, new ArrayList<>());
        }
        for (AType system : systems.values()) {
            byThread.get(system.annotation(SystemDef.class).thread()).add(system);
        }
        for (List<AType> list : byThread.values()) {
            list.sort((a, b) -> {
                int ia = a.annotation(SystemDef.class).index();
                int ib = b.annotation(SystemDef.class).index();
                if (ia != ib) {
                    return Integer.compare(ia, ib);
                }
                return a.fullName().compareTo(b.fullName());
            });
        }

        CodeBlock.Builder allInit = CodeBlock.builder().add("{\n$>");
        if (systems.isEmpty()) {
            allInit.add("ALL = new Sys[0];\n");
        } else {
            allInit.add("ALL = new Sys[] {\n$>");
            for (Map.Entry<String, List<AType>> entry : byThread.entrySet()) {
                for (AType system : entry.getValue()) {
                    SystemDef def = system.annotation(SystemDef.class);
                    allInit.add("new Sys($S, $S, $L),\n", def.name(), entry.getKey(), def.index());
                }
            }
            allInit.add("$<};\n");
        }
        allInit.add("$<}");

        type.addField(
                FieldSpec.builder(ArrayTypeName.of(SYS), "ALL",
                        Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addJavadoc("全部系统，按线程声明顺序、组内按 index 升序。\n")
                        .build());
        type.addStaticBlock(allInit.build());

        type.addMethod(
                MethodSpec.methodBuilder("threadOf")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addParameter(String.class, "systemName")
                        .returns(String.class)
                        .addJavadoc("系统跑在哪个线程；未注册返回 null。\n")
                        .addCode("for (Sys s : ALL) {\n"
                                + "  if (s.name.equals(systemName)) return s.thread;\n"
                                + "}\n"
                                + "return null;\n")
                        .build());

        type.addMethod(
                MethodSpec.methodBuilder("indexOf")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addParameter(String.class, "systemName")
                        .returns(int.class)
                        .addJavadoc("系统在同线程内的执行序号；未注册返回 -1。\n")
                        .addCode("for (Sys s : ALL) {\n"
                                + "  if (s.name.equals(systemName)) return s.index;\n"
                                + "}\n"
                                + "return -1;\n")
                        .build());

        type.addMethod(
                MethodSpec.methodBuilder("systemsOn")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addParameter(String.class, "thread")
                        .returns(String[].class)
                        .addJavadoc("某线程上的系统名列表（已按 index 排好）。\n")
                        .addCode("int n = 0;\n"
                                + "for (Sys s : ALL) {\n"
                                + "  if (s.thread.equals(thread)) n++;\n"
                                + "}\n"
                                + "String[] out = new String[n];\n"
                                + "int i = 0;\n"
                                + "for (Sys s : ALL) {\n"
                                + "  if (s.thread.equals(thread)) out[i++] = s.name;\n"
                                + "}\n"
                                + "return out;\n")
                        .build());

        MethodSpec.Builder threadsMethod = MethodSpec.methodBuilder("threads")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(String[].class)
                .addJavadoc("已声明的线程名（含保留名）。\n");
        threadsMethod.addCode("return new String[] {\n$>");
        for (String thread : threads) {
            threadsMethod.addCode("$S,\n", thread);
        }
        threadsMethod.addCode("$<};\n");
        type.addMethod(threadsMethod.build());

        try {
            JavaFile.builder(GENERATED_PACKAGE, type.build()).build().writeTo(filer);
            info("Generated " + GENERATED_PACKAGE + ".ECSchedule: "
                    + systems.size() + " system(s), " + threads.size() + " thread(s)");
        } catch (Exception e) {
            error("Failed to generate ECSchedule: " + e.getMessage());
        }
    }
}
