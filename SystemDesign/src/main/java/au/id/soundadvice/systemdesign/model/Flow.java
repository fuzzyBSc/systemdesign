/*
 * Please refer to the LICENSE file for licensing information.
 */
package au.id.soundadvice.systemdesign.model;

import au.id.soundadvice.systemdesign.hierarchy.BreakdownAddress;
import au.id.soundadvice.systemdesign.hierarchy.Hierarchical;
import au.id.soundadvice.systemdesign.hierarchy.Hierarchy;
import au.id.soundadvice.systemdesign.hierarchy.InvalidHierarchyException;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public final class Flow implements Hierarchical<Flow> {

	@Override
	public BreakdownAddress getAddress() {
		return flowHierarchy.getAddress();
	}

	@Override
	@Nullable
	public Flow getTrace() {
		return flowHierarchy.getTrace();
	}

	@Override
	public void addChild(Flow child) throws InvalidHierarchyException {
		flowHierarchy.addChild(child);
	}

	@Override
	public void removeChild(Flow child) {
		flowHierarchy.removeChild(child);
	}

	@Override
	public Map<BreakdownAddress, Flow> getChildren() {
		return flowHierarchy.getChildren();
	}

	public Function getFunctionTrace() {
		return functionTrace;
	}

	@Override
	public int hashCode() {
		int hash = 5;
		hash = 67 * hash + Objects.hashCode(this.source);
		hash = 67 * hash + Objects.hashCode(this.type);
		hash = 67 * hash + Objects.hashCode(this.target);
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
		final Flow other = (Flow) obj;
		if (!Objects.equals(this.source, other.source)) {
			return false;
		}
		if (!Objects.equals(this.type, other.type)) {
			return false;
		}
		if (!Objects.equals(this.target, other.target)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return source + " --" + type + "-> " + target;
	}

	public Interface getInterface() {
		return iface;
	}

	public FlowNode getSource() {
		return source;
	}

	public FlowType getType() {
		return type;
	}

	public FlowNode getTarget() {
		return target;
	}

	public Flow(Function trace, Interface iface, FlowNode source, FlowType type, FlowNode target) {
		this.flowHierarchy = new Hierarchy<>(this);
		this.functionTrace = trace;
		this.iface = iface;
		this.source = source;
		this.type = type;
		this.target = target;
	}

	public Flow(Flow trace, Interface iface, FlowNode source, FlowType type, FlowNode target) {
		this.flowHierarchy = new Hierarchy<>(
				trace, this,
				trace.getAddress().getChild(source.toString() + "-" + type.toString() + "->" + target.toString()));
		this.functionTrace = null;
		this.iface = iface;
		this.source = source;
		this.type = type;
		this.target = target;
	}

	@Override
	public void bind() throws InvalidHierarchyException {
		flowHierarchy.bind();
		if (functionTrace != null) {
			functionTrace.addSubFlow(this);
		}
		source.addOutFlow(this);
		target.addInFlow(this);
		iface.addFlow(this);
	}

	@Override
	public void unbind() {
		flowHierarchy.unbind();
		if (functionTrace != null) {
			functionTrace.removeSubFlow(this);
		}
		source.removeOutFlow(this);
		target.removeInFlow(this);
		iface.removeFlow(this);
	}
	private final Hierarchy<Flow> flowHierarchy;
	private final Function functionTrace;
	private final Interface iface;
	private final FlowNode source;
	private final FlowType type;
	private final FlowNode target;
}
