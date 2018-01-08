"use strict";

async function onload() {

    setupDialogs();
    let inventoryInput = document.getElementById('inventoryInput');
    inventoryInput.onchange = function (event) {
        let selectedFile = event.path[0].files[0];
        let fileReader = new FileReader();
        fileReader.onerror = function (error) {
            console.log(error);
        };
        fileReader.onload = function () {
            let typedArray = (new Uint8Array(this.result));
            renderPdfBytesToHolder(typedArray, "pdfHolder", "pdfDialog");
        };

        fileReader.readAsArrayBuffer(selectedFile);
    };

    loadPeers().then(function () {
        let getDepositsLoopable = function () {
            getDeposits();
            getBalance();
            setTimeout(getDepositsLoopable, 1000);
        };

        getDepositsLoopable();
    });
}

function viewDeductions(deposit) {

    const deductions = deposit.landlordDeductions;
    const tenantDeductions = deposit.tenantDeductions;

    const indexedTenantDeductions = _.keyBy(tenantDeductions, function (value) {
        return value.deductionId.id;
    });

    const deductionDialog = document.getElementById('deductionViewDialog');
    deductionDialog.innerHTML = '';

    deductions.forEach(function (deduction) {
        const deductionAmount = deduction.deductionAmount;
        const deductionReason = deduction.deductionReason;
        const imageHash = deduction.picture;
        const deductionTable = document.createElement('table');
        const rowHolder = document.createElement('tr');

        const deductionAmountCell = document.createElement('td');
        const deductionReasonCell = document.createElement('td');

        deductionReasonCell.innerHTML = deductionReason;
        deductionAmountCell.innerHTML = deductionAmount;

        const deductionImageCell = document.createElement('td');

        rowHolder.appendChild(deductionImageCell);
        rowHolder.appendChild(deductionReasonCell);
        rowHolder.appendChild(deductionAmountCell);
        deductionTable.appendChild(rowHolder);

        deductionDialog.appendChild(deductionTable);
        asyncDownload('/api/depositOps/deductionImage?imageId=' + imageHash).then(function (binaryArrayBuffer) {
            const base64Data = base64ArrayBuffer(binaryArrayBuffer);
            const outputImg = document.createElement('img');
            outputImg.style.width = '500px';
            outputImg.style.height = '500px';
            outputImg.src = 'data:image/png;base64,' + base64Data;
            deductionImageCell.appendChild(outputImg);
        });

        if (indexedTenantDeductions[deduction.deductionId.id]) {
            const tenantCostCell = document.createElement('td');
            tenantCostCell.innerHTML = indexedTenantDeductions[deduction.deductionId.id].deductionAmount;
            if (indexedTenantDeductions[deduction.deductionId.id].deductionAmount === deductionAmount) {
                tenantCostCell.style.backgroundColor = 'green';
            } else {
                tenantCostCell.style.backgroundColor = 'red'
            }
            rowHolder.appendChild(tenantCostCell);
        }
    });

    $(function () {
        $("#deductionViewDialog").dialog("open");
    });
}


function setupDialogs() {

    $(function () {
        $("#deductionViewDialog").dialog({
            autoOpen: true,
            modal: true,
            width: 'auto',
            closeOnEscape: false,
            draggable: true,
            resizable: true,
            height: Math.max(document.documentElement.clientHeight, window.innerHeight || 0) * 0.85
        });
    });
    $(function () {
        $("#deductionViewDialog").dialog("close");
    });

    $(function () {
        $("#pdfDialog").dialog();
    });

    $(function () {
        $("#pdfDialog").dialog("close");
    });

    $("#depositStatusDialog").dialog({
        autoOpen: true,
        modal: true,
        width: 'auto',
        closeOnEscape: false,
        draggable: false,
        resizable: false,
        buttons: {}
    });


    $(function () {
        $("#reportDialog").dialog({
            autoOpen: true,
            modal: true,
            width: 'auto',
            closeOnEscape: false,
            draggable: true,
            resizable: true,
            height: Math.max(document.documentElement.clientHeight, window.innerHeight || 0) * 0.85
        });

        $("#reportDialog").dialog('close');
    });

    $(function () {
        $("#depositStatusDialog").dialog("close");
    });


    $(function () {
        $("#deductionDialog").dialog();
    });

    $(function () {
        $("#deductionDialog").dialog("close");
    });
}

