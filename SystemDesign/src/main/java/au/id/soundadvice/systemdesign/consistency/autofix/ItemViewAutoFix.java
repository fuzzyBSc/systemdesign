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
package au.id.soundadvice.systemdesign.consistency.autofix;

import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.model.UndoState;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.model.ItemView;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class ItemViewAutoFix {

    static UndoState fix(UndoState state) {
        final UndoState preRemoveState = state;
        Stream<ItemView> removals = preRemoveState.getAllocated().getItems()
                .flatMap(item -> {
                    return item.getViews(preRemoveState.getAllocated())
                    // One view should exist for each item, so skip that one
                    .skip(1);
                });
        {
            Iterator<ItemView> it = removals.iterator();
            Baseline allocated = state.getAllocated();
            while (it.hasNext()) {
                ItemView view = it.next();
                allocated = view.removeFrom(allocated);
            }
            state = state.setAllocated(allocated);
        }

        final UndoState preAddState = state;
        Stream<Item> additions = preAddState.getAllocated().getItems()
                .filter(item -> !item.getViews(preAddState.getAllocated()).findAny().isPresent());
        {
            Iterator<Item> it = additions.iterator();
            Baseline allocated = state.getAllocated();
            while (it.hasNext()) {
                Item item = it.next();

                allocated = ItemView.create(allocated, item, ItemView.defaultOrigin)
                        .getBaseline();
            }
            state = state.setAllocated(allocated);
        }

        return state;
    }

}
