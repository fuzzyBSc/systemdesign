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

import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.model.UndoState;
import au.id.soundadvice.systemdesign.consistency.DisabledSolution;
import au.id.soundadvice.systemdesign.consistency.Problem;
import au.id.soundadvice.systemdesign.consistency.UpdateSolution;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FunctionConsistency {

    public static Predicate<? super Function> hasFlowsOnInterface(
            Baseline baseline, Interface iface) {
        return function -> {
            return function.getFlows(baseline)
                    .anyMatch(flow -> {
                        return iface.getUuid().equals(flow.getInterface().getUuid());
                    });
        };
    }

    private static Stream<Flow> getFlowsOnInterface(
            Baseline baseline, Function forFunction, Interface forInterface) {
        return forFunction.getFlows(baseline)
                .filter(flow -> forInterface.getUuid().equals(
                                flow.getInterface().getUuid()));
    }

    public static UndoState flowDown(UndoState state, Function parentFunction) {
        Optional<Item> system = state.getSystemOfInterest();
        if (!system.isPresent()) {
            return state;
        }
        Optional<Function> current = state.getFunctional().get(parentFunction);
        if (current.isPresent()) {
            return Function.flowDownExternal(state, current.get()).getState();
        } else {
            return state;
        }
    }

    /**
     * Figure out whether external functions are flowed down correctly.
     *
     * @param state The undo state to work from
     * @param iface The interface to analyse
     * @return The problems and solutions identified
     */
    public static Stream<Problem> checkConsistencyDown(UndoState state, Interface iface) {
        Optional<Item> system = state.getSystemOfInterest();
        if (!system.isPresent()) {
            return Stream.empty();
        }
        Baseline problemFunctional = state.getFunctional();
        Baseline problemAllocated = state.getAllocated();

        Item externalItem = iface.otherEnd(problemFunctional, system.get());

        return externalItem.getOwnedFunctions(problemFunctional)
                .filter(hasFlowsOnInterface(problemFunctional, iface))
                .flatMap(parentFunction -> {
                    Optional<Function> childFunction = problemAllocated.get(parentFunction);
                    if (childFunction.isPresent()) {
                        Stream<Problem> consistency;
                        if (!childFunction.get().isConsistent(parentFunction)) {
                            String name = parentFunction.getName();
                            consistency = Stream.of(
                                    new Problem(
                                            name + " differs between baselines", Stream.of(
                                                    UpdateSolution.updateAllocated(
                                                            "Flow down",
                                                            allocated -> childFunction.get().makeConsistent(
                                                                    allocated, parentFunction)
                                                            .getBaseline()),
                                                    UpdateSolution.updateFunctional(
                                                            "Flow up",
                                                            functional -> parentFunction.makeConsistent(
                                                                    functional, childFunction.get())
                                                            .getBaseline()))));
                        } else {
                            consistency = Stream.empty();
                        }
                        Stream<Problem> flows = FlowConsistency.checkConsistency(
                                state, iface, parentFunction);
                        return Stream.concat(consistency, flows);
                    } else {
                        String name = parentFunction.getName();
                        String parentId = system.get().getDisplayName();
                        return Stream.of(new Problem(
                                        name + " is missing in\n" + parentId, Stream.of(
                                                UpdateSolution.update("Flow down",
                                                        solutionState -> flowDown(solutionState, parentFunction)),
                                                UpdateSolution.update("Flow up", solutionState -> {
                                                    Optional<Item> systemOfInterest = state.getSystemOfInterest();
                                                    if (!systemOfInterest.isPresent()) {
                                                        return solutionState;
                                                    }
                                                    Baseline solutionFunctional = solutionState.getFunctional();
                                                    Stream<Flow> toDelete = getFlowsOnInterface(
                                                            solutionFunctional, parentFunction, iface);
                                                    Iterator<Flow> it = toDelete.iterator();
                                                    while (it.hasNext()) {
                                                        Flow flow = it.next();
                                                        solutionFunctional = flow.removeFrom(solutionFunctional);
                                                    }
                                                    return solutionState.setFunctional(solutionFunctional);
                                                }))));
                    }
                });
    }

    /**
     * Figure out whether external functions in allocated baseline exist in
     * functional baseline.
     *
     * @param state The undo state to work from
     * @param functionalInterface The parent interface that connects the system
     * of interest to the externalItem
     * @param allocatedExternalItem The external item to analyse in the
     * allocated baseline
     * @return The problems and solutions identified
     */
    public static Stream<Problem> checkConsistencyUp(
            UndoState state, Interface functionalInterface, Item allocatedExternalItem) {
        Optional<Item> system = state.getSystemOfInterest();
        if (!system.isPresent()) {
            return Stream.empty();
        }
        Baseline problemFunctional = state.getFunctional();
        Baseline problemAllocated = state.getAllocated();

        return allocatedExternalItem.getOwnedFunctions(problemAllocated)
                .filter(allocatedExternalFunction -> {
                    return allocatedExternalFunction.isExternal()
                    && !Function.getSystemFunctionsForExternalFunction(
                            state, allocatedExternalFunction).findAny().isPresent();
                })
                .flatMap(allocatedExternalFunction -> {
                    String name = allocatedExternalFunction.getName();
                    if (problemFunctional.get(allocatedExternalFunction).isPresent()) {
                        /*
                         * The parent instance exists, just no flows. Hand off
                         * to FlowConsistency.
                         */
                        return FlowConsistency.checkConsistency(
                                state, functionalInterface, allocatedExternalFunction);
                    } else {
                        /*
                         * The parent instance of the external function doesn't
                         * exist at all
                         */
                        String parentId = system.get().getDisplayName();
                        return Stream.of(new Problem(
                                        name + " is missing in\n" + parentId, Stream.of(
                                                UpdateSolution.updateAllocated("Flow down", solutionAllocated
                                                        -> allocatedExternalFunction.removeFrom(solutionAllocated)
                                                ),
                                                DisabledSolution.FlowUp)));
                    }

                });
    }
}
