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

import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.BaselinePair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import static au.id.soundadvice.systemdesign.physical.Interface.findForItem;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.geometry.Point2D;
import java.util.stream.Stream;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;
import au.id.soundadvice.systemdesign.moduleapi.entity.UniqueConstraint;

/**
 * A physical Item. Item is used as a fairly loose term in the model and could
 * mean system, subsystem, configuration item, or correspond to a number of
 * standards-based concepts. As far as the model goes it identifies something
 * that exists rather than dealing with what a thing does. Items are at the root
 * of how the model is put together. Each item either is or has the potential to
 * be a whole director unto itself containing other conceptual elements.
 *
 * An item is typically an entire system, an assembly of parts, or a hardware or
 * software configuration item. The kind of existence required of it can be
 * abstract. For software it could end up a unit of software installed under a
 * single software package, a name space or class.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public enum ItemView implements Table {
    itemView;

    public static Point2D DEFAULT_ORIGIN = new Point2D(200, 200);

    /**
     * Return all views of items within the baseline.
     *
     * @param baseline The baseline to search
     * @return
     */
    public static Stream<Record> find(Baseline baseline) {
        return baseline.findByType(itemView);
    }

    /**
     * Create a new item view.
     *
     * @param baseline The baseline to update
     * @param now The current time in ISO8601 format
     * @param item The item this view refers to
     * @param origin The location for the item on the screen
     * @return The updated baseline
     */
    @CheckReturnValue
    public static Pair<Baseline, Record> create(
            Baseline baseline, String now, Record item, Point2D origin) {
        Optional<Record> existingView = findForItem(baseline, item)
                .findAny();
        if (existingView.isPresent()) {
            return new Pair<>(baseline, existingView.get());
        } else {
            Record view = Record.create(itemView)
                    .setViewOf(item)
                    .setOrigin(origin)
                    .build(now);
            baseline = baseline.add(view);
            return new Pair<>(baseline, view);
        }
    }

    @CheckReturnValue
    public BaselinePair createNeededViews(BaselinePair baselines, String now) {
        BaselinePair result = baselines;
        Map<String, Record> itemsWithExistingViews
                = ItemView.find(baselines.getChild())
                .map(view -> ItemView.itemView.getItem(baselines.getChild(), view))
                .collect(Collectors.toMap(Record::getIdentifier, o -> o));
        // All child items should be on the context diagram
        Stream<Record> itemsForDrawing = Item.find(baselines.getChild());

        Iterator<Record> itemsToAdd = itemsForDrawing
                .filter(function -> !itemsWithExistingViews.containsKey(function.getIdentifier()))
                .iterator();
        while (itemsToAdd.hasNext()) {
            Record itemToAdd = itemsToAdd.next();
            result = result.setChild(
                    ItemView.create(result.getChild(), now, itemToAdd, ItemView.DEFAULT_ORIGIN)
                    .getKey());
        }
        return result;
    }

    public Record getItem(Baseline baseline, Record view) {
        return baseline.get(view.getViewOf().get(), Item.item).get();
    }

    @Override
    public String getTableName() {
        return name();
    }

    @Override
    public Stream<Problem> getTraceProblems(BaselinePair context, Record traceParent, Stream<Record> traceChild) {
        // Views don't trace
        return Stream.empty();
    }

    @Override
    public Stream<Problem> getUntracedParentProblems(BaselinePair context, Stream<Record> untracedParents) {
        // Views don't trace
        return Stream.empty();
    }

    @Override
    public Stream<Problem> getUntracedChildProblems(BaselinePair context, Stream<Record> untracedChildren) {
        // Views don't trace
        return Stream.empty();
    }

    @Override
    public Stream<UniqueConstraint> getUniqueConstraints() {
        return Stream.of(Record::getViewOf);
    }

    @Override
    public Record merge(BaselinePair baselines, String now, Record left, Record right) {
        return Record.newerOf(left, right);
    }
}
