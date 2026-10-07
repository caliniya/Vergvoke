package caliniya.vergvoke.ui.windows;

import arc.graphics.g2d.*;
import arc.scene.ui.layout.*;
import caliniya.vergvoke.core.meta.stat.*;

public class StatWindow extends Window {

	public TextureRegion icon;

	public Stat stat;

	public StatWindow(Stat stat, TextureRegion icon) {
		this.icon = icon;
		this.stat = stat;
		main.update(() -> {

		});


	}

	@Override
	public void main(Table t) {

	}
}
