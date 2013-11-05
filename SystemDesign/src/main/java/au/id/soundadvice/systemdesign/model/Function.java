/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.model;

import au.id.soundadvice.systemdesign.hierarchy.BreakdownAddress;
import au.id.soundadvice.systemdesign.hierarchy.Hierarchical;
import au.id.soundadvice.systemdesign.hierarchy.Hierarchy;
import au.id.soundadvice.systemdesign.hierarchy.InvalidHierarchyException;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author fuzzy
 */
public final class Function implements Hierarchical<Function>, FlowNode {

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 79 * hash + Objects.hashCode(this.functionHierarchy);
		hash = 79 * hash + Objects.hashCode(this.item);
		hash = 79 * hash + Objects.hashCode(this.name);
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final Function other = (Function) obj;
		if (!Objects.equals(this.functionHierarchy, other.functionHierarchy)) {
			return false;
		}
		if (!Objects.equals(this.item, other.item)) {
			return false;
		}
		if (!Objects.equals(this.name, other.name)) {
			return false;
		}
		return true;
	}

	@Override
	public BreakdownAddress getAddress() {
		return functionHierarchy.getAddress();
	}

	@Override
	@Nullable
	public Function getTrace() {
		return functionHierarchy.getTrace();
	}

	@Override
	public void addChild(Function child) throws InvalidHierarchyException {
		functionHierarchy.addChild(child);
	}

	@Override
	public void removeChild(Function child) {
		functionHierarchy.removeChild(child);
	}

	@Override
	public Map<BreakdownAddress, Function> getChildren() {
		return functionHierarchy.getChildren();
	}

	@Override
	public String toString() {
		if (functionHierarchy.getAddress().isContext()) {
			return "Context";
		} else {
			return functionHierarchy + " " + name;
		}
	}

	public Set<Flow> getSubFlows() {
		return subFlows;
	}

	@Override
	public Item getItem() {
		return item;
	}

	@Override
	public boolean isItem() {
		return false;
	}

	/**
	 * Get the identifier for the function.
	 */
	@Override
	public String getName() {
		return name;
	}

	public Set<Flow> getInFlows() {
		return inFlows;
	}

	public Set<Flow> getOutFlows() {
		return outFlows;
	}

	/**
	 * Create a context function
	 */
	public Function() {
		this.functionHierarchy = new Hierarchy(this);
		this.item = null;
		this.name = "Context";
	}

	/**
	 * Create a sub-function.
	 */
	public Function(Function trace, Item item, BreakdownAddress address, String name) {
		this.functionHierarchy = new Hierarchy(trace, this, address);
		this.item = item;
		this.name = name;
	}

	@Override
	public void bind() throws InvalidHierarchyException {
		functionHierarchy.bind();
		if (item != null) {
			item.addFunction(this);
		}
	}

	@Override
	public void unbind() {
		functionHierarchy.unbind();
		if (item != null) {
			item.removeFunction(this);
		}
	}
	private final Hierarchy<Function> functionHierarchy;
	@Nullable
	private final Item item;
	private final String name;
	private final Set<Flow> inFlows = Collections.newSetFromMap(new ConcurrentHashMap<Flow, Boolean>());
	private final Set<Flow> outFlows = Collections.newSetFromMap(new ConcurrentHashMap<Flow, Boolean>());
	private final Set<Flow> subFlows = Collections.newSetFromMap(new ConcurrentHashMap<Flow, Boolean>());

	@Override
	public void addInFlow(Flow flow) {
		this.inFlows.add(flow);
	}

	@Override
	public void addOutFlow(Flow flow) {
		this.outFlows.add(flow);
	}

	@Override
	public void removeInFlow(Flow flow) {
		this.inFlows.remove(flow);
	}

	@Override
	public void removeOutFlow(Flow flow) {
		this.outFlows.remove(flow);
	}

	public void addSubFlow(Flow flow) {
		this.subFlows.add(flow);
	}

	public void removeSubFlow(Flow flow) {
		this.subFlows.remove(flow);
	}
}