async function loadPeers() {
    return asyncGet('/api/depositOps/peers', function (json) {
        let parsedPeers = JSON.parse(json);
        return parsedPeers['peers'];
    }).then(function (loadedPeers) {
        populateSelectWithItems(document.getElementById('tenantSelect'), loadedPeers);
        populateSelectWithItems(document.getElementById('schemeSelect'), loadedPeers);
    })
}

async function sendDepositRequest() {

    $(function () {
        $("#depositStatusDialog").dialog("open");
    });

    let tenantX500 = document.getElementById('tenantSelect').selectedOptions[0].data.obj;
    let schemeX500Name = document.getElementById('schemeSelect').selectedOptions[0].data.obj;

    let amountOfDeposit = document.getElementById('amount').value;
    let propertyId = document.getElementById('propertyId').value;


    return getInventoryBytes().then(function (loadedData) {
        let depositRequest = {
            'schemeX500Name': schemeX500Name,
            'tenantX500Name': tenantX500,
            'amount': amountOfDeposit,
            'propertyId': propertyId,
            'inventory': loadedData
        };
        return asyncPost(depositRequest, '/api/depositOps/createDeposit', function (resolvedResponse) {
            $(function () {
                $("#depositStatusDialog").dialog("close");
            });
            getDeposits();
            return resolvedResponse;
        }, 10000);
    });
}


function populateRefundedDeposits(deposits) {
    let holdingTable = document.getElementById("refundedDeposits");
    holdingTable.innerHTML = "";
    deposits.forEach(deposit => {
        let row = document.createElement('tr');
        let propertyIdCell = document.createElement('td');
        let tenantNameCell = document.createElement('td');
        let depositAmountCell = document.createElement('td');

        let viewReportCell = document.createElement('td');
        let viewReportButton = document.createElement('button');
        viewReportButton.innerHTML = "View Report";
        viewReportCell.appendChild(viewReportButton);
        viewReportButton.onclick = function(){
            populateDepositReport(deposit, document.getElementById('reportDialog'), 'depositOps')
        };


        let viewInventoryCell = document.createElement('td');
        let viewInventoryButton = document.createElement('button');
        viewInventoryButton.onclick = function () {
            loadAndShowInventory(deposit.inventory);
        };
        viewInventoryButton.innerHTML = "View Inventory";
        viewInventoryCell.appendChild(viewInventoryButton);
        
        propertyIdCell.innerHTML = deposit.propertyId;
        tenantNameCell.innerHTML = deposit.tenant;
        depositAmountCell.innerHTML = deposit.depositAmount;
        row.appendChild(propertyIdCell);
        row.appendChild(depositAmountCell);
        row.appendChild(tenantNameCell);
        row.appendChild(viewInventoryButton);
        row.appendChild(viewReportCell);
        holdingTable.appendChild(row);
    });
}

async function getDeposits() {

    return asyncGet("/api/depositOps/deposits", JSON.parse).then((deposits) => {
        return deposits.map((incoming) => {
            return incoming.state.data;
        })
    }).then(deposits => {
        if (!_.isEqual(window.oldDeposits, deposits)) {
            window.oldDeposits = deposits;
            return splitDeposits(deposits)
        }
    }).then(function (processedDeposits) {
        if (processedDeposits) {
            populateDepositsToRefund(_.concat(processedDeposits.waitingForLandlordAfterRefundRequest, processedDeposits.waitingForLandlordAfterDeductions));
            populateDepositsWaitingForFunding(processedDeposits.waitingForFunds);
            populateActiveDeposits(processedDeposits.waitingForRefundRequest);
            populateArbitratedDeposits(processedDeposits.waitingForArbitration);
            populateRefundedDeposits(processedDeposits.refunded);
        }
    })
}

