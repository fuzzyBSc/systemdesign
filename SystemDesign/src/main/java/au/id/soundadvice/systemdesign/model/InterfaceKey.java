/*
 * Please refer to the LICENSE file for licensing information.
 */
package au.id.soundadvice.systemdesign.model;

import au.id.soundadvice.systemdesign.hierarchy.BreakdownAddress;
import java.util.Objects;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class InterfaceKey implements Comparable<InterfaceKey> {

	@Override
	public String toString() {
		return left + ":" + right;
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 47 * hash + Objects.hashCode(this.left);
		hash = 47 * hash + Objects.hashCode(this.right);
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
		final InterfaceKey other = (InterfaceKey) obj;
		if (!Objects.equals(this.left, other.left)) {
			return false;
		}
		if (!Objects.equals(this.right, other.right)) {
			return false;
		}
		return true;
	}

	public BreakdownAddress getLeft() {
		return left;
	}

	public BreakdownAddress getRight() {
		return right;
	}

	public InterfaceKey(BreakdownAddress left, BreakdownAddress right) {
		// Normalise ordering
		if (left.compareTo(right) < 0) {
			this.left = left;
			this.right = right;
		} else {
			this.left = right;
			this.right = left;
		}
	}
	private final BreakdownAddress left;
	private final BreakdownAddress right;

	@Override
	public int compareTo(InterfaceKey other) {
		{
			int value = left.compareTo(other.left);
			if (value != 0) {
				return value;
			}
		}
		{
			int value = right.compareTo(other.right);
			if (value != 0) {
				return value;
			}
		}
		return 0;
	}

	public boolean contains(BreakdownAddress address) {
		return left.equals(address) || right.equals(address);
	}
}
