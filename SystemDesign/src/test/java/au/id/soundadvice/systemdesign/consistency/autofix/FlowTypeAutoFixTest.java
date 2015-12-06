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

import au.id.soundadvice.systemdesign.beans.Direction;
import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.model.Baseline.BaselineAnd;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.FlowType;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.model.RelationPair;
import au.id.soundadvice.systemdesign.model.UndoState;
import java.util.Optional;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FlowTypeAutoFixTest {

    @Test
    public void testRemoveUnused() {
        System.out.println("Build baselines with unused types");
        UndoState state = UndoState.createNew();
        state = state.setFunctional(FlowType.add(
                state.getFunctional(), Optional.empty(), "Functional Type"
        ).getBaseline());
        state = state.setAllocated(FlowType.add(
                state.getAllocated(), Optional.empty(), "Allocated Type"
        ).getBaseline());
        assertEquals("Functional Type", FlowType.find(
                state.getFunctional(), "Functional Type").get().getName());
        assertEquals("Allocated Type", FlowType.find(
                state.getAllocated(), "Allocated Type").get().getName());

        System.out.println("Unused Types are deleted during fix");
        state = FlowTypeAutoFix.fix(state);
        assertEquals(Optional.empty(), FlowType.find(
                state.getFunctional(), "Functional Type"));
        assertEquals(Optional.empty(), FlowType.find(
                state.getAllocated(), "Allocated Type"));
    }

    private static BaselineAnd<Flow> addFlowForType(Baseline baseline, FlowType type) {
        BaselineAnd<Item> baselineAndItem = Item.create(baseline, "Item", Point2D.ZERO, Color.CORAL);
        baseline = baselineAndItem.getBaseline();
        Item item = baselineAndItem.getRelation();

        BaselineAnd<Function> baselineAndFunction;
        baselineAndFunction = Function.create(baseline, item);
        baseline = baselineAndFunction.getBaseline();
        Function left = baselineAndFunction.getRelation();
        baselineAndFunction = Function.create(baseline, item);
        baseline = baselineAndFunction.getBaseline();
        Function right = baselineAndFunction.getRelation();

        RelationPair<Function> flowScope = new RelationPair<>(left, right, Direction.Normal);
        BaselineAnd<Flow> baselineAndFlow = Flow.add(baseline, flowScope, type);
        return baselineAndFlow;
    }

    @Test
    public void testCompressDuplicates() {
        System.out.println("Build baselines with duplicate type entries");
        UndoState state = UndoState.createNew();
        for (int ii = 0; ii < 3; ++ii) {
            BaselineAnd<FlowType> stateAndType;
            stateAndType = FlowType.addUnchecked(
                    state.getFunctional(), Optional.empty(), "Functional Type");
            state = state.setFunctional(stateAndType.getBaseline());
            state = state.setFunctional(addFlowForType(
                    state.getFunctional(), stateAndType.getRelation()).getBaseline());

            stateAndType = FlowType.addUnchecked(
                    state.getAllocated(), Optional.empty(), "Allocated Type");
            state = state.setAllocated(stateAndType.getBaseline());
            state = state.setAllocated(addFlowForType(
                    state.getAllocated(), stateAndType.getRelation()).getBaseline());
        }
        final Baseline notfixedFunctional = state.getFunctional();
        final Baseline notfixedAllocated = state.getAllocated();
        assertEquals(3, Flow.find(notfixedFunctional)
                .filter(flow -> flow.getType(notfixedFunctional).getName().equals("Functional Type"))
                .map(flow -> flow.getType(notfixedFunctional).getUuid())
                .distinct().count());
        assertEquals(3, Flow.find(notfixedAllocated)
                .filter(flow -> flow.getType(notfixedAllocated).getName().equals("Allocated Type"))
                .map(flow -> flow.getType(notfixedAllocated).getUuid())
                .distinct().count());

        System.out.println("Fix removes duplicates");
        state = FlowTypeAutoFix.fix(state);
        final Baseline fixedFunctional = state.getFunctional();
        final Baseline fixedAllocated = state.getAllocated();
        assertEquals(1, FlowType.find(state.getFunctional())
                .filter(flowType -> flowType.getName().equals("Functional Type"))
                .count());
        assertEquals(1, FlowType.find(state.getAllocated())
                .filter(flowType -> flowType.getName().equals("Allocated Type"))
                .count());

        System.out.println("Created flows still exist and now all point to the same FlowType instance");
        assertEquals(1, Flow.find(fixedFunctional)
                .filter(flow -> flow.getType(fixedFunctional).getName().equals("Functional Type"))
                .map(flow -> flow.getType(fixedFunctional).getUuid())
                .distinct().count());
        assertEquals(1, Flow.find(fixedAllocated)
                .filter(flow -> flow.getType(fixedAllocated).getName().equals("Allocated Type"))
                .map(flow -> flow.getType(fixedAllocated).getUuid())
                .distinct().count());
    }

    @Test
    public void testInvalidTraces() {
        System.out.println("Build baselines with good trace");
        UndoState state = UndoState.createNew();
        BaselineAnd<FlowType> stateAndType;
        stateAndType = FlowType.addUnchecked(
                state.getFunctional(), Optional.empty(), "Functional Type");
        FlowType functionalType = stateAndType.getRelation();
        state = state.setFunctional(stateAndType.getBaseline());
        state = state.setFunctional(addFlowForType(
                state.getFunctional(), stateAndType.getRelation()).getBaseline());

        stateAndType = FlowType.addUnchecked(
                state.getAllocated(), Optional.of(functionalType), "Allocated Type");
        FlowType allocatedType = stateAndType.getRelation();
        state = state.setAllocated(stateAndType.getBaseline());
        state = state.setAllocated(addFlowForType(
                state.getAllocated(), stateAndType.getRelation()).getBaseline());
        assertTrue(allocatedType.isTraced());
        assertEquals(Optional.of(functionalType), allocatedType.getTrace(state.getFunctional()));

        System.out.println("Break the trace");
        state = state.setFunctional(
                functionalType.removeFrom(state.getFunctional()));
        assertTrue(allocatedType.isTraced());
        assertEquals(Optional.empty(), allocatedType.getTrace(state.getFunctional()));

        System.out.println("Fixing now removes the broken trace");
        state = FlowTypeAutoFix.fix(state);
        allocatedType = state.getAllocated().get(allocatedType).get();
        assertFalse(allocatedType.isTraced());
        assertEquals(Optional.empty(), allocatedType.getTrace(state.getFunctional()));
    }

    @Test
    public void testUntraced() {
        System.out.println("Build baselines with same type in both, but no trace");
        UndoState state = UndoState.createNew();
        BaselineAnd<FlowType> stateAndType;
        stateAndType = FlowType.addUnchecked(
                state.getFunctional(), Optional.empty(), "Common Type");
        FlowType functionalType = stateAndType.getRelation();
        state = state.setFunctional(stateAndType.getBaseline());
        state = state.setFunctional(addFlowForType(
                state.getFunctional(), stateAndType.getRelation()).getBaseline());

        stateAndType = FlowType.addUnchecked(
                state.getAllocated(), Optional.empty(), "Common Type");
        FlowType allocatedType = stateAndType.getRelation();
        state = state.setAllocated(stateAndType.getBaseline());
        state = state.setAllocated(addFlowForType(
                state.getAllocated(), stateAndType.getRelation()).getBaseline());
        assertFalse(allocatedType.isTraced());
        assertEquals(Optional.empty(), allocatedType.getTrace(state.getFunctional()));

        System.out.println("Fixing now creates the trace");
        state = FlowTypeAutoFix.fix(state);
        allocatedType = state.getAllocated().get(allocatedType).get();
        assertTrue(allocatedType.isTraced());
        assertEquals(Optional.of(functionalType), allocatedType.getTrace(state.getFunctional()));
    }

}
