/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.input;

import au.id.soundadvice.systemdesign.hierarchy.BreakdownAddress;

/**
 *
 * @author fuzzy
 */
public interface InputNode {

	public BreakdownAddress getAddress();

	public boolean isFunction();
	
}
