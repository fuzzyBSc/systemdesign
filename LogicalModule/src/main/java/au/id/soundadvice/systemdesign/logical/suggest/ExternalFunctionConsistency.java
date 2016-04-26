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
package au.id.soundadvice.systemdesign.logical.suggest;

import au.id.soundadvice.systemdesign.moduleapi.UndoState;
import au.id.soundadvice.systemdesign.logical.Flow;
import au.id.soundadvice.systemdesign.logical.Function;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import au.id.soundadvice.systemdesign.physical.Identity;
import au.id.soundadvice.systemdesign.physical.Interface;
import au.id.soundadvice.systemdesign.physical.Item;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class ExternalFunctionConsistency {

    public static Predicate<? super Function> hasFlowsOnInterface(
            Relations baseline, Interface iface) {
        return function -> {
            return function.findFlows(baseline)
                    .anyMatch(flow -> {
                        return iface.getIdentifier().equals(flow.getInterface().getKey());
                    });
        };
    }

    private static Stream<Flow> getFlowsOnInterface(
            Relations baseline, Function forFunction, Interface forInterface) {
        return forFunction.findFlows(baseline)
                .filter(flow -> forInterface.getIdentifier().equals(
                        flow.getInterface().getKey()));
    }

    public static UnaryOperator<UndoState> addExternalFunctionDown(Function parentFunction) {
        return state -> {
            Optional<Item> system = Identity.getSystemOfInterest(state);
            if (!system.isPresent()) {
                return state;
            }
            Optional<Function> current = state.getFunctional().get(parentFunction);
            if (current.isPresent()) {
                return Function.flowDownExternal(state, current.get()).getKey();
            } else {
                return state;
            }
        };
    }

    public static UnaryOperator<UndoState> removeExternalFunctionDown(Function allocatedExternalFunction) {
        return state -> state.setAllocated(
                allocatedExternalFunction.removeFrom(state.getAllocated()));
    }

    public static Stream<Problem> getProblems(UndoState state) {
        return Stream.concat(
                checkConsistencyDown(state), checkConsistencyUp(state));
    }

    /**
     * Figure out whether external functions are flowed down correctly.
     *
     * @param state The undo state to work from
     * @return The problems and solutions identified
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
                    Optional<Item> allocatedExternalItem = problemAllocated.get(functionalExternalItem);
                    if (!allocatedExternalItem.isPresent()) {
                        /*
                         * The interface has not been flowed down, so don't
                         * worry about external functions yet
                         */
                        return Stream.empty();
                    }

                    return Function.findOwnedFunctions(problemFunctional, functionalExternalItem)
                            .filter(hasFlowsOnInterface(problemFunctional, iface))
                            .flatMap(parentFunction -> {
                                Optional<Function> allocatedFunction = problemAllocated.get(parentFunction);
                                if (allocatedFunction.isPresent()) {
                                    if (allocatedFunction.get().isConsistent(parentFunction)) {
                                        /*
                                         * The external function exists in both
                                         * baselines and is consistent. All
                                         * good.
                                         */
                                        return Stream.empty();
                                    } else {
                                        /*
                                         * The function exists in both baselines
                                         * but is not consistent.
                                         */
                                        String functionName = parentFunction.getName();
                                        return Stream.of(
                                                new Problem(
                                                        functionName + " differs between baselines",
                                                        Optional.of(solutionState -> solutionState.setAllocated(
                                                                allocatedFunction.get().makeConsistent(
                                                                        solutionState.getAllocated(), parentFunction)
                                                                .getKey())),
                                                        Optional.of(solutionState -> solutionState.setFunctional(
                                                                parentFunction.makeConsistent(
                                                                        solutionState.getFunctional(), allocatedFunction.get())
                                                                .getKey()))));
                                    }
                                } else {
                                    String functionName = parentFunction.getName();
                                    String systemName = system.get().getDisplayName();
                                    return Stream.of(new Problem(
                                            functionName + " is missing in\n" + systemName,
                                            Optional.of(addExternalFunctionDown(parentFunction)),
                                            Optional.empty()));
                                }
                            });
                });
    }

    /**
     * Figure out whether external functions in allocated baseline exist in
     * functional baseline.
     *
     * @param state The undo state to work from
     * @return The problems and solutions identified
     */
    public static Stream<Problem> checkConsistencyUp(UndoState state) {
        Optional<Item> system = Identity.getSystemOfInterest(state);
        if (!system.isPresent()) {
            return Stream.empty();
        }
        Relations problemFunctional = state.getFunctional();
        Relations problemAllocated = state.getAllocated();

        return Function.find(problemAllocated)
                .filter(allocatedExternalFunction -> {
                    return allocatedExternalFunction.isExternal()
                            && !Function.getSystemFunctionsForExternalFunction(
                                    state, allocatedExternalFunction).findAny().isPresent();
                })
                .flatMap(allocatedExternalFunction -> {
                    String name = allocatedExternalFunction.getName();
                    if (problemFunctional.get(allocatedExternalFunction).isPresent()) {
                        /*
                         * The parent instance still exists. All good.
                         */
                        return Stream.empty();
                    } else {
                        /*
                         * The parent instance of the external function doesn't
                         * exist at all
                         */
                        String externalItemName = allocatedExternalFunction
                                .getItem(problemAllocated)
                                .getDisplayName();
                        return Stream.of(new Problem(
                                "Extra function\n"
                                + name + "\n"
                                + "in " + externalItemName,
                                Optional.of(removeExternalFunctionDown(allocatedExternalFunction)),
                                Optional.empty()));
                    }

                });
    }
}
