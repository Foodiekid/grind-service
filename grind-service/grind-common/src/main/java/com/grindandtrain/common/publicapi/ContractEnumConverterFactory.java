package com.grindandtrain.common.publicapi;

import java.util.Arrays;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

/**
 * Converts path and query values to enums by their contract value ({@code oura}), the way the generated enums print
 * themselves, instead of Spring's default constant name ({@code OURA}). Plain enums print their constant name, so they
 * still convert as before. An unknown value is a 400. Registered by {@link PublicApiConfiguration}.
 *
 * @author Dheeraj_Edupuganti
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class ContractEnumConverterFactory implements ConverterFactory<String, Enum> {

    @Override
    public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
        return source -> (T) Arrays.stream(targetType.getEnumConstants())
                .filter(constant -> constant.toString().equals(source))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown value for " + targetType.getSimpleName()));
    }
}
