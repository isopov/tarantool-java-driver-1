package io.tarantool.driver.mappers;

import org.msgpack.value.ArrayValue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for TarantoolResultMapper handling result of CRUD operation results returned by Tarantool server
 *
 * @author Alexey Kuzin
 */
public class TarantoolResultMapperFactory {

    private Map<Class<?>, MessagePackValueMapper> mapperCache = new ConcurrentHashMap<>();

    /**
     * Basic constructor
     */
    public TarantoolResultMapperFactory() {
    }

    /**
     * Create TarantoolResultMapper instance with the passed converter.
     * @param valueConverter entity-to-object converter
     * @param <T> target object type
     * @return TarantoolResultMapper instance
     */
    public <T> MessagePackValueMapper withConverter(ValueConverter<ArrayValue, T> valueConverter) {
        try {
            return withConverter(MapperReflectionUtils.getConverterTargetType(valueConverter), valueConverter);
        } catch (ConverterParameterTypeNotFoundException e) {
            throw new RuntimeException("Failed to determine the target parameter type of the generic interface, " +
                    "try to use the method withConverter(tupleClass, valueConverter) for registering the converter");
        }
    }

    /**
     * Create TarantoolResultMapper instance with the passed converter.
     * @param tupleClass target object type class. Necessary for resolving ambiguity when more than one suitable
     *        converters are present in the configured mapper
     * @param valueConverter entity-to-object converter
     * @param <T> target object type
     * @return TarantoolResultMapper instance
     */
    public <T> MessagePackValueMapper withConverter(Class<T> tupleClass, ValueConverter<ArrayValue, T> valueConverter) {
        MessagePackValueMapper mapper = mapperCache.get(tupleClass);
        if (mapper == null) {
            mapper = createMapper(tupleClass, valueConverter);
            mapperCache.put(tupleClass, mapper);
        }
        return mapper;
    }

    private <T> MessagePackValueMapper createMapper(Class<T> tupleClass, ValueConverter<ArrayValue, T> valueConverter) {
        MessagePackValueMapper mapper = new DefaultMessagePackMapper();
        return new TarantoolResultMapper<>(mapper, tupleClass, valueConverter);
    }
}