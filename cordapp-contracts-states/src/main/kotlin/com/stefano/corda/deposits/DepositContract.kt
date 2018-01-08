package com.stefano.corda.deposits

import com.stefano.corda.deposits.flow.sumByLong
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.Cash
import java.util.*

open class DepositContract : Contract {
    companion object {
        @JvmStatic
        val DEPOSIT_CONTRACT_ID = "com.stefano.corda.deposits.DepositContract"

        fun sumCashForParty(states: Collection<Cash.State>, party: Party): Amount<Currency> {
            return states.filter { it.owner == party }.fold(Amount(0, states.first().amount.token.product), { acc, state ->
                acc.plus(Amount(state.amount.quantity, state.amount.token.product))
            })
        }

        fun sumDeductions(deductions: Collection<Deduction>?): Long {
            return deductions?.sumByLong { deduction -> deduction.deductionAmount.quantity } ?: 0
        }
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.Create -> {
                requireThat {

                    val depositInputs = tx.inputsOfType<DepositState>()
                    "there must be no deposit input states, this is the initial one" using (depositInputs.isEmpty())
                    "there must be no input states at all" using tx.inputStates.isEmpty()

                    val depositStates = tx.outputsOfType<DepositState>()
                    "there must be only one deposit output state" using (depositStates.size == 1)
                    "there must be only one output state" using (tx.outputs.size == 1)

                    val outputState = depositStates.first()
                    "the landlord and tenants must be different" using (outputState.landlord != outputState.tenant)
                }
            }

            is Commands.Fund -> {
                requireThat {

                    val depositInputStates = tx.inputsOfType<DepositState>()
                    val cashInputs = tx.inputsOfType<Cash.State>()
                    "there must be one incoming deposit input state" using (depositInputStates.size == 1)
                    "there must be a cash input state" using (cashInputs.size == 1)

                    val depositOutputStates = tx.outputsOfType<DepositState>()
                    val cashOutputStates = tx.outputsOfType<Cash.State>()
                    "there must be only one deposit output state" using (depositInputStates.size == 1)

                    val depositOutputState = depositOutputStates.first()
                    val depositInputState = depositInputStates.first()
                    val cashOutput = cashOutputStates.first()

                    "only deposited amount can change on the deposit state" using
                            depositInputState.isEqualToExcluding(depositOutputState, setOf(DepositState::amountDeposited))

                    "the cash amount sent must equal the deposited amount" using (depositOutputState.amountDeposited?.quantity == cashOutput.amount.quantity)
                    "the cash amount must be sent to the deposit backing scheme" using (cashOutput.owner == depositOutputState.issuer)
                }
            }

            is Commands.Refund -> {
                requireThat {
                    val outputDeposit = tx.outputsOfType<DepositState>().first()

                    val tenantDeductionsTotal = sumDeductions(outputDeposit.tenantDeductions)
                    val landlordDeductionsTotal = sumDeductions(outputDeposit.landlordDeductions)

                    "there can be no landlord deductions or landlord deductions and tenant deductions must match" using
                            (outputDeposit.landlordDeductions == null ||
                                    outputDeposit.landlordDeductions.isEmpty() ||
                                    tenantDeductionsTotal == landlordDeductionsTotal)


                    val tenantPayment = sumCashForParty(tx.outputsOfType(), outputDeposit.tenant);
                    val landlordPayment = sumCashForParty(tx.outputsOfType(), outputDeposit.landlord);


                    "payment to landlord must equal the deductions accepted " using
                            (landlordPayment.quantity == landlordDeductionsTotal)

                    "payment to tenant must equal deposit amount minus deductions" using
                            (outputDeposit.depositAmount.minus(tenantPayment) == landlordPayment)

                }
            }

            is Commands.SendBackToTenant -> {
                requireThat {
                    val outputDeposit = tx.outputsOfType<DepositState>().first()
                    "there must be landlord deductions" using
                            (outputDeposit.landlordDeductions != null && !outputDeposit.landlordDeductions.isEmpty())

                    val inputDeposit = tx.inputsOfType<DepositState>().first()
                    "this deposit must not be refunded or already sent to tenant" using
                            (inputDeposit.refundedAt == null && inputDeposit.sentBackToTenantAt == null)

                    "output state must have a sent to tenant time " using
                            (outputDeposit.sentBackToTenantAt != null)
                }
            }

            is Commands.SendBackToLandlord -> {
                requireThat {
                    val outputDeposit = tx.outputsOfType<DepositState>().first()
                    "there must be tenant deductions" using
                            (outputDeposit.tenantDeductions != null && !outputDeposit.tenantDeductions.isEmpty())

                    val inputDeposit = tx.inputsOfType<DepositState>().first()
                    "this deposit must not be refunded or already sent to landlord" using
                            (inputDeposit.refundedAt == null && inputDeposit.sentBackToLandlordAt == null)

                    "output state must have a sent to landlord time " using
                            (outputDeposit.sentBackToLandlordAt != null)
                }
            }

            is Commands.SendToArbitrator -> {
                requireThat {
                    val outputDeposit = tx.outputsOfType<DepositState>().first()
                    val inputDeposit = tx.inputsOfType<DepositState>().first()

                    "this deposit must not be refunded" using
                            (inputDeposit.refundedAt == null)

                    "input deposit state must have a sent to landlord time " using
                            (inputDeposit.sentBackToLandlordAt != null)

                    "input deposit state must have a sent to tenant time " using
                            (inputDeposit.sentBackToTenantAt != null)

                    "there must be landlord deductions" using (
                            inputDeposit.landlordDeductions != null &&
                                    inputDeposit.landlordDeductions.isNotEmpty()
                            )

                    "there must be tenant deductions" using (
                            inputDeposit.tenantDeductions != null &&
                                    inputDeposit.tenantDeductions.isNotEmpty())

                    "output deposit state must have a sent to arbiter time " using
                            (outputDeposit.sentToArbiter != null)

                    "only state modified on deposit can be time sent to arbiter" using
                            outputDeposit.isEqualToExcluding(inputDeposit, setOf(DepositState::sentToArbiter))

                }
            }

            is Commands.Arbitrate -> {
                requireThat {
                    val outputDeposit = tx.outputsOfType<DepositState>().first()
                    val inputDeposit = tx.inputsOfType<DepositState>().first()

                    "there must be landlord deductions" using (
                            inputDeposit.landlordDeductions != null &&
                                    inputDeposit.landlordDeductions.isNotEmpty()
                            )

                    "there must be tenant deductions" using (
                            inputDeposit.tenantDeductions != null &&
                                    inputDeposit.tenantDeductions.isNotEmpty())

                    "this deposit must not be refunded" using
                            (inputDeposit.refundedAt == null)

                    "input deposit state must have a sent to landlord time " using
                            (inputDeposit.sentBackToLandlordAt != null)

                    "input deposit state must have a sent to tenant time " using
                            (inputDeposit.sentBackToTenantAt != null)

                    "input deposit state must have a sent to arbiter time " using
                            (inputDeposit.sentToArbiter != null)

                    "only state modified on deposit can be list of contested deductions and the refund time" using
                            outputDeposit.isEqualToExcluding(inputDeposit, setOf(DepositState::contestedDeductions, DepositState::refundedAt))
                }
            }
        }
    }


    /**
     * This contract only implements one command, Create.
     */
    interface Commands : CommandData {
        data class Create(val propertyId: String) : Commands
        data class AcceptTenantDeductions(val propertyId: String) : Commands
        data class Fund(val propertyId: String) : Commands
        data class LandlordDeduct(val propertyId: String) : Commands
        data class TenantDeduct(val propertyId: String) : Commands
        data class RequestRefund(val propertyId: String) : Commands
        data class SendBackToTenant(val propertyId: String) : Commands
        data class SendBackToLandlord(val propertyId: String) : Commands
        data class Refund(val propertyId: String) : Commands
        data class SendToArbitrator(val propertyId: String) : Commands {
        }

        data class Arbitrate(val propertyId: String) : Commands {
        }
    }
}
