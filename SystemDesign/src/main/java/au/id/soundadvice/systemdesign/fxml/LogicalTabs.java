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

import au.id.soundadvice.systemdesign.baselines.AllocatedBaseline;
import au.id.soundadvice.systemdesign.baselines.EditState;
import au.id.soundadvice.systemdesign.baselines.FunctionalBaseline;
import au.id.soundadvice.systemdesign.baselines.UndoState;
import au.id.soundadvice.systemdesign.beans.Direction;
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.DirectedPair;
import au.id.soundadvice.systemdesign.model.Function;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
        this.topLevel = new LogicalSchematicController(interactions, edit, tabs, null);
    }

    private final SingleRunnable onChange = new SingleRunnable(
            JFXExecutor.instance(), new OnChange());

    public void start() {
        edit.subscribe(onChange);
        onChange.run();
    }

    private final Map<UUID, LogicalSchematicController> controllers
            = new ConcurrentHashMap<>();
    private final LogicalSchematicController topLevel;
    private final Interactions interactions;
    private final EditState edit;
    private final TabPane tabs;

    private class OnChange implements Runnable {

        @Override
        public void run() {
            UndoState state = edit.getUndo().get();
            FunctionalBaseline functional = state.getFunctional();
            Map<UUID, Function> parentFunctions;
            if (functional == null) {
                parentFunctions = Collections.emptyMap();
                topLevel.start();
            } else {
                Stream<Function> reverse = functional.getStore().getReverse(
                        functional.getSystemOfInterest().getUuid(), Function.class);
                parentFunctions = reverse.collect(Collectors.toMap(
                        Function::getUuid,
                        java.util.function.Function.identity()));
                topLevel.stop();
            }
            parentFunctions.values().stream()
                    .filter(function -> !controllers.containsKey(function.getUuid()))
                    .forEachOrdered(function -> {
                        // Add new tabs
                        LogicalSchematicController newTab
                        = new LogicalSchematicController(interactions, edit, tabs, function);
                        controllers.put(function.getUuid(), newTab);
                        newTab.start();
                    });
            controllers.keySet().stream()
                    .filter(uuid -> !parentFunctions.containsKey(uuid))
                    .forEach(uuid -> {
                        // Remove old tabs
                        LogicalSchematicController controller = controllers.remove(uuid);
                        controller.stop();
                    });

            AllocatedBaseline allocated = state.getAllocated();
            // Obtain the flows for each connection scope, ignoring direction
            Map<DirectedPair, List<Flow>> flows = allocated.getFlows().parallel()
                    .sorted((left, right) -> left.getType().compareTo(right.getType()))
                    .collect(Collectors.groupingBy(
                                    flow -> flow.getScope().setDirection(Direction.Both)));
            if (functional == null) {
                Map<UUID, Function> childFunctions = allocated.getFunctions().parallel()
                        .collect(Collectors.toMap(
                                        Function::getUuid,
                                        java.util.function.Function.identity()));
                topLevel.populate(allocated, childFunctions, flows);
            } else {
                // Divide up all functions between the various controllers
                Map<UUID, List<Function>> displayFunctions
                        = allocated.getFunctions().parallel()
                        .filter(function -> function.getTrace() != null)
                        .collect(Collectors.groupingBy(Function::getTrace));
                displayFunctions.entrySet().stream()
                        .forEach(entry -> {
                            // Update each controller with its subset
                            LogicalSchematicController controller = controllers.get(entry.getKey());
                            Map<UUID, Function> childFunctions = entry.getValue().parallelStream()
                            .collect(Collectors.toMap(
                                            Function::getUuid,
                                            java.util.function.Function.identity()));
                            controller.populate(allocated, childFunctions, flows);
                        });
                controllers.entrySet().stream()
                        .filter(entry -> !displayFunctions.containsKey(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .forEach(controller -> {
                            // Clear any controllers whose function list is now empty
                            controller.populate(allocated, Collections.emptyMap(), flows);
                        });
            }
        }
    }
}
