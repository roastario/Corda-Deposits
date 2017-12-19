async function onload(){

  setupDialogs();

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
      renderPdfBytesToHolder(typedArray, "pdfHolder", "pdfDialog");
    }

    fileReader.readAsArrayBuffer(selectedFile);
  }

  await loadPeers();
}

function setupDialogs(){
  $( function() {
    $( "#pdfDialog" ).dialog();
  } );

  $( function() {
    $( "#pdfDialog" ).dialog("close");
  } );

  $("#depositStatusDialog").dialog({
      autoOpen: true,
      modal: true,
      width: 'auto',
      closeOnEscape: false,
      draggable: false,
      resizable: false,
      buttons: {}
  });


  $( function() {
    $( "#depositStatusDialog" ).dialog("close");
  });
}

async function loadPeers(){
  return asyncGet('/api/depositOps/peers', function(json){
        let parsedPeers = JSON.parse(json);
        return parsedPeers['peers'];
  }).then(function(loadedPeers){
        populateSelectWithItems(document.getElementById('tenantSelect'), loadedPeers);
        populateSelectWithItems(document.getElementById('schemeSelect'), loadedPeers);
  })
}

async function sendDepositRequest(){

     $( function() {
       $( "#depositStatusDialog" ).dialog("open");
     } );

     let tenantX500 = document.getElementById('tenantSelect').selectedOptions[0].data.obj;
     let schemeX500Name = document.getElementById('schemeSelect').selectedOptions[0].data.obj;

     let amountOfDeposit = document.getElementById('amount').value;
     let propertyId = document.getElementById('propertyId').value;


    return getInventoryBytes().then(function(loadedData){
      let depositRequest = {
          'schemeX500Name': schemeX500Name,
          'tenantX500Name': tenantX500,
          'amount': amountOfDeposit,
          'propertyId': propertyId,
          'inventory': loadedData
      }
      return asyncPost(depositRequest, '/api/depositOps/createDeposit', function(resolvedResponse){
        $( function() {
          $( "#depositStatusDialog" ).dialog("close");
        } );
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
