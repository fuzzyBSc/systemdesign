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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 *
 * @author fuzzy
 */
public class Item implements Hierarchical<Item>, FlowNode {

	@Override
	public BreakdownAddress getAddress() {
		return itemHierarchy.getAddress();
	}

	@Override
	@Nullable
	public Item getTrace() {
		return itemHierarchy.getTrace();
	}

	@Override
	public void addChild(Item child) throws InvalidHierarchyException {
		itemHierarchy.addChild(child);
	}

	@Override
	public void removeChild(Item child) {
		itemHierarchy.removeChild(child);
	}

	@Override
	public Map<BreakdownAddress, Item> getChildren() {
		return itemHierarchy.getChildren();
	}

	@Override
	public void bind() throws InvalidHierarchyException {
		itemHierarchy.bind();
	}

	@Override
	public void unbind() {
		itemHierarchy.unbind();
	}

	public Set<Flow> getInFlows() {
		return inFlows;
	}

	public Set<Flow> getOutFlows() {
		return outFlows;
	}

	@Override
	public String toString() {
		if (itemHierarchy.getAddress().isContext()) {
			return "Context";
		} else {
			return itemHierarchy + " " + name;
		}
	}

	public Map<Item, Interface> getExternalInterfaces() {
		return externalInterfaces;
	}

	public Map<InterfaceKey, Interface> getSubInterfaces() {
		return subInterfaces;
	}

	@Override
	public String getName() {
		return name;
	}

	public Set<Function> getFunctions() {
		return functions;
	}

	public Item() {
		this.itemHierarchy = new Hierarchy<>(this);
		this.name = "";
	}

	public Item(Item context, BreakdownAddress address, String name) {
		this.itemHierarchy = new Hierarchy<>(context, this, address);
		this.name = name;
	}
	private final Hierarchy<Item> itemHierarchy;
	/**
	 * A short descriptive name for this subsystem within the context
	 */
	private final String name;
	/**
	 * The external interfaces of this item that exist either within our context
	 * or some ancestor of our context.
	 */
	private final ConcurrentMap<Item, Interface> externalInterfaces = new ConcurrentHashMap<>();
	/**
	 * The internal interfaces of this item. These are interfaces between this
	 * item's subsystems.
	 */
	private final ConcurrentMap<InterfaceKey, Interface> subInterfaces = new ConcurrentHashMap<>();
	/**
	 * The functions this item is responsible for.
	 */
	private final Set<Function> functions = Collections.newSetFromMap(new ConcurrentHashMap<Function, Boolean>());
	/**
	 * The list of our own subsystems.
	 */
	private final Set<Flow> inFlows = Collections.newSetFromMap(new ConcurrentHashMap<Flow, Boolean>());
	private final Set<Flow> outFlows = Collections.newSetFromMap(new ConcurrentHashMap<Flow, Boolean>());

	public void addSubInterface(Interface iface) {
		subInterfaces.put(iface.getKey(), iface);
	}

	public void removeSubInterface(Interface iface) {
		subInterfaces.remove(iface.getKey(), iface);
	}

	void addExternalInterface(Interface iface) {
		if (!iface.getLeft().equals(this)) {
			externalInterfaces.put(iface.getLeft(), iface);
		}
		if (!iface.getRight().equals(this)) {
			externalInterfaces.put(iface.getRight(), iface);
		}
	}

	void removeExternalInterface(Interface iface) {
		if (!iface.getLeft().equals(this)) {
			externalInterfaces.remove(iface.getLeft(), iface);
		}
		if (!iface.getRight().equals(this)) {
			externalInterfaces.remove(iface.getRight(), iface);
		}
	}

	public Set<Interface> getSubsystemInterfaces() {
		Set<Interface> interfaces = new HashSet<>();
		for (Item subsystem : itemHierarchy.getChildren().values()) {
			interfaces.addAll(subsystem.getExternalInterfaces().values());
		}
		// Prune subinterfaces
		Set<Interface> subinterfaces = new HashSet<>();
		for (Interface iface : interfaces) {
			Interface interfaceTrace = iface.getTrace();
			if (interfaceTrace != null) {
				if (interfaces.contains((Interface) interfaceTrace)) {
					subinterfaces.add(iface);
				}
			}
		}
		interfaces.removeAll(subinterfaces);
		return interfaces;
	}

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

	@Override
	public Item getItem() {
		return this;
	}

	@Override
	public boolean isItem() {
		return true;
	}

	void addFunction(Function function) {
		functions.add(function);
	}

	void removeFunction(Function function) {
		functions.remove(function);
	}
}
