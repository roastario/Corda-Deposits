

async function doClick(){
    var amount = document.getElementById("amount").value;
    var currency = document.getElementById('currency').value;
    var returnedText = await asyncGet("/api/bankOps/issue-cash?currency="+currency+"&amount=" + amount);

    console.log(returnedText);
}

async function onLoad(){
  let peerDropdown = document.getElementById('peerDropDown');
  peerDropdown.options.length = 0;
  let loadedPeers = await loadPeersToTransferTo()
  loadedPeers.forEach(function (loadedPeer){
    let optionElement = document.createElement("option");
    optionElement.value = JSON.stringify(loadedPeer);
    optionElement.innerHTML = optionElement.value;
    optionElement.data = {}
    optionElement.data['x500'] = loadedPeer
    peerDropdown.appendChild(optionElement);
  })
}

async function sendTransferRequest(){

  let peerDropdown = document.getElementById('peerDropDown');
  let selectedPeer = peerDropdown.selectedOptions[0];

  let xferAmount = document.getElementById('xferAmount').value;
  let xferCurrency = document.getElementById('xferCurrency').value;

  let request = {
    'nameOfParty': selectedPeer.data.x500,
    'amount': xferAmount,
    'currency': xferCurrency
  };

  let response = await asyncPost(request, '/api/bankOps/xfer', function(resolvedResponse){
    console.log(resolvedResponse);
  });

}

async function loadPeersToTransferTo(){
    var loadingPromise = asyncGet("/api/bankOps/peers", function(response){
      return JSON.parse(response)['peers']
    }).then(function(resolvedPeers){
      console.log("retrieved peers: " + resolvedPeers);
      return resolvedPeers;
    });
    return loadingPromise;
}
