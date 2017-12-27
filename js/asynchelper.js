const HTTP_OK = 200;
const HTTP_OK_CREATED = 201;

async function asyncGet(url, responseTransform, timeout) {
    return asyncDo("GET", null, url, responseTransform, timeout)
}

async function asyncPost(data, url, responseTransform, timeout) {
    return asyncDo("POST", data, url, responseTransform, timeout)
}


async function asyncDownload(url) {
    return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        xhr.responseType = 'arraybuffer';
        xhr.open('GET', url, true);
        xhr.onload = function (e) {
            if (xhr.readyState === 4) {
                if (xhr.status === HTTP_OK || xhr.status === HTTP_OK_CREATED) {
                    resolve(xhr.response);
                } else {
                    reject(xhr.statusText);
                }
            }
        };
        xhr.onerror = function (e) {
            reject(xhr.statusText);
        };
        xhr.send();
    })
}

async function asyncDo(method, data, url, responseTransform, timeout) {

    return new Promise((resolve, reject) => {
        window.setTimeout(function () {
            reject("timedout");
        }, timeout ? timeout : 2000);

        const xhr = new XMLHttpRequest();
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
        if (data) {
            xhr.setRequestHeader("Content-Type", "application/json");
        }
        xhr.send(data ? JSON.stringify(data) : null);
    })
}

function populateSelectWithItems(selectElement, loadedItems) {
    selectElement.options.length = 0;
    loadedItems.forEach(function (loadedItem) {
        let optionElement = document.createElement("option");
        optionElement.value = JSON.stringify(loadedItem);
        optionElement.innerHTML = optionElement.value;
        optionElement.data = {}
        optionElement.data['obj'] = loadedItem
        selectElement.appendChild(optionElement);
    })
}


function base64ArrayBuffer(arrayBuffer) {
    let base64 = '';
    const encodings = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

    const bytes = new Uint8Array(arrayBuffer);
    const byteLength = bytes.byteLength;
    const byteRemainder = byteLength % 3;
    const mainLength = byteLength - byteRemainder;

    let a, b, c, d;
    let chunk;

    // Main loop deals with bytes in chunks of 3
    for (let i = 0; i < mainLength; i = i + 3) {
        // Combine the three bytes into a single integer
        chunk = (bytes[i] << 16) | (bytes[i + 1] << 8) | bytes[i + 2]

        // Use bitmasks to extract 6-bit segments from the triplet
        a = (chunk & 16515072) >> 18 // 16515072 = (2^6 - 1) << 18
        b = (chunk & 258048)   >> 12 // 258048   = (2^6 - 1) << 12
        c = (chunk & 4032)     >>  6 // 4032     = (2^6 - 1) << 6
        d = chunk & 63               // 63       = 2^6 - 1

        // Convert the raw binary segments to the appropriate ASCII encoding
        base64 += encodings[a] + encodings[b] + encodings[c] + encodings[d]
    }

    // Deal with the remaining bytes and padding
    if (byteRemainder === 1) {
        chunk = bytes[mainLength]

        a = (chunk & 252) >> 2 // 252 = (2^6 - 1) << 2

        // Set the 4 least significant bits to zero
        b = (chunk & 3)   << 4 // 3   = 2^2 - 1

        base64 += encodings[a] + encodings[b] + '=='
    } else if (byteRemainder === 2) {
        chunk = (bytes[mainLength] << 8) | bytes[mainLength + 1]

        a = (chunk & 64512) >> 10 // 64512 = (2^6 - 1) << 10
        b = (chunk & 1008)  >>  4 // 1008  = (2^6 - 1) << 4

        // Set the 2 least significant bits to zero
        c = (chunk & 15)    <<  2 // 15    = 2^4 - 1

        base64 += encodings[a] + encodings[b] + encodings[c] + '='
    }

    return base64
}
