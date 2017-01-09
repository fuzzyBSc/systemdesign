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

import au.id.soundadvice.systemdesign.physical.entity.Item;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Fields;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.moduleapi.event.EventDispatcher;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import au.id.soundadvice.systemdesign.physical.entity.Identity;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;
import au.id.soundadvice.systemdesign.moduleapi.entity.UniqueConstraint;

/**
 * A function is a unit of functionality of an item. An item can have multiple
 * functions. Each function has corresponding flows in and out that when added
 * together describe the total set of flows in and out of the item.
 *
 * Functions typically have a two-word name consisting of noun and verb. For
 * example: Collate files. Noun and verb should both be drawn from a restricted
 * vocabulary. This technique is intended to maintain a model of system
 * functionality simple enough for humans to readily understand and to readily
 * appreciate when functions overlap between items. Combined with the explicit
 * use of flows the functional design is intended to clearly demonstrate
 * coupling and cohesion and encourage allocation of functionality to the most
 * appropriate item.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public enum Function implements Table {
    function;

    @Override
    public String getTableName() {
        return "function";
    }

    /**
     * Return all of the functions allocated to any subsystems of the baseline,
     * as well as external functions.
     *
     * @param baseline The baseline to search
     * @return
     */
    public static Stream<Record> find(Baseline baseline) {
        return baseline.findByType(Function.function);
    }

    public Record getItemForFunction(Baseline baseline, Record function) {
        return baseline.get(function.getContainer().get(), Item.item).get();
    }

    /**
     * Return the list of functions that this item implements directly.
     *
     * @param baseline This item's baseline
     * @param item The item to search
     * @return
     */
    public static Stream<Record> findOwnedFunctions(Baseline baseline, Record item) {
        return baseline.findReverse(item.getIdentifier(), Function.function);
    }

    static Stream<Record> findConnectedFunctions(Baseline baseline, Record function) {
        return Flow.findForFunction(baseline, function)
                .map(flow -> flow.getConnectionScope().otherEnd(function.getIdentifier()))
                .distinct()
                .map(otherEndIdentifier -> baseline.get(otherEndIdentifier, Function.function).get());
    }

    /**
     * Create a new Function.
     *
     * @param baselines The baselines to update
     * @param now The current instant in ISO8601 format
     * @param item The item that implements this function
     * @param traceFunction If a functional baseline exists for this allocated
     * baseline then the functional baseline Function this function traces to.
     * Otherwise, Optional.empty()
     * @param longName The name of the function
     * @return The updated baseline
     */
    @CheckReturnValue
    public static Pair<WhyHowPair<Baseline>, Record> create(
            WhyHowPair<Baseline> baselines, String now, Record item, Optional<Record> traceFunction, String longName) {
        Record record = Record.create(function)
                .setContainer(item)
                .setTrace(traceFunction)
                .setLongName(longName)
                .build(now);
        baselines = baselines.setChild(baselines.getChild().add(record));
        baselines = EventDispatcher.INSTANCE.dispatchCreateEvent(baselines, now, record);
        return new Pair<>(baselines, record);
    }

    /**
     * Flow an external item down from the functional baseline to the allocated
     * baseline.
     *
     * @param baselines The state to update
     * @param now The current instant in ISO8610 format
     * @param record The function to flow down from the state's functional
     * baseline
     * @return The updated baseline
     */
    @CheckReturnValue
    public static Pair<WhyHowPair<Baseline>, Record> flowDownExternal(
            WhyHowPair<Baseline> baselines, String now, Record record) {
        Optional<Record> externalParentFunction = baselines.getParent().get(record);
        Optional<Record> externalParentItem = externalParentFunction.map(
                func -> function.getItemForFunction(baselines.getParent(), func));
        Optional<Record> externalChildItem = externalParentItem.flatMap(
                func -> baselines.getChild().findByTrace(Optional.of(func.getIdentifier())).findAny());
        if (externalParentFunction.isPresent() && externalParentItem.isPresent() && externalChildItem.isPresent()) {
            Optional<Record> existing = externalParentFunction.flatMap(
                    func -> baselines.getChild().findByTrace(Optional.of(func.getIdentifier())).findAny());
            if (existing.isPresent()) {
                Map<String, String> fields = new HashMap<>(externalParentFunction.get().getAllFields());
                Record updated = existing.get().asBuilder()
                        .putFields(fields)
                        .setTrace(externalParentFunction)
                        .setContainer(externalChildItem.get())
                        .setExternal(true)
                        .build(now);
                WhyHowPair<Baseline> result = baselines.setChild(baselines.getChild().add(updated));
                return result.and(updated);
            } else {
                Record flowedDown = externalParentFunction.get().asBuilder()
                        .newIdentifier()
                        .removeReferences()
                        .setTrace(externalParentFunction)
                        .setContainer(externalChildItem.get())
                        .setExternal(true)
                        .build(now);
                WhyHowPair<Baseline> result = baselines.setChild(baselines.getChild().add(flowedDown));
                result = EventDispatcher.INSTANCE.dispatchFlowDownEvent(result, now, flowedDown);
                return result.and(flowedDown);
            }
        }
        return baselines.and(null);
    }

    @CheckReturnValue
    public static Pair<WhyHowPair<Baseline>, Record> flowUpExternal(
            WhyHowPair<Baseline> baselines, String now, Record record) {
        Optional<Record> externalChildFunction = baselines.getChild().get(record);
        Optional<Record> externalChildItem = externalChildFunction.map(
                func -> function.getItemForFunction(baselines.getChild(), func));
        Optional<Record> externalParentFunction = externalChildFunction
                .flatMap(Record::getTrace)
                .flatMap(trace -> baselines.getParent().get(trace, function));
        Optional<Record> externalParentItem = externalChildItem
                .flatMap(Record::getTrace)
                .flatMap(trace -> baselines.getParent().get(trace, Item.item));
        if (externalParentFunction.isPresent() && externalParentItem.isPresent()
                && externalChildFunction.isPresent() && externalChildItem.isPresent()) {
            Map<String, String> fields = new HashMap<>(externalChildFunction.get().getAllFields());
            fields.remove(Fields.trace.name());
            fields.remove(Fields.external.name());
            Record updated = externalParentFunction.get().asBuilder()
                    .putFields(fields)
                    .build(now);
            WhyHowPair<Baseline> result = baselines.setParent(baselines.getParent().add(updated));
            return result.and(updated);
        }
        return baselines.and(null);
    }

    public Stream<Record> findViews(Baseline baseline, Record record) {
        return baseline.findReverse(record.getIdentifier(), FunctionView.functionView);
    }

    public Stream<Record> findFlows(Baseline baseline, Record record) {
        return baseline.findReverse(record.getIdentifier(), Flow.flow);
    }

    public Optional<Record> getTrace(WhyHowPair<Baseline> baselines, Record record) {
        return baselines.getChild().get(record)
                .flatMap(Record::getTrace)
                .flatMap(trace -> baselines.getParent().get(trace, this));
    }

    public String getDisplayName(Record item, Record function) {
        StringBuilder builder = new StringBuilder();
        builder.append(function.getLongName());
        builder.append("\n(");
        builder.append(Item.item.getDisplayName(item));
        builder.append(')');
        return builder.toString();
    }

    public String getDisplayName(Baseline context, Record function) {
        Record item = Function.function.getItemForFunction(context, function);
        return getDisplayName(item, function);
    }

    public boolean hasFlowsOnInterface(Baseline baseline, Record function, Record iface) {
        return Flow.getFlowsOnInterface(baseline, iface)
                .anyMatch(flow -> flow.getConnectionScope().hasEnd(function.getIdentifier()));
    }

    @Override
    public Stream<UniqueConstraint> getUniqueConstraints() {
        return Stream.of(
                record -> {
                    if (record.isExternal()) {
                        return record.getTrace();
                    } else {
                        // No additional constraint
                        return record.getIdentifier();
                    }
                });
    }

    @Override
    public Record merge(WhyHowPair<Baseline> baselines, String now, Record left, Record right) {
        return Record.newerOf(left, right);
    }

    @Override
    public Stream<Problem> getTraceProblems(WhyHowPair<Baseline> context, Record traceParent, Stream<Record> traceChildren) {
        return traceChildren
                .filter(Record::isExternal)
                .flatMap(externalChildFunction -> {
                    HashMap<String, String> parentFields = new HashMap<>(traceParent.getAllFields());
                    HashMap<String, String> childFields = new HashMap<>(externalChildFunction.getFields());
                    parentFields.remove(Fields.trace.name());
                    childFields.remove(Fields.trace.name());
                    if (!parentFields.equals(childFields)) // Refresh child fields from parent
                    {
                        Record item = Function.function.getItemForFunction(context.getChild(), externalChildFunction);
                        return Stream.of(Problem.flowProblem(
                                getDisplayName(item, externalChildFunction) + " does not match parent",
                                Optional.of((baselines, now) -> flowDownExternal(baselines, now, traceParent).getKey()),
                                Optional.of((baselines, now) -> flowUpExternal(baselines, now, externalChildFunction).getKey())));
                    }
                    return Stream.empty();
                });
    }

    @Override
    public Stream<Problem> getUntracedParentProblems(WhyHowPair<Baseline> context, Stream<Record> untracedParents) {
        Optional<Record> systemOfInterest = Identity.getSystemOfInterest(context);
        if (systemOfInterest.isPresent()) {
            Map<RecordID, Record> parentItemsWithChildren = Item.find(context.getChild())
                    .filter(Record::isExternal)
                    .flatMap(externalChildItem -> Item.item.getTrace(context, externalChildItem)
                            .map(Stream::of).orElse(Stream.empty()))
                    .collect(Collectors.toMap(Record::getIdentifier, o -> o));
            Map<RecordID, Record> connectedFunctions = Function.findOwnedFunctions(context.getParent(), systemOfInterest.get())
                    .flatMap(systemFunction -> findConnectedFunctions(context.getParent(), systemFunction))
                    .filter(candidate -> parentItemsWithChildren.containsKey(candidate.getContainer().get()))
                    .collect(Collectors.toMap(Record::getIdentifier, o -> o));

            return untracedParents
                    .filter(parentFunction -> connectedFunctions.containsKey(parentFunction.getIdentifier()))
                    .map(parentFunction -> {
                        Record parentItem = Function.function.getItemForFunction(context.getParent(), parentFunction);
                        return Problem.flowProblem(
                                getDisplayName(parentItem, parentFunction) + " is missing in child",
                                Optional.of((baselines, now) -> flowDownExternal(baselines, now, parentFunction).getKey()),
                                Optional.of((baselines, now) -> baselines.setParent(baselines.getParent().remove(parentFunction.getIdentifier()))));
                    });
        }
        return Stream.empty();
    }

    @Override
    public Stream<Problem> getUntracedChildProblems(WhyHowPair<Baseline> context, Stream<Record> untracedChildren) {
        Optional<Record> systemOfInterest = Identity.getSystemOfInterest(context);
        return untracedChildren
                .flatMap(function -> {
                    if (function.isExternal()) {
                        return Stream.of(Problem.flowProblem(
                                Function.function.getDisplayName(context.getChild(), function) + " is missing in parent",
                                Optional.of((baseline, now) -> baseline.setChild(baseline.getChild().remove(function.getIdentifier()))),
                                Optional.empty()));
                    } else if (systemOfInterest.isPresent()) {
                        return Stream.of(Problem.flowProblem(
                                Function.function.getDisplayName(context.getChild(), function) + " is untraced",
                                Optional.empty(),
                                Optional.empty()));
                    } else {
                        return Stream.empty();
                    }
                });
    }

    public Pair<WhyHowPair<Baseline>, Record> setTrace(WhyHowPair<Baseline> baselines, String now, Record function, Record traceFunction) {
        Record updated = function.asBuilder()
                .setTrace(traceFunction)
                .build(now);
        return new Pair<>(baselines.setChild(baselines.getChild().add(updated)), updated);
    }
}
