function onload(){
    getBalance();
    getDeposits();
}

function getBalance(){

    asyncGet('/api/tenantOps/balance', JSON.parse).then(function(balances){
      let numericBalance = balances[0];
      document.getElementById('balance').innerHTML = "£" + (numericBalance/100);
      return numericBalance;
    }).then(function(balancesByCurrency){
      console.log(balancesByCurrency);
    });
}

function getDeposits(){
  asyncGet('/api/tenantOps/mydeposits', JSON.parse).then(function(loadedDeposits){
    let fundedDeposits = [];
    let unFundedDeposits = [];
    loadedDeposits.forEach(deposit => {
      if (deposit.state.data.amountDeposited){
        fundedDeposits.push(deposit.state.data)
      }else{
        unFundedDeposits.push(deposit.state.data)
      }
    });
    return {funded: fundedDeposits, unfunded: unFundedDeposits}
  }).then(splitDeposits => {
      populateFundedDepositTable(splitDeposits.funded);
      populateUnfundedDepositTable(splitDeposits.unfunded);
  });
}



function loadAndShowInventory(inventoryHash, postRender){
  asyncDownload('/api/tenantOps/inventory?attachmentId='+inventoryHash).then(function(data){
      let typedArray = (new Uint8Array(data));
      return renderPdfBytesToHolder(typedArray, 'pdfHolder', 'dialog')
  }).then(function(pdfRender){
    if (postRender) {
      postRender();
    };
  })
}

function fundDeposit(uniqueId){
    asyncPost(uniqueId, '/api/tenantOps/fundDeposit', JSON.parse, 10000).then(function(response){
      console.log("funded deposit: " + uniqueId);
      return true;
    }).then(function(){
        onload();
    }).catch(function(rejection){
        console.log(rejection);
    })
}

function populateFundedDepositTable(loadedDeposits){
  let table = document.getElementById('fundedDepositTable');
  table.innerHTML = "";

  loadedDeposits.forEach(function(depositState){
    let loadedDeposit = depositState;
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
    requestRefundButton.onclick = function(){
        requestRefund(loadedDeposit.linearId);
    }

    showInventoryButton.innerHTML = "Show Inventory";
    showInventoryCell.appendChild(showInventoryButton);
    showInventoryButton.onclick = function(){
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

function populateUnfundedDepositTable(loadedDeposits){

  let table = document.getElementById('unfundedDepositTable');
  table.innerHTML = "";

  loadedDeposits.forEach(function(depositState){
    let loadedDeposit = depositState;
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
    fundDepositButton.onclick = function(){
      fundDeposit(loadedDeposit.linearId);
    }
    fundDepositButton.disabled = true;

    showInventoryButton.innerHTML = "Show Inventory";
    showInventoryCell.appendChild(showInventoryButton);
    showInventoryButton.onclick = function(){
      loadAndShowInventory(loadedDeposit.inventory, function(){
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

async function requestRefund(uniqueId){
        return asyncPost(uniqueId, '/api/tenantOps/refund', i => i, 10000).then(function(response){
          console.log("requested refund for deposit: " + (uniqueId));
          return true;
        }).then(function(){
            onload();
        }).catch(function(rejection){
            console.log(rejection);
        })
}

async function loadPeers(){
  return asyncGet('/api/depositOps/peers', function(json){
        let parsedPeers = JSON.parse(json);
        return parsedPeers['peers'].filter(function(peer){
            let lowerCaseName = peer.toLowerCase();
            return lowerCaseName.includes('landlord') || lowerCaseName.includes('tenant')
        });
  }).then(function(loadedPeers){
        populateSelectWithItems(document.getElementById('tenantSelect'), loadedPeers);
        populateSelectWithItems(document.getElementById('landlordSelect'), loadedPeers);
  })
}
