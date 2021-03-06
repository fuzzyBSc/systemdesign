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
package au.id.soundadvice.systemdesign.physical;

import au.id.soundadvice.systemdesign.physical.drawing.PhysicalSchematic;
import au.id.soundadvice.systemdesign.moduleapi.Module;
import au.id.soundadvice.systemdesign.moduleapi.drawing.Drawing;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.DiffPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.collection.RecordConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.moduleapi.event.EventDispatcher;
import au.id.soundadvice.systemdesign.moduleapi.tree.Tree;
import au.id.soundadvice.systemdesign.moduleapi.util.ISO8601;
import au.id.soundadvice.systemdesign.physical.tree.PhysicalTree;
import java.util.Optional;
import java.util.stream.Stream;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;
import au.id.soundadvice.systemdesign.physical.entity.Identity;
import au.id.soundadvice.systemdesign.physical.entity.Interface;
import au.id.soundadvice.systemdesign.physical.entity.Item;
import au.id.soundadvice.systemdesign.physical.entity.ItemView;
import au.id.soundadvice.systemdesign.physical.interactions.PhysicalContextMenus;
import au.id.soundadvice.systemdesign.physical.interactions.PhysicalInteractions;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class PhysicalModule implements Module {

    private static WhyHowPair<Baseline> flowDownExternalItemView(WhyHowPair<Baseline> baselines, String now, Record item) {
        Optional<RecordID> trace = item.getTrace();
        if (trace.isPresent()) {
            Record parentItem = baselines.getParent().get(trace.get(), Item.item).get();
            Record parentView = Item.item.findViews(baselines.getParent(), parentItem).findAny().get();
            Record childView = parentView.asBuilder()
                    .newIdentifier()
                    .setViewOf(item)
                    .build(now);
            return baselines.setChild(baselines.getChild().add(childView));
        }
        return baselines;
    }

    @Override
    public void init() {
        EventDispatcher.INSTANCE.addFlowDownListener(
                Item.item,
                (baselines, item) -> flowDownExternalItemView(baselines, ISO8601.now(), item));

        EventDispatcher.INSTANCE.setLinkOperation(Item.item, Item.item, (baseline, items) -> {
            RecordConnectionScope scope = RecordConnectionScope.resolve(items.getKey(), items.getValue());
            return Interface.connect(baseline, ISO8601.now(), scope).getKey();
        });
    }

    @Override
    public WhyHowPair<Baseline> onLoadAutoFix(WhyHowPair<Baseline> baselines, String now) {
        baselines = ItemView.itemView.createNeededViews(baselines, now);
        return baselines;
    }

    @Override
    public WhyHowPair<Baseline> onChangeAutoFix(WhyHowPair<Baseline> baselines, String now) {
        baselines = ItemView.itemView.createNeededViews(baselines, now);
        return baselines;
    }

    @Override
    public Stream<Table> getTables() {
        return Stream.of(
                Identity.identity,
                Item.item,
                ItemView.itemView,
                Interface.iface);
    }

    private final PhysicalInteractions interactions = new PhysicalInteractions();
    private final PhysicalContextMenus menus = new PhysicalContextMenus(interactions);

    @Override
    public Stream<Drawing> getDrawings(DiffPair<Baseline> baselines) {
        return Stream.of(new PhysicalSchematic(interactions, menus, baselines));
    }

    @Override
    public Stream<Tree> getTrees(WhyHowPair<Baseline> baselines) {
        return Stream.of(new PhysicalTree(menus, baselines));
    }
}
