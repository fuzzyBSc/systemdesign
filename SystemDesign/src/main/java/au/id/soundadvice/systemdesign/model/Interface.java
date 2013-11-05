/*
 * Please refer to the LICENSE file for licensing information.
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
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public final class Interface implements Hierarchical<Interface> {

	public InterfaceKey getKey() {
		return key;
	}

	@Override
	public BreakdownAddress getAddress() {
		return interfaceHierarchy.getAddress();
	}

	@Override
	@Nullable
	public Interface getTrace() {
		return interfaceHierarchy.getTrace();
	}

	@Override
	public void addChild(Interface child) throws InvalidHierarchyException {
		interfaceHierarchy.addChild(child);
	}

	@Override
	public void removeChild(Interface child) {
		interfaceHierarchy.removeChild(child);
	}

	@Override
	public Map<BreakdownAddress, Interface> getChildren() {
		return interfaceHierarchy.getChildren();
	}

	@Override
	public String toString() {
		return key.toString();
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 37 * hash + Objects.hashCode(this.key);
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
		final Interface other = (Interface) obj;
		if (!Objects.equals(this.key, other.key)) {
			return false;
		}
		return true;
	}

	@Nullable
	public Item getItemTrace() {
		return itemTrace;
	}

	public Item getLeft() {
		return left;
	}

	public Item getRight() {
		return right;
	}

	public Set<Flow> getFlows() {
		return flows;
	}

	/**
	 * Constructor for ordinary interface
	 */
	public Interface(Item trace, Item left, Item right) {
		this.key = new InterfaceKey(left.getAddress(), right.getAddress());
		this.interfaceHierarchy = new Hierarchy<>(this);
		this.itemTrace = trace;
		if (key.getLeft().equals(left.getAddress())) {
			this.left = left;
			this.right = right;
		} else {
			this.left = right;
			this.right = left;
		}
	}

	/**
	 * Constructor for ordinary sub-interface
	 */
	public Interface(Interface trace, Item left, Item right) {
		this.key = new InterfaceKey(left.getAddress(), right.getAddress());
		this.interfaceHierarchy = new Hierarchy<>(
				trace, this, trace.getAddress().getChild(key.toString()));
		this.itemTrace = null;
		// Normalise ordering in line with the interface key
		if (key.getLeft().equals(left.getAddress())) {
			this.left = left;
			this.right = right;
		} else {
			this.left = right;
			this.right = left;
		}
	}

	@Override
	public void bind() throws InvalidHierarchyException {
		interfaceHierarchy.bind();
		if (itemTrace != null) {
			itemTrace.addSubInterface(this);
		}
		left.addExternalInterface(this);
		right.addExternalInterface(this);
	}

	@Override
	public void unbind() {
		interfaceHierarchy.unbind();
		if (itemTrace != null) {
			itemTrace.removeSubInterface(this);
		}
		left.removeExternalInterface(this);
		right.removeExternalInterface(this);
	}
	private final Hierarchy<Interface> interfaceHierarchy;
	@Nullable
	private final Item itemTrace;
	private final InterfaceKey key;
	private final Item left;
	private final Item right;
	private final Set<Flow> flows = Collections.newSetFromMap(new ConcurrentHashMap<Flow, Boolean>());

	void addFlow(Flow flow) {
		this.flows.add(flow);
	}

	void removeFlow(Flow flow) {
		this.flows.remove(flow);
	}
}
