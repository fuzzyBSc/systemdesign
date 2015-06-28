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
import au.id.soundadvice.systemdesign.baselines.UndoState;
import au.id.soundadvice.systemdesign.beans.Direction;
import au.id.soundadvice.systemdesign.consistency.DisabledSolution;
import au.id.soundadvice.systemdesign.consistency.Problem;
import au.id.soundadvice.systemdesign.consistency.UpdateSolution;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FlowConsistency {

    private static Map<Function, Map<String, Direction>> getFlowTypes(
            Baseline baseline, Function fromFunction, Stream<Flow> flows, UnaryOperator<Optional<Function>> functionFixer) {
        return flows
                .filter(flow -> flow.hasEnd(fromFunction))
                .collect(Collectors.groupingBy(
                                flow -> functionFixer.apply(Optional.of(flow.otherEnd(baseline, fromFunction))),
                                Collectors.groupingBy(
                                        Flow::getType,
                                        Collectors.mapping(
                                                flow -> flow.getDirectionFrom(fromFunction),
                                                Collectors.collectingAndThen(
                                                        Collectors.toSet(),
                                                        Direction::valueOf
                                                )))))
                .entrySet().stream()
                .filter(entry -> entry.getKey().isPresent())
                .collect(Collectors.toMap(entry -> entry.getKey().get(), Map.Entry::getValue));
    }

    private static Stream<Map.Entry<String, Direction>> getDifferenceByType(
            Map<String, Direction> subtractFrom,
            Map<String, Direction> toSubtract) {
        return subtractFrom.entrySet().stream()
                .map(entry -> {
                    Direction toSubtractDirection
                    = toSubtract.get(entry.getKey());
                    if (toSubtractDirection == null) {
                        return entry;
                    } else {
                        return new SimpleImmutableEntry<>(entry.getKey(),
                                entry.getValue().remove(toSubtractDirection));
                    }
                })
                .filter(entry -> entry.getValue() != Direction.None);
    }

    private static Stream<Map.Entry<Function, Stream<Map.Entry<String, Direction>>>> getDifferenceByFunction(
            Map<Function, Map<String, Direction>> subtractFrom,
            Map<Function, Map<String, Direction>> toSubtract) {
        return subtractFrom.entrySet().stream()
                .map(entry -> {
                    Map<String, Direction> toSubtractByType
                    = toSubtract.get(entry.getKey());
                    if (toSubtractByType == null) {
                        return new SimpleImmutableEntry<>(
                                entry.getKey(), entry.getValue().entrySet().stream());
                    } else {
                        return new SimpleImmutableEntry<>(
                                entry.getKey(), getDifferenceByType(
                                        entry.getValue(), toSubtractByType));
                    }
                });
    }

    /**
     * Figure out whether external flows are consistent with the allocated
     * baseline.
     *
     * @param state The undo buffer state to work from
     * @param functionalInterface The interface in the functional baseline to
     * scope the analysis to
     * @param externalAllocatedFunction The external function in the allocated
     * baseline to scope analysis to
     * @return The list of problems identified
     */
    public static Stream<Problem> checkConsistency(
            UndoState state, Interface functionalInterface,
            Function externalAllocatedFunction) {
        Optional<Item> system = state.getSystemOfInterest();
        if (!system.isPresent()) {
            return Stream.empty();
        }
        Baseline problemFunctional = state.getFunctional();
        Baseline problemAllocated = state.getAllocated();

        // system function -> flow type -> flow direction from external function
        Map<Function, Map<String, Direction>> parentFlows = getFlowTypes(
                problemFunctional,
                externalAllocatedFunction,
                functionalInterface.getFlows(problemFunctional)
                .filter(flow -> flow.hasEnd(externalAllocatedFunction)),
                t -> t);
        Map<Function, Map<String, Direction>> childFlows = getFlowTypes(
                problemAllocated,
                externalAllocatedFunction,
                externalAllocatedFunction.getFlows(problemAllocated),
                optionalFunction -> optionalFunction
                .flatMap(function -> problemAllocated.get(function))
                .flatMap(function -> function.getTrace(problemFunctional))
        );

        Stream<Problem> missingFromParent = getDifferenceByFunction(childFlows, parentFlows)
                .flatMap(byFunction -> {
                    Function systemFunction = byFunction.getKey();
                    return byFunction.getValue()
                    .flatMap(byType -> {
                        String type = byType.getKey();
                        Direction direction = byType.getValue();
                        String description = getDescription(
                                "Extra",
                                externalAllocatedFunction.getDisplayName(problemAllocated),
                                systemFunction.getDisplayName(problemFunctional),
                                type, direction);
                        return Stream.of(new Problem(description, Stream.of(
                                                // Remove from all child functions
                                                UpdateSolution.updateAllocated("Flow down",
                                                        allocated -> {
                                                            Iterator<Function> it = allocated.getFunctions().iterator();
                                                            while (it.hasNext()) {
                                                                Function childFunction = it.next();
                                                                allocated = removeFlowDirections(
                                                                        allocated,
                                                                        externalAllocatedFunction,
                                                                        childFunction,
                                                                        type, direction);
                                                            }
                                                            return allocated;
                                                        }),
                                                UpdateSolution.updateFunctional("Flow up", functional
                                                        -> addFlowDirections(
                                                                functional,
                                                                externalAllocatedFunction,
                                                                systemFunction,
                                                                type, direction)))));
                    });
                });

        Stream<Problem> missingFromChild = getDifferenceByFunction(parentFlows, childFlows)
                .flatMap(byFunction -> {
                    Function systemFunction = byFunction.getKey();
                    return byFunction.getValue()
                    .flatMap(byType -> {
                        String type = byType.getKey();
                        Direction direction = byType.getValue();
                        String description = getDescription(
                                "Missing",
                                externalAllocatedFunction.getDisplayName(problemAllocated),
                                systemFunction.getDisplayName(problemFunctional),
                                type, direction);
                        return Stream.of(new Problem(description, Stream.of(
                                                // We don't know which new child flows to add,
                                                // so can't flow down
                                                new DisabledSolution("Flow down"),
                                                UpdateSolution.updateFunctional("Flow up", solutionFunctional
                                                        -> removeFlowDirections(
                                                                solutionFunctional,
                                                                externalAllocatedFunction,
                                                                systemFunction,
                                                                type, direction)))));
                    });
                });

        return Stream.concat(missingFromParent, missingFromChild);
    }

    private static String getDescription(
            String missingExtra,
            String fromDescription, String toDescription,
            String type, Direction direction) {
        String from;
        String to;
        switch (direction) {
            case Both:
            case Normal:
                from = fromDescription;
                to = toDescription;
                break;
            case Reverse:
                from = toDescription;
                to = fromDescription;
                break;
            case None:
            default:
                throw new AssertionError(direction.name());

        }
        return missingExtra + " " + type + " flow\n"
                + "from " + from + "\n"
                + "to " + to;
    }

    private static Baseline addFlowDirections(
            Baseline allocated,
            Function leftSample, Function rightSample,
            String type, Direction directions) {
        Optional<Function> left = allocated.get(leftSample);
        Optional<Function> right = allocated.get(rightSample);
        if (left.isPresent() && right.isPresent()) {
            return Flow.add(
                    allocated,
                    left.get(), right.get(),
                    type,
                    directions).getBaseline();
        } else {
            return allocated;
        }
    }

    private static Baseline removeFlowDirections(
            Baseline allocated,
            Function leftSample, Function rightSample,
            String type, Direction directions) {
        Optional<Function> left = allocated.get(leftSample);
        Optional<Function> right = allocated.get(rightSample);
        if (left.isPresent() && right.isPresent()) {
            return Flow.remove(
                    allocated,
                    left.get(), right.get(),
                    type,
                    directions);
        } else {
            return allocated;
        }
    }
}
