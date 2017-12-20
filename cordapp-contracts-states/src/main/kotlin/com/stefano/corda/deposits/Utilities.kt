package com.stefano.corda.deposits

import net.corda.core.contracts.requireThat
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties

fun <T : Any> T.isEqualToExcluding(thing: T, toExclude: Set<KProperty<Any?>>): Boolean {
    return areEqualToExcluding(this, thing, toExclude);
}

fun <T : Any> areEqualToExcluding(input1: T, input2: T, propertiesToExclude: Set<KProperty<Any?>>) : Boolean {
    val kClass = input1::class
    requireThat {
        "must be a data class" using (kClass.isData)
    }
    val declaredMemberProperties : Collection<KProperty<Any?>> = kClass.memberProperties
    for (declaredMemberProperty in declaredMemberProperties) {
        if (propertiesToExclude.contains(declaredMemberProperty)){
            continue;
        }else{
            val result1 = declaredMemberProperty.call(input1)
            val result2 = declaredMemberProperty.call(input2)
            if (result1 != result2){
                return false
            }
        }
    }
    return true;
}


data class TestClass(
        val name: String,
        val num: Number,
        val likesCheese: Boolean
)

fun main(args: Array<String>) {
    println(areEqualToExcluding(
            TestClass("yes", 10, false),
            TestClass("yes", 10, false),
            setOf(TestClass::likesCheese)
    ))

    println(areEqualToExcluding(
            TestClass("yes", 10, false),
            TestClass("yes", 10, true),
            setOf(TestClass::likesCheese)
    ))

    println(areEqualToExcluding(
            TestClass("yes", 10, false),
            TestClass("no", 10, false),
            setOf(TestClass::name)
    ))

    println(areEqualToExcluding(
            TestClass("yes", 10, false),
            TestClass("yes", 10, true),
            setOf(TestClass::name)
    ))

    println(
            TestClass("yes", 10, false)
                    .isEqualToExcluding(TestClass("yes", 10, true), setOf(TestClass::name))
    )

}