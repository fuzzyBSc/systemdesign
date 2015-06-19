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
package au.id.soundadvice.systemdesign.consistency;

import au.id.soundadvice.systemdesign.baselines.EditState;
import au.id.soundadvice.systemdesign.baselines.FunctionalBaseline;
import au.id.soundadvice.systemdesign.baselines.UndoState;
import au.id.soundadvice.systemdesign.beans.Direction;
import au.id.soundadvice.systemdesign.model.ConnectionScope;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.relation.RelationStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class ExternalItemMismatch implements ProblemFactory {

    @Override
    public Stream<Problem> getProblems(EditState edit) {
        UndoState state = edit.getUndo().get();
        FunctionalBaseline functional = state.getFunctional();
        if (functional == null) {
            return Stream.empty();
        }
        UUID contextId = functional.getSystemOfInterest().getUuid();
        RelationStore parentContext = functional.getStore();
        RelationStore childContext = state.getAllocated().getStore();

        Stream<Problem> childProblems = parentContext.getReverse(contextId, Interface.class)
                .map((iface) -> {
                    Item parentExternal;
                    // Obtain connected item from parent
                    Item left = iface.getLeft().getTarget(parentContext);
                    Item right = iface.getRight().getTarget(parentContext);
                    if (contextId.equals(left.getUuid())) {
                        parentExternal = right.asExternal(parentContext);
                    } else {
                        parentExternal = left.asExternal(parentContext);
                    }
                    Item allocatedExternal = childContext.get(
                            parentExternal.getUuid(), Item.class);
                    if (allocatedExternal == null) {
                        String name = parentExternal.getDisplayName();
                        String parentId = functional.getSystemOfInterest().getIdPath(parentContext).toString();
                        return new Problem(
                                name + " is missing in " + parentId, Arrays.asList(
                                        new SingleRelationSolution[]{
                                            SingleRelationSolution.addToChild("Flow down", parentExternal),
                                            SingleRelationSolution.removeFromParent("Flow up", iface)}));
                    } else if (!allocatedExternal.isConsistent(parentExternal)) {
                        String name = parentExternal.getDisplayName();
                        return new Problem(
                                name + " differs between baselines", Arrays.asList(
                                        new SingleRelationSolution[]{
                                            SingleRelationSolution.addToChild("Flow down",
                                                    allocatedExternal.makeConsistent(parentExternal)),
                                            SingleRelationSolution.addToParent("Flow up",
                                                    parentExternal.makeConsistent(allocatedExternal))}));
                    } else {
                        return null;
                    }
                })
                .filter((problem) -> problem != null);

        Stream<Problem> parentProblems = childContext.getByClass(Item.class)
                .filter((item) -> {
                    return item.isExternal() && parentContext.getReverse(contextId, Interface.class)
                    .noneMatch((iface) -> {
                        return item.getUuid().equals(iface.getLeft().getTarget(parentContext).getUuid())
                        || item.getUuid().equals(iface.getRight().getTarget(parentContext).getUuid());
                    });
                })
                .map((item) -> {
                    String name = item.getDisplayName();
                    String parentId = functional.getSystemOfInterest().getIdPath(parentContext).toString();
                    if (parentContext.get(item.getUuid(), Item.class) == null) {
                        /*
                         * The parent instance of the external item doesn't
                         * exist at all
                         */
                        return new Problem(
                                name + " is missing in " + parentId, Collections.singletonList(
                                        SingleRelationSolution.removeFromChild("Flow down", item)));
                    } else {
                        /*
                         * The parent instance exists, just not the relevant
                         * interface.
                         */
                        ConnectionScope connectionScope = new ConnectionScope(
                                item.getUuid(), contextId, Direction.Both);
                        Interface parentInterface = Interface.createNew(connectionScope);
                        return new Problem(
                                name + " is missing in " + parentId, Arrays.asList(
                                        new SingleRelationSolution[]{
                                            SingleRelationSolution.removeFromChild("Flow down", item),
                                            SingleRelationSolution.removeFromParent("Flow up", parentInterface)}));
                    }
                })
                .filter((problem) -> problem != null);

        return Stream.concat(childProblems, parentProblems);
    }
}
