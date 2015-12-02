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
package au.id.soundadvice.systemdesign.consistency.suggestions;

import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.model.Baseline.BaselineAnd;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.model.UndoState;
import java.util.Optional;
import java.util.concurrent.Executors;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class UntracedFunctionsTest {

    /**
     * Test of getProblems method, of class UntracedFunctions.
     */
    @Test
    public void testGetProblems() {
        System.out.println("getProblems");
        EditState edit = EditState.init(Executors.newCachedThreadPool());
        edit.updateState(state -> {
            Baseline functional = state.getFunctional();
            Baseline allocated = state.getAllocated();
            BaselineAnd<Item> itemTuple = Item.create(
                    functional, Point2D.ZERO, Color.LIGHTYELLOW);
            functional = itemTuple.getBaseline();
            allocated = allocated.setIdentity(itemTuple.getRelation().asIdentity(functional));

            System.out.println("Create an item with unallocated function");
            itemTuple = Item.create(allocated, Point2D.ZERO, Color.LIGHTYELLOW);
            allocated = itemTuple.getBaseline();
            BaselineAnd<Function> functionTuple = Function.create(allocated, itemTuple.getRelation(), Optional.empty(), "Function", Point2D.ZERO);
            allocated = functionTuple.getBaseline();

            return new UndoState(functional, allocated);
        });
        UntracedFunctions instance = new UntracedFunctions();
        assertEquals(1, instance.getProblems(edit).count());
    }

}
