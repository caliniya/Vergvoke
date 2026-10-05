package caliniya.vergvoke.annotation.tool;

import arc.struct.Ar;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;

/** 元素包装 */
public class AElement<T extends Element> {

  public final T e;
  public final Ar<? extends AnnotationMirror> mirror;

  public AElement(T e) {
    this.e = e;
    this.mirror = Ar.with(e.getAnnotationMirrors());
  }

  /** 获取元素名称 */
  public String name() {
    return e.getSimpleName().toString();
  }

  /** 获取完整名称 */
  public String fullName() {
    return e.toString();
  }

  /** 获取元素类型镜像 */
  public TypeMirror mirror() {
    return e.asType();
  }

  /** 获取封闭元素（父级） */
  public Element up() {
    return e.getEnclosingElement();
  }

  /** 判断是否为变量 */
  public boolean isVar() {
    return e instanceof VariableElement;
  }

  /** 判断是否为类型 */
  public boolean isType() {
    return e instanceof TypeElement;
  }

  /** 判断是否为方法 */
  public boolean isMethod() {
    return e instanceof ExecutableElement;
  }

  /** 判断是否为包 */
  public boolean isPackage() {
    return e instanceof PackageElement;
  }

  /** 判断是否有指定修饰符 */
  public boolean is(Modifier modifier) {
    return e.getModifiers().contains(modifier);
  }

  /** 判断是否有任意指定修饰符 */
  public boolean isAny(Modifier... modifiers) {
    for (Modifier m : modifiers) {
      if (is(m)) return true;
    }
    return false;
  }

  /** 判断是否所有指定修饰符 */
  public boolean isAll(Modifier... modifiers) {
    for (Modifier m : modifiers) {
      if (!is(m)) return false;
    }
    return true;
  }

  /** 获取所有子元素 */
  public Ar<AElement<?>> enclosed() {
    return Ar.with(e.getEnclosedElements()).map(AElement::new);
  }

  /** 获取所有注解镜像 */
  // 按理来说 一个元素不会活到下一轮处理，所以就干脆用镜像好了
  public Ar<? extends AnnotationMirror> annotations() {
    return mirror;
  }

  /** 判断是否包含指定注解 */
  public <A extends Annotation> boolean has(Class<A> annotation) {
    return e.getAnnotation(annotation) != null;
  }

  /** 获取指定注解 */
  public <A extends Annotation> A annotation(Class<A> annotation) {
    return e.getAnnotation(annotation);
  }

  /** 转换为具体类型包装 */
  public AType asType() {
    if (!isType()) {
      throw new IllegalStateException("Element is not a type: " + e);
    }
    return new AType((TypeElement) e);
  }

  /** 转换为变量包装 */
  public AVar asVar() {
    if (!isVar()) {
      throw new IllegalStateException("Element is not a variable: " + e);
    }
    return new AVar((VariableElement) e);
  }

  /** 转换为方法包装 */
  public AMethod asMethod() {
    if (!isMethod()) {
      throw new IllegalStateException("Element is not a method: " + e);
    }
    return new AMethod((ExecutableElement) e);
  }

  @Override
  public String toString() {
    return e.toString();
  }

  @Override
  public int hashCode() {
    return e.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    AElement<?> that = (AElement<?>) obj;
    return e.equals(that.e);
  }
}
