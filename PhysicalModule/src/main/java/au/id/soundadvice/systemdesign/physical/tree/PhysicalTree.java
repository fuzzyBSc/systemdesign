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
package au.id.soundadvice.systemdesign.physical.tree;

import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.moduleapi.interaction.MenuItems;
import au.id.soundadvice.systemdesign.moduleapi.tree.Tree;
import au.id.soundadvice.systemdesign.moduleapi.tree.TreeNode;
import au.id.soundadvice.systemdesign.physical.entity.Identity;
import au.id.soundadvice.systemdesign.physical.entity.Item;
import au.id.soundadvice.systemdesign.physical.interactions.PhysicalContextMenus;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class PhysicalTree implements Tree {

    private final PhysicalContextMenus menus;
    private final Optional<RecordID> systemOfInterestIdentifier;
    private final TreeNode systemOfInterest;
    private final List<TreeNode> externalSystems;
    private final List<TreeNode> children;

    public PhysicalTree(
            PhysicalContextMenus menus,
            WhyHowPair<Baseline> state) {
        this.menus = menus;

        this.children = Item.find(state.getChild())
                .filter(item -> !item.isExternal())
                .sorted()
                .map(sample -> new PhysicalTreeNode(WhyHowPair.Selector.CHILD, sample, Collections.emptyList()))
                .collect(Collectors.toList());

        Optional<Record> systemOfInterestSample = Identity.getSystemOfInterest(state);
        this.systemOfInterestIdentifier = systemOfInterestSample.map(Record::getIdentifier);
        this.systemOfInterest = Identity.getSystemOfInterest(state)
                .<TreeNode>map(sample -> new PhysicalTreeNode(WhyHowPair.Selector.PARENT, sample, children))
                .<TreeNode>orElseGet(() -> new DummySystemOfInterestNode(Identity.get(state.getChild())));
        Map<RecordID, Record> tmpConnectedSystems;
        if (systemOfInterestSample.isPresent()) {
            tmpConnectedSystems
                    = Item.findConnectedItems(state.getParent(), systemOfInterestSample.get())
                    .collect(Collectors.toMap(Record::getIdentifier, Function.identity()));
        } else {
            tmpConnectedSystems = Collections.emptyMap();
        }
        this.externalSystems
                = Stream.concat(
                        tmpConnectedSystems.values().stream()
                        .sorted(),
                        Item.find(state.getParent())
                        .filter(item -> !tmpConnectedSystems.containsKey(item.getIdentifier()))
                        .sorted()
                )
                .map(sample -> new PhysicalTreeNode(WhyHowPair.Selector.CHILD, sample, Collections.emptyList()))
                .collect(Collectors.toList());
    }

    @Override
    public String getLabel() {
        return "Physical";
    }

    @Override
    public RecordID getIdentifier() {
        return RecordID.of(this.getClass());
    }

    @Override
    public Optional<MenuItems> getContextMenu() {
        return Optional.of(menus.getPhysicalBackgroundMenu());
    }

    private class DummySystemOfInterestNode implements TreeNode {

        public DummySystemOfInterestNode(Record identity) {
            this.identity = identity;
        }
        private final Record identity;

        @Override
        public String toString() {
            return identity.getLongName();
        }

        @Override
        public WhyHowPair<Baseline> setLabel(WhyHowPair<Baseline> baselines, String now, String value) {
            return baselines;
        }

        @Override
        public WhyHowPair<Baseline> removeFrom(WhyHowPair<Baseline> baselines) {
            return baselines;
        }

        @Override
        public Stream<TreeNode> getChildren() {
            return children.stream();
        }

        @Override
        public Optional<Record> getDragDropObject() {
            return Optional.empty();
        }

        @Override
        public Optional<MenuItems> getContextMenu() {
            return Optional.of(menus.getPhysicalBackgroundMenu());
        }

        @Override
        public RecordID getIdentifier() {
            return RecordID.load("System Context").get();
        }

    }

    private class PhysicalTreeNode implements TreeNode {

        private final WhyHowPair.Selector selector;
        private final Record sample;
        private final List<TreeNode> children;

        private PhysicalTreeNode(
                WhyHowPair.Selector selector,
                Record sample, List<TreeNode> children) {
            this.selector = selector;
            this.sample = sample;
            this.children = children;
        }

        @Override
        public WhyHowPair<Baseline> setLabel(WhyHowPair<Baseline> baselines, String now, String value) {
            Baseline baseline = baselines.get(selector);
            Optional<Record> instance = baseline.get(sample);
            if (instance.isPresent()) {
                baseline = Item.item.setDisplayName(baseline, now, instance.get(), value);
                baselines = baselines.set(selector, baseline);
                if (systemOfInterestIdentifier.equals(instance.map(Record::getIdentifier))) {
                    Record childIdentity = Identity.get(baselines.getChild());
                    baselines = baselines.setChild(baselines.getChild().add(childIdentity.asBuilder()
                            .setLongName(value)
                            .build(now)));
                }
                return baselines;
            }
            return baselines;
        }

        @Override
        public String toString() {
            return Item.item.getDisplayName(sample);
        }

        @Override
        public WhyHowPair<Baseline> removeFrom(WhyHowPair<Baseline> baselines) {
            // Only allow removal from child baseline
            if (selector == WhyHowPair.Selector.CHILD) {
                Baseline baseline = baselines.get(selector);
                baseline = baseline.remove(sample.getIdentifier());
                return baselines.set(selector, baseline);
            } else {
                return baselines;
            }
        }

        @Override
        public Stream<TreeNode> getChildren() {
            return children.stream();
        }

        @Override
        public Optional<Record> getDragDropObject() {
            return Optional.of(sample);
        }

        @Override
        public RecordID getIdentifier() {
            return sample.getIdentifier();
        }

        @Override
        public Optional<MenuItems> getContextMenu() {
            return Optional.of(menus.getItemContextMenu(sample));
        }

    }

    @Override
    public Stream<TreeNode> getChildren() {
        return Stream.concat(
                Stream.of(systemOfInterest),
                externalSystems.stream());
    }

}
