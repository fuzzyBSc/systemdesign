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
import au.id.soundadvice.systemdesign.baselines.EditState;
import au.id.soundadvice.systemdesign.baselines.FunctionalBaseline;
import au.id.soundadvice.systemdesign.baselines.UndoState;
import au.id.soundadvice.systemdesign.relation.Relation;
import au.id.soundadvice.systemdesign.baselines.UndoBuffer;

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
    public void solve(EditState edit) {
        UndoBuffer<UndoState> undo = edit.getUndo();
        UndoState state = undo.get();
        if (modifyParent) {
            FunctionalBaseline functional = state.getFunctional();
            if (functional == null) {
                // Don't modify the edit state
                return;
            }
            if (insert) {
                undo.set(state.setFunctional(functional.add(relation)));
            } else {
                undo.set(state.setFunctional(functional.remove(relation.getUuid())));
            }
        } else {
            AllocatedBaseline baseline = state.getAllocated();
            if (insert) {
                undo.set(state.setAllocated(baseline.add(relation)));
            } else {
                undo.set(state.setAllocated(baseline.remove(relation.getUuid())));
            }
        }
    }
}
