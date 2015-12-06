/*
 * To change this license header, choose License Headers in Project Properties.
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
import au.id.soundadvice.systemdesign.versioning.VersionControl;
import au.id.soundadvice.systemdesign.versioning.VersionInfo;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class VersionMenuController {

    private static final Logger LOG = Logger.getLogger(VersionMenuController.class.getName());

    private final EditState edit;
    private final Menu diffBranchMenu;
    private final Menu diffVersionMenu;
    private final MenuItem diffNoneMenuItem;

    public VersionMenuController(
            EditState edit,
            Menu diffBranchMenu, Menu diffVersionMenu, MenuItem diffNoneMenuItem) {
        this.edit = edit;
        this.diffBranchMenu = diffBranchMenu;
        this.diffVersionMenu = diffVersionMenu;
        this.diffNoneMenuItem = diffNoneMenuItem;
    }

    private static void startMenu(
            EditState edit, Menu menu,
            Function<VersionControl, Stream<VersionInfo>> getter) {
        ContextMenus.initPerInstanceSubmenu(
                menu,
                () -> getter.apply(edit.getVersionControl())
                .sorted((a, b) -> -a.getTimestamp().compareTo(b.getTimestamp())),
                VersionInfo::getDescription,
                (e, versionInfo) -> {
                    edit.setDiffVersion(Optional.of(versionInfo));
                    e.consume();
                },
                Optional.empty());
    }

    public void start() {
        startMenu(edit, diffBranchMenu, versionControl -> {
            try {
                return versionControl.getBranches();
            } catch (IOException ex) {
                LOG.log(Level.WARNING, null, ex);
                return Stream.empty();
            }
        });
        startMenu(edit, diffVersionMenu, versionControl -> {
            try {
                return versionControl.getVersions();
            } catch (IOException ex) {
                LOG.log(Level.WARNING, null, ex);
                return Stream.empty();
            }
        });
        diffNoneMenuItem.setOnAction(e2 -> {
            edit.setDiffVersion(Optional.empty());
        });
    }

}
