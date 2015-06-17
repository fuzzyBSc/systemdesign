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

import au.id.soundadvice.systemdesign.beans.FlowBean;
import au.id.soundadvice.systemdesign.beans.FunctionBean;
import au.id.soundadvice.systemdesign.beans.HazardBean;
import au.id.soundadvice.systemdesign.beans.IdentityBean;
import au.id.soundadvice.systemdesign.beans.InterfaceBean;
import au.id.soundadvice.systemdesign.beans.ItemBean;
import au.id.soundadvice.systemdesign.beans.RequirementBean;
import au.id.soundadvice.systemdesign.files.BeanFile;
import au.id.soundadvice.systemdesign.files.BeanReader;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.files.SaveTransaction;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Hazard;
import au.id.soundadvice.systemdesign.model.IDSegment;
import au.id.soundadvice.systemdesign.model.Identity;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.model.Requirement;
import au.id.soundadvice.systemdesign.relation.Relation;
import au.id.soundadvice.systemdesign.relation.RelationContext;
import au.id.soundadvice.systemdesign.relation.RelationStore;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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

    public Collection<Item> getItems() {
        return store.getByClass(Item.class);
    }

    public Collection<Interface> getInterfaces() {
        return store.getByClass(Interface.class);
    }

    public Collection<Function> getFunctions() {
        return store.getByClass(Function.class);
    }

    public Collection<Flow> getFlows() {
        return store.getByClass(Flow.class);
    }

    public Collection<Hazard> getHazards() {
        return store.getByClass(Hazard.class);
    }

    public Collection<Requirement> getRequirements() {
        return store.getByClass(Requirement.class);
    }

    public static AllocatedBaseline create(Identity identity) {
        return new AllocatedBaseline(RelationStore.empty().put(identity));
    }

    public static AllocatedBaseline load(Directory directory) throws IOException {
        Identity identity = directory.getIdentity();
        if (identity == null) {
            throw new IOException("No Item found in directory");
        }

        List<Relation> relations = new ArrayList<>();

        relations.add(identity);

        if (Files.exists(directory.getItems())) {
            try (BeanReader<ItemBean> reader = BeanReader.forPath(ItemBean.class, directory.getItems())) {
                for (;;) {
                    ItemBean bean = reader.read();
                    if (bean == null) {
                        break;
                    }
                    relations.add(new Item(identity.getUuid(), bean));
                }
            }
        }

        if (Files.exists(directory.getInterfaces())) {
            try (BeanReader<InterfaceBean> reader = BeanReader.forPath(InterfaceBean.class, directory.getInterfaces())) {
                for (;;) {
                    InterfaceBean bean = reader.read();
                    if (bean == null) {
                        break;
                    }
                    relations.add(new Interface(bean));
                }
            }
        }

        if (Files.exists(directory.getFunctions())) {
            try (BeanReader<FunctionBean> reader = BeanReader.forPath(FunctionBean.class, directory.getFunctions())) {
                for (;;) {
                    FunctionBean bean = reader.read();
                    if (bean == null) {
                        break;
                    }
                    relations.add(new Function(bean));
                }
            }
        }

        if (Files.exists(directory.getFlows())) {
            try (BeanReader<FlowBean> reader = BeanReader.forPath(FlowBean.class, directory.getFlows())) {
                for (;;) {
                    FlowBean bean = reader.read();
                    if (bean == null) {
                        break;
                    }
                    relations.add(new Flow(bean));
                }
            }
        }

        if (Files.exists(directory.getHazards())) {
            try (BeanReader<HazardBean> reader = BeanReader.forPath(HazardBean.class, directory.getHazards())) {
                for (;;) {
                    HazardBean bean = reader.read();
                    if (bean == null) {
                        break;
                    }
                    relations.add(new Hazard(bean));
                }
            }
        }

        if (Files.exists(directory.getRequirements())) {
            try (BeanReader<RequirementBean> reader = BeanReader.forPath(RequirementBean.class, directory.getRequirements())) {
                for (;;) {
                    RequirementBean bean = reader.read();
                    if (bean == null) {
                        break;
                    }
                    relations.add(new Requirement(bean));
                }
            }
        }

        return new AllocatedBaseline(RelationStore.valueOf(relations));
    }

    public void saveTo(SaveTransaction transaction, Directory directory) throws IOException {
        if (store.size() > 1 || Files.isDirectory(directory.getPath())) {
            Files.createDirectories(directory.getPath());
            RelationContext context = store;
            BeanFile.saveModel(transaction, context, directory.getIdentityFile(), IdentityBean.class, store.getByClass(Identity.class));
            BeanFile.saveModel(transaction, context, directory.getItems(), ItemBean.class, store.getByClass(Item.class));
            BeanFile.saveModel(transaction, context, directory.getInterfaces(), InterfaceBean.class, store.getByClass(Interface.class));
            BeanFile.saveModel(transaction, context, directory.getFunctions(), FunctionBean.class, store.getByClass(Function.class));
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
                && getStore().get(relation.getUuid(), relation.getClass()) != null;
    }

    public IDSegment getNextItemId() {
        int nextId = 1;
        for (Item item : getItems()) {
            try {
                int itemId = Integer.parseInt(item.getShortId().toString());
                if (itemId >= nextId) {
                    nextId = itemId + 1;
                }
            } catch (NumberFormatException ex) {
                // Skip this item
            }
        }
        return new IDSegment(Integer.toString(nextId));
    }

    @CheckReturnValue
    public AllocatedBaseline removeAll(Collection<UUID> toRemove) {
        if (toRemove.isEmpty()) {
            return this;
        } else {
            return new AllocatedBaseline(store.removeAll(toRemove));
        }
    }
}
