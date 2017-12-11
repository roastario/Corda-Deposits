var HTTP_OK = 200;
var HTTP_OK_CREATED = 201;

async function doClick(){
    var amount = document.getElementById("amount").value;
    var currency = document.getElementById('currency').value;
    var returnedText = await asyncGet("/api/bankOps/issue-cash?currency="+currency+"&amount=" + amount);

    console.log(returnedText);
}

async function asyncGet(url){
  return new Promise( (resolve, reject) => {
    window.setTimeout(function () {
      reject("timedout");
    }, 2000);

    var xhr = new XMLHttpRequest();
    xhr.open("GET", url, true);
    xhr.onload = function (e) {
      if (xhr.readyState === 4) {
        if (xhr.status === HTTP_OK || xhr.status === HTTP_OK_CREATED) {
          resolve(xhr.responseText);
        } else {
          reject(xhr.statusText);
        }
      }
    };
    xhr.onerror = function (e) {
      reject(xhr.statusText);
    };
    xhr.send(null);
  })
}
