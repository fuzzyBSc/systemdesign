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

import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.model.Budget;
import au.id.soundadvice.systemdesign.model.BudgetAllocation;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.model.Range;
import au.id.soundadvice.systemdesign.model.UndoState;
import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class BudgetTreeController {

    private static final Logger LOG = Logger.getLogger(BudgetTreeController.class.getName());

    public BudgetTreeController(
            Interactions interactions, EditState edit,
            TreeView<BudgetSummary> view) {
        this.edit = edit;
        this.view = view;
        this.interactions = interactions;
        this.updateState = new SingleRunnable<>(edit.getExecutor(), new UpdateState());
        this.updateView = new SingleRunnable<>(JFXExecutor.instance(), new UpdateView());
    }

    public void start() {
        addContextMenu();
        edit.subscribe(updateState);
        updateState.run();
    }

    public void stop() {
        edit.unsubscribe(updateState);
    }

    private void addContextMenu() {
        view.setContextMenu(
                ContextMenus.budgetTreeBackgroundMenu(interactions));
    }

    private class TreeState {

        private final Map<Budget.Key, BudgetSummary> functionalBudgets;
        private final Map<Budget.Key, List<BudgetSummary>> allocatedBudgets;

        private TreeState(
                Map<Budget.Key, BudgetSummary> functionalBudgets,
                Map<Budget.Key, List<BudgetSummary>> allocatedBudgets) {
            this.functionalBudgets = functionalBudgets;
            this.allocatedBudgets = allocatedBudgets;
        }

    }

    private class UpdateState implements Runnable {

        @Override
        public void run() {
            UndoState state = edit.getUndo().get();
            Baseline functional = state.getFunctional();
            Baseline allocated = state.getAllocated();
            Optional<Item> system = state.getSystemOfInterest();

            List<Budget> allBudgets = Budget.find(allocated)
                    .collect(Collectors.toList());
            List<Item> internalItems = Item.find(allocated)
                    .filter(item -> !item.isExternal())
                    .collect(Collectors.toList());

            Map<Budget.Key, BudgetSummary> parentAmounts;
            {
                Stream<BudgetSummary> zeroAmounts = allBudgets.stream()
                        .map(budget -> {
                            return new BudgetSummary(
                                    budget.getKey(),
                                    Optional.empty(),
                                    Range.ZERO);
                        });
                Stream<BudgetSummary> realAmounts = system
                        .map(item -> item.findBudgetAllocations(functional)
                                .map(allocation -> {
                                    return new BudgetSummary(
                                            allocation.getBudget(functional).getKey(),
                                            Optional.empty(),
                                            allocation.getAmount());
                                }))
                        .orElse(Stream.empty());
                parentAmounts = Stream.concat(zeroAmounts, realAmounts)
                        .collect(Collectors.groupingBy(
                                        BudgetSummary::getKey,
                                        Collectors.reducing(
                                                BudgetSummary.ZERO,
                                                BudgetSummary::add)));
            }

            Map<Budget.Key, List<BudgetSummary>> childAmounts;
            {
                // Start with a fake zero budget for the cross-product of key and item
                Stream<BudgetSummary> zeroAmounts = allBudgets.stream()
                        .map(Budget::getKey)
                        .flatMap(key -> {
                            return internalItems.stream()
                            .flatMap(item -> {
                                return Stream.of(new BudgetSummary(
                                                key, Optional.of(item), Range.ZERO));
                            });
                        });

                // Add the real budgets in
                Stream<BudgetSummary> realAmounts = Budget.find(allocated)
                        .flatMap(budget -> {
                            return budget.findAllocations(allocated)
                            .map(allocation -> {
                                Item item = allocation.getItem(allocated);
                                return new BudgetSummary(
                                        budget.getKey(),
                                        Optional.of(item),
                                        allocation.getAmount()
                                );
                            });
                        });

                // Add the zeroed and real budgets together for each key and item
                Map<Budget.Key, Map<Optional<Item>, Optional<BudgetSummary>>> collectedAmounts
                        = Stream.concat(zeroAmounts, realAmounts)
                        .collect(Collectors.groupingBy(
                                        BudgetSummary::getKey,
                                        Collectors.groupingBy(
                                                BudgetSummary::getItem,
                                                Collectors.reducing(
                                                        BudgetSummary::add))));

                // Summarise the summed values
                childAmounts = collectedAmounts.values().stream()
                        .flatMap(map -> map.values().stream())
                        .map(Optional::get)
                        .sorted((l, r) -> l.getItem().get().getDisplayName().compareTo(
                                        r.getItem().get().getDisplayName()))
                        .collect(Collectors.groupingBy(BudgetSummary::getKey));
            }

            treeState.set(new TreeState(parentAmounts, childAmounts));
            updateView.run();
        }
    }

    private class UpdateView implements Runnable {

        @Override
        public void run() {
            TreeState state = treeState.get();
            TreeItem root = new TreeItem();
            root.setExpanded(true);
            root.getChildren().addAll(
                    state.allocatedBudgets.keySet().stream()
                    .map(key -> {
                        BudgetSummary functionalBudget = state.functionalBudgets.get(key);
                        if (functionalBudget == null) {
                            functionalBudget = new BudgetSummary(key, Optional.empty(), Range.ZERO);
                        }
                        TreeItem parent = new TreeItem(functionalBudget);
                        List<BudgetSummary> allocatedBudgets = state.allocatedBudgets.getOrDefault(
                                functionalBudget.getKey(), Collections.emptyList());
                        parent.getChildren().addAll(
                                allocatedBudgets.stream()
                                .map(allocatedBudget -> {
                                    return new TreeItem(allocatedBudget);
                                }).toArray()
                        );
                        Range parentSum = functionalBudget.getAmount();
                        Range childSum = allocatedBudgets.stream()
                        .map(BudgetSummary::getAmount)
                        .reduce(Range.ZERO, Range::add);
                        Range difference = childSum.subtract(parentSum);
                        if (!difference.isExactZero()) {
                            parent.getChildren().add(
                                    new TreeItem(
                                            new BudgetSummary(
                                                    Optional.empty(),
                                                    Optional.empty(),
                                                    difference.abs())));
                        }
                        parent.setExpanded(true);
                        return parent;
                    })
                    .toArray());

            view.setRoot(root);
            view.setShowRoot(false);
            view.setEditable(true);
            view.setCellFactory(view -> {
                BudgetTreeCell cell = new BudgetTreeCell();
                cell.start();
                return cell;
            });
        }
    }

    private final class BudgetTreeCell extends TreeCell<BudgetSummary> {

        private Optional<TextField> textField = Optional.empty();
        private final ContextMenu contextMenu = new ContextMenu();

        public BudgetTreeCell() {
            MenuItem renameMenuItem = new MenuItem("Rename...");
            contextMenu.getItems().add(renameMenuItem);
            renameMenuItem.setOnAction(event -> {
                BudgetSummary summary = getItem();
                interactions.setBudgetName(summary.getKey());
                event.consume();
            });
            MenuItem setUnitMenuItem = new MenuItem("Set Unit...");
            contextMenu.getItems().add(setUnitMenuItem);
            setUnitMenuItem.setOnAction(event -> {
                BudgetSummary summary = getItem();
                interactions.setBudgetUnit(summary.getKey());
                event.consume();
            });
            MenuItem deleteMenuItem = new MenuItem("Delete Budget");
            contextMenu.getItems().add(deleteMenuItem);
            deleteMenuItem.setOnAction(event -> {
                BudgetSummary summary = getItem();
                edit.update(state -> Budget.removeFromAllocated(state, summary.getKey()));
                event.consume();
            });
            this.editableProperty().bind(this.itemProperty().isNotNull());
        }

        public void start() {
        }

        @Override
        public void startEdit() {
            BudgetSummary budget = getItem();
            if (budget != null && budget.hasKey()) {
                super.startEdit();

                if (!textField.isPresent()) {
                    textField = Optional.of(createTextField(getItem()));
                }
                setText(null);
                setGraphic(textField.get());
                textField.get().selectAll();
                textField.get().requestFocus();
            } else {
                super.cancelEdit();
            }
        }

        @Override
        public void cancelEdit() {
            if (isFocused()) {
                super.cancelEdit();

                setText(getString());
                setGraphic(getTreeItem().getGraphic());
            } else {
                /*
                 * If the cancelEdit is due to a loss of focus, override it.
                 * Commit instead.
                 */
                if (textField.isPresent() && getItem().hasKey()) {
                    commitEdit(getItem());
                } else {
                    super.cancelEdit();
                }
            }
        }

        @Override
        public void commitEdit(BudgetSummary summary) {
            AtomicReference<BudgetSummary> result = new AtomicReference<>();
            edit.update(state -> {
                Optional<Item> optionalItem = summary.getItem();
                Baseline baseline;
                boolean allocatedBaseline = optionalItem.isPresent();
                if (allocatedBaseline) {
                    Baseline allocated = state.getAllocated();
                    baseline = allocated;
                    optionalItem = optionalItem.flatMap(item -> allocated.get(item));
                } else {
                    baseline = state.getFunctional();
                    optionalItem = state.getSystemOfInterest();
                }
                if (!optionalItem.isPresent()) {
                    return state;
                }
                Item item = optionalItem.get();
                Optional<Budget> optionalBudget
                        = Budget.find(baseline, summary.getKey()).findAny();
                if (!optionalBudget.isPresent()) {
                    return state;
                }
                Budget budget = optionalBudget.get();
                Range amount;
                try {
                    amount = Range.valueOf(textField.get().getText());
                } catch (ParseException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                    return state;
                }
                Baseline.BaselineAnd<BudgetAllocation> tmp
                        = BudgetAllocation.setAmount(baseline, item, budget, amount);
                baseline = tmp.getBaseline();
                BudgetAllocation allocation = tmp.getRelation();
                result.set(summary.setAmount(allocation.getAmount()));
                if (allocatedBaseline) {
                    return state.setAllocated(baseline);
                } else {
                    return state.setFunctional(baseline);
                }
            });
            super.commitEdit(summary);
        }

        @Override
        public void updateItem(BudgetSummary function, boolean empty) {
            super.updateItem(function, empty);

            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    setText(null);
                    if (textField.isPresent()) {
                        textField.get().setText(getString());
                        setGraphic(textField.get());
                    }
                } else {
                    setText(getString());
                    setGraphic(getTreeItem().getGraphic());
                    setContextMenu(contextMenu);
                }
            }
        }

        @CheckReturnValue
        private TextField createTextField(BudgetSummary summary) {
            TextField node = new TextField(summary.getAmount().toString());
            node.setOnKeyReleased(event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    commitEdit(getItem());
                    event.consume();
                } else if (event.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                    event.consume();
                }
            });
            return node;
        }

        private String getString() {
            return getItem().toString();
        }
    }
    private final EditState edit;
    private final TreeView<BudgetSummary> view;
    private final AtomicReference<TreeState> treeState = new AtomicReference<>();
    private final SingleRunnable<UpdateState> updateState;
    private final SingleRunnable<UpdateView> updateView;
    private final Interactions interactions;
}
