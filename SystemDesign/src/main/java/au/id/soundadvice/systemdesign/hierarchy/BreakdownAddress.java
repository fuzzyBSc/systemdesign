/*
 * Please refer to the LICENSE file for licensing information.
 */
package au.id.soundadvice.systemdesign.hierarchy;

import au.id.soundadvice.systemdesign.util.ListCompare;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class BreakdownAddress implements Comparable<BreakdownAddress> {

	private final static BreakdownAddress context = new BreakdownAddress();
	private final ListCompare<String> listCompare = new ListCompare<>();

	public static BreakdownAddress getContext() {
		return context;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		String sep = "";
		for (String segment : address) {
			builder.append(sep);
			builder.append(segment);
			sep = ".";
		}
		return builder.toString();
	}

	@Override
	public int hashCode() {
		int hash = 5;
		hash = 11 * hash + Objects.hashCode(this.address);
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
		final BreakdownAddress other = (BreakdownAddress) obj;
		if (!Objects.equals(this.address, other.address)) {
			return false;
		}
		return true;
	}

	public BreakdownAddress getParent() {
		return new BreakdownAddress(address.subList(0, address.size() - 1));
	}

	public List<String> getAddress() {
		return address;
	}

	public String getUnqualifiedId() {
		return address.get(address.size() - 1);
	}

	private BreakdownAddress() {
		this.address = Collections.emptyList();
	}

	public BreakdownAddress(List<String> address) {
		this.address = Collections.unmodifiableList(new ArrayList<>(address));
	}
	private final List<String> address;

	@Override
	public int compareTo(BreakdownAddress other) {
		return listCompare.compare(address, other.address);
	}

	public BreakdownAddress getChild(String unqualifiedId) {
		List<String> childAddress = new ArrayList(address);
		childAddress.add(unqualifiedId);
		return new BreakdownAddress(childAddress);
	}

	public boolean isChildOf(BreakdownAddress parent) {
		return address.size() == parent.address.size() + 1
				&& parent.address.subList(0, address.size()).equals(address);
	}

	public boolean isDescendantOf(BreakdownAddress ancestor) {
		if (address.size() <= ancestor.address.size()) {
			return false;
		}
		for (int ii = 0; ii < ancestor.address.size(); ++ii) {
			if (address.get(ii).equals(ancestor.address.get(ii))) {
				// continue;
			} else {
				return false;
			}
		}
		return true;
	}

	public boolean isContext() {
		return address.isEmpty();
	}
}
