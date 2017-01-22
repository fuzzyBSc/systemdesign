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

import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Fields;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.event.EventDispatcher;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;
import au.id.soundadvice.systemdesign.moduleapi.entity.UniqueConstraint;

/**
 * A flow represents the transfer of information, energy and/or materials from
 * one function to another.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public enum FlowType implements Table {
    flowType;

    @Override
    public String getTableName() {
        return name();
    }

    @Override
    public Stream<UniqueConstraint> getUniqueConstraints() {
        // Types have unique long names within a baseline
        return Stream.of(Record::getLongName);
    }

    @Override
    public Record merge(WhyHowPair<Baseline> baselines, String now, Record left, Record right) {
        return Record.newerOf(left, right);
    }

    /**
     * A distinct combination of information, energy and materials
     *
     * @param baseline The baseline to search
     * @return
     */
    public static Stream<Record> find(Baseline baseline) {
        return baseline.findByType(FlowType.flowType);
    }

    public static Optional<Record> get(Baseline baseline, String longName) {
        return baseline.findByLongName(longName)
                .filter(candidate -> candidate.getType().equals(flowType))
                .findAny();
    }

    @CheckReturnValue
    public static Pair<WhyHowPair<Baseline>, Record> define(
            WhyHowPair<Baseline> baselines, String now, String longName) {
        Optional<Record> existing = get(baselines.getChild(), longName);
        if (existing.isPresent()) {
            return new Pair<>(baselines, existing.get());
        } else {
            Optional<Record> parent = baselines.getParent().findByLongName(longName)
                    .findAny();
            if (parent.isPresent()) {
                return flowDown(baselines, now, parent.get());
            } else {
                Record created = Record.create(flowType)
                        .setLongName(longName)
                        .build(now);
                baselines = baselines.setChild(baselines.getChild().add(created));
                baselines = EventDispatcher.INSTANCE.dispatchCreateEvent(baselines, now, created);
                return new Pair<>(baselines, created);
            }
        }
    }

    @CheckReturnValue
    public static Baseline remove(Baseline baseline, String name) {
        Optional<Record> existing = get(baseline, name);
        if (existing.isPresent()) {
            return baseline.remove(existing.get().getIdentifier());
        } else {
            return baseline;
        }
    }

    private static Pair<WhyHowPair<Baseline>, Record> flowDown(WhyHowPair<Baseline> baselines, String now, Record record) {
        Optional<Record> parent = baselines.getParent().get(record);
        if (parent.isPresent()) {
            Optional<Record> child = baselines.getChild().findByTrace(Optional.of(parent.get().getIdentifier())).findAny();
            if (child.isPresent()) {
                HashMap<String, String> fields = new HashMap<>(parent.get().getAllFields());
                fields.remove(Fields.trace.name());
                Record newChild = child.get().asBuilder()
                        .putFields(fields)
                        .build(now);
                baselines = baselines.setChild(baselines.getChild().add(newChild));
                return new Pair<>(baselines, newChild);
            } else {
                Record newChild = parent.get().asBuilder()
                        .newIdentifier()
                        .removeReferences()
                        .setTrace(parent)
                        .build(now);
                baselines = baselines.setChild(baselines.getChild().add(newChild));
                baselines = EventDispatcher.INSTANCE.dispatchFlowDownEvent(baselines, now, record);
                return new Pair<>(baselines, newChild);
            }
        }
        return new Pair<>(baselines, null);
    }

    private static Pair<WhyHowPair<Baseline>, Record> flowUp(WhyHowPair<Baseline> baselines, String now, Record record) {
        Optional<Record> child = baselines.getChild().get(record);
        Optional<Record> parent = record.getTrace()
                .flatMap(trace -> baselines.getParent().get(trace, flowType));
        if (child.isPresent() && parent.isPresent()) {
            HashMap<String, String> fields = new HashMap<>(child.get().getAllFields());
            fields.remove(Fields.trace.name());
            Record newParent = parent.get().asBuilder()
                    .putFields(fields)
                    .build(now);
            WhyHowPair<Baseline> result = baselines.setParent(baselines.getParent().add(newParent));
            return new Pair<>(result, newParent);
        }
        return new Pair<>(baselines, null);
    }

    public Stream<Record> getFlows(Baseline baseline, Record flowType) {
        return baseline.findReverse(flowType.getIdentifier(), Flow.flow);
    }

    @Override
    public Stream<Problem> getTraceProblems(WhyHowPair<Baseline> context, Record traceParent, Stream<Record> traceChildren) {
        // Duplicate children are handled elsewhere
        return traceChildren.flatMap(childType -> {
            HashMap<String, String> parentFields = new HashMap<>(traceParent.getAllFields());
            HashMap<String, String> childFields = new HashMap<>(childType.getFields());
            parentFields.remove(Fields.trace.name());
            childFields.remove(Fields.trace.name());
            if (!parentFields.equals(childFields)) // Refresh child external item fields from parent
            {
                return Stream.of(Problem.flowProblem(childType.getLongName() + " does not match parent",
                        Optional.of((baselines, now) -> flowDown(baselines, now, traceParent).getKey()),
                        Optional.of((baselines, now) -> flowUp(baselines, now, childType).getKey())
                ));
            }
            return Stream.empty();
        });
    }

    public String getDisplayName(Record flowType) {
        return flowType.getLongName();
    }

    @Override
    public Stream<Problem> getUntracedParentProblems(WhyHowPair<Baseline> context, Stream<Record> untracedParents) {
        // It's correct for types to exist in the parent that either have not
        // yet or never will be flowed down to this level.
        return Stream.empty();
    }

    @Override
    public Stream<Problem> getUntracedChildProblems(WhyHowPair<Baseline> context, Stream<Record> untracedChildren) {
        return untracedChildren.map(child -> {
            return Problem.onLoadAutofixProblem((baselines, now) -> fixChildTrace(baselines, now, child));
        });
    }

    private WhyHowPair<Baseline> fixChildTrace(WhyHowPair<Baseline> baselines, String now, Record record) {
        Optional<Record> child = baselines.getChild().get(record);
        if (child.isPresent()) {
            Optional<Record> parent = baselines.getParent().findByLongName(child.get().getLongName()).findAny();
            if (parent.isPresent()) {
                return baselines.setChild(
                        baselines.getChild()
                        .add(child.get().asBuilder().setTrace(parent).build(now)));
            }
        }
        return baselines;
    }

    public Optional<Record> getTrace(WhyHowPair<Baseline> baselines, Record usedChildType) {
        return usedChildType.getTrace().flatMap(trace -> baselines.getParent().get(trace, this));
    }
}
