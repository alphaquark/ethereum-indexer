package com.rarible.protocol.order.core.converters.model

import com.rarible.protocol.dto.PlatformDto
import com.rarible.protocol.order.core.model.Platform
import org.springframework.core.convert.converter.Converter

object PlatformConverter : Converter<PlatformDto, Platform> {
    override fun convert(source: PlatformDto?): Platform? {
        return when (source ?: PlatformDto.RARIBLE) {
            PlatformDto.RARIBLE -> Platform.RARIBLE
            PlatformDto.OPEN_SEA -> Platform.OPEN_SEA
            PlatformDto.CRYPTO_PUNKS -> Platform.CRYPTO_PUNKS
            PlatformDto.ALL -> null
        }
    }
}
