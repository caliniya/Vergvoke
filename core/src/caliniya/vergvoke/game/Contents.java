package caliniya.vergvoke.game;

import arc.struct.ObjectMap;
import caliniya.vergvoke.base.game.ContentType;
import caliniya.vergvoke.base.tool.Ar;
import caliniya.vergvoke.base.type.CType;
import caliniya.vergvoke.content.*;
import caliniya.vergvoke.type.type.ItemType;

/**
 * 内容注册与管理器。
 *
 * <p>
 * 负责游戏内容（如物品、单位等）的注册、存储和查询。内容通过名称映射和类型数组存储， 注册时分配运行时ID（从1开始）。
 */
@SuppressWarnings("unchecked")
public class Contents {

    /** 内容名称映射表，用于通过名称快速查找内容对象。 */
    private static final ObjectMap<String, ContentType> contentMap = new ObjectMap<>();

    /** 按类型分类的内容数组，索引对应 {@link CType#ordinal()}。 */
    private static final Ar<ContentType>[] contentByTypes;

    public static ItemType[] items; // 所有已注册物品

    // public static IntAr EntitysID;

    // public static Ar<boolean> EntitysID;

    /** 已注册的物品类型内容总数。 */
    public static int totalItemCount = 0;

    /** 已注册的液体类型内容总数。 */
    public static int totalLiquidCount = 0;

    static {
        int typeCount = CType.values().length;
        contentByTypes = new Ar[typeCount];
        for (int i = 0; i < typeCount; i++) {
            contentByTypes[i] = new Ar<>();
        }
    }

    /** 初始化内容 */
    public static void load() {
        Items.load();
        Liquids.load();
        Floors.load();
        ENVBlocks.load();
        items = getByType(CType.Item).toArray(ItemType.class);
        Blocks.load();
        UnitTypes.load();
        Enhancements.load();
        Stars.load();
        for (Ar<ContentType> types : contentByTypes) {
            types.each(t -> t.load());
        }
        Techs.load();
        // EntitysID = new IntAr(100);
        // items = new ItemType[totalItemCount];
        // items = (ItemType[]) getByType(CType.Item).items;
    }

    /**
     * 注册内容并分配运行时ID。
     *
     * <p>
     * ID从1开始分配，每种类型的ID独立编号。
     *
     * @param content 要注册的内容对象
     * @throws RuntimeException 当某类型的内容数量超过 {@link Short#MAX_VALUE} 时抛出
     */
    public static void add(ContentType content) {
        if (content == null)
            return;

        contentMap.put(content.internalName, content);

        Ar<ContentType> list = contentByTypes[content.type.ordinal()];

        // ID 从 1 开始
        content.id = list.size + 1;
        list.add(content);

        if (content.type == CType.Item) {
            totalItemCount++;
        } else if (content.type == CType.Liquid) {
            totalLiquidCount++;
        }
    }

    /**
     * 通过内部名称获取内容对象。
     *
     * @param internalName 内容的内部名称
     * @return 对应的内容对象，不存在则返回null
     */
    public static ContentType get(String internalName) {
        return contentMap.get(internalName);
    }

    /**
     * 通过内部名称获取指定类型的内容对象。
     *
     * @param <T>          期望的内容类型
     * @param internalName 内容的内部名称
     * @param type         期望的内容类型Class对象
     * @return 类型匹配的内容对象，不匹配或不存在则返回null
     */
    @SuppressWarnings("unchecked")
    public static <T extends ContentType> T get(String internalName, Class<T> type) {
        ContentType c = contentMap.get(internalName);
        if (type.isInstance(c)) {
            return (T) c;
        }
        return null;
    }

    /**
     * 获取指定类型的所有内容对象。
     *
     * @param type 内容类型
     * @return 包含该类型所有内容的数组
     */
    public static Ar<ContentType> getByType(CType type) {
        return contentByTypes[type.ordinal()];
    }

    /**
     * 通过类型和运行时ID获取内容对象。
     *
     * <p>
     * 时间复杂度为 O(1)。(大喜)
     *
     * @param <T>  返回的内容类型
     * @param type 内容类型
     * @param id   运行时ID (有效范围 >= 1)
     * @return 对应的内容对象，ID无效则返回null
     */
    @SuppressWarnings("unchecked")
    public static <T extends ContentType> T getByID(CType type, int id) {
        if (id <= 0)
            return null;

        Ar<ContentType> list = contentByTypes[type.ordinal()];
        int index = id - 1;

        if (index >= list.size)
            return null;

        return (T) list.get(index);
    }

    /**
     * 清空所有已注册的内容并重置计数器。
     *
     * <p>
     * 别用
     */
    public static void clear() {
        contentMap.clear();
        for (Ar<ContentType> list : contentByTypes) {
            list.clear();
        }
        totalItemCount = 0;
        totalLiquidCount = 0;
    }
}
