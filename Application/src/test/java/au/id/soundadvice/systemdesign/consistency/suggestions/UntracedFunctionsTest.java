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

import au.id.soundadvice.systemdesign.logical.suggest.UntracedFunctions;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import au.id.soundadvice.systemdesign.logical.Function;
import au.id.soundadvice.systemdesign.physical.Item;
import au.id.soundadvice.systemdesign.moduleapi.UndoState;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.physical.Identity;
import au.id.soundadvice.systemdesign.state.Baseline;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.util.Pair;
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
        UndoState state = Baseline.createUndoState();

        Relations functional = state.getFunctional();
        Relations allocated = state.getAllocated();
        Pair<Relations, Item> itemTuple = Item.create(
                functional, "New Item", Point2D.ZERO, Color.LIGHTYELLOW);
        functional = itemTuple.getKey();
        allocated = Identity.setIdentity(allocated, itemTuple.getValue().asIdentity(functional));

        System.out.println("Create an item with unallocated function");
        itemTuple = Item.create(allocated, "New Item 2", Point2D.ZERO, Color.LIGHTYELLOW);
        allocated = itemTuple.getKey();
        Pair<Relations, Function> functionTuple = Function.create(allocated, itemTuple.getValue(), Optional.empty(), "Function", Point2D.ZERO);
        allocated = functionTuple.getKey();

        state = state.setFunctional(functional).setAllocated(allocated);
        Stream<Problem> problems = UntracedFunctions.getProblems(state);
        assertEquals(1, problems.count());
    }

}