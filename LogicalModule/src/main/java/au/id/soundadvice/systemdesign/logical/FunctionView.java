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
package au.id.soundadvice.systemdesign.logical;

import au.id.soundadvice.systemdesign.logical.beans.FunctionViewBean;
import au.id.soundadvice.systemdesign.moduleapi.Identifiable;
import au.id.soundadvice.systemdesign.moduleapi.relation.Reference;
import au.id.soundadvice.systemdesign.moduleapi.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javafx.geometry.Point2D;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;

/**
 * A view of a function within a specific drawing. The separation of the main
 * Function class and the FunctionView class is intended to allow each function
 * to appear independently on multiple drawings while still maintaining some
 * properties between such drawings.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FunctionView implements Relation {

    public static Point2D DEFAULT_ORIGIN = new Point2D(200, 200);

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 83 * hash + Objects.hashCode(this.uuid);
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
        final FunctionView other = (FunctionView) obj;
        if (!Objects.equals(this.uuid, other.uuid)) {
            return false;
        }
        if (!Objects.equals(this.function, other.function)) {
            return false;
        }
        if (!Objects.equals(this.drawing, other.drawing)) {
            return false;
        }
        if (!Objects.equals(this.origin, other.origin)) {
            return false;
        }
        return true;
    }

    /**
     * Return all views of functions. For functions allocated to subsystems we
     * should always see one view of that function, corresponding to its trace.
     * External functions may appear on multiple drawings and thus have multiple
     * views.
     *
     * @param baseline The baseline to search
     * @return
     */
    public static Stream<FunctionView> find(Relations baseline) {
        return baseline.findByClass(FunctionView.class);
    }

    public static Pair<Relations, FunctionView> create(
            Relations baseline, Function function, Optional<Function> drawing, Point2D origin) {
        Optional<FunctionView> existingView = function.findViews(baseline)
                .filter(view -> view.drawing.equals(
                        drawing.map(Function::getUuid)))
                .findAny();
        if (existingView.isPresent()) {
            return new Pair<>(baseline, existingView.get());
        } else {
            FunctionView view = new FunctionView(
                    UUID.randomUUID(),
                    function.getUuid(), drawing.map(Identifiable::getUuid), origin);
            return new Pair<>(baseline.add(view), view);
        }
    }

    public Relations removeFrom(Relations baseline) {
        return baseline.remove(uuid);
    }

    @Override
    public String toString() {
        return uuid.toString();
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public Reference<FunctionView, Function> getFunction() {
        return function;
    }

    public Function getFunction(Relations baseline) {
        return function.getTarget(baseline);
    }

    public boolean isDrawingDeleted(Relations functionalBaseline) {
        return this.drawing.map(
                functionUUID -> functionalBaseline.get(functionUUID, Function.class).isPresent())
                // The unallocated functions drawing can't be "deleted" per se
                .orElse(false);
    }

    public Optional<Function> getDrawing(Relations functionalBaseline) {
        return this.drawing.flatMap(
                functionUUID -> functionalBaseline.get(functionUUID, Function.class));
    }

    public FunctionView(FunctionViewBean bean) {
        this.uuid = bean.getUuid();
        this.drawing = Optional.ofNullable(bean.getDrawing());
        this.function = new Reference<>(this, bean.getFunction(), Function.class);
        this.origin = new Point2D(bean.getOriginX(), bean.getOriginY());
    }

    private FunctionView(UUID uuid, UUID function, Optional<UUID> drawing, Point2D origin) {
        this.uuid = uuid;
        this.function = new Reference<>(this, function, Function.class);
        this.drawing = drawing;
        this.origin = origin;
    }

    private final UUID uuid;
    private final Reference<FunctionView, Function> function;
    private final Optional<UUID> drawing;
    private final Point2D origin;

    public FunctionViewBean toBean(Relations baseline) {
        return new FunctionViewBean(
                uuid, function.getUuid(), getDisplayName(baseline), drawing,
                (int) origin.getX(), (int) origin.getY());
    }

    public String getDisplayName(Relations baseline) {
        return function.getTarget(baseline).getDisplayName(baseline);
    }

    private static final ReferenceFinder<FunctionView> FINDER
            = new ReferenceFinder<>(FunctionView.class);

    @Override
    public Stream<Reference> getReferences() {
        return FINDER.getReferences(this);
    }

    @CheckReturnValue
    public Pair<Relations, FunctionView> setOrigin(Relations baseline, Point2D value) {
        if (origin.equals(value)) {
            return new Pair<>(baseline, this);
        } else {
            FunctionView result = new FunctionView(uuid, function.getUuid(), drawing, value);
            return new Pair<>(baseline.add(result), result);
        }
    }

    public Point2D getOrigin() {
        return origin;
    }
}
