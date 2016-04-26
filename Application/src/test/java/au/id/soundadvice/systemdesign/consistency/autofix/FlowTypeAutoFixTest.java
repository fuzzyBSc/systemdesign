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

import au.id.soundadvice.systemdesign.logical.fix.FlowTypeAutoFix;
import au.id.soundadvice.systemdesign.moduleapi.Direction;
import au.id.soundadvice.systemdesign.state.Baseline;
import au.id.soundadvice.systemdesign.logical.Flow;
import au.id.soundadvice.systemdesign.logical.FlowType;
import au.id.soundadvice.systemdesign.logical.Function;
import au.id.soundadvice.systemdesign.logical.FunctionView;
import au.id.soundadvice.systemdesign.physical.Item;
import au.id.soundadvice.systemdesign.moduleapi.RelationPair;
import au.id.soundadvice.systemdesign.moduleapi.UndoState;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import java.util.Optional;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.util.Pair;
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
        UndoState state = Baseline.createUndoState();
        state = state.setFunctional(FlowType.add(
                state.getFunctional(), Optional.empty(), "Functional Type"
        ).getKey());
        state = state.setAllocated(FlowType.add(
                state.getAllocated(), Optional.empty(), "Allocated Type"
        ).getKey());
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

    private static Pair<Relations, Flow> addFlowForType(Relations baseline, FlowType type) {
        Pair<Relations, Item> baselineAndItem = Item.create(baseline, "Item", Point2D.ZERO, Color.CORAL);
        baseline = baselineAndItem.getKey();
        Item item = baselineAndItem.getValue();

        Pair<Relations, Function> baselineAndFunction;
        baselineAndFunction = Function.create(
                baseline, item, Optional.empty(), "Left", FunctionView.DEFAULT_ORIGIN);
        baseline = baselineAndFunction.getKey();
        Function left = baselineAndFunction.getValue();
        baselineAndFunction = Function.create(
                baseline, item, Optional.empty(), "Right", FunctionView.DEFAULT_ORIGIN);
        baseline = baselineAndFunction.getKey();
        Function right = baselineAndFunction.getValue();

        RelationPair<Function> flowScope = new RelationPair<>(left, right, Direction.Normal);
        Pair<Relations, Flow> baselineAndFlow = Flow.add(baseline, flowScope, type);
        return baselineAndFlow;
    }

    @Test
    public void testCompressDuplicates() {
        System.out.println("Build baselines with duplicate type entries");
        UndoState state = Baseline.createUndoState();
        for (int ii = 0; ii < 3; ++ii) {
            Pair<Relations, FlowType> stateAndType;
            stateAndType = FlowType.addUnchecked(
                    state.getFunctional(), Optional.empty(), "Functional Type");
            state = state.setFunctional(stateAndType.getKey());
            state = state.setFunctional(addFlowForType(
                    state.getFunctional(), stateAndType.getValue()).getKey());

            stateAndType = FlowType.addUnchecked(
                    state.getAllocated(), Optional.empty(), "Allocated Type");
            state = state.setAllocated(stateAndType.getKey());
            state = state.setAllocated(addFlowForType(
                    state.getAllocated(), stateAndType.getValue()).getKey());
        }
        final Relations notfixedFunctional = state.getFunctional();
        final Relations notfixedAllocated = state.getAllocated();
        assertEquals(3, Flow.find(notfixedFunctional)
                .filter(flow -> flow.getType(notfixedFunctional).getName().equals("Functional Type"))
                .map(flow -> flow.getType(notfixedFunctional).getIdentifier())
                .distinct().count());
        assertEquals(3, Flow.find(notfixedAllocated)
                .filter(flow -> flow.getType(notfixedAllocated).getName().equals("Allocated Type"))
                .map(flow -> flow.getType(notfixedAllocated).getIdentifier())
                .distinct().count());

        System.out.println("Fix removes duplicates");
        state = FlowTypeAutoFix.fix(state);
        final Relations fixedFunctional = state.getFunctional();
        final Relations fixedAllocated = state.getAllocated();
        assertEquals(1, FlowType.find(state.getFunctional())
                .filter(flowType -> flowType.getName().equals("Functional Type"))
                .count());
        assertEquals(1, FlowType.find(state.getAllocated())
                .filter(flowType -> flowType.getName().equals("Allocated Type"))
                .count());

        System.out.println("Created flows still exist and now all point to the same FlowType instance");
        assertEquals(1, Flow.find(fixedFunctional)
                .filter(flow -> flow.getType(fixedFunctional).getName().equals("Functional Type"))
                .map(flow -> flow.getType(fixedFunctional).getIdentifier())
                .distinct().count());
        assertEquals(1, Flow.find(fixedAllocated)
                .filter(flow -> flow.getType(fixedAllocated).getName().equals("Allocated Type"))
                .map(flow -> flow.getType(fixedAllocated).getIdentifier())
                .distinct().count());
    }

    @Test
    public void testInvalidTraces() {
        System.out.println("Build baselines with good trace");
        UndoState state = Baseline.createUndoState();
        Pair<Relations, FlowType> stateAndType;
        stateAndType = FlowType.addUnchecked(
                state.getFunctional(), Optional.empty(), "Functional Type");
        FlowType functionalType = stateAndType.getValue();
        state = state.setFunctional(stateAndType.getKey());
        state = state.setFunctional(addFlowForType(
                state.getFunctional(), stateAndType.getValue()).getKey());

        stateAndType = FlowType.addUnchecked(
                state.getAllocated(), Optional.of(functionalType), "Allocated Type");
        FlowType allocatedType = stateAndType.getValue();
        state = state.setAllocated(stateAndType.getKey());
        state = state.setAllocated(addFlowForType(
                state.getAllocated(), stateAndType.getValue()).getKey());
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
        UndoState state = Baseline.createUndoState();
        Pair<Relations, FlowType> stateAndType;
        stateAndType = FlowType.addUnchecked(
                state.getFunctional(), Optional.empty(), "Common Type");
        FlowType functionalType = stateAndType.getValue();
        state = state.setFunctional(stateAndType.getKey());
        state = state.setFunctional(addFlowForType(
                state.getFunctional(), stateAndType.getValue()).getKey());

        stateAndType = FlowType.addUnchecked(
                state.getAllocated(), Optional.empty(), "Common Type");
        FlowType allocatedType = stateAndType.getValue();
        state = state.setAllocated(stateAndType.getKey());
        state = state.setAllocated(addFlowForType(
                state.getAllocated(), stateAndType.getValue()).getKey());
        assertFalse(allocatedType.isTraced());
        assertEquals(Optional.empty(), allocatedType.getTrace(state.getFunctional()));

        System.out.println("Fixing now creates the trace");
        state = FlowTypeAutoFix.fix(state);
        allocatedType = state.getAllocated().get(allocatedType).get();
        assertTrue(allocatedType.isTraced());
        assertEquals(Optional.of(functionalType), allocatedType.getTrace(state.getFunctional()));
    }

}
