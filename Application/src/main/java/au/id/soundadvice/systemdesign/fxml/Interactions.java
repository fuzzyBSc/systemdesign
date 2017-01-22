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

import au.id.soundadvice.systemdesign.fxml.drawing.PreferredTab;
import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.moduleapi.interaction.InteractionContext;
import au.id.soundadvice.systemdesign.moduleapi.storage.RecordStorage;
import au.id.soundadvice.systemdesign.preferences.RecentFiles;
import au.id.soundadvice.systemdesign.moduleapi.storage.VersionInfo;
import au.id.soundadvice.systemdesign.moduleapi.util.ISO8601;
import au.id.soundadvice.systemdesign.physical.entity.Item;
import au.id.soundadvice.systemdesign.storage.CSVStorage;
import au.id.soundadvice.systemdesign.storage.FileStorage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.EmptyStackException;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextInputDialog;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Interactions implements InteractionContext {

    private static final Logger LOG = Logger.getLogger(Interactions.class.getName());

    public Interactions(Window window, EditState edit) {
        this.window = window;
        this.edit = edit;
    }
    private final Window window;
    private final EditState edit;

    @Override
    public Optional<String> textInput(String action, String question, String _default) {
        TextInputDialog dialog = new TextInputDialog(_default);
        dialog.setTitle(action);
        dialog.setHeaderText(question);
        return dialog.showAndWait();
    }

    @Override
    public Optional<Color> colorInput(String action, String question, Color _default) {
        Dialog<Color> dialog = new Dialog<>();
        dialog.setTitle(action);
        dialog.setHeaderText(question);
        ColorPicker picker = new ColorPicker(_default);
        dialog.getDialogPane().setContent(picker);

        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType.equals(ButtonType.OK)) {
                return picker.getValue();
            } else {
                return null;
            }
        });
        return dialog.showAndWait();
    }

    public void navigateUp() {
        try {
            if (checkSave("Save before navigating?")) {
                edit.loadParent();
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    public void navigateDown() {
        try {
            navigateDown(edit.getLastChild(), Optional.empty());
        } catch (EmptyStackException ex) {
            // Nowhere to navigate down to
        }
    }

    public void navigateDown(RecordID systemOfInterestID, Optional<Record> preferredTab) {
        try {
            Optional<Record> systemOfInterest = edit.getChild().get(systemOfInterestID, Item.item);
            if (systemOfInterest.isPresent()) {
                navigateDown(systemOfInterest.get(), preferredTab);
            }
        } catch (EmptyStackException ex) {
            // Nowhere to navigate down to
        }
    }

    @Override
    public void navigateDown(Record systemOfInterest, Optional<Record> preferredTab) {
        if (checkSave("Save before navigating?")) {
            try {
                String now = ISO8601.now();
                preferredTab.ifPresent(tab -> PreferredTab.set(tab.getIdentifier()));
                edit.loadChild(systemOfInterest, now);
            } catch (IOException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Load Failed");
                alert.setHeaderText("Load Failed");
                alert.setContentText(ex.toString());

                alert.showAndWait();
            }
        }
    }

    public void tryExit() {
        if (checkSave("Save before closing?")) {
            System.exit(0);
        }
    }

    public boolean checkSave(String description) {
        if (edit.saveNeeded()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(description);
            alert.setHeaderText(description);

            alert.getButtonTypes().setAll(
                    ButtonType.CANCEL, ButtonType.NO, ButtonType.YES);

            Optional<ButtonType> result = alert.showAndWait();
            if (ButtonType.YES.equals(result.get())) {
                return trySave();
            } else {
                // If no, success. If cancel, failed.
                return ButtonType.NO.equals(result.get());
            }
        } else {
            // Save not needed
            return true;
        }
    }

    public boolean trySave() {
        try {
            if (edit.getStorage().getChild().isPresent()) {
                edit.save();
            } else {
                return trySaveAs();
            }
            return true;
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Save Failed");
            alert.setHeaderText("Save Failed");
            alert.setContentText(ex.toString());

            alert.showAndWait();
            return false;
        }
    }

    public boolean trySaveAs() throws IOException {
        try {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("System Designs");
            File selectedDirectory = chooser.showDialog(window);
            if (selectedDirectory == null) {
                return false;
            } else {
                edit.saveTo(CSVStorage.forPath(Paths.get(selectedDirectory.getPath())));
                return true;
            }
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Save Failed");
            alert.setHeaderText("Save Failed");
            alert.setContentText(ex.toString());

            alert.showAndWait();
            return false;
        }
    }

    public boolean tryLoad(EditState edit, RecordStorage dir) {
        try {
            String now = ISO8601.now();
            edit.load(dir, now);
            if (dir instanceof FileStorage) {
                FileStorage fileStorage = (FileStorage) dir;
                RecentFiles.addRecentFile(fileStorage.getPath());
            }
            Optional<VersionInfo> baseline = edit.getStorage().getChild().flatMap(RecordStorage::getDefaultBaseline);
            if (baseline.isPresent()) {
                // Open default diff baseline
                edit.setDiffVersion(baseline);
            }
            return true;
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Load Failed");
            alert.setHeaderText("Load Failed");
            alert.setContentText(ex.toString());

            alert.showAndWait();
            return false;
        }
    }

    public boolean tryLoadChooser(Window window, EditState edit) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("System Designs");
        Optional<RecordStorage> current = edit.getStorage().getChild();
        if (current.isPresent() && current.get() instanceof FileStorage) {
            FileStorage fileStorage = (FileStorage) current.get();
            chooser.setInitialDirectory(fileStorage.getPath().toFile());
        }
        File selectedDirectory = chooser.showDialog(window);
        if (selectedDirectory == null) {
            return false;
        } else {
            RecordStorage dir = CSVStorage.forPath(Paths.get(selectedDirectory.getPath()));
            return tryLoad(edit, dir);
        }
    }

    @Override
    public void updateState(UnaryOperator<WhyHowPair<Baseline>> mutator) {
        edit.updateState(mutator);
    }

    @Override
    public void updateParent(UnaryOperator<Baseline> mutator) {
        edit.updateParent(mutator);
    }

    @Override
    public void updateChild(UnaryOperator<Baseline> mutator) {
        edit.updateChild(mutator);
    }

    @Override
    public WhyHowPair<Baseline> getState() {
        return edit.getState();
    }

    @Override
    public Baseline getParent() {
        return edit.getParent();
    }

    @Override
    public Baseline getChild() {
        return edit.getChild();
    }

    @Override
    public Optional<Baseline> getWas() {
        return edit.getDiffBaseline();
    }

    @Override
    public void restoreDeleted(Record sample) {
        edit.restoreDeleted(sample);
    }
}
