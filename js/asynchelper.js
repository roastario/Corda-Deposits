var HTTP_OK = 200;
var HTTP_OK_CREATED = 201;

async function asyncGet(url, responseTransform, timeout){
  return asyncDo("GET", null, url, responseTransform, timeout)
}

async function asyncPost(data, url, responseTransform, timeout){
  return asyncDo("POST", data, url, responseTransform, timeout)
}

async function asyncDo(method, data, url, responseTransform, timeout){

  return new Promise( (resolve, reject) => {
    window.setTimeout(function () {
      reject("timedout");
    }, timeout? timeout : 2000);

    var xhr = new XMLHttpRequest();
    xhr.open(method, url, true);
    xhr.onload = function (e) {
      if (xhr.readyState === 4) {
        if (xhr.status === HTTP_OK || xhr.status === HTTP_OK_CREATED) {
          resolve(responseTransform ? responseTransform(xhr.responseText) : xhr.responseText);
        } else {
          reject(xhr.statusText);
        }
      }
    };
    xhr.onerror = function (e) {
      reject(xhr.statusText);
    };
    if (data){
      xhr.setRequestHeader("Content-Type", "application/json");
    }
    xhr.send(data ? JSON.stringify(data) : null);
  })
}

function populateSelectWithItems(selectElement, loadedItems){
  selectElement.options.length = 0;
  loadedItems.forEach(function (loadedItem){
    let optionElement = document.createElement("option");
    optionElement.value = JSON.stringify(loadedItem);
    optionElement.innerHTML = optionElement.value;
    optionElement.data = {}
    optionElement.data['obj'] = loadedItem
    selectElement.appendChild(optionElement);
  })
}
