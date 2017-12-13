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

function sendDepositRequest(){

    let tenantX500 = document.getElementById('tenantSelect').selectedOptions[0].data.obj;
    let landlordX500 = document.getElementById('landlordSelect').selectedOptions[0].data.obj;

    let amountOfDeposit = document.getElementById('amount').value;
    let propertyId = document.getElementById('propertyId').value;

    let depositRequest = {
        'landlordX500Name': landlordX500,
        'tenantX500Name': tenantX500,
        'amount': amountOfDeposit,
        'propertyId': propertyId
    }

      asyncPost(depositRequest, '/api/depositOps/createDeposit', function(resolvedResponse){
        console.log(resolvedResponse);
      }, 10000);

}

