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
import au.id.soundadvice.systemdesign.beans.Direction;
import au.id.soundadvice.systemdesign.consistency.DisabledSolution;
import au.id.soundadvice.systemdesign.consistency.Problem;
import au.id.soundadvice.systemdesign.consistency.UpdateSolution;
import au.id.soundadvice.systemdesign.model.DirectedPair;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.relation.RelationStore;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FlowConsistency {

    private static Map<UUID, Map<String, Direction>> getFlowTypes(
            UUID fromUUID, Stream<Flow> flows, UnaryOperator<Optional<UUID>> uuidFixer) {
        return flows
                .filter(flow -> flow.getScope().hasEnd(fromUUID))
                .collect(Collectors.groupingBy(
                                flow -> uuidFixer.apply(Optional.of(flow.getScope().otherEnd(fromUUID))),
                                Collectors.groupingBy(
                                        Flow::getType,
                                        Collectors.mapping(
                                                flow -> flow.getScope().getDirectionFrom(fromUUID),
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

    private static Stream<Map.Entry<UUID, Stream<Map.Entry<String, Direction>>>> getDifferenceByFunction(
            Map<UUID, Map<String, Direction>> subtractFrom,
            Map<UUID, Map<String, Direction>> toSubtract) {
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
     * @param parentInterfaceUUID The parent interface to scope the analysis to
     * @param externalParentFunctionUUID The external parent function to scope
     * analysis to
     * @param externalFunctionDescription The display name of the external
     * function
     * @return The list of problems identified
     */
    public static Stream<Problem> checkConsistency(
            UndoState state, UUID parentInterfaceUUID,
            UUID externalParentFunctionUUID,
            String externalFunctionDescription) {
        RelationStore parentStore;
        {
            Optional<FunctionalBaseline> functional = state.getFunctional();
            if (!functional.isPresent()) {
                return Stream.empty();
            }
            parentStore = functional.get().getStore();
        }
        RelationStore childStore = state.getAllocated().getStore();

        // system function -> flow type -> flow direction from external function
        Map<UUID, Map<String, Direction>> parentFlows = getFlowTypes(
                externalParentFunctionUUID,
                parentStore.getReverse(parentInterfaceUUID, Flow.class)
                .filter(flow -> flow.getScope().hasEnd(externalParentFunctionUUID)),
                optionalUUID -> optionalUUID);
        Map<UUID, Map<String, Direction>> childFlows = getFlowTypes(
                externalParentFunctionUUID,
                childStore.getReverse(externalParentFunctionUUID, Flow.class),
                optionalUUID -> {
                    if (optionalUUID.isPresent()) {
                        Optional<Function> function = childStore.get(optionalUUID.get(), Function.class);
                        if (function.isPresent()) {
                            return function.get().getTrace();
                        }
                    }
                    return Optional.empty();
                }
        );

        Stream<Problem> missingFromParent = getDifferenceByFunction(childFlows, parentFlows)
                .flatMap(byFunction -> {
                    UUID functionUUID = byFunction.getKey();
                    Optional<Function> systemFunction = parentStore.get(functionUUID, Function.class);
                    if (!systemFunction.isPresent()) {
                        return Stream.empty();
                    }
                    String systemFunctionDescription = systemFunction.get().getName();
                    return byFunction.getValue()
                    .flatMap(byType -> {
                        String type = byType.getKey();
                        Direction direction = byType.getValue();
                        String description = getDescription(
                                "Extra",
                                externalFunctionDescription,
                                systemFunctionDescription,
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
                                                                        new DirectedPair(
                                                                                externalParentFunctionUUID,
                                                                                childFunction.getUuid(),
                                                                                direction),
                                                                        type);
                                                            }
                                                            return allocated;
                                                        }),
                                                UpdateSolution.updateFunctional("Flow up", functional
                                                        -> functional.setContext(
                                                                addFlowDirections(
                                                                        functional.getContext(),
                                                                        new DirectedPair(
                                                                                externalParentFunctionUUID,
                                                                                functionUUID,
                                                                                direction),
                                                                        type))))));
                    });
                });

        Stream<Problem> missingFromChild = getDifferenceByFunction(parentFlows, childFlows)
                .flatMap(byFunction -> {
                    UUID functionUUID = byFunction.getKey();
                    Optional<Function> systemFunction = parentStore.get(functionUUID, Function.class);
                    if (!systemFunction.isPresent()) {
                        return Stream.empty();
                    }
                    String systemFunctionDescription = systemFunction.get().getName();
                    return byFunction.getValue()
                    .flatMap(byType -> {
                        String type = byType.getKey();
                        Direction direction = byType.getValue();
                        String description = getDescription(
                                "Missing",
                                externalFunctionDescription,
                                systemFunctionDescription,
                                type, direction);
                        return Stream.of(new Problem(description, Stream.of(
                                                // We don't know which new child flows to add,
                                                // so can't flow down
                                                new DisabledSolution("Flow down"),
                                                UpdateSolution.updateFunctional("Flow up", functional
                                                        -> functional.setContext(
                                                                removeFlowDirections(
                                                                        functional.getContext(),
                                                                        new DirectedPair(
                                                                                externalParentFunctionUUID,
                                                                                functionUUID,
                                                                                direction),
                                                                        type))))));
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

    private static AllocatedBaseline addFlowDirections(
            AllocatedBaseline allocated,
            DirectedPair directions,
            String type) {
        RelationStore store = allocated.getStore();
        Optional<Function> left = store.get(directions.getLeft(), Function.class);
        Optional<Function> right = store.get(directions.getRight(), Function.class);
        if (left.isPresent() && right.isPresent()) {
            return allocated.addFlow(
                    left.get(), right.get(),
                    type,
                    directions.getDirection()).getValue();
        } else {
            return allocated;
        }
    }

    private static AllocatedBaseline removeFlowDirections(
            AllocatedBaseline allocated,
            DirectedPair directions,
            String type) {
        RelationStore store = allocated.getStore();
        Optional<Function> left = store.get(directions.getLeft(), Function.class);
        Optional<Function> right = store.get(directions.getRight(), Function.class);
        if (left.isPresent() && right.isPresent()) {
            return allocated.removeFlow(
                    left.get(), right.get(),
                    type,
                    directions.getDirection());
        } else {
            return allocated;
        }
    }
}
