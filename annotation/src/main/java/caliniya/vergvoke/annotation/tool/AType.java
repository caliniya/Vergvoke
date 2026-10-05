package caliniya.vergvoke.annotation.tool;

import arc.struct.Ar;
import caliniya.vergvoke.annotation.Processor;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;

/**
 * 类型元素包装（类、接口、枚举）
 */
public class AType extends AElement<TypeElement> {

    public AType(TypeElement element) {
        super(element);
    }

    /**
     * 从 TypeMirror 创建 AType 实例
     */
    public static AType of(TypeMirror mirror) {
        Element element = Processor.typeUtils.asElement(mirror);
        if (!(element instanceof TypeElement)) {
            throw new IllegalArgumentException("Not a type element: " + mirror);
        }
        return new AType((TypeElement) element);
    }

    /**
     * 获取完整类名
     */
    @Override
    public String fullName() {
        return e.getQualifiedName().toString();
    }

    /**
     * 获取简单类名
     */
    public String simpleName() {
        return e.getSimpleName().toString();
    }

    /**
     * 获取包名
     */
    public String packageName() {
        String fullName = fullName();
        int lastDot = fullName.lastIndexOf('.');
        return lastDot > 0 ? fullName.substring(0, lastDot) : "";
    }

    /**
     * 判断是否为接口
     */
    public boolean isInterface() {
        return e.getKind() == ElementKind.INTERFACE;
    }

    /**
     * 判断是否为枚举
     */
    public boolean isEnum() {
        return e.getKind() == ElementKind.ENUM;
    }

    /**
     * 判断是否为抽象类
     */
    public boolean isAbstract() {
        return e.getModifiers().contains(Modifier.ABSTRACT);
    }

    /**
     * 获取直接父类
     */
    public AType superclass() {
        TypeMirror superclass = e.getSuperclass();
        if (superclass == null) {
            return null;
        }
        return of(superclass);
    }

    /**
     * 获取所有父类（递归，不包括 Object）
     */
    public Ar<AType> allSuperclasses() {
        Ar<AType> result = new Ar<>();
        AType current = superclass();
        while (current != null && !current.fullName().equals("java.lang.Object")) {
            result.add(current);
            current = current.superclass();
        }
        return result;
    }

    /**
     * 获取直接实现的接口
     */
    public Ar<AType> interfaces() {
        return Ar.with(e.getInterfaces()).map(AType::of);
    }

    /**
     * 获取所有接口（包括父类实现的接口）
     */
    public Ar<AType> allInterfaces() {
        Ar<AType> result = new Ar<>();
        // 添加当前类实现的接口
        result.addAll(interfaces());
        // 添加父类实现的接口
        for (AType superclass : allSuperclasses()) {
            result.addAll(superclass.interfaces());
        }
        // 递归添加接口的父接口
        Ar<AType> all = new Ar<>();
        for (AType iface : result) {
            all.add(iface);
            all.addAll(iface.allInterfaces());
        }
        return all.distinct();
    }

    /**
     * 获取所有字段
     */
    public Ar<AVar> fields() {
        return Ar.with(e.getEnclosedElements())
                .select(el -> el instanceof VariableElement)
                .map(el -> new AVar((VariableElement) el));
    }

    /**
     * 获取所有方法（不包括构造方法）
     */
    public Ar<AMethod> methods() {
        return Ar.with(e.getEnclosedElements())
                .select(el -> el instanceof ExecutableElement && !isConstructor((ExecutableElement) el))
                .map(el -> new AMethod((ExecutableElement) el));
    }

    /**
     * 获取所有构造方法
     */
    public Ar<AMethod> constructors() {
        return Ar.with(e.getEnclosedElements())
                .select(el -> el instanceof ExecutableElement && isConstructor((ExecutableElement) el))
                .map(el -> new AMethod((ExecutableElement) el));
    }

    /**
     * 判断是否为构造方法
     */
    private boolean isConstructor(ExecutableElement method) {
        return method.getSimpleName().toString().equals("<init>");
    }

    /**
     * 判断是否继承自指定类型
     */
    public boolean isSubtypeOf(String className) {
        TypeElement other = Processor.elementUtils.getTypeElement(className);
        if (other == null) return false;
        return Processor.typeUtils.isSubtype(e.asType(), other.asType());
    }

    /**
     * 判断是否实现了指定接口
     */
    public boolean implementsInterface(String interfaceName) {
        return allInterfaces().contains(iface -> iface.fullName().equals(interfaceName));
    }

    /**
     * 获取泛型参数
     */
    public Ar<String> typeParameters() {
        return Ar.with(e.getTypeParameters()).map(param -> param.getSimpleName().toString());
    }
}