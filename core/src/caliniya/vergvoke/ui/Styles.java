package caliniya.vergvoke.ui;

import arc.*;
import arc.graphics.*;
import arc.scene.style.*;
import arc.scene.ui.Button;
import arc.scene.ui.Dialog.*;
import arc.scene.ui.ImageButton.*;
import arc.scene.ui.Label.*;
import arc.scene.ui.ScrollPane.*;
import arc.scene.ui.TextButton.*;
import arc.scene.ui.TextField.*;

public class Styles {

	/// 选中框（空心矩形九宫格）
	public static final TextureRegionDrawable sel = new TextureRegionDrawable(Core.atlas.find("select"));
	public static ImageButtonStyle buttondef, buttonc;
	public static LabelStyle label;
	public static ScrollPaneStyle scrollPane;
	public static TextFieldStyle textField;
	public static TextButtonStyle textButton;
	public static DialogStyle window;
	public static Button.ButtonStyle buttonStyle;

	public static Drawable background;

	/// 分割线（白块染色，高度由使用处 cell 指定）
	public static Drawable divider;

	public static void load() {

		buttondef = new ImageButtonStyle();
		buttondef.up = Core.atlas.drawable("button-up");
		buttondef.down = Core.atlas.drawable("button-down");
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

		divider = new TextureRegionDrawable(Core.atlas.find("white"));

		buttonStyle = new Button.ButtonStyle();
		Core.scene.addStyle(Button.ButtonStyle.class, buttonStyle);
	}
}