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
package au.id.soundadvice.systemdesign.model;

import au.id.soundadvice.systemdesign.beans.IdentityBean;
import au.id.soundadvice.systemdesign.beans.ItemBean;
import au.id.soundadvice.systemdesign.files.BeanFile;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.files.FileUtils;
import au.id.soundadvice.systemdesign.files.SaveTransaction;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class AllocatedBaselineTest {

    /**
     * Test of load method, of class AllocatedBaseline.
     *
     * @throws java.lang.Exception
     */
    @Test
    public void testEmpty() throws Exception {
        System.out.println("load");
        Path repo = Paths.get("/tmp/repo");
        Directory modelDirectory = new Directory(repo.resolve("model"));
        Directory systemDirectory = new Directory(modelDirectory.getPath().resolve("system"));
        Directory subsystemDirectory = new Directory(systemDirectory.getPath().resolve("subsystem"));
        FileUtils.recursiveDelete(repo);
        Files.createDirectories(subsystemDirectory.getPath());
        try (SaveTransaction transaction = new SaveTransaction()) {
            BeanFile.saveBean(transaction, modelDirectory.getIdentityFile(), new IdentityBean(
                    UUID.randomUUID(), "", "model"));
            BeanFile.saveBean(transaction, systemDirectory.getIdentityFile(), new IdentityBean(
                    UUID.randomUUID(), "C1234", "system"));
            BeanFile.saveBean(transaction, subsystemDirectory.getIdentityFile(), new IdentityBean(
                    UUID.randomUUID(), "C1234.1", "subsystem"));
            transaction.commit();
        }

        System.out.println("Load from nonexistant");
        Baseline baseline = Baseline.load(subsystemDirectory);
        assertFalse(baseline.getItems().iterator().hasNext());
        assertFalse(baseline.getInterfaces().iterator().hasNext());
        assertFalse(baseline.getFunctions().iterator().hasNext());
        assertFalse(baseline.getFlows().iterator().hasNext());

        System.out.println("Load from empty");
        try (SaveTransaction transaction = new SaveTransaction()) {
            baseline.saveTo(transaction, subsystemDirectory);
            transaction.commit();
        }
        baseline = Baseline.load(subsystemDirectory);
        assertFalse(baseline.getItems().iterator().hasNext());
        assertFalse(baseline.getInterfaces().iterator().hasNext());
        assertFalse(baseline.getFunctions().iterator().hasNext());
        assertFalse(baseline.getFlows().iterator().hasNext());
    }

    @Test
    public void testSubsystem() throws IOException {
        Path repo = Paths.get("/tmp/repo");
        Directory modelDirectory = new Directory(repo.resolve("model"));
        Directory systemDirectory = new Directory(modelDirectory.getPath().resolve("system"));
        Directory subsystemDirectory = new Directory(systemDirectory.getPath().resolve("subsystem"));
        FileUtils.recursiveDelete(repo);
        Files.createDirectories(subsystemDirectory.getPath());

        Baseline model = Baseline.create(Identity.create());

        Item systemOfInterest = new Item(
                model.getIdentity().getUuid(), new ItemBean(
                        UUID.randomUUID(), "C1234", "system", false));
        model = model.add(systemOfInterest);

        Baseline system = Baseline.create(
                systemOfInterest.asIdentity(model));

        Item subsystemOfInterest = new Item(
                system.getIdentity().getUuid(), new ItemBean(
                        UUID.randomUUID(), "1", "subsystem", false));
        system = system.add(subsystemOfInterest);

        Baseline subsystem = Baseline.create(
                subsystemOfInterest.asIdentity(system));
        for (int ii = 0; ii < 10; ++ii) {
            Item item = new Item(
                    subsystem.getIdentity().getUuid(), new ItemBean(
                            UUID.randomUUID(), Integer.toString(ii),
                            "subsystem " + ii, false));
            subsystem = subsystem.add(item);
        }

        try (SaveTransaction transaction = new SaveTransaction()) {
            model.saveTo(transaction, modelDirectory);
            system.saveTo(transaction, systemDirectory);
            subsystem.saveTo(transaction, subsystemDirectory);
            transaction.commit();
        }
    }
}