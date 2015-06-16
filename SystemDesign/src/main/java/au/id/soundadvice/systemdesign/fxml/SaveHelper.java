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
import au.id.soundadvice.systemdesign.files.Directory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class SaveHelper {

    public static void tryExit(Window owner, EditState edit) {
        if (checkSave(owner, edit, "Save before closing?")) {
            System.exit(0);
        }
    }

    public static boolean checkSave(Window owner, EditState edit, String description) {
        if (edit.saveNeeded()) {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            alert.setTitle(description);
            alert.setHeaderText(description);

            alert.getButtonTypes().setAll(
                    ButtonType.CANCEL, ButtonType.NO, ButtonType.YES);

            Optional<ButtonType> result = alert.showAndWait();
            if (ButtonType.YES.equals(result.get())) {
                return trySave(owner, edit);
            } else {
                // If no, success. If cancel, failed.
                return ButtonType.NO.equals(result.get());
            }
        } else {
            // Save not needed
            return true;
        }
    }

    public static boolean trySave(Window owner, EditState edit) {
        try {
            if (edit.getCurrentDirectory() == null) {
                return trySaveAs(owner, edit);
            } else {
                edit.save();
            }
            return true;
        } catch (IOException ex) {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Save Failed");
            alert.setHeaderText("Save Failed");
            alert.setContentText(ex.toString());

            alert.showAndWait();
            return false;
        }
    }

    public static boolean trySaveAs(Window owner, EditState edit) throws IOException {
        try {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("System Designs");
            File selectedDirectory = chooser.showDialog(owner);
            if (selectedDirectory == null) {
                return false;
            } else {
                edit.saveTo(new Directory(Paths.get(selectedDirectory.getPath())));
                return true;
            }
        } catch (IOException ex) {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Save Failed");
            alert.setHeaderText("Save Failed");
            alert.setContentText(ex.toString());

            alert.showAndWait();
            return false;
        }
    }

    public static boolean tryLoad(EditState edit, Directory dir) {
        try {
            edit.load(dir);
            return true;
        } catch (IOException ex) {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Load Failed");
            alert.setHeaderText("Load Failed");
            alert.setContentText(ex.toString());

            alert.showAndWait();
            return false;
        }
    }

    public static boolean tryLoadChooser(Window owner, EditState edit) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("System Designs");
        File selectedDirectory = chooser.showDialog(owner);
        if (selectedDirectory == null) {
            return false;
        } else {
            Directory dir = new Directory(Paths.get(selectedDirectory.getPath()));
            return tryLoad(edit, dir);
        }
    }
}
