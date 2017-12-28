"use strict";

function onload() {
    getBalance();
    getDeposits();
    setTimeout(function () {
        onload();
    }, 1000);
}

function getBalance() {

    asyncGet('/api/tenantOps/balance', JSON.parse).then(function (balances) {
        let numericBalance = balances[0];
        document.getElementById('balance').innerHTML = "£" + (numericBalance / 100);
        return numericBalance;
    }).then(function (balancesByCurrency) {
        console.log(balancesByCurrency);
    });
}

function getDeposits() {
    asyncGet('/api/tenantOps/mydeposits', JSON.parse).then(function (loadedDeposits) {
        let unfundedDeposits = [];
        let activeDeposits = [];
        let depositsAwaitingRefunding = [];
        let contestedDeposits = [];
        let closedDeposits = [];

        loadedDeposits.forEach(stateAndRef => {
            let deposit = stateAndRef.state.data;
            if (!deposit.amountDeposited) {
                unfundedDeposits.push(stateAndRef);
            } else if (!deposit.refundRequestedAt) {
                activeDeposits.push(stateAndRef);
            } else if (!deposit.refunded) {
                depositsAwaitingRefunding.push(stateAndRef);
            } else if (deposit.landlordDeductions) {
                contestedDeposits.push(deposit);
            }
            else {
                closedDeposits.push(stateAndRef);
            }
        });
        return {
            funded: activeDeposits,
            unfunded: unfundedDeposits,
            waitingForRefund: depositsAwaitingRefunding,
            closed: closedDeposits
        }
    }).then(splitDeposits => {
        setTimeout(function () {
            if (!_.isEqual(splitDeposits.funded, window.fundedDeposits)) {
                populateFundedDepositTable(splitDeposits.funded);
                window.fundedDeposits = splitDeposits.funded;
            }
        }, 0);
        setTimeout(function () {
            if (!_.isEqual(splitDeposits.unfunded, window.unfundedDeposits)) {
                populateUnfundedDepositTable(splitDeposits.unfunded);
                window.unfundedDeposits = splitDeposits.unfunded;
            }
        }, 0);
        setTimeout(function () {
            populateInactiveDeposits(splitDeposits.waitingForRefund, "waitingDeposits");
        }, 0);
        setTimeout(function () {
            populateInactiveDeposits(splitDeposits.closed, "refundedDeposits");
        }, 0);
    });
}


function loadAndShowInventory(inventoryHash, postRender) {
    asyncDownload('/api/tenantOps/inventory?attachmentId=' + inventoryHash).then(function (data) {
        let typedArray = (new Uint8Array(data));
        return renderPdfBytesToHolder(typedArray, 'pdfHolder', 'dialog')
    }).then(function (pdfRender) {
        if (postRender) {
            postRender();
        }
    })
}

function fundDeposit(uniqueId) {
    asyncPost(uniqueId, '/api/tenantOps/fundDeposit', JSON.parse, 10000).then(function (response) {
        console.log("funded deposit: " + uniqueId);
        return true;
    }).then(function () {
        onload();
    }).catch(function (rejection) {
        console.log(rejection);
    })
}

function populateInactiveDeposits(loadedDeposits, tableId) {

    let table = document.getElementById(tableId);
    table.innerHTML = "";

    (loadedDeposits || []).forEach(function (depositState) {
        let loadedDeposit = depositState.state.data;
        let row = document.createElement('tr');
        let propertyIdCell = document.createElement('td');
        let landlordCell = document.createElement('td');
        let depositAmountCell = document.createElement('td');

        let deductionTotalCell = document.createElement('td');
        let deductionViewCell = document.createElement('td');

        propertyIdCell.innerHTML = loadedDeposit.propertyId;
        landlordCell.innerHTML = loadedDeposit.landlord;
        depositAmountCell.innerHTML = loadedDeposit.depositAmount;

        if (loadedDeposit.landlordDeductions) {
            let totalDeduction = 0;
            loadedDeposit.landlordDeductions.forEach(deduction => {
                totalDeduction = parseFloat(deduction.deductionAmount.match(NUMERIC_REGEXP)[0]) + totalDeduction;
            });

            const viewDeductionButton = document.createElement('button');

            deductionTotalCell.innerHTML = '-[ ' + totalDeduction + " ]";
            viewDeductionButton.innerHTML = 'view deductions';
            deductionViewCell.appendChild(viewDeductionButton);

            viewDeductionButton.onclick = function () {
                viewAndContestDeductions(loadedDeposit);
            }
        }

        row.appendChild(propertyIdCell);
        row.appendChild(landlordCell);
        row.appendChild(depositAmountCell);
        row.appendChild(deductionTotalCell);
        row.appendChild(deductionViewCell);

        table.appendChild(row);
    })
}

