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
package au.id.soundadvice.systemdesign.logical.interactions;

import au.id.soundadvice.systemdesign.logical.drawing.LogicalSchematic;
import au.id.soundadvice.systemdesign.logical.entity.Flow;
import au.id.soundadvice.systemdesign.logical.entity.FlowType;
import au.id.soundadvice.systemdesign.logical.entity.Function;
import au.id.soundadvice.systemdesign.logical.entity.FunctionView;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.interaction.InteractionContext;
import java.util.stream.Stream;
import au.id.soundadvice.systemdesign.moduleapi.interaction.MenuItems;
import au.id.soundadvice.systemdesign.moduleapi.util.ISO8601;
import au.id.soundadvice.systemdesign.physical.entity.Item;
import au.id.soundadvice.systemdesign.physical.entity.ItemView;
import au.id.soundadvice.systemdesign.physical.interactions.PhysicalInteractions;
import java.util.Optional;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class LogicalContextMenus {

    public LogicalContextMenus(
            PhysicalInteractions physicalInteractions,
            LogicalInteractions logicalInteractions) {
        this.physicalInteractions = physicalInteractions;
        this.logicalInteractions = logicalInteractions;
    }

    private final PhysicalInteractions physicalInteractions;
    private final LogicalInteractions logicalInteractions;

    public MenuItems getFunctionContextMenu(Record function) {
        return new FunctionContextMenu(function);
    }

    public MenuItems getFunctionViewContextMenu(Record view) {
        return new FunctionViewContextMenu(view);
    }

    public MenuItems getDeletedFunctionContextMenu(Record function) {
        return new FunctionContextMenu(function);
    }

    public MenuItems getFlowContextMenu(Record flow) {
        return new FlowContextMenu(flow);
    }

    public MenuItems getDeletedFlowContextMenu(Record flow) {
        return new FlowContextMenu(flow);
    }

    public MenuItems getLogicalSchematicBackgroundMenu(LogicalSchematic drawing) {
        return new AddFunctionSubmenu(Optional.of(drawing));
    }

    public MenuItems getLogicalTreeBackgroundMenu() {
        return new AddFunctionSubmenu(Optional.empty());
    }

    public MenuItems getTypeTreeBackgroundMenu() {
        return new TypeContextMenu(Optional.empty());
    }

    public MenuItems getTypeContextMenu(Record sample) {
        return new TypeContextMenu(Optional.of(sample));
    }

    class FunctionContextMenu implements MenuItems {

        public FunctionContextMenu(Record function) {
            this.function = function;
        }

        private final Record function;

        @Override
        public Stream<MenuItems.MenuItem> items(InteractionContext context) {
            if (function.isExternal()) {
                return Stream.of(new MenuItems.SingleMenuItem(
                        "Delete External Function",
                        () -> {
                            context.updateChild(child -> child.remove(function.getIdentifier()));
                        }));
            } else {
                return Stream.of(new MenuItems.SingleMenuItem(
                        "Rename Function...",
                        () -> {
                            logicalInteractions.setFunctionName(context, function);
                        }),
                        new MenuItems.SingleMenuItem(
                                "Navigate Down",
                                () -> {
                                    Baseline baseline = context.getChild();
                                    Record item = Function.function.getItemForFunction(baseline, function);
                                    context.navigateDown(item, Optional.of(function));
                                }),
                        new MenuItems.SingleMenuItem(
                                "Delete Function",
                                () -> {
                                    context.updateChild(child -> child.remove(function.getIdentifier()));
                                }));

            }
        }
    }

    class FunctionViewContextMenu implements MenuItems {

        public FunctionViewContextMenu(Record view) {
            this.view = view;
        }

        private final Record view;

        @Override
        public Stream<MenuItems.MenuItem> items(InteractionContext context) {
            Record function;
            boolean foreign;
            {
                Baseline baseline = context.getChild();
                function = FunctionView.functionView.getFunction(baseline, view);
                foreign = FunctionView.functionView.isForeign(baseline, view);
            }

            if (function.isExternal()) {
                return Stream.of(new MenuItems.SingleMenuItem(
                        "Delete External Function",
                        () -> {
                            context.updateChild(child -> child.remove(function.getIdentifier()));
                        }));
            } else if (foreign) {
                // This view is foreign to the function's trace
                // Deleting the view here merely deletes the view
                return Stream.of(new MenuItems.SingleMenuItem(
                        "Rename Function...",
                        () -> {
                            logicalInteractions.setFunctionName(context, function);
                        }),
                        new MenuItems.SingleMenuItem(
                                "Navigate Down",
                                () -> {
                                    Baseline baseline = context.getChild();
                                    Record item = Function.function.getItemForFunction(baseline, function);
                                    context.navigateDown(item, Optional.of(function));
                                }),
                        new MenuItems.SingleMenuItem(
                                "Delete View",
                                () -> {
                                    context.updateChild(child -> child.remove(view.getIdentifier()));
                                }));
            } else {
                // This drawing is the "home" of this function. Deleting the view
                // here implies deleting the function.
                return Stream.of(new MenuItems.SingleMenuItem(
                        "Rename Function...",
                        () -> {
                            logicalInteractions.setFunctionName(context, function);
                        }),
                        new MenuItems.SingleMenuItem(
                                "Navigate Down",
                                () -> {
                                    Baseline baseline = context.getChild();
                                    Record item = Function.function.getItemForFunction(baseline, function);
                                    context.navigateDown(item, Optional.of(function));
                                }),
                        new MenuItems.SingleMenuItem(
                                "Delete Function",
                                () -> {
                                    context.updateChild(child -> child.remove(function.getIdentifier()));
                                }));
            }
        }
    }

    class DeletedFunctionContextMenu implements MenuItems {

        public DeletedFunctionContextMenu(Record func) {
            this.func = func;
        }

        private final Record func;

        @Override
        public Stream<MenuItems.MenuItem> items(InteractionContext context) {
            return Stream.of(new MenuItems.SingleMenuItem(
                    "Restore Function",
                    () -> context.restoreDeleted(func)));
        }
    }

    class FlowContextMenu implements MenuItems {

        public FlowContextMenu(Record flow) {
            this.flow = flow;
        }

        private final Record flow;

        @Override
        public Stream<MenuItems.MenuItem> items(InteractionContext context) {
            MenuItems setType = new SetFlowTypeSubmenu(flow);
            return Stream.concat(
                    setType.items(context),
                    Stream.of(new MenuItems.SingleMenuItem(
                            "Delete Flow",
                            () -> {
                                context.updateChild(child -> child.remove(flow.getIdentifier()));
                            })));
        }
    }

    class SetFlowTypeSubmenu implements MenuItems {

        public SetFlowTypeSubmenu(Record flowSample) {
            this.flowSample = flowSample;
        }

        private final Record flowSample;

        @Override
        public Stream<MenuItems.MenuItem> items(InteractionContext context) {
            return Stream.of(MenuItems.Submenu.of(
                    "Set Type",
                    () -> {
                        Stream<MenuItems.MenuItem> dynamicItems = FlowType.find(context.getChild())
                        .sorted()
                        .map(flowTypeSample -> {
                            return new MenuItems.SingleMenuItem(
                                    FlowType.flowType.getDisplayName(flowTypeSample),
                                    () -> {
                                        String now = ISO8601.now();
                                        context.updateChild(child -> {
                                            Optional<Record> flow = child.get(flowSample);
                                            Optional<Record> flowType = child.get(flowTypeSample);
                                            if (flow.isPresent() && flowType.isPresent()) {
                                                return Flow.flow.setFlowType(child, flow.get(), flowType.get(), now)
                                                        .getKey();
                                            } else {
                                                return child;
                                            }
                                        });
                                    }
                            );
                        });
                        Stream<MenuItems.MenuItem> staticItems = Stream.of(
                                new MenuItems.SingleMenuItem(
                                        "Rename Type...",
                                        () -> logicalInteractions.renameFlowType(context, flowSample)
                                ),
                                new MenuItems.SingleMenuItem(
                                        "New Type...",
                                        () -> logicalInteractions.createAndSetFlowType(context, flowSample)
                                ));
                        return Stream.concat(dynamicItems, staticItems);
                    }));
        }
    }

    class DeletedFlowContextMenu implements MenuItems {

        public DeletedFlowContextMenu(Record flow) {
            this.flow = flow;
        }

        private final Record flow;

        @Override
        public Stream<MenuItems.MenuItem> items(InteractionContext context) {
            return Stream.of(new MenuItems.SingleMenuItem(
                    "Restore Flow",
                    () -> context.restoreDeleted(flow)));
        }
    }

    class AddFunctionSubmenu implements MenuItems {

        public AddFunctionSubmenu(Optional<LogicalSchematic> drawing) {
            this.drawing = drawing;
        }

        private final Optional<LogicalSchematic> drawing;

        @Override
        public Stream<MenuItems.MenuItem> items(InteractionContext context) {
            return Stream.of(MenuItems.Submenu.of(
                    "Add Function to...",
                    () -> {
                        WhyHowPair<Baseline> state = context.getState();
                        Optional<Record> trace = drawing.flatMap(
                                schematic -> schematic.getTraceFunction(state));
                        Optional<Record> drawingRecord = drawing.map(
                                schematic -> schematic.getDrawingRecord());
                        Stream<MenuItems.MenuItem> dynamicItems = Item.find(state.getChild())
                        .filter(item -> !item.isExternal())
                        .sorted()
                        .map(item -> {
                            return new MenuItems.SingleMenuItem(
                                    Item.item.getDisplayName(item),
                                    hints -> {
                                        logicalInteractions.addFunctionToItem(
                                                context, item, trace, drawingRecord,
                                                hints.getLocationHint().orElse(FunctionView.DEFAULT_ORIGIN));
                                    }
                            );
                        });
                        Stream<MenuItems.MenuItem> staticItems = Stream.of(new MenuItems.SingleMenuItem(
                                "New Item",
                                hints -> {
                                    Optional<Record> item = physicalInteractions.createItem(context, ItemView.DEFAULT_ORIGIN);
                                    if (item.isPresent()) {
                                        logicalInteractions.addFunctionToItem(
                                                context, item.get(), trace, drawingRecord,
                                                hints.getLocationHint().orElse(FunctionView.DEFAULT_ORIGIN));
                                    }
                                }
                        ));
                        return Stream.concat(dynamicItems, staticItems);
                    }));
        }
    }

    class TypeContextMenu implements MenuItems {

        public TypeContextMenu(Optional<Record> sample) {
            this.sample = sample;
        }

        private final Optional<Record> sample;

        @Override
        public Stream<MenuItems.MenuItem> items(InteractionContext context) {
            if (sample.isPresent()) {
                return Stream.of(
                        new MenuItems.SingleMenuItem(
                                "Rename Type...",
                                () -> {
                                    logicalInteractions.renameType(context, sample.get());
                                }),
                        new MenuItems.SingleMenuItem(
                                "Delete Type",
                                () -> {
                                    context.updateChild(child -> child.remove(sample.get().getIdentifier()));
                                }));
            } else {
                return Stream.of(
                        new MenuItems.SingleMenuItem(
                                "New Type...",
                                () -> {
                                    logicalInteractions.createType(context);
                                })
                );
            }

        }
    }

}
