function renderPage(pdf, pageNumberToRender, viewPortInfo){
  let pageRenderPromise = pdf.getPage(pageNumberToRender).then(function (page){
    var scale = 1.5;
    var pageViewport = page.getViewport(scale);
    let pageCanvas = document.getElementById('page'+pageNumberToRender);
    var context = pageCanvas.getContext('2d');
    pageCanvas.height = pageViewport.height;
    pageCanvas.width = pageViewport.width;
    var renderContext = {
      canvasContext: context,
      viewport: pageViewport
    };
    if (viewPortInfo.width < pageViewport.width){
      viewPortInfo.width = pageViewport.width;
    }
    if (viewPortInfo.height < pageViewport.height){
      viewPortInfo.height = pageViewport.height;
    }
    return page.render(renderContext);
  })
  return pageRenderPromise;
}


function renderPdfBytesToHolder(typedArray, holderId, dialogId){
  var viewPortInfo = {width: 0, height: 0}
  let pdfHolderDiv = document.getElementById(holderId);
  pdfHolderDiv.innerHTML = "";
  let pdfPromise = PDFJS.getDocument(typedArray).then(function(pdf) {
    let numberOfPages = pdf.numPages;
    //as PDFJS is async, we need to do some tricks to ensure the pages
    //are rendered in correct order
    //first lets build the canvases
    for (var i = 1; i <= numberOfPages; i++) {
      let canvasElement = document.createElement('canvas');
      canvasElement.id = "page"+i;
      pdfHolderDiv.appendChild(canvasElement);
    }
    var renderPromises = [];
    //and now actually render to them
    for (var pageNumber = 1; pageNumber <= numberOfPages; pageNumber++) {
      renderPromises.push(renderPage(pdf, pageNumber, viewPortInfo))
    }
    return Promise.all(renderPromises);
  });

  //set dialog size and open with canvases
  return pdfPromise.then(function(renderResult){
    $( function() {
      var w = Math.max(document.documentElement.clientWidth, window.innerWidth || 0);
      var h = Math.max(document.documentElement.clientHeight, window.innerHeight || 0);
      $("#"+dialogId).dialog({width: Math.min(w, viewPortInfo.width), height: Math.min(h, viewPortInfo.height)});
      $("#"+dialogId).dialog("open");
    } );
  })

}
