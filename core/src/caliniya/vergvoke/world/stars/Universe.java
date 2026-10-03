package caliniya.vergvoke.world.stars;

/** 宇宙中所有共享数据 */
public class Universe {

    /** 当前选中的网格 X（世界坐标，已对齐） */
    public static float selectedX;

    /** 当前选中的网格 Y（世界坐标，已对齐） */
    public static float selectedY;

    /** 是否有选中 */
    public static boolean hasSelection;

    /** 当前选中的星域节点（宇宙视图点击拾取；渲染高亮与信息窗的数据源）。 */
    public static StarNode selectedNode;
}