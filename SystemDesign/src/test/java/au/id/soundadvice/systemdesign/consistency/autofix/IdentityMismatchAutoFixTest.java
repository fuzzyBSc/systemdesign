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
import au.id.soundadvice.systemdesign.model.IDPath;
import au.id.soundadvice.systemdesign.model.Identity;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.model.UndoState;
import java.util.concurrent.atomic.AtomicReference;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class IdentityMismatchAutoFixTest {

    /**
     * Test of fix method, of class IdentityMismatchAutoFix.
     */
    @Test
    public void testFix() {
        System.out.println("fix");

        UndoState state = UndoState.createNew();
        AtomicReference<Item> systemOfInterest = new AtomicReference<>();
        {
            Baseline functional = state.getFunctional();
            Baseline allocated = state.getAllocated();
            Baseline.BaselineAnd<Item> pair = Item.create(
                    functional, "New Item", Point2D.ZERO, Color.LIGHTYELLOW);
            functional = pair.getBaseline();
            Item item = pair.getRelation();
            systemOfInterest.set(item);
            // Create a mismatched identity
            allocated = allocated.setIdentity(
                    item.asIdentity(functional).setId(
                            IDPath.valueOfDotted("wrong.id")));

            state = new UndoState(functional, allocated);
            assertEquals("wrong.id", Identity.find(state.getAllocated()).getIdPath().toString());
        }
        UndoState result = IdentityMismatchAutoFix.fix(state);
        assertEquals("1", Identity.find(result.getAllocated()).getIdPath().toString());
    }

}
