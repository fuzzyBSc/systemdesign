/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.input;

import au.id.soundadvice.systemdesign.hierarchy.BreakdownAddress;
import java.util.Objects;

/**
 *
 * @author fuzzy
 */
public class InputFunction implements InputNode {

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 83 * hash + Objects.hashCode(this.item);
		hash = 83 * hash + Objects.hashCode(this.token);
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
		final InputFunction other = (InputFunction) obj;
		if (!Objects.equals(this.item, other.item)) {
			return false;
		}
		if (!Objects.equals(this.token, other.token)) {
			return false;
		}
		return true;
	}
	private final InputItem item;
	private final Token token;

	@Override
	public String toString() {
		if (token == null) {
			return "";
		} else {
			return token.toString();
		}
	}

	public InputItem getItem() {
		return item;
	}

	public Token getToken() {
		return token;
	}

	InputFunction(InputItem item, Token token) {
		this.item = item;
		this.token = token;
	}

	void bind() {
		item.addFunction(this);
	}

	@Override
	public BreakdownAddress getAddress() {
		return token.getAddress();
	}

	@Override
	public boolean isFunction() {
		return true;
	}
}
