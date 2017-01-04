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
package au.id.soundadvice.systemdesign.physical.drawing;

import au.id.soundadvice.systemdesign.logical.Function;
import au.id.soundadvice.systemdesign.moduleapi.drawing.DrawingEntity;
import au.id.soundadvice.systemdesign.moduleapi.drawing.EntityStyle;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.DiffInfo;
import au.id.soundadvice.systemdesign.moduleapi.collection.DiffPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.physical.Item;
import au.id.soundadvice.systemdesign.physical.ItemView;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.geometry.Point2D;
import javafx.scene.paint.Paint;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
class PhysicalSchematicItem implements DrawingEntity {

    @Override
    public boolean isDiff() {
        return itemView.isDiff();
    }

    @Override
    public boolean isChanged() {
        // Changes to the item view itself don't matter
        // We only care about substantive changes
        return item.isChanged()
                || functions.stream().anyMatch(DiffInfo::isChanged);
    }

    @Override
    public boolean isAdded() {
        return itemView.isAdded();
    }

    @Override
    public boolean isDeleted() {
        return itemView.isDeleted();
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + Objects.hashCode(this.itemView);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PhysicalSchematicItem other = (PhysicalSchematicItem) obj;
        if (!Objects.equals(this.itemView, other.itemView)) {
            return false;
        }
        if (!Objects.equals(this.item, other.item)) {
            return false;
        }
        if (!Objects.equals(this.functions, other.functions)) {
            return false;
        }
        return true;
    }

    private static final EntityStyle STYLE = new EntityStyle(
            EntityStyle.Shape.Rectangle);
    private final DiffPair<Record> itemView;
    private final DiffPair<Record> item;
    private final List<DiffPair<Record>> functions;

    public PhysicalSchematicItem(DiffPair<Record> itemView) {
        this.itemView = itemView;
        this.item = itemView.map((baseline, view) -> ItemView.itemView.getItem(baseline, view));
        this.functions = this.item.map(
                (baseline, theItem) -> Function.findOwnedFunctions(baseline, theItem))
                .stream()
                .flatMap(stream -> stream)
                .map(Record::getIdentifier)
                .distinct()
                .map(functionIdentifier -> DiffPair.get(itemView, functionIdentifier, Function.function))
                .sorted((left, right) -> left.getSample().getLongName().compareTo(right.getSample().getLongName()))
                .collect(Collectors.toList());
    }

    @Override
    public EntityStyle getStyle() {
        return STYLE;
    }

    @Override
    public DiffPair<String> getTitle() {
        return item.map(theItem -> Item.item.getDisplayName(theItem));
    }

    @Override
    public Point2D getOrigin() {
        return itemView.getSample().getOrigin();
    }

    @Override
    public Stream<DiffPair<String>> getBody() {
        return functions.stream()
                .map(functionDiff -> functionDiff.map(function -> "+" + function.getLongName()));
    }

    @Override
    public RecordID getIdentifier() {
        return itemView.getSample().getIdentifier();
    }

    @Override
    public Paint getColor() {
        return item.getSample().getColor();
    }

    @Override
    public boolean isExternal() {
        return item.getSample().isExternal();
    }

    @Override
    public boolean isExternalView() {
        // There is only one physical diagram, so this view can't be external
        // to the normal scope of the diagram.
        return false;
    }

    @Override
    public Baseline setOrigin(Baseline allocated, String now, Point2D origin) {
        Optional<Record> existing = allocated.get(itemView.getSample());
        if (existing.isPresent()) {
            Record newRecord = existing.get().asBuilder()
                    .setOrigin(origin)
                    .build(now);
            return allocated.add(newRecord);
        }
        return allocated;
    }

    @Override
    public Optional<Record> getDragDropObject() {
        return item.getIsInstance();
    }

}
