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
package au.id.soundadvice.systemdesign.logical;

import au.id.soundadvice.systemdesign.logical.beans.FunctionBean;
import au.id.soundadvice.systemdesign.moduleapi.relation.Reference;
import au.id.soundadvice.systemdesign.moduleapi.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.physical.Item;
import au.id.soundadvice.systemdesign.moduleapi.UndoState;
import au.id.soundadvice.systemdesign.physical.Identity;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javafx.geometry.Point2D;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;

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
public class Function implements Relation {

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 83 * hash + Objects.hashCode(this.uuid);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Function other = (Function) obj;
        if (!Objects.equals(this.uuid, other.uuid)) {
            return false;
        }
        if (!Objects.equals(this.item, other.item)) {
            return false;
        }
        if (!Objects.equals(this.trace, other.trace)) {
            return false;
        }
        if (this.external != other.external) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return true;
    }

    public boolean isConsistent(Function other) {
        if (!Objects.equals(this.uuid, other.uuid)) {
            return false;
        }
        if (!Objects.equals(this.item, other.item)) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return true;
    }

    @CheckReturnValue
    public Pair<Relations, Function> makeConsistent(Relations baseline, Function other) {
        Function result = new Function(
                other.getUuid(),
                other.getItem().getUuid(), trace,
                other.name, external);
        return new Pair<>(baseline.add(result), result);
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Return all of the functions allocated to any subsystems of the baseline,
     * as well as external functions.
     *
     * @param baseline The baseline to search
     * @return
     */
    public static Stream<Function> find(Relations baseline) {
        return baseline.findByClass(Function.class);
    }

    /**
     * Return the list of functions that this item implements directly.
     *
     * @param baseline This item's baseline
     * @param item The item to search
     * @return
     */
    public static Stream<Function> findOwnedFunctions(Relations baseline, Item item) {
        return baseline.findReverse(item.getUuid(), Function.class);
    }

    /**
     * Create a new Function.
     *
     * @param baseline The baseline to update
     * @param item The item that implements this function
     * @param trace If a functional baseline exists for this allocated baseline
     * then the functional baseline Function this function traces to. Otherwise,
     * Optional.empty()
     * @param name The name of the function
     * @param origin The location for the function on the screen
     * @return The updated baseline
     */
    @CheckReturnValue
    public static Pair<Relations, Function> create(
            Relations baseline, Item item, Optional<Function> trace, String name, Point2D origin) {
        Optional<UUID> traceUUID = trace.map(Function::getUuid);
        Function function = new Function(
                UUID.randomUUID(),
                item.getUuid(), traceUUID,
                name, false);
        baseline = baseline.add(function);
        // Also add the coresponding view
        return new Pair<>(FunctionView.create(baseline, function, trace, origin)
                .getKey(), function);
    }

    public static Stream<Function> getSystemFunctionsForExternalFunction(
            UndoState state, Function external) {
        Relations functional = state.getFunctional();
        Optional<Item> systemOfInterest = Identity.getSystemOfInterest(state);
        if (systemOfInterest.isPresent()
                && !external.item.getUuid().equals(systemOfInterest.get().getUuid())) {
            return external.findFlows(functional)
                    .filter(flow -> flow.hasEnd(functional, systemOfInterest.get()))
                    .map(flow -> flow.otherEnd(functional, external))
                    .distinct();
        } else {
            return Stream.empty();
        }
    }

    /**
     * Return a Stream of functional baseline functions in whose drawings this
     * allocated baseline function should appear
     *
     * @param state The state to search within
     * @return The functional baseline function associated with each drawing, or
     * Optional.empty() for the unallocated functions drawing.
     */
    public Stream<Optional<Function>> getExpectedDrawings(UndoState state) {
        Relations functional = state.getFunctional();
        Relations allocated = state.getAllocated();

        Stream<Optional<Function>> result;
        if (external) {
            /*
             * We should appear on each diagram that we have a flow with in the
             * functional baseline.
             */
            Optional<Item> systemOfInterest = Identity.getSystemOfInterest(state);
            Optional<Function> functionInstance = functional.get(uuid, Function.class);
            if (systemOfInterest.isPresent() && functionInstance.isPresent()) {
                result = functionInstance.get().findFlows(functional)
                        .map(flow -> {
                            Function otherEnd = flow.otherEnd(
                                    functional, functionInstance.get());
                            return Optional.of(otherEnd);
                        })
                        .filter(otherEnd -> {
                            Item otherEndItem = otherEnd.get().getItem(functional);
                            return otherEndItem.equals(systemOfInterest.get());
                        });
            } else {
                result = Stream.empty();
            }
        } else {
            /*
             * We should appear on the diagram for our trace.
             */
            result = Stream.of(getTrace(functional));
        }

        /*
         * We should appear on any diagram for which we have a flow to a
         * function that traces to that diagram.
         */
        result = Stream.concat(result, findFlows(allocated)
                .flatMap(flow -> {
                    // Find the other end of the flow
                    Function otherEnd = flow.otherEnd(allocated, this);
                    // Find its trace
                    return otherEnd.external
                            ? Stream.empty()
                            : Stream.of(otherEnd.getTrace(functional));
                })
        );
        return result.distinct();
    }

    /**
     * Flow an external item down from the functional baseline to the allocated
     * baseline.
     *
     * @param state The state to update
     * @param external The item to flow down from the state's functional
     * baseline
     * @return The updated baseline
     */
    @CheckReturnValue
    public static Pair<UndoState, Function> flowDownExternal(
            UndoState state, Function external) {
        Relations functional = state.getFunctional();
        Relations allocated = state.getAllocated();
        Item item = external.getItem().getTarget(functional);
        Function newExternal = new Function(
                external.uuid,
                item.getUuid(),
                Optional.empty(),
                external.name, true);
        allocated = allocated.add(newExternal);
        // Also add the coresponding views
        Iterator<Function> it = getSystemFunctionsForExternalFunction(state, external).iterator();
        // Pick a sample view to copy
        Optional<FunctionView> sampleView = external.findViews(functional).findAny();
        while (it.hasNext()) {
            Function systemFunction = it.next();
            /*
             * Create one view for each system function this external function
             * has flows with
             */
            allocated = FunctionView.create(
                    allocated, newExternal, Optional.of(systemFunction),
                    sampleView.map(FunctionView::getOrigin).orElse(FunctionView.DEFAULT_ORIGIN))
                    .getKey();
        }
        return state.setAllocated(allocated).and(newExternal);
    }

    /**
     * Remove an function from a baseline.
     *
     * @param baseline The baseline to update
     * @return The updated baseline
     */
    @CheckReturnValue
    public Relations removeFrom(Relations baseline) {
        return baseline.remove(uuid);
    }

    public Stream<FunctionView> findViews(Relations baseline) {
        return baseline.findReverse(uuid, FunctionView.class);
    }

    public Stream<Flow> findFlows(Relations baseline) {
        return baseline.findReverse(uuid, Flow.class);
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public Reference<Function, Item> getItem() {
        return item;
    }

    public Item getItem(Relations baseline) {
        return item.getTarget(baseline);
    }

    public Optional<Function> getTrace(Relations functionalBaseline) {
        if (external) {
            return functionalBaseline.get(uuid, Function.class);
        } else {
            return this.trace.flatMap(
                    traceUUID -> functionalBaseline.get(traceUUID, Function.class));
        }
    }

    public Stream<Function> findDrawings(
            Relations functional, Relations allocated) {
        return findViews(allocated)
                .flatMap(functionView -> {
                    return functionView.getDrawing(functional)
                            .map(Stream::of).orElse(Stream.empty());
                });
    }

    public boolean isExternal() {
        return external;
    }

    public String getName() {
        return name;
    }

    public Function(FunctionBean bean) {
        this.uuid = bean.getUuid();
        this.trace = Optional.ofNullable(bean.getTrace());
        this.external = bean.isExternal();
        this.item = new Reference<>(this, bean.getItem(), Item.class);
        this.name = bean.getName();
    }

    private Function(UUID uuid, UUID item, Optional<UUID> trace, String name, boolean external) {
        this.uuid = uuid;
        this.item = new Reference<>(this, item, Item.class);
        this.trace = trace;
        this.external = external;
        this.name = name;
    }

    private final UUID uuid;
    private final Reference<Function, Item> item;
    private final Optional<UUID> trace;
    private final boolean external;
    private final String name;

    public FunctionBean toBean(Relations baseline) {
        return new FunctionBean(
                uuid, item.getUuid(), getDisplayName(baseline), trace, external, name);
    }

    public String getDisplayName(Relations baseline) {
        return getDisplayName(item.getTarget(baseline));
    }

    public String getDisplayName(Item item) {
        StringBuilder builder = new StringBuilder();
        builder.append(name);
        builder.append(" (");
        builder.append(item.getDisplayName());
        builder.append(')');
        return builder.toString();
    }
    private static final ReferenceFinder<Function> FINDER
            = new ReferenceFinder<>(Function.class);

    @Override
    public Stream<Reference> getReferences() {
        return FINDER.getReferences(this);
    }

    @CheckReturnValue
    public Pair<Relations, Function> setName(Relations baseline, String name) {
        if (this.name.equals(name)) {
            return new Pair<>(baseline, this);
        } else {
            Function result = new Function(uuid, item.getUuid(), trace, name, external);
            return new Pair<>(baseline.add(result), result);
        }
    }

    @CheckReturnValue
    public Pair<Relations, Function> setTrace(Relations baseline, Function newTraceFunction) {
        if (external) {
            // External functions don't have traces, just views
            return new Pair<>(baseline, this);
        }
        if (trace.isPresent() && trace.get().equals(newTraceFunction.getUuid())) {
            // No change
            return new Pair<>(baseline, this);
        }
        // Replace ourselves within the baseline
        Function function = new Function(
                uuid,
                item.getUuid(), Optional.of(newTraceFunction.getUuid()),
                name, external);
        baseline = baseline.add(function);
        /*
         * We leave the old view behind to allow for manual deletion. Now ensure
         * there is a view in the new drawing
         */
        baseline = FunctionView.create(baseline, function,
                Optional.of(newTraceFunction),
                FunctionView.DEFAULT_ORIGIN).getKey();
        return new Pair<>(baseline, function);
    }

    public boolean isTracedTo(Function parentFunction) {
        return trace.isPresent() && trace.get().equals(parentFunction.uuid);
    }
}
