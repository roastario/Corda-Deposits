"use strict";

function onload() {
    setupDialogs();
    const depositLoop = function () {
        refresh();
        setTimeout(function () {
            depositLoop();
        }, 1000);
    };
    depositLoop();
}

function refresh() {
    getBalance();
    getDeposits();
}

function setupDialogs() {


    $(function () {
        $("#dialog").dialog();
    });

    $(function () {
        $("#dialog").dialog("close");
    });

    $(function () {
        $("#deductionViewDialog").dialog({
            autoOpen: true,
            modal: true,
            closeOnEscape: false,
            draggable: true,
            resizable: true,
            height: Math.max(document.documentElement.clientHeight, window.innerHeight || 0) * 0.85,
            width: Math.max(document.documentElement.clientWidth, window.innerWidth || 0) * 0.85,
        });
    });

    $(function () {
        $("#deductionViewDialog").dialog("close");
    });

}

function getBalance() {
    asyncGet('/api/tenantOps/balance', JSON.parse).then(function (balances) {
        let numericBalance = balances[0];
        document.getElementById('balance').innerHTML = "£" + (numericBalance / 100);
        return numericBalance;
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
            if (deposit.refundedAt){
                closedDeposits.push(stateAndRef);
                return;
            }
            if (!deposit.amountDeposited) {
                unfundedDeposits.push(stateAndRef);
            } else if (!deposit.refundRequestedAt) {
                activeDeposits.push(stateAndRef);
            } else if (!deposit.refundedAt && !deposit.sentBackToLandlordAt) {
                depositsAwaitingRefunding.push(stateAndRef);
            } else if (deposit.sentBackToLandlordAt) {
                contestedDeposits.push(stateAndRef);
            }
            else {
                closedDeposits.push(stateAndRef);
            }
        });
        return {
            funded: activeDeposits,
            unfunded: unfundedDeposits,
            waitingForRefund: depositsAwaitingRefunding,
            closed: closedDeposits,
            contested: contestedDeposits
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
            if (!_.isEqual(splitDeposits.waitingForRefund, window.waitingForRefund)) {
                populateInactiveDeposits(splitDeposits.waitingForRefund, "waitingDeposits");
                window.waitingForRefund = splitDeposits.waitingForRefund;
            }
        }, 0);
        setTimeout(function () {
            populateInactiveDeposits(splitDeposits.closed, "refundedDeposits");
        }, 0);
        setTimeout(function () {
            populateInactiveDeposits(splitDeposits.contested, "contestedDeposits");
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
        console.log("funded deposit: " + JSON.stringify(uniqueId));
        return true;
    }).then(function () {
        refresh();
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
        // fundDepositButton.disabled = true;

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
        console.log("requested refund for deposit: " + JSON.stringify(uniqueId));
        return true;
    }).then(function () {
        refresh();
    }).catch(function (rejection) {
        console.log(rejection);
    })
}

function removeExistingDeduction(deposit, deduction) {
    deposit.tenantDeductions = deposit.tenantDeductions ? deposit.tenantDeductions : [];
    const filtered = _.filter(deposit.tenantDeductions, function (currentDeduction) {
        return !_.isEqual(currentDeduction.deductionId.id, deduction.deductionId.id);
    });
    deposit.tenantDeductions = filtered;
    return deposit.tenantDeductions;
}

function viewAndContestDeductions(deposit) {

    const deductions = deposit.landlordDeductions;
    const tenantDeductions = deposit.tenantDeductions;

    const indexer = function (value) {
        return value.deductionId.id;
    };
    const indexedTenantDeductions = _.keyBy(tenantDeductions, indexer);

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




        if (indexedTenantDeductions[deduction.deductionId.id]) {
            //this is a deduction with a corresponding tenant deduction
            const tenantCostCell = document.createElement('td');

            tenantCostCell.innerHTML =  indexedTenantDeductions[deduction.deductionId.id].deductionAmount;

            if (indexedTenantDeductions[deduction.deductionId.id].deductionAmount === deductionAmount){
                tenantCostCell.style.backgroundColor = 'green';
            }else{
                tenantCostCell.style.backgroundColor = 'red'
            }
            rowHolder.appendChild(tenantCostCell);

        } else {
            const alternateCostCell = document.createElement('td');
            const alternateCostInput = document.createElement('input');
            alternateCostInput.setAttribute('type', 'number');
            alternateCostInput.disabled = false;

            alternateCostInput.oninput = function (event) {
                const value = parseFloat(this.value);
                const tenantVersion = _.clone(deduction);
                tenantVersion.deductionAmount = deduction.deductionAmount.replace(NUMERIC_REGEXP, value.toFixed(2));
                deposit.tenantDeductions = removeExistingDeduction(deposit, deduction);
                deposit.tenantDeductions.push(tenantVersion);
            };

            const alternateCostLabel = document.createElement('span');
            alternateCostLabel.innerHTML = 'Alternate Deduction Amount ';
            alternateCostCell.appendChild(alternateCostLabel);
            alternateCostCell.appendChild(alternateCostInput);
            const acceptTickBox = document.createElement("input");
            acceptTickBox.setAttribute("type", "checkbox");
            acceptTickBox.onchange = function (event) {
                const isChecked = this.checked;
                deposit.tenantDeductions = removeExistingDeduction(deposit, deduction);
                if (isChecked) {
                    alternateCostInput.value = "";
                    deposit.tenantDeductions.push(deduction);
                    alternateCostInput.disabled = true;
                } else {
                    alternateCostInput.disabled = false;
                }
            };
            const deductionAcceptCell = document.createElement('td');
            const acceptLabel = document.createElement('span');
            acceptLabel.innerHTML = 'Accept Deduction ';
            deductionAcceptCell.appendChild(acceptLabel);
            deductionAcceptCell.appendChild(acceptTickBox);
            rowHolder.appendChild(deductionAcceptCell);
            rowHolder.appendChild(alternateCostCell);
        }


    });

    $(function () {
        const deductionViewDialog = $("#deductionViewDialog");
        if (!_.isNil(deposit.sentBackToTenantAt) && _.isNil(deposit.sentBackToLandlordAt)) {
            deductionViewDialog.dialog("option", "buttons",
                [
                    {
                        text: "Submit",
                        icon: "ui-icon-heart",
                        click: function () {
                            sendDepositToLandlord(deposit).then(function (response) {
                                console.log("sent deposit: " + deposit.linearId.externalId + " back to landlord");
                                deductionViewDialog.dialog("close");
                            })
                        }
                    }
                ]
            );
        }
        deductionViewDialog.dialog("open");
    });
}

async function sendDepositToLandlord(deposit) {
    console.log("sending: " + JSON.stringify(deposit));
    const deductions = {
        forDeposit: deposit.linearId,
        deductions: deposit.tenantDeductions,
    };
    return asyncPost(deductions, '/api/tenantOps/handover', function (resolvedResponse) {
        getDeposits();
        return resolvedResponse;
    }, 10000);
}




