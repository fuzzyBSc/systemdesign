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

import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.consistency.AllSuggestions;
import au.id.soundadvice.systemdesign.consistency.AutoFix;
import au.id.soundadvice.systemdesign.consistency.EditProblem;
import au.id.soundadvice.systemdesign.consistency.EditSolution;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Solution;
import au.id.soundadvice.systemdesign.moduleapi.util.ISO8601;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class SuggestionsController {

    public SuggestionsController(EditState edit, Pane parent) {
        this.parent = parent;
        this.edit = edit;
        this.onLoad = new OnLoad();
        this.onChange = new SingleRunnable(edit.getExecutor(), new OnChange());
    }

    public void start() {
        AutoFix.addOnLoad(onLoad);
        edit.subscribe(onChange);
        onChange.run();
    }

    private final Pane parent;
    private final EditState edit;
    private final OnLoad onLoad;
    private final SingleRunnable<OnChange> onChange;
    private final AtomicReference<Collection<EditProblem>> problems = new AtomicReference<>();
    private final SingleRunnable<UpdateDisplay> updateDisplay
            = new SingleRunnable(JFXExecutor.instance(), new UpdateDisplay());

    private final class OnLoad implements BiFunction<WhyHowPair<Baseline>, String, WhyHowPair<Baseline>> {

        @Override
        public WhyHowPair<Baseline> apply(WhyHowPair<Baseline> state, String now) {
            Iterator<Problem> it = AllSuggestions.getUndoProblems(state).iterator();
            // Apply automatic fixes immediately
            while (it.hasNext()) {
                Problem problem = it.next();
                Optional<Solution> solution = problem.getOnLoadAutofixSolution();
                if (solution.isPresent()) {
                    state = solution.get().apply(state, now);
                }
            }
            return state;
        }
    };

    private final class OnChange implements Runnable {

        @Override
        public void run() {
            Map<EditProblem.Type, List<EditProblem>> newProblems = AllSuggestions.getEditProblems(edit)
                    .collect(Collectors.groupingBy(EditProblem::getType));
            // Apply automatic fixes immediately
            Iterator<EditSolution> it = newProblems.getOrDefault(EditProblem.Type.OnChange, Collections.emptyList())
                    .stream()
                    .flatMap(EditProblem::getSolutions)
                    .iterator();
            String now = ISO8601.now();
            while (it.hasNext()) {
                EditSolution solution = it.next();
                solution.solve(edit, now);
            }
            List<EditProblem> manualFix = newProblems.getOrDefault(EditProblem.Type.Manual, Collections.emptyList());
            problems.set(manualFix);
            updateDisplay.run();
        }
    };

    private final class UpdateDisplay implements Runnable {

        @Override
        public void run() {
            Collection children = parent.getChildren();
            children.clear();
            problems.get().stream()
                    .map(problem -> toNode(problem))
                    .collect(Collectors.toCollection(() -> children));
        }

    };

    private Node toNode(EditProblem problem) {
        Pane container = new VBox();
        container.getStyleClass().add("suggestion");

        Label description = new Label(problem.getDescription());
        description.setWrapText(true);
        container.getChildren().add(description);

        Pane buttonContainer = new HBox();
        container.getChildren().add(buttonContainer);

        problem.getSolutions()
                .map((solution) -> {
                    Button button = new Button(solution.getDescription());
                    button.setOnAction(event -> {
                        solution.solve(edit, ISO8601.now());
                        event.consume();
                    });
                    button.setDisable(!solution.isEnabled());
                    return button;
                })
                .forEachOrdered((button) -> {
                    buttonContainer.getChildren().add(button);
                });
        return container;
    }
}
