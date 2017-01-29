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
package au.id.soundadvice.systemdesign.physical.entity;

import au.id.soundadvice.systemdesign.moduleapi.entity.ConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.collection.RecordConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.moduleapi.event.EventDispatcher;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;
import au.id.soundadvice.systemdesign.moduleapi.entity.UniqueConstraint;
import java.util.Comparator;

/**
 * An association between two items that implies Flows may exist between them.
 * Each pair of Items in an allocated baseline may either have a corresponding
 * interface or have no corresponding interface. The description of the
 * interface is compose primarily of the flows across it, the nature of the two
 * items, and any associated interface requirements.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public enum Interface implements Table {
    iface;

    @Override
    public String getTableName() {
        return "interface";
    }

    @Override
    public Comparator<Record> getNaturalOrdering() {
        return (a, b) -> a.getShortName().compareTo(b.getShortName());
    }

    /**
     * Return all interfaces between subsystems of the allocated baseline, as
     * well as all interfaces between subsystems and external systems.
     *
     * @param baseline The baseline to search
     * @return
     */
    public static Stream<Record> find(Baseline baseline) {
        return baseline.findByType(iface);
    }

    /**
     * Return the interfaces related to this item within the specified baseline.
     *
     * @param baseline The level of the system to search within
     * @param item The item to search
     * @return
     */
    public static Stream<Record> findForItem(Baseline baseline, Record item) {
        return baseline.findReverse(item.getIdentifier(), iface);
    }

    /**
     * Return the interface specified by the connection scope (if any).
     *
     * @param baseline The level of the system to search within
     * @param scope The scope to search for
     * @return
     */
    public static Optional<Record> get(Baseline baseline, RecordConnectionScope scope) {
        return baseline.findByScope(scope.getScope())
                .filter(record -> Interface.iface.equals(record.getType()))
                .findAny();
    }

    public RecordConnectionScope getItems(Baseline baseline, Record iface) {
        return RecordConnectionScope.resolve(baseline, iface.getConnectionScope(), Item.item).get();
    }

    public Optional<RecordConnectionScope> getTraceItems(WhyHowPair<Baseline> baselines, Record iface) {
        RecordConnectionScope childScope = getItems(baselines.getChild(), iface);
        Optional<RecordConnectionScope> parentScope = childScope.getTrace(baselines, this);
        return parentScope;
    }

    /**
     * Create a new Interface.
     *
     * @param baselines The baselines to update
     * @param now The current time in ISO8601 format
     * @param scope The item pairing to create
     * @return The updated baseline
     */
    @CheckReturnValue
    public static Pair<WhyHowPair<Baseline>, Record> connect(WhyHowPair<Baseline> baselines, String now, RecordConnectionScope scope) {
        Baseline childBaseline = baselines.getChild();
        Optional<Record> existing = get(childBaseline, scope);
        return existing
                .map(record -> new Pair<>(baselines, record))
                .orElseGet(() -> {
                    // Note that self-interfaces are allowed to support flows
                    // between functions of the same item, but are not rendered
                    Record leftItem = scope.getLeft();
                    Record rightItem = scope.getRight();
                    boolean leftIsExternal = leftItem.isExternal();
                    boolean rightIsExternal = rightItem.isExternal();
                    if (leftIsExternal && rightIsExternal) {
                        // Connection disallowed
                        return new Pair<>(baselines, null);
                    } else {
                        boolean external = leftIsExternal || rightIsExternal;
                        Optional<Record> trace = iface.getExpectedTrace(baselines, scope.getScope());
                        Record record = Record.create(iface)
                                .setConnectionScope(scope)
                                .setExternal(external)
                                .setTrace(trace)
                                .setShortName(iface.getShortName(childBaseline, scope))
                                .setLongName(iface.getLongName(childBaseline, scope))
                                .build(now);
                        WhyHowPair<Baseline> updatedBaselines = baselines.setChild(childBaseline.add(record));
                        updatedBaselines = EventDispatcher.INSTANCE.dispatchCreateEvent(updatedBaselines, now, record);
                        return new Pair<>(updatedBaselines, record);
                    }
                });
    }

    /**
     * Remove an interface.
     *
     * @param baseline The baseline to update
     * @param scope The item pairing to remove
     * @return The updated baseline
     */
    @CheckReturnValue
    public static Baseline disconnect(
            Baseline baseline, RecordConnectionScope scope) {
        Optional<Record> existing = get(baseline, scope);
        return existing
                .map(record -> baseline.remove(record.getIdentifier()))
                .orElse(baseline);
    }

    private String getShortName(Baseline baseline, RecordConnectionScope scope) {
        String leftShortName = Item.item.getIdPath(baseline, scope.getLeft()).toString();
        String rightShortName = Item.item.getIdPath(baseline, scope.getRight()).toString();
        if (leftShortName.compareTo(rightShortName) > 0) {
            // Reverse
            String tmp = leftShortName;
            leftShortName = rightShortName;
            rightShortName = tmp;
        }

        return leftShortName + ":" + rightShortName;
    }

    public String getLongName(Baseline baseline, RecordConnectionScope scope) {
        String leftShortName = scope.getLeft().getShortName();
        String rightShortName = scope.getRight().getShortName();
        String leftLongName = scope.getLeft().getLongName();
        String rightLongName = scope.getRight().getLongName();
        if (leftShortName.compareTo(rightShortName) > 0) {
            // Reverse
            {
                String tmp = leftShortName;
                leftShortName = rightShortName;
                rightShortName = tmp;
            }
            {
                String tmp = leftLongName;
                leftLongName = rightLongName;
                rightLongName = tmp;
            }
        }

        return leftShortName + ':' + rightShortName + ' '
                + leftLongName + ':' + rightLongName;
    }

    @Override
    public Stream<Problem> getTraceProblems(WhyHowPair<Baseline> context, Record traceParent, Stream<Record> traceChildren) {
        return traceChildren
                .flatMap(traceChild -> {
                    ConnectionScope childScope = traceChild.getConnectionScope();
                    Optional<RecordID> expectedTrace = getExpectedTrace(context, childScope)
                            .map(Record::getIdentifier);
                    if (!expectedTrace.isPresent() || !expectedTrace.get().equals(traceParent.getIdentifier())) {
                        return Stream.of(Problem.onLoadAutofixProblem(
                                (baselines, now) -> fixInterfaceTrace(baselines, now, traceChild)));
                    }
                    return Stream.empty();
                });
    }

    @Override
    public Stream<Problem> getUntracedParentProblems(WhyHowPair<Baseline> context, Stream<Record> untracedParents) {
        Optional<Record> systemOfInterest = Identity.getSystemOfInterest(context);
        if (systemOfInterest.isPresent()) {
            // The child is not the top level baseline
            return untracedParents
                    .flatMap(untracedParent -> {
                        RecordConnectionScope scope = iface.getItems(context.getParent(), untracedParent);
                        if (scope.hasEnd(systemOfInterest.get())) {
                            // The untraced parent is connected to the system of interest
                            Record otherEndItem = scope.otherEnd(systemOfInterest.get());
                            Optional<Record> otherEndChildItem
                                    = context.getChild().findByTrace(Optional.of(otherEndItem.getIdentifier()))
                                    .findAny();
                            if (!otherEndChildItem.isPresent()) {
                                // The other end does not exist in the child baseline as an external item
                                return Stream.of(Problem.flowProblem(
                                        otherEndItem.getLongName() + " is missing",
                                        Optional.of((baselines, now) -> Item.item.flowDownExternal(baselines, now, otherEndItem)),
                                        Optional.of((baselines, now) -> baselines.setParent(disconnect(baselines.getChild(), scope)))));
                            }
                        }
                        return Stream.empty();
                    });
        } else {
            return Stream.empty();
        }
    }

    private Optional<Record> getExpectedTrace(WhyHowPair<Baseline> baselines, ConnectionScope childScope) {
        Baseline child = baselines.getChild();
        Optional<RecordConnectionScope> childItems = RecordConnectionScope.resolve(child, childScope, Item.item);
        Optional<RecordConnectionScope> parentItems = childItems.flatMap(items -> items.getTrace(baselines, Item.item));
        if (parentItems.isPresent()) {
            if (parentItems.get().isSelfConnection()) {
                // Internal interface (includes top-level interfaces)
                return Optional.of(parentItems.get().getLeft());
            } else {
                // External interface
                return get(baselines.getParent(), parentItems.get());
            }
        } else {
            // No parent interface could be found
            return Optional.empty();
        }
    }

    private WhyHowPair<Baseline> fixInterfaceTrace(WhyHowPair<Baseline> baselines, String now, Record record) {
        Optional<Record> iface = baselines.getChild().get(record);
        if (iface.isPresent()) {
            ConnectionScope scope = iface.get().getConnectionScope();
            Optional<Record> trace = getExpectedTrace(baselines, scope);
            return baselines.setChild(baselines.getChild().add(
                    iface.get().asBuilder()
                    .setTrace(trace)
                    .build(now)));
        }
        return baselines;
    }

    private WhyHowPair<Baseline> flowExternalInterfaceUp(WhyHowPair<Baseline> baselines, String now, Record record) {
        Baseline child = baselines.getChild();
        Optional<Record> iface = child.get(record);
        if (!iface.isPresent()) {
            return baselines;
        }
        Optional<RecordConnectionScope> parentItems = getTraceItems(baselines, iface.get());

        WhyHowPair<Baseline> result = baselines;
        if (parentItems.isPresent() && !parentItems.get().isSelfConnection()) {
            Optional<Record> existing = get(baselines.getParent(), parentItems.get());
            if (!existing.isPresent()) {
                // Flow the interface up in its simplest form. We'll have to 
                // rely on the next level up being opened and corrective code executed
                // to fully populate it.
                Record.Builder builder = iface.get().asBuilder();
                Optional<RecordID> trace = record.getTrace();
                if (trace.isPresent()) {
                    // Restore the previously known identifier
                    builder = builder.setIdentifier(trace.get());
                } else {
                    builder = builder.newIdentifier();
                }
                Record parentInterface = builder
                        .removeTrace()
                        .removeReferences()
                        .setShortName(getShortName(baselines.getParent(), parentItems.get()))
                        .setLongName(getLongName(baselines.getParent(), parentItems.get()))
                        .build(now);
                result = baselines.setParent(baselines.getParent().add(parentInterface));
            }
        }
        return result;
    }

    @Override
    public Stream<Problem> getUntracedChildProblems(WhyHowPair<Baseline> context, Stream<Record> untracedChildren) {
        Optional<Record> systemOfInterest = Identity.getSystemOfInterest(context);
        return untracedChildren.flatMap(record -> {
            if (record.isExternal()) {
                // External interfaces should be traced to their corresponding parent
                return Stream.of(Problem.flowProblem(
                        record.getLongName() + " is missing from parent",
                        Optional.of((baselines, now) -> baselines.setChild(disconnect(baselines.getChild(), iface.getItems(baselines.getChild(), record)))),
                        Optional.of((baselines, now) -> flowExternalInterfaceUp(baselines, now, record))));
            } else if (systemOfInterest.isPresent()) {
                // internal interfaces should trace to the system of interest
                return Stream.of(Problem.onLoadAutofixProblem(
                        (baselines, now) -> fixInterfaceTrace(baselines, now, record)));
            } else {
                // Top-level interfaces don't trace anywhere
                return Stream.empty();
            }
        });
    }

    @Override
    public Stream<UniqueConstraint> getUniqueConstraints() {
        return Stream.of(Record::getConnectionScope);
    }

    @Override
    public Record merge(WhyHowPair<Baseline> baselines, String now, Record left, Record right) {
        return Record.newerOf(left, right);
    }
}
