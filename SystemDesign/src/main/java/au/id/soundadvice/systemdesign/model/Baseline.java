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

import au.id.soundadvice.systemdesign.beans.BudgetAllocationBean;
import au.id.soundadvice.systemdesign.beans.BudgetBean;
import au.id.soundadvice.systemdesign.beans.FlowBean;
import au.id.soundadvice.systemdesign.beans.FlowTypeBean;
import au.id.soundadvice.systemdesign.beans.FunctionBean;
import au.id.soundadvice.systemdesign.beans.FunctionViewBean;
import au.id.soundadvice.systemdesign.beans.IdentityBean;
import au.id.soundadvice.systemdesign.beans.InterfaceBean;
import au.id.soundadvice.systemdesign.beans.ItemBean;
import au.id.soundadvice.systemdesign.beans.ItemViewBean;
import au.id.soundadvice.systemdesign.files.BeanFile;
import au.id.soundadvice.systemdesign.files.BeanReader;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.files.SaveTransaction;
import au.id.soundadvice.systemdesign.relation.Relation;
import au.id.soundadvice.systemdesign.relation.RelationContext;
import au.id.soundadvice.systemdesign.relation.RelationStore;
import au.id.soundadvice.systemdesign.versioning.VersionControl;
import au.id.soundadvice.systemdesign.versioning.VersionInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;

