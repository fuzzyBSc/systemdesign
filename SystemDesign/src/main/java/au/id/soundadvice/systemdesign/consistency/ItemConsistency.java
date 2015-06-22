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
package au.id.soundadvice.systemdesign.consistency;

import au.id.soundadvice.systemdesign.baselines.EditState;
import au.id.soundadvice.systemdesign.baselines.FunctionalBaseline;
import au.id.soundadvice.systemdesign.baselines.UndoState;
import au.id.soundadvice.systemdesign.model.UndirectedPair;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.relation.RelationStore;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class ItemConsistency implements ProblemFactory {

    private static Stream<Interface> getInterfaces(
            RelationStore store, Item item) {
        return store.getReverse(item.getUuid(), Interface.class);
    }

    private static UndoState flowItemDown(UndoState state, UUID interfaceUUID) {
        Optional<FunctionalBaseline> functional = state.getFunctional();
        if (!functional.isPresent()) {
            return state;
        }
        RelationStore parentStore = functional.get().getStore();
        Item system = functional.get().getSystemOfInterest();
        Optional<Interface> iface = parentStore.get(interfaceUUID, Interface.class);
        if (!iface.isPresent()) {
            return state;
        }
        Item parentItem = iface.get().otherEnd(parentStore, system);

        Item externalItem = parentItem.asExternal(parentStore);
        state = state.setAllocated(state.getAllocated().add(externalItem));

        Iterator<Function> functionsWithFlowsOnThisInterface = parentStore.getReverse(
                externalItem.getUuid(), Function.class)
                .filter(FunctionConsistency.hasFlowsOnInterface(parentStore, interfaceUUID))
                .iterator();
        while (functionsWithFlowsOnThisInterface.hasNext()) {
            Function function = functionsWithFlowsOnThisInterface.next();
            state = FunctionConsistency.flowDown(state, function.getUuid());
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
        Optional<FunctionalBaseline> functional = state.getFunctional();
        if (!functional.isPresent()) {
            return Stream.empty();
        }
        Item system = functional.get().getSystemOfInterest();
        RelationStore parentStore = functional.get().getStore();
        RelationStore childStore = state.getAllocated().getStore();

        return getInterfaces(parentStore, system)
                .flatMap(iface -> {
                    Item parentItem = iface.otherEnd(parentStore, system);
                    Optional<Item> childItem = childStore.get(parentItem.getUuid(), Item.class);
                    if (childItem.isPresent()) {
                        Stream<Problem> consistency;
                        Item parentItemAsExternal = parentItem.asExternal(parentStore);
                        if (!childItem.get().isConsistent(parentItemAsExternal)) {
                            String name = parentItemAsExternal.getDisplayName();
                            consistency = Stream.of(
                                    new Problem(
                                            name + " differs between baselines", Stream.of(
                                                    UpdateSolution.updateAllocatedRelation(
                                                            "Flow down", childItem.get().getUuid(), Item.class,
                                                            relation -> relation.makeConsistent(parentItemAsExternal)),
                                                    UpdateSolution.updateFunctionalRelation(
                                                            "Flow up", parentItem.getUuid(), Item.class,
                                                            relation -> relation.makeConsistent(childItem.get())))));
                        } else {
                            consistency = Stream.empty();
                        }
                        Stream<Problem> flows = FunctionConsistency.checkConsistencyDown(state, iface);
                        return Stream.concat(consistency, flows);
                    } else {
                        String name = parentItem.getDisplayName();
                        String parentId = functional.get().getSystemOfInterest().getIdPath(parentStore).toString();
                        return Stream.of(new Problem(
                                        name + " is missing in " + parentId, Stream.of(
                                                UpdateSolution.update("Flow down", solutionState
                                                        -> flowItemDown(solutionState, iface.getUuid())),
                                                UpdateSolution.updateFunctional("Flow up", solutionFunctional
                                                        -> solutionFunctional.remove(iface.getUuid())
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
        Optional<FunctionalBaseline> functional = state.getFunctional();
        if (!functional.isPresent()) {
            return Stream.empty();
        }
        Item system = functional.get().getSystemOfInterest();
        RelationStore parentStore = functional.get().getStore();
        RelationStore childStore = state.getAllocated().getStore();

        return childStore.getByClass(Item.class)
                .filter(Item::isExternal)
                .flatMap((item) -> {
                    Optional<Interface> interfaceInParent
                    = parentStore.getReverse(system.getUuid(), Interface.class)
                    .filter((iface) -> item.getUuid().equals(
                                    iface.otherEnd(parentStore, system).getUuid()))
                    .findAny();

                    if (interfaceInParent.isPresent()) {
                        // This item is fine. Check its functions.
                        return FunctionConsistency.checkConsistencyUp(
                                state, interfaceInParent.get(), item.getUuid());
                    }

                    String name = item.getDisplayName();
                    String parentId = functional.get().getSystemOfInterest().getIdPath(parentStore).toString();
                    if (parentStore.get(item.getUuid(), Item.class).isPresent()) {
                        /*
                         * The parent instance exists, just not the relevant
                         * interface.
                         */
                        return Stream.of(new Problem(
                                        name + " is missing in " + parentId, Stream.of(UpdateSolution.updateAllocated("Flow down", solutionAllocated
                                                        -> solutionAllocated.remove(item.getUuid())
                                                ),
                                                UpdateSolution.updateFunctional("Flow up", solutionFunctional -> {
                                                    UndirectedPair scope = new UndirectedPair(
                                                            item.getUuid(), system.getUuid());
                                                    Interface toAdd = Interface.createNew(scope);
                                                    return solutionFunctional.add(toAdd);
                                                }))));
                    } else {
                        /*
                         * The parent instance of the external item doesn't
                         * exist at all
                         */
                        return Stream.of(new Problem(
                                        name + " is missing in " + parentId, Stream.of(
                                                UpdateSolution.updateAllocated("Flow down", solutionAllocated
                                                        -> solutionAllocated.remove(item.getUuid())
                                                ),
                                                new DisabledSolution("Flow up"))));
                    }
                });
    }
}
