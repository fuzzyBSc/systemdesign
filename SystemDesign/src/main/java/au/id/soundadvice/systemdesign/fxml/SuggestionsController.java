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

import au.id.soundadvice.systemdesign.baselines.EditState;
import au.id.soundadvice.systemdesign.baselines.UndoState;
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.consistency.Problem;
import au.id.soundadvice.systemdesign.consistency.ProblemFactory;
import au.id.soundadvice.systemdesign.undo.UndoBuffer;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javafx.event.ActionEvent;
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

    public SuggestionsController(EditState edit, Pane parent, ProblemFactory factory) {
        this.parent = parent;
        this.undo = edit.getUndo();
        this.onChange = new SingleRunnable(edit.getExecutor(), new OnChange());
        this.factory = factory;
    }

    public void start() {
        undo.getChanged().subscribe(onChange);
        onChange.run();
    }

    private final Pane parent;
    private final UndoBuffer<UndoState> undo;
    private final ProblemFactory factory;
    private final SingleRunnable<OnChange> onChange;
    private final AtomicReference<Collection<Problem>> problems = new AtomicReference<>();
    private final SingleRunnable<UpdateDisplay> updateDisplay
            = new SingleRunnable(JFXExecutor.instance(), new UpdateDisplay());

    private final class OnChange implements Runnable {

        @Override
        public void run() {
            problems.set(factory.getProblems(undo.get()));
            updateDisplay.run();
        }

    };

    private final class UpdateDisplay implements Runnable {

        @Override
        public void run() {
            Collection children = parent.getChildren();
            children.clear();
            problems.get().stream()
                    .map((problem) -> toNode(problem))
                    .collect(Collectors.toCollection(() -> children));
        }

    };

    private Node toNode(Problem problem) {
        Pane container = new VBox();
        container.getStyleClass().add("suggestion");

        Label description = new Label(problem.getDescription());
        container.getChildren().add(description);

        Pane buttonContainer = new HBox();
        container.getChildren().add(buttonContainer);

        problem.getSolutions().stream()
                .map((solution) -> {
                    Button button = new Button(solution.getDescription());
                    button.setOnAction((ActionEvent e) -> {
                        undo.set(solution.solve(undo.get()));
                    });
                    return button;
                })
                .forEachOrdered((button) -> {
                    buttonContainer.getChildren().add(button);
                });
        return container;
    }
}
