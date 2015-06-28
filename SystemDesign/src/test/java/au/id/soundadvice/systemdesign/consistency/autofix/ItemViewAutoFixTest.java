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

import au.id.soundadvice.systemdesign.model.Baseline.BaselineAnd;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.model.ItemView;
import au.id.soundadvice.systemdesign.model.UndoState;
import java.util.concurrent.atomic.AtomicReference;
import javafx.geometry.Point2D;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class ItemViewAutoFixTest {

    /**
     * Test of fix method, of class ItemViewAutoFix.
     */
    @Test
    public void testFix() {
        System.out.println("fix");
        UndoState state = UndoState.createNew();
        AtomicReference<Item> missingViewItem = new AtomicReference<>();
        state = state.updateAllocated(allocated -> {
            BaselineAnd<Item> pair = Item.create(allocated, Point2D.ZERO);
            allocated = pair.getBaseline();
            Item item = pair.getRelation();
            missingViewItem.set(item);
            allocated = item.getView(allocated).removeFrom(allocated);
            assertEquals(0, item.getViews(allocated).count());
            return allocated;
        });
        AtomicReference<Item> extraViewItem = new AtomicReference<>();
        state = state.updateAllocated(allocated -> {
            BaselineAnd<Item> pair = Item.create(allocated, Point2D.ZERO);
            allocated = pair.getBaseline();
            Item item = pair.getRelation();
            extraViewItem.set(item);
            allocated = ItemView.create(allocated, item, Point2D.ZERO)
                    .getBaseline();
            assertEquals(2, item.getViews(allocated).count());
            return allocated;
        });
        UndoState result = ItemViewAutoFix.fix(state);
        assertEquals(1, missingViewItem.get().getViews(result.getAllocated()).count());
        assertEquals(1, extraViewItem.get().getViews(result.getAllocated()).count());
    }

}
