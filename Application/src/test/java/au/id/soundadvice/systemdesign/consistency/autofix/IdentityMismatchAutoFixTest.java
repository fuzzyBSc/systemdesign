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

import au.id.soundadvice.systemdesign.physical.fix.IdentityMismatchAutoFix;
import au.id.soundadvice.systemdesign.state.Baseline;
import au.id.soundadvice.systemdesign.physical.IDPath;
import au.id.soundadvice.systemdesign.physical.Identity;
import au.id.soundadvice.systemdesign.physical.Item;
import au.id.soundadvice.systemdesign.moduleapi.BaselinePair;
import au.id.soundadvice.systemdesign.moduleapi.relation.Baseline;
import java.util.concurrent.atomic.AtomicReference;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.util.Pair;
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

        BaselinePair state = Baseline.createUndoState();
        AtomicReference<Item> systemOfInterest = new AtomicReference<>();
        {
            Baseline functional = state.getParent();
            Baseline allocated = state.getChild();
            Pair<Baseline, Item> pair = Item.create(
                    functional, "New Item", Point2D.ZERO, Color.LIGHTYELLOW);
            functional = pair.getKey();
            Item item = pair.getValue();
            systemOfInterest.set(item);
            // Create a mismatched identity
            allocated = Identity.setIdentity(allocated,
                    item.asIdentity(functional).setId(
                    IDPath.valueOfDotted("wrong.id")));

            state = new BaselinePair(functional, allocated);
            assertEquals("wrong.id", Identity.get(state.getChild()).getIdPath().toString());
        }
        BaselinePair result = IdentityMismatchAutoFix.fix(state);
        assertEquals("1", Identity.get(result.getChild()).getIdPath().toString());
    }

}
