/*
 * Please refer to the LICENSE file for licensing information.
 */
package au.id.soundadvice.systemdesign.input;

import au.id.soundadvice.systemdesign.hierarchy.BreakdownAddress;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public interface InputNode {

	public BreakdownAddress getAddress();

	public boolean isFunction();
	
}