function populateFundedDepositTable(loadedDeposits) {
    let table = document.getElementById('fundedDepositTable');
    table.innerHTML = "";

    loadedDeposits.forEach(function (depositState) {
        let loadedDeposit = depositState.state.data;
        let row = document.createElement('tr');
        let propertyIdCell = document.createElement('td');
        let landlordCell = document.createElement('td');
        let depositAmountCell = document.createElement('td');

        let showInventoryCell = document.createElement('td');
        let showInventoryButton = document.createElement('button');

        let requestRefundCell = document.createElement('td');
        let requestRefundButton = document.createElement('button');

        requestRefundButton.innerHTML = "Request Refund";
        requestRefundCell.appendChild(requestRefundButton);
        requestRefundButton.onclick = function () {
            requestRefund(loadedDeposit.linearId);
        };

        showInventoryButton.innerHTML = "Show Inventory";
        showInventoryCell.appendChild(showInventoryButton);
        showInventoryButton.onclick = function () {
            loadAndShowInventory(loadedDeposit.inventory)
        };

        propertyIdCell.innerHTML = loadedDeposit.propertyId;
        landlordCell.innerHTML = loadedDeposit.landlord;
        depositAmountCell.innerHTML = loadedDeposit.depositAmount;

        row.appendChild(propertyIdCell);
        row.appendChild(landlordCell);
        row.appendChild(depositAmountCell);
        row.appendChild(showInventoryCell);
        row.appendChild(requestRefundCell);

        table.appendChild(row);
    })

}

function populateUnfundedDepositTable(loadedDeposits) {

    let table = document.getElementById('unfundedDepositTable');
    table.innerHTML = "";

    loadedDeposits.forEach(function (depositState) {
        let loadedDeposit = depositState.state.data;
        let row = document.createElement('tr');
        let propertyIdCell = document.createElement('td');
        let landlordCell = document.createElement('td');
        let depositAmountCell = document.createElement('td');

        let showInventoryCell = document.createElement('td');
        let showInventoryButton = document.createElement('button');

        let fundDepositCell = document.createElement('td');
        let fundDepositButton = document.createElement('button');
        fundDepositButton.innerHTML = "Fund Deposit";
        fundDepositCell.appendChild(fundDepositButton);
        fundDepositButton.onclick = function () {
            fundDeposit(loadedDeposit.linearId);
        };
        fundDepositButton.disabled = true;

        showInventoryButton.innerHTML = "Show Inventory";
        showInventoryCell.appendChild(showInventoryButton);
        showInventoryButton.onclick = function () {
            loadAndShowInventory(loadedDeposit.inventory, function () {
                fundDepositButton.disabled = false;
            })
        };


        propertyIdCell.innerHTML = loadedDeposit.propertyId;
        landlordCell.innerHTML = loadedDeposit.landlord;
        depositAmountCell.innerHTML = loadedDeposit.depositAmount;

        row.appendChild(propertyIdCell);
        row.appendChild(landlordCell);
        row.appendChild(depositAmountCell);
        row.appendChild(showInventoryCell);
        row.appendChild(fundDepositCell);


        table.appendChild(row);
    })
}

