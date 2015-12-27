/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.budget;

import au.id.soundadvice.systemdesign.budget.beans.BudgetAllocationBean;
import au.id.soundadvice.systemdesign.budget.beans.BudgetBean;
import au.id.soundadvice.systemdesign.budget.fix.BudgetDeduplicate;
import au.id.soundadvice.systemdesign.budget.suggest.BudgetConsistency;
import au.id.soundadvice.systemdesign.moduleapi.Identifiable;
import au.id.soundadvice.systemdesign.moduleapi.Module;
import au.id.soundadvice.systemdesign.moduleapi.UndoState;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import java.io.IOException;
import java.io.UncheckedIOException;
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
    public UndoState onLoadAutoFix(UndoState state) {
        state = BudgetDeduplicate.fix(state);
        return state;
    }

    @Override
    public UndoState onChangeAutoFix(UndoState state) {
        return state;
    }

    @Override
    public Stream<Identifiable> saveMementos(Relations context) {
        Stream<Identifiable> result;
        result = context.findByClass(Budget.class).map(Budget::toBean);
        result = Stream.concat(result,
                context.findByClass(BudgetAllocation.class).map(allocation -> allocation.toBean(context)));
        return result;
    }

    @Override
    public Stream<Problem> getProblems(UndoState state) {
        return BudgetConsistency.getProblems(state);
    }

    @Override
    public Stream<Class<? extends Identifiable>> getMementoTypes() {
        return Stream.of(
                BudgetBean.class,
                BudgetAllocationBean.class);
    }

    @Override
    public Stream<Relation> restoreMementos(Stream<Identifiable> beans) {
        return beans.
                map(bean -> {
                    if (BudgetBean.class.equals(bean.getClass())) {
                        return new Budget((BudgetBean) bean);
                    } else if (BudgetAllocationBean.class.equals(bean.getClass())) {
                        return new BudgetAllocation((BudgetAllocationBean) bean);
                    } else {
                        throw new UncheckedIOException(
                                new IOException(bean.getClass().getName()));
                    }
                });
    }
}
