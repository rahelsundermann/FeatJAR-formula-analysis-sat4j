/*
 * Copyright (C) 2022 Sebastian Krieter
 *
 * This file is part of formula-analysis-sat4j.
 *
 * formula-analysis-sat4j is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * formula-analysis-sat4j is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with formula-analysis-sat4j. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE/FeatJAR-formula-analysis-sat4j> for further information.
 */
package de.featjar.formula.analysis.sat4j;

import de.featjar.formula.analysis.sat4j.solver.Sat4JSolver;
import de.featjar.formula.clauses.LiteralList;
import de.featjar.base.task.Executor;
import de.featjar.base.task.Monitor;
import de.featjar.base.task.CancelableMonitor;
import de.featjar.base.log.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Finds clauses responsible for core and dead features.
 *
 * @author Sebastian Krieter
 */
public class CauseAnalysis extends AClauseAnalysis<List<CauseAnalysis.Anomalies>> {
    public static class Anomalies {

        protected LiteralList deadVariables = new LiteralList();
        protected List<LiteralList> redundantClauses = Collections.emptyList();

        public LiteralList getDeadVariables() {
            return deadVariables;
        }

        public void setDeadVariables(LiteralList variables) {
            if (variables == null) {
                deadVariables = new LiteralList();
            } else {
                deadVariables = variables;
            }
        }

        public List<LiteralList> getRedundantClauses() {
            return redundantClauses;
        }

        public void setRedundantClauses(List<LiteralList> redundantClauses) {
            if (redundantClauses == null) {
                this.redundantClauses = Collections.emptyList();
            } else {
                this.redundantClauses = redundantClauses;
            }
        }
    }

    private Anomalies anomalies;
    protected boolean[] relevantConstraint;

    public Anomalies getAnomalies() {
        return anomalies;
    }

    public void setAnomalies(Anomalies anomalies) {
        this.anomalies = anomalies;
    }

    public boolean[] getRelevantConstraint() {
        return relevantConstraint;
    }

    public void setRelevantConstraint(boolean[] relevantConstraint) {
        this.relevantConstraint = relevantConstraint;
    }

    @Override
    public List<Anomalies> analyze(Sat4JSolver solver, Monitor monitor) throws Exception {
        if (clauseList == null) {
            return Collections.emptyList();
        }
        if (clauseGroupSize == null) {
            clauseGroupSize = new int[clauseList.size()];
            Arrays.fill(clauseGroupSize, 1);
        }
        final List<Anomalies> resultList = new ArrayList<>(clauseGroupSize.length);
        for (int i = 0; i < clauseList.size(); i++) {
            resultList.add(null);
        }
        if (anomalies == null) {
            return resultList;
        }
        monitor.setTotalSteps(clauseList.size() + 3);

        LiteralList remainingVariables = anomalies.deadVariables.getVariables();
        final List<LiteralList> remainingClauses = new ArrayList<>(anomalies.redundantClauses);
        monitor.addStep();

        if (!remainingClauses.isEmpty()) {
            final List<LiteralList> result = Executor.apply(
                            new IndependentRedundancyAnalysis(remainingClauses)::execute, solver.getCnf())
                    .orElse(Log::problems);
            remainingClauses.removeIf(result::contains);
        }
        monitor.addStep();

        if (remainingVariables.getLiterals().length > 0) {
            remainingVariables = remainingVariables.removeAll(
                    Executor.apply(new CoreDeadAnalysis(remainingVariables)::execute, solver.getCnf())
                            .orElse(Log::problems));
        }
        monitor.addStep();

        int endIndex = 0;
        for (int i = 0; i < clauseGroupSize.length; i++) {
            if ((remainingVariables.getLiterals().length == 0) && remainingClauses.isEmpty()) {
                break;
            }

            final int startIndex = endIndex;
            endIndex += clauseGroupSize[i];
            solver.getFormula().push(clauseList.subList(startIndex, endIndex));
            if (relevantConstraint[i]) {
                if (remainingVariables.getLiterals().length > 0) {
                    final LiteralList deadVariables =
                            new CoreDeadAnalysis(remainingVariables).execute(solver, new CancelableMonitor());
                    if (deadVariables.getLiterals().length != 0) {
                        getAnomalies(resultList, i).setDeadVariables(deadVariables);
                        remainingVariables = remainingVariables.removeAll(deadVariables);
                    }
                }

                if (!remainingClauses.isEmpty()) {
                    final List<LiteralList> newClauseList =
                            new IndependentRedundancyAnalysis(remainingClauses).execute(solver, new CancelableMonitor());
                    newClauseList.removeIf(Objects::isNull);
                    if (!newClauseList.isEmpty()) {
                        getAnomalies(resultList, i).setRedundantClauses(newClauseList);
                        remainingClauses.removeAll(newClauseList);
                    }
                }
            }

            monitor.addStep();
        }

        return resultList;
    }

    protected Anomalies getAnomalies(final List<Anomalies> resultList, final Integer curIndex) {
        Anomalies curAnomalies = resultList.get(curIndex);
        if (curAnomalies == null) {
            curAnomalies = new Anomalies();
            resultList.set(curIndex, curAnomalies);
        }
        return curAnomalies;
    }
}