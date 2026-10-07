package caliniya.vergvoke.type.def.craft.output;

@SuppressWarnings("unchecked")
public class Outputer<T extends Outputer<?>> {

	public T bind(Module module) {
		return (T) this;
	}

	
}
