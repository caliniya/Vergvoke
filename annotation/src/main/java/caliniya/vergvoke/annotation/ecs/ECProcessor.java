package caliniya.vergvoke.annotation.ecs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.annotation.Processor;
import caliniya.vergvoke.annotation.tool.AType;
import caliniya.vergvoke.annotation.tool.AVar;
import caliniya.vergvoke.base.anno.auto.AnnoProc;

/**
 * EC 注解处理器
 *
 * <p>
 * 先在内存中做完全部校验（@Import 归属、实体组件重复、字段重名、@Import 目标存在且类型一致），
 * 只有全部通过才一次性生成所有实体类；校验期间不写任何文件，所以不会落下半成品。
 * 
 * "created in the last round will not be subject to annotation processing" 警告）。
 */
@AnnoProc
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes({
        "caliniya.vergvoke.annotation.Annotations.Component",
        "caliniya.vergvoke.annotation.Annotations.Entity",
        "caliniya.vergvoke.annotation.Annotations.Import"
})
public class ECProcessor extends Processor {

    private static final String GENERATED_PACKAGE = "caliniya.vergvoke.base.ecs";

    /** 每种类型的实体都有哪些组件（生成成功后填充） */
    public ObjectMap<String, ObjectSet<String>> ECMap = new ObjectMap<>();

    {
        maxRounds = 1;
    }

    @SuppressWarnings("unused")
    @Override
    protected void process() {
        Map<String, AType> components = componentTypes();
        boolean valid = validateImportTargets(components);

        List<EntityPlan> plans = new ArrayList<>();
        for (AType entity : types(Entity.class)) {
            EntityPlan plan = planEntity(entity, components);
            if (plan == null) {
                valid = false;
                continue;
            }
            plans.add(plan);
        }

        if (!valid) {
            return;
        }

        for (EntityPlan plan : plans) {
            generateEntity(plan);
        }
    }

    /** 收集所有 @Component 类型：全限定名 -> 类型包装 */
    private Map<String, AType> componentTypes() {
        Map<String, AType> components = new LinkedHashMap<>();
        for (AType component : types(Component.class)) {
            components.put(component.fullName(), component);
        }
        return components;
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

    /**
     * 校验一个实体并算出它的生成计划；返回 null 表示这个实体没通过校验（错误已经报出）。
     *
     * <p>
     * 这里一趟就把"要注入的字段"和"要校验的导入"都收集好，生成阶段直接复用，不再重复遍历。
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

        if (!valid) {
            return null;
        }

        ObjectSet<String> componentNames = new ObjectSet<>();
        for (AType component : entityComponents) {
            componentNames.add(component.simpleName());
        }

        return new EntityPlan(entity, entityName, componentNames, injectedFields);
    }

    /** 按生成计划写出实体类 */
    private void generateEntity(EntityPlan plan) {
        TypeSpec.Builder entityType = TypeSpec.classBuilder(plan.entityName).addModifiers(Modifier.PUBLIC);

        for (VariableElement field : plan.injectedFields.values()) {
            entityType.addField(
                    FieldSpec.builder(
                            TypeName.get(field.asType()), field.getSimpleName().toString(), Modifier.PUBLIC)
                            .build());
        }

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

    /** 一个实体校验通过后算好的生成计划 */
    private static final class EntityPlan {
        final AType entity;
        final String entityName;
        final ObjectSet<String> componentNames;
        final Map<String, VariableElement> injectedFields;

        EntityPlan(
                AType entity,
                String entityName,
                ObjectSet<String> componentNames,
                Map<String, VariableElement> injectedFields) {
            this.entity = entity;
            this.entityName = entityName;
            this.componentNames = componentNames;
            this.injectedFields = injectedFields;
        }
    }
}
