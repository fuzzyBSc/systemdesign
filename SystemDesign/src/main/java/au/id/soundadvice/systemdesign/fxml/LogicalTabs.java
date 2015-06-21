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

    private class OnChange implements Runnable {

        @Override
        public void run() {
            UndoState state = edit.getUndo().get();
            Optional<FunctionalBaseline> functional = state.getFunctional();
            Map<Optional<UUID>, Function> parentFunctions;
            if (functional.isPresent()) {
                Stream<Function> reverse = functional.get().getStore().getReverse(
                        functional.get().getSystemOfInterest().getUuid(), Function.class);
                parentFunctions = reverse.collect(Collectors.toMap(
                        function -> Optional.of(function.getUuid()),
                        java.util.function.Function.identity()));
            } else {
                parentFunctions = Collections.singletonMap(Optional.empty(), null);
            }
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
                Iterator<Map.Entry<Optional<UUID>, LogicalSchematicController>> it
                        = controllers.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Optional<UUID>, LogicalSchematicController> entry = it.next();
                    if (!parentFunctions.containsKey(entry.getKey())) {
                        entry.getValue().stop();
                        it.remove();
                    }
                }
            }

            AllocatedBaseline allocated = state.getAllocated();
            // Obtain the flows for each connection scope, ignoring direction
            Map<DirectedPair, List<Flow>> flows = allocated.getFlows().parallel()
                    .sorted((left, right) -> left.getType().compareTo(right.getType()))
                    .collect(Collectors.groupingBy(
                                    flow -> flow.getScope().setDirection(Direction.Both)));
            // Divide up all functions between the various controllers
            Map<Optional<UUID>, List<Function>> displayFunctions
                    = allocated.getFunctions().parallel()
                    .collect(Collectors.groupingBy(Function::getTrace));
            displayFunctions.entrySet().stream()
                    .forEach(entry -> {
                        Optional<LogicalSchematicController> controller
                        = Optional.ofNullable(controllers.get(entry.getKey()));
                        if (controller.isPresent()) {
                            Map<UUID, Function> childFunctions = entry.getValue().parallelStream()
                            .collect(Collectors.toMap(
                                            Function::getUuid,
                                            java.util.function.Function.identity()));
                            controller.get().populate(allocated, childFunctions, flows);
                        } else {
                            // Unallocated functions in an allocated baseline
                        }
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
