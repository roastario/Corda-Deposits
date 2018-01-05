"use strict";

async function onload() {
    let balanceLoop = function () {
        getBalance();
        setTimeout(balanceLoop, 1000);
    };
    balanceLoop();
    getDeposits();
}

function getBalance() {
    asyncGet('/api/schemeOps/balance', JSON.parse).then(function (balances) {
        let numericBalance = balances[0];
        document.getElementById('balance').innerHTML = "£" + (numericBalance / 100);
        return numericBalance;
    });
}


async function getDeposits() {
    return asyncGet('/api/schemeOps/deposits', JSON.parse).then(function (loadedDeposits) {

        const depositTable = document.getElementById('depositsHolder');


        loadedDeposits.forEach(function (deposit) {
            const depositRow = document.createElement('tr');
            const tenantCell = document.createElement('td');
            const landLordCell = document.createElement('td');
            const depositAmountCell = document.createElement('td');
            const landlordDeductionCell = document.createElement('td');
            const tenantDeductionCell = document.createElement('td');
            const arbitrateButtonCell = document.createElement('td');


            tenantCell.innerHTML = deposit.tenant;
            landLordCell.innerHTML = deposit.landlord;
            depositAmountCell.innerHTML = deposit.depositAmount;

            let landlordDeductionTotal = 0;
            deposit.landlordDeductions.forEach(deduction => {
                landlordDeductionTotal = parseFloat(deduction.deductionAmount.match(NUMERIC_REGEXP)[0]) + landlordDeductionTotal;
            });

            let tenantDeductionTotal = 0;
            deposit.tenantDeductions.forEach(deduction => {
                tenantDeductionTotal = parseFloat(deduction.deductionAmount.match(NUMERIC_REGEXP)[0]) + tenantDeductionTotal;
            });

            landlordDeductionCell.innerHTML = "£" + landlordDeductionTotal;
            tenantDeductionCell.innerHTML = "£" + tenantDeductionTotal;

            const arbitrateButton = document.createElement('button');
            arbitrateButton.innerHTML = 'Arbitrate!';
            arbitrateButton.onclick = function () {
                viewAndContestDeductions(deposit);
            };
            arbitrateButtonCell.appendChild(arbitrateButton);
            depositRow.appendChild(tenantCell);
            depositRow.appendChild(landLordCell);
            depositRow.appendChild(depositAmountCell);
            depositRow.appendChild(landlordDeductionCell);
            depositRow.appendChild(tenantDeductionCell);
            depositRow.appendChild(document.createElement('td'));
            depositRow.appendChild(arbitrateButtonCell);
            depositTable.appendChild(depositRow);
        });


        return loadedDeposits;
    });
}

