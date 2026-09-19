package caliniya.vergvoke.ui.windows;

import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.util.Scaling;
import arc.util.Log;
import caliniya.vergvoke.map.Map;
import caliniya.vergvoke.map.Maps;
import caliniya.vergvoke.ui.Button;

public class MapsWindow extends Window {

    private Table mapList;
    private TextField searchField;
    private String searchText = "";

    // 构造函数：通过 super 设置窗口标题
    public MapsWindow() {
        super("@mapList");
    }

    @Override
    public void top(Table t) {
        // 这里的内容会固定在窗口上方，不会随列表滚动

        // 1. 排序选项
        t.add("@sort").left();

        Button nameSort = new Button("@name", () -> {
            // 简单按名称排序示例
            Maps.maps.sort((a, b) -> a.plainName().compareTo(b.plainName()));
            reloadMaps();
        });
        t.add(nameSort).padLeft(8);

        Button typeSort = new Button("@type", () -> {
            Maps.maps.sort((a, b) -> {
                int type = Boolean.compare(a.custom, b.custom);
                if (type != 0)
                    return type;
                return a.plainName().compareTo(b.plainName());
            });
            reloadMaps();
        });
        t.add(typeSort).padLeft(4);

        // 2. 弹性空间，将后面的内容挤到右边
        t.add().growX();

        // 3. 搜索框
        searchField = t.field(null, text -> {
            searchText = text;
            reloadMaps();
        }).width(180).padRight(4).get();
        searchField.setMessageText("@search");

        // 4. 刷新按钮
        Button refreshButton = new Button("@refresh", () -> {
            Maps.load();
            reloadMaps();
        });
        t.add(refreshButton).padRight(8);

        // 设置这一行的高度和内边距，保持美观
        t.setHeight(42);
    }

    @Override
    public void main(Table t) {
        // 这里的内容会被 Window 基类自动包裹在 ScrollPane 中

        mapList = t; // 直接将 t 作为列表容器
        reloadMaps(); // 加载数据
    }

    /** 重新加载地图列表，应用搜索过滤 */
    public void reloadMaps() {
        mapList.clear(); // 清空列表

        int count = 0;

        for (Map map : Maps.maps) {
            // 搜索过滤逻辑
            if (!searchText.isEmpty()
                    && !map.name().toLowerCase().contains(searchText.toLowerCase())
                    && !map.author().toLowerCase().contains(searchText.toLowerCase())
                    && !map.description().toLowerCase().contains(searchText.toLowerCase())) {
                continue;
            }

            createMapItem(map);
            mapList.row(); // 每个地图项换行
            count++;
        }

        // 空列表提示
        if (count == 0) {
            if (Maps.maps.isEmpty()) {
                mapList.add("[lightgray]No maps found. Click Refresh to scan for maps.[]").pad(20).center();
            } else {
                mapList.add("[lightgray]No maps matching your search.[]").pad(20).center();
            }
        }
    }

    /** 创建单个地图项 */
    private void createMapItem(Map map) {
        Table itemContent = new Table();

        // 预览图 (左侧)
        Table previewTable = new Table();
        if (map.safeTexture() != null) {
            Image preview = new Image(map.safeTexture());
            preview.setScaling(Scaling.fit);
            previewTable.add(preview).size(80, 60);
        } else {
            previewTable.add("[gray]No Preview[]").size(80, 60).center();
        }
        itemContent.add(previewTable).size(84, 64).pad(4);

        // 信息区域 (右侧)
        Table infoTable = new Table();

        // 第一行：名称 + 标签
        Table nameRow = new Table();
        Label nameLabel = new Label(map.name());
        nameLabel.setWrap(true);
        nameRow.add(nameLabel).growX().left();

        String typeText = map.custom ? "Custom" : "Built-in";
        String typeColor = map.custom ? "#98C87A" : "#7BA4C8";
        nameRow.add("[" + typeColor + "]<" + typeText + ">[]").padLeft(8);

        if (map.space) {
            nameRow.add("[#C87AA4]<Space>[]").padLeft(4);
        }

        infoTable.add(nameRow).growX().left().padBottom(4);
        infoTable.row();

        // 第二行：作者
        if (!map.author().equals("Unknown")) {
            infoTable.add("[gray]By " + map.author() + "[]").left().padBottom(2);
            infoTable.row();
        }

        // 第三行：尺寸
        if (map.width > 0 && map.height > 0) {
            infoTable.add("[lightgray]Size: " + map.width + "x" + map.height + "[]").left().padBottom(2);
            infoTable.row();
        }

        // 第四行：描述
        if (!map.description().isEmpty()) {
            Label descLabel = new Label("[gray]" + map.description() + "[]");
            descLabel.setWrap(true);
            infoTable.add(descLabel).growX().left().padTop(2);
        }

        itemContent.add(infoTable).growX().pad(8);

        // 包装成按钮
        Button mapButton = new Button("", () -> onMapSelected(map));
        mapButton.clearChildren();
        mapButton.add(itemContent).grow();

        // 添加到列表
        mapList.add(mapButton).growX().pad(4);
    }

    private void onMapSelected(Map map) {
        Log.info("Selected map: " + map.name());
        // 处理选择逻辑
    }
}