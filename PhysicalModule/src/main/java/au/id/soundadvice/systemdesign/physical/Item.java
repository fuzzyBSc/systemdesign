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
package au.id.soundadvice.systemdesign.physical;

import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.BaselinePair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Fields;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.event.EventDispatcher;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.scene.paint.Color;
import javax.annotation.CheckReturnValue;
import java.util.HashMap;
import java.util.Map;
import javafx.util.Pair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;
import au.id.soundadvice.systemdesign.moduleapi.entity.UniqueConstraint;

/**
 * A physical Item. Item is used as a fairly loose term in the model and could
 * mean system, subsystem, configuration item, or correspond to a number of
 * standards-based concepts. As far as the model goes it identifies something
 * that exists rather than dealing with what a thing does. Items are at the root
 * of how the model is put together. Each item either is or has the potential to
 * be a whole director unto itself containing other conceptual elements.
 *
 * An item is typically an entire system, an assembly of parts, or a hardware or
 * software configuration item. The kind of existence required of it can be
 * abstract. For software it could end up a unit of software installed under a
 * single software package, a name space or class.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public enum Item implements Table {
    item;

    @Override
    public String getTableName() {
        return name();
    }

    /**
     * Return all items for the baseline.
     *
     * @param baseline The baseline to search
     * @return
     */
    public static Stream<Record> find(Baseline baseline) {
        return baseline.findByType(item);
    }

    @Override
    public Stream<Problem> getTraceProblems(BaselinePair context, Record traceParent, Stream<Record> traceChildren) {
        // Internal child items trace to parent items which indicates that the parent
        // is composed of the children, but there is nothing in particular that
        // should be consistent between the two
        // However, external items should be consistent.
        return traceChildren.flatMap(childItem -> {
            if (childItem.isExternal()) {
                HashMap<String, String> parentFields = new HashMap<>(traceParent.getAllFields());
                HashMap<String, String> childFields = new HashMap<>(childItem.getFields());
                for (Fields field : new Fields[]{Fields.identifier, Fields.trace}) {
                    parentFields.remove(field.name());
                    childFields.remove(field.name());
                }
                if (!traceParent.isExternal()) {
                    parentFields.remove(Fields.shortName.name());
                    childFields.remove(Fields.shortName.name());
                }
                if (!parentFields.equals(childFields)) // Refresh child external item fields from parent
                {
                    return Stream.of(Problem.flowProblem(childItem.getLongName() + " does not match parent",
                            Optional.of((baselines, now) -> flowDownExternal(baselines, now, traceParent)),
                            Optional.of((baselines, now) -> flowUpExternal(baselines, now, childItem))));
                }
            } else {
                // Make sure internal items trace to the system of interest
                return Stream.of(Problem.onLoadAutofixProblem(
                        (baselines, now) -> setInternalItemTrace(baselines, now, childItem)));
            }
            return Stream.empty();
        });
    }

    public Optional<Record> getTrace(BaselinePair context, Record childItem) {
        return childItem.getTrace().flatMap(trace -> context.getParent().get(trace, this));
    }

    @Override
    public Stream<Problem> getUntracedParentProblems(BaselinePair context, Stream<Record> untracedParents) {
        // It is correct for items to exist in the parent baseline that do not
        // exist in the child baseline.
        return Stream.empty();
    }

    private BaselinePair setInternalItemTrace(BaselinePair baselines, String now, Record record) {
        Optional<Record> parentItem = Identity.getSystemOfInterest(baselines);
        Optional<Record> childItem = baselines.getChild().get(record);
        if (parentItem.isPresent() && childItem.isPresent()) {
            return baselines.setChild(
                    baselines.getChild().add(
                            childItem.get().asBuilder()
                            .setTrace(parentItem.get())
                            .build(now)));
        } else {
            return baselines;
        }
    }

    private BaselinePair removeItemFromChild(BaselinePair baselines, Record record) {
        return baselines.setChild(baselines.getChild().remove(record.getIdentifier()));
    }

    @Override
    public Stream<Problem> getUntracedChildProblems(BaselinePair context, Stream<Record> untracedChildren) {
        return untracedChildren.map(childItem -> {
            if (childItem.isExternal()) {
                return Problem.flowProblem(
                        childItem.getLongName() + " is missing from parent",
                        Optional.of((baselines, now) -> removeItemFromChild(baselines, childItem)),
                        Optional.empty()
                );
            } else {
                return Problem.onLoadAutofixProblem(
                        (baselines, now) -> setInternalItemTrace(baselines, now, childItem));
            }
        });
    }

    public static IDPath getNextItemId(Baseline baseline) {
        Optional<Integer> currentMax = find(baseline).parallel()
                .filter(item -> !item.isExternal())
                .map(item -> {
                    try {
                        return Integer.parseInt(Item.item.getShortId(item).toString());
                    } catch (NumberFormatException ex) {
                        return 0;
                    }
                })
                .collect(Collectors.maxBy(Integer::compareTo));
        int nextId;
        if (currentMax.isPresent()) {
            nextId = currentMax.get() + 1;
        } else {
            nextId = 1;
        }
        return IDPath.valueOfSegment(Integer.toString(nextId));
    }

    public static Color shiftColor(Color color) {
        // Shift color
        // Adjust hue by +/- 128 out of the 256 range
        double hueShift = Math.random() * 128 - 64;
        // Adjust saturation by +/- 30%
        double saturationMultiplier = Math.random() * .6 + .7;
        // Adjust brightness by +/- 20%
        double brightnessMultiplier = Math.random() * .4 + .8;
        double opacityMultiplier = 1;
        return color.deriveColor(
                hueShift,
                saturationMultiplier,
                brightnessMultiplier,
                opacityMultiplier);
    }

    /**
     * Create a new item.
     *
     * @param baselines The context of the creation
     * @param now The current time in 8601 format
     * @param name The name of the item
     * @return The updated baseline
     */
    @CheckReturnValue
    public static Pair<BaselinePair, Record> create(
            BaselinePair baselines, String now, String name) {
        Optional<Record> systemOfInterest = Identity.getSystemOfInterest(baselines);
        Color traceColor = systemOfInterest.map(Record::getColor).orElse(Color.LIGHTYELLOW);
        Color itemColor = shiftColor(traceColor);
        Record record = Record.create(item)
                .setLongName(name)
                .setExternal(false)
                .setTrace(systemOfInterest)
                .setColor(itemColor)
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
     * @param now The current time in ISO8610 format
     * @param record The item to flow down from the state's functional baseline
     * @return The updated baseline
     */
    @CheckReturnValue
    public BaselinePair flowDownExternal(BaselinePair baselines, String now, Record record) {
        Optional<Record> parentItem = baselines.getParent().get(record);
        Optional<Record> childItem = baselines.getChild().findByTrace(Optional.of(record.getIdentifier())).findAny();
        if (parentItem.isPresent()) {
            Map<String, String> fields = new HashMap<>(parentItem.get().getAllFields());
            // The identifier is not inherited, but instead becomes the trace
            fields.remove(Fields.identifier.name());
            fields.put(Fields.trace.name(), parentItem.get().getIdentifier());
            // The short name needs to be translated if the parent is not external
            fields.put(Fields.shortName.name(),
                    this.getIdPath(Identity.get(baselines.getParent()), parentItem.get()).toString());
            // Otherwise all fields should be the same

            if (childItem.isPresent()) {
                Record updatedChildItem = childItem.get().asBuilder()
                        .putAll(fields)
                        .setExternal(true)
                        .build(now);
                // This is an update, not a genuine flow down
                return baselines.setChild(baselines.getChild().add(updatedChildItem));
            } else {
                Record newChildItem = Record.create(item)
                        .putAll(fields)
                        .setExternal(true)
                        .build(now);
                baselines = baselines.setChild(baselines.getChild().add(newChildItem));
                // Give other types the opportunity to perform their flow down
                // operations
                return EventDispatcher.INSTANCE.dispatchFlowDownEvent(baselines, now, newChildItem);
            }
        } else {
            return baselines;
        }
    }

    /**
     * Flow an external item down from the functional baseline to the allocated
     * baseline.
     *
     * @param baselines The state to update
     * @param now The current time in ISO8610 format
     * @param record The item to flow down from the state's functional baseline
     * @return The updated baseline
     */
    @CheckReturnValue
    public BaselinePair flowUpExternal(BaselinePair baselines, String now, Record record) {
        Optional<Record> childItem = baselines.getChild().get(record);
        Optional<Record> parentItem = childItem
                .flatMap(Record::getTrace)
                .flatMap(trace -> baselines.getParent().get(trace, item));
        if (childItem.isPresent() && parentItem.isPresent()) {
            Map<String, String> fields = new HashMap<>(childItem.get().getAllFields());
            // The identifier is not inherited
            fields.remove(Fields.identifier.name());
            fields.remove(Fields.trace.name());
            fields.remove(Fields.external.name());
            // The short name needs to be translated if the parent is not external
            if (!parentItem.get().isExternal()) {
                fields.put(Fields.shortName.name(),
                        item.getIdPath(baselines.getChild(), childItem.get())
                        .getLastSegment().toString());
            }

            Record updatedParentItem = parentItem.get().asBuilder()
                    .putAll(fields)
                    .build(now);
            return baselines.setParent(baselines.getParent().add(updatedParentItem));
        } else {
            return baselines;
        }
    }

    public IDPath getIdPath(Record identity, Record item) {
        if (item.isExternal()) {
            return IDPath.valueOfDotted(item.getShortName());
        } else {
            IDPath baselineIdPath = Identity.getIdPath(identity);
            return baselineIdPath.resolveSegment(item.getShortName());
        }
    }

    public IDPath getIdPath(Baseline baseline, Record item) {
        if (item.isExternal()) {
            return IDPath.valueOfDotted(item.getShortName());
        } else {
            IDPath baselineIdPath = Identity.findAll(baseline)
                    .findAny()
                    .map(Identity::getIdPath)
                    .orElse(IDPath.empty());

            return baselineIdPath.resolveSegment(item.getShortName());
        }
    }

    public IDPath getShortId(Record item) {
        return IDPath.valueOfDotted(item.getShortName());
    }

    @CheckReturnValue
    public Record setShortId(Record item, String now, IDPath id) {
        return item.asBuilder()
                .setShortName(id.toString())
                .build(now);
    }

    public Record getView(Baseline baseline, Record item) {
        return findViews(baseline, item).findAny().get();
    }

    public Stream<Record> findViews(Baseline baseline, Record item) {
        return baseline.findReverse(item.getIdentifier(), ItemView.itemView);
    }

    @Override
    public Stream<UniqueConstraint> getUniqueConstraints() {
        return Stream.of(record -> {
            if (record.isExternal()) {
                return record.getTrace();
            } else {
                return record.getIdentifier();
            }
        });
    }

    public Record mergeDuplicates(Record left, Record right) {
        // Preserve newest
        return Record.newerOf(left, right);
    }

    public String getDisplayName(Record item) {
        return item.getLongName();
    }

    public Baseline setDisplayName(Baseline baseline, String now, Record item, String value) {
        item = item.asBuilder()
                .setLongName(value)
                .build(now);
        return baseline.add(item);
    }

    @Override
    public Record merge(BaselinePair baselines, String now, Record left, Record right) {
        return Record.newerOf(left, right);
    }
}
