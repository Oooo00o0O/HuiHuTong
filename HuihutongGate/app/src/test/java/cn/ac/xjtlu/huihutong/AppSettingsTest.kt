package cn.ac.xjtlu.huihutong

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AppSettingsTest {
    @Test
    fun `complete apartment and room identifiers enable room balance`() {
        val settings = AppSettings.fromInput(
            openId = " open-id ",
            unionId = " union-id ",
            apartmentId = " 1 ",
            roomId = " 9626 "
        )

        assertEquals(
            RoomReference(apartmentId = "1", roomId = "9626"),
            settings.roomReference
        )
    }

    @Test
    fun `partial room identifiers are preserved without enabling room balance`() {
        val settings = AppSettings.fromInput(
            openId = "open-id",
            unionId = "",
            apartmentId = "1",
            roomId = ""
        )

        assertEquals("1", settings.apartmentId)
        assertNull(settings.roomId)
        assertNull(settings.roomReference)
    }

    @Test
    fun `open ID is required`() {
        val error = assertThrows(SettingsValidationException::class.java) {
            AppSettings.fromInput(
                openId = " ",
                unionId = "union-id",
                apartmentId = "",
                roomId = ""
            )
        }

        assertEquals(SettingsField.OPEN_ID, error.field)
    }

    @Test
    fun `apartment ID must be a positive whole number when present`() {
        val error = assertThrows(SettingsValidationException::class.java) {
            AppSettings.fromInput(
                openId = "open-id",
                unionId = "",
                apartmentId = "A1",
                roomId = ""
            )
        }

        assertEquals(SettingsField.APARTMENT_ID, error.field)
    }

    @Test
    fun `room ID must be greater than zero when present`() {
        val error = assertThrows(SettingsValidationException::class.java) {
            AppSettings.fromInput(
                openId = "open-id",
                unionId = "",
                apartmentId = "",
                roomId = "0"
            )
        }

        assertEquals(SettingsField.ROOM_ID, error.field)
    }
}
