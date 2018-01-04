package com.stefano.corda.deposits

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.Cash

open class DepositContract : Contract {
    companion object {
        @JvmStatic
        val DEPOSIT_CONTRACT_ID = "com.stefano.corda.deposits.DepositContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value){
            is Commands.Create -> {
                requireThat {

                    val depositInputs =  tx.inputsOfType<DepositState>()
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

                    val depositInputStates =  tx.inputsOfType<DepositState>()
                    val cashInputs =  tx.inputsOfType<Cash.State>()
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
                    "there can be no landlord deductions or landlord deductions and tenant deductions must match" using
                            (outputDeposit.landlordDeductions == null || outputDeposit.landlordDeductions.isEmpty() ||outputDeposit.landlordDeductions == outputDeposit.tenantDeductions)
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
                    "this deposit must not be refunded or already sent to tenant" using
                            (inputDeposit.refundedAt == null && inputDeposit.sentBackToLandlordAt == null)

                    "output state must have a sent to landlord time " using
                            (outputDeposit.sentBackToLandlordAt != null)
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
    }
}
