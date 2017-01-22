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
package au.id.soundadvice.systemdesign.logical.entity;

import au.id.soundadvice.systemdesign.moduleapi.entity.ConnectionScope;
import au.id.soundadvice.systemdesign.physical.entity.Interface;
import au.id.soundadvice.systemdesign.moduleapi.entity.Direction;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.collection.RecordConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import au.id.soundadvice.systemdesign.physical.entity.Identity;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;
import javafx.util.Pair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;
import au.id.soundadvice.systemdesign.moduleapi.entity.UniqueConstraint;

/**
 * A flow represents the transfer of information, energy and/or materials from
 * one function to another.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public enum Flow implements Table {
    flow;

    public static Pair<WhyHowPair<Baseline>, Record> addWithGuessedType(WhyHowPair<Baseline> baselines, String now, RecordConnectionScope scope) {
        Pair<WhyHowPair<Baseline>, Record> tmp = FlowType.define(baselines, now, guessTypeName(baselines, scope));
        baselines = tmp.getKey();
        Record flowType = tmp.getValue();
        return add(baselines, now, scope, flowType);
    }

    private static String guessTypeName(WhyHowPair<Baseline> baselines, RecordConnectionScope newChildScope) {
        // See if we can pick out a likely type
        Optional<RecordConnectionScope> parentScope = newChildScope.getTrace(baselines, Function.function);
        if (parentScope.isPresent()
                && !parentScope.get().isSelfConnection()
                && newChildScope.getLeft().isExternal() != newChildScope.getRight().isExternal()) {
            Set<Record> alreadyUsedParentTypes = Flow.find(baselines.getChild()).parallel()
                    .filter(childFlow -> {
                        Optional<RecordConnectionScope> existingParentScope = Flow.flow
                                .getFunctions(baselines.getChild(), childFlow)
                                .getTrace(baselines, Function.function);
                        if (existingParentScope.isPresent() && !existingParentScope.get().isSelfConnection()) {
                            return existingParentScope.get().contains(parentScope.get());
                        } else {
                            return false;
                        }
                    })
                    .flatMap(childFlow -> FlowType.flowType.getTrace(
                            baselines, Flow.flow.getFlowType(baselines.getChild(), childFlow))
                            .map(Stream::of).orElse(Stream.empty()))
                    .collect(Collectors.toSet());

            Optional<Record> parentSuggestion = Flow.findAnyType(baselines.getParent(), parentScope.get())
                    .map(parentFlow -> Flow.flow.getType(baselines.getParent(), parentFlow))
                    .filter(parentType -> !alreadyUsedParentTypes.contains(parentType))
                    .findAny();
            if (parentSuggestion.isPresent()) {
                return parentSuggestion.get().getLongName();
            }
        }
        // Fall back to guessing a new type name
        int ii = 0;
        for (;;) {
            String guess;
            if (ii == 0) {
                guess = "New Flow";
            } else {
                guess = "New Flow " + ii;
            }

            Optional<Record> existing = FlowType.get(baselines.getChild(), guess);
            if (!existing.isPresent()) {
                return guess;
            }
            ++ii;
        }
    }

    @Override
    public String getTableName() {
        return name();
    }

    public static Stream<Record> getFlowsOnInterface(
            Baseline baseline, Record forInterface) {
        return baseline.findReverse(forInterface.getIdentifier(), flow);
    }

    /**
     * A transfer of information, energy or materials from one function to
     * another
     *
     * @param baseline The baseline to search
     * @return
     */
    public static Stream<Record> find(Baseline baseline) {
        return baseline.findByType(Flow.flow);
    }

    public static Stream<Record> findForInterface(Baseline baseline, Record iface) {
        return baseline.findReverse(iface.getIdentifier(), Flow.flow);
    }

    static Stream<Record> findForFunction(Baseline baseline, Record function) {
        return baseline.findReverse(function.getIdentifier(), Flow.flow);
    }

    public static Stream<Record> findAnyType(Baseline baseline, RecordConnectionScope scope) {
        return baseline.findByScope(scope.getScope());
    }

    public static Optional<Record> get(
            Baseline baseline, RecordConnectionScope scope, Record flowType) {
        return findAnyType(baseline, scope)
                .filter(candidate -> {
                    return flowType.getIdentifier().equals(candidate.getIdentifier());
                }).findAny();
    }

    public RecordConnectionScope getFunctions(Baseline baseline, Record flow) {
        ConnectionScope scope = flow.getConnectionScope();
        return RecordConnectionScope.resolve(baseline, scope, Function.function).get();
    }

    public Pair<RecordConnectionScope, Record> getScopeAndType(Baseline baseline, Record flow) {
        return new Pair<>(getFunctions(baseline, flow), getType(baseline, flow));
    }

    private static Optional<Record> getExpectedTrace(
            WhyHowPair<Baseline> baselines, RecordConnectionScope childScope, Record flowType) {
        Optional<Record> flowTrace = flowType.getTrace().flatMap(
                trace -> baselines.getParent().get(trace, FlowType.flowType));
        Optional<Record> leftTrace
                = childScope.getLeft().getTrace()
                .flatMap(trace -> baselines.getParent().get(trace, Function.function));
        Optional<Record> rightTrace
                = childScope.getRight().getTrace()
                .flatMap(trace -> baselines.getParent().get(trace, Function.function));
        if (flowTrace.isPresent() && leftTrace.isPresent() && rightTrace.isPresent()) {
            RecordConnectionScope traceScope = RecordConnectionScope.resolve(
                    leftTrace.get(),
                    rightTrace.get(),
                    Direction.None);
            return get(baselines.getParent(), traceScope, flowTrace.get());
        } else {
            // The flow type doesn't exist. Let consistency checking
            // take care of it if necessary.
            return Optional.empty();
        }
    }

    @CheckReturnValue
    public static Pair<WhyHowPair<Baseline>, Record> add(
            final WhyHowPair<Baseline> baselines, String now,
            RecordConnectionScope scope, Record flowType) {
        Optional<Record> existing = get(baselines.getChild(), scope, flowType);
        if (existing.isPresent()) {
            // Already exists
            return baselines.and(existing.get());
        }
        existing = get(baselines.getChild(), scope.setDirection(Direction.None), flowType);
        if (existing.isPresent()) {
            // Exists, but doesn't fully enclose the desired directions
            RecordConnectionScope existingScope = Flow.flow.getFunctions(baselines.getChild(), existing.get());
            RecordConnectionScope updatedScope = existingScope.setDirection(
                    existingScope.getDirection().add(scope.getDirection()));
            Record updated = existing.get().asBuilder()
                    .setConnectionScope(updatedScope)
                    .build(now);
            WhyHowPair<Baseline> result = baselines.setChild(baselines.getChild().add(updated));
            return result.and(updated);
        }
        // The flow does not exist yet
        Record leftFunction = scope.getLeft();
        Record rightFunction = scope.getRight();
        if ( // At least one end must be internal
                (!leftFunction.isExternal() || !rightFunction.isExternal())
                // Self-flows are not allowed
                && !scope.isSelfConnection()) {
            boolean external = leftFunction.isExternal() || rightFunction.isExternal();
            Optional<Record> flowTrace;
            if (leftFunction.getIdentifier().equals(rightFunction.getIdentifier())) {
                flowTrace = Optional.of(leftFunction);
            } else {
                flowTrace = getExpectedTrace(baselines, scope, flowType);
            }

            WhyHowPair<Baseline> result;

            Record leftItem = Function.function.getItemForFunction(baselines.getChild(), leftFunction);
            Record rightItem = Function.function.getItemForFunction(baselines.getChild(), rightFunction);
            Record iface;
            {
                RecordConnectionScope interfaceScope = RecordConnectionScope.resolve(
                        leftItem, rightItem, Direction.None);
                Pair<WhyHowPair<Baseline>, Record> tmp = Interface.connect(baselines, now, interfaceScope);
                result = tmp.getKey();
                iface = tmp.getValue();
            }

            Record created = Record.create(flow)
                    .setTrace(flowTrace)
                    .setSubtype(flowType)
                    .setContainer(iface)
                    .setExternal(external)
                    .build(now);
            result = result.setChild(result.getChild().add(created));
            return result.and(created);
        }
        return baselines.and(null);
    }

    @CheckReturnValue
    public static Baseline remove(
            Baseline baseline, String now, RecordConnectionScope scope, Record type) {
        Optional<Record> existing = get(baseline, scope, type);
        if (existing.isPresent()) {
            RecordConnectionScope existingScope = Flow.flow.getFunctions(baseline, existing.get());
            Direction updatedDirection = existingScope.getDirection()
                    .remove(scope.getDirection());
            if (updatedDirection == Direction.None) {
                return baseline.remove(existing.get().getIdentifier());
            } else {
                RecordConnectionScope updatedScope = existingScope.setDirection(updatedDirection);
                Record updated = existing.get().asBuilder()
                        .setConnectionScope(updatedScope)
                        .build(now);
                return baseline.add(updated);
            }
        } else {
            return baseline;
        }
    }

    public Record getFlowType(Baseline baseline, Record flow) {
        return baseline.get(flow.getSubtype().get(), FlowType.flowType).get();
    }

    @CheckReturnValue
    public Pair<Baseline, Record> setFlowType(Baseline baseline, Record flow, Record flowType, String now) {
        Record newFlow = flow.asBuilder()
                .setSubtype(flowType)
                .build(now);
        return new Pair<>(baseline.add(newFlow), newFlow);
    }

    @Override
    public Stream<UniqueConstraint> getUniqueConstraints() {
        return Stream.of(record -> new Object[]{record.getConnectionScope().setDirection(Direction.None), record.getSubtype()});
    }

    @Override
    public Record merge(WhyHowPair<Baseline> baselines, String now, Record left, Record right) {
        RecordConnectionScope leftScope = Flow.flow.getFunctions(baselines.getChild(), left);
        RecordConnectionScope rightScope = Flow.flow.getFunctions(baselines.getChild(), right);
        Record result = Record.newerOf(left, right);
        if (!leftScope.equals(rightScope)) {
            Direction mergedDirection = leftScope.getDirection().add(rightScope.getDirection());
            result = result.asBuilder()
                    .setConnectionScope(leftScope.setDirection(mergedDirection))
                    .build(now);
        }
        return result;
    }

    @Override
    public Stream<Problem> getTraceProblems(
            WhyHowPair<Baseline> context, Record traceParent, Stream<Record> traceChildren) {
        RecordConnectionScope parentScope = Flow.flow.getFunctions(context.getParent(), traceParent);
        Record parentType = getType(context.getParent(), traceParent);
        Direction aggregateChildDirection = traceChildren
                .map(flow -> {
                    RecordConnectionScope scope = getFunctions(context.getChild(), flow);
                    Optional<RecordConnectionScope> traceScope = scope.getTrace(context, Function.function);
                    if (traceScope.isPresent()
                            && traceScope.get().hasSameEndsAs(parentScope)) {
                        return traceScope.get().getDirection();
                    }
                    return Direction.None;
                })
                .reduce(Direction.None, Direction::add);
        Direction extraChildDirections = aggregateChildDirection.remove(parentScope.getDirection());
        Direction extraParentDirections = parentScope.getDirection().remove(aggregateChildDirection);

        Stream.Builder<Problem> result = Stream.<Problem>builder();
        if (extraChildDirections != Direction.None) {
            RecordConnectionScope extraScope = parentScope.setDirection(extraChildDirections);
            result.accept(
                    Problem.flowProblem(
                            extraScope.toString() + " missing in parent",
                            Optional.of((baselines, now) -> removeFromChild(baselines, now, extraScope, parentType)),
                            Optional.of((baselines, now) -> addToParent(baselines, now, extraScope, parentType))));
        }
        if (extraParentDirections != Direction.None) {
            RecordConnectionScope extraScope = parentScope.setDirection(extraParentDirections);
            result.accept(
                    Problem.flowProblem(
                            extraScope.toString() + " missing in child",
                            Optional.empty(),
                            Optional.of((baselines, now) -> removeFromParent(baselines, now, extraScope, parentType))));
        }
        return result.build();
    }

    private WhyHowPair<Baseline> addToParent(WhyHowPair<Baseline> baselines, String now, RecordConnectionScope parentScope, Record parentFlowType) {
        // Use a fake baseline pair to modify the parent baseline as if it were the child
        WhyHowPair<Baseline> fakeBaselinePair = new WhyHowPair<>(
                baselines.getParent(), baselines.getParent());
        fakeBaselinePair = add(fakeBaselinePair, now, parentScope, parentFlowType).getKey();
        return baselines.setParent(fakeBaselinePair.getChild());
    }

    private WhyHowPair<Baseline> removeFromParent(WhyHowPair<Baseline> baselines, String now, RecordConnectionScope parentScope, Record parentFlowType) {
        return baselines.setParent(remove(baselines.getParent(), now, parentScope, parentFlowType));
    }

    private WhyHowPair<Baseline> removeFromChild(WhyHowPair<Baseline> baselines, String now, RecordConnectionScope parentScope, Record parentFlowType) {
        Iterator<Pair<RecordConnectionScope, Record>> childScopesToRemove = Flow.find(baselines.getChild())
                .flatMap(flow -> {
                    Record type = getType(baselines.getChild(), flow);
                    Optional<Record> traceType = type.getTrace().flatMap(trace -> baselines.getParent().get(trace, FlowType.flowType));
                    if (!traceType.isPresent() || !traceType.get().getIdentifier().equals(parentFlowType.getIdentifier())) {
                        return Stream.empty();
                    }
                    RecordConnectionScope scope = getFunctions(baselines.getChild(), flow);
                    Optional<RecordConnectionScope> traceScope = scope.getTrace(baselines, Function.function);
                    if (!traceScope.isPresent() || !traceScope.get().hasSameEndsAs(parentScope)) {
                        return Stream.empty();
                    }
                    Direction childDirectionsToRemove;
                    if (scope.getLeft().getTrace().get().equals(traceScope.get().getLeft().getIdentifier())) {
                        childDirectionsToRemove = parentScope.getDirection();
                    } else {
                        childDirectionsToRemove = parentScope.getDirection().reverse();
                    }
                    return Stream.of(new Pair<>(scope.setDirection(childDirectionsToRemove), type));
                })
                .iterator();
        WhyHowPair<Baseline> result = baselines;
        while (childScopesToRemove.hasNext()) {
            Pair<RecordConnectionScope, Record> toRemove = childScopesToRemove.next();
            result = result.setChild(
                    remove(result.getChild(), now, toRemove.getKey(), toRemove.getValue()));
        }
        return result;
    }

    @Override
    public Stream<Problem> getUntracedParentProblems(WhyHowPair<Baseline> context, Stream<Record> untracedParents) {
        Map<RecordID, Record> flowedDownFunctions = Function.find(context.getChild())
                .filter(Record::isExternal)
                .flatMap(func -> Function.function.getTrace(context, func)
                        .map(Stream::of).orElse(Stream.empty()))
                .collect(Collectors.toMap(Record::getIdentifier, o -> o));
        return untracedParents
                .flatMap(parentFlow -> {
                    RecordConnectionScope parentScope = Flow.flow.getFunctions(context.getParent(), parentFlow);
                    if (flowedDownFunctions.containsKey(parentScope.getLeft().getIdentifier())
                            || flowedDownFunctions.containsKey(parentScope.getRight().getIdentifier())) {
                        // If the function has been flowed down, then the flow should also be here
                        return Stream.of(Problem.flowProblem(getDisplayName(context.getParent(), parentFlow) + " is missing from child",
                                Optional.empty(),
                                Optional.of((baseline, now) -> baseline.setParent(baseline.getParent().remove(parentFlow.getIdentifier())))));
                    }
                    return Stream.empty();
                });
    }

    @Override
    public Stream<Problem> getUntracedChildProblems(WhyHowPair<Baseline> context, Stream<Record> untracedChildren) {
        Optional<Record> systemOfInterest = Identity.getSystemOfInterest(context);
        if (systemOfInterest.isPresent()) {
            return untracedChildren
                    .map(flow -> {
                        Optional<Record> expectedTrace = getExpectedTrace(
                                context, Flow.flow.getFunctions(context.getChild(), flow),
                                Flow.flow.getType(context.getChild(), flow));
                        if (expectedTrace.isPresent()) {
                            return Problem.onLoadAutofixProblem((baselines, now) -> fixTrace(baselines, now, flow));
                        } else {
                            return Problem.flowProblem(
                                    getDisplayName(context.getChild(), flow) + " is not traced",
                                    Optional.empty(),
                                    Optional.empty());
                        }
                    });
        } else {
            return Stream.empty();
        }
    }

    public Record getType(Baseline baseline, Record flow) {
        return baseline.get(flow.getSubtype().get(), FlowType.flowType).get();
    }

    private String getDisplayName(Baseline baseline, Record flow) {
        return flow.getLongName();
    }

    private WhyHowPair<Baseline> fixTrace(WhyHowPair<Baseline> baselines, String now, Record record) {
        Optional<Record> flow = baselines.getChild().get(record);
        Optional<Record> expectedTrace = flow.flatMap(
                ff -> getExpectedTrace(
                        baselines, Flow.flow.getFunctions(baselines.getChild(), ff),
                        Flow.flow.getType(baselines.getChild(), ff)));
        if (flow.isPresent() && expectedTrace.isPresent()) {
            Record updated = flow.get().asBuilder()
                    .setTrace(expectedTrace)
                    .build(now);
            return baselines.setChild(baselines.getChild().add(updated));
        }
        return baselines;
    }
}
