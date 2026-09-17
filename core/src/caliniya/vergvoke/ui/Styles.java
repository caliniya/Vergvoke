package caliniya.vergvoke.ui;

import arc.Core;
import arc.graphics.Color;
import arc.scene.style.Drawable;
import arc.scene.style.NinePatchDrawable;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import arc.scene.ui.Label.LabelStyle;
import arc.scene.ui.ScrollPane.ScrollPaneStyle;
import arc.scene.ui.TextField.TextFieldStyle;
import arc.scene.ui.TextButton.TextButtonStyle;
import arc.scene.ui.Button.ButtonStyle;
import arc.scene.ui.Dialog.DialogStyle;

public class Styles {

    public static ImageButtonStyle buttondef, buttonc;
    public static LabelStyle label;
    public static ScrollPaneStyle scrollPane;
    public static TextFieldStyle textField;
    public static TextButtonStyle textButton;
    public static ButtonStyle bu;
    public static DialogStyle window;

    public static Drawable background;

    public static void load() {

        buttondef = new ImageButtonStyle();
        buttondef.up = Core.atlas.drawable("button-up"); // 默认状态
        buttondef.down = Core.atlas.drawable("button-down"); // 按下状态
        buttondef.checked = buttondef.up;
        buttondef.disabled = Core.atlas.drawable("button-dis");

        buttonc = new ImageButtonStyle();
        buttonc.up = buttondef.up;
        buttonc.down = buttondef.down;
        buttonc.checked = buttondef.down;
        buttonc.disabled = buttondef.disabled;

        Core.scene.addStyle(ImageButtonStyle.class, buttondef);

        label = new LabelStyle(Fonts.def, Color.white);
        Core.scene.addStyle(LabelStyle.class, label);

        scrollPane = new ScrollPaneStyle();
        Core.scene.addStyle(ScrollPaneStyle.class, scrollPane);

        textButton = new TextButtonStyle();
        textButton.checked = buttondef.checked;
        textButton.font = Fonts.def;
        textButton.up = buttondef.up;
        textButton.down = buttondef.down;
        Core.scene.addStyle(TextButtonStyle.class, textButton);

        bu = new ButtonStyle();
        bu.checked = buttondef.checked;
        bu.down = buttondef.down;
        bu.up = buttondef.up;
        Core.scene.addStyle(ButtonStyle.class, bu);

        textField = new TextFieldStyle();
        textField.font = Fonts.def;
        textField.fontColor = Color.white; // 必需：文本颜色
        textField.messageFont = Fonts.def; // 提示文本字体
        textField.messageFontColor = Color.gray; // 提示文本颜色
        textField.background = buttondef.up;
        textField.cursor = buttondef.down; // 光标样式，使用已有资源
        textField.selection = buttondef.down; // 选择高亮背景
        Core.scene.addStyle(TextFieldStyle.class, textField);

        background = (NinePatchDrawable) Core.atlas.drawable("Window-2");
    }
}