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
import au.id.soundadvice.systemdesign.model.ItemView;
import au.id.soundadvice.systemdesign.model.UndoState;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javafx.scene.paint.Color;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class ExternalColorAutoFix {

    static UndoState fix(UndoState state) {
        Baseline functional = state.getFunctional();
        Baseline allocated = state.getAllocated();

        Map<UUID, Color> functionalViews = functional.getItemViews()
                .collect(Collectors.toMap(
                                view -> view.getItem().getUuid(),
                                ItemView::getColor));

        /*
         * Find external views in the allocated baseline that don't match the
         * color of the correspoding functional baseline view
         */
        Iterator<ItemView> it = allocated.getItemViews().iterator();
        while (it.hasNext()) {
            ItemView view = it.next();
            if (view.getItem().getTarget(allocated.getContext()).isExternal()) {
                Color allocatedColor = view.getColor();
                Color functionalColor = functionalViews.getOrDefault(
                        view.getItem().getUuid(), allocatedColor);
                if (!allocatedColor.equals(functionalColor)) {
                    allocated = view.setColor(allocated, functionalColor).getBaseline();
                }
            }
        }
        return state.setAllocated(allocated);
    }

}
