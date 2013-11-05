/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.input;

import au.id.soundadvice.systemdesign.hierarchy.BreakdownAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author fuzzy
 */
public class Token {

	public Token(BreakdownAddress address, String name) {
		this.address = address;
		this.name = name;
	}

	@Override
	public String toString() {
		if ("".equals(name)) {
			return address.toString();
		} else {
			return address + "." + name;
		}
	}

	@Override
	public int hashCode() {
		int hash = 5;
		hash = 89 * hash + Objects.hashCode(this.address);
		hash = 89 * hash + Objects.hashCode(this.name);
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
		final Token other = (Token) obj;
		if (!Objects.equals(this.address, other.address)) {
			return false;
		}
		if (!Objects.equals(this.name, other.name)) {
			return false;
		}
		return true;
	}

	public BreakdownAddress getAddress() {
		return address;
	}

	public String getName() {
		return name;
	}
	private final BreakdownAddress address;
	private final String name;

	Token(String value) {
		String[] split = value.split("\\.");
		List<String> segments = Arrays.asList(split);
		if (segments.size() == 1) {
			this.address = new BreakdownAddress(Collections.singletonList(value));
			this.name = address.getUnqualifiedId();
		} else {
			this.address = new BreakdownAddress(segments.subList(0, segments.size() - 1));
			this.name = segments.get(segments.size() - 1);
		}
	}
}
