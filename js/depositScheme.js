async function onload(){


  let inventoryInput = document.getElementById('inventoryInput');

  inventoryInput.onchange = function(event){
    let selectedFile = event.path[0].files[0];
    let fileReader = new FileReader();
    fileReader.onerror = function(error){
      console.log(error);
    };
    fileReader.onload = function(){
      var viewPortInfo = {width: 0, height: 0}
      var arrayBuffer = this.result
      let typedArray = (new Uint8Array(arrayBuffer));
      renderPdfBytesToHolder(typedArray, "pdfHolder", "dialog");
    }

    fileReader.readAsArrayBuffer(selectedFile);
  }

  await loadPeers();
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

async function sendDepositRequest(){

     let tenantX500 = document.getElementById('tenantSelect').selectedOptions[0].data.obj;
     let landlordX500 = document.getElementById('landlordSelect').selectedOptions[0].data.obj;

     let amountOfDeposit = document.getElementById('amount').value;
     let propertyId = document.getElementById('propertyId').value;


    return getInventoryBytes().then(function(loadedData){
      let depositRequest = {
          'landlordX500Name': landlordX500,
          'tenantX500Name': tenantX500,
          'amount': amountOfDeposit,
          'propertyId': propertyId,
          'inventory': loadedData
      }
      return asyncPost(depositRequest, '/api/depositOps/createDeposit', function(resolvedResponse){
        console.log(resolvedResponse);
        return resolvedResponse;
      }, 10000);
    });
}

async function getInventoryBytes(){
  return new Promise( (resolve, reject) => {
    let inventoryInputFile = document.getElementById('inventoryInput').files[0];
    let fileReader = new FileReader();
    fileReader.onerror = reject;
    fileReader.onload = function(){
      var arrayBuffer = this.result
      resolve(Array.from(new Uint8Array(arrayBuffer)));
    };

    fileReader.readAsArrayBuffer(inventoryInputFile);
  });
}
