/*
 * This is free and unencumbered software released into the public domain.
 * 
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 * 
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * 
 * For more information, please refer to <http://unlicense.org/>
 */
package au.id.soundadvice.systemdesign.consistency;

import au.id.soundadvice.systemdesign.baselines.AllocatedBaseline;
import au.id.soundadvice.systemdesign.baselines.FunctionalBaseline;
import au.id.soundadvice.systemdesign.baselines.UndoState;
import au.id.soundadvice.systemdesign.relation.Relation;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class SingleRelationSolution implements Solution {

    @Override
    public String toString() {
        if (modifyParent) {
            if (insert) {
                return "Insert " + relation + " into parent";
            } else {
                return "Remove " + relation + " from parent";
            }
        } else {
            if (insert) {
                return "Insert " + relation + " into child";
            } else {
                return "Remove " + relation + " from child";
            }
        }
    }

    public static SingleRelationSolution addToParent(String description, Relation relation) {
        return new SingleRelationSolution(true, description, relation, true);
    }

    public static SingleRelationSolution addToChild(String description, Relation relation) {
        return new SingleRelationSolution(false, description, relation, true);
    }

    public static SingleRelationSolution removeFromParent(String description, Relation relation) {
        return new SingleRelationSolution(true, description, relation, false);
    }

    public static SingleRelationSolution removeFromChild(String description, Relation relation) {
        return new SingleRelationSolution(false, description, relation, false);
    }

    private SingleRelationSolution(boolean modifyParent, String description, Relation relation, boolean insert) {
        this.modifyParent = modifyParent;
        this.description = description;
        this.relation = relation;
        this.insert = insert;
    }

    private final boolean modifyParent;
    private final String description;
    private final Relation relation;
    private final boolean insert;

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public UndoState solve(UndoState current) {
        if (modifyParent) {
            FunctionalBaseline functional = current.getFunctional();
            if (functional == null) {
                return current;
            }
            AllocatedBaseline baseline = functional.getContext();
            if (insert) {
                return current.setFunctional(
                        functional.setContext(baseline.add(relation)));
            } else {
                return current.setFunctional(
                        functional.setContext(baseline.remove(relation.getUuid())));
            }
        } else {
            AllocatedBaseline baseline = current.getAllocated();
            if (insert) {
                return current.setAllocated(baseline.add(relation));
            } else {
                return current.setAllocated(baseline.remove(relation.getUuid()));
            }
        }
    }
}
