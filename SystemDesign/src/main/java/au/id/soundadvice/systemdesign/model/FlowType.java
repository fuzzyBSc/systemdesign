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
import java.util.Map;
import java.util.Objects;

/**
 *
 * @author fuzzy
 */
public class FlowType implements Hierarchical<FlowType> {

	@Override
	public String toString() {
		return name;
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 79 * hash + Objects.hashCode(this.hierarchy);
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
		final FlowType other = (FlowType) obj;
		if (!Objects.equals(this.hierarchy, other.hierarchy)) {
			return false;
		}
		if (!Objects.equals(this.name, other.name)) {
			return false;
		}
		return true;
	}

	public String getName() {
		return name;
	}

	/**
	 * Create the context flow type
	 */
	public FlowType() {
		hierarchy = new Hierarchy<>(this);
		this.name = "Context";
	}

	/**
	 * Create a regular flow type
	 */
	public FlowType(FlowType trace, BreakdownAddress address, String name) {
		hierarchy = new Hierarchy<>(trace, this, address);
		this.name = name;
	}

	@Override
	public BreakdownAddress getAddress() {
		return hierarchy.getAddress();
	}

	@Override
	@Nullable
	public FlowType getTrace() {
		return hierarchy.getTrace();
	}

	@Override
	public void addChild(FlowType child) throws InvalidHierarchyException {
		hierarchy.addChild(child);
	}

	@Override
	public void removeChild(FlowType child) {
		hierarchy.removeChild(child);
	}

	@Override
	public Map<BreakdownAddress, FlowType> getChildren() {
		return hierarchy.getChildren();
	}

	@Override
	public void bind() throws InvalidHierarchyException {
		hierarchy.bind();
	}

	@Override
	public void unbind() {
		hierarchy.unbind();
	}
	private final Hierarchy<FlowType> hierarchy;
	private final String name;
}
