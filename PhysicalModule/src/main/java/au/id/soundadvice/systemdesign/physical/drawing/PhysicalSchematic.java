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

import au.id.soundadvice.systemdesign.moduleapi.drawing.Drawing;
import au.id.soundadvice.systemdesign.moduleapi.drawing.DrawingConnector;
import au.id.soundadvice.systemdesign.moduleapi.drawing.DrawingEntity;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.DiffPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.physical.entity.Identity;
import au.id.soundadvice.systemdesign.physical.entity.Interface;
import au.id.soundadvice.systemdesign.physical.entity.ItemView;
import au.id.soundadvice.systemdesign.physical.interactions.PhysicalContextMenus;
import au.id.soundadvice.systemdesign.physical.interactions.PhysicalInteractions;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A physical Item. Item is used as a fairly loose term in the model and could
 * mean system, subsystem, configuration item, or correspond to a number of
 * standards-based concepts. As far as the model goes it identifies something
 * that exists rather than dealing with what a thing does. Items are at the root
 * of how the model is put together. Each item either is or has the potential to
 * be a whole director unto itself containing other conceptual elements.
 *
 * An item is typically an entire system, an assembly of parts, or a hardware or
 * software configuration item. The kind of existence required of it can be
 * abstract. For software it could end up a unit of software installed under a
 * single software package, a name space or class.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class PhysicalSchematic implements Drawing {

    private final PhysicalInteractions interactions;
    private final PhysicalContextMenus menus;
    private final Record identity;
    private final List<DrawingEntity> entities;
    private final List<DrawingConnector> connectors;

    public PhysicalSchematic(
            PhysicalInteractions interactions,
            PhysicalContextMenus menus,
            DiffPair<Baseline> baselines) {
        this.interactions = interactions;
        this.menus = menus;
        this.identity = Identity.get(baselines.getSample());

        this.entities = DiffPair.find(baselines, ItemView::find, ItemView.itemView)
                .map(view -> new PhysicalSchematicItem(interactions, menus, view))
                .collect(Collectors.toList());
        this.connectors = DiffPair.find(baselines,
                baseline -> Interface.find(baseline)
                .filter(iface -> !iface.getConnectionScope().isSelfConnection()),
                Interface.iface)
                .map(iface -> new PhysicalSchematicInterface(menus, iface))
                .collect(Collectors.toList());
    }

    @Override
    public String getTitle() {
        return identity.getLongName();
    }

    @Override
    public Stream<DrawingEntity> getEntities() {
        return entities.stream();
    }

    @Override
    public Stream<DrawingConnector> getConnectors() {
        return connectors.stream();
    }

    @Override
    public RecordID getIdentifier() {
        return identity.getIdentifier();
    }

    @Override
    public Optional<Record> getDragDropObject() {
        return Optional.empty();
    }

}
