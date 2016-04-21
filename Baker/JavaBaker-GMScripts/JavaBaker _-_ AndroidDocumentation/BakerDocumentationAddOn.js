// ==UserScript==
// @name        BakerDocumentationAddOn
// @namespace   JavaBaker
// @version     1
// @include     http://developer.android.com/*
// @include     developer.android.com/*
// @include     https://developer.android.com/*
// ==/UserScript==


var url = document.URL,
    className = "", 
    i = 0;
var arr = url.split("/"),
    startFlag = 0;

for(i=0; i<arr.length; i++)
{
    if(startFlag == 1)
    {
        if(arr[i].indexOf(".html") == -1)
        {
            className = className + '.' + arr[i];
        }
        else
        {
            var temp = arr[i].split(".");
            className = className + "." + temp[0];
            break;
        }
    }
    if(arr[i] === "reference")
    {
        startFlag = 1;
    }
}
className = className.substring(1);


var getDistinctTableString = function(response) {
    var json = JSON.parse(response.responseText),
        table = "<div class='scrollit'><table><tr><th>#</th><th>Title</th><th>Snippet Index in Accepted Answer</th><th>Line No.</th><th>Date</th></tr>",
        stack = [],
        index = 0;
    for(i =0; i<json.length; i++)
    {
        var temp = json[i];
        if($.inArray(temp.title, stack) === -1)
        {
            index++;
            stack[stack.length] = temp.title;
            table = table + "<tr><td>" + index + "</td><td><a href = " + temp.url + ">" + temp.title + "</a></td><td>" + temp.codeid + "</td><td>" + temp.line + "</td><td>" + temp.date + "</td></tr>";
        }
    }
    table = table + "</table></div>";
    return table;
};

var updatePage = function(response){
    var table = getDistinctTableString(response);
    $( ".jd-descr" ).first().before(
        '<div class="stackoverflowExamples"><h2><img id="stackOverflowLogo" src="http://files.quickmediasolutions.com/so-images/stackoverflow.svg"> Code Examples for '+ className +' <hr></h2>' + table +  '</div>'
    );
    $("#stackOverflowLogo").css ({
        width:      "30px",
        height:     "30px",
    });
         

};

GM_xmlhttpRequest({
    method: "GET",
    url: "http://gadget.cs.uwaterloo.ca:2145/snippet/BakerExampleFetch.php?name="+className+"&precision=10",
    onload: updatePage
});
