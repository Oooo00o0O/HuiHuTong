package cn.ac.xjtlu.huihutong

enum class SettingsField { OPEN_ID, APARTMENT_ID, ROOM_ID }

class SettingsValidationException(
    val field: SettingsField,
    message: String
) : IllegalArgumentException(message)

data class RoomReference(
    val apartmentId: String,
    val roomId: String
)

data class AppSettings(
    val credentials: Credentials,
    val apartmentId: String?,
    val roomId: String?
) {
    val roomReference: RoomReference? =
        if (apartmentId != null && roomId != null) RoomReference(apartmentId, roomId) else null

    companion object {
        fun fromInput(
            openId: String,
            unionId: String,
            apartmentId: String,
            roomId: String
        ): AppSettings {
            val cleanOpenId = openId.trim()
            if (cleanOpenId.isEmpty()) {
                throw SettingsValidationException(SettingsField.OPEN_ID, "请输入 Open ID")
            }
            val cleanApartmentId = apartmentId.cleanOptionalId(
                SettingsField.APARTMENT_ID,
                "Apartment ID"
            )
            val cleanRoomId = roomId.cleanOptionalId(SettingsField.ROOM_ID, "Room ID")
            return AppSettings(
                credentials = Credentials(
                    openId = cleanOpenId,
                    unionId = unionId.trim().ifEmpty { null }
                ),
                apartmentId = cleanApartmentId,
                roomId = cleanRoomId
            )
        }

        private fun String.cleanOptionalId(field: SettingsField, label: String): String? {
            val value = trim().ifEmpty { return null }
            if (!value.all { it in '0'..'9' } || value.all { it == '0' }) {
                throw SettingsValidationException(field, "$label 必须是大于 0 的整数")
            }
            return value
        }
    }
}
