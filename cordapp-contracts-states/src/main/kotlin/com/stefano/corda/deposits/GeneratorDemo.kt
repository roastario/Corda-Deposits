package com.stefano.corda.deposits

import com.squareup.kotlinpoet.*
import com.stefano.corda.deposits.GeneratedWorkFlow.Companion.getEnd
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import java.util.*
import kotlin.collections.HashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

data class DemoState(val A: Boolean? = null,
                     val B: Boolean? = null,
                     val C: Boolean? = null,
                     val D: Boolean? = null,
                     val issuer: String,
                     val owner: String,
                     override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState {
    override val participants: List<AbstractParty> get() = listOf();
}


data class ContinuingTransition<T : LinearState>(
        val clazz: KClass<T>,
        val previousState: ContinuingTransition<T>?,
        val stageName: String,
        val noLongerNull: Set<KProperty<Any?>> = setOf(),
        var nextStage: ContinuingTransition<T>? = null,
        val partyAllowedToTransition: KProperty<Any>?) {


    fun transition(newStageName: String, noLongerNull: Set<KProperty<Any?>>, allowedTransitioner: KProperty<Any>): ContinuingTransition<T> {
        val setOfNonNulls = HashSet(this.noLongerNull)
        setOfNonNulls.addAll(noLongerNull);
        val continuingTransition = ContinuingTransition(this.clazz, this, newStageName, setOfNonNulls, null, allowedTransitioner)
        this.nextStage = continuingTransition;
        return continuingTransition
    }

    fun transition(newStageName: String, noLongerNull: KProperty<Any?>, allowedTransitioner: KProperty<Any>): ContinuingTransition<T> {
        return transition(newStageName, setOf(noLongerNull), allowedTransitioner)
    }
}


fun <T : LinearState> wrap(stateClass: KClass<T>): ContinuingTransition<T> {
    return ContinuingTransition(stateClass, null, "start", setOf(), null, null)
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


    fun printOutFlow(): GeneratedWorkFlow<T> {
        var next: ContinuingTransition<T>? = start;
        var previous: ContinuingTransition<T>? = null
        while (next != null) {
            println(previous?.stageName + " -> " + next.stageName + " must not be null: " + next.noLongerNull.map { it.name })
            previous = next;
            next = next.nextStage;
        }

        return this;
    }

    private fun inOrder(toStartFrom: ContinuingTransition<T>): Iterable<ContinuingTransition<T>> {
        return Iterable<ContinuingTransition<T>>({
            object : Iterator<ContinuingTransition<T>> {
                var next: ContinuingTransition<T>? = toStartFrom;
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

    private fun reversedOrder(toStartFrom: ContinuingTransition<T>, toEndAt: ContinuingTransition<T>?): Iterable<ContinuingTransition<T>> {

        return Iterable({
            object : Iterator<ContinuingTransition<T>> {
                var previous: ContinuingTransition<T>? = toStartFrom;
                override fun hasNext(): Boolean {
                    return previous != null && previous != toEndAt
                }

                override fun next(): ContinuingTransition<T> {
                    val current = previous;
                    previous = previous!!.previousState;
                    return current!!;
                }
            }
        });

    }

    fun generate() {
        val file = FileSpec.builder(start.clazz.java.`package`.name, start.clazz.simpleName + "Checks")
        buildStageEnumConstants(file)
        buildIsStageChecks(file)
        buildWhatStageCheck(file)
        buildTransitionChecks(file)
        file.build().writeTo(System.out)
    }

    private fun buildStageEnumConstants(fileSpec: FileSpec.Builder) {
        val typeSpec = TypeSpec.enumBuilder(buildEnumTypeNameForState());
        inOrder(start).forEach { stage ->
            typeSpec.addEnumConstant(buildEnumConstantForStageName(stage))
        }
        fileSpec.addType(typeSpec.build())
    }

    private fun buildEnumTypeNameForState() = start.clazz.simpleName + "Stages"

    private fun buildEnumConstantForStageName(stage: ContinuingTransition<T>) =
            stage.stageName.toUpperCase()

    private fun buildIsStageChecks(fileSpec: FileSpec.Builder) {
        inOrder(start).forEach { it ->
            if (it.noLongerNull.isNotEmpty()) {
                val functionPointer = FunSpec.builder(buildStageCheckName(it))
                        .addParameter("toCheck", start.clazz)
                        .returns(Boolean::class)

                //build the check for each non null property
                it.noLongerNull.forEach { nonNullProperty ->
                    functionPointer.beginControlFlow("if (%T::%L.get(toCheck) == null)", start.clazz, nonNullProperty.name)
                            .addStatement("return false")
                            .endControlFlow()
                }

                //now enforce the check that it is not in any other further along states
                val otherStatesCheck = reversedOrder(end, it).joinToString(separator = " && ", transform = { otherStage ->
                    "!" + buildStageCheckName(otherStage) + "(toCheck)"
                })

                functionPointer.addStatement("return " + if (otherStatesCheck.isEmpty()) "true" else otherStatesCheck);
                fileSpec.addFunction(functionPointer.build())
            }
        }
    }

    private fun buildStageCheckName(it: ContinuingTransition<T>) =
            "isInStage" + it.stageName.capitalize()


    private fun buildWhatStageCheck(fileSpec: FileSpec.Builder) {

        val functionBuilder = FunSpec.builder("getStage").addParameter("toCheck", start.clazz)
        reversedOrder(end, start).forEach { stage ->
            functionBuilder.beginControlFlow("if (" + buildStageCheckName(stage) + "(toCheck))")
            functionBuilder.addStatement("return " + buildEnumTypeNameForState() + "." + buildEnumConstantForStageName(stage))
            functionBuilder.endControlFlow()
        }

        functionBuilder.returns(ClassName.bestGuess(buildEnumTypeNameForState()))
        functionBuilder.addStatement("throw %T()", IllegalStateException::class)
        fileSpec.addFunction(functionBuilder.build())
    }

    private fun buildTransitionChecks(fileSpec: FileSpec.Builder) {
        val functionBuilder = FunSpec.builder("canTransition")
                .addParameter("input", start.clazz)
                .addParameter("output", start.clazz)
                .addParameter("transitionToken", ClassName.bestGuess("Any?"))
                .returns(Boolean::class)


        reversedOrder(end, null).forEach { stage ->
            functionBuilder.beginControlFlow("if (getStage(input) === %L)", buildEnumTypeNameForState() + "." + buildEnumConstantForStageName(stage))
            var returnStatement = "return getStage(output) === %L";
            stage.nextStage?.let { nextStage ->
                nextStage.partyAllowedToTransition?.let { transitionTokenGetter ->
                    returnStatement = returnStatement +
                            " && " + stage.clazz.simpleName + "::" + transitionTokenGetter.name +
                            ".get(input).equals(transitionToken);"
                }
                functionBuilder.addStatement(returnStatement, buildEnumTypeNameForState() + "." + buildEnumConstantForStageName(nextStage))
            }
            functionBuilder.endControlFlow()
        }
        functionBuilder.addStatement("throw %T()", IllegalStateException::class)
        fileSpec.addFunction(functionBuilder.build())
    }
}

fun main(args: Array<String>) {


    val start = wrap(DemoState::class)
    val stage1 = start.transition("open", DemoState::A, DemoState::issuer)
    val stage2 = stage1.transition("waitingForFunding", DemoState::B, DemoState::owner)
    val stage3 = stage2.transition("waitingForSignOf", DemoState::C, DemoState::issuer)
    val stage4 = stage3.transition("closed", DemoState::D, DemoState::issuer)


    val demoOpen = DemoState(A = true, issuer = "issuer", owner = "owner")
    val demoWaitingForFunding = DemoState(A = true, B = true, issuer = "issuer", owner = "owner")
    val demoWaitingForSignOf = DemoState(A = true, B = true, C = true, issuer = "issuer", owner = "owner")
    val demoClosed = DemoState(A = true, B = true, C = true, D = true, issuer = "issuer", owner = "owner")

    println(isInStageOpen(demoOpen)) //true
    println(isInStageOpen(demoWaitingForFunding)) // false
    println(isInStageWaitingForFunding(demoWaitingForSignOf)) // false
    println(isInStageWaitingForFunding(demoWaitingForFunding)) // true


    println(canTransition(demoOpen, demoWaitingForFunding, "issuer")) // false (wrong transition token)
    println(canTransition(demoOpen, demoWaitingForFunding, "owner")) // true
    println(canTransition(demoOpen, demoWaitingForSignOf, "issuer")) // false (wrong output stage)
    println(canTransition(demoWaitingForSignOf, demoClosed, "issuer")) // true

    GeneratedWorkFlow<DemoState>(start).printOutFlow().generate()


}