function sendDepositToTenant(depositId) {
    console.log("sending: " + JSON.stringify(depositId));
    return asyncPost(depositId, '/api/depositOps/handover', function (resolvedResponse) {
        getDeposits();
        return resolvedResponse;
    }, 10000);
}

function populateArbitratedDeposits(deposits) {
    // arbitratorDeposits
    let holdingTable = document.getElementById("arbitratorDeposits");
    holdingTable.innerHTML = "";
    deposits.forEach(deposit => {
        let row = document.createElement('tr');
        let propertyIdCell = document.createElement('td');
        let tenantNameCell = document.createElement('td');
        let depositAmountCell = document.createElement('td');
        propertyIdCell.innerHTML = deposit.propertyId;
        tenantNameCell.innerHTML = deposit.tenant;
        depositAmountCell.innerHTML = deposit.depositAmount;
        row.appendChild(propertyIdCell);
        row.appendChild(depositAmountCell);
        row.appendChild(tenantNameCell);
        holdingTable.appendChild(row);
    });
}

function populateDepositsToRefund(deposits) {

    let holdingTable = document.getElementById("refundDeposits");
    holdingTable.innerHTML = "";

    deposits.forEach(deposit => {
        let row = document.createElement('tr');
        let propertyIdCell = document.createElement('td');
        let tenantNameCell = document.createElement('td');
        let depositAmountCell = document.createElement('td');


        let inventoryButtonCell = document.createElement('td');
        let inventoryButton = document.createElement('button');
        inventoryButton.onclick = function () {
            loadAndShowInventory(deposit.inventory);
        };
        inventoryButton.innerHTML = "Show inventory";
        inventoryButtonCell.appendChild(inventoryButton);

        let deductButtonCell = document.createElement('td');

        let viewDeductionsButtonCell = document.createElement('td');
        let refundButtonCell = document.createElement('td');

        if (_.isNil(deposit.sentBackToTenantAt)) {
            //this is a deposit that needs to be sent to a tenant

            let deductButton = document.createElement('button');
            deductButton.onclick = function () {
                beginDeduction(deposit.linearId);
            };
            deductButton.innerHTML = "Request Deduction";
            deductButtonCell.appendChild(deductButton);

            const viewDeductionsButton = document.createElement('button');
            viewDeductionsButton.innerHTML = "View Current Deductions";
            viewDeductionsButton.onclick = function () {
                viewDeductions(deposit);
            };
            const sendDeductionsButton = document.createElement('button');
            sendDeductionsButton.innerHTML = "Send To Tenant";

            sendDeductionsButton.onclick = function () {
                sendDepositToTenant(deposit.linearId);
            };

            viewDeductionsButtonCell.append(viewDeductionsButton);
            viewDeductionsButtonCell.append(sendDeductionsButton);

            if (_.isNil(deposit.landlordDeductions)) {
                let refundButton = document.createElement('button');
                refundButton.onclick = () => {
                    refundDeposit(deposit.linearId);
                };
                refundButton.innerHTML = "Release Refund";
                refundButtonCell.appendChild(refundButton);
            }


        } else if (!_.isNil(deposit.sentBackToTenantAt) && _.isNil(deposit.sentBackToLandlordAt)) {
            const viewDeductionsButton = document.createElement('button');
            viewDeductionsButton.innerHTML = "View Current Deductions";
            viewDeductionsButton.onclick = function () {
                viewDeductions(deposit);
            };
            viewDeductionsButtonCell.append(viewDeductionsButton);
        } else {
            if (_.isEqual(deposit.tenantDeductions, deposit.landlordDeductions) || _.isNil(deposit.tenantDeductions) || deposit.tenantDeductions.length === 0) {
                const viewDeductionsButton = document.createElement('button');
                viewDeductionsButton.innerHTML = "View Current Deductions";
                viewDeductionsButton.onclick = function () {
                    viewDeductions(deposit);
                };
                viewDeductionsButtonCell.append(viewDeductionsButton);

                //this deposit is ready to refund
                let refundButton = document.createElement('button');
                refundButton.onclick = () => {
                    refundDeposit(deposit.linearId);
                };
                refundButton.innerHTML = "Release Refund";
                refundButtonCell.appendChild(refundButton);
            } else {
                const viewDeductionsButton = document.createElement('button');
                viewDeductionsButton.innerHTML = "View Current Deductions";
                viewDeductionsButton.onclick = function () {
                    viewDeductions(deposit);
                };
                viewDeductionsButtonCell.append(viewDeductionsButton);

                //this deposit is ready to refund
                let refundButton = document.createElement('button');
                refundButton.onclick = () => {
                    refundAcceptingTenantDeductions(deposit.linearId);
                };
                refundButton.innerHTML = "Accept and Refund";
                refundButtonCell.appendChild(refundButton);

                const arbitateButton = document.createElement('button');

                arbitateButton.onclick = function () {
                    sendDepositToArbitration(deposit.linearId);
                };

                arbitateButton.innerHTML = "Arbitrate!";
                refundButtonCell.appendChild(arbitateButton)
            }

        }


        propertyIdCell.innerHTML = deposit.propertyId;
        tenantNameCell.innerHTML = deposit.tenant;
        depositAmountCell.innerHTML = deposit.depositAmount;

        row.appendChild(propertyIdCell);
        row.appendChild(depositAmountCell);
        row.appendChild(tenantNameCell);

        row.appendChild(inventoryButtonCell);
        row.appendChild(deductButtonCell);
        row.appendChild(viewDeductionsButtonCell);


        row.appendChild(refundButtonCell);

        holdingTable.appendChild(row);
    })

}


