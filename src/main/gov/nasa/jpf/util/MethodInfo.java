package gov.nasa.jpf.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.UnaryOperator.identity;

public class MethodInfo {
    private final String className;
    private final String methodName;
    private final ParamInfo[] params;
    private final PrimitiveType returnType;

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public ParamInfo[] getParams() {
        return params;
    }

    public PrimitiveType getReturnType() {
        return returnType;
    }

    public MethodInfo(String className, String methodName, ParamInfo[] params, PrimitiveType returnType) {
        this.className = className;
        this.methodName = methodName;
        this.params = params;
        this.returnType = returnType;
    }

    public static MethodInfo fromJsonFile(String path) throws IOException {
        return fromJsonFile(path, StandardCharsets.UTF_8);
    }

    public static MethodInfo fromJsonFile(String path, Charset encoding) throws IOException {
        String jsonString = new String(Files.readAllBytes(Paths.get(path)), encoding);
        JSONObject jObj = new JSONObject(jsonString);
        String methodName = jObj.getString("methodName");
        String className = jObj.getString("className");
        JSONArray params = jObj.getJSONArray("params");
        ParamInfo[] methodParams = new ParamInfo[params.length()];
        for (int i = 0; i < params.length(); i++) {
            JSONObject obj = params.getJSONObject(i);
            String name = obj.getString("name");
            PrimitiveType type = PrimitiveType.getPrimitiveTypeFromString(obj.getString("type"));
            methodParams[i] = new ParamInfo(name, type);
        }
        PrimitiveType returnType = PrimitiveType.getPrimitiveTypeFromString(jObj.getString("returnType"));
        return new MethodInfo(className, methodName, methodParams, returnType);
    }

    public static class ParamInfo {
        private final String name;
        private final PrimitiveType type;

        public ParamInfo(String name, PrimitiveType type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public PrimitiveType getType() {
            return type;
        }
    }


    public enum PrimitiveType {

        INT("int"),
        INT_ARR("int[]"),
        DOUBLE("double"),
        STRING("String"),
        CHAR("char"),
        FLOAT("float"),
        LONG("long"),
        BYTE("byte"),
        SHORT("short"),
        BOOLEAN("boolean"),
        VOID("void");

        private static final Map<String, PrimitiveType> ENUM_MAP;
        private final String keyword;

        PrimitiveType(String keyword) {
            this.keyword = keyword;
        }

        @Override
        public String toString() {
            return this.keyword;
        }

        static {
            Map<String, PrimitiveType> map =
                    Stream.of(PrimitiveType.values()).collect(Collectors.toMap(PrimitiveType::toString, identity()));
            ENUM_MAP = Collections.unmodifiableMap(map);
        }

        public static PrimitiveType getPrimitiveTypeFromString(String s) {
            return ENUM_MAP.get(s);
        }
    }

}
