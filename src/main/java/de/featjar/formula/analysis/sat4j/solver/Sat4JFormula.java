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
package de.featjar.formula.analysis.sat4j.solver;

import de.featjar.formula.analysis.solver.SolverFormula;
import de.featjar.formula.analysis.solver.RuntimeContradictionException;
import de.featjar.formula.clauses.FormulaToCNF;
import de.featjar.formula.clauses.LiteralList;
import de.featjar.formula.structure.Expression;
import de.featjar.formula.structure.map.TermMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.sat4j.core.VecInt;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IConstr;

/**
 * Modifiable formula for a {@link Sat4JSolver}.
 *
 * @author Sebastian Krieter
 */
public class Sat4JFormula extends SolverFormula<IConstr> {

    private final AbstractSat4JSolver<?> sat4jSolver;

    public Sat4JFormula(AbstractSat4JSolver<?> solver, TermMap termMap) {
        super(termMap);
        sat4jSolver = solver;
    }

    protected Sat4JFormula(AbstractSat4JSolver<?> solver, Sat4JFormula oldFormula) {
        super(oldFormula);
        sat4jSolver = solver;
    }

    @Override
    public List<IConstr> push(Expression expression) throws RuntimeContradictionException {
        return push(FormulaToCNF.convert(expression, termMap).getClauses());
    }

    public List<IConstr> push(List<? extends LiteralList> clauses) {
        final ArrayList<IConstr> constrs = new ArrayList<>();
        for (final LiteralList clause : clauses) {
            try {
                if ((clause.size() == 1) && (clause.getLiterals()[0] == 0)) {
                    throw new ContradictionException();
                }
                final IConstr constr =
                        sat4jSolver.solver.addClause(new VecInt(Arrays.copyOf(clause.getLiterals(), clause.size())));
                constrs.add(constr);
            } catch (final ContradictionException e) {
                for (final IConstr constr : constrs) {
                    sat4jSolver.solver.removeConstr(constr);
                }
                throw new RuntimeContradictionException(e);
            }
        }
        if (sat4jSolver.solutionHistory != null) {
            sat4jSolver.solutionHistory.clear();
            sat4jSolver.lastModel = null;
        }
        this.solverFormulas.addAll(constrs);
        return constrs;
    }

    public IConstr push(LiteralList clause) throws RuntimeContradictionException {
        try {
            if ((clause.size() == 1) && (clause.getLiterals()[0] == 0)) {
                throw new ContradictionException();
            }
            final IConstr constr = sat4jSolver.solver.addClause(
                    new VecInt(Arrays.copyOfRange(clause.getLiterals(), 0, clause.size())));
            solverFormulas.add(constr);
            if (sat4jSolver.solutionHistory != null) {
                sat4jSolver.solutionHistory.clear();
                sat4jSolver.lastModel = null;
            }
            return constr;
        } catch (final ContradictionException e) {
            throw new RuntimeContradictionException(e);
        }
    }

    @Override
    public IConstr pop() {
        final IConstr lastConstraint = super.pop();
        sat4jSolver.solver.removeConstr(lastConstraint);
        return lastConstraint;
    }

    @Override
    public void remove(IConstr clause) {
        if (clause != null) {
            sat4jSolver.solver.removeConstr(clause);
            super.remove(clause);
        }
    }

    @Override
    public void pop(int count) {
        if (count > solverFormulas.size()) {
            count = solverFormulas.size();
        }
        for (int i = 0; i < count; i++) {
            final IConstr lastConstraint = remove(solverFormulas.size() - 1);
            if (lastConstraint != null) {
                sat4jSolver.solver.removeSubsumedConstr(lastConstraint);
            }
        }
        sat4jSolver.solver.clearLearntClauses();
    }
}