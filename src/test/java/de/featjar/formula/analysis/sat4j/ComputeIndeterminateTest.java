/*
 * Copyright (C) 2023 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-formula-analysis-sat4j.
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.featjar.base.computation.Computations;
import de.featjar.base.computation.IComputation;
import de.featjar.formula.analysis.VariableMap;
import de.featjar.formula.analysis.bool.BooleanAssignment;
import de.featjar.formula.analysis.bool.BooleanClauseList;
import de.featjar.formula.analysis.bool.BooleanRepresentationComputation;
import de.featjar.formula.analysis.bool.IBooleanRepresentation;
import de.featjar.formula.structure.Expressions;
import de.featjar.formula.structure.formula.IFormula;
import de.featjar.formula.transformer.ComputeCNFFormula;
import de.featjar.formula.transformer.ComputeNNFFormula;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class ComputeIndeterminateTest {

    @Test
    void formulaHas2Indeterminate() {
        IFormula formula = Expressions.and(
                Expressions.or(Expressions.literal("a"), Expressions.literal("b")),
                Expressions.biImplies(Expressions.literal("x"), Expressions.literal("y")));
        BooleanRepresentationComputation<IFormula, IBooleanRepresentation> cnf = Computations.of(formula)
                .map(ComputeNNFFormula::new)
                .map(ComputeCNFFormula::new)
                .map(BooleanRepresentationComputation::new);
        IComputation<IBooleanRepresentation> clauses = cnf.map(Computations::getKey);
        VariableMap variables = cnf.map(Computations::getValue).compute();
        BooleanAssignment compute = clauses.cast(BooleanClauseList.class)
                .map(ComputeIndeterminate::new)
                .compute();
        List<String> indeterminate = compute.streamValues()
                .map(v -> (v.getValue() ? "+" : "-") + variables.get(v.getKey()).get())
                .collect(Collectors.toCollection(ArrayList::new));
        assertEquals(new ArrayList<>(Arrays.asList("+a", "+b")), indeterminate);
    }
}