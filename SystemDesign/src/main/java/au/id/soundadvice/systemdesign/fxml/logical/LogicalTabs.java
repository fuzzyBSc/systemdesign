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
package au.id.soundadvice.systemdesign.fxml.logical;

import au.id.soundadvice.systemdesign.beans.Direction;
import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.model.UndoState;
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.fxml.Interactions;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.FunctionView;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.model.ItemView;
import au.id.soundadvice.systemdesign.model.RelationDiff;
import au.id.soundadvice.systemdesign.model.RelationPair;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.scene.control.TabPane;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class LogicalTabs {

    public LogicalTabs(Interactions interactions, EditState edit, TabPane tabs) {
        this.interactions = interactions;
        this.edit = edit;
        this.tabs = tabs;
    }

    private final SingleRunnable onChange = new SingleRunnable(
            JFXExecutor.instance(), new OnChange());

    public void start() {
        edit.subscribe(onChange);
        onChange.run();
    }

    private final Map<Optional<UUID>, LogicalSchematicController> controllers
            = new ConcurrentHashMap<>();
    private final Interactions interactions;
    private final EditState edit;
    private final TabPane tabs;

    private static final class FlowInfo {

        public Optional<Function> getDrawing() {
            return drawing;
        }

        public RelationPair<FunctionView> getViews() {
            return views;
        }

        public Flow getFlow() {
            return flow;
        }

        public FlowInfo(Optional<Function> drawing, RelationPair<FunctionView> views, Flow flow) {
            this.drawing = drawing;
            this.views = views;
            this.flow = flow;
        }

        Optional<Function> drawing;
        RelationPair<FunctionView> views;
        Flow flow;
    }

    public static final class FunctionInfo {

        @Override
        public String toString() {
            return function.toString();
        }

        public Item getItem() {
            return item;
        }

        public ItemView getItemView() {
            return itemView;
        }

        public RelationDiff<Function> getFunction() {
            return function;
        }

        public Optional<Function> getDrawing() {
            return drawing;
        }

        public FunctionView getView() {
            return view;
        }

        public FunctionInfo(
                Baseline functional,
                RelationDiff<Function> function,
                FunctionView view) {
            this.function = function;
            this.drawing = view.getDrawing(functional);
            this.view = view;
            this.item = function.getIsInstance().map(
                    f -> f.getItem(function.getIsBaseline()))
                    .orElseGet(() -> function.getWasInstance().map(
                            f -> f.getItem(function.getWasBaseline().get())).get());
            this.itemView = item.findViews(function.getIsBaseline()).findAny()
                    .orElseGet(() -> item.findViews(function.getWasBaseline().get()).findAny().get());
        }

        private final RelationDiff<Function> function;
        private final Optional<Function> drawing;
        private final FunctionView view;
        private final Item item;
        private final ItemView itemView;
    }

    private class OnChange implements Runnable {

        private void createTabs(
                Baseline functional,
                Optional<Item> systemOfInterest) {
            List<Optional<UUID>> drawings;
            if (systemOfInterest.isPresent()) {
                drawings = systemOfInterest.get().findOwnedFunctions(functional)
                        .sorted((a, b) -> a.getName().compareTo(b.getName()))
                        .map(function -> Optional.of(function.getUuid()))
                        .collect(Collectors.toList());
            } else {
                drawings = Collections.singletonList(Optional.empty());
            }
            /*
             * Each function on the system of interest gets its own diagram
             * whose UUID is equal to the function's UUID in the functional
             * baseline. It doesn't exist as an object in the alloated baseline,
             * just as an identifier.
             */
            drawings.stream()
                    .filter(optUuid -> !controllers.containsKey(optUuid))
                    .forEachOrdered(optUuid -> {
                        // Add new tabs
                        LogicalSchematicController newTab
                                = new LogicalSchematicController(
                                        interactions, edit, tabs,
                                        optUuid.flatMap(uuid -> functional.get(uuid, Function.class)));
                        controllers.put(optUuid, newTab);
                        newTab.start();
                    });
            {
                Iterator<Map.Entry<Optional<UUID>, LogicalSchematicController>> it
                        = controllers.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Optional<UUID>, LogicalSchematicController> entry = it.next();
                    if (!drawings.contains(entry.getKey())) {
                        entry.getValue().stop();
                        it.remove();
                    }
                }
            }
        }

        private Stream<RelationDiff<Function>> functionDiffs(
                Optional<Baseline> diffBaseline, Baseline allocated) {
            return Stream.concat(
                    Function.find(allocated).parallel()
                    .map(isView -> RelationDiff.get(diffBaseline, allocated, isView)),
                    diffBaseline.map(baseline -> Function.find(baseline).parallel())
                    .orElse(Stream.empty())
                    .map(wasView -> RelationDiff.get(diffBaseline, allocated, wasView)))
                    .distinct();
        }

        private Stream<FunctionInfo> findInfo(
                Baseline functional,
                Stream<RelationDiff<Function>> functions) {
            return functions.flatMap(function -> {
                // Diagram -> FunctionInfo
                Map<Optional<Function>, FunctionInfo> isViews = function.getIsInstance()
                        .map(f -> f.findViews(function.getIsBaseline()))
                        .orElse(Stream.empty())
                        .map(view -> new FunctionInfo(functional, function, view))
                        .collect(Collectors.toMap(
                                FunctionInfo::getDrawing,
                                f -> f));

                /*
                 * It's possible the was baseline will have duplicate views per
                 * drawing because the original drawing may have been destroyed.
                 * Collect and deduplicate
                 */
                Map<Optional<Function>, List<FunctionInfo>> wasViews = function.getWasInstance()
                        .map(f -> f.findViews(function.getWasBaseline().get()))
                        .orElse(Stream.empty())
                        // Only include "was" views if there is no "is" view for the diagram
                        .map(view -> new FunctionInfo(functional, function, view))
                        .filter(info -> isViews.get(info.getDrawing()) == null)
                        .collect(Collectors.groupingBy(
                                info -> info.getView().getDrawing(functional)));

                return Stream.concat(
                        isViews.values().parallelStream(),
                        wasViews.values().parallelStream()
                        .map(list -> list.get(0)));
            });
        }

        private void fillTabs(
                Baseline functional,
                Optional<Baseline> diffBaseline, Baseline allocated) {
            // Divide up all function views between the various controllers
            Map<Optional<Function>, List<FunctionInfo>> functionsPerDiagram
                    = findInfo(functional, functionDiffs(diffBaseline, allocated))
                    .collect(Collectors.groupingBy(FunctionInfo::getDrawing));

            Map<Optional<Function>, Map<RelationPair<FunctionView>, List<Flow>>> flowsPerDiagram
                    = Flow.find(allocated)
                    .flatMap(flow -> {
                        RelationPair<Function> functions = flow.getEndpoints(allocated)
                                .setDirection(Direction.None);
                        // Build up cross-product of left and right views
                        List<FunctionView> leftViews = functions.getLeft().findViews(allocated)
                                .collect(Collectors.toList());
                        List<FunctionView> rightViews = functions.getRight().findViews(allocated)
                                .collect(Collectors.toList());
                        return leftViews.stream()
                                .flatMap(leftView -> {
                                    return rightViews.stream()
                                            .flatMap(rightView -> {
                                                /*
                                                 * Only include flow if
                                                 * rightView and leftView appear
                                                 * on the same drawing.
                                                 */
                                                Optional<Function> leftDrawing = leftView.getDrawing(functional);
                                                if (rightView.getDrawing(functional).equals(leftDrawing)) {
                                                    return Stream.of(new FlowInfo(
                                                            leftDrawing, new RelationPair<>(leftView, rightView), flow));
                                                } else {
                                                    return Stream.empty();
                                                }
                                            });
                                });
                    })
                    .sorted((a, b) -> {
                        // Sort by type name for drawing stability
                        return a.getFlow().getType(allocated).getName()
                                .compareTo(b.getFlow().getType(allocated).getName());
                    })
                    .collect(Collectors.groupingBy(
                            FlowInfo::getDrawing,
                            Collectors.groupingBy(FlowInfo::getViews,
                                    Collectors.mapping(FlowInfo::getFlow,
                                            Collectors.toList()))));

            functionsPerDiagram.entrySet().stream()
                    .forEach(entry -> {
                        Optional<LogicalSchematicController> controller
                                = Optional.ofNullable(controllers.get(entry.getKey().map(Function::getUuid)));
                        if (controller.isPresent()) {
                            List<FunctionInfo> functionInfo = entry.getValue();
                            Map<RelationPair<FunctionView>, List<Flow>> flows
                                    = flowsPerDiagram.getOrDefault(entry.getKey(), Collections.emptyMap());
                            controller.get().populate(
                                    allocated, functionInfo, flows);
                        } else {
                            /*
                             * This is an unallocated function in an allocated
                             * baseline where the functional baseline exists.
                             * Such functions are not shown in diagrams, but
                             * instead appear in the unalloated section of the
                             * logical tree.
                             */
                        }
                    });
            controllers.entrySet().stream()
                    .filter(entry -> !functionsPerDiagram.containsKey(
                            entry.getKey().flatMap(uuid -> functional.get(uuid, Function.class))))
                    .forEach(entry -> {
                        // Clear any controllers whose function list is now empty
                        entry.getValue().populate(
                                allocated, Collections.emptyList(), Collections.emptyMap());
                    });
            Optional<Optional<Function>> toSelect = PreferredTab.getAndClear();
            Optional<LogicalSchematicController> tab
                    = toSelect.flatMap(drawing -> Optional.ofNullable(
                            controllers.get(drawing.map(Function::getUuid))));
            if (tab.isPresent()) {
                tab.get().select();
            }
        }

        @Override
        public void run() {
            UndoState state = edit.getState();
            Baseline functional = state.getFunctional();
            Baseline allocated = state.getAllocated();
            Optional<Item> systemOfInterest = state.getSystemOfInterest();
            Optional<Baseline> diffBaseline = edit.getDiffBaseline();
            createTabs(functional, systemOfInterest);
            fillTabs(functional, diffBaseline, allocated);
        }
    }
}
