/*
 * Copyright (c) 2022.
 *
 * This program is free software: you can use, redistribute, and/or modify
 * it under the terms of the GNU Affero General Public License, version 3
 * or later ("AGPL"), as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package ai.platon.pulsar.jsoup.ext;

import org.jsoup.nodes.Node;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The pulsar extension.
 *
 * We have two modifications to jsoup:
 * 1. add NodeExt to Node
 * 2. make NodeVisitor and NodeFilter compatible with kotlin lambda
 *
 * Core storage fields ({@code variables}, {@code tuples}, {@code node}) are kept here.
 * All feature-related fields ({@code features}, {@code featureBlock}, {@code nodeIndex},
 * {@code ownerDocumentNode}, {@code ownerBody}, {@code immutableText}) have been moved
 * to the Kotlin extension layer in {@code ai.platon.pulsar.dom.nodes.node.ext} and are
 * stored in the {@code variables} map.
 */
public class NodeExt {
    private final Node node;
    private Map<String, Object> variables;
    private Map<String, List<Object>> tuples;

    public NodeExt(Node node) {
        this.node = node;
    }

    public void removeAttrs(String... attrNames) {
        for (String attrName : attrNames) {
            node.removeAttr(attrName);
        }
    }

    @Nonnull
    public Map<String, Object> getVariables() {
        if (variables == null) {
            variables = new HashMap<>();
        }
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    @Nonnull
    public Map<String, List<Object>> getTuples() {
        if (tuples == null) {
            tuples = new HashMap<>();
        }
        return tuples;
    }

    public void setTuples(Map<String, List<Object>> tuples) {
        this.tuples = tuples;
    }
}
