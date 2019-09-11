// wait for the DOM to be loaded
var previousJSON=null;

$(document).ready(function() {
            getDataFromServer(false); // first time do not block
//            getDataFromServer(true); // second time block

});



// Does an synchronous non blocking GET or blocking (if data has not changed) from the server  to retrieve the data
function getDataFromServer(block) {
  //  $("#tree-space").jstree("refresh");

    $.ajax({
        type: "GET",
        // use two URLS, one for blocking and one for non blocking
        url: "/ajax/jstree/" + (block? "sync" : "async"),
        dataType: 'json',
        contentType: "application/json; charset=utf-8",
        data: "",
        async: block? true: false,
        success: function(retdata){
            if ((retdata!="") && (retdata!=null)) {
                var sameContent=false;
                if (previousJSON!=null)
                    sameContent= (JSON.stringify(retdata) === JSON.stringify(previousJSON));

                if (!sameContent) {
                    // make a copy to send to the tree, because the tree algorithm changes the content ....
                    var forTree = jQuery.extend(true, {}, retdata);
                    displayTree(forTree);
                    previousJSON=retdata;
                }
            }
        }

        });

    $('#ajaxError').ajaxError(function(e, xhr, settings, exception) {
                $(this).text('Error in: ' + settings.url + ' - Error: ' +
                    exception + " - Response: " + xhr.responseText);
        });
}
// Displays the provided data in the tree
function displayTree(jsondata) {
    $("#tree-space").jstree(eval(jsondata));
}