async function requestRefund(uniqueId) {
    return asyncPost(uniqueId, '/api/tenantOps/refund', i => i, 10000).then(function (response) {
        console.log("requested refund for deposit: " + (uniqueId));
        return true;
    }).then(function () {
        onload();
    }).catch(function (rejection) {
        console.log(rejection);
    })
}

function viewAndContestDeductions(deposit) {

    const deductions = deposit.landlordDeductions;

    const deductionDialog = document.getElementById('deductionViewDialog');
    deductionDialog.innerHTML = '';

    deductions.forEach(function (deduction) {
        const deductionAmount = deduction.deductionAmount;
        const deductionReason = deduction.deductionReason;
        const imageHash = deduction.picture;
        const deductionRow = document.createElement('div');
        deductionRow.classList.add('deductionRow');

        deductionRow.dataset.deduction = deduction;
        deductionRow.dataset.deposit = deposit;

        const deductionTable = document.createElement('table');

        const rowHolder = document.createElement('tr');
        deductionTable.appendChild(rowHolder);

        const deductionAmountCell = document.createElement('td');
        deductionAmountCell.innerHTML = deductionReason;

        const deductionReasonCell = document.createElement('td');
        deductionReasonCell.innerHTML = deductionAmount;

        const deductionImageCell = document.createElement('td');

        deductionDialog.appendChild(deductionRow);

        rowHolder.appendChild(deductionImageCell);
        rowHolder.appendChild(deductionAmountCell);
        rowHolder.appendChild(deductionReasonCell);

        deductionRow.appendChild(deductionTable);

        asyncDownload('/api/tenantOps/deductionImage?imageId=' + imageHash).then(function (binaryArrayBuffer) {
            const base64Data = base64ArrayBuffer(binaryArrayBuffer);
            const outputImg = document.createElement('img');
            outputImg.style.width = '500px';
            outputImg.style.height = '500px';
            outputImg.src = 'data:image/png;base64,' + base64Data;
            deductionImageCell.appendChild(outputImg);
        });

        const acceptTickBox = document.createElement("input");
        acceptTickBox.setAttribute("type", "checkbox");
        acceptTickBox.onchange = function (event) {
            const isChecked = this.checked;
            if (isChecked) {
                if (!deposit.tenantDeductions) {
                    deposit.tenantDeductions = [];
                }
                deposit.tenantDeductions.push(deduction);
            } else {
                deposit.tenantDeductions = _.filter(deposit.tenantDeductions, function (currentDeduction) {
                    return !_.isEqual(currentDeduction, deduction);
                });
            }
            console.log(deposit.tenantDeductions);
        };

        const deductionAcceptCell = document.createElement('td');
        const acceptLabel = document.createElement('span');
        acceptLabel.innerHTML = 'Accept Deduction';
        deductionAcceptCell.appendChild(acceptLabel);
        deductionAcceptCell.appendChild(acceptTickBox);
        rowHolder.appendChild(deductionAcceptCell);
    });

    $(function () {
        $("#deductionViewDialog").dialog("open");
    });
}

async function contestDeductions() {

    const deductionDialog = document.getElementById('deductionDialog');
    const depositId = deductionDialog.depositId;

    const acceptedDeductions = deductionDialog.accepted;
    const contestedDeductions = deductionDialog.contested;

    return asyncPost({
        forDeposit: depositId,
        accepted: acceptedDeductions,
        contested: contestedDeductions
    }, "/api/tenantOps/contest", JSON.parse, 10000);


}

async function loadPeers() {
    return asyncGet('/api/depositOps/peers', function (json) {
        let parsedPeers = JSON.parse(json);
        return parsedPeers['peers'].filter(function (peer) {
            let lowerCaseName = peer.toLowerCase();
            return lowerCaseName.includes('landlord') || lowerCaseName.includes('tenant')
        });
    }).then(function (loadedPeers) {
        populateSelectWithItems(document.getElementById('tenantSelect'), loadedPeers);
        populateSelectWithItems(document.getElementById('landlordSelect'), loadedPeers);
    })
}




