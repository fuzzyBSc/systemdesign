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
import au.id.soundadvice.systemdesign.model.UndoState;
import au.id.soundadvice.systemdesign.beans.Direction;
import au.id.soundadvice.systemdesign.consistency.DisabledSolution;
import au.id.soundadvice.systemdesign.consistency.Problem;
import au.id.soundadvice.systemdesign.consistency.ProblemFactory;
import au.id.soundadvice.systemdesign.consistency.UpdateSolution;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.FlowType;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.model.RelationPair;
import au.id.soundadvice.systemdesign.state.EditState;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FlowConsistency implements ProblemFactory {

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
                        left.scope.getLeft(), left.scope.getRight(),
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
        Baseline functionalBaseline = state.getFunctional();
        Baseline allocatedBaseline = state.getAllocated();
        Set<RelationPair<Item>> functionalInterfaceScopes = Interface.find(allocatedBaseline)
                .flatMap(iface -> {
                    Item allocatedLeftItem = iface.getLeft(allocatedBaseline);
                    Item allocatedRightItem = iface.getRight(allocatedBaseline);
                    // Translate into a functional baseline interface scope
                    Optional<Item> functionalLeftItem = allocatedLeftItem.getTrace(state);
                    Optional<Item> functionalRightItem = allocatedRightItem.getTrace(state);
                    if (functionalLeftItem.isPresent() && functionalRightItem.isPresent()) {
                        return Stream.of(new RelationPair<>(functionalLeftItem.get(), functionalRightItem.get()));
                    } else {
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toSet());
        Set<Function> tracedFunctions = Function.find(allocatedBaseline)
                .flatMap(allocatedFunction -> {
                    Optional<Function> result = allocatedFunction.getTrace(functionalBaseline);
                    return result.map(f -> Stream.of(f)).orElse(Stream.empty());
                })
                .collect(Collectors.toSet());
        return Flow.find(functionalBaseline).flatMap(functionalBaselineFlow -> {
            Function functionalLeft = functionalBaselineFlow.getLeft(functionalBaseline);
            Function functionalRight = functionalBaselineFlow.getRight(functionalBaseline);
            if (tracedFunctions.contains(functionalLeft)
                    && tracedFunctions.contains(functionalRight)) {
                /*
                 * Only report flows whose functions have something tracing to
                 * them from the allocated baseline. We still need to make sure
                 * the interface exists
                 */
                Item functionalLeftItem = functionalLeft.getItem(functionalBaseline);
                Item functionalRightItem = functionalRight.getItem(functionalBaseline);
                RelationPair<Item> functionalInterfaceScope = new RelationPair<>(
                        functionalLeftItem, functionalRightItem);
                if (functionalInterfaceScopes.contains(functionalInterfaceScope)) {
                    return Stream.of(new FlowDirectionSummary(
                            functionalLeft, functionalRight,
                            functionalBaselineFlow.getType(functionalBaseline),
                            functionalBaselineFlow.getDirection()));
                } else {
                    return Stream.empty();
                }
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
        Baseline functionalBaseline = state.getFunctional();
        Baseline allocatedBaseline = state.getAllocated();
        Set<RelationPair<Item>> functionalInterfaceScopes = Interface.find(functionalBaseline)
                .map(iface -> iface.getEndpoints(functionalBaseline))
                .collect(Collectors.toSet());
        return Flow.find(allocatedBaseline).flatMap(allocatedBaselineFlow -> {
            Function allocatedLeft = allocatedBaselineFlow.getLeft(allocatedBaseline);
            Function allocatedRight = allocatedBaselineFlow.getRight(allocatedBaseline);
            Optional<Function> functionalLeft = allocatedLeft.getTrace(functionalBaseline);
            Optional<Function> functionalRight = allocatedRight.getTrace(functionalBaseline);

            if (functionalLeft.isPresent() && functionalRight.isPresent()
                    && !functionalLeft.equals(functionalRight)) {
                /*
                 * Only report flows whose parent functions exist and are
                 * distinct. We use the allocated baseline type as the type may
                 * not be present in the functional baseline and we'll want to
                 * offer an opportunity to flow it up. If it does exist in the
                 * parent the parent should have the same UUID so this should
                 * still work correctly.
                 *
                 * We still need to make sure the interface exists
                 */
                Item functionalLeftItem = functionalLeft.get().getItem(functionalBaseline);
                Item functionalRightItem = functionalRight.get().getItem(functionalBaseline);
                RelationPair<Item> functionalInterfaceScope = new RelationPair<>(functionalLeftItem, functionalRightItem);
                if (functionalInterfaceScopes.contains(functionalInterfaceScope)) {
                    return Stream.of(new FlowDirectionSummary(
                            functionalLeft.get(), functionalRight.get(),
                            allocatedBaselineFlow.getType(allocatedBaseline),
                            allocatedBaselineFlow.getDirection()));
                } else {
                    return Stream.empty();
                }
            } else {
                return Stream.empty();
            }
        });
    }

    @Override
    public Stream<Problem> getProblems(EditState edit) {
        return checkConsistency(edit.getState());
    }

    /**
     * Search for missing flows in the allocated baseline.
     *
     * @param state
     * @return
     */
    public static Stream<Problem> checkConsistency(UndoState state) {
        Map<RelationPair<Function>, Map<FlowType, FlowDirectionSummary>> functionalBaselineCandidates
                = canFlowDown(state).collect(FlowDirectionSummary.collector());
        Map<RelationPair<Function>, Map<FlowType, FlowDirectionSummary>> allocatedBaselineCandidates
                = canFlowUp(state).collect(FlowDirectionSummary.collector());
        return Stream.concat(
                checkConsistencyDown(state, functionalBaselineCandidates, allocatedBaselineCandidates),
                checkConsistencyUp(state, functionalBaselineCandidates, allocatedBaselineCandidates));
    }

    private static Stream<Problem> checkConsistencyDown(
            UndoState state,
            Map<RelationPair<Function>, Map<FlowType, FlowDirectionSummary>> functionalBaselineCandidates,
            Map<RelationPair<Function>, Map<FlowType, FlowDirectionSummary>> allocatedBaselineCandidates) {
        return functionalBaselineCandidates.entrySet().stream()
                .flatMap(scopeEntry -> {
                    RelationPair<Function> scope = scopeEntry.getKey();
                    Map<FlowType, FlowDirectionSummary> functionalType = scopeEntry.getValue();
                    Map<FlowType, FlowDirectionSummary> allocatedType
                            = allocatedBaselineCandidates.getOrDefault(scope, Collections.emptyMap());
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
                                    return Stream.of(new Problem(description, Stream.of(
                                            // We don't know which new child flows to add,
                                            // so can't flow down
                                            DisabledSolution.FlowDown,
                                            UpdateSolution.update("Flow up",
                                                    solutionState -> {
                                                        FlowDirectionSummary toDelete = new FlowDirectionSummary(
                                                                functionalSummary.scope, functionalSummary.type,
                                                                missingDirections);
                                                        return removeFunctionalFlowDirections(solutionState, toDelete);
                                                    }))));
                                }

                            }
                            );
                });
    }

    private static Stream<Problem> checkConsistencyUp(
            UndoState state,
            Map<RelationPair<Function>, Map<FlowType, FlowDirectionSummary>> functionalBaselineCandidates,
            Map<RelationPair<Function>, Map<FlowType, FlowDirectionSummary>> allocatedBaselineCandidates) {
        return allocatedBaselineCandidates.entrySet().stream()
                .flatMap(scopeEntry -> {
                    RelationPair<Function> scope = scopeEntry.getKey();
                    Map<FlowType, FlowDirectionSummary> allocatedType = scopeEntry.getValue();
                    Map<FlowType, FlowDirectionSummary> functionalType
                            = functionalBaselineCandidates.getOrDefault(scope, Collections.emptyMap());
                    return allocatedType.entrySet().stream()
                            .flatMap(typeEntry -> {
                                FlowType type = typeEntry.getKey();
                                FlowDirectionSummary allocatedSummary = typeEntry.getValue();
                                Optional<FlowDirectionSummary> functionalSummary
                                        = Optional.ofNullable(functionalType.get(type));
                                Direction missingDirections = allocatedSummary.getDirection().remove(
                                        functionalSummary
                                        .map(FlowDirectionSummary::getDirection)
                                        .orElse(Direction.None));
                                if (missingDirections == Direction.None) {
                                    return Stream.empty();
                                } else {
                                    String description = getDescription(
                                            "Extra",
                                            allocatedSummary.scope.getLeft()
                                            .getDisplayName(state.getFunctional()),
                                            allocatedSummary.scope.getRight()
                                            .getDisplayName(state.getFunctional()),
                                            type, missingDirections);
                                    return Stream.of(new Problem(description, Stream.of(
                                            UpdateSolution.update("Flow down",
                                                    solutionState -> {
                                                        FlowDirectionSummary toDelete = new FlowDirectionSummary(
                                                                allocatedSummary.scope, allocatedSummary.type,
                                                                missingDirections);
                                                        return removeAllocatedFlowDirections(solutionState, toDelete);
                                                    }),
                                            UpdateSolution.update("Flow up",
                                                    solutionState -> {
                                                        FlowDirectionSummary toAdd = new FlowDirectionSummary(
                                                                allocatedSummary.scope, allocatedSummary.type,
                                                                missingDirections);
                                                        return addFunctionalFlowDirections(solutionState, toAdd);
                                                    }))));
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

    private static UndoState removeAllocatedFlowDirections(
            UndoState state, FlowDirectionSummary summary) {
        Baseline functional = state.getFunctional();
        Baseline allocated = state.getAllocated();
        Iterator<Flow> it = Flow.find(allocated).iterator();
        while (it.hasNext()) {
            Flow flow = it.next();
            Function left = flow.getLeft(allocated);
            Function right = flow.getRight(allocated);
            Optional<Function> leftTrace = left.getTrace(functional);
            Optional<Function> rightTrace = right.getTrace(functional);
            if (leftTrace.isPresent() && rightTrace.isPresent()) {
                RelationPair<Function> traceScope = new RelationPair<>(leftTrace.get(), rightTrace.get());
                if (traceScope.equals(summary.scope)) {
                    allocated = Flow.remove(
                            allocated,
                            left, right,
                            summary.type, summary.direction);
                }
            }
        }
        return state.setAllocated(allocated);
    }

    private static UndoState addFunctionalFlowDirections(
            UndoState state, FlowDirectionSummary summary) {
        Baseline functional = state.getFunctional();
        Optional<Function> left = functional.get(summary.scope.getLeft());
        Optional<Function> right = functional.get(summary.scope.getRight());
        if (left.isPresent() && right.isPresent()) {
            functional = Flow.add(
                    functional,
                    summary.scope.setDirection(summary.direction),
                    summary.type).getBaseline();
            return state.setFunctional(functional);
        } else {
            return state;
        }
    }

    private static UndoState removeFunctionalFlowDirections(
            UndoState state, FlowDirectionSummary summary) {
        Baseline functional = state.getFunctional();
        functional = Flow.remove(
                functional,
                summary.scope.getLeft(), summary.scope.getRight(),
                summary.type, summary.direction);
        return state.setFunctional(functional);
    }

}