function populateDepositsWaitingForFunding(deposits) {

    let holdingTable = document.getElementById("unfundedDeposits")
    holdingTable.innerHTML = "";


    deposits.forEach(deposit => {
        let row = document.createElement('tr');
        let propertyIdCell = document.createElement('td');
        let tenantNameCell = document.createElement('td');
        let depositAmountCell = document.createElement('td');


        let inventoryButtonCell = document.createElement('td');
        let inventoryButton = document.createElement('button');
        inventoryButton.onclick = function () {
            loadAndShowInventory(deposit.inventory);
        };
        inventoryButton.innerHTML = "Show inventory"
        inventoryButtonCell.appendChild(inventoryButton)


        let cancelButtonCell = document.createElement('td');
        let cancelButton = document.createElement('button');
        cancelButton.onclick = () => {
            cancelDeposit(deposit.linearId);
        };
        cancelButton.innerHTML = "Cancel Funding Request"
        cancelButtonCell.appendChild(cancelButton)

        propertyIdCell.innerHTML = deposit.propertyId;
        tenantNameCell.innerHTML = deposit.tenant;
        depositAmountCell.innerHTML = deposit.depositAmount;

        row.appendChild(propertyIdCell)
        row.appendChild(depositAmountCell)
        row.appendChild(tenantNameCell)

        row.appendChild(inventoryButtonCell)
        row.appendChild(cancelButtonCell)

        holdingTable.appendChild(row);
    })

}

