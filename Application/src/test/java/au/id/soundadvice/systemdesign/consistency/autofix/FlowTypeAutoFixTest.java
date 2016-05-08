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
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.BaselinePair;
import au.id.soundadvice.systemdesign.moduleapi.relation.Baseline;
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
        BaselinePair state = Baseline.createUndoState();
        state = state.setParent(FlowType.add(
                state.getParent(), Optional.empty(), "Functional Type"
        ).getKey());
        state = state.setChild(FlowType.add(
                state.getChild(), Optional.empty(), "Allocated Type"
        ).getKey());
        assertEquals("Functional Type", FlowType.get(
                state.getParent(), "Functional Type").get().getName());
        assertEquals("Allocated Type", FlowType.get(
                state.getChild(), "Allocated Type").get().getName());

        System.out.println("Unused Types are deleted during fix");
        state = FlowTypeAutoFix.fix(state);
        assertEquals(Optional.empty(), FlowType.get(
                state.getParent(), "Functional Type"));
        assertEquals(Optional.empty(), FlowType.get(
                state.getChild(), "Allocated Type"));
    }

    private static Pair<Baseline, Flow> addFlowForType(Baseline baseline, FlowType type) {
        Pair<Baseline, Item> baselineAndItem = Item.create(baseline, "Item", Point2D.ZERO, Color.CORAL);
        baseline = baselineAndItem.getKey();
        Item item = baselineAndItem.getValue();

        Pair<Baseline, Function> baselineAndFunction;
        baselineAndFunction = Function.create(
                baseline, item, Optional.empty(), "Left", FunctionView.DEFAULT_ORIGIN);
        baseline = baselineAndFunction.getKey();
        Function left = baselineAndFunction.getValue();
        baselineAndFunction = Function.create(
                baseline, item, Optional.empty(), "Right", FunctionView.DEFAULT_ORIGIN);
        baseline = baselineAndFunction.getKey();
        Function right = baselineAndFunction.getValue();

        RecordConnectionScope<Function> flowScope = new RecordConnectionScope<>(left, right, Direction.Forward);
        Pair<Baseline, Flow> baselineAndFlow = Flow.add(baseline, flowScope, type);
        return baselineAndFlow;
    }

    @Test
    public void testCompressDuplicates() {
        System.out.println("Build baselines with duplicate type entries");
        BaselinePair state = Baseline.createUndoState();
        for (int ii = 0; ii < 3; ++ii) {
            Pair<Baseline, FlowType> stateAndType;
            stateAndType = FlowType.addUnchecked(
                    state.getParent(), Optional.empty(), "Functional Type");
            state = state.setParent(stateAndType.getKey());
            state = state.setParent(addFlowForType(
                    state.getParent(), stateAndType.getValue()).getKey());

            stateAndType = FlowType.addUnchecked(
                    state.getChild(), Optional.empty(), "Allocated Type");
            state = state.setChild(stateAndType.getKey());
            state = state.setChild(addFlowForType(
                    state.getChild(), stateAndType.getValue()).getKey());
        }
        final Baseline notfixedFunctional = state.getParent();
        final Baseline notfixedAllocated = state.getChild();
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
        final Baseline fixedFunctional = state.getParent();
        final Baseline fixedAllocated = state.getChild();
        assertEquals(1, FlowType.find(state.getParent())
                .filter(flowType -> flowType.getName().equals("Functional Type"))
                .count());
        assertEquals(1, FlowType.find(state.getChild())
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
        BaselinePair state = Baseline.createUndoState();
        Pair<Baseline, FlowType> stateAndType;
        stateAndType = FlowType.addUnchecked(
                state.getParent(), Optional.empty(), "Functional Type");
        FlowType functionalType = stateAndType.getValue();
        state = state.setParent(stateAndType.getKey());
        state = state.setParent(addFlowForType(
                state.getParent(), stateAndType.getValue()).getKey());

        stateAndType = FlowType.addUnchecked(
                state.getChild(), Optional.of(functionalType), "Allocated Type");
        FlowType allocatedType = stateAndType.getValue();
        state = state.setChild(stateAndType.getKey());
        state = state.setChild(addFlowForType(
                state.getChild(), stateAndType.getValue()).getKey());
        assertTrue(allocatedType.isTraced());
        assertEquals(Optional.of(functionalType), allocatedType.getTrace(state.getParent()));

        System.out.println("Break the trace");
        state = state.setParent(
                functionalType.removeFrom(state.getParent()));
        assertTrue(allocatedType.isTraced());
        assertEquals(Optional.empty(), allocatedType.getTrace(state.getParent()));

        System.out.println("Fixing now removes the broken trace");
        state = FlowTypeAutoFix.fix(state);
        allocatedType = state.getChild().get(allocatedType).get();
        assertFalse(allocatedType.isTraced());
        assertEquals(Optional.empty(), allocatedType.getTrace(state.getParent()));
    }

    @Test
    public void testUntraced() {
        System.out.println("Build baselines with same type in both, but no trace");
        BaselinePair state = Baseline.createUndoState();
        Pair<Baseline, FlowType> stateAndType;
        stateAndType = FlowType.addUnchecked(
                state.getParent(), Optional.empty(), "Common Type");
        FlowType functionalType = stateAndType.getValue();
        state = state.setParent(stateAndType.getKey());
        state = state.setParent(addFlowForType(
                state.getParent(), stateAndType.getValue()).getKey());

        stateAndType = FlowType.addUnchecked(
                state.getChild(), Optional.empty(), "Common Type");
        FlowType allocatedType = stateAndType.getValue();
        state = state.setChild(stateAndType.getKey());
        state = state.setChild(addFlowForType(
                state.getChild(), stateAndType.getValue()).getKey());
        assertFalse(allocatedType.isTraced());
        assertEquals(Optional.empty(), allocatedType.getTrace(state.getParent()));

        System.out.println("Fixing now creates the trace");
        state = FlowTypeAutoFix.fix(state);
        allocatedType = state.getChild().get(allocatedType).get();
        assertTrue(allocatedType.isTraced());
        assertEquals(Optional.of(functionalType), allocatedType.getTrace(state.getParent()));
    }

}
