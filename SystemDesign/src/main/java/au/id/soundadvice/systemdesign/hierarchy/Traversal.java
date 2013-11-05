/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.hierarchy;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 *
 * @author fuzzy
 */
public class Traversal {

	static <T extends Hierarchical<T>> Map<BreakdownAddress, T> getMembers(T root) {
		Map<BreakdownAddress, T> result = new HashMap<>();
		Stack<T> stack = new Stack<>();
		stack.add(root);
		while (!stack.isEmpty()) {
			T current = stack.pop();
			Map<BreakdownAddress, T> children = current.getChildren();
			result.putAll(children);
			stack.addAll(children.values());
		}
		return result;
	}

	@Nullable
	static <T extends Hierarchical<T>> T getMember(T root, BreakdownAddress address) {
		T current = root;
		while (address.isDescendantOf(current.getAddress())) {
			T next = null;
			for (T candidate : current.getChildren().values()) {
				if (address.isDescendantOf(candidate.getAddress())) {
					next = candidate;
					break;
				}
			}
			if (next == null) {
				return null;
			} else {
				current = next;
			}
		}
		return null;
	}
}
