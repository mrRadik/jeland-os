package ru.jeland.os.navigation

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class NavigationKeyboardTest {
    @Test fun typesRussianAddressDigitsAndSpace() {
        var value=TextFieldValue()
        for(key in listOf("м","и","р"," ","1"))value=editNavigationAddress(value,key)
        assertEquals("мир 1",value.text)
        assertEquals(TextRange(5),value.selection)
    }
    @Test fun insertsAtCursorAndReplacesSelection() {
        assertEquals(TextFieldValue("Мира 10",TextRange(7)),editNavigationAddress(TextFieldValue("Мира 1",TextRange(6)),"0"))
        assertEquals("Мира 12",editNavigationAddress(TextFieldValue("Мира 10",TextRange(5,7)),"12").text)
        assertEquals("Мира 1",editNavigationAddress(TextFieldValue("Мира 01",TextRange(5,6)),"⌫").text)
    }
    @Test fun backspaceHandlesStartAndUnicodeWithoutBreakingText() {
        assertEquals("",editNavigationAddress(TextFieldValue(),"⌫").text)
        assertEquals("Мир 1",editNavigationAddress(TextFieldValue("Мир😀 1",TextRange(5)),"⌫").text)
        assertEquals("Мир",editNavigationAddress(TextFieldValue("Мира",TextRange(4)),"⌫").text)
    }
    @Test fun lengthLimitClampsCursorAndDoesNotGrow() {
        val edited=editNavigationAddress(TextFieldValue("а".repeat(160),TextRange(160)),"1")
        assertEquals(160,edited.text.length)
        assertEquals(160,edited.selection.start)
    }
}
