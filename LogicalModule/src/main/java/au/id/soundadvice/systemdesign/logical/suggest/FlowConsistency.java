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
import au.id.soundadvice.systemdesign.moduleapi.Direction;
import au.id.soundadvice.systemdesign.logical.Flow;
import au.id.soundadvice.systemdesign.logical.FlowType;
import au.id.soundadvice.systemdesign.logical.Function;
import au.id.soundadvice.systemdesign.physical.Interface;
import au.id.soundadvice.systemdesign.physical.Item;
import au.id.soundadvice.systemdesign.moduleapi.RelationPair;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import au.id.soundadvice.systemdesign.physical.Identity;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.util.Pair;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FlowConsistency {

    private static class FlowDirectionSummary {

        @Override
        public String toString() {
            return "FlowDirectionSummary{" + "scope=" + scope + ", type=" + type + ", direction=" + direction + '}';
        }

        public FlowType getType() {
            return type;
        }

        public Direction getDirection() {
            return direction;
        }

        public RelationPair<Function> getScope() {
            return scope;
        }

        public FlowDirectionSummary(Function left, Function right, FlowType type, Direction direction) {
            this.scope = new RelationPair<>(left, right);
            this.type = type;
            this.direction = (left == scope.getLeft())
                    ? direction : direction.reverse();
        }

        public FlowDirectionSummary(RelationPair<Function> scope, FlowType type, Direction direction) {
            this.scope = scope;
            this.type = type;
            this.direction = direction;
        }

        public static FlowDirectionSummary reduceDirection(
                FlowDirectionSummary left, FlowDirectionSummary right) {
            if (left == null) {
                return right;
            } else if (right == null) {
                return left;
            } else {
                assert left.scope.equals(right.scope);
                assert left.type == right.type;
                return new FlowDirectionSummary(
                        left.scope,
                        left.type, left.direction.add(right.direction));
            }
        }

        public static Collector<FlowDirectionSummary, ?, Map<RelationPair<Function>, Map<FlowType, FlowDirectionSummary>>> collector() {
            return Collectors.groupingBy(
                    FlowDirectionSummary::getScope,
                    Collectors.groupingBy(
                            FlowDirectionSummary::getType,
                            Collectors.reducing(
                                    null,
                                    FlowDirectionSummary::reduceDirection)));
        }

        private final RelationPair<Function> scope;
        private final FlowType type;
        private final Direction direction;
    }

    /**
     * Find the flow types and directions that exist in the functional baseline,
     * do not exist in the allocated baseline, and for which all prerequisites
     * exist in the allocated baseline.
     */
    private static Stream<FlowDirectionSummary> canFlowDown(UndoState state) {
        Relations functionalRelations = state.getFunctional();
        Relations allocatedRelations = state.getAllocated();
        Optional<Item> system = Identity.getSystemOfInterest(state);
        if (!system.isPresent()) {
            return Stream.empty();
        }
        Set<Function> tracedFunctions = Function.find(allocatedRelations)
                .flatMap(allocatedFunction -> {
                    Optional<Function> result = allocatedFunction.getTrace(functionalRelations);
                    return result.map(f -> Stream.of(f)).orElse(Stream.empty());
                })
                .collect(Collectors.toSet());
        return Interface.find(functionalRelations, system.get())
                .flatMap(iface -> Flow.find(functionalRelations, iface))
                .flatMap(functionalRelationsFlow -> {
                    Function functionalLeft = functionalRelationsFlow.getLeft(functionalRelations);
                    Function functionalRight = functionalRelationsFlow.getRight(functionalRelations);
                    if (tracedFunctions.contains(functionalLeft)
                            && tracedFunctions.contains(functionalRight)) {
                        /*
                         * Only report flows whose functions have something
                         * tracing to them from the allocated baseline.
                         */
                        return Stream.of(new FlowDirectionSummary(
                                functionalLeft, functionalRight,
                                functionalRelationsFlow.getType(functionalRelations),
                                functionalRelationsFlow.getDirection()));
                    } else {
                        return Stream.empty();
                    }
                });
    }

    /**
     * Find the flow types and directions that exist in the allocated baseline,
     * do not exist in the functional baseline, and for which all prerequisites
     * exist in the functional baseline.
     */
    private static Stream<FlowDirectionSummary> canFlowUp(UndoState state) {
        Relations functionalRelations = state.getFunctional();
        Relations allocatedRelations = state.getAllocated();
        Set<RelationPair<Item>> functionalInterfaceScopes = Interface.find(functionalRelations)
                .map(iface -> iface.getEndpoints(functionalRelations))
                .collect(Collectors.toSet());
        return Flow.find(allocatedRelations).flatMap(allocatedRelationsFlow -> {
            Function allocatedLeft = allocatedRelationsFlow.getLeft(allocatedRelations);
            Function allocatedRight = allocatedRelationsFlow.getRight(allocatedRelations);
            Optional<Function> functionalLeft = allocatedLeft.getTrace(functionalRelations);
            Optional<Function> functionalRight = allocatedRight.getTrace(functionalRelations);

            if (functionalLeft.isPresent() && functionalRight.isPresent()
                    && !functionalLeft.equals(functionalRight)) {
                /*
                 * Only report flows whose parent functions exist and are
                 * distinct.
                 *
                 * We still need to make sure the interface exists
                 */
                // Find or fake the parent type
                FlowType allocatedType = allocatedRelationsFlow.getType(allocatedRelations);
                FlowType functionalType = allocatedType.getTrace(functionalRelations)
                        .orElse(allocatedType);
                Item functionalLeftItem = functionalLeft.get().getItem(functionalRelations);
                Item functionalRightItem = functionalRight.get().getItem(functionalRelations);
                RelationPair<Item> functionalInterfaceScope = new RelationPair<>(functionalLeftItem, functionalRightItem);
                if (functionalInterfaceScopes.contains(functionalInterfaceScope)) {
                    return Stream.of(new FlowDirectionSummary(
                            functionalLeft.get(), functionalRight.get(),
                            functionalType,
                            allocatedRelationsFlow.getDirection()));
                } else {
                    return Stream.empty();
                }
            } else {
                return Stream.empty();
            }
        });
    }

    /**
     * Search for missing flows in the allocated baseline.
     *
     * @param state
     * @return
     */
    public static Stream<Problem> getProblems(UndoState state) {
        Map<RelationPair<Function>, Map<FlowType, FlowDirectionSummary>> functionalRelationsCandidates
                = canFlowDown(state).collect(FlowDirectionSummary.collector());
        Map<RelationPair<Function>, Map<FlowType, FlowDirectionSummary>> allocatedRelationsCandidates
                = canFlowUp(state).collect(FlowDirectionSummary.collector());
        return Stream.concat(
                checkConsistencyDown(state, functionalRelationsCandidates, allocatedRelationsCandidates),
                checkConsistencyUp(state, functionalRelationsCandidates, allocatedRelationsCandidates));
    }

    private static Stream<Problem> checkConsistencyDown(
            UndoState state,
            Map<RelationPair<Function>, Map<FlowType, FlowDirectionSummary>> functionalRelationsCandidates,
            Map<RelationPair<Function>, Map<FlowType, FlowDirectionSummary>> allocatedRelationsCandidates) {
        return functionalRelationsCandidates.entrySet().stream()
                .flatMap(scopeEntry -> {
                    RelationPair<Function> scope = scopeEntry.getKey();
                    Map<FlowType, FlowDirectionSummary> functionalType = scopeEntry.getValue();
                    Map<FlowType, FlowDirectionSummary> allocatedType
                            = allocatedRelationsCandidates.getOrDefault(scope, Collections.emptyMap());
                    return functionalType.entrySet().stream()
                            .flatMap(typeEntry -> {
                                FlowType type = typeEntry.getKey();
                                FlowDirectionSummary functionalSummary = typeEntry.getValue();
                                Optional<FlowDirectionSummary> allocatedSummary
                                        = Optional.ofNullable(allocatedType.get(type));
                                Direction missingDirections = functionalSummary.getDirection().remove(
                                        allocatedSummary
                                        .map(FlowDirectionSummary::getDirection)
                                        .orElse(Direction.None));
                                if (missingDirections == Direction.None) {
                                    return Stream.empty();
                                } else {
                                    String description = getDescription(
                                            "Missing",
                                            functionalSummary.scope.getLeft()
                                            .getDisplayName(state.getFunctional()),
                                            functionalSummary.scope.getRight()
                                            .getDisplayName(state.getFunctional()),
                                            type, missingDirections);
                                    FlowDirectionSummary toDelete = new FlowDirectionSummary(
                                            functionalSummary.scope, functionalSummary.type,
                                            missingDirections);
                                    return Stream.of(new Problem(description,
                                            // We don't know which new child flows to add,
                                            // so can't flow down
                                            Optional.empty(),
                                            Optional.of(removeFunctionalFlowDirections(toDelete))));
                                }

                            }
                            );
                });
    }

    private static Stream<Problem> checkConsistencyUp(
            UndoState state,
            Map<RelationPair<Function>, Map<FlowType, FlowDirectionSummary>> functionalRelationsCandidates,
            Map<RelationPair<Function>, Map<FlowType, FlowDirectionSummary>> allocatedRelationsCandidates) {
        return allocatedRelationsCandidates.entrySet().stream()
                .flatMap(scopeEntry -> {
                    RelationPair<Function> scope = scopeEntry.getKey();
                    Map<FlowType, FlowDirectionSummary> allocatedType = scopeEntry.getValue();
                    Map<FlowType, FlowDirectionSummary> functionalType
                            = functionalRelationsCandidates.getOrDefault(scope, Collections.emptyMap());
                    return allocatedType.entrySet().stream()
                            .flatMap(typeEntry -> {
                                FlowType type = typeEntry.getKey();
                                FlowDirectionSummary allocatedSummary = typeEntry.getValue();
                                Optional<FlowDirectionSummary> functionalSummary
                                        = Optional.ofNullable(functionalType.get(type));
                                Direction extraDirections = allocatedSummary.getDirection().remove(
                                        functionalSummary
                                        .map(FlowDirectionSummary::getDirection)
                                        .orElse(Direction.None));
                                if (extraDirections == Direction.None) {
                                    return Stream.empty();
                                } else {
                                    String description = getDescription(
                                            "Extra",
                                            allocatedSummary.scope.getLeft()
                                            .getDisplayName(state.getFunctional()),
                                            allocatedSummary.scope.getRight()
                                            .getDisplayName(state.getFunctional()),
                                            type, extraDirections);
                                    FlowDirectionSummary toDeleteDown = new FlowDirectionSummary(
                                            allocatedSummary.scope, allocatedSummary.type,
                                            extraDirections);
                                    FlowDirectionSummary toAddUp = new FlowDirectionSummary(
                                            allocatedSummary.scope, allocatedSummary.type,
                                            extraDirections);
                                    return Stream.of(new Problem(description,
                                            Optional.of(removeAllocatedFlowDirections(toDeleteDown)),
                                            Optional.of(addFunctionalFlowDirections(toAddUp))));
                                }
                            });
                });
    }

    private static String getDescription(
            String missingExtra,
            String fromDescription, String toDescription,
            FlowType type, Direction direction) {
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

    private static UnaryOperator<UndoState> removeAllocatedFlowDirections(
            FlowDirectionSummary summary) {
        return state -> {
            Relations functional = state.getFunctional();
            Relations allocated = state.getAllocated();
            /*
             * Find the allocated version of the type by tracing rather than
             * name matching, as type name mistmatch is a separate class of
             * inconsistency.
             */
            FlowType type = FlowType.find(allocated)
                    .filter(allocatedType -> allocatedType.getTrace(functional)
                            .map(traceType -> traceType.equals(summary.type)).orElse(false))
                    .findAny().orElse(summary.type);
            Iterator<Flow> it = Flow.find(allocated).iterator();
            while (it.hasNext()) {
                Flow flow = it.next();
                Function left = flow.getLeft(allocated);
                Function right = flow.getRight(allocated);
                Optional<Function> leftTrace = left.getTrace(functional);
                Optional<Function> rightTrace = right.getTrace(functional);
                if (leftTrace.isPresent() && rightTrace.isPresent()) {
                    RelationPair<Function> traceScope = new RelationPair<>(
                            leftTrace.get(), rightTrace.get());
                    if (traceScope.equals(summary.scope)) {
                        Direction allocatedDirection
                                = summary.scope.setDirection(summary.getDirection())
                                .getDirectionFrom(leftTrace.get());
                        allocated = Flow.remove(
                                allocated,
                                left, right,
                                type, allocatedDirection);
                    }
                }
            }
            return state.setAllocated(allocated);
        };
    }

    private static UnaryOperator<UndoState> addFunctionalFlowDirections(
            FlowDirectionSummary summary) {
        return state -> {
            Relations functional = state.getFunctional();
            Optional<Function> left = functional.get(summary.scope.getLeft());
            Optional<Function> right = functional.get(summary.scope.getRight());
            if (left.isPresent() && right.isPresent()) {
                // Type addition is a noop if the type already exists
                Pair<Relations, FlowType> typeAddResult
                        = FlowType.add(functional, Optional.empty(), summary.type.getName());
                functional = typeAddResult.getKey();
                FlowType functionalType = typeAddResult.getValue();
                functional = Flow.add(
                        functional,
                        summary.scope.setDirection(summary.direction),
                        functionalType).getKey();
                state = state.setFunctional(functional);
                // If we did just create the type above we might also need to add
                // a new trace upwards from the allocated type to the new functional
                // baseline type.
                Relations allocated = state.getAllocated();
                Optional<FlowType> allocatedType = allocated.get(summary.type);
                if (allocatedType.isPresent()) {
                    Optional<FlowType> existingTrace = allocatedType.get().getTrace(functional);
                    if (!existingTrace.isPresent()) {
                        allocated = allocatedType.get().setTrace(
                                allocated, Optional.of(functionalType)).getKey();
                        state = state.setAllocated(allocated);
                    }
                }
                return state;
            } else {
                return state;
            }
        };
    }

    private static UnaryOperator<UndoState> removeFunctionalFlowDirections(
            FlowDirectionSummary summary) {
        return state -> {
            Relations functional = state.getFunctional();
            functional = Flow.remove(
                    functional,
                    summary.scope.getLeft(), summary.scope.getRight(),
                    summary.type, summary.direction);
            return state.setFunctional(functional);
        };
    }

}
