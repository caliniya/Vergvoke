package caliniya.vergvoke.annotation.tool;

import arc.struct.Ar;
import java.util.*;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;

/** 方法元素包装（包括构造方法） */
public class AMethod extends AElement<ExecutableElement> {

  public AMethod(ExecutableElement element) {
    super(element);
  }

  /** 判断是否为构造方法 */
  public boolean isConstructor() {
    return e.getSimpleName().toString().equals("<init>");
  }

  /** 判断是否为静态方法 */
  public boolean isStatic() {
    return is(Modifier.STATIC);
  }

  /** 判断是否为抽象方法 */
  public boolean isAbstract() {
    return is(Modifier.ABSTRACT);
  }

  /** 判断是否为 final 方法 */
  public boolean isFinal() {
    return is(Modifier.FINAL);
  }

  /** 判断是否为 native 方法 */
  public boolean isNative() {
    return is(Modifier.NATIVE);
  }

  /** 判断是否为同步方法 */
  public boolean isSynchronized() {
    return is(Modifier.SYNCHRONIZED);
  }

  /** 获取返回类型 */
  public TypeMirror returnType() {
    return e.getReturnType();
  }

  /** 获取返回类型名称 */
  public String returnTypeName() {
    return returnType().toString();
  }

  /** 判断是否为 void 方法 */
  public boolean isVoid() {
    return returnType().toString().equals("void");
  }

  /** 获取所有参数 */
  public Ar<AVar> parameters() {
    return Ar.with(e.getParameters()).map(AVar::new);
  }

  /** 获取参数类型名称列表 */
  public Ar<String> parameterTypeNames() {
    return parameters().map(param -> param.mirror().toString());
  }

  /** 获取参数个数 */
  public int parameterCount() {
    return e.getParameters().size();
  }
  
  /** 获取抛出的异常类型 */
  @SuppressWarnings("unchecked")
  public Ar<TypeMirror> thrownTypes() {
    return Ar.with((List<TypeMirror>) e.getThrownTypes());
  }

  /** 获取抛出的异常类型名称 */
  public Ar<String> thrownTypeNames() {
    return thrownTypes().map(TypeMirror::toString);
  }

  /** 获取泛型参数 */
  public Ar<String> typeParameters() {
    return Ar.with(e.getTypeParameters()).map(param -> param.getSimpleName().toString());
  }

  /** 获取方法签名（简短描述） */
  public String signature() {
    return name() + "(" + parameters().toString(", ", AVar::name) + ")";
  }

  /** 获取完整方法描述（包含返回类型） */
  public String fullSignature() {
    return returnTypeName() + " " + signature();
  }

  /** 获取方法描述字符串（用于调试） */
  public String descString() {
    return up().toString() + "#" + e.toString();
  }

  /** 获取封闭类型 */
  public AType enclosingType() {
    Element parent = up();
    if (!(parent instanceof TypeElement)) {
      throw new IllegalStateException("Method not enclosed in a type: " + e);
    }
    return new AType((TypeElement) parent);
  }

  /** 判断是否为重写方法 */
  public boolean isOverride() {
    return e.getAnnotation(Override.class) != null;
  }

  /** 判断是否为默认方法（接口中的 default 方法） */
  public boolean isDefault() {
    return is(Modifier.DEFAULT);
  }
}
