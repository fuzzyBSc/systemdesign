/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.budget;

import au.id.soundadvice.systemdesign.moduleapi.Module;
import au.id.soundadvice.systemdesign.moduleapi.drawing.Drawing;
import au.id.soundadvice.systemdesign.moduleapi.entity.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.entity.BaselinePair;
import au.id.soundadvice.systemdesign.moduleapi.entity.DiffPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordType;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class BudgetModule implements Module {

    @Override
    public void init() {
    }

    @Override
    public BaselinePair onLoadAutoFix(BaselinePair state, String now) {
        return state;
    }

    @Override
    public BaselinePair onChangeAutoFix(BaselinePair state, String now) {
        return state;
    }

    @Override
    public Stream<RecordType> getRecordTypes() {
        return Stream.of(
                Budget.budget,
                BudgetAllocation.budgetAllocation);
    }

    @Override
    public Stream<Drawing> getDrawings(DiffPair<Baseline> baselines) {
        return Stream.empty();
    }
}
