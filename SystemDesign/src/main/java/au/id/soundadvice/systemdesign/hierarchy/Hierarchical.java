/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.hierarchy;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;

/**
 *
 * @author fuzzy
 */
public interface Hierarchical<T extends Hierarchical<T>> {

	/**
	 * Returns the absolute address of this item within the hierarchy
	 */
	public BreakdownAddress getAddress();

	/**
	 * Returns the parent object within the hierarchy
	 */
	@Nullable
	public T getTrace();

	/**
	 * Registers a child object with its parent within the hierarchy. This
	 * method is internal, and should be invoked from within bind().
	 *
	 * @param child
	 * @throws InvalidHierarchyException If the child address does not match the
	 * parent's address, or a child is already registered with the nominated
	 * address.
	 */
	public void addChild(T child) throws InvalidHierarchyException;

	/**
	 * Removes an existing child. This method is internal, and should be invoked
	 * from within unbind().
	 */
	public void removeChild(T child);

	/**
	 * Returns an unmodifiable map child address to child object
	 */
	public Map<BreakdownAddress, T> getChildren();

	/**
	 * Bind this member of the hierarchy to its parent.
	 */
	public void bind() throws InvalidHierarchyException;

	/**
	 * Disconnect this member of the hierarchy from its parent.
	 */
	public void unbind();
}
