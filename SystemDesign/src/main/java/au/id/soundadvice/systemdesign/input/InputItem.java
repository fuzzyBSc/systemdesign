/*
 * Please refer to the LICENSE file for licensing information.
 */
package au.id.soundadvice.systemdesign.input;

import au.id.soundadvice.systemdesign.hierarchy.BreakdownAddress;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class InputItem implements InputNode {

	@Override
	public int hashCode() {
		int hash = 7;
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
		final InputItem other = (InputItem) obj;
		if (!Objects.equals(this.token, other.token)) {
			return false;
		}
		return true;
	}
	private final Token token;
	private final Map<String, InputFunction> functions = new ConcurrentHashMap<>();

	@Override
	public String toString() {
		return token.toString();
	}

	public Token getToken() {
		return token;
	}

	InputItem(Token token) throws IOException {
		this.token = token;
	}

	void addFunction(InputFunction function) {
		Token functionToken = function.getToken();
		if (functionToken == null) {
			functions.put("", function);
		} else {
			functions.put(functionToken.getName(), function);
		}
	}

	@Override
	public BreakdownAddress getAddress() {
		return token.getAddress();
	}

	@Override
	public boolean isFunction() {
		return false;
	}
}
