/*
 * To change this license header, choose License Headers in Project Properties.
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
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.model.ItemView;
import au.id.soundadvice.systemdesign.model.UndoState;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class ExternalColorAutoFixTest {

    private static UndoState.StateAnd<Item> getStateWithExternalItem() {
        System.out.println("Create baseline pair with external item flowed down");
        UndoState state = UndoState.createNew();
        Baseline functional = state.getFunctional();
        Baseline allocated = state.getAllocated();
        Item system;
        {
            Baseline.BaselineAnd<Item> result = Item.create(
                    functional, "System of Interest", Point2D.ZERO, Color.CORAL);
            functional = result.getBaseline();
            system = result.getRelation();
        }
        allocated = allocated.setIdentity(system.asIdentity(functional));
        Item functionalExternal;
        {
            Baseline.BaselineAnd<Item> result = Item.create(
                    functional, "External", Point2D.ZERO, Color.BEIGE);
            functional = result.getBaseline();
            functionalExternal = result.getRelation();
        }
        state = state
                .setFunctional(functional)
                .setAllocated(allocated);
        return Item.flowDownExternal(state, functionalExternal);
    }

    /**
     * Test of fix method, of class ExternalColorAutoFix.
     */
    @Test
    public void testFix() {
        UndoState state;
        Item allocatedExternal;
        {
            UndoState.StateAnd<Item> result = getStateWithExternalItem();
            state = result.getState();
            allocatedExternal = result.getRelation();
        }

        System.out.println("Allocated item view has color " + Color.BEIGE);
        assertEquals(Color.BEIGE, allocatedExternal.getView(state.getAllocated()).getColor());

        System.out.println("Set allocated item view color to " + Color.AZURE);
        {
            ItemView view = allocatedExternal.getView(state.getAllocated());
            state = state.setAllocated(
                    view.setColor(state.getAllocated(), Color.AZURE).getBaseline());
        }
        assertEquals(Color.AZURE, allocatedExternal.getView(state.getAllocated()).getColor());

        System.out.println("Apply fix");
        state = ExternalColorAutoFix.fix(state);
        assertEquals(Color.BEIGE, allocatedExternal.getView(state.getAllocated()).getColor());
    }

}