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
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class UpdateSolution implements Solution {

    public static UpdateSolution update(String description, UnaryOperator<UndoState> update) {
        return new UpdateSolution(description, update);
    }

    public static UpdateSolution updateFunctional(
            String description, UnaryOperator<FunctionalBaseline> update) {
        return new UpdateSolution(description, state -> {
            FunctionalBaseline functional = state.getFunctional();
            if (functional == null) {
                return state;
            }
            return state.setFunctional(update.apply(functional));
        });
    }

    public static <T extends Relation> UpdateSolution updateFunctionalRelation(
            String description,
            UUID uuid, Class<T> type,
            UnaryOperator<T> update) {
        return updateFunctional(description, baseline -> {
            T current = baseline.getStore().get(uuid, type);
            if (current == null) {
                return baseline;
            }
            T updated = update.apply(current);
            return baseline.add(updated);
        });
    }

    public static UpdateSolution updateAllocated(
            String description, UnaryOperator<AllocatedBaseline> update) {
        return new UpdateSolution(description, state -> {
            AllocatedBaseline allocated = state.getAllocated();
            return state.setAllocated(update.apply(allocated));
        });
    }

    public static <T extends Relation> UpdateSolution updateAllocatedRelation(
            String description,
            UUID uuid, Class<T> type,
            UnaryOperator<T> update) {
        return updateAllocated(description, baseline -> {
            T current = baseline.getStore().get(uuid, type);
            if (current == null) {
                return baseline;
            }
            T updated = update.apply(current);
            return baseline.add(updated);
        });
    }

    private UpdateSolution(String description, UnaryOperator<UndoState> update) {
        this.description = description;
        this.update = update;
    }

    private final String description;
    private final UnaryOperator<UndoState> update;

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void solve(EditState edit) {
        edit.getUndo().update(update);
    }
}
