/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.trustyai.explainability.local.counterfactual.entities;

import java.util.ArrayList;
import java.util.Set;

import org.kie.trustyai.explainability.model.Feature;
import org.kie.trustyai.explainability.model.FeatureFactory;
import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.entity.PlanningPin;
import org.optaplanner.core.api.domain.valuerange.CountableValueRange;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.domain.variable.PlanningVariable;
import org.optaplanner.core.impl.domain.valuerange.buildin.collection.ListValueRange;

/**
 * Mapping between a categorical feature an OptaPlanner {@link PlanningEntity}
 */
@PlanningEntity
public class ObjectEntity extends AbstractCategoricalEntity<Object> {

    public ObjectEntity() {
        super();
    }

    private ObjectEntity(Object originalValue, String featureName, Set<Object> allowedCategories, boolean constrained) {
        super(originalValue, featureName, allowedCategories, constrained);
    }

    /**
     * Creates a {@link ObjectEntity}, taking the original input value from the
     * provided {@link Feature} and specifying whether the entity is constrained or not.
     * A set of allowed category values must be passed.
     *
     * @param originalFeature Original input {@link Feature}
     * @param categories Set of allowed category values
     * @param constrained Whether this entity's value should be fixed or not
     */
    public static ObjectEntity from(Feature originalFeature, Set<Object> categories, boolean constrained) {
        return new ObjectEntity(originalFeature.getValue().getUnderlyingObject(), originalFeature.getName(), categories,
                constrained);
    }

    /**
     * Creates an unconstrained {@link ObjectEntity}, taking the original input value from the
     * provided {@link Feature}.
     * A set of allowed category values must be passed.
     *
     * @param originalFeature feature Original input {@link Feature}
     * @param categories Set of allowed category values
     */
    public static ObjectEntity from(Feature originalFeature, Set<Object> categories) {
        return ObjectEntity.from(originalFeature, categories, false);
    }

    @Override
    @ValueRangeProvider(id = "objectRange")
    public CountableValueRange<Object> getValueRange() {
        return new ListValueRange<>(new ArrayList<>(allowedCategories));
    }

    /**
     * Returns the {@link ObjectEntity} as a {@link Feature}
     *
     * @return {@link Feature}
     */
    @Override
    public Feature asFeature() {
        return FeatureFactory.newObjectFeature(featureName, this.proposedValue);
    }

    @Override
    @PlanningVariable(valueRangeProviderRefs = { "objectRange" })
    public Object getProposedValue() {
        return proposedValue;
    }

    @Override
    public void setProposedValue(Object proposedValue) {
        this.proposedValue = proposedValue;
    }

    @Override
    @PlanningPin
    public boolean isConstrained() {
        return constrained;
    }

}
