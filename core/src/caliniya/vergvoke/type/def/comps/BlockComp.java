package caliniya.vergvoke.type.def.comps;

import caliniya.vergvoke.annotation.Annotations.*;

// FIXME 草稿补全（2026-09-26）：index 处理器要求非零（用于序列化/更新排序），原稿 index = 0 编不过，先占 3，按需改
@Component(index = 3, name = "Block")
public class BlockComp {

    public int ty, tx;

    public boolean contains(int x, int y) {
        // TODO 草稿补全：原文件方法体为空无法编译，先返回 false 占位
        return false;
    }
}
