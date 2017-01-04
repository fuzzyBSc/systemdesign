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
package au.id.soundadvice.systemdesign.physical.drawing;

import au.id.soundadvice.systemdesign.moduleapi.entity.ConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.drawing.DrawingConnector;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.DiffPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.collection.RecordConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.physical.Interface;
import java.util.Objects;
import java.util.Optional;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
class PhysicalSchematicInterface implements DrawingConnector {

    @Override
    public boolean isDiff() {
        return iface.isDiff();
    }

    @Override
    public boolean isChanged() {
        return iface.isChanged();
    }

    @Override
    public boolean isAdded() {
        return iface.isAdded()
                && !wasBaselineContainsIsScope();
    }

    @Override
    public boolean isDeleted() {
        return iface.isDeleted()
                && !isBaselineContainsWasScope();
    }

    private boolean wasBaselineContainsIsScope() {
        Optional<Baseline> wasBaseline = iface.getWasBaseline();
        if (!wasBaseline.isPresent()) {
            return false;
        }
        Optional<Record> isInterface = iface.getIsInstance();
        if (!isInterface.isPresent()) {
            return true;
        }
        RecordConnectionScope isScope = Interface.iface.getItems(
                iface.getIsBaseline(), isInterface.get());
        Optional<Record> wasInterface = Interface.get(iface.getWasBaseline().get(), isScope);
        return wasInterface.isPresent();
    }

    private boolean isBaselineContainsWasScope() {
        Optional<Baseline> wasBaseline = iface.getWasBaseline();
        if (!wasBaseline.isPresent()) {
            return false;
        }
        Optional<Record> wasInterface = iface.getIsInstance();
        if (!wasInterface.isPresent()) {
            return true;
        }
        RecordConnectionScope wasScope = Interface.iface.getItems(
                iface.getWasBaseline().get(), wasInterface.get());
        Optional<Record> isInterface = Interface.get(iface.getIsBaseline(), wasScope);
        return isInterface.isPresent();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.iface);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PhysicalSchematicInterface other = (PhysicalSchematicInterface) obj;
        if (!Objects.equals(this.iface, other.iface)) {
            return false;
        }
        return true;
    }

    private final DiffPair<Record> iface;

    public PhysicalSchematicInterface(DiffPair<Record> iface) {
        this.iface = iface;
    }

    @Override
    public ConnectionScope getScope() {
        return iface.getSample().getConnectionScope();
    }

    @Override
    public DiffPair<String> getLabel() {
        return iface.map(Record::getShortName);
    }

    @Override
    public RecordID getIdentifier() {
        return iface.getSample().getIdentifier();
    }

}
