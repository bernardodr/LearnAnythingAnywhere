package com.laa.llm.provider.fake;

import java.util.Iterator;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** Builds a deterministic JSON instance that satisfies a JSON Schema (as produced for records). */
final class FakeJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private FakeJson() {}

    static String fromSchema(String schemaJson, String tag) {
        JsonNode schema = MAPPER.readTree(schemaJson);
        return MAPPER.writeValueAsString(instance(schema, schema, tag, "value", 0));
    }

    private static JsonNode instance(JsonNode node, JsonNode root, String tag, String name, int depth) {
        if (depth > 12) return NODES.nullNode();
        if (node.has("$ref")) return instance(resolve(root, node.get("$ref").asString()), root, tag, name, depth + 1);
        if (node.has("const")) return node.get("const");
        if (node.has("enum")) return node.get("enum").get(0);
        if (node.has("anyOf")) return instance(firstNonNull(node.get("anyOf")), root, tag, name, depth + 1);
        if (node.has("oneOf")) return instance(firstNonNull(node.get("oneOf")), root, tag, name, depth + 1);
        String type = typeOf(node);
        return switch (type) {
            case "object" -> {
                ObjectNode obj = NODES.objectNode();
                JsonNode props = node.path("properties");
                for (Iterator<Map.Entry<String, JsonNode>> it = props.properties().iterator(); it.hasNext(); ) {
                    var e = it.next();
                    obj.set(e.getKey(), instance(e.getValue(), root, tag, e.getKey(), depth + 1));
                }
                yield obj;
            }
            case "array" -> {
                ArrayNode arr = NODES.arrayNode();
                int n = Math.max(1, node.path("minItems").asInt(1));
                for (int i = 0; i < n; i++) arr.add(instance(node.path("items"), root, tag, name, depth + 1));
                yield arr;
            }
            case "integer" -> NODES.numberNode(Math.max(1, node.path("minimum").asInt(1)));
            case "number" -> NODES.numberNode(Math.max(1.0, node.path("minimum").asDouble(1.0)));
            case "boolean" -> NODES.booleanNode(true);
            case "null" -> NODES.nullNode();
            default -> NODES.stringNode("[" + tag + "] " + name);
        };
    }

    private static String typeOf(JsonNode node) {
        JsonNode t = node.get("type");
        if (t == null) return node.has("properties") ? "object" : "string";
        if (t.isArray()) {
            for (JsonNode x : t) if (!"null".equals(x.asString())) return x.asString();
            return "null";
        }
        return t.asString();
    }

    private static JsonNode firstNonNull(JsonNode options) {
        for (JsonNode o : options) if (!"null".equals(o.path("type").asString())) return o;
        return options.get(0);
    }

    private static JsonNode resolve(JsonNode root, String ref) {
        if (!ref.startsWith("#/")) return NODES.objectNode();
        JsonNode cur = root;
        for (String part : ref.substring(2).split("/")) cur = cur.path(part.replace("~1", "/").replace("~0", "~"));
        return cur;
    }
}
