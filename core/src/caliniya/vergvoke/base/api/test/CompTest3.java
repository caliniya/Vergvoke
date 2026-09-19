package caliniya.vergvoke.base.api.test;

import caliniya.vergvoke.annotation.Annotations.*;

/** 独立档组件：proc 指向 @SystemDef("testSystem")，验证系统交叉校验 + 组件系统生成（直接调用）。 */
@Component(name = "test3", proc = "testSystem", index = 3)
public class CompTest3 {

    @Save(index = 1)
    public int z;

    @Updata
    public void test(float delta) {
        z++;
    }

}
