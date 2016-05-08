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
package au.id.soundadvice.systemdesign;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import au.id.soundadvice.systemdesign.storage.files.Merge;
import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.fxml.Interactions;
import au.id.soundadvice.systemdesign.fxml.MainController;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordType;
import au.id.soundadvice.systemdesign.storage.files.RecordReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.Executors;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.util.Callback;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class SystemDesign extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        Image icon = new Image(SystemDesign.class.getResource("/systemdesign.png").toString());
        stage.getIcons().add(icon);

        EditState edit = EditState.init(Executors.newCachedThreadPool());
        Interactions interactions = new Interactions(stage, edit);

        Callback<Class<?>, Object> controllerFactory = (Class<?> param) -> {
            if (param.equals(MainController.class)) {
                return new MainController(interactions, edit);
            } else {
                try {
                    return param.newInstance();
                } catch (InstantiationException | IllegalAccessException ex) {
                    throw new AssertionError(ex);
                }
            }
        };

        Parent root = FXMLLoader.load(
                getClass().getResource("/fxml/Main.fxml"),
                null, null, controllerFactory);

        stage.setOnCloseRequest(event -> {
            interactions.tryExit();
            event.consume();
        });

        Scene scene = new Scene(root);
        stage.setTitle("System Design");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args.length > 0 && args[0].endsWith("-merge")) {
            if (args.length == 4) {
                try {
                    Path ancestorFile = Paths.get(args[1]);
                    Path leftFile = Paths.get(args[2]);
                    Path rightFile = Paths.get(args[3]);
                    Path tmpFile = Files.createTempFile("merge", "");
                    Path outputFile = leftFile;
                    RecordType dummyType = new RecordType.Default("");
                    try (
                            BufferedReader ancestor = Files.newBufferedReader(ancestorFile);
                            BufferedReader left = Files.newBufferedReader(leftFile);
                            BufferedReader right = Files.newBufferedReader(rightFile);
                            CSVReader ancestorCSVReader = new CSVReader(ancestor);
                            CSVReader leftCSVReader = new CSVReader(left);
                            CSVReader rightCSVReader = new CSVReader(right);
                            RecordReader ancestorReader = new RecordReader(dummyType, ancestorCSVReader);
                            RecordReader leftReader = new RecordReader(dummyType, leftCSVReader);
                            RecordReader rightReader = new RecordReader(dummyType, rightCSVReader);
                            CSVWriter out = new CSVWriter(Files.newBufferedWriter(
                                    tmpFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                        Merge.threeWayCSV(ancestorReader, leftReader, rightReader, out);
                        // Close to flush
                        out.close();
                        // Now we are supposed to overwrite left with the result of our merge
                        Files.move(tmpFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
                        // All done
                        System.exit(0);
                    }
                } catch (IOException ex) {
                    System.err.println(ex.toString());
                    System.exit(1);
                }
            } else {
                System.out.println("Unexpected arguments " + Arrays.toString(args));
                System.exit(1);
            }
        } else {
            launch(args);
        }
    }

}
