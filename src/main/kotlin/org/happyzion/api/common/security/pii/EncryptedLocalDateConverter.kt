package org.happyzion.api.common.security.pii

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
@Converter
class EncryptedLocalDateConverter(private val encryptor: PiiEncryptor) : AttributeConverter<LocalDate, String> {

    override fun convertToDatabaseColumn(attribute: LocalDate?): String? =
        attribute?.format(DateTimeFormatter.ISO_LOCAL_DATE)?.let(encryptor::encrypt)

    override fun convertToEntityAttribute(dbData: String?): LocalDate? =
        dbData?.let(encryptor::decrypt)?.let(LocalDate::parse)
}