function populateActiveDeposits(deposits) {

    let holdingTable = document.getElementById("activeDeposits");
    holdingTable.innerHTML = "";


    deposits.forEach(deposit => {
        let row = document.createElement('tr');
        let propertyIdCell = document.createElement('td');
        let tenantNameCell = document.createElement('td');
        let depositAmountCell = document.createElement('td');

        propertyIdCell.innerHTML = deposit.propertyId;
        tenantNameCell.innerHTML = deposit.tenant;
        depositAmountCell.innerHTML = deposit.depositAmount;

        row.appendChild(propertyIdCell);
        row.appendChild(depositAmountCell);
        row.appendChild(tenantNameCell);
        holdingTable.appendChild(row);
    })

}

function loadAndShowInventory(inventoryHash, postRender) {
    asyncDownload('/api/depositOps/inventory?attachmentId=' + inventoryHash).then(function (data) {
        let typedArray = (new Uint8Array(data));
        return renderPdfBytesToHolder(typedArray, 'pdfHolder', 'pdfDialog')
    }).then(function (pdfRender) {
        if (postRender) {
            postRender();
        }
    })
}

function refundDeposit(depositId) {
    console.log("refunding: " + depositId);
    return asyncPost(depositId, '/api/depositOps/refund', function (resolvedResponse) {
        getDeposits();
        return resolvedResponse;
    }, 10000);
}

function refundAcceptingTenantDeductions(depositId) {
    console.log("refunding: " + depositId);
    return asyncPost(depositId, '/api/depositOps/acceptTenantDeductions', function (resolvedResponse) {
        return refundDeposit(depositId)
    }, 10000);
}

function sendDepositToArbitration(depositId) {
    console.log("sending to arbiter: " + JSON.stringify(depositId));
    return asyncPost(depositId, '/api/depositOps/arbitrate', JSON.parse, 10000).then(function (response) {
        console.log("successfully sent deposit : " + JSON.stringify(depositId) + " to arbiter")
    });
}

function beginDeduction(depositId) {
    let dialogHolder = document.getElementById('deductionDialog');
    dialogHolder.depositId = depositId;

    $(function () {
        $("#deductionDialog").dialog("open");
    });
}

function sendDeductionRequest() {

    let dialogHolder = document.getElementById('deductionDialog');
    let depositId = dialogHolder.depositId;

    let deductionAmount = document.getElementById("deductionAmount").value;
    let deductionReason = document.getElementById("deductionReason").value;
    getImageBytes(document.getElementById('deductionImage')).then(function (imageBytes) {
        let deductionRequest = {
            'depositId': depositId,
            'deductionReason': deductionReason,
            'deductionAmount': deductionAmount,
            'picture': imageBytes,
        };
        return asyncPost(deductionRequest, '/api/depositOps/deduct', function (resolvedResponse) {
            $(function () {
                $("#depositStatusDialog").dialog("close");
            });
            return resolvedResponse;
        }, 10000);
    }).then(function () {
        $(function () {
            $("#deductionDialog").dialog("close");
        });
    })
}

async function getImageBytes(imageInput) {
    return new Promise((resolve, reject) => {
        let imageFile = imageInput.files[0];
        let fileReader = new FileReader();
        fileReader.onerror = reject;
        fileReader.onload = function () {
            var arrayBuffer = this.result
            resolve(Array.from(new Uint8Array(arrayBuffer)));
        };

        fileReader.readAsArrayBuffer(imageFile);
    });


}

async function getInventoryBytes() {
    return new Promise((resolve, reject) => {
        let inventoryInputFile = document.getElementById('inventoryInput').files[0];
        let fileReader = new FileReader();
        fileReader.onerror = reject;
        fileReader.onload = function () {
            var arrayBuffer = this.result;
            resolve(Array.from(new Uint8Array(arrayBuffer)));
        };

        fileReader.readAsArrayBuffer(inventoryInputFile);
    });
}

function getBalance() {
    asyncGet('/api/depositOps/balance', JSON.parse).then(function (balances) {
        let numericBalance = balances[0];
        document.getElementById('balance').innerHTML = "£" + (numericBalance / 100);
        return numericBalance;
    });
}
