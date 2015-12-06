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

import au.id.soundadvice.systemdesign.model.UndoState;
import au.id.soundadvice.systemdesign.consistency.DisabledSolution;
import au.id.soundadvice.systemdesign.consistency.Problem;
import au.id.soundadvice.systemdesign.consistency.SolutionFlow;
import au.id.soundadvice.systemdesign.consistency.UpdateSolution;
import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class InterfaceConsistency {

    private static UndoState flowExternalItemDown(UndoState state, Interface iface) {
        Optional<Item> system = state.getSystemOfInterest();
        if (!system.isPresent()) {
            return state;
        }
        Baseline functional = state.getFunctional();
        Optional<Interface> current = functional.get(iface);
        if (current.isPresent()) {
            iface = current.get();
        } else {
            return state;
        }
        Item parentItem = iface.otherEnd(functional, system.get());

        state = Item.flowDownExternal(state, parentItem).getState();

        Iterator<Function> functionsWithFlowsOnThisInterface
                = parentItem.findOwnedFunctions(functional)
                .filter(ExternalFunctionConsistency.hasFlowsOnInterface(functional, iface))
                .iterator();
        while (functionsWithFlowsOnThisInterface.hasNext()) {
            Function function = functionsWithFlowsOnThisInterface.next();
            state = ExternalFunctionConsistency.flowDownExternalFunction(state, function);
        }

        return state;
    }

    public static Stream<Problem> getProblems(UndoState state) {
        return Stream.concat(
                checkConsistencyDown(state),
                checkConsistencyUp(state));
    }

    /**
     * Figure out whether external items are flowed down correctly.
     *
     * @param state The undo buffer state to work from
     * @return The list of identified problems and solutions
     */
    public static Stream<Problem> checkConsistencyDown(UndoState state) {
        Optional<Item> system = state.getSystemOfInterest();
        if (!system.isPresent()) {
            return Stream.empty();
        }
        Baseline problemFunctional = state.getFunctional();
        Baseline problemAllocated = state.getAllocated();

        return system.get().findInterfaces(problemFunctional)
                .flatMap(iface -> {
                    Item functionalExternalItem = iface.otherEnd(problemFunctional, system.get());
                    if (functionalExternalItem.equals(system.get())) {
                        // Ignore interfaces to self
                        return Stream.empty();
                    }
                    Optional<Item> allocatedExternalItem = problemAllocated.get(functionalExternalItem);
                    if (allocatedExternalItem.isPresent()) {
                        /**
                         * The external item exists in both baselines
                         */
                        Item parentItemAsExternal = functionalExternalItem.asExternal(problemFunctional);
                        if (!allocatedExternalItem.get().isConsistent(parentItemAsExternal)) {
                            String name = parentItemAsExternal.getDisplayName();
                            return Stream.of(
                                    new Problem(
                                            name + " differs between baselines", Stream.of(
                                                    UpdateSolution.updateAllocated(
                                                            baseline -> allocatedExternalItem.get().makeConsistent(baseline, parentItemAsExternal).getBaseline()),
                                                    UpdateSolution.updateFunctional(
                                                            baseline -> functionalExternalItem.makeConsistent(baseline, allocatedExternalItem.get()).getBaseline()))));
                        } else {
                            return Stream.empty();
                        }
                    } else {
                        /*
                         * An interface exists in the functional baseline that
                         * does not exist in the allocated baseline
                         */
                        String externalName = functionalExternalItem.getDisplayName();
                        String systemName = system.get().getDisplayName();
                        return Stream.of(new Problem(
                                externalName + " is missing in\n" + systemName, Stream.of(
                                        UpdateSolution.update(
                                                SolutionFlow.Down,
                                                solutionState
                                                -> flowExternalItemDown(solutionState, iface)),
                                        UpdateSolution.updateFunctional(
                                                solutionFunctional
                                                -> iface.removeFrom(solutionFunctional)
                                        ))));
                    }
                });
    }

    /**
     * Figure out whether external items in the allocated baseline exist as
     * interfaces in the functional baseline.
     *
     * @param state The undo buffer state to work from
     * @return The list of identified problems and solutions
     */
    public static Stream<Problem> checkConsistencyUp(UndoState state) {
        Optional<Item> system = state.getSystemOfInterest();
        if (!system.isPresent()) {
            return Stream.empty();
        }
        Baseline problemFunctional = state.getFunctional();
        Baseline problemAllocated = state.getAllocated();

        return Item.find(problemAllocated)
                .filter(Item::isExternal)
                .flatMap(externalAllocatedItem -> {
                    Optional<Interface> functionalInterface
                            = system.get().findInterfaces(problemFunctional)
                            .filter(iface -> externalAllocatedItem.getUuid().equals(
                                    iface.otherEnd(problemFunctional, system.get()).getUuid()))
                            .findAny();

                    if (functionalInterface.isPresent()) {
                        /*
                         * An interface corresponding to this external item
                         * exists in the functional baseline. All good.
                         */
                        return Stream.empty();
                    }

                    /*
                     * No interface exists in the functional baseline
                     * corresponding to the external item. So is it just the
                     * interface missing, or the item as well?
                     */
                    String name = externalAllocatedItem.getDisplayName();
                    String parentId = system.get().getDisplayName();
                    if (problemFunctional.get(externalAllocatedItem).isPresent()) {
                        /*
                         * The external item exists in the functional baseline,
                         * just not the relevant interface.
                         */
                        return Stream.of(new Problem(
                                name + " is missing in\n" + parentId, Stream.of(
                                        UpdateSolution.updateAllocated(
                                                solutionAllocated
                                                -> externalAllocatedItem.removeFrom(solutionAllocated)
                                        ),
                                        UpdateSolution.updateFunctional(
                                                solutionFunctional -> {
                                                    return Interface.create(
                                                            solutionFunctional, externalAllocatedItem, system.get())
                                                    .getBaseline();
                                                }))));
                    } else {
                        /*
                         * The external item doesn't exist at all in the
                         * functional baseline.
                         */
                        return Stream.of(new Problem(
                                name + " is missing in " + parentId, Stream.of(
                                        UpdateSolution.updateAllocated(
                                                solutionAllocated
                                                -> externalAllocatedItem.removeFrom(solutionAllocated)
                                        ),
                                        DisabledSolution.FlowUp)));
                    }
                });
    }
}
