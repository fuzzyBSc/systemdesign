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

import au.id.soundadvice.systemdesign.baselines.AllocatedBaseline;
import au.id.soundadvice.systemdesign.baselines.FunctionalBaseline;
import au.id.soundadvice.systemdesign.baselines.UndoState;
import au.id.soundadvice.systemdesign.consistency.DisabledSolution;
import au.id.soundadvice.systemdesign.consistency.Problem;
import au.id.soundadvice.systemdesign.consistency.UpdateSolution;
import au.id.soundadvice.systemdesign.files.Identifiable;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.relation.RelationContext;
import au.id.soundadvice.systemdesign.relation.RelationStore;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FunctionConsistency {

    private static Stream<Flow> getFlows(
            RelationStore store, Interface iface) {
        return store.getReverse(iface.getUuid(), Flow.class);
    }

    public static Predicate<? super Function> hasFlowsOnInterface(
            RelationContext context, UUID interfaceUUID) {
        return function -> {
            return context.getReverse(function.getUuid(), Flow.class)
                    .anyMatch(flow -> {
                        return interfaceUUID.equals(flow.getInterface().getUuid());
                    });
        };
    }

    private static Stream<Flow> getFlowsOnInterface(
            RelationContext context, UUID functionUUID, UUID interfaceUUID) {
        return context.getReverse(functionUUID, Flow.class)
                .filter(flow -> {
                    return interfaceUUID.equals(flow.getInterface().getUuid());
                });
    }

    public static UndoState flowDown(UndoState state, UUID parentFunctionUUID) {
        Optional<FunctionalBaseline> functional = state.getFunctional();
        if (!functional.isPresent()) {
            return state;
        }
        RelationStore parentStore = functional.get().getStore();
        Item system = functional.get().getSystemOfInterest();
        Optional<Function> parentFunction = parentStore.get(parentFunctionUUID, Function.class);
        if (!parentFunction.isPresent()) {
            return state;
        }
        // PROBLEM - need to allocate to multiple system function designs
        Optional<Function> systemFunction = parentStore.getReverse(parentFunction.get().getUuid(), Flow.class)
                .map(flow -> flow.otherEnd(parentStore, parentFunction.get()))
                .filter(candidate -> system.getUuid().equals(candidate.getItem().getUuid()))
                .findAny();
        AllocatedBaseline allocated = state.getAllocated();
        if (systemFunction.isPresent()) {
            allocated = allocated.add(parentFunction.get().asExternal(systemFunction.get().getUuid()));
        }
        return state.setAllocated(allocated);
    }

    /**
     * Figure out whether external functions are flowed down correctly.
     *
     * @param state The undo state to work from
     * @param iface The interface to analyse
     * @return The problems and solutions identified
     */
    public static Stream<Problem> checkConsistencyDown(UndoState state, Interface iface) {
        Optional<FunctionalBaseline> functional = state.getFunctional();
        if (!functional.isPresent()) {
            return Stream.empty();
        }
        Item system = functional.get().getSystemOfInterest();
        RelationStore parentStore = functional.get().getStore();
        RelationStore childStore = state.getAllocated().getStore();

        Item externalItem = iface.otherEnd(parentStore, system);

        return parentStore.getReverse(externalItem.getUuid(), Function.class)
                .filter(hasFlowsOnInterface(parentStore, iface.getUuid()))
                .flatMap(parentFunction -> {
                    Optional<Function> childFunction = childStore.get(parentFunction.getUuid(), Function.class);
                    if (childFunction.isPresent()) {
                        Stream<Problem> consistency;
                        if (!childFunction.get().isConsistent(parentFunction)) {
                            String name = parentFunction.getName();
                            consistency = Stream.of(
                                    new Problem(
                                            name + " differs between baselines", Stream.of(
                                                    UpdateSolution.updateAllocatedRelation(
                                                            "Flow down", childFunction.get().getUuid(), Function.class,
                                                            relation -> relation.makeConsistent(parentFunction)),
                                                    UpdateSolution.updateFunctionalRelation(
                                                            "Flow up", childFunction.get().getUuid(), Function.class,
                                                            relation -> relation.makeConsistent(childFunction.get())))));
                        } else {
                            consistency = Stream.empty();
                        }
                        Stream<Problem> flows = FlowConsistency.checkConsistency(
                                state, iface.getUuid(), parentFunction.getUuid(),
                                parentFunction.getName());
                        return Stream.concat(consistency, flows);
                    } else {
                        String name = parentFunction.getName();
                        String parentId = functional.get().getSystemOfInterest().getIdPath(parentStore).toString();
                        return Stream.of(new Problem(
                                        name + " is missing in " + parentId, Stream.of(
                                                UpdateSolution.update("Flow down",
                                                        solutionState -> flowDown(solutionState, parentFunction.getUuid())),
                                                UpdateSolution.update("Flow up", solutionState -> {
                                                    Optional<FunctionalBaseline> solutionFunctional = solutionState.getFunctional();
                                                    if (!solutionFunctional.isPresent()) {
                                                        return solutionState;
                                                    }
                                                    RelationStore solutionStore = solutionFunctional.get().getStore();
                                                    Stream<UUID> toDelete = getFlowsOnInterface(
                                                            solutionStore, parentFunction.getUuid(), iface.getUuid())
                                                    .map(Identifiable::getUuid);
                                                    return solutionState.setFunctional(
                                                            solutionFunctional.get().removeAll(toDelete));
                                                }))));
                    }
                });
    }

    /**
     * Figure out whether external functions in allocated baseline exist in
     * functional baseline.
     *
     * @param state The undo state to work from
     * @param parentInterface The parent interface that connects the system of
     * interest to the externalItem
     * @param externalItemUUID The item to analyse
     * @return The problems and solutions identified
     */
    public static Stream<Problem> checkConsistencyUp(
            UndoState state, Interface parentInterface, UUID externalItemUUID) {
        Optional<FunctionalBaseline> functional = state.getFunctional();
        if (!functional.isPresent()) {
            return Stream.empty();
        }
        Item system = functional.get().getSystemOfInterest();
        RelationStore parentStore = functional.get().getStore();
        RelationStore childStore = state.getAllocated().getStore();

        return childStore.getReverse(externalItemUUID, Function.class)
                .filter(externalFunction -> {
                    return externalFunction.isExternal() && parentStore.getReverse(system.getUuid(), Function.class)
                    .flatMap(systemFunction -> parentStore.getReverse(systemFunction.getUuid(), Flow.class))
                    .noneMatch((flow) -> externalFunction.getUuid().equals(
                                    flow.otherEnd(parentStore, system).getUuid()));
                })
                .flatMap((function) -> {
                    String name = function.getName();
                    if (parentStore.get(function.getUuid(), Function.class).isPresent()) {
                        /*
                         * The parent instance exists, just no flows. Hand off
                         * to FlowConsistency.
                         */
                        return FlowConsistency.checkConsistency(
                                state, parentInterface.getUuid(),
                                function.getUuid(), function.getName());
                    } else {
                        /*
                         * The parent instance of the external function doesn't
                         * exist at all
                         */
                        String parentId = functional.get().getSystemOfInterest().getIdPath(parentStore).toString();
                        return Stream.of(new Problem(
                                        name + " is missing in " + parentId, Stream.of(
                                                UpdateSolution.updateAllocated("Flow down", solutionAllocated
                                                        -> solutionAllocated.remove(function.getUuid())
                                                ),
                                                new DisabledSolution("Flow up"))));
                    }

                });
    }
}
