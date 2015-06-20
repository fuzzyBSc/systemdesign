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

import au.id.soundadvice.systemdesign.baselines.AllocatedBaseline;
import au.id.soundadvice.systemdesign.baselines.FunctionalBaseline;
import au.id.soundadvice.systemdesign.baselines.UndoState;
import au.id.soundadvice.systemdesign.beans.Direction;
import au.id.soundadvice.systemdesign.model.DirectedPair;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.UndirectedPair;
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
            UUID fromUUID, Stream<Flow> flows, UnaryOperator<UUID> uuidFixer) {
        return flows
                .filter(flow -> flow.getScope().hasEnd(fromUUID))
                .collect(Collectors.groupingBy(
                                flow -> uuidFixer.apply(flow.getScope().otherEnd(fromUUID)),
                                Collectors.groupingBy(
                                        Flow::getType,
                                        Collectors.mapping(
                                                flow -> flow.getScope().getDirectionFrom(fromUUID),
                                                Collectors.collectingAndThen(
                                                        Collectors.toSet(),
                                                        Direction::valueOf
                                                )))));
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
     * @param iface The parent interface to scope the analysis to
     * @param externalParentFunctionUUID The external parent function to scope
     * analysis to
     * @param externalFunctionDescription The display name of the external
     * function
     * @return The list of problems identified
     */
    public static Stream<Problem> checkConsistency(
            UndoState state, Interface iface,
            UUID externalParentFunctionUUID,
            String externalFunctionDescription) {
        RelationStore parentStore;
        {
            FunctionalBaseline functional = state.getFunctional();
            if (functional == null) {
                return Stream.empty();
            }
            parentStore = functional.getStore();
        }
        RelationStore childStore = state.getAllocated().getStore();

        // system function -> flow type -> flow direction from external function
        Map<UUID, Map<String, Direction>> parentFlows = getFlowTypes(
                externalParentFunctionUUID,
                parentStore.getReverse(iface.getUuid(), Flow.class)
                .filter(flow -> flow.getScope().hasEnd(externalParentFunctionUUID)),
                uuid -> uuid);
        Map<UUID, Map<String, Direction>> childFlows = getFlowTypes(
                externalParentFunctionUUID,
                childStore.getReverse(externalParentFunctionUUID, Flow.class),
                uuid -> {
                    Function childFunction = childStore.get(uuid, Function.class);
                    return childFunction == null ? null : childFunction.getTrace();
                });

        Stream<Problem> missingFromParent = getDifferenceByFunction(childFlows, parentFlows)
                .flatMap(byFunction -> {
                    UUID functionUUID = byFunction.getKey();
                    Function systemFunction = parentStore.get(functionUUID, Function.class);
                    if (systemFunction == null) {
                        return Stream.empty();
                    }
                    String systemFunctionDescription = systemFunction.getDisplayName();
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
                    Function systemFunction = parentStore.get(functionUUID, Function.class);
                    if (systemFunction == null) {
                        return Stream.empty();
                    }
                    String systemFunctionDescription = systemFunction.getDisplayName();
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
        Function left = store.get(directions.getLeft(), Function.class);
        Function right = store.get(directions.getRight(), Function.class);
        return allocated.addFlow(left, right, type, directions.getDirection()).getValue();
    }

    private static AllocatedBaseline removeFlowDirections(
            AllocatedBaseline allocated,
            DirectedPair directions,
            String type) {
        RelationStore store = allocated.getStore();
        Function left = store.get(directions.getLeft(), Function.class);
        Function right = store.get(directions.getRight(), Function.class);
        return allocated.removeFlow(left, right, type, directions.getDirection());
    }
}