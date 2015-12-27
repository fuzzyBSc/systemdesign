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
package au.id.soundadvice.systemdesign.physical.suggest;

import au.id.soundadvice.systemdesign.moduleapi.UndoState;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import au.id.soundadvice.systemdesign.physical.Identity;
import au.id.soundadvice.systemdesign.physical.Interface;
import au.id.soundadvice.systemdesign.physical.Item;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class InterfaceConsistency {

    private static UnaryOperator<UndoState> flowExternalItemDown(Interface iface) {
        return state -> {
            Optional<Item> system = Identity.getSystemOfInterest(state);
            if (!system.isPresent()) {
                return state;
            }
            Relations functional = state.getFunctional();
            Optional<Interface> current = functional.get(iface);
            if (!current.isPresent()) {
                return state;
            }
            Item parentItem = current.get().otherEnd(functional, system.get());

            state = Item.flowDownExternal(state, parentItem).getKey();

            return state;
        };
    }

    private static UnaryOperator<UndoState> removeSystemInterfaceUp(Interface iface) {
        return solutionState -> solutionState.setFunctional(
                iface.removeFrom(solutionState.getFunctional()));
    }

    private static UnaryOperator<UndoState> makeConsistentDown(
            Item functionalExternalItem, Item allocatedExternalItem) {
        return state -> {
            Item functionalItemAsExternal = functionalExternalItem
                    .asExternal(state.getFunctional());
            return state.setAllocated(
                    allocatedExternalItem.makeConsistent(
                            state.getAllocated(), functionalItemAsExternal).getKey());
        };
    }

    private static UnaryOperator<UndoState> makeConsistentUp(
            Item functionalExternalItem, Item allocatedExternalItem) {
        return state -> state.setFunctional(
                functionalExternalItem.makeConsistent(
                        state.getFunctional(), allocatedExternalItem).getKey());
    }

    private static UnaryOperator<UndoState> removeAllocatedItemDown(
            Item allocatedExternalItem) {
        return state -> state.setAllocated(
                allocatedExternalItem.removeFrom(state.getAllocated()));
    }

    private static UnaryOperator<UndoState> addFunctionalInterfaceUp(
            Item left, Item right) {
        return solutionState -> solutionState.setFunctional(
                Interface.create(
                        solutionState.getFunctional(), left, right)
                .getKey());
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
        Optional<Item> system = Identity.getSystemOfInterest(state);
        if (!system.isPresent()) {
            return Stream.empty();
        }
        Relations problemFunctional = state.getFunctional();
        Relations problemAllocated = state.getAllocated();

        return Interface.find(problemFunctional, system.get())
                .flatMap(iface -> {
                    Item functionalExternalItem = iface.otherEnd(problemFunctional, system.get());
                    if (functionalExternalItem.equals(system.get())) {
                        // Ignore interfaces to self
                        return Stream.empty();
                    }
                    Optional<Item> allocatedExternalItem
                            = problemAllocated.get(functionalExternalItem);
                    if (allocatedExternalItem.isPresent()) {
                        /**
                         * The external item exists in both baselines
                         */
                        Item parentItemAsExternal = functionalExternalItem.asExternal(problemFunctional);
                        if (!allocatedExternalItem.get().isConsistent(parentItemAsExternal)) {
                            String name = parentItemAsExternal.getDisplayName();
                            return Stream.of(
                                    new Problem(
                                            name + " differs between baselines",
                                            Optional.of(makeConsistentDown(
                                                    functionalExternalItem,
                                                    allocatedExternalItem.get())),
                                            Optional.of(makeConsistentUp(
                                                    functionalExternalItem,
                                                    allocatedExternalItem.get()))));
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
                                externalName + " is missing in\n" + systemName,
                                Optional.of(flowExternalItemDown(iface)),
                                Optional.of(removeSystemInterfaceUp(iface))));
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
        Optional<Item> system = Identity.getSystemOfInterest(state);
        if (!system.isPresent()) {
            return Stream.empty();
        }
        Relations problemFunctional = state.getFunctional();
        Relations problemAllocated = state.getAllocated();

        return Item.find(problemAllocated)
                .filter(Item::isExternal)
                .flatMap(allocatedExternalItem -> {
                    Optional<Interface> functionalInterface
                            = Interface.find(problemFunctional, system.get())
                            .filter(iface -> allocatedExternalItem.getUuid().equals(
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
                    String name = allocatedExternalItem.getDisplayName();
                    String parentId = system.get().getDisplayName();
                    if (problemFunctional.get(allocatedExternalItem).isPresent()) {
                        /*
                         * The external item exists in the functional baseline,
                         * just not the relevant interface.
                         */
                        return Stream.of(new Problem(
                                name + " is missing in\n" + parentId,
                                Optional.of(removeAllocatedItemDown(allocatedExternalItem)),
                                Optional.of(addFunctionalInterfaceUp(allocatedExternalItem, system.get()))));
                    } else {
                        /*
                         * The external item doesn't exist at all in the
                         * functional baseline.
                         */
                        return Stream.of(new Problem(
                                name + " is missing in " + parentId,
                                Optional.of(removeAllocatedItemDown(allocatedExternalItem)),
                                Optional.empty()));
                    }
                });
    }
}
