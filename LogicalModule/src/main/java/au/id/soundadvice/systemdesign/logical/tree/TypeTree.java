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

import au.id.soundadvice.systemdesign.logical.entity.FlowType;
import au.id.soundadvice.systemdesign.logical.interactions.LogicalContextMenus;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.moduleapi.interaction.MenuItems;
import au.id.soundadvice.systemdesign.moduleapi.tree.Tree;
import au.id.soundadvice.systemdesign.moduleapi.tree.TreeNode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class TypeTree implements Tree {

    @Override
    public String getLabel() {
        return "Type";
    }

    @Override
    public RecordID getIdentifier() {
        return RecordID.of(this.getClass());
    }

    @Override
    public Optional<MenuItems> getContextMenu() {
        return Optional.of(menus.getTypeTreeBackgroundMenu());
    }

    public final class TypeTreeNode implements TreeNode {

        private final Record sample;

        public TypeTreeNode(Record sample) {
            this.sample = sample;
        }

        @Override
        public WhyHowPair<Baseline> setLabel(WhyHowPair<Baseline> baselines, String now, String value) {
            Baseline baseline = baselines.getChild();

            Optional<Record> type = baseline.get(sample);
            if (type.isPresent()) {
                Record updated = type.get().asBuilder()
                        .setLongName(value)
                        .build(now);
                baseline = baseline.add(updated);

                return baselines.setChild(baseline);
            }
            return baselines;
        }

        @Override
        public String toString() {
            return sample.getLongName();
        }

        @Override
        public WhyHowPair<Baseline> removeFrom(WhyHowPair<Baseline> baselines) {
            Baseline baseline = baselines.getChild();

            baseline = baseline.remove(sample.getIdentifier());

            return baselines.setChild(baseline);
        }

        @Override
        public Stream<TreeNode> getChildren() {
            return Stream.empty();
        }

        @Override
        public RecordID getIdentifier() {
            return sample.getIdentifier();
        }

        @Override
        public Optional<Record> getDragDropObject() {
            return Optional.of(sample);
        }

        @Override
        public Optional<MenuItems> getContextMenu() {
            return Optional.of(menus.getTypeContextMenu(sample));
        }
    }

    @Override
    public Stream<TreeNode> getChildren() {
        return types.stream();
    }

    private final LogicalContextMenus menus;
    private final List<TreeNode> types;

    public TypeTree(
            LogicalContextMenus menus,
            WhyHowPair<Baseline> baselines) {
        this.menus = menus;
        this.types = baselines.getChild().findByType(FlowType.flowType)
                .map(sample -> new TypeTreeNode(sample))
                .sorted()
                .collect(Collectors.toList());
    }
}
