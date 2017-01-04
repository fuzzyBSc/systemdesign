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
package au.id.soundadvice.systemdesign.logical.drawing;

import au.id.soundadvice.systemdesign.logical.Function;
import au.id.soundadvice.systemdesign.logical.FunctionView;
import au.id.soundadvice.systemdesign.moduleapi.drawing.DrawingEntity;
import au.id.soundadvice.systemdesign.moduleapi.drawing.EntityStyle;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.DiffPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.geometry.Point2D;
import javafx.scene.paint.Paint;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class LogicalSchematicFunction implements DrawingEntity {

    @Override
    public boolean isDiff() {
        return view.isDiff();
    }

    @Override
    public boolean isChanged() {
        return view.isChanged()
                || function.isChanged();
    }

    @Override
    public boolean isAdded() {
        return view.isAdded();
    }

    @Override
    public boolean isDeleted() {
        return view.isDeleted();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.view);
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
        final LogicalSchematicFunction other = (LogicalSchematicFunction) obj;
        if (!Objects.equals(this.view, other.view)) {
            return false;
        }
        if (!Objects.equals(this.function, other.function)) {
            return false;
        }
        if (!Objects.equals(this.item, other.item)) {
            return false;
        }
        return true;
    }

    public LogicalSchematicFunction(Record drawing, DiffPair<Record> view) {
        this.drawing = drawing;
        this.view = view;
        this.function = view.map((baseline, record) -> FunctionView.functionView.getFunction(baseline, record));
        this.item = function.map((baseline, record) -> Function.function.getItemForFunction(baseline, record));
    }

    private final Record drawing;
    private final DiffPair<Record> view;
    private final DiffPair<Record> function;
    private final DiffPair<Record> item;

    private static final EntityStyle STYLE = new EntityStyle(EntityStyle.Shape.Oval);

    @Override
    public EntityStyle getStyle() {
        return STYLE;
    }

    @Override
    public DiffPair<String> getTitle() {
        return function.map((baseline, record) -> Function.function.getDisplayName(baseline, record));
    }

    @Override
    public Stream<DiffPair<String>> getBody() {
        return Stream.empty();
    }

    @Override
    public Point2D getOrigin() {
        return view.getSample().getOrigin();
    }

    @Override
    public RecordID getIdentifier() {
        return view.getSample().getIdentifier();
    }

    @Override
    public Paint getColor() {
        return item.getSample().getColor();
    }

    @Override
    public boolean isExternal() {
        return function.getSample().isExternal();
    }

    @Override
    public boolean isExternalView() {
        // If the function is not traced to the drawing's trace then it is
        // an external view for this diagram.
        return !function.getSample().getTrace().equals(
                drawing.getTrace());
    }

    @Override
    public Baseline setOrigin(Baseline allocated, String now, Point2D origin) {
        Optional<Record> existing = allocated.get(view.getSample());
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
        return function.getIsInstance();
    }

}
