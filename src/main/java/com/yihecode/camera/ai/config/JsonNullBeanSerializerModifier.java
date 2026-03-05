package com.yihecode.camera.ai.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * JSON空值处理
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public class JsonNullBeanSerializerModifier extends BeanSerializerModifier {

    private final NullArrayJsonSerializer array = new NullArrayJsonSerializer();
    private final NullStringJsonSerializer string = new NullStringJsonSerializer();
    private final NullNumberJsonSerializer number = new NullNumberJsonSerializer();
    private final NullObjectJsonSerializer object = new NullObjectJsonSerializer();
    private final NullBooleanJsonSerializer toBoolean = new NullBooleanJsonSerializer();
    private final NullDateJsonSerializer date = new NullDateJsonSerializer();

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {
        for(int i = 0; i < beanProperties.size(); i++) {
            BeanPropertyWriter writer = beanProperties.get(i);
            if(isArrayType(writer)) {
                writer.assignNullSerializer(this.array);
            } else if(isStringType(writer)) {
                writer.assignNullSerializer(this.string);
            } else if(isNumberType(writer)) {
                writer.assignNullSerializer(this.number);
            } else if(isBooleanType(writer)) {
                writer.assignNullSerializer(this.toBoolean);
            } else if(isDateType(writer)) {
                writer.assignNullSerializer(this.date);
            } else {
                writer.assignNullSerializer(this.object);
            }
        }
        return beanProperties;
    }

    private static boolean isArrayType(BeanPropertyWriter writer) {
        Class<?> clazz = writer.getType().getRawClass();
        return clazz.isArray() || Collection.class.isAssignableFrom(clazz);
    }

    private static boolean isStringType(BeanPropertyWriter writer) {
        Class<?> clazz = writer.getType().getRawClass();
        return CharSequence.class.isAssignableFrom(clazz) || Character.class.isAssignableFrom(clazz);
    }

    private static boolean isNumberType(BeanPropertyWriter writer) {
        Class<?> clazz = writer.getType().getRawClass();
        return Number.class.isAssignableFrom(clazz);
    }

    private static boolean isBooleanType(BeanPropertyWriter writer) {
        Class<?> clazz = writer.getType().getRawClass();
        return clazz.equals(Boolean.class);
    }

    private static boolean isDateType(BeanPropertyWriter writer) {
        Class<?> clazz = writer.getType().getRawClass();
        return clazz.equals(Date.class);
    }

    private static class NullArrayJsonSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object o, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartArray();
            jsonGenerator.writeEndArray();
        }
    }

    private static class NullStringJsonSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object o, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeString("");
        }
    }

    private static class NullNumberJsonSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object o, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeNumber(0);
        }
    }

    private static class NullBooleanJsonSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object o, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeBoolean(false);
        }
    }

    private static class NullDateJsonSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object o, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeString("");
        }
    }

    private static class NullObjectJsonSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object o, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeEndObject();
        }
    }
}
