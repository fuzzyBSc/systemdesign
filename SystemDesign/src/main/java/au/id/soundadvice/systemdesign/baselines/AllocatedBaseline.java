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
package au.id.soundadvice.systemdesign.baselines;

import au.id.soundadvice.systemdesign.beans.Direction;
import au.id.soundadvice.systemdesign.beans.FlowBean;
import au.id.soundadvice.systemdesign.beans.FunctionBean;
import au.id.soundadvice.systemdesign.beans.FunctionViewBean;
import au.id.soundadvice.systemdesign.beans.HazardBean;
import au.id.soundadvice.systemdesign.beans.IdentityBean;
import au.id.soundadvice.systemdesign.beans.InterfaceBean;
import au.id.soundadvice.systemdesign.beans.ItemBean;
import au.id.soundadvice.systemdesign.beans.RequirementBean;
import au.id.soundadvice.systemdesign.files.BeanFile;
import au.id.soundadvice.systemdesign.files.BeanReader;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.files.SaveTransaction;
import au.id.soundadvice.systemdesign.model.DirectedPair;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.FunctionView;
import au.id.soundadvice.systemdesign.model.Hazard;
import au.id.soundadvice.systemdesign.model.IDSegment;
import au.id.soundadvice.systemdesign.model.Identity;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.model.Requirement;
import au.id.soundadvice.systemdesign.model.UndirectedPair;
import au.id.soundadvice.systemdesign.relation.Relation;
import au.id.soundadvice.systemdesign.relation.RelationContext;
import au.id.soundadvice.systemdesign.relation.RelationStore;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class AllocatedBaseline {

    @Override
    public String toString() {
        return getIdentity().toString();
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + Objects.hashCode(getIdentity());
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
        final AllocatedBaseline other = (AllocatedBaseline) obj;
        if (!Objects.equals(this.store, other.store)) {
            return false;
        }
        return true;
    }

    public Identity getIdentity() {
        return store.getByClass(Identity.class).iterator().next();
    }

    public RelationStore getStore() {
        return store;
    }

    public Stream<Item> getItems() {
        return store.getByClass(Item.class);
    }

    public Stream<Interface> getInterfaces() {
        return store.getByClass(Interface.class);
    }

    public Stream<Function> getFunctions() {
        return store.getByClass(Function.class);
    }

    public Stream<FunctionView> getFunctionViews() {
        return store.getByClass(FunctionView.class);
    }

    public Stream<Flow> getFlows() {
        return store.getByClass(Flow.class);
    }

    public Stream<Hazard> getHazards() {
        return store.getByClass(Hazard.class);
    }

    public Stream<Requirement> getRequirements() {
        return store.getByClass(Requirement.class);
    }

    public static AllocatedBaseline create(Identity identity) {
        return new AllocatedBaseline(RelationStore.empty().put(identity));
    }

    public static AllocatedBaseline load(Directory directory) throws IOException {
        Optional<Identity> identity = directory.getIdentity();
        identity.orElseThrow(() -> new IOException("No Item found in directory"));

        List<Relation> relations = new ArrayList<>();

        relations.add(identity.get());

        if (Files.exists(directory.getItems())) {
            try (BeanReader<ItemBean> reader = BeanReader.forPath(ItemBean.class, directory.getItems())) {
                for (;;) {
                    Optional<ItemBean> bean = reader.read();
                    if (bean.isPresent()) {
                        relations.add(new Item(identity.get().getUuid(), bean.get()));
                    } else {
                        break;
                    }
                }
            }
        }

        if (Files.exists(directory.getInterfaces())) {
            try (BeanReader<InterfaceBean> reader = BeanReader.forPath(InterfaceBean.class, directory.getInterfaces())) {
                for (;;) {
                    Optional<InterfaceBean> bean = reader.read();
                    if (bean.isPresent()) {
                        relations.add(new Interface(bean.get()));
                    } else {
                        break;
                    }
                }
            }
        }

        if (Files.exists(directory.getFunctions())) {
            try (BeanReader<FunctionBean> reader = BeanReader.forPath(FunctionBean.class, directory.getFunctions())) {
                for (;;) {
                    Optional<FunctionBean> bean = reader.read();
                    if (bean.isPresent()) {
                        relations.add(new Function(bean.get()));
                    } else {
                        break;
                    }
                }
            }
        }

        if (Files.exists(directory.getFunctionViews())) {
            try (BeanReader<FunctionViewBean> reader = BeanReader.forPath(FunctionViewBean.class, directory.getFunctionViews())) {
                for (;;) {
                    Optional<FunctionViewBean> bean = reader.read();
                    if (bean.isPresent()) {
                        relations.add(new FunctionView(bean.get()));
                    } else {
                        break;
                    }
                }
            }
        }

        if (Files.exists(directory.getFlows())) {
            try (BeanReader<FlowBean> reader = BeanReader.forPath(FlowBean.class, directory.getFlows())) {
                for (;;) {
                    Optional<FlowBean> bean = reader.read();
                    if (bean.isPresent()) {
                        relations.add(new Flow(bean.get()));
                    } else {
                        break;
                    }
                }
            }
        }

        if (Files.exists(directory.getHazards())) {
            try (BeanReader<HazardBean> reader = BeanReader.forPath(HazardBean.class, directory.getHazards())) {
                for (;;) {
                    Optional<HazardBean> bean = reader.read();
                    if (bean.isPresent()) {
                        relations.add(new Hazard(bean.get()));
                    } else {
                        break;
                    }
                }
            }
        }

        if (Files.exists(directory.getRequirements())) {
            try (BeanReader<RequirementBean> reader = BeanReader.forPath(RequirementBean.class, directory.getRequirements())) {
                for (;;) {
                    Optional<RequirementBean> bean = reader.read();
                    if (bean.isPresent()) {
                        relations.add(new Requirement(bean.get()));
                    } else {
                        break;
                    }
                }
            }
        }

        return new AllocatedBaseline(RelationStore.valueOf(relations.stream()));
    }

    public void saveTo(SaveTransaction transaction, Directory directory) throws IOException {
        if (store.size() > 1 || Files.isDirectory(directory.getPath())) {
            Files.createDirectories(directory.getPath());
            RelationContext context = store;
            BeanFile.saveModel(transaction, context, directory.getIdentityFile(), IdentityBean.class, store.getByClass(Identity.class));
            BeanFile.saveModel(transaction, context, directory.getItems(), ItemBean.class, store.getByClass(Item.class));
            BeanFile.saveModel(transaction, context, directory.getInterfaces(), InterfaceBean.class, store.getByClass(Interface.class));
            BeanFile.saveModel(transaction, context, directory.getFunctions(), FunctionBean.class, store.getByClass(Function.class));
            BeanFile.saveModel(transaction, context, directory.getFunctionViews(), FunctionViewBean.class, store.getByClass(FunctionView.class));
            BeanFile.saveModel(transaction, context, directory.getFlows(), FlowBean.class, store.getByClass(Flow.class));
            BeanFile.saveModel(transaction, context, directory.getHazards(), HazardBean.class, store.getByClass(Hazard.class));
            BeanFile.saveModel(transaction, context, directory.getRequirements(), RequirementBean.class, store.getByClass(Requirement.class));
        }
    }

    private final RelationStore store;

    private AllocatedBaseline(RelationStore store) {
        this.store = store;
    }

    @CheckReturnValue
    public AllocatedBaseline setIdentity(Identity id) {
        RelationStore tmpStore = store
                .remove(getIdentity().getUuid())
                .put(id);
        return new AllocatedBaseline(tmpStore);
    }

    @CheckReturnValue
    public AllocatedBaseline add(Relation item) {
        return new AllocatedBaseline(store.put(item));
    }

    @CheckReturnValue
    public AllocatedBaseline remove(UUID key) {
        return new AllocatedBaseline(store.remove(key));
    }

    public boolean hasRelation(Relation relation) {
        return relation != null
                && getStore().get(relation.getUuid(), relation.getClass()).isPresent();
    }

    public IDSegment getNextItemId() {
        Optional<Integer> currentMax = getItems().parallel()
                .filter(item -> !item.isExternal())
                .map(item -> {
                    try {
                        return Integer.parseInt(item.getShortId().toString());
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
        return new IDSegment(Integer.toString(nextId));
    }

    @CheckReturnValue
    public AllocatedBaseline removeAll(Stream<UUID> toRemove) {
        return new AllocatedBaseline(store.removeAll(toRemove));
    }

    @CheckReturnValue
    public Pair<Interface, AllocatedBaseline> addInterface(Item left, Item right) {
        UndirectedPair scope = new UndirectedPair(left.getUuid(), right.getUuid());
        Optional<Interface> existing = store.getReverse(scope.getLeft(), Interface.class).parallel()
                .filter(iface
                        -> scope.getLeft().equals(iface.getLeft().getUuid())
                        && scope.getRight().equals(iface.getRight().getUuid())
                )
                .findAny();
        if (existing.isPresent()) {
            return new Pair<>(existing.get(), this);
        } else {
            Interface newInterface = Interface.createNew(scope);
            return new Pair<>(newInterface, this.add(newInterface));
        }
    }

    @CheckReturnValue
    public Pair<Flow, AllocatedBaseline> addFlow(
            Function left, Function right, String flowType, Direction direction) {
        Item leftItem = left.getItem().getTarget(store);
        Item rightItem = right.getItem().getTarget(store);
        Pair<Interface, AllocatedBaseline> tmp = addInterface(leftItem, rightItem);

        Optional<Flow> existing = Flow.findExisting(
                tmp.getValue().store,
                new UndirectedPair(left.getUuid(), right.getUuid()),
                flowType);
        if (existing.isPresent()) {
            Flow flow = existing.get();
            Direction current = flow.getDirectionFrom(left);
            Direction updated = current.add(direction);
            if (current.equals(updated)) {
                // Nothing changed
                return new Pair<>(flow, tmp.getValue());
            } else {
                flow = flow.setDirectionFrom(left, updated);
                return new Pair<>(flow, tmp.getValue().add(flow));
            }
        } else {
            Flow flow = Flow.createNew(
                    tmp.getKey().getUuid(),
                    new DirectedPair(left.getUuid(), right.getUuid(), direction),
                    flowType);
            return new Pair<>(flow, tmp.getValue().add(flow));
        }
    }

    @CheckReturnValue
    public AllocatedBaseline removeFlow(Function left, Function right, String type, Direction direction) {
        Optional<Flow> existing = Flow.findExisting(
                store, new UndirectedPair(left.getUuid(), right.getUuid()), type);
        if (existing.isPresent()) {
            Flow flow = existing.get();
            Direction current = flow.getDirectionFrom(left);
            Direction remaining = current.remove(direction);
            if (current.equals(remaining)) {
                // No change
                return this;
            } else if (remaining == Direction.None) {
                // Remove the flow completely
                return remove(flow.getUuid());
            } else {
                // Update the flow
                return add(flow.setDirectionFrom(left, remaining));
            }
        } else {
            return this;
        }
    }
}
