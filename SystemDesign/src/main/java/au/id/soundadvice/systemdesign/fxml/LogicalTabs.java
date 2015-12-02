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
package au.id.soundadvice.systemdesign.fxml;

import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.model.UndoState;
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.files.Identifiable;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.FlowType;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.FunctionView;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.model.Scope;
import au.id.soundadvice.systemdesign.model.UndirectedPair;
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
import javafx.util.Pair;

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

    private final Map<Optional<Function>, LogicalSchematicController> controllers
            = new ConcurrentHashMap<>();
    private final Interactions interactions;
    private final EditState edit;
    private final TabPane tabs;

    private class OnChange implements Runnable {

        @Override
        public void run() {
            UndoState state = edit.getState();
            Baseline functional = state.getFunctional();
            Baseline allocated = state.getAllocated();
            Optional<Item> systemOfInterest = state.getSystemOfInterest();
            Map<Optional<Function>, Function> parentFunctions;
            if (systemOfInterest.isPresent()) {
                parentFunctions = systemOfInterest.get().findOwnedFunctions(functional).collect(
                        Collectors.toMap(
                                function -> Optional.of(function),
                                java.util.function.Function.identity()));
            } else if (Function.find(allocated).findAny().isPresent()) {
                parentFunctions = Collections.singletonMap(Optional.empty(), null);
            } else {
                parentFunctions = Collections.emptyMap();
            }
            /*
             * Each function on the system of interest gets its own diagram
             * whose UUID is equal to the function's UUID in the functional
             * baseline. It doesn't exist as an object in the alloated baseline,
             * just as an identifier.
             */
            parentFunctions.entrySet().stream()
                    .filter(entry -> !controllers.containsKey(entry.getKey()))
                    .forEachOrdered(entry -> {
                        // Add new tabs
                        LogicalSchematicController newTab
                                = new LogicalSchematicController(
                                        interactions, edit, tabs,
                                        entry.getKey().map(uuid -> entry.getValue()));
                        controllers.put(entry.getKey(), newTab);
                        newTab.start();
                    });
            {
                Iterator<Map.Entry<Optional<Function>, LogicalSchematicController>> it
                        = controllers.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Optional<Function>, LogicalSchematicController> entry = it.next();
                    if (!parentFunctions.containsKey(entry.getKey())) {
                        entry.getValue().stop();
                        it.remove();
                    }
                }
            }

            // Obtain the flows for each connection scope, ignoring direction
            Map<Scope<Function>, List<Flow>> flows = Flow.find(allocated).parallel()
                    .sorted((left, right) -> {
                        FlowType leftType = left.getType().getTarget(allocated.getContext());
                        FlowType rightType = right.getType().getTarget(allocated.getContext());
                        return leftType.getName().compareTo(rightType.getName());
                    })
                    .collect(Collectors.groupingBy(flow -> flow.getScope(allocated)));
            Map<UndirectedPair, List<Flow>> flowsForViews = flows.entrySet().parallelStream()
                    .flatMap(entry -> {
                        List<FunctionView> leftViews
                                = entry.getKey().getLeft().findViews(allocated)
                                .collect(Collectors.toList());
                        List<FunctionView> rightViews
                                = entry.getKey().getRight().findViews(allocated)
                                .collect(Collectors.toList());
                        // Build up the cross-product of leftViews and right views
                        return leftViews.parallelStream()
                                .flatMap(leftView -> {
                                    return rightViews.parallelStream()
                                            .flatMap(rightView -> {
                                                return Stream.of(new Pair<>(
                                                        new UndirectedPair(
                                                                leftView.getUuid(),
                                                                rightView.getUuid()),
                                                        entry.getValue()));
                                            });
                                });
                    })
                    .collect(Collectors.toMap(Pair::getKey, Pair::getValue));

            // Divide up all function views between the various controllers
            Map<Optional<Function>, List<FunctionView>> perDrawingFunctionViews
                    = FunctionView.find(allocated).parallel()
                    .collect(Collectors.groupingBy(view -> view.getDrawing(functional)));
            perDrawingFunctionViews.entrySet().stream()
                    .forEach(entry -> {
                        Optional<LogicalSchematicController> controller
                                = Optional.ofNullable(controllers.get(entry.getKey()));
                        if (controller.isPresent()) {
                            Map<UUID, FunctionView> childFunctions = entry.getValue().parallelStream()
                                    .collect(Identifiable.toMap());
                            controller.get().populate(
                                    allocated, childFunctions, flowsForViews);
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
                    .filter(entry -> !perDrawingFunctionViews.containsKey(entry.getKey()))
                    .forEach(entry -> {
                        // Clear any controllers whose function list is now empty
                        entry.getValue().populate(
                                allocated, Collections.emptyMap(), flowsForViews);
                    });
        }
    }
}
