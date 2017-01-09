/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.budget;

import au.id.soundadvice.systemdesign.budget.entity.Budget;
import au.id.soundadvice.systemdesign.budget.entity.BudgetAllocation;
import au.id.soundadvice.systemdesign.moduleapi.Module;
import au.id.soundadvice.systemdesign.moduleapi.drawing.Drawing;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.DiffPair;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.tree.Tree;
import java.util.stream.Stream;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class BudgetModule implements Module {

    @Override
    public void init() {
    }

    @Override
    public WhyHowPair<Baseline> onLoadAutoFix(WhyHowPair<Baseline> state, String now) {
        return state;
    }

    @Override
    public WhyHowPair<Baseline> onChangeAutoFix(WhyHowPair<Baseline> state, String now) {
        return state;
    }

    @Override
    public Stream<Table> getTables() {
        return Stream.of(
                Budget.budget,
                BudgetAllocation.budgetAllocation);
    }

    @Override
    public Stream<Drawing> getDrawings(DiffPair<Baseline> baselines) {
        return Stream.empty();
    }

    @Override
    public Stream<Tree> getTrees(WhyHowPair<Baseline> baselines) {
//        return Stream.of(new BudgetTree(baselines));
        return Stream.empty();
    }
}
