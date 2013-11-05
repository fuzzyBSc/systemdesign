/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.model;

/**
 *
 * @author fuzzy
 */
public class Model {

	@Override
	public String toString() {
		return "Model{" + "itemContext=" + itemContext + ", typeContext=" + typeContext + ", functionContext=" + functionContext + '}';
	}

	public Item getItemContext() {
		return itemContext;
	}

	public FlowType getTypeContext() {
		return typeContext;
	}

	public Function getFunctionContext() {
		return functionContext;
	}

	public Model(Item itemContext, Function functionContext, FlowType typeContext) {
		this.itemContext = itemContext;
		this.typeContext = typeContext;
		this.functionContext = functionContext;
	}
	private final Item itemContext;
	private final FlowType typeContext;
	private final Function functionContext;
}
