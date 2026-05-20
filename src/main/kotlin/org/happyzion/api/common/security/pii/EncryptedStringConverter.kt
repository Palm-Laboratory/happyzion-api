package org.happyzion.api.common.security.pii

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component

@Component
@Converter
class EncryptedStringConverter(private val encryptor: PiiEncryptor) : AttributeConverter<String, String> {

    override fun convertToDatabaseColumn(attribute: String?): String? =
        attribute?.let(encryptor::encrypt)

    override fun convertToEntityAttribute(dbData: String?): String? =
        dbData?.let(encryptor::decrypt)
}
