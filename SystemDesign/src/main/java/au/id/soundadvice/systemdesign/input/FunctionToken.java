/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.input;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.Objects;

/**
 *
 * @author fuzzy
 */
public class FunctionToken {

	@Override
	public String toString() {
		if (functionToken == null) {
			return itemToken.toString();
		} else {
			return itemToken + "/" + functionToken;
		}
	}

	@Override
	public int hashCode() {
		int hash = 5;
		hash = 89 * hash + Objects.hashCode(this.itemToken);
		hash = 89 * hash + Objects.hashCode(this.functionToken);
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
		final FunctionToken other = (FunctionToken) obj;
		if (!Objects.equals(this.itemToken, other.itemToken)) {
			return false;
		}
		if (!Objects.equals(this.functionToken, other.functionToken)) {
			return false;
		}
		return true;
	}

	public Token getItemToken() {
		return itemToken;
	}

	@Nullable
	public Token getFunctionToken() {
		return functionToken;
	}
	private final Token itemToken;
	@Nullable
	private final Token functionToken;

	FunctionToken(String text) throws IOException {
		String[] tokens = text.split("/");
		switch (tokens.length) {
			case 1:
				this.itemToken = new Token(text);
				this.functionToken = null;
				break;
			case 2:
				this.itemToken = new Token(tokens[0]);
				this.functionToken = new Token(tokens[1]);
				break;
			default:
				throw new IOException("Invalid function token: " + text);
		}
	}
}