/**
 * The allocated baseline is the decomposition of the system of interest onto
 * its subsystems. At the top level of a model it may be that an allocated
 * baseline exists for the model as a whole without a parent functional
 * baseline. However apart from this top level throughout the model each
 * allocated baseline is intended to be consistent with it's context's baseline
 * known as the functional baseline.
 *
 * Each allocated baseline consists of all of the physical and logical parts a
 * system's decomposition onto subsystems. These include identification of the
 * subsystems themselves (known in this model as Items) as well as how those
 * subsystems are connected and what each subsystem does.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Baseline {

    @Override
    public String toString() {
        return Identity.find(this).toString();
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + Objects.hashCode(Identity.find(this));
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
        final Baseline other = (Baseline) obj;
        if (!Objects.equals(this.store, other.store)) {
            return false;
        }
        return true;
    }

    public Optional<Item> getItemForIdentity(Identity identity) {
        return store.get(identity.getUuid(), Item.class);
    }

    /**
     * A simple tuple class for returning an updated Baseline along with an
     * updated Relation class.
     *
     * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
     * @param <T> The type of the relation
     */
    public class BaselineAnd<T> {

        private BaselineAnd(Baseline baseline, T relation) {
            this.baseline = baseline;
            this.relation = relation;
        }

        public Baseline getBaseline() {
            return baseline;
        }

        public T getRelation() {
            return relation;
        }

        private final Baseline baseline;
        private final T relation;
    }

    public <T> BaselineAnd<T> and(T relation) {
        return new BaselineAnd(this, relation);
    }

    private static final Baseline empty = create(Identity.create());

    public static Baseline empty() {
        return empty;
    }

    /**
     * Create an AllocatedBaseline in memory corresponding to the specified
     * identity.
     *
     * @param identity The identity of the system of interest for this
     * AllocatedBaseline
     * @return The allocated baseline
     */
    public static Baseline create(Identity identity) {
        return new Baseline(RelationStore.empty().add(identity));
    }

    /**
     * Load an allocated baseline from the nominated directory.
     *
     * @param directory The directory to load from
     * @param versionControl The version control system to search
     * @param version The version to read
     * @return
     * @throws java.io.IOException
     */
    public static Baseline load(
            Directory directory,
            VersionControl versionControl, Optional<VersionInfo> version) throws IOException {
        Optional<Identity> identity = directory.getIdentity();
        identity.orElseThrow(() -> new IOException("No Item found in directory"));

        List<Relation> relations = new ArrayList<>();

        relations.add(identity.get());

        {
            Optional<BeanReader<ItemBean>> optional = BeanReader.forPath(
                    ItemBean.class, directory.getItems(), versionControl, version);
            if (optional.isPresent()) {
                try (BeanReader<ItemBean> reader = optional.get()) {
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
        }

        {
            Optional<BeanReader<ItemViewBean>> optional = BeanReader.forPath(
                    ItemViewBean.class, directory.getItemViews(), versionControl, version);
            if (optional.isPresent()) {
                try (BeanReader<ItemViewBean> reader = optional.get()) {
                    for (;;) {
                        Optional<ItemViewBean> bean = reader.read();
                        if (bean.isPresent()) {
                            relations.add(new ItemView(bean.get()));
                        } else {
                            break;
                        }
                    }
                }
            }
        }

        {
            Optional<BeanReader<InterfaceBean>> optional = BeanReader.forPath(
                    InterfaceBean.class, directory.getInterfaces(), versionControl, version);
            if (optional.isPresent()) {
                try (BeanReader<InterfaceBean> reader = optional.get()) {
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
        }

        {
            Optional<BeanReader<FunctionBean>> optional = BeanReader.forPath(
                    FunctionBean.class, directory.getFunctions(), versionControl, version);
            if (optional.isPresent()) {
                try (BeanReader<FunctionBean> reader = optional.get()) {
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
        }

        {
            Optional<BeanReader<FunctionViewBean>> optional = BeanReader.forPath(
                    FunctionViewBean.class, directory.getFunctionViews(), versionControl, version);
            if (optional.isPresent()) {
                try (BeanReader<FunctionViewBean> reader = optional.get()) {
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
        }

        Map<String, UUID> flowTypes = new HashMap<>();
        {
            Optional<BeanReader<FlowTypeBean>> optional = BeanReader.forPath(
                    FlowTypeBean.class, directory.getFlowTypes(), versionControl, version);
            if (optional.isPresent()) {
                try (BeanReader<FlowTypeBean> reader = optional.get()) {
                    for (;;) {
                        Optional<FlowTypeBean> bean = reader.read();
                        if (bean.isPresent()) {
                            FlowType flowType = new FlowType(bean.get());
                            relations.add(flowType);
                            flowTypes.put(flowType.getName(), flowType.getUuid());
                        } else {
                            break;
                        }
                    }
                }
            }
        }

        {
            Optional<BeanReader<FlowBean>> optional = BeanReader.forPath(
                    FlowBean.class, directory.getFlows(), versionControl, version);
            if (optional.isPresent()) {
                try (BeanReader<FlowBean> reader = optional.get()) {
                    for (;;) {
                        Optional<FlowBean> bean = reader.read();
                        if (bean.isPresent()) {
                            UUID uuid = bean.get().getTypeUUID();
                            if (uuid == null) {
                                String typeName = bean.get().getType();
                                if (typeName == null) {
                                    // Invalid bean
                                    continue;
                                }
                                // Legacy bean: v0.2 support
                                uuid = flowTypes.get(typeName);
                                if (uuid == null) {
                                    FlowType flowType = FlowType.create(typeName);
                                    uuid = flowType.getUuid();
                                    relations.add(flowType);
                                    flowTypes.put(typeName, uuid);
                                }
                                bean.get().setType(uuid.toString());
                            }
                            relations.add(new Flow(bean.get()));
                        } else {
                            break;
                        }
                    }
                }
            }
        }

        {
            Optional<BeanReader<BudgetBean>> optional = BeanReader.forPath(
                    BudgetBean.class, directory.getBudgets(), versionControl, version);
            if (optional.isPresent()) {
                try (BeanReader<BudgetBean> reader = optional.get()) {
                    for (;;) {
                        Optional<BudgetBean> bean = reader.read();
                        if (bean.isPresent()) {
                            Budget model = new Budget(bean.get());
                            relations.add(model);
                        } else {
                            break;
                        }
                    }
                }
            }
        }

        {
            Optional<BeanReader<BudgetAllocationBean>> optional = BeanReader.forPath(
                    BudgetAllocationBean.class, directory.getBudgetAllocations(), versionControl, version);
            if (optional.isPresent()) {
                try (BeanReader<BudgetAllocationBean> reader = optional.get()) {
                    for (;;) {
                        Optional<BudgetAllocationBean> bean = reader.read();
                        if (bean.isPresent()) {
                            BudgetAllocation model = new BudgetAllocation(bean.get());
                            relations.add(model);
                        } else {
                            break;
                        }
                    }
                }
            }
        }

        return new Baseline(RelationStore.valueOf(relations.stream()));
    }

    /**
     * Save the allocated baseline to the nominated directory.
     *
     * @param transaction Save under the specified transaction. The transaction
     * will ensure that all files are written without error before moving the
     * files to overwrite existing file entries.
     * @param directory The directory to save to.
     * @throws java.io.IOException
     */
    public void saveTo(SaveTransaction transaction, Directory directory) throws IOException {
        if (store.size() > 1 || Files.isDirectory(directory.getPath())) {
            Files.createDirectories(directory.getPath());
            BeanFile.saveModel(transaction, this, directory.getIdentityFile(), IdentityBean.class, store.getByClass(Identity.class));
            BeanFile.saveModel(transaction, this, directory.getItems(), ItemBean.class, store.getByClass(Item.class));
            BeanFile.saveModel(transaction, this, directory.getItemViews(), ItemViewBean.class, store.getByClass(ItemView.class));
            BeanFile.saveModel(transaction, this, directory.getInterfaces(), InterfaceBean.class, store.getByClass(Interface.class));
            BeanFile.saveModel(transaction, this, directory.getFunctions(), FunctionBean.class, store.getByClass(Function.class));
            BeanFile.saveModel(transaction, this, directory.getFunctionViews(), FunctionViewBean.class, store.getByClass(FunctionView.class));
            BeanFile.saveModel(transaction, this, directory.getFlowTypes(), FlowTypeBean.class, store.getByClass(FlowType.class));
            BeanFile.saveModel(transaction, this, directory.getFlows(), FlowBean.class, store.getByClass(Flow.class));
            BeanFile.saveModel(transaction, this, directory.getBudgets(), BudgetBean.class, store.getByClass(Budget.class));
            BeanFile.saveModel(transaction, this, directory.getBudgetAllocations(), BudgetAllocationBean.class, store.getByClass(BudgetAllocation.class));
        }
    }

    private final RelationStore store;

    Baseline(RelationStore store) {
        this.store = store;
    }

    /**
     * Return a copy of this baseline with identity set to id.
     *
     * @param id
     * @return
     */
    @CheckReturnValue
    public Baseline setIdentity(Identity id) {
        RelationStore tmpStore = store
                .remove(Identity.find(this).getUuid())
                .add(id);
        return new Baseline(tmpStore);
    }

    /**
     * Get the current instance of a given relation based on a sample object.
     *
     * @param <T> The type to return
     * @param sample A recent version of the object to base the query on
     * @return The relation, or Optional.empty() if no such relation exists
     */
    public <T extends Relation> Optional<T> get(T sample) {
        return get(sample.getUuid(), (Class<T>) sample.getClass());
    }

    /**
     * Support a general fetch method given a UUID. To preserve information
     * hiding this function should only be invoked in cases where it is
     * completely impractical to use the real object as its own identifier. This
     * function can be used for cut and paste operations.
     *
     * @param <T> The type to return
     * @param uuid The key to look up
     * @param type The type to return
     * @return The relation, or Optional.empty() if no such relation exists
     */
    public <T extends Relation> Optional<T> get(UUID uuid, Class<T> type) {
        return store.get(uuid, type);
    }

    /**
     * Search for references to a given UUID
     *
     * @param <T> The type to return
     * @param uuid The key to look up
     * @param fromType The type to return
     * @return The stream of matching relations
     */
    <T extends Relation> Stream<T> getReverse(UUID uuid, Class<T> fromType) {
        return store.getReverse(uuid, fromType);
    }

    /**
     * Expose the complete store for package-private use.
     *
     * @return
     */
    RelationStore getStore() {
        return store;
    }

    /**
     * Expose a read-only view of the store for resolving the target of relation
     * references.
     *
     * @return
     */
    public RelationContext getContext() {
        return store;
    }

    /**
     * Expose interface finding based on UUIDs for package-private use.
     */
    Optional<Interface> getInterface(UndirectedPair scope) {
        return store.getReverse(scope.getLeft(), Interface.class).parallel()
                .filter(iface
                        -> scope.getLeft().equals(iface.getLeft().getUuid())
                        && scope.getRight().equals(iface.getRight().getUuid())
                )
                .findAny();
    }

    /**
     * Expose flow finding based on UUIDs for package-private use.
     */
    Optional<Flow> getFlow(UndirectedPair functions, UUID type) {
        return store.getReverse(functions.getLeft(), Flow.class).parallel()
                .filter(candidate -> {
                    return functions.getRight().equals(candidate.getRight().getUuid())
                            && type.equals(candidate.getType().getUuid());
                }).findAny();
    }

    /*
     * Expose quick and dirty modification methods for package-private use.
     */
    @CheckReturnValue
    Baseline add(Relation relation) {
        RelationStore newStore = store.add(relation);
        if (store == newStore) {
            return this;
        } else {
            return new Baseline(newStore);
        }
    }

    @CheckReturnValue
    Baseline remove(UUID uuid) {
        RelationStore newStore = store.remove(uuid);
        if (store == newStore) {
            return this;
        } else {
            return new Baseline(newStore);
        }
    }

    @CheckReturnValue
    <T extends Relation> Baseline update(UUID uuid, Class<T> type, UnaryOperator<T> update) {
        Optional<T> was = store.get(uuid, type);
        Optional<T> is = was.map(update);
        if (is.isPresent() && !is.equals(was)) {
            return this.add(is.get());
        } else {
            return this;
        }
    }
}
