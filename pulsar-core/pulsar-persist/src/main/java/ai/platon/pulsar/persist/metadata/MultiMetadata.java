/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.platon.pulsar.persist.metadata;

import ai.platon.pulsar.common.DateTimes;
import ai.platon.pulsar.common.DublinCore;
import ai.platon.pulsar.common.HttpHeaders;
import ai.platon.pulsar.common.config.AppConstants;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.time.Instant;
import java.util.*;

public class MultiMetadata implements DublinCore, HttpHeaders, AppConstants {

    /**
     * A map of all data attributes. Each key maps to a list of values.
     */
    private final Map<String, List<String>> data = new LinkedHashMap<>();

    /**
     * Constructs a new, empty data.
     */
    public MultiMetadata() {

    }

    /**
     * Returns a set of the names contained in the data.
     *
     * @return Metadata names
     */
    public Set<String> names() {
        return new LinkedHashSet<>(data.keySet());
    }

    /**
     * <p>asMultimap.</p>
     *
     * @return a map from keys to collections of values.
     */
    public Map<String, Collection<String>> asMultimap() {
        Map<String, Collection<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : data.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableCollection(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Get the value associated to a data name. If many values are assiociated
     * to the specified name, then the first one is returned.
     *
     * @param name of the data.
     * @return the value associated to the specified data name.
     */
    public String get(String name) {
        List<String> values = data.get(name);
        if (values == null || values.isEmpty()) {
            return null;
        } else {
            return values.get(0);
        }
    }

    /**
     * <p>get.</p>
     *
     * @param name a {@link ai.platon.pulsar.persist.metadata.Name} object.
     * @return a {@link java.lang.String} object.
     */
    public String get(Name name) {
        return get(name.text());
    }

    /**
     * Get the values associated to a data name.
     *
     * @param name of the data.
     * @return the values associated to a data name.
     */
    public Collection<String> getValues(String name) {
        return CollectionUtils.emptyIfNull(data.get(name));
    }

    /**
     * Add a data name/value mapping. Add the specified value to the list of
     * values associated to the specified data name.
     *
     * @param name  the data name.
     * @param value the data value.
     */
    public void put(String name, String value) {
        data.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
    }

    /**
     * <p>put.</p>
     *
     * @param name  a {@link ai.platon.pulsar.persist.metadata.Name} object.
     * @param value a {@link java.lang.String} object.
     */
    public void put(Name name, String value) {
        put(name.text(), value);
    }

    /**
     * <p>put.</p>
     *
     * @param name  a {@link ai.platon.pulsar.persist.metadata.Name} object.
     * @param value a int.
     */
    public void put(Name name, int value) {
        put(name, String.valueOf(value));
    }

    /**
     * <p>put.</p>
     *
     * @param name  a {@link ai.platon.pulsar.persist.metadata.Name} object.
     * @param value a long.
     */
    public void put(Name name, long value) {
        put(name, String.valueOf(value));
    }

    /**
     * <p>put.</p>
     *
     * @param name  a {@link ai.platon.pulsar.persist.metadata.Name} object.
     * @param value a {@link java.time.Instant} object.
     */
    public void put(Name name, Instant value) {
        put(name, DateTimes.isoInstantFormat(value));
    }

    /**
     * <p>set.</p>
     *
     * @param name  a {@link java.lang.String} object.
     * @param value a {@link java.lang.String} object.
     */
    public void set(String name, String value) {
        data.remove(name);
        List<String> list = new ArrayList<>();
        list.add(value);
        data.put(name, list);
    }

    /**
     * <p>getInt.</p>
     *
     * @param name         a {@link java.lang.String} object.
     * @param defaultValue a int.
     * @return a int.
     */
    public int getInt(String name, int defaultValue) {
        String s = get(name);
        return NumberUtils.toInt(s, defaultValue);
    }

    /**
     * <p>getLong.</p>
     *
     * @param name         a {@link java.lang.String} object.
     * @param defaultValue a long.
     * @return a long.
     */
    public long getLong(String name, long defaultValue) {
        String s = get(name);
        return NumberUtils.toLong(s, defaultValue);
    }

    /**
     * <p>getBoolean.</p>
     *
     * @param name         a {@link java.lang.String} object.
     * @param defaultValue a {@link java.lang.Boolean} object.
     * @return a boolean.
     */
    public boolean getBoolean(String name, Boolean defaultValue) {
        String s = get(name);
        if (s == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(s);
    }

    /**
     * A primitive-friendly overload to avoid null defaults.
     */
    public boolean getBoolean(String name, boolean defaultValue) {
        return getBoolean(name, Boolean.valueOf(defaultValue));
    }

    /**
     * Returns true if the data contains at least one value for the given name.
     */
    public boolean has(String name) {
        List<String> values = data.get(name);
        return values != null && !values.isEmpty();
    }

    /**
     * Removes all values for the given name.
     *
     * @return number of removed values
     */
    public int remove(String name) {
        List<String> removed = data.remove(name);
        return removed != null ? removed.size() : 0;
    }

    /**
     * Creates a deep copy of this metadata.
     */
    public MultiMetadata copy() {
        MultiMetadata m = new MultiMetadata();
        for (Map.Entry<String, List<String>> entry : data.entrySet()) {
            m.data.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return m;
    }

    /**
     * Remove all mappings from data.
     */
    public void clear() {
        data.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof MultiMetadata)) {
            return false;
        }

        MultiMetadata other;
        try {
            other = (MultiMetadata) o;
        } catch (ClassCastException e) {
            return false;
        }

        return data.equals(other.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        // Sort keys for stable output.
        List<String> keys = new ArrayList<>(data.keySet());
        keys.sort(String::compareTo);
        for (String name : keys) {
            List<String> values = new ArrayList<>(data.get(name));
            buf.append(name).append("=").append(StringUtils.join(values, ",")).append(StringUtils.SPACE);
        }
        return buf.toString();
    }
}
