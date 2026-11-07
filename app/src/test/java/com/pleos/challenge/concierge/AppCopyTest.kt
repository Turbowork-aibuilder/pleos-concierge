package com.pleos.challenge.concierge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Step 34: app copy that never appears in the demo videos, rewritten in 해요체 (steps/step34.md). */
class AppCopyTest {
    private val all = listOf(AppCopy.CONNECTED, AppCopy.LOW_BATTERY, AppCopy.DRIVING_STATUS,
        AppCopy.DRIVING_TITLE, AppCopy.UNAVAILABLE)

    @Test fun valuesMatchTheCopyTable() {
        assertEquals("차량 연결됨 · 바꾼 값은 차에서 확인되면 표시돼요", AppCopy.CONNECTED)
        assertEquals("배터리가 얼마 안 남았어요. 차를 세우고 충전소를 찾아 보세요.", AppCopy.LOW_BATTERY)
        assertEquals("달리는 중이에요 · 조작은 세운 다음에", AppCopy.DRIVING_STATUS)
        assertEquals("운전에만 집중해요.", AppCopy.DRIVING_TITLE)
        assertEquals("연결과 권한을 확인하면 사용할 수 있어요.\n확인이 안 되면 차량 조작은 잠가 둬요.", AppCopy.UNAVAILABLE)
    }

    @Test fun noStiffEndingsOrOldPhrasesLeft() {
        val stiff = listOf("합니다", "습니다", "주세요", "부드러운 여정", "정차 후", "구분해")
        all.forEach { text -> stiff.forEach { word -> assertFalse("$word in $text", text.contains(word)) } }
    }
}