function viewAndContestDeductions(deposit) {

    const deductions = deposit.landlordDeductions;
    const tenantDeductions = deposit.tenantDeductions;

    const indexer = function (value) {
        return value.deductionId.id;
    };
    const indexedTenantDeductions = _.keyBy(tenantDeductions, indexer);

    const deductionDialog = document.getElementById('arbitrateDialog');
    deductionDialog.innerHTML = '';
    deductionDialog.deductions = [];
    deductionDialog.deposit = deposit;

    deductions.forEach(function (deduction) {


        const tenantDeduction = indexedTenantDeductions[deduction.deductionId.id];

        const landlordAmount = parseFloat(deduction.deductionAmount.match(NUMERIC_REGEXP)[0]);
        const tenantAmount = parseFloat(tenantDeduction.deductionAmount.match(NUMERIC_REGEXP)[0]);

        const deductionAmount = deduction.deductionAmount;
        const deductionReason = deduction.deductionReason;
        const imageHash = deduction.picture;

        const deductionTable = document.createElement('table');
        const row = document.createElement('tr');
        deductionTable.appendChild(row);

        const deductionReasonCell = document.createElement('td');
        deductionReasonCell.innerHTML = deductionReason;

        const landlordAmountCell = document.createElement('td');
        const tenantAmountCell = document.createElement('td');

        landlordAmountCell.innerHTML = deductionAmount;
        tenantAmountCell.innerHTML = tenantDeduction.deductionAmount;

        //amounts and reason set up
        const deductionImageCell = document.createElement('td');

        deductionDialog.appendChild(deductionTable);
        row.appendChild(deductionReasonCell);
        row.appendChild(deductionImageCell);
        row.appendChild(landlordAmountCell);
        row.appendChild(tenantAmountCell);


        asyncDownload('/api/schemeOps/deductionImage?imageId=' + imageHash).then(function (binaryArrayBuffer) {
            const base64Data = base64ArrayBuffer(binaryArrayBuffer);
            const outputImg = document.createElement('img');
            outputImg.style.width = '500px';
            outputImg.style.height = '500px';
            outputImg.src = 'data:image/png;base64,' + base64Data;
            deductionImageCell.appendChild(outputImg);
        });

        const sliderCell = document.createElement('td');


        const sliderBetweenTenantAndLandlord = document.createElement('input');
        sliderBetweenTenantAndLandlord.setAttribute('type', 'range');
        sliderBetweenTenantAndLandlord.setAttribute('min', Math.min(tenantAmount, landlordAmount).toString());
        sliderBetweenTenantAndLandlord.setAttribute('max', Math.max(tenantAmount, landlordAmount).toString());
        sliderBetweenTenantAndLandlord.setAttribute('value', ((Math.max(tenantAmount, landlordAmount) - Math.min(tenantAmount, landlordAmount)) / 2).toString());

        sliderCell.appendChild(sliderBetweenTenantAndLandlord);

        const commentInputCell = document.createElement('td');
        const commentInput = document.createElement('input');
        commentInput.setAttribute('type', 'text');
        commentInputCell.appendChild(commentInput);


        const arbitratorDeductionAmountCell = document.createElement('td');

        row.appendChild(sliderCell);
        row.appendChild(commentInputCell);
        row.appendChild(arbitratorDeductionAmountCell);

        const updateArbitratorDeductions = function (arbitratorDeduction) {
            if (deductionDialog.deductions) {
                deductionDialog.deductions = _.filter(deductionDialog.deductions, function (deductionToCheck) {
                    return arbitratorDeduction.deductionId !== deductionToCheck.deductionId
                });

                deductionDialog.deductions.push(arbitratorDeduction);
                console.log(JSON.stringify(deductionDialog.deductions));
            }

            if (deductionDialog.deductions.length === deposit.tenantDeductions.length) {
                $(deductionDialog).dialog("option", "buttons",
                    [
                        {
                            text: "Submit",
                            icon: "ui-icon-heart",
                            click: function () {
                                asyncPost(
                                    {
                                        depositId: deposit.linearId,
                                        deductions: deductionDialog.deductions
                                    }, '/api/schemeOps/arbitrate', JSON.parse, 10000).then(function (arbitratedDeposit) {
                                    console.log(JSON.stringify(arbitratedDeposit));
                                    $(deductionDialog).dialog("close");
                                });
                            }
                        }
                    ]
                )
            }
        };

        const populateTotal = function (arbitratorDeduction) {
            arbitratorDeductionAmountCell.innerHTML = JSON.stringify(arbitratorDeduction.amount);
            updateArbitratorDeductions(arbitratorDeduction);
        };

        const performUpdateToDeductions = function (event) {
            const arbitratorDeduction = {
                deductionId: deduction.deductionId,
                comment: commentInput.value,
                amount: Math.round(sliderBetweenTenantAndLandlord.value),
            };
            populateTotal(arbitratorDeduction);
        };

        commentInput.oninput = performUpdateToDeductions;
        sliderBetweenTenantAndLandlord.oninput = performUpdateToDeductions;


    });
    $(deductionDialog).dialog('open');
}
