/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.hierarchy;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 *
 * @author fuzzy
 */
public class Hierarchy<T extends Hierarchical<T>> implements Comparable<Hierarchy<T>> {

	@Override
	public String toString() {
		return address.toString();
	}

	@Override
	public int hashCode() {
		int hash = 5;
		hash = 83 * hash + Objects.hashCode(this.address);
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
		final Hierarchy<T> other = (Hierarchy<T>) obj;
		if (!Objects.equals(this.address, other.address)) {
			return false;
		}
		return true;
	}

	@Override
	public int compareTo(Hierarchy<T> other) {
		if (other == null) {
			return 1;
		}
		{
			int value = address.compareTo(other.address);
			if (value != 0) {
				return value;
			}
		}
		return 0;
	}

	/**
	 * Constructor for a context node.
	 */
	public Hierarchy(T self) {
		this.address = BreakdownAddress.getContext();
		this.self = self;
		this.trace = null;
	}

	/**
	 * Constructor for a typical hierarchy member.
	 */
	public Hierarchy(T trace, T self, BreakdownAddress address) {
		this.address = address;
		this.self = self;
		this.trace = trace;
	}
	private final BreakdownAddress address;
	@Nullable
	private final T trace;
	private final T self;
	private final ConcurrentMap<BreakdownAddress, T> children = new ConcurrentHashMap<>();

	public BreakdownAddress getAddress() {
		return address;
	}

	@Nullable
	public T getTrace() {
		return trace;
	}

	public void addChild(T child) throws InvalidHierarchyException {
		T oldValue = children.putIfAbsent(child.getAddress(), child);
		if (oldValue != null) {
			throw new InvalidHierarchyException(
					"Address " + child.getAddress()
					+ " conflicts with an already-registered address");
		}
	}

	public void removeChild(T child) {
		children.remove(child.getAddress(), child);
	}

	public Map<BreakdownAddress, T> getChildren() {
		return new HashMap<>(children);
	}

	public void bind() throws InvalidHierarchyException {
		if (trace == null) {
			if (address.isContext() || address.isChildOf(BreakdownAddress.getContext())) {
				// The lack of a trace is ok and expected
			} else {
				throw new InvalidHierarchyException(self + " has no trace");
			}
		} else {
			if (!trace.getAddress().equals(address.getParent())) {
				throw new InvalidHierarchyException(
						"Cannot place child " + self + " under parent " + trace);
			}
			trace.addChild(self);
		}
	}

	public void unbind() {
		trace.removeChild(self);
	}
}
