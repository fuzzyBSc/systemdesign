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

import au.id.soundadvice.systemdesign.baselines.EditState;
import au.id.soundadvice.systemdesign.model.UndoState;
import au.id.soundadvice.systemdesign.consistency.DisabledSolution;
import au.id.soundadvice.systemdesign.consistency.Problem;
import au.id.soundadvice.systemdesign.consistency.ProblemFactory;
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
public class ItemConsistency implements ProblemFactory {

    private static UndoState flowItemDown(UndoState state, Interface iface) {
        Optional<Item> system = state.getSystemOfInterest();
        if (!system.isPresent()) {
            return state;
        }
        Baseline functional = state.getFunctional();
        Baseline allocated = state.getAllocated();
        Optional<Interface> current = functional.get(iface);
        if (current.isPresent()) {
            iface = current.get();
        } else {
            return state;
        }
        Item parentItem = iface.otherEnd(functional, system.get());

        state = Item.flowDownExternal(state, parentItem).getState();

        Iterator<Function> functionsWithFlowsOnThisInterface
                = parentItem.getOwnedFunctions(functional)
                .filter(FunctionConsistency.hasFlowsOnInterface(functional, iface))
                .iterator();
        while (functionsWithFlowsOnThisInterface.hasNext()) {
            Function function = functionsWithFlowsOnThisInterface.next();
            state = FunctionConsistency.flowDown(state, function);
        }

        return state;
    }

    @Override
    public Stream<Problem> getProblems(EditState edit) {
        UndoState state = edit.getUndo().get();
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

        return system.get().getInterfaces(problemFunctional)
                .flatMap(iface -> {
                    Item parentItem = iface.otherEnd(problemFunctional, system.get());
                    Optional<Item> childItem = problemAllocated.get(parentItem);
                    if (childItem.isPresent()) {
                        Stream<Problem> consistency;
                        Item parentItemAsExternal = parentItem.asExternal(problemFunctional);
                        if (!childItem.get().isConsistent(parentItemAsExternal)) {
                            String name = parentItemAsExternal.getDisplayName();
                            consistency = Stream.of(
                                    new Problem(
                                            name + " differs between baselines", Stream.of(
                                                    UpdateSolution.updateAllocated(
                                                            "Flow down",
                                                            baseline -> childItem.get().makeConsistent(baseline, parentItemAsExternal).getBaseline()),
                                                    UpdateSolution.updateAllocated(
                                                            "Flow up",
                                                            baseline -> parentItem.makeConsistent(baseline, childItem.get()).getBaseline()))));
                        } else {
                            consistency = Stream.empty();
                        }
                        Stream<Problem> flows = FunctionConsistency.checkConsistencyDown(state, iface);
                        return Stream.concat(consistency, flows);
                    } else {
                        String name = parentItem.getDisplayName();
                        String parentId = system.get().getDisplayName();
                        return Stream.of(new Problem(
                                        name + " is missing in\n" + parentId, Stream.of(
                                                UpdateSolution.update("Flow down", solutionState
                                                        -> flowItemDown(solutionState, iface)),
                                                UpdateSolution.updateFunctional("Flow up", solutionFunctional
                                                        -> iface.remove(solutionFunctional)
                                                ))));
                    }
                });
    }

    /**
     * Figure out whether external items in the allocated baseline exist in the
     * functional baseline.
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

        return problemAllocated.getItems()
                .filter(Item::isExternal)
                .flatMap(externalAllocatedItem -> {
                    Optional<Interface> functionalInterface
                    = system.get().getInterfaces(problemFunctional)
                    .filter(iface -> externalAllocatedItem.getUuid().equals(
                                    iface.otherEnd(problemFunctional, system.get()).getUuid()))
                    .findAny();

                    if (functionalInterface.isPresent()) {
                        // This item is fine. Check its functions.
                        return FunctionConsistency.checkConsistencyUp(
                                state, functionalInterface.get(), externalAllocatedItem);
                    }

                    String name = externalAllocatedItem.getDisplayName();
                    String parentId = system.get().getDisplayName();
                    if (problemFunctional.get(externalAllocatedItem).isPresent()) {
                        /*
                         * The parent instance exists, just not the relevant
                         * interface.
                         */
                        return Stream.of(new Problem(
                                        name + " is missing in\n" + parentId, Stream.of(UpdateSolution.updateAllocated("Flow down", solutionAllocated
                                                        -> externalAllocatedItem.removeFrom(solutionAllocated)
                                                ),
                                                UpdateSolution.updateFunctional("Flow up", solutionFunctional -> {
                                                    return Interface.create(
                                                            solutionFunctional, externalAllocatedItem, system.get())
                                                    .getBaseline();
                                                }))));
                    } else {
                        /*
                         * The parent instance of the external item doesn't
                         * exist at all
                         */
                        return Stream.of(new Problem(
                                        name + " is missing in " + parentId, Stream.of(
                                                UpdateSolution.updateAllocated("Flow down", solutionAllocated
                                                        -> externalAllocatedItem.removeFrom(solutionAllocated)
                                                ),
                                                new DisabledSolution("Flow up"))));
                    }
                });
    }
}
