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

import au.id.soundadvice.systemdesign.logical.entity.Flow;
import au.id.soundadvice.systemdesign.logical.entity.FunctionView;
import au.id.soundadvice.systemdesign.logical.entity.LogicalDrawing;
import au.id.soundadvice.systemdesign.logical.interactions.LogicalContextMenus;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.entity.ConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.drawing.Drawing;
import au.id.soundadvice.systemdesign.moduleapi.drawing.DrawingConnector;
import au.id.soundadvice.systemdesign.moduleapi.drawing.DrawingEntity;
import au.id.soundadvice.systemdesign.moduleapi.collection.DiffPair;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.moduleapi.interaction.InteractionContext;
import au.id.soundadvice.systemdesign.moduleapi.interaction.MenuItems;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class LogicalSchematic implements Drawing {

    private final LogicalContextMenus menus;
    private final DiffPair<Record> drawing;
    private final DiffPair<Optional<RecordID>> parentFunctionIdentifier;
    private final List<DrawingEntity> entities;
    private final List<DrawingConnector> connectors;

    public LogicalSchematic(
            LogicalContextMenus menus,
            DiffPair<Record> drawing) {
        this.menus = menus;
        this.drawing = drawing;
        this.parentFunctionIdentifier = drawing.map(Record::getTrace);
        Map<RecordID, DiffPair<Record>> functionIdentifierToFunctionView
                = drawing.flatMapStream(FunctionView::findForDrawing)
                .collect(Collectors.toMap(
                        view -> view.getSample().getViewOf().get(),
                        view -> view));
        this.entities = functionIdentifierToFunctionView.values().stream()
                .map(view -> new LogicalSchematicFunction(menus, drawing, view))
                .collect(Collectors.toList());
        this.connectors = DiffPair.find(drawing, Flow::find, Flow.flow)
                .flatMap(flowDiff -> {
                    ConnectionScope scope = flowDiff.getSample().getConnectionScope();
                    @Nullable
                    DiffPair<Record> leftView = functionIdentifierToFunctionView.get(scope.getLeft());
                    @Nullable
                    DiffPair<Record> rightView = functionIdentifierToFunctionView.get(scope.getRight());
                    if (leftView != null && rightView != null) {
                        return Stream.of(new LogicalSchematicFlow(menus, flowDiff, leftView.getSample(), rightView.getSample()));
                    }
                    return Stream.empty();
                })
                .collect(Collectors.toList());
    }

    public Optional<Record> getTraceFunction(WhyHowPair<Baseline> state) {
        return LogicalDrawing.logicalDrawing.getTrace(state, drawing.getSample());
    }

    public Record getDrawingRecord() {
        return drawing.getSample();
    }

    @Override
    public String getTitle() {
        return drawing.getSample().getLongName();
    }

    @Override
    public Stream<DrawingEntity> getEntities() {
        return entities.stream();
    }

    @Override
    public Stream<DrawingConnector> getConnectors() {
        return connectors.stream();
    }

    @Override
    public RecordID getIdentifier() {
        return parentFunctionIdentifier.getSample().orElseGet(
                () -> RecordID.of(this.getClass()));
    }

    @Override
    public Optional<Record> getDragDropObject() {
        return drawing.getIsInstance();
    }

    @Override
    public Optional<MenuItems> getContextMenu(InteractionContext context) {
        return Optional.of(menus.getLogicalSchematicBackgroundMenu(this));
    }

}
