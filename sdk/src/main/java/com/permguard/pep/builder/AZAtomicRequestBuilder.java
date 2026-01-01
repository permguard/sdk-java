/**
 *   Copyright 2024 Nitro Agility S.r.l.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
  */

package com.permguard.pep.builder;

import com.permguard.pep.model.request.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Builder for creating an atomic AZRequest object.
 */
public class AZAtomicRequestBuilder {
    private final AZRequestBuilder requestBuilder;
    private final String subjectId;
    private String subjectType;
    private final String resourceType;
    private final String actionName;
    private String requestId;
    private String subjectSource;
    private String resourceId;
    private final Map<String, Object> subjectProperties = new HashMap<>();
    private final Map<String, Object> resourceProperties = new HashMap<>();
    private final Map<String, Object> actionProperties = new HashMap<>();
    private final Map<String, Object> context = new HashMap<>();

    /**
     * Constructor for AZAtomicRequestBuilder.
     *
     * @param zoneId        The authorization zone ID.
     * @param policyStoreId The ID of the policy store.
     * @param id            The ID of the subject.
     * @param resourceType  The type of the resource.
     * @param actionName    The name of the action.
     */
    public AZAtomicRequestBuilder(long zoneId, String policyStoreId, String id, String resourceType, String actionName) {
        this.requestBuilder = new AZRequestBuilder(zoneId, policyStoreId);
        this.subjectId = id;
        this.resourceType = resourceType;
        this.actionName = actionName;

        this.requestBuilder.withSubject(new SubjectBuilder(id).build());
        this.requestBuilder.withResource(new ResourceBuilder(resourceType).build());
        this.requestBuilder.withAction(new ActionBuilder(actionName).build());
    }

    /**
     * Sets the request ID.
     */
    public AZAtomicRequestBuilder withRequestId(String requestId) {
        this.requestId = requestId;
        this.requestBuilder.withRequestId(requestId);
        return this;
    }

    /**
     * Sets the principal.
     */
    public AZAtomicRequestBuilder withPrincipal(Principal principal) {
        this.requestBuilder.withPrincipal(principal);
        return this;
    }

    /**
     * Sets the subject type.
     */
    public AZAtomicRequestBuilder withSubjectType(String type) {
        this.subjectType = type;
        return this;
    }

    /**
     * Sets the subject source.
     */
    public AZAtomicRequestBuilder withSubjectSource(String source) {
        this.subjectSource = source;
        return this;
    }

    /**
     * Adds a property to the subject.
     */
    public AZAtomicRequestBuilder withSubjectProperty(String key, Object value) {
        this.subjectProperties.put(key, value);
        return this;
    }

    /**
     * Sets the resource ID.
     */
    public AZAtomicRequestBuilder withResourceId(String resourceId) {
        this.resourceId = resourceId;
        return this;
    }

    /**
     * Adds a property to the resource.
     */
    public AZAtomicRequestBuilder withResourceProperty(String key, Object value) {
        this.resourceProperties.put(key, value);
        return this;
    }

    /**
     * Adds a property to the action.
     */
    public AZAtomicRequestBuilder withActionProperty(String key, Object value) {
        this.actionProperties.put(key, value);
        return this;
    }

    /**
     * Adds a property to the request context.
     */
    public AZAtomicRequestBuilder withContextProperty(String key, Object value) {
        this.context.put(key, value);
        return this;
    }

    /**
     * Sets the entities for the request.
     */
    public AZAtomicRequestBuilder withEntitiesItems(String schema, Entities entities) {
        this.requestBuilder.withEntitiesItems(schema, entities);
        return this;
    }

    /**
     * Builds the AZRequest object, ensuring all properties are correctly applied.
     */
    public AZRequest build() {
        Subject subject = new SubjectBuilder(this.subjectId)
                .withType(this.subjectType)
                .withSource(subjectSource)
                .build();
        subject.getProperties().putAll(subjectProperties);

        Resource resource = new ResourceBuilder(this.resourceType).withId(resourceId).build();
        resource.getProperties().putAll(resourceProperties);

        Action action = new ActionBuilder(this.actionName).build();
        action.getProperties().putAll(actionProperties);

        this.requestBuilder.withContext(context);

        return requestBuilder.withSubject(subject)
                .withResource(resource)
                .withAction(action)
                .build();
    }
}
