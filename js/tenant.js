function onload(){
  getDeposits();
}

function getDeposits(){
  asyncGet('/api/tenantOps/mydeposits', JSON.parse).then(populateDepositTable);
}

function loadAndShowInventory(inventoryHash){
  asyncDownload('/api/tenantOps/inventory?attachmentId='+inventoryHash).then(function(data){
      let typedArray = (new Uint8Array(data));
      renderPdfBytesToHolder(typedArray, 'pdfHolder', 'dialog')
  })
}

function fundDeposit(uniqueId){
    asyncPost(uniqueId, '/api/tenantOps/fundDeposit', JSON.parse, 10000).then(function(response){
      console.log(response);
    })
}

function populateDepositTable(loadedDeposits){

  let table = document.getElementById('depositTable');
  table.innerHTML = "";

  loadedDeposits.forEach(function(depositState){
    let loadedDeposit = depositState.state.data;
    let row = document.createElement('tr');
    let propertyIdCell = document.createElement('td');
    let landlordCell = document.createElement('td');
    let depositAmountCell = document.createElement('td');

    let showInventoryCell = document.createElement('td');
    let showInventoryButton = document.createElement('button');
    showInventoryButton.innerHTML = "Show Inventory";
    showInventoryCell.appendChild(showInventoryButton);
    showInventoryButton.onclick = function(){
      loadAndShowInventory(loadedDeposit.inventory)
    };

    let fundDepositCell = document.createElement('td');
    let fundDepositButton = document.createElement('button');
    fundDepositButton.innerHTML = "Fund Deposit";
    fundDepositCell.appendChild(fundDepositButton);
    fundDepositButton.onclick = function(){
      fundDeposit(loadedDeposit.linearId);
    }

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
