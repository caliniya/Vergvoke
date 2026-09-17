package caliniya.vergvoke.ui;

import arc.func.Cons;
import arc.scene.ui.Label;
import arc.util.Align;
import arc.scene.ui.ImageButton;

public class Button extends ImageButton {

    public Label text;

    public Button(String text, Runnable action) {
        super();
        clicked(action);
        this.text = new Label(text);
        add(this.text).growX().scrollX(true).center().get().setAlignment(Align.center, Align.center);
        setStyle(Styles.buttondef);
    }

    public Button(Runnable action, String text) {
        super();
        clicked(action);
        this.text = new Label(text);
        add(this.text).growX().scrollX(true).center().get().setAlignment(Align.center, Align.center);
        setStyle(Styles.buttonc);
    }

    public Button set(Cons<Button> use) {
        use.get(this);
        return this;
    }

}
