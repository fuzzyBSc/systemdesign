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

import au.id.soundadvice.systemdesign.logical.entity.Flow;
import au.id.soundadvice.systemdesign.logical.entity.LogicalDrawingRecord;
import au.id.soundadvice.systemdesign.logical.entity.FlowType;
import au.id.soundadvice.systemdesign.logical.entity.FunctionView;
import au.id.soundadvice.systemdesign.logical.entity.Function;
import au.id.soundadvice.systemdesign.logical.drawing.LogicalSchematic;
import au.id.soundadvice.systemdesign.logical.interactions.LogicalContextMenus;
import au.id.soundadvice.systemdesign.logical.interactions.LogicalInteractions;
import au.id.soundadvice.systemdesign.logical.tree.LogicalTree;
import au.id.soundadvice.systemdesign.moduleapi.entity.ConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.entity.Direction;
import au.id.soundadvice.systemdesign.moduleapi.Module;
import au.id.soundadvice.systemdesign.moduleapi.drawing.Drawing;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.DiffPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.collection.RecordConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.event.EventDispatcher;
import au.id.soundadvice.systemdesign.moduleapi.tree.Tree;
import au.id.soundadvice.systemdesign.moduleapi.util.ISO8601;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.util.Pair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;
import au.id.soundadvice.systemdesign.physical.drawing.PhysicalSchematicItem;
import au.id.soundadvice.systemdesign.physical.entity.Identity;
import au.id.soundadvice.systemdesign.physical.entity.Interface;
import au.id.soundadvice.systemdesign.physical.entity.Item;
import au.id.soundadvice.systemdesign.physical.interactions.PhysicalInteractions;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class LogicalModule implements Module {

    @Override
    public void init() {
        EventDispatcher.INSTANCE.addFlowDownListener(Item.item, (baselines, externalItem) -> {
            Baseline functional = baselines.getParent();
            Optional<Record> system = Identity.getSystemOfInterest(baselines);
            if (!system.isPresent()) {
                return baselines;
            }
            ConnectionScope interfaceScope = new ConnectionScope(
                    externalItem.getTrace().get(), system.get().getIdentifier(), Direction.None);
            Optional<Record> iface
                    = functional.findByScope(interfaceScope)
                    .filter(candidate -> candidate.getType().equals(Interface.iface))
                    .findAny();
            if (!iface.isPresent()) {
                return baselines;
            }
            Iterator<Record> relevantFunctionsForExternalItem
                    = Flow.findForInterface(functional, iface.get())
                    .flatMap(flow -> flow.getConnectionScope().ends())
                    .flatMap(flowEndIdentifier -> functional.get(flowEndIdentifier, Function.function)
                            .map(Stream::of).orElse(Stream.empty()))
                    // Don't include the system of interest end of the flow
                    .filter(function -> !Function.function.getItemForFunction(functional, function).getIdentifier()
                            .equals(system.get().getIdentifier()))
                    .distinct().iterator();

            while (relevantFunctionsForExternalItem.hasNext()) {
                Record function = relevantFunctionsForExternalItem.next();
                baselines = Function.flowDownExternal(baselines, externalItem.getLastChange(), function).getKey();
            }
            return baselines;
        });

        EventDispatcher.INSTANCE.setLinkOperation(Function.function, Function.function, (baselines, functions) -> {
            RecordConnectionScope scope = RecordConnectionScope.resolve(functions.getKey(), functions.getValue(), Direction.Forward);
            return Flow.addWithGuessedType(baselines, ISO8601.now(), scope).getKey();
        });
        EventDispatcher.INSTANCE.setMoveOperation(Function.function, LogicalDrawingRecord.logicalDrawing, (baselines, functionToDrawing) -> {
            Optional<Record> traceFunction = LogicalDrawingRecord.logicalDrawing.getTrace(
                    baselines, functionToDrawing.getValue());
            if (traceFunction.isPresent()) {
                String now = ISO8601.now();
                Record function = functionToDrawing.getKey();
                Record drawing = functionToDrawing.getValue();
                {
                    // Make sure a view is present in the nominated drawing
                    Pair<Baseline, Record> tmp = FunctionView.create(baselines.getChild(), now, function, drawing);
                    baselines = baselines.setChild(tmp.getKey());
                }
                return Function.function.setTrace(baselines, now, function, traceFunction.get()).getKey();
            } else {
                return baselines;
            }
        });
        EventDispatcher.INSTANCE.setCopyOperation(Function.function, LogicalDrawingRecord.logicalDrawing, (baselines, functionToDrawing) -> {
            Optional<Record> traceFunction = LogicalDrawingRecord.logicalDrawing.getTrace(
                    baselines, functionToDrawing.getValue());
            if (traceFunction.isPresent()) {
                String now = ISO8601.now();
                Record function = functionToDrawing.getKey();
                Record drawing = functionToDrawing.getValue();
                {
                    // Make sure a view is present in the nominated drawing
                    Pair<Baseline, Record> tmp = FunctionView.create(baselines.getChild(), now, function, drawing);
                    baselines = baselines.setChild(tmp.getKey());
                }
                return baselines;
            } else {
                return baselines;
            }
        });

        PhysicalSchematicItem.addCompartment(
                itemDiffPair -> {
                    List<DiffPair<Record>> functions = itemDiffPair.map(
                            (baseline, theItem) -> Function.findOwnedFunctions(baseline, theItem))
                    .stream()
                    .flatMap(stream -> stream)
                    .map(Record::getIdentifier)
                    .distinct()
                    .map(functionIdentifier -> DiffPair.get(itemDiffPair, functionIdentifier, Function.function))
                    .sorted((left, right) -> left.getSample().getLongName().compareTo(right.getSample().getLongName()))
                    .collect(Collectors.toList());
                    return new FunctionCompartment(functions);
                }
        );
    }

    private class FunctionCompartment implements PhysicalSchematicItem.Compartment {

        public FunctionCompartment(List<DiffPair<Record>> functions) {
            this.functions = functions;
        }

        private final List<DiffPair<Record>> functions;

        @Override
        public boolean isChanged() {
            return functions.stream().anyMatch(DiffPair::isChanged);
        }

        @Override
        public Stream<DiffPair<String>> getBody() {
            return functions.stream()
                    .map(functionDiff -> functionDiff.map(function -> "+" + function.getLongName()));
        }
    }

    private final PhysicalInteractions physicalInteractions = new PhysicalInteractions();
    private final LogicalInteractions logicalInteractions = new LogicalInteractions();
    private final LogicalContextMenus menus = new LogicalContextMenus(physicalInteractions, logicalInteractions);

    @Override
    public WhyHowPair<Baseline> onLoadAutoFix(WhyHowPair<Baseline> baselines, String now) {
        baselines = LogicalDrawingRecord.logicalDrawing.createNeededDrawings(baselines, now);
        baselines = FunctionView.functionView.createNeededViews(baselines, now);
        return baselines;
    }

    @Override
    public WhyHowPair<Baseline> onChangeAutoFix(WhyHowPair<Baseline> baselines, String now) {
        baselines = FunctionView.functionView.createNeededViews(baselines, now);
        return baselines;
    }

    @Override
    public Stream<Table> getTables() {
        return Stream.of(
                Function.function,
                FunctionView.functionView,
                FlowType.flowType,
                Flow.flow);
    }

    @Override
    public Stream<Drawing> getDrawings(DiffPair<Baseline> baselines) {
        return baselines.getIsBaseline().findByType(LogicalDrawingRecord.logicalDrawing)
                .map(entity -> new LogicalSchematic(menus, baselines, entity));
    }

    @Override
    public Stream<Tree> getTrees(WhyHowPair<Baseline> baselines) {
        return Stream.of(new LogicalTree(menus, baselines));
    }
}
