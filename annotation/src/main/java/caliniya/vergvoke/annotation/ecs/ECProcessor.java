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
import caliniya.vergvoke.annotation.Annotations.Component;
import caliniya.vergvoke.annotation.Annotations.Entity;
import caliniya.vergvoke.annotation.Annotations.Import;
import caliniya.vergvoke.annotation.Processor;
import caliniya.vergvoke.annotation.tool.AType;
import caliniya.vergvoke.annotation.tool.AVar;
import caliniya.vergvoke.base.anno.auto.AnnoProc;
import caliniya.vergvoke.base.tool.Ar;

@AnnoProc
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes({
    "caliniya.vergvoke.annotation.Annotations.Component",
    "caliniya.vergvoke.annotation.Annotations.Entity",
    "caliniya.vergvoke.annotation.Annotations.Import"
})
public class ECProcessor extends Processor {

  private static final String GENERATED_PACKAGE = "caliniya.vergvoke.base.ecs";

  // 每种类型的实体都有哪些组件
  public ObjectMap<String, ObjectSet<String>> ECMap = new ObjectMap<>();
  public Ar<AType> entityDef = new Ar<>();

  {
    maxRounds = 2;
  }

  @SuppressWarnings("unused")
  @Override
  protected void process() {
    
    entityDef = types(Entity.class);
    Map<String, AType> components = componentTypes();
    validateImportTargets(components);

    for (AType entity : entityDef) {
      List<AType> entityComponents = resolveComponents(entity, components);
      if (entityComponents == null) {
        continue;
      }

      ObjectSet<String> componentNames = new ObjectSet<>();
      for (AType component : entityComponents) {
        componentNames.add(component.simpleName());
      }
      ECMap.put(entity.annotation(Entity.class).name() + "Entity", componentNames);

      generateEntity(entity, entityComponents);
    }
  }

  private Map<String, AType> componentTypes() {
    Map<String, AType> components = new LinkedHashMap<>();
    for (AType component : types(Component.class)) {
      components.put(component.fullName(), component);
    }
    return components;
  }

  private void validateImportTargets(Map<String, AType> components) {
    for (AVar imported : fields(Import.class)) {
      if (!components.containsKey(imported.enclosingType().fullName())) {
        error("@Import can only be used on a field declared by @Component", imported);
      }
    }
  }

  private List<AType> resolveComponents(AType entity, Map<String, AType> components) {
    List<AType> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    boolean valid = true;

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
      result.add(component);
    }

    return valid ? result : null;
  }

  private void generateEntity(AType entity, List<AType> components) {
    String entityName = entity.annotation(Entity.class).name() + "Entity";
    if (!SourceVersion.isName(entityName)) {
      error("Invalid generated entity name: " + entityName, entity);
      return;
    }

    Map<String, VariableElement> injectedFields = new LinkedHashMap<>();
    List<VariableElement> imports = new ArrayList<>();
    boolean valid = true;

    for (AType component : components) {
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

    if (!validateImports(imports, injectedFields)) {
      valid = false;
    }
    if (!valid) {
      return;
    }

    TypeSpec.Builder entityType = TypeSpec.classBuilder(entityName).addModifiers(Modifier.PUBLIC);
    for (VariableElement field : injectedFields.values()) {
      entityType.addField(
          FieldSpec.builder(
                  TypeName.get(field.asType()), field.getSimpleName().toString(), Modifier.PUBLIC)
              .build());
    }

    try {
      JavaFile.builder(GENERATED_PACKAGE, entityType.build()).build().writeTo(filer);
    } catch (IOException e) {
      error(
          "Failed to generate entity "
              + GENERATED_PACKAGE
              + "."
              + entityName
              + ": "
              + e.getMessage(),
          entity);
    }
  }

  private boolean validateImports(
      List<VariableElement> imports, Map<String, VariableElement> injectedFields) {
    boolean valid = true;
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
    return valid;
  }
}
