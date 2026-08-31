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


import com.permguard.pep.model.request.Subject;

import java.util.HashMap;
import java.util.Map;

/**
 * Builder for creating a Subject object.
 */
public class SubjectBuilder {
    private String type;
    private String id;
    private String source;
    private Map<String, Object> properties = new HashMap<>();

    /**
     * Constructor with required subject ID.
     *
     * @param id The unique identifier for the subject.
     */
    public SubjectBuilder(String id) {
        this.id = id;
    }

    /**
     * Sets the subject type.
     *
     * @param type The type of the subject.
     * @return The current builder instance.
     */
    public SubjectBuilder withType(String type) {
        this.type = type;
        return this;
    }

    /**
     * Sets the source of the subject.
     *
     * @param source The source system of the subject.
     * @return The current builder instance.
     */
    public SubjectBuilder withSource(String source) {
        this.source = source;
        return this;
    }

    /**
     * Adds a property to the subject.
     *
     * @param key   The property key.
     * @param value The property value.
     * @return The current builder instance.
     */
    public SubjectBuilder withProperty(String key, Object value) {
        properties.put(key, value);
        return this;
    }

    /**
     * Builds the Subject object.
     *
     * @return A new Subject instance.
     */
    public Subject build() {
        return new Subject(type, id, source, properties);
    }
}
