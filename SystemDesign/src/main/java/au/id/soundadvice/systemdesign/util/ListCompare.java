/*
 * Please refer to the LICENSE file for licensing information.
 */
package au.id.soundadvice.systemdesign.util;

import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

/**
 * Lexicographically compare two lists
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <T> List member type
 */
public class ListCompare<T extends Comparable<T>> implements Comparator<List<T>> {

	@Override
	public int compare(List<T> left, List<T> right) {
		ListIterator<T> leftIt = left.listIterator();
		ListIterator<T> rightIt = right.listIterator();
		for (;;) {
			if (leftIt.hasNext()) {
				if (rightIt.hasNext()) {
					T leftValue = leftIt.next();
					T rightValue = rightIt.next();
					int value = leftValue.compareTo(rightValue);
					if (value != 0) {
						return value;
					}
				} else {
					// right ran out first, so left is greater
					return 1;
				}
			} else {
				if (rightIt.hasNext()) {
					// Left ran out first, so left is lesser
					return -1;
				} else {
					// Both lists have run out, and are therefore equal
					return 0;
				}
			}
		}
	}
}
