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
import au.id.soundadvice.systemdesign.moduleapi.collection.BaselinePair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.moduleapi.tree.Tree;
import au.id.soundadvice.systemdesign.moduleapi.tree.TreeNode;
import au.id.soundadvice.systemdesign.physical.Identity;
import au.id.soundadvice.systemdesign.physical.Item;
import java.util.Collections;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class PhysicalTree implements Tree {

    public PhysicalTree(BaselinePair baselines) {
        children = Item.find(baselines.getParent())
                .collect(Collectors.toMap(
                        Record::getShortName,
                        item -> new PhysicalTreeNode(baselines, BaselinePair.Selector.PARENT, item),
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s", u));
                        },
                        TreeMap::new));
    }

    private final SortedMap<String, TreeNode> children;

    @Override
    public String getLabel() {
        return "Physical";
    }

    @Override
    public RecordID getIdentifier() {
        return "Physical";
    }

    private class PhysicalTreeNode implements TreeNode {

        private final BaselinePair.Selector selector;
        private final Record item;
        private final SortedMap<String, TreeNode> children;

        private PhysicalTreeNode(BaselinePair baselines, BaselinePair.Selector selector, Record item) {
            this.selector = selector;
            this.item = item;
            Optional<Record> systemOfInterest = Identity.getSystemOfInterest(baselines);
            if (selector == BaselinePair.Selector.PARENT
                    && systemOfInterest.isPresent()
                    && systemOfInterest.get().getIdentifier().equals(item.getIdentifier())) {
                this.children = Item.find(baselines.getChild())
                        .collect(Collectors.toMap(
                                Record::getShortName,
                                childItem -> new PhysicalTreeNode(baselines, BaselinePair.Selector.CHILD, childItem),
                                (u, v) -> {
                                    throw new IllegalStateException(String.format("Duplicate key %s", u));
                                },
                                TreeMap::new));
            } else {
                this.children = Collections.emptySortedMap();
            }
        }

        @Override
        public BaselinePair setLabel(BaselinePair baselines, String now, String value) {
            Baseline baseline = baselines.get(selector);
            Optional<Record> instance = baseline.get(item);
            if (instance.isPresent()) {
                baseline = Item.item.setDisplayName(baseline, now, instance.get(), value);
                return baselines.set(selector, baseline);
            }
            return baselines;
        }

        @Override
        public String getLabel() {
            return Item.item.getDisplayName(item);
        }

        @Override
        public BaselinePair removeFrom(BaselinePair baselines) {
            // Only allow removal from child baseline
            if (selector == BaselinePair.Selector.CHILD) {
                Baseline baseline = baselines.get(selector);
                baseline = baseline.remove(item.getIdentifier());
                return baselines.set(selector, baseline);
            } else {
                return baselines;
            }
        }

        @Override
        public Stream<TreeNode> getChildren() {
            return children.values().stream();
        }

        @Override
        public Optional<Record> getDragDropObject() {
            return Optional.of(item);
        }

        @Override
        public RecordID getIdentifier() {
            return item.getIdentifier();
        }

    }

    @Override
    public Stream<TreeNode> getChildren() {
        return children.values().stream();
    }

}
