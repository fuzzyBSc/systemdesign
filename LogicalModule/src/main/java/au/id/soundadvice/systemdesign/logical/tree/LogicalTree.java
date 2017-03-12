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
package au.id.soundadvice.systemdesign.logical.tree;

import au.id.soundadvice.systemdesign.logical.entity.Function;
import au.id.soundadvice.systemdesign.logical.interactions.LogicalContextMenus;
import au.id.soundadvice.systemdesign.moduleapi.entity.Identifiable;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.moduleapi.interaction.MenuItems;
import au.id.soundadvice.systemdesign.moduleapi.tree.Tree;
import au.id.soundadvice.systemdesign.moduleapi.tree.TreeNode;
import au.id.soundadvice.systemdesign.physical.entity.Identity;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class LogicalTree implements Tree {

    @Override
    public String getLabel() {
        return "Logical";
    }

    @Override
    public RecordID getIdentifier() {
        return RecordID.of(this.getClass());
    }

    @Override
    public Optional<MenuItems> getContextMenu() {
        return Optional.of(menus.getLogicalTreeBackgroundMenu());
    }

    public final class LogicalTreeNode implements TreeNode {

        private final String orphanLabel;

        public LogicalTreeNode(
                String orphanLabel, Optional<Record> sample,
                WhyHowPair.Selector selector, Stream<TreeNode> children) {
            this.orphanLabel = orphanLabel;
            this.sample = sample;
            this.selector = selector;
            this.children = children.sorted().collect(Collectors.toList());
        }

        @Override
        public WhyHowPair<Baseline> setLabel(WhyHowPair<Baseline> baselines, String now, String value) {
            if (sample.isPresent()) {
                Baseline baseline = baselines.get(selector);

                Optional<Record> function = baseline.get(sample.get());
                if (function.isPresent()) {
                    Record updated = function.get().asBuilder()
                            .setLongName(value)
                            .build(now);
                    baseline = baseline.add(updated);

                    return baselines.set(selector, baseline);
                }
            }
            return baselines;
        }

        @Override
        public String toString() {
            return sample.map(Record::getLongName).orElse(orphanLabel);
        }

        private final WhyHowPair.Selector selector;
        private final Optional<Record> sample;
        private final List<TreeNode> children;

        private boolean isEmpty() {
            return children.isEmpty();
        }

        @Override
        public WhyHowPair<Baseline> removeFrom(WhyHowPair<Baseline> baselines) {
            if (sample.isPresent()) {
                Baseline baseline = baselines.get(selector);

                baseline = baseline.remove(sample.get().getIdentifier());

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
        public RecordID getIdentifier() {
            return sample.map(Record::getIdentifier).get();
        }

        @Override
        public Optional<Record> getDragDropObject() {
            return sample;
        }

        @Override
        public Optional<MenuItems> getContextMenu() {
            return sample.map(ff -> menus.getFunctionContextMenu(ff));
        }
    }

    @Override
    public Stream<TreeNode> getChildren() {
        Stream<TreeNode> result = allocation.stream();
        if (!orphans.isEmpty()) {
            result = Stream.concat(result, Stream.of(orphans));
        }
        return result;
    }

    private final LogicalContextMenus menus;
    private final List<TreeNode> allocation;
    private final LogicalTreeNode orphans;

    public LogicalTree(
            LogicalContextMenus menus,
            WhyHowPair<Baseline> baselines) {
        this.menus = menus;
        Optional<Record> systemOfInterest = Identity.getSystemOfInterest(baselines);

        Map<RecordID, Record> parentFunctions;
        String orphanLabel;
        if (systemOfInterest.isPresent()) {
            parentFunctions = Function.findOwnedFunctions(baselines.getParent(), systemOfInterest.get())
                    .collect(Collectors.toMap(Identifiable::getIdentifier, t -> t));
            orphanLabel = "Untraced Functions";
        } else {
            parentFunctions = Collections.emptyMap();
            orphanLabel = "Top Level Functions";
        }

        Map<Optional<Record>, Map<RecordID, Record>> childFunctions
                = Function.find(baselines.getChild())
                .collect(Collectors.groupingBy(
                        child -> child.getTrace().<Record>flatMap(
                                traceID -> Optional.ofNullable(parentFunctions.get(traceID))),
                        Collectors.toMap(Identifiable::getIdentifier, t -> t)));

        this.allocation = childFunctions.entrySet().stream()
                .filter(entry -> entry.getKey().isPresent())
                .map(entry -> {
                    Stream<TreeNode> children = Optional.ofNullable(childFunctions.get(entry.getKey()))
                            .orElse(Collections.emptySortedMap())
                            .entrySet().stream()
                            .map(childEntry -> new LogicalTreeNode(
                                    orphanLabel, Optional.of(childEntry.getValue()),
                                    WhyHowPair.Selector.CHILD, Stream.empty()));
                    return new LogicalTreeNode(
                            orphanLabel, entry.getKey(),
                            WhyHowPair.Selector.PARENT,
                            children);
                })
                .sorted()
                .collect(Collectors.toList());
        this.orphans = new LogicalTreeNode(
                orphanLabel, Optional.empty(),
                WhyHowPair.Selector.PARENT,
                Optional.ofNullable(childFunctions.get(Optional.<Record>empty()))
                .orElse(Collections.emptySortedMap())
                .entrySet().stream()
                .map(childEntry -> new LogicalTreeNode(
                        orphanLabel, Optional.of(childEntry.getValue()),
                        WhyHowPair.Selector.CHILD, Stream.empty())));
    }
}
