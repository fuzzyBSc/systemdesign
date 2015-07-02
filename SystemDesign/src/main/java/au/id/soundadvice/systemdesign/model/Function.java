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
package au.id.soundadvice.systemdesign.model;

import au.id.soundadvice.systemdesign.beans.BeanFactory;
import au.id.soundadvice.systemdesign.beans.FunctionBean;
import au.id.soundadvice.systemdesign.fxml.UniqueName;
import au.id.soundadvice.systemdesign.model.Baseline.BaselineAnd;
import au.id.soundadvice.systemdesign.relation.Reference;
import au.id.soundadvice.systemdesign.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.relation.Relation;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javafx.geometry.Point2D;
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
public class Function implements BeanFactory<Baseline, FunctionBean>, Relation {

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
    public BaselineAnd<Function> makeConsistent(Baseline baseline, Function other) {
        Function result = new Function(
                other.getUuid(),
                other.getItem().getUuid(), trace,
                other.name, external);
        return baseline.add(result).and(result);
    }

    @Override
    public String toString() {
        return name;
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
    public static BaselineAnd<Function> create(
            Baseline baseline, Item item, Optional<Function> trace, String name, Point2D origin) {
        Optional<UUID> traceUUID = trace.map(Function::getUuid);
        Function function = new Function(
                UUID.randomUUID(),
                item.getUuid(), traceUUID,
                name, false);
        baseline = baseline.add(function);
        // Also add the coresponding view
        return FunctionView.create(baseline, function, trace, origin)
                .getBaseline().and(function);
    }

    /**
     * Create a new Function with no trace and a default name.
     *
     * @param baseline The baseline to update
     * @param item The item that implements this function
     * @return The updated baseline
     */
    @CheckReturnValue
    public static BaselineAnd<Function> create(Baseline baseline, Item item) {
        String name = baseline.getFunctions().parallel()
                .map(Function::getName)
                .collect(new UniqueName("New Function"));
        return Function.create(
                baseline, item, Optional.empty(), name, FunctionView.defaultOrigin);
    }

    public static Stream<Function> getSystemFunctionsForExternalFunction(
            UndoState state, Function external) {
        Baseline functional = state.getFunctional();
        Optional<Item> systemOfInterest = state.getSystemOfInterest();
        if (systemOfInterest.isPresent()
                && !external.item.getUuid().equals(systemOfInterest.get().getUuid())) {
            return external.getFlows(functional)
                    .filter(flow -> flow.hasEnd(functional, systemOfInterest.get()))
                    .map(flow -> flow.otherEnd(functional, external))
                    .distinct();
        } else {
            return Stream.empty();
        }
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
    public static UndoState.StateAnd<Function> flowDownExternal(
            UndoState state, Function external) {
        Baseline functional = state.getFunctional();
        Baseline allocated = state.getAllocated();
        Item item = external.getItem().getTarget(functional.getStore());
        Function newExternal = new Function(
                external.uuid,
                item.getUuid(),
                Optional.empty(),
                external.name, true);
        allocated = allocated.add(newExternal);
        // Also add the coresponding views
        Iterator<Function> it = getSystemFunctionsForExternalFunction(state, external).iterator();
        // Pick a sample view to copy
        Optional<FunctionView> sampleView = external.getViews(functional).findAny();
        while (it.hasNext()) {
            Function systemFunction = it.next();
            /*
             * Create one view for each system function this external function
             * has flows with
             */
            allocated = FunctionView.create(
                    allocated, newExternal, Optional.of(systemFunction),
                    sampleView.map(FunctionView::getOrigin).orElse(FunctionView.defaultOrigin))
                    .getBaseline();
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
    public Baseline removeFrom(Baseline baseline) {
        return baseline.remove(uuid);
    }

    public Stream<FunctionView> getViews(Baseline baseline) {
        return baseline.getReverse(uuid, FunctionView.class);
    }

    public Stream<Flow> getFlows(Baseline baseline) {
        return baseline.getReverse(uuid, Flow.class);
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public Reference<Function, Item> getItem() {
        return item;
    }

    public Optional<Function> getTrace(Baseline functionalBaseline) {
        return this.trace.flatMap(traceUUID -> functionalBaseline.get(traceUUID, Function.class));
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

    @Override
    public FunctionBean toBean(Baseline baseline) {
        return new FunctionBean(
                uuid, item.getUuid(), getDisplayName(baseline), trace, external, name);
    }

    public String getDisplayName(Baseline baseline) {
        return getDisplayName(item.getTarget(baseline.getStore()));
    }

    public String getDisplayName(Item item) {
        StringBuilder builder = new StringBuilder();
        builder.append(name);
        builder.append(" (");
        builder.append(item.getDisplayName());
        builder.append(')');
        return builder.toString();
    }
    private static final ReferenceFinder<Function> finder
            = new ReferenceFinder<>(Function.class);

    @Override
    public Stream<Reference> getReferences() {
        return finder.getReferences(this);
    }

    @CheckReturnValue
    public BaselineAnd<Function> setName(Baseline baseline, String name) {
        if (this.name.equals(name)) {
            return baseline.and(this);
        } else {
            Function result = new Function(uuid, item.getUuid(), trace, name, external);
            return baseline.add(result).and(result);
        }
    }

    @CheckReturnValue
    public BaselineAnd<Function> setTrace(Baseline baseline, Function traceFunction) {
        if (external) {
            // External functions don't have traces, just views
            return baseline.and(this);
        }
        {
            Iterator<Flow> toRemove = getFlows(baseline).iterator();
            while (toRemove.hasNext()) {
                Flow flow = toRemove.next();
                baseline = flow.removeFrom(baseline);
            }
        }
        Optional<FunctionView> sampleView = Optional.empty();
        {
            Iterator<FunctionView> toRemove = getViews(baseline).iterator();
            while (toRemove.hasNext()) {
                FunctionView view = toRemove.next();
                // Preserve as sample
                sampleView = Optional.of(view);
                baseline = view.removeFrom(baseline);
            }
        }
        Function function = new Function(
                uuid,
                item.getUuid(), Optional.of(traceFunction.uuid),
                name, external);
        baseline = baseline.add(function);
        // Also add the coresponding view
        baseline = FunctionView.create(
                baseline, function, Optional.of(traceFunction),
                sampleView.map(FunctionView::getOrigin).orElse(FunctionView.defaultOrigin)
        ).getBaseline();
        return baseline.and(function);
    }

    public Function asExternal(UUID allocateToParentFunction) {
        return new Function(
                uuid, item.getUuid(),
                Optional.of(allocateToParentFunction),
                name, true);
    }
}
