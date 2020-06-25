package io.tarantool.driver.api.tuple;

import io.tarantool.driver.exceptions.TarantoolValueConverterNotFoundException;
import io.tarantool.driver.mappers.MessagePackObjectMapper;
import io.tarantool.driver.mappers.MessagePackValueMapper;
import io.tarantool.driver.util.Nullable;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Basic tuple field implementation
 *
 * @author Alexey Kuzin
 */
public class TarantoolFieldImpl<V extends Value> implements TarantoolField {

    private V entity;
    private MessagePackValueMapper valueMapper;

    TarantoolFieldImpl(V entity, MessagePackValueMapper valueMapper) {
        this.entity = entity;
        this.valueMapper = valueMapper;
    }

    <O> TarantoolFieldImpl(@Nullable O value, MessagePackObjectMapper mapper) {
        this.entity = value == null ? null : mapper.toValue(value);
    }

    @Override
    public Value toMessagePackValue(MessagePackObjectMapper mapper) {
        return entity == null ? ValueFactory.newNil() : entity;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <O> O getValue(Class<O> targetClass) throws TarantoolValueConverterNotFoundException {
        return valueMapper.getValueConverter((Class<V>) entity.getClass(), targetClass)
                .orElseThrow(() -> new TarantoolValueConverterNotFoundException(entity.getClass(), targetClass))
                .fromValue(entity);
    }

    @Override
    public byte[] getByteArray() throws TarantoolValueConverterNotFoundException {
        return getValue(byte[].class);
    }

    @Override
    public Boolean getBoolean() throws TarantoolValueConverterNotFoundException {
        return getValue(Boolean.class);
    }

    @Override
    public Double getDouble() throws TarantoolValueConverterNotFoundException {
        return getValue(Double.class);
    }

    @Override
    public Integer getInteger() throws TarantoolValueConverterNotFoundException {
        return getValue(Integer.class);
    }

    @Override
    public String getString() throws TarantoolValueConverterNotFoundException {
        return getValue(String.class);
    }

    @Override
    public UUID getUUID() throws TarantoolValueConverterNotFoundException {
        return getValue(UUID.class);
    }

    @Override
    public BigDecimal getDecimal() throws TarantoolValueConverterNotFoundException {
        return getValue(BigDecimal.class);
    }
}