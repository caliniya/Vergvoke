package caliniya.vergvoke.type;

import arc.util.io.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.type.type.*;

/**
 * 表示物品栈
 */
public class Item {

	public ItemType type;
	public int amount;

	public Item(ItemType type, int amount) {
		this.type = type;
		this.amount = amount;
	}

	public void write(Writes write) {
		write.i(type == null ? -1 : type.id);
		write.i(amount);
	}

	public void read(Reads read) {
		int id = read.i();
		this.type = Contents.getByID(CType.Item, id);
		this.amount = read.i();
	}

	public void set(ItemType type, int amount) {
		this.type = type;
		this.amount = amount;
	}

	public boolean isEmpty() {
		return type == null || amount <= 0;
	}
}