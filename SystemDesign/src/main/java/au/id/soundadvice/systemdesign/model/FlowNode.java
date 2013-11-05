/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.model;

import au.id.soundadvice.systemdesign.hierarchy.BreakdownAddress;

/**
 *
 * @author fuzzy
 */
public interface FlowNode {

	public void addOutFlow(Flow aThis);

	public void addInFlow(Flow aThis);

	public void removeOutFlow(Flow aThis);

	public void removeInFlow(Flow aThis);

	public Item getItem();
	
	public boolean isItem();

	public BreakdownAddress getAddress();

	public String getName();
}
