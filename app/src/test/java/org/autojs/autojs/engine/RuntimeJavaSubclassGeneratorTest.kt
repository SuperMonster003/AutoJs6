package org.autojs.autojs.engine

import org.junit.Assert.*
import org.junit.Test
import java.util.Comparator

class RuntimeJavaSubclassGeneratorTest {
    private class Loader:ClassLoader(RuntimeJavaCallbackHandler::class.java.classLoader) {
        fun define(name:String,bytes:ByteArray)=defineClass(name,bytes,0,bytes.size)
    }
    @Test fun constructorCallbacksProtectedOverridesAndConcreteSuperCalls() {
        val base=JavaSubclassFixture::class.java
        val methods=RuntimeJavaSubclassGenerator.methods(base,emptyList(),setOf("decorate","sum","twice"))
        val name="owned.多态"
        val type=Loader().define(name,RuntimeJavaSubclassGenerator.generate(name,base,emptyList(),methods))
        val handler=object:RuntimeJavaCallbackHandler {
            override fun invoke(receiver:Any,method:Int,arguments:Array<Any?>):Any? = when(methods[method].name) {
                "decorate"->"wrapped:"+arguments[0]
                "sum"->(type.getMethod(RuntimeJavaSubclassGenerator.superName(method),Int::class.javaPrimitiveType,Int::class.javaPrimitiveType)
                    .invoke(receiver,*arguments) as Int)+10
                "twice"->(arguments[0] as Long)*3
                else->error("Unexpected method")
            }
        }
        val value=type.getConstructor(RuntimeJavaCallbackHandler::class.java,String::class.java).newInstance(handler,"seed") as JavaSubclassFixture
        assertEquals("wrapped:seed",value.constructed);assertEquals(15,value.sum(2,3));assertEquals(21L,type.getMethod("twice",Long::class.javaPrimitiveType).invoke(value,7L))
        assertTrue(value is RuntimeJavaAdapter)
    }
    @Test fun interfaceProxyInheritsDefaultMethodsAndBoxesPrimitiveReturns() {
        val interfaces=listOf(Comparator::class.java)
        val methods=RuntimeJavaSubclassGenerator.methods(Any::class.java,interfaces,setOf("*"))
        val type=Loader().define("owned.GeneratedComparator",RuntimeJavaSubclassGenerator.generate("owned.GeneratedComparator",Any::class.java,interfaces,methods))
        @Suppress("UNCHECKED_CAST") val comparator=type.getConstructor(RuntimeJavaCallbackHandler::class.java).newInstance(object:RuntimeJavaCallbackHandler {
            override fun invoke(receiver:Any,method:Int,arguments:Array<Any?>):Any = (arguments[0] as String).compareTo(arguments[1] as String)
        }) as Comparator<String>
        assertTrue(comparator.compare("a","b")<0);assertTrue(comparator.reversed().compare("a","b")>0)
    }
    @Test fun classNamesAcceptJavaIdentifiersWithoutInventedAsciiOrPackageRestrictions() {
        for(name in listOf("Adapter","owned.多态","owned.Adapter\$Nested"))assertTrue(name,RuntimeJavaSubclassGenerator.validName(name))
        for(name in listOf("","owned..Adapter","1Adapter","owned.Bad/Name"))assertFalse(name,RuntimeJavaSubclassGenerator.validName(name))
    }
    @Test fun missingAbstractAndFinalOverridesRejectBeforeGeneratingCode() {
        for(handlers in listOf(emptySet(),setOf("decorate","fixed"),setOf("decorate","missing"))) {
            try {RuntimeJavaSubclassGenerator.methods(JavaSubclassFixture::class.java,emptyList(),handlers);fail("Invalid definition")}
            catch (_:IllegalArgumentException) {}
        }
    }
    @Test fun abstractInterfacesInheritedThroughSuperclassRemainRequired() {
        val base=JavaSubclassFixture.InheritedBase::class.java
        try { RuntimeJavaSubclassGenerator.methods(base,emptyList(),emptySet());fail("Inherited abstract method") }catch (_:IllegalArgumentException) {}
        val methods=RuntimeJavaSubclassGenerator.methods(base,emptyList(),setOf("inherited"))
        val type=Loader().define("owned.InheritedAdapter",RuntimeJavaSubclassGenerator.generate("owned.InheritedAdapter",base,emptyList(),methods))
        val value=type.getConstructor(RuntimeJavaCallbackHandler::class.java).newInstance(object:RuntimeJavaCallbackHandler {
            override fun invoke(receiver:Any,method:Int,arguments:Array<Any?>):Any="implemented"
        }) as JavaSubclassFixture.ParentInterface
        assertEquals("implemented",value.inherited());assertEquals("inherited-default",value.defaultName())
    }
}
