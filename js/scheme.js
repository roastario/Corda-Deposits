"use strict";

async function onload() {
    let balanceLoop = function () {
        getBalance();
        setTimeout(balanceLoop, 1000);
    };
    balanceLoop();
}

function getBalance() {
    asyncGet('/api/schemeOps/balance', JSON.parse).then(function (balances) {
        let numericBalance = balances[0];
        document.getElementById('balance').innerHTML = "£" + (numericBalance / 100);
        return numericBalance;
    });
}