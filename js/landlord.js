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

  loadPeers().then(function(){
    let getDepositsLoopable = function(){
        getDeposits();
        setTimeout(getDepositsLoopable, 1000)
    }

    getDepositsLoopable();
  });



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


    $( function() {
      $( "#deductionDialog" ).dialog();
    } );

    $( function() {
      $( "#deductionDialog" ).dialog("close");
    } );
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
        getDeposits();
        return resolvedResponse;
      }, 10000);
    });
}

async function getDeposits(){

    return asyncGet("/api/depositOps/deposits", JSON.parse).then((deposits) => {
        return deposits.map((incoming) => {
            return incoming.state.data;
        })
    }).then(function (extractedDeposits){

        let unfundedDeposits = [];
        let activeDeposits = [];
        let depositsAwaitingRefunding = [];
        let closedDeposits = [];

        extractedDeposits.forEach(deposit => {
            if (!deposit.amountDeposited){
                unfundedDeposits.push(deposit);
            }else if (!deposit.refundRequested){
                activeDeposits.push(deposit);
            }else if (!deposit.refunded){
                depositsAwaitingRefunding.push(deposit);
            }else{
                closedDeposits.push(deposit);
            }
        })

        populateDepositsToRefund(depositsAwaitingRefunding);
        populateDepositsWaitingForFunding(unfundedDeposits);
        populateActiveDeposits(activeDeposits);
    });

}

function populateDepositsToRefund(deposits){

    let holdingTable = document.getElementById("refundDeposits")
    holdingTable.innerHTML = "";

    deposits.forEach(deposit => {
        let row = document.createElement('tr');
        let propertyIdCell = document.createElement('td');
        let tenantNameCell = document.createElement('td');
        let depositAmountCell = document.createElement('td');


        let inventoryButtonCell = document.createElement('td');
        let inventoryButton = document.createElement('button');
        inventoryButton.onclick = function(){
            loadAndShowInventory(deposit.inventory);
        }
        inventoryButton.innerHTML = "Show inventory"
        inventoryButtonCell.appendChild(inventoryButton)

        let deductButtonCell = document.createElement('td');
        let deductButton = document.createElement('button');
        deductButton.onclick = function(){
            beginDeduction(deposit.linearId);
        }
        deductButton.innerHTML = "Request Deduction"
        deductButtonCell.appendChild(deductButton)

        let refundButtonCell = document.createElement('td');
        let refundButton = document.createElement('button');
        refundButton.onclick = () => {
            refundDeposit(deposit.linearId);
        }
        refundButton.innerHTML = "Release Refund"
        refundButtonCell.appendChild(refundButton)

        propertyIdCell.innerHTML = deposit.propertyId;
        tenantNameCell.innerHTML = deposit.tenant;
        depositAmountCell.innerHTML = deposit.depositAmount;

        row.appendChild(propertyIdCell)
        row.appendChild(depositAmountCell)
        row.appendChild(tenantNameCell)

        row.appendChild(inventoryButtonCell)
        row.appendChild(deductButtonCell)
        row.appendChild(refundButtonCell)

        holdingTable.appendChild(row);
    })

}


function populateDepositsWaitingForFunding(deposits){

    let holdingTable = document.getElementById("unfundedDeposits")
    holdingTable.innerHTML = "";


    deposits.forEach(deposit => {
        let row = document.createElement('tr');
        let propertyIdCell = document.createElement('td');
        let tenantNameCell = document.createElement('td');
        let depositAmountCell = document.createElement('td');


        let inventoryButtonCell = document.createElement('td');
        let inventoryButton = document.createElement('button');
        inventoryButton.onclick = function(){
            loadAndShowInventory(deposit.inventory);
        }
        inventoryButton.innerHTML = "Show inventory"
        inventoryButtonCell.appendChild(inventoryButton)



        let cancelButtonCell = document.createElement('td');
        let cancelButton = document.createElement('button');
        cancelButton.onclick = () => {
            cancelDeposit(deposit.linearId);
        }
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

function populateActiveDeposits(deposits){

    let holdingTable = document.getElementById("activeDeposits")
    holdingTable.innerHTML = "";


    deposits.forEach(deposit => {
        let row = document.createElement('tr');
        let propertyIdCell = document.createElement('td');
        let tenantNameCell = document.createElement('td');
        let depositAmountCell = document.createElement('td');

        propertyIdCell.innerHTML = deposit.propertyId;
        tenantNameCell.innerHTML = deposit.tenant;
        depositAmountCell.innerHTML = deposit.depositAmount;

        row.appendChild(propertyIdCell)
        row.appendChild(depositAmountCell)
        row.appendChild(tenantNameCell)
        holdingTable.appendChild(row);
    })

}

function loadAndShowInventory(inventoryHash, postRender){
  asyncDownload('/api/depositOps/inventory?attachmentId='+inventoryHash).then(function(data){
      let typedArray = (new Uint8Array(data));
      return renderPdfBytesToHolder(typedArray, 'pdfHolder', 'pdfDialog')
  }).then(function(pdfRender){
    if (postRender) {
      postRender();
    };
  })
}

function refundDeposit(depositId){
    console.log("refunding: " + depositId);
    return asyncPost(depositId, '/api/depositOps/refund', function(resolvedResponse){
            getDeposits();
            return resolvedResponse;
    }, 10000);
}

function beginDeduction(depositId){
    let dialogHolder = document.getElementById('deductionDialog');
    dialogHolder.depositId = depositId;

        $( function() {
          $( "#deductionDialog" ).dialog("open");
        } );
}

function sendDeductionRequest(){

        let dialogHolder = document.getElementById('deductionDialog');
        let depositId = dialogHolder.depositId;

        let deductionAmount = document.getElementById("deductionAmount").value;
        let deductionReason = document.getElementById("deductionReason").value;
        getImageBytes(document.getElementById('deductionImage')).then(function(imageBytes){
          let deductionRequest = {
              'depositId': depositId,
              'deductionReason': deductionReason,
              'deductionAmount': deductionAmount,
              'picture': imageBytes,
          }
          return asyncPost(deductionRequest, '/api/depositOps/deduct', function(resolvedResponse){
            $( function() {
              $( "#depositStatusDialog" ).dialog("close");
            } );
            return resolvedResponse;
          }, 10000);
        }).then(function(){
            $( function() {
              $( "#deductionDialog" ).dialog("close");
            } );
        })
}

async function getImageBytes(imageInput){
  return new Promise( (resolve, reject) => {
    let imageFile = imageInput.files[0];
    let fileReader = new FileReader();
    fileReader.onerror = reject;
    fileReader.onload = function(){
      var arrayBuffer = this.result
      resolve(Array.from(new Uint8Array(arrayBuffer)));
    };

    fileReader.readAsArrayBuffer(imageFile);
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
