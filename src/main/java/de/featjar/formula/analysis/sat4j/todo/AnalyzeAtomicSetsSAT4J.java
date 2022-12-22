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
package de.featjar.formula.analysis.sat4j.todo;

import de.featjar.base.computation.FutureResult;
import de.featjar.base.data.Result;
import de.featjar.formula.analysis.FormulaAnalysis;
import de.featjar.formula.analysis.bool.BooleanSolutionList;
import de.featjar.formula.analysis.sat4j.SAT4JAnalysis;
import de.featjar.formula.analysis.sat4j.solver.SelectionStrategy;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Finds atomic sets.
 *
 * @author Sebastian Krieter
 */
public class AnalyzeAtomicSetsSAT4J extends SAT4JAnalysis.Solution<AnalyzeAtomicSetsSAT4J, BooleanSolutionList> implements FormulaAnalysis.WithRandom { // todo: here, a BooleanAssignmentList would be better
    protected Random random = new Random(WithRandom.DEFAULT_RANDOM_SEED);

    @Override
    public Random getRandom() {
        return random;
    }

    @Override
    public AnalyzeAtomicSetsSAT4J setRandom(Random random) {
        this.random = random;
        return this;
    }

    @Override
    public AnalyzeAtomicSetsSAT4J setRandom(Long seed) {
        WithRandom.super.setRandom(seed);
        return this;
    }

    @Override
    public FutureResult<BooleanSolutionList> compute() {
        return computeSolver().thenCompute(((solver, monitor) -> {
            final BooleanSolutionList result = new BooleanSolutionList();
            //		if (variables == null) {
            //			variables = LiteralList.getVariables(solver.getVariables());
            //		}

            // for all variables not in this.variables, set done[...] to 2

            solver.setSelectionStrategy(SelectionStrategy.positive());
            final int[] model1 = solver.findSolution().get().getIntegers();
            final List<SortedIntegerList> solutions = solver.rememberSolutionHistory(1000);

            if (model1 != null) {
                // initial atomic set consists of core and dead features
                solver.setSelectionStrategy(SelectionStrategy.negative());
                final int[] model2 = solver.findSolution().get().getIntegers();
                solver.setSelectionStrategy(SelectionStrategy.positive());

                final byte[] done = new byte[model1.length];

                final int[] model1Copy = Arrays.copyOf(model1, model1.length);

                SortedIntegerList.resetConflicts(model1Copy, model2);
                for (int i = 0; i < model1Copy.length; i++) {
                    final int varX = model1Copy[i];
                    if (varX != 0) {
                        solver.getAssumptionList().push(-varX);
                        Result<Boolean> hasSolution = solver.hasSolution();
                        if (Result.of(false).equals(hasSolution)) {
                            done[i] = 2;
                            solver.getAssumptionList().replaceLast(varX);
                        } else if (Result.empty().equals(hasSolution)) {
                            solver.getAssumptionList().pop();
                            // return Result.empty(new TimeoutException()); // TODO: optionally ignore timeout or continue?
                        } else if (Result.of(true).equals(hasSolution)) {
                            solver.getAssumptionList().pop();
                            SortedIntegerList.resetConflicts(model1Copy, solver.getInternalSolution());
                            solver.shuffleOrder(random);
                        }
                    }
                }
                final int fixedSize = solver.getAssumptionList().size();
                result.add(new SortedIntegerList(solver.getAssumptionList().asArray(0, fixedSize)));

                solver.setSelectionStrategy(SelectionStrategy.random(random));

                for (int i = 0; i < model1.length; i++) {
                    if (done[i] == 0) {
                        done[i] = 2;

                        int[] xModel0 = Arrays.copyOf(model1, model1.length);

                        final int mx0 = xModel0[i];
                        solver.getAssumptionList().push(mx0);

                        inner:
                        for (int j = i + 1; j < xModel0.length; j++) {
                            final int my0 = xModel0[j];
                            if ((my0 != 0) && (done[j] == 0)) {
                                for (final SortedIntegerList solution : solutions) {
                                    final int mxI = solution.getIntegers()[i];
                                    final int myI = solution.getIntegers()[j];
                                    if ((mx0 == mxI) != (my0 == myI)) {
                                        continue inner;
                                    }
                                }

                                solver.getAssumptionList().push(-my0);

                                Result<Boolean> hasSolution = solver.hasSolution();
                                if (Result.of(false).equals(hasSolution)) {
                                    done[j] = 1;
                                } else if (Result.empty().equals(hasSolution)) {
                                    // return Result.empty(new TimeoutException()); // TODO: optionally ignore timeout or continue?
                                } else if (Result.of(true).equals(hasSolution)) {
                                    SortedIntegerList.resetConflicts(xModel0, solver.getInternalSolution());
                                    solver.shuffleOrder(random);
                                }
                                solver.getAssumptionList().pop();
                            }
                        }

                        solver.getAssumptionList().pop();
                        solver.getAssumptionList().push(-mx0);

                        Result<Boolean> hasSolution = solver.hasSolution();
                        if (Result.of(false).equals(hasSolution)) {
                        } else if (Result.empty().equals(hasSolution)) {
                            for (int j = i + 1; j < xModel0.length; j++) {
                                done[j] = 0;
                            }
                            // return Result.empty(new TimeoutException()); // TODO: optionally ignore timeout or continue?
                        } else if (Result.of(true).equals(hasSolution)) {
                            xModel0 = solver.getInternalSolution();
                        }

                        for (int j = i + 1; j < xModel0.length; j++) {
                            if (done[j] == 1) {
                                final int my0 = xModel0[j];
                                if (my0 != 0) {
                                    solver.getAssumptionList().push(-my0);

                                    Result<Boolean> solution = solver.hasSolution();
                                    if (Result.of(false).equals(solution)) {
                                        done[j] = 2;
                                        solver.getAssumptionList().replaceLast(my0);
                                    } else if (Result.empty().equals(solution)) {
                                        done[j] = 0;
                                        solver.getAssumptionList().pop();
                                        // return Result.empty(new TimeoutException()); // TODO: optionally ignore timeout or continue?
                                    } else if (Result.of(true).equals(solution)) {
                                        done[j] = 0;
                                        SortedIntegerList.resetConflicts(xModel0, solver.getInternalSolution());
                                        solver.shuffleOrder(random);
                                        solver.getAssumptionList().pop();
                                    }
                                } else {
                                    done[j] = 0;
                                }
                            }
                        }

                        result.add(new SortedIntegerList(solver.getAssumptionList()
                                .asArray(fixedSize, solver.getAssumptionList().size())));
                        solver.getAssumptionList().clear(fixedSize);
                    }
                }
            }
            return result;
        }));
    }
}