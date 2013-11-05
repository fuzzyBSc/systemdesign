/*
 * Please refer to the LICENSE file for licensing information.
 */
package au.id.soundadvice.systemdesign.input;

import java.util.Objects;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class InputFlow {

	@Override
	public String toString() {
		return source + " --" + flowType + "-> " + target;
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 17 * hash + Objects.hashCode(this.source);
		hash = 17 * hash + Objects.hashCode(this.flowType);
		hash = 17 * hash + Objects.hashCode(this.target);
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
		final InputFlow other = (InputFlow) obj;
		if (!Objects.equals(this.source, other.source)) {
			return false;
		}
		if (!Objects.equals(this.flowType, other.flowType)) {
			return false;
		}
		if (!Objects.equals(this.target, other.target)) {
			return false;
		}
		return true;
	}

	public InputNode getSource() {
		return source;
	}

	public Token getFlowType() {
		return flowType;
	}

	public InputNode getTarget() {
		return target;
	}
	private final InputNode source;
	private final Token flowType;
	private final InputNode target;

	public InputFlow(InputNode source, Token flowType, InputNode target) {
		this.source = source;
		this.flowType = flowType;
		this.target = target;
	}
}
