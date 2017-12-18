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
    return loadedDeposits.filter(deposit => deposit.state.data.amountDeposited.startsWith("0"));
  }).then(populateUnfundedDepositTable);
}

function loadAndShowInventory(inventoryHash, buttonToEnable){
  asyncDownload('/api/tenantOps/inventory?attachmentId='+inventoryHash).then(function(data){
      let typedArray = (new Uint8Array(data));
      return renderPdfBytesToHolder(typedArray, 'pdfHolder', 'dialog')
  }).then(function(pdfRender){
    buttonToEnable.disabled = false;
  })
}

function fundDeposit(uniqueId){
    asyncPost(uniqueId, '/api/tenantOps/fundDeposit', JSON.parse, 10000).then(function(response){
      console.log(response);
    })
}

function populateUnfundedDepositTable(loadedDeposits){

  let table = document.getElementById('unfundedDepositTable');
  table.innerHTML = "";

  loadedDeposits.forEach(function(depositState){
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
    fundDepositButton.onclick = function(){
      fundDeposit(loadedDeposit.linearId);
    }
    fundDepositButton.disabled = true;

    showInventoryButton.innerHTML = "Show Inventory";
    showInventoryCell.appendChild(showInventoryButton);
    showInventoryButton.onclick = function(){
      loadAndShowInventory(loadedDeposit.inventory, fundDepositButton)
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
