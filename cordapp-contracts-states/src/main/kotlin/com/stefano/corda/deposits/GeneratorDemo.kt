package com.stefano.corda.deposits

import com.squareup.kotlinpoet.ClassName
import com.stefano.corda.deposits.GeneratedWorkFlow.Companion.getEnd
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

data class DemoState(val A: Boolean? = null, val B: Boolean? = null, val C: Boolean? = null, val D: Boolean? = null,
                     override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState {
    override val participants: List<AbstractParty> get() = listOf();
}


class ContinuingTransition<T : LinearState>(
        val clazz: KClass<T>,
        val previousState: ContinuingTransition<T>?,
        val newStageName: String,
        val noLongerNull: Set<KProperty<Any?>>?,
        var nextStage: ContinuingTransition<T>? = null) {


    fun transition(newStageName: String, noLongerNull: Set<KProperty<Any?>>): ContinuingTransition<T> {
        val setOfNonNulls = HashSet(this.noLongerNull ?: setOf())
        setOfNonNulls.addAll(noLongerNull);
        val continuingTransition = ContinuingTransition(this.clazz, this, newStageName, setOfNonNulls)
        this.nextStage = continuingTransition;
        return continuingTransition
    }

    fun transition(newStageName: String, noLongerNull: KProperty<Any?>): ContinuingTransition<T> {
        return transition(newStageName, setOf(noLongerNull))
    }
}


fun <T : LinearState> wrap(stateClass: KClass<T>): ContinuingTransition<T> {
    return ContinuingTransition(stateClass, null, "start", null)
}

class GeneratedWorkFlow<T : LinearState>(val start: ContinuingTransition<T>, val end: ContinuingTransition<T> = getEnd(start)) {


    object Companion {
        fun <T : LinearState> getEnd(start: ContinuingTransition<T>): ContinuingTransition<T> {
            var next: ContinuingTransition<T>? = start;
            var previous: ContinuingTransition<T>? = null
            while (next != null) {
                previous = next;
                next = next.nextStage;
            }
            return previous!!;
        }
    }


    fun printOutFlow() {
        var next: ContinuingTransition<T>? = start;
        var previous: ContinuingTransition<T>? = null
        while (next != null) {
            println(previous?.newStageName + " -> " + next.newStageName + " must not be null: " + (next.noLongerNull ?: setOf()).map { it.name })
            previous = next;
            next = next.nextStage;
        }
    }

    fun inOrder(): Iterable<ContinuingTransition<T>> {
        return Iterable<ContinuingTransition<T>>({
            object : Iterator<ContinuingTransition<T>> {
                var next: ContinuingTransition<T>? = start;
                override fun hasNext(): Boolean {
                    return next != null
                }

                override fun next(): ContinuingTransition<T> {
                    val current = next;
                    next = next!!.nextStage;
                    return current!!;
                }
            }
        });
    }

    fun reversedOrder(): Iterable<ContinuingTransition<T>> {

        return inOrder().reversed();

    }

    fun buildIsStageChecks() {

        val className = start.clazz.simpleName + "TransitionChecks"
        val classData = ClassName(start.clazz.qualifiedName!!.replace("." + start.clazz.simpleName, ""), className)

        reversedOrder().forEach { transition ->
            println(transition.newStageName)
        }

        inOrder().map { it.newStageName }.forEach(::println)


//        val file = FileSpec.builder("", "HelloWorld")
//                .addType(TypeSpec.classBuilder(className)
//                        .primaryConstructor(FunSpec.constructorBuilder()
//                                .addParameter("toCheck", start.clazz)
//                                .build())
//                        .addProperty(PropertySpec.builder("name", String::class)
//                                .initializer("name")
//                                .build())
//                        .addFunction(FunSpec.builder("")
//                                .addStatement("println(%S)", "Hello, \$name")
//                                .build())
//                        .build())
//                .addFunction(FunSpec.builder("main")
//                        .addParameter("args", String::class, KModifier.VARARG)
//                        .addStatement("%T(args[0]).greet()", classData)
//                        .build())
//
//                .build()
//
//        file.writeTo(System.out)


    }
}

fun main(args: Array<String>) {

    val start = wrap(DemoState::class)
    val stage1 = start.transition("open", DemoState::A)
    val stage2 = stage1.transition("waitingForFunding", DemoState::B)
    val stage3 = stage2.transition("waitingForSignOf", DemoState::C)
    val stage4 = stage3.transition("closed", DemoState::D)

    GeneratedWorkFlow(start).buildIsStageChecks()


}