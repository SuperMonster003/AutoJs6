package org.autojs.autojs.codegeneration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [FlowCodeStyle]: the Flow forms of the generated code per action, and the search modes that
 * keep the classic code.
 * zh-CN: [FlowCodeStyle]: 各动作生成代码的 Flow 形式, 以及保持经典代码的搜索模式.
 */
class FlowCodeStyleTest {

    @Test
    fun actionsHaveTheirFlowForms() {
        assertEquals("waitThenClick(id(\"a\")).sync()", FlowCodeStyle.render("id(\"a\")", CodeGenerator.FIND_ONE, "click()"))
        assertEquals("waitThenLongClick(id(\"a\")).sync()", FlowCodeStyle.render("id(\"a\")", CodeGenerator.FIND_ONE, "longClick()"))
        assertEquals("waitAsync(id(\"a\")).setText(\"\").sync()", FlowCodeStyle.render("id(\"a\")", CodeGenerator.FIND_ONE, "setText(\"\")"))
        assertEquals("waitAsync(id(\"a\")).scrollForward().sync()", FlowCodeStyle.render("id(\"a\")", CodeGenerator.WAIT_FOR, "scrollForward()"))
        assertEquals("waitAsync(id(\"a\")).sync()", FlowCodeStyle.render("id(\"a\")", CodeGenerator.WAIT_FOR, ""))
    }

    @Test
    fun otherSearchModesStayClassic() {
        assertNull(FlowCodeStyle.render("id(\"a\")", CodeGenerator.UNTIL_FIND, "click()"))
        assertNull(FlowCodeStyle.render("id(\"a\")", CodeGenerator.EXISTS, ""))
    }

}
